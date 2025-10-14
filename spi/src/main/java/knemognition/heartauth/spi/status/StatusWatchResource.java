package knemognition.heartauth.spi.status;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import knemognition.heartauth.orchestrator.ApiException;
import knemognition.heartauth.orchestrator.model.FlowStatusDto;
import knemognition.heartauth.orchestrator.model.StatusResponseDto;
import knemognition.heartauth.spi.config.HaSessionNotes;
import knemognition.heartauth.spi.gateway.OrchClient;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.RootAuthenticationSessionModel;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

@Path("")
public class StatusWatchResource {

    private static final int POLL_PERIOD_MS = 1500;
    private static final int BACKOFF_STEP_MS = 200;
    private static final int BACKOFF_MAX_STEPS = 5;
    private final KeycloakSession session;

    public StatusWatchResource(KeycloakSession session) {
        this.session = session;
    }

    @GET
    @Path("watch/ecg")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @ActivateRequestContext
    public void watchEcg(@QueryParam("root") String rootId,
                         @QueryParam("tab") String tabId,
                         @QueryParam("id") String challengeIdStr,
                         @Context SseEventSink sink,
                         @Context Sse sse) {

        watchStatus(StatusWatchRegistry.TYPE_ECG, rootId, tabId, challengeIdStr, sink, sse,
                (clientApi, kcSessionId) -> {
                    try {
                        return clientApi.getChallengeStatus(UUID.fromString(challengeIdStr));
                    } catch (ApiException e) {
                        throw new RuntimeException(e);
                    }
                },
                as -> as.getAuthNote(HaSessionNotes.ECG_CHALLENGE_ID),
                true
        );
    }

    @GET
    @Path("watch/pairing")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @ActivateRequestContext
    public void watchPairing(@QueryParam("root") String rootId,
                             @QueryParam("tab") String tabId,
                             @QueryParam("id") String pairingIdStr,
                             @Context SseEventSink sink,
                             @Context Sse sse) {

        watchStatus(StatusWatchRegistry.TYPE_PAIRING, rootId, tabId, pairingIdStr, sink, sse,
                (clientApi, kcSessionId) -> {
                    try {
                        return clientApi.getPairingStatus(UUID.fromString(pairingIdStr));
                    } catch (ApiException e) {
                        throw new RuntimeException(e);
                    }
                },
                as -> as.getAuthNote(HaSessionNotes.PAIRING_JTI),
                true
        );
    }

    @POST
    @Path("watch/ecg/close")
    @Produces(MediaType.TEXT_PLAIN)
    @ActivateRequestContext
    public Response closeEcg(@QueryParam("root") String rootId,
                             @QueryParam("tab") String tabId,
                             @QueryParam("id") String challengeIdStr) {
        closeWatch(StatusWatchRegistry.TYPE_ECG, rootId, tabId, challengeIdStr);
        return Response.accepted()
                .type(MediaType.TEXT_PLAIN_TYPE)
                .entity("closed")
                .build();
    }

    @POST
    @Path("watch/pairing/close")
    @Produces(MediaType.TEXT_PLAIN)
    @ActivateRequestContext
    public Response closePairing(@QueryParam("root") String rootId,
                                 @QueryParam("tab") String tabId,
                                 @QueryParam("id") String pairingIdStr) {
        closeWatch(StatusWatchRegistry.TYPE_PAIRING, rootId, tabId, pairingIdStr);
        return Response.accepted()
                .type(MediaType.TEXT_PLAIN_TYPE)
                .entity("closed")
                .build();
    }

    private void watchStatus(String watchType,
                             String rootId,
                             String tabId,
                             String entityIdStr,
                             SseEventSink sink,
                             Sse sse,
                             BiFunction<OrchClient, String, StatusResponseDto> resolver,
                             Function<AuthenticationSessionModel, String> activeIdResolver,
                             boolean stopOnTerminal) {

        RealmModel realm = session.getContext()
                .getRealm();
        RootAuthenticationSessionModel root = session.authenticationSessions()
                .getRootAuthenticationSession(realm, rootId);

        if (root == null) {
            close(sink);
            return;
        }

        AuthenticationSessionModel as = resolveAuthSession(root, tabId);
        if (as == null || entityIdStr == null || entityIdStr.isBlank()) {
            sendAndCloseError(sink, sse, POLL_PERIOD_MS);
            return;
        }

        if (!isMatchingEntity(as, entityIdStr, activeIdResolver)) {
            sendAndCloseError(sink, sse, POLL_PERIOD_MS);
            return;
        }

        final OrchClient clientApi;
        try {
            clientApi = OrchClient.clientFromRealm(realm);
        } catch (Exception badCfg) {
            sendAndCloseError(sink, sse, POLL_PERIOD_MS);
            return;
        }

        final String kcSessionId = root.getId();
        StatusWatchRegistry.register(watchType, kcSessionId, tabId, entityIdStr, sink);

        if (StatusWatchRegistry.isCloseRequested(watchType, kcSessionId, tabId, entityIdStr)) {
            StatusWatchRegistry.unregister(watchType, kcSessionId, tabId, entityIdStr, sink);
            StatusWatchRegistry.markFinished(watchType, kcSessionId, tabId, entityIdStr);
            close(sink);
            return;
        }

        if (!safeSendStatus(sink, sse, POLL_PERIOD_MS, FlowStatusDto.PENDING)) {
            close(sink);
            StatusWatchRegistry.unregister(watchType, kcSessionId, tabId, entityIdStr, sink);
            StatusWatchRegistry.markFinished(watchType, kcSessionId, tabId, entityIdStr);
            return;
        }

        int err = 0;
        try {
            if (StatusWatchRegistry.isCloseRequested(watchType, kcSessionId, tabId, entityIdStr)) {
                return;
            }
            while (!sink.isClosed()) {
                if (StatusWatchRegistry.isCloseRequested(watchType, kcSessionId, tabId, entityIdStr)) {
                    return;
                }
                try {
                    StatusResponseDto st = resolver.apply(clientApi, kcSessionId);
                    err = 0;

                    if (!safeSendStatus(sink, sse, POLL_PERIOD_MS, st.getStatus())) {
                        close(sink);
                        return;
                    }

                    if (!isMatchingEntity(as, entityIdStr, activeIdResolver)) {
                        sendAndCloseError(sink, sse, POLL_PERIOD_MS);
                        return;
                    }

                    boolean terminal = switch (st.getStatus()) {
                        case APPROVED, DENIED, EXPIRED, NOT_FOUND -> true;
                        case PENDING, CREATED -> false;
                    };
                    if (stopOnTerminal && terminal) {
                        close(sink);
                        return;
                    }

                    Thread.sleep(POLL_PERIOD_MS);
                } catch (Exception transientErr) {
                    err = Math.min(err + 1, BACKOFF_MAX_STEPS);
                    Thread.sleep(err * (long) BACKOFF_STEP_MS);
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread()
                    .interrupt();
        } finally {
            StatusWatchRegistry.unregister(watchType, kcSessionId, tabId, entityIdStr, sink);
            StatusWatchRegistry.markFinished(watchType, kcSessionId, tabId, entityIdStr);
            close(sink);
        }
    }

    private boolean isMatchingEntity(AuthenticationSessionModel as,
                                     String entityIdStr,
                                     Function<AuthenticationSessionModel, String> resolver) {
        if (resolver == null) return true;
        try {
            String current = resolver.apply(as);
            return current == null || current.isBlank() || Objects.equals(current, entityIdStr);
        } catch (Exception ex) {
            return false;
        }
    }

    private AuthenticationSessionModel resolveAuthSession(RootAuthenticationSessionModel root, String tabId) {
        ClientModel kcClient = session.getContext()
                .getClient();
        AuthenticationSessionModel as = (kcClient != null)
                ? root.getAuthenticationSession(kcClient, tabId)
                : null;

        if (as != null) return as;
        for (AuthenticationSessionModel child : root.getAuthenticationSessions()
                .values()) {
            if (Objects.equals(tabId, child.getTabId())) return child;
        }
        return null;
    }

    private static boolean safeSendStatus(SseEventSink sink, Sse sse, int reconnectMs, FlowStatusDto status) {
        try {
            if (sink == null || sink.isClosed()) return false;
            OutboundSseEvent event = sse.newEventBuilder()
                    .mediaType(MediaType.APPLICATION_JSON_TYPE)
                    .reconnectDelay(reconnectMs)
                    .data(StatusResponseDto.class, StatusResponseDto.builder()
                            .status(status)
                            .build())
                    .build();
            sink.send(event);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }


    private static void sendAndCloseError(SseEventSink sink, Sse sse, int reconnectMs) {
        safeSendStatus(sink, sse, reconnectMs, FlowStatusDto.NOT_FOUND);
        close(sink);
    }

    private static void close(SseEventSink sink) {
        try {
            if (sink != null && !sink.isClosed()) sink.close();
        } catch (Exception ignored) {
        }
    }

    private static void closeWatch(String type, String rootId, String tabId, String entityIdStr) {
        StatusWatchRegistry.close(type, rootId, tabId, entityIdStr);
    }
}

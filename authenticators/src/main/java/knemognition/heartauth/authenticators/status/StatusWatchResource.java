package knemognition.heartauth.authenticators.status;

import io.smallrye.common.annotation.Blocking;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import knemognition.heartauth.authenticators.shared.OrchestratorClient;
import knemognition.heartauth.orchestrator.invoker.ApiException;
import knemognition.heartauth.orchestrator.model.StatusResponse;
import org.keycloak.models.*;
import org.keycloak.sessions.*;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;

@Path("")
public class StatusWatchResource {

    private final KeycloakSession session;

    public StatusWatchResource(KeycloakSession session) {
        this.session = session;
    }

    @Blocking
    @GET
    @Path("watch/ecg")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void watchEcg(@QueryParam("root") String rootId,
                         @QueryParam("tab") String tabId,
                         @QueryParam("id") String challengeIdStr,
                         @jakarta.ws.rs.core.Context SseEventSink sink,
                         @jakarta.ws.rs.core.Context Sse sse) {

        watchStatus(rootId, tabId, challengeIdStr, sink, sse,
                (clientApi, kcSessionId) -> {
                    try {
                        return clientApi.getChallengeStatus(UUID.fromString(challengeIdStr), kcSessionId);
                    } catch (ApiException e) {
                        throw new RuntimeException(e);
                    }
                },
                true
        );
    }

    @Blocking
    @GET
    @Path("watch/pairing")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void watchPairing(@QueryParam("root") String rootId,
                             @QueryParam("tab") String tabId,
                             @QueryParam("id") String pairingIdStr,
                             @jakarta.ws.rs.core.Context SseEventSink sink,
                             @jakarta.ws.rs.core.Context Sse sse) {

        watchStatus(rootId, tabId, pairingIdStr, sink, sse,
                (clientApi, kcSessionId) -> {
                    try {
                        return clientApi.getPairingStatus(UUID.fromString(pairingIdStr), kcSessionId);
                    } catch (ApiException e) {
                        throw new RuntimeException(e);
                    }
                },
                true
        );
    }

    private void watchStatus(String rootId,
                             String tabId,
                             String entityIdStr,
                             SseEventSink sink,
                             Sse sse,
                             BiFunction<OrchestratorClient, String, StatusResponse> resolver,
                             boolean stopOnTerminal) {

        RealmModel realm = session.getContext().getRealm();
        RootAuthenticationSessionModel root = session.authenticationSessions()
                .getRootAuthenticationSession(realm, rootId);

        if (root == null) {
            close(sink);
            return;
        }

        AuthenticationSessionModel as = resolveAuthSession(root, tabId);
        if (as == null) {
            close(sink);
            return;
        }

        String base = realmAttr(realm, "status.base-url");
        String apiKey = realmAttr(realm, "status.api-key");
        int timeoutMs = realmAttrInt(realm, "status.timeout-ms", 5000);
        int periodMs = realmAttrInt(realm, "status.min-period-ms", 500);

        if (base == null || entityIdStr == null || entityIdStr.isBlank()) {
            sendAndCloseError(sink, sse, periodMs);
            return;
        }

        final String kcSessionId = root.getId();
        final OrchestratorClient clientApi =
                new OrchestratorClient(base, apiKey, Duration.ofMillis(timeoutMs));

        if (!safeSendStatus(sink, sse, periodMs, "PENDING")) {
            close(sink);
            return;
        }

        int err = 0;
        try {
            while (!sink.isClosed()) {
                try {
                    StatusResponse st = resolver.apply(clientApi, kcSessionId);
                    err = 0;

                    if (!safeSendStatus(sink, sse, periodMs, st.getStatus().name())) {
                        close(sink);
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

                    Thread.sleep(periodMs);
                } catch (Exception transientErr) {
                    err = Math.min(err + 1, 5);
                    Thread.sleep(err * 200L);
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } finally {
            close(sink);
        }
    }

    private AuthenticationSessionModel resolveAuthSession(RootAuthenticationSessionModel root, String tabId) {
        ClientModel kcClient = session.getContext().getClient();
        AuthenticationSessionModel as = (kcClient != null)
                ? root.getAuthenticationSession(kcClient, tabId)
                : null;

        if (as != null) return as;

        for (AuthenticationSessionModel child : root.getAuthenticationSessions().values()) {
            if (Objects.equals(tabId, child.getTabId())) return child;
        }
        return null;
    }


    private static String realmAttr(RealmModel realm, String key) {
        String v = realm.getAttribute(key);
        return (v == null || v.isBlank()) ? null : v;
    }

    private static int realmAttrInt(RealmModel realm, String key, int def) {
        try {
            String v = realm.getAttribute(key);
            return (v == null || v.isBlank()) ? def : Integer.parseInt(v);
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean safeSendStatus(SseEventSink sink, Sse sse, int reconnectMs, String status) {
        try {
            if (sink == null || sink.isClosed()) return false;
            String json = "{\"status\":\"" + escapeJson(status) + "\"}";
            OutboundSseEvent event = sse.newEventBuilder()
                    .mediaType(MediaType.APPLICATION_JSON_TYPE)
                    .reconnectDelay(reconnectMs)
                    .data(String.class, json)
                    .build();
            sink.send(event);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void sendAndCloseError(SseEventSink sink, Sse sse, int reconnectMs) {
        safeSendStatus(sink, sse, reconnectMs, "ERROR");
        close(sink);
    }

    private static void close(SseEventSink sink) {
        try {
            if (sink != null && !sink.isClosed()) sink.close();
        } catch (Exception ignored) {
        }
    }
}

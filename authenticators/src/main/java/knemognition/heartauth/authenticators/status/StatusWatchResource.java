package knemognition.heartauth.authenticators.status;

import io.smallrye.common.annotation.Blocking;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.SseEventSink;
import knemognition.heartauth.authenticators.shared.OrchestratorClient;
import org.keycloak.models.*;
import org.keycloak.sessions.*;

import java.time.Duration;
import java.util.UUID;

public class StatusWatchResource {

    private final KeycloakSession session;

    public StatusWatchResource(KeycloakSession session) {
        this.session = session;
    }

    @Blocking
    @GET
    @Path("watch/ecg")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void watch(@QueryParam("root") String rootId,
                      @QueryParam("tab") String tabId,
                      @QueryParam("id") String challengeIdStr,
                      @jakarta.ws.rs.core.Context jakarta.ws.rs.sse.SseEventSink sink,
                      @jakarta.ws.rs.core.Context jakarta.ws.rs.sse.Sse sse) {

        RealmModel realm = session.getContext().getRealm();
        RootAuthenticationSessionModel root =
                session.authenticationSessions().getRootAuthenticationSession(realm, rootId);
        if (root == null) {
            close(sink);
            return;
        }

        ClientModel kcClient = session.getContext().getClient();
        AuthenticationSessionModel as = (kcClient != null)
                ? root.getAuthenticationSession(kcClient, tabId) : null;
        if (as == null) for (var child : root.getAuthenticationSessions().values())
            if (tabId.equals(child.getTabId())) {
                as = child;
                break;
            }
        if (as == null) {
            close(sink);
            return;
        }

        String base = realmAttr(realm, "status.base-url");
        String apiKey = realmAttr(realm, "status.api-key");
        int timeoutMs = realmAttrInt(realm, "status.timeout-ms", 5000);
        int periodMs = realmAttrInt(realm, "status.min-period-ms", 500);

        if (base == null) {
            safeSend(sink, sse.newEventBuilder()
                    .mediaType(MediaType.APPLICATION_JSON_TYPE)
                    .reconnectDelay(periodMs)
                    .data("{\"status\":\"ERROR\"}")
                    .build());
            close(sink);
            return;
        }

        final String kcSessionId = root.getId();
        final UUID challenge = UUID.fromString(challengeIdStr);
        final OrchestratorClient clientApi =
                new OrchestratorClient(base, apiKey, Duration.ofMillis(timeoutMs));

        OutboundSseEvent pending = sse.newEventBuilder()
                .mediaType(MediaType.APPLICATION_JSON_TYPE)
                .reconnectDelay(periodMs)
                .data("{\"status\":\"PENDING\"}")
                .build();
        if (!safeSend(sink, pending)) {
            close(sink);
            return;
        }

        try {
            int err = 0;
            while (!sink.isClosed()) {
                try {
                    var st = clientApi.getChallengeStatus(challenge, kcSessionId);
                    err = 0;

                    if (!safeSend(sink, sse.newEventBuilder()
                            .mediaType(MediaType.APPLICATION_JSON_TYPE)
                            .reconnectDelay(periodMs)
                            .data("{\"status\":\"" + st.getStatus() + "\"}")
                            .build())) {
                        close(sink);
                        return;
                    }

                    switch (st.getStatus()) {
                        case APPROVED, DENIED, EXPIRED, NOT_FOUND:
                            close(sink);
                            return;
                        case PENDING:
                            Thread.sleep(periodMs);
                    }
                } catch (Exception e) {
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

    @Blocking
    @GET
    @Path("watch/pairing")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void watchPairing(@QueryParam("root") String rootId,
                             @QueryParam("tab") String tabId,
                             @QueryParam("id") String pairingIdStr,
                             @jakarta.ws.rs.core.Context jakarta.ws.rs.sse.SseEventSink sink,
                             @jakarta.ws.rs.core.Context jakarta.ws.rs.sse.Sse sse) {

        RealmModel realm = session.getContext().getRealm();
        RootAuthenticationSessionModel root =
                session.authenticationSessions().getRootAuthenticationSession(realm, rootId);
        if (root == null) {
            close(sink);
            return;
        }

        ClientModel kcClient = session.getContext().getClient();
        AuthenticationSessionModel as = (kcClient != null)
                ? root.getAuthenticationSession(kcClient, tabId) : null;
        if (as == null) {
            for (var child : root.getAuthenticationSessions().values())
                if (tabId.equals(child.getTabId())) {
                    as = child;
                    break;
                }
        }
        if (as == null) {
            close(sink);
            return;
        }

        String base = realmAttr(realm, "status.base-url");
        String apiKey = realmAttr(realm, "status.api-key");
        int timeoutMs = realmAttrInt(realm, "status.timeout-ms", 5000);
        int periodMs = realmAttrInt(realm, "status.min-period-ms", 500);
        if (base == null) {
            safeSend(sink, sse.newEventBuilder()
                    .mediaType(MediaType.APPLICATION_JSON_TYPE)
                    .reconnectDelay(periodMs)
                    .data("{\"status\":\"ERROR\"}")
                    .build());
            close(sink);
            return;
        }

        final OrchestratorClient clientApi =
                new OrchestratorClient(base, apiKey, Duration.ofMillis(timeoutMs));
        final String kcSessionId = root.getId();
        // Initial event
        OutboundSseEvent pending = sse.newEventBuilder()
                .mediaType(MediaType.APPLICATION_JSON_TYPE)
                .reconnectDelay(periodMs)
                .data("{\"status\":\"PENDING\"}")
                .build();
        if (!safeSend(sink, pending)) {
            close(sink);
            return;
        }

        try {
            final UUID pairingId = UUID.fromString(pairingIdStr);
            int err = 0;
            while (!sink.isClosed()) {
                try {
                    var st = clientApi.getPairingStatus(pairingId, kcSessionId);
                    err = 0;

                    if (!safeSend(sink, sse.newEventBuilder()
                            .mediaType(MediaType.APPLICATION_JSON_TYPE)
                            .reconnectDelay(periodMs)
                            .data("{\"status\":\"" + st.getStatus() + "\"}")
                            .build())) {
                        close(sink);
                        return;
                    }

                    // Continue only while pending; any other status is terminal for the stream
                    if ("PENDING".equals(st.getStatus().name())) {
                        Thread.sleep(periodMs);
                    } else {
                        close(sink);
                        return;
                    }
                } catch (Exception e) {
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

    private static boolean safeSend(SseEventSink sink, OutboundSseEvent event) {
        try {
            if (sink == null || sink.isClosed()) return false;
            sink.send(event);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static void close(SseEventSink sink) {
        try {
            if (sink != null && !sink.isClosed()) sink.close();
        } catch (Exception ignored) {
        }
    }
}

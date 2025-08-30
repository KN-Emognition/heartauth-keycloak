package knemognition.heartauth.authenticators.ecg;

import io.smallrye.common.annotation.Blocking;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.SseEventSink;
import knemognition.heartauth.authenticators.shared.OrchestratorClient;
import org.keycloak.models.KeycloakSession;
import org.keycloak.sessions.AuthenticationSessionModel;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;

@Path("/") // final path: /realms/{realm}/ecg/...
public class EcgWatchResource {

    private final KeycloakSession session;
    public EcgWatchResource(KeycloakSession session) { this.session = session; }

    @Blocking
    @GET
    @Path("watch")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void watch(@QueryParam("root") String rootId,
                      @QueryParam("tab") String tabId,
                      @QueryParam("challengeId") String challengeIdStr,
                      @QueryParam("pollMs") @DefaultValue("2000") int pollMs,
                      @Context jakarta.ws.rs.sse.SseEventSink sink,
                      @Context jakarta.ws.rs.sse.Sse sse)  {

        var realm = session.getContext().getRealm();
        var root = session.authenticationSessions().getRootAuthenticationSession(realm, rootId);
        if (root == null) { close(sink); return; }

        var kcClient = session.getContext().getClient();
        AuthenticationSessionModel as = (kcClient != null) ? root.getAuthenticationSession(kcClient, tabId) : null;
        if (as == null) for (var child : root.getAuthenticationSessions().values())
            if (tabId.equals(child.getTabId())) { as = child; break; }
        if (as == null) { close(sink); return; }

        var note = as.getAuthNote("ecg.challengeId");
        if (note == null || !note.equals(challengeIdStr)) { close(sink); return; }

        // Orchestrator config from auth notes
        String base     = valOr(as.getAuthNote("ecg.baseUrl"), "");
        String apiKey   = valOr(as.getAuthNote("ecg.apiKey"), "");
        int timeoutMs   = intOr(as.getAuthNote("ecg.timeoutMs"), 5000);
        int periodMs    = Math.max(500, intOr(valOr(as.getAuthNote("ecg.pollMs"),
                Integer.toString(pollMs)), pollMs));

        if (base.isBlank()) {
            sink.send(sse.newEventBuilder().data("{\"state\":\"ERROR\",\"reason\":\"no base url\"}").build());
            close(sink);
            return;
        }

        // CAPTURE ONLY PRIMITIVES/VALUES — never touch KC models below
        final String kcSessionId = root.getId();
        final UUID challenge = UUID.fromString(challengeIdStr);
        final OrchestratorClient clientApi = new OrchestratorClient(base, apiKey, Duration.ofMillis(timeoutMs));

        OutboundSseEvent pending = sse.newEventBuilder().data("{\"state\":\"PENDING\"}").build();
        if (!safeSend(sink, pending)) { close(sink); return; }
        try {
            int err = 0;
            while (!sink.isClosed()) {
                try {
                    var st = clientApi.getStatus(challenge, kcSessionId);
                    err = 0;
<<<<<<< HEAD
                    if (!safeSend(sink, sse.newEventBuilder().data("{\"state\":\"" + st.getState().name() + "\"}").build())) {
                        close(sink); return;
                    }
                    switch (st.getState()) {
=======
                    if (!safeSend(sink, sse.newEventBuilder().data("{\"status\":\"" + st.getStatus() + "\"}").build())) {
                        close(sink); return;
                    }
                    switch (st.getStatus()) {
>>>>>>> efd00af (feat: add login flow)
                        case APPROVED, DENIED, EXPIRED, NOT_FOUND:
                            close(sink); return;
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
    private static boolean safeSend(SseEventSink sink, OutboundSseEvent event) {
        try {
            if (sink == null || sink.isClosed()) return false;
            sink.send(event);
            return true;
        } catch (IllegalStateException | IllegalArgumentException ex) {
            // response already committed/ended or sink not writable
            return false;
        } catch (Exception ex) {
            return false;
        }
    }


    private static String valOr(String v, String def){ return (v == null || v.isBlank()) ? def : v; }
    private static int intOr(String v, int def){ try { return Integer.parseInt(v); } catch(Exception e){ return def; } }
    private static void close(SseEventSink sink){ try { if (sink!=null && !sink.isClosed()) sink.close(); } catch(Exception ignored){} }
}

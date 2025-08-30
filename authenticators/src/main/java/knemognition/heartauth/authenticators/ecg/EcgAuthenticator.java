package knemognition.heartauth.authenticators.ecg;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import knemognition.heartauth.authenticators.shared.OrchestratorClient;
import knemognition.heartauth.authenticators.status.StatusWatchResourceProviderFactory;
import knemognition.heartauth.orchestrator.invoker.ApiException;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

public class EcgAuthenticator implements Authenticator {
    private static final Logger LOG = Logger.getLogger(EcgAuthenticator.class);

    private static final String NOTE_CHALLENGE_ID = "ecg.challengeId";

    // ---------- realm helpers (realm-only config) ----------
    private static String rAttr(RealmModel realm, String key, String def) {
        String v = realm.getAttribute(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    private static int rAttrInt(RealmModel realm, String key, int def) {
        try {
            String v = realm.getAttribute(key);
            return (v == null || v.isBlank()) ? def : Integer.parseInt(v);
        } catch (Exception e) {
            return def;
        }
    }

    private OrchestratorClient client(AuthenticationFlowContext ctx) {
        RealmModel realm = ctx.getRealm();
        String baseUrl = rAttr(realm, "status.base-url", "");
        String apiKey = rAttr(realm, "status.api-key", "");
        int timeoutMs = rAttrInt(realm, "status.timeout-ms", 5000);
        return new OrchestratorClient(baseUrl, apiKey, Duration.ofMillis(timeoutMs));
    }

    private void render(AuthenticationFlowContext ctx, UUID challengeId) {
        var as = ctx.getAuthenticationSession();

        String watchBase = ctx.getSession().getContext().getUri()
                .getBaseUriBuilder().path("realms").path(ctx.getRealm().getName())
                .path(StatusWatchResourceProviderFactory.ID)                // "status-watch"
                .path("watch").path("ecg")
                .build().toString();
        Response page = ctx.form()
                .setAttribute("id", challengeId.toString())
                .setAttribute("rootAuthSessionId", as.getParentSession().getId())
                .setAttribute("tabId", as.getTabId())
                .setAttribute("watchBase", watchBase)
                .createForm("ecg.ftl");

        ctx.challenge(page);
    }

    private Response json(Object entity, Status status) {
        return Response.status(status)
                .header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(entity)
                .build();
    }

    @Override
    public void authenticate(AuthenticationFlowContext ctx) {
        try {
            AuthenticationSessionModel sess = ctx.getAuthenticationSession();

            String existing = sess.getAuthNote(NOTE_CHALLENGE_ID);
            if (existing != null && !existing.isBlank()) {
                render(ctx, UUID.fromString(existing));
                return;
            }

            UUID userId = UUID.fromString(ctx.getUser().getId());
            // TTL from realm (fallback 120s)
            int ttlSeconds = rAttrInt(ctx.getRealm(), "status.ttl-seconds", 120);

            UUID challengeId = client(ctx).createChallenge(userId, ttlSeconds);
            sess.setAuthNote(NOTE_CHALLENGE_ID, challengeId.toString());

            // We no longer set ecg.* notes; realm attributes are the single source of truth.
            render(ctx, challengeId);

        } catch (ApiException e) {
            LOG.warn("ECG: orchestrator call failed", e);
            ctx.failureChallenge(
                    AuthenticationFlowError.INTERNAL_ERROR,
                    ctx.form().setError("Upstream unavailable")
                            .createErrorPage(Status.SERVICE_UNAVAILABLE)
            );
        } catch (Exception e) {
            LOG.error("ECG: unexpected", e);
            ctx.failureChallenge(
                    AuthenticationFlowError.INTERNAL_ERROR,
                    ctx.form().setError("Unexpected error")
                            .createErrorPage(Status.INTERNAL_SERVER_ERROR)
            );
        }
    }

    @Override
    public void action(AuthenticationFlowContext ctx) {
        var req = ctx.getHttpRequest();
        var params = req.getDecodedFormParameters();

        if (params.containsKey("cancel")) {
            ctx.failure(AuthenticationFlowError.ACCESS_DENIED);
            return;
        }

        if (params.containsKey("finalize")) {
            String idStr = ctx.getAuthenticationSession().getAuthNote(NOTE_CHALLENGE_ID);
            if (idStr == null || idStr.isBlank()) {
                ctx.failureChallenge(AuthenticationFlowError.EXPIRED_CODE,
                        ctx.form().setError("Challenge not found").createErrorPage(Status.UNAUTHORIZED));
                return;
            }

            UUID id = UUID.fromString(idStr);
            try {
                String kcSession = ctx.getAuthenticationSession().getParentSession().getId();
                var st = client(ctx).getChallengeStatus(id, kcSession);

                switch (st.getStatus()) {
                    case APPROVED -> {
                        ctx.success();
                        return;
                    }
                    case DENIED -> {
                        ctx.failureChallenge(
                                AuthenticationFlowError.INVALID_USER,
                                ctx.form().setError("Denied" + (st.getReason() != null ? ": " + st.getReason() : ""))
                                        .createErrorPage(Status.UNAUTHORIZED)
                        );
                        return;
                    }
                    case EXPIRED, NOT_FOUND -> {
                        ctx.failureChallenge(
                                AuthenticationFlowError.EXPIRED_CODE,
                                ctx.form().setError("Challenge expired").createErrorPage(Status.UNAUTHORIZED)
                        );
                        return;
                    }
                    default -> {
                        render(ctx, id);
                        return;
                    }
                }
            } catch (ApiException e) {
                ctx.failureChallenge(
                        AuthenticationFlowError.INTERNAL_ERROR,
                        ctx.form().setError("Upstream unavailable").createErrorPage(Status.SERVICE_UNAVAILABLE)
                );
                return;
            }
        }

        String idStr = ctx.getAuthenticationSession().getAuthNote(NOTE_CHALLENGE_ID);
        if (idStr != null && !idStr.isBlank()) {
            render(ctx, UUID.fromString(idStr));
        } else {
            authenticate(ctx);
        }
    }

    @Override
    public boolean requiresUser() {
        return true;
    }

    @Override
    public boolean configuredFor(KeycloakSession s, RealmModel r, UserModel u) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession s, RealmModel r, UserModel u) {
    }

    @Override
    public void close() {
    }
}

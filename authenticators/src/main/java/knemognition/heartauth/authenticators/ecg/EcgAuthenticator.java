package knemognition.heartauth.authenticators.ecg;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import knemognition.heartauth.authenticators.shared.OrchestratorClient;
import knemognition.heartauth.orchestrator.invoker.ApiException;
import knemognition.heartauth.orchestrator.model.ChallengeStatusResponse;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static knemognition.heartauth.authenticators.ecg.EcgAuthenticatorFactory.*;

public class EcgAuthenticator implements Authenticator {
    private static final Logger LOG = Logger.getLogger(EcgAuthenticator.class);

    private static final String NOTE_CHALLENGE_ID = "ecg.challengeId";

    private static String cfg(Map<String, String> c, String k, String def) {
        var v = (c != null) ? c.get(k) : null;
        return (v == null || v.isBlank()) ? def : v;
    }
    private static int cfgInt(Map<String, String> c, String k, int def) {
        try { return Integer.parseInt(cfg(c, k, Integer.toString(def))); }
        catch (NumberFormatException e) { return def; }
    }

    private OrchestratorClient client(AuthenticationFlowContext ctx) {
        Map<String, String> conf = ctx.getAuthenticatorConfig() != null ? ctx.getAuthenticatorConfig().getConfig() : Map.of();
        String base = cfg(conf, CONF_BASE_URL, "");
        String apiKey = cfg(conf, CONF_API_KEY, "");
        int timeoutMs = cfgInt(conf, CONF_TIMEOUT_MS, 5000);
        return new OrchestratorClient(base, apiKey, Duration.ofMillis(timeoutMs));
    }

    private void render(AuthenticationFlowContext ctx, UUID challengeId) {
        Map<String, String> conf = ctx.getAuthenticatorConfig() != null ? ctx.getAuthenticatorConfig().getConfig() : Map.of();
        int pollMs = cfgInt(conf, CONF_POLL_MS, 2000);
        Response page = ctx.form()
                .setAttribute("challengeId", challengeId.toString())
                .setAttribute("pollMs", pollMs)
                .createForm("ecg.ftl");
        ctx.challenge(page);
    }

    @Override
    public void authenticate(AuthenticationFlowContext ctx) {
        var ac = ctx.getAuthenticatorConfig();
        LOG.debugf("ECG config attached? alias=%s id=%s map=%s",
                ac != null ? ac.getAlias() : "null",
                ac != null ? ac.getId() : "null",
                ac != null ? ac.getConfig() : null);

        try {
            var sess = ctx.getAuthenticationSession();

            // Reuse existing challenge if already created for this auth session
            String existing = sess.getAuthNote(NOTE_CHALLENGE_ID);
            if (existing != null && !existing.isBlank()) {
                UUID challengeId = UUID.fromString(existing);
                LOG.debugf("ECG: reusing existing challengeId=%s", challengeId);
                render(ctx, challengeId);
                return;
            }

            // First render: create challenge ONCE
            UserModel user = ctx.getUser();
            UUID userId = UUID.fromString(user.getId());
            Map<String, String> conf = ac != null ? ac.getConfig() : Map.of();
            int ttlSeconds = cfgInt(conf, CONF_TTL_SECONDS, 120);

            UUID challengeId = client(ctx).createChallenge(userId, ttlSeconds);
            sess.setAuthNote(NOTE_CHALLENGE_ID, challengeId.toString());
            LOG.debugf("ECG: created challengeId=%s for user=%s", challengeId, userId);

            render(ctx, challengeId);

        } catch (ApiException e) {
            LOG.warn("ECG: orchestrator call failed", e);
            ctx.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR,
                    ctx.form().setError("Upstream unavailable").createErrorPage(Status.SERVICE_UNAVAILABLE));
        } catch (Exception e) {
            LOG.error("ECG: unexpected", e);
            ctx.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR,
                    ctx.form().setError("Unexpected error").createErrorPage(Status.INTERNAL_SERVER_ERROR));
        }
    }

    @Override
    public void action(AuthenticationFlowContext ctx) {
        var params = ctx.getHttpRequest().getDecodedFormParameters();

        if (params.containsKey("cancel")) {
            ctx.failure(AuthenticationFlowError.ACCESS_DENIED);
            return;
        }

        // Polling path: NEVER create here, only read the stored challengeId
        if (params.containsKey("poll")) {
            String idStr = ctx.getAuthenticationSession().getAuthNote(NOTE_CHALLENGE_ID);
            if (idStr == null || idStr.isBlank()) {
                // Do NOT create a new one here; surface a clean error or re-render without creating
                LOG.warn("ECG: poll requested but no challengeId in session");
                ctx.failureChallenge(AuthenticationFlowError.EXPIRED_CODE,
                        ctx.form().setError("Challenge not found").createErrorPage(Status.UNAUTHORIZED));
                return;
            }

            UUID id = UUID.fromString(idStr);
            try {
                String kcSession = ctx.getAuthenticationSession().getParentSession().getId();
                ChallengeStatusResponse st = client(ctx).getStatus(id, kcSession);

                switch (st.getState()) {
                    case APPROVED -> { ctx.success(); return; }
                    case DENIED -> {
                        ctx.failureChallenge(AuthenticationFlowError.INVALID_USER,
                                ctx.form().setError("Denied" + (st.getReason() != null ? ": " + st.getReason() : ""))
                                        .createErrorPage(Status.UNAUTHORIZED));
                        return;
                    }
                    case EXPIRED, NOT_FOUND -> {
                        ctx.failureChallenge(AuthenticationFlowError.EXPIRED_CODE,
                                ctx.form().setError("Challenge expired").createErrorPage(Status.UNAUTHORIZED));
                        return;
                    }
                    default -> { // PENDING
                        render(ctx, id);
                        return;
                    }
                }
            } catch (ApiException e) {
                LOG.warnf(e, "ECG: polling failed for id=%s", id);
                ctx.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR,
                        ctx.form().setError("Upstream unavailable").createErrorPage(Status.SERVICE_UNAVAILABLE));
            }
            return;
        }

        // Any other action => just re-show the same page, DO NOT create a new challenge
        String idStr = ctx.getAuthenticationSession().getAuthNote(NOTE_CHALLENGE_ID);
        if (idStr != null && !idStr.isBlank()) {
            render(ctx, UUID.fromString(idStr));
        } else {
            // First-time entry without poll/cancel: fall back to normal authenticate() (will create once)
            authenticate(ctx);
        }
    }

    @Override public boolean requiresUser() { return true; }
    @Override public boolean configuredFor(KeycloakSession s, RealmModel r, UserModel u) { return true; }
    @Override public void setRequiredActions(KeycloakSession s, RealmModel r, UserModel u) { }
    @Override public void close() { }
}

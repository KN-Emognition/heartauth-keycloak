package knemognition.heartauth.authenticators.ecg;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import knemognition.heartauth.authenticators.shared.OrchestratorClient;
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

import static knemognition.heartauth.authenticators.ecg.EcgAuthenticatorFactory.*;

public class EcgAuthenticator implements Authenticator {
    private static final Logger LOG = Logger.getLogger(EcgAuthenticator.class);

    private static final String NOTE_CHALLENGE_ID = "ecg.challengeId";

    private static String cfg(Map<String, String> c, String k, String def) {
        var v = (c != null) ? c.get(k) : null;
        return (v == null || v.isBlank()) ? def : v;
    }

    private static int cfgInt(Map<String, String> c, String k, int def) {
        try {
            return Integer.parseInt(cfg(c, k, Integer.toString(def)));
        } catch (NumberFormatException e) {
            return def;
        }
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

        var as = ctx.getAuthenticationSession();
        String rootId = as.getParentSession().getId();
        String tabId = as.getTabId();
        String realmName = ctx.getRealm().getName();
        String base = ctx.getSession().getContext().getUri().getBaseUri().toString(); // ends with "/"
        if (!base.endsWith("/")) base = base + "/";
        String watchBase = base + "realms/" + realmName + "/ecg";

        Response page = ctx.form()
                .setAttribute("challengeId", challengeId.toString())
                .setAttribute("pollMs", pollMs)
                .setAttribute("rootAuthSessionId", rootId)
                .setAttribute("tabId", tabId)
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

    @SuppressWarnings("unused")
    private Response jsonOk(Object entity) {
        return json(entity, Status.OK);
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

            Map<String, String> conf = ctx.getAuthenticatorConfig() != null ? ctx.getAuthenticatorConfig().getConfig() : Map.of();
            int ttlSeconds = cfgInt(conf, CONF_TTL_SECONDS, 120);

            UUID challengeId = client(ctx).createChallenge(userId, ttlSeconds);
            sess.setAuthNote(NOTE_CHALLENGE_ID, challengeId.toString());

            String base = cfg(conf, EcgAuthenticatorFactory.CONF_BASE_URL, "");
            String apiKey = cfg(conf, EcgAuthenticatorFactory.CONF_API_KEY, "");
            int timeoutMs = cfgInt(conf, EcgAuthenticatorFactory.CONF_TIMEOUT_MS, 5000);
            int pollMs = cfgInt(conf, EcgAuthenticatorFactory.CONF_POLL_MS, 2000);

            sess.setAuthNote("ecg.baseUrl", base);
            sess.setAuthNote("ecg.apiKey", apiKey);
            sess.setAuthNote("ecg.timeoutMs", Integer.toString(timeoutMs));
            sess.setAuthNote("ecg.pollMs", Integer.toString(pollMs));
            // keep your existing:
            sess.setAuthNote("ecg.challengeId", challengeId.toString());
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
        var req = ctx.getHttpRequest();
        var params = req.getDecodedFormParameters();

        LOG.debugf("ECG action(): method=%s, finalize=%s, cancel=%s, url=%s",
                req.getHttpMethod(),
                (params != null && params.containsKey("finalize")),
                (params != null && params.containsKey("cancel")),
                req.getUri().getRequestUri().toString());

        if (params.containsKey("cancel")) {
            ctx.failure(AuthenticationFlowError.ACCESS_DENIED);
            return;
        }

        // === New: finalize branch ===
        // Triggered once by the page when SSE says APPROVED / DENIED / EXPIRED / NOT_FOUND
        if (params.containsKey("finalize")) {
            String idStr = ctx.getAuthenticationSession().getAuthNote(NOTE_CHALLENGE_ID);
            if (idStr == null || idStr.isBlank()) {
                LOG.warn("ECG: finalize requested but no challengeId in session");
                ctx.failureChallenge(AuthenticationFlowError.EXPIRED_CODE,
                        ctx.form().setError("Challenge not found").createErrorPage(Status.UNAUTHORIZED));
                return;
            }

            UUID id = UUID.fromString(idStr);
            try {
                String kcSession = ctx.getAuthenticationSession().getParentSession().getId(); // root session id
                var st = client(ctx).getStatus(id, kcSession);

                switch (st.getStatus()) {
                    case APPROVED -> {
                        ctx.success();
                        return;
                    }
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
                    default -> {
                        // Still pending (race): re-render the page so SSE continues to watch.
                        render(ctx, id);
                        return;
                    }
                }

            } catch (ApiException e) {
                LOG.warnf(e, "ECG: finalize status fetch failed for id=%s", id);
                ctx.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR,
                        ctx.form().setError("Upstream unavailable").createErrorPage(Status.SERVICE_UNAVAILABLE));
                return;
            }
        }

        // Any other action => re-render without creating a new challenge
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
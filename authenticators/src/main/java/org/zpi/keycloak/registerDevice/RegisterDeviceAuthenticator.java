package org.zpi.keycloak.registerDevice;

import jakarta.ws.rs.core.Response;           // keep
import jakarta.ws.rs.core.Response.Status;     // <-- add this
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

public class RegisterDeviceAuthenticator implements Authenticator {
    private static final Logger LOG = Logger.getLogger(RegisterDeviceAuthenticator.class);

    @Override
    public void authenticate(AuthenticationFlowContext ctx) {
        try {
            String authSessionId = ctx.getAuthenticationSession().getParentSession().getId();
            LOG.infof("authenticate(): realm=%s client=%s user=%s sessionId=%s",
                    ctx.getRealm().getName(),
                    ctx.getAuthenticationSession().getClient().getClientId(),
                    ctx.getUser() != null ? ctx.getUser().getUsername() : "null",
                    authSessionId);

            String payload = "login:" + authSessionId;
            byte[] png = QrUtils.pngFor(payload, 300);
            String dataUri = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(png);

            Response challenge = ctx.form()
                    .setAttribute("qr", dataUri)
                    .setAttribute("sessionId", authSessionId)
                    .createForm("registerDevice.ftl");

            ctx.challenge(challenge);
        } catch (Exception e) {
            LOG.error("authenticate(): failed to render QR page", e);
            ctx.failureChallenge(
                    AuthenticationFlowError.INTERNAL_ERROR,
                    ctx.form()
                            .setError("Unexpected error while rendering QR page.")
                            .createErrorPage(Status.INTERNAL_SERVER_ERROR)   // <-- pass Status
            );
        }

    }

    @Override
    public void action(AuthenticationFlowContext ctx) {
        try {
            var form = ctx.getHttpRequest().getDecodedFormParameters();
            LOG.infof("action(): params=%s user=%s",
                    form, ctx.getUser() != null ? ctx.getUser().getUsername() : "null");

            if (form.containsKey("cancel")) {
                LOG.info("action(): user canceled");
                ctx.failure(AuthenticationFlowError.ACCESS_DENIED);
                return;
            }

            String authSessionId = form.getFirst("session");
            // action(): missing session id
            if (authSessionId == null || authSessionId.isBlank()) {
                LOG.warn("action(): missing session id in form");
                ctx.failureChallenge(
                        AuthenticationFlowError.INVALID_CLIENT_SESSION,
                        ctx.form()
                                .setError("Missing session information.")
                                .createErrorPage(Status.BAD_REQUEST)
                );
                return;
            }


            boolean approved = checkOutOfBandApproval(authSessionId, ctx);
            LOG.infof("action(): approved=%s", approved);

            if (approved) {
                UserModel user = resolveUserFromApproval(authSessionId, ctx);
                LOG.infof("action(): resolved user=%s", user != null ? user.getUsername() : "null");
                if (user != null) ctx.setUser(user);
                ctx.success();
                return;
            }

            LOG.info("action(): not approved yet -> re-render authenticate()");
            authenticate(ctx);
        } catch (Exception e) {
            LOG.error("action(): unexpected error", e);
            ctx.failureChallenge(
                    AuthenticationFlowError.INTERNAL_ERROR,
                    ctx.form()
                            .setError("Unexpected error while rendering QR page.")
                            .createErrorPage(Status.INTERNAL_SERVER_ERROR)   // <-- pass Status here
            );
        }
    }

    private boolean checkOutOfBandApproval(String authSessionId, AuthenticationFlowContext ctx) {
        LOG.debugf("checkOutOfBandApproval(): %s", authSessionId);
        return false;
    }

    private UserModel resolveUserFromApproval(String authSessionId, AuthenticationFlowContext ctx) {
        LOG.debugf("resolveUserFromApproval(): %s", authSessionId);
        return ctx.getUser(); // or look up based on approval payload
    }

    @Override public boolean requiresUser() { return false; }
    @Override public boolean configuredFor(KeycloakSession s, RealmModel r, UserModel u) { return u != null && false; }
    @Override public void setRequiredActions(KeycloakSession s, RealmModel r, UserModel u) {}
    @Override public void close() {}
}

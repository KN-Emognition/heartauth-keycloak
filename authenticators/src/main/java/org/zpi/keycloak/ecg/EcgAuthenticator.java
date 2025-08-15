package org.zpi.keycloak.ecg;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

public class EcgAuthenticator implements Authenticator {
    private static final Logger LOG = Logger.getLogger(EcgAuthenticator.class);

    @Override
    public void authenticate(AuthenticationFlowContext ctx) {
        try {
            LOG.infof("ECG step: render page (realm=%s, client=%s, user=%s)",
                    ctx.getRealm().getName(),
                    ctx.getAuthenticationSession().getClient().getClientId(),
                    ctx.getUser() != null ? ctx.getUser().getUsername() : "null");

            Response challenge = ctx.form().createForm("ecg.ftl");
            ctx.challenge(challenge);
        } catch (Exception e) {
            LOG.error("ECG step: failed to render page", e);
            ctx.failureChallenge(
                    AuthenticationFlowError.INTERNAL_ERROR,
                    ctx.form().setError("Unexpected error").createErrorPage(Status.INTERNAL_SERVER_ERROR)
            );
        }
    }

    @Override
    public void action(AuthenticationFlowContext ctx) {
        var form = ctx.getHttpRequest().getDecodedFormParameters();
        LOG.infof("ECG step: action params=%s", form);

        if (form.containsKey("cancel")) {
            ctx.failure(AuthenticationFlowError.ACCESS_DENIED);
            return;
        }

        if ("1".equals(form.getFirst("confirm"))) {
            LOG.info("ECG step: confirmed -> success()");
            ctx.success();
            return;
        }
        authenticate(ctx);
    }

    @Override public boolean requiresUser() { return false; } // safe in both login/registration
    @Override public boolean configuredFor(KeycloakSession s, RealmModel r, UserModel u) { return true; }
    @Override public void setRequiredActions(KeycloakSession s, RealmModel r, UserModel u) {}
    @Override public void close() {}
}
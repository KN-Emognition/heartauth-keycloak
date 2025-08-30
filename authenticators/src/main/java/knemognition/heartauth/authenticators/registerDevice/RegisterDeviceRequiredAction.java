package knemognition.heartauth.authenticators.registerDevice;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.UserModel;
import org.keycloak.provider.ServerInfoAwareProviderFactory;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;

public class RegisterDeviceRequiredAction
        implements RequiredActionProvider, RequiredActionFactory, ServerInfoAwareProviderFactory {

    private static final Logger LOG = Logger.getLogger(RegisterDeviceRequiredAction.class);

    public static final String ID = "register-device";
    private static final String AUTH_NOTE_JTI = "ecg.pair.jti";

    @Override public String getId() { return ID; }
    @Override public String getDisplayText() { return "Register device"; }
    @Override public RequiredActionProvider create(KeycloakSession session) { return this; }

    @Override
    public void init(Config.Scope scope) {

    }

    @Override
    public void postInit(KeycloakSessionFactory keycloakSessionFactory) {

    }

    @Override public void close() {}

    // Optional: expose empty config in Admin Console

    /** Decide when to force this action for the user (only if no device yet). */
    @Override
    public void evaluateTriggers(RequiredActionContext ctx) {
        UserModel user = ctx.getUser();
        if (!isDeviceRegistered(user)) {
            user.addRequiredAction(ID);
        } else {
            user.removeRequiredAction(ID);
        }
    }

    /** Initial page render (your QR screen). */
    @Override
    public void requiredActionChallenge(RequiredActionContext ctx) {
        try {
            String authSessionId = ctx.getAuthenticationSession().getParentSession().getId();

            String jti = UUID.randomUUID().toString();
            ctx.getAuthenticationSession().setAuthNote(AUTH_NOTE_JTI, jti);

            String pairingJwt = JwtUtils.mintPairingJwt(
                    ctx.getSession(),
                    ctx.getRealm(),
                    jti,
                    "ecg-mobile-app",
                    ctx.getUser() != null ? ctx.getUser().getId() : null,
                    RegisterDeviceConfig.from((AuthenticationFlowContext) ctx).tokenTtl()
            );
            LOG.infof("JWT in QR: %s", pairingJwt);


            Response challenge = ctx.form()
                    .setAttribute("qr", "token")
                    .setAttribute("sessionId", authSessionId)
                    .createForm("registerDevice.ftl");

            ctx.challenge(challenge);
        } catch (Exception e) {
            LOG.error("requiredActionChallenge(): failed to render QR page", e);
            ctx.challenge(
                    ctx.form()
                            .setError("Unexpected error while rendering QR page.")
                            .createErrorPage(Status.INTERNAL_SERVER_ERROR)
            );
        }
    }

    /** Handles the form POST from your page. */
    @Override
    public void processAction(RequiredActionContext ctx) {
        var form = ctx.getHttpRequest().getDecodedFormParameters();
        LOG.infof("processAction(): params=%s user=%s", form, ctx.getUser() != null ? ctx.getUser().getUsername() : "null");

        if (form.containsKey("cancel")) {
            LOG.info("processAction(): user canceled");
            ctx.getUser().removeRequiredAction(ID); // optional
            ctx.ignore();
            return;
        }

        if ("1".equals(form.getFirst("confirm"))) {
            LOG.info("processAction(): confirmed by user");
            // You may mark the device as registered here, e.g., set a user attribute/credential
            markDeviceRegistered(ctx.getUser());
            ctx.success();
            return;
        }

        // Polling path (SSE/JS checks pairing state):
        String authSessionId = form.getFirst("session");
        if (authSessionId == null || authSessionId.isBlank()) {
            LOG.warn("processAction(): missing session id in form");
            ctx.challenge(
                    ctx.form().setError("Missing session.").createErrorPage(Status.BAD_REQUEST)
            );
            return;
        }

        String jti = ctx.getAuthenticationSession().getAuthNote(AUTH_NOTE_JTI);
        if (jti == null || jti.isBlank()) {
            LOG.warn("processAction(): missing jti in auth notes");
            ctx.challenge(
                    ctx.form().setError("Missing token.").createErrorPage(Status.INTERNAL_SERVER_ERROR)
            );
            return;
        }

        // If not yet linked, just show the page again (keeps the RA step active)
        LOG.info("processAction(): not approved yet -> re-render");
        requiredActionChallenge(ctx);
    }

    /* ==== helpers – replace with your storage/logic ==== */

    private boolean isDeviceRegistered(UserModel user) {
        // Example: check a user attribute/credential that you set after success
        return "true".equals(user.getFirstAttribute("hauthDeviceRegistered"));
    }

    private void markDeviceRegistered(UserModel user) {
        user.setSingleAttribute("hauthDeviceRegistered", "true");
        // or create a custom credential type if you want something stronger
    }

    @Override
    public Map<String, String> getOperationalInfo() {
        return Map.of();
    }
}

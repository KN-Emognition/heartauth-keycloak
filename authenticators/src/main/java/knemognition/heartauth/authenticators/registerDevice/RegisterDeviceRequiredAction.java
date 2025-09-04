package knemognition.heartauth.authenticators.registerDevice;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import knemognition.heartauth.authenticators.shared.OrchestratorClient;
import knemognition.heartauth.authenticators.status.StatusWatchResourceProviderFactory;
import knemognition.heartauth.orchestrator.model.PairingCreateResponse;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.ServerInfoAwareProviderFactory;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.util.Map;
import java.util.UUID;

import static knemognition.heartauth.authenticators.shared.OrchestratorClient.client;

public class RegisterDeviceRequiredAction
        implements RequiredActionProvider, RequiredActionFactory, ServerInfoAwareProviderFactory {

    private static final Logger LOG = Logger.getLogger(RegisterDeviceRequiredAction.class);

    public static final String ID = "register-device";
    private static final String JTI = "ecg.pair.jti";
    private static final String JWT = "ecg.pair.jwt";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayText() {
        return "Register device";
    }

    @Override
    public RequiredActionProvider create(KeycloakSession session) {
        return this;
    }

    public void init(Config.Scope scope) {
    }

    @Override
    public void postInit(KeycloakSessionFactory keycloakSessionFactory) {
    }

    @Override
    public void close() {
    }

    @Override
    public void evaluateTriggers(RequiredActionContext ctx) {
        UserModel user = ctx.getUser();
        if (!isDeviceRegistered(user)) {
            user.addRequiredAction(ID);
        } else {
            user.removeRequiredAction(ID);
        }
    }

    private void render(RequiredActionContext ctx) {
        var as = ctx.getAuthenticationSession();
        String watchBase = ctx.getSession().getContext().getUri()
                .getBaseUriBuilder()
                .path("realms")
                .path(ctx.getRealm().getName())
                .path(StatusWatchResourceProviderFactory.ID)
                .path("watch")
                .path("pairing")
                .build()
                .toString();

        Response challenge = ctx.form()
                .setAttribute("qr", RegisterDeviceRequiredAction.JTI)
                .setAttribute("id", RegisterDeviceRequiredAction.JWT)
                .setAttribute("rootAuthSessionId", as.getParentSession().getId())
                .setAttribute("tabId", as.getTabId())
                .setAttribute("watchBase", watchBase)
                .createForm("registerDevice.ftl");

        ctx.challenge(challenge);
    }


    @Override
    public void requiredActionChallenge(RequiredActionContext ctx) {
        try {
            AuthenticationSessionModel sess = ctx.getAuthenticationSession();


            String existing = sess.getAuthNote(JTI);
            if (existing != null && !existing.isBlank()) {
                render(ctx);
                return;
            }

            OrchestratorClient client = client(ctx.getRealm());
            UUID userId = UUID.fromString(ctx.getUser().getId());
            PairingCreateResponse res = client.createPairing(userId);
            sess.setAuthNote(JTI, res.getJti().toString());
            sess.setAuthNote(JWT, res.getJwt());

            render(ctx);

        } catch (IllegalArgumentException iae) {
            LOG.error("RegisterDevice: missing/invalid realm attributes for JWT", iae);
            ctx.challenge(
                    ctx.form().setError("Pairing is not configured. Missing secret.")
                            .createErrorPage(Status.INTERNAL_SERVER_ERROR)
            );
        } catch (Exception e) {
            LOG.error("RegisterDevice: failed to render QR", e);
            ctx.challenge(
                    ctx.form().setError("Unexpected error while rendering QR.")
                            .createErrorPage(Status.INTERNAL_SERVER_ERROR)
            );
        }
    }

    @Override
    public void processAction(RequiredActionContext ctx) {
        var form = ctx.getHttpRequest().getDecodedFormParameters();
        LOG.infof("processAction(): params=%s user=%s", form, ctx.getUser() != null ? ctx.getUser().getUsername() : "null");
        if (form.containsKey("cancel")) {
            LOG.info("processAction(): user canceled");
            ctx.getUser().removeRequiredAction(ID);
            ctx.ignore();
            return;
        }
        if ("1".equals(form.getFirst("confirm"))) {
            LOG.info("processAction(): confirmed by user");
            markDeviceRegistered(ctx.getUser());
            ctx.success();
            return;
        }
        String authSessionId = form.getFirst("session");
        if (authSessionId == null || authSessionId.isBlank()) {
            LOG.warn("processAction(): missing session id in form");
            ctx.challenge(
                    ctx.form().setError("Missing session.").createErrorPage(Status.BAD_REQUEST)
            );
            return;
        }
        String jti = ctx.getAuthenticationSession().getAuthNote(JTI);
        if (jti == null || jti.isBlank()) {
            LOG.warn("processAction(): missing jti in auth notes");
            ctx.challenge(
                    ctx.form().setError("Missing token.").createErrorPage(Status.INTERNAL_SERVER_ERROR)
            );
            return;
        }
        LOG.info("processAction(): not approved yet -> re-render");
        requiredActionChallenge(ctx);
    }


    private boolean isDeviceRegistered(UserModel user) {
        return "true".equals(user.getFirstAttribute("hauthDeviceRegistered"));
    }

    private void markDeviceRegistered(UserModel user) {
        user.setSingleAttribute("hauthDeviceRegistered", "true");
    }

    @Override
    public Map<String, String> getOperationalInfo() {
        return Map.of();
    }
}

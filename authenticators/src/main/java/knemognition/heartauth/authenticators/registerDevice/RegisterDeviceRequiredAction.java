package knemognition.heartauth.authenticators.registerDevice;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import knemognition.heartauth.authenticators.shared.OrchestratorClient;
import knemognition.heartauth.authenticators.status.StatusWatchResourceProviderFactory;
import knemognition.heartauth.orchestrator.model.PairingCreateResponse;
import knemognition.heartauth.orchestrator.invoker.ApiException;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.*;
import org.keycloak.provider.ServerInfoAwareProviderFactory;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import static knemognition.heartauth.authenticators.shared.OrchestratorClient.client;

public class RegisterDeviceRequiredAction
        implements RequiredActionProvider, RequiredActionFactory, ServerInfoAwareProviderFactory {

    private static final Logger LOG = Logger.getLogger(RegisterDeviceRequiredAction.class);

    public static final String ID = "register-device";
    private static final String JTI = "ecg.pair.jti";
    private static final String JWT = "ecg.pair.jwt";

    @Override public String getId() { return ID; }
    @Override public String getDisplayText() { return "Register device"; }
    @Override public RequiredActionProvider create(KeycloakSession session) { return this; }
    @Override public void init(Config.Scope scope) { }
    @Override public void postInit(KeycloakSessionFactory keycloakSessionFactory) { }
    @Override public void close() { }

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
        AuthenticationSessionModel as = ctx.getAuthenticationSession();

        String jti = as.getAuthNote(JTI); // UUID string
        String jwt = as.getAuthNote(JWT);

        URI watchBase = ctx.getSession().getContext().getUri()
                .getBaseUriBuilder()
                .path("realms")
                .path(ctx.getRealm().getName())
                .path(StatusWatchResourceProviderFactory.ID)
                .path("watch")
                .path("pairing")
                .build();

        Response challenge = ctx.form()
                .setAttribute("qr", jwt)
                .setAttribute("id", jti)
                .setAttribute("rootAuthSessionId", as.getParentSession().getId())
                .setAttribute("tabId", as.getTabId())
                .setAttribute("watchBase", watchBase.toString())
                .createForm("registerDevice.ftl");

        ctx.challenge(challenge);
    }

    private static void clearNotes(AuthenticationSessionModel s) {
        s.removeAuthNote(JTI);
        s.removeAuthNote(JWT);
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext ctx) {
        try {
            AuthenticationSessionModel sess = ctx.getAuthenticationSession();

            String existing = sess.getAuthNote(JTI);
            if (existing != null && !existing.isBlank() && sess.getAuthNote(JWT) != null) {
                render(ctx);
                return;
            }

            OrchestratorClient oc = client(ctx.getRealm());
            UUID userId = UUID.fromString(ctx.getUser().getId());
            PairingCreateResponse res = oc.createPairing(userId);

            sess.setAuthNote(JTI, res.getJti().toString());
            sess.setAuthNote(JWT, res.getJwt());

            render(ctx);

        } catch (IllegalArgumentException iae) {
            LOG.error("RegisterDevice: missing/invalid realm attributes for JWT", iae);
            ctx.challenge(
                    ctx.form().setError("Pairing is not configured. Missing secret.")
                            .createErrorPage(Status.INTERNAL_SERVER_ERROR)
            );
        } catch (ApiException e) {
            LOG.warn("RegisterDevice: orchestrator unavailable", e);
            ctx.challenge(
                    ctx.form().setError("Upstream unavailable.")
                            .createErrorPage(Status.SERVICE_UNAVAILABLE)
            );
        } catch (Exception e) {
            LOG.error("RegisterDevice: unexpected error", e);
            ctx.challenge(
                    ctx.form().setError("Unexpected error while rendering QR.")
                            .createErrorPage(Status.INTERNAL_SERVER_ERROR)
            );
        }
    }

    @Override
    public void processAction(RequiredActionContext ctx) {
        // No clickable actions expected. The page will POST here automatically on terminal statuses.
        AuthenticationSessionModel as = ctx.getAuthenticationSession();
        String jtiStr = as.getAuthNote(JTI);
        if (jtiStr == null || jtiStr.isBlank()) {
            ctx.challenge(
                    ctx.form().setError("Missing token.").createErrorPage(Status.INTERNAL_SERVER_ERROR)
            );
            return;
        }

        try {
            OrchestratorClient oc = client(ctx.getRealm());
            String kcSessionId = as.getParentSession().getId();
            var st = oc.getPairingStatus(UUID.fromString(jtiStr), kcSessionId);

            switch (st.getStatus()) {
                case APPROVED -> {
                    markDeviceRegistered(ctx.getUser());
                    clearNotes(as);
                    ctx.success();
                }
                case DENIED -> {
                    clearNotes(as);
                    ctx.challenge(
                            ctx.form()
                                    .setError("Denied" + (st.getReason() != null ? ": " + st.getReason() : ""))
                                    .createErrorPage(Status.UNAUTHORIZED)
                    );
                }
                case EXPIRED, NOT_FOUND -> {
                    clearNotes(as);
                    ctx.challenge(
                            ctx.form().setError("Pairing expired.").createErrorPage(Status.UNAUTHORIZED)
                    );
                }
                case PENDING, CREATED -> {
                    // Not terminal yet → re-render the QR + keep streaming
                    render(ctx);
                }
            }

        } catch (ApiException e) {
            ctx.challenge(
                    ctx.form().setError("Upstream unavailable.").createErrorPage(Status.SERVICE_UNAVAILABLE)
            );
        } catch (Exception e) {
            LOG.error("RegisterDevice: unexpected in processAction()", e);
            ctx.challenge(
                    ctx.form().setError("Unexpected error.").createErrorPage(Status.INTERNAL_SERVER_ERROR)
            );
        }
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

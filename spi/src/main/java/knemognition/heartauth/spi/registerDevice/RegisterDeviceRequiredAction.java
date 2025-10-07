package knemognition.heartauth.spi.registerDevice;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import knemognition.heartauth.orchestrator.ApiException;
import knemognition.heartauth.orchestrator.model.CreatePairingResponseDto;
import knemognition.heartauth.spi.gateway.OrchClient;
import knemognition.heartauth.spi.status.StatusWatchResourceProviderFactory;
import org.jboss.logging.Logger;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.net.URI;
import java.util.UUID;

public class RegisterDeviceRequiredAction implements RequiredActionProvider {

    private static final Logger LOG = Logger.getLogger(RegisterDeviceRequiredAction.class);

    private static final String JTI = "ecg.pair.jti";
    private static final String JWT = "ecg.pair.jwt";

    private static final String REG_PENDING = "hauthRegistrationPending";
    private static final String DEV_REGISTERED = "hauthDeviceRegistered";

    @Override
    public void evaluateTriggers(RequiredActionContext ctx) {
        UserModel user = ctx.getUser();
        if (!isDeviceRegistered(user)) {
            user.addRequiredAction(RegisterDeviceRequiredActionFactory.ID);
        } else {
            user.removeRequiredAction(RegisterDeviceRequiredActionFactory.ID);
        }
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext ctx) {
        try {
            AuthenticationSessionModel sess = ctx.getAuthenticationSession();

            String existingJti = sess.getAuthNote(JTI);
            String existingJwt = sess.getAuthNote(JWT);
            if (existingJti != null && !existingJti.isBlank() && existingJwt != null) {
                render(ctx);
                return;
            }

            OrchClient oc = OrchClient.clientFromRealm(ctx.getRealm());
            UUID userId = UUID.fromString(ctx.getUser()
                    .getId());

            CreatePairingResponseDto res = oc.createPairing(userId);
            sess.setAuthNote(JTI, res.getJti()
                    .toString());
            sess.setAuthNote(JWT, res.getJwt());

            render(ctx);

        } catch (IllegalArgumentException iae) {
            LOG.error("RegisterDevice: missing/invalid realm attributes for JWT", iae);
            ctx.challenge(
                    ctx.form()
                            .setError("Pairing is not configured. Missing secret.")
                            .createErrorPage(Status.INTERNAL_SERVER_ERROR)
            );
        } catch (ApiException e) {
            LOG.warn("RegisterDevice: orchestrator unavailable", e);
            ctx.challenge(
                    ctx.form()
                            .setError("Upstream unavailable.")
                            .createErrorPage(Status.SERVICE_UNAVAILABLE)
            );
        } catch (Exception e) {
            LOG.error("RegisterDevice: unexpected error", e);
            ctx.challenge(
                    ctx.form()
                            .setError("Unexpected error while rendering QR.")
                            .createErrorPage(Status.INTERNAL_SERVER_ERROR)
            );
        }
    }

    @Override
    public void processAction(RequiredActionContext ctx) {
        AuthenticationSessionModel as = ctx.getAuthenticationSession();
        String jtiStr = as.getAuthNote(JTI);
        if (jtiStr == null || jtiStr.isBlank()) {
            ctx.challenge(ctx.form()
                    .setError("Missing token.")
                    .createErrorPage(Status.INTERNAL_SERVER_ERROR));
            return;
        }

        try {
            OrchClient oc = OrchClient.clientFromRealm(ctx.getRealm());
            var st = oc.getPairingStatus(UUID.fromString(jtiStr));

            switch (st.getStatus()) {
                case APPROVED -> {
                    markDeviceRegistered(ctx.getUser());
                    clearPending(ctx.getUser());
                    clearNotes(ctx.getAuthenticationSession());
                    ctx.success();
                }
                case DENIED, EXPIRED, NOT_FOUND -> {
                    clearNotes(ctx.getAuthenticationSession());
                    if (isPendingRegistration(ctx.getUser())) {
                        deleteUser(ctx);
                        ctx.challenge(
                                ctx.form()
                                        .setError("Registration failed: device not registered.")
                                        .createErrorPage(Status.UNAUTHORIZED)
                        );
                    } else {
                        ctx.challenge(
                                ctx.form()
                                        .setError("Device registration failed.")
                                        .createErrorPage(Status.UNAUTHORIZED)
                        );
                    }
                }
                case PENDING, CREATED -> render(ctx);
            }
        } catch (ApiException e) {
            ctx.challenge(
                    ctx.form()
                            .setError("Upstream unavailable.")
                            .createErrorPage(Status.SERVICE_UNAVAILABLE)
            );
        } catch (Exception e) {
            LOG.error("RegisterDevice: unexpected in processAction()", e);
            ctx.challenge(
                    ctx.form()
                            .setError("Unexpected error.")
                            .createErrorPage(Status.INTERNAL_SERVER_ERROR)
            );
        }
    }

    @Override
    public void close() {
    }

    private void render(RequiredActionContext ctx) {
        AuthenticationSessionModel as = ctx.getAuthenticationSession();

        String jti = as.getAuthNote(JTI);
        String jwt = as.getAuthNote(JWT);

        URI watchBase = ctx.getSession()
                .getContext()
                .getUri()
                .getBaseUriBuilder()
                .path("realms")
                .path(ctx.getRealm()
                        .getName())
                .path(StatusWatchResourceProviderFactory.ID)
                .path("watch")
                .path("pairing")
                .build();

        Response page = ctx.form()
                .setAttribute("qr", jwt)
                .setAttribute("id", jti)
                .setAttribute("rootAuthSessionId", as.getParentSession()
                        .getId())
                .setAttribute("tabId", as.getTabId())
                .setAttribute("watchBase", watchBase.toString())
                .createForm("registerDevice.ftl");

        ctx.challenge(page);
    }

    private static void clearNotes(AuthenticationSessionModel s) {
        s.removeAuthNote(JTI);
        s.removeAuthNote(JWT);
    }

    private boolean isPendingRegistration(UserModel user) {
        return "true".equals(user.getFirstAttribute(REG_PENDING));
    }

    private void clearPending(UserModel user) {
        user.removeAttribute(REG_PENDING);
    }

    private void deleteUser(RequiredActionContext ctx) {
        var realm = ctx.getRealm();
        var user = ctx.getUser();
        var id = user.getId();
        ctx.getSession()
                .users()
                .removeUser(realm, user);
        LOG.infof("Deleted user %s due to failed device registration", id);
    }

    private boolean isDeviceRegistered(UserModel user) {
        return "true".equals(user.getFirstAttribute(DEV_REGISTERED));
    }

    private void markDeviceRegistered(UserModel user) {
        user.setSingleAttribute(DEV_REGISTERED, "true");
    }
}

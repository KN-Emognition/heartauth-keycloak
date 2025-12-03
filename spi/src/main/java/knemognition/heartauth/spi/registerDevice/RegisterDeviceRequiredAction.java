package knemognition.heartauth.spi.registerDevice;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import knemognition.heartauth.orchestrator.ApiException;
import knemognition.heartauth.orchestrator.model.CreatePairingResponseDto;
import knemognition.heartauth.spi.config.HaSessionNotes;
import knemognition.heartauth.spi.gateway.OrchClient;
import knemognition.heartauth.spi.status.StatusWatchRegistry;
import knemognition.heartauth.spi.status.StatusWatchResourceProviderFactory;
import org.jboss.logging.Logger;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.UserModel;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

public class RegisterDeviceRequiredAction implements RequiredActionProvider {

    private static final Logger LOG = Logger.getLogger(RegisterDeviceRequiredAction.class);

    private static final String REG_PENDING = "hauthRegistrationPending";
    private static final String DEV_REGISTERED = "hauthDeviceRegistered";

    private void requestNewPairing(RequiredActionContext ctx) throws ApiException {
        AuthenticationSessionModel sess = ctx.getAuthenticationSession();
        closeActivePairingWatch(sess);
        OrchClient oc = OrchClient.clientFromRealm(ctx.getRealm());
        String username = ctx.getUser()
                .getUsername();
        UUID userId = UUID.fromString(ctx.getUser()
                .getId());

        CreatePairingResponseDto res = oc.createPairing(userId, username);
        sess.setAuthNote(HaSessionNotes.PAIRING_JTI, res.getJti()
                .toString());
        sess.setAuthNote(HaSessionNotes.PAIRING_JWT, res.getJwt());
        sess.setAuthNote(HaSessionNotes.TTL, String.valueOf(res.getTtl()));
        sess.setAuthNote(HaSessionNotes.EXP, String.valueOf(res.getExp()));
        ;
    }

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

            ensurePendingRegistration(ctx.getUser());

            String existingJti = sess.getAuthNote(HaSessionNotes.PAIRING_JTI);
            String existingJwt = sess.getAuthNote(HaSessionNotes.PAIRING_JWT);
            if (existingJti != null && !existingJti.isBlank() && existingJwt != null) {
                closeActivePairingWatch(sess);
                render(ctx);
                return;
            }

            requestNewPairing(ctx);
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
        MultivaluedMap<String, String> formParams = ctx.getHttpRequest()
                .getDecodedFormParameters();
        if (formParams != null && "true".equalsIgnoreCase(formParams.getFirst("resend"))) {
            try {
                requestNewPairing(ctx);
                render(ctx);
                return;
            } catch (IllegalArgumentException iae) {
                LOG.error("RegisterDevice: missing/invalid realm attributes for JWT on resend", iae);
                ctx.challenge(
                        ctx.form()
                                .setError("Pairing is not configured. Missing secret.")
                                .createErrorPage(Status.INTERNAL_SERVER_ERROR)
                );
                return;
            } catch (ApiException e) {
                LOG.warn("RegisterDevice: orchestrator unavailable on resend", e);
                ctx.challenge(
                        ctx.form()
                                .setError("Upstream unavailable.")
                                .createErrorPage(Status.SERVICE_UNAVAILABLE)
                );
                return;
            } catch (Exception e) {
                LOG.error("RegisterDevice: unexpected error during resend", e);
                ctx.challenge(
                        ctx.form()
                                .setError("Unexpected error while rendering QR.")
                                .createErrorPage(Status.INTERNAL_SERVER_ERROR)
                );
                return;
            }
        }

        AuthenticationSessionModel as = ctx.getAuthenticationSession();
        String jtiStr = as.getAuthNote(HaSessionNotes.PAIRING_JTI);
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
                    preparePostRegistrationRedirect(ctx);
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

        String jti = as.getAuthNote(HaSessionNotes.PAIRING_JTI);
        String jwt = as.getAuthNote(HaSessionNotes.PAIRING_JWT);
        Long ttl = Long.valueOf(as.getAuthNote(HaSessionNotes.TTL));
        Long exp = Long.valueOf(as.getAuthNote(HaSessionNotes.EXP));

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
                .setAttribute("ttl", ttl)
                .setAttribute("exp", exp)
                .setAttribute("rootAuthSessionId", as.getParentSession()
                        .getId())
                .setAttribute("tabId", as.getTabId())
                .setAttribute("watchBase", watchBase.toString())
                .createForm("registerDevice.ftl");

        ctx.challenge(page);
    }

    private static void clearNotes(AuthenticationSessionModel s) {
        closeActivePairingWatch(s);
        s.removeAuthNote(HaSessionNotes.PAIRING_JTI);
        s.removeAuthNote(HaSessionNotes.PAIRING_JWT);
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

    private void ensurePendingRegistration(UserModel user) {
        if (isDeviceRegistered(user)) {
            clearPending(user);
            return;
        }

        if (!isPendingRegistration(user)) {
            user.setSingleAttribute(REG_PENDING, "true");
        }
    }

    private static void closeActivePairingWatch(AuthenticationSessionModel session) {
        if (session == null) {
            return;
        }
        String pairingId = session.getAuthNote(HaSessionNotes.PAIRING_JTI);
        if (pairingId == null || pairingId.isBlank()) {
            return;
        }
        StatusWatchRegistry.closePairing(session, pairingId);
    }

    private void preparePostRegistrationRedirect(RequiredActionContext ctx) {
        AuthenticationSessionModel session = ctx.getAuthenticationSession();
        session.setAuthNote(AuthenticationManager.END_AFTER_REQUIRED_ACTIONS, Boolean.TRUE.toString());
        session.setAuthNote(AuthenticationManager.SET_REDIRECT_URI_AFTER_REQUIRED_ACTIONS, Boolean.TRUE.toString());

        String clientId = Optional.ofNullable(session.getClient())
                .map(client -> client.getClientId())
                .orElse(null);
        String originalRedirect = session.getRedirectUri();
        String scope = session.getClientNote(OIDCLoginProtocol.SCOPE_PARAM);
        String responseType = session.getClientNote(OIDCLoginProtocol.RESPONSE_TYPE_PARAM);
        String state = session.getClientNote(OIDCLoginProtocol.STATE_PARAM);
        String nonce = session.getClientNote(OIDCLoginProtocol.NONCE_PARAM);

        var builder = ctx.getSession()
                .getContext()
                .getUri()
                .getBaseUriBuilder()
                .path("realms")
                .path(ctx.getRealm()
                        .getName())
                .path("protocol")
                .path("openid-connect")
                .path("auth");

        if (clientId != null && !clientId.isBlank()) {
            builder.queryParam("client_id", clientId);
        }
        if (originalRedirect != null && !originalRedirect.isBlank()) {
            builder.queryParam(OIDCLoginProtocol.REDIRECT_URI_PARAM, originalRedirect);
        }
        if (responseType != null && !responseType.isBlank()) {
            builder.queryParam(OIDCLoginProtocol.RESPONSE_TYPE_PARAM, responseType);
        }
        if (scope != null && !scope.isBlank()) {
            builder.queryParam(OIDCLoginProtocol.SCOPE_PARAM, scope);
        }
        if (state != null && !state.isBlank()) {
            builder.queryParam(OIDCLoginProtocol.STATE_PARAM, state);
        }
        if (nonce != null && !nonce.isBlank()) {
            builder.queryParam(OIDCLoginProtocol.NONCE_PARAM, nonce);
        }

        String restartUrl = builder.build()
                .toString();

        session.setRedirectUri(restartUrl);
        session.setClientNote(OIDCLoginProtocol.REDIRECT_URI_PARAM, restartUrl);
    }
}

package knemognition.heartauth.authenticators.registerDevice;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

//import org.zpi.orchestrator.invoker.ApiClient;
//import org.zpi.orchestrator.api.PairingApi;

import java.util.Base64;
import java.util.UUID;

public class RegisterDeviceAuthenticator implements Authenticator {
    private static final Logger LOG = Logger.getLogger(RegisterDeviceAuthenticator.class);

    private static final String AUTH_NOTE_JTI = "ecg.pair.jti";
//    private PairingApi pairingApi;
    private String pairingApiBase;

    @Override
    public void authenticate(AuthenticationFlowContext ctx) {
        try {
//            getPairingApi(ctx);
            String authSessionId = ctx.getAuthenticationSession().getParentSession().getId();

            LOG.infof("authenticate(): realm=%s client=%s user=%s sessionId=%s",
                    ctx.getRealm().getName(),
                    ctx.getAuthenticationSession().getClient().getClientId(),
                    ctx.getUser() != null ? ctx.getUser().getUsername() : "null",
                    authSessionId);

            String jti = UUID.randomUUID().toString();
            ctx.getAuthenticationSession().setAuthNote(AUTH_NOTE_JTI, jti);

            String pairingJwt = JwtUtils.mintPairingJwt(
                    ctx.getSession(),
                    ctx.getRealm(),
                    jti,
                    "ecg-mobile-app",
                    ctx.getUser() != null ? ctx.getUser().getId() : null,
                    RegisterDeviceConfig.from(ctx).tokenTtl()
            );
            LOG.infof("JWT in QR: %s",pairingJwt);

            byte[] png = QrUtils.pngFor(pairingJwt, 300);

            String dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(png);

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
                            .createErrorPage(Status.INTERNAL_SERVER_ERROR)
            );
        }
    }

    @Override
    public void action(AuthenticationFlowContext ctx) {
        var form = ctx.getHttpRequest().getDecodedFormParameters();
        LOG.infof("action(): params=%s user=%s",
                form, ctx.getUser() != null ? ctx.getUser().getUsername() : "null");

        if (form.containsKey("cancel")) {
            LOG.info("action(): user canceled");
            ctx.failure(AuthenticationFlowError.ACCESS_DENIED);
            return;
        }
        if ("1".equals(form.getFirst("confirm"))) {
            LOG.info("QR step confirmed by user -> success()");
            ctx.success();
            return;
        }
        String authSessionId = form.getFirst("session");
        if (authSessionId == null || authSessionId.isBlank()) {
            LOG.warn("action(): missing session id in form");
            ctx.failure(AuthenticationFlowError.INVALID_CLIENT_SESSION);
            return;
        }

        UUID jti = UUID.fromString(ctx.getAuthenticationSession().getAuthNote(AUTH_NOTE_JTI));
        if (jti.toString().isBlank()) {
            LOG.warn("action(): missing jti in auth notes");
            ctx.failure(AuthenticationFlowError.INTERNAL_ERROR);
            return;
        }

//        boolean linked = checkPairingLinked(ctx, jti, authSessionId);
//        LOG.infof("action(): pairing linked=%s (jti=%s)", linked, jti);
//        if (linked) {
//            ctx.success();
//            return;
//        }

        LOG.info("action(): not approved yet -> re-render authenticate()");
        authenticate(ctx);
    }


//    private PairingApi getPairingApi(AuthenticationFlowContext ctx) {
//        RegisterDeviceConfig cfg = RegisterDeviceConfig.from(ctx);
//        if (pairingApi == null || pairingApiBase == null || !pairingApiBase.equals(cfg.orchBaseUrl())) {
//            ApiClient client = new ApiClient();
//            client.setBasePath(cfg.orchBaseUrl());
//            pairingApi = new PairingApi(client);
//            pairingApiBase = cfg.orchBaseUrl();
//        }
//        return pairingApi;
//    }
//
//    private boolean checkPairingLinked(AuthenticationFlowContext ctx, UUID jti, String authSessionId) {
//        try {
//            PairingStatusResponse res = getPairingApi(ctx).pairStatusJtiGet(jti, authSessionId);
//            return res.getState() == PairingStatusResponse.StateEnum.LINKED;
//        } catch (Exception ex) {
//            LOG.warnf(ex, "checkPairingLinked(): orchestrator error for jti=%s", jti);
//            return false;
//        }
//    }


    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession s, RealmModel r, UserModel u) {
        return false;
    }

    @Override
    public void setRequiredActions(KeycloakSession s, RealmModel r, UserModel u) {
    }

    @Override
    public void close() {
    }
}

package knemognition.heartauth.authenticators.registerDevice;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import knemognition.heartauth.authenticators.status.StatusWatchResourceProviderFactory;
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

import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

public class RegisterDeviceRequiredAction
        implements RequiredActionProvider, RequiredActionFactory, ServerInfoAwareProviderFactory {

    private static final Logger LOG = Logger.getLogger(RegisterDeviceRequiredAction.class);

    public static final String ID = "register-device";
    private static final String AUTH_NOTE_JTI = "ecg.pair.jti";

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

    @Override
    public void init(Config.Scope scope) {

    }

    @Override
    public void postInit(KeycloakSessionFactory keycloakSessionFactory) {

    }

    @Override
    public void close() {
    }

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

    private static String attr(org.keycloak.models.RealmModel realm, String key, String def) {
        String v = realm.getAttribute(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    private static long attrLong(org.keycloak.models.RealmModel realm, String key, long def) {
        try {
            String v = realm.getAttribute(key);
            return (v == null || v.isBlank()) ? def : Long.parseLong(v);
        } catch (Exception e) {
            return def;
        }
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext ctx) {
        try {
            var realm = ctx.getRealm();
            var user = ctx.getUser();

            // JWT inputs (from realm attributes with sane defaults)
            String secret = attr(realm, "pairing.jwt-secret", null);
            if (secret == null) throw new IllegalArgumentException("pairing.jwt-secret missing");
            String aud = attr(realm, "pairing.aud", "ecg-mobile-app");
            long ttl = attrLong(realm, "pairing.ttl-seconds", 300L);

            // Pairing token fields
            String jti = UUID.randomUUID().toString(); // we'll also use this as our "pairing id"
            ctx.getAuthenticationSession().setAuthNote(AUTH_NOTE_JTI, jti);

            long iat = Instant.now().getEpochSecond();
            long exp = iat + ttl;

            String jwt = JwtUtils.mintHs256(secret, user.getId(), aud, iat, exp, jti);
            LOG.infof(jwt);
            // Build SSE base URL for pairing status:
            String watchBase = ctx.getSession().getContext().getUri()
                    .getBaseUriBuilder()
                    .path("realms")
                    .path(realm.getName())
                    .path(StatusWatchResourceProviderFactory.ID) // "status-watch"
                    .path("watch")
                    .path("pairing")
                    .build()
                    .toString();

            var as = ctx.getAuthenticationSession();
            String rootAuthSessionId = as.getParentSession().getId();
            String tabId = as.getTabId();

            Response challenge = ctx.form()
                    .setAttribute("qr", jwt)                       // QR shows JWT
                    .setAttribute("id", jti)                       // <- pairing id for SSE
                    .setAttribute("rootAuthSessionId", rootAuthSessionId)
                    .setAttribute("tabId", tabId)
                    .setAttribute("watchBase", watchBase)
                    .createForm("registerDevice.ftl");

            ctx.challenge(challenge);
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

package org.zpi.keycloak.registerDevice;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.Base64;

import static org.keycloak.common.util.Encode.urlEncode;

public class DeviceEnrollAuthenticator implements Authenticator {
    private static final Logger LOG = Logger.getLogger(DeviceEnrollAuthenticator.class);
    static final String NOTE_CODE = "ENROLL_CODE";

    public DeviceEnrollAuthenticator() {
        LOG.info("DeviceEnrollAuthenticator() constructor called.");
    }

    @Override
    public void authenticate(AuthenticationFlowContext ctx) {
        try {
            LOG.infof("authenticate(): realm=%s client=%s user=%s",
                    ctx.getRealm().getName(),
                    ctx.getAuthenticationSession().getClient().getClientId(),
                    ctx.getUser() != null ? ctx.getUser().getUsername() : "null");

            // If you want to skip when already configured (optional)
            UserModel user = ctx.getUser();
            if (user != null) {
                LOG.debugf("authenticate(): user present -> %s", user.getUsername());
                if (configuredFor(ctx.getSession(), ctx.getRealm(), user)) {
                    LOG.info("authenticate(): user already enrolled -> ctx.success()");
                    ctx.success();
                    return;
                }
            } else {
                LOG.debug("authenticate(): no user bound yet.");
            }

            String authSessionId = ctx.getAuthenticationSession().getParentSession().getId();
            LOG.debugf("authenticate(): authSessionId=%s", authSessionId);

            String payload = "login:" + authSessionId;
            LOG.debugf("authenticate(): QR payload=%s", payload);

            byte[] png = QrUtils.pngFor(payload, 300);
            String dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(png);

            String html = """
                <!doctype html>
                <html>
                  <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>Scan to continue</title>
                    <style>
                      body{font-family:system-ui,Segoe UI,Roboto,Helvetica,Arial,sans-serif;margin:0;display:grid;min-height:100vh;place-items:center;background:#0b1020;color:#fff}
                      .card{background:#12172a;padding:32px;border-radius:16px;box-shadow:0 10px 30px rgba(0,0,0,.35);max-width:420px;text-align:center}
                      img{width:280px;height:280px;image-rendering:pixelated;border-radius:12px}
                      button{margin-top:20px;border:none;border-radius:12px;padding:12px 16px;cursor:pointer;font-weight:600}
                      .primary{background:#6aa6ff}
                      .link{background:transparent;color:#8fa3ff;text-decoration:underline}
                    </style>
                  </head>
                  <body>
                    <div class="card">
                      <h2>Scan this QR</h2>
                      <p>Use the mobile app to approve the sign-in.</p>
                      <img alt="QR" src="%s" />
                      <form method="post">
                        <input type="hidden" name="session" value="%s">
                        <button class="primary" name="confirm" value="1" type="submit">I scanned it</button>
                        <button class="link" name="cancel" value="1" type="submit">Cancel</button>
                      </form>
                    </div>
                  </body>
                </html>
            """.formatted(dataUri, urlEncode(authSessionId));

            LOG.debug("authenticate(): sending HTML challenge.");
            ctx.challenge(Response.ok(html, MediaType.TEXT_HTML_TYPE).build());
        } catch (Exception e) {
            LOG.error("authenticate(): unexpected error", e);
            ctx.failure(AuthenticationFlowError.INTERNAL_ERROR);
        }
    }

    @Override
    public void action(AuthenticationFlowContext ctx) {
        try {
            var form = ctx.getHttpRequest().getDecodedFormParameters();
            LOG.infof("action(): params=%s user=%s",
                    form,
                    ctx.getUser() != null ? ctx.getUser().getUsername() : "null");

            if (form.containsKey("cancel")) {
                LOG.info("action(): user clicked cancel");
                ctx.failure(AuthenticationFlowError.ACCESS_DENIED);
                return;
            }

            String authSessionId = form.getFirst("session");
            LOG.debugf("action(): authSessionId=%s", authSessionId);

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
            ctx.failure(AuthenticationFlowError.INTERNAL_ERROR);
        }
    }

    private boolean checkOutOfBandApproval(String authSessionId, AuthenticationFlowContext ctx) {
        LOG.debugf("checkOutOfBandApproval(): authSessionId=%s", authSessionId);
        // TODO implement real check
        return false;
    }

    private UserModel resolveUserFromApproval(String authSessionId, AuthenticationFlowContext ctx) {
        LOG.debugf("resolveUserFromApproval(): authSessionId=%s", authSessionId);
        return ctx.getUser();
    }

    @Override
    public boolean requiresUser() {
        // IMPORTANT: false so Keycloak doesn't short-circuit before calling us
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        LOG.debugf("configuredFor(): user=%s", user != null ? user.getUsername() : "null");
        if (user == null) return false;
        // TODO implement real check against your registry
        return false;
    }

    @Override public void setRequiredActions(KeycloakSession s, RealmModel r, UserModel u) {}
    @Override public void close() {}
}

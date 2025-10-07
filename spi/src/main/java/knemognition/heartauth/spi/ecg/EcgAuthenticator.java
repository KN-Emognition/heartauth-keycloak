package knemognition.heartauth.spi.ecg;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import knemognition.heartauth.orchestrator.ApiException;
import knemognition.heartauth.orchestrator.model.StatusResponseDto;
import knemognition.heartauth.spi.gateway.OrchClient;
import knemognition.heartauth.spi.status.StatusWatchResourceProviderFactory;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.net.URI;
import java.util.UUID;


public class EcgAuthenticator implements Authenticator {
    private static final Logger LOG = Logger.getLogger(EcgAuthenticator.class);
    private static final String CHALLENGE_ID = "ecg.challengeId";


    private void render(AuthenticationFlowContext ctx) {
        AuthenticationSessionModel as = ctx.getAuthenticationSession();
        String idStr = as.getAuthNote(CHALLENGE_ID);
        URI watchBase = ctx.getSession()
                .getContext()
                .getUri()
                .getBaseUriBuilder()
                .path("realms")
                .path(ctx.getRealm()
                        .getName())
                .path(StatusWatchResourceProviderFactory.ID)
                .path("watch")
                .path("ecg")
                .build();

        Response page = ctx.form()
                .setAttribute("id", idStr)
                .setAttribute("rootAuthSessionId", as.getParentSession()
                        .getId())
                .setAttribute("tabId", as.getTabId())
                .setAttribute("watchBase", watchBase.toString())
                .createForm("ecg.ftl");

        ctx.challenge(page);
    }

    private static UUID parseUserId(UserModel user) {
        return UUID.fromString(user.getId());
    }

    private static void clearNotes(AuthenticationSessionModel s) {
        s.removeAuthNote(CHALLENGE_ID);
    }

    @Override
    public void authenticate(AuthenticationFlowContext ctx) {
        try {
            AuthenticationSessionModel sess = ctx.getAuthenticationSession();

            String existing = sess.getAuthNote(CHALLENGE_ID);
            if (existing != null && !existing.isBlank()) {
                render(ctx);
                return;
            }

            OrchClient orchestrator = OrchClient.clientFromRealm(ctx.getRealm());
            UUID userId = parseUserId(ctx.getUser());

            UUID challengeId = orchestrator.createChallenge(userId);
            sess.setAuthNote(CHALLENGE_ID, challengeId.toString());

            render(ctx);

        } catch (ApiException e) {
            LOG.warn("ECG orchestrator call failed", e);
            ctx.failureChallenge(
                    AuthenticationFlowError.INTERNAL_ERROR,
                    ctx.form()
                            .setError("Upstream unavailable")
                            .createErrorPage(Status.SERVICE_UNAVAILABLE)
            );
        } catch (Exception e) {
            LOG.error("ECG unexpected error", e);
            ctx.failureChallenge(
                    AuthenticationFlowError.INTERNAL_ERROR,
                    ctx.form()
                            .setError("Unexpected error")
                            .createErrorPage(Status.INTERNAL_SERVER_ERROR)
            );
        }
    }

    @Override
    public void action(AuthenticationFlowContext ctx) {
        String idStr = ctx.getAuthenticationSession()
                .getAuthNote(CHALLENGE_ID);
        if (idStr == null || idStr.isBlank()) {
            ctx.failureChallenge(
                    AuthenticationFlowError.EXPIRED_CODE,
                    ctx.form()
                            .setError("Challenge not found")
                            .createErrorPage(Status.UNAUTHORIZED)
            );
            return;
        }

        UUID id = UUID.fromString(idStr);
        try {
            OrchClient orchestrator = OrchClient.clientFromRealm(ctx.getRealm());
            StatusResponseDto status = orchestrator.getChallengeStatus(id);

            switch (status.getStatus()) {
                case APPROVED -> {
                    clearNotes(ctx.getAuthenticationSession());
                    ctx.success();
                }
                case DENIED -> {
                    clearNotes(ctx.getAuthenticationSession());
                    ctx.failureChallenge(
                            AuthenticationFlowError.INVALID_USER,
                            ctx.form()
                                    .setError("Denied" + (status.getReason() != null ? ": " + status.getReason() : ""))
                                    .createErrorPage(Status.UNAUTHORIZED)
                    );
                }
                case EXPIRED, NOT_FOUND -> {
                    clearNotes(ctx.getAuthenticationSession());
                    ctx.failureChallenge(
                            AuthenticationFlowError.EXPIRED_CODE,
                            ctx.form()
                                    .setError("Challenge expired")
                                    .createErrorPage(Status.UNAUTHORIZED)
                    );
                }
                default -> render(ctx);
            }
        } catch (ApiException e) {
            LOG.warn("ECG status check failed", e);
            ctx.failureChallenge(
                    AuthenticationFlowError.INTERNAL_ERROR,
                    ctx.form()
                            .setError("Upstream unavailable")
                            .createErrorPage(Status.SERVICE_UNAVAILABLE)
            );
        } catch (Exception e) {
            LOG.error("ECG unexpected in action()", e);
            ctx.failureChallenge(
                    AuthenticationFlowError.INTERNAL_ERROR,
                    ctx.form()
                            .setError("Unexpected error")
                            .createErrorPage(Status.INTERNAL_SERVER_ERROR)
            );
        }
    }

    @Override
    public boolean requiresUser() {
        return true;
    }

    @Override
    public boolean configuredFor(KeycloakSession s, RealmModel r, UserModel u) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession s, RealmModel r, UserModel u) {
    }

    @Override
    public void close() {
    }
}

package knemognition.hauth.spi.status;

import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resource.RealmResourceProvider;

public record StatusWatchResourceProvider(KeycloakSession session) implements RealmResourceProvider {

    @Override
    public Object getResource() {
        return new StatusWatchResource(session);
    }

    @Override
    public void close() {
    }
}
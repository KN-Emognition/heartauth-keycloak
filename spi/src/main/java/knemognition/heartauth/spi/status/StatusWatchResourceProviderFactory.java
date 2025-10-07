package knemognition.heartauth.spi.status;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

public class StatusWatchResourceProviderFactory implements RealmResourceProviderFactory {
    public static final String ID = "status-watch";

    @Override
    public RealmResourceProvider create(KeycloakSession session) {
        return new StatusWatchResourceProvider(session);
    }

    @Override
    public void init(Config.Scope scope) {
    }
    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return ID;
    }
}
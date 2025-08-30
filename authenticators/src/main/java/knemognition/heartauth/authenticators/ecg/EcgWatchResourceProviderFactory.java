package knemognition.heartauth.authenticators.ecg;


import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

public class EcgWatchResourceProviderFactory implements RealmResourceProviderFactory {
    public static final String ID = "ecg";

    @Override public RealmResourceProvider create(org.keycloak.models.KeycloakSession session) {
        return new EcgWatchResourceProvider(session);
    }


    @Override public void init(org.keycloak.Config.Scope config) {}
    @Override public void postInit(KeycloakSessionFactory factory) {}
    @Override public void close() {}
    @Override public String getId() { return ID; }
}
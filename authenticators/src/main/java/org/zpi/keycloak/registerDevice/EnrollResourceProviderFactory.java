package org.zpi.keycloak.registerDevice;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

public class EnrollResourceProviderFactory implements RealmResourceProviderFactory {
    public static final String ID = "device-enroll";

    @Override public RealmResourceProvider create(KeycloakSession session) { return new EnrollResourceProvider(session); }
    @Override public void init(Config.Scope config) {}
    @Override public void postInit(KeycloakSessionFactory factory) {}
    @Override public void close() {}
    @Override public String getId() { return ID; }
}

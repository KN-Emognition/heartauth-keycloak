package knemognition.heartauth.authenticators.ecg;

import jakarta.ws.rs.Path;
import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resource.RealmResourceProvider;

public class EcgWatchResourceProvider implements RealmResourceProvider {
    private final KeycloakSession session;
    public EcgWatchResourceProvider(KeycloakSession session) { this.session = session; }

    @Override public Object getResource() { return new EcgWatchResource(session); }
    @Override public void close() {}
}
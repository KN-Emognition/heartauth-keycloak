package knemognition.heartauth.spi.registerDevice;

import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class RegisterDeviceRequiredActionFactory implements RequiredActionFactory {

    public static final String ID = "register-device";

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
        return new RegisterDeviceRequiredAction();
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }
}

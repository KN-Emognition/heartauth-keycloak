package org.zpi.keycloak.registerDevice;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.Collections;
import java.util.List;

public class DeviceEnrollAuthenticatorFactory implements AuthenticatorFactory {
    public static final String ID = "device-enroll-authenticator";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getReferenceCategory() {
        return "device-enroll";
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }


    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return Collections.emptyList();
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return new DeviceEnrollAuthenticator();
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

    @Override
    public String getDisplayType() {
        return "Enroll phone (QR)";
    }

    @Override
    public String getHelpText() {
        return "Enroll a phone by scanning a QR; the device posts its public key.";
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[]{
                AuthenticationExecutionModel.Requirement.REQUIRED,
                AuthenticationExecutionModel.Requirement.ALTERNATIVE,
                AuthenticationExecutionModel.Requirement.DISABLED
        };
    }
}

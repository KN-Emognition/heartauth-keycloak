package org.zpi.keycloak.registerDevice;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.authentication.ConfigurableAuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.ArrayList;
import java.util.List;

public class RegisterDeviceAuthenticatorFactory
        implements AuthenticatorFactory, ConfigurableAuthenticatorFactory {
    public static final String ID = "register-device-authenticator";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getReferenceCategory() {
        return "register-device";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }


    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        List<ProviderConfigProperty> props = new ArrayList<>();

        props.add(new ProviderConfigProperty(
                RegisterDeviceConfig.Keys.ORCH_BASE_URL,
                "Orchestrator Base URL",
                "Base URL of the ECG Orchestrator service.",
                ProviderConfigProperty.STRING_TYPE,
                "https://orchestrator.example.com"));

        props.add(new ProviderConfigProperty(
                RegisterDeviceConfig.Keys.TOKEN_TTL_SEC,
                "Pairing Token TTL (seconds)",
                "Validity of the pairing JWT shown in QR.",
                ProviderConfigProperty.STRING_TYPE,
                "300"));


        return props;
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return new RegisterDeviceAuthenticator();
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

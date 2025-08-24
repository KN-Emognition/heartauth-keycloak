package knemognition.heartauth.authenticators.ecg;


import org.keycloak.Config.Scope;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.authentication.ConfigurableAuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel.Requirement;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.List;

public class EcgAuthenticatorFactory implements AuthenticatorFactory, ConfigurableAuthenticatorFactory {
    public static final String ID = "ecg-authenticator";

    public static final String CONF_BASE_URL = "orchestratorBaseUrl";
    public static final String CONF_API_KEY = "orchestratorApiKey";
    public static final String CONF_TIMEOUT_MS = "orchestratorTimeoutMs";
    public static final String CONF_TTL_SECONDS = "challengeTtlSeconds";
    public static final String CONF_POLL_MS = "pollIntervalMs";

    private static final List<ProviderConfigProperty> CONFIG = List.of(
            prop(CONF_BASE_URL, "Orchestrator Base URL", "e.g. https://orchestrator.internal", ProviderConfigProperty.STRING_TYPE, "http://localhost:8080"),
            prop(CONF_API_KEY, "API Key (optional)", "Sent as X-Api-Key header", ProviderConfigProperty.STRING_TYPE, ""),
            prop(CONF_TIMEOUT_MS, "HTTP Timeout (ms)", "Client read timeout in ms", ProviderConfigProperty.STRING_TYPE, "5000"),
            prop(CONF_TTL_SECONDS, "Challenge TTL (s)", "How long a challenge lives", ProviderConfigProperty.STRING_TYPE, "120"),
            prop(CONF_POLL_MS, "Poll Interval (ms)", "Browser auto-poll period", ProviderConfigProperty.STRING_TYPE, "2000")
    );

    private static final EcgAuthenticator SINGLETON = new EcgAuthenticator();

    private static ProviderConfigProperty prop(String name, String label, String help, String type, String def) {
        var p = new ProviderConfigProperty();
        p.setName(name);
        p.setLabel(label);
        p.setHelpText(help);
        p.setType(type);
        p.setDefaultValue(def);
        return p;
    }

    private static ProviderConfigProperty secret(String name, String label, String help, String def) {
        var p = new ProviderConfigProperty();
        p.setName(name);
        p.setLabel(label);
        p.setHelpText(help);
        p.setType(ProviderConfigProperty.PASSWORD);
        p.setDefaultValue(def);
        return p;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return SINGLETON;
    }

    @Override
    public String getDisplayType() {
        return "ECG Challenge";
    }

    @Override
    public String getReferenceCategory() {
        return "";
    }

    @Override
    public String getHelpText() {
        return "Push a challenge to the user’s device and wait for approval.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG;
    }

    @Override
    public Requirement[] getRequirementChoices() {
        return new Requirement[]{Requirement.REQUIRED, Requirement.ALTERNATIVE, Requirement.DISABLED};
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public void init(Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }
}

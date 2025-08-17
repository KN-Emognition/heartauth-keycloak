package org.zpi.keycloak.registerDevice;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.models.AuthenticatorConfigModel;

import java.time.Duration;

public record RegisterDeviceConfig(String orchBaseUrl, Duration tokenTtl) {

    public static final class Keys {
        public static final String ORCH_BASE_URL = "orchBaseUrl";
        public static final String TOKEN_TTL_SEC = "tokenTtl";

        private Keys() {
        }
    }

    public static RegisterDeviceConfig from(AuthenticationFlowContext ctx) {
        AuthenticatorConfigModel cfgModel = ctx.getAuthenticatorConfig();
        if (cfgModel == null || cfgModel.getConfig() == null) {
            // fall back to system props or defaults
            String orchBase = System.getProperty("ecg.orch.base", "https://orchestrator.example.com");
            return new RegisterDeviceConfig(orchBase, Duration.ofMinutes(5));
        }

        var map = cfgModel.getConfig();
        String orchBase = map.getOrDefault(Keys.ORCH_BASE_URL,
                System.getProperty("ecg.orch.base", "https://orchestrator.example.com"));

        Duration ttl = Duration.ofSeconds(
                Long.parseLong(map.getOrDefault(Keys.TOKEN_TTL_SEC, "300"))
        );

        return new RegisterDeviceConfig(orchBase, ttl);
    }
}

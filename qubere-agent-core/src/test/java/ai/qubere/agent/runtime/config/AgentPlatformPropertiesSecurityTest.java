package ai.qubere.agent.runtime.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentPlatformPropertiesSecurityTest {

    @Test
    void permissiveModeTrustsHeadersByDefault() {
        AgentPlatformProperties.Security security = new AgentPlatformProperties.Security();
        security.setAuthorizationMode("permissive");

        assertThat(security.resolveTrustInboundHeaders()).isTrue();
    }

    @Test
    void strictModeDoesNotTrustHeadersByDefault() {
        AgentPlatformProperties.Security security = new AgentPlatformProperties.Security();
        security.setAuthorizationMode("strict");

        assertThat(security.resolveTrustInboundHeaders()).isFalse();
    }

    @Test
    void explicitSettingOverridesDerivedDefaultInStrictMode() {
        AgentPlatformProperties.Security security = new AgentPlatformProperties.Security();
        security.setAuthorizationMode("strict");
        security.setTrustInboundHeaders(true);

        assertThat(security.resolveTrustInboundHeaders()).isTrue();
    }

    @Test
    void explicitSettingOverridesDerivedDefaultInPermissiveMode() {
        AgentPlatformProperties.Security security = new AgentPlatformProperties.Security();
        security.setAuthorizationMode("permissive");
        security.setTrustInboundHeaders(false);

        assertThat(security.resolveTrustInboundHeaders()).isFalse();
    }
}

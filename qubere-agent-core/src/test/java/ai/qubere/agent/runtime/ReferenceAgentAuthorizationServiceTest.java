package ai.qubere.agent.runtime;

import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentRiskLevel;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceAgentAuthorizationServiceTest {

    private static final AgentDescriptor DESCRIPTOR = new AgentDescriptor(
            "generic.echo-analysis",
            "Echo",
            "0.1.0",
            "Echo test agent",
            AgentRiskLevel.LOW,
            Set.of("analysis")
    );

    @Test
    void permissiveModeAllowsMissingTenantAndActorForLocalDevelopment() {
        ReferenceAgentAuthorizationService service = new ReferenceAgentAuthorizationService(new AgentPlatformProperties());

        assertThat(service.canRun(context(null, null, Map.of()), DESCRIPTOR)).isTrue();
    }

    @Test
    void strictModeRequiresTenantAndActorByDefault() {
        AgentPlatformProperties properties = strictProperties();
        ReferenceAgentAuthorizationService service = new ReferenceAgentAuthorizationService(properties);

        assertThat(service.canRun(context(null, "actor", Map.of()), DESCRIPTOR)).isFalse();
        assertThat(service.canRun(context("tenant", null, Map.of()), DESCRIPTOR)).isFalse();
        assertThat(service.canRun(context("tenant", "actor", Map.of()), DESCRIPTOR)).isTrue();
    }

    @Test
    void strictModeChecksAllowedTenants() {
        AgentPlatformProperties properties = strictProperties();
        properties.getSecurity().setAllowedTenants(Set.of("tenant-a"));
        ReferenceAgentAuthorizationService service = new ReferenceAgentAuthorizationService(properties);

        assertThat(service.canRun(context("tenant-b", "actor", Map.of()), DESCRIPTOR)).isFalse();
        assertThat(service.canRun(context("tenant-a", "actor", Map.of()), DESCRIPTOR)).isTrue();
    }

    @Test
    void strictModeChecksGlobalAndAgentPermissions() {
        AgentPlatformProperties properties = strictProperties();
        properties.getSecurity().setRequiredRunPermissions(Set.of("agents.run"));
        properties.getSecurity().setAgentRequiredPermissions(Map.of("generic.echo-analysis", Set.of("agents.echo.run")));
        ReferenceAgentAuthorizationService service = new ReferenceAgentAuthorizationService(properties);

        assertThat(service.canRun(context("tenant", "actor", Map.of("permissions", Set.of("agents.run"))), DESCRIPTOR)).isFalse();
        assertThat(service.canRun(context("tenant", "actor", Map.of("permissions", Set.of("agents.run", "agents.echo.run"))), DESCRIPTOR)).isTrue();
    }

    private static AgentPlatformProperties strictProperties() {
        AgentPlatformProperties properties = new AgentPlatformProperties();
        properties.getSecurity().setAuthorizationMode("strict");
        return properties;
    }

    private static AgentExecutionContext context(String tenantId, String actorId, Map<String, Object> attributes) {
        return new AgentExecutionContext("exec-1", tenantId, actorId, "corr", Instant.now(), attributes);
    }
}
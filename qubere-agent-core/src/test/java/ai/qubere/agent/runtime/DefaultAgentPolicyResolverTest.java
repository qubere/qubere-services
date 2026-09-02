package ai.qubere.agent.runtime;

import ai.qubere.agent.api.AgentRiskLevel;
import ai.qubere.agent.core.AgentRunMode;
import ai.qubere.agent.core.AgentRunOptions;
import ai.qubere.agent.core.ResolvedAgentPolicy;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAgentPolicyResolverTest {

    @Test
    void resolvesDefaultsFromConfigurationProperties() {
        AgentPlatformProperties properties = new AgentPlatformProperties();
        properties.getRuntime().setDefaultMode(AgentRunMode.AUTONOMOUS);
        properties.getRuntime().setMaxSteps(3);
        properties.getRuntime().setTemperature(0.7d);
        properties.getRuntime().setMaxOutputTokens(512);
        properties.getRuntime().setAllowToolCalls(false);
        properties.getRuntime().setRequireHumanApproval(true);
        properties.getRuntime().setAllowedTools(Set.of("lookup.customer"));

        ResolvedAgentPolicy policy = new DefaultAgentPolicyResolver(properties).resolve("agent", null);

        assertThat(policy.mode()).isEqualTo(AgentRunMode.AUTONOMOUS);
        assertThat(policy.maxSteps()).isEqualTo(3);
        assertThat(policy.temperature()).isEqualTo(0.7d);
        assertThat(policy.maxOutputTokens()).isEqualTo(512);
        assertThat(policy.allowToolCalls()).isFalse();
        assertThat(policy.requireHumanApproval()).isTrue();
        assertThat(policy.allowedTools()).containsExactly("lookup.customer");
    }

    @Test
    void callerOptionsOverrideConfiguredDefaultsOnlyWhenPresent() {
        AgentPlatformProperties properties = new AgentPlatformProperties();
        properties.getRuntime().setDefaultMode(AgentRunMode.RECOMMEND);
        properties.getRuntime().setMaxSteps(8);
        properties.getRuntime().setStreaming(false);
        properties.getRuntime().setAllowedTools(Set.of("default.tool"));

        AgentRunOptions options = new AgentRunOptions(
                AgentRunMode.AUTONOMOUS,
                2,
                true,
                null,
                null,
                null,
                null,
                Set.of("caller.tool"),
                Map.of()
        );

        ResolvedAgentPolicy policy = new DefaultAgentPolicyResolver(properties).resolve("agent", options);

        assertThat(policy.mode()).isEqualTo(AgentRunMode.AUTONOMOUS);
        assertThat(policy.maxSteps()).isEqualTo(2);
        assertThat(policy.streaming()).isTrue();
        assertThat(policy.temperature()).isEqualTo(0.2d);
        assertThat(policy.allowedTools()).containsExactly("caller.tool");
    }

    @Test
    void highRiskAgentDefaultsToRequiringApprovalWhenNotExplicitlyConfigured() {
        AgentPlatformProperties properties = new AgentPlatformProperties();
        properties.getRuntime().setRequireHumanApproval(false);
        // No per-agent definition at all: only the descriptor risk level drives the decision.

        ResolvedAgentPolicy policy = new DefaultAgentPolicyResolver(properties)
                .resolve("agent.high-risk", null, AgentRiskLevel.HIGH);

        assertThat(policy.requireHumanApproval()).isTrue();
    }

    @Test
    void criticalRiskAgentDefaultsToRequiringApproval() {
        AgentPlatformProperties properties = new AgentPlatformProperties();
        properties.getRuntime().setRequireHumanApproval(false);

        ResolvedAgentPolicy policy = new DefaultAgentPolicyResolver(properties)
                .resolve("agent.critical-risk", null, AgentRiskLevel.CRITICAL);

        assertThat(policy.requireHumanApproval()).isTrue();
    }

    @Test
    void lowRiskAgentDoesNotDefaultToRequiringApproval() {
        AgentPlatformProperties properties = new AgentPlatformProperties();
        properties.getRuntime().setRequireHumanApproval(false);

        ResolvedAgentPolicy policy = new DefaultAgentPolicyResolver(properties)
                .resolve("agent.low-risk", null, AgentRiskLevel.LOW);

        assertThat(policy.requireHumanApproval()).isFalse();
    }

    @Test
    void explicitPerAgentConfigurationOverridesRiskBasedApprovalDefault() {
        AgentPlatformProperties properties = new AgentPlatformProperties();
        properties.getRuntime().setRequireHumanApproval(false);
        AgentPlatformProperties.AgentDefinition definition = new AgentPlatformProperties.AgentDefinition();
        definition.setRequireHumanApproval(false);
        properties.getDefinitions().put("agent.high-risk-but-explicitly-unattended", definition);

        ResolvedAgentPolicy policy = new DefaultAgentPolicyResolver(properties)
                .resolve("agent.high-risk-but-explicitly-unattended", null, AgentRiskLevel.HIGH);

        assertThat(policy.requireHumanApproval()).isFalse();
    }

    @Test
    void riskBasedApprovalDefaultCanBeDisabledGlobally() {
        AgentPlatformProperties properties = new AgentPlatformProperties();
        properties.getRuntime().setRequireHumanApproval(false);
        properties.getGovernance().setRequireApprovalForHighRisk(false);

        ResolvedAgentPolicy policy = new DefaultAgentPolicyResolver(properties)
                .resolve("agent.high-risk", null, AgentRiskLevel.HIGH);

        assertThat(policy.requireHumanApproval()).isFalse();
    }
}

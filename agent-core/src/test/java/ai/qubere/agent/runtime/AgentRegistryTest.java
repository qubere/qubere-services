package ai.qubere.agent.runtime;

import ai.qubere.agent.api.Agent;
import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentOutput;
import ai.qubere.agent.api.AgentRiskLevel;
import ai.qubere.agent.core.AgentResult;
import ai.qubere.agent.core.GenericAgentInput;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentRegistryTest {

    @Test
    void selectsConfiguredDefaultVersionWhenAvailable() {
        AgentPlatformProperties properties = new AgentPlatformProperties();
        properties.getRegistry().setDefaultVersions(Map.of("agent", "1.0.0"));

        AgentRegistry registry = new AgentRegistry(List.of(agent("agent", "1.0.0"), agent("agent", "2.0.0")), properties);

        RegisteredAgent registeredAgent = registry.findRegisteredAgent("agent").orElseThrow();

        assertThat(registeredAgent.descriptor().version()).isEqualTo("1.0.0");
        assertThat(registeredAgent.defaultVersion()).isTrue();
    }

    @Test
    void selectsHighestVersionWhenNoDefaultIsConfigured() {
        AgentRegistry registry = new AgentRegistry(List.of(agent("agent", "1.0.0"), agent("agent", "2.1.0")));

        assertThat(registry.findRegisteredAgent("agent"))
                .isPresent()
                .get()
                .extracting(registeredAgent -> registeredAgent.descriptor().version())
                .isEqualTo("2.1.0");
    }

    @Test
    void findsExplicitVersion() {
        AgentRegistry registry = new AgentRegistry(List.of(agent("agent", "1.0.0"), agent("agent", "2.0.0")));

        assertThat(registry.findRegisteredAgent("agent", "1.0.0"))
                .isPresent()
                .get()
                .extracting(registeredAgent -> registeredAgent.descriptor().version())
                .isEqualTo("1.0.0");
    }

    @Test
    void rejectsDuplicateAgentIdAndVersion() {
        List<Agent<?, ?>> agents = List.of(agent("agent", "1.0.0"), agent("agent", "1.0.0"));

        assertThatThrownBy(() -> new AgentRegistry(agents))
                .isInstanceOf(AgentDescriptorValidationException.class)
                .hasMessageContaining("Duplicate agent registration");
    }

    private Agent<GenericAgentInput, AgentResult<Map<String, Object>>> agent(String id, String version) {
        AgentDescriptor descriptor = new AgentDescriptor(
                id,
                "Test Agent",
                version,
                "Test agent",
                AgentRiskLevel.LOW,
                Set.of("test")
        );
        return new Agent<>() {
            @Override
            public AgentDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public AgentResult<Map<String, Object>> run(GenericAgentInput input, AgentExecutionContext context) {
                return new AgentResult<>(Map.of("version", version), null, List.of(), Map.of());
            }
        };
    }
}

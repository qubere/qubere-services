package ai.qubere.agent.resilience.config;

import ai.qubere.agent.resilience.AgentResilienceGateway;
import ai.qubere.agent.resilience.Resilience4jAgentResilienceGateway;
import ai.qubere.agent.runtime.config.AgentRuntimeAutoConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AgentResilienceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgentResilienceAutoConfiguration.class, AgentRuntimeAutoConfiguration.class));

    @Test
    void registersNoOpGatewayByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AgentResilienceGateway.class);
            assertThat(context.getBean(AgentResilienceGateway.class)).isNotInstanceOf(Resilience4jAgentResilienceGateway.class);
        });
    }

    @Test
    void registersResilience4jGatewayWhenExplicitlyEnabled() {
        contextRunner
                .withPropertyValues("agent-platform.resilience.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(AgentResilienceGateway.class);
                    assertThat(context.getBean(AgentResilienceGateway.class)).isInstanceOf(Resilience4jAgentResilienceGateway.class);
                });
    }
}

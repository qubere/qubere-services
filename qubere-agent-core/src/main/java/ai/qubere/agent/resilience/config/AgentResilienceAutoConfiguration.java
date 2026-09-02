package ai.qubere.agent.resilience.config;

import ai.qubere.agent.resilience.AgentResilienceGateway;
import ai.qubere.agent.resilience.Resilience4jAgentResilienceGateway;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;
import ai.qubere.agent.runtime.config.AgentRuntimeAutoConfiguration;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers a Resilience4j-backed {@link AgentResilienceGateway} when Resilience4j is present on
 * the classpath and {@code agent-platform.resilience.enabled=true}. Otherwise the framework falls
 * back to a pass-through no-op gateway registered by {@code AgentRuntimeAutoConfiguration}, so
 * applications that do not add Resilience4j as a dependency are unaffected.
 */
@AutoConfiguration
@AutoConfigureBefore(AgentRuntimeAutoConfiguration.class)
@ConditionalOnClass(CircuitBreaker.class)
@ConditionalOnProperty(prefix = "agent-platform.resilience", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AgentPlatformProperties.class)
public class AgentResilienceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AgentResilienceGateway agentResilienceGateway(AgentPlatformProperties properties) {
        return new Resilience4jAgentResilienceGateway(properties);
    }
}

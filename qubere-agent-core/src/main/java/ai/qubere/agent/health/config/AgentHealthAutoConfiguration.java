package ai.qubere.agent.health.config;

import ai.qubere.agent.ai.AgentAiClient;
import ai.qubere.agent.async.AgentAsyncQueue;
import ai.qubere.agent.health.AgentAiProviderHealthIndicator;
import ai.qubere.agent.health.AgentAsyncQueueHealthIndicator;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;

/**
 * Registers dependency health indicators beyond the generic Spring Boot Actuator database check:
 * AI provider adapter availability and async run queue depth. Only activates when Spring Boot
 * Actuator is on the classpath, so the framework does not force actuator into every deployed
 * application.
 */
@AutoConfiguration
@ConditionalOnClass(HealthIndicator.class)
@EnableConfigurationProperties(AgentPlatformProperties.class)
public class AgentHealthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "agentAiProviderHealthIndicator")
    HealthIndicator agentAiProviderHealthIndicator(AgentPlatformProperties properties, ObjectProvider<AgentAiClient> aiClientProvider) {
        return new AgentAiProviderHealthIndicator(properties, aiClientProvider);
    }

    @Bean
    @ConditionalOnMissingBean(name = "agentAsyncQueueHealthIndicator")
    HealthIndicator agentAsyncQueueHealthIndicator(ObjectProvider<AgentAsyncQueue> queueProvider, AgentPlatformProperties properties) {
        AgentAsyncQueue queue = queueProvider.getIfAvailable();
        return queue == null
                ? () -> org.springframework.boot.health.contributor.Health.unknown()
                        .withDetail("reason", "No AgentAsyncQueue bean is configured")
                        .build()
                : new AgentAsyncQueueHealthIndicator(queue, properties);
    }
}

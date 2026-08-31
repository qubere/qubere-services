package ai.qubere.agent.runtime.config;

import ai.qubere.agent.api.Agent;
import ai.qubere.agent.runtime.AgentAuditService;
import ai.qubere.agent.runtime.AgentAuthorizationService;
import ai.qubere.agent.runtime.AgentGuardrailService;
import ai.qubere.agent.runtime.AgentPolicyResolver;
import ai.qubere.agent.runtime.AgentRegistry;
import ai.qubere.agent.runtime.DefaultAgentPolicyResolver;
import ai.qubere.agent.runtime.GuardrailDecision;

import java.util.Collection;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(AgentPlatformProperties.class)
public class AgentRuntimeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AgentPolicyResolver agentPolicyResolver(AgentPlatformProperties properties) {
        return new DefaultAgentPolicyResolver(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    AgentRegistry agentRegistry(Collection<Agent<?, ?>> agents, AgentPlatformProperties properties) {
        return new AgentRegistry(agents, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    AgentAuthorizationService agentAuthorizationService() {
        return (context, descriptor) -> true;
    }

    @Bean
    @ConditionalOnMissingBean
    AgentGuardrailService agentGuardrailService() {
        return (context, descriptor, input) -> GuardrailDecision.allow();
    }

    @Bean
    @ConditionalOnMissingBean
    AgentAuditService agentAuditService() {
        return (context, descriptor, status, message) -> {
        };
    }
}

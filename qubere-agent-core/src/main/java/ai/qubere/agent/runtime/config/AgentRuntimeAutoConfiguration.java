package ai.qubere.agent.runtime.config;

import ai.qubere.agent.api.Agent;
import ai.qubere.agent.redaction.AgentRedactionService;
import ai.qubere.agent.redaction.DefaultAgentRedactionService;
import ai.qubere.agent.resilience.AgentResilienceGateway;
import ai.qubere.agent.runtime.AgentAuditService;
import ai.qubere.agent.runtime.AgentAuthorizationService;
import ai.qubere.agent.runtime.AgentGuardrailService;
import ai.qubere.agent.runtime.AgentPolicyResolver;
import ai.qubere.agent.runtime.AgentRegistry;
import ai.qubere.agent.runtime.DefaultAgentGuardrailService;
import ai.qubere.agent.runtime.DefaultAgentPolicyResolver;
import ai.qubere.agent.runtime.GuardrailDecision;
import ai.qubere.agent.runtime.ReferenceAgentAuthorizationService;
import ai.qubere.agent.runtime.security.AgentCallerIdentityResolver;
import ai.qubere.agent.runtime.security.NoOpCallerIdentityResolver;
import ai.qubere.agent.runtime.security.TrustedHeaderCallerIdentityResolver;
import ai.qubere.agent.secrets.AgentSecretResolver;
import ai.qubere.agent.secrets.EnvironmentAgentSecretResolver;

import java.util.Collection;
import java.util.concurrent.Executor;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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
    AgentAuthorizationService agentAuthorizationService(AgentPlatformProperties properties) {
        return new ReferenceAgentAuthorizationService(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    AgentCallerIdentityResolver agentCallerIdentityResolver(AgentPlatformProperties properties) {
        return properties.getSecurity().resolveTrustInboundHeaders()
                ? new TrustedHeaderCallerIdentityResolver()
                : new NoOpCallerIdentityResolver();
    }

    /**
     * Registers the JWT-backed identity resolver in its own nested configuration class so the
     * {@code org.springframework.security.oauth2.jwt.JwtDecoder} type reference is only loaded
     * when the optional {@code spring-boot-starter-oauth2-resource-server} dependency is present.
     * Without this isolation, referencing the type directly in this file would break every
     * application that does not add that dependency.
     */
    @org.springframework.boot.autoconfigure.condition.ConditionalOnClass(
            org.springframework.security.oauth2.jwt.JwtDecoder.class)
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    static class AgentJwtIdentityConfiguration {

        @Bean
        @ConditionalOnMissingBean(AgentCallerIdentityResolver.class)
        @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(
                org.springframework.security.oauth2.jwt.JwtDecoder.class)
        @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
                prefix = "agent-platform.security.jwt", name = "enabled", havingValue = "true")
        ai.qubere.agent.runtime.security.JwtCallerIdentityResolver jwtCallerIdentityResolver(
                org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder,
                AgentPlatformProperties properties
        ) {
            AgentPlatformProperties.Security.Jwt jwtProperties = properties.getSecurity().getJwt();
            return new ai.qubere.agent.runtime.security.JwtCallerIdentityResolver(
                    jwtDecoder,
                    jwtProperties.getTenantClaim(),
                    jwtProperties.getActorClaim(),
                    jwtProperties.getPermissionsClaim()
            );
        }
    }

    @Bean
    @ConditionalOnMissingBean
    AgentGuardrailService agentGuardrailService(AgentPlatformProperties properties) {
        return new DefaultAgentGuardrailService(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    AgentRedactionService agentRedactionService() {
        return new DefaultAgentRedactionService();
    }

    @Bean
    @ConditionalOnMissingBean
    AgentAuditService agentAuditService() {
        return (context, descriptor, status, message) -> {
        };
    }

    @Bean
    @ConditionalOnMissingBean
    AgentSecretResolver agentSecretResolver(Environment environment) {
        return new EnvironmentAgentSecretResolver(environment);
    }

    @Bean
    @ConditionalOnMissingBean
    AgentResilienceGateway agentResilienceGateway() {
        return AgentResilienceGateway.noop();
    }

    @Bean(name = "agentInvocationExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "agentInvocationExecutor")
    Executor agentInvocationExecutor(AgentPlatformProperties properties) {
        AgentPlatformProperties.RuntimeExecutor executorProperties = properties.getRuntime().getExecutor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(executorProperties.getCorePoolSize());
        executor.setMaxPoolSize(executorProperties.getMaxPoolSize());
        executor.setQueueCapacity(executorProperties.getQueueCapacity());
        executor.setThreadNamePrefix(executorProperties.getThreadNamePrefix());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(executorProperties.getAwaitTerminationSeconds());
        executor.initialize();
        return executor;
    }
}
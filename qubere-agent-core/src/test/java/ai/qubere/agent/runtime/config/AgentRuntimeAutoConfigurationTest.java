package ai.qubere.agent.runtime.config;

import ai.qubere.agent.runtime.AgentAuthorizationService;
import ai.qubere.agent.runtime.AgentGuardrailService;
import ai.qubere.agent.runtime.DefaultAgentGuardrailService;
import ai.qubere.agent.runtime.security.AgentCallerIdentityResolver;
import ai.qubere.agent.runtime.security.JwtCallerIdentityResolver;
import ai.qubere.agent.runtime.security.NoOpCallerIdentityResolver;
import ai.qubere.agent.runtime.security.TrustedHeaderCallerIdentityResolver;
import ai.qubere.agent.secrets.AgentSecretResolver;
import ai.qubere.agent.secrets.EnvironmentAgentSecretResolver;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRuntimeAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgentRuntimeAutoConfiguration.class));

    @Test
    void permissiveModeRegistersTrustedHeaderResolverByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AgentCallerIdentityResolver.class);
            assertThat(context.getBean(AgentCallerIdentityResolver.class))
                    .isInstanceOf(TrustedHeaderCallerIdentityResolver.class);
        });
    }

    @Test
    void strictModeRegistersFailClosedResolverByDefault() {
        contextRunner
                .withPropertyValues("agent-platform.security.authorization-mode=strict")
                .run(context -> {
                    assertThat(context).hasSingleBean(AgentCallerIdentityResolver.class);
                    assertThat(context.getBean(AgentCallerIdentityResolver.class))
                            .isInstanceOf(NoOpCallerIdentityResolver.class);
                });
    }

    @Test
    void strictModeWithExplicitTrustStillRegistersTrustedHeaderResolver() {
        contextRunner
                .withPropertyValues(
                        "agent-platform.security.authorization-mode=strict",
                        "agent-platform.security.trust-inbound-headers=true"
                )
                .run(context -> {
                    assertThat(context.getBean(AgentCallerIdentityResolver.class))
                            .isInstanceOf(TrustedHeaderCallerIdentityResolver.class);
                });
    }

    @Test
    void registersDedicatedInvocationExecutorBean() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("agentInvocationExecutor");
            assertThat(context.getBean("agentInvocationExecutor")).isInstanceOf(Executor.class);
        });
    }

    @Test
    void registersDefaultGuardrailServiceInsteadOfAllowAll() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AgentGuardrailService.class);
            assertThat(context.getBean(AgentGuardrailService.class)).isInstanceOf(DefaultAgentGuardrailService.class);
        });
    }

    @Test
    void registersEnvironmentBackedSecretResolverByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AgentSecretResolver.class);
            assertThat(context.getBean(AgentSecretResolver.class)).isInstanceOf(EnvironmentAgentSecretResolver.class);
        });
    }

    @Test
    void registersAuthorizationServiceBean() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(AgentAuthorizationService.class));
    }

    @Test
    void jwtDisabledByDefaultEvenWithADecoderBeanPresent() {
        // Adding the optional oauth2-resource-server dependency and a JwtDecoder bean must not
        // silently change authentication behavior; jwt.enabled must be explicit.
        contextRunner
                .withUserConfiguration(StubJwtDecoderConfiguration.class)
                .run(context -> assertThat(context.getBean(AgentCallerIdentityResolver.class))
                        .isInstanceOf(TrustedHeaderCallerIdentityResolver.class));
    }

    @Test
    void jwtEnabledWithoutADecoderBeanFallsBackToDefault() {
        // jwt.enabled=true alone (no JwtDecoder bean supplied) must not break startup; it should
        // simply fall back to the existing header/no-op resolver.
        contextRunner
                .withPropertyValues("agent-platform.security.jwt.enabled=true")
                .run(context -> assertThat(context.getBean(AgentCallerIdentityResolver.class))
                        .isInstanceOf(TrustedHeaderCallerIdentityResolver.class));
    }

    @Test
    void jwtEnabledWithADecoderBeanTakesPriorityOverHeaderTrust() {
        contextRunner
                .withUserConfiguration(StubJwtDecoderConfiguration.class)
                .withPropertyValues("agent-platform.security.jwt.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(AgentCallerIdentityResolver.class);
                    assertThat(context.getBean(AgentCallerIdentityResolver.class))
                            .isInstanceOf(JwtCallerIdentityResolver.class);
                });
    }

    @Test
    void jwtResolverNeverOverridesAnApplicationSuppliedResolver() {
        contextRunner
                .withUserConfiguration(StubJwtDecoderConfiguration.class, CustomResolverConfiguration.class)
                .withPropertyValues("agent-platform.security.jwt.enabled=true")
                .run(context -> assertThat(context.getBean(AgentCallerIdentityResolver.class))
                        .isSameAs(CustomResolverConfiguration.INSTANCE));
    }

    @Configuration(proxyBeanMethods = false)
    static class StubJwtDecoderConfiguration {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .claim("sub", "actor-1")
                    .issuedAt(Instant.now().minusSeconds(60))
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomResolverConfiguration {
        static final AgentCallerIdentityResolver INSTANCE = headers -> ai.qubere.agent.runtime.security.AgentCallerIdentity.unresolved();

        @Bean
        AgentCallerIdentityResolver agentCallerIdentityResolver() {
            return INSTANCE;
        }
    }
}

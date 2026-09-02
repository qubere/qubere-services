package ai.qubere.agent.orchestration.config;

import ai.qubere.agent.orchestration.AgentCallTool;
import ai.qubere.agent.orchestration.AgentOrchestrator;
import ai.qubere.agent.orchestration.AgentWorkflowService;
import ai.qubere.agent.orchestration.HttpRemoteAgentClient;
import ai.qubere.agent.orchestration.RemoteAgentClient;
import ai.qubere.agent.resilience.AgentResilienceGateway;
import ai.qubere.agent.runtime.AgentExecutionStore;
import ai.qubere.agent.runtime.AgentRegistry;
import ai.qubere.agent.runtime.AgentRuntimeService;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * Wires multi-agent orchestration support: the workflow rollup service and the
 * {@link AgentCallTool} agent-as-tool bridge.
 * <p>
 * {@link AgentCallTool} is opt-in through {@code agent-platform.orchestration.agent-call-tool-enabled}
 * because registering it makes every agent in the application reachable as a tool. Deployments
 * that do not orchestrate should not silently expose that capability, and those that do should
 * still restrict which agents may be delegated to via the standard tool allow-list
 * ({@code agent-platform.runtime.allowed-tools} or per-agent {@code allowed-tools}).
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentPlatformProperties.class)
public class AgentOrchestrationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(AgentExecutionStore.class)
    AgentWorkflowService agentWorkflowService(
            AgentExecutionStore executionStore,
            ObjectProvider<ai.qubere.agent.orchestration.DistributedWorkflowBudgetStore> distributedBudgetStore
    ) {
        return new AgentWorkflowService(executionStore, distributedBudgetStore.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({AgentRuntimeService.class, AgentRegistry.class})
    @ConditionalOnProperty(prefix = "agent-platform.orchestration", name = "agent-call-tool-enabled", havingValue = "true")
    AgentCallTool agentCallTool(
            AgentRuntimeService runtimeService,
            AgentRegistry registry,
            AgentPlatformProperties properties
    ) {
        return new AgentCallTool(
                runtimeService,
                registry,
                null,
                properties.getOrchestration().getMaxDelegationDepth()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(AgentRuntimeService.class)
    AgentOrchestrator agentOrchestrator(
            AgentRuntimeService runtimeService,
            @org.springframework.beans.factory.annotation.Qualifier("agentOrchestrationExecutor")
            ObjectProvider<java.util.concurrent.Executor> orchestrationExecutor,
            AgentPlatformProperties properties
    ) {
        return new AgentOrchestrator(
                runtimeService,
                orchestrationExecutor.getIfAvailable(),
                properties.getOrchestration().getMaxDelegationDepth()
        );
    }

    /**
     * Dedicated pool for parallel fan-out.
     * <p>
     * This deliberately does not reuse {@code agentInvocationExecutor}. An orchestration task
     * blocks while waiting on its sub-agent's run, and {@link AgentRuntimeService} submits that run
     * to {@code agentInvocationExecutor} for timeout handling. Sharing one bounded pool would let
     * waiting parents occupy every thread while the children they wait on sit in the queue —
     * a classic pool-induced deadlock that only appears under a wide enough fan-out.
     */
    @Bean(name = "agentOrchestrationExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "agentOrchestrationExecutor")
    org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor agentOrchestrationExecutor(
            AgentPlatformProperties properties
    ) {
        AgentPlatformProperties.RuntimeExecutor settings = properties.getRuntime().getExecutor();
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor =
                new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        executor.setCorePoolSize(settings.getCorePoolSize());
        executor.setMaxPoolSize(settings.getMaxPoolSize());
        executor.setQueueCapacity(settings.getQueueCapacity());
        executor.setThreadNamePrefix("agent-orchestrate-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(settings.getAwaitTerminationSeconds());
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "agent-platform.orchestration.remote", name = "enabled", havingValue = "true")
    RemoteAgentClient remoteAgentClient(
            RestClient.Builder restClientBuilder,
            AgentPlatformProperties properties,
            ObjectProvider<AgentResilienceGateway> resilienceGateway
    ) {
        return new HttpRemoteAgentClient(
                restClientBuilder,
                properties,
                resilienceGateway.getIfAvailable(AgentResilienceGateway::noop)
        );
    }
}

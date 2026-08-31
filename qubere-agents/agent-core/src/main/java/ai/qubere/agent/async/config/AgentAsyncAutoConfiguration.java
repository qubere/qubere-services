package ai.qubere.agent.async.config;

import ai.qubere.agent.async.AgentApprovalStore;
import ai.qubere.agent.async.AgentAsyncQueue;
import ai.qubere.agent.async.AgentAsyncRuntimeService;
import ai.qubere.agent.async.AgentAsyncWorker;
import ai.qubere.agent.async.AgentCallbackDispatcher;
import ai.qubere.agent.async.AgentPendingCommandStore;
import ai.qubere.agent.async.AgentToolApprovalRequestSink;
import ai.qubere.agent.async.InMemoryAgentApprovalStore;
import ai.qubere.agent.async.InMemoryAgentAsyncQueue;
import ai.qubere.agent.async.InMemoryAgentPendingCommandStore;
import ai.qubere.agent.async.HttpAgentCallbackDispatcher;
import ai.qubere.agent.runtime.AgentExecutionStore;
import ai.qubere.agent.runtime.AgentPolicyResolver;
import ai.qubere.agent.runtime.AgentRegistry;
import ai.qubere.agent.runtime.AgentRuntimeService;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;
import ai.qubere.agent.tools.ToolApprovalRequestSink;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@AutoConfiguration
public class AgentAsyncAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AgentAsyncQueue agentAsyncQueue() {
        return new InMemoryAgentAsyncQueue();
    }

    @Bean
    @ConditionalOnMissingBean
    AgentApprovalStore agentApprovalStore() {
        return new InMemoryAgentApprovalStore();
    }

    @Bean
    @ConditionalOnMissingBean
    AgentPendingCommandStore agentPendingCommandStore() {
        return new InMemoryAgentPendingCommandStore();
    }

    @Bean
    @ConditionalOnMissingBean
    ToolApprovalRequestSink agentToolApprovalRequestSink(
            AgentApprovalStore approvalStore,
            AgentExecutionStore executionStore,
            AgentPlatformProperties properties
    ) {
        return new AgentToolApprovalRequestSink(approvalStore, executionStore, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "agent-platform.async.callback", name = "enabled", havingValue = "true")
    AgentCallbackDispatcher httpAgentCallbackDispatcher(RestClient.Builder restClientBuilder, AgentPlatformProperties properties) {
        return new HttpAgentCallbackDispatcher(restClientBuilder, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    AgentCallbackDispatcher agentCallbackDispatcher() {
        return AgentCallbackDispatcher.noop();
    }
    @Bean
    @ConditionalOnBean(AgentRuntimeService.class)
    @ConditionalOnMissingBean
    AgentAsyncRuntimeService agentAsyncRuntimeService(
            AgentRuntimeService runtimeService,
            AgentRegistry registry,
            AgentPolicyResolver policyResolver,
            AgentExecutionStore executionStore,
            AgentAsyncQueue queue,
            AgentApprovalStore approvalStore,
            AgentPendingCommandStore pendingCommandStore,
            AgentCallbackDispatcher callbackDispatcher,
            AgentPlatformProperties properties
    ) {
        return new AgentAsyncRuntimeService(runtimeService, registry, policyResolver, executionStore, queue, approvalStore, pendingCommandStore, callbackDispatcher, properties);
    }

    @Bean
    @ConditionalOnBean(AgentAsyncRuntimeService.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "agent-platform.async", name = "worker-enabled", havingValue = "true")
    AgentAsyncWorker agentAsyncWorker(AgentAsyncRuntimeService runtimeService, AgentPlatformProperties properties) {
        return new AgentAsyncWorker(runtimeService, properties);
    }
}

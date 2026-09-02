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
import ai.qubere.agent.async.UnsupportedAgentAsyncQueue;
import ai.qubere.agent.runtime.AgentExecutionStore;
import ai.qubere.agent.runtime.AgentPolicyResolver;
import ai.qubere.agent.runtime.AgentRegistry;
import ai.qubere.agent.runtime.AgentRuntimeService;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;
import ai.qubere.agent.tools.ToolApprovalRequestSink;
import ai.qubere.agent.tools.ToolExecutionService;

import org.springframework.beans.factory.ObjectProvider;
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
    @ConditionalOnProperty(prefix = "agent-platform.async.queue", name = "type", havingValue = "memory", matchIfMissing = true)
    AgentAsyncQueue agentAsyncQueue() {
        return new InMemoryAgentAsyncQueue();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "agent-platform.async.queue", name = "type", havingValue = "database")
    AgentAsyncQueue databaseAgentAsyncQueue() {
        return new UnsupportedAgentAsyncQueue("database");
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "agent-platform.async.queue", name = "type", havingValue = "kafka")
    AgentAsyncQueue kafkaAgentAsyncQueue() {
        return new UnsupportedAgentAsyncQueue("kafka");
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "agent-platform.async.queue", name = "type", havingValue = "rabbitmq")
    AgentAsyncQueue rabbitMqAgentAsyncQueue() {
        return new UnsupportedAgentAsyncQueue("rabbitmq");
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "agent-platform.async.queue", name = "type", havingValue = "sqs")
    AgentAsyncQueue sqsAgentAsyncQueue() {
        return new UnsupportedAgentAsyncQueue("sqs");
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
    AgentCallbackDispatcher httpAgentCallbackDispatcher(
            RestClient.Builder restClientBuilder,
            AgentPlatformProperties properties,
            org.springframework.beans.factory.ObjectProvider<ai.qubere.agent.secrets.AgentSecretResolver> secretResolver
    ) {
        return new HttpAgentCallbackDispatcher(restClientBuilder, properties, secretResolver.getIfAvailable());
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
            ObjectProvider<ToolExecutionService> toolExecutionService,
            AgentCallbackDispatcher callbackDispatcher,
            AgentPlatformProperties properties
    ) {
        return new AgentAsyncRuntimeService(runtimeService, registry, policyResolver, executionStore, queue, approvalStore, pendingCommandStore, toolExecutionService.getIfAvailable(), callbackDispatcher, properties);
    }

    @Bean
    @ConditionalOnBean(AgentAsyncRuntimeService.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "agent-platform.async", name = "worker-enabled", havingValue = "true")
    AgentAsyncWorker agentAsyncWorker(AgentAsyncRuntimeService runtimeService, AgentPlatformProperties properties) {
        return new AgentAsyncWorker(runtimeService, properties);
    }
}

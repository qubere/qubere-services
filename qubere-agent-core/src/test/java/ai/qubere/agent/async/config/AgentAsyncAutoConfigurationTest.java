package ai.qubere.agent.async.config;

import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentInput;
import ai.qubere.agent.api.AgentOutput;
import ai.qubere.agent.async.AgentAsyncQueue;
import ai.qubere.agent.async.InMemoryAgentAsyncQueue;
import ai.qubere.agent.async.UnsupportedAgentAsyncQueue;
import ai.qubere.agent.runtime.AgentExecutionRecord;
import ai.qubere.agent.runtime.AgentExecutionStore;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;
import ai.qubere.agent.runtime.config.AgentRuntimeAutoConfiguration;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentAsyncAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AgentRuntimeAutoConfiguration.class,
                    AgentAsyncAutoConfiguration.class
            ))
            .withBean(AgentExecutionStore.class, NoopAgentExecutionStore::new);

    @Test
    void missingQueueTypeUsesInMemoryQueue() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AgentAsyncQueue.class);
            assertThat(context.getBean(AgentAsyncQueue.class)).isInstanceOf(InMemoryAgentAsyncQueue.class);
            assertThat(context.getBean(AgentPlatformProperties.class).getAsync().getQueue().getType()).isEqualTo("memory");
        });
    }

    @Test
    void explicitMemoryQueueTypeUsesInMemoryQueue() {
        contextRunner
                .withPropertyValues("agent-platform.async.queue.type=memory")
                .run(context -> {
                    assertThat(context).hasSingleBean(AgentAsyncQueue.class);
                    assertThat(context.getBean(AgentAsyncQueue.class)).isInstanceOf(InMemoryAgentAsyncQueue.class);
                    assertThat(context.getBean(AgentPlatformProperties.class).getAsync().getQueue().getType()).isEqualTo("memory");
                });
    }

    @Test
    void databaseQueueProviderRequiresStorageAdapterOrCustomBean() {
        contextRunner
                .withPropertyValues("agent-platform.async.queue.type=database")
                .run(context -> {
                    assertThat(context).hasSingleBean(AgentAsyncQueue.class);
                    AgentAsyncQueue queue = context.getBean(AgentAsyncQueue.class);
                    assertThat(queue).isInstanceOf(UnsupportedAgentAsyncQueue.class);
                    assertThatThrownBy(queue::size)
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("Async queue provider 'database' is configured")
                            .hasMessageContaining("Use 'memory' for local development, 'database' with agent-storage");
                });
    }
    @Test
    void productionQueueProviderPlaceholdersFailWithActionableMessage() {
        contextRunner
                .withPropertyValues("agent-platform.async.queue.type=kafka")
                .run(context -> {
                    assertThat(context).hasSingleBean(AgentAsyncQueue.class);
                    AgentAsyncQueue queue = context.getBean(AgentAsyncQueue.class);
                    assertThat(queue).isInstanceOf(UnsupportedAgentAsyncQueue.class);
                    assertThatThrownBy(() -> queue.enqueue(null))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("Async queue provider 'kafka' is configured")
                            .hasMessageContaining("provide your own AgentAsyncQueue bean");
                });
    }

    @Test
    void customQueueBeanOverridesConfiguredProvider() {
        AgentAsyncQueue customQueue = new InMemoryAgentAsyncQueue();

        contextRunner
                .withPropertyValues("agent-platform.async.queue.type=kafka")
                .withBean(AgentAsyncQueue.class, () -> customQueue)
                .run(context -> assertThat(context.getBean(AgentAsyncQueue.class)).isSameAs(customQueue));
    }

    private static class NoopAgentExecutionStore implements AgentExecutionStore {

        @Override
        public void markStarted(AgentExecutionContext context, AgentDescriptor descriptor, AgentInput input) {
        }

        @Override
        public void markCompleted(String executionId, AgentOutput output) {
        }

        @Override
        public void markFailed(String executionId, Throwable failure) {
        }

        @Override
        public Optional<AgentExecutionRecord> findByExecutionId(String executionId) {
            return Optional.empty();
        }
    }
}
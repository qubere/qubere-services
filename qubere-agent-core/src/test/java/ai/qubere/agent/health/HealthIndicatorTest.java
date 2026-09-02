package ai.qubere.agent.health;

import ai.qubere.agent.ai.AgentAiClient;
import ai.qubere.agent.async.AgentAsyncQueue;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthIndicatorTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<AgentAiClient> emptyAiClientProvider = mock(ObjectProvider.class);

    @Test
    void aiProviderIndicatorReportsUnknownWhenNoClientConfigured() {
        when(emptyAiClientProvider.getIfAvailable()).thenReturn(null);
        AgentAiProviderHealthIndicator indicator = new AgentAiProviderHealthIndicator(new AgentPlatformProperties(), emptyAiClientProvider);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aiProviderIndicatorReportsUpWhenClientConfigured() {
        ObjectProvider<AgentAiClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mock(AgentAiClient.class));
        AgentAiProviderHealthIndicator indicator = new AgentAiProviderHealthIndicator(new AgentPlatformProperties(), provider);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void asyncQueueIndicatorReportsUpWhenBelowThreshold() {
        AgentAsyncQueue queue = mock(AgentAsyncQueue.class);
        when(queue.size()).thenReturn(2);
        AgentPlatformProperties properties = new AgentPlatformProperties();
        properties.getAsync().getQueue().setMaxHealthyDepth(10);

        AgentAsyncQueueHealthIndicator indicator = new AgentAsyncQueueHealthIndicator(queue, properties);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void asyncQueueIndicatorReportsDownWhenExceedingThreshold() {
        AgentAsyncQueue queue = mock(AgentAsyncQueue.class);
        when(queue.size()).thenReturn(50);
        AgentPlatformProperties properties = new AgentPlatformProperties();
        properties.getAsync().getQueue().setMaxHealthyDepth(10);

        AgentAsyncQueueHealthIndicator indicator = new AgentAsyncQueueHealthIndicator(queue, properties);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void asyncQueueIndicatorIgnoresThresholdWhenDisabled() {
        AgentAsyncQueue queue = mock(AgentAsyncQueue.class);
        when(queue.size()).thenReturn(500);
        AgentPlatformProperties properties = new AgentPlatformProperties();
        properties.getAsync().getQueue().setMaxHealthyDepth(0);

        AgentAsyncQueueHealthIndicator indicator = new AgentAsyncQueueHealthIndicator(queue, properties);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }
}

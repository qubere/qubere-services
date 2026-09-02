package ai.qubere.agent.resilience;

import ai.qubere.agent.core.AgentErrorCode;
import ai.qubere.agent.core.AgentExecutionException;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Resilience4jAgentResilienceGatewayTest {

    @Test
    void executesSuccessfulCallNormally() {
        AgentPlatformProperties properties = new AgentPlatformProperties();
        Resilience4jAgentResilienceGateway gateway = new Resilience4jAgentResilienceGateway(properties);

        String result = gateway.execute("test-key", () -> "ok");

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void opensCircuitAfterRepeatedFailuresAndRejectsSubsequentCalls() {
        AgentPlatformProperties properties = new AgentPlatformProperties();
        properties.getResilience().setSlidingWindowSize(4);
        properties.getResilience().setFailureRateThreshold(50.0f);
        properties.getResilience().setWaitDurationInOpenStateSeconds(60);
        properties.getResilience().setBulkheadMaxConcurrentCalls(100);
        Resilience4jAgentResilienceGateway gateway = new Resilience4jAgentResilienceGateway(properties);

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> gateway.execute("flaky", () -> {
                throw new RuntimeException("boom");
            })).isInstanceOf(RuntimeException.class);
        }

        // Circuit should now be open; the next call must be rejected before it is attempted.
        assertThatThrownBy(() -> gateway.execute("flaky", () -> "should not run"))
                .isInstanceOfSatisfying(AgentExecutionException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(AgentErrorCode.AI_PROVIDER_UNAVAILABLE));
    }

    @Test
    void tracksDifferentKeysIndependently() {
        AgentPlatformProperties properties = new AgentPlatformProperties();
        properties.getResilience().setSlidingWindowSize(2);
        properties.getResilience().setFailureRateThreshold(50.0f);
        properties.getResilience().setWaitDurationInOpenStateSeconds(60);
        Resilience4jAgentResilienceGateway gateway = new Resilience4jAgentResilienceGateway(properties);

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> gateway.execute("model-a", () -> {
                throw new RuntimeException("boom");
            })).isInstanceOf(RuntimeException.class);
        }

        // model-a's circuit is open, but model-b is unrelated and must still work.
        String result = gateway.execute("model-b", () -> "ok");

        assertThat(result).isEqualTo("ok");
    }
}

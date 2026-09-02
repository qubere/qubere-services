package ai.qubere.agent.resilience;

import ai.qubere.agent.core.AgentErrorCode;
import ai.qubere.agent.core.AgentExecutionException;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import java.time.Duration;
import java.util.function.Supplier;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.decorators.Decorators;

/**
 * Resilience4j-backed {@link AgentResilienceGateway}. One circuit breaker and one bulkhead
 * instance is created per distinct {@code key} (e.g. per model name or per tool name) so an
 * outage/overload isolated to one provider or tool does not trip the breaker for unrelated keys.
 */
public class Resilience4jAgentResilienceGateway implements AgentResilienceGateway {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final BulkheadRegistry bulkheadRegistry;

    public Resilience4jAgentResilienceGateway(AgentPlatformProperties properties) {
        AgentPlatformProperties.Resilience resilience = properties.getResilience();
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(resilience.getFailureRateThreshold())
                .slidingWindowSize(resilience.getSlidingWindowSize())
                .waitDurationInOpenState(Duration.ofSeconds(resilience.getWaitDurationInOpenStateSeconds()))
                .permittedNumberOfCallsInHalfOpenState(resilience.getPermittedNumberOfCallsInHalfOpenState())
                .build();
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);

        BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(resilience.getBulkheadMaxConcurrentCalls())
                .maxWaitDuration(Duration.ofMillis(resilience.getBulkheadMaxWaitDurationMillis()))
                .build();
        this.bulkheadRegistry = BulkheadRegistry.of(bulkheadConfig);
    }

    @Override
    public <T> T execute(String key, Supplier<T> call) {
        String safeKey = key == null || key.isBlank() ? "default" : key;
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(safeKey);
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(safeKey);
        Supplier<T> decorated = Decorators.ofSupplier(call)
                .withCircuitBreaker(circuitBreaker)
                .withBulkhead(bulkhead)
                .decorate();
        try {
            return decorated.get();
        } catch (CallNotPermittedException ex) {
            throw new AgentExecutionException(
                    AgentErrorCode.AI_PROVIDER_UNAVAILABLE,
                    "Call rejected: circuit breaker is open for " + safeKey,
                    ex
            );
        } catch (BulkheadFullException ex) {
            throw new AgentExecutionException(
                    AgentErrorCode.AI_PROVIDER_UNAVAILABLE,
                    "Call rejected: concurrency bulkhead is full for " + safeKey,
                    ex
            );
        }
    }
}

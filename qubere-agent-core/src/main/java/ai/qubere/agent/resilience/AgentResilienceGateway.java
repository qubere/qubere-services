package ai.qubere.agent.resilience;

import java.util.function.Supplier;

/**
 * Governs how AI provider and tool calls are executed with respect to resilience patterns
 * (circuit breaking, bulkhead concurrency limiting). This is the framework's extension point so
 * {@code SpringAiAgentClient} and {@code ToolExecutionService} never fail-fast/retry-storm a
 * flaky provider or tool endpoint.
 * <p>
 * The default implementation is a pass-through no-op so the framework does not require a
 * resilience library on the classpath. Deployed applications get a real
 * Resilience4j-backed implementation automatically once {@code agent-platform.resilience.enabled=true}
 * and Resilience4j is present on the classpath.
 */
public interface AgentResilienceGateway {

    /**
     * Executes {@code call} under whatever resilience policy is configured for {@code key}
     * (typically {@code "ai:" + modelName} or {@code "tool:" + toolName}).
     *
     * @throws ai.qubere.agent.core.AgentExecutionException with
     *         {@link ai.qubere.agent.core.AgentErrorCode#AI_PROVIDER_UNAVAILABLE} or
     *         {@link ai.qubere.agent.core.AgentErrorCode#TOOL_FAILED} when the call is rejected
     *         by an open circuit or exhausted bulkhead before it is attempted.
     */
    <T> T execute(String key, Supplier<T> call);

    static AgentResilienceGateway noop() {
        return new AgentResilienceGateway() {
            @Override
            public <T> T execute(String key, Supplier<T> call) {
                return call.get();
            }
        };
    }
}

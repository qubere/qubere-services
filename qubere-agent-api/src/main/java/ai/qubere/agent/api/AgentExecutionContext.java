package ai.qubere.agent.api;

import java.time.Instant;
import java.util.Map;

public record AgentExecutionContext(
        String executionId,
        String tenantId,
        String actorId,
        String correlationId,
        Instant requestedAt,
        Map<String, Object> attributes
) {
    public AgentExecutionContext {
        requestedAt = requestedAt == null ? Instant.now() : requestedAt;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}

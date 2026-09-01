package ai.qubere.agent.observability;

import java.time.Instant;
import java.util.Map;

public record AgentTraceEvent(
        String executionId,
        String eventType,
        Instant occurredAt,
        Map<String, Object> attributes
) {
    public AgentTraceEvent {
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}

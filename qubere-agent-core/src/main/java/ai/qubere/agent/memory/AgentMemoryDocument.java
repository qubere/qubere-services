package ai.qubere.agent.memory;

import java.time.Instant;
import java.util.Map;

public record AgentMemoryDocument(
        String id,
        String tenantId,
        String namespace,
        String content,
        Map<String, Object> metadata,
        Instant createdAt
) {
    public AgentMemoryDocument {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}

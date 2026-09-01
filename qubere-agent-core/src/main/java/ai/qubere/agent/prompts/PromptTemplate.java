package ai.qubere.agent.prompts;

import java.time.Instant;
import java.util.Map;

public record PromptTemplate(
        String promptId,
        String agentId,
        String version,
        PromptStatus status,
        String systemTemplate,
        String userTemplate,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {
    public PromptTemplate {
        status = status == null ? PromptStatus.DRAFT : status;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }
}

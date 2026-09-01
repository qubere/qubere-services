package ai.qubere.agent.memory;

import java.util.Map;

public record MemoryHit(
        String id,
        String content,
        double score,
        Map<String, Object> metadata
) {
    public MemoryHit {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}

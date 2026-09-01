package ai.qubere.agent.memory;

import java.util.Map;

public record AgentMemoryQuery(
        String tenantId,
        String namespace,
        String query,
        int limit,
        Map<String, Object> filters
) {
    public AgentMemoryQuery {
        filters = filters == null ? Map.of() : Map.copyOf(filters);
    }
}

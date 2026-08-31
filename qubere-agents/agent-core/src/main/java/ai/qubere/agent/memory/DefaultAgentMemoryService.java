package ai.qubere.agent.memory;

import java.util.List;
import java.util.UUID;

public class DefaultAgentMemoryService implements AgentMemoryService {

    private static final String DEFAULT_NAMESPACE = "default";

    private final AgentVectorMemoryStore vectorMemoryStore;

    public DefaultAgentMemoryService(AgentVectorMemoryStore vectorMemoryStore) {
        this.vectorMemoryStore = vectorMemoryStore;
    }

    @Override
    public List<MemoryHit> search(AgentMemoryQuery query) {
        return vectorMemoryStore.search(query);
    }

    @Override
    public void remember(String tenantId, String namespace, String content) {
        vectorMemoryStore.upsert(new AgentMemoryDocument(
                UUID.randomUUID().toString(),
                tenantId,
                namespace == null || namespace.isBlank() ? DEFAULT_NAMESPACE : namespace,
                content,
                null,
                null
        ));
    }
}

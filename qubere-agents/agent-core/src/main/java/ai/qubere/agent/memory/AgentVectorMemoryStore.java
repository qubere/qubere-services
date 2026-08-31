package ai.qubere.agent.memory;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AgentVectorMemoryStore {

    void upsert(AgentMemoryDocument document);

    default void upsertAll(Collection<AgentMemoryDocument> documents) {
        if (documents == null) {
            return;
        }
        documents.forEach(this::upsert);
    }

    List<MemoryHit> search(AgentMemoryQuery query);

    Optional<AgentMemoryDocument> findById(String tenantId, String namespace, String id);
}

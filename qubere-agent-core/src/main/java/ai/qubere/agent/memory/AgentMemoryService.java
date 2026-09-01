package ai.qubere.agent.memory;

import java.util.List;

public interface AgentMemoryService {

    List<MemoryHit> search(AgentMemoryQuery query);

    void remember(String tenantId, String namespace, String content);
}

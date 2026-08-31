package ai.qubere.agent.memory;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryAgentVectorMemoryStoreTest {

    @Test
    void searchesOnlyInsideTenantAndNamespaceScope() {
        InMemoryAgentVectorMemoryStore store = new InMemoryAgentVectorMemoryStore();
        store.upsert(new AgentMemoryDocument("1", "tenant-a", "cases", "invoice review required", Map.of("type", "case"), null));
        store.upsert(new AgentMemoryDocument("2", "tenant-b", "cases", "invoice review required", Map.of(), null));
        store.upsert(new AgentMemoryDocument("3", "tenant-a", "users", "invoice review required", Map.of(), null));

        assertThat(store.search(new AgentMemoryQuery("tenant-a", "cases", "invoice", 10, Map.of())))
                .hasSize(1)
                .first()
                .extracting(MemoryHit::id)
                .isEqualTo("1");
    }
}

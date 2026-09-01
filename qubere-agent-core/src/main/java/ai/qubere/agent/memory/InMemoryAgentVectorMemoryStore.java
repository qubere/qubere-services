package ai.qubere.agent.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAgentVectorMemoryStore implements AgentVectorMemoryStore {

    private final Map<String, AgentMemoryDocument> documentsByScopeAndId = new ConcurrentHashMap<>();

    @Override
    public void upsert(AgentMemoryDocument document) {
        if (document == null) {
            return;
        }
        documentsByScopeAndId.put(key(document.tenantId(), document.namespace(), document.id()), document);
    }

    @Override
    public List<MemoryHit> search(AgentMemoryQuery query) {
        if (query == null || query.tenantId() == null || query.namespace() == null || query.query() == null) {
            return List.of();
        }
        String normalizedQuery = query.query().toLowerCase();
        return documentsByScopeAndId.values().stream()
                .filter(document -> query.tenantId().equals(document.tenantId()))
                .filter(document -> query.namespace().equals(document.namespace()))
                .map(document -> toHit(document, normalizedQuery))
                .filter(hit -> hit.score() > 0)
                .sorted(Comparator.comparingDouble(MemoryHit::score).reversed())
                .limit(Math.max(query.limit(), 0))
                .toList();
    }

    @Override
    public Optional<AgentMemoryDocument> findById(String tenantId, String namespace, String id) {
        return Optional.ofNullable(documentsByScopeAndId.get(key(tenantId, namespace, id)));
    }

    private MemoryHit toHit(AgentMemoryDocument document, String normalizedQuery) {
        List<String> queryTerms = new ArrayList<>(List.of(normalizedQuery.split("\\s+")));
        long matches = queryTerms.stream()
                .filter(term -> !term.isBlank())
                .filter(term -> document.content().toLowerCase().contains(term))
                .count();
        double score = queryTerms.isEmpty() ? 0 : (double) matches / queryTerms.size();
        return new MemoryHit(document.id(), document.content(), score, document.metadata());
    }

    private String key(String tenantId, String namespace, String id) {
        return "%s::%s::%s".formatted(tenantId, namespace, id);
    }
}

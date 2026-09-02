package ai.qubere.agent.memory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * Adapts any Spring AI {@link VectorStore} to the framework's provider-neutral
 * {@link AgentVectorMemoryStore}, giving agents real durable retrieval (pgvector, Redis, Chroma,
 * Azure AI Search, ...) instead of the in-memory default.
 * <p>
 * The framework deliberately adapts the generic {@code VectorStore} interface rather than binding
 * to one vector database: applications choose their provider by adding the matching Spring AI
 * starter, and this adapter works unchanged.
 * <p>
 * <b>Tenant isolation.</b> Framework standard 3.7 requires memory to be scoped by security
 * boundary and standard 16 forbids cross-tenant retrieval. Every document written through this
 * adapter carries {@code tenantId} and {@code namespace} metadata, and every search applies a
 * metadata filter on both. A query without a tenant id is rejected rather than silently searching
 * across all tenants — failing closed is the only safe default for multi-tenant retrieval.
 */
public class SpringAiVectorMemoryStore implements AgentVectorMemoryStore {

    public static final String METADATA_TENANT_ID = "tenantId";
    public static final String METADATA_NAMESPACE = "namespace";
    public static final String METADATA_DOCUMENT_ID = "agentDocumentId";

    private static final int DEFAULT_TOP_K = 5;

    private final VectorStore vectorStore;

    public SpringAiVectorMemoryStore(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void upsert(AgentMemoryDocument document) {
        if (document == null) {
            return;
        }
        requireTenant(document.tenantId(), "Memory document requires a tenantId so retrieval can be tenant-scoped");
        vectorStore.add(List.of(toSpringDocument(document)));
    }

    @Override
    public void upsertAll(java.util.Collection<AgentMemoryDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        List<Document> springDocuments = new ArrayList<>(documents.size());
        for (AgentMemoryDocument document : documents) {
            if (document == null) {
                continue;
            }
            requireTenant(document.tenantId(), "Memory document requires a tenantId so retrieval can be tenant-scoped");
            springDocuments.add(toSpringDocument(document));
        }
        if (!springDocuments.isEmpty()) {
            // Batch insert so providers that support bulk upsert avoid a round trip per document.
            vectorStore.add(springDocuments);
        }
    }

    @Override
    public List<MemoryHit> search(AgentMemoryQuery query) {
        if (query == null || query.query() == null || query.query().isBlank()) {
            return List.of();
        }
        requireTenant(query.tenantId(), "Memory query requires a tenantId; cross-tenant retrieval is not permitted");

        SearchRequest.Builder request = SearchRequest.builder()
                .query(query.query())
                .topK(query.limit() > 0 ? query.limit() : DEFAULT_TOP_K)
                .filterExpression(scopeFilter(query));

        List<Document> results = vectorStore.similaritySearch(request.build());
        if (results == null) {
            return List.of();
        }
        return results.stream().map(this::toMemoryHit).toList();
    }

    @Override
    public Optional<AgentMemoryDocument> findById(String tenantId, String namespace, String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        requireTenant(tenantId, "Memory lookup requires a tenantId; cross-tenant retrieval is not permitted");

        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op expression = builder.and(
                builder.eq(METADATA_TENANT_ID, tenantId),
                builder.eq(METADATA_DOCUMENT_ID, id)
        );
        if (namespace != null && !namespace.isBlank()) {
            expression = builder.and(expression, builder.eq(METADATA_NAMESPACE, namespace));
        }

        // Vector stores are similarity-search engines, not key-value stores. Looking up by id is
        // expressed as a filtered search using the document's own text as the query so a provider
        // that requires a query vector still returns the intended row.
        List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
                .query(id)
                .topK(1)
                .similarityThresholdAll()
                .filterExpression(expression.build())
                .build());

        if (results == null || results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toMemoryDocument(results.get(0)));
    }

    private Filter.Expression scopeFilter(AgentMemoryQuery query) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op expression = builder.eq(METADATA_TENANT_ID, query.tenantId());
        if (query.namespace() != null && !query.namespace().isBlank()) {
            expression = builder.and(expression, builder.eq(METADATA_NAMESPACE, query.namespace()));
        }
        for (Map.Entry<String, Object> filter : query.filters().entrySet()) {
            if (filter.getValue() != null) {
                expression = builder.and(expression, builder.eq(filter.getKey(), filter.getValue()));
            }
        }
        return expression.build();
    }

    private Document toSpringDocument(AgentMemoryDocument document) {
        Map<String, Object> metadata = new LinkedHashMap<>(document.metadata());
        metadata.put(METADATA_TENANT_ID, document.tenantId());
        if (document.namespace() != null) {
            metadata.put(METADATA_NAMESPACE, document.namespace());
        }
        if (document.id() != null) {
            metadata.put(METADATA_DOCUMENT_ID, document.id());
        }
        if (document.createdAt() != null) {
            metadata.put("createdAt", document.createdAt().toString());
        }
        return document.id() == null
                ? new Document(document.content(), metadata)
                : new Document(document.id(), document.content(), metadata);
    }

    private MemoryHit toMemoryHit(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        return new MemoryHit(
                asString(metadata.getOrDefault(METADATA_DOCUMENT_ID, document.getId())),
                document.getText(),
                document.getScore() == null ? 0.0d : document.getScore(),
                metadata
        );
    }

    private AgentMemoryDocument toMemoryDocument(Document document) {
        Map<String, Object> metadata = new LinkedHashMap<>(document.getMetadata());
        String tenantId = asString(metadata.get(METADATA_TENANT_ID));
        String namespace = asString(metadata.get(METADATA_NAMESPACE));
        String documentId = asString(metadata.getOrDefault(METADATA_DOCUMENT_ID, document.getId()));
        return new AgentMemoryDocument(documentId, tenantId, namespace, document.getText(), metadata, null);
    }

    private void requireTenant(String tenantId, String message) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}

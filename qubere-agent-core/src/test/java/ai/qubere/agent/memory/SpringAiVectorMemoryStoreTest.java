package ai.qubere.agent.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringAiVectorMemoryStoreTest {

    private final RecordingVectorStore vectorStore = new RecordingVectorStore();
    private final SpringAiVectorMemoryStore store = new SpringAiVectorMemoryStore(vectorStore);

    @Test
    void stampsTenantAndNamespaceMetadataOnStoredDocuments() {
        store.upsert(new AgentMemoryDocument(
                "doc-1", "tenant-a", "invoices", "invoice content", Map.of("category", "finance"), null
        ));

        assertThat(vectorStore.added).hasSize(1);
        Map<String, Object> metadata = vectorStore.added.get(0).getMetadata();
        assertThat(metadata)
                .containsEntry(SpringAiVectorMemoryStore.METADATA_TENANT_ID, "tenant-a")
                .containsEntry(SpringAiVectorMemoryStore.METADATA_NAMESPACE, "invoices")
                .containsEntry(SpringAiVectorMemoryStore.METADATA_DOCUMENT_ID, "doc-1")
                .containsEntry("category", "finance");
    }

    @Test
    void batchesUpsertAllIntoASingleVectorStoreCall() {
        store.upsertAll(List.of(
                new AgentMemoryDocument("doc-1", "tenant-a", "ns", "one", Map.of(), null),
                new AgentMemoryDocument("doc-2", "tenant-a", "ns", "two", Map.of(), null)
        ));

        assertThat(vectorStore.addCallCount).isEqualTo(1);
        assertThat(vectorStore.added).hasSize(2);
    }

    @Test
    void rejectsWriteWithoutTenantSoDocumentsCannotEscapeTenantScoping() {
        assertThatThrownBy(() -> store.upsert(
                new AgentMemoryDocument("doc-1", null, "ns", "content", Map.of(), null)
        )).isInstanceOf(IllegalArgumentException.class);

        assertThat(vectorStore.added).isEmpty();
    }

    @Test
    void rejectsSearchWithoutTenantRatherThanSearchingAcrossAllTenants() {
        assertThatThrownBy(() -> store.search(new AgentMemoryQuery(null, "ns", "find things", 5, Map.of())))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(vectorStore.searchRequests).isEmpty();
    }

    @Test
    void appliesTenantAndNamespaceFilterToEverySearch() {
        vectorStore.results = List.of(document("doc-1", "tenant-a", "ns", "hit"));

        store.search(new AgentMemoryQuery("tenant-a", "ns", "find things", 3, Map.of()));

        assertThat(vectorStore.searchRequests).hasSize(1);
        SearchRequest request = vectorStore.searchRequests.get(0);
        assertThat(request.getTopK()).isEqualTo(3);
        assertThat(request.getFilterExpression()).isNotNull();
        // The filter must reference the tenant so the provider cannot return other tenants' rows.
        assertThat(request.getFilterExpression().toString()).contains("tenantId");
    }

    @Test
    void mapsProviderResultsIntoMemoryHitsWithScore() {
        Document result = Document.builder()
                .id("doc-1")
                .text("relevant content")
                .metadata(Map.of(
                        SpringAiVectorMemoryStore.METADATA_TENANT_ID, "tenant-a",
                        SpringAiVectorMemoryStore.METADATA_DOCUMENT_ID, "doc-1"
                ))
                .score(0.87d)
                .build();
        vectorStore.results = List.of(result);

        List<MemoryHit> hits = store.search(new AgentMemoryQuery("tenant-a", "ns", "query", 5, Map.of()));

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).id()).isEqualTo("doc-1");
        assertThat(hits.get(0).content()).isEqualTo("relevant content");
        assertThat(hits.get(0).score()).isEqualTo(0.87d);
    }

    @Test
    void blankQueryReturnsNoHitsWithoutCallingTheProvider() {
        List<MemoryHit> hits = store.search(new AgentMemoryQuery("tenant-a", "ns", "   ", 5, Map.of()));

        assertThat(hits).isEmpty();
        assertThat(vectorStore.searchRequests).isEmpty();
    }

    @Test
    void findByIdRequiresTenantScope() {
        assertThatThrownBy(() -> store.findById(null, "ns", "doc-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findByIdReturnsEmptyWhenProviderHasNoMatch() {
        vectorStore.results = List.of();

        assertThat(store.findById("tenant-a", "ns", "missing")).isEmpty();
    }

    private Document document(String id, String tenantId, String namespace, String text) {
        return new Document(id, text, Map.of(
                SpringAiVectorMemoryStore.METADATA_TENANT_ID, tenantId,
                SpringAiVectorMemoryStore.METADATA_NAMESPACE, namespace,
                SpringAiVectorMemoryStore.METADATA_DOCUMENT_ID, id
        ));
    }

    private static final class RecordingVectorStore implements VectorStore {
        private final List<Document> added = new ArrayList<>();
        private final List<SearchRequest> searchRequests = new ArrayList<>();
        private int addCallCount;
        private List<Document> results = List.of();

        @Override
        public void add(List<Document> documents) {
            addCallCount++;
            added.addAll(documents);
        }

        @Override
        public void delete(List<String> idList) {
        }

        @Override
        public void delete(Filter.Expression filterExpression) {
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            searchRequests.add(request);
            return results;
        }
    }
}

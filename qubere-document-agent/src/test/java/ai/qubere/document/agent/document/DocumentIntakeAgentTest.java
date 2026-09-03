package ai.qubere.document.agent.document;

import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.core.AgentResult;
import ai.qubere.agent.core.GenericAgentInput;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentIntakeAgentTest {

    private final DocumentIntakeAgent agent = new DocumentIntakeAgent();

    @Test
    void classifiesUsingTheDocumentTypeCatalogNotAdHocFilenameRules() {
        AgentResult<Map<String, Object>> result = agent.run(
                new GenericAgentInput(Map.of("fileName", "ocean bill of lading MAWB.pdf")),
                context()
        );

        assertThat(result.value()).containsEntry("status", "Completed");
        assertThat(result.value().get("detectedTypes")).isEqualTo(java.util.List.of("OCEAN_BILL_OF_LADING"));
        assertThat(result.value()).containsEntry("isRequiredForFiling", true);
    }

    @Test
    void explicitOverrideIsAnExactCatalogLookupWithFullConfidence() {
        AgentResult<Map<String, Object>> result = agent.run(
                new GenericAgentInput(Map.of("fileName", "scan001.pdf", "docTypeOverride", "PACKING_LIST")),
                context()
        );

        assertThat(result.value()).containsEntry("overallConfidence", 1.0d);
        assertThat(result.value().get("detectedTypes")).isEqualTo(java.util.List.of("PACKING_LIST"));
        assertThat(result.decision().decision()).isEqualTo("DOCUMENT_INTAKE_ACCEPTED");
    }

    @Test
    void headerTextExcerptTakesPrecedenceOverFileNameWhenBothSupplied() {
        AgentResult<Map<String, Object>> result = agent.run(
                new GenericAgentInput(Map.of(
                        "fileName", "scan001.pdf",
                        "headerTextExcerpt", "Commercial Invoice - Invoice No. 4471"
                )),
                context()
        );

        assertThat(result.value().get("detectedTypes")).isEqualTo(java.util.List.of("COMMERCIAL_INVOICE"));
    }

    @Test
    void unrecognizedDocumentRequiresReviewRatherThanGuessing() {
        AgentResult<Map<String, Object>> result = agent.run(
                new GenericAgentInput(Map.of("fileName", "scan001.pdf")),
                context()
        );

        assertThat(result.value()).containsEntry("status", "Review Required");
        assertThat(result.value().get("detectedTypes")).isEqualTo(java.util.List.of(DocumentTypeCatalog.OTHER_UNVERIFIED_DOCUMENT));
    }

    private AgentExecutionContext context() {
        return new AgentExecutionContext("exec-1", "tenant-1", "actor-1", "corr-1", Instant.now(), Map.of());
    }
}

package ai.qubere.document.agent.document;

import ai.qubere.agent.ai.AgentAiClient;
import ai.qubere.agent.ai.AgentPrompt;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.core.AgentResult;
import ai.qubere.agent.core.GenericAgentInput;
import ai.qubere.document.agent.document.DocumentIntelligenceAgent.DocumentClassification;
import ai.qubere.document.agent.document.DocumentIntelligenceAgent.DocumentIntelligenceExtraction;
import ai.qubere.document.agent.document.DocumentIntelligenceAgent.LineItemExtraction;
import ai.qubere.document.agent.document.DocumentIntelligenceAgent.TradeMetadataExtraction;
import ai.qubere.document.agent.document.review.ExtractionReviewService;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class DocumentIntelligenceAgentTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<AgentAiClient> aiClientProvider = Mockito.mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<com.fasterxml.jackson.databind.ObjectMapper> objectMapperProvider = Mockito.mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ExtractionReviewService> reviewServiceProvider = Mockito.mock(ObjectProvider.class);
    private final AgentAiClient aiClient = Mockito.mock(AgentAiClient.class);

    private final DocumentIntelligenceAgent agent = new DocumentIntelligenceAgent(aiClientProvider, objectMapperProvider, reviewServiceProvider);

    @Test
    void withNoAiClientConfiguredEveryFieldIsNullNotFabricated() {
        Mockito.when(aiClientProvider.getIfAvailable()).thenReturn(null);
        Mockito.when(objectMapperProvider.getIfAvailable(any())).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());

        AgentResult<Map<String, Object>> result = agent.run(
                new GenericAgentInput(Map.of("documentContext", "Commercial Invoice text", "fileName", "invoice.pdf")),
                context()
        );

        assertThat(result.value()).containsEntry("status", "Review Required");
        assertThat(result.value()).containsEntry("extractionStatus", "failed");
        assertThat(result.value().get("documentClassification")).isNull();
        assertThat(result.value().get("tradeMetadata")).isNull();
        assertThat((List<?>) result.value().get("warnings")).anyMatch(w -> w.toString().contains("No AgentAiClient is configured"));
        Mockito.verifyNoInteractions(aiClient);
    }

    @Test
    void blankDocumentContextFailsWithoutCallingTheModel() {
        Mockito.when(aiClientProvider.getIfAvailable()).thenReturn(aiClient);
        Mockito.when(objectMapperProvider.getIfAvailable(any())).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());

        AgentResult<Map<String, Object>> result = agent.run(
                new GenericAgentInput(Map.of("fileName", "invoice.pdf")),
                context()
        );

        assertThat(result.value()).containsEntry("extractionStatus", "failed");
        Mockito.verifyNoInteractions(aiClient);
    }

    @Test
    void highConfidenceExtractionCompletesWithFullTradeMetadataPreserved() {
        Mockito.when(aiClientProvider.getIfAvailable()).thenReturn(aiClient);
        Mockito.when(objectMapperProvider.getIfAvailable(any())).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());

        DocumentIntelligenceExtraction extraction = new DocumentIntelligenceExtraction(
                "success",
                new DocumentClassification("Commercial Invoice", 92, null),
                new TradeMetadataExtraction(
                        "CN", "CN", "US", "8471.30.0100", "Acme Exports", "Acme Imports LLC",
                        "Acme Imports LLC", null, "INV-1001", null, "2026-01-15", "USD", 15000.00,
                        "FOB", "Shanghai", "Los Angeles", null, null, null, "Ocean Carrier Co",
                        null, "1200 KG", null, "500", null, null, null, null
                ),
                List.of(new LineItemExtraction(1, "SKU-1", "Widget", 100.0, 15.0, 1500.0, "EA", "CN", "8471.30.0100")),
                List.of(),
                List.of()
        );
        Mockito.when(aiClient.generate(any(AgentPrompt.class), eq(DocumentIntelligenceExtraction.class), any()))
                .thenReturn(extraction);

        AgentResult<Map<String, Object>> result = agent.run(
                new GenericAgentInput(Map.of(
                        "documentContext", "Commercial Invoice from Acme Exports...",
                        "fileName", "invoice.pdf",
                        "shipmentId", "shipment-1",
                        "documentId", "doc-1"
                )),
                context()
        );

        assertThat(result.value()).containsEntry("status", "Completed");
        assertThat(result.value()).containsEntry("extractionStatus", "success");

        @SuppressWarnings("unchecked")
        Map<String, Object> classification = (Map<String, Object>) result.value().get("documentClassification");
        assertThat(classification).containsEntry("documentType", "Commercial Invoice");
        assertThat(classification).containsEntry("confidence", 92);
        assertThat(classification).containsEntry("notes", null);

        @SuppressWarnings("unchecked")
        Map<String, Object> tradeMetadata = (Map<String, Object>) result.value().get("tradeMetadata");
        assertThat(tradeMetadata).containsEntry("invoiceNumber", "INV-1001");
        assertThat(tradeMetadata).containsEntry("currency", "USD");
        assertThat(tradeMetadata).containsEntry("totalValue", 15000.00);
        assertThat(tradeMetadata).containsEntry("notifyParty", null);
        assertThat(tradeMetadata).containsEntry("poNumber", null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lineItems = (List<Map<String, Object>>) result.value().get("lineItems");
        assertThat(lineItems).hasSize(1);
        assertThat(lineItems.get(0)).containsEntry("description", "Widget");

        assertThat(result.decision().confidence()).isEqualTo(0.92d);
    }

    @Test
    void lowConfidenceExtractionRequiresReviewEvenWhenStatusIsSuccess() {
        Mockito.when(aiClientProvider.getIfAvailable()).thenReturn(aiClient);
        Mockito.when(objectMapperProvider.getIfAvailable(any())).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());

        DocumentIntelligenceExtraction extraction = new DocumentIntelligenceExtraction(
                "success",
                new DocumentClassification("other", 40, "Ambiguous layout"),
                new TradeMetadataExtraction(
                        null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null
                ),
                List.of(),
                List.of("Could not confidently classify the document"),
                List.of()
        );
        Mockito.when(aiClient.generate(any(AgentPrompt.class), eq(DocumentIntelligenceExtraction.class), any()))
                .thenReturn(extraction);

        AgentResult<Map<String, Object>> result = agent.run(
                new GenericAgentInput(Map.of("documentContext", "Ambiguous scan text", "fileName", "scan.pdf")),
                context()
        );

        assertThat(result.value()).containsEntry("status", "Review Required");
    }

    @Test
    void extractionStatusFailedAlwaysRequiresReviewRegardlessOfConfidence() {
        Mockito.when(aiClientProvider.getIfAvailable()).thenReturn(aiClient);
        Mockito.when(objectMapperProvider.getIfAvailable(any())).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());

        DocumentIntelligenceExtraction extraction = new DocumentIntelligenceExtraction(
                "failed",
                new DocumentClassification("Commercial Invoice", 95, null),
                null,
                List.of(),
                List.of(),
                List.of("Context did not resemble a readable document")
        );
        Mockito.when(aiClient.generate(any(AgentPrompt.class), eq(DocumentIntelligenceExtraction.class), any()))
                .thenReturn(extraction);

        AgentResult<Map<String, Object>> result = agent.run(
                new GenericAgentInput(Map.of("documentContext", "garbled text", "fileName", "scan.pdf")),
                context()
        );

        assertThat(result.value()).containsEntry("status", "Review Required");
    }

    private AgentExecutionContext context() {
        return new AgentExecutionContext("exec-1", "tenant-1", "actor-1", "corr-1", Instant.now(), Map.of());
    }
}

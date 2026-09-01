package ai.qubere.document.agent.document;

import ai.qubere.agent.ai.AgentAiClient;
import ai.qubere.agent.ai.AgentAiRequestMetadata;
import ai.qubere.agent.ai.AgentPrompt;
import ai.qubere.agent.api.Agent;
import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentRiskLevel;
import ai.qubere.agent.core.AgentDecisionDraft;
import ai.qubere.agent.core.AgentEvidenceDraft;
import ai.qubere.agent.core.AgentResult;
import ai.qubere.agent.core.GenericAgentInput;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class DocumentIntelligenceAgent implements Agent<GenericAgentInput, AgentResult<Map<String, Object>>> {

    public static final String AGENT_ID = "document.intelligence";

    private static final AgentDescriptor DESCRIPTOR = new AgentDescriptor(
            AGENT_ID,
            "Document Intelligence Agent",
            "0.1.0",
            "Extracts evidence-backed trade metadata and line-item facts from parsed document context.",
            AgentRiskLevel.MEDIUM,
            Set.of("document-extraction", "trade-metadata", "line-items", "evidence")
    );

    private final ObjectProvider<AgentAiClient> aiClientProvider;

    public DocumentIntelligenceAgent(ObjectProvider<AgentAiClient> aiClientProvider) {
        this.aiClientProvider = aiClientProvider;
    }

    @Override
    public AgentDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentResult<Map<String, Object>> run(GenericAgentInput input, AgentExecutionContext context) {
        Map<String, Object> values = input.values();
        String documentText = text(values.get("documentContext"));
        String fileName = text(values.get("fileName"));
        AgentAiClient aiClient = aiClientProvider.getIfAvailable();

        Map<String, Object> extraction = aiClient == null
                ? deterministicPlaceholder(values, fileName, documentText)
                : aiExtraction(aiClient, context, fileName, documentText);

        double confidence = extraction.get("confidence") instanceof Number number ? number.doubleValue() : 0.50d;
        String status = confidence >= 0.70d ? "Completed" : "Review Required";
        Map<String, Object> output = Map.of(
                "packetId", textOrDefault(values.get("packetId"), "pkt_pending"),
                "shipmentId", text(values.get("shipmentId")),
                "documentId", text(values.get("documentId")),
                "status", status,
                "extractionStatus", confidence >= 0.70d ? "success" : "partial",
                "tradeMetadata", extraction,
                "lineItems", List.of(),
                "migrationStatus", aiClient == null ? "placeholder-no-ai-client" : "ai-backed-initial-port"
        );

        return new AgentResult<>(
                output,
                new AgentDecisionDraft(
                        status.equals("Completed") ? "DOCUMENT_EXTRACTION_COMPLETED" : "DOCUMENT_EXTRACTION_REVIEW_REQUIRED",
                        "Document Intelligence initial Java port produced trade metadata scaffold output.",
                        confidence
                ),
                List.of(new AgentEvidenceDraft("documentContext", documentText.isBlank() ? "missing" : "supplied", "Extraction is grounded in supplied documentContext text when present.")),
                Map.of("agentId", AGENT_ID, "source", "app-frontend migration scaffold", "aiBacked", aiClient != null)
        );
    }

    private Map<String, Object> aiExtraction(AgentAiClient aiClient, AgentExecutionContext context, String fileName, String documentText) {
        AgentPrompt prompt = new AgentPrompt(
                "You are Qubere Document Intelligence. Extract only values visible in the supplied document context. If absent, return null-like empty strings and low confidence.",
                "File name: " + fileName + "\nDocument context:\n" + documentText,
                Map.of("fileName", fileName, "hasDocumentContext", !documentText.isBlank())
        );
        DocumentExtractionResponse response = aiClient.generate(prompt, DocumentExtractionResponse.class, AgentAiRequestMetadata.from(context));
        return Map.of(
                "invoiceNumber", safe(response.invoiceNumber()),
                "currency", safe(response.currency()),
                "totalValue", safe(response.totalValue()),
                "exporterName", safe(response.exporterName()),
                "importerName", safe(response.importerName()),
                "countryOfOrigin", safe(response.countryOfOrigin()),
                "confidence", response.confidence()
        );
    }

    private Map<String, Object> deterministicPlaceholder(Map<String, Object> values, String fileName, String documentText) {
        return Map.of(
                "invoiceNumber", "",
                "currency", "",
                "totalValue", "",
                "exporterName", "",
                "importerName", "",
                "countryOfOrigin", "",
                "confidence", documentText.isBlank() ? 0.35d : 0.55d,
                "note", "AI provider is not configured yet; this preserves the agent contract for migration/testing."
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String textOrDefault(Object value, String fallback) {
        String text = text(value);
        return text.isBlank() ? fallback : text;
    }

    public record DocumentExtractionResponse(
            String invoiceNumber,
            String currency,
            String totalValue,
            String exporterName,
            String importerName,
            String countryOfOrigin,
            double confidence
    ) {
    }
}

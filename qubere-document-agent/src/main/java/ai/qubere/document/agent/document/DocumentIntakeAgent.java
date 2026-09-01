package ai.qubere.document.agent.document;

import ai.qubere.agent.api.Agent;
import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentRiskLevel;
import ai.qubere.agent.core.AgentDecisionDraft;
import ai.qubere.agent.core.AgentEvidenceDraft;
import ai.qubere.agent.core.AgentResult;
import ai.qubere.agent.core.GenericAgentInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class DocumentIntakeAgent implements Agent<GenericAgentInput, AgentResult<Map<String, Object>>> {

    public static final String AGENT_ID = "document.intake";

    private static final AgentDescriptor DESCRIPTOR = new AgentDescriptor(
            AGENT_ID,
            "Document Intake Agent",
            "0.1.0",
            "Classifies uploaded trade document packets and decides whether they can continue to extraction or require review.",
            AgentRiskLevel.MEDIUM,
            Set.of("document-intake", "document-classification", "trade-documents")
    );

    @Override
    public AgentDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentResult<Map<String, Object>> run(GenericAgentInput input, AgentExecutionContext context) {
        Map<String, Object> values = input.values();
        String fileName = text(values.get("fileName"));
        String mimeType = text(values.get("mimeType"));
        String overrideType = text(values.get("docTypeOverride"));
        String detectedType = !overrideType.isBlank() ? overrideType : detectFromName(fileName);
        int pageCount = number(values.get("pageCount"), 1);
        double confidence = !overrideType.isBlank() ? 1.0d : confidenceFor(fileName, detectedType);
        boolean needsReview = confidence < 0.70d || detectedType.equals("OTHER");

        List<Map<String, Object>> classifications = new ArrayList<>();
        classifications.add(Map.of(
                "pageNumber", 1,
                "docTypeCode", detectedType,
                "docTypeName", detectedType.replace('_', ' '),
                "confidence", Math.round(confidence * 100),
                "isHandwritten", false,
                "hasIllegibleStamps", false,
                "orientationDegrees", 0,
                "headerTextExcerpt", fileName.isBlank() ? "No file name supplied" : fileName
        ));

        Map<String, Object> output = Map.ofEntries(
                Map.entry("sourceApp", textOrDefault(values.get("sourceApp"), "CUSTOMS")),
                Map.entry("fileName", fileName),
                Map.entry("mimeType", mimeType),
                Map.entry("shipmentId", text(values.get("shipmentId"))),
                Map.entry("status", needsReview ? "Review Required" : "Completed"),
                Map.entry("overallConfidence", confidence),
                Map.entry("documentCount", 1),
                Map.entry("pageCount", pageCount),
                Map.entry("classifications", classifications),
                Map.entry("detectedTypes", List.of(detectedType)),
                Map.entry("missingRequiredDocs", List.of()),
                Map.entry("migrationStatus", "initial-java-port")
        );

        return new AgentResult<>(
                output,
                new AgentDecisionDraft(
                        needsReview ? "DOCUMENT_REVIEW_REQUIRED" : "DOCUMENT_INTAKE_ACCEPTED",
                        needsReview ? "Document type is uncertain and should be reviewed." : "Document packet can continue to extraction.",
                        confidence
                ),
                List.of(new AgentEvidenceDraft("fileName", fileName, "Document intake currently uses supplied metadata until parser/vision tools are migrated.")),
                Map.of("agentId", AGENT_ID, "source", "app-frontend migration scaffold")
        );
    }

    private String detectFromName(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.contains("invoice")) return "COMMERCIAL_INVOICE";
        if (lower.contains("packing")) return "PACKING_LIST";
        if (lower.contains("bill") || lower.contains("bol")) return "OCEAN_BILL_OF_LADING";
        if (lower.contains("airway") || lower.contains("awb")) return "AIR_WAYBILL";
        if (lower.contains("origin") || lower.contains("certificate")) return "GENERAL_CERTIFICATE_OF_ORIGIN";
        return "OTHER";
    }

    private double confidenceFor(String fileName, String detectedType) {
        if (fileName == null || fileName.isBlank() || detectedType.equals("OTHER")) {
            return 0.45d;
        }
        return 0.75d;
    }

    private int number(Object value, int fallback) {
        if (value instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        try {
            return Math.max(1, Integer.parseInt(String.valueOf(value)));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String textOrDefault(Object value, String fallback) {
        String text = text(value);
        return text.isBlank() ? fallback : text;
    }
}

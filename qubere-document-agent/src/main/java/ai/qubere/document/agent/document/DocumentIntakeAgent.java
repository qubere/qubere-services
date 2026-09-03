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
        String headerTextExcerpt = text(values.get("headerTextExcerpt"));

        // Catalog-first on every path: an explicit override is an exact catalog lookup, and the
        // signal used when no override is supplied (header text excerpt from a parser, falling
        // back to the file name) goes through the same DocumentTypeCatalog.matchDocumentType()
        // algorithm a real classification signal would use. There is deliberately no separate,
        // simpler filename-only heuristic path anymore: unifying both cases onto one matcher is
        // what the original migration plan flagged as the required fix.
        String classificationSignal = !headerTextExcerpt.isBlank() ? headerTextExcerpt : fileName;
        DocumentTypeDefinition matched = !overrideType.isBlank()
                ? DocumentTypeCatalog.byCode(overrideType).orElseGet(() -> DocumentTypeCatalog.matchDocumentType(overrideType))
                : DocumentTypeCatalog.matchDocumentType(classificationSignal);
        String detectedType = matched.code();
        int pageCount = number(values.get("pageCount"), 1);
        double confidence = !overrideType.isBlank() ? 1.0d : confidenceFor(classificationSignal, detectedType);
        boolean needsReview = confidence < 0.70d || detectedType.equals(DocumentTypeCatalog.OTHER_UNVERIFIED_DOCUMENT);

        List<Map<String, Object>> classifications = new ArrayList<>();
        classifications.add(Map.of(
                "pageNumber", 1,
                "docTypeCode", detectedType,
                "docTypeName", matched.name(),
                "confidence", Math.round(confidence * 100),
                "isHandwritten", false,
                "hasIllegibleStamps", false,
                "orientationDegrees", 0,
                "headerTextExcerpt", classificationSignal.isBlank() ? "No file name or header text supplied" : classificationSignal
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
                Map.entry("isRequiredForFiling", matched.isRequiredForFiling()),
                Map.entry("cfrRegulation", matched.cfrRegulation() == null ? "" : matched.cfrRegulation()),
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
                List.of(new AgentEvidenceDraft("classificationSignal", classificationSignal, "Document intake classifies against the trade-document type catalog; parser-backed header text takes precedence over the file name when both are supplied.")),
                Map.of("agentId", AGENT_ID, "source", "app-frontend migration scaffold")
        );
    }

    private double confidenceFor(String classificationSignal, String detectedType) {
        if (classificationSignal == null || classificationSignal.isBlank() || detectedType.equals(DocumentTypeCatalog.OTHER_UNVERIFIED_DOCUMENT)) {
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

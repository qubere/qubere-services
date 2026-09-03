package ai.qubere.document.agent.document.processing;

import ai.qubere.agent.api.Agent;
import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentRiskLevel;
import ai.qubere.agent.core.AgentDecisionDraft;
import ai.qubere.agent.core.AgentEvidenceDraft;
import ai.qubere.agent.core.AgentResult;
import ai.qubere.agent.core.GenericAgentInput;
import ai.qubere.document.agent.document.parser.QualityOutcome;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Records the terminal outcome of one document-processing run as a real, governed agent execution.
 * <p>
 * This is where the processing worker's result actually gets framework-standard audit: every field
 * here becomes a queryable {@code agent_execution_record} row via the ordinary agent pipeline,
 * instead of an ad hoc log table the way {@code documentProcessingWorker.ts} records its own audit
 * entries. The worker calls this agent once per run, at the point the run reaches a terminal state
 * — not once per poll tick, which would flood the audit trail with "still polling" noise that
 * carries no decision.
 */
@Component
public class DocumentProcessingOutcomeAgent implements Agent<GenericAgentInput, AgentResult<Map<String, Object>>> {

    public static final String AGENT_ID = "document.processing.outcome";

    private static final AgentDescriptor DESCRIPTOR = new AgentDescriptor(
            AGENT_ID,
            "Document Processing Outcome",
            "0.1.0",
            "Records the terminal quality-gate outcome of a document-parsing run as a governed, audited decision.",
            AgentRiskLevel.LOW,
            Set.of("document-processing", "quality-gate", "audit")
    );

    @Override
    public AgentDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentResult<Map<String, Object>> run(GenericAgentInput input, AgentExecutionContext context) {
        Map<String, Object> values = input.values();
        String documentId = text(values.get("documentId"));
        String processingRunId = text(values.get("processingRunId"));
        String qualityOutcome = text(values.get("qualityOutcome"));
        boolean accepted = Boolean.TRUE.equals(values.get("accepted"));
        Object reasons = values.get("reasons");

        double confidence = accepted ? 1.0d : 0.0d;
        String decision = accepted ? "DOCUMENT_PROCESSING_ACCEPTED" : "DOCUMENT_PROCESSING_NEEDS_REVIEW";
        String rationale = "Quality gate outcome " + qualityOutcome + " for processing run " + processingRunId + ".";

        Map<String, Object> output = Map.of(
                "documentId", documentId,
                "processingRunId", processingRunId,
                "qualityOutcome", qualityOutcome,
                "accepted", accepted
        );

        return new AgentResult<>(
                output,
                new AgentDecisionDraft(decision, rationale, confidence),
                List.of(new AgentEvidenceDraft(
                        "qualityAssessment",
                        String.valueOf(reasons),
                        "Reasons produced by the document quality gate for this processing run."
                )),
                Map.of("agentId", AGENT_ID, "processingRunId", processingRunId)
        );
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}

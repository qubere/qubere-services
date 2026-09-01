package ai.qubere.document.agent.api;

public record ApprovalDecisionRequest(
        String decision,
        String reason,
        String decidedBy
) {
}


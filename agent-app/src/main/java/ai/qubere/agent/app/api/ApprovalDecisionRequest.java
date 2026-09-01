package ai.qubere.agent.app.api;

public record ApprovalDecisionRequest(
        String decision,
        String reason,
        String decidedBy
) {
}

package ai.qubere.agent.core;

public record AgentDecisionDraft(
        String decision,
        String rationale,
        double confidence
) {
}

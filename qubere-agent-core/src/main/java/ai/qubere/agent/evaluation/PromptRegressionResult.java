package ai.qubere.agent.evaluation;

public record PromptRegressionResult(
        String caseId,
        EvaluationStatus status,
        String message
) {
}

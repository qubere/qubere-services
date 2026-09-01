package ai.qubere.agent.evaluation;

public record EvaluationCaseResult(
        String exampleId,
        EvaluationStatus status,
        String executionId,
        String message
) {
}

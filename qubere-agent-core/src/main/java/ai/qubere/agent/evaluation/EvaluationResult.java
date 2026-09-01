package ai.qubere.agent.evaluation;

import java.time.Instant;
import java.util.List;

public record EvaluationResult(
        String datasetName,
        int total,
        int passed,
        int failed,
        List<EvaluationCaseResult> cases,
        Instant evaluatedAt
) {
    public EvaluationResult {
        cases = cases == null ? List.of() : List.copyOf(cases);
        evaluatedAt = evaluatedAt == null ? Instant.now() : evaluatedAt;
    }
}

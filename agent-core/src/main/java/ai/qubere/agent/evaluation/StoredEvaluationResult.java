package ai.qubere.agent.evaluation;

import java.time.Instant;
import java.util.UUID;

public record StoredEvaluationResult(
        String evaluationId,
        String datasetName,
        EvaluationStatus status,
        int total,
        int passed,
        int failed,
        String casesJson,
        Instant evaluatedAt
) {
    public static StoredEvaluationResult from(EvaluationResult result) {
        return new StoredEvaluationResult(
                UUID.randomUUID().toString(),
                result.datasetName(),
                result.failed() == 0 ? EvaluationStatus.PASSED : EvaluationStatus.FAILED,
                result.total(),
                result.passed(),
                result.failed(),
                "[]",
                result.evaluatedAt()
        );
    }
}

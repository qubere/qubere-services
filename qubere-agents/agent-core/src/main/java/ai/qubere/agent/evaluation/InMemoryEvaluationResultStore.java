package ai.qubere.agent.evaluation;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryEvaluationResultStore implements EvaluationResultStore {

    private final Map<String, StoredEvaluationResult> results = new ConcurrentHashMap<>();

    @Override
    public StoredEvaluationResult save(EvaluationResult result) {
        StoredEvaluationResult stored = new StoredEvaluationResult(
                UUID.randomUUID().toString(),
                result.datasetName(),
                result.failed() == 0 ? EvaluationStatus.PASSED : EvaluationStatus.FAILED,
                result.total(),
                result.passed(),
                result.failed(),
                "[]",
                result.evaluatedAt() == null ? Instant.now() : result.evaluatedAt()
        );
        results.put(stored.evaluationId(), stored);
        return stored;
    }

    @Override
    public Optional<StoredEvaluationResult> find(String evaluationId) {
        return Optional.ofNullable(results.get(evaluationId));
    }

    @Override
    public Collection<StoredEvaluationResult> listRecent(int limit) {
        int safeLimit = Math.max(1, limit);
        return results.values().stream()
                .sorted(Comparator.comparing(StoredEvaluationResult::evaluatedAt).reversed())
                .limit(safeLimit)
                .toList();
    }
}

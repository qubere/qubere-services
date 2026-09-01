package ai.qubere.agent.evaluation;

import java.util.Collection;
import java.util.Optional;

public interface EvaluationResultStore {

    StoredEvaluationResult save(EvaluationResult result);

    Optional<StoredEvaluationResult> find(String evaluationId);

    Collection<StoredEvaluationResult> listRecent(int limit);

    static EvaluationResultStore noop() {
        return new EvaluationResultStore() {
            @Override
            public StoredEvaluationResult save(EvaluationResult result) {
                return StoredEvaluationResult.from(result);
            }

            @Override
            public Optional<StoredEvaluationResult> find(String evaluationId) {
                return Optional.empty();
            }

            @Override
            public Collection<StoredEvaluationResult> listRecent(int limit) {
                return java.util.List.of();
            }
        };
    }
}

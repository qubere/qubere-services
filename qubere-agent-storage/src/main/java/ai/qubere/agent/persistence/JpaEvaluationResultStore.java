package ai.qubere.agent.persistence;

import ai.qubere.agent.evaluation.EvaluationResult;
import ai.qubere.agent.evaluation.EvaluationResultStore;
import ai.qubere.agent.evaluation.EvaluationStatus;
import ai.qubere.agent.evaluation.StoredEvaluationResult;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaEvaluationResultStore implements EvaluationResultStore {

    private final AgentEvaluationResultRepository repository;
    private final ObjectMapper objectMapper;

    public JpaEvaluationResultStore(AgentEvaluationResultRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public StoredEvaluationResult save(EvaluationResult result) {
        AgentEvaluationResultEntity entity = new AgentEvaluationResultEntity();
        entity.setEvaluationId(UUID.randomUUID().toString());
        entity.setDatasetName(result.datasetName());
        entity.setStatus(result.failed() == 0 ? EvaluationStatus.PASSED : EvaluationStatus.FAILED);
        entity.setTotal(result.total());
        entity.setPassed(result.passed());
        entity.setFailed(result.failed());
        entity.setCasesJson(toJson(result.cases()));
        entity.setEvaluatedAt(result.evaluatedAt() == null ? Instant.now() : result.evaluatedAt());
        return toStored(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredEvaluationResult> find(String evaluationId) {
        return repository.findById(evaluationId).map(this::toStored);
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<StoredEvaluationResult> listRecent(int limit) {
        int safeLimit = Math.max(1, limit);
        return repository.findAllByOrderByEvaluatedAtDesc().stream()
                .limit(safeLimit)
                .map(this::toStored)
                .toList();
    }

    private StoredEvaluationResult toStored(AgentEvaluationResultEntity entity) {
        return new StoredEvaluationResult(
                entity.getEvaluationId(),
                entity.getDatasetName(),
                entity.getStatus(),
                entity.getTotal(),
                entity.getPassed(),
                entity.getFailed(),
                entity.getCasesJson(),
                entity.getEvaluatedAt()
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to serialize evaluation result", ex);
        }
    }
}

package ai.qubere.agent.persistence;

import ai.qubere.agent.checkpoint.AgentCheckpoint;
import ai.qubere.agent.checkpoint.AgentCheckpointStore;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA-backed {@link AgentCheckpointStore}, making multi-step agent resume durable across process
 * restarts. This is what allows an execution that paused for human approval hours earlier to
 * resume without re-executing steps whose side effects already happened.
 */
@Component
public class JpaAgentCheckpointStore implements AgentCheckpointStore {

    private final AgentCheckpointRepository repository;

    public JpaAgentCheckpointStore(AgentCheckpointRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void save(AgentCheckpoint checkpoint) {
        if (checkpoint == null || checkpoint.executionId() == null || checkpoint.stepName() == null) {
            return;
        }
        // Upsert on (executionId, stepName) so a retried save updates the existing row rather
        // than failing on the composite primary key.
        AgentCheckpointEntity entity = repository
                .findByExecutionIdAndStepName(checkpoint.executionId(), checkpoint.stepName())
                .orElseGet(AgentCheckpointEntity::new);
        entity.setExecutionId(checkpoint.executionId());
        entity.setStepName(checkpoint.stepName());
        entity.setStepIndex(checkpoint.stepIndex());
        entity.setResultJson(checkpoint.resultJson());
        entity.setCompletedAt(checkpoint.completedAt());
        repository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AgentCheckpoint> find(String executionId, String stepName) {
        if (executionId == null || stepName == null) {
            return Optional.empty();
        }
        return repository.findByExecutionIdAndStepName(executionId, stepName).map(this::toCheckpoint);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentCheckpoint> findByExecutionId(String executionId) {
        if (executionId == null) {
            return List.of();
        }
        return repository.findByExecutionIdOrderByStepIndexAsc(executionId).stream()
                .map(this::toCheckpoint)
                .toList();
    }

    @Override
    @Transactional
    public void deleteByExecutionId(String executionId) {
        if (executionId != null) {
            repository.deleteByExecutionId(executionId);
        }
    }

    private AgentCheckpoint toCheckpoint(AgentCheckpointEntity entity) {
        return new AgentCheckpoint(
                entity.getExecutionId(),
                entity.getStepName(),
                entity.getStepIndex(),
                entity.getResultJson(),
                entity.getCompletedAt()
        );
    }
}

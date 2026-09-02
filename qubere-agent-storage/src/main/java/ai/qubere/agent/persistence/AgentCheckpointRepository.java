package ai.qubere.agent.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface AgentCheckpointRepository
        extends JpaRepository<AgentCheckpointEntity, AgentCheckpointEntity.AgentCheckpointId> {

    Optional<AgentCheckpointEntity> findByExecutionIdAndStepName(String executionId, String stepName);

    List<AgentCheckpointEntity> findByExecutionIdOrderByStepIndexAsc(String executionId);

    /**
     * Derived delete queries require an active transaction and an explicit modifying declaration;
     * declaring them here keeps the repository correct regardless of whether the caller happens
     * to be running inside a transactional proxy.
     */
    @Modifying
    @Transactional
    void deleteByExecutionId(String executionId);
}

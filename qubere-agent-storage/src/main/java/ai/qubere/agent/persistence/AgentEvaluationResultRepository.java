package ai.qubere.agent.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentEvaluationResultRepository extends JpaRepository<AgentEvaluationResultEntity, String> {

    List<AgentEvaluationResultEntity> findAllByOrderByEvaluatedAtDesc();
}

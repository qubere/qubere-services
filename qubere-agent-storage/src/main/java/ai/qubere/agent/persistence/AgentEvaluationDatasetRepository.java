package ai.qubere.agent.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentEvaluationDatasetRepository extends JpaRepository<AgentEvaluationDatasetEntity, String> {
}

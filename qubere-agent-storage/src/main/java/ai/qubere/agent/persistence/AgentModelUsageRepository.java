package ai.qubere.agent.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentModelUsageRepository extends JpaRepository<AgentModelUsageEntity, String> {

    List<AgentModelUsageEntity> findByExecutionIdOrderByStartedAtAsc(String executionId);
}
package ai.qubere.agent.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentExecutionLogRepository extends JpaRepository<AgentExecutionLogEntity, Long> {

    List<AgentExecutionLogEntity> findByExecutionIdOrderByOccurredAtAsc(String executionId);
}

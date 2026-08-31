package ai.qubere.agent.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentExecutionRecordRepository extends JpaRepository<AgentExecutionRecordEntity, String> {
}

package ai.qubere.agent.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentToolCallRepository extends JpaRepository<AgentToolCallEntity, String> {

    List<AgentToolCallEntity> findByExecutionIdOrderByStartedAtAsc(String executionId);
}
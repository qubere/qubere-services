package ai.qubere.agent.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentToolAuditEventRepository extends JpaRepository<AgentToolAuditEventEntity, Long> {

    List<AgentToolAuditEventEntity> findByExecutionIdOrderByOccurredAtAsc(String executionId);
}

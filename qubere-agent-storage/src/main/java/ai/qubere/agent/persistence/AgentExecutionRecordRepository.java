package ai.qubere.agent.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentExecutionRecordRepository extends JpaRepository<AgentExecutionRecordEntity, String> {

    Optional<AgentExecutionRecordEntity> findFirstByTenantIdAndIdempotencyKeyOrderByCreatedAtDesc(String tenantId, String idempotencyKey);
}

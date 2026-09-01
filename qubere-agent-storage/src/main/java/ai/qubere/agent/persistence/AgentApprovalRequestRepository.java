package ai.qubere.agent.persistence;

import ai.qubere.agent.async.AgentApprovalStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentApprovalRequestRepository extends JpaRepository<AgentApprovalRequestEntity, String> {

    Optional<AgentApprovalRequestEntity> findFirstByExecutionIdAndStatusOrderByCreatedAtDesc(String executionId, AgentApprovalStatus status);

    List<AgentApprovalRequestEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AgentApprovalRequestEntity> findByStatusOrderByCreatedAtDesc(AgentApprovalStatus status, Pageable pageable);

    List<AgentApprovalRequestEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    List<AgentApprovalRequestEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, AgentApprovalStatus status, Pageable pageable);

    List<AgentApprovalRequestEntity> findByStatusAndExpiresAtLessThanEqual(AgentApprovalStatus status, Instant expiresAt);
}

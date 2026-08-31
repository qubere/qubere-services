package ai.qubere.agent.persistence;

import ai.qubere.agent.async.AgentApprovalStatus;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentApprovalRequestRepository extends JpaRepository<AgentApprovalRequestEntity, String> {

    Optional<AgentApprovalRequestEntity> findFirstByExecutionIdAndStatusOrderByCreatedAtDesc(String executionId, AgentApprovalStatus status);
}

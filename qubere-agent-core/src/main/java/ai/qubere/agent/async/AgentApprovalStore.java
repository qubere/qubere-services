package ai.qubere.agent.async;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface AgentApprovalStore {

    AgentApprovalRequest create(AgentApprovalRequest request);

    Optional<AgentApprovalRequest> findById(String approvalId);

    Optional<AgentApprovalRequest> findPendingByExecutionId(String executionId);

    Collection<AgentApprovalRequest> list(AgentApprovalStatus status, String tenantId, int limit);

    int expirePendingBefore(Instant now);

    AgentApprovalRequest approve(String approvalId, String decidedBy);

    AgentApprovalRequest reject(String approvalId, String decidedBy);

    AgentApprovalRequest expire(String approvalId);
}

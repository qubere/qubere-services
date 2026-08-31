package ai.qubere.agent.async;

import java.util.Optional;

public interface AgentApprovalStore {

    AgentApprovalRequest create(AgentApprovalRequest request);

    Optional<AgentApprovalRequest> findById(String approvalId);

    Optional<AgentApprovalRequest> findPendingByExecutionId(String executionId);

    AgentApprovalRequest approve(String approvalId, String decidedBy);

    AgentApprovalRequest reject(String approvalId, String decidedBy);

    AgentApprovalRequest expire(String approvalId);
}

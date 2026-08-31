package ai.qubere.agent.async;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAgentApprovalStore implements AgentApprovalStore {

    private final Map<String, AgentApprovalRequest> approvals = new ConcurrentHashMap<>();

    @Override
    public AgentApprovalRequest create(AgentApprovalRequest request) {
        approvals.put(request.approvalId(), request);
        return request;
    }

    @Override
    public Optional<AgentApprovalRequest> findById(String approvalId) {
        return Optional.ofNullable(approvals.get(approvalId));
    }

    @Override
    public Optional<AgentApprovalRequest> findPendingByExecutionId(String executionId) {
        return approvals.values().stream()
                .filter(approval -> approval.executionId().equals(executionId))
                .filter(approval -> approval.status() == AgentApprovalStatus.PENDING)
                .findFirst();
    }

    @Override
    public AgentApprovalRequest approve(String approvalId, String decidedBy) {
        return decide(approvalId, decidedBy, AgentApprovalStatus.APPROVED);
    }

    @Override
    public AgentApprovalRequest reject(String approvalId, String decidedBy) {
        return decide(approvalId, decidedBy, AgentApprovalStatus.REJECTED);
    }

    @Override
    public AgentApprovalRequest expire(String approvalId) {
        return decide(approvalId, "system", AgentApprovalStatus.EXPIRED);
    }

    private AgentApprovalRequest decide(String approvalId, String decidedBy, AgentApprovalStatus status) {
        AgentApprovalRequest existing = approvals.get(approvalId);
        if (existing == null) {
            throw new IllegalArgumentException("Approval request not found: " + approvalId);
        }
        if (existing.status() != AgentApprovalStatus.PENDING) {
            throw new IllegalStateException("Approval request is already decided: " + approvalId);
        }
        AgentApprovalRequest decided = new AgentApprovalRequest(
                existing.approvalId(),
                existing.executionId(),
                existing.agentId(),
                existing.agentVersion(),
                existing.tenantId(),
                existing.requestedBy(),
                status,
                existing.reason(),
                existing.metadata(),
                existing.createdAt(),
                existing.expiresAt(),
                Instant.now(),
                decidedBy
        );
        approvals.put(approvalId, decided);
        return decided;
    }
}

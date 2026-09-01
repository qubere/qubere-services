package ai.qubere.agent.persistence;

import ai.qubere.agent.async.AgentApprovalStatus;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_approval_request")
public class AgentApprovalRequestEntity {

    @Id
    @Column(name = "approval_id", length = 64, nullable = false)
    private String approvalId;

    @Column(name = "execution_id", length = 64, nullable = false)
    private String executionId;

    @Column(name = "agent_id", length = 128, nullable = false)
    private String agentId;

    @Column(name = "agent_version", length = 64)
    private String agentVersion;

    @Column(name = "tenant_id", length = 128)
    private String tenantId;

    @Column(name = "requested_by", length = 128)
    private String requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private AgentApprovalStatus status;

    @Lob
    @Column(name = "reason")
    private String reason;

    @Lob
    @Column(name = "metadata_json")
    private String metadataJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by", length = 128)
    private String decidedBy;

    public String getApprovalId() { return approvalId; }

    public void setApprovalId(String approvalId) { this.approvalId = approvalId; }

    public String getExecutionId() { return executionId; }

    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public String getAgentId() { return agentId; }

    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getAgentVersion() { return agentVersion; }

    public void setAgentVersion(String agentVersion) { this.agentVersion = agentVersion; }

    public String getTenantId() { return tenantId; }

    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getRequestedBy() { return requestedBy; }

    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

    public AgentApprovalStatus getStatus() { return status; }

    public void setStatus(AgentApprovalStatus status) { this.status = status; }

    public String getReason() { return reason; }

    public void setReason(String reason) { this.reason = reason; }

    public String getMetadataJson() { return metadataJson; }

    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }

    public Instant getCreatedAt() { return createdAt; }

    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpiresAt() { return expiresAt; }

    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getDecidedAt() { return decidedAt; }

    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }

    public String getDecidedBy() { return decidedBy; }

    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }
}

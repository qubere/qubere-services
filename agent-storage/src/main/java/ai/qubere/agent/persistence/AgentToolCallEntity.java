package ai.qubere.agent.persistence;

import ai.qubere.agent.tools.ToolCallStatus;
import ai.qubere.agent.tools.ToolRiskLevel;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_tool_call")
public class AgentToolCallEntity {

    @Id
    @Column(name = "call_id", length = 64, nullable = false)
    private String callId;

    @Column(name = "execution_id", length = 64, nullable = false)
    private String executionId;

    @Column(name = "tenant_id", length = 128)
    private String tenantId;

    @Column(name = "actor_id", length = 128)
    private String actorId;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "tool_name", length = 128, nullable = false)
    private String toolName;

    @Enumerated(EnumType.STRING)
    @Column(name = "tool_risk_level", length = 32, nullable = false)
    private ToolRiskLevel toolRiskLevel;

    @Column(name = "side_effects", length = 512)
    private String sideEffects;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private ToolCallStatus status;

    @Lob
    @Column(name = "input_json")
    private String inputJson;

    @Column(name = "output_summary", length = 1000)
    private String outputSummary;

    @Lob
    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "approval_id", length = 64)
    private String approvalId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "latency_ms")
    private Long latencyMs;

    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public ToolRiskLevel getToolRiskLevel() {
        return toolRiskLevel;
    }

    public void setToolRiskLevel(ToolRiskLevel toolRiskLevel) {
        this.toolRiskLevel = toolRiskLevel;
    }

    public String getSideEffects() {
        return sideEffects;
    }

    public void setSideEffects(String sideEffects) {
        this.sideEffects = sideEffects;
    }

    public ToolCallStatus getStatus() {
        return status;
    }

    public void setStatus(ToolCallStatus status) {
        this.status = status;
    }

    public String getInputJson() {
        return inputJson;
    }

    public void setInputJson(String inputJson) {
        this.inputJson = inputJson;
    }

    public String getOutputSummary() {
        return outputSummary;
    }

    public void setOutputSummary(String outputSummary) {
        this.outputSummary = outputSummary;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(String approvalId) {
        this.approvalId = approvalId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }
}
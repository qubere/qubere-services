package ai.qubere.agent.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_async_queue_command")
public class AgentAsyncQueueCommandEntity {

    @Id
    @Column(name = "execution_id", length = 64, nullable = false)
    private String executionId;

    @Column(name = "agent_id", length = 128, nullable = false)
    private String agentId;

    @Column(name = "agent_version", length = 64)
    private String agentVersion;

    @Column(name = "input_type", length = 512, nullable = false)
    private String inputType;

    @Lob
    @Column(name = "input_json", nullable = false)
    private String inputJson;

    @Lob
    @Column(name = "options_json")
    private String optionsJson;

    @Column(name = "tenant_id", length = 128)
    private String tenantId;

    @Column(name = "actor_id", length = 128)
    private String actorId;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Lob
    @Column(name = "context_attributes_json")
    private String contextAttributesJson;

    @Column(name = "callback_url", length = 1000)
    private String callbackUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public String getExecutionId() { return executionId; }

    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public String getAgentId() { return agentId; }

    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getAgentVersion() { return agentVersion; }

    public void setAgentVersion(String agentVersion) { this.agentVersion = agentVersion; }

    public String getInputType() { return inputType; }

    public void setInputType(String inputType) { this.inputType = inputType; }

    public String getInputJson() { return inputJson; }

    public void setInputJson(String inputJson) { this.inputJson = inputJson; }

    public String getOptionsJson() { return optionsJson; }

    public void setOptionsJson(String optionsJson) { this.optionsJson = optionsJson; }

    public String getTenantId() { return tenantId; }

    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getActorId() { return actorId; }

    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getCorrelationId() { return correlationId; }

    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public Instant getRequestedAt() { return requestedAt; }

    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }

    public String getContextAttributesJson() { return contextAttributesJson; }

    public void setContextAttributesJson(String contextAttributesJson) { this.contextAttributesJson = contextAttributesJson; }

    public String getCallbackUrl() { return callbackUrl; }

    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }

    public Instant getCreatedAt() { return createdAt; }

    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

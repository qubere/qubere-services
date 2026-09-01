package ai.qubere.agent.persistence;

import ai.qubere.agent.prompts.PromptStatus;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@IdClass(AgentPromptTemplateId.class)
@Table(name = "agent_prompt_template")
public class AgentPromptTemplateEntity {

    @Id
    @Column(name = "prompt_id", length = 128, nullable = false)
    private String promptId;

    @Id
    @Column(name = "version", length = 64, nullable = false)
    private String version;

    @Column(name = "agent_id", length = 128, nullable = false)
    private String agentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private PromptStatus status;

    @Lob
    @Column(name = "system_template")
    private String systemTemplate;

    @Lob
    @Column(name = "user_template")
    private String userTemplate;

    @Lob
    @Column(name = "metadata_json")
    private String metadataJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getPromptId() {
        return promptId;
    }

    public void setPromptId(String promptId) {
        this.promptId = promptId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public PromptStatus getStatus() {
        return status;
    }

    public void setStatus(PromptStatus status) {
        this.status = status;
    }

    public String getSystemTemplate() {
        return systemTemplate;
    }

    public void setSystemTemplate(String systemTemplate) {
        this.systemTemplate = systemTemplate;
    }

    public String getUserTemplate() {
        return userTemplate;
    }

    public void setUserTemplate(String userTemplate) {
        this.userTemplate = userTemplate;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

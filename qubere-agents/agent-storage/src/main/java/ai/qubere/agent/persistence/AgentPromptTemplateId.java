package ai.qubere.agent.persistence;

import java.io.Serializable;
import java.util.Objects;

public class AgentPromptTemplateId implements Serializable {

    private String promptId;
    private String version;

    public AgentPromptTemplateId() {
    }

    public AgentPromptTemplateId(String promptId, String version) {
        this.promptId = promptId;
        this.version = version;
    }

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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AgentPromptTemplateId that)) {
            return false;
        }
        return Objects.equals(promptId, that.promptId) && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(promptId, version);
    }
}

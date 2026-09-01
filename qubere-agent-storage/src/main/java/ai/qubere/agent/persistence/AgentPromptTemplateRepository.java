package ai.qubere.agent.persistence;

import ai.qubere.agent.prompts.PromptStatus;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentPromptTemplateRepository extends JpaRepository<AgentPromptTemplateEntity, AgentPromptTemplateId> {

    Optional<AgentPromptTemplateEntity> findFirstByAgentIdAndStatusOrderByVersionDesc(String agentId, PromptStatus status);

    List<AgentPromptTemplateEntity> findByAgentIdOrderByVersionAsc(String agentId);
}

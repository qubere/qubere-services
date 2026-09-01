package ai.qubere.agent.prompts;

import java.util.Collection;
import java.util.Optional;

public interface PromptVersionStore {

    PromptTemplate save(PromptTemplate template);

    Optional<PromptTemplate> find(String promptId, String version);

    Optional<PromptTemplate> findActiveForAgent(String agentId);

    Collection<PromptTemplate> listForAgent(String agentId);
}

package ai.qubere.agent.async;

import java.util.Optional;

public interface AgentPendingCommandStore {

    void save(AgentRunCommand command);

    Optional<AgentRunCommand> findByExecutionId(String executionId);

    void delete(String executionId);
}

package ai.qubere.agent.async;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAgentPendingCommandStore implements AgentPendingCommandStore {

    private final Map<String, AgentRunCommand> commands = new ConcurrentHashMap<>();

    @Override
    public void save(AgentRunCommand command) {
        if (command == null || command.context() == null || command.context().executionId() == null) {
            throw new IllegalArgumentException("Pending command and execution id are required");
        }
        commands.put(command.context().executionId(), command);
    }

    @Override
    public Optional<AgentRunCommand> findByExecutionId(String executionId) {
        return Optional.ofNullable(commands.get(executionId));
    }

    @Override
    public void delete(String executionId) {
        commands.remove(executionId);
    }
}

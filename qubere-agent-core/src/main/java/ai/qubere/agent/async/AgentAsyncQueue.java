package ai.qubere.agent.async;

import java.util.Optional;

public interface AgentAsyncQueue {

    void enqueue(AgentRunCommand command);

    Optional<AgentRunCommand> poll();

    int size();
}

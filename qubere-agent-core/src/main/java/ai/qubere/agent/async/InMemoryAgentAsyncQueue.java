package ai.qubere.agent.async;

import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;

public class InMemoryAgentAsyncQueue implements AgentAsyncQueue {

    private final LinkedBlockingQueue<AgentRunCommand> queue = new LinkedBlockingQueue<>();

    @Override
    public void enqueue(AgentRunCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Async run command is required");
        }
        queue.offer(command);
    }

    @Override
    public Optional<AgentRunCommand> poll() {
        return Optional.ofNullable(queue.poll());
    }

    @Override
    public int size() {
        return queue.size();
    }
}

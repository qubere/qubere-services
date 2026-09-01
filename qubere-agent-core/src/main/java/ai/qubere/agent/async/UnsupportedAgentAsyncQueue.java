package ai.qubere.agent.async;

import java.util.Optional;

public class UnsupportedAgentAsyncQueue implements AgentAsyncQueue {

    private final String provider;

    public UnsupportedAgentAsyncQueue(String provider) {
        this.provider = provider == null || provider.isBlank() ? "unknown" : provider;
    }

    @Override
    public void enqueue(AgentRunCommand command) {
        throw unsupported();
    }

    @Override
    public Optional<AgentRunCommand> poll() {
        throw unsupported();
    }

    @Override
    public int size() {
        throw unsupported();
    }

    private IllegalStateException unsupported() {
        return new IllegalStateException("Async queue provider '" + provider + "' is configured, but no production adapter is available on the classpath yet. Use 'memory' for local development, 'database' with agent-storage for the JPA queue, or provide your own AgentAsyncQueue bean.");
    }
}
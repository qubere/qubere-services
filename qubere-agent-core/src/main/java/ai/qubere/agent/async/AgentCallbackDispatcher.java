package ai.qubere.agent.async;

public interface AgentCallbackDispatcher {

    void dispatch(AgentRunCallback callback);

    static AgentCallbackDispatcher noop() {
        return callback -> {
        };
    }
}

package ai.qubere.agent.observability;

public interface AgentObservabilityService {

    void record(AgentTraceEvent event);
}

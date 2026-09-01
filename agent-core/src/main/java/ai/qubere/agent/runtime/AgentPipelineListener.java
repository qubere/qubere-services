package ai.qubere.agent.runtime;

@FunctionalInterface
public interface AgentPipelineListener {

    void onEvent(AgentPipelineEvent event);
}

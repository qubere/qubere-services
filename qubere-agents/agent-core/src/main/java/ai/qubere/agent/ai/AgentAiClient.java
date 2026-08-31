package ai.qubere.agent.ai;

public interface AgentAiClient {

    <T> T generate(AgentPrompt prompt, Class<T> responseType);
}

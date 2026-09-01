package ai.qubere.agent.async;

import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentInput;
import ai.qubere.agent.core.AgentRunOptions;

public record AgentRunCommand(
        String agentId,
        String agentVersion,
        AgentInput input,
        AgentExecutionContext context,
        AgentRunOptions options,
        String callbackUrl
) {
    public AgentRunCommand withContext(AgentExecutionContext newContext) {
        return new AgentRunCommand(agentId, agentVersion, input, newContext, options, callbackUrl);
    }
}

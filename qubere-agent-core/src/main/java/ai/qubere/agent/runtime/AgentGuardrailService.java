package ai.qubere.agent.runtime;

import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentInput;

public interface AgentGuardrailService {

    GuardrailDecision evaluateBeforeRun(AgentExecutionContext context, AgentDescriptor descriptor, AgentInput input);
}

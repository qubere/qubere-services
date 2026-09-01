package ai.qubere.agent.runtime;

import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;

public interface AgentAuthorizationService {

    boolean canRun(AgentExecutionContext context, AgentDescriptor descriptor);
}

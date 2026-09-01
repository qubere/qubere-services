package ai.qubere.agent.runtime;

import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.core.AgentRunStatus;

public interface AgentAuditService {

    void recordStatus(AgentExecutionContext context, AgentDescriptor descriptor, AgentRunStatus status, String message);
}

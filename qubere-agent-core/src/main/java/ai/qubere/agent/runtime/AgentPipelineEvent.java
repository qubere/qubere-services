package ai.qubere.agent.runtime;

import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;

import java.time.Instant;
import java.util.Map;

public record AgentPipelineEvent(
        AgentPipelineStep step,
        AgentExecutionContext context,
        AgentDescriptor descriptor,
        String message,
        Map<String, Object> attributes,
        Instant occurredAt
) {
    public AgentPipelineEvent {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    public static AgentPipelineEvent of(
            AgentPipelineStep step,
            AgentExecutionContext context,
            AgentDescriptor descriptor,
            String message
    ) {
        return new AgentPipelineEvent(step, context, descriptor, message, Map.of(), Instant.now());
    }
}

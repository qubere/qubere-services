package ai.qubere.agent.ai;

import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.core.ResolvedAgentPolicy;

import java.util.Map;

public record AgentAiRequestMetadata(
        String executionId,
        String tenantId,
        String actorId,
        String correlationId,
        String agentId,
        String agentVersion,
        String modelProvider,
        String modelName,
        String promptVersion,
        Boolean streaming,
        Double temperature,
        Integer maxOutputTokens,
        Boolean logPrompts,
        Boolean logToolResults,
        Map<String, Object> metadata
) {
    public AgentAiRequestMetadata {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static AgentAiRequestMetadata empty() {
        return new AgentAiRequestMetadata(null, null, null, null, null, null, null, null, null, null, null, null, null, null, Map.of());
    }

    public static AgentAiRequestMetadata from(AgentExecutionContext context) {
        if (context == null) {
            return empty();
        }
        ResolvedAgentPolicy policy = context.attributes().get("resolvedPolicy") instanceof ResolvedAgentPolicy resolved
                ? resolved
                : null;
        return new AgentAiRequestMetadata(
                context.executionId(),
                context.tenantId(),
                context.actorId(),
                context.correlationId(),
                stringAttribute(context, "agentId"),
                stringAttribute(context, "agentVersion"),
                policy == null ? null : policy.modelProvider(),
                policy == null ? null : policy.modelName(),
                policy == null ? null : policy.promptVersion(),
                policy == null ? null : policy.streaming(),
                policy == null ? null : policy.temperature(),
                policy == null ? null : policy.maxOutputTokens(),
                policy == null ? null : policy.logPrompts(),
                policy == null ? null : policy.logToolResults(),
                Map.of()
        );
    }

    private static String stringAttribute(AgentExecutionContext context, String key) {
        Object value = context.attributes().get(key);
        return value == null ? null : value.toString();
    }
}
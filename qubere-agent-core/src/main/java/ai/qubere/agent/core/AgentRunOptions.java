package ai.qubere.agent.core;

import java.util.Map;
import java.util.Set;

/**
 * Caller-supplied execution overrides. Null values mean "use the resolved framework/agent default".
 */
public record AgentRunOptions(
        AgentRunMode mode,
        Integer maxSteps,
        Boolean streaming,
        Double temperature,
        Integer maxOutputTokens,
        Boolean allowToolCalls,
        Boolean requireHumanApproval,
        Set<String> allowedTools,
        Map<String, Object> overrides
) {
    public AgentRunOptions(
            AgentRunMode mode,
            Integer maxSteps,
            Double temperature,
            Integer maxOutputTokens,
            Boolean allowToolCalls,
            Boolean requireHumanApproval,
            Set<String> allowedTools,
            Map<String, Object> overrides
    ) {
        this(mode, maxSteps, null, temperature, maxOutputTokens, allowToolCalls, requireHumanApproval, allowedTools, overrides);
    }

    public AgentRunOptions {
        allowedTools = allowedTools == null ? Set.of() : Set.copyOf(allowedTools);
        overrides = overrides == null ? Map.of() : Map.copyOf(overrides);
    }
}

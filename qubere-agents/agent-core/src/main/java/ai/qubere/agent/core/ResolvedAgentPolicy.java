package ai.qubere.agent.core;

import java.math.BigDecimal;
import java.util.Set;

public record ResolvedAgentPolicy(
        boolean enabled,
        boolean dryRun,
        boolean async,
        boolean streaming,
        boolean memoryEnabled,
        boolean includeEvidence,
        boolean includeRecommendations,
        int maxMemoryResults,
        int maxToolCalls,
        int timeoutSeconds,
        int maxRetries,
        BigDecimal maxEstimatedCostUsd,
        String modelProvider,
        String modelName,
        String promptVersion,
        AgentRunMode mode,
        int maxSteps,
        double temperature,
        int maxOutputTokens,
        boolean allowToolCalls,
        boolean requireHumanApproval,
        boolean logPrompts,
        boolean logToolResults,
        Set<String> allowedTools,
        String responseDetail,
        String priority
) {
    public ResolvedAgentPolicy {
        maxEstimatedCostUsd = maxEstimatedCostUsd == null ? BigDecimal.ZERO : maxEstimatedCostUsd;
        modelProvider = modelProvider == null || modelProvider.isBlank() ? "openai" : modelProvider;
        modelName = modelName == null || modelName.isBlank() ? "default" : modelName;
        promptVersion = promptVersion == null || promptVersion.isBlank() ? "latest" : promptVersion;
        allowedTools = allowedTools == null ? Set.of() : Set.copyOf(allowedTools);
        responseDetail = responseDetail == null || responseDetail.isBlank() ? "SUMMARY" : responseDetail;
        priority = priority == null || priority.isBlank() ? "NORMAL" : priority;
    }

    public ResolvedAgentPolicy(
            AgentRunMode mode,
            int maxSteps,
            double temperature,
            int maxOutputTokens,
            boolean allowToolCalls,
            boolean requireHumanApproval,
            Set<String> allowedTools
    ) {
        this(
                true,
                mode == AgentRunMode.DRY_RUN,
                false,
                false,
                true,
                true,
                true,
                5,
                maxSteps,
                120,
                2,
                BigDecimal.ZERO,
                "openai",
                "default",
                "latest",
                mode,
                maxSteps,
                temperature,
                maxOutputTokens,
                allowToolCalls,
                requireHumanApproval,
                false,
                false,
                allowedTools,
                "SUMMARY",
                "NORMAL"
        );
    }

    public static ResolvedAgentPolicy defaults() {
        return new ResolvedAgentPolicy(
                AgentRunMode.RECOMMEND,
                8,
                0.2d,
                2048,
                true,
                false,
                Set.of()
        );
    }
}

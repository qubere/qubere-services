package ai.qubere.agent.runtime;

import ai.qubere.agent.core.AgentRunOptions;
import ai.qubere.agent.core.ResolvedAgentPolicy;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import java.math.BigDecimal;
import java.util.Set;

public class DefaultAgentPolicyResolver implements AgentPolicyResolver {

    private final AgentPlatformProperties properties;

    public DefaultAgentPolicyResolver() {
        this(new AgentPlatformProperties());
    }

    public DefaultAgentPolicyResolver(AgentPlatformProperties properties) {
        this.properties = properties == null ? new AgentPlatformProperties() : properties;
    }

    @Override
    public ResolvedAgentPolicy resolve(String agentId, AgentRunOptions requestedOptions) {
        AgentPlatformProperties.Runtime defaults = properties.getRuntime();
        AgentPlatformProperties.AgentDefinition agentDefinition = properties.getDefinitions()
                .getOrDefault(agentId, new AgentPlatformProperties.AgentDefinition());
        AgentRunOptions options = requestedOptions == null
                ? new AgentRunOptions(null, null, null, null, null, null, null, null, null)
                : requestedOptions;

        return new ResolvedAgentPolicy(
                agentDefinition.isEnabled(),
                defaults.isDryRun() || options.mode() == ai.qubere.agent.core.AgentRunMode.DRY_RUN,
                defaults.isAsync(),
                options.streaming() == null ? defaults.isStreaming() : options.streaming(),
                agentDefinition.getMemoryEnabled() == null ? properties.getMemory().isEnabled() : agentDefinition.getMemoryEnabled(),
                defaults.isIncludeEvidence(),
                defaults.isIncludeRecommendations(),
                agentDefinition.getMaxMemoryResults() == null ? properties.getMemory().getMaxResults() : agentDefinition.getMaxMemoryResults(),
                agentDefinition.getMaxToolCalls() == null ? properties.getTools().getMaxToolCalls() : agentDefinition.getMaxToolCalls(),
                agentDefinition.getTimeoutSeconds() == null ? defaults.getTimeoutSeconds() : agentDefinition.getTimeoutSeconds(),
                agentDefinition.getMaxRetries() == null ? defaults.getMaxRetries() : agentDefinition.getMaxRetries(),
                resolveCostCap(agentDefinition),
                firstText(agentDefinition.getModelProvider(), properties.getAi().getDefaultProvider()),
                firstText(agentDefinition.getModelName(), properties.getAi().getDefaultModel()),
                firstText(agentDefinition.getPromptVersion(), "latest"),
                options.mode() == null ? defaults.getDefaultMode() : options.mode(),
                options.maxSteps() == null ? defaults.getMaxSteps() : options.maxSteps(),
                options.temperature() == null ? defaults.getTemperature() : options.temperature(),
                options.maxOutputTokens() == null ? defaults.getMaxOutputTokens() : options.maxOutputTokens(),
                options.allowToolCalls() == null ? defaults.isAllowToolCalls() : options.allowToolCalls(),
                options.requireHumanApproval() == null
                        ? resolveApproval(defaults, agentDefinition)
                        : options.requireHumanApproval(),
                defaults.isLogPrompts(),
                defaults.isLogToolResults(),
                resolveAllowedTools(defaults.getAllowedTools(), agentDefinition.getAllowedTools(), options.allowedTools()),
                defaults.getResponseDetail(),
                defaults.getPriority()
        );
    }

    private ResolvedAgentPolicy fromDefaults(AgentPlatformProperties.Runtime defaults) {
        return new ResolvedAgentPolicy(
                defaults.getDefaultMode(),
                defaults.getMaxSteps(),
                defaults.getTemperature(),
                defaults.getMaxOutputTokens(),
                defaults.isAllowToolCalls(),
                defaults.isRequireHumanApproval(),
                defaults.getAllowedTools()
        );
    }

    private boolean resolveApproval(AgentPlatformProperties.Runtime defaults, AgentPlatformProperties.AgentDefinition agentDefinition) {
        return agentDefinition.getRequireHumanApproval() == null
                ? defaults.isRequireHumanApproval()
                : agentDefinition.getRequireHumanApproval();
    }

    private Set<String> resolveAllowedTools(Set<String> platformTools, Set<String> agentTools, Set<String> callerTools) {
        if (callerTools != null && !callerTools.isEmpty()) {
            return callerTools;
        }
        if (agentTools != null && !agentTools.isEmpty()) {
            return agentTools;
        }
        return platformTools;
    }

    private BigDecimal resolveCostCap(AgentPlatformProperties.AgentDefinition agentDefinition) {
        return agentDefinition.getMaxEstimatedCostUsd() == null
                ? properties.getAi().getMaxEstimatedCostUsd()
                : agentDefinition.getMaxEstimatedCostUsd();
    }

    private String firstText(String candidate, String fallback) {
        return candidate == null || candidate.isBlank() ? fallback : candidate;
    }
}

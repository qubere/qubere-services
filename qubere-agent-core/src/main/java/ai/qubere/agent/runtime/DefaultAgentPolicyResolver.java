package ai.qubere.agent.runtime;

import ai.qubere.agent.api.AgentRiskLevel;
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
        return resolve(agentId, requestedOptions, null);
    }

    @Override
    public ResolvedAgentPolicy resolve(String agentId, AgentRunOptions requestedOptions, AgentRiskLevel descriptorRiskLevel) {
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
                        ? resolveApproval(defaults, agentDefinition, descriptorRiskLevel)
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

    /**
     * Resolves whether human approval is required. Precedence:
     * <ol>
     *   <li>Explicit per-agent {@code require-human-approval} configuration always wins.</li>
     *   <li>Otherwise, if {@code agent-platform.governance.require-approval-for-high-risk} is
     *       enabled (default) and the agent's declared risk level is {@code HIGH} or
     *       {@code CRITICAL}, approval defaults to required. This is fail-closed: a descriptor
     *       declaring elevated risk cannot silently run unattended just because nobody wrote
     *       agent-specific configuration for it.</li>
     *   <li>Otherwise, the platform-wide runtime default applies.</li>
     * </ol>
     */
    private boolean resolveApproval(
            AgentPlatformProperties.Runtime defaults,
            AgentPlatformProperties.AgentDefinition agentDefinition,
            AgentRiskLevel descriptorRiskLevel
    ) {
        if (agentDefinition.getRequireHumanApproval() != null) {
            return agentDefinition.getRequireHumanApproval();
        }
        if (properties.getGovernance().isRequireApprovalForHighRisk() && isElevatedRisk(descriptorRiskLevel)) {
            return true;
        }
        return defaults.isRequireHumanApproval();
    }

    private boolean isElevatedRisk(AgentRiskLevel riskLevel) {
        return riskLevel == AgentRiskLevel.HIGH || riskLevel == AgentRiskLevel.CRITICAL;
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

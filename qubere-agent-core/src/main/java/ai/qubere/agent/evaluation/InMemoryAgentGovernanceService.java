package ai.qubere.agent.evaluation;

import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentOutput;
import ai.qubere.agent.core.AgentErrorCode;
import ai.qubere.agent.core.AgentExecutionException;
import ai.qubere.agent.core.ResolvedAgentPolicy;
import ai.qubere.agent.runtime.AgentGovernanceService;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAgentGovernanceService implements AgentGovernanceService {

    private final AgentPlatformProperties properties;
    private final Clock clock;
    private final Map<String, ArrayDeque<Instant>> tenantRuns = new ConcurrentHashMap<>();
    private final Map<String, ArrayDeque<Instant>> actorRuns = new ConcurrentHashMap<>();
    private final Map<String, ArrayDeque<Instant>> agentRuns = new ConcurrentHashMap<>();

    public InMemoryAgentGovernanceService(AgentPlatformProperties properties) {
        this(properties, Clock.systemUTC());
    }

    InMemoryAgentGovernanceService(AgentPlatformProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void beforeRun(AgentExecutionContext context, AgentDescriptor descriptor, ResolvedAgentPolicy policy) {
        AgentPlatformProperties.Governance governance = properties.getGovernance();
        if (!governance.isEnabled()) {
            return;
        }
        enforceRateLimit("tenant", context.tenantId(), governance.getMaxRunsPerTenantPerMinute(), tenantRuns);
        enforceRateLimit("actor", context.actorId(), governance.getMaxRunsPerActorPerMinute(), actorRuns);
        enforceRateLimit("agent", descriptor.id(), resolveAgentRateLimit(descriptor.id()), agentRuns);
        enforceCostLimit(descriptor, policy, governance);
    }

    private int resolveAgentRateLimit(String agentId) {
        AgentPlatformProperties.AgentDefinition definition = properties.getDefinitions().get(agentId);
        if (definition == null || definition.getMaxRunsPerMinute() == null) {
            return 0;
        }
        return definition.getMaxRunsPerMinute();
    }

    @Override
    public void afterRun(AgentExecutionContext context, AgentDescriptor descriptor, AgentOutput output) {
        // Reserved for future token/cost reconciliation once providers return final usage metadata.
    }

    private void enforceRateLimit(String scope, String key, int maxRunsPerMinute, Map<String, ArrayDeque<Instant>> buckets) {
        if (maxRunsPerMinute <= 0 || key == null || key.isBlank()) {
            return;
        }
        Instant now = Instant.now(clock);
        Instant cutoff = now.minus(1, ChronoUnit.MINUTES);
        ArrayDeque<Instant> bucket = buckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (bucket) {
            while (!bucket.isEmpty() && bucket.peekFirst().isBefore(cutoff)) {
                bucket.removeFirst();
            }
            if (bucket.size() >= maxRunsPerMinute) {
                throw new AgentExecutionException(
                        AgentErrorCode.GOVERNANCE_LIMIT_EXCEEDED,
                        "Agent run rejected by %s rate limit for %s".formatted(scope, key)
                );
            }
            bucket.addLast(now);
        }
    }

    private void enforceCostLimit(AgentDescriptor descriptor, ResolvedAgentPolicy policy, AgentPlatformProperties.Governance governance) {
        BigDecimal maxCost = governance.getMaxEstimatedCostUsdPerRun();
        BigDecimal unitCost = governance.getEstimatedCostUsdPerThousandTokens();
        if (maxCost.signum() <= 0 || unitCost.signum() <= 0) {
            return;
        }
        BigDecimal estimated = BigDecimal.valueOf(policy.maxOutputTokens())
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP)
                .multiply(unitCost);
        if (estimated.compareTo(maxCost) > 0) {
            throw new AgentExecutionException(
                    AgentErrorCode.GOVERNANCE_LIMIT_EXCEEDED,
                    "Agent run rejected by estimated cost limit for %s: estimated %s USD exceeds %s USD"
                            .formatted(descriptor.id(), estimated.stripTrailingZeros().toPlainString(), maxCost.stripTrailingZeros().toPlainString())
            );
        }
    }
}

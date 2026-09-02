package ai.qubere.agent.health;

import ai.qubere.agent.ai.AgentAiClient;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Reports whether an {@link AgentAiClient} bean is available (i.e. the Spring AI adapter is
 * configured and enabled) and which provider/model the platform is configured to use. This is a
 * configuration/bean-presence-based readiness signal, not a live network call to the model
 * provider: making a real provider round-trip on every health check would incur latency and cost
 * on every liveness/readiness probe. Deployed applications that need a live provider connectivity
 * check should compose a custom indicator that performs a bounded, low-cost provider call and can
 * safely be rate-limited or cached.
 */
public class AgentAiProviderHealthIndicator implements HealthIndicator {

    private final AgentPlatformProperties properties;
    private final ObjectProvider<AgentAiClient> aiClientProvider;

    public AgentAiProviderHealthIndicator(AgentPlatformProperties properties, ObjectProvider<AgentAiClient> aiClientProvider) {
        this.properties = properties == null ? new AgentPlatformProperties() : properties;
        this.aiClientProvider = aiClientProvider;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("provider", properties.getAi().getDefaultProvider());
        details.put("model", properties.getAi().getDefaultModel());

        boolean clientAvailable = aiClientProvider.getIfAvailable() != null;
        details.put("adapterConfigured", clientAvailable);

        if (!clientAvailable) {
            return Health.unknown()
                    .withDetail("reason", "No AgentAiClient bean is configured; the Spring AI adapter may be disabled")
                    .withDetails(details)
                    .build();
        }
        return Health.up().withDetails(details).build();
    }
}

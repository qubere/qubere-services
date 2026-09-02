package ai.qubere.agent.health;

import ai.qubere.agent.async.AgentAsyncQueue;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Reports the current depth of the async run queue. A configurable
 * {@code agent-platform.async.queue.max-healthy-depth} threshold (default {@code 0}, disabled)
 * can be set so operators are alerted through the health endpoint when queued work is backing up
 * faster than workers can drain it.
 */
public class AgentAsyncQueueHealthIndicator implements HealthIndicator {

    private final AgentAsyncQueue queue;
    private final AgentPlatformProperties properties;

    public AgentAsyncQueueHealthIndicator(AgentAsyncQueue queue, AgentPlatformProperties properties) {
        this.queue = queue;
        this.properties = properties == null ? new AgentPlatformProperties() : properties;
    }

    @Override
    public Health health() {
        int depth = queue.size();
        int maxHealthyDepth = properties.getAsync().getQueue().getMaxHealthyDepth();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("queuedRuns", depth);
        details.put("queueType", properties.getAsync().getQueue().getType());

        if (maxHealthyDepth > 0 && depth > maxHealthyDepth) {
            return Health.down()
                    .withDetail("reason", "Queue depth " + depth + " exceeds configured max-healthy-depth " + maxHealthyDepth)
                    .withDetails(details)
                    .build();
        }
        return Health.up().withDetails(details).build();
    }
}

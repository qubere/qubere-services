package ai.qubere.agent.evaluation;

import java.time.Instant;
import java.util.Map;

public record AgentRunSummary(
        long observedEvents,
        Map<String, Long> eventsByStep,
        Map<String, Long> eventsByAgent,
        Instant generatedAt
) {
    public AgentRunSummary {
        eventsByStep = eventsByStep == null ? Map.of() : Map.copyOf(eventsByStep);
        eventsByAgent = eventsByAgent == null ? Map.of() : Map.copyOf(eventsByAgent);
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
    }
}

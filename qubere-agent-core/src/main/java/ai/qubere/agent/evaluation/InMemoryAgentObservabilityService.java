package ai.qubere.agent.evaluation;

import ai.qubere.agent.runtime.AgentPipelineEvent;
import ai.qubere.agent.runtime.AgentPipelineListener;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class InMemoryAgentObservabilityService implements AgentPipelineListener {

    private final int maxEvents;
    private final ArrayDeque<AgentPipelineEvent> events = new ArrayDeque<>();

    public InMemoryAgentObservabilityService(int maxEvents) {
        this.maxEvents = Math.max(100, maxEvents);
    }

    @Override
    public synchronized void onEvent(AgentPipelineEvent event) {
        events.addLast(event);
        while (events.size() > maxEvents) {
            events.removeFirst();
        }
    }

    public synchronized List<AgentPipelineEvent> recentEvents(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, maxEvents));
        return events.stream()
                .sorted(Comparator.comparing(AgentPipelineEvent::occurredAt).reversed())
                .limit(safeLimit)
                .toList();
    }

    public synchronized AgentRunSummary summary() {
        List<AgentPipelineEvent> snapshot = new ArrayList<>(events);
        Map<String, Long> byStep = snapshot.stream()
                .collect(Collectors.groupingBy(event -> event.step().name(), Collectors.counting()));
        Map<String, Long> byAgent = snapshot.stream()
                .collect(Collectors.groupingBy(event -> event.descriptor().id() + ":" + event.descriptor().version(), Collectors.counting()));
        return new AgentRunSummary(snapshot.size(), byStep, byAgent, Instant.now());
    }
}

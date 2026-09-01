package ai.qubere.agent.observability;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class InMemoryAgentTelemetryExporter implements AgentTelemetryExporter {

    private final int maxEvents;
    private final ArrayDeque<AgentTelemetryEvent> events = new ArrayDeque<>();

    public InMemoryAgentTelemetryExporter(int maxEvents) {
        this.maxEvents = Math.max(100, maxEvents);
    }

    @Override
    public synchronized void export(AgentTelemetryEvent event) {
        if (event == null) {
            return;
        }
        events.addLast(event);
        while (events.size() > maxEvents) {
            events.removeFirst();
        }
    }

    public synchronized List<AgentTelemetryEvent> recentEvents(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, maxEvents));
        return new ArrayList<>(events).stream()
                .sorted(Comparator.comparing(AgentTelemetryEvent::occurredAt).reversed())
                .limit(safeLimit)
                .toList();
    }

    public synchronized int size() {
        return events.size();
    }
}
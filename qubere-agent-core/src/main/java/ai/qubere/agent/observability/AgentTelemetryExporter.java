package ai.qubere.agent.observability;

@FunctionalInterface
public interface AgentTelemetryExporter {

    void export(AgentTelemetryEvent event);

    static AgentTelemetryExporter noop() {
        return event -> {
        };
    }
}
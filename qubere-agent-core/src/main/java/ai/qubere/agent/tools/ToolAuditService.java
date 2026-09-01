package ai.qubere.agent.tools;

public interface ToolAuditService {

    void record(ToolAuditEvent event);

    static ToolAuditService noop() {
        return event -> {
        };
    }
}

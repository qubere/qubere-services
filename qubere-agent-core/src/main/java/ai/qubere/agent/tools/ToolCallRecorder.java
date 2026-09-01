package ai.qubere.agent.tools;

@FunctionalInterface
public interface ToolCallRecorder {

    void record(ToolCallRecord record);

    static ToolCallRecorder noop() {
        return record -> {
        };
    }
}
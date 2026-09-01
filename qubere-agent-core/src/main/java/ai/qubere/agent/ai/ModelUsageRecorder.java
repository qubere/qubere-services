package ai.qubere.agent.ai;

@FunctionalInterface
public interface ModelUsageRecorder {

    void record(ModelUsageRecord record);

    static ModelUsageRecorder noop() {
        return record -> {
        };
    }
}
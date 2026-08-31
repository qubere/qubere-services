package ai.qubere.agent.core;

public enum AgentRunStatus {
    QUEUED,
    RUNNING,
    WAITING_FOR_APPROVAL,
    SUCCEEDED,
    FAILED,
    CANCELLED
}

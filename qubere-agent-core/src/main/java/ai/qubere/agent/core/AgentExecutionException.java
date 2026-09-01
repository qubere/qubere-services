package ai.qubere.agent.core;

public class AgentExecutionException extends RuntimeException {

    private final AgentErrorCode errorCode;

    public AgentExecutionException(AgentErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AgentExecutionException(AgentErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public AgentErrorCode errorCode() {
        return errorCode;
    }
}

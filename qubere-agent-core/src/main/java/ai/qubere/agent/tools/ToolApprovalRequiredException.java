package ai.qubere.agent.tools;

import ai.qubere.agent.core.AgentErrorCode;
import ai.qubere.agent.core.AgentExecutionException;

public class ToolApprovalRequiredException extends AgentExecutionException {

    private final String approvalId;

    public ToolApprovalRequiredException(String message, String approvalId) {
        super(AgentErrorCode.TOOL_APPROVAL_REQUIRED, message);
        this.approvalId = approvalId;
    }

    public String approvalId() {
        return approvalId;
    }
}

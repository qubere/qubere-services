package ai.qubere.agent.tools;

public record ToolApprovalDecision(
        boolean allowed,
        boolean approvalRequired,
        String reason
) {
    public static ToolApprovalDecision allow() {
        return new ToolApprovalDecision(true, false, "Tool execution allowed");
    }

    public static ToolApprovalDecision requireApproval(String reason) {
        return new ToolApprovalDecision(false, true, reason);
    }

    public static ToolApprovalDecision deny(String reason) {
        return new ToolApprovalDecision(false, false, reason);
    }
}

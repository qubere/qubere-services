package ai.qubere.agent.tools;

public interface ToolApprovalPolicy {

    ToolApprovalDecision evaluate(ToolDescriptor descriptor, ToolExecutionRequest request);

    static ToolApprovalPolicy defaultPolicy() {
        return (descriptor, request) -> {
            if (request.policy() != null && request.policy().requireHumanApproval()) {
                return ToolApprovalDecision.requireApproval("Run policy requires human approval before tool execution: " + descriptor.name());
            }
            if (descriptor.humanApprovalRequired()) {
                return ToolApprovalDecision.requireApproval("Tool requires human approval: " + descriptor.name());
            }
            if (descriptor.riskLevel() == ToolRiskLevel.CRITICAL || descriptor.riskLevel() == ToolRiskLevel.DESTRUCTIVE) {
                return ToolApprovalDecision.requireApproval("High-risk tool requires human approval: " + descriptor.name());
            }
            if (descriptor.sideEffects().contains(ToolSideEffect.DESTRUCTIVE)) {
                return ToolApprovalDecision.requireApproval("Destructive tool requires human approval: " + descriptor.name());
            }
            return ToolApprovalDecision.allow();
        };
    }
}

package ai.qubere.agent.tools;

import java.util.Optional;

public interface ToolApprovalRequestSink {

    Optional<String> requestApproval(ToolDescriptor descriptor, ToolExecutionRequest request, ToolApprovalDecision decision);

    static ToolApprovalRequestSink noop() {
        return (descriptor, request, decision) -> Optional.empty();
    }
}

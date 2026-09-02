package ai.qubere.agent.orchestration;

import java.util.Map;

/**
 * Result of invoking an agent hosted by another service.
 *
 * @param executionId execution id assigned by the remote service
 * @param agentId     id of the remote agent that ran
 * @param status      remote run status as reported by the remote service, e.g. {@code SUCCEEDED}
 *                    or {@code WAITING_FOR_APPROVAL} for an async/approval-gated remote run
 * @param output      remote agent output payload, empty when the run did not complete
 * @param approvalId  approval id when the remote run paused for human approval, otherwise {@code null}
 */
public record RemoteAgentRunResult(
        String executionId,
        String agentId,
        String status,
        Map<String, Object> output,
        String approvalId
) {
    public RemoteAgentRunResult {
        output = output == null ? Map.of() : Map.copyOf(output);
    }

    public boolean succeeded() {
        return "SUCCEEDED".equalsIgnoreCase(status);
    }

    public boolean awaitingApproval() {
        return "WAITING_FOR_APPROVAL".equalsIgnoreCase(status);
    }
}

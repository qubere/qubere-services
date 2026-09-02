package ai.qubere.agent.orchestration;

import ai.qubere.agent.runtime.AgentExecutionRecord;

import java.time.Instant;
import java.util.List;

/**
 * Rolled-up view of one orchestrated multi-agent run: the workflow's overall status plus every
 * participating execution, oldest first.
 *
 * @param workflowId       id shared by all executions in the orchestration
 * @param status           aggregate status derived from all child execution statuses
 * @param totalExecutions  number of executions in the workflow
 * @param succeeded        count of executions that completed successfully
 * @param failed           count of executions that failed
 * @param running          count of executions still queued or running
 * @param waitingApproval  count of executions paused for human approval
 * @param cancelled        count of executions that were cancelled
 * @param startedAt        earliest execution start in the workflow
 * @param updatedAt        latest execution update in the workflow
 * @param executions       all participating executions, oldest first
 */
public record AgentWorkflowSummary(
        String workflowId,
        AgentWorkflowStatus status,
        int totalExecutions,
        int succeeded,
        int failed,
        int running,
        int waitingApproval,
        int cancelled,
        Instant startedAt,
        Instant updatedAt,
        List<AgentExecutionRecord> executions
) {
    public AgentWorkflowSummary {
        executions = executions == null ? List.of() : List.copyOf(executions);
    }

    /**
     * Derives a workflow summary from the executions belonging to one workflow.
     */
    public static AgentWorkflowSummary from(String workflowId, List<AgentExecutionRecord> executions) {
        List<AgentExecutionRecord> safeExecutions = executions == null ? List.of() : List.copyOf(executions);
        if (safeExecutions.isEmpty()) {
            return new AgentWorkflowSummary(workflowId, AgentWorkflowStatus.UNKNOWN, 0, 0, 0, 0, 0, 0, null, null, List.of());
        }

        int succeeded = 0;
        int failed = 0;
        int running = 0;
        int waitingApproval = 0;
        int cancelled = 0;
        Instant startedAt = null;
        Instant updatedAt = null;

        for (AgentExecutionRecord execution : safeExecutions) {
            switch (execution.status()) {
                case SUCCEEDED -> succeeded++;
                case FAILED -> failed++;
                case QUEUED, RUNNING -> running++;
                case WAITING_FOR_APPROVAL -> waitingApproval++;
                case CANCELLED -> cancelled++;
            }
            if (startedAt == null || (execution.createdAt() != null && execution.createdAt().isBefore(startedAt))) {
                startedAt = execution.createdAt();
            }
            if (updatedAt == null || (execution.updatedAt() != null && execution.updatedAt().isAfter(updatedAt))) {
                updatedAt = execution.updatedAt();
            }
        }

        return new AgentWorkflowSummary(
                workflowId,
                deriveStatus(safeExecutions.size(), succeeded, failed, running, waitingApproval, cancelled),
                safeExecutions.size(),
                succeeded,
                failed,
                running,
                waitingApproval,
                cancelled,
                startedAt,
                updatedAt,
                safeExecutions
        );
    }

    /**
     * Status precedence is deliberately "in-flight beats terminal": a workflow with any execution
     * still running or awaiting approval is not yet finished, even if some branch already failed,
     * because the outcome can still change. Only once every execution is terminal does the
     * summary distinguish full success, full failure, or partial failure.
     */
    private static AgentWorkflowStatus deriveStatus(int total, int succeeded, int failed, int running, int waitingApproval, int cancelled) {
        if (waitingApproval > 0) {
            return AgentWorkflowStatus.WAITING_FOR_APPROVAL;
        }
        if (running > 0) {
            return AgentWorkflowStatus.RUNNING;
        }
        if (succeeded == total) {
            return AgentWorkflowStatus.SUCCEEDED;
        }
        if (failed == total) {
            return AgentWorkflowStatus.FAILED;
        }
        if (cancelled == total) {
            return AgentWorkflowStatus.CANCELLED;
        }
        if (failed > 0) {
            return AgentWorkflowStatus.PARTIAL_FAILURE;
        }
        // Remaining mixes are terminal but neither all-succeeded nor all-failed, e.g. some
        // succeeded and some cancelled.
        return AgentWorkflowStatus.PARTIAL_FAILURE;
    }

    /**
     * Convenience predicate for callers polling a workflow to completion.
     */
    public boolean isTerminal() {
        return status != AgentWorkflowStatus.RUNNING
                && status != AgentWorkflowStatus.WAITING_FOR_APPROVAL
                && status != AgentWorkflowStatus.UNKNOWN;
    }

    /**
     * Returns the root execution of the workflow, i.e. the one with no parent.
     */
    public AgentExecutionRecord root() {
        return executions.stream()
                .filter(AgentExecutionRecord::isWorkflowRoot)
                .findFirst()
                .orElse(null);
    }
}

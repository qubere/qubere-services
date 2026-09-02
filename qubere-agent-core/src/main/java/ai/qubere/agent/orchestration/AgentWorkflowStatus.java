package ai.qubere.agent.orchestration;

/**
 * Aggregate status of an orchestrated multi-agent workflow, derived from the statuses of all
 * participating executions. This is intentionally distinct from
 * {@link ai.qubere.agent.core.AgentRunStatus}, which describes a single execution: a workflow has
 * outcomes a single run cannot have, most importantly {@link #PARTIAL_FAILURE}.
 */
public enum AgentWorkflowStatus {

    /** No executions found for the workflow id. */
    UNKNOWN,

    /** At least one execution is still queued or running. */
    RUNNING,

    /** At least one execution is paused awaiting human approval. */
    WAITING_FOR_APPROVAL,

    /** Every execution completed successfully. */
    SUCCEEDED,

    /** Every execution failed. */
    FAILED,

    /** Every execution was cancelled. */
    CANCELLED,

    /**
     * All executions are terminal but the outcome is mixed, e.g. some sub-agents succeeded while
     * others failed or were cancelled. Callers must decide whether a partial result is usable.
     */
    PARTIAL_FAILURE
}

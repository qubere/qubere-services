package ai.qubere.agent.runtime;

import ai.qubere.agent.api.AgentExecutionContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Well-known {@link AgentExecutionContext} attribute keys and helpers that link a sub-agent
 * execution to the orchestration it belongs to.
 * <p>
 * Without this linkage, an orchestrator agent that invokes several sub-agents produces several
 * independent {@code agent_execution_record} rows with no way to answer "show me everything that
 * happened in workflow X". Two attributes solve that:
 * <ul>
 *   <li>{@link #WORKFLOW_ID} — stable id shared by every execution in one orchestrated run.
 *       The root (outermost) execution's id becomes the workflow id when it is not already set.</li>
 *   <li>{@link #PARENT_EXECUTION_ID} — the id of the execution that directly invoked this one,
 *       preserving the call tree rather than only a flat workflow grouping.</li>
 * </ul>
 * Both are plain strings so they survive serialization into durable pending-command/queue
 * payloads and can be propagated across service boundaries as HTTP headers.
 */
public final class AgentWorkflowContext {

    public static final String WORKFLOW_ID = "workflowId";
    public static final String PARENT_EXECUTION_ID = "parentExecutionId";
    /**
     * Attribute holding the {@link AgentWorkflowBudget} shared by every execution in the
     * workflow. Unlike {@link #WORKFLOW_ID}, this is a live object and therefore only propagates
     * within a single JVM; cross-service orchestration must re-establish budget enforcement at
     * the remote boundary.
     */
    public static final String WORKFLOW_BUDGET = "agentWorkflowBudget";
    /**
     * Number of delegation hops between the workflow root and this execution. The root is depth
     * {@code 0}; a sub-agent it invokes is depth {@code 1}, and so on.
     */
    public static final String DELEGATION_DEPTH = "agentDelegationDepth";
    /**
     * Ordered ids of the agents on the delegation path from the workflow root down to (and
     * including) the currently executing agent.
     * <p>
     * This exists because workflow id and parent execution id alone cannot detect a delegation
     * <em>cycle</em>. Checking only "is the target the same as my caller" catches A&nbsp;→&nbsp;A
     * but misses A&nbsp;→&nbsp;B&nbsp;→&nbsp;A, which would then recurse until the workflow budget
     * or the JVM stack was exhausted — an expensive and confusing failure mode for what is really
     * a configuration mistake. Keeping the ancestor chain lets the cycle be rejected on the first
     * repeat with an actionable error naming the full path.
     */
    public static final String AGENT_PATH = "agentPath";

    private AgentWorkflowContext() {
    }

    /**
     * Returns the number of delegation hops between the workflow root and {@code context}.
     */
    public static int delegationDepth(AgentExecutionContext context) {
        if (context == null) {
            return 0;
        }
        return context.attributes().get(DELEGATION_DEPTH) instanceof Number depth ? depth.intValue() : 0;
    }

    /**
     * Returns the agent ids on the delegation path from the workflow root to {@code context},
     * oldest first. Never {@code null}.
     */
    @SuppressWarnings("unchecked")
    public static List<String> agentPath(AgentExecutionContext context) {
        if (context == null) {
            return List.of();
        }
        Object value = context.attributes().get(AGENT_PATH);
        if (value instanceof List<?> path) {
            return List.copyOf((List<String>) path);
        }
        return List.of();
    }

    /**
     * Returns the aggregate workflow budget carried by this context, or {@code null} when the
     * execution is not participating in a budgeted workflow.
     */
    public static AgentWorkflowBudget workflowBudget(AgentExecutionContext context) {
        if (context == null) {
            return null;
        }
        return context.attributes().get(WORKFLOW_BUDGET) instanceof AgentWorkflowBudget budget ? budget : null;
    }

    /**
     * Returns the workflow id this context belongs to, or {@code null} when the context is not
     * part of a linked orchestration.
     */
    public static String workflowId(AgentExecutionContext context) {
        return stringAttribute(context, WORKFLOW_ID);
    }

    /**
     * Returns the id of the execution that directly invoked this one, or {@code null} for a
     * root execution.
     */
    public static String parentExecutionId(AgentExecutionContext context) {
        return stringAttribute(context, PARENT_EXECUTION_ID);
    }

    /**
     * Builds the execution context for a sub-agent invoked by {@code parent}.
     * <p>
     * The child inherits the parent's workflow id when the parent already has one; otherwise the
     * parent's own execution id becomes the workflow id, making the outermost execution the
     * workflow root. Tenant, actor, and correlation id are inherited unchanged so the child
     * cannot escape the parent's security scope or break distributed trace correlation.
     *
     * @param parent           context of the invoking (orchestrator) execution
     * @param childExecutionId execution id assigned to the sub-agent run
     */
    public static AgentExecutionContext childOf(AgentExecutionContext parent, String childExecutionId) {
        return childOf(parent, childExecutionId, null);
    }

    /**
     * Builds the execution context for a sub-agent invoked by {@code parent}, additionally
     * recording the delegation depth and the agent path so cycles and runaway nesting can be
     * detected.
     *
     * @param parent           context of the invoking (orchestrator) execution
     * @param childExecutionId execution id assigned to the sub-agent run
     * @param targetAgentId    id of the agent being invoked; when {@code null} the agent path is
     *                         carried through unchanged
     */
    public static AgentExecutionContext childOf(
            AgentExecutionContext parent,
            String childExecutionId,
            String targetAgentId
    ) {
        if (parent == null) {
            throw new IllegalArgumentException("Parent execution context is required to build a child context");
        }
        if (childExecutionId == null || childExecutionId.isBlank()) {
            throw new IllegalArgumentException("Child execution id is required");
        }
        Object parentAgentId = parent.attributes().get("agentId");
        Map<String, Object> attributes = new HashMap<>(parent.attributes());
        // Runtime-scoped state must not leak into the child: the child gets its own resolved
        // policy and run budget when its own run() resolves them.
        attributes.remove("resolvedPolicy");
        attributes.remove("agentRunBudget");
        attributes.remove("agentId");
        attributes.remove("agentVersion");

        String inheritedWorkflowId = workflowId(parent);
        attributes.put(WORKFLOW_ID, inheritedWorkflowId == null ? parent.executionId() : inheritedWorkflowId);
        attributes.put(PARENT_EXECUTION_ID, parent.executionId());
        attributes.put(DELEGATION_DEPTH, delegationDepth(parent) + 1);

        List<String> path = new ArrayList<>(agentPath(parent));
        // The orchestrator itself may not be on the path yet (it is the workflow root the first
        // time it delegates), so seed it before appending the target.
        if (parentAgentId != null && (path.isEmpty() || !path.get(path.size() - 1).equals(parentAgentId.toString()))) {
            path.add(parentAgentId.toString());
        }
        if (targetAgentId != null && !targetAgentId.isBlank()) {
            path.add(targetAgentId);
        }
        attributes.put(AGENT_PATH, List.copyOf(path));

        return new AgentExecutionContext(
                childExecutionId,
                parent.tenantId(),
                parent.actorId(),
                parent.correlationId(),
                Instant.now(),
                attributes
        );
    }

    private static String stringAttribute(AgentExecutionContext context, String key) {
        if (context == null) {
            return null;
        }
        Object value = context.attributes().get(key);
        return value == null ? null : value.toString();
    }
}

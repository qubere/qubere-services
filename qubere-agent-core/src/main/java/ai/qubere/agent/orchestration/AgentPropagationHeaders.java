package ai.qubere.agent.orchestration;

/**
 * Standard HTTP headers used to propagate agent execution context across service boundaries.
 * <p>
 * Once orchestration spans multiple Spring Boot services, workflow linkage and distributed trace
 * correlation only survive if the caller forwards them explicitly. These constants keep the
 * outbound {@link RemoteAgentClient} and any inbound
 * {@code ai.qubere.agent.runtime.security.AgentCallerIdentityResolver} implementation agreed on
 * the same wire format.
 * <p>
 * Identity headers ({@code X-Tenant-Id}, {@code X-Actor-Id}) are deliberately <em>not</em>
 * defined here as something a remote agent should blindly trust. A receiving service must still
 * resolve caller identity through its own authenticated
 * {@code AgentCallerIdentityResolver}; these propagation headers carry correlation and workflow
 * metadata, not authorization decisions.
 */
public final class AgentPropagationHeaders {

    /** Correlation id shared by every execution in a distributed trace. */
    public static final String CORRELATION_ID = "X-Correlation-Id";

    /** Workflow id shared by every execution in one orchestrated multi-agent run. */
    public static final String WORKFLOW_ID = "X-Agent-Workflow-Id";

    /** Execution id of the calling agent, becoming the remote execution's parent. */
    public static final String PARENT_EXECUTION_ID = "X-Agent-Parent-Execution-Id";

    /** Idempotency key so a retried cross-service call does not duplicate remote work. */
    public static final String IDEMPOTENCY_KEY = "X-Idempotency-Key";

    private AgentPropagationHeaders() {
    }
}

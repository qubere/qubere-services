package ai.qubere.agent.orchestration;

import ai.qubere.agent.api.AgentExecutionContext;

import java.util.Map;

/**
 * Invokes an agent hosted by another service, mirroring the local
 * {@link ai.qubere.agent.runtime.AgentRuntimeService#run} call shape so an orchestrator can
 * delegate to a remote sub-agent using the same mental model as a local one.
 * <p>
 * This is the seam for the "each agent as its own Spring Boot service" topology. Implementations
 * are responsible for propagating workflow/correlation metadata (see
 * {@link AgentPropagationHeaders}) and for applying transport concerns such as timeouts, retries,
 * and circuit breaking.
 * <p>
 * Note what this contract intentionally does <em>not</em> promise: the live
 * {@link ai.qubere.agent.runtime.AgentWorkflowBudget} object cannot cross a process boundary, so
 * aggregate workflow budgets are enforced independently on each side. A deployment that needs a
 * single hard ceiling spanning services must add a shared, durable budget store; the in-process
 * budget alone is not sufficient once orchestration is distributed.
 */
public interface RemoteAgentClient {

    /**
     * Invokes a remote agent as a sub-agent of the given calling context.
     *
     * @param agentId      id of the agent to invoke on the remote service
     * @param agentVersion specific version, or {@code null} for the remote service's default
     * @param input        input payload passed to the remote agent
     * @param callerContext execution context of the invoking agent, used to derive workflow and
     *                      correlation propagation headers
     * @return the remote run result
     */
    RemoteAgentRunResult run(
            String agentId,
            String agentVersion,
            Map<String, Object> input,
            AgentExecutionContext callerContext
    );
}

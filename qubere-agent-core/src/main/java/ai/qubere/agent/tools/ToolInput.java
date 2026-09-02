package ai.qubere.agent.tools;

import ai.qubere.agent.api.AgentExecutionContext;

import java.util.Map;

/**
 * Input handed to an {@link AgentTool} implementation.
 *
 * @param executionId  id of the execution invoking the tool
 * @param tenantId     tenant scope of the invoking execution
 * @param actorId      actor scope of the invoking execution
 * @param arguments    tool-specific arguments
 * @param callerContext full execution context of the invoking agent, or {@code null} when the
 *                      tool was invoked outside an agent run. Tools that need to propagate
 *                      scoped context — most notably a tool that invokes another agent and must
 *                      preserve workflow linkage and the shared workflow budget — read it from
 *                      here rather than reconstructing a context from the flat id fields.
 */
public record ToolInput(
        String executionId,
        String tenantId,
        String actorId,
        Map<String, Object> arguments,
        AgentExecutionContext callerContext
) {
    public ToolInput {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }

    public ToolInput(String executionId, String tenantId, String actorId, Map<String, Object> arguments) {
        this(executionId, tenantId, actorId, arguments, null);
    }
}

package ai.qubere.agent.runtime;

import ai.qubere.agent.core.AgentErrorCode;
import ai.qubere.agent.core.AgentExecutionException;
import ai.qubere.agent.core.ResolvedAgentPolicy;

import java.util.concurrent.atomic.AtomicInteger;

public class AgentRunBudget {

    private final int maxSteps;
    private final int maxToolCalls;
    private final AtomicInteger usedSteps = new AtomicInteger();
    private final AtomicInteger usedToolCalls = new AtomicInteger();

    public AgentRunBudget(ResolvedAgentPolicy policy) {
        this.maxSteps = policy == null ? 0 : Math.max(0, policy.maxSteps());
        this.maxToolCalls = policy == null ? 0 : Math.max(0, policy.maxToolCalls());
    }

    public int consumeStep(String reason) {
        int used = usedSteps.incrementAndGet();
        if (maxSteps > 0 && used > maxSteps) {
            throw new AgentExecutionException(
                    AgentErrorCode.GOVERNANCE_LIMIT_EXCEEDED,
                    "Agent run exceeded maxSteps=" + maxSteps + " while executing " + reason
            );
        }
        return used;
    }

    public int consumeToolCall(String toolName) {
        int used = usedToolCalls.incrementAndGet();
        if (maxToolCalls >= 0 && used > maxToolCalls) {
            throw new AgentExecutionException(
                    AgentErrorCode.TOOL_NOT_ALLOWED,
                    "Agent run exceeded maxToolCalls=" + maxToolCalls + " while executing tool " + toolName
            );
        }
        consumeStep("tool " + toolName);
        return used;
    }

    public int usedSteps() {
        return usedSteps.get();
    }

    public int usedToolCalls() {
        return usedToolCalls.get();
    }
}
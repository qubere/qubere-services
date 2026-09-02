package ai.qubere.agent.redteam;

/**
 * The safe behavior a {@link RedTeamCase} requires from the agent under adversarial input.
 */
public enum RedTeamExpectation {

    /**
     * The run must be refused before the agent does any work — by a guardrail, authorization
     * check, or governance limit. Appropriate for inputs that should never reach the model.
     */
    BLOCKED,

    /**
     * The run may complete, but must not invoke the case's forbidden tools or emit its forbidden
     * text. Appropriate for inputs the agent is allowed to reason about as long as it does not
     * take the dangerous action the attacker is steering it toward.
     */
    COMPLETED_WITHOUT_VIOLATION,

    /**
     * The run must pause for human approval rather than acting autonomously. Appropriate for
     * inputs that attempt to trick an agent into performing a high-risk action unattended.
     */
    REQUIRES_APPROVAL
}

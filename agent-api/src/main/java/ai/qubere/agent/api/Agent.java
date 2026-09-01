package ai.qubere.agent.api;

/**
 * Stable contract every agent implements.
 *
 * @param <I> typed input accepted by the agent
 * @param <O> typed output produced by the agent
 */
public interface Agent<I extends AgentInput, O extends AgentOutput> {

    AgentDescriptor descriptor();

    O run(I input, AgentExecutionContext context);
}

package ai.qubere.agent.ai;

import reactor.core.publisher.Flux;

public interface AgentAiClient {

    <T> T generate(AgentPrompt prompt, Class<T> responseType);

    default <T> T generate(AgentPrompt prompt, Class<T> responseType, AgentAiRequestMetadata metadata) {
        return generate(prompt, responseType);
    }

    /**
     * Streams raw text tokens as the model generates them, for conversational or long-generation
     * agent UX where waiting for the full structured response is not acceptable latency.
     * <p>
     * Unlike {@link #generate}, streaming does not support structured/typed output: providers
     * return incremental text chunks, and a caller wanting structured output should either wait
     * for a future structured-streaming API or fall back to {@link #generate}. Token/cost usage
     * for a streamed call may be incomplete or unavailable depending on provider streaming
     * behavior; do not rely on {@code agent_model_usage} rows for streamed calls having the same
     * completeness as non-streamed calls.
     * <p>
     * The default implementation throws {@link UnsupportedOperationException} so existing
     * {@link AgentAiClient} implementations written before streaming existed keep compiling and
     * behave predictably (a clear failure) rather than silently returning incorrect behavior.
     *
     * @param prompt   the prompt to send to the model
     * @param metadata run/policy metadata, as with {@link #generate(AgentPrompt, Class, AgentAiRequestMetadata)}
     * @return a stream of text chunks as the model generates them
     */
    default Flux<String> generateStream(AgentPrompt prompt, AgentAiRequestMetadata metadata) {
        throw new UnsupportedOperationException("Streaming is not supported by this AgentAiClient implementation");
    }
}

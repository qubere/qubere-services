package ai.qubere.document.agent.document.parser;

/**
 * @param runId         opaque Qubere run id; providers may pass it through for correlation
 * @param correlationId propagated across upload -&gt; queue -&gt; provider -&gt; result
 * @param profile       requested processing profile
 * @param source        how the document is delivered to the provider
 */
public record ParserSubmission(String runId, String correlationId, ProcessingProfile profile, ParserSource source) {
}

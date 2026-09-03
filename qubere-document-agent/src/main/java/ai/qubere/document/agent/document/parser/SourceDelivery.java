package ai.qubere.document.agent.document.parser;

/**
 * How a document reaches the provider. {@code SIGNED_URL} hands the provider a short-lived URL to
 * Qubere-controlled object storage; client-supplied URLs are never an option — that would be an
 * SSRF vector.
 */
public enum SourceDelivery {
    INLINE,
    SIGNED_URL
}

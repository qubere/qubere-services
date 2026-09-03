package ai.qubere.document.agent.document.parser;

/**
 * Provider-neutral document parser contract, ported from {@code parser/contracts.ts}. Nothing in
 * this interface (or the types it uses) may mention a specific vendor: downstream code depends
 * only on this contract, so a second parser provider can be introduced without touching business
 * logic. Vendor-specific payload shapes belong next to their provider implementation, not here.
 */
public interface DocumentParserProvider {

    /** Stable identifier persisted on the run, e.g. {@code "IBM_DOCLING"} or {@code "MOCK"}. */
    String providerId();

    /** {@code true} for providers that must never be trusted in production. */
    boolean isMockProvider();

    /** How this provider wants the document delivered. */
    SourceDelivery sourceDelivery();

    /**
     * Hash of the provider configuration that affects output (base URL, profile-option mapping,
     * contract version). Feeds the run's idempotency key so a configuration change produces a new
     * run rather than colliding with an old one.
     */
    String configurationHash(ProcessingProfile profile);

    ParserSubmissionAck submit(ParserSubmission submission);

    ParserJobStatus getStatus(ParserJobReference reference);

    ParserResult getResult(ParserJobReference reference, ProcessingProfile profile);
}

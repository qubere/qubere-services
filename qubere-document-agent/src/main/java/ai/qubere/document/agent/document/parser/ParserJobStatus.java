package ai.qubere.document.agent.document.parser;

import java.time.Instant;

/**
 * @param state          restricted to {@code POLLING}, {@code SUCCEEDED}, or {@code FAILED}; never
 *                       {@code SUCCEEDED} with no result available — {@code SUCCEEDED} here means
 *                       {@link DocumentParserProvider#getResult} may be called
 * @param providerStatus sanitized provider status string, kept for operational forensics
 * @param error          present only when {@code state} is {@code FAILED}
 * @param observedAt     when this status was observed
 */
public record ParserJobStatus(
        ProcessingRunState state,
        String providerStatus,
        DocumentParserException error,
        Instant observedAt
) {
}

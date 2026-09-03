package ai.qubere.document.agent.document.parser;

import java.time.Instant;
import java.util.List;

/**
 * @param externalTaskId    durable provider-side task identifier, persisted immediately
 * @param providerStatus    raw provider status at submission, already sanitized
 * @param state             Qubere state to record; restricted to {@code SUBMITTED}, {@code POLLING},
 *                          or {@code SUCCEEDED} (providers that complete synchronously may return
 *                          {@code SUCCEEDED}) — not enforced at the type level, but callers must
 *                          not pass any other {@link ProcessingRunState}
 * @param unsupportedOptions profile options this provider could not honor, recorded as warnings on
 *                          the run so nobody reads e.g. {@code FULL_PAGE_OCR} as proof it actually ran
 * @param submittedAt       when the provider accepted the submission
 */
public record ParserSubmissionAck(
        String externalTaskId,
        String providerStatus,
        ProcessingRunState state,
        List<String> unsupportedOptions,
        Instant submittedAt
) {
    public ParserSubmissionAck {
        unsupportedOptions = unsupportedOptions == null ? List.of() : List.copyOf(unsupportedOptions);
    }
}

package ai.qubere.document.agent.document.parser;

import java.util.EnumSet;
import java.util.Set;

/** Structured, persistable/exposable parser failure codes, ported from {@code parser/contracts.ts}. */
public enum ParserErrorCode {
    // Intake-side
    UNSUPPORTED_FILE_TYPE,
    INVALID_FILE,
    EMPTY_FILE,
    FILE_TOO_LARGE,
    PDF_ENCRYPTED,
    PDF_CORRUPTED,
    MALWARE_QUARANTINED,
    // Provider-side
    PARSER_NOT_CONFIGURED,
    PARSER_SUBMISSION_FAILED,
    PARSER_TIMEOUT,
    PARSER_PROVIDER_ERROR,
    PARSER_RESULT_INVALID,
    PARSER_RESULT_INCOMPLETE,
    // Qubere-side
    ARTIFACT_STORAGE_FAILED,
    SOURCE_FILE_UNAVAILABLE,
    QUALITY_REVIEW_REQUIRED;

    /** Error codes that are never worth another attempt. */
    public static final Set<ParserErrorCode> NON_RETRYABLE = EnumSet.of(
            UNSUPPORTED_FILE_TYPE, INVALID_FILE, EMPTY_FILE, FILE_TOO_LARGE,
            PDF_ENCRYPTED, PDF_CORRUPTED, MALWARE_QUARANTINED,
            PARSER_NOT_CONFIGURED, PARSER_RESULT_INVALID, QUALITY_REVIEW_REQUIRED
    );

    public boolean isRetryableByDefault() {
        return !NON_RETRYABLE.contains(this);
    }
}

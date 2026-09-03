package ai.qubere.document.agent.document.parser;

/**
 * A parser failure with a code that is safe to persist and expose, ported from
 * {@code parser/contracts.ts}'s {@code DocumentParserError}.
 * <p>
 * {@link #getMessage()} must never carry provider response bodies, credentials, signed URLs, or
 * document content — callers constructing this are responsible for sanitizing first.
 * {@link #retryable} is decided by the caller by default from {@link ParserErrorCode#isRetryableByDefault()},
 * but can be overridden explicitly; this is why the constructor takes an explicit {@code Boolean}
 * rather than always deriving it, mirroring the source's {@code options?.retryable ?? defaultRetryable}.
 */
public class DocumentParserException extends RuntimeException {

    private final ParserErrorCode code;
    private final boolean retryable;
    /** Sanitized provider status string, when the failure came from a provider. */
    private final String providerStatus;

    public DocumentParserException(ParserErrorCode code, String message) {
        this(code, message, null, null, null);
    }

    public DocumentParserException(ParserErrorCode code, String message, Boolean retryable, String providerStatus, Throwable cause) {
        super(message, cause);
        if (code == null) {
            throw new IllegalArgumentException("Parser error code is required");
        }
        this.code = code;
        this.retryable = retryable != null ? retryable : code.isRetryableByDefault();
        this.providerStatus = providerStatus;
    }

    public ParserErrorCode code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }

    public String providerStatus() {
        return providerStatus;
    }
}

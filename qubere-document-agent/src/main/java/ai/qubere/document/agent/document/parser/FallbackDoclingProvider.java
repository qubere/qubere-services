package ai.qubere.document.agent.document.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes document parsing via a primary provider with automatic failover to a backup provider
 * when the primary is unavailable or fails, ported from {@code parser/fallbackProvider.ts}.
 * <p>
 * A retryable primary failure is treated as transient — a provider hiccup, a result that has not
 * finished landing — and is resolved by re-queuing the run against the same primary provider.
 * Falling through to the backup on a retryable failure would replace a real parse-in-progress with
 * a different provider's result, so only a <strong>non-retryable</strong> primary failure hands off.
 * <p>
 * <strong>Known limitation, preserved faithfully from the source rather than silently
 * "fixed":</strong> {@link #getStatus} and {@link #getResult} can fail over to the backup
 * mid-flight, after {@link #submit} already succeeded against the primary. The backup provider has
 * no knowledge of the primary's {@code externalTaskId}, so this only behaves sensibly today because
 * failures at that late stage are rare in practice and the worker treats a failed poll as a reason
 * to re-queue rather than trusting a fallback result blindly. A cleaner design — e.g. a circuit
 * breaker that fails the whole run rather than silently switching providers mid-poll — is recorded
 * as a documented improvement opportunity in {@code MIGRATION.md} rather than assumed away here.
 */
public class FallbackDoclingProvider implements DocumentParserProvider {

    private static final Logger log = LoggerFactory.getLogger(FallbackDoclingProvider.class);

    public static final String PROVIDER_ID = "docling-primary-with-fallback";

    private final DocumentParserProvider primary;
    private final DocumentParserProvider backup;

    public FallbackDoclingProvider(DocumentParserProvider primary, DocumentParserProvider backup) {
        if (primary == null || backup == null) {
            throw new IllegalArgumentException("Both a primary and a backup provider are required");
        }
        this.primary = primary;
        this.backup = backup;
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean isMockProvider() {
        return primary.isMockProvider() && backup.isMockProvider();
    }

    @Override
    public SourceDelivery sourceDelivery() {
        return primary.sourceDelivery();
    }

    @Override
    public String configurationHash(ProcessingProfile profile) {
        return primary.configurationHash(profile) + "-fallback-" + backup.configurationHash(profile);
    }

    @Override
    public ParserSubmissionAck submit(ParserSubmission submission) {
        try {
            return primary.submit(submission);
        } catch (DocumentParserException ex) {
            if (!shouldFailOver(ex)) {
                throw ex;
            }
            log.warn("Primary parser submit failed, using backup provider: {}", ex.getMessage());
            return backup.submit(submission);
        }
    }

    @Override
    public ParserJobStatus getStatus(ParserJobReference reference) {
        try {
            return primary.getStatus(reference);
        } catch (DocumentParserException ex) {
            if (!shouldFailOver(ex)) {
                throw ex;
            }
            log.warn("Primary parser getStatus failed, using backup provider: {}", ex.getMessage());
            return backup.getStatus(reference);
        }
    }

    @Override
    public ParserResult getResult(ParserJobReference reference, ProcessingProfile profile) {
        try {
            return primary.getResult(reference, profile);
        } catch (DocumentParserException ex) {
            if (!shouldFailOver(ex)) {
                throw ex;
            }
            log.warn("Primary parser getResult failed, using backup provider: {}", ex.getMessage());
            return backup.getResult(reference, profile);
        }
    }

    private static boolean shouldFailOver(DocumentParserException ex) {
        return !ex.retryable();
    }
}

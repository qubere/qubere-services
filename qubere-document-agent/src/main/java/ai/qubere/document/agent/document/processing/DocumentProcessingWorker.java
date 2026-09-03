package ai.qubere.document.agent.document.processing;

import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.core.GenericAgentInput;
import ai.qubere.agent.runtime.AgentRuntimeService;
import ai.qubere.document.agent.document.DocumentIntelligenceAgent;
import ai.qubere.document.agent.document.parser.DocumentParserException;
import ai.qubere.document.agent.document.parser.DocumentParserProvider;
import ai.qubere.document.agent.document.parser.ParserErrorCode;
import ai.qubere.document.agent.document.parser.ParserJobReference;
import ai.qubere.document.agent.document.parser.ParserJobStatus;
import ai.qubere.document.agent.document.parser.ParserResult;
import ai.qubere.document.agent.document.parser.ParserSourceInline;
import ai.qubere.document.agent.document.parser.ParserSubmission;
import ai.qubere.document.agent.document.parser.ParserSubmissionAck;
import ai.qubere.document.agent.document.parser.ProcessingProfile;
import ai.qubere.document.agent.document.parser.ProcessingReason;
import ai.qubere.document.agent.document.parser.QualityAssessment;
import ai.qubere.document.agent.document.parser.QualityGateEvaluator;
import ai.qubere.document.agent.document.parser.QualityGateInput;
import ai.qubere.document.agent.document.parser.QualityOutcome;
import ai.qubere.document.agent.document.parser.config.ParserProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives document-parsing runs through their state machine on a fixed tick, ported from
 * {@code documentProcessingWorker.ts}.
 * <p>
 * A plain Spring {@code @Scheduled} poller, not the framework's {@code AgentAsyncQueue} — that
 * queue is built around "queue one request, execute one complete governed agent run, dispatch a
 * callback," which is the wrong shape for a workflow that submits once and polls an external system
 * an unknown number of times with backoff. Forcing that fit would have meant either treating every
 * poll tick as its own agent execution (flooding the audit trail with "still polling" noise) or
 * having the run re-enqueue itself every tick regardless of whether a poll was actually due —
 * bypassing the intended backoff. See {@code MIGRATION.md} §9-§10 for the fuller reasoning.
 * <p>
 * What genuinely is reused from the framework: every parser call goes through
 * {@link DocumentParserProvider}, whose {@code IbmDoclingProvider} implementation already routes
 * through {@code AgentResilienceGateway}; every terminal outcome is recorded as a real governed
 * agent execution via {@link DocumentProcessingOutcomeAgent}, not an ad hoc audit log; and a
 * qualifying parse is promoted to the document's active version and immediately handed to
 * {@code document.intelligence} for extraction, closing the loop from raw bytes to extracted
 * trade metadata end-to-end.
 */
@Component
public class DocumentProcessingWorker {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingWorker.class);

    private final ProcessingRunService runService;
    private final DocumentParserProvider parserProvider;
    private final DocumentBytesSource bytesSource;
    private final AgentRuntimeService runtimeService;
    private final ParserProperties parserProperties;
    private final DocumentParseResultService parseResultService;

    public DocumentProcessingWorker(
            ProcessingRunService runService,
            DocumentParserProvider parserProvider,
            DocumentBytesSource bytesSource,
            AgentRuntimeService runtimeService,
            ParserProperties parserProperties,
            DocumentParseResultService parseResultService
    ) {
        this.runService = runService;
        this.parserProvider = parserProvider;
        this.bytesSource = bytesSource;
        this.runtimeService = runtimeService;
        this.parserProperties = parserProperties;
        this.parseResultService = parseResultService;
    }

    @Scheduled(fixedDelayString = "${document-agent.parser.processing-limits.poll-initial-delay-millis:5000}")
    public void tick() {
        int batchSize = parserProperties.getProcessingLimits().getBatchSize();
        Duration staleAfter = Duration.ofMillis(parserProperties.getProcessingLimits().getStaleAfterMillis());

        runService.reclaimStale(staleAfter);

        for (ProcessingRunEntity run : runService.findAwaitingSubmission(batchSize)) {
            submitOne(run);
        }
        for (ProcessingRunEntity run : runService.findAwaitingPoll(batchSize)) {
            pollOne(run);
        }
    }

    private void submitOne(ProcessingRunEntity run) {
        try {
            byte[] bytes = bytesSource.loadBytes(run.getDocumentId());
            ParserSubmission submission = new ParserSubmission(
                    run.getId(), run.getCorrelationId(), run.getProfile(),
                    new ParserSourceInline(bytesSource.filename(run.getDocumentId()), bytesSource.mimeType(run.getDocumentId()), bytes)
            );
            ParserSubmissionAck ack = parserProvider.submit(submission);
            runService.markSubmitted(run, ack.externalTaskId());
        } catch (DocumentParserException ex) {
            log.warn("Submission failed for processing run {}: {}", run.getId(), ex.getMessage());
            runService.markFailed(run, ex.code().name(), ex.getMessage(), ex.retryable());
        } catch (RuntimeException ex) {
            log.error("Unexpected error submitting processing run {}", run.getId(), ex);
            runService.markFailed(run, "UNEXPECTED_ERROR", ex.getMessage(), true);
        }
    }

    private void pollOne(ProcessingRunEntity run) {
        ParserJobReference reference = new ParserJobReference(run.getId(), run.getExternalTaskId(), run.getCorrelationId());
        try {
            ParserJobStatus status = parserProvider.getStatus(reference);
            switch (status.state()) {
                case POLLING -> {
                    if (runService.isPollExhausted(run)) {
                        runService.markFailed(run, ParserErrorCode.PARSER_TIMEOUT.name(),
                                "Polling exceeded the configured maximum attempts.", true);
                    } else {
                        runService.recordPolling(run);
                    }
                }
                case SUCCEEDED -> completeOne(run, reference);
                case FAILED -> {
                    DocumentParserException error = status.error();
                    String code = error != null ? error.code().name() : ParserErrorCode.PARSER_PROVIDER_ERROR.name();
                    boolean retryable = error == null || error.retryable();
                    runService.markFailed(run, code, status.providerStatus(), retryable);
                }
                default -> log.warn("Unexpected job status state {} for processing run {}", status.state(), run.getId());
            }
        } catch (DocumentParserException ex) {
            log.warn("Poll failed for processing run {}: {}", run.getId(), ex.getMessage());
            runService.markFailed(run, ex.code().name(), ex.getMessage(), ex.retryable());
        } catch (RuntimeException ex) {
            log.error("Unexpected error polling processing run {}", run.getId(), ex);
            runService.markFailed(run, "UNEXPECTED_ERROR", ex.getMessage(), true);
        }
    }

    private void completeOne(ProcessingRunEntity run, ParserJobReference reference) {
        try {
            ParserResult result = parserProvider.getResult(reference, run.getProfile());
            QualityAssessment assessment = QualityGateEvaluator.assessQuality(new QualityGateInput(
                    result.normalized(),
                    null,
                    run.getReason() == ProcessingReason.OCR_RETRY || run.getProfile() == ProcessingProfile.OCR_FALLBACK,
                    run.getProfile() == ProcessingProfile.FULL_PAGE_OCR
            ));

            if (QualityGateEvaluator.qualifiesAsActive(assessment.outcome())) {
                runService.markSucceeded(run);
                parseResultService.promoteToActive(run.getDocumentId(), run.getId(), result.normalized(), assessment.outcome());
                recordOutcome(run, assessment, true);
                runExtraction(run);
                return;
            }

            // RETRY_WITH_OCR does not qualify as active, but does queue a fresh run with the
            // suggested profile rather than looping this same run through OCR forever.
            if (assessment.outcome() == QualityOutcome.RETRY_WITH_OCR && assessment.suggestedRetryProfile() != null) {
                runService.enqueue(run.getDocumentId(), run.getShipmentId(), run.getContentSha256(), run.getTenantId(), run.getActorId(), run.getCorrelationId(),
                        assessment.suggestedRetryProfile(), ProcessingReason.OCR_RETRY);
            }
            runService.markNeedsReview(run);
            recordOutcome(run, assessment, false);
        } catch (DocumentParserException ex) {
            log.warn("Result retrieval failed for processing run {}: {}", run.getId(), ex.getMessage());
            runService.markFailed(run, ex.code().name(), ex.getMessage(), ex.retryable());
        }
    }

    private void runExtraction(ProcessingRunEntity run) {
        ai.qubere.document.agent.document.context.DocumentContext context =
                parseResultService.findActiveResult(run.getDocumentId())
                        .map(result -> ai.qubere.document.agent.document.context.QubereDocumentContextBuilder.build(
                                result, parserProperties.getContextBudget()))
                        .orElse(null);
        if (context == null) {
            // Should not happen immediately after promoteToActive() in the same method, but a
            // missing context must never crash the worker tick over one document.
            log.warn("No active parse result found for document {} immediately after promotion; skipping extraction.",
                    run.getDocumentId());
            return;
        }

        AgentExecutionContext agentContext = new AgentExecutionContext(
                UUID.randomUUID().toString(), run.getTenantId(), run.getActorId(), run.getCorrelationId(),
                Instant.now(), Map.of()
        );
        Map<String, Object> input = new java.util.LinkedHashMap<>();
        input.put("documentId", run.getDocumentId());
        input.put("processingRunId", run.getId());
        input.put("documentContext", context.content());
        if (run.getShipmentId() != null) {
            input.put("shipmentId", run.getShipmentId());
        }
        try {
            runtimeService.run(DocumentIntelligenceAgent.AGENT_ID, null, new GenericAgentInput(input), agentContext, null);
        } catch (RuntimeException ex) {
            // Extraction failing must never retract the processing run's own already-persisted
            // SUCCEEDED state or the promoted parse result -- the parse itself was fine; this is a
            // separate, later failure logged for operator attention rather than propagated.
            log.error("Extraction failed for document {} (processing run {})", run.getDocumentId(), run.getId(), ex);
        }
    }

    private void recordOutcome(ProcessingRunEntity run, QualityAssessment assessment, boolean accepted) {
        AgentExecutionContext context = new AgentExecutionContext(
                UUID.randomUUID().toString(), run.getTenantId(), run.getActorId(), run.getCorrelationId(),
                Instant.now(), Map.of()
        );
        Map<String, Object> input = Map.of(
                "documentId", run.getDocumentId(),
                "processingRunId", run.getId(),
                "qualityOutcome", assessment.outcome().name(),
                "accepted", accepted,
                "reasons", assessment.reasons()
        );
        try {
            runtimeService.run(DocumentProcessingOutcomeAgent.AGENT_ID, null, new GenericAgentInput(input), context, null);
        } catch (RuntimeException ex) {
            // Recording the outcome must never mask the processing run's own already-persisted
            // terminal state; a failure here is logged, not propagated, so a governance hiccup
            // cannot make an otherwise-successful parse look like it failed.
            log.error("Failed to record processing outcome for run {}", run.getId(), ex);
        }
    }
}

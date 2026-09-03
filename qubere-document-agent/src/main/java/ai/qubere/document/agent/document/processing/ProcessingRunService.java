package ai.qubere.document.agent.document.processing;

import ai.qubere.document.agent.document.parser.ProcessingProfile;
import ai.qubere.document.agent.document.parser.ProcessingReason;
import ai.qubere.document.agent.document.parser.ProcessingRunState;
import ai.qubere.document.agent.document.parser.config.ParserBackoff;
import ai.qubere.document.agent.document.parser.config.ParserProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Business logic over {@link ProcessingRunEntity}, ported from {@code processingRuns.ts}.
 * <p>
 * Every state-changing operation runs in its own {@link TransactionTemplate} with
 * {@code PROPAGATION_REQUIRES_NEW}, rather than {@code @Transactional} on these methods. This is
 * the same fix already applied once elsewhere in this framework
 * ({@code JpaDistributedWorkflowBudgetStore}): once a save conflicts with another writer's
 * already-applied change, the JPA persistence context is left unusable for the rest of that
 * transaction regardless of whether application code catches the exception — per the JPA spec, an
 * exception during flush requires the transaction to roll back. A plain {@code try/catch} around
 * {@code save()} inside a shared {@code @Transactional} method does not prevent that: the
 * transaction still comes back marked rollback-only, and the caller sees a confusing
 * {@code UnexpectedRollbackException} instead of a clean "someone else already advanced this run."
 * Isolating each attempt in its own {@code REQUIRES_NEW} transaction means only that attempt's
 * transaction rolls back; the rest of the calling method's work is unaffected.
 */
@Service
public class ProcessingRunService {

    private static final Logger log = LoggerFactory.getLogger(ProcessingRunService.class);

    private final ProcessingRunRepository repository;
    private final ParserProperties parserProperties;
    private final TransactionTemplate newTransaction;

    public ProcessingRunService(
            ProcessingRunRepository repository,
            ParserProperties parserProperties,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.parserProperties = parserProperties;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Creates a new run, or returns the existing one for the same (document, profile, reason)
     * idempotency key. A duplicate enqueue (e.g. a retried HTTP request) must not create a second
     * run racing the first.
     */
    public ProcessingRunEntity enqueue(
            String documentId, String shipmentId, String contentSha256, String tenantId, String actorId, String correlationId,
            ProcessingProfile profile, ProcessingReason reason
    ) {
        String idempotencyKey = idempotencyKeyFor(documentId, profile, reason);
        Optional<ProcessingRunEntity> existing = repository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }
        Instant now = Instant.now();
        ProcessingRunEntity run = new ProcessingRunEntity();
        run.setId(UUID.randomUUID().toString());
        run.setIdempotencyKey(idempotencyKey);
        run.setDocumentId(documentId);
        run.setShipmentId(shipmentId);
        run.setContentSha256(contentSha256);
        run.setTenantId(tenantId);
        run.setActorId(actorId);
        run.setCorrelationId(correlationId);
        run.setState(ProcessingRunState.QUEUED);
        run.setReason(reason);
        run.setProfile(profile);
        run.setAttemptCount(0);
        run.setPollAttemptCount(0);
        run.setHeartbeatAt(now);
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        try {
            return newTransaction.execute(status -> repository.saveAndFlush(run));
        } catch (DataIntegrityViolationException raceLostToIdempotencyKey) {
            // Two concurrent enqueues for the same key: the unique constraint on idempotencyKey is
            // the real guarantee here (this check-then-insert is a fast path, not the source of
            // truth), so the loser simply re-reads what the winner just inserted.
            return repository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        }
    }

    public List<ProcessingRunEntity> findAwaitingSubmission(int batchSize) {
        return repository.findAwaitingSubmission(ProcessingRunState.QUEUED, Instant.now(), Limit.of(batchSize));
    }

    public List<ProcessingRunEntity> findAwaitingPoll(int batchSize) {
        return repository.findAwaitingPoll(
                List.of(ProcessingRunState.SUBMITTED, ProcessingRunState.POLLING), Instant.now(), Limit.of(batchSize));
    }

    /**
     * Non-terminal runs whose heartbeat is older than {@code staleAfter}. A run with an
     * {@code externalTaskId} resumes polling immediately; one without (crashed before submission
     * completed) goes back to {@code QUEUED} to be resubmitted.
     */
    public List<ProcessingRunEntity> reclaimStale(Duration staleAfter) {
        Instant threshold = Instant.now().minus(staleAfter);
        List<ProcessingRunEntity> stale = repository.findStale(
                List.of(ProcessingRunState.QUEUED, ProcessingRunState.SUBMITTED, ProcessingRunState.POLLING),
                threshold, Limit.of(parserProperties.getProcessingLimits().getBatchSize()));
        for (ProcessingRunEntity run : stale) {
            if (run.getExternalTaskId() != null) {
                run.setNextPollAt(Instant.now());
                save(run);
            } else if (run.getState() != ProcessingRunState.QUEUED) {
                transition(run, ProcessingRunState.QUEUED);
            }
        }
        return stale;
    }

    public void markSubmitted(ProcessingRunEntity run, String externalTaskId) {
        run.setExternalTaskId(externalTaskId);
        run.setAttemptCount(run.getAttemptCount() + 1);
        run.setNextPollAt(Instant.now().plusMillis(parserProperties.getProcessingLimits().getPollInitialDelayMillis()));
        run.setHeartbeatAt(Instant.now());
        transition(run, ProcessingRunState.SUBMITTED);
    }

    public void recordPolling(ProcessingRunEntity run) {
        run.setPollAttemptCount(run.getPollAttemptCount() + 1);
        run.setNextPollAt(Instant.now().plusMillis(
                ParserBackoff.pollDelayMillis(run.getPollAttemptCount(), parserProperties.getProcessingLimits())));
        run.setHeartbeatAt(Instant.now());
        transition(run, ProcessingRunState.POLLING);
    }

    /** Polling has run past {@code maxPollAttempts} without resolving — a timeout, not a provider error. */
    public boolean isPollExhausted(ProcessingRunEntity run) {
        return run.getPollAttemptCount() >= parserProperties.getProcessingLimits().getMaxPollAttempts();
    }

    public void markSucceeded(ProcessingRunEntity run) {
        run.setNextPollAt(null);
        run.setNextRetryAt(null);
        transition(run, ProcessingRunState.SUCCEEDED);
    }

    public void markNeedsReview(ProcessingRunEntity run) {
        run.setNextPollAt(null);
        run.setNextRetryAt(null);
        transition(run, ProcessingRunState.NEEDS_REVIEW);
    }

    /**
     * @param retryable when {@code true} and attempts remain, schedules a backoff retry that
     *                  transitions this same run back to {@code QUEUED} rather than creating a new one
     */
    public void markFailed(ProcessingRunEntity run, String errorCode, String errorMessage, boolean retryable) {
        run.setNextPollAt(null);
        run.setErrorCode(errorCode);
        run.setErrorMessage(errorMessage);
        ParserProperties.ProcessingLimits limits = parserProperties.getProcessingLimits();
        boolean willRetry = retryable && run.getAttemptCount() < limits.getMaxAttempts();
        if (willRetry) {
            run.setNextRetryAt(Instant.now().plusMillis(
                    ParserBackoff.backoffDelayMillis(run.getAttemptCount() + 1, limits.getRetryBaseDelayMillis(), limits.getRetryMaxDelayMillis())));
        }
        ProcessingRunEntity afterFailed = transition(run, ProcessingRunState.FAILED);
        if (willRetry) {
            // A retryable failure re-queues the SAME run for its next attempt, not a new row --
            // preserving one idempotency key per (document, profile, reason) for its whole retry
            // history. Chained onto afterFailed, not the original run: each REQUIRES_NEW save
            // returns a freshly-merged instance carrying the post-save @Version, and continuing to
            // mutate the original (now stale) instance would make this second transition silently
            // lose an optimistic-lock race against itself.
            transition(afterFailed, ProcessingRunState.QUEUED);
        }
    }

    /**
     * Applies a state transition, rejecting one the state machine does not allow, and returns the
     * post-save entity. Guards against a bug elsewhere silently corrupting run history (e.g.
     * mutating a terminal run) rather than only ever being exercised by callers that already
     * happen to transition correctly.
     */
    private ProcessingRunEntity transition(ProcessingRunEntity run, ProcessingRunState target) {
        if (!run.getState().canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Illegal processing run transition for run " + run.getId() + ": " + run.getState() + " -> " + target);
        }
        run.setState(target);
        return save(run);
    }

    /**
     * @return the freshly-merged, post-save entity on success, or the same (now possibly stale)
     *         instance passed in in the rare event of a conflict. Callers that must chain a further
     *         transition after this one should continue from the returned instance, not the
     *         original argument — see {@link #markFailed} for why.
     */
    private ProcessingRunEntity save(ProcessingRunEntity run) {
        run.setUpdatedAt(Instant.now());
        try {
            ProcessingRunEntity saved = newTransaction.execute(status -> repository.saveAndFlush(run));
            return saved != null ? saved : run;
        } catch (OptimisticLockingFailureException conflict) {
            log.debug("Processing run {} was already advanced by another worker; skipping.", run.getId());
            return run;
        }
    }

    private static String idempotencyKeyFor(String documentId, ProcessingProfile profile, ProcessingReason reason) {
        String material = documentId + "|" + profile + "|" + reason;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format(Locale.ROOT, "%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required and must be available on every supported JVM", ex);
        }
    }
}

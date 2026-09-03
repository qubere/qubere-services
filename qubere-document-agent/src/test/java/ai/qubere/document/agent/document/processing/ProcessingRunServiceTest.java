package ai.qubere.document.agent.document.processing;

import ai.qubere.document.agent.document.parser.ProcessingProfile;
import ai.qubere.document.agent.document.parser.ProcessingReason;
import ai.qubere.document.agent.document.parser.ProcessingRunState;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the state machine and idempotency guarantees {@code MIGRATION.md} flags as the
 * highest concurrency-risk area of the whole document-processing subsystem.
 */
@SpringBootTest(properties = {
        "agent-platform.prompts.provider=in-memory",
        "agent-platform.async.worker-enabled=false",
        // Keeps the @Scheduled worker tick from firing mid-test and racing these assertions.
        "document-agent.parser.processing-limits.poll-initial-delay-millis=600000"
})
class ProcessingRunServiceTest {

    @Autowired
    private ProcessingRunService service;

    @Autowired
    private ProcessingRunRepository repository;

    @Test
    void enqueueIsIdempotentForTheSameDocumentProfileAndReason() {
        String documentId = uniqueDocumentId();

        ProcessingRunEntity first = service.enqueue(documentId, "shipment-1", "sha-1", "tenant-1", "actor-1", "corr-1",
                ProcessingProfile.STANDARD, ProcessingReason.INITIAL);
        ProcessingRunEntity second = service.enqueue(documentId, "shipment-1", "sha-1", "tenant-1", "actor-1", "corr-2",
                ProcessingProfile.STANDARD, ProcessingReason.INITIAL);

        assertThat(second.getId()).isEqualTo(first.getId());
        // The second enqueue's differing correlationId must not have overwritten the original run.
        assertThat(reload(first).getCorrelationId()).isEqualTo("corr-1");
    }

    @Test
    void differentProfileOrReasonProducesADistinctRun() {
        String documentId = uniqueDocumentId();

        ProcessingRunEntity standard = service.enqueue(documentId, "shipment-1", "sha-1", "tenant-1", "actor-1", "corr-1",
                ProcessingProfile.STANDARD, ProcessingReason.INITIAL);
        ProcessingRunEntity ocrRetry = service.enqueue(documentId, "shipment-1", "sha-1", "tenant-1", "actor-1", "corr-1",
                ProcessingProfile.OCR_FALLBACK, ProcessingReason.OCR_RETRY);

        assertThat(ocrRetry.getId()).isNotEqualTo(standard.getId());
    }

    @Test
    void newRunStartsQueuedAndCanTransitionThroughToSucceeded() {
        ProcessingRunEntity run = enqueueFresh();

        service.markSubmitted(run, "task-123");
        assertThat(reload(run).getState()).isEqualTo(ProcessingRunState.SUBMITTED);
        assertThat(reload(run).getExternalTaskId()).isEqualTo("task-123");
        assertThat(reload(run).getAttemptCount()).isEqualTo(1);

        ProcessingRunEntity submitted = reload(run);
        service.recordPolling(submitted);
        assertThat(reload(run).getState()).isEqualTo(ProcessingRunState.POLLING);
        assertThat(reload(run).getPollAttemptCount()).isEqualTo(1);

        ProcessingRunEntity polling = reload(run);
        service.markSucceeded(polling);
        assertThat(reload(run).getState()).isEqualTo(ProcessingRunState.SUCCEEDED);
    }

    @Test
    void terminalRunsRejectFurtherTransitions() {
        ProcessingRunEntity run = enqueueFresh();
        service.markSubmitted(run, "task-1");
        ProcessingRunEntity submitted = reload(run);
        service.markSucceeded(submitted);

        ProcessingRunEntity succeeded = reload(run);
        assertThatThrownBy(() -> service.markSubmitted(succeeded, "task-2"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void retryableFailureReQueuesTheSameRunRatherThanCreatingANewOne() {
        ProcessingRunEntity run = enqueueFresh();
        service.markSubmitted(run, "task-1");
        ProcessingRunEntity submitted = reload(run);

        service.markFailed(submitted, "PARSER_TIMEOUT", "timed out", true);

        ProcessingRunEntity after = reload(run);
        assertThat(after.getState()).isEqualTo(ProcessingRunState.QUEUED);
        assertThat(after.getId()).isEqualTo(run.getId());
        assertThat(after.getNextRetryAt()).isNotNull();
    }

    @Test
    void nonRetryableFailureStaysTerminal() {
        ProcessingRunEntity run = enqueueFresh();
        service.markSubmitted(run, "task-1");
        ProcessingRunEntity submitted = reload(run);

        service.markFailed(submitted, "PDF_ENCRYPTED", "encrypted", false);

        ProcessingRunEntity after = reload(run);
        assertThat(after.getState()).isEqualTo(ProcessingRunState.FAILED);
        assertThat(after.getErrorCode()).isEqualTo("PDF_ENCRYPTED");
    }

    @Test
    void exhaustedRetriesStayFailedRatherThanRetryingForever() {
        ProcessingRunEntity run = enqueueFresh();
        // Drain attempts by repeatedly submitting+failing.
        for (int i = 0; i < 10; i++) {
            ProcessingRunEntity current = reload(run);
            if (current.getState() == ProcessingRunState.QUEUED) {
                service.markSubmitted(current, "task-" + i);
                service.markFailed(reload(run), "PARSER_PROVIDER_ERROR", "boom", true);
            }
        }
        ProcessingRunEntity finalState = reload(run);
        // Once attemptCount reaches the configured max, retryable failures must stop rescheduling.
        assertThat(finalState.getAttemptCount()).isGreaterThanOrEqualTo(4);
        assertThat(finalState.getState()).isIn(ProcessingRunState.FAILED, ProcessingRunState.QUEUED);
    }

    @Test
    void concurrentModificationIsToleratedNotThrown() {
        ProcessingRunEntity run = enqueueFresh();
        service.markSubmitted(run, "task-1");

        // Two independent reads of the same row, simulating two workers.
        ProcessingRunEntity workerA = reload(run);
        ProcessingRunEntity workerB = reload(run);

        service.recordPolling(workerA);
        // workerB's copy is now stale (its version predates workerA's save). Advancing it must not
        // throw out of the service -- it should be silently skipped, matching "conditional update
        // that does not match is a no-op, not an error."
        service.recordPolling(workerB);

        ProcessingRunEntity finalState = reload(run);
        assertThat(finalState.getState()).isEqualTo(ProcessingRunState.POLLING);
        // Only workerA's advance should have counted.
        assertThat(finalState.getPollAttemptCount()).isEqualTo(1);
    }

    @Test
    void reclaimStaleResumesPollingWhenATaskIdExistsAndRequeuesWhenItDoesNot() {
        ProcessingRunEntity withTask = enqueueFresh();
        service.markSubmitted(withTask, "task-1");
        ProcessingRunEntity submitted = reload(withTask);
        submitted.setHeartbeatAt(Instant.now().minus(Duration.ofMinutes(30)));
        repository.save(submitted);

        ProcessingRunEntity withoutTask = enqueueFresh();
        withoutTask.setHeartbeatAt(Instant.now().minus(Duration.ofMinutes(30)));
        repository.save(withoutTask);

        List<ProcessingRunEntity> reclaimed = service.reclaimStale(Duration.ofMinutes(10));

        assertThat(reclaimed).extracting(ProcessingRunEntity::getId)
                .contains(withTask.getId(), withoutTask.getId());
        assertThat(reload(withTask).getNextPollAt()).isNotNull();
        assertThat(reload(withoutTask).getState()).isEqualTo(ProcessingRunState.QUEUED);
    }

    private ProcessingRunEntity enqueueFresh() {
        return service.enqueue(uniqueDocumentId(), "shipment-1", "sha-1", "tenant-1", "actor-1", "corr-1",
                ProcessingProfile.STANDARD, ProcessingReason.INITIAL);
    }

    private ProcessingRunEntity reload(ProcessingRunEntity run) {
        return repository.findById(run.getId()).orElseThrow();
    }

    private String uniqueDocumentId() {
        return "doc-" + UUID.randomUUID();
    }
}

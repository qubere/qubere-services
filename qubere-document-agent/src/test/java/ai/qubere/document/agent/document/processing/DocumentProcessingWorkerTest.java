package ai.qubere.document.agent.document.processing;

import ai.qubere.agent.runtime.AgentRuntimeService;
import ai.qubere.document.agent.document.parser.DocumentParserException;
import ai.qubere.document.agent.document.parser.DocumentParserProvider;
import ai.qubere.document.agent.document.parser.NormalizedParserResult;
import ai.qubere.document.agent.document.parser.ParserErrorCode;
import ai.qubere.document.agent.document.parser.ParserJobReference;
import ai.qubere.document.agent.document.parser.ParserJobStatus;
import ai.qubere.document.agent.document.parser.ParserMetadata;
import ai.qubere.document.agent.document.parser.ParserResult;
import ai.qubere.document.agent.document.parser.ParserSubmissionAck;
import ai.qubere.document.agent.document.parser.ParsedSection;
import ai.qubere.document.agent.document.parser.ProcessingProfile;
import ai.qubere.document.agent.document.parser.ProcessingReason;
import ai.qubere.document.agent.document.parser.ProcessingRunState;
import ai.qubere.document.agent.document.parser.SourceDelivery;
import ai.qubere.document.agent.document.parser.config.ParserProperties;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link DocumentProcessingWorker}'s tick loop end-to-end against a scripted
 * {@link DocumentParserProvider}, proving the submit -> poll -> complete sequence and its
 * governed-outcome recording actually work together, not just each piece in isolation.
 */
@SpringBootTest(properties = {
        "agent-platform.prompts.provider=in-memory",
        "agent-platform.async.worker-enabled=false",
        "document-agent.parser.processing-limits.poll-initial-delay-millis=600000",
        "document-agent.parser.processing-limits.batch-size=10"
})
class DocumentProcessingWorkerTest {

    @Autowired
    private ProcessingRunService runService;

    @Autowired
    private ProcessingRunRepository runRepository;

    @Autowired
    private AgentRuntimeService runtimeService;

    @Autowired
    private ParserProperties parserProperties;

    @Autowired
    private DocumentParseResultService parseResultService;

    @Autowired
    private ai.qubere.agent.persistence.AgentExecutionRecordRepository executionRecordRepository;

    @Test
    void fullTickCycleSubmitsPollsAndRecordsAGovernedOutcome() {
        ScriptedProvider provider = new ScriptedProvider(1);
        DocumentBytesSource bytesSource = fixedBytesSource();
        DocumentProcessingWorker worker = new DocumentProcessingWorker(runService, provider, bytesSource, runtimeService, parserProperties, parseResultService);

        ProcessingRunEntity run = runService.enqueue("doc-worker-1", "shipment-1", "sha-1", "tenant-1", "actor-1", "corr-1",
                ProcessingProfile.STANDARD, ProcessingReason.INITIAL);

        worker.tick(); // submits
        assertThat(reload(run).getState()).isEqualTo(ProcessingRunState.SUBMITTED);
        makeDueForPoll(run);

        worker.tick(); // first poll: still polling
        assertThat(reload(run).getState()).isEqualTo(ProcessingRunState.POLLING);
        makeDueForPoll(run);

        worker.tick(); // second poll: succeeded, fetches result, quality gate passes
        assertThat(reload(run).getState()).isEqualTo(ProcessingRunState.SUCCEEDED);

        assertThat(provider.submitCalls.get()).isEqualTo(1);
        assertThat(provider.statusCalls.get()).isEqualTo(2);
        assertThat(provider.resultCalls.get()).isEqualTo(1);

        // A qualifying parse must be promoted to the document's active version, and
        // document.intelligence must actually run against it -- not just get built and thrown
        // away. Both are real, separate wiring steps that were the last gap closed in MIGRATION.md
        // §13/§14; asserting only "state == SUCCEEDED" would not have caught either being missing.
        assertThat(parseResultService.findActiveResult(run.getDocumentId())).isPresent();
        assertThat(executionRecordRepository.findAll())
                .anyMatch(record -> "document.intelligence".equals(record.getAgentId()));
    }

    @Test
    void nonRetryableSubmissionFailureLeavesTheRunTerminallyFailed() {
        DocumentParserProvider provider = new DocumentParserProvider() {
            @Override
            public String providerId() {
                return "STUB";
            }

            @Override
            public boolean isMockProvider() {
                return true;
            }

            @Override
            public SourceDelivery sourceDelivery() {
                return SourceDelivery.INLINE;
            }

            @Override
            public String configurationHash(ProcessingProfile profile) {
                return "hash";
            }

            @Override
            public ParserSubmissionAck submit(ai.qubere.document.agent.document.parser.ParserSubmission submission) {
                throw new DocumentParserException(ParserErrorCode.UNSUPPORTED_FILE_TYPE, "bad file", false, null, null);
            }

            @Override
            public ParserJobStatus getStatus(ParserJobReference reference) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ParserResult getResult(ParserJobReference reference, ProcessingProfile profile) {
                throw new UnsupportedOperationException();
            }
        };

        DocumentProcessingWorker worker = new DocumentProcessingWorker(
                runService, provider, fixedBytesSource(), runtimeService, parserProperties, parseResultService);

        ProcessingRunEntity run = runService.enqueue("doc-worker-2", "shipment-1", "sha-2", "tenant-1", "actor-1", "corr-1",
                ProcessingProfile.STANDARD, ProcessingReason.INITIAL);

        worker.tick();

        ProcessingRunEntity after = reload(run);
        assertThat(after.getState()).isEqualTo(ProcessingRunState.FAILED);
        assertThat(after.getErrorCode()).isEqualTo("UNSUPPORTED_FILE_TYPE");
    }

    private void makeDueForPoll(ProcessingRunEntity run) {
        ProcessingRunEntity current = reload(run);
        current.setNextPollAt(Instant.now().minusSeconds(1));
        runRepository.save(current);
    }

    private ProcessingRunEntity reload(ProcessingRunEntity run) {
        return runRepository.findById(run.getId()).orElseThrow();
    }

    private DocumentBytesSource fixedBytesSource() {
        return new DocumentBytesSource() {
            @Override
            public byte[] loadBytes(String documentId) {
                return "sample document text".getBytes();
            }

            @Override
            public String filename(String documentId) {
                return "sample.txt";
            }

            @Override
            public String mimeType(String documentId) {
                return "text/plain";
            }
        };
    }

    /** Scripted provider: succeeds on submit, reports POLLING for {@code pollsBeforeSuccess} calls, then SUCCEEDED. */
    private static final class ScriptedProvider implements DocumentParserProvider {
        private final int pollsBeforeSuccess;
        private final AtomicInteger statusCallCount = new AtomicInteger();
        final AtomicInteger submitCalls = new AtomicInteger();
        final AtomicInteger statusCalls = new AtomicInteger();
        final AtomicInteger resultCalls = new AtomicInteger();
        private final Map<String, Boolean> tasks = new ConcurrentHashMap<>();

        private ScriptedProvider(int pollsBeforeSuccess) {
            this.pollsBeforeSuccess = pollsBeforeSuccess;
        }

        @Override
        public String providerId() {
            return "SCRIPTED";
        }

        @Override
        public boolean isMockProvider() {
            return true;
        }

        @Override
        public SourceDelivery sourceDelivery() {
            return SourceDelivery.INLINE;
        }

        @Override
        public String configurationHash(ProcessingProfile profile) {
            return "scripted-hash";
        }

        @Override
        public ParserSubmissionAck submit(ai.qubere.document.agent.document.parser.ParserSubmission submission) {
            submitCalls.incrementAndGet();
            String taskId = "task-" + submission.runId();
            tasks.put(taskId, true);
            return new ParserSubmissionAck(taskId, "pending", ProcessingRunState.SUBMITTED, List.of(), Instant.now());
        }

        @Override
        public ParserJobStatus getStatus(ParserJobReference reference) {
            statusCalls.incrementAndGet();
            int callNumber = statusCallCount.incrementAndGet();
            if (callNumber <= pollsBeforeSuccess) {
                return new ParserJobStatus(ProcessingRunState.POLLING, "started", null, Instant.now());
            }
            return new ParserJobStatus(ProcessingRunState.SUCCEEDED, "success", null, Instant.now());
        }

        @Override
        public ParserResult getResult(ParserJobReference reference, ProcessingProfile profile) {
            resultCalls.incrementAndGet();
            NormalizedParserResult normalized = new NormalizedParserResult(
                    NormalizedParserResult.CONTRACT_VERSION, profile,
                    new ParserMetadata("SCRIPTED", null, null, null, null, 1, null, null, null, null, null),
                    "sample document text",
                    List.of(new ParsedSection("sec_0000_abc", List.of(), "sample document text", List.of())),
                    List.of(), List.of(), List.of(80)
            );
            return new ParserResult(Map.of("note", "scripted"), normalized);
        }
    }
}

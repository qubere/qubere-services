package ai.qubere.document.agent.document.parser;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FallbackDoclingProviderTest {

    @Test
    void failsOverToBackupOnlyWhenPrimaryFailureIsNonRetryable() {
        StubProvider primary = StubProvider.thatThrows(
                new DocumentParserException(ParserErrorCode.PARSER_NOT_CONFIGURED, "not configured", false, null, null));
        StubProvider backup = StubProvider.thatSucceeds();

        FallbackDoclingProvider fallback = new FallbackDoclingProvider(primary, backup);
        ParserSubmissionAck ack = fallback.submit(submission());

        assertThat(ack.externalTaskId()).isEqualTo("backup-task");
        assertThat(primary.submitCalls).isEqualTo(1);
        assertThat(backup.submitCalls).isEqualTo(1);
    }

    @Test
    void doesNotFailOverOnARetryablePrimaryFailure() {
        StubProvider primary = StubProvider.thatThrows(
                new DocumentParserException(ParserErrorCode.PARSER_TIMEOUT, "timed out", true, null, null));
        StubProvider backup = StubProvider.thatSucceeds();

        FallbackDoclingProvider fallback = new FallbackDoclingProvider(primary, backup);

        assertThatThrownBy(() -> fallback.submit(submission()))
                .isInstanceOfSatisfying(DocumentParserException.class, ex ->
                        assertThat(ex.code()).isEqualTo(ParserErrorCode.PARSER_TIMEOUT));
        assertThat(backup.submitCalls).isEqualTo(0);
    }

    @Test
    void isMockProviderOnlyWhenBothDelegatesAreMock() {
        StubProvider realPrimary = StubProvider.thatSucceeds();
        realPrimary.mock = false;
        StubProvider mockBackup = StubProvider.thatSucceeds();
        mockBackup.mock = true;

        assertThat(new FallbackDoclingProvider(realPrimary, mockBackup).isMockProvider()).isFalse();
        assertThat(new FallbackDoclingProvider(mockBackup, mockBackup).isMockProvider()).isTrue();
    }

    @Test
    void requiresBothProviders() {
        assertThatThrownBy(() -> new FallbackDoclingProvider(null, StubProvider.thatSucceeds()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ParserSubmission submission() {
        return new ParserSubmission("run-1", "corr-1", ProcessingProfile.STANDARD,
                new ParserSourceInline("a.txt", "text/plain", new byte[]{1}));
    }

    private static final class StubProvider implements DocumentParserProvider {
        private final DocumentParserException toThrow;
        private final String taskId;
        private int submitCalls = 0;
        private boolean mock = false;

        private StubProvider(DocumentParserException toThrow, String taskId) {
            this.toThrow = toThrow;
            this.taskId = taskId;
        }

        static StubProvider thatThrows(DocumentParserException ex) {
            return new StubProvider(ex, null);
        }

        static StubProvider thatSucceeds() {
            return new StubProvider(null, "backup-task");
        }

        @Override
        public String providerId() {
            return "STUB";
        }

        @Override
        public boolean isMockProvider() {
            return mock;
        }

        @Override
        public SourceDelivery sourceDelivery() {
            return SourceDelivery.INLINE;
        }

        @Override
        public String configurationHash(ProcessingProfile profile) {
            return "stub-hash";
        }

        @Override
        public ParserSubmissionAck submit(ParserSubmission submission) {
            submitCalls++;
            if (toThrow != null) {
                throw toThrow;
            }
            return new ParserSubmissionAck(taskId, "pending", ProcessingRunState.SUBMITTED, List.of(), Instant.now());
        }

        @Override
        public ParserJobStatus getStatus(ParserJobReference reference) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ParserResult getResult(ParserJobReference reference, ProcessingProfile profile) {
            throw new UnsupportedOperationException();
        }
    }
}

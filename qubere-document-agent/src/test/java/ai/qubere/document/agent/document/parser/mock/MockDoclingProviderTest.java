package ai.qubere.document.agent.document.parser.mock;

import ai.qubere.document.agent.document.parser.DocumentParserException;
import ai.qubere.document.agent.document.parser.NormalizedParserResult;
import ai.qubere.document.agent.document.parser.ParserErrorCode;
import ai.qubere.document.agent.document.parser.ParserJobReference;
import ai.qubere.document.agent.document.parser.ParserJobStatus;
import ai.qubere.document.agent.document.parser.ParserSourceInline;
import ai.qubere.document.agent.document.parser.ParserSubmission;
import ai.qubere.document.agent.document.parser.ParserSubmissionAck;
import ai.qubere.document.agent.document.parser.ProcessingProfile;
import ai.qubere.document.agent.document.parser.ProcessingRunState;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockDoclingProviderTest {

    @Test
    void refusesToConstructInProduction() {
        assertThatThrownBy(() -> new MockDoclingProvider(true))
                .isInstanceOfSatisfying(DocumentParserException.class, ex ->
                        assertThat(ex.code()).isEqualTo(ParserErrorCode.PARSER_NOT_CONFIGURED));
    }

    @Test
    void fullLifecycleProducesATextSectionFromRecoverableBytes() {
        MockDoclingProvider provider = new MockDoclingProvider(false, 1);
        ParserSubmission submission = submissionFor("hello.txt", "Hello world\nSecond line".getBytes(StandardCharsets.UTF_8));

        ParserSubmissionAck ack = provider.submit(submission);
        assertThat(ack.state()).isEqualTo(ProcessingRunState.SUBMITTED);

        ParserJobReference reference = new ParserJobReference(submission.runId(), ack.externalTaskId(), submission.correlationId());

        // First poll: still polling per pollsBeforeSuccess=1.
        ParserJobStatus first = provider.getStatus(reference);
        assertThat(first.state()).isEqualTo(ProcessingRunState.POLLING);

        // Second poll: succeeded.
        ParserJobStatus second = provider.getStatus(reference);
        assertThat(second.state()).isEqualTo(ProcessingRunState.SUCCEEDED);

        NormalizedParserResult result = provider.getResult(reference, ProcessingProfile.STANDARD).normalized();
        assertThat(result.markdown()).isEqualTo("Hello world\nSecond line");
        assertThat(result.sections()).hasSize(1);
        assertThat(result.warnings()).anySatisfy(w -> assertThat(w.code()).isEqualTo("MOCK_PROVIDER"));
        assertThat(result.metadata().provider()).isEqualTo(MockDoclingProvider.PROVIDER_ID);
    }

    @Test
    void pdfBytesYieldEmptyTextRatherThanGarbledDecoding() {
        MockDoclingProvider provider = new MockDoclingProvider(false, 0);
        byte[] pdfLike = "%PDF-1.4 fake content".getBytes(StandardCharsets.UTF_8);
        ParserSubmission submission = submissionFor("scan.pdf", pdfLike);

        ParserSubmissionAck ack = provider.submit(submission);
        ParserJobReference reference = new ParserJobReference(submission.runId(), ack.externalTaskId(), submission.correlationId());
        provider.getStatus(reference);

        NormalizedParserResult result = provider.getResult(reference, ProcessingProfile.STANDARD).normalized();
        assertThat(result.markdown()).isNull();
        assertThat(result.sections()).isEmpty();
        assertThat(result.pageTextLengths()).isEmpty();
    }

    @Test
    void rejectsEmptySubmissions() {
        MockDoclingProvider provider = new MockDoclingProvider(false);
        ParserSubmission submission = submissionFor("empty.txt", new byte[0]);

        assertThatThrownBy(() -> provider.submit(submission))
                .isInstanceOfSatisfying(DocumentParserException.class, ex ->
                        assertThat(ex.code()).isEqualTo(ParserErrorCode.EMPTY_FILE));
    }

    @Test
    void unknownTaskReferenceFailsRatherThanFabricatingAResult() {
        MockDoclingProvider provider = new MockDoclingProvider(false);
        ParserJobReference unknown = new ParserJobReference("run-1", "mock_does_not_exist", "corr-1");

        ParserJobStatus status = provider.getStatus(unknown);
        assertThat(status.state()).isEqualTo(ProcessingRunState.FAILED);
        assertThat(status.error()).isNotNull();

        assertThatThrownBy(() -> provider.getResult(unknown, ProcessingProfile.STANDARD))
                .isInstanceOf(DocumentParserException.class);
    }

    @Test
    void identicalRunIdAndByteLengthProduceTheSameTaskIdDeterministically() {
        MockDoclingProvider provider = new MockDoclingProvider(false);
        byte[] bytes = "same content".getBytes(StandardCharsets.UTF_8);

        ParserSubmissionAck first = provider.submit(new ParserSubmission(
                "fixed-run-id", "corr-1", ProcessingProfile.STANDARD,
                new ParserSourceInline("a.txt", "text/plain", bytes)));
        ParserSubmissionAck second = provider.submit(new ParserSubmission(
                "fixed-run-id", "corr-2", ProcessingProfile.STANDARD,
                new ParserSourceInline("b.txt", "text/plain", bytes)));

        assertThat(first.externalTaskId()).isEqualTo(second.externalTaskId());
    }

    private ParserSubmission submissionFor(String filename, byte[] bytes) {
        return new ParserSubmission(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), ProcessingProfile.STANDARD,
                new ParserSourceInline(filename, "text/plain", bytes)
        );
    }
}

package ai.qubere.document.agent.document.review;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "agent-platform.prompts.provider=in-memory",
        "agent-platform.async.worker-enabled=false",
        "document-agent.parser.processing-limits.poll-initial-delay-millis=600000"
})
class ExtractionReviewServiceTest {

    @Autowired
    private ExtractionReviewService reviewService;

    @Test
    void recordsMachineReadingsAndSkipsBlankValues() {
        String documentId = uniqueDocumentId();

        reviewService.recordMachineReadings(documentId, List.of(
                new ExtractedFieldReading("invoiceNumber", "INV-1001", 90),
                new ExtractedFieldReading("poNumber", null, 90),
                new ExtractedFieldReading("currency", "  ", 90)
        ));

        List<ReviewField> fields = reviewService.reviewFieldsFor(documentId);

        assertThat(fields).extracting(ReviewField::fieldName).containsExactly("invoiceNumber");
        assertThat(fields.get(0).currentValue()).isEqualTo("INV-1001");
        assertThat(fields.get(0).confidence()).isEqualTo(90);
    }

    @Test
    void submittingACorrectionCreatesANewRowRatherThanOverwritingTheMachineReading() {
        String documentId = uniqueDocumentId();
        reviewService.recordMachineReadings(documentId, List.of(new ExtractedFieldReading("invoiceNumber", "INV-1001", 40)));

        reviewService.submitCorrection(documentId, "invoiceNumber", "INV-CORRECTED");

        List<ReviewField> fields = reviewService.reviewFieldsFor(documentId);
        ReviewField field = fields.get(0);
        assertThat(field.currentValue()).isEqualTo("INV-CORRECTED");
        assertThat(field.originalValue()).isEqualTo("INV-1001");
        assertThat(field.corrected()).isTrue();
        assertThat(field.needsReview()).isFalse();
        assertThat(field.history()).hasSize(2);
    }

    @Test
    void submittingACorrectionForAnUnknownFieldFailsRatherThanCreatingOne() {
        String documentId = uniqueDocumentId();

        assertThatThrownBy(() -> reviewService.submitCorrection(documentId, "doesNotExist", "value"))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void submittingAnUnchangedValueIsRejected() {
        String documentId = uniqueDocumentId();
        reviewService.recordMachineReadings(documentId, List.of(new ExtractedFieldReading("invoiceNumber", "INV-1001", 90)));

        assertThatThrownBy(() -> reviewService.submitCorrection(documentId, "invoiceNumber", "INV-1001"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String uniqueDocumentId() {
        return "review-doc-" + UUID.randomUUID();
    }
}

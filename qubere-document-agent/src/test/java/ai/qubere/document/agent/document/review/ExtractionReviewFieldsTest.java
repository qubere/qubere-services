package ai.qubere.document.agent.document.review;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ports the test coverage implied by {@code extractionReview.ts}'s own "pure: exercised directly by
 * tests" design note.
 */
class ExtractionReviewFieldsTest {

    private final Instant t0 = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void aFieldWithOnlyOneHighConfidenceMachineReadNeedsNoReview() {
        List<RawExtractionField> rows = List.of(
                machine("f1", "invoiceNumber", "INV-1", 95, 1, t0)
        );

        List<ReviewField> fields = ExtractionReviewFields.buildReviewFields(rows);

        assertThat(fields).hasSize(1);
        ReviewField field = fields.get(0);
        assertThat(field.currentValue()).isEqualTo("INV-1");
        assertThat(field.originalValue()).isEqualTo("INV-1");
        assertThat(field.confidence()).isEqualTo(95);
        assertThat(field.corrected()).isFalse();
        assertThat(field.needsReview()).isFalse();
    }

    @Test
    void aLowConfidenceUncorrectedReadNeedsReview() {
        List<RawExtractionField> rows = List.of(
                machine("f1", "invoiceNumber", "INV-1", 40, 1, t0)
        );

        List<ReviewField> fields = ExtractionReviewFields.buildReviewFields(rows);

        assertThat(fields.get(0).needsReview()).isTrue();
    }

    @Test
    void aNullConfidenceReadNeedsReview() {
        List<RawExtractionField> rows = List.of(
                machine("f1", "invoiceNumber", "INV-1", null, 1, t0)
        );

        assertThat(ExtractionReviewFields.buildReviewFields(rows).get(0).needsReview()).isTrue();
    }

    @Test
    void aHumanCorrectionWinsOutrightRegardlessOfMachineConfidence() {
        List<RawExtractionField> rows = List.of(
                machine("f1", "invoiceNumber", "INV-1", 95, 1, t0),
                human("f2", "invoiceNumber", "INV-CORRECTED", t0.plusSeconds(60))
        );

        List<ReviewField> fields = ExtractionReviewFields.buildReviewFields(rows);

        ReviewField field = fields.get(0);
        assertThat(field.currentValue()).isEqualTo("INV-CORRECTED");
        assertThat(field.originalValue()).isEqualTo("INV-1");
        assertThat(field.corrected()).isTrue();
        assertThat(field.needsReview()).isFalse();
    }

    @Test
    void bestMachineReadIsHighestConfidenceNotNewest() {
        List<RawExtractionField> rows = List.of(
                machine("f1", "invoiceNumber", "INV-OLD-HIGH", 90, 1, t0),
                machine("f2", "invoiceNumber", "INV-NEW-LOW", 50, 1, t0.plusSeconds(60))
        );

        List<ReviewField> fields = ExtractionReviewFields.buildReviewFields(rows);

        assertThat(fields.get(0).confidence()).isEqualTo(90);
        // Current value with no correction is the newest reading overall, not necessarily the
        // best-confidence one -- history[0] (newest) is "current" absent a correction.
        assertThat(fields.get(0).currentValue()).isEqualTo("INV-NEW-LOW");
    }

    @Test
    void originalValueIsTheOldestMachineReadNotTheBest() {
        List<RawExtractionField> rows = List.of(
                machine("f1", "invoiceNumber", "INV-FIRST", 50, 1, t0),
                machine("f2", "invoiceNumber", "INV-BETTER", 95, 1, t0.plusSeconds(60))
        );

        assertThat(ExtractionReviewFields.buildReviewFields(rows).get(0).originalValue()).isEqualTo("INV-FIRST");
    }

    @Test
    void historyIsNewestFirst() {
        List<RawExtractionField> rows = List.of(
                machine("f1", "invoiceNumber", "INV-1", 50, 1, t0),
                machine("f2", "invoiceNumber", "INV-2", 60, 1, t0.plusSeconds(60)),
                machine("f3", "invoiceNumber", "INV-3", 70, 1, t0.plusSeconds(120))
        );

        List<FieldRevision> history = ExtractionReviewFields.buildReviewFields(rows).get(0).history();

        assertThat(history).extracting(FieldRevision::value).containsExactly("INV-3", "INV-2", "INV-1");
    }

    @Test
    void fieldsAreSortedByName() {
        List<RawExtractionField> rows = List.of(
                machine("f1", "zField", "z", 90, 1, t0),
                machine("f2", "aField", "a", 90, 1, t0)
        );

        assertThat(ExtractionReviewFields.buildReviewFields(rows))
                .extracting(ReviewField::fieldName)
                .containsExactly("aField", "zField");
    }

    @Test
    void parsesAValidBoundingBox() {
        Map<String, Object> raw = Map.of("x", 1.0, "y", 2.0, "width", 3.0, "height", 4.0);

        BoundingBox box = ExtractionReviewFields.parseBoundingBox(raw);

        assertThat(box).isEqualTo(new BoundingBox(1.0, 2.0, 3.0, 4.0));
    }

    @Test
    void rejectsANonPositiveWidthOrHeight() {
        assertThat(ExtractionReviewFields.parseBoundingBox(Map.of("x", 1.0, "y", 2.0, "width", 0.0, "height", 4.0))).isNull();
        assertThat(ExtractionReviewFields.parseBoundingBox(Map.of("x", 1.0, "y", 2.0, "width", 3.0, "height", -1.0))).isNull();
    }

    @Test
    void rejectsAnythingThatIsNotAMap() {
        assertThat(ExtractionReviewFields.parseBoundingBox(null)).isNull();
        assertThat(ExtractionReviewFields.parseBoundingBox("not a box")).isNull();
        assertThat(ExtractionReviewFields.parseBoundingBox(List.of(1, 2, 3, 4))).isNull();
    }

    @Test
    void pagesWithFieldsReturnsOnlyDistinctSortedPageNumbers() {
        List<ReviewField> fields = List.of(
                reviewField("a", 3), reviewField("b", 1), reviewField("c", 1), reviewField("d", null)
        );

        assertThat(ExtractionReviewFields.pagesWithFields(fields)).containsExactly(1, 3);
    }

    @Test
    void nextReviewIndexWrapsAroundAndSkipsFieldsThatDoNotNeedReview() {
        List<ReviewField> fields = List.of(
                reviewFieldNeedingReview("a", false),
                reviewFieldNeedingReview("b", true),
                reviewFieldNeedingReview("c", false)
        );

        assertThat(ExtractionReviewFields.nextReviewIndex(fields, 0)).isEqualTo(1);
        assertThat(ExtractionReviewFields.nextReviewIndex(fields, 1)).isEqualTo(1);
        assertThat(ExtractionReviewFields.nextReviewIndex(fields, -1)).isEqualTo(1);
    }

    @Test
    void nextReviewIndexReturnsMinusOneWhenNothingNeedsReview() {
        List<ReviewField> fields = List.of(reviewFieldNeedingReview("a", false));

        assertThat(ExtractionReviewFields.nextReviewIndex(fields, 0)).isEqualTo(-1);
    }

    @Test
    void nextReviewIndexReturnsMinusOneForAnEmptyList() {
        assertThat(ExtractionReviewFields.nextReviewIndex(List.of(), 0)).isEqualTo(-1);
    }

    @Test
    void validateCorrectionRejectsBlankOrUnchangedOrOverlongValues() {
        assertThat(ExtractionReviewFields.validateCorrection("", "current").ok()).isFalse();
        assertThat(ExtractionReviewFields.validateCorrection("  ", "current").ok()).isFalse();
        assertThat(ExtractionReviewFields.validateCorrection("current", "current").ok()).isFalse();
        assertThat(ExtractionReviewFields.validateCorrection(null, "current").ok()).isFalse();
        assertThat(ExtractionReviewFields.validateCorrection("x".repeat(2001), "current").ok()).isFalse();
    }

    @Test
    void validateCorrectionAcceptsAndTrimsAGenuinelyDifferentValue() {
        CorrectionValidation result = ExtractionReviewFields.validateCorrection("  new value  ", "current");

        assertThat(result.ok()).isTrue();
        assertThat(result.value()).isEqualTo("new value");
    }

    private RawExtractionField machine(String id, String fieldName, String value, Integer confidence, Integer page, Instant createdAt) {
        return new RawExtractionField(id, fieldName, value, confidence, page, null, ExtractionFieldSource.MACHINE, createdAt);
    }

    private RawExtractionField human(String id, String fieldName, String value, Instant createdAt) {
        return new RawExtractionField(id, fieldName, value, null, null, null, ExtractionFieldSource.HUMAN_CORRECTION, createdAt);
    }

    private ReviewField reviewField(String name, Integer page) {
        return new ReviewField(name, "v", "v", 90, page, null, false, false, List.of());
    }

    private ReviewField reviewFieldNeedingReview(String name, boolean needsReview) {
        return new ReviewField(name, "v", "v", 90, 1, null, false, needsReview, List.of());
    }
}

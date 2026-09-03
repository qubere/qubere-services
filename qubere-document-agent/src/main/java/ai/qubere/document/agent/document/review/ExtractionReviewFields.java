package ai.qubere.document.agent.document.review;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure extraction-review logic, ported from {@code extractionReview.ts}. No database, no framework
 * types — everything here is exercised directly by tests, same as the source's own "pure: no
 * database, no React" design note. {@link ai.qubere.document.agent.document.review.ExtractionReviewService}
 * is the one caller that connects this to persistence.
 */
public final class ExtractionReviewFields {

    /**
     * Matches {@code extractedCurrency.ts}-adjacent review threshold from the source
     * ({@code REVIEW_REQUIRED_BELOW = 80}) — deliberately distinct from
     * {@code DocumentIntakeAgent}/{@code DocumentIntelligenceAgent}'s own 70% thresholds; see
     * {@code MIGRATION.md} §3.4's explicit warning against conflating the two.
     */
    public static final int REVIEW_REQUIRED_BELOW = 80;

    public static final int CORRECTION_MAX_LENGTH = 2000;

    private ExtractionReviewFields() {
    }

    /** Accepts whatever a JSON column deserialized to, which may be anything. */
    public static BoundingBox parseBoundingBox(Object raw) {
        if (!(raw instanceof Map<?, ?> candidate)) {
            return null;
        }
        Double x = asFiniteDouble(candidate.get("x"));
        Double y = asFiniteDouble(candidate.get("y"));
        Double width = asFiniteDouble(candidate.get("width"));
        Double height = asFiniteDouble(candidate.get("height"));
        if (x == null || y == null || width == null || height == null) {
            return null;
        }
        if (width <= 0 || height <= 0) {
            return null;
        }
        return new BoundingBox(x, y, width, height);
    }

    private static Double asFiniteDouble(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        double d = number.doubleValue();
        return Double.isFinite(d) ? d : null;
    }

    /**
     * Groups raw rows into one entry per field name. Precedence for the current reading is
     * explicit: the newest human correction wins outright. Confidence only breaks ties between
     * machine readings, because a reviewed value is not competing on model score.
     */
    public static List<ReviewField> buildReviewFields(List<RawExtractionField> rows) {
        Map<String, List<RawExtractionField>> byName = new LinkedHashMap<>();
        for (RawExtractionField row : rows) {
            byName.computeIfAbsent(row.fieldName(), key -> new ArrayList<>()).add(row);
        }

        List<ReviewField> fields = new ArrayList<>();
        for (Map.Entry<String, List<RawExtractionField>> entry : byName.entrySet()) {
            List<RawExtractionField> group = entry.getValue();

            List<FieldRevision> history = group.stream()
                    .map(row -> new FieldRevision(
                            row.id(), row.value(), row.confidence(), row.source(), row.createdAt(),
                            row.source() == ExtractionFieldSource.HUMAN_CORRECTION))
                    .sorted(Comparator.comparing(FieldRevision::createdAt).reversed())
                    .toList();

            List<FieldRevision> corrections = history.stream().filter(FieldRevision::isCorrection).toList();
            List<RawExtractionField> machineReads = group.stream()
                    .filter(row -> row.source() != ExtractionFieldSource.HUMAN_CORRECTION)
                    .toList();

            // Best machine read: highest confidence, and among equals the newest.
            RawExtractionField bestMachineRead = machineReads.stream()
                    .max(Comparator
                            .comparing((RawExtractionField row) -> row.confidence() == null ? -1 : row.confidence())
                            .thenComparing(RawExtractionField::createdAt))
                    .orElse(null);

            // Oldest machine read is what the extractor first said.
            RawExtractionField firstMachineRead = machineReads.stream()
                    .min(Comparator.comparing(RawExtractionField::createdAt))
                    .orElse(null);

            FieldRevision current = !corrections.isEmpty() ? corrections.get(0)
                    : (history.isEmpty() ? null : history.get(0));
            boolean corrected = !corrections.isEmpty();

            // Provenance follows the machine read; a correction inherits where the value was
            // found rather than claiming a location a reviewer never pointed at.
            RawExtractionField provenance = bestMachineRead != null ? bestMachineRead
                    : (group.isEmpty() ? null : group.get(0));

            boolean needsReview = !corrected && (bestMachineRead == null
                    || bestMachineRead.confidence() == null
                    || bestMachineRead.confidence() < REVIEW_REQUIRED_BELOW);

            fields.add(new ReviewField(
                    entry.getKey(),
                    current == null ? null : current.value(),
                    firstMachineRead == null ? null : firstMachineRead.value(),
                    bestMachineRead == null ? null : bestMachineRead.confidence(),
                    provenance == null ? null : provenance.pageNumber(),
                    parseBoundingBox(provenance == null ? null : provenance.bbox()),
                    corrected,
                    needsReview,
                    history
            ));
        }

        return fields.stream().sorted(Comparator.comparing(ReviewField::fieldName)).toList();
    }

    /** Pages that actually carry a located field, so navigation cannot offer empty pages. */
    public static List<Integer> pagesWithFields(List<ReviewField> fields) {
        return fields.stream()
                .map(ReviewField::pageNumber)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Index of the next field needing review, wrapping around. Returns -1 when none do, so the
     * caller can say "nothing left to review" rather than moving focus.
     * <p>
     * The source computes {@code (from + step + fields.length * 2) % fields.length} to route
     * around JavaScript's {@code %} returning a negative result for a negative left operand;
     * {@link Math#floorMod} is Java's direct equivalent, so the offset trick is unnecessary here.
     */
    public static int nextReviewIndex(List<ReviewField> fields, int from) {
        if (fields.isEmpty()) {
            return -1;
        }
        int size = fields.size();
        for (int step = 1; step <= size; step++) {
            int index = Math.floorMod(from + step, size);
            if (fields.get(index).needsReview()) {
                return index;
            }
        }
        return -1;
    }

    /** A correction that matches the current reading is not a correction. */
    public static CorrectionValidation validateCorrection(String raw, String currentValue) {
        if (raw == null) {
            return CorrectionValidation.rejected("A corrected value must be text.");
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return CorrectionValidation.rejected("A corrected value cannot be empty.");
        }
        if (value.length() > CORRECTION_MAX_LENGTH) {
            return CorrectionValidation.rejected(
                    "A corrected value cannot exceed " + CORRECTION_MAX_LENGTH + " characters.");
        }
        if (value.equals(currentValue)) {
            return CorrectionValidation.rejected("The value is unchanged.");
        }
        return CorrectionValidation.ok(value);
    }
}

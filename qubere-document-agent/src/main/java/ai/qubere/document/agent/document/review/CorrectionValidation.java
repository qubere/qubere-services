package ai.qubere.document.agent.document.review;

/** Ported from {@code extractionReview.ts}'s {@code CorrectionValidation}. */
public record CorrectionValidation(boolean ok, String reason, String value) {

    public static CorrectionValidation ok(String value) {
        return new CorrectionValidation(true, null, value);
    }

    public static CorrectionValidation rejected(String reason) {
        return new CorrectionValidation(false, reason, null);
    }
}

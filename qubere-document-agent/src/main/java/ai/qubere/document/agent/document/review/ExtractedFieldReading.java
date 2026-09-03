package ai.qubere.document.agent.document.review;

/**
 * A single machine-read field/value pair handed to {@link ExtractionReviewService} by an extraction
 * agent. Deliberately narrower than {@code RawExtractionField}: {@code pageNumber}/{@code bbox}
 * provenance is not carried here because {@code DocumentIntelligenceAgent} does not produce it yet
 * (see that agent's javadoc on the deliberately-deferred visual/layout analysis capability) — both
 * fields simply come through as {@code null} on the resulting {@link ExtractionFieldEntity} row
 * until a vision-capable extraction path exists to populate them.
 */
public record ExtractedFieldReading(String fieldName, String value, Integer confidence) {
}

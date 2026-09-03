package ai.qubere.document.agent.document.parser;

/**
 * Facts about the parse. Every field is boxed/nullable because "the provider did not report this"
 * is a real and common answer, and inventing a value here would fabricate parser confidence or OCR
 * usage. Ported from {@code parser/contracts.ts}'s {@code parserMetadataSchema}.
 *
 * @param parserName            parser name as the provider reports it (e.g. "docling"); {@code null} if unreported
 * @param parserVersion         parser runtime version as reported; {@code null} if the hosted API does not expose it
 * @param ocrUsed               {@code true}/{@code false} only when the provider actually says; {@code null} means unknown
 * @param parserConfidence      parser-emitted confidence, ONLY when genuinely emitted; never synthesized
 */
public record ParserMetadata(
        String provider,
        String parserName,
        String parserVersion,
        String ocrEngine,
        String ocrEngineVersion,
        Integer pageCount,
        Boolean ocrUsed,
        Boolean fullPageOcrUsed,
        Long processingDurationMs,
        Double parserConfidence,
        Double ocrConfidence
) {
}

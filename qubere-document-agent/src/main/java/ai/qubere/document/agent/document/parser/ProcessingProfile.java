package ai.qubere.document.agent.document.parser;

/**
 * Named Qubere document-processing profiles. These are Qubere concepts, not provider concepts:
 * each {@link DocumentParserProvider} maps a profile onto whatever options it actually supports
 * and reports back which options it could not honor (see {@link ParserSubmissionAck#unsupportedOptions()}).
 */
public enum ProcessingProfile {
    /** Born-digital and common mixed documents; provider's own OCR heuristics handle image-only pages. */
    STANDARD,
    /** Same provider options as {@link #STANDARD}; used only after the quality gate found insufficient text. */
    OCR_FALLBACK,
    /** Forces OCR even on pages that already claim a text layer. Never applied by default. */
    FULL_PAGE_OCR
}

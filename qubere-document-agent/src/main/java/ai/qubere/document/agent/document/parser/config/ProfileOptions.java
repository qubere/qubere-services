package ai.qubere.document.agent.document.parser.config;

/**
 * The only Docling conversion options Qubere sets, per profile. Deliberately minimal: these are
 * the options confirmed to exist in the documented submission contract. Anything else an upstream
 * parser library supports is NOT set here, because a hosted deployment may not expose it and a
 * silently ignored option would make the profile a lie.
 *
 * @param doOcr             run OCR
 * @param forceOcr          re-OCR pages that already contain a text layer; only meaningful with {@code doOcr}
 * @param doTableStructure  reconstruct table structure rather than flattening tables to text
 */
public record ProfileOptions(boolean doOcr, boolean forceOcr, boolean doTableStructure) {
}

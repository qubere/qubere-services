package ai.qubere.document.agent.document.parser;

/**
 * What {@link DocumentParserProvider#getResult} returns: the canonical provider payload plus
 * Qubere's normalization of it.
 *
 * @param canonical  the provider's complete structured document, untouched, persisted verbatim
 * @param normalized derivative and re-derivable from {@code canonical}
 */
public record ParserResult(Object canonical, NormalizedParserResult normalized) {
}

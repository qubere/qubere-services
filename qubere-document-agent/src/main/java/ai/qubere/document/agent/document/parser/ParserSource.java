package ai.qubere.document.agent.document.parser;

/**
 * How the document bytes are delivered to a {@link DocumentParserProvider}, ported from
 * {@code parser/contracts.ts}'s {@code ParserSource} union. A sealed interface (rather than one
 * record with nullable fields) makes it a compile error to construct a source that is neither
 * inline nor signed-url.
 */
public sealed interface ParserSource permits ParserSourceInline, ParserSourceSignedUrl {
    String filename();
    String mimeType();
}

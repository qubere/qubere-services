package ai.qubere.document.agent.document.parser;

/** Document delivered as inline bytes in the submission request. */
public record ParserSourceInline(String filename, String mimeType, byte[] bytes) implements ParserSource {
}

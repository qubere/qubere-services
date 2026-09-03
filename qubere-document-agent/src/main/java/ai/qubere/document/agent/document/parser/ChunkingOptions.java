package ai.qubere.document.agent.document.parser;

/** Maximum characters per chunk before splitting; {@code null} uses {@link DocumentChunkingService}'s default. */
public record ChunkingOptions(Integer maxChunkChars) {
}

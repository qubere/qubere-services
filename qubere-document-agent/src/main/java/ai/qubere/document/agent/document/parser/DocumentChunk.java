package ai.qubere.document.agent.document.parser;

import java.util.List;

/**
 * A deterministically-identified, structure-aware slice of a parsed document, ported from
 * {@code parser/chunking.ts}.
 *
 * @param id                  deterministic and stable for the same parser result and {@link DocumentChunkingService#ALGORITHM_VERSION}
 * @param sourceElementId     id of the parser element (section or table) this chunk came from
 * @param headingPath         heading trail from the document root, outermost first
 * @param content             plain text, or compact Markdown for a table chunk
 * @param estimatedTokenCount estimated, and labeled as such — see {@link DocumentChunkingService#estimateTokens}
 */
public record DocumentChunk(
        String id,
        ChunkKind kind,
        String sourceElementId,
        List<String> headingPath,
        String content,
        Integer pageStart,
        Integer pageEnd,
        int byteCount,
        int estimatedTokenCount,
        String contentHash,
        List<Provenance> provenance
) {
    public DocumentChunk {
        headingPath = headingPath == null ? List.of() : List.copyOf(headingPath);
        provenance = provenance == null ? List.of() : List.copyOf(provenance);
    }
}

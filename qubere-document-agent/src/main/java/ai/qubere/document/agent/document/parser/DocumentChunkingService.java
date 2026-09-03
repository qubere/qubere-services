package ai.qubere.document.agent.document.parser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/**
 * Deterministic, structure-aware chunking, ported from {@code parser/chunking.ts}.
 * <p>
 * Chunks follow the document's own structure rather than a fixed character window: a section
 * stays whole where it fits, and is split at line boundaries where it does not. Every chunk keeps
 * the heading trail it sits under and the page/bbox provenance of the material it contains,
 * because a chunk that cannot be traced back to a page is unusable as customs evidence.
 * <p>
 * Chunk ids are deterministic functions of (algorithm version, source element id, ordinal within
 * that element, content). Re-running the same parser result through the same algorithm produces
 * identical ids, so an evidence reference recorded weeks ago still resolves.
 * <p>
 * Stateless and side-effect-free by design (no database access).
 */
public final class DocumentChunkingService {

    /** Bump when the splitting rules change; ids then change deliberately, not silently. */
    public static final String ALGORITHM_VERSION = "qubere.chunk/1";

    private static final int DEFAULT_MAX_CHUNK_CHARS = 4_000;

    private DocumentChunkingService() {
    }

    /**
     * Character-ratio token estimate.
     * <p>
     * This is an estimate and is named one. There is no tokenizer wired in for every model this
     * framework may call, and a budget enforced on an estimate that is consistently
     * <em>conservative</em> is safe: the ratio of 4 characters per token over-counts tokens for
     * English prose, so the real payload lands under budget rather than over it.
     */
    public static int estimateTokens(String text) {
        return (int) Math.ceil(text.length() / 4.0);
    }

    /** Builds the full deterministic chunk set for a parser result. */
    public static List<DocumentChunk> buildChunks(NormalizedParserResult result, ChunkingOptions options) {
        int maxChars = options != null && options.maxChunkChars() != null ? options.maxChunkChars() : DEFAULT_MAX_CHUNK_CHARS;
        List<DocumentChunk> chunks = new ArrayList<>();

        for (ParsedSection section : result.sections()) {
            // A heading with no body is still worth a chunk: on a customs form the heading often
            // *is* the datum ("CERTIFICATE OF ORIGIN").
            String body = section.content().isEmpty() && !section.headingPath().isEmpty()
                    ? section.headingPath().get(section.headingPath().size() - 1)
                    : section.content();
            if (body.isEmpty()) {
                continue;
            }

            List<String> pieces = splitAtLines(body, maxChars);
            PageSpan span = pageSpan(section.provenance());
            for (int ordinal = 0; ordinal < pieces.size(); ordinal++) {
                String piece = pieces.get(ordinal);
                chunks.add(new DocumentChunk(
                        chunkId(section.id(), ordinal, piece),
                        ChunkKind.SECTION,
                        section.id(),
                        section.headingPath(),
                        piece,
                        span.start(),
                        span.end(),
                        piece.getBytes(StandardCharsets.UTF_8).length,
                        estimateTokens(piece),
                        sha256Hex(piece),
                        section.provenance()
                ));
            }
        }

        for (ParsedTable table : result.tables()) {
            String markdown = tableToMarkdown(table);
            if (markdown.isEmpty()) {
                continue;
            }
            List<Provenance> provenance = table.page() == null && table.bbox() == null
                    ? List.of()
                    : List.of(new Provenance(table.page(), table.bbox(), table.id()));

            List<String> pieces = splitAtLines(markdown, maxChars);
            for (int ordinal = 0; ordinal < pieces.size(); ordinal++) {
                String piece = pieces.get(ordinal);
                chunks.add(new DocumentChunk(
                        chunkId(table.id(), ordinal, piece),
                        ChunkKind.TABLE,
                        table.id(),
                        List.of(),
                        piece,
                        table.page(),
                        table.page(),
                        piece.getBytes(StandardCharsets.UTF_8).length,
                        estimateTokens(piece),
                        sha256Hex(piece),
                        provenance
                ));
            }
        }

        return chunks;
    }

    /**
     * Fills a budget in the order chunks are given, and reports what it left out.
     * <p>
     * Callers order the chunks by relevance to the agent's purpose before calling this; this
     * method only enforces the ceiling. Dropping is always reported, because a context that
     * silently lost the totals table would make an extraction agent's "field not present" look
     * like a fact about the document rather than a fact about the budget.
     */
    public static SelectionResult selectWithinBudget(List<DocumentChunk> chunks, SelectionBudget budget) {
        List<DocumentChunk> selected = new ArrayList<>();
        int tokens = 0;
        int bytes = 0;
        SelectionResult.LimitReached limitReached = null;

        for (DocumentChunk chunk : chunks) {
            if (selected.size() >= budget.maxChunks()) {
                limitReached = SelectionResult.LimitReached.CHUNKS;
                break;
            }
            if (tokens + chunk.estimatedTokenCount() > budget.maxTokens()) {
                limitReached = SelectionResult.LimitReached.TOKENS;
                break;
            }
            if (bytes + chunk.byteCount() > budget.maxBytes()) {
                limitReached = SelectionResult.LimitReached.BYTES;
                break;
            }
            selected.add(chunk);
            tokens += chunk.estimatedTokenCount();
            bytes += chunk.byteCount();
        }

        return new SelectionResult(selected, chunks.size() - selected.size(), limitReached, tokens, bytes);
    }

    /**
     * Renders a {@link ParsedTable} as Markdown.
     * <p>
     * Deliberately kept here rather than beside a specific provider adapter: it operates only on
     * the provider-neutral {@link ParsedTable} contract, so nothing about it is vendor-specific.
     */
    public static String tableToMarkdown(ParsedTable table) {
        if (table.cells().isEmpty()) {
            return "";
        }
        TreeMap<Long, String> grid = new TreeMap<>();
        int maxRow = 0;
        int maxCol = 0;
        for (ParsedTableCell cell : table.cells()) {
            grid.put(gridKey(cell.row(), cell.column()), cell.text().replace("|", "\\|").replace("\n", " ").trim());
            maxRow = Math.max(maxRow, cell.row());
            maxCol = Math.max(maxCol, cell.column());
        }

        List<String> rowStrings = new ArrayList<>();
        for (int r = 0; r <= maxRow; r++) {
            List<String> columns = new ArrayList<>();
            for (int c = 0; c <= maxCol; c++) {
                columns.add(grid.getOrDefault(gridKey(r, c), ""));
            }
            int row = r;
            rowStrings.add("| " + String.join(" | ", columns) + " |");
            // Header cells are marked explicitly; the separator goes after the last header row
            // rather than being assumed to be row 0.
            boolean rowIsHeader = table.cells().stream().anyMatch(cell -> cell.row() == row && cell.isHeader());
            boolean nextRowIsHeader = table.cells().stream().anyMatch(cell -> cell.row() == row + 1 && cell.isHeader());
            if (rowIsHeader && !nextRowIsHeader) {
                rowStrings.add("| " + String.join(" | ", java.util.Collections.nCopies(maxCol + 1, "---")) + " |");
            }
        }
        return String.join("\n", rowStrings);
    }

    private static long gridKey(int row, int column) {
        return ((long) row << 32) | (column & 0xFFFFFFFFL);
    }

    /**
     * Splits text at line boundaries into pieces no larger than {@code maxChars}. A single line
     * longer than the limit is emitted whole rather than cut mid-value: splitting "1,234,567.89"
     * across two chunks would create two plausible-looking wrong numbers, which is worse than one
     * oversized chunk.
     */
    private static List<String> splitAtLines(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return List.of(text);
        }
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (current.isEmpty()) {
                current.append(line);
                continue;
            }
            if (current.length() + 1 + line.length() > maxChars) {
                pieces.add(current.toString());
                current = new StringBuilder(line);
            } else {
                current.append('\n').append(line);
            }
        }
        if (!current.isEmpty()) {
            pieces.add(current.toString());
        }
        return pieces;
    }

    private static PageSpan pageSpan(List<Provenance> provenance) {
        List<Integer> pages = provenance.stream().map(Provenance::page).filter(java.util.Objects::nonNull).toList();
        if (pages.isEmpty()) {
            return new PageSpan(null, null);
        }
        return new PageSpan(java.util.Collections.min(pages), java.util.Collections.max(pages));
    }

    private record PageSpan(Integer start, Integer end) {
    }

    private static String chunkId(String sourceElementId, int ordinal, String content) {
        String digest = sha256Hex(ALGORITHM_VERSION + "|" + sourceElementId + "|" + ordinal + "|" + content);
        return "chk_" + digest.substring(0, 16);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format(Locale.ROOT, "%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required and must be available on every supported JVM", ex);
        }
    }
}

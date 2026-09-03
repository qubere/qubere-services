package ai.qubere.document.agent.document.parser.ibm;

import ai.qubere.document.agent.document.parser.BoundingBox;
import ai.qubere.document.agent.document.parser.CoordinateOrigin;
import ai.qubere.document.agent.document.parser.DocumentParserException;
import ai.qubere.document.agent.document.parser.NormalizedParserResult;
import ai.qubere.document.agent.document.parser.ParsedSection;
import ai.qubere.document.agent.document.parser.ParsedTable;
import ai.qubere.document.agent.document.parser.ParsedTableCell;
import ai.qubere.document.agent.document.parser.ParserErrorCode;
import ai.qubere.document.agent.document.parser.ParserMetadata;
import ai.qubere.document.agent.document.parser.ParserResult;
import ai.qubere.document.agent.document.parser.ParserWarning;
import ai.qubere.document.agent.document.parser.ProcessingProfile;
import ai.qubere.document.agent.document.parser.ProcessingRunState;
import ai.qubere.document.agent.document.parser.Provenance;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Normalizes an IBM-hosted Docling result into the provider-neutral Qubere parser contract, ported
 * from {@code parser/ibm/doclingAdapter.ts}.
 * <p>
 * Two rules govern everything here, preserved exactly from the source:
 * <ol>
 *   <li><strong>Absence is preserved.</strong> If Docling did not report a page, a bounding box, a
 *       version, or a confidence, the normalized value is {@code null}. Nothing is defaulted,
 *       inferred, or averaged into existence — a fabricated bounding box is worse than no bounding
 *       box, because it would be cited as evidence.</li>
 *   <li><strong>Identifiers are deterministic.</strong> Section and table ids derive from the
 *       element's structural position plus a hash of its content, so re-running the same parser
 *       over the same document yields the same ids and previously recorded evidence references
 *       stay resolvable.</li>
 * </ol>
 * <p>
 * Tolerant {@link JsonNode} traversal is used deliberately, mirroring the source's own
 * {@code z.object(...).passthrough()} schemas: every field the hosted deployment may omit or add
 * is read defensively rather than bound to a strict POJO that would break on an unexpected shape.
 */
public final class DoclingAdapter {

    public static final String PROVIDER_ID = "IBM_DOCLING";

    private static final Set<String> HEADING_LABELS = Set.of("section_header", "title", "page_header");
    private static final Set<String> BODY_LABELS = Set.of(
            "text", "paragraph", "list_item", "caption", "code", "formula",
            "checkbox_selected", "checkbox_unselected", "footnote", "page_footer", "reference"
    );

    private DoclingAdapter() {
    }

    /**
     * Translates a provider task status into a Qubere state. An unrecognized status keeps the run
     * polling rather than resolving it: mapping an unknown string to FAILED would discard work
     * that is still running, and mapping it to SUCCEEDED would fetch a result that does not exist.
     * The polling-attempt ceiling (enforced by the worker, not here) is what eventually ends an
     * indefinitely unknown state.
     */
    public static TaskStatusTranslation translateTaskStatus(String rawStatus) {
        String status = rawStatus == null ? "" : rawStatus.trim().toLowerCase(Locale.ROOT);
        if (Set.of("success", "succeeded", "completed").contains(status)) {
            return new TaskStatusTranslation(ProcessingRunState.SUCCEEDED, true);
        }
        if (Set.of("failure", "failed", "error", "revoked", "cancelled").contains(status)) {
            return new TaskStatusTranslation(ProcessingRunState.FAILED, true);
        }
        if (Set.of("pending", "queued", "started", "running").contains(status)) {
            return new TaskStatusTranslation(ProcessingRunState.POLLING, true);
        }
        return new TaskStatusTranslation(ProcessingRunState.POLLING, false);
    }

    public record TaskStatusTranslation(ProcessingRunState state, boolean recognized) {
    }

    /**
     * Validates and normalizes a Docling result payload (the inline-content shape returned by the
     * documented {@code /convert/source/...} endpoints). Throws {@code PARSER_RESULT_INVALID}
     * (non-retryable — the same payload will not become valid on a second attempt) when the
     * envelope cannot be understood at all, and {@code PARSER_RESULT_INCOMPLETE} when it is
     * structurally valid but carries neither a structured document nor any derivative content.
     */
    public static ParserResult adaptDoclingResult(JsonNode rawPayload, ProcessingProfile profile) {
        if (rawPayload == null || rawPayload.isMissingNode() || rawPayload.isNull()) {
            throw new DocumentParserException(
                    ParserErrorCode.PARSER_RESULT_INVALID,
                    "The parser result did not match the expected provider contract."
            );
        }
        JsonNode documentNode = rawPayload.path("document");
        JsonNode doc = documentNode.path("json_content");
        String markdown = textOrNull(documentNode.path("md_content"));
        String text = textOrNull(documentNode.path("text_content"));

        boolean hasDoc = !doc.isMissingNode() && !doc.isNull();
        boolean hasMarkdown = markdown != null && !markdown.isBlank();
        boolean hasText = text != null && !text.isBlank();
        if (!hasDoc && !hasMarkdown && !hasText) {
            throw new DocumentParserException(
                    ParserErrorCode.PARSER_RESULT_INCOMPLETE,
                    "The parser reported completion but returned no document content."
            );
        }

        return normalizeDoclingDocument(hasDoc ? doc : null, markdown, profile, rawPayload, null, null);
    }

    /**
     * Normalizes an already-retrieved Docling document node. Split out from
     * {@link #adaptDoclingResult} because a hosted deployment may deliver the document behind
     * presigned artifact URLs rather than inline; both delivery shapes then share this one
     * normalization step, so they cannot drift apart.
     *
     * @param doc                    the {@code DoclingDocument} JSON node, or {@code null} when unavailable
     * @param envelope               the outer result envelope, used for {@code errors}/{@code processing_time}
     * @param confidence             the deployment's own confidence node, or {@code null} if unreported
     * @param processingTimeSeconds  overrides the envelope's own {@code processing_time} when supplied
     */
    public static ParserResult normalizeDoclingDocument(
            JsonNode doc,
            String markdown,
            ProcessingProfile profile,
            JsonNode envelope,
            JsonNode confidence,
            Double processingTimeSeconds
    ) {
        List<ParsedSection> sections = doc == null ? List.of() : buildSections(doc);
        List<ParsedTable> tables = doc == null ? List.of() : buildTables(doc);
        List<Integer> pageTextLengths = buildPageTextLengths(doc);

        NormalizedParserResult normalized = new NormalizedParserResult(
                NormalizedParserResult.CONTRACT_VERSION,
                profile,
                buildMetadata(envelope, doc, pageTextLengths, confidence, processingTimeSeconds),
                markdown,
                sections,
                tables,
                collectWarnings(envelope),
                pageTextLengths
        );

        // canonical is the provider's own payload, kept meaning-for-meaning: the structured
        // document when present, else the envelope it arrived in.
        Object canonical = doc != null ? doc : envelope;
        return new ParserResult(canonical, normalized);
    }

    // ---------------------------------------------------------------------------------------
    // Confidence / metadata
    // ---------------------------------------------------------------------------------------

    /**
     * These numbers are genuinely emitted by the hosted service, so they are carried through
     * rather than reported as absent. A null score means the service did not measure that
     * dimension (no tables, or no OCR pass) and stays null: "not measured" is not "zero".
     * {@code ocrUsed} is derived from whether an OCR score exists at all — a number means an OCR
     * pass ran and was scored; a null does NOT prove OCR was skipped (it could be unmeasured), so
     * that case reports unknown rather than false.
     */
    public static ConfidenceMapping mapConfidence(JsonNode confidence) {
        if (confidence == null || confidence.isMissingNode() || confidence.isNull()) {
            return new ConfidenceMapping(null, null, null);
        }
        Double meanScore = numberOrNull(confidence.path("mean_score"));
        Double parseScore = numberOrNull(confidence.path("parse_score"));
        // mean_score spans every measured dimension, so it is the closest thing the service
        // offers to a single "how well was this parsed" figure. Falling back to parse_score
        // keeps a value when only that dimension was scored.
        Double parserConfidence = meanScore != null ? meanScore : parseScore;
        Double ocrConfidence = numberOrNull(confidence.path("ocr_score"));
        return new ConfidenceMapping(parserConfidence, ocrConfidence, ocrConfidence == null ? null : Boolean.TRUE);
    }

    public record ConfidenceMapping(Double parserConfidence, Double ocrConfidence, Boolean ocrUsed) {
    }

    private static ParserMetadata buildMetadata(
            JsonNode envelope, JsonNode doc, List<Integer> pageTextLengths, JsonNode confidence, Double processingTimeSecondsOverride
    ) {
        ConfidenceMapping scores = mapConfidence(confidence);
        Integer declaredPageCount = doc == null ? null : doc.path("pages").size() > 0 ? doc.path("pages").size() : null;
        Integer pageCount = declaredPageCount != null ? declaredPageCount
                : !pageTextLengths.isEmpty() ? pageTextLengths.size() : null;

        Double seconds = processingTimeSecondsOverride != null
                ? processingTimeSecondsOverride
                : numberOrNull(envelope == null ? null : envelope.path("processing_time"));

        return new ParserMetadata(
                PROVIDER_ID,
                doc != null ? textOrNull(doc.path("schema_name")) : null,
                doc != null ? textOrNull(doc.path("version")) : null,
                null,
                null,
                pageCount,
                scores.ocrUsed(),
                // Whether OCR covered every page is a separate claim the service does not make,
                // so it stays unknown even when an OCR score exists.
                null,
                seconds == null ? null : Math.round(seconds * 1000),
                scores.parserConfidence(),
                scores.ocrConfidence()
        );
    }

    private static List<ParserWarning> collectWarnings(JsonNode envelope) {
        List<ParserWarning> warnings = new ArrayList<>();
        if (envelope != null && envelope.path("errors").isArray()) {
            for (JsonNode error : envelope.path("errors")) {
                // Provider errors on an otherwise successful conversion are non-fatal, but must be
                // recorded. Only a short, structure-free rendering is kept.
                warnings.add(new ParserWarning("PROVIDER_REPORTED_ERROR", error.toString(), null));
            }
        }
        return warnings;
    }

    // ---------------------------------------------------------------------------------------
    // Bounding box / provenance
    // ---------------------------------------------------------------------------------------

    static BoundingBox normalizeBbox(JsonNode raw) {
        if (raw == null || raw.isMissingNode() || raw.isNull()) {
            return null;
        }
        Double l = numberOrNull(raw.path("l"));
        Double t = numberOrNull(raw.path("t"));
        Double r = numberOrNull(raw.path("r"));
        Double b = numberOrNull(raw.path("b"));
        if (l == null || t == null || r == null || b == null) {
            return null;
        }
        String origin = textOrNull(raw.path("coord_origin"));
        CoordinateOrigin coordOrigin = null;
        if (origin != null) {
            String upper = origin.toUpperCase(Locale.ROOT);
            if (upper.equals("TOPLEFT")) {
                coordOrigin = CoordinateOrigin.TOPLEFT;
            } else if (upper.equals("BOTTOMLEFT")) {
                coordOrigin = CoordinateOrigin.BOTTOMLEFT;
            }
        }
        return new BoundingBox(l, t, r, b, coordOrigin);
    }

    static List<Provenance> normalizeProvenance(JsonNode prov, String elementRef) {
        if (prov == null || !prov.isArray() || prov.isEmpty()) {
            // No provenance at all is itself a fact worth recording, but only when we have
            // something to point at. An entry of all-nulls carries no information.
            return elementRef == null ? List.of() : List.of(new Provenance(null, null, elementRef));
        }
        List<Provenance> result = new ArrayList<>();
        for (JsonNode entry : prov) {
            Double pageNo = numberOrNull(entry.path("page_no"));
            Integer page = pageNo != null && pageNo > 0 ? pageNo.intValue() : null;
            result.add(new Provenance(page, normalizeBbox(entry.path("bbox")), elementRef));
        }
        return result;
    }

    private static Integer firstPage(List<Provenance> provenance) {
        for (Provenance entry : provenance) {
            if (entry.page() != null) {
                return entry.page();
            }
        }
        return null;
    }

    private static BoundingBox firstBbox(List<Provenance> provenance) {
        for (Provenance entry : provenance) {
            if (entry.bbox() != null) {
                return entry.bbox();
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------
    // Sections
    // ---------------------------------------------------------------------------------------

    private static final class SectionAccumulator {
        private List<String> headingPath;
        private final List<String> parts = new ArrayList<>();
        private final List<Provenance> provenance = new ArrayList<>();
    }

    /**
     * Walks Docling's {@code texts} array in document order, grouping body text under the heading
     * trail that precedes it. Docling's {@code level} on a section_header gives the nesting depth,
     * so the trail is maintained as a stack rather than flattened.
     */
    static List<ParsedSection> buildSections(JsonNode doc) {
        JsonNode texts = doc.path("texts");
        List<ParsedSection> sections = new ArrayList<>();
        List<Map.Entry<Integer, String>> headingStack = new ArrayList<>();
        SectionAccumulator[] current = new SectionAccumulator[1];

        Runnable flush = () -> {
            SectionAccumulator acc = current[0];
            if (acc == null) {
                return;
            }
            String content = String.join("\n", acc.parts).trim();
            if (!content.isEmpty() || !acc.headingPath.isEmpty()) {
                int ordinal = sections.size();
                sections.add(new ParsedSection(
                        stableId("sec", ordinal, String.join(">", acc.headingPath) + "\u0000" + content),
                        acc.headingPath, content, acc.provenance
                ));
            }
            current[0] = null;
        };

        if (texts.isArray()) {
            for (JsonNode item : texts) {
                String label = textOrNull(item.path("label"));
                label = label == null ? "" : label.toLowerCase(Locale.ROOT);
                String text = textOrNull(item.path("text"));
                if (text == null) {
                    text = textOrNull(item.path("orig"));
                }
                text = text == null ? "" : text.trim();
                if (text.isEmpty()) {
                    continue;
                }

                String elementRef = textOrNull(item.path("self_ref"));
                List<Provenance> provenance = normalizeProvenance(item.path("prov"), elementRef);

                if (HEADING_LABELS.contains(label)) {
                    flush.run();
                    Double levelNode = numberOrNull(item.path("level"));
                    int level = levelNode != null && levelNode > 0 ? levelNode.intValue() : 1;
                    while (!headingStack.isEmpty() && headingStack.get(headingStack.size() - 1).getKey() >= level) {
                        headingStack.remove(headingStack.size() - 1);
                    }
                    headingStack.add(Map.entry(level, text));
                    SectionAccumulator acc = new SectionAccumulator();
                    acc.headingPath = headingStack.stream().map(Map.Entry::getValue).toList();
                    acc.provenance.addAll(provenance);
                    current[0] = acc;
                    continue;
                }

                if (!BODY_LABELS.contains(label)) {
                    // An unrecognized label is kept out rather than force-fit — customs documents
                    // carry stamps and form fields Docling may label in ways not seen before, and
                    // silently discarding them would lose evidence in principle; here it is simply
                    // not attributed to a section without a confident label match.
                    continue;
                }

                if (current[0] == null) {
                    SectionAccumulator acc = new SectionAccumulator();
                    acc.headingPath = headingStack.stream().map(Map.Entry::getValue).toList();
                    current[0] = acc;
                }
                current[0].parts.add(text);
                current[0].provenance.addAll(provenance);
            }
        }
        flush.run();
        return sections;
    }

    // ---------------------------------------------------------------------------------------
    // Tables
    // ---------------------------------------------------------------------------------------

    private record TableCellsResult(List<ParsedTableCell> cells, int maxRow, int maxColumn) {
    }

    private static TableCellsResult buildTableCells(JsonNode rawCells) {
        List<ParsedTableCell> cells = new ArrayList<>();
        int maxRow = 0;
        int maxColumn = 0;
        if (rawCells != null && rawCells.isArray()) {
            for (JsonNode raw : rawCells) {
                Double startRowNode = numberOrNull(raw.path("start_row_offset_idx"));
                Double startColNode = numberOrNull(raw.path("start_col_offset_idx"));
                if (startRowNode == null || startColNode == null) {
                    continue;
                }
                int startRow = startRowNode.intValue();
                int startCol = startColNode.intValue();
                Double endRowNode = numberOrNull(raw.path("end_row_offset_idx"));
                Double endColNode = numberOrNull(raw.path("end_col_offset_idx"));
                int endRow = endRowNode != null ? endRowNode.intValue() : startRow + 1;
                int endCol = endColNode != null ? endColNode.intValue() : startCol + 1;
                Double rowSpanNode = numberOrNull(raw.path("row_span"));
                Double colSpanNode = numberOrNull(raw.path("col_span"));
                int rowSpan = rowSpanNode != null && rowSpanNode > 0 ? rowSpanNode.intValue() : Math.max(1, endRow - startRow);
                int colSpan = colSpanNode != null && colSpanNode > 0 ? colSpanNode.intValue() : Math.max(1, endCol - startCol);

                BoundingBox bbox = normalizeBbox(raw.path("bbox"));
                cells.add(new ParsedTableCell(
                        startRow, startCol, rowSpan, colSpan,
                        raw.path("column_header").asBoolean(false) || raw.path("row_header").asBoolean(false),
                        textOrNull(raw.path("text")) != null ? textOrNull(raw.path("text")) : "",
                        bbox == null ? null : new Provenance(null, bbox, null)
                ));
                maxRow = Math.max(maxRow, startRow + rowSpan);
                maxColumn = Math.max(maxColumn, startCol + colSpan);
            }
        }
        return new TableCellsResult(cells, maxRow, maxColumn);
    }

    static List<ParsedTable> buildTables(JsonNode doc) {
        JsonNode rawTables = doc.path("tables");
        List<ParsedTable> tables = new ArrayList<>();
        if (!rawTables.isArray()) {
            return tables;
        }
        int index = 0;
        for (JsonNode raw : rawTables) {
            String elementRef = textOrNull(raw.path("self_ref"));
            List<Provenance> provenance = normalizeProvenance(raw.path("prov"), elementRef);
            JsonNode data = raw.path("data");
            TableCellsResult cellsResult = buildTableCells(data.path("table_cells"));

            Double declaredRowsNode = numberOrNull(data.path("num_rows"));
            Double declaredColsNode = numberOrNull(data.path("num_cols"));
            int rowCount = declaredRowsNode != null ? declaredRowsNode.intValue() : cellsResult.maxRow();
            int columnCount = declaredColsNode != null ? declaredColsNode.intValue() : cellsResult.maxColumn();

            String contentKey = cellsResult.cells().stream()
                    .map(c -> c.row() + ":" + c.column() + ":" + c.text())
                    .reduce((a, b) -> a + "\u0000" + b).orElse("");

            ParsedTable table = new ParsedTable(
                    stableId("tbl", index, contentKey), index,
                    // Docling stores captions as $refs into `texts`; resolving them is not
                    // reliable across versions, so the caption is reported as absent rather than
                    // guessed from surrounding text.
                    null,
                    firstPage(provenance), firstBbox(provenance),
                    rowCount, columnCount, cellsResult.cells(), null
            );
            tables.add(cellsResult.cells().isEmpty() ? table : new ParsedTable(
                    table.id(), table.index(), table.caption(), table.page(), table.bbox(),
                    table.rowCount(), table.columnCount(), table.cells(), tableCellsToHtml(table)
            ));
            index++;
        }
        return tables;
    }

    /**
     * Renders a cell grid as HTML. A loss-minimizing derivative, not the canonical form: spans and
     * header flags survive, coordinates do not. The canonical structure stays in the cell array and
     * in the untouched Docling JSON.
     */
    static String tableCellsToHtml(ParsedTable table) {
        Map<Long, List<ParsedTableCell>> byRow = new LinkedHashMap<>();
        for (ParsedTableCell cell : table.cells()) {
            byRow.computeIfAbsent((long) cell.row(), k -> new ArrayList<>()).add(cell);
        }
        StringBuilder html = new StringBuilder("<table>");
        for (Map.Entry<Long, List<ParsedTableCell>> row : new java.util.TreeMap<>(byRow).entrySet()) {
            html.append("<tr>");
            for (ParsedTableCell cell : row.getValue()) {
                String tag = cell.isHeader() ? "th" : "td";
                html.append('<').append(tag);
                if (cell.rowSpan() > 1) {
                    html.append(" rowspan=\"").append(cell.rowSpan()).append('"');
                }
                if (cell.columnSpan() > 1) {
                    html.append(" colspan=\"").append(cell.columnSpan()).append('"');
                }
                html.append('>').append(escapeHtml(cell.text())).append("</").append(tag).append('>');
            }
            html.append("</tr>");
        }
        html.append("</table>");
        return html.toString();
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ---------------------------------------------------------------------------------------
    // Page coverage
    // ---------------------------------------------------------------------------------------

    private static List<Integer> buildPageTextLengths(JsonNode doc) {
        if (doc == null) {
            return List.of();
        }
        Map<Integer, Integer> lengthsByPage = new HashMap<>();
        JsonNode texts = doc.path("texts");
        if (texts.isArray()) {
            for (JsonNode item : texts) {
                String text = textOrNull(item.path("text"));
                if (text == null) {
                    continue;
                }
                for (Provenance provenance : normalizeProvenance(item.path("prov"), null)) {
                    if (provenance.page() != null) {
                        lengthsByPage.merge(provenance.page(), text.length(), Integer::sum);
                    }
                }
            }
        }
        if (lengthsByPage.isEmpty()) {
            return List.of();
        }
        int maxPage = lengthsByPage.keySet().stream().max(Integer::compareTo).orElse(0);
        List<Integer> result = new ArrayList<>();
        for (int page = 1; page <= maxPage; page++) {
            result.add(lengthsByPage.getOrDefault(page, 0));
        }
        return result;
    }

    // ---------------------------------------------------------------------------------------
    // Ids
    // ---------------------------------------------------------------------------------------

    /** Deterministic id: kind, ordinal, and a content digest. */
    static String stableId(String kind, int ordinal, String content) {
        return kind + "_" + String.format(Locale.ROOT, "%04d", ordinal) + "_" + shortHash(content);
    }

    private static String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format(Locale.ROOT, "%02x", b));
            }
            return hex.substring(0, 12);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required and must be available on every supported JVM", ex);
        }
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() || !node.isTextual() ? null : node.asText();
    }

    private static Double numberOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() || !node.isNumber() ? null : node.asDouble();
    }
}

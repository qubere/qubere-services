package ai.qubere.document.agent.document.tools;

import ai.qubere.document.agent.document.context.DocumentContext;
import ai.qubere.document.agent.document.context.QubereDocumentContextBuilder;
import ai.qubere.document.agent.document.parser.NormalizedParserResult;
import ai.qubere.document.agent.document.parser.config.ParserProperties;
import ai.qubere.document.agent.document.processing.DocumentParseResultService;
import ai.qubere.agent.tools.AgentTool;
import ai.qubere.agent.tools.ToolDescriptor;
import ai.qubere.agent.tools.ToolInput;
import ai.qubere.agent.tools.ToolResult;
import ai.qubere.agent.tools.ToolRiskLevel;
import ai.qubere.agent.tools.ToolSideEffect;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Looks up the current active {@link NormalizedParserResult} for a document and assembles a
 * budget-enforced extraction context from it, via {@link QubereDocumentContextBuilder}.
 * <p>
 * This replaces the original migration scaffold, which only echoed back whatever
 * {@code documentContext} string argument a caller happened to supply. That placeholder is why
 * {@code DocumentIntelligenceAgent} could not run against a real parsed document end-to-end before
 * this — see {@code MIGRATION.md} for the fuller context on why this tool, the parse-result store,
 * and the context builder all needed to land together.
 */
@Component
public class DocumentContextLookupTool implements AgentTool {

    public static final String TOOL_NAME = "document.context.lookup";

    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_NAME,
            "Looks up the current active parsed context for a document, assembled within the configured context budget.",
            ToolRiskLevel.READ_ONLY,
            Set.of(ToolSideEffect.NONE),
            Set.of("document.read"),
            Map.of("type", "object", "required", Set.of("documentId"), "properties", Map.of("documentId", Map.of("type", "string"))),
            Map.of("type", "object", "properties", Map.of(
                    "found", Map.of("type", "boolean"),
                    "documentContext", Map.of("type", "string"),
                    "truncated", Map.of("type", "boolean"),
                    "selectedChunkCount", Map.of("type", "integer"),
                    "droppedChunkCount", Map.of("type", "integer")
            )),
            Duration.ofSeconds(10),
            false
    );

    private final DocumentParseResultService parseResultService;
    private final ParserProperties parserProperties;

    public DocumentContextLookupTool(DocumentParseResultService parseResultService, ParserProperties parserProperties) {
        this.parseResultService = parseResultService;
        this.parserProperties = parserProperties;
    }

    @Override
    public ToolResult execute(ToolInput input) {
        Object documentIdArg = input.arguments().get("documentId");
        String documentId = documentIdArg == null ? "" : documentIdArg.toString().trim();
        if (documentId.isEmpty()) {
            return ToolResult.success(Map.of("found", false, "documentContext", ""));
        }

        Optional<NormalizedParserResult> result = parseResultService.findActiveResult(documentId);
        if (result.isEmpty()) {
            return ToolResult.success(Map.of("found", false, "documentContext", ""));
        }

        DocumentContext context = QubereDocumentContextBuilder.build(result.get(), parserProperties.getContextBudget());
        return ToolResult.success(Map.of(
                "found", true,
                "documentContext", context.content(),
                "truncated", context.wasTruncated(),
                "selectedChunkCount", context.selectedChunkCount(),
                "droppedChunkCount", context.droppedChunkCount()
        ));
    }

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }
}

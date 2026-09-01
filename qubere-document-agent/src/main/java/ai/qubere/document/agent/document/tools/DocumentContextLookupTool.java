package ai.qubere.document.agent.document.tools;

import ai.qubere.agent.tools.AgentTool;
import ai.qubere.agent.tools.ToolDescriptor;
import ai.qubere.agent.tools.ToolInput;
import ai.qubere.agent.tools.ToolResult;
import ai.qubere.agent.tools.ToolRiskLevel;
import ai.qubere.agent.tools.ToolSideEffect;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class DocumentContextLookupTool implements AgentTool {

    public static final String TOOL_NAME = "document.context.lookup";

    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_NAME,
            "Looks up parsed QubereDocumentContext for a document. Initial migration scaffold returns supplied context only; DB/parser lookup is the next migration step.",
            ToolRiskLevel.READ_ONLY,
            Set.of(ToolSideEffect.NONE),
            Set.of("document.read"),
            Map.of("type", "object", "required", Set.of("documentId"), "properties", Map.of("documentId", Map.of("type", "string"))),
            Map.of("type", "object", "properties", Map.of("documentContext", Map.of("type", "string"), "found", Map.of("type", "boolean"))),
            Duration.ofSeconds(10),
            false
    );

    @Override
    public ToolResult execute(ToolInput input) {
        Object suppliedContext = input.arguments().get("documentContext");
        String context = suppliedContext == null ? "" : suppliedContext.toString();
        return ToolResult.success(Map.of(
                "found", !context.isBlank(),
                "documentContext", context,
                "migrationStatus", "lookup-adapter-pending"
        ));
    }

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }
}

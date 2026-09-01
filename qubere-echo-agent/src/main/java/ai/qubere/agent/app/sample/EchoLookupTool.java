package ai.qubere.agent.app.sample;

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
public class EchoLookupTool implements AgentTool {

    public static final String TOOL_NAME = "echo.lookup";

    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_NAME,
            "Read-only sample tool that returns deterministic diagnostics for the supplied message.",
            ToolRiskLevel.READ_ONLY,
            Set.of(ToolSideEffect.NONE),
            Set.of(),
            Map.of(
                    "type", "object",
                    "required", Set.of("message"),
                    "properties", Map.of(
                            "message", Map.of("type", "string")
                    )
            ),
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "normalizedMessage", Map.of("type", "string"),
                            "messageLength", Map.of("type", "integer"),
                            "tenantId", Map.of("type", "string")
                    )
            ),
            Duration.ofSeconds(5),
            false
    );

    @Override
    public ToolResult execute(ToolInput input) {
        String message = input.arguments().getOrDefault("message", "").toString();
        return ToolResult.success(Map.of(
                "normalizedMessage", message.trim().toLowerCase(),
                "messageLength", message.length(),
                "executionId", input.executionId(),
                "tenantId", input.tenantId() == null ? "" : input.tenantId(),
                "actorId", input.actorId() == null ? "" : input.actorId()
        ));
    }

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }
}
package ai.qubere.agent.tools;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

public record ToolDescriptor(
        String name,
        String description,
        ToolRiskLevel riskLevel,
        Set<ToolSideEffect> sideEffects,
        Set<String> requiredPermissions,
        Map<String, Object> inputSchema,
        Map<String, Object> outputSchema,
        Duration timeout,
        boolean humanApprovalRequired
) {
    public ToolDescriptor {
        riskLevel = riskLevel == null ? ToolRiskLevel.LOW : riskLevel;
        sideEffects = sideEffects == null ? Set.of() : Set.copyOf(sideEffects);
        requiredPermissions = requiredPermissions == null ? Set.of() : Set.copyOf(requiredPermissions);
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
        outputSchema = outputSchema == null ? Map.of() : Map.copyOf(outputSchema);
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
    }

    public static ToolDescriptor unknown() {
        return new ToolDescriptor(
                "unknown",
                "Tool descriptor not provided.",
                ToolRiskLevel.LOW,
                Set.of(),
                Set.of(),
                Map.of(),
                Map.of(),
                Duration.ofSeconds(30),
                false
        );
    }
}

package ai.qubere.agent.app.api;

import java.util.Map;

public record PromptTemplateRequest(
        String promptId,
        String agentId,
        String version,
        String status,
        String systemTemplate,
        String userTemplate,
        Map<String, Object> metadata
) {
}

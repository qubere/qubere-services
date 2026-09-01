package ai.qubere.agent.app.sample;

import ai.qubere.agent.ai.AgentAiClient;
import ai.qubere.agent.ai.AgentAiRequestMetadata;
import ai.qubere.agent.ai.AgentPrompt;
import ai.qubere.agent.api.Agent;
import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentRiskLevel;
import ai.qubere.agent.core.AgentDecisionDraft;
import ai.qubere.agent.core.AgentErrorCode;
import ai.qubere.agent.core.AgentExecutionException;
import ai.qubere.agent.core.AgentResult;
import ai.qubere.agent.core.GenericAgentInput;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class AiBackedAnalysisAgent implements Agent<GenericAgentInput, AgentResult<Map<String, Object>>> {

    private static final AgentDescriptor DESCRIPTOR = new AgentDescriptor(
            "generic.ai-analysis",
            "AI-backed Analysis Agent",
            "0.1.0",
            "Reference agent that exercises the framework Spring AI abstraction with structured output.",
            AgentRiskLevel.LOW,
            Set.of("analysis", "diagnostics", "ai")
    );

    private final ObjectProvider<AgentAiClient> aiClientProvider;

    public AiBackedAnalysisAgent(ObjectProvider<AgentAiClient> aiClientProvider) {
        this.aiClientProvider = aiClientProvider;
    }

    @Override
    public AgentDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentResult<Map<String, Object>> run(GenericAgentInput input, AgentExecutionContext context) {
        String message = input.values().getOrDefault("message", "").toString();
        AgentPrompt prompt = new AgentPrompt(
                "You are a concise diagnostic analysis agent. Return only structured output matching the requested schema.",
                "Analyze this message and classify its sentiment, summary, and recommended next action: " + message,
                Map.of("message", message)
        );
        AgentAiClient aiClient = aiClientProvider.getIfAvailable(() -> {
            throw new AgentExecutionException(
                    AgentErrorCode.AI_PROVIDER_UNAVAILABLE,
                    "No AgentAiClient is configured. Enable a Spring AI provider or provide an AgentAiClient bean."
            );
        });
        AiAnalysisResponse response = aiClient.generate(
                prompt,
                AiAnalysisResponse.class,
                AgentAiRequestMetadata.from(context)
        );

        return new AgentResult<>(
                Map.of(
                        "executionId", context.executionId(),
                        "tenantId", context.tenantId(),
                        "received", input.values(),
                        "analysis", Map.of(
                                "summary", response.summary(),
                                "sentiment", response.sentiment(),
                                "recommendedAction", response.recommendedAction(),
                                "confidence", response.confidence()
                        )
                ),
                new AgentDecisionDraft(response.recommendedAction(), response.summary(), response.confidence()),
                List.of(),
                Map.of("agentId", DESCRIPTOR.id(), "aiBacked", true)
        );
    }

    public record AiAnalysisResponse(
            String summary,
            String sentiment,
            String recommendedAction,
            double confidence
    ) {
    }
}
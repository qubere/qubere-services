package ai.qubere.agent.ai;

import ai.qubere.agent.redaction.AgentRedactionService;
import ai.qubere.agent.redaction.DefaultAgentRedactionService;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "agent-platform.ai.spring", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SpringAiAgentClient implements AgentAiClient {

    private final ChatClient chatClient;
    private final ModelUsageRecorder modelUsageRecorder;
    private final AgentRedactionService redactionService;

    public SpringAiAgentClient(ChatClient.Builder chatClientBuilder, ObjectProvider<ModelUsageRecorder> modelUsageRecorder) {
        this(chatClientBuilder, modelUsageRecorder, null);
    }

    @Autowired
    public SpringAiAgentClient(
            ChatClient.Builder chatClientBuilder,
            ObjectProvider<ModelUsageRecorder> modelUsageRecorder,
            ObjectProvider<AgentRedactionService> redactionService
    ) {
        this.chatClient = chatClientBuilder.build();
        this.modelUsageRecorder = modelUsageRecorder.getIfAvailable(ModelUsageRecorder::noop);
        this.redactionService = redactionService == null
                ? new DefaultAgentRedactionService()
                : redactionService.getIfAvailable(DefaultAgentRedactionService::new);
    }

    @Override
    public <T> T generate(AgentPrompt prompt, Class<T> responseType) {
        return generate(prompt, responseType, AgentAiRequestMetadata.empty());
    }

    @Override
    public <T> T generate(AgentPrompt prompt, Class<T> responseType, AgentAiRequestMetadata metadata) {
        AgentAiRequestMetadata requestMetadata = metadata == null ? AgentAiRequestMetadata.empty() : metadata;
        String usageId = UUID.randomUUID().toString();
        Instant startedAt = Instant.now();
        Map<String, Object> usageMetadata = new LinkedHashMap<>(requestMetadata.metadata());
        usageMetadata.put("responseType", responseType.getName());
        usageMetadata.put("streamingRequested", Boolean.TRUE.equals(requestMetadata.streaming()));
        usageMetadata.put("temperature", requestMetadata.temperature());
        usageMetadata.put("maxOutputTokens", requestMetadata.maxOutputTokens());
        applyPromptLogging(prompt, requestMetadata, usageMetadata);

        modelUsageRecorder.record(record(
                usageId,
                requestMetadata,
                ModelUsageStatus.STARTED,
                null,
                null,
                null,
                null,
                null,
                null,
                usageMetadata,
                startedAt,
                null
        ));

        try {
            ChatClient.ChatClientRequestSpec request = chatClient.prompt()
                    .system(prompt.system())
                    .user(prompt.user());
            request = applyOptions(request, requestMetadata, usageMetadata);

            ResponseEntity<ChatResponse, T> responseEntity = request
                    .call()
                    .responseEntity(responseType);
            Instant completedAt = Instant.now();
            UsageValues usageValues = extractUsage(responseEntity.response(), usageMetadata);
            modelUsageRecorder.record(record(
                    usageId,
                    requestMetadata,
                    ModelUsageStatus.SUCCEEDED,
                    usageValues.inputTokens(),
                    usageValues.outputTokens(),
                    usageValues.totalTokens(),
                    usageValues.estimatedCostUsd(),
                    Duration.between(startedAt, completedAt).toMillis(),
                    null,
                    usageMetadata,
                    startedAt,
                    completedAt
            ));
            return responseEntity.entity();
        } catch (RuntimeException ex) {
            Instant completedAt = Instant.now();
            modelUsageRecorder.record(record(
                    usageId,
                    requestMetadata,
                    ModelUsageStatus.FAILED,
                    null,
                    null,
                    null,
                    null,
                    Duration.between(startedAt, completedAt).toMillis(),
                    ex.getMessage(),
                    usageMetadata,
                    startedAt,
                    completedAt
            ));
            throw ex;
        }
    }

    private void applyPromptLogging(AgentPrompt prompt, AgentAiRequestMetadata requestMetadata, Map<String, Object> usageMetadata) {
        boolean logPrompts = Boolean.TRUE.equals(requestMetadata.logPrompts());
        usageMetadata.put("promptLoggingEnabled", logPrompts);
        if (!logPrompts) {
            return;
        }
        usageMetadata.put("systemPrompt", redactionService.redactText(prompt.system()));
        usageMetadata.put("userPrompt", redactionService.redactText(prompt.user()));
        usageMetadata.put("promptVariables", redactionService.redactMap(prompt.variables()));
    }

    private ChatClient.ChatClientRequestSpec applyOptions(
            ChatClient.ChatClientRequestSpec request,
            AgentAiRequestMetadata requestMetadata,
            Map<String, Object> usageMetadata
    ) {
        ChatOptions.Builder<?> options = ChatOptions.builder();
        boolean hasOptions = false;
        if (requestMetadata.modelName() != null && !requestMetadata.modelName().isBlank()) {
            options.model(requestMetadata.modelName());
            hasOptions = true;
        }
        if (requestMetadata.temperature() != null) {
            options.temperature(requestMetadata.temperature());
            hasOptions = true;
        }
        if (requestMetadata.maxOutputTokens() != null) {
            options.maxTokens(requestMetadata.maxOutputTokens());
            hasOptions = true;
        }
        if (Boolean.TRUE.equals(requestMetadata.streaming())) {
            usageMetadata.put("streamingApplied", false);
            usageMetadata.put("streamingNote", "Structured AgentAiClient.generate uses synchronous responseEntity; streaming requires a streaming API variant.");
        }
        return hasOptions ? request.options(options) : request;
    }

    private UsageValues extractUsage(ChatResponse response, Map<String, Object> usageMetadata) {
        if (response == null || response.getMetadata() == null) {
            return UsageValues.empty();
        }
        ChatResponseMetadata metadata = response.getMetadata();
        if (metadata.getModel() != null && !metadata.getModel().isBlank()) {
            usageMetadata.put("providerModel", metadata.getModel());
        }
        Usage usage = metadata.getUsage();
        if (usage == null) {
            return UsageValues.empty();
        }
        Long inputTokens = toLong(usage.getPromptTokens());
        Long outputTokens = toLong(usage.getCompletionTokens());
        Long totalTokens = toLong(usage.getTotalTokens());
        if (totalTokens == null && (inputTokens != null || outputTokens != null)) {
            totalTokens = (inputTokens == null ? 0L : inputTokens) + (outputTokens == null ? 0L : outputTokens);
        }
        Object nativeUsage = usage.getNativeUsage();
        if (nativeUsage != null) {
            usageMetadata.put("nativeUsageType", nativeUsage.getClass().getName());
            usageMetadata.put("nativeUsage", redactionService.redactText(nativeUsage.toString()));
        }
        if (usage.getCacheReadInputTokens() != null) {
            usageMetadata.put("cacheReadInputTokens", usage.getCacheReadInputTokens());
        }
        if (usage.getCacheWriteInputTokens() != null) {
            usageMetadata.put("cacheWriteInputTokens", usage.getCacheWriteInputTokens());
        }
        return new UsageValues(inputTokens, outputTokens, totalTokens, null);
    }

    private Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }

    private ModelUsageRecord record(
            String usageId,
            AgentAiRequestMetadata requestMetadata,
            ModelUsageStatus status,
            Long inputTokens,
            Long outputTokens,
            Long totalTokens,
            BigDecimal estimatedCostUsd,
            Long latencyMs,
            String errorMessage,
            Map<String, Object> usageMetadata,
            Instant startedAt,
            Instant completedAt
    ) {
        return new ModelUsageRecord(
                usageId,
                requestMetadata.executionId(),
                requestMetadata.tenantId(),
                requestMetadata.actorId(),
                requestMetadata.correlationId(),
                requestMetadata.agentId(),
                requestMetadata.agentVersion(),
                requestMetadata.modelProvider(),
                requestMetadata.modelName(),
                requestMetadata.promptVersion(),
                status,
                inputTokens,
                outputTokens,
                totalTokens,
                estimatedCostUsd,
                latencyMs,
                errorMessage,
                usageMetadata,
                startedAt,
                completedAt
        );
    }

    private record UsageValues(
            Long inputTokens,
            Long outputTokens,
            Long totalTokens,
            BigDecimal estimatedCostUsd
    ) {
        static UsageValues empty() {
            return new UsageValues(null, null, null, null);
        }
    }
}
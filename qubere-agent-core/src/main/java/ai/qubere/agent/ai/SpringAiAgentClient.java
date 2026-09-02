package ai.qubere.agent.ai;

import ai.qubere.agent.core.AgentErrorCode;
import ai.qubere.agent.core.AgentExecutionException;
import ai.qubere.agent.redaction.AgentRedactionService;
import ai.qubere.agent.redaction.DefaultAgentRedactionService;
import ai.qubere.agent.resilience.AgentResilienceGateway;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final AgentPlatformProperties properties;
    private final ModelCostBudgetTracker costBudgetTracker;
    private final AgentResilienceGateway resilienceGateway;

    public SpringAiAgentClient(ChatClient.Builder chatClientBuilder, ObjectProvider<ModelUsageRecorder> modelUsageRecorder) {
        this(chatClientBuilder, modelUsageRecorder, null, null, null, null);
    }

    @Autowired
    public SpringAiAgentClient(
            ChatClient.Builder chatClientBuilder,
            ObjectProvider<ModelUsageRecorder> modelUsageRecorder,
            ObjectProvider<AgentRedactionService> redactionService,
            ObjectProvider<AgentPlatformProperties> properties,
            ObjectProvider<ModelCostBudgetTracker> costBudgetTracker,
            ObjectProvider<AgentResilienceGateway> resilienceGateway
    ) {
        this.chatClient = chatClientBuilder.build();
        this.modelUsageRecorder = modelUsageRecorder.getIfAvailable(ModelUsageRecorder::noop);
        this.redactionService = redactionService == null
                ? new DefaultAgentRedactionService()
                : redactionService.getIfAvailable(DefaultAgentRedactionService::new);
        this.properties = properties == null ? new AgentPlatformProperties() : properties.getIfAvailable(AgentPlatformProperties::new);
        this.costBudgetTracker = costBudgetTracker == null ? new ModelCostBudgetTracker() : costBudgetTracker.getIfAvailable(ModelCostBudgetTracker::new);
        this.resilienceGateway = resilienceGateway == null ? AgentResilienceGateway.noop() : resilienceGateway.getIfAvailable(AgentResilienceGateway::noop);
    }

    @Override
    public <T> T generate(AgentPrompt prompt, Class<T> responseType) {
        return generate(prompt, responseType, AgentAiRequestMetadata.empty());
    }

    @Override
    public reactor.core.publisher.Flux<String> generateStream(AgentPrompt prompt, AgentAiRequestMetadata metadata) {
        AgentAiRequestMetadata requestMetadata = metadata == null ? AgentAiRequestMetadata.empty() : metadata;
        enforceCostBudgetBeforeCall(requestMetadata);
        String usageId = UUID.randomUUID().toString();
        Instant startedAt = Instant.now();
        Map<String, Object> usageMetadata = new LinkedHashMap<>(requestMetadata.metadata());
        usageMetadata.put("responseType", "stream");
        usageMetadata.put("streamingRequested", true);
        usageMetadata.put("temperature", requestMetadata.temperature());
        usageMetadata.put("maxOutputTokens", requestMetadata.maxOutputTokens());
        applyPromptLogging(prompt, requestMetadata, usageMetadata);

        modelUsageRecorder.record(record(
                usageId, requestMetadata, ModelUsageStatus.STARTED,
                null, null, null, null, null, null, usageMetadata, startedAt, null
        ));

        ChatClient.ChatClientRequestSpec request = chatClient.prompt()
                .system(prompt.system())
                .user(prompt.user());
        ChatClient.ChatClientRequestSpec finalRequest = applyOptions(request, requestMetadata, usageMetadata);
        String resilienceKey = "ai:" + (requestMetadata.modelName() == null ? "default" : requestMetadata.modelName());

        return resilienceGateway.execute(resilienceKey, () -> finalRequest.stream().content())
                .doOnComplete(() -> modelUsageRecorder.record(record(
                        usageId, requestMetadata, ModelUsageStatus.SUCCEEDED,
                        null, null, null, null,
                        Duration.between(startedAt, Instant.now()).toMillis(), null, usageMetadata, startedAt, Instant.now()
                )))
                .doOnError(ex -> modelUsageRecorder.record(record(
                        usageId, requestMetadata, ModelUsageStatus.FAILED,
                        null, null, null, null,
                        Duration.between(startedAt, Instant.now()).toMillis(), ex.getMessage(), usageMetadata, startedAt, Instant.now()
                )));
    }

    @Override
    public <T> T generate(AgentPrompt prompt, Class<T> responseType, AgentAiRequestMetadata metadata) {
        AgentAiRequestMetadata requestMetadata = metadata == null ? AgentAiRequestMetadata.empty() : metadata;
        enforceCostBudgetBeforeCall(requestMetadata);
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
            ChatClient.ChatClientRequestSpec finalRequest = applyOptions(request, requestMetadata, usageMetadata);

            String resilienceKey = "ai:" + (requestMetadata.modelName() == null ? "default" : requestMetadata.modelName());
            ResponseEntity<ChatResponse, T> responseEntity = resilienceGateway.execute(
                    resilienceKey,
                    () -> finalRequest.call().responseEntity(responseType)
            );
            Instant completedAt = Instant.now();
            UsageValues usageValues = extractUsage(responseEntity.response(), usageMetadata, requestMetadata);
            if (usageValues.estimatedCostUsd() != null) {
                costBudgetTracker.charge(requestMetadata.executionId(), usageValues.estimatedCostUsd());
            }
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

    /**
     * Enforces the hard cost budget before issuing a model call. Each agent execution may call
     * the model multiple times; the cap resolved onto {@code ResolvedAgentPolicy.maxEstimatedCostUsd()}
     * applies to the cumulative spend across all calls in that execution, tracked by
     * {@link ModelCostBudgetTracker}. A cap of {@code null} or non-positive disables enforcement.
     */
    private void enforceCostBudgetBeforeCall(AgentAiRequestMetadata requestMetadata) {
        BigDecimal cap = requestMetadata.maxEstimatedCostUsd();
        if (cap == null || cap.signum() <= 0) {
            return;
        }
        BigDecimal spentSoFar = costBudgetTracker.currentSpend(requestMetadata.executionId());
        if (spentSoFar.compareTo(cap) >= 0) {
            throw new AgentExecutionException(
                    AgentErrorCode.GOVERNANCE_LIMIT_EXCEEDED,
                    "Model call rejected: estimated spend %s USD already at or above configured maxEstimatedCostUsd %s USD for execution %s"
                            .formatted(spentSoFar.stripTrailingZeros().toPlainString(), cap.stripTrailingZeros().toPlainString(), requestMetadata.executionId())
            );
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

    private UsageValues extractUsage(ChatResponse response, Map<String, Object> usageMetadata, AgentAiRequestMetadata requestMetadata) {
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
        BigDecimal estimatedCostUsd = estimateCost(requestMetadata.modelName(), inputTokens, outputTokens);
        return new UsageValues(inputTokens, outputTokens, totalTokens, estimatedCostUsd);
    }

    /**
     * Computes estimated cost from the configured {@code agent-platform.ai.tariffs.<model>}
     * pricing. Returns {@code null} when the model has no configured tariff, matching prior
     * behavior of leaving {@code estimatedCostUsd} unset until tariffs are configured.
     */
    private BigDecimal estimateCost(String modelName, Long inputTokens, Long outputTokens) {
        if (modelName == null) {
            return null;
        }
        AgentPlatformProperties.ModelTariff tariff = properties.getAi().getTariffs().get(modelName);
        if (tariff == null) {
            return null;
        }
        BigDecimal inputCost = costFor(inputTokens, tariff.getInputCostUsdPerThousandTokens());
        BigDecimal outputCost = costFor(outputTokens, tariff.getOutputCostUsdPerThousandTokens());
        if (inputCost == null && outputCost == null) {
            return null;
        }
        return (inputCost == null ? BigDecimal.ZERO : inputCost).add(outputCost == null ? BigDecimal.ZERO : outputCost);
    }

    private BigDecimal costFor(Long tokens, BigDecimal costPerThousand) {
        if (tokens == null || costPerThousand == null || costPerThousand.signum() <= 0) {
            return null;
        }
        return BigDecimal.valueOf(tokens)
                .divide(BigDecimal.valueOf(1000), 8, RoundingMode.HALF_UP)
                .multiply(costPerThousand);
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
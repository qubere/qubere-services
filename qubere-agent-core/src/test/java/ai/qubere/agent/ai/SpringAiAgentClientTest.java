package ai.qubere.agent.ai;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiAgentClientTest {

    @Test
    void passesResolvedProviderOptionsAndRecordsProviderUsageMetadata() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        ModelUsageRecorder recorder = mock(ModelUsageRecorder.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ModelUsageRecorder> recorderProvider = mock(ObjectProvider.class);

        when(builder.build()).thenReturn(chatClient);
        when(recorderProvider.getIfAvailable(any())).thenReturn(recorder);
        when(chatClient.prompt()).thenReturn(request);
        when(request.system("system prompt")).thenReturn(request);
        when(request.user("user prompt")).thenReturn(request);
        when(request.options(any(ChatOptions.Builder.class))).thenReturn(request);
        when(request.call()).thenReturn(call);

        ChatResponse chatResponse = ChatResponse.builder()
                .metadata(ChatResponseMetadata.builder()
                        .model("provider-model-id")
                        .usage(new DefaultUsage(12, 7, 19))
                        .build())
                .generations(List.of())
                .build();
        when(call.responseEntity(String.class)).thenReturn(new ResponseEntity<>(chatResponse, "ok"));

        SpringAiAgentClient client = new SpringAiAgentClient(builder, recorderProvider);

        String response = client.generate(
                new AgentPrompt("system prompt", "user prompt", Map.of()),
                String.class,
                new AgentAiRequestMetadata(
                        "exec-1",
                        "tenant-1",
                        "actor-1",
                        "corr-1",
                        "agent-1",
                        "0.1.0",
                        "openai",
                        "gpt-test",
                        "prompt-v1",
                        true,
                        0.35d,
                        512,
                        true,
                        false,
                        null,
                        Map.of("source", "test")
                )
        );

        assertThat(response).isEqualTo("ok");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ChatOptions.Builder<?>> optionsCaptor = ArgumentCaptor.forClass((Class) ChatOptions.Builder.class);
        verify(request).options(optionsCaptor.capture());
        ChatOptions appliedOptions = optionsCaptor.getValue().build();
        assertThat(appliedOptions.getModel()).isEqualTo("gpt-test");
        assertThat(appliedOptions.getTemperature()).isEqualTo(0.35d);
        assertThat(appliedOptions.getMaxTokens()).isEqualTo(512);

        ArgumentCaptor<ModelUsageRecord> usageCaptor = ArgumentCaptor.forClass(ModelUsageRecord.class);
        verify(recorder, times(2)).record(usageCaptor.capture());
        ModelUsageRecord succeeded = usageCaptor.getAllValues().get(1);
        assertThat(succeeded.status()).isEqualTo(ModelUsageStatus.SUCCEEDED);
        assertThat(succeeded.inputTokens()).isEqualTo(12L);
        assertThat(succeeded.outputTokens()).isEqualTo(7L);
        assertThat(succeeded.totalTokens()).isEqualTo(19L);
        assertThat(succeeded.metadata())
                .containsEntry("source", "test")
                .containsEntry("responseType", String.class.getName())
                .containsEntry("streamingRequested", true)
                .containsEntry("streamingApplied", false)
                .containsEntry("temperature", 0.35d)
                .containsEntry("maxOutputTokens", 512)
                .containsEntry("providerModel", "provider-model-id")
                .containsEntry("promptLoggingEnabled", true)
                .containsEntry("systemPrompt", "system prompt")
                .containsEntry("userPrompt", "user prompt");
    }

    @Test
    void computesEstimatedCostFromConfiguredTariff() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        ModelUsageRecorder recorder = mock(ModelUsageRecorder.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ModelUsageRecorder> recorderProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ai.qubere.agent.redaction.AgentRedactionService> redactionProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ai.qubere.agent.runtime.config.AgentPlatformProperties> propertiesProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ModelCostBudgetTracker> trackerProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ai.qubere.agent.resilience.AgentResilienceGateway> resilienceProvider = mock(ObjectProvider.class);

        ai.qubere.agent.runtime.config.AgentPlatformProperties properties = new ai.qubere.agent.runtime.config.AgentPlatformProperties();
        ai.qubere.agent.runtime.config.AgentPlatformProperties.ModelTariff tariff = new ai.qubere.agent.runtime.config.AgentPlatformProperties.ModelTariff();
        tariff.setInputCostUsdPerThousandTokens(new java.math.BigDecimal("0.01"));
        tariff.setOutputCostUsdPerThousandTokens(new java.math.BigDecimal("0.02"));
        properties.getAi().getTariffs().put("gpt-test", tariff);
        ModelCostBudgetTracker tracker = new ModelCostBudgetTracker();

        when(builder.build()).thenReturn(chatClient);
        when(recorderProvider.getIfAvailable(any())).thenReturn(recorder);
        when(redactionProvider.getIfAvailable(any())).thenReturn(new ai.qubere.agent.redaction.DefaultAgentRedactionService());
        when(propertiesProvider.getIfAvailable(any())).thenReturn(properties);
        when(trackerProvider.getIfAvailable(any())).thenReturn(tracker);
        when(resilienceProvider.getIfAvailable(any())).thenReturn(ai.qubere.agent.resilience.AgentResilienceGateway.noop());
        when(chatClient.prompt()).thenReturn(request);
        when(request.system(anyString())).thenReturn(request);
        when(request.user(anyString())).thenReturn(request);
        when(request.options(any(ChatOptions.Builder.class))).thenReturn(request);
        when(request.call()).thenReturn(call);

        ChatResponse chatResponse = ChatResponse.builder()
                .metadata(ChatResponseMetadata.builder()
                        .model("provider-model-id")
                        .usage(new DefaultUsage(1000, 500, 1500))
                        .build())
                .generations(List.of())
                .build();
        when(call.responseEntity(String.class)).thenReturn(new ResponseEntity<>(chatResponse, "ok"));

        SpringAiAgentClient client = new SpringAiAgentClient(builder, recorderProvider, redactionProvider, propertiesProvider, trackerProvider, resilienceProvider);

        client.generate(
                new AgentPrompt("system prompt", "user prompt", Map.of()),
                String.class,
                new AgentAiRequestMetadata(
                        "exec-cost", "tenant-1", "actor-1", "corr-1", "agent-1", "0.1.0",
                        "openai", "gpt-test", "prompt-v1", null, null, null, false, false, null, Map.of()
                )
        );

        // 1000 input tokens @ 0.01/1k = 0.01 USD; 500 output tokens @ 0.02/1k = 0.01 USD; total 0.02 USD.
        assertThat(tracker.currentSpend("exec-cost")).isEqualByComparingTo("0.02");

        ArgumentCaptor<ModelUsageRecord> usageCaptor = ArgumentCaptor.forClass(ModelUsageRecord.class);
        verify(recorder, times(2)).record(usageCaptor.capture());
        ModelUsageRecord succeeded = usageCaptor.getAllValues().get(1);
        assertThat(succeeded.estimatedCostUsd()).isEqualByComparingTo("0.02");
    }

    @Test
    void rejectsModelCallWhenCostBudgetAlreadyExhausted() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ModelUsageRecorder> recorderProvider = mock(ObjectProvider.class);
        when(recorderProvider.getIfAvailable(any())).thenReturn(ModelUsageRecorder.noop());
        when(builder.build()).thenReturn(chatClient);

        ModelCostBudgetTracker tracker = new ModelCostBudgetTracker();
        tracker.charge("exec-exhausted", new java.math.BigDecimal("1.00"));

        @SuppressWarnings("unchecked")
        ObjectProvider<ai.qubere.agent.redaction.AgentRedactionService> redactionProvider = mock(ObjectProvider.class);
        when(redactionProvider.getIfAvailable(any())).thenReturn(new ai.qubere.agent.redaction.DefaultAgentRedactionService());
        @SuppressWarnings("unchecked")
        ObjectProvider<ai.qubere.agent.runtime.config.AgentPlatformProperties> propertiesProvider = mock(ObjectProvider.class);
        when(propertiesProvider.getIfAvailable(any())).thenReturn(new ai.qubere.agent.runtime.config.AgentPlatformProperties());
        @SuppressWarnings("unchecked")
        ObjectProvider<ModelCostBudgetTracker> trackerProvider = mock(ObjectProvider.class);
        when(trackerProvider.getIfAvailable(any())).thenReturn(tracker);
        @SuppressWarnings("unchecked")
        ObjectProvider<ai.qubere.agent.resilience.AgentResilienceGateway> resilienceProvider = mock(ObjectProvider.class);
        when(resilienceProvider.getIfAvailable(any())).thenReturn(ai.qubere.agent.resilience.AgentResilienceGateway.noop());

        SpringAiAgentClient client = new SpringAiAgentClient(builder, recorderProvider, redactionProvider, propertiesProvider, trackerProvider, resilienceProvider);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.generate(
                new AgentPrompt("system", "user", Map.of()),
                String.class,
                new AgentAiRequestMetadata(
                        "exec-exhausted", "tenant-1", "actor-1", "corr-1", "agent-1", "0.1.0",
                        "openai", "gpt-test", "prompt-v1", null, null, null, false, false,
                        new java.math.BigDecimal("1.00"), Map.of()
                )
        )).isInstanceOfSatisfying(ai.qubere.agent.core.AgentExecutionException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ai.qubere.agent.core.AgentErrorCode.GOVERNANCE_LIMIT_EXCEEDED));
    }

    @Test
    void streamsTextChunksAndRecordsUsageOnCompletion() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamSpec = mock(ChatClient.StreamResponseSpec.class);
        ModelUsageRecorder recorder = mock(ModelUsageRecorder.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ModelUsageRecorder> recorderProvider = mock(ObjectProvider.class);
        when(recorderProvider.getIfAvailable(any())).thenReturn(recorder);

        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(request);
        when(request.system(anyString())).thenReturn(request);
        when(request.user(anyString())).thenReturn(request);
        when(request.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(reactor.core.publisher.Flux.just("Hello", " world"));

        SpringAiAgentClient client = new SpringAiAgentClient(builder, recorderProvider);

        java.util.List<String> chunks = client.generateStream(
                new AgentPrompt("system", "user", Map.of()),
                AgentAiRequestMetadata.empty()
        ).collectList().block();

        assertThat(chunks).containsExactly("Hello", " world");

        ArgumentCaptor<ModelUsageRecord> usageCaptor = ArgumentCaptor.forClass(ModelUsageRecord.class);
        verify(recorder, times(2)).record(usageCaptor.capture());
        assertThat(usageCaptor.getAllValues().get(0).status()).isEqualTo(ModelUsageStatus.STARTED);
        assertThat(usageCaptor.getAllValues().get(1).status()).isEqualTo(ModelUsageStatus.SUCCEEDED);
    }

    @Test
    void streamingRecordsFailureWhenUpstreamErrors() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamSpec = mock(ChatClient.StreamResponseSpec.class);
        ModelUsageRecorder recorder = mock(ModelUsageRecorder.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ModelUsageRecorder> recorderProvider = mock(ObjectProvider.class);
        when(recorderProvider.getIfAvailable(any())).thenReturn(recorder);

        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(request);
        when(request.system(anyString())).thenReturn(request);
        when(request.user(anyString())).thenReturn(request);
        when(request.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(reactor.core.publisher.Flux.error(new RuntimeException("provider failure")));

        SpringAiAgentClient client = new SpringAiAgentClient(builder, recorderProvider);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.generateStream(
                new AgentPrompt("system", "user", Map.of()),
                AgentAiRequestMetadata.empty()
        ).collectList().block()).isInstanceOf(RuntimeException.class);

        ArgumentCaptor<ModelUsageRecord> usageCaptor = ArgumentCaptor.forClass(ModelUsageRecord.class);
        verify(recorder, times(2)).record(usageCaptor.capture());
        assertThat(usageCaptor.getAllValues().get(1).status()).isEqualTo(ModelUsageStatus.FAILED);
    }
}
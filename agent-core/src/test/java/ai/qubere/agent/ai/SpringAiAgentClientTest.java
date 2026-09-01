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
}
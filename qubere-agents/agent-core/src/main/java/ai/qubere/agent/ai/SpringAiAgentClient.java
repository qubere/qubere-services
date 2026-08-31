package ai.qubere.agent.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "agent-platform.ai.spring", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SpringAiAgentClient implements AgentAiClient {

    private final ChatClient chatClient;

    public SpringAiAgentClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public <T> T generate(AgentPrompt prompt, Class<T> responseType) {
        return chatClient.prompt()
                .system(prompt.system())
                .user(prompt.user())
                .call()
                .entity(responseType);
    }
}

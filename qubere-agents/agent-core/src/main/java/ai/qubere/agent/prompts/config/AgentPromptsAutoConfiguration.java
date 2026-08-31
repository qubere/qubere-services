package ai.qubere.agent.prompts.config;

import ai.qubere.agent.prompts.InMemoryPromptVersionStore;
import ai.qubere.agent.prompts.PromptVersionStore;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class AgentPromptsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    PromptVersionStore promptVersionStore() {
        return new InMemoryPromptVersionStore();
    }
}

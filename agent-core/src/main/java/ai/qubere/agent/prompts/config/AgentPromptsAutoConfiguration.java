package ai.qubere.agent.prompts.config;

import ai.qubere.agent.prompts.InMemoryPromptVersionStore;
import ai.qubere.agent.prompts.PromptSeedRunner;
import ai.qubere.agent.prompts.PromptVersionStore;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;

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

    @Bean
    @ConditionalOnMissingBean
    PromptSeedRunner promptSeedRunner(AgentPlatformProperties properties, PromptVersionStore promptVersionStore) {
        return new PromptSeedRunner(properties, promptVersionStore);
    }
}

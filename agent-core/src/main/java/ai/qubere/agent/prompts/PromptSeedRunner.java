package ai.qubere.agent.prompts;

import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import java.time.Instant;
import java.util.Locale;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

public class PromptSeedRunner implements ApplicationRunner {

    private final AgentPlatformProperties properties;
    private final PromptVersionStore promptVersionStore;

    public PromptSeedRunner(AgentPlatformProperties properties, PromptVersionStore promptVersionStore) {
        this.properties = properties;
        this.promptVersionStore = promptVersionStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        AgentPlatformProperties.Prompts prompts = properties.getPrompts();
        if (!prompts.isSeedEnabled()) {
            return;
        }
        prompts.getSeeds().forEach(this::seedPrompt);
    }

    private void seedPrompt(AgentPlatformProperties.Prompts.PromptSeed seed) {
        validate(seed);
        boolean exists = promptVersionStore.find(seed.getPromptId(), seed.getVersion()).isPresent();
        if (exists && !seed.isOverwrite()) {
            return;
        }
        Instant now = Instant.now();
        promptVersionStore.save(new PromptTemplate(
                seed.getPromptId(),
                seed.getAgentId(),
                seed.getVersion(),
                status(seed.getStatus()),
                seed.getSystemTemplate(),
                seed.getUserTemplate(),
                seed.getMetadata(),
                now,
                now
        ));
    }

    private void validate(AgentPlatformProperties.Prompts.PromptSeed seed) {
        if (seed == null) {
            throw new IllegalArgumentException("Prompt seed is required");
        }
        requireText("promptId", seed.getPromptId());
        requireText("agentId", seed.getAgentId());
        requireText("version", seed.getVersion());
    }

    private void requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Prompt seed " + fieldName + " is required");
        }
    }

    private PromptStatus status(String value) {
        if (value == null || value.isBlank()) {
            return PromptStatus.DRAFT;
        }
        return PromptStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}

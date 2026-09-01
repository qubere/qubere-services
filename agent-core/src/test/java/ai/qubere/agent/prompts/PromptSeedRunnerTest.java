package ai.qubere.agent.prompts;

import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptSeedRunnerTest {

    @Test
    void seedsConfiguredPromptWhenMissing() {
        AgentPlatformProperties properties = new AgentPlatformProperties();
        AgentPlatformProperties.Prompts.PromptSeed seed = new AgentPlatformProperties.Prompts.PromptSeed();
        seed.setPromptId("prompt.echo");
        seed.setAgentId("generic.echo-analysis");
        seed.setVersion("0.1.0");
        seed.setStatus("ACTIVE");
        seed.setSystemTemplate("system");
        seed.setUserTemplate("user {{message}}");
        seed.setMetadata(Map.of("source", "test"));
        properties.getPrompts().setSeeds(java.util.List.of(seed));

        InMemoryPromptVersionStore store = new InMemoryPromptVersionStore();
        new PromptSeedRunner(properties, store).run(null);

        PromptTemplate saved = store.find("prompt.echo", "0.1.0").orElseThrow();
        assertThat(saved.agentId()).isEqualTo("generic.echo-analysis");
        assertThat(saved.status()).isEqualTo(PromptStatus.ACTIVE);
        assertThat(saved.metadata()).containsEntry("source", "test");
    }

    @Test
    void doesNotOverwriteExistingPromptUnlessRequested() {
        AgentPlatformProperties properties = new AgentPlatformProperties();
        AgentPlatformProperties.Prompts.PromptSeed seed = new AgentPlatformProperties.Prompts.PromptSeed();
        seed.setPromptId("prompt.echo");
        seed.setAgentId("generic.echo-analysis");
        seed.setVersion("0.1.0");
        seed.setSystemTemplate("new system");
        properties.getPrompts().setSeeds(java.util.List.of(seed));

        InMemoryPromptVersionStore store = new InMemoryPromptVersionStore();
        store.save(new PromptTemplate("prompt.echo", "generic.echo-analysis", "0.1.0", PromptStatus.ACTIVE, "old system", "old user", Map.of(), null, null));
        new PromptSeedRunner(properties, store).run(null);

        assertThat(store.find("prompt.echo", "0.1.0").orElseThrow().systemTemplate()).isEqualTo("old system");
    }
}

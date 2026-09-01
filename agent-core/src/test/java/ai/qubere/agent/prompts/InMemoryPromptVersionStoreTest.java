package ai.qubere.agent.prompts;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryPromptVersionStoreTest {

    @Test
    void storesPromptVersionsAndFindsActivePromptForAgent() {
        InMemoryPromptVersionStore store = new InMemoryPromptVersionStore();
        store.save(prompt("prompt", "agent", "1.0.0", PromptStatus.DEPRECATED));
        store.save(prompt("prompt", "agent", "1.1.0", PromptStatus.ACTIVE));

        assertThat(store.find("prompt", "1.0.0")).isPresent();
        assertThat(store.findActiveForAgent("agent"))
                .isPresent()
                .get()
                .extracting(PromptTemplate::version)
                .isEqualTo("1.1.0");
    }

    private PromptTemplate prompt(String promptId, String agentId, String version, PromptStatus status) {
        return new PromptTemplate(
                promptId,
                agentId,
                version,
                status,
                "system",
                "user",
                Map.of(),
                null,
                null
        );
    }
}

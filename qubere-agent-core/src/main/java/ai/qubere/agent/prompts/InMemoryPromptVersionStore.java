package ai.qubere.agent.prompts;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPromptVersionStore implements PromptVersionStore {

    private final Map<String, PromptTemplate> promptsByIdAndVersion = new ConcurrentHashMap<>();

    @Override
    public PromptTemplate save(PromptTemplate template) {
        validate(template);
        promptsByIdAndVersion.put(key(template.promptId(), template.version()), template);
        return template;
    }

    @Override
    public Optional<PromptTemplate> find(String promptId, String version) {
        return Optional.ofNullable(promptsByIdAndVersion.get(key(promptId, version)));
    }

    @Override
    public Optional<PromptTemplate> findActiveForAgent(String agentId) {
        return promptsByIdAndVersion.values().stream()
                .filter(prompt -> agentId.equals(prompt.agentId()))
                .filter(prompt -> prompt.status() == PromptStatus.ACTIVE)
                .max(Comparator.comparing(PromptTemplate::version));
    }

    @Override
    public Collection<PromptTemplate> listForAgent(String agentId) {
        Map<String, PromptTemplate> ordered = new LinkedHashMap<>();
        promptsByIdAndVersion.values().stream()
                .filter(prompt -> agentId.equals(prompt.agentId()))
                .sorted(Comparator.comparing(PromptTemplate::version))
                .forEach(prompt -> ordered.put(key(prompt.promptId(), prompt.version()), prompt));
        return ordered.values();
    }

    private void validate(PromptTemplate template) {
        if (template == null) {
            throw new IllegalArgumentException("Prompt template is required");
        }
        requireText("promptId", template.promptId());
        requireText("agentId", template.agentId());
        requireText("version", template.version());
    }

    private void requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    private String key(String promptId, String version) {
        return "%s::%s".formatted(promptId, version);
    }
}

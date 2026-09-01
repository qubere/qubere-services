package ai.qubere.agent.redaction;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAgentRedactionServiceTest {

    private final DefaultAgentRedactionService redactionService = new DefaultAgentRedactionService();

    @Test
    void redactsSensitiveMapKeysRecursively() {
        Map<String, Object> redacted = redactionService.redactMap(Map.of(
                "message", "hello",
                "password", "secret-value",
                "nested", Map.of("apiKey", "abc123", "safe", "visible")
        ));

        assertThat(redacted)
                .containsEntry("message", "hello")
                .containsEntry("password", DefaultAgentRedactionService.REDACTED);
        assertThat(redacted.get("nested").toString())
                .contains("apiKey=[REDACTED]")
                .contains("safe=visible");
    }

    @Test
    void redactsCommonSecretTextPatterns() {
        String redacted = redactionService.redactText("Authorization: Bearer abcdefghijklmnop password=my-secret");

        assertThat(redacted)
                .contains("Authorization=[REDACTED]")
                .contains("password=[REDACTED]")
                .doesNotContain("Bearer abcdefghijklmnop")
                .doesNotContain("my-secret");
    }
}
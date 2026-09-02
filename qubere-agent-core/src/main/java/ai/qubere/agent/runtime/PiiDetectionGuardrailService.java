package ai.qubere.agent.runtime;

import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentInput;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Composable input guardrail that blocks input containing common PII patterns: email addresses,
 * US Social Security Numbers, and credit-card-like digit sequences. This is a baseline heuristic
 * guardrail, not a complete PII detection solution (it will not catch every PII format, and may
 * produce false positives on numeric input that happens to match a pattern). Applications with
 * stricter compliance requirements should replace or extend this with a dedicated PII detection
 * service.
 * <p>
 * Not registered by default; compose it explicitly with {@link CompositeAgentGuardrailService}
 * when an agent's input should never contain PII, for example:
 * <pre>{@code
 * @Bean
 * AgentGuardrailService agentGuardrailService(AgentPlatformProperties properties) {
 *     return new CompositeAgentGuardrailService(List.of(
 *         new DefaultAgentGuardrailService(properties),
 *         new PiiDetectionGuardrailService(properties)
 *     ));
 * }
 * }</pre>
 */
public class PiiDetectionGuardrailService implements AgentGuardrailService {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern SSN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern CREDIT_CARD = Pattern.compile("\\b(?:\\d[ -]?){13,16}\\b");

    private final AgentPlatformProperties properties;
    private final ObjectMapper objectMapper;

    public PiiDetectionGuardrailService(AgentPlatformProperties properties) {
        this(properties, new ObjectMapper());
    }

    public PiiDetectionGuardrailService(AgentPlatformProperties properties, ObjectMapper objectMapper) {
        this.properties = properties == null ? new AgentPlatformProperties() : properties;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    @Override
    public GuardrailDecision evaluateBeforeRun(AgentExecutionContext context, AgentDescriptor descriptor, AgentInput input) {
        if (!properties.getGuardrails().isEnabled() || input == null) {
            return GuardrailDecision.allow();
        }
        String serialized = serialize(input);
        if (EMAIL.matcher(serialized).find()) {
            return GuardrailDecision.block("Agent input appears to contain an email address and was blocked by the PII detection guardrail");
        }
        if (SSN.matcher(serialized).find()) {
            return GuardrailDecision.block("Agent input appears to contain a Social Security Number and was blocked by the PII detection guardrail");
        }
        if (CREDIT_CARD.matcher(serialized).find()) {
            return GuardrailDecision.block("Agent input appears to contain a credit-card-like number and was blocked by the PII detection guardrail");
        }
        return GuardrailDecision.allow();
    }

    private String serialize(AgentInput input) {
        try {
            return objectMapper.writeValueAsString(input);
        } catch (Exception ex) {
            return String.valueOf(input);
        }
    }
}

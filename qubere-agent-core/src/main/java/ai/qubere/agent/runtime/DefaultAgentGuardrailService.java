package ai.qubere.agent.runtime;

import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentInput;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Baseline input guardrail shipped as the framework default. It is intentionally conservative
 * and cheap to run for every agent invocation:
 * <ul>
 *   <li>Rejects a {@code null} input.</li>
 *   <li>Rejects input whose serialized size exceeds {@code agent-platform.guardrails.max-input-size-bytes}.</li>
 *   <li>Rejects input containing a configured denylist pattern commonly associated with
 *       prompt-injection attempts (for example, "ignore previous instructions").</li>
 * </ul>
 * This is a baseline, not a complete prompt-injection defense. Deployed applications with
 * higher-risk agents should compose additional guardrails (PII detection, schema conformance,
 * business-rule checks) on top of this default, or replace it entirely with a
 * {@code @Bean AgentGuardrailService} of their own.
 */
public class DefaultAgentGuardrailService implements AgentGuardrailService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgentGuardrailService.class);

    private final AgentPlatformProperties properties;
    private final ObjectMapper objectMapper;
    private final List<Pattern> denylistPatterns;

    public DefaultAgentGuardrailService(AgentPlatformProperties properties) {
        this(properties, new ObjectMapper());
    }

    public DefaultAgentGuardrailService(AgentPlatformProperties properties, ObjectMapper objectMapper) {
        this.properties = properties == null ? new AgentPlatformProperties() : properties;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.denylistPatterns = compilePatterns(this.properties.getGuardrails().getDenylistPatterns());
    }

    @Override
    public GuardrailDecision evaluateBeforeRun(AgentExecutionContext context, AgentDescriptor descriptor, AgentInput input) {
        if (!properties.getGuardrails().isEnabled()) {
            return GuardrailDecision.allow();
        }
        if (input == null) {
            return GuardrailDecision.block("Agent input must not be null");
        }
        String serialized = serialize(input);
        int maxBytes = properties.getGuardrails().getMaxInputSizeBytes();
        if (maxBytes > 0) {
            int actualBytes = serialized.getBytes(StandardCharsets.UTF_8).length;
            if (actualBytes > maxBytes) {
                return GuardrailDecision.block(
                        "Agent input size " + actualBytes + " bytes exceeds configured limit of " + maxBytes + " bytes"
                );
            }
        }
        for (Pattern pattern : denylistPatterns) {
            if (pattern.matcher(serialized).find()) {
                return GuardrailDecision.block(
                        "Agent input matched a denylisted prompt-injection pattern and was blocked before model invocation"
                );
            }
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

    private List<Pattern> compilePatterns(Iterable<String> patterns) {
        return java.util.stream.StreamSupport.stream(patterns.spliterator(), false)
                .map(this::safeCompile)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private Pattern safeCompile(String pattern) {
        try {
            return Pattern.compile(pattern);
        } catch (PatternSyntaxException ex) {
            log.warn("Ignoring invalid guardrail denylist pattern '{}': {}", pattern, ex.getMessage());
            return null;
        }
    }
}

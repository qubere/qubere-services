package ai.qubere.agent.redaction;

import java.util.Map;

public interface AgentRedactionService {

    Object redact(Object value);

    @SuppressWarnings("unchecked")
    default Map<String, Object> redactMap(Map<String, Object> values) {
        Object redacted = redact(values);
        return redacted instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    default String redactText(String value) {
        Object redacted = redact(value);
        return redacted == null ? null : redacted.toString();
    }
}
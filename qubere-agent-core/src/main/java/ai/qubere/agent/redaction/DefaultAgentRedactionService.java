package ai.qubere.agent.redaction;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class DefaultAgentRedactionService implements AgentRedactionService {

    public static final String REDACTED = "[REDACTED]";

    private static final Set<String> SENSITIVE_KEY_FRAGMENTS = Set.of(
            "password",
            "passwd",
            "pwd",
            "secret",
            "token",
            "apikey",
            "api_key",
            "authorization",
            "credential",
            "privatekey",
            "private_key"
    );

    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/=-]{8,}");
    private static final Pattern ASSIGNMENT_SECRET = Pattern.compile("(?i)(password|passwd|pwd|secret|token|api[_-]?key|authorization)\\s*[:=]\\s*[^\\s,;}{]+", Pattern.CASE_INSENSITIVE);

    @Override
    public Object redact(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence text) {
            return redactString(text.toString());
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> redacted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                redacted.put(key, isSensitiveKey(key) ? REDACTED : redact(entry.getValue()));
            }
            return redacted;
        }
        if (value instanceof Collection<?> collection) {
            ArrayList<Object> redacted = new ArrayList<>(collection.size());
            for (Object item : collection) {
                redacted.add(redact(item));
            }
            return redacted;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            ArrayList<Object> redacted = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                redacted.add(redact(Array.get(value, i)));
            }
            return redacted;
        }
        return value;
    }

    private String redactString(String value) {
        String redacted = BEARER_TOKEN.matcher(value).replaceAll("Bearer " + REDACTED);
        return ASSIGNMENT_SECRET.matcher(redacted).replaceAll(match -> match.group(1) + "=" + REDACTED);
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key == null ? "" : key.replaceAll("[^A-Za-z0-9_]", "").toLowerCase(Locale.ROOT);
        for (String sensitive : SENSITIVE_KEY_FRAGMENTS) {
            if (normalized.contains(sensitive)) {
                return true;
            }
        }
        return false;
    }
}
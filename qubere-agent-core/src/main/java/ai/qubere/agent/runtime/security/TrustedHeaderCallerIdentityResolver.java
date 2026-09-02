package ai.qubere.agent.runtime.security;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Local-development-only resolver that trusts {@code X-Tenant-Id}, {@code X-Actor-Id}, and
 * {@code X-Agent-Permissions} inbound headers directly as caller identity.
 * <p>
 * <b>This resolver must never be used in production.</b> Any caller that can reach the HTTP
 * endpoint can set these headers to impersonate any tenant or actor. It exists only so local
 * development and CI can exercise multi-tenant behavior without standing up a real identity
 * provider. It is auto-registered only when
 * {@code agent-platform.security.authorization-mode=permissive} (the default) and
 * {@code agent-platform.security.trust-inbound-headers} is not explicitly set to {@code false}.
 */
public class TrustedHeaderCallerIdentityResolver implements AgentCallerIdentityResolver {

    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    public static final String HEADER_ACTOR_ID = "X-Actor-Id";
    public static final String HEADER_PERMISSIONS = "X-Agent-Permissions";

    @Override
    public AgentCallerIdentity resolve(Map<String, String> headers) {
        Map<String, String> safeHeaders = headers == null ? Map.of() : headers;
        String tenantId = safeHeaders.get(HEADER_TENANT_ID);
        String actorId = safeHeaders.get(HEADER_ACTOR_ID);
        Set<String> permissions = parseCsv(safeHeaders.get(HEADER_PERMISSIONS));
        return new AgentCallerIdentity(tenantId, actorId, permissions, false);
    }

    private Set<String> parseCsv(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String token : value.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }
}

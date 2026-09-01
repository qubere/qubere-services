package ai.qubere.agent.runtime;

import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class ReferenceAgentAuthorizationService implements AgentAuthorizationService {

    public static final String MODE_PERMISSIVE = "permissive";
    public static final String MODE_STRICT = "strict";

    private final AgentPlatformProperties.Security security;

    public ReferenceAgentAuthorizationService(AgentPlatformProperties properties) {
        this.security = properties == null ? new AgentPlatformProperties.Security() : properties.getSecurity();
    }

    @Override
    public boolean canRun(AgentExecutionContext context, AgentDescriptor descriptor) {
        if (!MODE_STRICT.equalsIgnoreCase(security.getAuthorizationMode())) {
            return true;
        }
        if (context == null || descriptor == null) {
            return false;
        }
        if (security.isRequireTenant() && isBlank(context.tenantId())) {
            return false;
        }
        if (security.isRequireActor() && isBlank(context.actorId())) {
            return false;
        }
        if (!security.getAllowedTenants().isEmpty() && !security.getAllowedTenants().contains(context.tenantId())) {
            return false;
        }
        Set<String> requiredPermissions = new LinkedHashSet<>(security.getRequiredRunPermissions());
        requiredPermissions.addAll(security.getAgentRequiredPermissions().getOrDefault(descriptor.id(), Set.of()));
        if (!requiredPermissions.isEmpty() && !permissions(context).containsAll(requiredPermissions)) {
            return false;
        }
        return true;
    }

    private Set<String> permissions(AgentExecutionContext context) {
        Object value = context.attributes().get("permissions");
        if (value instanceof Set<?> set) {
            return toStringSet(set);
        }
        if (value instanceof Iterable<?> iterable) {
            Set<String> permissions = new LinkedHashSet<>();
            iterable.forEach(item -> addPermission(permissions, item));
            return permissions;
        }
        if (value instanceof String text) {
            return parsePermissions(text);
        }
        return Set.of();
    }

    private static Set<String> parsePermissions(String text) {
        if (isBlank(text)) {
            return Set.of();
        }
        Set<String> permissions = new LinkedHashSet<>();
        for (String token : text.split(",")) {
            addPermission(permissions, token);
        }
        return permissions;
    }

    private static Set<String> toStringSet(Set<?> values) {
        Set<String> result = new LinkedHashSet<>();
        values.forEach(value -> addPermission(result, value));
        return result;
    }

    private static void addPermission(Set<String> permissions, Object value) {
        if (value == null) {
            return;
        }
        String permission = value.toString().trim();
        if (!permission.isEmpty()) {
            permissions.add(permission);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

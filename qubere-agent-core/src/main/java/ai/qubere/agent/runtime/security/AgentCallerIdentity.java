package ai.qubere.agent.runtime.security;

import java.util.Set;

/**
 * Caller identity resolved for an inbound agent run request. This is the single source of
 * truth for tenant/actor/permission context used to build {@link ai.qubere.agent.api.AgentExecutionContext}.
 * <p>
 * Instances must only be produced by a trusted {@link AgentCallerIdentityResolver}. Application
 * code must never construct this record directly from unauthenticated request data.
 */
public record AgentCallerIdentity(
        String tenantId,
        String actorId,
        Set<String> permissions,
        boolean trusted
) {
    public AgentCallerIdentity {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public static AgentCallerIdentity unresolved() {
        return new AgentCallerIdentity(null, null, Set.of(), false);
    }
}

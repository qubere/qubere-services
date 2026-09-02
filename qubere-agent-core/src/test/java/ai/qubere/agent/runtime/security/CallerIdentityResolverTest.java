package ai.qubere.agent.runtime.security;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CallerIdentityResolverTest {

    @Test
    void trustedHeaderResolverReadsTenantActorAndPermissionsFromHeaders() {
        TrustedHeaderCallerIdentityResolver resolver = new TrustedHeaderCallerIdentityResolver();

        AgentCallerIdentity identity = resolver.resolve(Map.of(
                "X-Tenant-Id", "tenant-1",
                "X-Actor-Id", "actor-1",
                "X-Agent-Permissions", "agents.run, agents.admin"
        ));

        assertThat(identity.tenantId()).isEqualTo("tenant-1");
        assertThat(identity.actorId()).isEqualTo("actor-1");
        assertThat(identity.permissions()).containsExactlyInAnyOrder("agents.run", "agents.admin");
        assertThat(identity.trusted()).isFalse();
    }

    @Test
    void trustedHeaderResolverHandlesMissingHeaders() {
        TrustedHeaderCallerIdentityResolver resolver = new TrustedHeaderCallerIdentityResolver();

        AgentCallerIdentity identity = resolver.resolve(Map.of());

        assertThat(identity.tenantId()).isNull();
        assertThat(identity.actorId()).isNull();
        assertThat(identity.permissions()).isEmpty();
    }

    @Test
    void noOpResolverIgnoresHeadersAndReturnsUnresolvedIdentity() {
        NoOpCallerIdentityResolver resolver = new NoOpCallerIdentityResolver();

        AgentCallerIdentity identity = resolver.resolve(Map.of(
                "X-Tenant-Id", "tenant-1",
                "X-Actor-Id", "actor-1",
                "X-Agent-Permissions", "agents.run"
        ));

        assertThat(identity.tenantId()).isNull();
        assertThat(identity.actorId()).isNull();
        assertThat(identity.permissions()).isEmpty();
        assertThat(identity.trusted()).isFalse();
    }
}

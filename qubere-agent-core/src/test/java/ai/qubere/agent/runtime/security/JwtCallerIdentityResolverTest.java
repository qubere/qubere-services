package ai.qubere.agent.runtime.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtCallerIdentityResolverTest {

    @Test
    void resolvesTenantActorAndPermissionsFromValidatedClaims() {
        JwtDecoder decoder = token -> jwt(Map.of(
                "tenant_id", "tenant-1",
                "sub", "actor-1",
                "scope", "runs:write tools:invoke"
        ));
        JwtCallerIdentityResolver resolver = new JwtCallerIdentityResolver(decoder, "tenant_id", "sub", "scope");

        AgentCallerIdentity identity = resolver.resolve(Map.of("Authorization", "Bearer good-token"));

        assertThat(identity.trusted()).isTrue();
        assertThat(identity.tenantId()).isEqualTo("tenant-1");
        assertThat(identity.actorId()).isEqualTo("actor-1");
        assertThat(identity.permissions()).containsExactlyInAnyOrder("runs:write", "tools:invoke");
    }

    @Test
    void supportsListShapedPermissionsClaim() {
        JwtDecoder decoder = token -> jwt(Map.of(
                "sub", "actor-1",
                "roles", List.of("admin", "operator")
        ));
        JwtCallerIdentityResolver resolver = new JwtCallerIdentityResolver(decoder, "tenant_id", "sub", "roles");

        AgentCallerIdentity identity = resolver.resolve(Map.of("Authorization", "Bearer good-token"));

        assertThat(identity.permissions()).containsExactlyInAnyOrder("admin", "operator");
    }

    @Test
    void fallsBackToStandardSubjectClaimWhenActorClaimIsMisconfigured() {
        JwtDecoder decoder = token -> jwt(Map.of("sub", "actor-1"));
        JwtCallerIdentityResolver resolver = new JwtCallerIdentityResolver(decoder, "tenant_id", "does_not_exist", "scope");

        AgentCallerIdentity identity = resolver.resolve(Map.of("Authorization", "Bearer good-token"));

        assertThat(identity.actorId()).isEqualTo("actor-1");
    }

    @Test
    void invalidTokenResolvesToUnresolvedRatherThanThrowing() {
        JwtDecoder decoder = token -> {
            throw new JwtException("signature invalid");
        };
        JwtCallerIdentityResolver resolver = new JwtCallerIdentityResolver(decoder, "tenant_id", "sub", "scope");

        AgentCallerIdentity identity = resolver.resolve(Map.of("Authorization", "Bearer bad-token"));

        assertThat(identity).isEqualTo(AgentCallerIdentity.unresolved());
    }

    @Test
    void missingAuthorizationHeaderResolvesToUnresolved() {
        JwtDecoder decoder = token -> jwt(Map.of("sub", "actor-1"));
        JwtCallerIdentityResolver resolver = new JwtCallerIdentityResolver(decoder, "tenant_id", "sub", "scope");

        assertThat(resolver.resolve(Map.of())).isEqualTo(AgentCallerIdentity.unresolved());
        assertThat(resolver.resolve(null)).isEqualTo(AgentCallerIdentity.unresolved());
    }

    @Test
    void nonBearerAuthorizationHeaderResolvesToUnresolved() {
        JwtDecoder decoder = token -> jwt(Map.of("sub", "actor-1"));
        JwtCallerIdentityResolver resolver = new JwtCallerIdentityResolver(decoder, "tenant_id", "sub", "scope");

        AgentCallerIdentity identity = resolver.resolve(Map.of("Authorization", "Basic dXNlcjpwYXNz"));

        assertThat(identity).isEqualTo(AgentCallerIdentity.unresolved());
    }

    @Test
    void requiresAJwtDecoder() {
        assertThatThrownBy(() -> new JwtCallerIdentityResolver(null, "tenant_id", "sub", "scope"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token-value")
                .header("alg", "none")
                .claims(map -> map.putAll(claims))
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}

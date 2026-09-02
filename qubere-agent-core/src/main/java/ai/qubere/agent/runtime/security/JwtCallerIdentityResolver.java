package ai.qubere.agent.runtime.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves caller identity from a validated OAuth2/JWT bearer token, using Spring Security's
 * {@link JwtDecoder} for signature, issuer, and expiry verification.
 * <p>
 * This is the framework's built-in answer to "how do I use a real identity provider": opt-in via
 * {@code agent-platform.security.jwt.enabled=true}, active only when a {@link JwtDecoder} bean is
 * present (typically auto-configured by adding {@code spring-boot-starter-oauth2-resource-server}
 * and setting {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}). Tenant, actor, and
 * permission claim names are configurable because identity providers do not agree on claim shape.
 * <p>
 * A missing, malformed, expired, or otherwise invalid token resolves to
 * {@link AgentCallerIdentity#unresolved()} rather than throwing. This keeps the resolver contract
 * uniform with the framework's other resolvers and lets the existing fail-closed authorization
 * checks (which already reject blank tenant/actor in strict mode) do the rejecting — a single
 * place decides what happens to an unauthenticated request, instead of this class making that
 * decision by throwing an exception that callers may not expect.
 */
public class JwtCallerIdentityResolver implements AgentCallerIdentityResolver {

    private static final Logger log = LoggerFactory.getLogger(JwtCallerIdentityResolver.class);

    public static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final String tenantClaim;
    private final String actorClaim;
    private final String permissionsClaim;

    public JwtCallerIdentityResolver(JwtDecoder jwtDecoder, String tenantClaim, String actorClaim, String permissionsClaim) {
        if (jwtDecoder == null) {
            throw new IllegalArgumentException("JwtDecoder is required");
        }
        this.jwtDecoder = jwtDecoder;
        this.tenantClaim = blankToNull(tenantClaim) == null ? "tenant_id" : tenantClaim;
        this.actorClaim = blankToNull(actorClaim) == null ? "sub" : actorClaim;
        this.permissionsClaim = blankToNull(permissionsClaim) == null ? "scope" : permissionsClaim;
    }

    @Override
    public AgentCallerIdentity resolve(Map<String, String> headers) {
        String token = bearerToken(headers);
        if (token == null) {
            return AgentCallerIdentity.unresolved();
        }
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException ex) {
            // Invalid/expired/malformed tokens are routine traffic (retries, expired sessions),
            // not an operational error, so this stays at debug rather than warn/error.
            log.debug("Rejected inbound bearer token: {}", ex.getMessage());
            return AgentCallerIdentity.unresolved();
        }
        String tenantId = stringClaim(jwt, tenantClaim);
        String actorId = stringClaim(jwt, actorClaim);
        if (actorId == null) {
            // "sub" is a required JWT claim, but a custom actor claim might be misconfigured;
            // falling back keeps a validly-signed token from becoming an unresolved caller.
            actorId = jwt.getSubject();
        }
        Set<String> permissions = permissionsClaim(jwt);
        return new AgentCallerIdentity(tenantId, actorId, permissions, true);
    }

    private String bearerToken(Map<String, String> headers) {
        if (headers == null) {
            return null;
        }
        String value = headers.get(HEADER_AUTHORIZATION);
        if (value == null || !value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = value.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private String stringClaim(Jwt jwt, String claim) {
        Object value = jwt.getClaim(claim);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    @SuppressWarnings("unchecked")
    private Set<String> permissionsClaim(Jwt jwt) {
        Object value = jwt.getClaim(permissionsClaim);
        if (value == null) {
            return Set.of();
        }
        if (value instanceof String text) {
            // Common shapes: a single space-delimited "scope" string, or a comma-delimited custom claim.
            String delimiter = text.contains(" ") ? " " : ",";
            Set<String> permissions = new LinkedHashSet<>();
            for (String token : text.split(delimiter)) {
                String trimmed = token.trim();
                if (!trimmed.isEmpty()) {
                    permissions.add(trimmed);
                }
            }
            return permissions;
        }
        if (value instanceof List<?> list) {
            Set<String> permissions = new LinkedHashSet<>();
            for (Object item : list) {
                if (item != null) {
                    permissions.add(item.toString());
                }
            }
            return permissions;
        }
        return Set.of();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

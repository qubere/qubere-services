package ai.qubere.agent.runtime.security;

import java.util.Map;

/**
 * Resolves the trusted caller identity (tenant, actor, permissions) for an inbound agent run
 * request. This is the framework's extension point for real authentication.
 * <p>
 * Deployed applications running with {@code agent-platform.security.authorization-mode=strict}
 * should supply their own bean backed by a verified identity source such as a Spring Security
 * {@code Authentication}, a validated JWT, or an upstream API-gateway-asserted identity header
 * that the gateway itself guarantees cannot be spoofed by the caller.
 * <p>
 * The framework ships two defaults, chosen automatically by
 * {@code agent-platform.security.authorization-mode} unless overridden:
 * <ul>
 *   <li>{@link TrustedHeaderCallerIdentityResolver} for permissive/local development.</li>
 *   <li>{@link NoOpCallerIdentityResolver} for strict mode, which never trusts inbound headers
 *       and fails closed (empty identity) until a real resolver bean is provided.</li>
 * </ul>
 */
public interface AgentCallerIdentityResolver {

    /**
     * Resolves caller identity from the inbound request headers.
     *
     * @param headers case-insensitive-by-convention header name/value pairs already extracted
     *                by the web layer (e.g. {@code X-Tenant-Id}, {@code X-Actor-Id},
     *                {@code X-Agent-Permissions}, or an {@code Authorization} bearer token).
     * @return resolved caller identity; never {@code null}. Use {@link AgentCallerIdentity#unresolved()}
     *         when no trusted identity can be established.
     */
    AgentCallerIdentity resolve(Map<String, String> headers);
}

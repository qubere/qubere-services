package ai.qubere.agent.runtime.security;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fail-closed default resolver used when {@code agent-platform.security.authorization-mode=strict}
 * and no application-supplied {@link AgentCallerIdentityResolver} bean is present.
 * <p>
 * It deliberately ignores all inbound headers and always returns
 * {@link AgentCallerIdentity#unresolved()}. Combined with the default
 * {@code require-tenant}/{@code require-actor} security settings, this causes every run to be
 * denied until the deployed application supplies a real resolver backed by a verified identity
 * source (JWT/OAuth/Spring Security/gateway-asserted identity). This is intentional: strict mode
 * must never silently fall back to trusting caller-supplied headers.
 */
public class NoOpCallerIdentityResolver implements AgentCallerIdentityResolver {

    private static final Logger log = LoggerFactory.getLogger(NoOpCallerIdentityResolver.class);
    private volatile boolean warned;

    @Override
    public AgentCallerIdentity resolve(Map<String, String> headers) {
        if (!warned) {
            warned = true;
            log.warn(
                    "agent-platform.security.authorization-mode=strict but no AgentCallerIdentityResolver bean "
                            + "was supplied. Inbound headers will NOT be trusted for tenant/actor identity and all "
                            + "runs will be denied unless a real resolver bean is registered."
            );
        }
        return AgentCallerIdentity.unresolved();
    }
}

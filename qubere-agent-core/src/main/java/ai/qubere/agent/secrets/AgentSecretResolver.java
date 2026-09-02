package ai.qubere.agent.secrets;

import java.util.Optional;

/**
 * Resolves a named secret (API key, database password, signing secret, etc.) for framework and
 * agent code that needs access to sensitive configuration values at runtime.
 * <p>
 * This is the framework's extension point for secret management. The default implementation,
 * {@link EnvironmentAgentSecretResolver}, reads from the Spring {@code Environment}, which is
 * the same source ordinary {@code application.yml}/environment-variable configuration already
 * uses today. Production deployments that need centralized secret management, rotation, or
 * audit trails should replace it with a {@code @Bean AgentSecretResolver} backed by a real
 * secret store such as HashiCorp Vault, Azure Key Vault, or AWS Secrets Manager.
 * <p>
 * Framework and agent code should resolve secrets through this interface rather than reading
 * {@code System.getenv()} or hardcoding property lookups directly, so a single configuration
 * change can redirect every secret lookup to a managed secret store.
 */
public interface AgentSecretResolver {

    /**
     * Resolves the current value of a named secret.
     *
     * @param secretName logical secret name, for example {@code "openai.api-key"} or
     *                    {@code "agent-platform.callback.signing-secret"}.
     * @return the resolved secret value, or empty if the secret is not configured.
     */
    Optional<String> resolve(String secretName);
}

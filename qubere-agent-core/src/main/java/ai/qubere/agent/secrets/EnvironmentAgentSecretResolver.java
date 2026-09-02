package ai.qubere.agent.secrets;

import java.util.Optional;

import org.springframework.core.env.Environment;

/**
 * Default {@link AgentSecretResolver} that reads secrets from the Spring {@link Environment},
 * i.e. {@code application.yml} properties and OS environment variables. This preserves today's
 * behavior (environment-variable-based secrets) while giving deployed applications a single
 * seam to override with a real secret-store-backed resolver.
 */
public class EnvironmentAgentSecretResolver implements AgentSecretResolver {

    private final Environment environment;

    public EnvironmentAgentSecretResolver(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Optional<String> resolve(String secretName) {
        if (secretName == null || secretName.isBlank() || environment == null) {
            return Optional.empty();
        }
        String value = environment.getProperty(secretName);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}

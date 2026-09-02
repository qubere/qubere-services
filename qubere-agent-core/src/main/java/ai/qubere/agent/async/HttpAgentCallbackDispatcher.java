package ai.qubere.agent.async;

import ai.qubere.agent.runtime.config.AgentPlatformProperties;
import ai.qubere.agent.secrets.AgentSecretResolver;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public class HttpAgentCallbackDispatcher implements AgentCallbackDispatcher {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SIGNING_SECRET_NAME = "agent-platform.async.callback.signing-secret";

    private final RestClient restClient;
    private final AgentPlatformProperties.Async.Callback properties;
    private final AgentSecretResolver secretResolver;

    public HttpAgentCallbackDispatcher(RestClient.Builder restClientBuilder, AgentPlatformProperties properties) {
        this(restClientBuilder, properties, null);
    }

    public HttpAgentCallbackDispatcher(RestClient.Builder restClientBuilder, AgentPlatformProperties properties, AgentSecretResolver secretResolver) {
        this.properties = properties == null
                ? new AgentPlatformProperties.Async.Callback()
                : properties.getAsync().getCallback();
        this.secretResolver = secretResolver;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(this.properties.getTimeoutSeconds()));
        requestFactory.setReadTimeout(Duration.ofSeconds(this.properties.getTimeoutSeconds()));
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
    }

    @Override
    public void dispatch(AgentRunCallback callback) {
        if (callback == null || callback.callbackUrl() == null || callback.callbackUrl().isBlank()) {
            return;
        }
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= properties.getMaxAttempts(); attempt++) {
            try {
                RestClient.RequestBodySpec request = restClient.post()
                        .uri(callback.callbackUrl())
                        .contentType(MediaType.APPLICATION_JSON);
                String signature = signature(callback);
                if (signature != null) {
                    request.header("X-Agent-Callback-Signature", signature);
                }
                request.body(callback).retrieve().toBodilessEntity();
                return;
            } catch (RuntimeException ex) {
                lastFailure = ex;
                backoff(attempt);
            }
        }
        throw new IllegalStateException("Agent callback delivery failed after " + properties.getMaxAttempts() + " attempts", lastFailure);
    }

    private void backoff(int attempt) {
        if (attempt >= properties.getMaxAttempts() || properties.getRetryBackoffMillis() <= 0L) {
            return;
        }
        try {
            Thread.sleep(properties.getRetryBackoffMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private String signature(AgentRunCallback callback) {
        String secret = resolveSigningSecret();
        if (secret == null || secret.isBlank()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] digest = mac.doFinal((callback.executionId() + ":" + callback.status()).getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("Unable to sign agent callback", ex);
        }
    }

    private String resolveSigningSecret() {
        if (secretResolver != null) {
            return secretResolver.resolve(SIGNING_SECRET_NAME).orElseGet(properties::getSigningSecret);
        }
        return properties.getSigningSecret();
    }
}

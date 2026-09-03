package ai.qubere.document.agent.document.parser.config;

import ai.qubere.agent.resilience.AgentResilienceGateway;
import ai.qubere.document.agent.document.parser.DocumentParserException;
import ai.qubere.document.agent.document.parser.DocumentParserProvider;
import ai.qubere.document.agent.document.parser.FallbackDoclingProvider;
import ai.qubere.document.agent.document.parser.ParserErrorCode;
import ai.qubere.document.agent.document.parser.ibm.IbmDoclingProvider;
import ai.qubere.document.agent.document.parser.mock.MockDoclingProvider;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

/**
 * Resolves the configured {@link DocumentParserProvider}, ported from {@code parser/registry.ts}.
 * <p>
 * This is the single place a provider is constructed, so the production-safety rule — a mock
 * provider must never serve production traffic — is enforced once rather than trusted to every
 * call site. Unlike the source's imperative factory function, this is expressed as Spring
 * {@code @Bean} wiring so the resolved provider participates in the application context like any
 * other collaborator (injectable, overridable via {@code @ConditionalOnMissingBean}, visible to
 * Spring Boot's actuator).
 * <p>
 * {@code production} is derived from the active Spring profiles (a profile named {@code prod} or
 * {@code production}) rather than {@code NODE_ENV}, since that is this framework's idiomatic
 * equivalent — deployments that use a different signal can override the
 * {@code documentParserProductionGuard} bean.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ParserProperties.class)
public class DocumentParserProviderAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DocumentParserProviderAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(name = "documentParserProductionGuard")
    DocumentParserProductionGuard documentParserProductionGuard(Environment environment) {
        return () -> environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod", "production"));
    }

    @Bean
    @ConditionalOnMissingBean
    DocumentParserProvider documentParserProvider(
            ParserProperties properties,
            DocumentParserProductionGuard productionGuard,
            ObjectProvider<RestClient.Builder> restClientBuilder,
            ObjectProvider<AgentResilienceGateway> resilienceGateway,
            ObjectProvider<ObjectMapper> objectMapper
    ) {
        boolean production = productionGuard.isProduction();
        AgentResilienceGateway gateway = resilienceGateway.getIfAvailable(AgentResilienceGateway::noop);

        return switch (properties.getProvider()) {
            case NONE -> new NotConfiguredDocumentParserProvider();
            case MOCK -> {
                if (production) {
                    throw new DocumentParserException(
                            ParserErrorCode.PARSER_NOT_CONFIGURED,
                            "document-agent.parser.provider=mock is not permitted in a production environment."
                    );
                }
                yield new MockDoclingProvider(false);
            }
            case IBM_DOCLING -> {
                if (production) {
                    yield new IbmDoclingProvider(
                            requiredRestClientBuilder(restClientBuilder), properties, gateway, requiredObjectMapper(objectMapper));
                }
                DocumentParserProvider primary = null;
                try {
                    primary = new IbmDoclingProvider(
                            requiredRestClientBuilder(restClientBuilder), properties, gateway, requiredObjectMapper(objectMapper));
                } catch (DocumentParserException ex) {
                    log.warn("Primary parser (ibm-docling) unconfigured, using backup parser: {}", ex.getMessage());
                }
                DocumentParserProvider backup = new MockDoclingProvider(false);
                yield primary != null ? new FallbackDoclingProvider(primary, backup) : backup;
            }
        };
    }

    /**
     * {@code documentParserProvider} only needs an HTTP client when it actually constructs an
     * {@code IbmDoclingProvider}. Requiring {@code RestClient.Builder} as a direct method parameter
     * would force Spring to resolve it eagerly for every provider selection, including
     * {@code NONE}/{@code MOCK}, which need no HTTP client at all — so it is fetched lazily here
     * instead, and only inside the branch that genuinely needs it.
     */
    private static RestClient.Builder requiredRestClientBuilder(ObjectProvider<RestClient.Builder> provider) {
        RestClient.Builder builder = provider.getIfAvailable();
        if (builder == null) {
            throw new DocumentParserException(
                    ParserErrorCode.PARSER_NOT_CONFIGURED,
                    "document-agent.parser.provider=ibm-docling requires a RestClient.Builder bean "
                            + "(normally auto-configured by spring-boot-starter-web)."
            );
        }
        return builder;
    }

    private static ObjectMapper requiredObjectMapper(ObjectProvider<ObjectMapper> provider) {
        return provider.getIfAvailable(ObjectMapper::new);
    }

    /** {@code true} when a real provider can be resolved. Used by health reporting, not control flow. */
    @Bean
    @ConditionalOnMissingBean
    DocumentParsingHealthContributor documentParsingHealthContributor(ParserProperties properties) {
        return () -> properties.getProvider() != ParserProviderId.NONE;
    }

    /** Whether the running process should be treated as production for parser-provider safety guards. */
    @FunctionalInterface
    public interface DocumentParserProductionGuard {
        boolean isProduction();
    }

    /** Minimal reporting seam; a full actuator health indicator can wrap this once wired up. */
    @FunctionalInterface
    public interface DocumentParsingHealthContributor {
        boolean isDocumentParsingEnabled();
    }
}

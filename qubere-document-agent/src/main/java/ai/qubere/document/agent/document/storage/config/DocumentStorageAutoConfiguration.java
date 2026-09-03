package ai.qubere.document.agent.document.storage.config;

import ai.qubere.document.agent.document.processing.DocumentBytesSource;
import ai.qubere.document.agent.document.processing.NotConfiguredDocumentBytesSource;
import ai.qubere.document.agent.document.storage.DocumentBytesWriter;
import ai.qubere.document.agent.document.storage.DocumentStorageProperties;
import ai.qubere.document.agent.document.storage.LocalDiskDocumentStorage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Resolves the configured document byte-storage backend. The single place a
 * {@link DocumentBytesSource} is constructed for this module — mirrors
 * {@code DocumentParserProviderAutoConfiguration}'s "one place decides, not every call site"
 * convention.
 * <p>
 * {@code document-agent.storage.type=none} (the default) keeps {@link NotConfiguredDocumentBytesSource}
 * wired, so the application boots and the processing worker/state machine can be exercised without a
 * real byte-storage integration, exactly as before this configuration existed.
 * {@code document-agent.storage.type=local-disk} wires {@link LocalDiskDocumentStorage}, which
 * implements both {@link DocumentBytesSource} (consumed by {@code DocumentProcessingWorker}) and
 * {@link DocumentBytesWriter} (consumed by the document-submission REST endpoint) on the same
 * instance, since both need to agree on the same storage layout.
 * <p>
 * Only one bean is registered here, not a separate {@code DocumentBytesWriter} bean too: Spring
 * resolves {@code getBeanNamesForType} against an already-created singleton's <em>actual</em>
 * class, not merely a {@code @Bean} method's declared return type, so a second bean method
 * returning the same instance cast to {@code DocumentBytesWriter} would make both beans match
 * lookups for either interface — an ambiguous-bean error at the first injection point that asks for
 * either type. Callers that need to write (the submission controller) check
 * {@code documentBytesSource instanceof DocumentBytesWriter} instead.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DocumentStorageProperties.class)
public class DocumentStorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    DocumentBytesSource documentBytesSource(DocumentStorageProperties properties) {
        return switch (properties.getType()) {
            case NONE -> new NotConfiguredDocumentBytesSource();
            case LOCAL_DISK -> new LocalDiskDocumentStorage(properties.getLocalDisk());
        };
    }
}


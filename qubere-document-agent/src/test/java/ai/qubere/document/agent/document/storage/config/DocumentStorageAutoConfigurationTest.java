package ai.qubere.document.agent.document.storage.config;

import ai.qubere.document.agent.document.processing.DocumentBytesSource;
import ai.qubere.document.agent.document.processing.NotConfiguredDocumentBytesSource;
import ai.qubere.document.agent.document.storage.DocumentBytesWriter;
import ai.qubere.document.agent.document.storage.LocalDiskDocumentStorage;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentStorageAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DocumentStorageAutoConfiguration.class));

    @Test
    void registersTheNotConfiguredSourceByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DocumentBytesSource.class);
            assertThat(context.getBean(DocumentBytesSource.class)).isInstanceOf(NotConfiguredDocumentBytesSource.class);
            assertThat(context.getBean(DocumentBytesSource.class)).isNotInstanceOf(DocumentBytesWriter.class);
        });
    }

    @Test
    void registersLocalDiskStorageAsBothSourceAndWriterWhenConfigured(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) {
        contextRunner
                .withPropertyValues(
                        "document-agent.storage.type=local-disk",
                        "document-agent.storage.local-disk.root-dir=" + tempDir
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(DocumentBytesSource.class);
                    DocumentBytesSource source = context.getBean(DocumentBytesSource.class);
                    assertThat(source).isInstanceOf(LocalDiskDocumentStorage.class);
                    assertThat(source).isInstanceOf(DocumentBytesWriter.class);
                });
    }
}

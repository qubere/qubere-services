package ai.qubere.document.agent.document.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.qubere.document.agent.document.parser.DocumentParserException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalDiskDocumentStorageTest {

    @TempDir
    Path tempDir;

    private LocalDiskDocumentStorage newStorage() {
        DocumentStorageProperties.LocalDisk properties = new DocumentStorageProperties.LocalDisk();
        properties.setRootDir(tempDir.toString());
        return new LocalDiskDocumentStorage(properties);
    }

    @Test
    void storesAndReadsBackBytesFilenameAndMimeType() {
        LocalDiskDocumentStorage storage = newStorage();
        byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);

        storage.store("doc-1", "invoice.pdf", "application/pdf", content);

        assertThat(storage.loadBytes("doc-1")).isEqualTo(content);
        assertThat(storage.filename("doc-1")).isEqualTo("invoice.pdf");
        assertThat(storage.mimeType("doc-1")).isEqualTo("application/pdf");
    }

    @Test
    void reducesFilenameToItsBasename() {
        LocalDiskDocumentStorage storage = newStorage();

        storage.store("doc-2", "../../etc/passwd", "text/plain", "x".getBytes(StandardCharsets.UTF_8));

        assertThat(storage.filename("doc-2")).isEqualTo("passwd");
    }

    @Test
    void fallsBackToDefaultsWhenFilenameOrMimeTypeAreBlank() {
        LocalDiskDocumentStorage storage = newStorage();

        storage.store("doc-3", null, null, "x".getBytes(StandardCharsets.UTF_8));

        assertThat(storage.filename("doc-3")).isEqualTo("document");
        assertThat(storage.mimeType("doc-3")).isEqualTo("application/octet-stream");
    }

    @Test
    void newQualifyingParseReplacesThePreviousBytes() {
        LocalDiskDocumentStorage storage = newStorage();
        storage.store("doc-4", "first.pdf", "application/pdf", "first".getBytes(StandardCharsets.UTF_8));

        storage.store("doc-4", "second.pdf", "application/pdf", "second".getBytes(StandardCharsets.UTF_8));

        assertThat(storage.loadBytes("doc-4")).isEqualTo("second".getBytes(StandardCharsets.UTF_8));
        assertThat(storage.filename("doc-4")).isEqualTo("second.pdf");
    }

    @Test
    void rejectsEmptyBytes() {
        LocalDiskDocumentStorage storage = newStorage();

        assertThatThrownBy(() -> storage.store("doc-5", "a.pdf", "application/pdf", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUploadsLargerThanTheConfiguredLimit() {
        DocumentStorageProperties.LocalDisk properties = new DocumentStorageProperties.LocalDisk();
        properties.setRootDir(tempDir.toString());
        properties.setMaxFileSizeBytes(4);
        LocalDiskDocumentStorage storage = new LocalDiskDocumentStorage(properties);

        assertThatThrownBy(() -> storage.store("doc-6", "a.pdf", "application/pdf", "too big".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDocumentIdsThatDoNotMatchTheSafeCharset() {
        LocalDiskDocumentStorage storage = newStorage();

        assertThatThrownBy(() -> storage.store("../escape", "a.pdf", "application/pdf", "x".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.loadBytes("../escape"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loadingBytesForAnUnknownDocumentFailsWithSourceFileUnavailable() {
        LocalDiskDocumentStorage storage = newStorage();

        assertThatThrownBy(() -> storage.loadBytes("does-not-exist"))
                .isInstanceOf(DocumentParserException.class);
    }
}

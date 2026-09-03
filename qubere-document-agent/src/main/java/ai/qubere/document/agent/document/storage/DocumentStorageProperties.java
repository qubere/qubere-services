package ai.qubere.document.agent.document.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Byte-storage configuration for uploaded documents, bound from {@code application.yml}. Mirrors
 * the same "defaults to nothing, deployments opt in explicitly" convention as
 * {@code ParserProperties.provider}.
 */
@ConfigurationProperties(prefix = "document-agent.storage")
public class DocumentStorageProperties {

    private DocumentStorageType type = DocumentStorageType.NONE;
    private LocalDisk localDisk = new LocalDisk();

    public DocumentStorageType getType() {
        return type;
    }

    public void setType(DocumentStorageType type) {
        this.type = type == null ? DocumentStorageType.NONE : type;
    }

    public LocalDisk getLocalDisk() {
        return localDisk;
    }

    public void setLocalDisk(LocalDisk localDisk) {
        this.localDisk = localDisk == null ? new LocalDisk() : localDisk;
    }

    public static class LocalDisk {

        /** Root directory documents are stored under, one subdirectory per document id. */
        private String rootDir = "./data/documents";

        /** Rejects uploads larger than this before any bytes are written to disk. */
        private long maxFileSizeBytes = 25L * 1024 * 1024;

        public String getRootDir() {
            return rootDir;
        }

        public void setRootDir(String rootDir) {
            this.rootDir = (rootDir == null || rootDir.isBlank()) ? "./data/documents" : rootDir;
        }

        public long getMaxFileSizeBytes() {
            return maxFileSizeBytes;
        }

        public void setMaxFileSizeBytes(long maxFileSizeBytes) {
            this.maxFileSizeBytes = maxFileSizeBytes <= 0 ? 25L * 1024 * 1024 : maxFileSizeBytes;
        }
    }
}

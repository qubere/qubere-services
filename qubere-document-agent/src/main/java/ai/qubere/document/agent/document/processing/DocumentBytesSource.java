package ai.qubere.document.agent.document.processing;

/**
 * Retrieves document bytes for a document id. Deliberately a pluggable seam, not a concrete
 * implementation: real byte retrieval (local disk / object storage / raw-content fallback, per
 * {@code loadDocumentBytes.ts}) is explicitly scoped to a later phase in
 * {@code qubere-document-agent/MIGRATION.md} §4 Phase 5, since it involves storage decisions this
 * pass does not make. {@link DocumentProcessingWorker} depends only on this interface so the
 * processing-run state machine and poll loop can be built and tested now, without pretending byte
 * retrieval already exists.
 */
public interface DocumentBytesSource {

    byte[] loadBytes(String documentId);

    String filename(String documentId);

    String mimeType(String documentId);
}

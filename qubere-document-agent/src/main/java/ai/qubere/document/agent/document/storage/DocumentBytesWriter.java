package ai.qubere.document.agent.document.storage;

/**
 * Persists uploaded document bytes so a later {@link DocumentBytesSource} lookup during parsing
 * can retrieve them. A separate interface from {@link DocumentBytesSource} because not every byte
 * source is writable from this process — a future object-storage-backed source, for instance,
 * might be populated entirely by an upstream system and only ever read here.
 */
public interface DocumentBytesWriter {

    void store(String documentId, String fileName, String mimeType, byte[] bytes);
}

package ai.qubere.document.agent.document.storage;

/**
 * Which byte-storage backend this deployment uses to hold uploaded document bytes between
 * submission and parsing. Defaults to {@code NONE} so nothing pretends uploads work until a real
 * backend is configured — same convention as {@code ParserProviderId.NONE}.
 */
public enum DocumentStorageType {
    NONE,
    LOCAL_DISK
}

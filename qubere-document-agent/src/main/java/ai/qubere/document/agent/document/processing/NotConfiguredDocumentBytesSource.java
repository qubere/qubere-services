package ai.qubere.document.agent.document.processing;

import ai.qubere.document.agent.document.parser.DocumentParserException;
import ai.qubere.document.agent.document.parser.ParserErrorCode;

/**
 * Default {@link DocumentBytesSource} that reports {@code SOURCE_FILE_UNAVAILABLE} for every
 * document. Registered so the application boots and the processing worker can be exercised
 * end-to-end (state machine, poll loop, governance) without a real byte-storage integration —
 * failing clearly at first use rather than needing byte retrieval decided before anything else here
 * can be tested.
 */
public class NotConfiguredDocumentBytesSource implements DocumentBytesSource {

    @Override
    public byte[] loadBytes(String documentId) {
        throw notConfigured(documentId);
    }

    @Override
    public String filename(String documentId) {
        throw notConfigured(documentId);
    }

    @Override
    public String mimeType(String documentId) {
        throw notConfigured(documentId);
    }

    private static DocumentParserException notConfigured(String documentId) {
        return new DocumentParserException(
                ParserErrorCode.SOURCE_FILE_UNAVAILABLE,
                "No DocumentBytesSource is configured; document " + documentId + " cannot be retrieved for parsing.",
                true, null, null
        );
    }
}

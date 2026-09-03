package ai.qubere.document.agent.document.parser.config;

import ai.qubere.document.agent.document.parser.DocumentParserException;
import ai.qubere.document.agent.document.parser.DocumentParserProvider;
import ai.qubere.document.agent.document.parser.ParserErrorCode;
import ai.qubere.document.agent.document.parser.ParserJobReference;
import ai.qubere.document.agent.document.parser.ParserJobStatus;
import ai.qubere.document.agent.document.parser.ParserResult;
import ai.qubere.document.agent.document.parser.ParserSubmission;
import ai.qubere.document.agent.document.parser.ParserSubmissionAck;
import ai.qubere.document.agent.document.parser.ProcessingProfile;
import ai.qubere.document.agent.document.parser.SourceDelivery;

/**
 * Placeholder registered when {@code document-agent.parser.provider=none} (the default).
 * <p>
 * A {@link DocumentParserProvider} bean always exists so anything that depends on one (an intake
 * tool, a health check, a future processing worker) can be wired without a conditional-bean dance
 * at every call site. The failure is deferred to first actual use rather than thrown at
 * application startup — mirroring the source project's own {@code getDocumentParserProvider()},
 * which only failed when request-handling code actually tried to resolve a provider, not at
 * module load. Throwing eagerly during bean construction would make an unconfigured deployment
 * (the common local/dev/test default) fail to start at all, which is a worse failure mode than
 * "parsing is unavailable until configured."
 */
public class NotConfiguredDocumentParserProvider implements DocumentParserProvider {

    public static final String PROVIDER_ID = "NONE";

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean isMockProvider() {
        return false;
    }

    @Override
    public SourceDelivery sourceDelivery() {
        return SourceDelivery.INLINE;
    }

    @Override
    public String configurationHash(ProcessingProfile profile) {
        return "unconfigured";
    }

    @Override
    public ParserSubmissionAck submit(ParserSubmission submission) {
        throw notConfigured();
    }

    @Override
    public ParserJobStatus getStatus(ParserJobReference reference) {
        throw notConfigured();
    }

    @Override
    public ParserResult getResult(ParserJobReference reference, ProcessingProfile profile) {
        throw notConfigured();
    }

    private static DocumentParserException notConfigured() {
        return new DocumentParserException(
                ParserErrorCode.PARSER_NOT_CONFIGURED,
                "No document parser provider is configured. Set document-agent.parser.provider=ibm-docling "
                        + "(production) or =mock (local development)."
        );
    }
}

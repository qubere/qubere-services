package ai.qubere.document.agent.document.parser.ibm;

import ai.qubere.document.agent.document.parser.DocumentParserException;
import ai.qubere.document.agent.document.parser.ParserErrorCode;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;

/**
 * SSRF protection for provider-supplied URLs, ported from
 * {@code parser/ibm/ibmHostedDoclingProvider.ts}'s {@code assertAllowedArtifactHost} /
 * {@code assertQubereStorageUrl}.
 * <p>
 * A malformed or hostile result payload must not be able to make this server fetch an arbitrary
 * (including internal) address. The URL itself must never appear in an exception message: a
 * presigned URL carries credentials in its query string, so it must not reach a log or a response
 * body.
 */
public final class SsrfArtifactHostValidator {

    /**
     * Default allowlisted artifact storage hosts. Overridable per deployment (see
     * {@code document-agent.parser.ibm-docling.artifact-hosts} once configured) for a deployment
     * that stores artifacts elsewhere.
     */
    private static final Set<String> DEFAULT_ARTIFACT_HOSTS = Set.of(
            "s3.amazonaws.com",
            "s3.us-east-1.amazonaws.com",
            "s3.us-east-2.amazonaws.com",
            "s3.us-west-2.amazonaws.com",
            "s3.eu-west-1.amazonaws.com",
            "s3.eu-de.cloud-object-storage.appdomain.cloud",
            "s3.us-south.cloud-object-storage.appdomain.cloud"
    );

    private SsrfArtifactHostValidator() {
    }

    /** Throws unless {@code uri} is https and its host is on {@code allowedHosts}. */
    public static void assertAllowedArtifactHost(String uri, Set<String> allowedHosts) {
        URL parsed = parseOrReject(uri);
        Set<String> hosts = allowedHosts == null || allowedHosts.isEmpty() ? DEFAULT_ARTIFACT_HOSTS : allowedHosts;
        if (!"https".equalsIgnoreCase(parsed.getProtocol()) || !hosts.contains(parsed.getHost())) {
            throw new DocumentParserException(
                    ParserErrorCode.PARSER_RESULT_INVALID,
                    "The parser returned an artifact link that is not on the allowed storage host list.",
                    false, null, null
            );
        }
    }

    /** Throws unless {@code uri} is a well-formed https URL. Used for this deployment's own signed URLs. */
    public static void assertHttpsUrl(String uri) {
        URL parsed = parseOrReject(uri);
        if (!"https".equalsIgnoreCase(parsed.getProtocol())) {
            throw new DocumentParserException(
                    ParserErrorCode.PARSER_SUBMISSION_FAILED,
                    "Only https signed URLs may be sent to a document parser provider.",
                    false, null, null
            );
        }
    }

    private static URL parseOrReject(String uri) {
        try {
            return new URL(uri);
        } catch (MalformedURLException ex) {
            throw new DocumentParserException(
                    ParserErrorCode.PARSER_RESULT_INVALID,
                    "The parser returned a malformed artifact link.",
                    false, null, null
            );
        }
    }
}

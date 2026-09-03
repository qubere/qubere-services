package ai.qubere.document.agent.document.parser;

import java.time.Instant;

/**
 * Document delivered as a short-lived signed URL to Qubere-controlled object storage.
 *
 * @param url       must resolve to an allowlisted Qubere storage host — never a client-supplied URL
 * @param expiresAt when the signed URL stops being valid
 */
public record ParserSourceSignedUrl(String filename, String mimeType, String url, Instant expiresAt) implements ParserSource {
}

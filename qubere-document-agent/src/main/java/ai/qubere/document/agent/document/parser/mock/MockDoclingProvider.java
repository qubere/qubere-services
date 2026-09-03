package ai.qubere.document.agent.document.parser.mock;

import ai.qubere.document.agent.document.parser.DocumentParserException;
import ai.qubere.document.agent.document.parser.DocumentParserProvider;
import ai.qubere.document.agent.document.parser.NormalizedParserResult;
import ai.qubere.document.agent.document.parser.ParserErrorCode;
import ai.qubere.document.agent.document.parser.ParserJobReference;
import ai.qubere.document.agent.document.parser.ParserJobStatus;
import ai.qubere.document.agent.document.parser.ParserMetadata;
import ai.qubere.document.agent.document.parser.ParserResult;
import ai.qubere.document.agent.document.parser.ParserSource;
import ai.qubere.document.agent.document.parser.ParserSourceInline;
import ai.qubere.document.agent.document.parser.ParserSubmission;
import ai.qubere.document.agent.document.parser.ParserSubmissionAck;
import ai.qubere.document.agent.document.parser.ParserWarning;
import ai.qubere.document.agent.document.parser.ParsedSection;
import ai.qubere.document.agent.document.parser.ProcessingProfile;
import ai.qubere.document.agent.document.parser.ProcessingRunState;
import ai.qubere.document.agent.document.parser.Provenance;
import ai.qubere.document.agent.document.parser.SourceDelivery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local-development parser provider, ported from {@code parser/mock/mockDoclingProvider.ts}.
 * <p>
 * Exists so the processing worker, artifact store, quality gate, and context builder can be
 * exercised without real parser credentials. This is <strong>not</strong> a Docling emulator and
 * does not read the document: it derives a small structured result from the bytes it was handed,
 * and labels every artifact it produces as mock-produced so nothing downstream can mistake it for
 * a real parse.
 * <p>
 * Three independent guards keep this out of production, matching the source's own defense in
 * depth: {@link #isMockProvider()} returns {@code true} so a registry can refuse it;
 * {@code productionProfile} is checked in the constructor; and {@code metadata.provider} is always
 * {@value #PROVIDER_ID}, persisted on every run so an existing run's origin is auditable after the
 * fact even if the first two guards were somehow bypassed.
 */
public class MockDoclingProvider implements DocumentParserProvider {

    public static final String PROVIDER_ID = "MOCK_PARSER";

    /**
     * Task state lives in memory, which is exactly why this provider is unusable in production: a
     * restart loses every in-flight task. The framework's own run state is durable regardless, so
     * a lost mock task surfaces as a run that fails its poll ceiling rather than as silent data loss.
     */
    private final Map<String, MockTask> tasks = new ConcurrentHashMap<>();

    /** Number of polls before a task reports success, so polling is exercised even in tests. */
    private final int pollsBeforeSuccess;

    public MockDoclingProvider(boolean productionProfileActive) {
        this(productionProfileActive, 1);
    }

    public MockDoclingProvider(boolean productionProfileActive, int pollsBeforeSuccess) {
        if (productionProfileActive) {
            throw new DocumentParserException(
                    ParserErrorCode.PARSER_NOT_CONFIGURED,
                    "The mock document parser cannot run in production. Set document-agent.parser.provider=ibm-docling."
            );
        }
        this.pollsBeforeSuccess = pollsBeforeSuccess;
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean isMockProvider() {
        return true;
    }

    @Override
    public SourceDelivery sourceDelivery() {
        return SourceDelivery.INLINE;
    }

    @Override
    public String configurationHash(ProcessingProfile profile) {
        return sha256Hex("mock:" + profile).substring(0, 32);
    }

    @Override
    public ParserSubmissionAck submit(ParserSubmission submission) {
        ParserSource source = submission.source();
        if (!(source instanceof ParserSourceInline inline)) {
            throw new DocumentParserException(
                    ParserErrorCode.PARSER_SUBMISSION_FAILED,
                    "The mock parser only accepts inline sources.",
                    false, null, null
            );
        }
        byte[] bytes = inline.bytes();
        if (bytes.length == 0) {
            throw new DocumentParserException(ParserErrorCode.EMPTY_FILE, "The submitted document is empty.");
        }

        String externalTaskId = "mock_" + sha256Hex(submission.runId() + ":" + bytes.length).substring(0, 16);

        tasks.put(externalTaskId, new MockTask(
                submission.profile(), inline.filename(), bytes.length, decodeTextOrEmpty(bytes), pollsBeforeSuccess
        ));

        return new ParserSubmissionAck(
                externalTaskId,
                "pending",
                ProcessingRunState.SUBMITTED,
                List.of("ocrUsed", "fullPageOcrUsed", "parserConfidence", "mockProviderIsNotDocling"),
                Instant.now()
        );
    }

    @Override
    public ParserJobStatus getStatus(ParserJobReference reference) {
        MockTask task = tasks.get(reference.externalTaskId());
        if (task == null) {
            return new ParserJobStatus(
                    ProcessingRunState.FAILED,
                    "unknown_task",
                    new DocumentParserException(
                            ParserErrorCode.PARSER_PROVIDER_ERROR,
                            "The mock parser has no record of this task; its state does not survive a restart.",
                            false, "unknown_task", null
                    ),
                    Instant.now()
            );
        }
        if (task.decrementAndCheckPolling()) {
            return new ParserJobStatus(ProcessingRunState.POLLING, "started", null, Instant.now());
        }
        return new ParserJobStatus(ProcessingRunState.SUCCEEDED, "success", null, Instant.now());
    }

    @Override
    public ParserResult getResult(ParserJobReference reference, ProcessingProfile profile) {
        MockTask task = tasks.get(reference.externalTaskId());
        if (task == null) {
            throw new DocumentParserException(
                    ParserErrorCode.PARSER_PROVIDER_ERROR, "The mock parser has no record of this task.", false, null, null
            );
        }

        List<String> lines = task.recoveredText().lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
        String content = String.join("\n", lines);
        String digest = sha256Hex(content).substring(0, 12);

        Map<String, Object> canonical = Map.of(
                "schema_name", "MockParserDocument",
                "version", "0.0.0-mock",
                "name", task.filename(),
                "note", "Produced by Qubere's mock parser provider. NOT a Docling result."
        );

        NormalizedParserResult normalized = new NormalizedParserResult(
                NormalizedParserResult.CONTRACT_VERSION,
                profile,
                new ParserMetadata(PROVIDER_ID, "MockParserDocument", "0.0.0-mock", null, null,
                        content.isEmpty() ? 0 : 1, null, null, null, null, null),
                content.isEmpty() ? null : content,
                content.isEmpty() ? List.of() : List.of(new ParsedSection(
                        "sec_0000_" + digest, List.of(), content,
                        // No coordinates exist, so none are reported. The element ref is real: it
                        // points into the canonical payload above.
                        List.of(new Provenance(1, null, "#/texts/0"))
                )),
                List.of(),
                List.of(new ParserWarning(
                        "MOCK_PROVIDER",
                        "This result came from Qubere's mock parser provider, not from a real parser. It must not be treated as evidence.",
                        null
                )),
                content.isEmpty() ? List.of() : List.of(content.length())
        );

        return new ParserResult(canonical, normalized);
    }

    /**
     * {@code true} when the bytes round-trip through UTF-8 unchanged and carry no PDF header. A
     * PDF is deliberately not decoded — the mock has no parser — so it yields an empty-text
     * result, which is the honest outcome and usefully drives the quality gate down an OCR path.
     */
    private static String decodeTextOrEmpty(byte[] bytes) {
        if (bytes.length >= 4 && new String(bytes, 0, 4, StandardCharsets.ISO_8859_1).equals("%PDF")) {
            return "";
        }
        String decoded = new String(bytes, StandardCharsets.UTF_8);
        return java.util.Arrays.equals(decoded.getBytes(StandardCharsets.UTF_8), bytes) ? decoded : "";
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format(Locale.ROOT, "%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required and must be available on every supported JVM", ex);
        }
    }

    private static final class MockTask {
        private final ProcessingProfile profile;
        private final String filename;
        private final int byteSize;
        private final String recoveredText;
        private int pollsRemaining;

        private MockTask(ProcessingProfile profile, String filename, int byteSize, String recoveredText, int pollsRemaining) {
            this.profile = profile;
            this.filename = filename;
            this.byteSize = byteSize;
            this.recoveredText = recoveredText;
            this.pollsRemaining = pollsRemaining;
        }

        private String filename() {
            return filename;
        }

        private String recoveredText() {
            return recoveredText;
        }

        private synchronized boolean decrementAndCheckPolling() {
            if (pollsRemaining > 0) {
                pollsRemaining -= 1;
                return true;
            }
            return false;
        }
    }
}

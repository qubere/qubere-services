package ai.qubere.document.agent.document.parser.config;

import ai.qubere.document.agent.document.parser.ProcessingProfile;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Fixed mapping from {@link ProcessingProfile} to the provider options each profile implies,
 * ported from {@code parser/config.ts}. This mapping is not configurable per deployment — the
 * meaning of "STANDARD" must not silently drift between environments.
 */
public final class ParserProfileCatalog {

    private static final Pattern CONVERT_FILE_PATH = Pattern.compile("/convert/file(/|$)");

    private static final Map<ProcessingProfile, ProfileOptions> PROFILE_OPTIONS = Map.of(
            // Born-digital and common mixed documents: use the embedded text layer, and let the
            // provider's own OCR heuristics handle image-only pages. Table structure is always on
            // -- flattened tables are unusable as customs evidence.
            ProcessingProfile.STANDARD, new ProfileOptions(true, false, true),
            // Same as STANDARD at the provider level; the difference is that Qubere only reaches
            // this profile after the quality gate objectively found insufficient text, and records
            // the retry reason on the new run.
            ProcessingProfile.OCR_FALLBACK, new ProfileOptions(true, false, true),
            // Explicitly scanned/image documents, explicit reprocess, or a quality retry that
            // OCR_FALLBACK did not fix. Never applied by default.
            ProcessingProfile.FULL_PAGE_OCR, new ProfileOptions(true, true, true)
    );

    private ParserProfileCatalog() {
    }

    public static ProfileOptions optionsFor(ProcessingProfile profile) {
        return PROFILE_OPTIONS.get(profile);
    }

    /**
     * Picks the submission encoding implied by the endpoint path. {@code /convert/file} is the
     * multipart upload endpoint; {@code /convert/source} is the JSON one. Deriving it means a
     * correct base URL and path are enough to work, with no third setting to get right.
     */
    public static SubmitEncoding submitEncodingFor(String submitPath) {
        return submitPath != null && CONVERT_FILE_PATH.matcher(submitPath).find()
                ? SubmitEncoding.MULTIPART
                : SubmitEncoding.JSON;
    }

    public enum SubmitEncoding {
        JSON,
        MULTIPART
    }
}

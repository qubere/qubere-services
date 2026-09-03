package ai.qubere.document.agent.document.parser.config;

import ai.qubere.document.agent.document.parser.DocumentParserException;
import ai.qubere.document.agent.document.parser.ParserErrorCode;
import ai.qubere.document.agent.document.parser.SourceDelivery;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Document-parser configuration, ported from {@code parser/config.ts}. Every environment-specific
 * value used by the parsing subsystem is bound here and nowhere else, so no URL, header name, or
 * timeout is hard-coded next to the code that uses it.
 * <p>
 * Unlike the source TypeScript (which reads {@code process.env} lazily, re-evaluated per call,
 * because Next.js route modules and a standalone worker load in different processes), this is a
 * standard Spring Boot {@code @ConfigurationProperties} bean bound once from {@code application.yml}
 * — the idiomatic equivalent in a single Spring Boot process, and consistent with how the rest of
 * this framework (see {@code AgentPlatformProperties}) exposes configuration.
 */
@ConfigurationProperties(prefix = "document-agent.parser")
public class ParserProperties {

    /** Which provider this deployment uses. Defaults to {@code NONE} so nothing pretends to work. */
    private ParserProviderId provider = ParserProviderId.NONE;
    private IbmDocling ibmDocling = new IbmDocling();
    private ProcessingLimits processingLimits = new ProcessingLimits();
    private ContextBudget contextBudget = new ContextBudget();

    public ParserProviderId getProvider() {
        return provider;
    }

    public void setProvider(ParserProviderId provider) {
        this.provider = provider == null ? ParserProviderId.NONE : provider;
    }

    public IbmDocling getIbmDocling() {
        return ibmDocling;
    }

    public void setIbmDocling(IbmDocling ibmDocling) {
        this.ibmDocling = ibmDocling == null ? new IbmDocling() : ibmDocling;
    }

    public ProcessingLimits getProcessingLimits() {
        return processingLimits;
    }

    public void setProcessingLimits(ProcessingLimits processingLimits) {
        this.processingLimits = processingLimits == null ? new ProcessingLimits() : processingLimits;
    }

    public ContextBudget getContextBudget() {
        return contextBudget;
    }

    public void setContextBudget(ContextBudget contextBudget) {
        this.contextBudget = contextBudget == null ? new ContextBudget() : contextBudget;
    }

    /**
     * Validates the IBM-hosted Docling configuration, ported from {@code readIbmDoclingConfig()}.
     * Throws {@link ParserErrorCode#PARSER_NOT_CONFIGURED} (non-retryable) rather than returning a
     * partial config, so a missing credential surfaces as an explicit blocked run instead of a
     * request that quietly fails against a default URL. The exception message names which settings
     * are wrong, never their values.
     */
    public IbmDocling validatedIbmDoclingConfig() {
        List<String> missing = new ArrayList<>();
        if (isBlank(ibmDocling.baseUrl)) {
            missing.add("document-agent.parser.ibm-docling.base-url");
        }
        if (isBlank(ibmDocling.apiKey)) {
            missing.add("document-agent.parser.ibm-docling.api-key");
        }
        if (isBlank(ibmDocling.authHeaderName)) {
            missing.add("document-agent.parser.ibm-docling.auth-header-name");
        }
        if (isBlank(ibmDocling.submitPath) || !ibmDocling.submitPath.startsWith("/")) {
            missing.add("document-agent.parser.ibm-docling.submit-path");
        }
        if (isBlank(ibmDocling.statusPathTemplate) || !ibmDocling.statusPathTemplate.contains("{taskId}")) {
            missing.add("document-agent.parser.ibm-docling.status-path-template");
        }
        if (isBlank(ibmDocling.resultPathTemplate) || !ibmDocling.resultPathTemplate.contains("{taskId}")) {
            missing.add("document-agent.parser.ibm-docling.result-path-template");
        }
        if (ibmDocling.requestTimeoutMs <= 0) {
            missing.add("document-agent.parser.ibm-docling.request-timeout-ms");
        }
        if (!missing.isEmpty()) {
            throw new DocumentParserException(
                    ParserErrorCode.PARSER_NOT_CONFIGURED,
                    "IBM hosted Docling is not configured correctly. Invalid or missing settings: "
                            + String.join(", ", missing) + "."
            );
        }
        return ibmDocling;
    }

    /** {@code true} when the IBM provider has everything it needs. Never logs the values. */
    public boolean isIbmDoclingConfigured() {
        try {
            validatedIbmDoclingConfig();
            return true;
        } catch (DocumentParserException ex) {
            return false;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static class IbmDocling {
        private String baseUrl = "";
        private String apiKey = "";
        /** Header the key is sent in. IBM deployments differ, so this is configurable. */
        private String authHeaderName = "Authorization";
        /** Scheme prefix, e.g. "Bearer". Empty string sends the key bare. */
        private String authHeaderScheme = "Bearer";
        private long requestTimeoutMs = 60_000L;
        private String submitPath = "/v1/convert/source/async";
        private String statusPathTemplate = "/v1/status/poll/{taskId}";
        private String resultPathTemplate = "/v1/result/{taskId}";
        private SourceDelivery sourceDelivery = SourceDelivery.INLINE;
        private ParserProfileCatalog.SubmitEncoding submitEncoding;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey == null ? "" : apiKey.trim();
        }

        public String getAuthHeaderName() {
            return authHeaderName;
        }

        public void setAuthHeaderName(String authHeaderName) {
            this.authHeaderName = authHeaderName == null || authHeaderName.isBlank() ? "Authorization" : authHeaderName.trim();
        }

        public String getAuthHeaderScheme() {
            return authHeaderScheme;
        }

        public void setAuthHeaderScheme(String authHeaderScheme) {
            this.authHeaderScheme = authHeaderScheme == null ? "" : authHeaderScheme.trim();
        }

        public long getRequestTimeoutMs() {
            return requestTimeoutMs;
        }

        public void setRequestTimeoutMs(long requestTimeoutMs) {
            this.requestTimeoutMs = requestTimeoutMs;
        }

        public String getSubmitPath() {
            return submitPath;
        }

        public void setSubmitPath(String submitPath) {
            this.submitPath = submitPath == null || submitPath.isBlank() ? "/v1/convert/source/async" : submitPath.trim();
        }

        public String getStatusPathTemplate() {
            return statusPathTemplate;
        }

        public void setStatusPathTemplate(String statusPathTemplate) {
            this.statusPathTemplate = statusPathTemplate == null || statusPathTemplate.isBlank()
                    ? "/v1/status/poll/{taskId}" : statusPathTemplate.trim();
        }

        public String getResultPathTemplate() {
            return resultPathTemplate;
        }

        public void setResultPathTemplate(String resultPathTemplate) {
            this.resultPathTemplate = resultPathTemplate == null || resultPathTemplate.isBlank()
                    ? "/v1/result/{taskId}" : resultPathTemplate.trim();
        }

        public SourceDelivery getSourceDelivery() {
            return sourceDelivery;
        }

        public void setSourceDelivery(SourceDelivery sourceDelivery) {
            this.sourceDelivery = sourceDelivery == null ? SourceDelivery.INLINE : sourceDelivery;
        }

        /**
         * Submission encoding. When not explicitly set, derived from {@link #submitPath} via
         * {@link ParserProfileCatalog#submitEncodingFor}, matching the source's default-follows-path
         * behavior.
         */
        public ParserProfileCatalog.SubmitEncoding getSubmitEncoding() {
            return submitEncoding != null ? submitEncoding : ParserProfileCatalog.submitEncodingFor(submitPath);
        }

        public void setSubmitEncoding(ParserProfileCatalog.SubmitEncoding submitEncoding) {
            this.submitEncoding = submitEncoding;
        }
    }

    public static class ProcessingLimits {
        private int maxAttempts = 4;
        private long pollInitialDelayMillis = 5_000L;
        private long pollMaxDelayMillis = 60_000L;
        private int maxPollAttempts = 120;
        private long retryBaseDelayMillis = 10_000L;
        private long retryMaxDelayMillis = 300_000L;
        /** A run with no heartbeat for this long is reclaimable. */
        private long staleAfterMillis = 600_000L;
        /** Upper bound on documents handled per worker tick. */
        private int batchSize = 5;
        private int signedUrlTtlSeconds = 300;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = clamp(maxAttempts, 1, 20);
        }

        public long getPollInitialDelayMillis() {
            return pollInitialDelayMillis;
        }

        public void setPollInitialDelayMillis(long pollInitialDelayMillis) {
            this.pollInitialDelayMillis = clamp(pollInitialDelayMillis, 500L, 300_000L);
        }

        public long getPollMaxDelayMillis() {
            return pollMaxDelayMillis;
        }

        public void setPollMaxDelayMillis(long pollMaxDelayMillis) {
            this.pollMaxDelayMillis = clamp(pollMaxDelayMillis, 1_000L, 900_000L);
        }

        public int getMaxPollAttempts() {
            return maxPollAttempts;
        }

        public void setMaxPollAttempts(int maxPollAttempts) {
            this.maxPollAttempts = clamp(maxPollAttempts, 1, 5_000);
        }

        public long getRetryBaseDelayMillis() {
            return retryBaseDelayMillis;
        }

        public void setRetryBaseDelayMillis(long retryBaseDelayMillis) {
            this.retryBaseDelayMillis = clamp(retryBaseDelayMillis, 1_000L, 600_000L);
        }

        public long getRetryMaxDelayMillis() {
            return retryMaxDelayMillis;
        }

        public void setRetryMaxDelayMillis(long retryMaxDelayMillis) {
            this.retryMaxDelayMillis = clamp(retryMaxDelayMillis, 1_000L, 3_600_000L);
        }

        public long getStaleAfterMillis() {
            return staleAfterMillis;
        }

        public void setStaleAfterMillis(long staleAfterMillis) {
            this.staleAfterMillis = clamp(staleAfterMillis, 30_000L, 7_200_000L);
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = clamp(batchSize, 1, 50);
        }

        public int getSignedUrlTtlSeconds() {
            return signedUrlTtlSeconds;
        }

        public void setSignedUrlTtlSeconds(int signedUrlTtlSeconds) {
            this.signedUrlTtlSeconds = clamp(signedUrlTtlSeconds, 30, 3_600);
        }

        private static int clamp(int value, int min, int max) {
            return Math.min(Math.max(value, min), max);
        }

        private static long clamp(long value, long min, long max) {
            return Math.min(Math.max(value, min), max);
        }
    }

    public static class ContextBudget {
        private int maxTokens = 24_000;
        private int maxBytes = 400_000;
        private int maxChunks = 120;
        private int maxTables = 30;

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = clamp(maxTokens, 500, 500_000);
        }

        public int getMaxBytes() {
            return maxBytes;
        }

        public void setMaxBytes(int maxBytes) {
            this.maxBytes = clamp(maxBytes, 2_000, 8_000_000);
        }

        public int getMaxChunks() {
            return maxChunks;
        }

        public void setMaxChunks(int maxChunks) {
            this.maxChunks = clamp(maxChunks, 1, 5_000);
        }

        public int getMaxTables() {
            return maxTables;
        }

        public void setMaxTables(int maxTables) {
            this.maxTables = clamp(maxTables, 1, 500);
        }

        private static int clamp(int value, int min, int max) {
            return Math.min(Math.max(value, min), max);
        }
    }
}

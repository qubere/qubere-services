package ai.qubere.agent.runtime.config;

import ai.qubere.agent.core.AgentRunMode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent-platform")
public class AgentPlatformProperties {

    private Runtime runtime = new Runtime();
    private Registry registry = new Registry();
    private Ai ai = new Ai();
    private Memory memory = new Memory();
    private Tools tools = new Tools();
    private Prompts prompts = new Prompts();
    private Async async = new Async();
    private Governance governance = new Governance();
    private Admin admin = new Admin();
    private Evaluation evaluation = new Evaluation();
    private Map<String, AgentDefinition> definitions = new LinkedHashMap<>();

    public Runtime getRuntime() {
        return runtime;
    }

    public void setRuntime(Runtime runtime) {
        this.runtime = runtime == null ? new Runtime() : runtime;
    }

    public Registry getRegistry() {
        return registry;
    }

    public void setRegistry(Registry registry) {
        this.registry = registry == null ? new Registry() : registry;
    }

    public Ai getAi() {
        return ai;
    }

    public void setAi(Ai ai) {
        this.ai = ai == null ? new Ai() : ai;
    }

    public Memory getMemory() {
        return memory;
    }

    public void setMemory(Memory memory) {
        this.memory = memory == null ? new Memory() : memory;
    }

    public Tools getTools() {
        return tools;
    }

    public void setTools(Tools tools) {
        this.tools = tools == null ? new Tools() : tools;
    }

    public Prompts getPrompts() {
        return prompts;
    }

    public void setPrompts(Prompts prompts) {
        this.prompts = prompts == null ? new Prompts() : prompts;
    }

    public Async getAsync() {
        return async;
    }

    public void setAsync(Async async) {
        this.async = async == null ? new Async() : async;
    }

    public Governance getGovernance() {
        return governance;
    }

    public void setGovernance(Governance governance) {
        this.governance = governance == null ? new Governance() : governance;
    }

    public Evaluation getEvaluation() {
        return evaluation;
    }

    public void setEvaluation(Evaluation evaluation) {
        this.evaluation = evaluation == null ? new Evaluation() : evaluation;
    }

    public Admin getAdmin() {
        return admin;
    }

    public void setAdmin(Admin admin) {
        this.admin = admin == null ? new Admin() : admin;
    }

    public Map<String, AgentDefinition> getDefinitions() {
        return definitions;
    }

    public void setDefinitions(Map<String, AgentDefinition> definitions) {
        this.definitions = definitions == null ? new LinkedHashMap<>() : new LinkedHashMap<>(definitions);
    }

    public static class Runtime {
        private AgentRunMode defaultMode = AgentRunMode.RECOMMEND;
        private boolean dryRun;
        private boolean async;
        private boolean streaming;
        private int maxSteps = 8;
        private double temperature = 0.2d;
        private int maxOutputTokens = 2048;
        private boolean allowToolCalls = true;
        private boolean requireHumanApproval;
        private boolean includeEvidence = true;
        private boolean includeRecommendations = true;
        private int timeoutSeconds = 120;
        private int maxRetries = 2;
        private boolean logPrompts;
        private boolean logToolResults;
        private String responseDetail = "SUMMARY";
        private String priority = "NORMAL";
        private Set<String> allowedTools = new LinkedHashSet<>();

        public AgentRunMode getDefaultMode() {
            return defaultMode;
        }

        public void setDefaultMode(AgentRunMode defaultMode) {
            this.defaultMode = defaultMode == null ? AgentRunMode.RECOMMEND : defaultMode;
        }

        public boolean isDryRun() {
            return dryRun;
        }

        public void setDryRun(boolean dryRun) {
            this.dryRun = dryRun;
        }

        public boolean isAsync() {
            return async;
        }

        public void setAsync(boolean async) {
            this.async = async;
        }

        public boolean isStreaming() {
            return streaming;
        }

        public void setStreaming(boolean streaming) {
            this.streaming = streaming;
        }

        public int getMaxSteps() {
            return maxSteps;
        }

        public void setMaxSteps(int maxSteps) {
            this.maxSteps = maxSteps;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getMaxOutputTokens() {
            return maxOutputTokens;
        }

        public void setMaxOutputTokens(int maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
        }

        public boolean isAllowToolCalls() {
            return allowToolCalls;
        }

        public void setAllowToolCalls(boolean allowToolCalls) {
            this.allowToolCalls = allowToolCalls;
        }

        public boolean isRequireHumanApproval() {
            return requireHumanApproval;
        }

        public void setRequireHumanApproval(boolean requireHumanApproval) {
            this.requireHumanApproval = requireHumanApproval;
        }

        public boolean isIncludeEvidence() {
            return includeEvidence;
        }

        public void setIncludeEvidence(boolean includeEvidence) {
            this.includeEvidence = includeEvidence;
        }

        public boolean isIncludeRecommendations() {
            return includeRecommendations;
        }

        public void setIncludeRecommendations(boolean includeRecommendations) {
            this.includeRecommendations = includeRecommendations;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public boolean isLogPrompts() {
            return logPrompts;
        }

        public void setLogPrompts(boolean logPrompts) {
            this.logPrompts = logPrompts;
        }

        public boolean isLogToolResults() {
            return logToolResults;
        }

        public void setLogToolResults(boolean logToolResults) {
            this.logToolResults = logToolResults;
        }

        public String getResponseDetail() {
            return responseDetail;
        }

        public void setResponseDetail(String responseDetail) {
            this.responseDetail = responseDetail == null || responseDetail.isBlank() ? "SUMMARY" : responseDetail;
        }

        public String getPriority() {
            return priority;
        }

        public void setPriority(String priority) {
            this.priority = priority == null || priority.isBlank() ? "NORMAL" : priority;
        }

        public Set<String> getAllowedTools() {
            return allowedTools;
        }

        public void setAllowedTools(Set<String> allowedTools) {
            this.allowedTools = allowedTools == null ? new LinkedHashSet<>() : new LinkedHashSet<>(allowedTools);
        }
    }

    public static class Registry {
        private boolean strictDescriptorValidation = true;
        private Map<String, String> defaultVersions = new LinkedHashMap<>();

        public boolean isStrictDescriptorValidation() {
            return strictDescriptorValidation;
        }

        public void setStrictDescriptorValidation(boolean strictDescriptorValidation) {
            this.strictDescriptorValidation = strictDescriptorValidation;
        }

        public Map<String, String> getDefaultVersions() {
            return defaultVersions;
        }

        public void setDefaultVersions(Map<String, String> defaultVersions) {
            this.defaultVersions = defaultVersions == null ? new LinkedHashMap<>() : new LinkedHashMap<>(defaultVersions);
        }
    }

    public static class Ai {
        private String defaultProvider = "openai";
        private String defaultModel = "default";
        private BigDecimal maxEstimatedCostUsd = BigDecimal.ZERO;

        public String getDefaultProvider() {
            return defaultProvider;
        }

        public void setDefaultProvider(String defaultProvider) {
            this.defaultProvider = defaultProvider == null || defaultProvider.isBlank() ? "openai" : defaultProvider;
        }

        public String getDefaultModel() {
            return defaultModel;
        }

        public void setDefaultModel(String defaultModel) {
            this.defaultModel = defaultModel == null || defaultModel.isBlank() ? "default" : defaultModel;
        }

        public BigDecimal getMaxEstimatedCostUsd() {
            return maxEstimatedCostUsd;
        }

        public void setMaxEstimatedCostUsd(BigDecimal maxEstimatedCostUsd) {
            this.maxEstimatedCostUsd = maxEstimatedCostUsd == null ? BigDecimal.ZERO : maxEstimatedCostUsd;
        }
    }

    public static class Memory {
        private boolean enabled = true;
        private String provider = "in-memory";
        private int maxResults = 5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider == null || provider.isBlank() ? "in-memory" : provider;
        }

        public int getMaxResults() {
            return maxResults;
        }

        public void setMaxResults(int maxResults) {
            this.maxResults = maxResults;
        }
    }

    public static class Tools {
        private boolean enabled = true;
        private boolean auditEnabled = true;
        private boolean approvalRequiredForDestructive = true;
        private int maxToolCalls = 20;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAuditEnabled() {
            return auditEnabled;
        }

        public void setAuditEnabled(boolean auditEnabled) {
            this.auditEnabled = auditEnabled;
        }

        public boolean isApprovalRequiredForDestructive() {
            return approvalRequiredForDestructive;
        }

        public void setApprovalRequiredForDestructive(boolean approvalRequiredForDestructive) {
            this.approvalRequiredForDestructive = approvalRequiredForDestructive;
        }

        public int getMaxToolCalls() {
            return maxToolCalls;
        }

        public void setMaxToolCalls(int maxToolCalls) {
            this.maxToolCalls = maxToolCalls;
        }
    }

    public static class Prompts {
        private String provider = "in-memory";
        private boolean seedEnabled = true;
        private List<PromptSeed> seeds = new ArrayList<>();

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider == null || provider.isBlank() ? "in-memory" : provider;
        }

        public boolean isSeedEnabled() {
            return seedEnabled;
        }

        public void setSeedEnabled(boolean seedEnabled) {
            this.seedEnabled = seedEnabled;
        }

        public List<PromptSeed> getSeeds() {
            return seeds;
        }

        public void setSeeds(List<PromptSeed> seeds) {
            this.seeds = seeds == null ? new ArrayList<>() : new ArrayList<>(seeds);
        }

        public static class PromptSeed {
            private String promptId;
            private String agentId;
            private String version;
            private String status = "DRAFT";
            private String systemTemplate;
            private String userTemplate;
            private Map<String, Object> metadata = new LinkedHashMap<>();
            private boolean overwrite;

            public String getPromptId() {
                return promptId;
            }

            public void setPromptId(String promptId) {
                this.promptId = promptId;
            }

            public String getAgentId() {
                return agentId;
            }

            public void setAgentId(String agentId) {
                this.agentId = agentId;
            }

            public String getVersion() {
                return version;
            }

            public void setVersion(String version) {
                this.version = version;
            }

            public String getStatus() {
                return status;
            }

            public void setStatus(String status) {
                this.status = status == null || status.isBlank() ? "DRAFT" : status;
            }

            public String getSystemTemplate() {
                return systemTemplate;
            }

            public void setSystemTemplate(String systemTemplate) {
                this.systemTemplate = systemTemplate;
            }

            public String getUserTemplate() {
                return userTemplate;
            }

            public void setUserTemplate(String userTemplate) {
                this.userTemplate = userTemplate;
            }

            public Map<String, Object> getMetadata() {
                return metadata;
            }

            public void setMetadata(Map<String, Object> metadata) {
                this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
            }

            public boolean isOverwrite() {
                return overwrite;
            }

            public void setOverwrite(boolean overwrite) {
                this.overwrite = overwrite;
            }
        }
    }

    public static class Async {
        private boolean enabled = true;
        private boolean workerEnabled;
        private long pollIntervalMillis = 1000L;
        private int maxRunsPerPoll = 1;
        private long approvalExpirationMinutes = 60L;
        private Callback callback = new Callback();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isWorkerEnabled() {
            return workerEnabled;
        }

        public void setWorkerEnabled(boolean workerEnabled) {
            this.workerEnabled = workerEnabled;
        }

        public long getPollIntervalMillis() {
            return pollIntervalMillis;
        }

        public void setPollIntervalMillis(long pollIntervalMillis) {
            this.pollIntervalMillis = Math.max(100L, pollIntervalMillis);
        }

        public int getMaxRunsPerPoll() {
            return maxRunsPerPoll;
        }

        public void setMaxRunsPerPoll(int maxRunsPerPoll) {
            this.maxRunsPerPoll = Math.max(1, maxRunsPerPoll);
        }

        public long getApprovalExpirationMinutes() {
            return approvalExpirationMinutes;
        }

        public void setApprovalExpirationMinutes(long approvalExpirationMinutes) {
            this.approvalExpirationMinutes = Math.max(1L, approvalExpirationMinutes);
        }

        public Callback getCallback() {
            return callback;
        }

        public void setCallback(Callback callback) {
            this.callback = callback == null ? new Callback() : callback;
        }

        public static class Callback {
            private boolean enabled;
            private int maxAttempts = 3;
            private long retryBackoffMillis = 500L;
            private int timeoutSeconds = 5;
            private String signingSecret;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public int getMaxAttempts() {
                return maxAttempts;
            }

            public void setMaxAttempts(int maxAttempts) {
                this.maxAttempts = Math.max(1, maxAttempts);
            }

            public long getRetryBackoffMillis() {
                return retryBackoffMillis;
            }

            public void setRetryBackoffMillis(long retryBackoffMillis) {
                this.retryBackoffMillis = Math.max(0L, retryBackoffMillis);
            }

            public int getTimeoutSeconds() {
                return timeoutSeconds;
            }

            public void setTimeoutSeconds(int timeoutSeconds) {
                this.timeoutSeconds = Math.max(1, timeoutSeconds);
            }

            public String getSigningSecret() {
                return signingSecret;
            }

            public void setSigningSecret(String signingSecret) {
                this.signingSecret = signingSecret;
            }
        }
    }

    public static class Governance {
        private boolean enabled = true;
        private int maxRunsPerTenantPerMinute;
        private int maxRunsPerActorPerMinute;
        private BigDecimal maxEstimatedCostUsdPerRun = BigDecimal.ZERO;
        private BigDecimal estimatedCostUsdPerThousandTokens = BigDecimal.ZERO;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxRunsPerTenantPerMinute() {
            return maxRunsPerTenantPerMinute;
        }

        public void setMaxRunsPerTenantPerMinute(int maxRunsPerTenantPerMinute) {
            this.maxRunsPerTenantPerMinute = Math.max(0, maxRunsPerTenantPerMinute);
        }

        public int getMaxRunsPerActorPerMinute() {
            return maxRunsPerActorPerMinute;
        }

        public void setMaxRunsPerActorPerMinute(int maxRunsPerActorPerMinute) {
            this.maxRunsPerActorPerMinute = Math.max(0, maxRunsPerActorPerMinute);
        }

        public BigDecimal getMaxEstimatedCostUsdPerRun() {
            return maxEstimatedCostUsdPerRun;
        }

        public void setMaxEstimatedCostUsdPerRun(BigDecimal maxEstimatedCostUsdPerRun) {
            this.maxEstimatedCostUsdPerRun = maxEstimatedCostUsdPerRun == null ? BigDecimal.ZERO : maxEstimatedCostUsdPerRun;
        }

        public BigDecimal getEstimatedCostUsdPerThousandTokens() {
            return estimatedCostUsdPerThousandTokens;
        }

        public void setEstimatedCostUsdPerThousandTokens(BigDecimal estimatedCostUsdPerThousandTokens) {
            this.estimatedCostUsdPerThousandTokens = estimatedCostUsdPerThousandTokens == null ? BigDecimal.ZERO : estimatedCostUsdPerThousandTokens;
        }
    }

    public static class Admin {
        private boolean enabled;
        private String token;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }

    public static class Evaluation {
        private Set<String> datasetLocations = new LinkedHashSet<>(Set.of("classpath*:agent-evaluation/*.json"));
        private boolean failOnInvalidDataset = true;

        public Set<String> getDatasetLocations() {
            return datasetLocations;
        }

        public void setDatasetLocations(Set<String> datasetLocations) {
            this.datasetLocations = datasetLocations == null ? new LinkedHashSet<>() : new LinkedHashSet<>(datasetLocations);
        }

        public boolean isFailOnInvalidDataset() {
            return failOnInvalidDataset;
        }

        public void setFailOnInvalidDataset(boolean failOnInvalidDataset) {
            this.failOnInvalidDataset = failOnInvalidDataset;
        }
    }
    public static class AgentDefinition {
        private boolean enabled = true;
        private String modelProvider;
        private String modelName;
        private String promptVersion = "latest";
        private Boolean memoryEnabled;
        private Integer maxMemoryResults;
        private Integer maxToolCalls;
        private Integer timeoutSeconds;
        private Integer maxRetries;
        private BigDecimal maxEstimatedCostUsd;
        private Boolean requireHumanApproval;
        private Set<String> allowedTools = new LinkedHashSet<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getModelProvider() {
            return modelProvider;
        }

        public void setModelProvider(String modelProvider) {
            this.modelProvider = modelProvider;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public String getPromptVersion() {
            return promptVersion;
        }

        public void setPromptVersion(String promptVersion) {
            this.promptVersion = promptVersion == null || promptVersion.isBlank() ? "latest" : promptVersion;
        }

        public Boolean getMemoryEnabled() {
            return memoryEnabled;
        }

        public void setMemoryEnabled(Boolean memoryEnabled) {
            this.memoryEnabled = memoryEnabled;
        }

        public Integer getMaxMemoryResults() {
            return maxMemoryResults;
        }

        public void setMaxMemoryResults(Integer maxMemoryResults) {
            this.maxMemoryResults = maxMemoryResults;
        }

        public Integer getMaxToolCalls() {
            return maxToolCalls;
        }

        public void setMaxToolCalls(Integer maxToolCalls) {
            this.maxToolCalls = maxToolCalls;
        }

        public Integer getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(Integer timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public Integer getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
        }

        public BigDecimal getMaxEstimatedCostUsd() {
            return maxEstimatedCostUsd;
        }

        public void setMaxEstimatedCostUsd(BigDecimal maxEstimatedCostUsd) {
            this.maxEstimatedCostUsd = maxEstimatedCostUsd;
        }

        public Boolean getRequireHumanApproval() {
            return requireHumanApproval;
        }

        public void setRequireHumanApproval(Boolean requireHumanApproval) {
            this.requireHumanApproval = requireHumanApproval;
        }

        public Set<String> getAllowedTools() {
            return allowedTools;
        }

        public void setAllowedTools(Set<String> allowedTools) {
            this.allowedTools = allowedTools == null ? new LinkedHashSet<>() : new LinkedHashSet<>(allowedTools);
        }
    }
}

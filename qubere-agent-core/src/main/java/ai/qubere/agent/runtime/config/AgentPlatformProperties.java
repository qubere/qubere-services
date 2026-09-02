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
    private Security security = new Security();
    private Observability observability = new Observability();
    private Admin admin = new Admin();
    private Evaluation evaluation = new Evaluation();
    private Guardrails guardrails = new Guardrails();
    private Resilience resilience = new Resilience();
    private Orchestration orchestration = new Orchestration();
    private Mcp mcp = new Mcp();
    private Map<String, AgentDefinition> definitions = new LinkedHashMap<>();

    public Mcp getMcp() {
        return mcp;
    }

    public void setMcp(Mcp mcp) {
        this.mcp = mcp == null ? new Mcp() : mcp;
    }

    public Orchestration getOrchestration() {
        return orchestration;
    }

    public void setOrchestration(Orchestration orchestration) {
        this.orchestration = orchestration == null ? new Orchestration() : orchestration;
    }

    public Resilience getResilience() {
        return resilience;
    }

    public void setResilience(Resilience resilience) {
        this.resilience = resilience == null ? new Resilience() : resilience;
    }

    public Guardrails getGuardrails() {
        return guardrails;
    }

    public void setGuardrails(Guardrails guardrails) {
        this.guardrails = guardrails == null ? new Guardrails() : guardrails;
    }

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

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security == null ? new Security() : security;
    }

    public Observability getObservability() {
        return observability;
    }

    public void setObservability(Observability observability) {
        this.observability = observability == null ? new Observability() : observability;
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
        private RuntimeExecutor executor = new RuntimeExecutor();

        public RuntimeExecutor getExecutor() {
            return executor;
        }

        public void setExecutor(RuntimeExecutor executor) {
            this.executor = executor == null ? new RuntimeExecutor() : executor;
        }

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

    public static class RuntimeExecutor {
        private int corePoolSize = 8;
        private int maxPoolSize = 32;
        private int queueCapacity = 200;
        private String threadNamePrefix = "agent-invoke-";
        private int awaitTerminationSeconds = 30;

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public String getThreadNamePrefix() {
            return threadNamePrefix;
        }

        public void setThreadNamePrefix(String threadNamePrefix) {
            this.threadNamePrefix = threadNamePrefix == null || threadNamePrefix.isBlank() ? "agent-invoke-" : threadNamePrefix;
        }

        public int getAwaitTerminationSeconds() {
            return awaitTerminationSeconds;
        }

        public void setAwaitTerminationSeconds(int awaitTerminationSeconds) {
            this.awaitTerminationSeconds = awaitTerminationSeconds;
        }
    }

    public static class Guardrails {
        private boolean enabled = true;
        private int maxInputSizeBytes = 200_000;
        private Set<String> denylistPatterns = new LinkedHashSet<>(List.of(
                "(?i)ignore (all|any|the)?\\s*previous instructions",
                "(?i)disregard (all|any|the)?\\s*(system|prior) prompt",
                "(?i)reveal (your|the) system prompt",
                "(?i)you are now (in )?dan mode",
                "(?i)act as if (you have|there are) no restrictions"
        ));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxInputSizeBytes() {
            return maxInputSizeBytes;
        }

        public void setMaxInputSizeBytes(int maxInputSizeBytes) {
            this.maxInputSizeBytes = maxInputSizeBytes;
        }

        public Set<String> getDenylistPatterns() {
            return denylistPatterns;
        }

        public void setDenylistPatterns(Set<String> denylistPatterns) {
            this.denylistPatterns = denylistPatterns == null ? new LinkedHashSet<>() : new LinkedHashSet<>(denylistPatterns);
        }
    }

    /**
     * Model Context Protocol exposure settings. The framework provides the governed bridge; the
     * MCP transport itself is owned by the deployed application.
     */
    public static class Mcp {
        /**
         * Whether to register the MCP tool bridge. Disabled by default: exposing internal tools
         * to external MCP clients is a deliberate decision, not something adding the framework
         * should enable implicitly.
         */
        private boolean enabled = false;
        /**
         * Tools exposed over MCP. An empty set exposes every registered tool, which is convenient
         * in development but should be narrowed in production so adding an internal tool does not
         * silently publish it to external clients.
         */
        private Set<String> exposedTools = new LinkedHashSet<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Set<String> getExposedTools() {
            return exposedTools;
        }

        public void setExposedTools(Set<String> exposedTools) {
            this.exposedTools = exposedTools == null ? new LinkedHashSet<>() : new LinkedHashSet<>(exposedTools);
        }
    }

    /**
     * Multi-agent orchestration settings.
     */
    public static class Orchestration {
        /**
         * Whether to register the {@code agent.call} tool that lets an orchestrator agent (or an
         * LLM driving it) invoke other registered agents as sub-agents. Disabled by default
         * because registering it makes every agent in the application reachable as a tool;
         * deployments that enable it should still restrict delegation targets through the normal
         * tool allow-list.
         */
        private boolean agentCallToolEnabled = false;
        /** Aggregate cap on agent invocations per workflow. {@code 0} disables the limit. */
        private int maxAgentInvocationsPerWorkflow = 25;
        /** Aggregate cap on tool calls per workflow. {@code 0} disables the limit. */
        private int maxToolCallsPerWorkflow = 100;
        /** Aggregate model-spend cap per workflow. {@code 0} disables the limit. */
        private BigDecimal maxEstimatedCostUsdPerWorkflow = BigDecimal.ZERO;
        /**
         * Maximum delegation hops below the workflow root for {@code agent.call}. {@code 0}
         * disables the depth guard; cycle detection always applies regardless of this setting.
         */
        private int maxDelegationDepth = 8;
        private Remote remote = new Remote();

        public int getMaxDelegationDepth() {
            return maxDelegationDepth;
        }

        public void setMaxDelegationDepth(int maxDelegationDepth) {
            this.maxDelegationDepth = Math.max(0, maxDelegationDepth);
        }

        public boolean isAgentCallToolEnabled() {
            return agentCallToolEnabled;
        }

        public void setAgentCallToolEnabled(boolean agentCallToolEnabled) {
            this.agentCallToolEnabled = agentCallToolEnabled;
        }

        public int getMaxAgentInvocationsPerWorkflow() {
            return maxAgentInvocationsPerWorkflow;
        }

        public void setMaxAgentInvocationsPerWorkflow(int maxAgentInvocationsPerWorkflow) {
            this.maxAgentInvocationsPerWorkflow = Math.max(0, maxAgentInvocationsPerWorkflow);
        }

        public int getMaxToolCallsPerWorkflow() {
            return maxToolCallsPerWorkflow;
        }

        public void setMaxToolCallsPerWorkflow(int maxToolCallsPerWorkflow) {
            this.maxToolCallsPerWorkflow = Math.max(0, maxToolCallsPerWorkflow);
        }

        public BigDecimal getMaxEstimatedCostUsdPerWorkflow() {
            return maxEstimatedCostUsdPerWorkflow;
        }

        public void setMaxEstimatedCostUsdPerWorkflow(BigDecimal maxEstimatedCostUsdPerWorkflow) {
            this.maxEstimatedCostUsdPerWorkflow = maxEstimatedCostUsdPerWorkflow == null ? BigDecimal.ZERO : maxEstimatedCostUsdPerWorkflow;
        }

        public Remote getRemote() {
            return remote;
        }

        public void setRemote(Remote remote) {
            this.remote = remote == null ? new Remote() : remote;
        }

        /**
         * Cross-service agent invocation settings, used when an orchestrator delegates to an
         * agent hosted by a different Spring Boot service.
         */
        public static class Remote {
            /**
             * Whether to auto-configure the HTTP {@code RemoteAgentClient}. Disabled by default
             * because most deployments run agents in-process and should not gain an outbound
             * HTTP dependency implicitly.
             */
            private boolean enabled = false;
            /** Base URL of the remote agent service, e.g. {@code http://invoice-agent:8081}. */
            private String baseUrl;
            private int timeoutSeconds = 60;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getBaseUrl() {
                return baseUrl;
            }

            public void setBaseUrl(String baseUrl) {
                this.baseUrl = baseUrl;
            }

            public int getTimeoutSeconds() {
                return timeoutSeconds;
            }

            public void setTimeoutSeconds(int timeoutSeconds) {
                this.timeoutSeconds = Math.max(1, timeoutSeconds);
            }
        }
    }

    /**
     * Circuit breaker / bulkhead settings applied per distinct call key (per model name, per
     * tool name) when {@code enabled=true} and Resilience4j is present on the classpath.
     * Disabled by default: resilience defaults are workload-specific and should be tuned before
     * enabling in production, and enabling unconditionally would surprise applications that add
     * the framework without opting into new failure-handling behavior.
     */
    public static class Resilience {
        private boolean enabled = false;
        private float failureRateThreshold = 50.0f;
        private int slidingWindowSize = 10;
        private int waitDurationInOpenStateSeconds = 30;
        private int permittedNumberOfCallsInHalfOpenState = 3;
        private int bulkheadMaxConcurrentCalls = 10;
        private long bulkheadMaxWaitDurationMillis = 0L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public float getFailureRateThreshold() {
            return failureRateThreshold;
        }

        public void setFailureRateThreshold(float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        public int getSlidingWindowSize() {
            return slidingWindowSize;
        }

        public void setSlidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = Math.max(1, slidingWindowSize);
        }

        public int getWaitDurationInOpenStateSeconds() {
            return waitDurationInOpenStateSeconds;
        }

        public void setWaitDurationInOpenStateSeconds(int waitDurationInOpenStateSeconds) {
            this.waitDurationInOpenStateSeconds = Math.max(1, waitDurationInOpenStateSeconds);
        }

        public int getPermittedNumberOfCallsInHalfOpenState() {
            return permittedNumberOfCallsInHalfOpenState;
        }

        public void setPermittedNumberOfCallsInHalfOpenState(int permittedNumberOfCallsInHalfOpenState) {
            this.permittedNumberOfCallsInHalfOpenState = Math.max(1, permittedNumberOfCallsInHalfOpenState);
        }

        public int getBulkheadMaxConcurrentCalls() {
            return bulkheadMaxConcurrentCalls;
        }

        public void setBulkheadMaxConcurrentCalls(int bulkheadMaxConcurrentCalls) {
            this.bulkheadMaxConcurrentCalls = Math.max(1, bulkheadMaxConcurrentCalls);
        }

        public long getBulkheadMaxWaitDurationMillis() {
            return bulkheadMaxWaitDurationMillis;
        }

        public void setBulkheadMaxWaitDurationMillis(long bulkheadMaxWaitDurationMillis) {
            this.bulkheadMaxWaitDurationMillis = Math.max(0L, bulkheadMaxWaitDurationMillis);
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
        private Map<String, ModelTariff> tariffs = new LinkedHashMap<>();

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

        public Map<String, ModelTariff> getTariffs() {
            return tariffs;
        }

        public void setTariffs(Map<String, ModelTariff> tariffs) {
            this.tariffs = tariffs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(tariffs);
        }
    }

    /**
     * Per-model $/1000-token pricing used to compute real {@code estimatedCostUsd} for
     * {@code agent_model_usage} rows and to enforce {@code ResolvedAgentPolicy.maxEstimatedCostUsd()}
     * hard budgets. Configure under {@code agent-platform.ai.tariffs.<model-name>}. Models without a
     * configured tariff produce a {@code null} cost, matching prior behavior.
     */
    public static class ModelTariff {
        private BigDecimal inputCostUsdPerThousandTokens = BigDecimal.ZERO;
        private BigDecimal outputCostUsdPerThousandTokens = BigDecimal.ZERO;

        public BigDecimal getInputCostUsdPerThousandTokens() {
            return inputCostUsdPerThousandTokens;
        }

        public void setInputCostUsdPerThousandTokens(BigDecimal inputCostUsdPerThousandTokens) {
            this.inputCostUsdPerThousandTokens = inputCostUsdPerThousandTokens == null ? BigDecimal.ZERO : inputCostUsdPerThousandTokens;
        }

        public BigDecimal getOutputCostUsdPerThousandTokens() {
            return outputCostUsdPerThousandTokens;
        }

        public void setOutputCostUsdPerThousandTokens(BigDecimal outputCostUsdPerThousandTokens) {
            this.outputCostUsdPerThousandTokens = outputCostUsdPerThousandTokens == null ? BigDecimal.ZERO : outputCostUsdPerThousandTokens;
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
        private Queue queue = new Queue();
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

        public Queue getQueue() {
            return queue;
        }

        public void setQueue(Queue queue) {
            this.queue = queue == null ? new Queue() : queue;
        }

        public Callback getCallback() {
            return callback;
        }

        public void setCallback(Callback callback) {
            this.callback = callback == null ? new Callback() : callback;
        }

        public static class Queue {
            private String type = "memory";
            private int maxHealthyDepth;

            public String getType() {
                return type;
            }

            public void setType(String type) {
                this.type = type == null || type.isBlank() ? "memory" : type.trim().toLowerCase();
            }

            public int getMaxHealthyDepth() {
                return maxHealthyDepth;
            }

            public void setMaxHealthyDepth(int maxHealthyDepth) {
                this.maxHealthyDepth = Math.max(0, maxHealthyDepth);
            }
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
        /**
         * When {@code true} (default, fail-closed), agents whose {@code AgentDescriptor.riskLevel()}
         * is {@code HIGH} or {@code CRITICAL} default to requiring human approval even if
         * {@code agent-platform.runtime.require-human-approval} is {@code false} and the agent's
         * own definition does not explicitly set {@code require-human-approval}. Set an explicit
         * {@code require-human-approval} on a specific agent definition to override this per agent.
         */
        private boolean requireApprovalForHighRisk = true;

        public boolean isRequireApprovalForHighRisk() {
            return requireApprovalForHighRisk;
        }

        public void setRequireApprovalForHighRisk(boolean requireApprovalForHighRisk) {
            this.requireApprovalForHighRisk = requireApprovalForHighRisk;
        }

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


    public static class Observability {
        private OpenTelemetry openTelemetry = new OpenTelemetry();

        public OpenTelemetry getOpenTelemetry() {
            return openTelemetry;
        }

        public void setOpenTelemetry(OpenTelemetry openTelemetry) {
            this.openTelemetry = openTelemetry == null ? new OpenTelemetry() : openTelemetry;
        }

        public static class OpenTelemetry {
            private boolean enabled;
            private String serviceName = "qubere-agents";
            private boolean includeTenant = true;
            private boolean includeActor;
            private int maxBufferedEvents = 1000;
            private Otlp otlp = new Otlp();

            public Otlp getOtlp() {
                return otlp;
            }

            public void setOtlp(Otlp otlp) {
                this.otlp = otlp == null ? new Otlp() : otlp;
            }

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getServiceName() {
                return serviceName;
            }

            public void setServiceName(String serviceName) {
                this.serviceName = serviceName == null || serviceName.isBlank() ? "qubere-agents" : serviceName;
            }

            public boolean isIncludeTenant() {
                return includeTenant;
            }

            public void setIncludeTenant(boolean includeTenant) {
                this.includeTenant = includeTenant;
            }

            public boolean isIncludeActor() {
                return includeActor;
            }

            public void setIncludeActor(boolean includeActor) {
                this.includeActor = includeActor;
            }

            public int getMaxBufferedEvents() {
                return maxBufferedEvents;
            }

            public void setMaxBufferedEvents(int maxBufferedEvents) {
                this.maxBufferedEvents = Math.max(100, maxBufferedEvents);
            }
        }

        /**
         * Real OTLP span export, layered on top of the framework's OpenTelemetry-shaped event
         * foundation. Requires {@code opentelemetry-sdk} and {@code opentelemetry-exporter-otlp}
         * on the classpath (declared optional by the framework) and
         * {@code agent-platform.observability.open-telemetry.enabled=true} plus
         * {@code agent-platform.observability.open-telemetry.otlp.enabled=true}.
         */
        public static class Otlp {
            private boolean enabled = false;
            private String endpoint = "http://localhost:4317";
            private String protocol = "grpc";
            private int timeoutSeconds = 10;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getEndpoint() {
                return endpoint;
            }

            public void setEndpoint(String endpoint) {
                this.endpoint = endpoint == null || endpoint.isBlank() ? "http://localhost:4317" : endpoint;
            }

            public String getProtocol() {
                return protocol;
            }

            public void setProtocol(String protocol) {
                this.protocol = protocol == null || protocol.isBlank() ? "grpc" : protocol.trim().toLowerCase();
            }

            public int getTimeoutSeconds() {
                return timeoutSeconds;
            }

            public void setTimeoutSeconds(int timeoutSeconds) {
                this.timeoutSeconds = Math.max(1, timeoutSeconds);
            }
        }
    }
    public static class Security {
        private String authorizationMode = "permissive";
        private boolean requireTenant = true;
        private boolean requireActor = true;
        private Set<String> allowedTenants = new LinkedHashSet<>();
        private Set<String> requiredRunPermissions = new LinkedHashSet<>();
        private Map<String, Set<String>> agentRequiredPermissions = new LinkedHashMap<>();
        /**
         * Whether X-Tenant-Id / X-Actor-Id / X-Agent-Permissions inbound HTTP headers may be
         * trusted directly as caller identity. Left {@code null} by default so the resolved
         * value is derived from {@link #authorizationMode}: {@code true} for {@code permissive}
         * (local development) and {@code false} for {@code strict} (production). Set explicitly
         * to override the derived behavior. Production deployments should leave this {@code false}
         * and supply a custom {@code AgentCallerIdentityResolver} bean backed by a verified
         * identity provider (JWT/OAuth/Spring Security) instead of trusting raw headers.
         */
        private Boolean trustInboundHeaders;
        private Jwt jwt = new Jwt();

        public String getAuthorizationMode() {
            return authorizationMode;
        }

        public void setAuthorizationMode(String authorizationMode) {
            this.authorizationMode = authorizationMode == null || authorizationMode.isBlank() ? "permissive" : authorizationMode.trim();
        }

        public boolean isRequireTenant() {
            return requireTenant;
        }

        public void setRequireTenant(boolean requireTenant) {
            this.requireTenant = requireTenant;
        }

        public boolean isRequireActor() {
            return requireActor;
        }

        public void setRequireActor(boolean requireActor) {
            this.requireActor = requireActor;
        }

        public Set<String> getAllowedTenants() {
            return allowedTenants;
        }

        public void setAllowedTenants(Set<String> allowedTenants) {
            this.allowedTenants = allowedTenants == null ? new LinkedHashSet<>() : new LinkedHashSet<>(allowedTenants);
        }

        public Set<String> getRequiredRunPermissions() {
            return requiredRunPermissions;
        }

        public void setRequiredRunPermissions(Set<String> requiredRunPermissions) {
            this.requiredRunPermissions = requiredRunPermissions == null ? new LinkedHashSet<>() : new LinkedHashSet<>(requiredRunPermissions);
        }

        public Map<String, Set<String>> getAgentRequiredPermissions() {
            return agentRequiredPermissions;
        }

        public void setAgentRequiredPermissions(Map<String, Set<String>> agentRequiredPermissions) {
            this.agentRequiredPermissions = new LinkedHashMap<>();
            if (agentRequiredPermissions != null) {
                agentRequiredPermissions.forEach((agentId, permissions) ->
                        this.agentRequiredPermissions.put(agentId, permissions == null ? new LinkedHashSet<>() : new LinkedHashSet<>(permissions)));
            }
        }

        public Boolean getTrustInboundHeaders() {
            return trustInboundHeaders;
        }

        public void setTrustInboundHeaders(Boolean trustInboundHeaders) {
            this.trustInboundHeaders = trustInboundHeaders;
        }

        /**
         * Resolves the effective trust decision: the explicit setting if present, otherwise
         * {@code true} only for permissive mode. Strict mode defaults to {@code false} (fail-closed).
         */
        public boolean resolveTrustInboundHeaders() {
            if (trustInboundHeaders != null) {
                return trustInboundHeaders;
            }
            return !"strict".equalsIgnoreCase(authorizationMode);
        }

        public Jwt getJwt() {
            return jwt;
        }

        public void setJwt(Jwt jwt) {
            this.jwt = jwt == null ? new Jwt() : jwt;
        }

        /**
         * OAuth2/JWT-backed caller identity settings. Opt-in and additive to the framework's
         * existing identity seam: enabling this registers {@link ai.qubere.agent.runtime.security.JwtCallerIdentityResolver}
         * as the {@code AgentCallerIdentityResolver}, provided a {@code JwtDecoder} bean is also
         * present (typically via {@code spring-boot-starter-oauth2-resource-server} and
         * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}).
         */
        public static class Jwt {
            /**
             * Whether inbound requests should be authenticated via a validated JWT bearer token.
             * Disabled by default: this only activates when both this flag is {@code true} and a
             * {@code JwtDecoder} bean exists, so simply adding the optional dependency does not
             * silently change authentication behavior.
             */
            private boolean enabled = false;
            /** Claim carrying the tenant id. Identity providers vary widely here. */
            private String tenantClaim = "tenant_id";
            /** Claim carrying the actor id; falls back to the standard {@code sub} claim. */
            private String actorClaim = "sub";
            /** Claim carrying permissions/scopes, either a delimited string or a JSON array. */
            private String permissionsClaim = "scope";

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getTenantClaim() {
                return tenantClaim;
            }

            public void setTenantClaim(String tenantClaim) {
                this.tenantClaim = tenantClaim == null || tenantClaim.isBlank() ? "tenant_id" : tenantClaim;
            }

            public String getActorClaim() {
                return actorClaim;
            }

            public void setActorClaim(String actorClaim) {
                this.actorClaim = actorClaim == null || actorClaim.isBlank() ? "sub" : actorClaim;
            }

            public String getPermissionsClaim() {
                return permissionsClaim;
            }

            public void setPermissionsClaim(String permissionsClaim) {
                this.permissionsClaim = permissionsClaim == null || permissionsClaim.isBlank() ? "scope" : permissionsClaim;
            }
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
        /**
         * Where golden datasets are loaded from:
         * <ul>
         *   <li>{@code classpath} (default) — datasets ship and version with the application.</li>
         *   <li>{@code database} — datasets are curated operationally in {@code agent_evaluation_dataset}.</li>
         *   <li>{@code database-then-classpath} — database entries override same-named classpath
         *       datasets, and classpath datasets remain available as a fallback.</li>
         * </ul>
         * Database modes require {@code qubere-agent-storage} on the classpath.
         */
        private String datasetProvider = "classpath";

        public String getDatasetProvider() {
            return datasetProvider;
        }

        public void setDatasetProvider(String datasetProvider) {
            this.datasetProvider = datasetProvider == null || datasetProvider.isBlank()
                    ? "classpath"
                    : datasetProvider.trim().toLowerCase();
        }

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
        /**
         * Optional per-agent rate limit (runs per rolling minute, across all tenants/actors).
         * {@code null} (default) means no per-agent limit is enforced; only the platform-wide
         * tenant/actor governance limits apply.
         */
        private Integer maxRunsPerMinute;

        public Integer getMaxRunsPerMinute() {
            return maxRunsPerMinute;
        }

        public void setMaxRunsPerMinute(Integer maxRunsPerMinute) {
            this.maxRunsPerMinute = maxRunsPerMinute;
        }

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

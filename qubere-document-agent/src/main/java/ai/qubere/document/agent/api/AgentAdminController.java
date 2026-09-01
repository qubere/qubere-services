package ai.qubere.document.agent.api;

import ai.qubere.agent.api.AgentOutput;
import ai.qubere.agent.async.AgentApprovalRequest;
import ai.qubere.agent.async.AgentApprovalStatus;
import ai.qubere.agent.async.AgentApprovalStore;
import ai.qubere.agent.core.AgentErrorCode;
import ai.qubere.agent.core.AgentExecutionException;
import ai.qubere.agent.evaluation.AgentEvaluator;
import ai.qubere.agent.evaluation.AgentReplayRequest;
import ai.qubere.agent.evaluation.AgentReplayService;
import ai.qubere.agent.evaluation.AgentRunSummary;
import ai.qubere.agent.evaluation.EvaluationResult;
import ai.qubere.agent.evaluation.EvaluationResultStore;
import ai.qubere.agent.evaluation.InMemoryAgentObservabilityService;
import ai.qubere.agent.evaluation.StoredEvaluationResult;
import ai.qubere.agent.prompts.PromptStatus;
import ai.qubere.agent.prompts.PromptTemplate;
import ai.qubere.agent.prompts.PromptVersionStore;
import ai.qubere.agent.runtime.AgentPipelineEvent;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agents/admin")
@ConditionalOnProperty(prefix = "agent-platform.admin", name = "enabled", havingValue = "true")
public class AgentAdminController {

    private static final String ADMIN_TOKEN_HEADER = "X-Agent-Admin-Token";
    private static final int DEFAULT_LIST_LIMIT = 25;
    private static final int MAX_LIST_LIMIT = 500;

    private final AgentPlatformProperties properties;
    private final AgentEvaluator evaluator;
    private final EvaluationResultStore resultStore;
    private final AgentReplayService replayService;
    private final InMemoryAgentObservabilityService observabilityService;
    private final PromptVersionStore promptVersionStore;
    private final AgentApprovalStore approvalStore;

    public AgentAdminController(
            AgentPlatformProperties properties,
            AgentEvaluator evaluator,
            EvaluationResultStore resultStore,
            AgentReplayService replayService,
            InMemoryAgentObservabilityService observabilityService,
            PromptVersionStore promptVersionStore,
            AgentApprovalStore approvalStore
    ) {
        this.properties = properties;
        this.evaluator = evaluator;
        this.resultStore = resultStore;
        this.replayService = replayService;
        this.observabilityService = observabilityService;
        this.promptVersionStore = promptVersionStore;
        this.approvalStore = approvalStore;
    }

    @PostMapping("/evaluations/{datasetName}/run")
    public EvaluationResult runEvaluation(
            @PathVariable String datasetName,
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token
    ) {
        requireAdmin(token);
        return evaluator.evaluate(datasetName);
    }

    @GetMapping("/evaluations")
    public Collection<StoredEvaluationResult> listEvaluations(
            @RequestParam(defaultValue = "25") int limit,
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token
    ) {
        requireAdmin(token);
        return resultStore.listRecent(safeLimit(limit));
    }

    @GetMapping("/prompts/agents/{agentId}")
    public Collection<PromptTemplate> listPromptVersionsForAgent(
            @PathVariable String agentId,
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token
    ) {
        requireAdmin(token);
        requireText("agentId", agentId);
        return promptVersionStore.listForAgent(agentId);
    }

    @GetMapping("/prompts/{promptId}/versions/{version}")
    public PromptTemplate getPromptVersion(
            @PathVariable String promptId,
            @PathVariable String version,
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token
    ) {
        requireAdmin(token);
        return promptVersionStore.find(promptId, version)
                .orElseThrow(() -> new AgentExecutionException(AgentErrorCode.NOT_FOUND, "Prompt version not found"));
    }

    @PostMapping("/prompts")
    public PromptTemplate createPromptVersion(
            @RequestBody PromptTemplateRequest request,
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token
    ) {
        requireAdmin(token);
        PromptTemplate template = toPromptTemplate(request);
        if (promptVersionStore.find(template.promptId(), template.version()).isPresent()) {
            throw new IllegalArgumentException("Prompt version already exists");
        }
        return promptVersionStore.save(template);
    }

    @PostMapping("/prompts/{promptId}/versions/{version}/activate")
    public PromptTemplate activatePromptVersion(
            @PathVariable String promptId,
            @PathVariable String version,
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token
    ) {
        requireAdmin(token);
        PromptTemplate template = getExistingPrompt(promptId, version);
        promptVersionStore.listForAgent(template.agentId()).stream()
                .filter(existing -> existing.status() == PromptStatus.ACTIVE)
                .filter(existing -> !samePromptVersion(existing, template))
                .map(existing -> withStatus(existing, PromptStatus.DEPRECATED))
                .forEach(promptVersionStore::save);
        return promptVersionStore.save(withStatus(template, PromptStatus.ACTIVE));
    }

    @PostMapping("/prompts/{promptId}/versions/{version}/deprecate")
    public PromptTemplate deprecatePromptVersion(
            @PathVariable String promptId,
            @PathVariable String version,
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token
    ) {
        requireAdmin(token);
        return transitionPromptStatus(promptId, version, PromptStatus.DEPRECATED);
    }

    @PostMapping("/prompts/{promptId}/versions/{version}/archive")
    public PromptTemplate archivePromptVersion(
            @PathVariable String promptId,
            @PathVariable String version,
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token
    ) {
        requireAdmin(token);
        return transitionPromptStatus(promptId, version, PromptStatus.ARCHIVED);
    }

    @GetMapping("/approvals")
    public Collection<AgentApprovalRequest> listApprovals(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tenantId,
            @RequestParam(defaultValue = "25") int limit,
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token
    ) {
        requireAdmin(token);
        return approvalStore.list(parseApprovalStatus(status), tenantId, safeLimit(limit));
    }

    @GetMapping("/approvals/{approvalId}")
    public AgentApprovalRequest getApproval(
            @PathVariable String approvalId,
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token
    ) {
        requireAdmin(token);
        requireText("approvalId", approvalId);
        return approvalStore.findById(approvalId)
                .orElseThrow(() -> new AgentExecutionException(AgentErrorCode.NOT_FOUND, "Approval request not found"));
    }

    @PostMapping("/approvals/expire")
    public ApprovalExpiryResponse expireApprovals(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token
    ) {
        requireAdmin(token);
        return new ApprovalExpiryResponse(approvalStore.expirePendingBefore(Instant.now()));
    }


    @GetMapping("/observability/summary")
    public AgentRunSummary observabilitySummary(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token
    ) {
        requireAdmin(token);
        return observabilityService.summary();
    }

    @GetMapping("/observability/events")
    public List<AgentPipelineEvent> recentEvents(
            @RequestParam(defaultValue = "50") int limit,
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token
    ) {
        requireAdmin(token);
        return observabilityService.recentEvents(safeLimit(limit));
    }

    @PostMapping("/runs/replay")
    public ResponseEntity<AgentOutput> replay(
            @RequestBody AgentReplayRequest request,
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token
    ) {
        requireAdmin(token);
        return ResponseEntity.ok(replayService.replay(request));
    }

    private PromptTemplate transitionPromptStatus(String promptId, String version, PromptStatus status) {
        return promptVersionStore.save(withStatus(getExistingPrompt(promptId, version), status));
    }

    private PromptTemplate getExistingPrompt(String promptId, String version) {
        requireText("promptId", promptId);
        requireText("version", version);
        return promptVersionStore.find(promptId, version)
                .orElseThrow(() -> new AgentExecutionException(AgentErrorCode.NOT_FOUND, "Prompt version not found"));
    }

    private PromptTemplate toPromptTemplate(PromptTemplateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Prompt template request is required");
        }
        requireText("promptId", request.promptId());
        requireText("agentId", request.agentId());
        requireText("version", request.version());
        Instant now = Instant.now();
        return new PromptTemplate(
                request.promptId(),
                request.agentId(),
                request.version(),
                parseStatus(request.status()),
                request.systemTemplate(),
                request.userTemplate(),
                request.metadata(),
                now,
                now
        );
    }

    private PromptTemplate withStatus(PromptTemplate template, PromptStatus status) {
        return new PromptTemplate(
                template.promptId(),
                template.agentId(),
                template.version(),
                status,
                template.systemTemplate(),
                template.userTemplate(),
                template.metadata(),
                template.createdAt(),
                Instant.now()
        );
    }

    private AgentApprovalStatus parseApprovalStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return AgentApprovalStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }


    private PromptStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return PromptStatus.DRAFT;
        }
        return PromptStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private boolean samePromptVersion(PromptTemplate left, PromptTemplate right) {
        return left.promptId().equals(right.promptId()) && left.version().equals(right.version());
    }

    private void requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    private void requireAdmin(String token) {
        String configuredToken = properties.getAdmin().getToken();
        if (configuredToken == null || configuredToken.isBlank()) {
            throw new AgentExecutionException(AgentErrorCode.AUTHORIZATION_DENIED, "Admin API token is not configured");
        }
        if (token == null || !constantTimeEquals(configuredToken, token)) {
            throw new AgentExecutionException(AgentErrorCode.AUTHORIZATION_DENIED, "Admin API token is invalid");
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected.length() != actual.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < expected.length(); i++) {
            result |= expected.charAt(i) ^ actual.charAt(i);
        }
        return result == 0;
    }

    private int safeLimit(int requestedLimit) {
        if (requestedLimit <= 0) {
            return DEFAULT_LIST_LIMIT;
        }
        return Math.min(requestedLimit, MAX_LIST_LIMIT);
    }
}



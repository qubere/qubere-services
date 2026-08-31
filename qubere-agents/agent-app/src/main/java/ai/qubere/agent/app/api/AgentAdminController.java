package ai.qubere.agent.app.api;

import ai.qubere.agent.api.AgentOutput;
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
import ai.qubere.agent.runtime.AgentPipelineEvent;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import java.util.Collection;
import java.util.List;

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

    public AgentAdminController(
            AgentPlatformProperties properties,
            AgentEvaluator evaluator,
            EvaluationResultStore resultStore,
            AgentReplayService replayService,
            InMemoryAgentObservabilityService observabilityService
    ) {
        this.properties = properties;
        this.evaluator = evaluator;
        this.resultStore = resultStore;
        this.replayService = replayService;
        this.observabilityService = observabilityService;
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

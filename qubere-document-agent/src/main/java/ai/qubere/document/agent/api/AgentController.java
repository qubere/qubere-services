package ai.qubere.document.agent.api;

import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentOutput;
import ai.qubere.agent.async.AgentAsyncRunHandle;
import ai.qubere.agent.async.AgentAsyncRuntimeService;
import ai.qubere.agent.core.GenericAgentInput;
import ai.qubere.agent.runtime.AgentExecutionRecord;
import ai.qubere.agent.runtime.AgentExecutionStore;
import ai.qubere.agent.runtime.AgentRegistry;
import ai.qubere.agent.runtime.AgentRuntimeService;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentRegistry registry;
    private final AgentRuntimeService runtimeService;
    private final AgentAsyncRuntimeService asyncRuntimeService;
    private final AgentExecutionStore executionStore;

    public AgentController(
            AgentRegistry registry,
            AgentRuntimeService runtimeService,
            AgentAsyncRuntimeService asyncRuntimeService,
            AgentExecutionStore executionStore
    ) {
        this.registry = registry;
        this.runtimeService = runtimeService;
        this.asyncRuntimeService = asyncRuntimeService;
        this.executionStore = executionStore;
    }

    @GetMapping
    public Collection<AgentDescriptor> listAgents() {
        return registry.listAgents();
    }

    @PostMapping("/{agentId}/runs")
    public AgentRunResponse runAgent(
            @PathVariable String agentId,
            @Valid @RequestBody AgentRunRequest request,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(name = "X-Actor-Id", required = false) String actorId,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(name = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = "X-Agent-Permissions", required = false) String permissions
    ) {
        String executionId = UUID.randomUUID().toString();
        AgentExecutionContext context = new AgentExecutionContext(
                executionId,
                tenantId,
                actorId,
                correlationId,
                Instant.now(),
                contextAttributes(permissions)
        );
        if (Boolean.TRUE.equals(request.async())) {
            AgentAsyncRunHandle handle = asyncRuntimeService.submit(
                    agentId,
                    request.agentVersion(),
                    new GenericAgentInput(request.input()),
                    context,
                    request.options(),
                    request.callbackUrl(),
                    firstNonBlank(idempotencyKey, request.idempotencyKey())
            );
            return new AgentRunResponse(handle.executionId(), handle.status(), handle.approvalId(), null);
        }
        AgentOutput output = runtimeService.run(
                agentId,
                request.agentVersion(),
                new GenericAgentInput(request.input()),
                context,
                request.options()
        );
        return new AgentRunResponse(executionId, output);
    }

    @PostMapping("/async/process-next")
    public ResponseEntity<Void> processNextAsyncRun() {
        return asyncRuntimeService.processNext().isPresent()
                ? ResponseEntity.accepted().build()
                : ResponseEntity.noContent().build();
    }

    @PostMapping("/approvals/{approvalId}/approve")
    public AgentRunResponse approve(
            @PathVariable String approvalId,
            @RequestBody(required = false) ApprovalDecisionRequest request
    ) {
        AgentAsyncRunHandle handle = asyncRuntimeService.resumeApproved(approvalId, decidedBy(request));
        return new AgentRunResponse(handle.executionId(), handle.status(), handle.approvalId(), null);
    }

    @PostMapping("/approvals/{approvalId}/reject")
    public AgentRunResponse reject(
            @PathVariable String approvalId,
            @RequestBody(required = false) ApprovalDecisionRequest request
    ) {
        AgentAsyncRunHandle handle = asyncRuntimeService.reject(approvalId, decidedBy(request));
        return new AgentRunResponse(handle.executionId(), handle.status(), handle.approvalId(), null);
    }

    @PostMapping("/approvals/{approvalId}/decision")
    public AgentRunResponse decide(
            @PathVariable String approvalId,
            @RequestBody ApprovalDecisionRequest request
    ) {
        String decision = request == null || request.decision() == null ? "" : request.decision().trim();
        AgentAsyncRunHandle handle;
        if ("APPROVED".equalsIgnoreCase(decision) || "APPROVE".equalsIgnoreCase(decision)) {
            handle = asyncRuntimeService.resumeApproved(approvalId, decidedBy(request));
        } else if ("REJECTED".equalsIgnoreCase(decision) || "REJECT".equalsIgnoreCase(decision)) {
            handle = asyncRuntimeService.reject(approvalId, decidedBy(request));
        } else if ("EXPIRED".equalsIgnoreCase(decision) || "EXPIRE".equalsIgnoreCase(decision)) {
            handle = asyncRuntimeService.expire(approvalId);
        } else {
            throw new IllegalArgumentException("Unsupported approval decision: " + decision);
        }
        return new AgentRunResponse(handle.executionId(), handle.status(), handle.approvalId(), null);
    }

    @GetMapping("/runs/{executionId}")
    public ResponseEntity<AgentExecutionRecord> getRun(@PathVariable String executionId) {
        return executionStore.findByExecutionId(executionId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Map<String, Object> contextAttributes(String permissionsHeader) {
        Set<String> permissions = parseCsvHeader(permissionsHeader);
        if (permissions.isEmpty()) {
            return Map.of();
        }
        return Map.of("permissions", permissions);
    }

    private Set<String> parseCsvHeader(String header) {
        if (header == null || header.isBlank()) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String token : header.split(",")) {
            String value = token.trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private String decidedBy(ApprovalDecisionRequest request) {
        return request == null || request.decidedBy() == null || request.decidedBy().isBlank()
                ? "system"
                : request.decidedBy();
    }
}

package ai.qubere.agent.app.api;

import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentOutput;
import ai.qubere.agent.async.AgentAsyncRunHandle;
import ai.qubere.agent.async.AgentAsyncRuntimeService;
import ai.qubere.agent.core.GenericAgentInput;
import ai.qubere.agent.orchestration.AgentPropagationHeaders;
import ai.qubere.agent.runtime.AgentExecutionRecord;
import ai.qubere.agent.runtime.AgentExecutionStore;
import ai.qubere.agent.runtime.AgentRegistry;
import ai.qubere.agent.runtime.AgentRuntimeService;
import ai.qubere.agent.runtime.AgentWorkflowContext;
import ai.qubere.agent.runtime.security.AgentCallerIdentity;
import ai.qubere.agent.runtime.security.AgentCallerIdentityResolver;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private final AgentCallerIdentityResolver callerIdentityResolver;

    public AgentController(
            AgentRegistry registry,
            AgentRuntimeService runtimeService,
            AgentAsyncRuntimeService asyncRuntimeService,
            AgentExecutionStore executionStore,
            AgentCallerIdentityResolver callerIdentityResolver
    ) {
        this.registry = registry;
        this.runtimeService = runtimeService;
        this.asyncRuntimeService = asyncRuntimeService;
        this.executionStore = executionStore;
        this.callerIdentityResolver = callerIdentityResolver;
    }

    @GetMapping
    public Collection<AgentDescriptor> listAgents() {
        return registry.listAgents();
    }

    @PostMapping("/{agentId}/runs")
    public AgentRunResponse runAgent(
            @PathVariable String agentId,
            @Valid @RequestBody AgentRunRequest request,
            @RequestHeader Map<String, String> headers,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(name = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = AgentPropagationHeaders.WORKFLOW_ID, required = false) String workflowId,
            @RequestHeader(name = AgentPropagationHeaders.PARENT_EXECUTION_ID, required = false) String parentExecutionId
    ) {
        // Tenant/actor/permission identity must come only from callerIdentityResolver, never
        // read directly from request headers here. In strict authorization mode the default
        // resolver ignores inbound headers entirely (fail-closed) unless the deployed application
        // supplies a real resolver backed by a verified identity source (JWT/OAuth/gateway).
        AgentCallerIdentity callerIdentity = callerIdentityResolver.resolve(headers);
        String executionId = UUID.randomUUID().toString();
        AgentExecutionContext context = new AgentExecutionContext(
                executionId,
                callerIdentity.tenantId(),
                callerIdentity.actorId(),
                correlationId,
                Instant.now(),
                contextAttributes(callerIdentity, workflowId, parentExecutionId)
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

    private Map<String, Object> contextAttributes(AgentCallerIdentity callerIdentity, String workflowId, String parentExecutionId) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (!callerIdentity.permissions().isEmpty()) {
            attributes.put("permissions", callerIdentity.permissions());
        }
        // Workflow linkage is metadata, not an authorization decision, so it is safe to accept
        // from an upstream agent service: it only groups this execution into a workflow for
        // observability and rollup. Tenant/actor still come solely from the identity resolver.
        if (workflowId != null && !workflowId.isBlank()) {
            attributes.put(AgentWorkflowContext.WORKFLOW_ID, workflowId.trim());
        }
        if (parentExecutionId != null && !parentExecutionId.isBlank()) {
            attributes.put(AgentWorkflowContext.PARENT_EXECUTION_ID, parentExecutionId.trim());
        }
        return attributes;
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

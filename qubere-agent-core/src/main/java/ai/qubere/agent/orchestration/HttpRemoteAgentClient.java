package ai.qubere.agent.orchestration;

import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.core.AgentErrorCode;
import ai.qubere.agent.core.AgentExecutionException;
import ai.qubere.agent.resilience.AgentResilienceGateway;
import ai.qubere.agent.runtime.AgentWorkflowContext;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP {@link RemoteAgentClient} that calls another qubere-agents service through its standard
 * {@code POST /api/agents/{agentId}/runs} endpoint.
 * <p>
 * Workflow linkage and correlation are propagated as {@link AgentPropagationHeaders} so the
 * remote execution can be stitched back into the calling workflow, and the call is routed through
 * the {@link AgentResilienceGateway} keyed per remote agent so one unhealthy downstream agent
 * service does not take down unrelated delegations.
 */
public class HttpRemoteAgentClient implements RemoteAgentClient {

    private final RestClient restClient;
    private final String baseUrl;
    private final AgentResilienceGateway resilienceGateway;

    public HttpRemoteAgentClient(RestClient.Builder restClientBuilder, AgentPlatformProperties properties) {
        this(restClientBuilder, properties, AgentResilienceGateway.noop());
    }

    public HttpRemoteAgentClient(
            RestClient.Builder restClientBuilder,
            AgentPlatformProperties properties,
            AgentResilienceGateway resilienceGateway
    ) {
        AgentPlatformProperties.Orchestration.Remote remote = properties.getOrchestration().getRemote();
        this.baseUrl = stripTrailingSlash(remote.getBaseUrl());
        this.resilienceGateway = resilienceGateway == null ? AgentResilienceGateway.noop() : resilienceGateway;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(remote.getTimeoutSeconds()));
        requestFactory.setReadTimeout(Duration.ofSeconds(remote.getTimeoutSeconds()));
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
    }

    @Override
    public RemoteAgentRunResult run(
            String agentId,
            String agentVersion,
            Map<String, Object> input,
            AgentExecutionContext callerContext
    ) {
        if (agentId == null || agentId.isBlank()) {
            throw new AgentExecutionException(AgentErrorCode.VALIDATION_FAILED, "Remote agent id is required");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new AgentExecutionException(
                    AgentErrorCode.VALIDATION_FAILED,
                    "agent-platform.orchestration.remote.base-url must be configured to call remote agents"
            );
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("input", input == null ? Map.of() : input);
        if (agentVersion != null && !agentVersion.isBlank()) {
            body.put("agentVersion", agentVersion);
        }

        String uri = baseUrl + "/api/agents/" + agentId + "/runs";
        return resilienceGateway.execute("remote-agent:" + agentId, () -> {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON);
            applyPropagationHeaders(request, callerContext);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = request.body(body).retrieve().body(Map.class);
            return toResult(agentId, response);
        });
    }

    private void applyPropagationHeaders(RestClient.RequestBodySpec request, AgentExecutionContext callerContext) {
        if (callerContext == null) {
            return;
        }
        if (callerContext.correlationId() != null) {
            request.header(AgentPropagationHeaders.CORRELATION_ID, callerContext.correlationId());
        }
        // The caller's own execution id becomes the remote execution's parent, and its workflow
        // id (falling back to its execution id) groups the remote run into this orchestration.
        String workflowId = AgentWorkflowContext.workflowId(callerContext);
        request.header(AgentPropagationHeaders.WORKFLOW_ID, workflowId == null ? callerContext.executionId() : workflowId);
        request.header(AgentPropagationHeaders.PARENT_EXECUTION_ID, callerContext.executionId());
    }

    private RemoteAgentRunResult toResult(String agentId, Map<String, Object> response) {
        if (response == null) {
            throw new AgentExecutionException(
                    AgentErrorCode.EXECUTION_FAILED,
                    "Remote agent returned an empty response: " + agentId
            );
        }
        Object result = response.get("result");
        @SuppressWarnings("unchecked")
        Map<String, Object> output = result instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        return new RemoteAgentRunResult(
                asString(response.get("executionId")),
                agentId,
                asString(response.getOrDefault("status", "SUCCEEDED")),
                output,
                asString(response.get("approvalId"))
        );
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private String stripTrailingSlash(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}

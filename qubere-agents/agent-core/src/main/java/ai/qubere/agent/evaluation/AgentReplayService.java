package ai.qubere.agent.evaluation;

import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentOutput;
import ai.qubere.agent.core.AgentRunOptions;
import ai.qubere.agent.core.GenericAgentInput;
import ai.qubere.agent.runtime.AgentExecutionRecord;
import ai.qubere.agent.runtime.AgentExecutionStore;
import ai.qubere.agent.runtime.AgentRuntimeService;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AgentReplayService {

    private final AgentExecutionStore executionStore;
    private final AgentRuntimeService runtimeService;
    private final ObjectMapper objectMapper;

    public AgentReplayService(AgentExecutionStore executionStore, AgentRuntimeService runtimeService, ObjectMapper objectMapper) {
        this.executionStore = executionStore;
        this.runtimeService = runtimeService;
        this.objectMapper = objectMapper;
    }

    public AgentOutput replay(AgentReplayRequest request) {
        if (request == null || request.sourceExecutionId() == null || request.sourceExecutionId().isBlank()) {
            throw new IllegalArgumentException("sourceExecutionId is required");
        }
        AgentExecutionRecord record = executionStore.findByExecutionId(request.sourceExecutionId())
                .orElseThrow(() -> new IllegalArgumentException("Source execution not found: " + request.sourceExecutionId()));
        String replayExecutionId = "replay-" + UUID.randomUUID();
        return runtimeService.run(
                record.agentId(),
                request.agentVersion() == null || request.agentVersion().isBlank() ? record.agentVersion() : request.agentVersion(),
                new GenericAgentInput(replayInput(record, request)),
                new AgentExecutionContext(
                        replayExecutionId,
                        record.tenantId(),
                        record.actorId(),
                        "replay-of-" + record.executionId(),
                        Instant.now(),
                        Map.of("replay", true, "sourceExecutionId", record.executionId())
                ),
                request.options() == null ? nullOptions() : request.options()
        );
    }

    private Map<String, Object> replayInput(AgentExecutionRecord record, AgentReplayRequest request) {
        if (!request.inputOverride().isEmpty()) {
            return request.inputOverride();
        }
        try {
            JsonNode root = objectMapper.readTree(record.inputJson());
            JsonNode input = root.has("values") ? root.get("values") : root;
            return objectMapper.convertValue(input, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to deserialize source execution input for replay", ex);
        }
    }

    private AgentRunOptions nullOptions() {
        return new AgentRunOptions(null, null, null, null, null, null, null, null);
    }
}

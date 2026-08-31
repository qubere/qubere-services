package ai.qubere.agent.evaluation;

import ai.qubere.agent.api.Agent;
import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentInput;
import ai.qubere.agent.api.AgentOutput;
import ai.qubere.agent.api.AgentRiskLevel;
import ai.qubere.agent.core.AgentErrorCode;
import ai.qubere.agent.core.AgentExecutionException;
import ai.qubere.agent.core.AgentResult;
import ai.qubere.agent.core.AgentRunOptions;
import ai.qubere.agent.core.AgentRunStatus;
import ai.qubere.agent.core.GenericAgentInput;
import ai.qubere.agent.core.ResolvedAgentPolicy;
import ai.qubere.agent.prompts.InMemoryPromptVersionStore;
import ai.qubere.agent.prompts.PromptStatus;
import ai.qubere.agent.prompts.PromptTemplate;
import ai.qubere.agent.runtime.AgentExecutionRecord;
import ai.qubere.agent.runtime.AgentExecutionStore;
import ai.qubere.agent.runtime.AgentRegistry;
import ai.qubere.agent.runtime.AgentRuntimeService;
import ai.qubere.agent.runtime.DefaultAgentPolicyResolver;
import ai.qubere.agent.runtime.GuardrailDecision;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentEvaluationTest {

    @Test
    void evaluatesGoldenDatasetAgainstAgentOutput() {
        InMemoryGoldenDatasetRepository datasets = new InMemoryGoldenDatasetRepository();
        datasets.save(new GoldenDataset(
                "echo-golden",
                "Echo regression dataset",
                List.of(new GoldenExample("case-1", "echo", "1.0.0", Map.of("message", "hello"), Map.of("message", "hello"), null)),
                Map.of()
        ));

        AgentEvaluator evaluator = new AgentEvaluator(runtimeService(new InMemoryExecutionStore(), List.of()), datasets);

        EvaluationResult result = evaluator.evaluate("echo-golden");

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.passed()).isEqualTo(1);
        assertThat(result.failed()).isZero();
    }

    @Test
    void verifiesPromptRegressionExpectations() {
        InMemoryPromptVersionStore prompts = new InMemoryPromptVersionStore();
        prompts.save(new PromptTemplate(
                "prompt-1",
                "echo",
                "1.0.0",
                PromptStatus.ACTIVE,
                "You are safe and concise.",
                "Summarize {{input}}",
                Map.of(),
                Instant.now(),
                Instant.now()
        ));

        PromptRegressionService service = new PromptRegressionService(prompts);

        List<PromptRegressionResult> results = service.verify(List.of(new PromptRegressionCase(
                "prompt-case-1",
                "prompt-1",
                "1.0.0",
                List.of("safe"),
                List.of("Summarize"),
                Map.of()
        )));

        assertThat(results).extracting(PromptRegressionResult::status).containsExactly(EvaluationStatus.PASSED);
    }

    @Test
    void enforcesActorRateLimit() {
        AgentPlatformProperties properties = new AgentPlatformProperties();
        properties.getGovernance().setMaxRunsPerActorPerMinute(1);
        InMemoryAgentGovernanceService governance = new InMemoryAgentGovernanceService(properties);
        AgentRuntimeService runtime = runtimeService(new InMemoryExecutionStore(), List.of(governance));

        runtime.run("echo", "1.0.0", input("first"), context("exec-1"), null);

        assertThatThrownBy(() -> runtime.run("echo", "1.0.0", input("second"), context("exec-2"), null))
                .isInstanceOfSatisfying(AgentExecutionException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(AgentErrorCode.GOVERNANCE_LIMIT_EXCEEDED));
    }

    @Test
    void enforcesEstimatedCostLimit() {
        AgentPlatformProperties properties = new AgentPlatformProperties();
        properties.getGovernance().setMaxEstimatedCostUsdPerRun(new BigDecimal("0.01"));
        properties.getGovernance().setEstimatedCostUsdPerThousandTokens(new BigDecimal("0.02"));
        InMemoryAgentGovernanceService governance = new InMemoryAgentGovernanceService(properties);
        AgentRuntimeService runtime = runtimeService(new InMemoryExecutionStore(), List.of(governance));
        AgentRunOptions options = new AgentRunOptions(null, null, null, 1000, null, null, null, null);

        assertThatThrownBy(() -> runtime.run("echo", "1.0.0", input("expensive"), context("exec-cost"), options))
                .isInstanceOfSatisfying(AgentExecutionException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(AgentErrorCode.GOVERNANCE_LIMIT_EXCEEDED));
    }

    @Test
    void replaysExecutionUsingStoredInput() {
        InMemoryExecutionStore store = new InMemoryExecutionStore();
        AgentRuntimeService runtime = runtimeService(store, List.of());
        AgentOutput original = runtime.run("echo", "1.0.0", input("original"), context("exec-source"), null);
        assertThat(original).isNotNull();
        AgentReplayService replayService = new AgentReplayService(store, runtime, new ObjectMapper());

        AgentOutput replayed = replayService.replay(new AgentReplayRequest("exec-source", null, Map.of(), null));

        @SuppressWarnings("unchecked")
        AgentResult<Map<String, Object>> result = (AgentResult<Map<String, Object>>) replayed;
        assertThat(result.value()).containsEntry("message", "original");
    }

    private AgentRuntimeService runtimeService(InMemoryExecutionStore executionStore, List<ai.qubere.agent.runtime.AgentGovernanceService> governanceServices) {
        return new AgentRuntimeService(
                new AgentRegistry(List.of(new EchoAgent())),
                new DefaultAgentPolicyResolver(),
                (context, descriptor) -> true,
                (context, descriptor, input) -> GuardrailDecision.allow(),
                executionStore,
                (context, descriptor, status, message) -> {
                },
                governanceServices,
                List.of()
        );
    }

    private GenericAgentInput input(String message) {
        return new GenericAgentInput(Map.of("message", message));
    }

    private AgentExecutionContext context(String executionId) {
        return new AgentExecutionContext(executionId, "tenant-1", "actor-1", "corr-1", Instant.now(), Map.of());
    }

    private static final class EchoAgent implements Agent<GenericAgentInput, AgentResult<Map<String, Object>>> {
        private static final AgentDescriptor DESCRIPTOR = new AgentDescriptor("echo", "Echo", "1.0.0", "Echo agent", AgentRiskLevel.LOW, Set.of("test"));

        @Override
        public AgentDescriptor descriptor() {
            return DESCRIPTOR;
        }

        @Override
        public AgentResult<Map<String, Object>> run(GenericAgentInput input, AgentExecutionContext context) {
            return new AgentResult<>(input.values(), null, List.of(), Map.of());
        }
    }

    private static final class InMemoryExecutionStore implements AgentExecutionStore {
        private final Map<String, AgentExecutionRecord> records = new ConcurrentHashMap<>();
        private final ObjectMapper objectMapper = new ObjectMapper();

        @Override
        public void markStarted(AgentExecutionContext context, AgentDescriptor descriptor, AgentInput input) {
            records.put(context.executionId(), new AgentExecutionRecord(
                    context.executionId(),
                    descriptor.id(),
                    descriptor.version(),
                    context.tenantId(),
                    context.actorId(),
                    AgentRunStatus.RUNNING,
                    toJson(input),
                    null,
                    null,
                    context.requestedAt(),
                    Instant.now()
            ));
        }

        @Override
        public void markCompleted(String executionId, AgentOutput output) {
            AgentExecutionRecord current = records.get(executionId);
            records.put(executionId, new AgentExecutionRecord(
                    current.executionId(),
                    current.agentId(),
                    current.agentVersion(),
                    current.tenantId(),
                    current.actorId(),
                    AgentRunStatus.SUCCEEDED,
                    current.inputJson(),
                    toJson(output),
                    null,
                    current.createdAt(),
                    Instant.now()
            ));
        }

        @Override
        public void markFailed(String executionId, Throwable failure) {
            AgentExecutionRecord current = records.get(executionId);
            records.put(executionId, new AgentExecutionRecord(
                    current.executionId(),
                    current.agentId(),
                    current.agentVersion(),
                    current.tenantId(),
                    current.actorId(),
                    AgentRunStatus.FAILED,
                    current.inputJson(),
                    current.outputJson(),
                    failure.getMessage(),
                    current.createdAt(),
                    Instant.now()
            ));
        }

        @Override
        public Optional<AgentExecutionRecord> findByExecutionId(String executionId) {
            return Optional.ofNullable(records.get(executionId));
        }

        private String toJson(Object value) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception ex) {
                throw new IllegalArgumentException(ex);
            }
        }
    }
}

package ai.qubere.agent.evaluation;

import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentOutput;
import ai.qubere.agent.core.AgentResult;
import ai.qubere.agent.core.GenericAgentInput;
import ai.qubere.agent.runtime.AgentRuntimeService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class AgentEvaluator {

    private final AgentRuntimeService runtimeService;
    private final GoldenDatasetRepository datasetRepository;
    private final EvaluationResultStore resultStore;

    public AgentEvaluator(AgentRuntimeService runtimeService, GoldenDatasetRepository datasetRepository) {
        this(runtimeService, datasetRepository, EvaluationResultStore.noop());
    }

    public AgentEvaluator(AgentRuntimeService runtimeService, GoldenDatasetRepository datasetRepository, EvaluationResultStore resultStore) {
        this.runtimeService = runtimeService;
        this.datasetRepository = datasetRepository;
        this.resultStore = resultStore == null ? EvaluationResultStore.noop() : resultStore;
    }

    public EvaluationResult evaluate(String datasetName) {
        GoldenDataset dataset = datasetRepository.find(datasetName)
                .orElseThrow(() -> new IllegalArgumentException("Golden dataset not found: " + datasetName));
        List<EvaluationCaseResult> cases = new ArrayList<>();
        for (GoldenExample example : dataset.examples()) {
            String executionId = "eval-" + UUID.randomUUID();
            try {
                AgentOutput output = runtimeService.run(
                        example.agentId(),
                        example.agentVersion(),
                        new GenericAgentInput(example.input()),
                        new AgentExecutionContext(executionId, "evaluation", "evaluation", dataset.name(), Instant.now(), Map.of("evaluation", true)),
                        example.options()
                );
                cases.add(match(example, output, executionId));
            } catch (RuntimeException ex) {
                cases.add(new EvaluationCaseResult(example.id(), EvaluationStatus.FAILED, executionId, ex.getMessage()));
            }
        }
        int passed = (int) cases.stream().filter(result -> result.status() == EvaluationStatus.PASSED).count();
        EvaluationResult result = new EvaluationResult(dataset.name(), cases.size(), passed, cases.size() - passed, cases, Instant.now());
        resultStore.save(result);
        return result;
    }

    private EvaluationCaseResult match(GoldenExample example, AgentOutput output, String executionId) {
        if (example.expectedOutput().isEmpty()) {
            return new EvaluationCaseResult(example.id(), EvaluationStatus.PASSED, executionId, "No expected output assertions configured");
        }
        if (!(output instanceof AgentResult<?> result) || !(result.value() instanceof Map<?, ?> actual)) {
            return new EvaluationCaseResult(example.id(), EvaluationStatus.FAILED, executionId, "Output is not a map-backed AgentResult");
        }
        for (Map.Entry<String, Object> expected : example.expectedOutput().entrySet()) {
            if (!Objects.equals(expected.getValue(), actual.get(expected.getKey()))) {
                return new EvaluationCaseResult(
                        example.id(),
                        EvaluationStatus.FAILED,
                        executionId,
                        "Expected %s=%s but found %s".formatted(expected.getKey(), expected.getValue(), actual.get(expected.getKey()))
                );
            }
        }
        return new EvaluationCaseResult(example.id(), EvaluationStatus.PASSED, executionId, "Matched expected output");
    }
}

package ai.qubere.agent.evaluation;

import ai.qubere.agent.prompts.PromptTemplate;
import ai.qubere.agent.prompts.PromptVersionStore;

import java.util.ArrayList;
import java.util.List;

public class PromptRegressionService {

    private final PromptVersionStore promptVersionStore;

    public PromptRegressionService(PromptVersionStore promptVersionStore) {
        this.promptVersionStore = promptVersionStore;
    }

    public List<PromptRegressionResult> verify(List<PromptRegressionCase> cases) {
        List<PromptRegressionResult> results = new ArrayList<>();
        for (PromptRegressionCase regressionCase : cases == null ? List.<PromptRegressionCase>of() : cases) {
            results.add(verifyOne(regressionCase));
        }
        return results;
    }

    private PromptRegressionResult verifyOne(PromptRegressionCase regressionCase) {
        PromptTemplate template = promptVersionStore.find(regressionCase.promptId(), regressionCase.version())
                .orElse(null);
        if (template == null) {
            return new PromptRegressionResult(regressionCase.id(), EvaluationStatus.FAILED, "Prompt template not found");
        }
        String system = template.systemTemplate() == null ? "" : template.systemTemplate();
        String user = template.userTemplate() == null ? "" : template.userTemplate();
        for (String expected : regressionCase.expectedSystemContains()) {
            if (!system.contains(expected)) {
                return new PromptRegressionResult(regressionCase.id(), EvaluationStatus.FAILED, "System template missing: " + expected);
            }
        }
        for (String expected : regressionCase.expectedUserContains()) {
            if (!user.contains(expected)) {
                return new PromptRegressionResult(regressionCase.id(), EvaluationStatus.FAILED, "User template missing: " + expected);
            }
        }
        return new PromptRegressionResult(regressionCase.id(), EvaluationStatus.PASSED, "Prompt regression passed");
    }
}

package ai.qubere.agent.redteam;

import java.util.Map;
import java.util.Set;

/**
 * One adversarial test case: a hostile input plus the safe behavior the agent is required to
 * exhibit.
 * <p>
 * Unlike a golden example, which asserts that the agent produced the <em>right</em> answer, a
 * red-team case asserts that the agent <em>refused to do the wrong thing</em>. The two are
 * complementary and cannot be expressed with the same assertions: "blocked by a guardrail" is a
 * pass here and a failure in a golden dataset.
 *
 * @param id                  stable case identifier
 * @param category            attack class, e.g. {@code prompt-injection}, {@code data-exfiltration},
 *                            {@code excessive-agency}, aligned with OWASP LLM Top 10 naming
 * @param agentId             agent under test
 * @param agentVersion        specific version, or {@code null} for the registry default
 * @param input               the adversarial input
 * @param expectedOutcome     the safe behavior required of the agent
 * @param forbiddenToolCalls  tools the agent must not invoke for this input; invoking any of them
 *                            fails the case even if the textual output looks benign
 * @param forbiddenOutputText substrings that must not appear in the agent's output, used to catch
 *                            leaked system prompts, credentials, or other tenants' data
 */
public record RedTeamCase(
        String id,
        String category,
        String agentId,
        String agentVersion,
        Map<String, Object> input,
        RedTeamExpectation expectedOutcome,
        Set<String> forbiddenToolCalls,
        Set<String> forbiddenOutputText
) {
    public RedTeamCase {
        input = input == null ? Map.of() : Map.copyOf(input);
        expectedOutcome = expectedOutcome == null ? RedTeamExpectation.BLOCKED : expectedOutcome;
        forbiddenToolCalls = forbiddenToolCalls == null ? Set.of() : Set.copyOf(forbiddenToolCalls);
        forbiddenOutputText = forbiddenOutputText == null ? Set.of() : Set.copyOf(forbiddenOutputText);
    }
}

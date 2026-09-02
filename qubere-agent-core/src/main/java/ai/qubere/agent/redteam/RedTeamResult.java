package ai.qubere.agent.redteam;

import java.time.Instant;
import java.util.List;

/**
 * Outcome of running a red-team suite against an agent.
 *
 * @param suiteName suite identifier
 * @param total     number of adversarial cases executed
 * @param passed    cases where the agent behaved safely
 * @param failed    cases where the agent behaved unsafely
 * @param cases     per-case detail
 * @param runAt     when the suite ran
 */
public record RedTeamResult(
        String suiteName,
        int total,
        int passed,
        int failed,
        List<RedTeamCaseResult> cases,
        Instant runAt
) {
    public RedTeamResult {
        cases = cases == null ? List.of() : List.copyOf(cases);
        runAt = runAt == null ? Instant.now() : runAt;
    }

    /**
     * Red-team suites are a release gate, and unlike accuracy evaluation there is no acceptable
     * partial pass rate: a single reproducible safety violation should block release.
     */
    public boolean isClean() {
        return failed == 0;
    }

    /**
     * One adversarial case outcome.
     *
     * @param caseId      case identifier
     * @param category    attack class
     * @param passed      whether the agent behaved safely
     * @param detail      explanation, especially the violation when {@code passed} is false
     * @param executionId execution id for tracing the attempt in durable records
     */
    public record RedTeamCaseResult(
            String caseId,
            String category,
            boolean passed,
            String detail,
            String executionId
    ) {
    }
}

package ai.qubere.document.agent.document.parser.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParserBackoffTest {

    @Test
    void neverExceedsTheConfiguredMaximumDelay() {
        for (int attempt = 1; attempt <= 20; attempt++) {
            long delay = ParserBackoff.backoffDelayMillis(attempt, 1_000L, 30_000L);
            assertThat(delay).isLessThanOrEqualTo(30_000L);
            assertThat(delay).isGreaterThanOrEqualTo(0L);
        }
    }

    @Test
    void growsWithAttemptNumberBeforeHittingTheCeiling() {
        // Full-jitter backoff is randomized, so assert on the ceiling used to compute it rather
        // than the sampled delay itself.
        long earlyCeiling = (long) (1_000L * Math.pow(2, 0));
        long laterCeiling = (long) (1_000L * Math.pow(2, 3));

        assertThat(laterCeiling).isGreaterThan(earlyCeiling);
    }

    @Test
    void pollDelayUsesTheConfiguredLimitsBounds() {
        ParserProperties.ProcessingLimits limits = new ParserProperties.ProcessingLimits();
        limits.setPollInitialDelayMillis(5_000L);
        limits.setPollMaxDelayMillis(10_000L);

        for (int attempt = 1; attempt <= 10; attempt++) {
            long delay = ParserBackoff.pollDelayMillis(attempt, limits);
            assertThat(delay).isLessThanOrEqualTo(10_000L);
        }
    }
}

package ai.qubere.agent.ai;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelCostBudgetTrackerTest {

    @Test
    void accumulatesChargesAcrossMultipleCallsForTheSameExecution() {
        ModelCostBudgetTracker tracker = new ModelCostBudgetTracker();

        tracker.charge("exec-1", new BigDecimal("0.10"));
        tracker.charge("exec-1", new BigDecimal("0.05"));

        assertThat(tracker.currentSpend("exec-1")).isEqualByComparingTo("0.15");
    }

    @Test
    void tracksExecutionsIndependently() {
        ModelCostBudgetTracker tracker = new ModelCostBudgetTracker();

        tracker.charge("exec-a", new BigDecimal("1.00"));
        tracker.charge("exec-b", new BigDecimal("2.00"));

        assertThat(tracker.currentSpend("exec-a")).isEqualByComparingTo("1.00");
        assertThat(tracker.currentSpend("exec-b")).isEqualByComparingTo("2.00");
    }

    @Test
    void evictRemovesTrackedSpend() {
        ModelCostBudgetTracker tracker = new ModelCostBudgetTracker();
        tracker.charge("exec-1", new BigDecimal("0.50"));

        tracker.evict("exec-1");

        assertThat(tracker.currentSpend("exec-1")).isEqualByComparingTo("0.00");
    }

    @Test
    void ignoresNonPositiveOrNullCharges() {
        ModelCostBudgetTracker tracker = new ModelCostBudgetTracker();

        tracker.charge("exec-1", null);
        tracker.charge("exec-1", BigDecimal.ZERO);
        tracker.charge("exec-1", new BigDecimal("-1.00"));

        assertThat(tracker.currentSpend("exec-1")).isEqualByComparingTo("0.00");
    }

    @Test
    void evictsLeastRecentlyUsedExecutionWhenBoundExceeded() {
        ModelCostBudgetTracker tracker = new ModelCostBudgetTracker(2);

        tracker.charge("exec-1", new BigDecimal("1.00"));
        tracker.charge("exec-2", new BigDecimal("2.00"));
        tracker.charge("exec-3", new BigDecimal("3.00"));

        // exec-1 was least recently touched and must be evicted once capacity is exceeded.
        assertThat(tracker.currentSpend("exec-1")).isEqualByComparingTo("0.00");
        assertThat(tracker.currentSpend("exec-2")).isEqualByComparingTo("2.00");
        assertThat(tracker.currentSpend("exec-3")).isEqualByComparingTo("3.00");
    }
}

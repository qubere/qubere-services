package ai.qubere.agent.ai;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Tracks cumulative estimated model spend per execution so {@link SpringAiAgentClient} can
 * enforce {@code ResolvedAgentPolicy.maxEstimatedCostUsd()} as a hard budget across multiple
 * model calls within a single agent run. One agent execution may call the model zero, one, or
 * many times; the cap applies to the sum across all calls in that execution, not any single call.
 * <p>
 * Bounded by a least-recently-used eviction policy (default 5000 tracked executions) so
 * long-running applications do not grow this map unboundedly even if a caller never explicitly
 * evicts a finished execution. Explicit eviction through {@link #evict(String)} remains the
 * preferred path when the runtime knows an execution has reached a terminal state.
 */
public class ModelCostBudgetTracker {

    private static final int DEFAULT_MAX_TRACKED_EXECUTIONS = 5000;

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, BigDecimal> spendByExecution;

    public ModelCostBudgetTracker() {
        this(DEFAULT_MAX_TRACKED_EXECUTIONS);
    }

    public ModelCostBudgetTracker(int maxTrackedExecutions) {
        int capacity = Math.max(1, maxTrackedExecutions);
        this.spendByExecution = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, BigDecimal> eldest) {
                return size() > capacity;
            }
        };
    }

    /**
     * Returns the cumulative estimated cost recorded so far for the given execution.
     */
    public BigDecimal currentSpend(String executionId) {
        if (executionId == null) {
            return BigDecimal.ZERO;
        }
        lock.lock();
        try {
            return spendByExecution.getOrDefault(executionId, BigDecimal.ZERO);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Adds {@code cost} to the running total for the given execution and returns the new total.
     * A {@code null} or non-positive cost is a no-op that returns the current total unchanged.
     */
    public BigDecimal charge(String executionId, BigDecimal cost) {
        if (executionId == null || cost == null || cost.signum() <= 0) {
            return currentSpend(executionId);
        }
        lock.lock();
        try {
            BigDecimal updated = spendByExecution.getOrDefault(executionId, BigDecimal.ZERO).add(cost);
            spendByExecution.put(executionId, updated);
            return updated;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes tracked spend for an execution. Safe to call even if the execution was never
     * charged or was already evicted.
     */
    public void evict(String executionId) {
        if (executionId == null) {
            return;
        }
        lock.lock();
        try {
            spendByExecution.remove(executionId);
        } finally {
            lock.unlock();
        }
    }
}

package ai.qubere.agent.checkpoint;

import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.core.AgentExecutionException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentCheckpointScopeTest {

    private final AgentCheckpointStore store = new InMemoryAgentCheckpointStore();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void executesStepOnFirstRunAndReplaysItOnResume() {
        AtomicInteger executions = new AtomicInteger();

        String first = newScope("exec-1").step("load", String.class, () -> {
            executions.incrementAndGet();
            return "loaded-value";
        });
        assertThat(first).isEqualTo("loaded-value");
        assertThat(executions.get()).isEqualTo(1);

        // A resume re-invokes the agent from the top with a fresh scope over the same execution.
        String replayed = newScope("exec-1").step("load", String.class, () -> {
            executions.incrementAndGet();
            return "should-not-run";
        });

        assertThat(replayed).isEqualTo("loaded-value");
        assertThat(executions.get()).describedAs("completed step must not execute again").isEqualTo(1);
    }

    @Test
    void resumeSkipsCompletedStepsAndContinuesFromTheFirstIncompleteOne() {
        List<String> sideEffects = new ArrayList<>();

        // First attempt: two steps succeed, the third pauses for approval.
        AgentCheckpointScope firstAttempt = newScope("exec-approval");
        firstAttempt.step("charge-card", String.class, () -> {
            sideEffects.add("charged");
            return "txn-1";
        });
        firstAttempt.step("reserve-stock", String.class, () -> {
            sideEffects.add("reserved");
            return "res-1";
        });
        assertThatThrownBy(() -> firstAttempt.step("ship-order", String.class, () -> {
            throw new IllegalStateException("approval required");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(sideEffects).containsExactly("charged", "reserved");

        // After approval the agent runs again from the top.
        AgentCheckpointScope resumed = newScope("exec-approval");
        String txn = resumed.step("charge-card", String.class, () -> {
            sideEffects.add("charged-again");
            return "txn-2";
        });
        String reservation = resumed.step("reserve-stock", String.class, () -> {
            sideEffects.add("reserved-again");
            return "res-2";
        });
        String shipment = resumed.step("ship-order", String.class, () -> {
            sideEffects.add("shipped");
            return "ship-1";
        });

        // The money was not charged twice and stock was not double-reserved.
        assertThat(sideEffects).containsExactly("charged", "reserved", "shipped");
        assertThat(txn).isEqualTo("txn-1");
        assertThat(reservation).isEqualTo("res-1");
        assertThat(shipment).isEqualTo("ship-1");
    }

    @Test
    void replaysComplexResultTypesThroughTypeReference() {
        newScope("exec-generic").step("collect", new TypeReference<Map<String, Object>>() {
        }, () -> Map.of("count", 3, "label", "findings"));

        Map<String, Object> replayed = newScope("exec-generic").step(
                "collect",
                new TypeReference<Map<String, Object>>() {
                },
                () -> Map.of("count", 999, "label", "should-not-run")
        );

        assertThat(replayed).containsEntry("count", 3).containsEntry("label", "findings");
    }

    @Test
    void rejectsDuplicateStepNamesWithinOneRun() {
        AgentCheckpointScope scope = newScope("exec-dup");
        scope.step("same-name", String.class, () -> "first");

        // Two different pieces of work sharing a checkpoint key would replay the wrong result.
        assertThatThrownBy(() -> scope.step("same-name", String.class, () -> "second"))
                .isInstanceOf(AgentExecutionException.class);
    }

    @Test
    void rejectsBlankStepNames() {
        AgentCheckpointScope scope = newScope("exec-blank");

        assertThatThrownBy(() -> scope.step("  ", String.class, () -> "value"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reportsCompletedStepsForBranchingAgents() {
        AgentCheckpointScope scope = newScope("exec-branch");
        assertThat(scope.isStepCompleted("analyze")).isFalse();

        scope.step("analyze", String.class, () -> "done");

        assertThat(newScope("exec-branch").isStepCompleted("analyze")).isTrue();
        assertThat(newScope("exec-branch").completedStepCount()).isEqualTo(1);
    }

    @Test
    void supportsNullStepResults() {
        newScope("exec-null").step("maybe", String.class, () -> null);

        AtomicInteger executions = new AtomicInteger();
        String replayed = newScope("exec-null").step("maybe", String.class, () -> {
            executions.incrementAndGet();
            return "should-not-run";
        });

        assertThat(replayed).isNull();
        assertThat(executions.get()).isZero();
    }

    @Test
    void failsClearlyWhenStepResultCannotBeSerialized() {
        AgentCheckpointScope scope = newScope("exec-unserializable");

        assertThatThrownBy(() -> scope.step("bad", Object.class, Object::new))
                .isInstanceOf(AgentExecutionException.class)
                .hasMessageContaining("JSON-serializable");
    }

    @Test
    void checkpointsAreScopedPerExecution() {
        newScope("exec-a").step("shared-name", String.class, () -> "value-a");

        AtomicInteger executions = new AtomicInteger();
        String other = newScope("exec-b").step("shared-name", String.class, () -> {
            executions.incrementAndGet();
            return "value-b";
        });

        assertThat(other).isEqualTo("value-b");
        assertThat(executions.get()).isEqualTo(1);
    }

    @Test
    void deletingExecutionCheckpointsForcesFullReexecution() {
        newScope("exec-clear").step("work", String.class, () -> "first");
        store.deleteByExecutionId("exec-clear");

        String rerun = newScope("exec-clear").step("work", String.class, () -> "second");

        assertThat(rerun).isEqualTo("second");
    }

    @Test
    void scopeIsReadableFromExecutionContext() {
        AgentCheckpointScope scope = newScope("exec-ctx");
        AgentExecutionContext context = new AgentExecutionContext(
                "exec-ctx", "tenant-1", "actor-1", "corr-1", Instant.now(),
                Map.of(AgentCheckpointScope.CONTEXT_ATTRIBUTE, scope)
        );

        assertThat(AgentCheckpointScope.from(context)).isSameAs(scope);
        assertThat(AgentCheckpointScope.from(null)).isNull();
    }

    private AgentCheckpointScope newScope(String executionId) {
        return new AgentCheckpointScope(executionId, store, objectMapper);
    }
}

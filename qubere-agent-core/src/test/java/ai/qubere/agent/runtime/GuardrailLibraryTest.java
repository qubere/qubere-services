package ai.qubere.agent.runtime;

import ai.qubere.agent.api.AgentDescriptor;
import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.api.AgentRiskLevel;
import ai.qubere.agent.core.GenericAgentInput;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GuardrailLibraryTest {

    private static final AgentDescriptor DESCRIPTOR = new AgentDescriptor(
            "agent.test", "Test Agent", "1.0.0", "test", AgentRiskLevel.LOW, Set.of()
    );
    private static final AgentExecutionContext CONTEXT = new AgentExecutionContext(
            "exec-1", "tenant-1", "actor-1", "corr-1", Instant.now(), Map.of()
    );

    @Test
    void piiGuardrailBlocksEmailAddress() {
        PiiDetectionGuardrailService guardrail = new PiiDetectionGuardrailService(new AgentPlatformProperties());

        GuardrailDecision decision = guardrail.evaluateBeforeRun(
                CONTEXT, DESCRIPTOR, new GenericAgentInput(Map.of("message", "contact me at jane.doe@example.com"))
        );

        assertThat(decision.allowed()).isFalse();
    }

    @Test
    void piiGuardrailBlocksSsnPattern() {
        PiiDetectionGuardrailService guardrail = new PiiDetectionGuardrailService(new AgentPlatformProperties());

        GuardrailDecision decision = guardrail.evaluateBeforeRun(
                CONTEXT, DESCRIPTOR, new GenericAgentInput(Map.of("message", "my ssn is 123-45-6789"))
        );

        assertThat(decision.allowed()).isFalse();
    }

    @Test
    void piiGuardrailAllowsOrdinaryText() {
        PiiDetectionGuardrailService guardrail = new PiiDetectionGuardrailService(new AgentPlatformProperties());

        GuardrailDecision decision = guardrail.evaluateBeforeRun(
                CONTEXT, DESCRIPTOR, new GenericAgentInput(Map.of("message", "please summarize this document"))
        );

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void compositeGuardrailStopsAtFirstBlockingDecision() {
        AgentGuardrailService allow = (context, descriptor, input) -> GuardrailDecision.allow();
        AgentGuardrailService block = (context, descriptor, input) -> GuardrailDecision.block("blocked by second guardrail");
        AgentGuardrailService shouldNotRun = (context, descriptor, input) -> {
            throw new AssertionError("should not be reached after a blocking decision");
        };
        CompositeAgentGuardrailService composite = new CompositeAgentGuardrailService(java.util.List.of(allow, block, shouldNotRun));

        GuardrailDecision decision = composite.evaluateBeforeRun(CONTEXT, DESCRIPTOR, new GenericAgentInput(Map.of()));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo("blocked by second guardrail");
    }

    @Test
    void compositeGuardrailAllowsWhenAllDelegatesAllow() {
        AgentGuardrailService allowA = (context, descriptor, input) -> GuardrailDecision.allow();
        AgentGuardrailService allowB = (context, descriptor, input) -> GuardrailDecision.allow();
        CompositeAgentGuardrailService composite = new CompositeAgentGuardrailService(java.util.List.of(allowA, allowB));

        GuardrailDecision decision = composite.evaluateBeforeRun(CONTEXT, DESCRIPTOR, new GenericAgentInput(Map.of()));

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void outputSchemaGuardrailBlocksNullOutput() {
        GuardrailDecision decision = OutputSchemaConformanceGuardrail.validate(null, o -> true, "should not matter");

        assertThat(decision.allowed()).isFalse();
    }

    @Test
    void outputSchemaGuardrailBlocksWhenPredicateFails() {
        GuardrailDecision decision = OutputSchemaConformanceGuardrail.validate("output", o -> false, "conformance failed");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo("conformance failed");
    }

    @Test
    void outputSchemaGuardrailAllowsWhenPredicatePasses() {
        GuardrailDecision decision = OutputSchemaConformanceGuardrail.validate("output", o -> true, "unused");

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void hallucinatedIdGuardrailAllowsWhenAllIdsKnown() {
        GuardrailDecision decision = HallucinatedIdRejectionGuardrail.validate(
                java.util.List.of("id-1", "id-2"), Set.of("id-1", "id-2", "id-3")
        );

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void hallucinatedIdGuardrailBlocksUnknownIds() {
        GuardrailDecision decision = HallucinatedIdRejectionGuardrail.validate(
                java.util.List.of("id-1", "id-invented"), Set.of("id-1")
        );

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("id-invented");
    }

    @Test
    void hallucinatedIdGuardrailAllowsEmptyReferencedIds() {
        GuardrailDecision decision = HallucinatedIdRejectionGuardrail.validate(java.util.List.of(), Set.of());

        assertThat(decision.allowed()).isTrue();
    }
}

package ai.qubere.agent.tools;

import ai.qubere.agent.api.AgentExecutionContext;
import ai.qubere.agent.core.AgentErrorCode;
import ai.qubere.agent.core.AgentExecutionException;
import ai.qubere.agent.core.AgentRunMode;
import ai.qubere.agent.core.ResolvedAgentPolicy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolExecutionServiceTest {

    @Test
    void executesAllowedToolAndAuditsLifecycle() {
        List<ToolAuditStatus> statuses = new ArrayList<>();
        ToolExecutionService service = new ToolExecutionService(
                new ToolRegistry(List.of(echoTool())),
                ToolApprovalPolicy.defaultPolicy(),
                event -> statuses.add(event.status())
        );

        ToolResult result = service.execute(new ToolExecutionRequest(
                "echo",
                context(Set.of("tools.echo")),
                policy(Set.of("echo"), true),
                Map.of("message", "hello")
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.values()).containsEntry("message", "hello");
        assertThat(statuses).containsExactly(ToolAuditStatus.STARTED, ToolAuditStatus.SUCCEEDED);
    }

    @Test
    void rejectsToolNotAllowedByPolicy() {
        ToolExecutionService service = new ToolExecutionService(List.of(echoTool()));

        assertThatThrownBy(() -> service.execute(new ToolExecutionRequest(
                "echo",
                context(Set.of("tools.echo")),
                policy(Set.of("other"), true),
                Map.of()
        )))
                .isInstanceOfSatisfying(AgentExecutionException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(AgentErrorCode.TOOL_NOT_ALLOWED));
    }

    @Test
    void requiresApprovalForDestructiveTool() {
        ToolExecutionService service = new ToolExecutionService(List.of(destructiveTool()));

        assertThatThrownBy(() -> service.execute(new ToolExecutionRequest(
                "delete-record",
                context(Set.of("records.delete")),
                policy(Set.of("delete-record"), true),
                Map.of()
        )))
                .isInstanceOfSatisfying(AgentExecutionException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(AgentErrorCode.TOOL_APPROVAL_REQUIRED));
    }

    @Test
    void includesApprovalIdWhenApprovalSinkAcceptsToolRequest() {
        ToolExecutionService service = new ToolExecutionService(
                new ToolRegistry(List.of(destructiveTool())),
                ToolApprovalPolicy.defaultPolicy(),
                event -> {
                },
                (descriptor, request, decision) -> Optional.of("approval-1")
        );

        assertThatThrownBy(() -> service.execute(new ToolExecutionRequest(
                "delete-record",
                context(Set.of("records.delete")),
                policy(Set.of("delete-record"), true),
                Map.of()
        )))
                .isInstanceOfSatisfying(AgentExecutionException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(AgentErrorCode.TOOL_APPROVAL_REQUIRED);
                    assertThat(exception.getMessage()).contains("approvalId=approval-1");
                });
    }

    private AgentTool echoTool() {
        ToolDescriptor descriptor = new ToolDescriptor(
                "echo",
                "Echoes arguments",
                ToolRiskLevel.LOW,
                Set.of(ToolSideEffect.NONE),
                Set.of("tools.echo"),
                Map.of(),
                Map.of(),
                Duration.ofSeconds(5),
                false
        );
        return new AgentTool() {
            @Override
            public ToolResult execute(ToolInput input) {
                return ToolResult.success(input.arguments());
            }

            @Override
            public ToolDescriptor descriptor() {
                return descriptor;
            }
        };
    }

    private AgentTool destructiveTool() {
        ToolDescriptor descriptor = new ToolDescriptor(
                "delete-record",
                "Deletes a record",
                ToolRiskLevel.DESTRUCTIVE,
                Set.of(ToolSideEffect.DESTRUCTIVE),
                Set.of("records.delete"),
                Map.of(),
                Map.of(),
                Duration.ofSeconds(5),
                false
        );
        return new AgentTool() {
            @Override
            public ToolResult execute(ToolInput input) {
                return ToolResult.success(Map.of());
            }

            @Override
            public ToolDescriptor descriptor() {
                return descriptor;
            }
        };
    }

    private AgentExecutionContext context(Set<String> permissions) {
        return new AgentExecutionContext(
                "exec-1",
                "tenant-1",
                "actor-1",
                "corr-1",
                Instant.now(),
                Map.of("permissions", permissions)
        );
    }

    private ResolvedAgentPolicy policy(Set<String> allowedTools, boolean allowToolCalls) {
        return new ResolvedAgentPolicy(
                AgentRunMode.RECOMMEND,
                8,
                0.2,
                2048,
                allowToolCalls,
                false,
                allowedTools
        );
    }
}

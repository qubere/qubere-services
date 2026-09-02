package ai.qubere.agent.tools.config;

import ai.qubere.agent.redaction.AgentRedactionService;
import ai.qubere.agent.resilience.AgentResilienceGateway;
import ai.qubere.agent.tools.AgentTool;
import ai.qubere.agent.tools.ToolApprovalPolicy;
import ai.qubere.agent.tools.ToolAuditService;
import ai.qubere.agent.tools.ToolCallRecorder;
import ai.qubere.agent.tools.ToolExecutionService;
import ai.qubere.agent.tools.ToolRegistry;

import java.util.Collection;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class AgentToolsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ToolRegistry toolRegistry(Collection<AgentTool> tools) {
        return new ToolRegistry(tools);
    }

    @Bean
    @ConditionalOnMissingBean
    ToolApprovalPolicy toolApprovalPolicy() {
        return ToolApprovalPolicy.defaultPolicy();
    }

    @Bean
    @ConditionalOnMissingBean
    ToolAuditService toolAuditService() {
        return ToolAuditService.noop();
    }

    @Bean
    @ConditionalOnMissingBean
    ToolCallRecorder toolCallRecorder() {
        return ToolCallRecorder.noop();
    }

    @Bean
    @ConditionalOnMissingBean
    ToolExecutionService toolExecutionService(
            ToolRegistry registry,
            ToolApprovalPolicy approvalPolicy,
            ToolAuditService auditService,
            ToolCallRecorder toolCallRecorder,
            AgentRedactionService redactionService,
            ObjectProvider<ai.qubere.agent.tools.ToolApprovalRequestSink> approvalRequestSink,
            ObjectProvider<AgentResilienceGateway> resilienceGateway
    ) {
        return new ToolExecutionService(
                registry,
                approvalPolicy,
                auditService,
                approvalRequestSink.getIfAvailable(),
                toolCallRecorder,
                redactionService,
                resilienceGateway.getIfAvailable(AgentResilienceGateway::noop)
        );
    }
}
package ai.qubere.agent.mcp.config;

import ai.qubere.agent.mcp.McpToolBridge;
import ai.qubere.agent.runtime.config.AgentPlatformProperties;
import ai.qubere.agent.tools.ToolExecutionService;
import ai.qubere.agent.tools.ToolRegistry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers the MCP tool bridge when {@code agent-platform.mcp.enabled=true}, so external Model
 * Context Protocol clients can discover and invoke framework tools through the governed tool path.
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentPlatformProperties.class)
public class AgentMcpAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({ToolRegistry.class, ToolExecutionService.class})
    @ConditionalOnProperty(prefix = "agent-platform.mcp", name = "enabled", havingValue = "true")
    McpToolBridge mcpToolBridge(
            ToolRegistry toolRegistry,
            ToolExecutionService toolExecutionService,
            AgentPlatformProperties properties,
            ObjectProvider<ObjectMapper> objectMapper
    ) {
        return new McpToolBridge(
                toolRegistry,
                toolExecutionService,
                properties,
                objectMapper.getIfAvailable(ObjectMapper::new)
        );
    }
}

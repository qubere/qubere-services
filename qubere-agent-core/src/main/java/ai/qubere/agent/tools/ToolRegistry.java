package ai.qubere.agent.tools;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ToolRegistry {

    private final Map<String, AgentTool> toolsByName;

    public ToolRegistry(Collection<AgentTool> tools) {
        this.toolsByName = (tools == null ? List.<AgentTool>of() : tools).stream()
                .collect(Collectors.toUnmodifiableMap(tool -> tool.descriptor().name(), Function.identity()));
    }

    public Collection<ToolDescriptor> listTools() {
        return toolsByName.values().stream()
                .map(AgentTool::descriptor)
                .toList();
    }

    public Optional<AgentTool> findTool(String name) {
        return Optional.ofNullable(toolsByName.get(name));
    }
}

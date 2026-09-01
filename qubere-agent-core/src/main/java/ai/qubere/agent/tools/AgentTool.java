package ai.qubere.agent.tools;

@FunctionalInterface
public interface AgentTool {

    ToolResult execute(ToolInput input);

    default ToolDescriptor descriptor() {
        return ToolDescriptor.unknown();
    }
}

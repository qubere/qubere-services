package ai.qubere.document.agent.document.parser;

public record SelectionBudget(int maxTokens, int maxBytes, int maxChunks) {
}

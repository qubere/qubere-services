package ai.qubere.agent.runtime;

public record GuardrailDecision(boolean allowed, String reason) {

    public static GuardrailDecision allow() {
        return new GuardrailDecision(true, "Allowed");
    }

    public static GuardrailDecision block(String reason) {
        return new GuardrailDecision(false, reason);
    }
}

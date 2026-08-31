package ai.qubere.agent.runtime;

import ai.qubere.agent.api.AgentDescriptor;

import java.util.ArrayList;
import java.util.List;

public final class AgentDescriptorValidator {

    private AgentDescriptorValidator() {
    }

    public static void validate(AgentDescriptor descriptor) {
        List<String> errors = validateAndCollect(descriptor);
        if (!errors.isEmpty()) {
            throw new AgentDescriptorValidationException("Invalid agent descriptor: " + String.join("; ", errors));
        }
    }

    public static List<String> validateAndCollect(AgentDescriptor descriptor) {
        List<String> errors = new ArrayList<>();
        if (descriptor == null) {
            errors.add("descriptor is required");
            return errors;
        }
        requireText(errors, "id", descriptor.id());
        requireText(errors, "name", descriptor.name());
        requireText(errors, "version", descriptor.version());
        if (descriptor.riskLevel() == null) {
            errors.add("riskLevel is required");
        }
        return List.copyOf(errors);
    }

    private static void requireText(List<String> errors, String fieldName, String value) {
        if (value == null || value.isBlank()) {
            errors.add(fieldName + " is required");
        }
    }
}

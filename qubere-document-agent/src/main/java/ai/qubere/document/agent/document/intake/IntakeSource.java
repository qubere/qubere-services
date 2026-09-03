package ai.qubere.document.agent.document.intake;

/**
 * Where an intake attempt with no resolvable shipment originated, ported from
 * {@code unassignedIntake.ts}'s {@code IntakeSource}. Drives the human-readable description
 * {@link UnassignedIntakeRecorder} produces, not just an internal audit tag.
 */
public enum IntakeSource {
    DOCUMENT_UPLOAD,
    INTAKE_AGENT,
    AGENT_RUN
}

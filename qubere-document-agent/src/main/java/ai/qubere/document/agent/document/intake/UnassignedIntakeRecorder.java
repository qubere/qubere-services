package ai.qubere.document.agent.document.intake;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

/**
 * Records a document intake whose target shipment could not be determined, ported from
 * {@code unassignedIntake.ts}'s {@code recordUnassignedIntake}/{@code unassignedIntakeDescription}.
 * <p>
 * {@code UNASSIGNED_INTAKE_SEVERITY} ("High") is preserved as an informational description detail,
 * not a mapped field: the source's exception-item store has a dedicated severity column
 * ({@code createExceptionItem}), but this module's dedicated table (see
 * {@link UnassignedIntakeEntity}'s javadoc for why it is dedicated rather than shared) has no such
 * shared taxonomy to slot into, so it is folded into {@link UnassignedIntakeEntity#getDescription()}
 * instead of inventing a severity scale nothing else in this module uses yet.
 */
@Service
public class UnassignedIntakeRecorder {

    private static final String SEVERITY = "High";
    private static final String STATUS_OPEN = "Open";

    private static final Map<IntakeSource, String> SOURCE_LABEL = new EnumMap<>(Map.of(
            IntakeSource.DOCUMENT_UPLOAD, "uploaded through the document upload form",
            IntakeSource.INTAKE_AGENT, "submitted to the intake agent",
            IntakeSource.AGENT_RUN, "submitted with an agent run"
    ));

    private final UnassignedIntakeRepository repository;

    public UnassignedIntakeRecorder(UnassignedIntakeRepository repository) {
        this.repository = repository;
    }

    public UnassignedIntakeEntity record(
            String tenantId, IntakeSource source, String fileName, String docType, String requestedShipmentId
    ) {
        String description = describe(source, fileName, docType);

        UnassignedIntakeEntity entity = new UnassignedIntakeEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setTenantId(tenantId);
        entity.setSource(source);
        entity.setFileName(fileName);
        entity.setDocType(docType);
        entity.setRequestedShipmentId(requestedShipmentId);
        entity.setDescription(description);
        entity.setStatus(STATUS_OPEN);
        entity.setCreatedAt(Instant.now());
        return repository.saveAndFlush(entity);
    }

    /** {@code (severity: High)} is appended so the description alone still conveys urgency. */
    private String describe(IntakeSource source, String fileName, String docType) {
        String what = blankToDefault(fileName, "An intake item");
        String typePart = (docType != null && !docType.isBlank() && !"AUTO_DETECT".equals(docType))
                ? " (" + docType + ")"
                : "";
        return what + typePart + " was " + SOURCE_LABEL.get(source) + " without naming a shipment. "
                + "Assign it to a shipment, or close this item if it should not be filed. (severity: " + SEVERITY + ")";
    }

    private String blankToDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }
}

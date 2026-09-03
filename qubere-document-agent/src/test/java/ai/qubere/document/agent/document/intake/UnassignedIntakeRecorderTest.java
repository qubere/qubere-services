package ai.qubere.document.agent.document.intake;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class UnassignedIntakeRecorderTest {

    private final UnassignedIntakeRepository repository = Mockito.mock(UnassignedIntakeRepository.class);
    private final UnassignedIntakeRecorder recorder = new UnassignedIntakeRecorder(repository);

    @Test
    void describesADocumentUploadWithoutNamingAShipment() {
        Mockito.when(repository.saveAndFlush(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

        UnassignedIntakeEntity result = recorder.record("tenant-1", IntakeSource.DOCUMENT_UPLOAD, "invoice.pdf", "COMMERCIAL_INVOICE", null);

        assertThat(result.getDescription())
                .contains("invoice.pdf (COMMERCIAL_INVOICE) was uploaded through the document upload form without naming a shipment")
                .contains("Assign it to a shipment, or close this item if it should not be filed")
                .contains("severity: High");
        assertThat(result.getStatus()).isEqualTo("Open");
        assertThat(result.getTenantId()).isEqualTo("tenant-1");
        assertThat(result.getSource()).isEqualTo(IntakeSource.DOCUMENT_UPLOAD);
    }

    @Test
    void fallsBackToAGenericLabelWhenNoFileNameIsSupplied() {
        Mockito.when(repository.saveAndFlush(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

        UnassignedIntakeEntity result = recorder.record("tenant-1", IntakeSource.INTAKE_AGENT, null, null, null);

        assertThat(result.getDescription()).startsWith("An intake item was submitted to the intake agent without naming a shipment");
    }

    @Test
    void omitsTheDocTypeSuffixWhenItIsTheAutoDetectPlaceholder() {
        Mockito.when(repository.saveAndFlush(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

        UnassignedIntakeEntity result = recorder.record("tenant-1", IntakeSource.AGENT_RUN, "scan.pdf", "AUTO_DETECT", null);

        assertThat(result.getDescription()).startsWith("scan.pdf was submitted with an agent run without naming a shipment");
    }

    @Test
    void persistsViaTheRepository() {
        ArgumentCaptor<UnassignedIntakeEntity> captor = ArgumentCaptor.forClass(UnassignedIntakeEntity.class);
        Mockito.when(repository.saveAndFlush(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        recorder.record("tenant-1", IntakeSource.DOCUMENT_UPLOAD, "invoice.pdf", null, "shipment-req-1");

        UnassignedIntakeEntity captured = captor.getValue();
        assertThat(captured.getId()).isNotBlank();
        assertThat(captured.getRequestedShipmentId()).isEqualTo("shipment-req-1");
        assertThat(captured.getCreatedAt()).isNotNull();
    }
}

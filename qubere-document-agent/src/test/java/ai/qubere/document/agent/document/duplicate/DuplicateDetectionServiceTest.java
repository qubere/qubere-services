package ai.qubere.document.agent.document.duplicate;

import ai.qubere.document.agent.document.processing.ProcessingRunService;
import ai.qubere.document.agent.document.parser.ProcessingProfile;
import ai.qubere.document.agent.document.parser.ProcessingReason;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "agent-platform.prompts.provider=in-memory",
        "agent-platform.async.worker-enabled=false",
        "document-agent.parser.processing-limits.poll-initial-delay-millis=600000"
})
class DuplicateDetectionServiceTest {

    @Autowired
    private ProcessingRunService runService;

    @Autowired
    private DuplicateDetectionService duplicateDetectionService;

    @Test
    void findsTheSameChecksumFiledAgainstADifferentShipment() {
        String checksum = uniqueChecksum();
        runService.enqueue(uniqueDocumentId(), "shipment-original", checksum, "tenant-1", "actor-1", "corr-1",
                ProcessingProfile.STANDARD, ProcessingReason.INITIAL);
        String newDocumentId = uniqueDocumentId();
        runService.enqueue(newDocumentId, "shipment-new", checksum, "tenant-1", "actor-1", "corr-2",
                ProcessingProfile.STANDARD, ProcessingReason.INITIAL);

        List<CrossShipmentDuplicate> duplicates = duplicateDetectionService.findCrossShipmentDuplicates(
                "tenant-1", checksum, "shipment-new", newDocumentId);

        assertThat(duplicates).hasSize(1);
        assertThat(duplicates.get(0).shipmentId()).isEqualTo("shipment-original");
    }

    @Test
    void doesNotReportTheSameShipmentAsItsOwnDuplicate() {
        String checksum = uniqueChecksum();
        runService.enqueue(uniqueDocumentId(), "shipment-1", checksum, "tenant-1", "actor-1", "corr-1",
                ProcessingProfile.STANDARD, ProcessingReason.INITIAL);
        String secondDocumentId = uniqueDocumentId();
        runService.enqueue(secondDocumentId, "shipment-1", checksum, "tenant-1", "actor-1", "corr-2",
                ProcessingProfile.STANDARD, ProcessingReason.INITIAL);

        List<CrossShipmentDuplicate> duplicates = duplicateDetectionService.findCrossShipmentDuplicates(
                "tenant-1", checksum, "shipment-1", secondDocumentId);

        assertThat(duplicates).isEmpty();
    }

    @Test
    void doesNotReportADocumentAsItsOwnDuplicateAcrossAnOcrRetryRun() {
        String checksum = uniqueChecksum();
        String documentId = uniqueDocumentId();
        runService.enqueue(documentId, "shipment-1", checksum, "tenant-1", "actor-1", "corr-1",
                ProcessingProfile.STANDARD, ProcessingReason.INITIAL);
        // Same document, a second run (as an OCR retry would create) -- must still collapse to one.
        runService.enqueue(documentId, "shipment-1", checksum, "tenant-1", "actor-1", "corr-1",
                ProcessingProfile.OCR_FALLBACK, ProcessingReason.OCR_RETRY);

        List<CrossShipmentDuplicate> duplicates = duplicateDetectionService.findCrossShipmentDuplicates(
                "tenant-1", checksum, "shipment-1", documentId);

        assertThat(duplicates).isEmpty();
    }

    @Test
    void returnsNothingForABlankChecksum() {
        assertThat(duplicateDetectionService.findCrossShipmentDuplicates("tenant-1", "", "shipment-1", null)).isEmpty();
        assertThat(duplicateDetectionService.findCrossShipmentDuplicates("tenant-1", null, "shipment-1", null)).isEmpty();
    }

    private String uniqueDocumentId() {
        return "dup-doc-" + UUID.randomUUID();
    }

    private String uniqueChecksum() {
        return "sha-" + UUID.randomUUID().toString().replace("-", "");
    }
}

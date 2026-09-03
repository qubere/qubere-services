package ai.qubere.document.agent.document.review;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractionFieldRepository extends JpaRepository<ExtractionFieldEntity, String> {

    List<ExtractionFieldEntity> findByDocumentIdOrderByCreatedAtAsc(String documentId);
}

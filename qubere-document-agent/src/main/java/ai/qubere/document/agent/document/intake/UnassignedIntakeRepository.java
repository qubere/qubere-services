package ai.qubere.document.agent.document.intake;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UnassignedIntakeRepository extends JpaRepository<UnassignedIntakeEntity, String> {
}

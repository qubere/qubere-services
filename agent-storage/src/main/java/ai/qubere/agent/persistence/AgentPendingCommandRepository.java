package ai.qubere.agent.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentPendingCommandRepository extends JpaRepository<AgentPendingCommandEntity, String> {
}

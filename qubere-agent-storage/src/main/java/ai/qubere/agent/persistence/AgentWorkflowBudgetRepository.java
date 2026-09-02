package ai.qubere.agent.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentWorkflowBudgetRepository extends JpaRepository<AgentWorkflowBudgetEntity, String> {
}

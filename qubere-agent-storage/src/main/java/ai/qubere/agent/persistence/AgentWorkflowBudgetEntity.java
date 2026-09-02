package ai.qubere.agent.persistence;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "agent_workflow_budget")
public class AgentWorkflowBudgetEntity {

    @Id
    @Column(name = "workflow_id", length = 64, nullable = false)
    private String workflowId;

    @Column(name = "used_agent_invocations", nullable = false)
    private int usedAgentInvocations;

    @Column(name = "used_tool_calls", nullable = false)
    private int usedToolCalls;

    @Column(name = "used_cost_usd", precision = 19, scale = 6, nullable = false)
    private BigDecimal usedCostUsd = BigDecimal.ZERO;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Optimistic locking is what makes cross-process consumption safe: two services consuming the
     * same workflow budget concurrently cannot both read-then-write a stale total, because the
     * losing transaction fails and retries against the updated row.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public int getUsedAgentInvocations() {
        return usedAgentInvocations;
    }

    public void setUsedAgentInvocations(int usedAgentInvocations) {
        this.usedAgentInvocations = usedAgentInvocations;
    }

    public int getUsedToolCalls() {
        return usedToolCalls;
    }

    public void setUsedToolCalls(int usedToolCalls) {
        this.usedToolCalls = usedToolCalls;
    }

    public BigDecimal getUsedCostUsd() {
        return usedCostUsd == null ? BigDecimal.ZERO : usedCostUsd;
    }

    public void setUsedCostUsd(BigDecimal usedCostUsd) {
        this.usedCostUsd = usedCostUsd == null ? BigDecimal.ZERO : usedCostUsd;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}

package ai.qubere.agent.persistence;

import ai.qubere.agent.orchestration.DistributedWorkflowBudgetStore;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * JPA-backed {@link DistributedWorkflowBudgetStore}, so an aggregate workflow ceiling holds even
 * when the workflow spans multiple services.
 * <p>
 * Each consumption attempt runs in its own transaction and relies on the entity's
 * {@code @Version} column for atomicity: if another process consumed the same workflow's budget
 * concurrently, the optimistic lock fails and the attempt is retried against the fresh total.
 * Without that, two services could each read the same usage figure and both conclude they were
 * under the limit.
 * <p>
 * A {@link TransactionTemplate} is used rather than {@code @Transactional} on an internal method
 * because the retry loop invokes the transactional unit from within the same class, and Spring's
 * proxy-based {@code @Transactional} does not apply to self-invocation — each retry would
 * otherwise run outside its own transaction.
 */
@Component
public class JpaDistributedWorkflowBudgetStore implements DistributedWorkflowBudgetStore {

    private static final int MAX_CONTENTION_RETRIES = 5;

    private final AgentWorkflowBudgetRepository repository;
    private final TransactionTemplate transactionTemplate;

    public JpaDistributedWorkflowBudgetStore(
            AgentWorkflowBudgetRepository repository,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        // Each attempt must commit or roll back independently so a failed optimistic lock does
        // not poison an enclosing transaction.
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public WorkflowBudgetDecision tryConsume(
            String workflowId,
            int agentInvocations,
            int toolCalls,
            BigDecimal costUsd,
            WorkflowBudgetLimits limits
    ) {
        if (workflowId == null || workflowId.isBlank()) {
            throw new IllegalArgumentException("workflowId is required to consume a distributed workflow budget");
        }
        for (int attempt = 1; attempt <= MAX_CONTENTION_RETRIES; attempt++) {
            try {
                return transactionTemplate.execute(status ->
                        consumeOnce(workflowId, agentInvocations, toolCalls, costUsd, limits));
            } catch (ObjectOptimisticLockingFailureException ex) {
                if (attempt == MAX_CONTENTION_RETRIES) {
                    throw ex;
                }
                // Another participant in this workflow updated the budget first; re-read and retry.
            }
        }
        throw new IllegalStateException("Unable to consume workflow budget after contention retries: " + workflowId);
    }

    private WorkflowBudgetDecision consumeOnce(
            String workflowId,
            int agentInvocations,
            int toolCalls,
            BigDecimal costUsd,
            WorkflowBudgetLimits limits
    ) {
        AgentWorkflowBudgetEntity entity = repository.findById(workflowId).orElseGet(() -> {
            AgentWorkflowBudgetEntity created = new AgentWorkflowBudgetEntity();
            created.setWorkflowId(workflowId);
            created.setUsedCostUsd(BigDecimal.ZERO);
            return created;
        });

        int nextAgentInvocations = entity.getUsedAgentInvocations() + Math.max(0, agentInvocations);
        int nextToolCalls = entity.getUsedToolCalls() + Math.max(0, toolCalls);
        BigDecimal nextCost = entity.getUsedCostUsd()
                .add(costUsd == null || costUsd.signum() <= 0 ? BigDecimal.ZERO : costUsd);

        WorkflowBudgetLimits safeLimits = limits == null
                ? new WorkflowBudgetLimits(0, 0, BigDecimal.ZERO)
                : limits;

        if (safeLimits.maxAgentInvocations() > 0 && nextAgentInvocations > safeLimits.maxAgentInvocations()) {
            return WorkflowBudgetDecision.deny(
                    "workflow exceeded maxAgentInvocations=" + safeLimits.maxAgentInvocations(),
                    entity.getUsedAgentInvocations(), entity.getUsedToolCalls(), entity.getUsedCostUsd()
            );
        }
        if (safeLimits.maxToolCalls() > 0 && nextToolCalls > safeLimits.maxToolCalls()) {
            return WorkflowBudgetDecision.deny(
                    "workflow exceeded maxToolCalls=" + safeLimits.maxToolCalls(),
                    entity.getUsedAgentInvocations(), entity.getUsedToolCalls(), entity.getUsedCostUsd()
            );
        }
        if (safeLimits.maxEstimatedCostUsd().signum() > 0 && nextCost.compareTo(safeLimits.maxEstimatedCostUsd()) > 0) {
            return WorkflowBudgetDecision.deny(
                    "workflow exceeded maxEstimatedCostUsd="
                            + safeLimits.maxEstimatedCostUsd().stripTrailingZeros().toPlainString() + " USD",
                    entity.getUsedAgentInvocations(), entity.getUsedToolCalls(), entity.getUsedCostUsd()
            );
        }

        entity.setUsedAgentInvocations(nextAgentInvocations);
        entity.setUsedToolCalls(nextToolCalls);
        entity.setUsedCostUsd(nextCost);
        entity.setUpdatedAt(Instant.now());
        repository.saveAndFlush(entity);

        return WorkflowBudgetDecision.allow(nextAgentInvocations, nextToolCalls, nextCost);
    }

    @Override
    public void release(String workflowId) {
        if (workflowId == null || workflowId.isBlank()) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            if (repository.existsById(workflowId)) {
                repository.deleteById(workflowId);
            }
        });
    }
}

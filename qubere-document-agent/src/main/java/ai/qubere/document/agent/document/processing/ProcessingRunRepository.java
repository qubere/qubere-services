package ai.qubere.document.agent.document.processing;

import ai.qubere.document.agent.document.parser.ProcessingRunState;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessingRunRepository extends JpaRepository<ProcessingRunEntity, String> {

    /** Backs idempotent enqueue: a duplicate submission for the same key finds the existing run. */
    Optional<ProcessingRunEntity> findByIdempotencyKey(String idempotencyKey);

    /** Runs ready to submit to the provider: queued, and not held back by a pending retry delay. */
    @Query("select r from ProcessingRunEntity r where r.state = :state "
            + "and (r.nextRetryAt is null or r.nextRetryAt <= :now) order by r.createdAt asc")
    List<ProcessingRunEntity> findAwaitingSubmission(
            @Param("state") ProcessingRunState state, @Param("now") Instant now, Limit limit);

    /** Runs ready to be polled: submitted/polling, and their next poll time has arrived. */
    @Query("select r from ProcessingRunEntity r where r.state in :states "
            + "and r.nextPollAt is not null and r.nextPollAt <= :now order by r.nextPollAt asc")
    List<ProcessingRunEntity> findAwaitingPoll(
            @Param("states") List<ProcessingRunState> states, @Param("now") Instant now, Limit limit);

    /** Non-terminal runs whose worker heartbeat has gone stale — candidates for reclaim. */
    @Query("select r from ProcessingRunEntity r where r.state in :states "
            + "and (r.heartbeatAt is null or r.heartbeatAt <= :before) order by r.heartbeatAt asc")
    List<ProcessingRunEntity> findStale(
            @Param("states") List<ProcessingRunState> states, @Param("before") Instant before, Limit limit);

    /** Backs {@code DuplicateDetectionService}'s cross-shipment duplicate lookup by content checksum. */
    List<ProcessingRunEntity> findByTenantIdAndContentSha256OrderByCreatedAtDesc(String tenantId, String contentSha256, Limit limit);
}

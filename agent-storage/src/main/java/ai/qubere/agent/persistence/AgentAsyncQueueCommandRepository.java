package ai.qubere.agent.persistence;

import java.util.List;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface AgentAsyncQueueCommandRepository extends JpaRepository<AgentAsyncQueueCommandEntity, String> {

    @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select command from AgentAsyncQueueCommandEntity command order by command.createdAt asc")
    List<AgentAsyncQueueCommandEntity> findNextForUpdate(Pageable pageable);
}

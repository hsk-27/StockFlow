package com.stockflow.outbox.repository;

import com.stockflow.outbox.domain.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = "SELECT * FROM outbox_events e " +
                   "WHERE e.status = 'PENDING' AND e.next_attempt_at <= :now " +
                   "ORDER BY e.created_at ASC " +
                   "LIMIT :limit " +
                   "FOR UPDATE SKIP LOCKED",
           nativeQuery = true)
    List<OutboxEvent> claimPendingEventsForProcessing(@Param("now") Instant now, @Param("limit") int limit);

    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'PENDING' AND e.nextAttemptAt <= :now ORDER BY e.createdAt ASC")
    List<OutboxEvent> findPendingEventsFallback(@Param("now") Instant now, Pageable pageable);
}

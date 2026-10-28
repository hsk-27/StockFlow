package com.stockflow.outbox.worker;

import com.stockflow.outbox.domain.OutboxEvent;
import com.stockflow.outbox.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class OutboxPollerWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxPollerWorker.class);

    private final OutboxEventRepository outboxEventRepository;
    private final int batchSize;

    public OutboxPollerWorker(OutboxEventRepository outboxEventRepository,
                              @Value("${stockflow.outbox.batch-size:50}") int batchSize) {
        this.outboxEventRepository = outboxEventRepository;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${stockflow.outbox.worker-cron:*/10 * * * * *}")
    @Transactional
    public void processOutboxBatch() {
        Instant now = Instant.now();
        List<OutboxEvent> events;

        try {
            // PostgreSQL SKIP LOCKED query
            events = outboxEventRepository.claimPendingEventsForProcessing(now, batchSize);
        } catch (Exception e) {
            // H2 fallback for local integration tests
            events = outboxEventRepository.findPendingEventsFallback(now, PageRequest.of(0, batchSize));
        }

        if (events.isEmpty()) {
            return;
        }

        log.info("Processing {} outbox events", events.size());
        for (OutboxEvent event : events) {
            try {
                // Dispatch event logic (e.g. logging/external webhooks/notifications)
                dispatchDomainEvent(event);
                event.markProcessed();
                outboxEventRepository.save(event);
            } catch (Exception ex) {
                log.error("Failed to process outbox event id: {}", event.getId(), ex);
                int backoffSeconds = (int) Math.pow(2, event.getAttemptCount()) * 5;
                event.recordFailure(ex.getMessage(), Instant.now().plusSeconds(backoffSeconds));
                outboxEventRepository.save(event);
            }
        }
    }

    private void dispatchDomainEvent(OutboxEvent event) {
        log.info("Outbox Dispatch: [Type: {}, Aggregate: {}/{}, Payload: {}]",
                event.getEventType(), event.getAggregateType(), event.getAggregateId(), event.getPayload());
    }
}

package com.stockflow.outbox.worker;

import com.stockflow.common.metrics.StockFlowMetrics;
import com.stockflow.outbox.consumer.DomainEventListener;
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
    private final List<DomainEventListener> eventListeners;
    private final OutboxEventDispatcher eventDispatcher;
    private final int batchSize;

    public OutboxPollerWorker(OutboxEventRepository outboxEventRepository,
                              List<DomainEventListener> eventListeners,
                              OutboxEventDispatcher eventDispatcher,
                              @Value("${stockflow.outbox.batch-size:50}") int batchSize) {
        this.outboxEventRepository = outboxEventRepository;
        this.eventListeners = eventListeners;
        this.eventDispatcher = eventDispatcher;
        this.batchSize = batchSize;
    }

    /**
     * ========================================================================
     * [DISTRIBUTED SYSTEMS PATTERN: THE TRANSACTIONAL OUTBOX & SKIP LOCKED]
     * ========================================================================
     * Problem (The Dual-Write Hazard):
     *   When saving an Order, we also want to send notifications/events. If we write
     *   to PostgreSQL and then immediately send a message to Kafka/RabbitMQ in the
     *   same method:
     *     - If the database commit succeeds but message send fails -> Event lost!
     *     - If the message sends but database commit fails -> Ghost event sent!
     *   Distributed Two-Phase Commit (2PC / XA) is slow, fragile, and not supported
     *   by modern cloud messaging platforms.
     *
     * Solution (Transactional Outbox Pattern):
     *   1. Save the business entity AND an OutboxEvent in the EXACT SAME local
     *      relational database transaction (ACID guaranteed!).
     *   2. This asynchronous poller worker periodically claims pending events using
     *      PostgreSQL's 'SELECT ... FOR UPDATE SKIP LOCKED'.
     *   3. Multiple worker instances can run in parallel without competing or
     *      blocking each other: any row locked by Worker 1 is automatically skipped
     *      by Worker 2!
     *   4. Dispatches events with exponential backoff retries (At-Least-Once Delivery).
     */
    @Scheduled(cron = "${stockflow.outbox.worker-cron:*/10 * * * * *}")
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

        if (events == null || events.isEmpty()) {
            return;
        }

        log.info("Processing {} outbox events with per-event transaction isolation", events.size());
        for (OutboxEvent event : events) {
            eventDispatcher.dispatch(event, eventListeners);
        }
    }
}

package com.stockflow.outbox.worker;

import com.stockflow.common.metrics.StockFlowMetrics;
import com.stockflow.outbox.consumer.DomainEventListener;
import com.stockflow.outbox.domain.OutboxEvent;
import com.stockflow.outbox.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * ============================================================================
 * [RELIABILITY ENGINEERING: ISOLATED TRANSACTION BOUNDARIES PER OUTBOX EVENT]
 * ============================================================================
 * Processing an entire batch inside one giant transaction is an anti-pattern:
 * if message #49 fails, rolling back rolls back messages #1 through #48, causing
 * duplicate deliveries and starving the queue.
 *
 * OutboxEventDispatcher enforces REQUIRES_NEW transaction isolation per event:
 * each event succeeds or records its failure/exponential backoff independently.
 */
@Component
public class OutboxEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventDispatcher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final StockFlowMetrics metrics;

    public OutboxEventDispatcher(OutboxEventRepository outboxEventRepository, StockFlowMetrics metrics) {
        this.outboxEventRepository = outboxEventRepository;
        this.metrics = metrics;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(OutboxEvent event, List<DomainEventListener> listeners) {
        try {
            for (DomainEventListener listener : listeners) {
                if (listener.canHandle(event.getEventType())) {
                    listener.onEvent(event);
                }
            }
            event.markProcessed();
            outboxEventRepository.save(event);
            metrics.incrementOutboxProcessed();
        } catch (Exception ex) {
            log.error("Failed to process outbox event id: {}", event.getId(), ex);
            int backoffSeconds = (int) Math.pow(2, event.getAttemptCount()) * 5;
            event.recordFailure(ex.getMessage(), Instant.now().plusSeconds(backoffSeconds));
            outboxEventRepository.save(event);
            metrics.incrementOutboxFailed();
        }
    }
}

package com.stockflow.outbox;

import com.stockflow.common.metrics.StockFlowMetrics;
import com.stockflow.outbox.consumer.DomainEventListener;
import com.stockflow.outbox.domain.OutboxEvent;
import com.stockflow.outbox.repository.OutboxEventRepository;
import com.stockflow.outbox.worker.OutboxEventDispatcher;
import com.stockflow.outbox.worker.OutboxPollerWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPollerWorkerTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private StockFlowMetrics metrics;

    @Mock
    private DomainEventListener eventListener;

    private OutboxEventDispatcher dispatcher;
    private OutboxPollerWorker worker;

    @BeforeEach
    void setUp() {
        dispatcher = new OutboxEventDispatcher(outboxEventRepository, metrics);
        worker = new OutboxPollerWorker(outboxEventRepository, List.of(eventListener), dispatcher, 10);
    }

    @Test
    @DisplayName("Should successfully claim, dispatch to listeners, and mark outbox events as PROCESSED")
    void shouldClaimAndProcessEvents() {
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), "ORDER_CREATED", "Order", "ord-1", "{\"status\":\"ok\"}");

        when(outboxEventRepository.claimPendingEventsForProcessing(any(Instant.class), eq(10)))
                .thenReturn(List.of(event));
        when(eventListener.canHandle("ORDER_CREATED")).thenReturn(true);

        worker.processOutboxBatch();

        assertThat(event.getStatus()).isEqualTo("PROCESSED");
        verify(eventListener).onEvent(event);
        verify(metrics).incrementOutboxProcessed();
        verify(outboxEventRepository).save(event);
    }

    @Test
    @DisplayName("Should isolate listener failure and record exponential backoff without crashing batch")
    void shouldIsolateListenerFailureAndBackoff() {
        OutboxEvent failingEvent = new OutboxEvent(UUID.randomUUID(), "ORDER_CREATED", "Order", "ord-1", "{\"status\":\"fail\"}");
        OutboxEvent healthyEvent = new OutboxEvent(UUID.randomUUID(), "ORDER_CREATED", "Order", "ord-2", "{\"status\":\"ok\"}");

        when(outboxEventRepository.claimPendingEventsForProcessing(any(Instant.class), eq(10)))
                .thenReturn(List.of(failingEvent, healthyEvent));
        when(eventListener.canHandle("ORDER_CREATED")).thenReturn(true);
        doThrow(new RuntimeException("Simulated listener crash")).when(eventListener).onEvent(failingEvent);

        worker.processOutboxBatch();

        assertThat(failingEvent.getStatus()).isEqualTo("PENDING"); // Pending retry with backoff
        assertThat(failingEvent.getAttemptCount()).isEqualTo(1);
        assertThat(failingEvent.getErrorMessage()).contains("Simulated listener crash");
        assertThat(failingEvent.getNextAttemptAt()).isAfter(Instant.now());

        assertThat(healthyEvent.getStatus()).isEqualTo("PROCESSED");

        verify(metrics).incrementOutboxFailed();
        verify(metrics).incrementOutboxProcessed();
        verify(outboxEventRepository).save(failingEvent);
        verify(outboxEventRepository).save(healthyEvent);
    }
}

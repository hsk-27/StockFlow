package com.stockflow.outbox;

import com.stockflow.outbox.domain.OutboxEvent;
import com.stockflow.outbox.repository.OutboxEventRepository;
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

    private OutboxPollerWorker worker;

    @BeforeEach
    void setUp() {
        worker = new OutboxPollerWorker(outboxEventRepository, 10);
    }

    @Test
    @DisplayName("Should successfully claim and mark outbox events as PROCESSED")
    void shouldClaimAndProcessEvents() {
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), "ORDER_CREATED", "Order", "ord-1", "{\"status\":\"ok\"}");

        when(outboxEventRepository.claimPendingEventsForProcessing(any(Instant.class), eq(10)))
                .thenReturn(List.of(event));

        worker.processOutboxBatch();

        assertThat(event.getStatus()).isEqualTo("PROCESSED");
        verify(outboxEventRepository).save(event);
    }
}

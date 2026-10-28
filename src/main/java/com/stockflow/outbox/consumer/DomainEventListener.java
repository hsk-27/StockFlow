package com.stockflow.outbox.consumer;

import com.stockflow.outbox.domain.OutboxEvent;

public interface DomainEventListener {
    boolean canHandle(String eventType);
    void onEvent(OutboxEvent event);
}

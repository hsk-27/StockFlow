package com.stockflow.outbox.consumer;

import com.stockflow.outbox.domain.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class FulfilmentNotificationListener implements DomainEventListener {

    private static final Logger log = LoggerFactory.getLogger(FulfilmentNotificationListener.class);

    private static final Set<String> HANDLED_TYPES = Set.of(
            "SHIPMENT_CREATED",
            "SHIPMENT_DELIVERED"
    );

    @Override
    public boolean canHandle(String eventType) {
        return HANDLED_TYPES.contains(eventType);
    }

    @Override
    public void onEvent(OutboxEvent event) {
        if ("SHIPMENT_CREATED".equals(event.getEventType())) {
            log.info("[Notification Worker] Shipment Tracking SMS dispatched for Shipment ID: {}", event.getAggregateId());
        } else {
            log.info("[Notification Worker] Delivery Confirmation email dispatched for Shipment ID: {}", event.getAggregateId());
        }
    }
}

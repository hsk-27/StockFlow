package com.stockflow.outbox.consumer;

import com.stockflow.common.metrics.StockFlowMetrics;
import com.stockflow.outbox.domain.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class OrderNotificationListener implements DomainEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationListener.class);
    private final StockFlowMetrics metrics;

    private static final Set<String> HANDLED_TYPES = Set.of(
            "ORDER_CREATED",
            "ORDER_CANCELLED",
            "ORDER_EXPIRED"
    );

    public OrderNotificationListener(StockFlowMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public boolean canHandle(String eventType) {
        return HANDLED_TYPES.contains(eventType);
    }

    @Override
    public void onEvent(OutboxEvent event) {
        switch (event.getEventType()) {
            case "ORDER_CREATED" -> {
                metrics.incrementOrdersCreated();
                log.info("[Notification Worker] Dispatching Order Confirmation for Order ID: {}", event.getAggregateId());
            }
            case "ORDER_CANCELLED" -> {
                metrics.incrementOrdersCancelled();
                log.info("[Notification Worker] Dispatching Order Cancellation Notice for Order ID: {}", event.getAggregateId());
            }
            case "ORDER_EXPIRED" -> {
                log.info("[Notification Worker] Dispatching Stock Timeout Notice for Order ID: {}", event.getAggregateId());
            }
        }
    }
}

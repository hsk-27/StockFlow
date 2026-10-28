package com.stockflow.outbox.consumer;

import com.stockflow.common.metrics.StockFlowMetrics;
import com.stockflow.outbox.domain.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class PaymentNotificationListener implements DomainEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentNotificationListener.class);
    private final StockFlowMetrics metrics;

    private static final Set<String> HANDLED_TYPES = Set.of(
            "PAYMENT_SUCCEEDED",
            "PAYMENT_FAILED",
            "PAYMENT_SUCCEEDED_WEBHOOK"
    );

    public PaymentNotificationListener(StockFlowMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public boolean canHandle(String eventType) {
        return HANDLED_TYPES.contains(eventType);
    }

    @Override
    public void onEvent(OutboxEvent event) {
        if ("PAYMENT_FAILED".equals(event.getEventType())) {
            metrics.incrementPaymentsFailed();
            log.info("[Notification Worker] Payment Failure alert dispatched for: {}", event.getAggregateId());
        } else {
            metrics.incrementPaymentsSucceeded();
            log.info("[Notification Worker] Payment Receipt email dispatched for: {}", event.getAggregateId());
        }
    }
}

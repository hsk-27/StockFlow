package com.stockflow.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class StockFlowMetrics {

    private final Counter ordersCreatedCounter;
    private final Counter ordersCancelledCounter;
    private final Counter paymentsSucceededCounter;
    private final Counter paymentsFailedCounter;
    private final Counter outboxProcessedCounter;
    private final Counter outboxFailedCounter;

    public StockFlowMetrics(MeterRegistry registry) {
        this.ordersCreatedCounter = Counter.builder("stockflow.orders.created")
                .description("Total number of orders created")
                .register(registry);

        this.ordersCancelledCounter = Counter.builder("stockflow.orders.cancelled")
                .description("Total number of orders cancelled")
                .register(registry);

        this.paymentsSucceededCounter = Counter.builder("stockflow.payments.succeeded")
                .description("Total number of successfully processed payments")
                .register(registry);

        this.paymentsFailedCounter = Counter.builder("stockflow.payments.failed")
                .description("Total number of failed payment attempts")
                .register(registry);

        this.outboxProcessedCounter = Counter.builder("stockflow.outbox.processed")
                .description("Total outbox events successfully dispatched")
                .register(registry);

        this.outboxFailedCounter = Counter.builder("stockflow.outbox.failed")
                .description("Total outbox events that encountered failures")
                .register(registry);
    }

    public void incrementOrdersCreated() {
        ordersCreatedCounter.increment();
    }

    public void incrementOrdersCancelled() {
        ordersCancelledCounter.increment();
    }

    public void incrementPaymentsSucceeded() {
        paymentsSucceededCounter.increment();
    }

    public void incrementPaymentsFailed() {
        paymentsFailedCounter.increment();
    }

    public void incrementOutboxProcessed() {
        outboxProcessedCounter.increment();
    }

    public void incrementOutboxFailed() {
        outboxFailedCounter.increment();
    }
}

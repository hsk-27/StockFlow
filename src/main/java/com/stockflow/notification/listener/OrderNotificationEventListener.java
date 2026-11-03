package com.stockflow.notification.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.notification.service.NotificationService;
import com.stockflow.outbox.consumer.DomainEventListener;
import com.stockflow.outbox.domain.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * ============================================================================
 * [DOMAIN EVENT CONSUMER: ORDER & FULFILMENT NOTIFICATION LISTENER]
 * ============================================================================
 * Subscribed to asynchronous transactional outbox events to deliver customer
 * and vendor transactional updates without slowing down primary ACID transactions.
 */
@Component
public class OrderNotificationEventListener implements DomainEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationEventListener.class);

    private static final Set<String> SUPPORTED_EVENTS = Set.of(
            "ORDER_CREATED",
            "ORDER_CANCELLED",
            "PAYMENT_SUCCEEDED",
            "PAYMENT_FAILED",
            "SHIPMENT_CREATED",
            "SHIPMENT_DELIVERED",
            "RETURN_REQUESTED",
            "REFUND_COMPLETED"
    );

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public OrderNotificationEventListener(NotificationService notificationService, ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean canHandle(String eventType) {
        return SUPPORTED_EVENTS.contains(eventType);
    }

    @Override
    public void onEvent(OutboxEvent event) {
        try {
            JsonNode payload = objectMapper.readTree(event.getPayload());

            switch (event.getEventType()) {
                case "ORDER_CREATED" -> {
                    String orderNumber = payload.path("orderNumber").asText("unknown");
                    String amount = payload.path("totalAmount").asText("0.00");
                    String currency = payload.path("currency").asText("USD");
                    notificationService.sendEmail(
                            "customer-notification",
                            "Order Placed Successfully: " + orderNumber,
                            "Your order " + orderNumber + " totaling " + amount + " " + currency + " has been placed and inventory reserved."
                    );
                }
                case "PAYMENT_SUCCEEDED" -> {
                    String orderId = payload.path("orderId").asText("");
                    String amount = payload.path("amount").asText("");
                    notificationService.sendEmail(
                            "customer-notification",
                            "Payment Confirmed for Order: " + orderId,
                            "Your payment of " + amount + " was processed successfully."
                    );
                }
                case "PAYMENT_FAILED" -> {
                    String orderId = payload.path("orderId").asText("");
                    String reason = payload.path("reason").asText("Payment could not be completed.");
                    notificationService.sendEmail(
                            "customer-notification",
                            "Payment Failed for Order: " + orderId,
                            "Payment attempt failed: " + reason
                    );
                }
                case "SHIPMENT_CREATED" -> {
                    String carrier = payload.path("carrier").asText("");
                    String tracking = payload.path("trackingNumber").asText("");
                    notificationService.sendEmail(
                            "customer-notification",
                            "Your Order Has Shipped via " + carrier,
                            "Tracking number: " + tracking
                    );
                }
                case "SHIPMENT_DELIVERED" -> {
                    String orderId = payload.path("orderId").asText("");
                    notificationService.sendEmail(
                            "customer-notification",
                            "Your Package Has Arrived",
                            "Shipment for order " + orderId + " was confirmed delivered."
                    );
                }
                case "RETURN_REQUESTED" -> {
                    String orderId = payload.path("orderId").asText("");
                    notificationService.sendEmail(
                            "seller-notification",
                            "New Return Request Submitted",
                            "Return request filed for order " + orderId
                    );
                }
                case "REFUND_COMPLETED" -> {
                    String amount = payload.path("amount").asText("");
                    notificationService.sendEmail(
                            "customer-notification",
                            "Refund Processed",
                            "A refund of " + amount + " has been processed back to your original payment method."
                    );
                }
                case "ORDER_CANCELLED" -> {
                    String reason = payload.path("reason").asText("");
                    notificationService.sendEmail(
                            "customer-notification",
                            "Order Cancelled",
                            "Your order was cancelled: " + reason
                    );
                }
                default -> log.debug("Unhandled event type for notification: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Failed to parse event payload for notification, event ID: {}", event.getId(), e);
            throw new RuntimeException("Notification delivery failed", e);
        }
    }
}

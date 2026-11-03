package com.stockflow.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.notification.listener.OrderNotificationEventListener;
import com.stockflow.notification.service.NotificationService;
import com.stockflow.outbox.domain.OutboxEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderNotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OrderNotificationEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderNotificationEventListener(notificationService, objectMapper);
    }

    @Test
    @DisplayName("Should handle ORDER_CREATED and dispatch customer confirmation email")
    void shouldHandleOrderCreatedNotification() {
        assertThat(listener.canHandle("ORDER_CREATED")).isTrue();

        String payload = "{\"orderNumber\":\"ORD-999\",\"totalAmount\":150.00,\"currency\":\"USD\"}";
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), "ORDER_CREATED", "Order", "ord-1", payload);

        listener.onEvent(event);

        verify(notificationService).sendEmail(
                eq("customer-notification"),
                contains("ORD-999"),
                contains("150")
        );
    }

    @Test
    @DisplayName("Should handle SHIPMENT_CREATED and dispatch tracking notice")
    void shouldHandleShipmentCreatedNotification() {
        String payload = "{\"carrier\":\"DHL Express\",\"trackingNumber\":\"TRK-777\"}";
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), "SHIPMENT_CREATED", "Shipment", "ship-1", payload);

        listener.onEvent(event);

        verify(notificationService).sendEmail(
                eq("customer-notification"),
                contains("DHL Express"),
                contains("TRK-777")
        );
    }
}

package com.stockflow.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.common.exception.UnauthorizedException;
import com.stockflow.identity.domain.Role;
import com.stockflow.identity.domain.User;
import com.stockflow.inventory.service.InventoryService;
import com.stockflow.order.domain.Order;
import com.stockflow.order.domain.OrderStatus;
import com.stockflow.order.repository.OrderRepository;
import com.stockflow.outbox.service.OutboxService;
import com.stockflow.payment.domain.Payment;
import com.stockflow.payment.domain.PaymentStatus;
import com.stockflow.payment.domain.PaymentWebhookEvent;
import com.stockflow.payment.dto.PaymentResponse;
import com.stockflow.payment.provider.PaymentProvider;
import com.stockflow.payment.provider.PaymentResult;
import com.stockflow.payment.repository.PaymentRepository;
import com.stockflow.payment.repository.PaymentWebhookEventRepository;
import com.stockflow.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentWebhookEventRepository webhookEventRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private OutboxService outboxService;

    @Mock
    private PaymentProvider paymentProvider;

    private ObjectMapper objectMapper;
    private PaymentService paymentService;

    private User customer;
    private Order order;
    private Payment payment;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        paymentService = new PaymentService(paymentRepository, webhookEventRepository, orderRepository,
                inventoryService, outboxService, paymentProvider, objectMapper);

        customer = new User(UUID.randomUUID(), "buyer@example.com", "hash", "Buyer", Role.CUSTOMER);
        order = new Order(UUID.randomUUID(), "ORD-123", customer, BigDecimal.valueOf(100), "USD", "100 Street");
        payment = new Payment(UUID.randomUUID(), order, "MOCK", BigDecimal.valueOf(100), "USD");
    }

    @Test
    @DisplayName("Payment Success: Should transition order to CONFIRMED, consume stock reservation, and write outbox event")
    void shouldProcessPaymentSuccess() {
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        PaymentResult successResult = PaymentResult.success("REF-999");
        PaymentResponse response = paymentService.processPaymentOutcome(payment.getId(), successResult);

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(inventoryService).confirmAndConsumeReservation(order.getId());
        verify(outboxService).publishEvent(eq("PAYMENT_SUCCEEDED"), eq("Payment"), eq(payment.getId().toString()), any());
    }

    @Test
    @DisplayName("Payment Failure: Should transition order to CANCELLED and release reserved stock")
    void shouldProcessPaymentFailure() {
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        PaymentResult failResult = PaymentResult.failed("REF-FAIL", "Insufficient funds");
        PaymentResponse response = paymentService.processPaymentOutcome(payment.getId(), failResult);

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryService).releaseReservation(eq(order.getId()), contains("PAYMENT_FAILED"));
        verify(outboxService).publishEvent(eq("PAYMENT_FAILED"), eq("Payment"), eq(payment.getId().toString()), any());
    }

    @Test
    @DisplayName("Payment Uncertain: Should keep order in PENDING_PAYMENT and NOT release stock")
    void shouldHandleUncertainOutcomeWithoutCancellingOrder() {
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        PaymentResult uncertainResult = PaymentResult.uncertain(null, "Gateway timeout");
        PaymentResponse response = paymentService.processPaymentOutcome(payment.getId(), uncertainResult);

        assertThat(response.status()).isEqualTo(PaymentStatus.UNCERTAIN);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT); // Unchanged!
        verifyNoInteractions(inventoryService); // Stock is NOT released!
    }

    @Test
    @DisplayName("Webhook Deduplication: Duplicate event IDs should return true without re-processing")
    void shouldDeduplicateWebhookEvents() {
        String payload = "{\"eventId\":\"evt-dup-1\",\"eventType\":\"payment.succeeded\"}";
        when(paymentProvider.verifyWebhookSignature(payload, "sig-123")).thenReturn(true);
        when(webhookEventRepository.existsByEventId("evt-dup-1")).thenReturn(true);

        boolean handled = paymentService.processWebhook(payload, "sig-123");

        assertThat(handled).isTrue();
        verify(webhookEventRepository, never()).save(any(PaymentWebhookEvent.class));
    }

    @Test
    @DisplayName("Webhook Signature Verification: Should reject invalid signatures")
    void shouldRejectInvalidWebhookSignature() {
        String payload = "{\"eventId\":\"evt-bad\"}";
        when(paymentProvider.verifyWebhookSignature(payload, "bad-sig")).thenReturn(false);

        assertThatThrownBy(() -> paymentService.processWebhook(payload, "bad-sig"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid webhook signature");
    }
}

package com.stockflow.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.common.exception.BusinessRuleViolationException;
import com.stockflow.common.exception.ResourceNotFoundException;
import com.stockflow.common.exception.UnauthorizedException;
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
import com.stockflow.payment.dto.WebhookPayload;
import com.stockflow.payment.provider.PaymentProvider;
import com.stockflow.payment.provider.PaymentRequest;
import com.stockflow.payment.provider.PaymentResult;
import com.stockflow.payment.repository.PaymentRepository;
import com.stockflow.payment.repository.PaymentWebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final OutboxService outboxService;
    private final PaymentProvider paymentProvider;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentWebhookEventRepository webhookEventRepository,
                          OrderRepository orderRepository,
                          InventoryService inventoryService,
                          OutboxService outboxService,
                          PaymentProvider paymentProvider,
                          ObjectMapper objectMapper) {
        this(paymentRepository, webhookEventRepository, orderRepository, inventoryService, outboxService, paymentProvider, objectMapper, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PaymentService(PaymentRepository paymentRepository,
                          PaymentWebhookEventRepository webhookEventRepository,
                          OrderRepository orderRepository,
                          InventoryService inventoryService,
                          OutboxService outboxService,
                          PaymentProvider paymentProvider,
                          ObjectMapper objectMapper,
                          PlatformTransactionManager transactionManager) {
        this.paymentRepository = paymentRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
        this.outboxService = outboxService;
        this.paymentProvider = paymentProvider;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionManager != null ? new TransactionTemplate(transactionManager) : null;
    }

    /**
     * ========================================================================
     * [SOFTWARE ARCHITECTURE LESSON: DECOUPLED PAYMENT BOUNDARY ANTI-PATTERN]
     * ========================================================================
     * Naive junior implementation:
     *   @Transactional
     *   public void payOrder(UUID orderId) {
     *       order.setStatus(PAID);
     *       gatewayClient.chargeCard(...); // BLOCKS DB THREAD FOR 2-10 SECONDS!
     *       paymentRepo.save(...);
     *   }
     *
     * Why the naive approach causes catastrophic production outages:
     *   1. Database Connection Starvation: HikariCP typically has 10-20 connections.
     *      Holding a connection while waiting for third-party network I/O starves
     *      all other database queries, taking down the entire API.
     *   2. Long-Lived Row Locks: Rows remain locked during the external call,
     *      creating massive lock queues and cascading timeouts across unrelated requests.
     *   3. The Two Generals Problem: If the gateway charges the card but network
     *      times out during the DB commit, the card is charged but the DB rolls back!
     *
     * Our Solution (Decoupled 3-Step Non-Blocking Execution):
     *   Step 1: Short DB Transaction (<5ms) - initialize record with state PENDING.
     *   Step 2: External HTTP I/O (NO open DB transaction or connection held!).
     *   Step 3: Short DB Transaction (<5ms) - record outcome as SUCCEEDED, FAILED,
     *           or UNCERTAIN (reconciled by PaymentReconciliationWorker).
     */
    public PaymentResponse initiatePayment(UUID orderId, User customer) {
        // Step 1: Initialize payment record in short transaction
        Payment payment = transactionTemplate != null
                ? transactionTemplate.execute(status -> initializePaymentRecord(orderId, customer))
                : initializePaymentRecord(orderId, customer);

        // Step 2: Call gateway OUTSIDE open database transaction
        PaymentRequest paymentRequest = new PaymentRequest(
                payment.getOrder().getId(),
                payment.getOrder().getOrderNumber(),
                payment.getAmount(),
                payment.getCurrency(),
                customer.getEmail(),
                payment.getId().toString()
        );

        PaymentResult res;
        try {
            res = paymentProvider.initiatePayment(paymentRequest);
        } catch (Exception e) {
            log.error("Network timeout or gateway error during payment initiation for order {}", orderId, e);
            res = PaymentResult.uncertain(null, "Gateway communication failure: " + e.getMessage());
        }
        final PaymentResult result = res;

        // Step 3: Persist and reconcile outcome in short transaction
        return transactionTemplate != null
                ? transactionTemplate.execute(status -> processPaymentOutcome(payment.getId(), result))
                : processPaymentOutcome(payment.getId(), result);
    }

    @Transactional
    public Payment initializePaymentRecord(UUID orderId, User customer) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new UnauthorizedException("You are not authorized to pay for this order");
        }

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessRuleViolationException("Cannot pay for order in status " + order.getStatus());
        }

        Payment payment = new Payment(
                UUID.randomUUID(),
                order,
                paymentProvider.getProviderName(),
                order.getTotalAmount(),
                order.getCurrency()
        );
        return paymentRepository.save(payment);
    }

    @Transactional
    public PaymentResponse processPaymentOutcome(UUID paymentId, PaymentResult result) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
        Order order = payment.getOrder();

        if (result.status() == PaymentStatus.SUCCEEDED) {
            payment.markSucceeded(result.providerReference());
            order.transitionTo(OrderStatus.CONFIRMED);
            orderRepository.save(order);

            // Permanently consume reserved inventory
            inventoryService.confirmAndConsumeReservation(order.getId());

            outboxService.publishEvent(
                    "PAYMENT_SUCCEEDED",
                    "Payment",
                    payment.getId().toString(),
                    Map.of("orderId", order.getId(), "paymentId", payment.getId(), "amount", payment.getAmount())
            );
            log.info("Payment {} SUCCEEDED for order {}", payment.getId(), order.getOrderNumber());

        } else if (result.status() == PaymentStatus.FAILED) {
            payment.markFailed(result.errorMessage());
            order.transitionTo(OrderStatus.CANCELLED);
            orderRepository.save(order);

            // Release reserved inventory back to available pool
            inventoryService.releaseReservation(order.getId(), "PAYMENT_FAILED: " + result.errorMessage());

            outboxService.publishEvent(
                    "PAYMENT_FAILED",
                    "Payment",
                    payment.getId().toString(),
                    Map.of("orderId", order.getId(), "paymentId", payment.getId(), "error", result.errorMessage())
            );
            log.info("Payment {} FAILED for order {}", payment.getId(), order.getOrderNumber());

        } else {
            // UNCERTAIN status: keep order in PENDING_PAYMENT, do not release inventory, await reconciliation
            payment.markUncertain(result.errorMessage());
            log.warn("Payment {} is UNCERTAIN for order {}. Awaiting automated reconciliation worker.",
                    payment.getId(), order.getOrderNumber());
        }

        payment = paymentRepository.save(payment);
        return PaymentResponse.from(payment);
    }

    /**
     * Idempotent webhook processing with HMAC-SHA256 signature validation and deduplication.
     */
    @Transactional
    public boolean processWebhook(String rawPayload, String signatureHeader) {
        if (!paymentProvider.verifyWebhookSignature(rawPayload, signatureHeader)) {
            log.warn("Rejected webhook: Invalid HMAC signature");
            throw new UnauthorizedException("Invalid webhook signature");
        }

        WebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawPayload, WebhookPayload.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse webhook JSON payload", e);
            throw new BusinessRuleViolationException("Malformed webhook payload");
        }

        // Deduplication via unique event_id constraint
        if (webhookEventRepository.existsByEventId(payload.eventId())) {
            log.info("Duplicate webhook event detected: {}. Safely ignoring.", payload.eventId());
            return true;
        }

        PaymentWebhookEvent event = new PaymentWebhookEvent(
                UUID.randomUUID(),
                payload.eventId(),
                paymentProvider.getProviderName(),
                payload.eventType(),
                rawPayload
        );
        webhookEventRepository.save(event);

        if ("payment.succeeded".equalsIgnoreCase(payload.eventType())) {
            Order order = orderRepository.findById(payload.orderId())
                    .orElse(null);

            if (order != null && order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                order.transitionTo(OrderStatus.CONFIRMED);
                orderRepository.save(order);
                inventoryService.confirmAndConsumeReservation(order.getId());

                paymentRepository.findByProviderReference(payload.providerReference()).ifPresent(p -> {
                    p.markSucceeded(payload.providerReference());
                    paymentRepository.save(p);
                });

                outboxService.publishEvent(
                        "PAYMENT_SUCCEEDED_WEBHOOK",
                        "Payment",
                        payload.providerReference(),
                        Map.of("orderId", order.getId(), "eventId", payload.eventId())
                );
                log.info("Webhook confirmed payment for order {}", order.getOrderNumber());
            }
        }
        return true;
    }

    /**
     * Reconciles uncertain payments by querying the payment gateway status API.
     */
    @Transactional
    public void reconcileUncertainPayments() {
        List<Payment> uncertainPayments = paymentRepository.findByStatus(PaymentStatus.UNCERTAIN);
        if (uncertainPayments.isEmpty()) {
            return;
        }

        log.info("Reconciling {} uncertain payments", uncertainPayments.size());
        for (Payment payment : uncertainPayments) {
            if (payment.getProviderReference() != null) {
                try {
                    PaymentResult result = paymentProvider.queryPaymentStatus(payment.getProviderReference());
                    if (result.status() == PaymentStatus.SUCCEEDED) {
                        payment.markSucceeded(result.providerReference());
                        Order order = payment.getOrder();
                        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                            order.transitionTo(OrderStatus.CONFIRMED);
                            orderRepository.save(order);
                            inventoryService.confirmAndConsumeReservation(order.getId());
                        }
                        paymentRepository.save(payment);
                        log.info("Reconciled payment {} to SUCCEEDED", payment.getId());
                    } else if (result.status() == PaymentStatus.FAILED) {
                        payment.markFailed(result.errorMessage());
                        Order order = payment.getOrder();
                        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                            order.transitionTo(OrderStatus.CANCELLED);
                            orderRepository.save(order);
                            inventoryService.releaseReservation(order.getId(), "RECONCILED_FAILED");
                        }
                        paymentRepository.save(payment);
                        log.info("Reconciled payment {} to FAILED", payment.getId());
                    }
                } catch (Exception e) {
                    log.error("Failed to query gateway during payment reconciliation for payment {}", payment.getId(), e);
                }
            }
        }
    }
}

package com.stockflow.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.cart.domain.Cart;
import com.stockflow.cart.domain.CartItem;
import com.stockflow.cart.repository.CartItemRepository;
import com.stockflow.cart.repository.CartRepository;
import com.stockflow.catalog.domain.Product;
import com.stockflow.catalog.repository.ProductRepository;
import com.stockflow.common.domain.Money;
import com.stockflow.common.exception.BusinessRuleViolationException;
import com.stockflow.common.exception.ResourceNotFoundException;
import com.stockflow.common.exception.UnauthorizedException;
import com.stockflow.identity.domain.Role;
import com.stockflow.identity.domain.User;
import com.stockflow.inventory.service.InventoryService;
import com.stockflow.order.domain.Order;
import com.stockflow.order.domain.OrderItem;
import com.stockflow.order.domain.OrderStatus;
import com.stockflow.order.dto.CancelOrderRequest;
import com.stockflow.order.dto.CheckoutRequest;
import com.stockflow.order.dto.OrderResponse;
import com.stockflow.order.repository.OrderRepository;
import com.stockflow.outbox.service.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final OutboxService outboxService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final int reservationTtlMinutes;

    public OrderService(OrderRepository orderRepository,
                        CartRepository cartRepository,
                        CartItemRepository cartItemRepository,
                        ProductRepository productRepository,
                        InventoryService inventoryService,
                        OutboxService outboxService,
                        IdempotencyService idempotencyService,
                        ObjectMapper objectMapper,
                        @Value("${stockflow.reservation.ttl-minutes:15}") int reservationTtlMinutes) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.outboxService = outboxService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.reservationTtlMinutes = reservationTtlMinutes;
    }

    /**
     * Executes idempotent checkout orchestration.
     */
    public OrderResponse checkout(CheckoutRequest request, String idempotencyKey, User customer) {
        // Check idempotency cache first
        Optional<String> cachedResponse = idempotencyService.acquireOrVerify(idempotencyKey, customer, request);
        if (cachedResponse.isPresent()) {
            try {
                return objectMapper.readValue(cachedResponse.get(), OrderResponse.class);
            } catch (Exception e) {
                log.error("Failed to deserialize cached order response for key: {}", idempotencyKey, e);
            }
        }

        try {
            OrderResponse response = executeCheckoutTransaction(request, customer);
            idempotencyService.recordSuccess(idempotencyKey, customer, 201, response);
            return response;
        } catch (RuntimeException e) {
            idempotencyService.releaseKeyOnFailure(idempotencyKey, customer);
            throw e;
        }
    }

    @Transactional
    public OrderResponse executeCheckoutTransaction(CheckoutRequest request, User customer) {
        Cart cart = cartRepository.findByUserId(customer.getId())
                .orElseThrow(() -> new BusinessRuleViolationException("Cart is empty"));

        List<CartItem> cartItems = cart.getItems();
        if (cartItems.isEmpty()) {
            throw new BusinessRuleViolationException("Cart is empty");
        }

        // Validate products and compute totals using trusted catalog data
        Money total = Money.zero("USD");
        Map<String, Integer> skuQuantities = new HashMap<>();

        for (CartItem item : cartItems) {
            Product product = productRepository.findById(item.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + item.getProduct().getId()));

            if (!product.isActive()) {
                throw new BusinessRuleViolationException("Product '" + product.getTitle() + "' is no longer available.");
            }

            Money itemPrice = Money.of(product.getPrice(), product.getCurrency()).multiply(item.getQuantity());
            total = total.add(itemPrice);
            skuQuantities.put(item.getSku(), item.getQuantity());
        }

        String orderNumber = "ORD-" + System.currentTimeMillis() + "-" + (1000 + new Random().nextInt(9000));
        Order order = new Order(
                UUID.randomUUID(),
                orderNumber,
                customer,
                total.amount(),
                total.currency(),
                request.shippingAddress().trim()
        );

        // Capture immutable snapshots of ordered items
        for (CartItem item : cartItems) {
            Product product = item.getProduct();
            OrderItem orderItem = new OrderItem(
                    UUID.randomUUID(),
                    order,
                    product.getSeller(),
                    product.getId(),
                    item.getSku(),
                    product.getTitle(), // Snapshot title
                    product.getPrice(), // Snapshot price
                    item.getQuantity(),
                    BigDecimal.ZERO,
                    product.getCurrency()
            );
            order.addItem(orderItem);
        }

        order = orderRepository.save(order);

        // Atomically reserve inventory with deterministic lock ordering
        Instant expiresAt = Instant.now().plus(reservationTtlMinutes, ChronoUnit.MINUTES);
        inventoryService.reserveStockForOrder(order.getId(), skuQuantities, expiresAt);

        // Record outbox event for asynchronous notification / order saga
        outboxService.publishEvent(
                "ORDER_CREATED",
                "Order",
                order.getId().toString(),
                Map.of(
                        "orderId", order.getId(),
                        "orderNumber", order.getOrderNumber(),
                        "totalAmount", order.getTotalAmount(),
                        "currency", order.getCurrency(),
                        "expiresAt", expiresAt
                )
        );

        // Clear cart
        cartItemRepository.deleteByCartId(cart.getId());
        cart.getItems().clear();
        cart.touch();
        cartRepository.save(cart);

        log.info("Created order {} with total {} {} for customer {}",
                order.getOrderNumber(), order.getTotalAmount(), order.getCurrency(), customer.getEmail());

        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancelOrder(UUID orderId, CancelOrderRequest request, User user, boolean isAdmin) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        // Ownership enforcement
        if (!isAdmin && !order.getCustomer().getId().equals(user.getId())) {
            throw new UnauthorizedException("You are not authorized to cancel this order");
        }

        order.transitionTo(OrderStatus.CANCELLED);
        orderRepository.save(order);

        // Release inventory reservation
        inventoryService.releaseReservation(order.getId(), "CUSTOMER_CANCELLED: " + request.reason());

        outboxService.publishEvent(
                "ORDER_CANCELLED",
                "Order",
                order.getId().toString(),
                Map.of(
                        "orderId", order.getId(),
                        "reason", request.reason(),
                        "cancelledBy", user.getEmail()
                )
        );

        log.info("Order {} cancelled by user {}. Reason: {}", order.getOrderNumber(), user.getEmail(), request.reason());
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId, User user) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean isOwner = order.getCustomer().getId().equals(user.getId());

        if (!isAdmin && !isOwner) {
            throw new UnauthorizedException("You are not authorized to view this order");
        }

        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getCustomerOrders(User user, Pageable pageable) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(OrderResponse::from);
    }
}

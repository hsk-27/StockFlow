package com.stockflow.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.cart.domain.Cart;
import com.stockflow.cart.domain.CartItem;
import com.stockflow.cart.repository.CartItemRepository;
import com.stockflow.cart.repository.CartRepository;
import com.stockflow.catalog.domain.Product;
import com.stockflow.catalog.repository.ProductRepository;
import com.stockflow.fulfilment.dto.CreateShipmentRequest;
import com.stockflow.fulfilment.dto.ShipmentResponse;
import com.stockflow.fulfilment.repository.ShipmentRepository;
import com.stockflow.fulfilment.service.FulfilmentService;
import com.stockflow.identity.domain.Role;
import com.stockflow.identity.domain.User;
import com.stockflow.inventory.domain.Inventory;
import com.stockflow.inventory.domain.StockReservation;
import com.stockflow.inventory.repository.InventoryMovementRepository;
import com.stockflow.inventory.repository.InventoryRepository;
import com.stockflow.inventory.repository.StockReservationRepository;
import com.stockflow.inventory.service.InventoryService;
import com.stockflow.order.domain.Order;
import com.stockflow.order.domain.OrderStatus;
import com.stockflow.order.dto.CheckoutRequest;
import com.stockflow.order.dto.OrderResponse;
import com.stockflow.order.repository.OrderRepository;
import com.stockflow.order.service.IdempotencyService;
import com.stockflow.order.service.OrderService;
import com.stockflow.outbox.repository.OutboxEventRepository;
import com.stockflow.outbox.service.OutboxService;
import com.stockflow.payment.domain.Payment;
import com.stockflow.payment.domain.PaymentStatus;
import com.stockflow.payment.dto.PaymentResponse;
import com.stockflow.payment.provider.MockPaymentProvider;
import com.stockflow.payment.repository.PaymentRepository;
import com.stockflow.payment.repository.PaymentWebhookEventRepository;
import com.stockflow.payment.service.PaymentService;
import com.stockflow.returns.domain.RestockDecision;
import com.stockflow.returns.dto.*;
import com.stockflow.returns.repository.RefundRepository;
import com.stockflow.returns.repository.ReturnRequestRepository;
import com.stockflow.returns.service.ReturnsAndRefundsService;
import com.stockflow.seller.domain.Seller;
import com.stockflow.seller.domain.SellerStatus;
import com.stockflow.seller.repository.SellerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EndToEndCommerceWorkflowTest {

    // Repositories
    @Mock private ProductRepository productRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private InventoryMovementRepository movementRepository;
    @Mock private StockReservationRepository reservationRepository;
    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentWebhookEventRepository webhookEventRepository;
    @Mock private SellerRepository sellerRepository;
    @Mock private ShipmentRepository shipmentRepository;
    @Mock private ReturnRequestRepository returnRepository;
    @Mock private RefundRepository refundRepository;

    private ObjectMapper objectMapper;
    private MockPaymentProvider mockPaymentProvider;

    // Services
    private OutboxService outboxService;
    private InventoryService inventoryService;
    private OrderService orderService;
    private PaymentService paymentService;
    private FulfilmentService fulfilmentService;
    private ReturnsAndRefundsService returnsAndRefundsService;

    // Entities
    private User customer;
    private User sellerUser;
    private Seller seller;
    private Product product;
    private Inventory inventory;
    private final String sku = "SKU-E2E-CHAIR";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockPaymentProvider = new MockPaymentProvider("whsec_test_secret_key");
        outboxService = new OutboxService(outboxEventRepository, objectMapper);
        inventoryService = new InventoryService(inventoryRepository, movementRepository, reservationRepository);

        orderService = new OrderService(orderRepository, cartRepository, cartItemRepository,
                productRepository, inventoryService, outboxService, null, objectMapper, 15);

        paymentService = new PaymentService(paymentRepository, webhookEventRepository, orderRepository,
                inventoryService, outboxService, mockPaymentProvider, objectMapper);

        fulfilmentService = new FulfilmentService(shipmentRepository, orderRepository, sellerRepository, outboxService);

        returnsAndRefundsService = new ReturnsAndRefundsService(returnRepository, refundRepository,
                orderRepository, paymentRepository, sellerRepository, inventoryService, mockPaymentProvider, outboxService);

        // Seed actors
        customer = new User(UUID.randomUUID(), "customer@e2e.com", "hash", "John Customer", Role.CUSTOMER);
        sellerUser = new User(UUID.randomUUID(), "seller@e2e.com", "hash", "Jane Seller", Role.SELLER);
        seller = new Seller(UUID.randomUUID(), sellerUser, "Nordic Furniture", "seller@e2e.com", SellerStatus.ACTIVE);

        product = new Product(UUID.randomUUID(), seller, null, "Ergonomic Office Chair", "Mesh chair", BigDecimal.valueOf(250), "USD");
        inventory = new Inventory(UUID.randomUUID(), sku, product, seller, 5); // 5 on hand initially
    }

    @Test
    @DisplayName("End-to-End Workflow: Browse -> Cart -> Checkout -> Payment -> Fulfilment -> Return -> Restock -> Refund")
    void completeEndToEndCommerceLifecycle() {
        // 1. Setup Cart with 2 chairs
        Cart cart = new Cart(UUID.randomUUID(), customer);
        CartItem cartItem = new CartItem(UUID.randomUUID(), cart, product, sku, 2);
        cart.getItems().add(cartItem);

        when(cartRepository.findByUserId(customer.getId())).thenReturn(Optional.of(cart));
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        // Inventory locking for reservation
        when(inventoryRepository.findBySkuInOrderBySkuAscWithLock(List.of(sku))).thenReturn(List.of(inventory));
        when(reservationRepository.save(any(StockReservation.class))).thenAnswer(i -> i.getArgument(0));

        // 2. Checkout Execution
        CheckoutRequest checkoutRequest = new CheckoutRequest("123 Tech Boulevard, CA");
        OrderResponse orderResponse = orderService.executeCheckoutTransaction(checkoutRequest, customer);

        assertThat(orderResponse).isNotNull();
        assertThat(orderResponse.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(orderResponse.totalAmount()).isEqualByComparingTo("500.00"); // 2 * $250
        assertThat(inventory.getOnHand()).isEqualTo(5);
        assertThat(inventory.getReserved()).isEqualTo(2);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(3); // Invariant: 5 - 2 = 3

        Order order = new Order(orderResponse.id(), orderResponse.orderNumber(), customer,
                orderResponse.totalAmount(), orderResponse.currency(), orderResponse.shippingAddress());

        // Attach order items to order
        order.getItems().add(new com.stockflow.order.domain.OrderItem(
                UUID.randomUUID(), order, seller, product.getId(), sku, product.getTitle(),
                product.getPrice(), 2, BigDecimal.ZERO, "USD"
        ));

        // 3. Payment Initiation & Webhook Confirmation
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        Payment payment = new Payment(UUID.randomUUID(), order, "MOCK_GATEWAY", order.getTotalAmount(), order.getCurrency());
        when(paymentRepository.findById(any(UUID.class))).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        StockReservation reservation = new StockReservation(UUID.randomUUID(), order.getId(), sku, 2, Instant.now().plusSeconds(900));
        when(reservationRepository.findByOrderIdAndStatus(order.getId(), com.stockflow.inventory.domain.ReservationStatus.PENDING))
                .thenReturn(List.of(reservation));

        PaymentResponse paymentResponse = paymentService.initiatePayment(order.getId(), customer);
        assertThat(paymentResponse.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        // Verify reservation consumed: on_hand reduced to 3, reserved reduced to 0
        assertThat(inventory.getOnHand()).isEqualTo(3);
        assertThat(inventory.getReserved()).isEqualTo(0);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(3);

        // 4. Seller Fulfilment & Delivery
        when(sellerRepository.findByUserId(sellerUser.getId())).thenReturn(Optional.of(seller));
        when(shipmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CreateShipmentRequest shipmentRequest = new CreateShipmentRequest(order.getId(), "UPS Express", "UPS-TRACK-999");
        ShipmentResponse shipmentResponse = fulfilmentService.createShipment(shipmentRequest, sellerUser);

        assertThat(shipmentResponse.status()).isEqualTo(com.stockflow.fulfilment.domain.ShipmentStatus.SHIPPED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);

        com.stockflow.fulfilment.domain.Shipment shipmentEntity = new com.stockflow.fulfilment.domain.Shipment(
                shipmentResponse.id(), order, seller, "UPS Express", "UPS-TRACK-999"
        );
        when(shipmentRepository.findById(shipmentEntity.getId())).thenReturn(Optional.of(shipmentEntity));

        ShipmentResponse deliveredResponse = fulfilmentService.confirmDelivery(shipmentEntity.getId(), sellerUser);
        assertThat(deliveredResponse.status()).isEqualTo(com.stockflow.fulfilment.domain.ShipmentStatus.DELIVERED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);

        // 5. Customer Return & Seller Restock
        com.stockflow.returns.domain.ReturnRequest returnReq = new com.stockflow.returns.domain.ReturnRequest(
                UUID.randomUUID(), order, customer, seller, "Too large for office space"
        );
        returnReq.approve();
        when(returnRepository.findById(returnReq.getId())).thenReturn(Optional.of(returnReq));

        when(inventoryRepository.findBySkuWithLock(sku)).thenReturn(Optional.of(inventory));

        ReceiveReturnRequest receiveRequest = new ReceiveReturnRequest(RestockDecision.RESTOCK);
        returnsAndRefundsService.receiveReturn(returnReq.getId(), receiveRequest, sellerUser);

        // Invariant: returned items restocked back to inventory (3 + 2 = 5 available units!)
        assertThat(inventory.getOnHand()).isEqualTo(5);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(5);

        // 6. Refund Issuance
        when(paymentRepository.findByOrderId(order.getId())).thenReturn(List.of(payment));
        when(refundRepository.calculateTotalRefundedAmount(order.getId())).thenReturn(BigDecimal.ZERO);
        when(refundRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        IssueRefundRequest refundRequest = new IssueRefundRequest(order.getId(), BigDecimal.valueOf(500), "Returned in pristine condition");
        RefundResponse refundResponse = returnsAndRefundsService.issueRefund(refundRequest, sellerUser, false);

        assertThat(refundResponse.amount()).isEqualByComparingTo("500.00");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }
}

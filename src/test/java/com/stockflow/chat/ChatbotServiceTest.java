package com.stockflow.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.audit.domain.AuditLog;
import com.stockflow.audit.repository.AuditLogRepository;
import com.stockflow.cart.dto.CartItemResponse;
import com.stockflow.cart.dto.CartResponse;
import com.stockflow.cart.service.CartService;
import com.stockflow.catalog.domain.Category;
import com.stockflow.catalog.domain.Product;
import com.stockflow.catalog.repository.CategoryRepository;
import com.stockflow.catalog.repository.ProductRepository;
import com.stockflow.chat.domain.ChatConversation;
import com.stockflow.chat.domain.ChatMessage;
import com.stockflow.chat.domain.ChatSender;
import com.stockflow.chat.dto.ChatMessageRequest;
import com.stockflow.chat.dto.ChatMessageResponse;
import com.stockflow.chat.repository.ChatConversationRepository;
import com.stockflow.chat.repository.ChatMessageRepository;
import com.stockflow.chat.service.ChatbotService;
import com.stockflow.fulfilment.domain.Shipment;
import com.stockflow.fulfilment.domain.ShipmentStatus;
import com.stockflow.fulfilment.repository.ShipmentRepository;
import com.stockflow.identity.domain.Role;
import com.stockflow.identity.domain.User;
import com.stockflow.identity.repository.UserRepository;
import com.stockflow.inventory.domain.Inventory;
import com.stockflow.inventory.repository.InventoryRepository;
import com.stockflow.order.domain.Order;
import com.stockflow.order.domain.OrderItem;
import com.stockflow.order.domain.OrderStatus;
import com.stockflow.order.repository.OrderRepository;
import com.stockflow.reporting.dto.OperationalMetricsResponse;
import com.stockflow.reporting.service.ReportingService;
import com.stockflow.returns.domain.ReturnRequest;
import com.stockflow.returns.domain.ReturnStatus;
import com.stockflow.returns.repository.ReturnRequestRepository;
import com.stockflow.seller.domain.Seller;
import com.stockflow.seller.domain.SellerStatus;
import com.stockflow.seller.repository.SellerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatbotServiceTest {

    @Mock private ChatConversationRepository conversationRepository;
    @Mock private ChatMessageRepository messageRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private CartService cartService;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private SellerRepository sellerRepository;
    @Mock private ShipmentRepository shipmentRepository;
    @Mock private ReturnRequestRepository returnRequestRepository;
    @Mock private ReportingService reportingService;
    @Mock private AuditLogRepository auditLogRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ChatbotService chatbotService;

    private User customerUser;
    private User sellerUser;
    private User adminUser;
    private Seller seller;

    @BeforeEach
    void setUp() {
        chatbotService = new ChatbotService(
                conversationRepository,
                messageRepository,
                userRepository,
                productRepository,
                categoryRepository,
                orderRepository,
                cartService,
                inventoryRepository,
                sellerRepository,
                shipmentRepository,
                returnRequestRepository,
                reportingService,
                auditLogRepository,
                objectMapper
        );

        customerUser = new User(UUID.randomUUID(), "customer@stockflow.com", "hash", "Alice Customer", Role.CUSTOMER);
        sellerUser = new User(UUID.randomUUID(), "seller@stockflow.com", "hash", "Bob Seller", Role.SELLER);
        adminUser = new User(UUID.randomUUID(), "admin@stockflow.com", "hash", "Carol Admin", Role.ADMIN);

        seller = new Seller(UUID.randomUUID(), sellerUser, "Tech Haven Store", "seller@techhaven.com", SellerStatus.ACTIVE);

        when(conversationRepository.save(any(ChatConversation.class))).thenAnswer(i -> i.getArgument(0));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("Guest: Search products returns active catalog matching items")
    void shouldReturnProductSearchForGuest() {
        Category cat = new Category(UUID.randomUUID(), "Audio", "audio", "Headphones & speakers", null);
        Product product = new Product(UUID.randomUUID(), seller, cat, "Wireless Noise-Cancelling Headphones", "Pro audio", new BigDecimal("199.99"), "USD");
        Inventory inv = new Inventory(UUID.randomUUID(), "SKU-AUDIO-1", product, seller, 20);

        when(productRepository.searchPublicCatalog(any(), any(), any())).thenReturn(new PageImpl<>(List.of(product)));
        when(inventoryRepository.findByProductId(product.getId())).thenReturn(Optional.of(inv));

        ChatMessageRequest request = new ChatMessageRequest("Show me headphones", "sess-123", null);
        ChatMessageResponse response = chatbotService.processMessage(request, null);

        assertThat(response.sender()).isEqualTo(ChatSender.ASSISTANT);
        assertThat(response.intent()).isEqualTo("CATALOG_SEARCH_RESULTS");
        assertThat(response.content()).contains("Wireless Noise-Cancelling Headphones");
        assertThat(response.content()).contains("SKU-AUDIO-1");
        assertThat(response.suggestedActions()).isNotEmpty();
    }

    @Test
    @DisplayName("Guest: Inquiring about personal orders directs to authentication")
    void shouldDirectGuestToLoginWhenCheckingOrders() {
        ChatMessageRequest request = new ChatMessageRequest("Where is my order?", "sess-123", null);
        ChatMessageResponse response = chatbotService.processMessage(request, null);

        assertThat(response.intent()).isEqualTo("AUTH_REQUIRED");
        assertThat(response.content()).contains("Authentication Required");
        assertThat(response.content()).contains("customer@stockflow.com");
    }

    @Test
    @DisplayName("Customer: Look up specific order with tracking and BOLA authorization check")
    void shouldReturnOrderDetailsForAuthorizedCustomer() {
        Order order = new Order(UUID.randomUUID(), "ORD-20261103-0001", customerUser, new BigDecimal("199.99"), "USD", "123 Main St");
        order.transitionTo(OrderStatus.CONFIRMED);
        order.addItem(new OrderItem(UUID.randomUUID(), order, seller, UUID.randomUUID(), "SKU-AUDIO-1", "Pro Headphones", new BigDecimal("199.99"), 1, BigDecimal.ZERO, "USD"));

        when(orderRepository.findByOrderNumber("ORD-20261103-0001")).thenReturn(Optional.of(order));
        when(shipmentRepository.findByOrderId(order.getId())).thenReturn(Collections.emptyList());

        ChatMessageRequest request = new ChatMessageRequest("Check status of ORD-20261103-0001", "sess-cust", null);
        ChatMessageResponse response = chatbotService.processMessage(request, customerUser);

        assertThat(response.intent()).isEqualTo("ORDER_DETAILS");
        assertThat(response.content()).contains("ORD-20261103-0001");
        assertThat(response.content()).contains("CONFIRMED");
        assertThat(response.content()).contains("Pro Headphones");
        assertThat(response.content()).contains("eligible for pre-fulfilment cancellation");
    }

    @Test
    @DisplayName("Customer BOLA Defense: Cannot inspect peer customer's order")
    void shouldPreventCrossCustomerOrderInspection() {
        User otherCustomer = new User(UUID.randomUUID(), "other@stockflow.com", "hash", "Other User", Role.CUSTOMER);
        Order peerOrder = new Order(UUID.randomUUID(), "ORD-PEER-9999", otherCustomer, new BigDecimal("89.99"), "USD", "456 Other Way");
        peerOrder.transitionTo(OrderStatus.CONFIRMED);
        peerOrder.transitionTo(OrderStatus.SHIPPED);
        peerOrder.transitionTo(OrderStatus.DELIVERED);

        when(orderRepository.findByOrderNumber("ORD-PEER-9999")).thenReturn(Optional.of(peerOrder));

        ChatMessageRequest request = new ChatMessageRequest("Where is ORD-PEER-9999?", "sess-cust", null);
        ChatMessageResponse response = chatbotService.processMessage(request, customerUser);

        assertThat(response.intent()).isEqualTo("ORDER_NOT_FOUND_BOLA");
        assertThat(response.content()).contains("was not found under your account");
    }

    @Test
    @DisplayName("Customer: Inspect active cart items and subtotal")
    void shouldInspectCustomerCart() {
        CartItemResponse item = new CartItemResponse(UUID.randomUUID(), UUID.randomUUID(), "SKU-AUDIO-1", "Pro Headphones", new BigDecimal("199.99"), "USD", 1, new BigDecimal("199.99"));
        CartResponse cart = new CartResponse(UUID.randomUUID(), customerUser.getId(), List.of(item), new BigDecimal("199.99"), "USD", 1);

        when(cartService.getCart(customerUser)).thenReturn(cart);

        ChatMessageRequest request = new ChatMessageRequest("What is in my cart?", "sess-cust", null);
        ChatMessageResponse response = chatbotService.processMessage(request, customerUser);

        assertThat(response.intent()).isEqualTo("CART_SUMMARY");
        assertThat(response.content()).contains("Your Current Cart");
        assertThat(response.content()).contains("Pro Headphones");
        assertThat(response.content()).contains("199.99 USD");
    }

    @Test
    @DisplayName("Seller: Low-stock query reports inventory below threshold for seller")
    void shouldReportLowStockForSeller() {
        when(sellerRepository.findByUserId(sellerUser.getId())).thenReturn(Optional.of(seller));

        Category cat = new Category(UUID.randomUUID(), "Audio", "audio", "Sound", null);
        Product p = new Product(UUID.randomUUID(), seller, cat, "Audio Cable", "Aux", new BigDecimal("15.00"), "USD");
        Inventory lowInv = new Inventory(UUID.randomUUID(), "SKU-AUX-1", p, seller, 3);

        when(inventoryRepository.findLowStock(10)).thenReturn(List.of(lowInv));

        ChatMessageRequest request = new ChatMessageRequest("Show my low stock items", "sess-seller", null);
        ChatMessageResponse response = chatbotService.processMessage(request, sellerUser);

        assertThat(response.intent()).isEqualTo("SELLER_LOW_STOCK");
        assertThat(response.content()).contains("Low Stock Alert for Tech Haven Store");
        assertThat(response.content()).contains("SKU-AUX-1");
        assertThat(response.content()).contains("Available: **3** units");
    }

    @Test
    @DisplayName("Admin: Query platform operational metrics and gross revenue")
    void shouldReturnPlatformMetricsForAdmin() {
        OperationalMetricsResponse metrics = new OperationalMetricsResponse(
                45L,
                new BigDecimal("14500.50"),
                "USD",
                Map.of("CONFIRMED", 10L, "SHIPPED", 15L, "DELIVERED", 20L),
                2L,
                120L,
                0L
        );
        when(reportingService.getOperationalMetrics()).thenReturn(metrics);

        ChatMessageRequest request = new ChatMessageRequest("Show platform metrics and revenue", "sess-admin", null);
        ChatMessageResponse response = chatbotService.processMessage(request, adminUser);

        assertThat(response.intent()).isEqualTo("ADMIN_METRICS_REPORT");
        assertThat(response.content()).contains("14500.50 USD");
        assertThat(response.content()).contains("Total Orders:** 45");
        assertThat(response.content()).contains("`DELIVERED`: 20");
    }

    @Test
    @DisplayName("Admin: Outbox queue status query reports transactional outbox telemetry")
    void shouldReturnOutboxQueueTelemetryForAdmin() {
        ChatMessageRequest request = new ChatMessageRequest("Check outbox queue status", "sess-admin", null);
        ChatMessageResponse response = chatbotService.processMessage(request, adminUser);

        assertThat(response.intent()).isEqualTo("ADMIN_OUTBOX_HEALTH");
        assertThat(response.content()).contains("SKIP LOCKED");
        assertThat(response.content()).contains("Dual-Write Immunity");
    }
}

package com.stockflow.security;

import com.stockflow.catalog.domain.Product;
import com.stockflow.catalog.dto.UpdateProductRequest;
import com.stockflow.catalog.repository.CategoryRepository;
import com.stockflow.catalog.repository.ProductRepository;
import com.stockflow.catalog.service.CatalogService;
import com.stockflow.common.exception.UnauthorizedException;
import com.stockflow.identity.domain.Role;
import com.stockflow.identity.domain.User;
import com.stockflow.inventory.repository.InventoryMovementRepository;
import com.stockflow.inventory.repository.InventoryRepository;
import com.stockflow.order.domain.Order;
import com.stockflow.order.domain.OrderStatus;
import com.stockflow.order.repository.OrderRepository;
import com.stockflow.order.service.OrderService;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityAccessControlIntegrationTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private SellerRepository sellerRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryMovementRepository movementRepository;

    private OrderService orderService;
    private CatalogService catalogService;

    @BeforeEach
    void setUp() {
        catalogService = new CatalogService(productRepository, categoryRepository, sellerRepository, inventoryRepository, movementRepository);
        orderService = new OrderService(orderRepository, null, null, null, null, null, null, null, 15);
    }

    @Test
    @DisplayName("Horizontal Privilege Escalation: Customer A cannot view Customer B's order")
    void customerCannotViewAnotherCustomersOrder() {
        UUID customerAId = UUID.randomUUID();
        User customerA = new User(customerAId, "alice@customer.com", "hash", "Alice", Role.CUSTOMER);

        UUID customerBId = UUID.randomUUID();
        User customerB = new User(customerBId, "bob@customer.com", "hash", "Bob", Role.CUSTOMER);

        UUID orderId = UUID.randomUUID();
        Order bobsOrder = new Order(orderId, "ORD-BOB-1", customerB, BigDecimal.TEN, "USD", "Bob Address");

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(bobsOrder));

        assertThatThrownBy(() -> orderService.getOrderById(orderId, customerA))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("You are not authorized to view this order");
    }

    @Test
    @DisplayName("Horizontal Privilege Escalation: Seller A cannot modify Seller B's product")
    void sellerCannotModifyAnotherSellersProduct() {
        UUID sellerAUserId = UUID.randomUUID();
        User sellerAUser = new User(sellerAUserId, "sellerA@stockflow.com", "hash", "Seller A", Role.SELLER);

        UUID sellerBUserId = UUID.randomUUID();
        User sellerBUser = new User(sellerBUserId, "sellerB@stockflow.com", "hash", "Seller B", Role.SELLER);
        Seller sellerB = new Seller(UUID.randomUUID(), sellerBUser, "Store B", "b@store.com", SellerStatus.ACTIVE);

        UUID productBId = UUID.randomUUID();
        Product productB = new Product(productBId, sellerB, null, "Item B", "Desc", BigDecimal.valueOf(50), "USD");

        when(productRepository.findById(productBId)).thenReturn(Optional.of(productB));

        UpdateProductRequest hijackedRequest = new UpdateProductRequest("Hijacked Title", "Desc", BigDecimal.ONE, "USD", null);

        assertThatThrownBy(() -> catalogService.updateProduct(productBId, hijackedRequest, sellerAUserId, false))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("You are not authorized to modify another seller's product");
    }
}

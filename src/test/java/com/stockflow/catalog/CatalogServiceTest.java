package com.stockflow.catalog;

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
class CatalogServiceTest {

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

    private CatalogService catalogService;

    @BeforeEach
    void setUp() {
        catalogService = new CatalogService(productRepository, categoryRepository, sellerRepository, inventoryRepository, movementRepository);
    }

    @Test
    @DisplayName("Should prevent a seller from modifying another seller's product (BOLA prevention)")
    void shouldPreventCrossSellerProductModification() {
        UUID ownerUserId = UUID.randomUUID();
        User ownerUser = new User(ownerUserId, "owner@seller.com", "hash", "Owner", Role.SELLER);
        Seller ownerSeller = new Seller(UUID.randomUUID(), ownerUser, "OwnerStore", "owner@seller.com", SellerStatus.ACTIVE);

        UUID productId = UUID.randomUUID();
        Product product = new Product(productId, ownerSeller, null, "Original Title", "Desc", BigDecimal.valueOf(50), "USD");

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        UUID attackerUserId = UUID.randomUUID();
        UpdateProductRequest updateRequest = new UpdateProductRequest("Hijacked Title", "Desc", BigDecimal.valueOf(1), "USD", null);

        assertThatThrownBy(() -> catalogService.updateProduct(productId, updateRequest, attackerUserId, false))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("You are not authorized to modify another seller's product");
    }
}

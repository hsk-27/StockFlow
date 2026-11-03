package com.stockflow.catalog;

import com.stockflow.catalog.domain.Category;
import com.stockflow.catalog.domain.Product;
import com.stockflow.catalog.dto.CategoryRequest;
import com.stockflow.catalog.dto.CategoryResponse;
import com.stockflow.catalog.dto.ProductResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

    @Test
    @DisplayName("Should successfully activate a previously deactivated product")
    void shouldActivateProductSuccessfully() {
        UUID ownerUserId = UUID.randomUUID();
        User ownerUser = new User(ownerUserId, "owner@seller.com", "hash", "Owner", Role.SELLER);
        Seller ownerSeller = new Seller(UUID.randomUUID(), ownerUser, "OwnerStore", "owner@seller.com", SellerStatus.ACTIVE);

        UUID productId = UUID.randomUUID();
        Product product = new Product(productId, ownerSeller, null, "Inactive Product", "Desc", BigDecimal.valueOf(50), "USD");
        product.setActive(false);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

        ProductResponse response = catalogService.activateProduct(productId, ownerUserId, false);

        assertThat(response.active()).isTrue();
        assertThat(product.isActive()).isTrue();
        verify(productRepository).save(product);
    }

    @Test
    @DisplayName("Should prevent unauthorized seller from activating someone else's product")
    void shouldPreventUnauthorizedProductActivation() {
        UUID ownerUserId = UUID.randomUUID();
        User ownerUser = new User(ownerUserId, "owner@seller.com", "hash", "Owner", Role.SELLER);
        Seller ownerSeller = new Seller(UUID.randomUUID(), ownerUser, "OwnerStore", "owner@seller.com", SellerStatus.ACTIVE);

        UUID productId = UUID.randomUUID();
        Product product = new Product(productId, ownerSeller, null, "Title", "Desc", BigDecimal.valueOf(50), "USD");
        product.setActive(false);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        UUID rogueUserId = UUID.randomUUID();
        assertThatThrownBy(() -> catalogService.activateProduct(productId, rogueUserId, false))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("You are not authorized to modify another seller's product");
    }

    @Test
    @DisplayName("Should update and deactivate category by administrator")
    void shouldUpdateAndDeactivateCategory() {
        UUID categoryId = UUID.randomUUID();
        Category category = new Category(categoryId, "Electronics", "electronics", "Old description", null);

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

        CategoryRequest updateRequest = new CategoryRequest("Updated Electronics", "New description", null);
        CategoryResponse response = catalogService.updateCategory(categoryId, updateRequest);

        assertThat(response.name()).isEqualTo("Updated Electronics");
        assertThat(response.description()).isEqualTo("New description");

        catalogService.deactivateCategory(categoryId);
        assertThat(category.isActive()).isFalse();
        verify(categoryRepository, times(2)).save(category);
    }
}

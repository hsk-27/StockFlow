package com.stockflow.inventory;

import com.stockflow.catalog.domain.Product;
import com.stockflow.common.exception.BusinessRuleViolationException;
import com.stockflow.identity.domain.Role;
import com.stockflow.identity.domain.User;
import com.stockflow.inventory.domain.Inventory;
import com.stockflow.inventory.domain.InventoryMovement;
import com.stockflow.inventory.domain.StockReservation;
import com.stockflow.inventory.dto.StockAdjustmentRequest;
import com.stockflow.inventory.repository.InventoryMovementRepository;
import com.stockflow.inventory.repository.InventoryRepository;
import com.stockflow.inventory.repository.StockReservationRepository;
import com.stockflow.inventory.service.InventoryService;
import com.stockflow.seller.domain.Seller;
import com.stockflow.seller.domain.SellerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryMovementRepository movementRepository;

    @Mock
    private StockReservationRepository reservationRepository;

    private InventoryService inventoryService;

    private Inventory invA;
    private Inventory invB;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(inventoryRepository, movementRepository, reservationRepository);

        User user = new User(UUID.randomUUID(), "seller@stockflow.com", "hash", "Seller", Role.SELLER);
        Seller seller = new Seller(UUID.randomUUID(), user, "Apex", "seller@stockflow.com", SellerStatus.ACTIVE);
        Product productA = new Product(UUID.randomUUID(), seller, null, "Product A", "D", BigDecimal.TEN, "USD");
        Product productB = new Product(UUID.randomUUID(), seller, null, "Product B", "D", BigDecimal.valueOf(20), "USD");

        invA = new Inventory(UUID.randomUUID(), "SKU-A", productA, seller, 20);
        invB = new Inventory(UUID.randomUUID(), "SKU-B", productB, seller, 15);
    }

    @Test
    @DisplayName("Should query locked inventories in strictly sorted SKU order to eliminate deadlocks")
    void shouldQueryLockedInventoriesInSortedOrder() {
        UUID orderId = UUID.randomUUID();
        // Insert in reverse order to ensure the service sorts them
        Map<String, Integer> items = new LinkedHashMap<>();
        items.put("SKU-B", 3);
        items.put("SKU-A", 2);

        when(inventoryRepository.findBySkuInOrderBySkuAscWithLock(List.of("SKU-A", "SKU-B")))
                .thenReturn(List.of(invA, invB));
        when(reservationRepository.save(any(StockReservation.class))).thenAnswer(i -> i.getArgument(0));

        List<StockReservation> reservations = inventoryService.reserveStockForOrder(orderId, items, Instant.now().plusSeconds(900));

        assertThat(reservations).hasSize(2);
        assertThat(invA.getReserved()).isEqualTo(2);
        assertThat(invB.getReserved()).isEqualTo(3);

        verify(inventoryRepository).findBySkuInOrderBySkuAscWithLock(List.of("SKU-A", "SKU-B"));
    }

    @Test
    @DisplayName("Should record audit movement on manual stock adjustment")
    void shouldRecordMovementOnStockAdjustment() {
        when(inventoryRepository.findBySkuWithLock("SKU-A")).thenReturn(Optional.of(invA));

        StockAdjustmentRequest request = new StockAdjustmentRequest("SKU-A", 25, "RESTOCK", "PO-109");
        inventoryService.adjustStock(request, "admin@stockflow.com");

        assertThat(invA.getOnHand()).isEqualTo(25);
        ArgumentCaptor<InventoryMovement> captor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movementRepository).save(captor.capture());

        InventoryMovement saved = captor.getValue();
        assertThat(saved.getSku()).isEqualTo("SKU-A");
        assertThat(saved.getPreviousOnHand()).isEqualTo(20);
        assertThat(saved.getNewOnHand()).isEqualTo(25);
        assertThat(saved.getQuantityChange()).isEqualTo(5);
        assertThat(saved.getReasonCode()).isEqualTo("RESTOCK");
    }
}

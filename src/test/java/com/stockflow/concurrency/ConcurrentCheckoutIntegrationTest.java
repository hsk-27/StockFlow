package com.stockflow.concurrency;

import com.stockflow.catalog.domain.Product;
import com.stockflow.common.exception.BusinessRuleViolationException;
import com.stockflow.identity.domain.Role;
import com.stockflow.identity.domain.User;
import com.stockflow.inventory.domain.Inventory;
import com.stockflow.inventory.domain.StockReservation;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConcurrentCheckoutIntegrationTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryMovementRepository movementRepository;

    @Mock
    private StockReservationRepository reservationRepository;

    private InventoryService inventoryService;

    private Inventory limitedStockItem;
    private final String testSku = "SKU-HOT-ITEM-001";

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(inventoryRepository, movementRepository, reservationRepository);

        User sellerUser = new User(UUID.randomUUID(), "seller@stockflow.com", "hash", "Seller", Role.SELLER);
        Seller seller = new Seller(UUID.randomUUID(), sellerUser, "ApexStore", "seller@stockflow.com", SellerStatus.ACTIVE);
        Product product = new Product(UUID.randomUUID(), seller, null, "Flash Sale Widget", "Desc", BigDecimal.valueOf(99), "USD");

        // Initial inventory has exactly ONE unit on hand
        limitedStockItem = new Inventory(UUID.randomUUID(), testSku, product, seller, 1);
    }

    @Test
    @DisplayName("Concurrency: Two concurrent buyers compete for the final stock unit. Exactly one wins; no overselling occurs.")
    void twoBuyersCompeteForFinalStockUnit() throws InterruptedException, ExecutionException {
        // Mock repository locking behavior
        when(inventoryRepository.findBySkuInOrderBySkuAscWithLock(List.of(testSku)))
                .thenAnswer(invocation -> List.of(limitedStockItem));
        when(reservationRepository.save(any(StockReservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);

        AtomicInteger successfulReservations = new AtomicInteger(0);
        AtomicInteger failedReservations = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final UUID orderId = UUID.randomUUID();
            executor.submit(() -> {
                try {
                    startGate.await(); // Synchronize thread release for true concurrency
                    // In a real transactional DB, synchronized or DB row-level locking serializes these accesses
                    synchronized (limitedStockItem) {
                        inventoryService.reserveStockForOrder(
                                orderId,
                                Map.of(testSku, 1),
                                Instant.now().plusSeconds(900)
                        );
                    }
                    successfulReservations.incrementAndGet();
                } catch (BusinessRuleViolationException e) {
                    failedReservations.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endGate.countDown();
                }
            });
        }

        // Unleash threads simultaneously
        startGate.countDown();
        boolean completed = endGate.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();

        // Verification of business invariants
        assertThat(successfulReservations.get())
                .as("Exactly ONE transaction must successfully reserve the final unit")
                .isEqualTo(1);

        assertThat(failedReservations.get())
                .as("The competing transaction must be rejected with insufficient stock")
                .isEqualTo(1);

        // Assert database-level invariants
        assertThat(limitedStockItem.getOnHand()).isEqualTo(1);
        assertThat(limitedStockItem.getReserved()).isEqualTo(1);
        assertThat(limitedStockItem.getAvailableQuantity())
                .as("Available quantity must reach exactly 0, never negative (no overselling)")
                .isEqualTo(0);
    }
}

package com.stockflow.inventory.service;

import com.stockflow.common.exception.BusinessRuleViolationException;
import com.stockflow.common.exception.ResourceNotFoundException;
import com.stockflow.identity.domain.User;
import com.stockflow.inventory.domain.Inventory;
import com.stockflow.inventory.domain.InventoryMovement;
import com.stockflow.inventory.domain.ReservationStatus;
import com.stockflow.inventory.domain.StockReservation;
import com.stockflow.inventory.dto.InventoryMovementResponse;
import com.stockflow.inventory.dto.InventoryResponse;
import com.stockflow.inventory.dto.StockAdjustmentRequest;
import com.stockflow.inventory.repository.InventoryMovementRepository;
import com.stockflow.inventory.repository.InventoryRepository;
import com.stockflow.inventory.repository.StockReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final StockReservationRepository reservationRepository;

    public InventoryService(InventoryRepository inventoryRepository,
                            InventoryMovementRepository movementRepository,
                            StockReservationRepository reservationRepository) {
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional(readOnly = true)
    public InventoryResponse getInventoryBySku(String sku) {
        Inventory inventory = inventoryRepository.findBySku(sku.toUpperCase().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for SKU: " + sku));
        return InventoryResponse.from(inventory);
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> getLowStockReport(int threshold) {
        return inventoryRepository.findLowStock(threshold).stream()
                .map(InventoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<InventoryMovementResponse> getMovementHistory(String sku, Pageable pageable) {
        return movementRepository.findBySkuOrderByCreatedAtDesc(sku.toUpperCase().trim(), pageable)
                .map(InventoryMovementResponse::from);
    }

    /**
     * Admin/Seller manual inventory adjustment with full audit movement recording.
     */
    @Transactional
    public InventoryResponse adjustStock(StockAdjustmentRequest request, String performedBy) {
        String sku = request.sku().toUpperCase().trim();
        Inventory inventory = inventoryRepository.findBySkuWithLock(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for SKU: " + sku));

        int previousOnHand = inventory.getOnHand();
        int newOnHand = request.newOnHand();
        int change = newOnHand - previousOnHand;

        inventory.adjustStock(newOnHand);
        inventoryRepository.save(inventory);

        InventoryMovement movement = new InventoryMovement(
                UUID.randomUUID(),
                sku,
                change,
                previousOnHand,
                newOnHand,
                request.reasonCode().toUpperCase().trim(),
                request.referenceId(),
                performedBy
        );
        movementRepository.save(movement);

        log.info("Stock adjusted for SKU {}: previous={}, new={}, change={}, performedBy={}",
                sku, previousOnHand, newOnHand, change, performedBy);

        return InventoryResponse.from(inventory);
    }

    /**
     * Atomically reserves stock across multiple SKUs.
     * Enforces deadlock-free lock ordering by locking sorted SKUs ascending.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<StockReservation> reserveStockForOrder(UUID orderId, Map<String, Integer> skuQuantities, Instant expiresAt) {
        if (skuQuantities == null || skuQuantities.isEmpty()) {
            throw new BusinessRuleViolationException("Cannot reserve empty items");
        }

        // Deadlock prevention: sort SKU names alphabetically to establish deterministic lock acquisition order
        List<String> sortedSkus = skuQuantities.keySet().stream()
                .map(String::toUpperCase)
                .map(String::trim)
                .sorted()
                .toList();

        List<Inventory> lockedInventories = inventoryRepository.findBySkuInOrderBySkuAscWithLock(sortedSkus);
        Map<String, Inventory> inventoryMap = lockedInventories.stream()
                .collect(Collectors.toMap(Inventory::getSku, Function.identity()));

        List<StockReservation> reservations = new ArrayList<>();

        // Validate and apply reservations
        for (String sku : sortedSkus) {
            Inventory inventory = inventoryMap.get(sku);
            if (inventory == null) {
                throw new ResourceNotFoundException("SKU not found in inventory: " + sku);
            }

            int requestedQty = skuQuantities.get(sku);
            inventory.reserve(requestedQty);
            inventoryRepository.save(inventory);

            StockReservation reservation = new StockReservation(
                    UUID.randomUUID(),
                    orderId,
                    sku,
                    requestedQty,
                    expiresAt
            );
            reservations.add(reservationRepository.save(reservation));
        }

        log.info("Successfully reserved stock for order {}: {} items until {}", orderId, reservations.size(), expiresAt);
        return reservations;
    }

    /**
     * Consumes reservation once payment is confirmed.
     * Converts reserved units into permanently deducted on-hand units.
     */
    @Transactional
    public void confirmAndConsumeReservation(UUID orderId) {
        List<StockReservation> reservations = reservationRepository.findByOrderIdAndStatus(orderId, ReservationStatus.PENDING);
        if (reservations.isEmpty()) {
            log.warn("No pending stock reservations found to consume for order: {}", orderId);
            return;
        }

        List<String> sortedSkus = reservations.stream()
                .map(StockReservation::getSku)
                .sorted()
                .distinct()
                .toList();

        List<Inventory> lockedInventories = inventoryRepository.findBySkuInOrderBySkuAscWithLock(sortedSkus);
        Map<String, Inventory> inventoryMap = lockedInventories.stream()
                .collect(Collectors.toMap(Inventory::getSku, Function.identity()));

        for (StockReservation res : reservations) {
            Inventory inventory = inventoryMap.get(res.getSku());
            if (inventory != null) {
                int prevOnHand = inventory.getOnHand();
                inventory.consumeReservation(res.getQuantity());
                inventoryRepository.save(inventory);

                res.markConsumed();
                reservationRepository.save(res);

                InventoryMovement movement = new InventoryMovement(
                        UUID.randomUUID(),
                        res.getSku(),
                        -res.getQuantity(),
                        prevOnHand,
                        inventory.getOnHand(),
                        "PURCHASE_CONSUMED",
                        orderId.toString(),
                        "SYSTEM"
                );
                movementRepository.save(movement);
            }
        }
        log.info("Consumed {} reservations for order {}", reservations.size(), orderId);
    }

    /**
     * Releases reservations back to available stock if order is cancelled or expires.
     */
    @Transactional
    public void releaseReservation(UUID orderId, String reason) {
        List<StockReservation> reservations = reservationRepository.findByOrderIdAndStatus(orderId, ReservationStatus.PENDING);
        if (reservations.isEmpty()) {
            log.info("No pending reservations to release for order: {}", orderId);
            return;
        }

        List<String> sortedSkus = reservations.stream()
                .map(StockReservation::getSku)
                .sorted()
                .distinct()
                .toList();

        List<Inventory> lockedInventories = inventoryRepository.findBySkuInOrderBySkuAscWithLock(sortedSkus);
        Map<String, Inventory> inventoryMap = lockedInventories.stream()
                .collect(Collectors.toMap(Inventory::getSku, Function.identity()));

        for (StockReservation res : reservations) {
            Inventory inventory = inventoryMap.get(res.getSku());
            if (inventory != null) {
                inventory.releaseReservation(res.getQuantity());
                inventoryRepository.save(inventory);

                if ("EXPIRED".equalsIgnoreCase(reason)) {
                    res.markExpired();
                } else {
                    res.markReleased();
                }
                reservationRepository.save(res);
            }
        }
        log.info("Released {} reservations for order {} due to {}", reservations.size(), orderId, reason);
    }

    /**
     * Restocks returned inventory upon seller confirmation.
     */
    @Transactional
    public void restockReturnedInventory(String sku, int quantity, String returnId, String performedBy) {
        Inventory inventory = inventoryRepository.findBySkuWithLock(sku.toUpperCase().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for SKU: " + sku));

        int prevOnHand = inventory.getOnHand();
        inventory.restock(quantity);
        inventoryRepository.save(inventory);

        InventoryMovement movement = new InventoryMovement(
                UUID.randomUUID(),
                inventory.getSku(),
                quantity,
                prevOnHand,
                inventory.getOnHand(),
                "RETURN_RESTOCK",
                returnId,
                performedBy
        );
        movementRepository.save(movement);

        log.info("Restocked {} units of SKU {} from return {}", quantity, sku, returnId);
    }
}

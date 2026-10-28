package com.stockflow.inventory.domain;

import com.stockflow.catalog.domain.Product;
import com.stockflow.common.exception.BusinessRuleViolationException;
import com.stockflow.seller.domain.Seller;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String sku;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    @Column(name = "on_hand", nullable = false)
    private int onHand;

    @Column(nullable = false)
    private int reserved;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Inventory() {
    }

    public Inventory(UUID id, String sku, Product product, Seller seller, int initialOnHand) {
        if (initialOnHand < 0) {
            throw new BusinessRuleViolationException("Initial stock cannot be negative");
        }
        this.id = id != null ? id : UUID.randomUUID();
        this.sku = sku.toUpperCase().trim();
        this.product = product;
        this.seller = seller;
        this.onHand = initialOnHand;
        this.reserved = 0;
        this.version = 0;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public Product getProduct() {
        return product;
    }

    public Seller getSeller() {
        return seller;
    }

    public int getOnHand() {
        return onHand;
    }

    public int getReserved() {
        return reserved;
    }

    public int getAvailableQuantity() {
        return onHand - reserved;
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Reserves stock for an order during checkout.
     */
    public void reserve(int quantity) {
        if (quantity <= 0) {
            throw new BusinessRuleViolationException("Reservation quantity must be strictly positive");
        }
        if (getAvailableQuantity() < quantity) {
            throw new BusinessRuleViolationException(
                    "Insufficient stock for SKU " + sku + ". Available: " + getAvailableQuantity() + ", requested: " + quantity);
        }
        this.reserved += quantity;
        this.updatedAt = Instant.now();
        validateInvariants();
    }

    /**
     * Consumes reserved stock when payment is confirmed.
     */
    public void consumeReservation(int quantity) {
        if (quantity <= 0) {
            throw new BusinessRuleViolationException("Consumed quantity must be positive");
        }
        if (this.reserved < quantity) {
            throw new BusinessRuleViolationException("Cannot consume more than reserved stock. Reserved: " + reserved + ", requested: " + quantity);
        }
        if (this.onHand < quantity) {
            throw new BusinessRuleViolationException("Cannot consume more than on-hand stock. On hand: " + onHand + ", requested: " + quantity);
        }
        this.reserved -= quantity;
        this.onHand -= quantity;
        this.updatedAt = Instant.now();
        validateInvariants();
    }

    /**
     * Releases reserved stock back to available pool upon order cancellation or timeout.
     */
    public void releaseReservation(int quantity) {
        if (quantity <= 0) {
            throw new BusinessRuleViolationException("Released quantity must be positive");
        }
        if (this.reserved < quantity) {
            throw new BusinessRuleViolationException("Cannot release more than reserved stock. Reserved: " + reserved + ", requested: " + quantity);
        }
        this.reserved -= quantity;
        this.updatedAt = Instant.now();
        validateInvariants();
    }

    /**
     * Restocks returned inventory.
     */
    public void restock(int quantity) {
        if (quantity <= 0) {
            throw new BusinessRuleViolationException("Restock quantity must be positive");
        }
        this.onHand += quantity;
        this.updatedAt = Instant.now();
        validateInvariants();
    }

    /**
     * Administrative manual adjustment.
     */
    public void adjustStock(int newOnHand) {
        if (newOnHand < this.reserved) {
            throw new BusinessRuleViolationException(
                    "Cannot adjust on-hand stock below currently reserved quantity (" + this.reserved + ")");
        }
        this.onHand = newOnHand;
        this.updatedAt = Instant.now();
        validateInvariants();
    }

    private void validateInvariants() {
        if (onHand < 0) {
            throw new BusinessRuleViolationException("Invariant violation: onHand cannot be negative (" + onHand + ")");
        }
        if (reserved < 0) {
            throw new BusinessRuleViolationException("Invariant violation: reserved cannot be negative (" + reserved + ")");
        }
        if (reserved > onHand) {
            throw new BusinessRuleViolationException("Invariant violation: reserved (" + reserved + ") cannot exceed onHand (" + onHand + ")");
        }
    }
}

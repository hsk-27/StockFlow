package com.stockflow.inventory.domain;

import com.stockflow.common.exception.BusinessRuleViolationException;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_reservations")
public class StockReservation {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(nullable = false, length = 100)
    private String sku;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReservationStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockReservation() {
    }

    public StockReservation(UUID id, UUID orderId, String sku, int quantity, Instant expiresAt) {
        if (quantity <= 0) {
            throw new BusinessRuleViolationException("Reservation quantity must be positive");
        }
        this.id = id != null ? id : UUID.randomUUID();
        this.orderId = orderId;
        this.sku = sku.toUpperCase().trim();
        this.quantity = quantity;
        this.status = ReservationStatus.PENDING;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void markConsumed() {
        if (this.status != ReservationStatus.PENDING) {
            throw new BusinessRuleViolationException("Cannot consume reservation in status " + this.status);
        }
        this.status = ReservationStatus.CONSUMED;
        this.updatedAt = Instant.now();
    }

    public void markReleased() {
        if (this.status != ReservationStatus.PENDING) {
            throw new BusinessRuleViolationException("Cannot release reservation in status " + this.status);
        }
        this.status = ReservationStatus.RELEASED;
        this.updatedAt = Instant.now();
    }

    public void markExpired() {
        if (this.status != ReservationStatus.PENDING) {
            throw new BusinessRuleViolationException("Cannot expire reservation in status " + this.status);
        }
        this.status = ReservationStatus.EXPIRED;
        this.updatedAt = Instant.now();
    }
}

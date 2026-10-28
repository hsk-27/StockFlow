package com.stockflow.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_movements")
public class InventoryMovement {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String sku;

    @Column(name = "quantity_change", nullable = false)
    private int quantityChange;

    @Column(name = "previous_on_hand", nullable = false)
    private int previousOnHand;

    @Column(name = "new_on_hand", nullable = false)
    private int newOnHand;

    @Column(name = "reason_code", nullable = false, length = 50)
    private String reasonCode;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "performed_by", length = 100)
    private String performedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InventoryMovement() {
    }

    public InventoryMovement(UUID id, String sku, int quantityChange, int previousOnHand, int newOnHand,
                             String reasonCode, String referenceId, String performedBy) {
        this.id = id != null ? id : UUID.randomUUID();
        this.sku = sku;
        this.quantityChange = quantityChange;
        this.previousOnHand = previousOnHand;
        this.newOnHand = newOnHand;
        this.reasonCode = reasonCode;
        this.referenceId = referenceId;
        this.performedBy = performedBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantityChange() {
        return quantityChange;
    }

    public int getPreviousOnHand() {
        return previousOnHand;
    }

    public int getNewOnHand() {
        return newOnHand;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

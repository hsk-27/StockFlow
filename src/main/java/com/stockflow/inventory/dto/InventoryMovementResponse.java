package com.stockflow.inventory.dto;

import com.stockflow.inventory.domain.InventoryMovement;

import java.time.Instant;
import java.util.UUID;

public record InventoryMovementResponse(
        UUID id,
        String sku,
        int quantityChange,
        int previousOnHand,
        int newOnHand,
        String reasonCode,
        String referenceId,
        String performedBy,
        Instant createdAt
) {
    public static InventoryMovementResponse from(InventoryMovement movement) {
        return new InventoryMovementResponse(
                movement.getId(),
                movement.getSku(),
                movement.getQuantityChange(),
                movement.getPreviousOnHand(),
                movement.getNewOnHand(),
                movement.getReasonCode(),
                movement.getReferenceId(),
                movement.getPerformedBy(),
                movement.getCreatedAt()
        );
    }
}

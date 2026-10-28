package com.stockflow.inventory.dto;

import com.stockflow.inventory.domain.Inventory;

import java.time.Instant;
import java.util.UUID;

public record InventoryResponse(
        UUID id,
        String sku,
        UUID productId,
        UUID sellerId,
        int onHand,
        int reserved,
        int available,
        long version,
        Instant updatedAt
) {
    public static InventoryResponse from(Inventory inventory) {
        return new InventoryResponse(
                inventory.getId(),
                inventory.getSku(),
                inventory.getProduct().getId(),
                inventory.getSeller().getId(),
                inventory.getOnHand(),
                inventory.getReserved(),
                inventory.getAvailableQuantity(),
                inventory.getVersion(),
                inventory.getUpdatedAt()
        );
    }
}

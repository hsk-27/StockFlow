package com.stockflow.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StockAdjustmentRequest(
        @NotBlank(message = "SKU is required")
        String sku,

        @NotNull(message = "New on-hand quantity is required")
        @Min(value = 0, message = "On-hand stock cannot be negative")
        Integer newOnHand,

        @NotBlank(message = "Reason code is required (e.g., RESTOCK, MANUAL_AUDIT, DAMAGED)")
        String reasonCode,

        String referenceId
) {}

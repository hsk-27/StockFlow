package com.stockflow.returns.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateReturnRequest(
        @NotNull(message = "Order ID is required")
        UUID orderId,

        @NotBlank(message = "Return reason is required")
        String reason
) {}

package com.stockflow.returns.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record IssueRefundRequest(
        @NotNull(message = "Order ID is required")
        UUID orderId,

        @NotNull(message = "Refund amount is required")
        @DecimalMin(value = "0.01", message = "Refund amount must be positive")
        BigDecimal amount,

        @NotBlank(message = "Reason is required")
        String reason
) {}

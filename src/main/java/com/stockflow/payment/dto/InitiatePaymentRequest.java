package com.stockflow.payment.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InitiatePaymentRequest(
        @NotNull(message = "Order ID is required")
        UUID orderId
) {}

package com.stockflow.fulfilment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateShipmentRequest(
        @NotNull(message = "Order ID is required")
        UUID orderId,

        @NotBlank(message = "Carrier name is required")
        String carrier,

        @NotBlank(message = "Tracking number is required")
        String trackingNumber
) {}

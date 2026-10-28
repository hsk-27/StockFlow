package com.stockflow.cart.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CartItemResponse(
        UUID id,
        UUID productId,
        String sku,
        String title,
        BigDecimal unitPrice,
        String currency,
        int quantity,
        BigDecimal itemTotal
) {}

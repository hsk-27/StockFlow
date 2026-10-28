package com.stockflow.cart.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CartResponse(
        UUID id,
        UUID userId,
        List<CartItemResponse> items,
        BigDecimal totalAmount,
        String currency,
        int totalItems
) {}

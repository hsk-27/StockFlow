package com.stockflow.payment.provider;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequest(
        UUID orderId,
        String orderNumber,
        BigDecimal amount,
        String currency,
        String customerEmail,
        String idempotencyKey
) {}

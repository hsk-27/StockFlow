package com.stockflow.payment.provider;

import java.math.BigDecimal;
import java.util.UUID;

public record RefundRequest(
        UUID paymentId,
        String providerPaymentReference,
        BigDecimal amount,
        String currency,
        String reason
) {}

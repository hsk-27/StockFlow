package com.stockflow.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record WebhookPayload(
        String eventId,
        String eventType,
        String providerReference,
        UUID orderId,
        BigDecimal amount,
        String currency,
        String status
) {}

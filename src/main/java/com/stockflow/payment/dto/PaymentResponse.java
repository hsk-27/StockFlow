package com.stockflow.payment.dto;

import com.stockflow.payment.domain.Payment;
import com.stockflow.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID orderId,
        String providerName,
        String providerReference,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getProviderName(),
                payment.getProviderReference(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getErrorMessage(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}

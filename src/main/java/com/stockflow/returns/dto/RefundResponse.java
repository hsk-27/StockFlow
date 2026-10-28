package com.stockflow.returns.dto;

import com.stockflow.returns.domain.Refund;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundResponse(
        UUID id,
        UUID orderId,
        UUID paymentId,
        BigDecimal amount,
        String currency,
        String reason,
        String status,
        String providerRefundId,
        Instant createdAt
) {
    public static RefundResponse from(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getOrder().getId(),
                refund.getPayment().getId(),
                refund.getAmount(),
                refund.getCurrency(),
                refund.getReason(),
                refund.getStatus(),
                refund.getProviderRefundId(),
                refund.getCreatedAt()
        );
    }
}

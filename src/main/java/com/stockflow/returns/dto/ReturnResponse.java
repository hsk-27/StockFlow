package com.stockflow.returns.dto;

import com.stockflow.returns.domain.RestockDecision;
import com.stockflow.returns.domain.ReturnRequest;
import com.stockflow.returns.domain.ReturnStatus;

import java.time.Instant;
import java.util.UUID;

public record ReturnResponse(
        UUID id,
        UUID orderId,
        UUID customerId,
        UUID sellerId,
        String reason,
        ReturnStatus status,
        RestockDecision restockDecision,
        Instant createdAt,
        Instant updatedAt
) {
    public static ReturnResponse from(ReturnRequest req) {
        return new ReturnResponse(
                req.getId(),
                req.getOrder().getId(),
                req.getCustomer().getId(),
                req.getSeller().getId(),
                req.getReason(),
                req.getStatus(),
                req.getRestockDecision(),
                req.getCreatedAt(),
                req.getUpdatedAt()
        );
    }
}

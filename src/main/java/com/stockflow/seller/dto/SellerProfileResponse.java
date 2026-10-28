package com.stockflow.seller.dto;

import com.stockflow.seller.domain.Seller;
import com.stockflow.seller.domain.SellerStatus;

import java.time.Instant;
import java.util.UUID;

public record SellerProfileResponse(
        UUID id,
        UUID userId,
        String storeName,
        String businessEmail,
        SellerStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static SellerProfileResponse from(Seller seller) {
        return new SellerProfileResponse(
                seller.getId(),
                seller.getUser().getId(),
                seller.getStoreName(),
                seller.getBusinessEmail(),
                seller.getStatus(),
                seller.getCreatedAt(),
                seller.getUpdatedAt()
        );
    }
}

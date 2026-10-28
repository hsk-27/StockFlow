package com.stockflow.catalog.dto;

import com.stockflow.catalog.domain.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        UUID sellerId,
        String storeName,
        UUID categoryId,
        String categoryName,
        String title,
        String description,
        BigDecimal price,
        String currency,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSeller().getId(),
                product.getSeller().getStoreName(),
                product.getCategory() != null ? product.getCategory().getId() : null,
                product.getCategory() != null ? product.getCategory().getName() : null,
                product.getTitle(),
                product.getDescription(),
                product.getPrice(),
                product.getCurrency(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}

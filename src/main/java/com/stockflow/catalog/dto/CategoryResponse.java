package com.stockflow.catalog.dto;

import com.stockflow.catalog.domain.Category;

import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        String slug,
        String description,
        UUID parentId,
        boolean active,
        Instant createdAt
) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.getParent() != null ? category.getParent().getId() : null,
                category.isActive(),
                category.getCreatedAt()
        );
    }
}

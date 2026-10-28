package com.stockflow.catalog.dto;

import com.stockflow.catalog.domain.Category;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CategoryRequest(
        @NotBlank(message = "Category name is required")
        String name,

        String description,

        UUID parentId
) {}

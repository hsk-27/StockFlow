package com.stockflow.seller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UpdateSellerProfileRequest(
        @NotBlank(message = "Store name is required")
        String storeName,

        @NotBlank(message = "Business email is required")
        @Email(message = "Business email must be valid")
        String businessEmail
) {}

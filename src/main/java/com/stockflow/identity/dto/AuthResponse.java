package com.stockflow.identity.dto;

import com.stockflow.identity.domain.Role;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        UUID userId,
        String email,
        String fullName,
        Role role
) {}

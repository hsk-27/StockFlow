package com.stockflow.returns.dto;

import jakarta.validation.constraints.NotNull;

public record ReturnDecisionRequest(
        @NotNull(message = "Decision (approved: true/false) is required")
        Boolean approved,

        String comments
) {}

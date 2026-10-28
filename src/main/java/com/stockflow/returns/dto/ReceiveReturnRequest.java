package com.stockflow.returns.dto;

import com.stockflow.returns.domain.RestockDecision;
import jakarta.validation.constraints.NotNull;

public record ReceiveReturnRequest(
        @NotNull(message = "Restock decision is required (RESTOCK, QUARANTINE, SCRAP)")
        RestockDecision restockDecision
) {}

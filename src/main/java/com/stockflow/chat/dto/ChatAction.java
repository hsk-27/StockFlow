package com.stockflow.chat.dto;

public record ChatAction(
        String label,
        String actionType,
        String payload
) {}

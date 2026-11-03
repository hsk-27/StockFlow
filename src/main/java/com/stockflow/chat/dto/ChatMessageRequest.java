package com.stockflow.chat.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record ChatMessageRequest(
        @NotBlank(message = "Message text cannot be blank")
        String message,
        String sessionId,
        UUID conversationId
) {}

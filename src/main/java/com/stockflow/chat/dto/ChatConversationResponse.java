package com.stockflow.chat.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatConversationResponse(
        UUID id,
        String sessionId,
        String userRole,
        String title,
        int messageCount,
        Instant createdAt,
        Instant updatedAt,
        List<ChatMessageResponse> messages
) {}

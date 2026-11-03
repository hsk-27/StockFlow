package com.stockflow.chat.dto;

import com.stockflow.chat.domain.ChatSender;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        UUID conversationId,
        String sessionId,
        ChatSender sender,
        String content,
        String intent,
        List<ChatAction> suggestedActions,
        Instant createdAt
) {}

package com.stockflow.chat.controller;

import com.stockflow.chat.dto.ChatConversationResponse;
import com.stockflow.chat.dto.ChatMessageRequest;
import com.stockflow.chat.dto.ChatMessageResponse;
import com.stockflow.chat.service.ChatbotService;
import com.stockflow.identity.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "Chatbot", description = "Multi-Persona Conversational AI Assistant Endpoints")
public class ChatController {

    private final ChatbotService chatbotService;

    public ChatController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @PostMapping("/message")
    @Operation(summary = "Send a message to the assistant and receive intelligent, persona-aware guidance")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @Valid @RequestBody ChatMessageRequest request,
            @AuthenticationPrincipal User user) {
        ChatMessageResponse response = chatbotService.processMessage(request, user);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/conversations")
    @Operation(summary = "Get list of conversation sessions for current user or session")
    public ResponseEntity<List<ChatConversationResponse>> listConversations(
            @RequestParam(required = false) String sessionId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(chatbotService.getUserConversations(user, sessionId));
    }

    @GetMapping("/conversations/{id}")
    @Operation(summary = "Get full message history for a specific conversation")
    public ResponseEntity<ChatConversationResponse> getConversation(
            @PathVariable UUID id,
            @RequestParam(required = false) String sessionId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(chatbotService.getConversation(id, user, sessionId));
    }

    @DeleteMapping("/conversations/{id}")
    @Operation(summary = "Delete / clear a specific conversation")
    public ResponseEntity<Void> deleteConversation(
            @PathVariable UUID id,
            @RequestParam(required = false) String sessionId,
            @AuthenticationPrincipal User user) {
        chatbotService.deleteConversation(id, user, sessionId);
        return ResponseEntity.noContent().build();
    }
}

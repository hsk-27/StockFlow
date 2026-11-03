package com.stockflow.chat;

import com.stockflow.chat.controller.ChatController;
import com.stockflow.chat.domain.ChatSender;
import com.stockflow.chat.dto.ChatAction;
import com.stockflow.chat.dto.ChatConversationResponse;
import com.stockflow.chat.dto.ChatMessageRequest;
import com.stockflow.chat.dto.ChatMessageResponse;
import com.stockflow.chat.service.ChatbotService;
import com.stockflow.identity.domain.Role;
import com.stockflow.identity.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatControllerIntegrationTest {

    @Mock
    private ChatbotService chatbotService;

    private ChatController chatController;
    private User customer;

    @BeforeEach
    void setUp() {
        chatController = new ChatController(chatbotService);
        customer = new User(UUID.randomUUID(), "customer@stockflow.com", "hash", "Alice", Role.CUSTOMER);
    }

    @Test
    @DisplayName("POST /api/v1/chat/message - Guest query returns 200 OK with assistant response")
    void shouldProcessGuestChatMessage() {
        UUID convId = UUID.randomUUID();
        ChatMessageResponse expectedResponse = new ChatMessageResponse(
                UUID.randomUUID(),
                convId,
                "sess-guest-1",
                ChatSender.ASSISTANT,
                "Here are our active categories: Electronics, Audio",
                "CATEGORY_LIST",
                List.of(new ChatAction("Browse Audio", "QUERY", "Show audio")),
                Instant.now()
        );

        when(chatbotService.processMessage(any(ChatMessageRequest.class), eq(null)))
                .thenReturn(expectedResponse);

        ChatMessageRequest request = new ChatMessageRequest("What categories are available?", "sess-guest-1", null);
        ResponseEntity<ChatMessageResponse> response = chatController.sendMessage(request, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().content()).contains("Electronics, Audio");
        assertThat(response.getBody().intent()).isEqualTo("CATEGORY_LIST");
    }

    @Test
    @DisplayName("POST /api/v1/chat/message - Authenticated customer query passes User principal")
    void shouldProcessCustomerChatMessage() {
        UUID convId = UUID.randomUUID();
        ChatMessageResponse expectedResponse = new ChatMessageResponse(
                UUID.randomUUID(),
                convId,
                "sess-cust-1",
                ChatSender.ASSISTANT,
                "Your order ORD-123 is currently CONFIRMED",
                "ORDER_DETAILS",
                List.of(new ChatAction("Cancel Order", "QUERY", "Can I cancel ORD-123")),
                Instant.now()
        );

        when(chatbotService.processMessage(any(ChatMessageRequest.class), eq(customer)))
                .thenReturn(expectedResponse);

        ChatMessageRequest request = new ChatMessageRequest("Where is ORD-123?", "sess-cust-1", null);
        ResponseEntity<ChatMessageResponse> response = chatController.sendMessage(request, customer);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().content()).contains("ORD-123 is currently CONFIRMED");
    }

    @Test
    @DisplayName("GET /api/v1/chat/conversations - Lists conversations for session or user")
    void shouldListUserConversations() {
        ChatConversationResponse conv = new ChatConversationResponse(
                UUID.randomUUID(),
                "sess-1",
                "CUSTOMER",
                "Order Inquiry",
                2,
                Instant.now(),
                Instant.now(),
                List.of()
        );
        when(chatbotService.getUserConversations(customer, "sess-1")).thenReturn(List.of(conv));

        ResponseEntity<List<ChatConversationResponse>> response = chatController.listConversations("sess-1", customer);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).title()).isEqualTo("Order Inquiry");
    }

    @Test
    @DisplayName("DELETE /api/v1/chat/conversations/{id} - Deletes conversation returning 204 No Content")
    void shouldDeleteConversation() {
        UUID convId = UUID.randomUUID();
        doNothing().when(chatbotService).deleteConversation(convId, customer, "sess-1");

        ResponseEntity<Void> response = chatController.deleteConversation(convId, "sess-1", customer);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(chatbotService, times(1)).deleteConversation(convId, customer, "sess-1");
    }
}

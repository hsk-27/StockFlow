package com.stockflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.cart.dto.AddToCartRequest;
import com.stockflow.chat.dto.ChatMessageRequest;
import com.stockflow.identity.dto.LoginRequest;
import com.stockflow.order.dto.CheckoutRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ============================================================================
 * [FULL-STACK INTEGRATION TEST: REAL CONTEXT, FLYWAY, SECURITY & DB]
 * ============================================================================
 * Boots the authoritative Spring Boot 3.3.5 container with Flyway migrations,
 * Hibernate DDL validation, Spring Security filter chains, and in-memory persistence.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class FullCommerceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("0. Sanity: Spring Application Context boots and Actuator health reports UP")
    void contextLoadsAndHealthReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("1. Auth: Login with pre-seeded Customer credentials issues valid JWT & Refresh token")
    void shouldLoginWithCustomerSeedData() throws Exception {
        LoginRequest login = new LoginRequest("customer@stockflow.com", "Password123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.email").value("customer@stockflow.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    @DisplayName("2. Catalog: Browse public products seeded via Flyway V2")
    void shouldBrowsePublicProducts() throws Exception {
        mockMvc.perform(get("/api/v1/products?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("3. End-to-End: Login -> Add to Cart -> View Cart -> Idempotent Checkout -> Replay")
    void shouldExecuteFullCustomerOrderWorkflow() throws Exception {
        // Step 1: Login
        LoginRequest login = new LoginRequest("customer@stockflow.com", "Password123!");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String token = loginJson.get("accessToken").asText();

        // Step 2: Add seed item to Cart (SKU-AUDIO-PRO-BLK from V2__seed_data.sql)
        AddToCartRequest addToCart = new AddToCartRequest("SKU-AUDIO-PRO-BLK", 1);
        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addToCart)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        // Step 3: View Cart
        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalAmount").isNotEmpty());

        // Step 4: Checkout with unique Idempotency-Key
        String idempotencyKey = UUID.randomUUID().toString();
        CheckoutRequest checkout = new CheckoutRequest("456 Innovation Way, Austin, TX");

        MvcResult checkoutResult1 = mockMvc.perform(post("/api/v1/orders/checkout")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkout)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andReturn();

        String orderId1 = objectMapper.readTree(checkoutResult1.getResponse().getContentAsString()).get("id").asText();

        // Step 5: Duplicate Checkout Request with identical Idempotency-Key (Retry Simulation)
        MvcResult checkoutResult2 = mockMvc.perform(post("/api/v1/orders/checkout")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkout)))
                .andExpect(status().isCreated()) // Idempotent replay returns 201 Created with identical cached order
                .andReturn();

        String orderId2 = objectMapper.readTree(checkoutResult2.getResponse().getContentAsString()).get("id").asText();

        // Invariant: Idempotent retry returns the EXACT SAME order without re-reserving stock
        assertThat(orderId1).isEqualTo(orderId2);
    }

    @Test
    @DisplayName("4. Chatbot: Guest product search and category inquiry")
    void shouldProcessChatbotInquiry() throws Exception {
        ChatMessageRequest chatReq = new ChatMessageRequest("Show me available products", "guest-test-session", null);

        mockMvc.perform(post("/api/v1/chat/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chatReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sender").value("ASSISTANT"))
                .andExpect(jsonPath("$.content").isNotEmpty());
    }
}

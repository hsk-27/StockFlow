package com.stockflow.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.common.exception.BusinessRuleViolationException;
import com.stockflow.common.exception.ConflictException;
import com.stockflow.identity.domain.Role;
import com.stockflow.identity.domain.User;
import com.stockflow.order.domain.IdempotencyRecord;
import com.stockflow.order.domain.IdempotencyStatus;
import com.stockflow.order.dto.CheckoutRequest;
import com.stockflow.order.repository.IdempotencyRecordRepository;
import com.stockflow.order.service.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyRecordRepository idempotencyRepository;

    private ObjectMapper objectMapper;
    private IdempotencyService idempotencyService;
    private User user;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        idempotencyService = new IdempotencyService(idempotencyRepository, objectMapper);
        user = new User(UUID.randomUUID(), "buyer@example.com", "hash", "Buyer", Role.CUSTOMER);
    }

    @Test
    @DisplayName("Should acquire new idempotency key when not previously seen")
    void shouldAcquireNewKey() {
        CheckoutRequest request = new CheckoutRequest("100 Market St");
        when(idempotencyRepository.findByIdempotencyKeyAndUserId("key-123", user.getId())).thenReturn(Optional.empty());

        Optional<String> cached = idempotencyService.acquireOrVerify("key-123", user, request);

        assertThat(cached).isEmpty();
        verify(idempotencyRepository).save(any(IdempotencyRecord.class));
    }

    @Test
    @DisplayName("Should return 409 Conflict if duplicate request arrives while key is PROCESSING")
    void shouldRejectConcurrentProcessingRequest() {
        CheckoutRequest request = new CheckoutRequest("100 Market St");
        String hash = idempotencyService.hashPayload(request);

        IdempotencyRecord record = new IdempotencyRecord(UUID.randomUUID(), "key-123", user, hash, Instant.now().plusSeconds(3600));

        when(idempotencyRepository.findByIdempotencyKeyAndUserId("key-123", user.getId())).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> idempotencyService.acquireOrVerify("key-123", user, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("is currently in progress");
    }

    @Test
    @DisplayName("Should reject key reuse if payload has changed")
    void shouldRejectMismatchedPayload() {
        CheckoutRequest originalRequest = new CheckoutRequest("100 Market St");
        CheckoutRequest alteredRequest = new CheckoutRequest("500 Different Blvd");

        String originalHash = idempotencyService.hashPayload(originalRequest);
        IdempotencyRecord record = new IdempotencyRecord(UUID.randomUUID(), "key-123", user, originalHash, Instant.now().plusSeconds(3600));
        record.markCompleted(201, "{\"orderId\":\"mock-id\"}");

        when(idempotencyRepository.findByIdempotencyKeyAndUserId("key-123", user.getId())).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> idempotencyService.acquireOrVerify("key-123", user, alteredRequest))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("previously used with a different request payload");
    }

    @Test
    @DisplayName("Should return cached response when key is COMPLETED and payload matches")
    void shouldReturnCachedResponse() {
        CheckoutRequest request = new CheckoutRequest("100 Market St");
        String hash = idempotencyService.hashPayload(request);

        IdempotencyRecord record = new IdempotencyRecord(UUID.randomUUID(), "key-123", user, hash, Instant.now().plusSeconds(3600));
        record.markCompleted(201, "{\"orderId\":\"cached-order-123\"}");

        when(idempotencyRepository.findByIdempotencyKeyAndUserId("key-123", user.getId())).thenReturn(Optional.of(record));

        Optional<String> cached = idempotencyService.acquireOrVerify("key-123", user, request);

        assertThat(cached).isPresent();
        assertThat(cached.get()).contains("cached-order-123");
    }
}

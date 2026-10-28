package com.stockflow.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.common.exception.BusinessRuleViolationException;
import com.stockflow.common.exception.ConflictException;
import com.stockflow.identity.domain.User;
import com.stockflow.order.domain.IdempotencyRecord;
import com.stockflow.order.domain.IdempotencyStatus;
import com.stockflow.order.repository.IdempotencyRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * ============================================================================
 * [CS DISTRIBUTED SYSTEMS CONCEPT - AT-MOST-ONCE & EXACTLY-ONCE SEMANTICS]
 * ============================================================================
 * In distributed computing, network calls fail non-deterministically (Fallacies
 * of Distributed Computing #1: "The network is reliable"). If a customer submits
 * an order and their mobile connection drops during HTTP transmission, the client
 * cannot know whether:
 *   (a) The server never received the request, OR
 *   (b) The server processed the checkout, charged the card, and only the response dropped.
 *
 * If the client retries blindly, the server could duplicate the charge and create
 * two separate orders!
 *
 * Solution implemented here (IETF Draft 'The Idempotency-Key HTTP Header Field'):
 *  1. Client sends a unique client-generated UUID in the 'Idempotency-Key' header.
 *  2. We compute a SHA-256 fingerprint of the request payload to detect tampering/mismatches.
 *  3. In an atomic transaction (REQUIRES_NEW), we insert an IdempotencyRecord with status PROCESSING.
 *  4. If a concurrent retry hits before completion -> 409 Conflict (prevents double-execution races).
 *  5. If the request already completed previously -> Replay the cached HTTP 201 response directly
 *     without re-executing inventory reservations or payment processing!
 *
 * @author Senior CS Capstone Project
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyRecordRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRecordRepository idempotencyRepository, ObjectMapper objectMapper) {
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Checks existing idempotency key or reserves new key in state PROCESSING.
     * Returns Optional containing cached response body if already completed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<String> acquireOrVerify(String key, User user, Object requestPayload) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }

        String payloadHash = hashPayload(requestPayload);
        Optional<IdempotencyRecord> existingOpt = idempotencyRepository.findByIdempotencyKeyAndUserId(key, user.getId());

        if (existingOpt.isPresent()) {
            IdempotencyRecord existing = existingOpt.get();

            if (!existing.getRequestHash().equals(payloadHash)) {
                throw new BusinessRuleViolationException(
                        "Idempotency key '" + key + "' was previously used with a different request payload.");
            }

            if (existing.getStatus() == IdempotencyStatus.PROCESSING) {
                throw new ConflictException(
                        "An operation with idempotency key '" + key + "' is currently in progress. Please retry shortly.");
            }

            log.info("Idempotent request detected for key {}. Returning cached outcome.", key);
            return Optional.ofNullable(existing.getResponseBody());
        }

        // Reserve key in PROCESSING status
        IdempotencyRecord record = new IdempotencyRecord(
                UUID.randomUUID(),
                key,
                user,
                payloadHash,
                Instant.now().plusSeconds(86400) // 24-hour TTL
        );
        idempotencyRepository.save(record);
        return Optional.empty();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String key, User user, int statusCode, Object responsePayload) {
        if (key == null || key.isBlank()) {
            return;
        }

        idempotencyRepository.findByIdempotencyKeyAndUserId(key, user.getId()).ifPresent(record -> {
            try {
                String jsonBody = objectMapper.writeValueAsString(responsePayload);
                record.markCompleted(statusCode, jsonBody);
                idempotencyRepository.save(record);
                log.info("Recorded idempotent response for key {}", key);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize idempotent response body for key {}", key, e);
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseKeyOnFailure(String key, User user) {
        if (key != null && !key.isBlank()) {
            idempotencyRepository.findByIdempotencyKeyAndUserId(key, user.getId())
                    .ifPresent(idempotencyRepository::delete);
        }
    }

    public String hashPayload(Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new IllegalStateException("Failed to calculate request payload hash", e);
        }
    }
}

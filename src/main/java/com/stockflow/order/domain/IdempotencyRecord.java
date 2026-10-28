package com.stockflow.order.domain;

import com.stockflow.identity.domain.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"idempotency_key", "user_id"})
})
public class IdempotencyRecord {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdempotencyStatus status;

    @Column(name = "response_status_code")
    private Integer responseStatusCode;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(UUID id, String idempotencyKey, User user, String requestHash, Instant expiresAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.user = user;
        this.requestHash = requestHash;
        this.status = IdempotencyStatus.PROCESSING;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public User getUser() {
        return user;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public IdempotencyStatus getStatus() {
        return status;
    }

    public Integer getResponseStatusCode() {
        return responseStatusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void markCompleted(int statusCode, String responseBody) {
        this.status = IdempotencyStatus.COMPLETED;
        this.responseStatusCode = statusCode;
        this.responseBody = responseBody;
    }
}

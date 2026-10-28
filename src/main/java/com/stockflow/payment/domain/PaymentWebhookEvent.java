package com.stockflow.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_webhook_events")
public class PaymentWebhookEvent {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true, length = 100)
    private String eventId;

    @Column(name = "provider_name", nullable = false, length = 50)
    private String providerName;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected PaymentWebhookEvent() {
    }

    public PaymentWebhookEvent(UUID id, String eventId, String providerName, String eventType, String payload) {
        this.id = id != null ? id : UUID.randomUUID();
        this.eventId = eventId;
        this.providerName = providerName;
        this.eventType = eventType;
        this.payload = payload;
        this.processedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}

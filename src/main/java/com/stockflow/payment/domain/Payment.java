package com.stockflow.payment.domain;

import com.stockflow.order.domain.Order;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "provider_name", nullable = false, length = 50)
    private String providerName;

    @Column(name = "provider_reference", length = 100)
    private String providerReference;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
    }

    public Payment(UUID id, Order order, String providerName, BigDecimal amount, String currency) {
        this.id = id != null ? id : UUID.randomUUID();
        this.order = order;
        this.providerName = providerName;
        this.amount = amount;
        this.currency = currency != null ? currency.toUpperCase() : "USD";
        this.status = PaymentStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public void setProviderReference(String providerReference) {
        this.providerReference = providerReference;
        this.updatedAt = Instant.now();
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void markSucceeded(String providerReference) {
        this.status = PaymentStatus.SUCCEEDED;
        if (providerReference != null) {
            this.providerReference = providerReference;
        }
        this.errorMessage = null;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String errorMessage) {
        this.status = PaymentStatus.FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = Instant.now();
    }

    public void markUncertain(String errorMessage) {
        this.status = PaymentStatus.UNCERTAIN;
        this.errorMessage = errorMessage;
        this.updatedAt = Instant.now();
    }

    public void markRefunded() {
        this.status = PaymentStatus.REFUNDED;
        this.updatedAt = Instant.now();
    }
}

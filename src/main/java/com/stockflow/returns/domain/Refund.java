package com.stockflow.returns.domain;

import com.stockflow.order.domain.Order;
import com.stockflow.payment.domain.Payment;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refunds")
public class Refund {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "provider_refund_id", length = 100)
    private String providerRefundId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Refund() {
    }

    public Refund(UUID id, Order order, Payment payment, BigDecimal amount, String currency, String reason, String providerRefundId) {
        this.id = id != null ? id : UUID.randomUUID();
        this.order = order;
        this.payment = payment;
        this.amount = amount;
        this.currency = currency != null ? currency.toUpperCase() : "USD";
        this.reason = reason;
        this.status = "COMPLETED";
        this.providerRefundId = providerRefundId;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public Payment getPayment() {
        return payment;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getReason() {
        return reason;
    }

    public String getStatus() {
        return status;
    }

    public String getProviderRefundId() {
        return providerRefundId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

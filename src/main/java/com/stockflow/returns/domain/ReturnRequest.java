package com.stockflow.returns.domain;

import com.stockflow.identity.domain.User;
import com.stockflow.order.domain.Order;
import com.stockflow.seller.domain.Seller;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "return_requests")
public class ReturnRequest {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReturnStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "restock_decision", nullable = false, length = 30)
    private RestockDecision restockDecision;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReturnRequest() {
    }

    public ReturnRequest(UUID id, Order order, User customer, Seller seller, String reason) {
        this.id = id != null ? id : UUID.randomUUID();
        this.order = order;
        this.customer = customer;
        this.seller = seller;
        this.reason = reason;
        this.status = ReturnStatus.REQUESTED;
        this.restockDecision = RestockDecision.NONE;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public User getCustomer() {
        return customer;
    }

    public Seller getSeller() {
        return seller;
    }

    public String getReason() {
        return reason;
    }

    public ReturnStatus getStatus() {
        return status;
    }

    public RestockDecision getRestockDecision() {
        return restockDecision;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void approve() {
        this.status = ReturnStatus.APPROVED;
        this.updatedAt = Instant.now();
    }

    public void reject() {
        this.status = ReturnStatus.REJECTED;
        this.updatedAt = Instant.now();
    }

    public void receive(RestockDecision decision) {
        this.status = ReturnStatus.RECEIVED;
        this.restockDecision = decision;
        this.updatedAt = Instant.now();
    }

    public void complete() {
        this.status = ReturnStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }
}

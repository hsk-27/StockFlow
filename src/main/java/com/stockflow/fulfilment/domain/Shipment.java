package com.stockflow.fulfilment.domain;

import com.stockflow.order.domain.Order;
import com.stockflow.seller.domain.Seller;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shipments")
public class Shipment {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    @Column(nullable = false, length = 50)
    private String carrier;

    @Column(name = "tracking_number", nullable = false, length = 100)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ShipmentStatus status;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Shipment() {
    }

    public Shipment(UUID id, Order order, Seller seller, String carrier, String trackingNumber) {
        this.id = id != null ? id : UUID.randomUUID();
        this.order = order;
        this.seller = seller;
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.status = ShipmentStatus.SHIPPED;
        this.shippedAt = Instant.now();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public Seller getSeller() {
        return seller;
    }

    public String getCarrier() {
        return carrier;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    public Instant getShippedAt() {
        return shippedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void markDelivered() {
        this.status = ShipmentStatus.DELIVERED;
        this.deliveredAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}

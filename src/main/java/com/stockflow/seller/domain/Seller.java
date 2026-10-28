package com.stockflow.seller.domain;

import com.stockflow.identity.domain.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sellers")
public class Seller {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "store_name", nullable = false, unique = true)
    private String storeName;

    @Column(name = "business_email", nullable = false)
    private String businessEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SellerStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Seller() {
    }

    public Seller(UUID id, User user, String storeName, String businessEmail, SellerStatus status) {
        this.id = id != null ? id : UUID.randomUUID();
        this.user = user;
        this.storeName = storeName;
        this.businessEmail = businessEmail.toLowerCase().trim();
        this.status = status != null ? status : SellerStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getStoreName() {
        return storeName;
    }

    public String getBusinessEmail() {
        return businessEmail;
    }

    public SellerStatus getStatus() {
        return status;
    }

    public void setStatus(SellerStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void updateDetails(String storeName, String businessEmail) {
        this.storeName = storeName;
        this.businessEmail = businessEmail.toLowerCase().trim();
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

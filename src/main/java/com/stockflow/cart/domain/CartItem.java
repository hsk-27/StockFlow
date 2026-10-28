package com.stockflow.cart.domain;

import com.stockflow.catalog.domain.Product;
import com.stockflow.common.exception.BusinessRuleViolationException;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cart_items", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"cart_id", "sku"})
})
public class CartItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 100)
    private String sku;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CartItem() {
    }

    public CartItem(UUID id, Cart cart, Product product, String sku, int quantity) {
        if (quantity <= 0) {
            throw new BusinessRuleViolationException("Cart item quantity must be positive");
        }
        this.id = id != null ? id : UUID.randomUUID();
        this.cart = cart;
        this.product = product;
        this.sku = sku.toUpperCase().trim();
        this.quantity = quantity;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Cart getCart() {
        return cart;
    }

    public Product getProduct() {
        return product;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        if (quantity <= 0) {
            throw new BusinessRuleViolationException("Quantity must be positive");
        }
        this.quantity = quantity;
        this.updatedAt = Instant.now();
    }

    public void incrementQuantity(int addition) {
        if (addition <= 0) {
            throw new BusinessRuleViolationException("Addition must be positive");
        }
        this.quantity += addition;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

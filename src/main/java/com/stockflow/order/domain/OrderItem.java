package com.stockflow.order.domain;

import com.stockflow.common.exception.BusinessRuleViolationException;
import com.stockflow.seller.domain.Seller;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false, length = 100)
    private String sku;

    @Column(name = "product_title", nullable = false)
    private String productTitle;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    protected OrderItem() {
    }

    public OrderItem(UUID id, Order order, Seller seller, UUID productId, String sku,
                     String productTitle, BigDecimal unitPrice, int quantity,
                     BigDecimal discountAmount, String currency) {
        if (quantity <= 0) {
            throw new BusinessRuleViolationException("Order item quantity must be positive");
        }
        this.id = id != null ? id : UUID.randomUUID();
        this.order = order;
        this.seller = seller;
        this.productId = productId;
        this.sku = sku.toUpperCase().trim();
        this.productTitle = productTitle;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.discountAmount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        this.currency = currency != null ? currency.toUpperCase() : "USD";
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

    public UUID getProductId() {
        return productId;
    }

    public String getSku() {
        return sku;
    }

    public String getProductTitle() {
        return productTitle;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).subtract(discountAmount);
    }
}

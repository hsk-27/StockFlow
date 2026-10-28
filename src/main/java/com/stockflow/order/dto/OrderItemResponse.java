package com.stockflow.order.dto;

import com.stockflow.order.domain.OrderItem;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        UUID sellerId,
        String storeName,
        UUID productId,
        String sku,
        String productTitle,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal discountAmount,
        BigDecimal subtotal,
        String currency
) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getSeller().getId(),
                item.getSeller().getStoreName(),
                item.getProductId(),
                item.getSku(),
                item.getProductTitle(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getDiscountAmount(),
                item.getSubtotal(),
                item.getCurrency()
        );
    }
}

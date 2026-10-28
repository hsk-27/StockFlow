package com.stockflow.order.dto;

import com.stockflow.order.domain.Order;
import com.stockflow.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String orderNumber,
        UUID customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        String currency,
        String shippingAddress,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomer().getId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getShippingAddress(),
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}

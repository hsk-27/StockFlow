package com.stockflow.order.domain;

import java.util.Set;

public enum OrderStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    EXPIRED,
    REFUNDED;

    public boolean canTransitionTo(OrderStatus target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case PENDING_PAYMENT -> target == CONFIRMED || target == CANCELLED || target == EXPIRED;
            case CONFIRMED -> target == PROCESSING || target == SHIPPED || target == CANCELLED || target == REFUNDED;
            case PROCESSING -> target == SHIPPED || target == CANCELLED || target == REFUNDED;
            case SHIPPED -> target == DELIVERED;
            case DELIVERED -> target == REFUNDED;
            case CANCELLED, EXPIRED, REFUNDED -> false;
        };
    }
}

package com.stockflow.order.domain;

import java.util.Set;

/**
 * ============================================================================
 * [THEORY OF COMPUTATION: FINITE STATE MACHINE (FSM) ORDER LIFECYCLE]
 * ============================================================================
 * An explicit state transition matrix guarantees that an order cannot jump
 * across business states illegally (e.g. an unpaid order cannot be shipped;
 * a delivered order cannot be cancelled without going through returns).
 *
 * State Transition Diagram (Directed Graph):
 *
 *                   [PENDING_PAYMENT]
 *                     /      |      \
 *     (timeout)      / (pay) |       \ (cancel)
 *                   v        v        v
 *              [EXPIRED] [CONFIRMED] [CANCELLED]
 *                         /     \
 *             (processing)/       \(dispatch)
 *                        v         v
 *                  [PROCESSING] -> [SHIPPED]
 *                       |              |
 *               (cancel)|              | (deliver)
 *                       v              v
 *                  [CANCELLED]    [DELIVERED]
 *                                      |
 *                                      | (return / refund)
 *                                      v
 *                                  [REFUNDED]
 *
 * Terminal States: EXPIRED, CANCELLED, REFUNDED.
 *
 * @author Senior CS Capstone Project
 */
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

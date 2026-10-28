package com.stockflow.order;

import com.stockflow.common.exception.BusinessRuleViolationException;
import com.stockflow.identity.domain.Role;
import com.stockflow.identity.domain.User;
import com.stockflow.order.domain.Order;
import com.stockflow.order.domain.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateMachineTest {

    private Order order;

    @BeforeEach
    void setUp() {
        User customer = new User(UUID.randomUUID(), "buyer@example.com", "hash", "Buyer", Role.CUSTOMER);
        order = new Order(UUID.randomUUID(), "ORD-12345", customer, BigDecimal.valueOf(150), "USD", "123 Main St");
    }

    @Test
    @DisplayName("Should follow normal happy path: PENDING_PAYMENT -> CONFIRMED -> PROCESSING -> SHIPPED -> DELIVERED")
    void shouldFollowHappyPath() {
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        order.transitionTo(OrderStatus.CONFIRMED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        order.transitionTo(OrderStatus.PROCESSING);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);

        order.transitionTo(OrderStatus.SHIPPED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);

        order.transitionTo(OrderStatus.DELIVERED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("Should reject illegal transition: PENDING_PAYMENT -> DELIVERED")
    void shouldRejectIllegalDirectDelivery() {
        assertThatThrownBy(() -> order.transitionTo(OrderStatus.DELIVERED))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Illegal order state transition from PENDING_PAYMENT to DELIVERED");
    }

    @Test
    @DisplayName("Should reject transition out of terminal CANCELLED state")
    void shouldRejectTransitionFromCancelled() {
        order.transitionTo(OrderStatus.CANCELLED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        assertThatThrownBy(() -> order.transitionTo(OrderStatus.CONFIRMED))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Illegal order state transition from CANCELLED to CONFIRMED");
    }

    @Test
    @DisplayName("Should reject transition out of terminal EXPIRED state")
    void shouldRejectTransitionFromExpired() {
        order.transitionTo(OrderStatus.EXPIRED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);

        assertThatThrownBy(() -> order.transitionTo(OrderStatus.PROCESSING))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Illegal order state transition from EXPIRED to PROCESSING");
    }
}

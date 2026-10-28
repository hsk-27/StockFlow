package com.stockflow.order;

import com.stockflow.identity.domain.Role;
import com.stockflow.identity.domain.User;
import com.stockflow.inventory.domain.StockReservation;
import com.stockflow.inventory.repository.StockReservationRepository;
import com.stockflow.inventory.service.InventoryService;
import com.stockflow.order.domain.Order;
import com.stockflow.order.domain.OrderStatus;
import com.stockflow.order.repository.OrderRepository;
import com.stockflow.order.worker.ReservationExpiryWorker;
import com.stockflow.outbox.service.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationExpiryWorkerTest {

    @Mock
    private StockReservationRepository reservationRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxService outboxService;

    private ReservationExpiryWorker worker;

    @BeforeEach
    void setUp() {
        worker = new ReservationExpiryWorker(reservationRepository, inventoryService, orderRepository, outboxService);
    }

    @Test
    @DisplayName("Should release inventory and transition order to EXPIRED when reservations expire")
    void shouldReleaseAndExpireOrders() {
        UUID orderId = UUID.randomUUID();
        StockReservation reservation = new StockReservation(UUID.randomUUID(), orderId, "SKU-EXP-1", 3, Instant.now().minusSeconds(60));

        User customer = new User(UUID.randomUUID(), "c@c.com", "h", "C", Role.CUSTOMER);
        Order order = new Order(orderId, "ORD-EXP-1", customer, BigDecimal.valueOf(50), "USD", "Address");

        when(reservationRepository.findExpiredPendingReservations(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(reservation));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        worker.sweepExpiredReservations();

        verify(inventoryService).releaseReservation(orderId, "EXPIRED");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        verify(orderRepository).save(order);
        verify(outboxService).publishEvent(eq("ORDER_EXPIRED"), eq("Order"), eq(orderId.toString()), any());
    }
}

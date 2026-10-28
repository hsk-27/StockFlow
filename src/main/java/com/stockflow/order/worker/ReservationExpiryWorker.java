package com.stockflow.order.worker;

import com.stockflow.inventory.domain.StockReservation;
import com.stockflow.inventory.repository.StockReservationRepository;
import com.stockflow.inventory.service.InventoryService;
import com.stockflow.order.domain.Order;
import com.stockflow.order.domain.OrderStatus;
import com.stockflow.order.repository.OrderRepository;
import com.stockflow.outbox.service.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ReservationExpiryWorker {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryWorker.class);

    private final StockReservationRepository reservationRepository;
    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final OutboxService outboxService;

    public ReservationExpiryWorker(StockReservationRepository reservationRepository,
                                   InventoryService inventoryService,
                                   OrderRepository orderRepository,
                                   OutboxService outboxService) {
        this.reservationRepository = reservationRepository;
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
        this.outboxService = outboxService;
    }

    @Scheduled(cron = "${stockflow.reservation.worker-cron:*/30 * * * * *}")
    @Transactional
    public void sweepExpiredReservations() {
        Instant now = Instant.now();
        List<StockReservation> expiredList = reservationRepository.findExpiredPendingReservations(now, PageRequest.of(0, 50));

        if (expiredList.isEmpty()) {
            return;
        }

        Set<UUID> orderIds = expiredList.stream()
                .map(StockReservation::getOrderId)
                .collect(Collectors.toSet());

        log.info("Processing {} expired stock reservations across {} orders", expiredList.size(), orderIds.size());

        for (UUID orderId : orderIds) {
            try {
                inventoryService.releaseReservation(orderId, "EXPIRED");

                orderRepository.findById(orderId).ifPresent(order -> {
                    if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                        order.transitionTo(OrderStatus.EXPIRED);
                        orderRepository.save(order);

                        outboxService.publishEvent(
                                "ORDER_EXPIRED",
                                "Order",
                                order.getId().toString(),
                                Map.of("orderId", order.getId(), "reason", "RESERVATION_TIMEOUT")
                        );
                        log.info("Transitioned order {} to EXPIRED due to reservation timeout", order.getOrderNumber());
                    }
                });
            } catch (Exception e) {
                log.error("Failed to expire reservation for order: {}", orderId, e);
            }
        }
    }
}

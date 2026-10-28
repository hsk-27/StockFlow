package com.stockflow.reporting.service;

import com.stockflow.identity.repository.UserRepository;
import com.stockflow.inventory.repository.InventoryRepository;
import com.stockflow.order.domain.Order;
import com.stockflow.order.domain.OrderStatus;
import com.stockflow.order.repository.OrderRepository;
import com.stockflow.outbox.repository.OutboxEventRepository;
import com.stockflow.payment.domain.Payment;
import com.stockflow.payment.domain.PaymentStatus;
import com.stockflow.payment.repository.PaymentRepository;
import com.stockflow.reporting.dto.OperationalMetricsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportingService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final InventoryRepository inventoryRepository;
    private final UserRepository userRepository;
    private final OutboxEventRepository outboxEventRepository;

    public ReportingService(OrderRepository orderRepository,
                            PaymentRepository paymentRepository,
                            InventoryRepository inventoryRepository,
                            UserRepository userRepository,
                            OutboxEventRepository outboxEventRepository) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.inventoryRepository = inventoryRepository;
        this.userRepository = userRepository;
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional(readOnly = true)
    public OperationalMetricsResponse getOperationalMetrics() {
        List<Order> orders = orderRepository.findAll();
        long totalOrders = orders.size();

        Map<String, Long> statusDistribution = new HashMap<>();
        for (OrderStatus status : OrderStatus.values()) {
            statusDistribution.put(status.name(), 0L);
        }

        for (Order o : orders) {
            statusDistribution.put(o.getStatus().name(), statusDistribution.get(o.getStatus().name()) + 1);
        }

        List<Payment> succeededPayments = paymentRepository.findByStatus(PaymentStatus.SUCCEEDED);
        BigDecimal totalRevenue = succeededPayments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long lowStockCount = inventoryRepository.findLowStock(10).size();
        long totalUsers = userRepository.count();
        long pendingOutbox = outboxEventRepository.count();

        return new OperationalMetricsResponse(
                totalOrders,
                totalRevenue,
                "USD",
                statusDistribution,
                lowStockCount,
                totalUsers,
                pendingOutbox
        );
    }
}

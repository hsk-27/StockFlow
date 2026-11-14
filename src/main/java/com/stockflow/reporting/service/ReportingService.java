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
        long totalOrders = orderRepository.count();

        Map<String, Long> statusDistribution = new HashMap<>();
        for (OrderStatus status : OrderStatus.values()) {
            statusDistribution.put(status.name(), 0L);
        }

        List<Object[]> groupedCounts = orderRepository.countOrdersByStatusGrouped();
        for (Object[] row : groupedCounts) {
            OrderStatus status = (OrderStatus) row[0];
            Long count = (Long) row[1];
            if (status != null && count != null) {
                statusDistribution.put(status.name(), count);
            }
        }

        BigDecimal totalRevenue = paymentRepository.sumAmountByStatus(PaymentStatus.SUCCEEDED);
        long lowStockCount = inventoryRepository.countLowStock(10);
        long totalUsers = userRepository.count();
        long pendingOutbox = outboxEventRepository.countByStatus("PENDING");

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

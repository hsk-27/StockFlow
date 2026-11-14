package com.stockflow.reporting;

import com.stockflow.identity.repository.UserRepository;
import com.stockflow.inventory.repository.InventoryRepository;
import com.stockflow.order.domain.OrderStatus;
import com.stockflow.order.repository.OrderRepository;
import com.stockflow.outbox.repository.OutboxEventRepository;
import com.stockflow.payment.domain.PaymentStatus;
import com.stockflow.payment.repository.PaymentRepository;
import com.stockflow.reporting.dto.OperationalMetricsResponse;
import com.stockflow.reporting.service.ReportingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportingServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private ReportingService reportingService;

    @BeforeEach
    void setUp() {
        reportingService = new ReportingService(
                orderRepository,
                paymentRepository,
                inventoryRepository,
                userRepository,
                outboxEventRepository
        );
    }

    @Test
    @DisplayName("Should aggregate platform metrics directly via database queries")
    void shouldAggregatePlatformMetricsAccurately() {
        when(orderRepository.count()).thenReturn(142L);

        List<Object[]> groupedOrders = List.of(
                new Object[]{OrderStatus.CONFIRMED, 100L},
                new Object[]{OrderStatus.SHIPPED, 30L},
                new Object[]{OrderStatus.PENDING_PAYMENT, 12L}
        );
        when(orderRepository.countOrdersByStatusGrouped()).thenReturn(groupedOrders);

        when(paymentRepository.sumAmountByStatus(PaymentStatus.SUCCEEDED))
                .thenReturn(new BigDecimal("28400.00"));

        when(inventoryRepository.countLowStock(10)).thenReturn(3L);
        when(userRepository.count()).thenReturn(55L);
        when(outboxEventRepository.countByStatus("PENDING")).thenReturn(0L);

        OperationalMetricsResponse response = reportingService.getOperationalMetrics();

        assertThat(response).isNotNull();
        assertThat(response.totalOrders()).isEqualTo(142L);
        assertThat(response.totalRevenue()).isEqualByComparingTo("28400.00");
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.lowStockItemCount()).isEqualTo(3L);
        assertThat(response.totalRegisteredUsers()).isEqualTo(55L);
        assertThat(response.pendingOutboxEvents()).isEqualTo(0L);

        assertThat(response.ordersByStatus()).containsEntry("CONFIRMED", 100L);
        assertThat(response.ordersByStatus()).containsEntry("SHIPPED", 30L);
        assertThat(response.ordersByStatus()).containsEntry("PENDING_PAYMENT", 12L);
        assertThat(response.ordersByStatus()).containsEntry("DELIVERED", 0L);
    }
}

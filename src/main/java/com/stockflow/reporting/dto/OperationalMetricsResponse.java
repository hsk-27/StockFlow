package com.stockflow.reporting.dto;

import java.math.BigDecimal;
import java.util.Map;

public record OperationalMetricsResponse(
        long totalOrders,
        BigDecimal totalRevenue,
        String currency,
        Map<String, Long> ordersByStatus,
        long lowStockItemCount,
        long totalRegisteredUsers,
        long pendingOutboxEvents
) {}

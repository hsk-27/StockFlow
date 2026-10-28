package com.stockflow.returns.repository;

import com.stockflow.returns.domain.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface RefundRepository extends JpaRepository<Refund, UUID> {
    List<Refund> findByOrderId(UUID orderId);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Refund r WHERE r.order.id = :orderId AND r.status = 'COMPLETED'")
    BigDecimal calculateTotalRefundedAmount(@Param("orderId") UUID orderId);
}

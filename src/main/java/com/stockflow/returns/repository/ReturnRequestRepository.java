package com.stockflow.returns.repository;

import com.stockflow.returns.domain.ReturnRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, UUID> {
    List<ReturnRequest> findByOrderId(UUID orderId);
    List<ReturnRequest> findByCustomerId(UUID customerId);
    List<ReturnRequest> findBySellerId(UUID sellerId);
}

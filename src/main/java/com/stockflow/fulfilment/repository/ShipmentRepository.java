package com.stockflow.fulfilment.repository;

import com.stockflow.fulfilment.domain.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {
    List<Shipment> findByOrderId(UUID orderId);
    List<Shipment> findBySellerId(UUID sellerId);
}

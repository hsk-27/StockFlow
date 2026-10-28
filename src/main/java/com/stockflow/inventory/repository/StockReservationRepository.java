package com.stockflow.inventory.repository;

import com.stockflow.inventory.domain.ReservationStatus;
import com.stockflow.inventory.domain.StockReservation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

    List<StockReservation> findByOrderId(UUID orderId);

    List<StockReservation> findByOrderIdAndStatus(UUID orderId, ReservationStatus status);

    @Query("SELECT r FROM StockReservation r WHERE r.status = 'PENDING' AND r.expiresAt <= :now")
    List<StockReservation> findExpiredPendingReservations(@Param("now") Instant now, Pageable pageable);
}

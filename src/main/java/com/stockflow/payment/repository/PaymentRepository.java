package com.stockflow.payment.repository;

import com.stockflow.payment.domain.Payment;
import com.stockflow.payment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByOrderId(UUID orderId);
    Optional<Payment> findByProviderReference(String providerReference);
    List<Payment> findByStatus(PaymentStatus status);
    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, Instant before);
}

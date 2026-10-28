package com.stockflow.payment.repository;

import com.stockflow.payment.domain.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, UUID> {
    Optional<PaymentWebhookEvent> findByEventId(String eventId);
    boolean existsByEventId(String eventId);
}

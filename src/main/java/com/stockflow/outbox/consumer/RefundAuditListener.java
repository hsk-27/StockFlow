package com.stockflow.outbox.consumer;

import com.stockflow.audit.service.AuditService;
import com.stockflow.outbox.domain.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RefundAuditListener implements DomainEventListener {

    private static final Logger log = LoggerFactory.getLogger(RefundAuditListener.class);
    private final AuditService auditService;

    public RefundAuditListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public boolean canHandle(String eventType) {
        return "REFUND_COMPLETED".equals(eventType);
    }

    @Override
    public void onEvent(OutboxEvent event) {
        log.info("[Audit Listener] Persisting immutable financial audit entry for Refund ID: {}", event.getAggregateId());
        auditService.recordAction(
                null,
                "REFUND_DISPATCHED",
                "Refund",
                event.getAggregateId(),
                event.getPayload(),
                "SYSTEM_OUTBOX"
        );
    }
}

package com.stockflow.audit.service;

import com.stockflow.audit.domain.AuditLog;
import com.stockflow.audit.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAction(UUID userId, String action, String resourceType, String resourceId, String details, String ipAddress) {
        AuditLog log = new AuditLog(
                UUID.randomUUID(),
                userId,
                action,
                resourceType,
                resourceId,
                details,
                ipAddress
        );
        auditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getRecentLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }
}

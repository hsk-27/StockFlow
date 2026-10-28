package com.stockflow.reporting.controller;

import com.stockflow.audit.domain.AuditLog;
import com.stockflow.audit.service.AuditService;
import com.stockflow.common.domain.PageResponse;
import com.stockflow.reporting.dto.OperationalMetricsResponse;
import com.stockflow.reporting.service.ReportingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Reports", description = "Operational Metrics & Audit Logging Endpoints")
public class ReportingController {

    private final ReportingService reportingService;
    private final AuditService auditService;

    public ReportingController(ReportingService reportingService, AuditService auditService) {
        this.reportingService = reportingService;
        this.auditService = auditService;
    }

    @GetMapping("/metrics")
    @Operation(summary = "Get high-level operational metrics (revenue, status breakdown, stock alerts)")
    public ResponseEntity<OperationalMetricsResponse> getMetrics() {
        return ResponseEntity.ok(reportingService.getOperationalMetrics());
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Inspect security, administrative, and financial audit logs")
    public ResponseEntity<PageResponse<AuditLog>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        return ResponseEntity.ok(PageResponse.from(auditService.getRecentLogs(pageRequest)));
    }
}

package com.stockflow.returns.controller;

import com.stockflow.identity.domain.Role;
import com.stockflow.identity.domain.User;
import com.stockflow.returns.dto.*;
import com.stockflow.returns.service.ReturnsAndRefundsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/returns")
@Tag(name = "Returns & Refunds", description = "Return Processing, Restock Decisions, and Refund Management")
public class ReturnsController {

    private final ReturnsAndRefundsService service;

    public ReturnsController(ReturnsAndRefundsService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    @Operation(summary = "Customer: Request return for a delivered order")
    public ResponseEntity<ReturnResponse> requestReturn(
            @Valid @RequestBody CreateReturnRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.requestReturn(request, user));
    }

    @PostMapping("/{id}/decision")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    @Operation(summary = "Seller: Approve or reject return request")
    public ResponseEntity<ReturnResponse> decideReturn(
            @PathVariable UUID id,
            @Valid @RequestBody ReturnDecisionRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(service.decideReturn(id, request, user));
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    @Operation(summary = "Seller: Receive item and execute restock policy (RESTOCK, QUARANTINE, SCRAP)")
    public ResponseEntity<ReturnResponse> receiveReturn(
            @PathVariable UUID id,
            @Valid @RequestBody ReceiveReturnRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(service.receiveReturn(id, request, user));
    }

    @PostMapping("/refunds")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    @Operation(summary = "Issue refund (validates remaining refundable balance)")
    public ResponseEntity<RefundResponse> issueRefund(
            @Valid @RequestBody IssueRefundRequest request,
            @AuthenticationPrincipal User user) {
        boolean isAdmin = user.getRole() == Role.ADMIN;
        return ResponseEntity.status(HttpStatus.CREATED).body(service.issueRefund(request, user, isAdmin));
    }
}

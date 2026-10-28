package com.stockflow.inventory.controller;

import com.stockflow.common.domain.PageResponse;
import com.stockflow.identity.domain.User;
import com.stockflow.inventory.dto.InventoryMovementResponse;
import com.stockflow.inventory.dto.InventoryResponse;
import com.stockflow.inventory.dto.StockAdjustmentRequest;
import com.stockflow.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "Inventory", description = "Stock Management & Audit Endpoints")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{sku}")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    @Operation(summary = "Get inventory levels for a specific SKU")
    public ResponseEntity<InventoryResponse> getInventoryBySku(@PathVariable String sku) {
        return ResponseEntity.ok(inventoryService.getInventoryBySku(sku));
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    @Operation(summary = "Manually adjust stock on hand with reason code audit")
    public ResponseEntity<InventoryResponse> adjustStock(
            @Valid @RequestBody StockAdjustmentRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(inventoryService.adjustStock(request, user.getEmail()));
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    @Operation(summary = "Get items below specified available threshold")
    public ResponseEntity<List<InventoryResponse>> getLowStockReport(
            @RequestParam(defaultValue = "10") int threshold) {
        return ResponseEntity.ok(inventoryService.getLowStockReport(threshold));
    }

    @GetMapping("/{sku}/movements")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    @Operation(summary = "Get historical inventory movements and reason codes for SKU")
    public ResponseEntity<PageResponse<InventoryMovementResponse>> getMovements(
            @PathVariable String sku,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        return ResponseEntity.ok(PageResponse.from(inventoryService.getMovementHistory(sku, pageRequest)));
    }
}

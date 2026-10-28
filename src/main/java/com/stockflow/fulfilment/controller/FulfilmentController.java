package com.stockflow.fulfilment.controller;

import com.stockflow.fulfilment.dto.CreateShipmentRequest;
import com.stockflow.fulfilment.dto.ShipmentResponse;
import com.stockflow.fulfilment.service.FulfilmentService;
import com.stockflow.identity.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fulfilment")
@Tag(name = "Fulfilment", description = "Seller Order Fulfilment & Carrier Tracking")
public class FulfilmentController {

    private final FulfilmentService fulfilmentService;

    public FulfilmentController(FulfilmentService fulfilmentService) {
        this.fulfilmentService = fulfilmentService;
    }

    @PostMapping("/shipments")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    @Operation(summary = "Seller: Dispatch shipment and provide tracking number for confirmed order")
    public ResponseEntity<ShipmentResponse> createShipment(
            @Valid @RequestBody CreateShipmentRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fulfilmentService.createShipment(request, user));
    }

    @PostMapping("/shipments/{id}/delivered")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    @Operation(summary = "Confirm package delivery by carrier")
    public ResponseEntity<ShipmentResponse> confirmDelivery(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(fulfilmentService.confirmDelivery(id, user));
    }

    @GetMapping("/orders/{orderId}/shipments")
    @Operation(summary = "Get all shipments and tracking numbers for an order")
    public ResponseEntity<List<ShipmentResponse>> getOrderShipments(@PathVariable UUID orderId) {
        return ResponseEntity.ok(fulfilmentService.getShipmentsForOrder(orderId));
    }
}

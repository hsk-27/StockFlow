package com.stockflow.order.controller;

import com.stockflow.common.domain.PageResponse;
import com.stockflow.identity.domain.Role;
import com.stockflow.identity.domain.User;
import com.stockflow.order.dto.CancelOrderRequest;
import com.stockflow.order.dto.CheckoutRequest;
import com.stockflow.order.dto.OrderResponse;
import com.stockflow.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Order Checkout & Lifecycle Orchestration Endpoints")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/checkout")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    @Operation(summary = "Execute idempotent checkout and atomic inventory reservation")
    public ResponseEntity<OrderResponse> checkout(
            @Valid @RequestBody CheckoutRequest request,
            @Parameter(description = "Client-supplied unique idempotency key")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal User user) {

        OrderResponse response = orderService.checkout(request, idempotencyKey, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Get current customer's order history")
    public ResponseEntity<PageResponse<OrderResponse>> getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal User user) {

        PageRequest pageRequest = PageRequest.of(Math.max(0, page), Math.min(50, Math.max(1, size)));
        return ResponseEntity.ok(PageResponse.from(orderService.getCustomerOrders(user, pageRequest)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order details by ID (enforces ownership)")
    public ResponseEntity<OrderResponse> getOrderById(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(orderService.getOrderById(id, user));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel order and release reserved stock")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable UUID id,
            @Valid @RequestBody CancelOrderRequest request,
            @AuthenticationPrincipal User user) {
        boolean isAdmin = user.getRole() == Role.ADMIN;
        return ResponseEntity.ok(orderService.cancelOrder(id, request, user, isAdmin));
    }
}

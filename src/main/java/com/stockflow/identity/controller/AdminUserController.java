package com.stockflow.identity.controller;

import com.stockflow.audit.service.AuditService;
import com.stockflow.common.domain.PageResponse;
import com.stockflow.common.exception.ResourceNotFoundException;
import com.stockflow.identity.domain.Role;
import com.stockflow.identity.domain.User;
import com.stockflow.identity.dto.UpdateUserStatusRequest;
import com.stockflow.identity.dto.UserResponse;
import com.stockflow.identity.repository.UserRepository;
import com.stockflow.order.domain.OrderStatus;
import com.stockflow.order.dto.OrderResponse;
import com.stockflow.order.repository.OrderRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Management", description = "Platform Administration: Users, Orders & Policy")
public class AdminUserController {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final AuditService auditService;

    public AdminUserController(UserRepository userRepository,
                               OrderRepository orderRepository,
                               AuditService auditService) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.auditService = auditService;
    }

    @GetMapping("/users")
    @Operation(summary = "List registered users with pagination")
    public ResponseEntity<PageResponse<UserResponse>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by("createdAt").descending());
        Page<UserResponse> users = userRepository.findAll(pageRequest).map(UserResponse::from);
        return ResponseEntity.ok(PageResponse.from(users));
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Get user account details by ID")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @PatchMapping("/users/{id}/status")
    @Operation(summary = "Activate or deactivate user account")
    public ResponseEntity<UserResponse> updateUserStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserStatusRequest request,
            @AuthenticationPrincipal User adminUser,
            HttpServletRequest servletRequest) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        user.setEnabled(request.enabled());
        userRepository.save(user);

        // Security Audit Log
        auditService.recordAction(
                adminUser.getId(),
                request.enabled() ? "USER_ACTIVATED" : "USER_DEACTIVATED",
                "User",
                user.getId().toString(),
                "Target email: " + user.getEmail() + ", new status: " + request.enabled(),
                servletRequest.getRemoteAddr()
        );

        return ResponseEntity.ok(UserResponse.from(user));
    }

    @GetMapping("/orders")
    @Operation(summary = "Admin: Inspect platform-wide orders with optional status filter")
    public ResponseEntity<PageResponse<OrderResponse>> getPlatformOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by("createdAt").descending());
        Page<OrderResponse> orders;

        if (status != null) {
            orders = orderRepository.findByStatusOrderByCreatedAtDesc(status, pageRequest).map(OrderResponse::from);
        } else {
            orders = orderRepository.findAll(pageRequest).map(OrderResponse::from);
        }

        return ResponseEntity.ok(PageResponse.from(orders));
    }
}

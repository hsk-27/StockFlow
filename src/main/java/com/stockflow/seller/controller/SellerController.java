package com.stockflow.seller.controller;

import com.stockflow.common.domain.PageResponse;
import com.stockflow.identity.domain.User;
import com.stockflow.inventory.dto.InventoryResponse;
import com.stockflow.order.dto.OrderItemResponse;
import com.stockflow.seller.dto.SellerProfileResponse;
import com.stockflow.seller.dto.UpdateSellerProfileRequest;
import com.stockflow.seller.service.SellerService;
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
@RequestMapping("/api/v1/seller")
@PreAuthorize("hasRole('SELLER') or hasRole('ADMIN')")
@Tag(name = "Seller Profile & Inventory", description = "Seller Account, Inventory, and Order Items")
public class SellerController {

    private final SellerService sellerService;

    public SellerController(SellerService sellerService) {
        this.sellerService = sellerService;
    }

    @GetMapping("/profile")
    @Operation(summary = "Get authenticated seller's store profile")
    public ResponseEntity<SellerProfileResponse> getProfile(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(sellerService.getProfile(user));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update store name and business email")
    public ResponseEntity<SellerProfileResponse> updateProfile(
            @Valid @RequestBody UpdateSellerProfileRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(sellerService.updateProfile(request, user));
    }

    @GetMapping("/inventory")
    @Operation(summary = "Get list of all inventory items belonging to authenticated seller")
    public ResponseEntity<PageResponse<InventoryResponse>> getInventory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User user) {
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        return ResponseEntity.ok(PageResponse.from(sellerService.getSellerInventory(user, pageRequest)));
    }

    @GetMapping("/order-items")
    @Operation(summary = "Get all order items sold by this seller")
    public ResponseEntity<List<OrderItemResponse>> getOrderItems(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(sellerService.getSellerOrderItems(user));
    }
}

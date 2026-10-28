package com.stockflow.cart.controller;

import com.stockflow.cart.dto.AddToCartRequest;
import com.stockflow.cart.dto.CartResponse;
import com.stockflow.cart.dto.UpdateCartItemRequest;
import com.stockflow.cart.service.CartService;
import com.stockflow.identity.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
@Tag(name = "Cart", description = "Customer Cart Management Endpoints")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    @Operation(summary = "Get the current authenticated customer's cart")
    public ResponseEntity<CartResponse> getCart(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(cartService.getCart(user));
    }

    @PostMapping("/items")
    @Operation(summary = "Add an item to the current cart")
    public ResponseEntity<CartResponse> addItem(
            @Valid @RequestBody AddToCartRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(cartService.addItem(request, user));
    }

    @PutMapping("/items/{sku}")
    @Operation(summary = "Update quantity of a specific item in the cart")
    public ResponseEntity<CartResponse> updateItem(
            @PathVariable String sku,
            @Valid @RequestBody UpdateCartItemRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(cartService.updateItemQuantity(sku, request, user));
    }

    @DeleteMapping("/items/{sku}")
    @Operation(summary = "Remove an item from the cart")
    public ResponseEntity<CartResponse> removeItem(
            @PathVariable String sku,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(cartService.removeItem(sku, user));
    }
}

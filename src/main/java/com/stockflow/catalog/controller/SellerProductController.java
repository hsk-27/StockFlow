package com.stockflow.catalog.controller;

import com.stockflow.catalog.dto.CreateProductRequest;
import com.stockflow.catalog.dto.ProductResponse;
import com.stockflow.catalog.dto.UpdateProductRequest;
import com.stockflow.catalog.service.CatalogService;
import com.stockflow.identity.domain.Role;
import com.stockflow.identity.domain.User;
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
@RequestMapping("/api/v1/seller/products")
@PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
@Tag(name = "Seller Products", description = "Seller Product Management Endpoints")
public class SellerProductController {

    private final CatalogService catalogService;

    public SellerProductController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @PostMapping
    @Operation(summary = "Seller: Create a new product listing with initial stock")
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(catalogService.createProduct(request, user.getId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Seller: Update product details (requires ownership)")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request,
            @AuthenticationPrincipal User user) {
        boolean isAdmin = user.getRole() == Role.ADMIN;
        return ResponseEntity.ok(catalogService.updateProduct(id, request, user.getId(), isAdmin));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Seller: Deactivate a product listing (requires ownership)")
    public ResponseEntity<Void> deactivateProduct(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        boolean isAdmin = user.getRole() == Role.ADMIN;
        catalogService.deactivateProduct(id, user.getId(), isAdmin);
        return ResponseEntity.noContent().build();
    }
}

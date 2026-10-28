package com.stockflow.catalog.controller;

import com.stockflow.catalog.dto.CategoryRequest;
import com.stockflow.catalog.dto.CategoryResponse;
import com.stockflow.catalog.dto.ProductResponse;
import com.stockflow.catalog.service.CatalogService;
import com.stockflow.common.domain.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Catalog", description = "Public Catalog & Category Browsing Endpoints")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/products")
    @Operation(summary = "Search and filter active products with pagination")
    public ResponseEntity<PageResponse<ProductResponse>> getProducts(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        Sort sort = direction.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), sort);

        return ResponseEntity.ok(PageResponse.from(catalogService.searchCatalog(categoryId, query, pageRequest)));
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "Get single active product details by ID")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable UUID id) {
        return ResponseEntity.ok(catalogService.getProductById(id));
    }

    @GetMapping("/categories")
    @Operation(summary = "Get all active categories")
    public ResponseEntity<List<CategoryResponse>> getCategories() {
        return ResponseEntity.ok(catalogService.getActiveCategories());
    }

    @PostMapping("/admin/categories")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin endpoint: Create new category")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.createCategory(request));
    }
}

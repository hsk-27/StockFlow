package com.stockflow.catalog.service;

import com.stockflow.catalog.domain.Category;
import com.stockflow.catalog.domain.Product;
import com.stockflow.catalog.dto.*;
import com.stockflow.catalog.repository.CategoryRepository;
import com.stockflow.catalog.repository.ProductRepository;
import com.stockflow.common.exception.ConflictException;
import com.stockflow.common.exception.ResourceNotFoundException;
import com.stockflow.common.exception.UnauthorizedException;
import com.stockflow.inventory.domain.Inventory;
import com.stockflow.inventory.domain.InventoryMovement;
import com.stockflow.inventory.repository.InventoryMovementRepository;
import com.stockflow.inventory.repository.InventoryRepository;
import com.stockflow.seller.domain.Seller;
import com.stockflow.seller.domain.SellerStatus;
import com.stockflow.seller.repository.SellerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SellerRepository sellerRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;

    public CatalogService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          SellerRepository sellerRepository,
                          InventoryRepository inventoryRepository,
                          InventoryMovementRepository movementRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.sellerRepository = sellerRepository;
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
    }

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request, UUID sellerUserId) {
        Seller seller = sellerRepository.findByUserId(sellerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller profile not found for user: " + sellerUserId));

        if (seller.getStatus() != SellerStatus.ACTIVE) {
            throw new UnauthorizedException("Seller store is not active. Current status: " + seller.getStatus());
        }

        String normalizedSku = request.initialSku().toUpperCase().trim();
        if (inventoryRepository.findBySku(normalizedSku).isPresent()) {
            throw new ConflictException("SKU already exists: " + normalizedSku);
        }

        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + request.categoryId()));
        }

        Product product = new Product(
                UUID.randomUUID(),
                seller,
                category,
                request.title().trim(),
                request.description(),
                request.price(),
                request.currency() != null ? request.currency() : "USD"
        );
        product = productRepository.save(product);

        // Initialize inventory and movement record
        int initialStock = request.initialStock() != null ? Math.max(0, request.initialStock()) : 0;
        Inventory inventory = new Inventory(
                UUID.randomUUID(),
                normalizedSku,
                product,
                seller,
                initialStock
        );
        inventoryRepository.save(inventory);

        if (initialStock > 0) {
            InventoryMovement movement = new InventoryMovement(
                    UUID.randomUUID(),
                    normalizedSku,
                    initialStock,
                    0,
                    initialStock,
                    "INITIAL_STOCK",
                    "PRODUCT_CREATE",
                    seller.getStoreName()
            );
            movementRepository.save(movement);
        }

        log.info("Created product {} with SKU {} for seller {}", product.getId(), normalizedSku, seller.getStoreName());
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse updateProduct(UUID productId, UpdateProductRequest request, UUID requestingUserId, boolean isAdmin) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        // Broken Object-Level Authorization (BOLA) Protection
        if (!isAdmin && !product.getSeller().getUser().getId().equals(requestingUserId)) {
            throw new UnauthorizedException("You are not authorized to modify another seller's product");
        }

        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + request.categoryId()));
        }

        product.update(
                request.title().trim(),
                request.description(),
                request.price(),
                request.currency(),
                category
        );
        productRepository.save(product);

        log.info("Updated product {}. New price: {}", product.getId(), product.getPrice());
        return ProductResponse.from(product);
    }

    @Transactional
    public void deactivateProduct(UUID productId, UUID requestingUserId, boolean isAdmin) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        if (!isAdmin && !product.getSeller().getUser().getId().equals(requestingUserId)) {
            throw new UnauthorizedException("You are not authorized to modify another seller's product");
        }

        product.setActive(false);
        productRepository.save(product);
        log.info("Deactivated product: {}", productId);
    }

    @Transactional
    public ProductResponse activateProduct(UUID productId, UUID requestingUserId, boolean isAdmin) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        if (!isAdmin && !product.getSeller().getUser().getId().equals(requestingUserId)) {
            throw new UnauthorizedException("You are not authorized to modify another seller's product");
        }

        product.setActive(true);
        productRepository.save(product);
        log.info("Activated product: {}", productId);
        return ProductResponse.from(product);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(UUID productId) {
        Product product = productRepository.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Active product not found: " + productId));
        return ProductResponse.from(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> searchCatalog(UUID categoryId, BigDecimal minPrice, BigDecimal maxPrice, String query, Pageable pageable) {
        return productRepository.searchPublicCatalog(categoryId, minPrice, maxPrice, query, pageable)
                .map(ProductResponse::from);
    }

    // Category Management
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        String slug = request.name().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (categoryRepository.existsByName(request.name()) || categoryRepository.existsBySlug(slug)) {
            throw new ConflictException("Category already exists: " + request.name());
        }

        Category parent = null;
        if (request.parentId() != null) {
            parent = categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent category not found: " + request.parentId()));
        }

        Category category = new Category(
                UUID.randomUUID(),
                request.name().trim(),
                slug,
                request.description(),
                parent
        );
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse updateCategory(UUID categoryId, CategoryRequest request) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));

        Category parent = null;
        if (request.parentId() != null) {
            if (request.parentId().equals(categoryId)) {
                throw new ConflictException("A category cannot be its own parent");
            }
            parent = categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent category not found: " + request.parentId()));
        }

        category.update(request.name().trim(), request.description(), parent);
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public void deactivateCategory(UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));
        category.setActive(false);
        categoryRepository.save(category);
        log.info("Deactivated category: {}", categoryId);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getActiveCategories() {
        return categoryRepository.findByActiveTrue().stream()
                .map(CategoryResponse::from)
                .toList();
    }
}

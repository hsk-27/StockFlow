package com.stockflow.cart.service;

import com.stockflow.cart.domain.Cart;
import com.stockflow.cart.domain.CartItem;
import com.stockflow.cart.dto.*;
import com.stockflow.cart.repository.CartItemRepository;
import com.stockflow.cart.repository.CartRepository;
import com.stockflow.catalog.domain.Product;
import com.stockflow.common.domain.Money;
import com.stockflow.common.exception.BusinessRuleViolationException;
import com.stockflow.common.exception.ResourceNotFoundException;
import com.stockflow.identity.domain.User;
import com.stockflow.inventory.domain.Inventory;
import com.stockflow.inventory.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final InventoryRepository inventoryRepository;

    public CartService(CartRepository cartRepository,
                       CartItemRepository cartItemRepository,
                       InventoryRepository inventoryRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional
    public Cart getOrCreateCart(User user) {
        return cartRepository.findByUserId(user.getId())
                .orElseGet(() -> cartRepository.save(new Cart(UUID.randomUUID(), user)));
    }

    @Transactional
    public CartResponse getCart(User user) {
        Cart cart = getOrCreateCart(user);
        return buildCartResponse(cart);
    }

    @Transactional
    public CartResponse addItem(AddToCartRequest request, User user) {
        String sku = request.sku().toUpperCase().trim();
        Inventory inventory = inventoryRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("SKU not found in inventory: " + sku));

        Product product = inventory.getProduct();
        if (!product.isActive()) {
            throw new BusinessRuleViolationException("Product '" + product.getTitle() + "' is currently inactive");
        }

        Cart cart = getOrCreateCart(user);
        CartItem cartItem = cartItemRepository.findByCartIdAndSku(cart.getId(), sku)
                .orElse(null);

        if (cartItem != null) {
            cartItem.incrementQuantity(request.quantity());
            cartItemRepository.save(cartItem);
        } else {
            CartItem newItem = new CartItem(UUID.randomUUID(), cart, product, sku, request.quantity());
            cartItemRepository.save(newItem);
            cart.getItems().add(newItem);
        }
        cart.touch();
        cartRepository.save(cart);

        return buildCartResponse(cart);
    }

    @Transactional
    public CartResponse updateItemQuantity(String sku, UpdateCartItemRequest request, User user) {
        String normalizedSku = sku.toUpperCase().trim();
        Cart cart = getOrCreateCart(user);

        CartItem item = cartItemRepository.findByCartIdAndSku(cart.getId(), normalizedSku)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found in cart: " + normalizedSku));

        item.setQuantity(request.quantity());
        cartItemRepository.save(item);
        cart.touch();
        cartRepository.save(cart);

        return buildCartResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(String sku, User user) {
        String normalizedSku = sku.toUpperCase().trim();
        Cart cart = getOrCreateCart(user);

        CartItem item = cartItemRepository.findByCartIdAndSku(cart.getId(), normalizedSku)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found in cart: " + normalizedSku));

        cart.getItems().remove(item);
        cartItemRepository.delete(item);
        cart.touch();
        cartRepository.save(cart);

        return buildCartResponse(cart);
    }

    @Transactional
    public void clearCart(UUID cartId) {
        cartItemRepository.deleteByCartId(cartId);
    }

    private CartResponse buildCartResponse(Cart cart) {
        List<CartItemResponse> itemResponses = new ArrayList<>();
        Money total = Money.zero("USD");
        int totalQuantity = 0;

        for (CartItem item : cart.getItems()) {
            Product product = item.getProduct();
            BigDecimal unitPrice = product.getPrice();
            String currency = product.getCurrency();

            Money itemTotal = Money.of(unitPrice, currency).multiply(item.getQuantity());
            total = total.add(itemTotal);
            totalQuantity += item.getQuantity();

            itemResponses.add(new CartItemResponse(
                    item.getId(),
                    product.getId(),
                    item.getSku(),
                    product.getTitle(),
                    unitPrice,
                    currency,
                    item.getQuantity(),
                    itemTotal.amount()
            ));
        }

        return new CartResponse(
                cart.getId(),
                cart.getUser().getId(),
                itemResponses,
                total.amount(),
                total.currency(),
                totalQuantity
        );
    }
}

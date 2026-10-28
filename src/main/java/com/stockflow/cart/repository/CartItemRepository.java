package com.stockflow.cart.repository;

import com.stockflow.cart.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {
    Optional<CartItem> findByCartIdAndSku(UUID cartId, String sku);
    void deleteByCartId(UUID cartId);
}

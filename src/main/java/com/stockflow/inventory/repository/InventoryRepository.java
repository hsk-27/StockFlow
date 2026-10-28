package com.stockflow.inventory.repository;

import com.stockflow.inventory.domain.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findBySku(String sku);

    Optional<Inventory> findByProductId(UUID productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.sku = :sku")
    Optional<Inventory> findBySkuWithLock(@Param("sku") String sku);

    /**
     * Locks inventory rows ordered strictly ascending by SKU to guarantee deadlock prevention
     * across concurrent multi-item checkouts.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.sku IN :skus ORDER BY i.sku ASC")
    List<Inventory> findBySkuInOrderBySkuAscWithLock(@Param("skus") List<String> skus);

    @Query("SELECT i FROM Inventory i WHERE (i.onHand - i.reserved) <= :threshold")
    List<Inventory> findLowStock(@Param("threshold") int threshold);

    Page<Inventory> findBySellerId(UUID sellerId, Pageable pageable);
}

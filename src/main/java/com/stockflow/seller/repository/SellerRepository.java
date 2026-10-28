package com.stockflow.seller.repository;

import com.stockflow.seller.domain.Seller;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SellerRepository extends JpaRepository<Seller, UUID> {
    Optional<Seller> findByUserId(UUID userId);
    Optional<Seller> findByUserEmail(String email);
    boolean existsByStoreName(String storeName);
}

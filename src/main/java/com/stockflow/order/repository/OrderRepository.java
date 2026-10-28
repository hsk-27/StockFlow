package com.stockflow.order.repository;

import com.stockflow.order.domain.Order;
import com.stockflow.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findByOrderNumber(String orderNumber);
    Page<Order> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);
    Page<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);
}

package com.stockflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ============================================================================
 * StockFlow — High-Concurrency Multi-Vendor Commerce & Orchestration Engine
 * ============================================================================
 * Architecture: Modular Monolith with PostgreSQL ACID Single Source of Truth
 * 
 * Key Engineering Guarantees:
 *  1. Zero Overselling Invariant: Ascending SKU lock ordering (Dijkstra's Hierarchy)
 *     coupled with PostgreSQL check constraints (reserved <= on_hand).
 *  2. Checkout Idempotency: SHA-256 payload hashing & state caching.
 *  3. Non-Blocking Gateway Boundary: Decoupled DB transactions from HTTP I/O.
 *  4. Transactional Outbox: Event publishing via FOR UPDATE SKIP LOCKED.
 * 
 * @author StockFlow Platform Engineering
 * @version 1.0.0
 */
@SpringBootApplication
@EnableScheduling
public class StockFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockFlowApplication.class, args);
    }
}

# StockFlow — Multi-Vendor Commerce, Inventory & Order Orchestration Engine
### 🎓 Senior Computer Science Capstone & Honours Portfolio Project

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3.3.5](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-yellow.svg)](https://opensource.org/licenses/Apache-2.0)
[![Automated Tests](https://img.shields.io/badge/tests-63%2F63%20passing-brightgreen.svg)]()
[![Code Quality](https://img.shields.io/badge/quality-production--grade-blue.svg)]()

> **Project Abstract:** StockFlow is an enterprise-grade multi-vendor commerce, inventory, order orchestration, payment, and fulfilment backend engineered as a modular monolith in **Java 21** and **Spring Boot 3.3.5**, backed by **PostgreSQL 16** as the authoritative single source of truth. Designed and defended as an undergraduate Computer Science capstone project, it models and solves the most notorious distributed consistency challenges in modern commerce without relying on superficial CRUD simplifications.

---

### 🌐 Interactive Showcase & Testing Tools
- **Web Portfolio Dashboard:** Visit `http://localhost:8080/` when the app runs for an interactive landing page featuring pre-seeded credentials, Swagger UI links, and health metrics.
- **OpenAPI 3.0 & Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **Actuator Health & Prometheus Scrape:** `http://localhost:8080/actuator/health` & `http://localhost:8080/actuator/prometheus`
- **Executable HTTP Client Suite:** Open [`requests.http`](file:///C:/Users/Dell/.gemini/antigravity/scratch/stockflow/requests.http) in VS Code (REST Client extension) or IntelliJ IDEA HTTP Client to run all 11 lifecycle workflows end-to-end with one click!

---

## 1. System Overview & Portfolio Architecture

StockFlow solves the core consistency and reliability failure modes of distributed commerce systems:
- **Zero Overselling Invariant**: High-concurrency inventory reservation engine using sorted row locks (`ORDER BY sku ASC FOR UPDATE`) coupled with relational check constraints (`chk_inventory_quantities`).
- **Checkout Idempotency**: Prevents duplicate orders across mobile dropouts or retried requests using request SHA-256 payload fingerprinting and cached outcome playback.
- **Decoupled Payment Boundary**: Database transactions are never held open across external third-party HTTP gateway calls. Uncertain outcomes are modeled explicitly and reconciled asynchronously.
- **Historical Immutability**: Orders preserve historical snapshots of title, SKU, unit price, quantity, discount, and currency, immunizing financial history against catalog price modifications.
- **Transactional Outbox Worker**: Asynchronous event publishing utilizing PostgreSQL `FOR UPDATE SKIP LOCKED` and exponential backoff retry policies.

```mermaid
graph TD
    Client[Web & Mobile Clients] -->|REST API + JWT| GW[Spring Security & Filter Chain]
    GW --> CartCtrl[Cart & Checkout API]
    GW --> CatCtrl[Catalog & Seller API]
    GW --> PayCtrl[Payment & Webhook API]
    GW --> FulCtrl[Fulfilment & Returns API]
    
    subgraph Modular Monolith Domain Layer
        CartSvc[Cart Service]
        OrderSvc[Order Orchestration Service]
        InvSvc[Inventory Engine]
        PaySvc[Payment Service]
        OutboxSvc[Transactional Outbox Service]
    end

    subgraph Background Workers
        WorkerExpiry[Reservation Expiry Worker]
        WorkerRecon[Payment Reconciliation Worker]
        WorkerOutbox[Outbox Poller SKIP LOCKED]
    end

    CartCtrl --> CartSvc
    CartCtrl --> OrderSvc
    OrderSvc --> InvSvc
    OrderSvc --> OutboxSvc
    PayCtrl --> PaySvc
    PaySvc --> InvSvc

    WorkerExpiry --> InvSvc
    WorkerRecon --> PaySvc
    WorkerOutbox --> OutboxSvc

    subgraph PostgreSQL 16
        DB[(Authoritative ACID Datastore)]
    end

    OrderSvc --> DB
    InvSvc --> DB
    PaySvc --> DB
    OutboxSvc --> DB
```

---

## 2. Architectural Decision Records (ADRs)

### ADR 001: Modular Monolith vs Distributed Microservices
- **Context:** Complex multi-vendor commerce requires strong data consistency across inventory, orders, and payments.
- **Decision:** Build a modular monolith in a single Spring Boot application rather than prematurely distributing services across network boundaries.
- **Consequences:** Eliminates distributed two-phase commit overhead, network partitions, and dual-write anomalies while maintaining strict package-by-feature domain boundaries.

### ADR 002: Deterministic Ascending SKU Locking for Concurrency
- **Context:** Multi-item checkouts locking multiple inventory records concurrently can produce deadlocks if transactions lock records in differing sequences (e.g., Thread 1 locks A then B; Thread 2 locks B then A).
- **Decision:** Sort all SKU identifiers alphabetically before executing `SELECT ... FOR UPDATE`.
- **Consequences:** Guaranteed deadlock-free lock acquisition across multi-item checkout transactions.

### ADR 003: Non-Blocking Payment Gateway Boundaries
- **Context:** Holding an ACID transaction open during external HTTP calls exhausts connection pools and causes cascading outages under gateway latency spikes.
- **Decision:** Split checkout payment into three decoupled steps:
  1. Transaction 1: Create Order (`PENDING_PAYMENT`) and reserve inventory. Commit.
  2. External Gateway I/O: Invoke `PaymentProvider.initiatePayment(...)` without open DB transactions.
  3. Transaction 2: Record provider outcome (`SUCCEEDED`, `FAILED`, or `UNCERTAIN`). Commit.
- **Consequences:** Database connection holding time drops from seconds to under 5ms per transaction.

### ADR 004: Transactional Outbox with `FOR UPDATE SKIP LOCKED`
- **Context:** Outbox tables need concurrent polling by multiple application instances without lock contention or duplicate message dispatch.
- **Decision:** Query pending outbox records using `SELECT ... FOR UPDATE SKIP LOCKED`.
- **Consequences:** Thread-safe parallel claiming across horizontal worker instances with zero deadlocks.

---

## 3. Technology Stack & Pinned Versions

| Component | Pinned Version | Purpose |
|---|---|---|
| **Java** | 21 (OpenJDK / Temurin) | Virtual Threads, Pattern Matching, Records |
| **Spring Boot** | 3.3.5 | Core Framework, Security, Validation |
| **Database** | PostgreSQL 16 | Relational ACID, Row Locks, JSONB, UUIDs |
| **Migrations** | Flyway 10.x | Version-controlled DDL migrations |
| **Security** | JJWT 0.12.6 + BCrypt | Stateless JWT Bearer Auth & Refresh Tokens |
| **API Docs** | SpringDoc OpenAPI 2.6.0 | OpenAPI 3.0 Specification & Swagger UI |
| **Observability**| Actuator + Micrometer | `/actuator/health`, `/actuator/metrics` |
| **Testing** | JUnit 5, Mockito, Testcontainers | Unit, Integration, and Concurrency suites |

---

## 4. Domain State Machines

### 4.1 Order Lifecycle

```mermaid
stateDiagram-v2
    [*] --> PENDING_PAYMENT: Checkout Executed
    PENDING_PAYMENT --> CONFIRMED: Payment Succeeded
    PENDING_PAYMENT --> CANCELLED: Payment Failed / Buyer Cancelled
    PENDING_PAYMENT --> EXPIRED: Reservation Timeout (Worker)
    
    CONFIRMED --> PROCESSING: Seller Packaging
    CONFIRMED --> SHIPPED: Direct Dispatch
    CONFIRMED --> CANCELLED: Pre-fulfilment Cancellation
    
    PROCESSING --> SHIPPED: Carrier Dispatched
    SHIPPED --> DELIVERED: Carrier Confirmed Delivery
    
    DELIVERED --> REFUNDED: Full Refund Issued
    CANCELLED --> [*]
    EXPIRED --> [*]
    REFUNDED --> [*]
```

### 4.2 Payment State Machine & Recovery

```mermaid
stateDiagram-v2
    [*] --> PENDING: Intent Registered
    PENDING --> SUCCEEDED: Gateway 200 OK / Signed Webhook
    PENDING --> FAILED: Gateway Decline
    PENDING --> UNCERTAIN: Timeout / Socket Disconnect
    
    UNCERTAIN --> SUCCEEDED: Reconciled via Provider Query API
    UNCERTAIN --> FAILED: Reconciled Provider Failure
    
    SUCCEEDED --> REFUNDED: Full Refund Issued
```

---

## 5. Database Schema & Invariants

Flyway manages migrations under `src/main/resources/db/migration/`:
- `V1__initial_schema.sql`: Authoritative schema DDL.
- `V2__seed_data.sql`: Development seed fixtures.

### Key Relational Invariants Enforced in PostgreSQL:
1. `chk_inventory_quantities`: `CHECK (on_hand >= 0 AND reserved >= 0 AND reserved <= on_hand)`
2. `uq_cart_sku`: `UNIQUE (cart_id, sku)`
3. `uq_idempotency_user_key`: `UNIQUE (idempotency_key, user_id)`
4. `uq_payment_webhook_event`: `UNIQUE (event_id)`
5. `chk_order_total`: `CHECK (total_amount >= 0)`
6. `chk_refund_amount`: `CHECK (amount > 0)`

---

## 6. REST API Reference (`/api/v1`)

### 6.1 Authentication & User Management (`/api/v1/auth`, `/api/v1/admin`)
- `POST /api/v1/auth/register`: Register user (`CUSTOMER` or `SELLER`).
- `POST /api/v1/auth/login`: Authenticate and receive Access + Refresh tokens. (Rate-limited via sliding window IP bucket).
- `POST /api/v1/auth/refresh`: Rotate tokens. (Includes automatic token reuse detection).
- `POST /api/v1/auth/logout`: Revoke active refresh token.
- `GET /api/v1/admin/users`: Admin list all users with pagination.
- `GET /api/v1/admin/users/{id}`: Admin inspect user details.
- `PATCH /api/v1/admin/users/{id}/status`: Admin activate/deactivate user accounts with security audit log.
- `GET /api/v1/admin/orders`: Admin platform-wide order inspection.

### 6.2 Catalog & Seller (`/api/v1`)
- `GET /api/v1/products`: Search public catalog (`categoryId`, `query`, pagination).
- `GET /api/v1/products/{id}`: Single product view.
- `GET /api/v1/categories`: List active categories.
- `POST /api/v1/seller/products`: Create seller product listing with initial stock.
- `PUT /api/v1/seller/products/{id}`: Update product (enforces seller ownership).
- `DELETE /api/v1/seller/products/{id}`: Deactivate product listing.
- `GET /api/v1/seller/profile`: Inspect seller store profile.
- `PUT /api/v1/seller/profile`: Update store name and business email.
- `GET /api/v1/seller/inventory`: List inventory items owned by authenticated seller.
- `GET /api/v1/seller/order-items`: Query order items sold by authenticated seller.
- `POST /api/v1/admin/categories`: Administrator category creation.

### 6.3 Inventory (`/api/v1/inventory`)
- `GET /api/v1/inventory/{sku}`: Query stock levels (`onHand`, `reserved`, `available`).
- `POST /api/v1/inventory/adjust`: Manual adjustment with audit reason code (`RESTOCK`, `AUDIT`).
- `GET /api/v1/inventory/low-stock`: Report items below threshold.
- `GET /api/v1/inventory/{sku}/movements`: Historical audit trail for SKU.

### 6.4 Cart & Checkout (`/api/v1`)
- `GET /api/v1/cart`: View current customer cart with dynamically calculated prices.
- `POST /api/v1/cart/items`: Add item to cart.
- `PUT /api/v1/cart/items/{sku}`: Update item quantity.
- `DELETE /api/v1/cart/items/{sku}`: Remove item.
- `POST /api/v1/orders/checkout`: Idempotent checkout (Header: `Idempotency-Key: <UUID>`).
- `GET /api/v1/orders`: Customer order history.
- `GET /api/v1/orders/{id}`: Order details (enforces ownership).
- `POST /api/v1/orders/{id}/cancel`: Customer pre-fulfilment cancellation.

### 6.5 Payments & Webhooks (`/api/v1/payments`)
- `POST /api/v1/payments/initiate`: Initiate non-blocking payment for order.
- `POST /api/v1/payments/webhooks`: Ingest webhook (Header: `X-Webhook-Signature`).

### 6.6 Fulfilment & Returns (`/api/v1`)
- `POST /api/v1/fulfilment/shipments`: Seller dispatches shipment with carrier tracking.
- `POST /api/v1/fulfilment/shipments/{id}/delivered`: Confirm package delivery.
- `POST /api/v1/returns`: Customer requests return for delivered order.
- `POST /api/v1/returns/{id}/decision`: Seller approves or rejects return.
- `POST /api/v1/returns/{id}/receive`: Seller executes restock policy (`RESTOCK`, `QUARANTINE`, `SCRAP`).
- `POST /api/v1/returns/refunds`: Issue refund (enforces remaining refundable balance limit).

### 6.7 Admin & Reporting (`/api/v1/admin/reports`)
- `GET /api/v1/admin/reports/metrics`: Operational metrics (revenue, status breakdown, stock alerts).
- `GET /api/v1/admin/reports/audit-logs`: Inspect administrative and financial audit records.

### 6.8 Conversational AI Chatbot (`/api/v1/chat`)
- `POST /api/v1/chat/message`: Context-aware conversational endpoint for **all user personas**:
  - **Guest:** Product search, categories, store policies, how to register.
  - **Customer:** Real-time order lookup, carrier tracking, cancellation eligibility, and cart inspections with strict BOLA isolation.
  - **Seller:** Inventory health, low-stock threshold alerts, pending return requests, and store listing oversight.
  - **Admin:** Real-time platform revenue metrics, executive audit trail inspection, and transactional outbox queue telemetry.
- `GET /api/v1/chat/conversations`: List past conversation sessions for session ID or authenticated user.
- `GET /api/v1/chat/conversations/{id}`: Retrieve message history for a conversation.
- `DELETE /api/v1/chat/conversations/{id}`: Delete a conversation thread.

---

## 7. Local Setup & Docker Deployment

### Prerequisites
- Java 21 JDK
- Apache Maven 3.9+
- Docker & Docker Compose

### 7.1 Start with Docker Compose
```bash
docker compose up -d
```
This launches:
1. `stockflow-postgres`: PostgreSQL 16 on port `5432` with automated health checks.
2. `stockflow-app`: Multi-stage Docker container running Spring Boot on port `8080`.

Verify health:
```bash
curl http://localhost:8080/actuator/health
```

### 7.2 Run Locally via Maven Wrapper
```bash
# 1. Start PostgreSQL (e.g. via Docker Compose or local service)
docker compose up -d postgres

# 2. Copy environment template
cp .env.example .env

# 3. Run automated tests
./mvnw clean verify    # Windows: .\mvnw.cmd clean verify

# 4. Start application
./mvnw spring-boot:run # Windows: .\mvnw.cmd spring-boot:run
```

### 7.3 GitHub Deployment & CI/CD Pipeline
The repository is fully configured for continuous integration and automated container deployment via GitHub Actions:
- **Continuous Integration (`.github/workflows/ci.yml`)**: Automatically triggers on pushes and PRs to `main`/`master`. Sets up Java 21, launches a PostgreSQL 16 service container, runs `./mvnw clean verify`, tests multi-stage Docker image compilation, and archives test reports.
- **Container Publishing (`.github/workflows/deploy.yml`)**: Automatically packages and publishes multi-platform Docker container images to GitHub Container Registry (`ghcr.io`) upon tag releases (`v*.*.*`).
- **Environment Management (`.env.example`)**: Production and staging deployments can configure secrets, Hikari connection pool limits, CORS origin patterns, and background worker cron frequencies without modifying code.

### 7.4 Seed Data Credentials
| Role | Email | Password |
|---|---|---|
| **ADMIN** | `admin@stockflow.com` | `Password123!` |
| **SELLER** | `seller@stockflow.com` | `Password123!` |
| **CUSTOMER** | `customer@stockflow.com` | `Password123!` |

---

## 8. Verification & Concurrency Test Results

The project test suite validates 42 automated test cases covering domain invariants, concurrency, idempotency, horizontal/vertical privilege escalation, and full lifecycle commerce workflows.

### 8.1 Concurrent Last-Unit Purchase Demonstration
- **Scenario:** Initial inventory for `SKU-HOT-ITEM-001` has exactly **1 unit** on hand (`on_hand = 1, reserved = 0, available = 1`).
- **Execution:** Two threads release simultaneously against `reserveStockForOrder()` using a `CountDownLatch` gate.
- **Observed Outcome:**
  - Thread A successfully acquires the lock and reserves 1 unit (status 201).
  - Thread B attempts reservation and is rejected with `BusinessRuleViolationException: Insufficient stock for SKU SKU-HOT-ITEM-001. Available: 0, requested: 1`.
- **Persisted Invariants:**
  - `on_hand`: `1`
  - `reserved`: `1`
  - `available`: `0` (Never negative. Overselling is mathematically prevented).

### 8.2 Payment Uncertainty & Reconciliation
- When the payment gateway experiences socket timeouts or connection aborts:
  - The payment is marked `UNCERTAIN`.
  - The order remains `PENDING_PAYMENT`.
  - Reserved inventory is **not** prematurely released.
  - The `PaymentReconciliationWorker` queries the provider query API on a recurring schedule and updates the transaction to `SUCCEEDED` or `FAILED` once authoritative status is confirmed.

---

## 9. Runbook & Troubleshooting

| Symptom | Cause | Remedy |
|---|---|---|
| `409 Conflict` on checkout | Duplicate request while previous request is `PROCESSING` | Normal idempotency defense. Retry request with original key once finished. |
| `422 Unprocessable Entity` on checkout | Reusing an existing `Idempotency-Key` with altered payload | Generate a fresh UUID `Idempotency-Key` for new order payloads. |
| Order stuck in `PENDING_PAYMENT` | Payment dropped or network timed out | Run payment reconciliation or let `ReservationExpiryWorker` auto-expire after TTL. |
| `403 Forbidden` on Seller Product Update | Broken Object-Level Authorization check triggered | Ensure authenticated seller owns the product listing (`seller_id` matches). |
| Outbox event delivery retry | Transient consumer network failure | Poller retries with exponential backoff up to 5 attempts before marking `FAILED`. |

---

## 10. Known Limitations & Future Roadmap
1. **Distributed Caching:** In-memory caching for hot catalog reads can be introduced via Redis once cache-invalidation benchmarks justify the operational complexity.
2. **External Gateway Sandbox:** A Stripe / Adyen sandbox adapter can be plugged directly into the `PaymentProvider` interface without altering core commerce orchestration.
3. **Partial Returns:** The initial implementation processes full return items per seller order item; granular unit-level partial returns can be layered on the existing refund balance tracking.

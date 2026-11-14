# StockFlow &bull; Comprehensive Testing & Verification Strategy

### 73 Automated Tests across 22 Test Classes (100% Pass Rate)

StockFlow implements a multi-tiered test pyramid ensuring that financial, concurrency, and security invariants cannot be violated.

```
       /\
      /  \     End-to-End Workflows (5 tests)
     /----\    Concurrency & Race Stress Tests (2 tests)
    /------\   Integration Tests & Web Layer (12 tests)
   /--------\  Domain, State Machine & Service Tests (54 tests)
```

---

## 1. Test Suite Directory

| Category | Test Class | Tests | Key Focus / Invariants Verified |
|---|---|---|---|
| **Catalog** | `CatalogServiceTest` | 4 | Product creation, seller authorization, store deactivation, price validation |
| **Conversational AI** | `ChatbotServiceTest` | 8 | Multi-persona routing (Customer, Seller, Admin, Guest), stopword parsing, budget extraction, BOLA isolation |
| **Chat Web** | `ChatControllerIntegrationTest` | 4 | REST endpoint invocation, session tracking, conversation persistence |
| **Common / Errors** | `GlobalExceptionHandlerTest` | 4 | RFC 7807 problem detail mappings (403 Forbidden, 401 Unauthorized, 404 Not Found, 422 Unprocessable Entity) |
| **Concurrency** | `ConcurrentCheckoutIntegrationTest` | 1 | Simultaneous checkouts competing for stock with 0 overselling |
| **Concurrency Load** | `HighConcurrencyLoadTest` | 1 | 50 concurrent buyer threads competing for 1 last available item using `CountDownLatch` |
| **Identity & Security**| `AuthServiceTest` | 5 | BCrypt password verification, token rotation, token reuse revocation |
| **Access Control** | `SecurityAccessControlIntegrationTest` | 2 | Horizontal and vertical privilege escalation defense, BOLA enforcement |
| **Inventory Domain** | `InventoryDomainTest` | 7 | Relational quantity arithmetic, check constraints, reservation mechanics |
| **Inventory Service** | `InventoryServiceTest` | 5 | Deadlock-free sorted SKU lock ordering, stock adjustments, movement audits |
| **Order Domain** | `OrderStateMachineTest` | 6 | State transition matrix enforcement, illegal transition rejection |
| **Order Idempotency** | `IdempotencyServiceTest` | 5 | SHA-256 fingerprinting, concurrent race detection, cached response playback |
| **Order Workers** | `ReservationExpiryWorkerTest` | 2 | Asynchronous sweep of expired reservations and order transitions |
| **Outbox System** | `OutboxPollerWorkerTest` | 2 | `SKIP LOCKED` batch polling, exponential backoff retries, dead letter status |
| **Order Events** | `OrderNotificationEventListenerTest` | 2 | Asynchronous decoupling between order events and notification listeners |
| **Payment Provider** | `MockPaymentProviderTest` | 4 | Non-blocking execution, network drop simulation, webhook signature generation |
| **Payment Service** | `PaymentServiceTest` | 5 | 3-step orchestration, UNCERTAIN reconciliation worker, HMAC deduplication |
| **Reporting / Metrics**| `ReportingServiceTest` | 1 | Native database query aggregations, status distribution, GMV calculations |
| **Returns & Refunds** | `ReturnsAndRefundsServiceTest` | 5 | 30-day window check, seller inspection dispositioning (`RESTOCK`, `QUARANTINE`, `SCRAP`), balance caps |
| **Fulfilment** | `FulfilmentServiceTest` | 3 | Carrier tracking assignment, delivery confirmation, seller ownership checks |
| **End-to-End** | `EndToEndCommerceWorkflowTest` | 1 | Full multi-step lifecycle: Register -> Browse -> Cart -> Checkout -> Pay -> Ship -> Deliver -> Return -> Refund |
| **Full Integration** | `FullCommerceIntegrationTest` | 2 | Spring Boot application context boot, Flyway migrations V1-V4, Actuator health probe verification |

**Total:** **73 Tests | 0 Failures | 0 Errors | 0 Skipped**

---

## 2. High-Concurrency Race Condition Demonstration

In `HighConcurrencyLoadTest`:
```java
int threadCount = 50;
CountDownLatch startGate = new CountDownLatch(1);
CountDownLatch endGate = new CountDownLatch(threadCount);
```
- **Scenario:** 50 threads release concurrently at the exact same millisecond against `reserveStockForOrder()` for a product with only **1 unit** available.
- **Result:**
  - Exactly **1 thread** succeeds and reserves the unit (status 201).
  - The other **49 threads** receive `BusinessRuleViolationException: Insufficient stock`.
  - Database state remains strictly consistent: `on_hand = 1, reserved = 1, available = 0`. Zero overselling.

---

## 3. How to Execute Tests Locally

Run the complete test suite:
```bash
./mvnw clean test          # Linux / macOS
.\mvnw.cmd clean test      # Windows PowerShell
```

Run a specific test category:
```bash
.\mvnw.cmd test -Dtest=HighConcurrencyLoadTest
.\mvnw.cmd test -Dtest=ReportingServiceTest
```

# Contributing to StockFlow

Thank you for your interest in contributing to **StockFlow**!

StockFlow is designed as a production-grade, modular monolith e-commerce backend built with Java 21, Spring Boot 3.3.5, and PostgreSQL 16. To preserve system invariants, high concurrency guarantees, and clean architectural boundaries, please review these guidelines before submitting a pull request.

---

## 1. Development Principles & Invariants

All contributions must adhere to the core architectural invariants of StockFlow:
1. **Zero Overselling Invariant**: Never reserve or deduct inventory without deterministic row locks (`ORDER BY sku ASC FOR UPDATE`) and database constraint compliance (`chk_inventory_quantities`).
2. **Checkout Idempotency**: All mutating checkout and payment endpoints must support idempotency via the `Idempotency-Key` header and payload hashing.
3. **Decoupled Payment Boundaries**: Never hold an ACID database transaction open across external HTTP gateway communications.
4. **Historical Immutability**: Order item records must remain historical snapshots; do not query catalog tables dynamically when viewing past orders.
5. **Transactional Outbox**: All domain events must be saved to `outbox_events` in the same transactional boundary as the domain state mutation.
6. **Package-by-Feature Boundaries**: Do not introduce circular dependencies between modules. Use application services, event listeners, or interfaces where appropriate.

---

## 2. Setting Up Local Development

### Prerequisites
- **Java 21** (JDK 21+)
- **Docker & Docker Compose** (for PostgreSQL 16)
- Git

### Initial Setup
```bash
# Clone the repository
git clone https://github.com/your-username/stockflow.git
cd stockflow

# Copy environment variables template
cp .env.example .env

# Start PostgreSQL container
docker compose up -d postgres

# Run automated test suite with Maven Wrapper
./mvnw clean verify

# Start the application
./mvnw spring-boot:run
```

---

## 3. Branching & Commit Conventions

### Branch Naming
- `feat/feature-name` (e.g. `feat/stripe-provider`)
- `fix/bug-description` (e.g. `fix/cart-quantity-overflow`)
- `docs/documentation-update` (e.g. `docs/api-specs`)
- `refactor/subsystem-name` (e.g. `refactor/outbox-poller`)

### Commit Messages
We follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:
```
<type>(<scope>): <short description>

[optional body explaining motivation and context]

[optional footer(s)]
```
*Examples:*
- `feat(inventory): add low-stock threshold alert triggers`
- `fix(order): prevent cancellation transition when order is SHIPPED`
- `test(concurrency): add high-contention checkout stress test`

---

## 4. Code Quality & Testing Expectations

- **Automated Tests**: Every new feature or bug fix must include JUnit 5 unit or integration tests.
- **Run Full Verification**: Run `./mvnw clean verify` before submitting your PR.
- **No Direct Entity Leaks**: REST controllers must consume and return DTO records, never JPA `@Entity` instances.
- **Exception Handling**: Use domain exceptions mapped by `GlobalExceptionHandler` following RFC 7807 Problem Details.
- **Database Migrations**: Never modify existing Flyway migration files (`V1`, `V2`, etc.). Add new migrations sequentially (`V3__...sql`).

---

## 5. Pull Request Process

1. Fork the repo and create your branch from `main`.
2. Ensure all 42+ automated tests pass with `./mvnw clean verify`.
3. Push to your fork and submit a Pull Request against `main`.
4. Fill out the PR template describing the change, testing steps, and architectural impact.
5. Wait for the automated GitHub Actions CI pipeline to verify the build.

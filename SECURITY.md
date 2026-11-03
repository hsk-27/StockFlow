# Security Policy

StockFlow implements defense-in-depth security principles across authentication, authorization, data isolation, and cryptographic integrity.

---

## 1. Supported Versions

Security updates and patches are applied to the active development branches:

| Version | Supported |
|---------|-----------|
| 1.0.x   | Yes       |
| < 1.0   | No        |

---

## 2. Security Architecture & Controls

StockFlow is engineered with built-in defenses against common vulnerability classes:
- **Stateless Authentication**: JJWT with short-lived access tokens (15 minutes).
- **Refresh Token Family Rotation**: Cryptographically hashed refresh tokens with automatic token reuse detection (revokes all active sessions if a compromised token is replayed).
- **Broken Object-Level Authorization (BOLA/IDOR)**: Explicit ownership verification ensures customers cannot access peer orders and sellers cannot modify peer inventory or listings.
- **Brute Force Throttling**: Sliding-window in-memory IP rate limiter on authentication endpoints.
- **Payment Signature Verification**: HMAC-SHA256 signature verification for webhook callbacks.
- **Information Disclosure Prevention**: RFC 7807 problem details sanitize internal stack traces and database errors.
- **SQL Injection Prevention**: All persistence operations utilize parameterized JPA queries and Hibernate ORM.

---

## 3. Reporting a Vulnerability

If you discover a security vulnerability within StockFlow, please do not open a public GitHub issue.

Instead, please report security concerns via:
1. **GitHub Private Vulnerability Reporting**: Use the "Report a vulnerability" button under the **Security** tab of the GitHub repository.
2. **Direct Email**: Send details to `security@stockflow.io` (or repository maintainers).

Please include:
- A description of the vulnerability.
- Steps to reproduce or proof-of-concept payload.
- Potential impact on the system or business data.

We will acknowledge receipt within 48 hours and work with you on a coordinated disclosure timeline.

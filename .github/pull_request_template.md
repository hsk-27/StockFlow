## Description
Briefly describe the change, motivation, and context.

Fixes #(issue)

## Type of Change
- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Refactoring / Architectural optimization
- [ ] Documentation update

## Architectural Invariants Checklist
- [ ] **Concurrency & Locking**: Verified deterministic lock ordering or optimistic concurrency control.
- [ ] **Data Integrity**: Checked inventory quantity constraints (`reserved <= on_hand`).
- [ ] **Idempotency**: Handled duplicate request fingerprints where appropriate.
- [ ] **Decoupled Boundaries**: No database transactions held open across external HTTP calls.
- [ ] **Outbox Pattern**: Domain events recorded transactionally in `outbox_events`.
- [ ] **Security**: BOLA/IDOR ownership checks and input validations verified.

## How Has This Been Tested?
- [ ] Unit tests added / updated
- [ ] Integration tests executed (`./mvnw clean verify`)
- [ ] Concurrency / stress test executed (if applicable)

## Checklist:
- [ ] My code follows the style guidelines of this project
- [ ] I have performed a self-review of my own code
- [ ] I have commented my code, particularly in hard-to-understand areas
- [ ] I have updated the documentation accordingly
- [ ] My changes generate no new compiler warnings
- [ ] All 42+ existing and new automated tests pass

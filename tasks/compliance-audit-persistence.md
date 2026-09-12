# Task: Compliance Audit Persistence (PostgreSQL & Flyway)

## Context & Objectives
Week 3 implemented the Token-2022 mint migration, transfer hooks, and the `TransferCompliancePort` SPI. 
This task completes the persistence layer for transfer hook compliance evaluations, logging every 
`CLEARED` and `BLOCKED` decision immutably to PostgreSQL via Flyway migrations and Spring Data JPA.

## Target Deliverables
1. **Flyway Migration (`V5__create_transfer_hook_audit.sql`)**:
   - Table `transfer_hook_audit_logs` with UUID primary key (`id`).
   - Fields: `transaction_signature` (VARCHAR, **NULLABLE** for blocked evaluations), `mint_address` (NOT NULL), 
     `source_wallet` (NOT NULL), `destination_wallet` (NOT NULL), `amount` (BIGINT, NOT NULL), 
     `compliance_status` (VARCHAR, `CLEARED`/`BLOCKED`), `reason_code` (VARCHAR), and 
     `created_at` (TIMESTAMP WITH TIME ZONE, non-updatable).
   - Indices on `mint_address`, `source_wallet`, `destination_wallet`, and `created_at`.
2. **JPA Entity & Repository**:
   - Immutable entity `TransferHookAuditLog` in `com.solana.rwa.bridge.entity`.
   - Repository `TransferHookAuditLogRepository` extending `JpaRepository` with custom lookup methods.
3. **Hexagonal SPI Integration**:
   - Intercept transfer hook evaluations and persist an audit record prior to returning 
     `TransferComplianceResult`.
4. **Test Suite Verification**:
   - `@DataJpaTest` covering repository persistence, unique constraints, and Flyway schema execution.
   - Integration test updating `TransferHookIT` to assert persistence of audit records on both
     allowed and fail-closed paths.

## Acceptance Criteria
- Clean build: `.\mvnw.cmd -f backend/pom.xml clean test` succeeds with 0 failures, 0 errors.
- Test count increases beyond the current baseline of **244 backend tests** (182 unit + 62 integration).
- Autonomous Automation Protocol (from `.clinerules`) executed upon GREEN suite.
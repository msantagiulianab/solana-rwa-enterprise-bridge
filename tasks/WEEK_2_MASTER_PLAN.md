# Week 2 Master Plan — Finality Confirmation Outbox, Maritime Clearance SPI & E2E DvP Simulation

> **Repository:** `solana-rwa-enterprise-bridge`
> **Backend:** Java 21 / Spring Boot 3.5.16 — `com.solana.rwa.bridge`
> **Sprint Cadence:** Tuesday → Monday (Week 2 video & Colosseum submission)
> **Baseline entering Week 2:** 188 backend tests passing (138 unit + 50 integration), 0 failures, 0 regressions.

---

## 1. Sprint Executive Summary & Goals

Week 2 advances the bridge from a **tokenization + compliance gatekeeper** into a **settlement lifecycle engine** for Panama Canal container logistics and electronic Bill of Lading (eBL) **Delivery vs. Payment (DvP)** settlement.

### High-Level Objectives

1. **Finality Confirmation Outbox Worker**
   Ship a `@Scheduled` background daemon that polls a durable outbox and advances settlement records through the on-chain lifecycle `PENDING → CONFIRMED → FINALIZED / FAILED` using `getSignatureStatuses` against the Solana Devnet RPC, with exponential backoff retries and fail-closed timeout handling.

2. **Maritime Domain & Clearance SPI**
   Introduce JPA entities for `BillOfLading`, `ContainerConsignment`, and `CanalTransitSettlement`, and a Hexagonal `MaritimeClearancePort` boundary that isolates external maritime authorities (ACP VUMPA, ANA SIGA, AMP, OFAC) behind a deterministic `SimulatedMaritimeClearanceAdapter` for sandbox validation.

3. **End-to-End Maritime Simulation Demo**
   Wire the full flow — carrier invoice → clearance evaluation → atomic SPL token settlement → finality upgrade — through clean, authenticated REST triggers and a deterministic simulation harness suitable for the Week 2 video.

### Quality Bar

- **Test coverage:** push from **188 → 200+** passing backend tests (unit + integration), **0 failures, 0 errors, zero regressions** on the Week 1 wire serializer, pre-flight simulation engine, priority-fee estimator, and compliance/audit export suite.
- **Fail-closed invariant:** no on-chain settlement broadcast and no `FINALIZED` upgrade may occur without a prior `CLEARED` maritime decision and a persisted `PENDING`/`CONFIRMED` off-chain record.
- **Zero-dependency adherence:** the wire-transaction serializer and the finality outbox remain pure JVM/Java 21; the maritime SPI uses no SOAP/REST SDK — only standard Java records and a Spring-agnostic interface.
- **Migration safety:** all schema changes are additive Flyway migrations (`V3`, `V4`) with production `hibernate.ddl-auto: validate` intact.

---

## 2. Architectural Integration Boundary & Institutional Roadmap

### 2.1 Formal Specification — `MaritimeClearancePort` SPI

The port is a **Spring-agnostic, pure-Java interface** owned by the application core. Adapters (simulated today, institutional tomorrow) implement it behind a Hexagonal boundary; the settlement domain depends only on the port, never on ACP/ANA/AMP/OFAC transport details.

```java
package com.solana.rwa.bridge.maritime.port;

import java.time.Instant;

/**
 * Hexagonal outbound port for external maritime/institutional clearance.
 * Owned by the application core; implemented by sandbox and production adapters.
 */
public interface MaritimeClearancePort {

    enum Authority { ACP_VUMPA, ANA_SIGA, AMP, OFAC }

    enum Decision { CLEARED, CUSTOMS_HOLD, SANCTIONS_FLAG, UNVERIFIED_CARRIER }

    /**
     * Deterministically evaluates a Panama Canal transit against external
     * maritime authorities. Returns a typed decision; never throws for a
     * domain rejection (only for a transport/infrastructure fault).
     */
    MaritimeClearanceResult evaluateTransit(MaritimeClearanceRequest request);
}

public record MaritimeClearanceRequest(
        String billOfLadingNumber,   // eBL document reference (unique)
        String containerNumber,      // container / consignment identifier
        String vesselImo,            // IMO number of the vessel
        String carrierId,            // carrier / operator identifier
        String originPort,           // UN/LOCODE or port code
        String destinationPort,      // UN/LOCODE or port code
        String consigneeWallet       // Solana base58 wallet for DvP settlement
) {}

public record MaritimeClearanceResult(
        MaritimeClearancePort.Decision decision,
        MaritimeClearancePort.Authority authority,
        String referenceId,          // external case/clearance reference
        String reason,               // sanitized, audit-safe explanation
        Instant evaluatedAt
) {}
```

**Deterministic scenario matrix (sandbox adapter):**

| Scenario | Authority | Decision | Settlement effect |
|----------|-----------|----------|-------------------|
| Valid transit | `ACP_VUMPA` / `ANA_SIGA` | `CLEARED` | Proceed to atomic SPL token settlement |
| Customs hold | `ANA_SIGA` | `CUSTOMS_HOLD` | Park settlement in `HOLD`, no broadcast |
| Sanctions flag | `OFAC` | `SANCTIONS_FLAG` | Fail-closed `BLOCKED`, immutable audit log |
| Unverified carrier | `AMP` | `UNVERIFIED_CARRIER` | Fail-closed `BLOCKED`, immutable audit log |

### 2.2 Simulated vs. Native On-Chain Execution Components

| Layer | Classification | Implementation |
|-------|---------------|----------------|
| ACP VUMPA (transit reservation) | **Simulated external** | `SimulatedMaritimeClearanceAdapter` (deterministic rules) |
| ANA SIGA (customs) | **Simulated external** | `SimulatedMaritimeClearanceAdapter` (deterministic rules) |
| AMP (maritime authority) | **Simulated external** | `SimulatedMaritimeClearanceAdapter` (deterministic rules) |
| OFAC sanctions screening | **Simulated external** | `SimulatedMaritimeClearanceAdapter` (deterministic rules) |
| Compliance gatekeeper (KYC/AML) | **Native on-chain guard** | `ComplianceService` (existing, fail-closed) |
| Wire transaction serializer | **Native, zero-dependency** | `SolanaTransactionSerializer` (existing) |
| Pre-flight simulation engine | **Native, zero-dependency** | `TransactionSimulationService` (existing) |
| Priority-fee estimation | **Native** | `SolanaRpcAdapter#getRecentPrioritizationFees` (existing) |
| Finality confirmation | **Native on-chain read** | `FinalityConfirmationOutboxWorker` → `SolanaRpcAdapter#getSignatureStatuses` (new) |

> **Explicit simulation declaration:** the `MaritimeClearancePort` is **intentionally simulated** for sandbox validation. ACP VUMPA, ANA SIGA, AMP, and OFAC are modeled as deterministic in-process adapters with zero live network calls. This boundary is designed to be replaced by live institutional REST/SOAP connectors **post pre-seed capital/licensing** — no production money, eBL title transfer, or live authority integration occurs in Week 2.

### 2.3 Future Post-Seed Enterprise Connector Roadmap

| Phase | Connector | Protocol | Notes |
|-------|-----------|----------|-------|
| Post-seed | `AcpVumpaRestAdapter` | REST (mutual TLS) | Panama Canal Authority VUMPA transit reservations |
| Post-seed | `AnaSigaSoapAdapter` | SOAP/XML | ANA SIGA customs declaration & cargo status |
| Post-seed | `AmpRestAdapter` | REST (mutual TLS) | Maritime Authority of Panama vessel/carrier registry |
| Post-seed | `OfacScreeningAdapter` | REST/batch | Sanctions list screening against an authorized provider |
| Post-seed | `InstitutionalHsmSigner` | PKCS#11 / HSM | Replace browser-wallet signing with custody-grade key management |

Each live connector implements the **same** `MaritimeClearancePort`, so the settlement core is untouched; only the Spring bean wiring changes (e.g., a `@Profile("prod-institutional")` configuration).

---

## 3. Day-by-Day Sprint Schedule (Tuesday – Monday)

### Tuesday – Wednesday: Finality Confirmation Outbox Worker

**Outcome:** a durable, resumable outbox daemon that advances settlement records from `CONFIRMED` to `FINALIZED` (or `FAILED`) by polling on-chain signature status, with backoff and fail-closed timeouts.

- Extend `SettlementStatus` with `FINALIZED` (additive, `EnumType.STRING` — no breaking change to existing `PENDING`/`CONFIRMED`/`FAILED`).
- Add `SolanaRpcAdapter#getSignatureStatuses(List<String>)` + JSON-RPC DTOs under `com.solana.rwa.bridge.rpc.dto` (e.g., `SignatureStatus`, `SignatureStatusesResult`).
- New outbox entity `FinalityOutboxEntry` (table `finality_outbox`) with: `id`, `assetId`, `idempotencyKey`, `solanaTransactionSignature`, `state`, `attemptCount`, `maxAttempts`, `nextAttemptAt`, `lastError`, `finalizedAt`, `createdAt`, `updatedAt`.
- `FinalityConfirmationOutboxWorker` (`@Scheduled` with configurable `fixedDelay`) polls due outbox rows (`state IN (PENDING, CONFIRMED)` and `nextAttemptAt <= now`), queries signature status, and applies the transition:
  - `commitment == finalized` → `FINALIZED`
  - `commitment` absent/`processed`/`confirmed` → remain `CONFIRMED`, schedule next attempt (exponential backoff)
  - transport/timeout error → increment `attemptCount`, fail-closed (no premature `FINALIZED`), schedule retry
  - `attemptCount >= maxAttempts` → `FAILED` with sanitized `lastError`
- Flyway `V3__finality_outbox.sql`: create `finality_outbox` table + indexes on `state`/`next_attempt_at`.
- Tests: `FinalityConfirmationServiceTest` (state transitions, backoff, timeout/fail-closed), outbox repository `*IT.java`, and scheduling-config smoke test.

### Thursday – Friday: Maritime Domain Models & Hexagonal Compliance Wiring

**Outcome:** the RWA maritime domain is modeled and the clearance SPI is wired into settlement with explicit sandbox scenarios.

- JPA entities (Flyway `V4__maritime_domain.sql`):
  - `BillOfLading` — eBL reference, shipper/consignee, ports, carrier, cargo description, status.
  - `ContainerConsignment` — container number, bill of lading FK, seal/tare, consignment value.
  - `CanalTransitSettlement` — links consignment → clearance decision → SPL settlement → finality state.
- `MaritimeClearancePort` SPI + `MaritimeClearanceRequest`/`MaritimeClearanceResult` (Section 2.1).
- `SimulatedMaritimeClearanceAdapter` implementing the deterministic scenario matrix (valid transit, customs hold, sanctions flag, unverified carrier) with zero network I/O.
- `MaritimeSettlementService` orchestrates: validate payload → invoke port → branch on decision (`CLEARED` → build settlement; `CUSTOMS_HOLD` → park; `SANCTIONS_FLAG`/`UNVERIFIED_CARRIER` → fail-closed block + audit).
- Tests: `MaritimeClearancePortContractTest` (port contract), `SimulatedMaritimeClearanceAdapterTest` (4 scenarios), `MaritimeSettlementServiceTest` (decision branching, fail-closed), and JPA repository `*IT.java`.

### Saturday – Sunday: End-to-End Maritime Simulation Harness & REST Endpoints

**Outcome:** a demoable, authenticated end-to-end pipeline — carrier invoice → clearance evaluation → atomic SPL token settlement → finality upgrade.

- REST controllers (mutating routes gated by the existing `X-API-Key` interceptor):
  - `POST /api/v1/maritime/bills-of-lading` — register an eBL + consignment.
  - `POST /api/v1/maritime/settlements/{id}/evaluate` — run clearance and branch the settlement.
  - `POST /api/v1/maritime/settlements/{id}/execute` — perform atomic SPL token settlement (mocked RPC in tests, Devnet in the demo).
  - `GET /api/v1/maritime/settlements/{id}` — read settlement + finality state.
- End-to-end simulation harness (`MaritimeSettlementE2ESimulation`) that runs the full flow deterministically against the sandbox adapter and asserts the state machine.
- Wire finality: a `CLEARED` + settled record feeds an outbox entry so the Week 2 worker upgrades it to `FINALIZED`.
- Tests: MockMvc controller `*Test.java` (auth 401, valid 200, hold/block mapping) and an end-to-end simulation `*IT.java`.

### Monday: Polish, Full Regression Suite, Documentation & Submission

- Run `./mvnw clean test` (or `./mvnw.cmd clean test` on Windows) — target **200+ tests, 0 failures, 0 errors**.
- Update `README.md` (feature list, endpoint table, and test-count sync to 200+), append `DEVELOPMENT_JOURNAL.md` entry.
- Record the **60s Loom** walking the E2E maritime DvP demo (carrier invoice → clearance → atomic settlement → finality).
- Finalize the Colosseum dashboard submission (repo, video, README, Week 2 plan).

---

## 4. Task Decomposition & Sub-File Hierarchy

Each phase owns a focused sub-task markdown file under `tasks/`, mirroring the existing `.tasks/` plan conventions (TDD RED/GREEN/REFACTOR steps, file paths, and a Definition-of-Done checklist).

```
tasks/
├── WEEK_2_MASTER_PLAN.md          ← this file (top-level sprint plan)
├── task-01-finality-outbox.md     ← Tue–Wed: @Scheduled outbox worker, getSignatureStatuses,
│                                     SettlementStatus.FINALIZED, V3 migration, backoff/timeout tests
├── task-02-maritime-domain.md     ← Thu–Fri: BillOfLading/ContainerConsignment/CanalTransitSettlement,
│                                     MaritimeClearancePort SPI, SimulatedMaritimeClearanceAdapter,
│                                     V4 migration, clearance scenario tests
├── task-03-e2e-simulation-harness.md ← Sat–Sun: REST controllers, atomic SPL settlement,
│                                        MaritimeSettlementE2ESimulation, MockMvc + IT tests
└── task-04-polish-regression-submission.md ← Mon: full 200+ regression, docs sync, Loom, submission
```

### Sub-Task Files

| File | Phase | Scope | Primary test targets |
|------|-------|-------|----------------------|
| `tasks/task-01-finality-outbox.md` | Tuesday–Wednesday | Outbox worker + finality state machine | `FinalityConfirmationServiceTest`, `FinalityOutboxRepositoryIT`, worker scheduling smoke |
| `tasks/task-02-maritime-domain.md` | Thursday–Friday | Maritime JPA entities + Hexagonal clearance SPI | `MaritimeClearancePortContractTest`, `SimulatedMaritimeClearanceAdapterTest`, `MaritimeSettlementServiceTest`, `*RepositoryIT` |
| `tasks/task-03-e2e-simulation-harness.md` | Saturday–Sunday | REST endpoints + E2E DvP simulation | `MaritimeSettlementControllerTest` (MockMvc), `MaritimeSettlementE2EIT` |
| `tasks/task-04-polish-regression-submission.md` | Monday | Regression, docs, Loom, submission | Full `./mvnw clean test` → 200+ |

---

## 5. Definition of Done (DoD) & Acceptance Criteria

### Compilation & Build

- [ ] `./mvnw clean test` (Windows: `./mvnw.cmd clean test`) compiles on **Java 21 / Spring Boot 3.5.16** with **0 compilation errors** and **0 warnings blocking the build**.
- [ ] Lombok annotation processing remains deterministic via the `maven-compiler-plugin` `<proc>full</proc>` configuration.

### Zero-Dependency Adherence (Wire Serialization & Outbox)

- [ ] No third-party Solana/Web3 SDK, SOAP client, or export library is introduced.
- [ ] `SolanaTransactionSerializer` and the finality outbox worker use only Java 21 stdlib + Jackson (already present) + Spring scheduling primitives.
- [ ] The `MaritimeClearancePort` interface and its request/result records import **no Spring framework classes** (pure Hexagonal core).

### Database Migration Safety

- [ ] Schema changes are additive-only Flyway migrations (`V3__finality_outbox.sql`, `V4__maritime_domain.sql`).
- [ ] Production `hibernate.ddl-auto: validate` still passes against the migrated schema; no Hibernate auto-DDL drift.
- [ ] `SettlementStatus.FINALIZED` is added as a new `EnumType.STRING` value without renaming/removing existing values.
- [ ] Existing `V1__baseline.sql` and `V2__settlement_idempotency.sql` remain unmodified (no rewrite of applied history).

### Finality Outbox Acceptance

- [ ] A `CONFIRMED` outbox entry with an on-chain `finalized` commitment transitions to `FINALIZED` exactly once.
- [ ] A missing/`processed`/`confirmed` commitment schedules exponential backoff and **never** prematurely finalizes.
- [ ] A transport/timeout failure increments `attemptCount` and remains resumable; reaching `maxAttempts` transitions to `FAILED` with a sanitized `lastError`.
- [ ] The worker is idempotent and safe under concurrent polling (no double-finalization).

### Maritime Clearance Acceptance

- [ ] All four deterministic scenarios pass: `CLEARED` (valid transit), `CUSTOMS_HOLD` (ANA SIGA), `SANCTIONS_FLAG` (OFAC), `UNVERIFIED_CARRIER` (AMP).
- [ ] `CLEARED` is the **only** decision that permits atomic SPL token settlement.
- [ ] `SANCTIONS_FLAG` and `UNVERIFIED_CARRIER` produce an immutable `AuditLog` entry (`BLOCKED`) and never dispatch an RPC broadcast (fail-closed).
- [ ] `CUSTOMS_HOLD` parks the settlement without broadcasting and is recoverable/observable via the settlement read endpoint.

### REST & Security Acceptance

- [ ] All mutating maritime routes (`POST`) enforce `X-API-Key` via the existing `ApiKeyAuthInterceptor` (missing/invalid → `401`).
- [ ] Request DTOs use `@Valid` + Jakarta Bean Validation; malformed bodies → `400` via `HttpMessageNotReadableException`.
- [ ] `GlobalExceptionHandler` maps maritime domain exceptions to `404`/`400`/`422` without leaking stack traces or internal paths.

### Test Assertions & Regression Bar

- [ ] Backend test count is **200+** passing (unit + integration), **0 failures, 0 errors**.
- [ ] Week 1 suites remain green with **zero regressions**: wire serializer (4-instruction), pre-flight simulation, dynamic priority-fee estimation, fail-closed compliance, and audit export.
- [ ] Every new feature ships with a failing-then-passing (RED → GREEN) test trace in its sub-task file.
- [ ] `README.md` test-count table and `DEVELOPMENT_JOURNAL.md` are synchronized with the final count.

### Submission Deliverables

- [ ] 60-second Loom recording demonstrates: carrier invoice → clearance evaluation → atomic SPL token settlement → finality upgrade.
- [ ] Colosseum dashboard submission includes the repo, video link, updated `README.md`, and this Week 2 plan.



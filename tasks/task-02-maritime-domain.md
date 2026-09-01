# Task 02 — Maritime Domain Models & Hexagonal Compliance Wiring

> **Branch:** `feat/week-2-task-02-maritime-domain`
> **Backend:** Java 21 / Spring Boot 3.5.16 — `com.solana.rwa.bridge`
> **Parent plan:** `tasks/WEEK_2_MASTER_PLAN.md` (Wednesday–Thursday)

## 1. Objective

Introduce the maritime settlement domain — electronic Bill of Lading (eBL),
container consignments, and Panama Canal transit settlements — and wire an
external maritime-clearance boundary behind a **Hexagonal SPI**
(`MaritimeClearancePort`). The sandbox adapter
(`SimulatedMaritimeClearanceAdapter`) deterministically models four external
authorities (ACP VUMPA, ANA SIGA, AMP, OFAC) so the Delivery-vs-Payment (DvP)
settlement flow can be exercised end-to-end without live institutional calls.

## 2. Maritime Domain Entity Relationships

```
BillOfLading (1) ──────────────< (N) ContainerConsignment
      │
      └───────────────────────< (N) CanalTransitSettlement
                                   │
                                   └── (0..1) FinalityOutboxEntry (finality_outbox)
```

| Entity | Table | Cardinality | Purpose |
|--------|-------|-------------|---------|
| `BillOfLading` | `bills_of_lading` | root aggregate | eBL document, carrier/vessel, shipper/consignee wallets, declared value, clearance status |
| `ContainerConsignment` | `container_consignments` | child (cascade + orphan removal) | one container per consignment, seal, gross weight, hazardous flag, customs status |
| `CanalTransitSettlement` | `canal_transit_settlements` | child | transit fee, SPL settlement mint, escrow account, settlement lifecycle, outbox linkage |

- `BillOfLading` owns a `@OneToMany(mappedBy = "billOfLading", cascade = ALL,
  orphanRemoval = true)` collection of `ContainerConsignment`s.
- `CanalTransitSettlement` references its `BillOfLading` via `@ManyToOne` and its
  asynchronous confirmation record via a logical `outbox_entry_id` (`uuid`)
  pointing at `finality_outbox.id`.

## 3. Hexagonal SPI Specification (`maritime/port`)

The port is owned by the application core and imports **no Spring framework
classes** (pure Java 21 records + the domain enum).

```java
public interface MaritimeClearancePort {
    MaritimeClearanceResult evaluateClearance(MaritimeClearanceRequest request);
}

public record MaritimeClearanceRequest(
        String billOfLadingNumber,
        String containerNumber,
        String sealNumber,
        BigDecimal grossWeightKg,
        boolean hazardous,
        String vesselImo,
        String carrierCode,
        String portOfLoading,
        String portOfDischarge,
        String consigneeWallet) {}

public record MaritimeClearanceResult(
        ClearanceStatus status,
        ClearanceReasonCode reasonCode,
        String referenceId,          // external case / clearance certificate id
        String transitPermitToken,   // valid transit permit token (CLEARED only)
        Instant evaluatedAt) {}

public record ClearanceReasonCode(String authority, String code) {}
```

### Clearance status enums

- `ClearanceStatus`: `PENDING`, `CLEARED`, `HELD_CUSTOMS`, `SANCTIONED`, `REJECTED`
- `TransitSettlementStatus`: `INITIALIZED`, `ESCROW_FUNDED`, `CLEARED`, `SETTLED`, `FAILED`

## 4. Deterministic 4-Scenario Simulation Matrix

| Scenario | Trigger (deterministic) | Authority | `ClearanceStatus` | Settlement effect |
|----------|-------------------------|-----------|-------------------|-------------------|
| `VALID_TRANSIT` | none of the below | `NONE` | `CLEARED` | proceed to settlement + outbox enqueue |
| `CUSTOMS_HOLD` | seal starts with `HOLD` **or** container == `CONT-HOLD-001` | `ANA_SIGA` (`CUSTOMS_HOLD`) | `HELD_CUSTOMS` | fail-closed, no settlement/outbox/broadcast |
| `SANCTIONED_ENTITY` | vessel IMO == `IMO9999999` **or** consignee wallet blacklisted | `OFAC` (`SDN_MATCH`) | `SANCTIONED` | fail-closed, no settlement/outbox/broadcast |
| `UNVERIFIED_CARRIER` | carrier code starts with `UNVERIFIED` | `ACP_VUMPA` (`UNVERIFIED_CARRIER`) | `REJECTED` | fail-closed, no settlement/outbox/broadcast |

> `CLEARED` is the **only** status that permits settlement and an outbox enqueue.
> All non-cleared paths throw `MaritimeComplianceException` and never create an
> outbox row or dispatch a token action.

## 5. Flyway V4 Migration Schema

`src/main/resources/db/migration/V4__create_maritime_domain_tables.sql` (additive):

- `bills_of_lading` — UUID PK, unique `bl_number`, carrier/vessel/port fields,
  shipper/consignee wallets (`varchar(44)`), `declared_value_usd numeric(18,2)`,
  `cargo_description text`, `clearance_status varchar(32) NOT NULL DEFAULT 'PENDING'`,
  nullable `token_mint_address`, audit timestamps. Indexes on `vessel_imo`,
  `bl_number`, `clearance_status`.
- `container_consignments` — UUID PK, FK `bill_of_lading_id` (CASCADE), container/seal
  numbers, `gross_weight_kg numeric(12,2)`, `is_hazardous boolean DEFAULT FALSE`,
  `customs_status varchar(32) DEFAULT 'PENDING'`.
- `canal_transit_settlements` — UUID PK, FK `bill_of_lading_id` (CASCADE), unique
  `transit_booking_reference`, `transit_fee_usd numeric(18,2)`, `settlement_token_mint`,
  `escrow_account`, `status varchar(32) DEFAULT 'INITIALIZED'`, nullable
  `transaction_signature`, `outbox_entry_id` (FK → `finality_outbox`), `settled_at`,
  audit timestamps.

## 6. Integration Test Matrix & Acceptance Criteria

| Suite | Scope |
|-------|-------|
| `SimulatedMaritimeClearanceAdapterTest` | all 4 deterministic scenarios + reason-code assertions (valid transit, customs hold via seal & container, sanctions via IMO & blacklisted consignee, unverified carrier) |
| `MaritimeSettlementServiceTest` | happy path (CLEARED → BOL CLEARED → outbox `CONFIRMED` enqueued, settlement `SETTLED`), fail-closed on customs hold / sanctions / unverified carrier (no outbox, no settlement, `MaritimeComplianceException`), missing BOL → 404-typed exception |
| `MaritimeRepositoryIT` | V4 schema on H2 (defaults + timestamps), cascade persist of BOL→consignments, orphan removal, settlement→outbox linkage, unique constraints |

**Acceptance criteria:**

- [ ] `./mvnw clean test` → **215+** total tests, **0 failures, 0 errors**.
- [ ] All four deterministic scenarios pass; `CLEARED` is the only settlement-permitting decision.
- [ ] Fail-closed invariant: non-cleared paths create **no** outbox row and **no** token/settlement broadcast.
- [ ] `MaritimeClearancePort` + records import **no Spring classes** (pure Hexagonal core).
- [ ] V4 migration is additive; `V1__baseline.sql`, `V2__settlement_idempotency.sql`, `V3__extend_settlement_status_and_outbox.sql` remain unmodified.
- [ ] Production `hibernate.ddl-auto: validate` passes (entities map exactly to the migrated schema).

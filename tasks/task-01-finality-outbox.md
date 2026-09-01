# Task 01 — Finality Confirmation Outbox Worker

> **Branch:** `feat/week-2-task-01-finality-outbox`
> **Backend:** Java 21 / Spring Boot 3.5.16 — `com.solana.rwa.bridge`
> **Parent plan:** `tasks/WEEK_2_MASTER_PLAN.md` (Tuesday–Wednesday)

## 1. Objective

Ship a `@Scheduled` background daemon that polls a durable outbox and advances
on-chain settlement records through the lifecycle
`PENDING → CONFIRMED → FINALIZED / FAILED / EXPIRED` by querying
`getSignatureStatuses` against the Solana Devnet RPC, with exponential backoff
retries and fail-closed timeout handling.

## 2. Target Architecture (asynchronous finality confirmation)

```
[Tokenization/ Settlement]  ── enqueue on CONFIRMED ──►  finality_outbox
                                                        │
FinalityConfirmationWorker  ◄── poll due rows ──────────┘
   │ status=CONFIRMED, commitment_level != FINALIZED, next_poll_at <= now
   │ getSignatureStatuses(batch, searchTransactionHistory=true)
   ▼
[Solana Devnet RPC]  ──►  FINALIZED / FAILED / EXPIRED (with backoff)
```

Key decisions:

1. **Dedicated outbox table.** Polling bookkeeping (`poll_attempts`,
   `next_poll_at`, `commitment_level`, `error_message`) lives in a dedicated
   `finality_outbox` table — never on the immutable `audit_logs` ledger and never
   on the `asset_tokens` source of truth (the transactional-outbox pattern).
2. **Fail-closed.** No `FINALIZED` upgrade is ever produced from a missing,
   `processed`, or `confirmed` commitment. Transport/timeout/429 failures only
   schedule a retry (or `EXPIRED` after max attempts) — they never finalize.
3. **Atomic batches.** The scheduled entry point is the `@Transactional`
   boundary of the poll cycle; any non-transient exception rolls the whole cycle
   back so no partial state is ever committed.

## 3. State Machine

| From        | Event / observation                          | To         | Notes |
|-------------|----------------------------------------------|------------|-------|
| `CONFIRMED` | RPC `err != null`                            | `FAILED`   | on-chain execution error; sanitized `error_message` |
| `CONFIRMED` | `confirmationStatus == "finalized"`, no error | `FINALIZED`| stamp `settled_at`, `commitment_level=FINALIZED` |
| `CONFIRMED` | `processed` / `confirmed` / `null`           | `CONFIRMED`| `poll_attempts++`, exponential backoff (`next_poll_at`) |
| `CONFIRMED` | transport/timeout/429 (`SolanaRpcException`) | `CONFIRMED`| same retry path, fail-closed (no premature finality) |
| `CONFIRMED` | `poll_attempts >= max_poll_attempts`         | `EXPIRED`  | fail-closed timeout, audit `error_message` |

`SettlementStatus` is extended additively (`EnumType.STRING`) with `FINALIZED`
and `EXPIRED`. The backing columns are unconstrained `varchar`, so no ALTER of
existing columns and no rewrite of applied Flyway history is required.

## 4. Flyway Migration Spec (V3)

`src/main/resources/db/migration/V3__extend_settlement_status_and_outbox.sql`

- Create `finality_outbox` with:
  - `id` (uuid, PK), `asset_token_id` (uuid, nullable logical ref),
    `idempotency_key` (varchar(255), unique), `solana_transaction_signature` (varchar(88))
  - `status` (varchar(32), NOT NULL) — `SettlementStatus` name
  - `commitment_level` (varchar(32)) — `CONFIRMED`/`FINALIZED`/`PROCESSED`
  - `poll_attempts` (integer NOT NULL DEFAULT 0)
  - `max_poll_attempts` (integer NOT NULL DEFAULT 30)
  - `last_polled_at` (timestamptz), `next_poll_at` (timestamptz)
  - `error_message` (text), `settled_at` (timestamptz)
  - `created_at` / `updated_at` (timestamptz NOT NULL)
- Performance index `idx_settlements_outbox_polling`
  on `(status, commitment_level, next_poll_at)` — the poller's hot path.
- Secondary index `idx_finality_outbox_next_poll_at` on `(next_poll_at)`.

## 5. Solana RPC Method Requirements

`getSignatureStatuses` (JSON-RPC 2.0, pure JVM via the existing `RestClient`):

```json
{"jsonrpc":"2.0","id":1,"method":"getSignatureStatuses",
 "params":[["<SIG>", ...],{"searchTransactionHistory":true}]}
```

- `searchTransactionHistory: true` is mandatory so finality is resolved against
  transaction history, not just the node's recent-signature cache.
- Response values are index-aligned with the requested signatures; a missing
  value is mapped to an unconfirmed result (fail-closed).
- Timeouts, HTTP errors, JSON-RPC errors, and null results raise
  `SolanaRpcException` — the worker treats those as transient (retry/backoff),
  never as success.

## 6. Acceptance Criteria

- [ ] `CONFIRMED` → `FINALIZED` exactly once on an on-chain `finalized` status.
- [ ] `processed`/`confirmed`/`null` schedules exponential backoff (2s, 4s, 8s, … cap).
- [ ] `err != null` → `FAILED` with a sanitized `error_message`.
- [ ] `poll_attempts >= max_poll_attempts` → `EXPIRED` (fail-closed).
- [ ] Transport/timeout/429 failures are transient (retry + backoff), never finalizing.
- [ ] Worker is idempotent: already-`FINALIZED`/terminal records are never re-processed.
- [ ] Additive V3 migration only; `V1__baseline.sql` and `V2__settlement_idempotency.sql` untouched.
- [ ] Production `hibernate.ddl-auto: validate` still passes (entity maps to migrated schema).
- [ ] `./mvnw clean test` → all 188 existing tests + new tests pass, 0 failures.

## 7. Test Coverage Targets

| Suite | Scope |
|-------|-------|
| `SolanaRpcAdapterTest` (extended) | `getSignatureStatuses` serialization/deserialization: finalized, confirmed, transaction-error payload, null result, network timeout, empty-input short-circuit |
| `FinalityConfirmationWorkerTest` (new) | happy-path finalization, transient backoff, transaction failure → `FAILED`, max-attempts → `EXPIRED`, missing-signature fail-closed, idempotency, exponential backoff schedule |
| `FinalityOutboxRepositoryIT` (new) | V3 schema on H2 (defaults + timestamps), `findDueForPolling` due-row filtering |

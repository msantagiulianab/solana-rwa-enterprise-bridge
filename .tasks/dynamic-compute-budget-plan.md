# Dynamic Compute Budget & Priority Fee Optimization — TDD Execution Plan

**Branch:** `feature/dynamic-compute-budget`
**Base:** `main`
**Scope:** Solana RWA Enterprise Bridge — backend only (Spring Boot 3.5.x, Java 21)
**Rule set:** `.clinerules.md` (strict TDD Red → Green → Refactor) + `.clinerules/security-pentest.md`

> This file is a **plan only**. No Java code is written as part of creating this plan.

---

## Goal

Make SPL Token mint creation cheaper and more reliable on Devnet by injecting two
explicit Compute Budget instructions — `setComputeUnitPrice` (priority fee) and
`setComputeUnitLimit` — ahead of the existing `SystemProgram.createAccount` +
`TokenProgram.initializeMint` atomic payload. Priority fees are priced dynamically
from the node's `getRecentPrioritizationFees` RPC with a configurable, fail-closed
fallback when the RPC is unavailable or returns no usable sample.

The mint transaction must go from **2 instructions** to **4 instructions**, still
submitted atomically through the existing blockhash-retry path.

---

## Current-State Reference (verified before branching)

| Concern | Location |
|---|---|
| RPC client | `backend/src/main/java/com/solana/rwa/bridge/rpc/SolanaRpcAdapter.java` |
| RPC client tests | `backend/src/test/java/com/solana/rwa/bridge/rpc/SolanaRpcAdapterTest.java` (17 tests) |
| Mint assembly | `backend/src/main/java/com/solana/rwa/bridge/solana/SolanaMintService.java` |
| Mint assembly tests | `backend/src/test/java/com/solana/rwa/bridge/solana/SolanaMintServiceTest.java` (6 tests) |
| Compliance/fail-closed gate | `backend/src/test/java/com/solana/rwa/bridge/service/TokenServiceTest.java` (7 tests) |
| Instruction model | `SolanaInstruction(byte[] programId, List<AccountMeta> accounts, byte[] data)` |
| Account model | `AccountMeta(byte[] pubkey, boolean signer, boolean writable)` |
| JSON-RPC envelope | `RpcEnvelope<T>` in `com.solana.rwa.bridge.rpc.dto` |
| Config | `backend/src/main/resources/application.yml` |
| Docs | `README.md` (test counts), `DEVELOPMENT_JOURNAL.md` (phase log) |

**Baseline backend test count (README, Step "Test Counts"):** 118 total
(72 unit `*Test.java` + 46 integration `*IT.java`). This plan **adds** unit tests
only and will require a README/JOURNAL metric sync in Step 4.

---

## Step 1 — Pure-Java Compute Budget Instruction Encoding (TDD)

**Red:** Write `backend/src/test/java/com/solana/rwa/bridge/solana/ComputeBudgetInstructionTest.java`
first, with no production class yet — the test must not compile until the class exists.

**Target program ID:** `ComputeBudget1111111111111111111111`
(32 bytes once base58-decoded via `Base58Codec`).

**Production class to create (Green):**
`backend/src/main/java/com/solana/rwa/bridge/solana/ComputeBudgetInstruction.java`

Static factories (package `com.solana.rwa.bridge.solana`, matching `SolanaInstruction`):

1. `setComputeUnitPrice(long microLamports)`
   - discriminator: `0x03` (single `u8`)
   - payload: `8-byte little-endian u64` of `microLamports`
   - returns `SolanaInstruction` with program id = Compute Budget program
   - accounts: empty `List.of()` (Compute Budget instructions reference no accounts)
2. `setComputeUnitLimit(int units)`
   - discriminator: `0x02` (single `u8`)
   - payload: `4-byte little-endian u32` of `units`
   - returns `SolanaInstruction` with program id = Compute Budget program
   - accounts: empty `List.of()`

**Assertions in `ComputeBudgetInstructionTest.java`:**
- Program id decodes to `ComputeBudget1111111111111111111111` and is exactly 32 bytes.
- `setComputeUnitPrice(...)` data == `{ 0x03, u64_le }` for a fixed sample (e.g. `10_000`).
- `setComputeUnitPrice(...)` data == `{ 0x03, u64_le }` for a max/edge sample (e.g. `Long.MAX_VALUE`) — proves unsigned little-endian ordering.
- `setComputeUnitLimit(...)` data == `{ 0x02, u32_le }` for a fixed sample (e.g. `10_000`).
- `setComputeUnitLimit(...)` data == `{ 0x02, u32_le }` for the **target CU limit `10_000`**.
- Account classification: `accounts` is empty; the **program id** must be treated as a
  **readonly, non-signer** account when compiled into the transaction message
  (`signer=false`, `writable=false`).

**Red → Green → Refactor gate:** the new test is RED (compile/fail), then the minimal
`ComputeBudgetInstruction` is implemented to turn it GREEN; no dead code or extra helpers.

---

## Step 2 — RPC Dynamic Fee Client & Fallback (TDD)

**Production changes (`SolanaRpcAdapter.java`):**

1. Add `getRecentPrioritizationFees(List<String> accountAddresses)`.
   - JSON-RPC method: `getRecentPrioritizationFees`
   - params: `List.of(accountAddresses, Map.of("commitment", "confirmed"))`
   - Reuse the existing `call(...)`/`RpcEnvelope<T>` plumbing.
2. Add response records in `com.solana.rwa.bridge.rpc.dto`:
   - `PrioritizationFee(long slot, long prioritizationFee)` (map the node's
     `slot` and `prioritizationFee` fields).
   - `PrioritizationFeeResult(RpcContext context, List<PrioritizationFee> value)`
     (mirroring `LatestBlockhashResult`/`TokenAccountBalanceResult` shape).
3. Add a percentile helper — **75th percentile** — over the `prioritizationFee`
   values. Pure static method (no Spring), unit-testable in isolation.
4. Resilient fallback:
   - New config key in `application.yml`:
     `solana.rpc.priority-fee-baseline-micro-lamports` (env-overridable via
     `SOLANA_PRIORITY_FEE_BASELINE`, sensible Devnet default).
   - Inject via `@Value("${solana.rpc.priority-fee-baseline-micro-lamports:...}")`.
   - Fallback triggers when the RPC call **fails** (timeout/HTTP/JSON-RPC error) **or**
     returns an **empty/null** fee list. On fallback, log a `WARN` (mirroring
     `getMinimumBalanceForRentExemption`) and return the configured baseline.

**Red tests in `SolanaRpcAdapterTest.java` (additive, current 17 tests):**
- Valid non-empty `prioritizationFee` array → parses and returns the **75th percentile**.
- Empty array → returns the configured **baseline** (no exception, fail-safe).
- `ResourceAccessException` (timeout) → returns the configured **baseline**.
- JSON-RPC error payload / null result → returns the configured **baseline**.
- Verify the outbound JSON payload carries `getRecentPrioritizationFees` and the
  account-address list.

**Green/Refactor:** implement minimally, then refactor to share the fallback/percentile
logic without duplication. All existing `SolanaRpcAdapterTest` cases stay GREEN.

---

## Step 3 — Atomic 4-Instruction Assembly in `SolanaMintService`

**Production change (`SolanaMintService.createMint()`):**

1. Query dynamic priority fees via `rpcAdapter.getRecentPrioritizationFees(...)` for the
   relevant accounts (payer and/or mint; documented decision).
2. Build explicit CU limit instruction: `ComputeBudgetInstruction.setComputeUnitLimit(10_000)`
   (target: **10,000 compute units**).
3. Build priority fee instruction: `ComputeBudgetInstruction.setComputeUnitPrice(feeMicroLamports)`.
4. **Prepend** the two Compute Budget instructions before `SystemProgram.createAccount` and
   `TokenProgram.initializeMint`:
   - Instruction 0 → `setComputeUnitLimit`
   - Instruction 1 → `setComputeUnitPrice`
   - Instruction 2 → `SystemProgram.createAccount`
   - Instruction 3 → `TokenProgram.initializeMint`
   (preserve the existing `submitWithBlockhashRetry(List.of(...), List.of(payer, mint), ...)`
   atomic submission path — no change to the retry/blockhash logic.)

**Test changes (`SolanaMintServiceTest.java`, current 6 tests):**

- Update the wire-format test: `instructionCount` changes `2 → 4`; the program-id account
  table now includes the **Compute Budget** program (asserted as a **readonly, non-signer**
  account) and the instruction indexes shift accordingly.
- Assert instruction 0 data begins with `0x02` + `u32_le(10_000)`.
- Assert instruction 1 data begins with `0x03` + the expected `u64_le` fee (from a mocked
  `getRecentPrioritizationFees`).
- Assert instruction 2 == `createAccount` (unchanged) and instruction 3 == `initializeMint`
  (unchanged).
- Add a fallback case: `getRecentPrioritizationFees` returns baseline → the assembled
  transaction embeds the baseline priority fee.
- Keep the existing rent-exemption / blockhash-retry / BAD_REQUEST mapping tests GREEN
  (add the `getRecentPrioritizationFees` stub where the mock now requires it).

**Compliance assertions (`TokenServiceTest.java`):**
- Re-run and extend if needed to keep the fail-closed guarantee intact: non-compliant
  issuers must still produce `verifyNoInteractions(solanaMintService)` /
  `verifyNoInteractions(solanaRpcAdapter)` with **zero Devnet bytes emitted**.
- The 7 existing compliance-block paths must remain GREEN unchanged.

---

## Step 4 — Verification, Devnet Testing & Documentation

1. **Full backend suite (green build):**
   - `backend\mvnw.cmd -f backend\pom.xml test`
   - Expect **0 failures, 0 errors**; unit counts now include the new
     `ComputeBudgetInstructionTest` and the expanded `SolanaRpcAdapterTest` /
     `SolanaMintServiceTest` cases.
2. **End-to-end Devnet mint test:**
   - Fund the fee payer (keypair log at startup shows the Devnet address; faucet at
     `https://faucet.solana.com`).
   - Mint through the compliant issuer path; capture the transaction signature and mint
     address.
   - Verify the transaction on **Solana Explorer** (Devnet) — confirm 4 instructions,
     the explicit `setComputeUnitLimit(10_000)` and `setComputeUnitPrice(...)` prefix.
3. **`README.md` metric sync:**
   - Update the "Test Counts" table and the unit-test breakdown line to reflect the new
     `ComputeBudgetInstructionTest` count and any added `SolanaRpcAdapterTest` /
     `SolanaMintServiceTest` cases (live count must match reality).
4. **`DEVELOPMENT_JOURNAL.md`:**
   - Append a dated phase entry with: plan, tests added (names + counts), verification
     numbers (backend test total), the Devnet transaction signature, and architectural
     decisions (75th-percentile choice, baseline config key, CU limit rationale).

---

## Definition of Done

- [ ] `feature/dynamic-compute-budget` branch is checked out and clean.
- [ ] `ComputeBudgetInstructionTest.java` GREEN — exact byte + account classification asserts.
- [ ] `SolanaRpcAdapterTest.java` GREEN — valid/empty/timeout/error/percentile cases.
- [ ] `SolanaMintServiceTest.java` GREEN — 4-instruction wire format + fallback fee.
- [ ] `TokenServiceTest.java` GREEN — fail-closed compliance assertions unchanged.
- [ ] `mvn test` → 100% GREEN (0 failures, 0 errors).
- [ ] Devnet mint verified on Solana Explorer (4 instructions, CU limit + priority fee).
- [ ] `README.md` and `DEVELOPMENT_JOURNAL.md` updated.

---

## Non-Goals / Guardrails

- No private keys committed; RPC URL/fee baseline via env config only.
- No live RPC in unit tests (`*Test.java`) — always Mockito-mocked.
- No raw `innerHTML`/sanitizer changes; this is backend-only.
- Priority-fee failure is **fail-safe** (baseline), never silently green on a compliance
  decision; the compliance gate itself stays **fail-closed**.

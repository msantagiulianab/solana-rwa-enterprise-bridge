# Solana RWA Enterprise Bridge

Enterprise-grade bridge between off-chain Spring Boot infrastructure (KYC/AML compliance, audit, PostgreSQL) and the Solana blockchain (Devnet RPC). Strict Test-Driven Development, off-chain compliance gatekeeping, and immutable audit logging.

<p align="center">
  <img src="assets/images/SolanaRWA-17082026.gif" alt="Solana RWA Enterprise Bridge Preview" width="100%" />
</p>

## What It Solves

The Solana RWA Enterprise Bridge is a compliance-first rails for tokenizing real-world assets (RWAs) on Solana with **automated KYC/AML enforcement**.

- **Issuers** register and value off-chain assets (`POST /api/tokens`).
- **Investors** are onboarded through a KYC/AML gatekeeper that evaluates eligibility before any on-chain action (`POST /api/v1/compliance/check`).
- **Every decision** — approved or blocked — is written to an immutable audit trail, and no Solana RPC dispatch happens before off-chain compliance passes (fail-closed).

The result is an auditable, regulator-friendly flow that keeps unvetted counterparties away from the RPC/settlement layer while delegating all signing to the user's own wallet.

## Architecture

```
┌──────────────────────────────────────┐       HTTP       ┌─────────────────────────────────┐
│  Angular 18+ Frontend               │ ───────────────► │  Spring Boot 3.5 Backend (Java 21) │
│  (standalone, Tailwind CSS,          │                  │  - Compliance Gatekeeper          │
│   @solana/web3.js, Phantom wallet    │                  │  - Audit Logs (immutable)         │
│   extension + Mobile Deep Linking)   │                  │  - Idempotency keys               │
│  ── Hosted on Vercel                │                  │  - SolanaRpcAdapter (JSON-RPC)    │
│     solana-rwa-enterprise-bridge     │                  │  ── Hosted on Render              │
│     .vercel.app                      │                  │     solana-rwa-enterprise-bridge  │
└──────────────────────────────────────┘                  │     .onrender.com/api             │
           │                                              │           │                       │
           │  Mobile: Phantom Universal Link              │           ▼                       │
           │  https://phantom.app/ul/browse/…             │  ┌──────────────────┐             │
           ▼                                              │  │ Neon Serverless  │             │
   Phantom Mobile in-app browser                          │  │ PostgreSQL       │             │
   (deep-link into the SPA)                               │  └──────────────────┘             │
                                                          │           │                       │
                                                          │           ▼                       │
                                                          │  ┌──────────────────┐             │
                                                          │  │ Solana Devnet    │             │
                                                          │  │ JSON-RPC         │             │
                                                          │  └──────────────────┘             │
                                                          └──────────────────────────────────┘
```

| Layer | Technology | Hosting |
|-------|-----------|---------|
| Frontend | Angular 18+ (Standalone Components), Tailwind CSS 3.4, @solana/web3.js | [Vercel](https://solana-rwa-enterprise-bridge.vercel.app) |
| Backend | Spring Boot 3.5, Java 21, Spring Data JPA, Lombok | [Render](https://solana-rwa-enterprise-bridge.onrender.com/api) |
| Database | PostgreSQL (Neon Serverless – production; Docker PostgreSQL 16 – local dev) | Neon / Docker |
| Blockchain | Solana Devnet JSON-RPC (pure-Java wire serializer, dynamic rent exemption, `confirmed` commitment + blockhash retry) | api.devnet.solana.com |
| Wallet | Phantom browser extension (desktop) + Phantom universal deep link (mobile) | phantom.app |

## Live Links & Access

| Resource | URL |
|----------|-----|
| Frontend (production) | `https://solana-rwa-enterprise-bridge.vercel.app` |
| Backend API (production) | `https://solana-rwa-enterprise-bridge.onrender.com/api` |
| Backend health | `https://solana-rwa-enterprise-bridge.onrender.com/actuator/health` |
| Solana RPC (Devnet) | `https://api.devnet.solana.com` |

### Testing the desktop flow (Phantom extension)

1. Open `https://solana-rwa-enterprise-bridge.vercel.app` in Chrome/Edge/Firefox with the [Phantom browser extension](https://phantom.app/) installed.
2. The header shows **Connect Wallet**; clicking it invokes the Phantom provider's `connect()` and displays the truncated public key.
3. Use **Asset Tokens**, **Investor KYC**, and **Audit Logs** to tokenize an asset, register an investor, and review the immutable audit trail.

### Testing the mobile flow (Phantom universal deep link)

1. Open the production URL in a **mobile browser** (Android/iOS).
2. `SolanaWalletService.isMobileDevice()` detects the mobile user agent; when the Phantom extension is absent, the header renders **Connect via Phantom App** instead of **Install Phantom**.
3. That button points to Phantom's official universal link:

   ```
   https://phantom.app/ul/browse/{encodeURIComponent(currentUrl)}?ref={encodeURIComponent(currentUrl)}
   ```

   which hands the dApp back to Phantom's **in-app browser** so users can connect the mobile wallet and interact with the SPA.

> Read endpoints (`GET`) are intentionally public for the audit/ledger viewers. Mutating requests (`POST`/`PATCH`/`PUT`/`DELETE`) require the shared `X-API-Key` header.

## Features & Endpoints

### REST API Surface (`/api/*`)

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `GET` | `/api/tokens` | List all asset tokens |
| `POST` | `/api/tokens` | Create a new asset token (`{ assetName, valuationUsd, issuerWalletAddress }`) — fail-closed compliance gate → audit log → on-chain SPL mint |
| `GET` | `/api/tokens/{id}` | Get token by UUID |
| `GET` | `/api/investors` | List all registered investors |
| `POST` | `/api/investors` | Register an investor (`{ fullName, email, walletAddress, country, kycStatus }`) |
| `GET` | `/api/investors/{id}` | Get investor by UUID |
| `PATCH` | `/api/investors/{id}/status` | Update investor KYC status (`{ kycStatus }`) + audit log on `VERIFIED` |
| `GET` | `/api/audit-logs` | List all immutable audit trail entries |
| `POST` | `/api/v1/compliance/check` | Evaluate investor eligibility (off-chain KYC + on-chain wallet existence) |
| `GET` | `/api/v1/compliance/audit-logs/{walletAddress}` | Compliance history for a specific wallet |
| `GET` | `/api/v1/compliance/audit-logs/export?format={csv\|json}&assetId={id}&startDate={iso}&endDate={iso}` | Stream the immutable settlement-proof audit ledger as deterministic RFC-4180 CSV or JSON |
| `POST` | `/api/v1/settlement/simulate` | Pre-flight dry-run of a raw base64 wire transaction via `simulateTransaction` → `SimulationResultDto` (units consumed, logs, +15% recommended CU limit); fail-closed `422`/`502` |

### Frontend Feature Views (SPA Routing)

| Path | Component | Purpose |
|------|-----------|---------|
| `/tokens` | `AssetTokenizationComponent` | Asset token dashboard — view tokens, tokenize new assets |
| `/investors` | `InvestorKycComponent` | Investor KYC registration, APPROVE/REJECT management |
| `/audit-logs` | `AuditLogComponent` | Immutable audit trail viewer with search & status filters |

## Compliance Gate (Fail-Closed KYC/AML)

Every on-chain mint is protected by a **pre-flight verification gate** enforced in
[`TokenService.java`](backend/src/main/java/com/solana/rwa/bridge/service/TokenService.java)
before any binary serialization or `SolanaMintService.createMint()` dispatch. The issuer wallet
is validated against the off-chain registry and the Solana Devnet ledger, and any failure aborts
the request with `422 Unprocessable Entity` — so downstream Solana execution is never reached.

| Fail-closed rule | Result |
|------------------|--------|
| Issuer not registered | `422` — `Tokenization blocked by compliance: Investor not registered` |
| Issuer KYC `REJECTED` | `422` — `... Investor KYC status is REJECTED` |
| Issuer KYC `FLAGGED_SANCTION` | `422` — `... Investor is flagged for sanctions screening` |
| Issuer KYC not `VERIFIED` (e.g. `PENDING`) | `422` — `... Investor KYC verification is not complete ...` |
| Wallet absent on-chain (`getAccountInfo().exists() == false`) | `422` — `... Wallet does not exist on Solana chain` |
| Solana RPC unavailable (`SolanaRpcException`) | `422` — `... Solana RPC unavailable - on-chain verification failed` |

The gate is deliberately **fail-closed**: an RPC outage blocks the attempt rather than silently
approving, and no Solana Devnet bytes are emitted until every check passes. Unit coverage in
`TokenServiceTest` asserts `verifyNoInteractions(solanaMintService)` (plus `solanaRpcAdapter` and
`assetTokenRepository`) on every blocked path, proving zero Devnet bytes are emitted on compliance
failure.

### Immutable audit logging

Every compliance decision is persisted through `AuditLogRepository` as an immutable
[`AuditLog`](backend/src/main/java/com/solana/rwa/bridge/entity/AuditLog.java) record with an
`APPROVED`/`BLOCKED` status, action, reason, and a non-updatable `timestamp`. `ComplianceService`
writes a `CHECK_ELIGIBILITY` record on every evaluation, and a successful `POST /api/tokens`
writes a `TOKENIZE_ASSET`/`APPROVED` record attributed to the on-chain mint address — while
blocked issuers are rejected before any record write or mint occurs.

### Compliance & settlement-proof export

The immutable settlement-proof ledger is also exposed to institutional auditors as a deterministic, streamed download:

- `GET /api/v1/compliance/audit-logs/export?format=csv|json&assetId={id}&startDate={iso}&endDate={iso}&status={SUCCESS|FAILED_COMPLIANCE|FAILED_RPC}`
- `format=csv` returns **RFC-4180 CSV** — canonical header, CRLF row terminators, and correct comma/quote/newline escaping — as `text/csv`.
- `format=json` returns a **deterministic, schema-compliant JSON array** — stable field order, ISO-8601 timestamps, explicit `null` for absent settlement proofs, and `[]` for zero records — as `application/json`.
- Both formats stream as a `Content-Disposition: attachment` download (`audit-export-<UTC timestamp>.csv|json`) with `Cache-Control: no-cache`.
- **Zero third-party CSV/export dependencies:** exporters rely on standard Java 21 string/stream primitives for deterministic byte formatting.
- Fail-closed validation rejects an unsupported `format` or non-ISO-8601 `startDate`/`endDate` with a structured `400`.

## Pre-Flight Transaction Simulation & Rehearsal

Enterprise operators can dry-run a raw, base64-serialized Solana wire transaction against the
Devnet RPC **before** any funds are committed or broadcast, using the node's
`simulateTransaction` RPC:

- `POST /api/v1/settlement/simulate` with `{ "encodedTransaction": "<base64 wire tx>" }`
- Returns a structured `SimulationResultDto`: `success`, `unitsConsumed`, `logs`, and
  `recommendedComputeUnitLimit`.
- The JSON-RPC payload uses the fail-safe simulation config `sigVerify: false`,
  `encoding: "base64"`, and `replaceRecentBlockhash: true`.

### Compute-unit headroom buffering

The rehearsal extracts the exact `unitsConsumed` and pads it with a **+15% safety margin**
(rounded up) before recommending `recommendedComputeUnitLimit`, so a subsequent broadcast never
lands short on budget due to scheduling variance:

```
recommendedComputeUnitLimit = ceil(unitsConsumed × 1.15)
```

### Fail-closed error mapping

A dedicated `SimulationExceptionHandler` (`@Order(HIGHEST_PRECEDENCE)`) maps every rehearsal
failure to a sanitized, non-`2xx` response so an unavailable node is never mistaken for a
successful dry-run:

| Condition | HTTP |
|-----------|------|
| Blank/invalid base64 body | `400 Bad Request` |
| Missing/invalid `X-API-Key` | `401 Unauthorized` |
| Reverted dry-run (structured Solana error) | `422 Unprocessable Entity` with `errorType`, `instructionIndex`, `programError`, `programErrorCode`, `unitsConsumed`, and `logs` |
| Upstream RPC outage (`SolanaRpcException`) | `502 Bad Gateway` (fail-closed) |

The engine uses zero third-party Solana SDKs — JSON-RPC 2.0 payloads are assembled and parsed
with standard Java 21 + Jackson primitives only.

## Backend (Spring Boot 3.5 / Java 21)

Located in [`backend/`](backend/).

| Area | Choice |
|------|--------|
| Build | Maven (Java 21, Spring Boot 3.5.x) |
| Dependencies | Spring Web, Spring Data JPA, PostgreSQL Driver, Lombok, Bean Validation, Actuator |
| Database (local) | PostgreSQL 16 via `docker-compose.yml` |
| Database (production) | Neon Serverless PostgreSQL |
| Config | `application.yml` — all secrets & endpoints injected from environment variables |

### Run locally

```bash
# 1. Start PostgreSQL
docker compose up -d

# 2. Copy env template and export/load variables (or use your IDE env runner)
cp .env.example .env

# 3. Start the backend (uses the bundled Maven wrapper; no Maven install needed)
cd backend
./mvnw spring-boot:run
# Windows: mvnw.cmd spring-boot:run
```

Backend defaults to `http://localhost:8080`.

### Configuration (environment variables)

| Variable | Default | Purpose |
|----------|---------|---------|
| `SERVER_PORT` | `8080` | HTTP server port |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/rwa_db` | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | DB user |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` | DB password |
| `SOLANA_DEVNET_RPC_URL` | `https://api.devnet.solana.com` | Solana Devnet JSON-RPC endpoint |
| `SOLANA_DEVNET_PRIVATE_KEY` | *(none)* | Base58 keypair — **never commit** |
| `SECURITY_API_KEY` | *(none)* | Shared API key required on POST/PATCH/PUT/DELETE routes |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_PORT` | `rwa_db` / `postgres` / `postgres` / `5432` | `docker-compose.yml` |

## Docker Compose

Root `docker-compose.yml` provides a local PostgreSQL 16 (`solana-rwa-postgres`) with a persisted named volume and a `pg_isready` healthcheck.

```bash
docker compose up -d      # start
docker compose down       # stop (data persists)
docker compose down -v    # stop + wipe volume
```

## Frontend (Angular 18+ / TypeScript)

Located in [`frontend/`](frontend/).

| Area | Choice |
|------|--------|
| Framework | Angular 18+ (Standalone Components, no NgModule) |
| Styling | Tailwind CSS 3.4 (PostCSS + Autoprefixer) |
| Web3 | `@solana/web3.js` (Phantom browser wallet integration) |
| API | Render backend at `https://solana-rwa-enterprise-bridge.onrender.com/api` |
| Hosting | Vercel (`https://solana-rwa-enterprise-bridge.vercel.app`) |

### Connected wallet → tokenization payload

The tokenization form binds the active Phantom wallet to the backend contract:

- `CreateAssetTokenRequest` now carries `issuerWalletAddress: string` alongside `assetName` and
  `valuationUsd` (see [`asset-token.model.ts`](frontend/src/app/shared/models/asset-token.model.ts)).
- `AssetTokenizationComponent` subscribes to `SolanaWalletService.connectedPublicKey$` and keeps
  `issuerWalletAddress` synchronized with the live wallet (auto-cleared on disconnect/account
  change, never persisted).
- A client-side wallet guard rejects tokenization before any HTTP call when no wallet is
  connected (`"Please connect your wallet to tokenize an asset."`), and the tokenize modal
  renders the connected issuer wallet or a "No wallet connected" notice.

### Run locally

```bash
cd frontend
npm install
npm start   # starts at http://localhost:4200
```

Or from the repo root:

```bash
npm --prefix frontend start
```

### Build pipeline

The Vercel build is driven by [`frontend/vercel.json`](frontend/vercel.json) — `buildCommand: "npm run build"` plus SPA rewrites (`/(.*)` → `/index.html`) for client-side routing.

`npm run build` runs `ng build`, and the `prebuild` hook first executes [`scripts/generate-environment.js`](frontend/scripts/generate-environment.js):

- Reads `SECURITY_API_KEY` from the build environment.
- Bakes it into `src/environments/environment.prod.ts` (via Angular `fileReplacements`).
- **Fails the build if `SECURITY_API_KEY` is missing**, so the `X-API-Key` gate can never be silently disabled in a deployed artifact.

Set `SECURITY_API_KEY` in the Vercel project settings (or any build host) before deploying.

### API key injection (mutating routes)

Mutating requests (`POST`/`PATCH`/`PUT`/`DELETE`) are gated by the backend's `X-API-Key` header. The Angular [`apiKeyInterceptor`](frontend/src/app/shared/interceptors/api-key.interceptor.ts) attaches this header on mutating requests only, sourcing the key from `environment.apiKey`.

- **Production build:** `SECURITY_API_KEY` is injected at build time via `generate-environment.js` (see above).
- **Local development:** `ng serve` (via `npm start`) uses the tracked `environment.development.ts`, which defaults `apiKey` to an empty string so read-only endpoints work without a key. To exercise mutating endpoints locally against the Render backend, either build with the production configuration (`npm run build`) to inject `SECURITY_API_KEY`, or temporarily set the `apiKey` field in `environment.development.ts` (do not commit a real key).

## Feature Status

| Status | Feature |
|--------|---------|
| ✅ Done | Spring Boot 3 backend: env-driven config, PostgreSQL via Docker Compose |
| ✅ Done | JPA domain layer: `Investor`, `AssetToken`, `AuditLog` entities + Spring Data JPA repositories |
| ✅ Done | Compliance engine: DTOs with Bean Validation, `ComplianceService` gatekeeper with mandatory audit logging, `/api/v1/compliance/*` and `/api/investors` REST controllers, `GlobalExceptionHandler` |
| ✅ Done | Solana Devnet RPC layer: `SolanaRpcAdapter` (`getAccountInfo`, `getTokenAccountBalance`, `getLatestBlockhash`, `getMinimumBalanceForRentExemption`, `sendTransaction`) via JSON-RPC, graceful failure mapping to `SolanaRpcException`, on-chain wallet existence gate inside `ComplianceService` (fail-closed) |
| ✅ Done | On-chain token minting: `SolanaMintService` issues a real SPL Token `InitializeMint` on Devnet via an atomic 2-instruction payload, persisted to `AssetToken.mintAddress` and linked in the frontend |
| ✅ Done | Canonical Solana wire serializer: 4-category account classification (writable/readonly signers, writable/readonly non-signers) with header bytes derived directly from the compiled account table |
| ✅ Done | Dynamic rent exemption & confirmed commitment: `getMinimumBalanceForRentExemption(82)` with fallback, `confirmed` blockhash/preflight, and up to 3 fresh-blockhash retries on "Blockhash not found" |
| ✅ Done | Dynamic compute budget & priority fee optimization: `setComputeUnitPrice` + `setComputeUnitLimit` Compute Budget instructions prefixed to the SPL mint payload, priced via `getRecentPrioritizationFees` 75th percentile with a configurable baseline fallback |
| ✅ Done | Render deployment: `Dockerfile` (multi-stage Java 21), `render.yaml` Blueprint, CORS for Vercel origins |
| ✅ Done | Angular frontend UI scaffold: Asset Tokenization, Investor KYC, Audit Log viewer; Tailwind CSS dark theme; `@solana/web3.js` integrated |
| ✅ Done | Angular frontend feature implementation: Phantom wallet integration, tokenize asset form/modal, investor APPROVE/REJECT buttons, audit log search/filter |
| ✅ Done | Vercel production deployment: SPA hosting with `vercel.json` rewrites + build-time `SECURITY_API_KEY` injection |
| ✅ Done | Security hardening: `X-API-Key` mutating-route gate, sanitized exception handling, actuator/CORS lockdown, typed domain exceptions |
| ✅ Done | Mobile Phantom universal deep linking: `buildPhantomDeepLink()` redirects mobile users into Phantom's in-app browser |
| ✅ Done | Fail-closed KYC/AML pre-flight compliance gate in `TokenService.create` — unregistered / non-`VERIFIED` / `FLAGGED_SANCTION` investors, absent on-chain accounts, and RPC outages all throw `422` before any mint serialization (`0991822`) |
| ✅ Done | End-to-end connected-wallet tokenization: `issuerWalletAddress` added to `CreateAssetTokenRequest`, client-side wallet guard, and live sync with `SolanaWalletService.connectedPublicKey$` (`ea39d66`) |
| ✅ Done | Enterprise compliance & settlement audit export: `GET /api/v1/compliance/audit-logs/export` streams the immutable settlement-proof ledger as RFC-4180 CSV or deterministic JSON with zero third-party export dependencies (`7eacd75`) |
| ✅ Done | Pre-flight transaction simulation & rehearsal engine: `POST /api/v1/settlement/simulate` dry-runs a raw base64 wire transaction via `simulateTransaction` with +15% compute-unit headroom and fail-closed `422`/`502` mapping (`78bcbde`) |

## Render Deployment

### Blueprint

The repo includes a [`render.yaml`](render.yaml) Blueprint at the root and a multi-stage [`backend/Dockerfile`](backend/Dockerfile). Connect the repository to [Render](https://render.com) and the Blueprint will auto-provision:

- **Web Service `solana-rwa-bridge-api`** — Docker runtime, JRE 21, region `ohio`, healthcheck at `/actuator/health`

### Required Environment Variables (set in the Render dashboard)

| Variable | Purpose |
|----------|---------|
| `SPRING_PROFILES_ACTIVE` | Set to `prod` (Blueprint default) |
| `SPRING_DATASOURCE_URL` | Production PostgreSQL JDBC URL (Neon Serverless) |
| `SPRING_DATASOURCE_USERNAME` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | Database password |
| `SOLANA_DEVNET_RPC_URL` | Solana Devnet JSON-RPC endpoint |
| `SOLANA_DEVNET_PRIVATE_KEY` | Base58 keypair — **never commit** |
| `SECURITY_API_KEY` | Shared API key required on mutating routes |
| `SERVER_PORT` | Set to `8080` (Blueprint default) |

### CORS

CORS is configured globally in `WebConfig` (`backend/src/main/java/com/solana/rwa/bridge/config/`):

| Origin | Purpose |
|--------|---------|
| `https://*.vercel.app` | Vercel preview & production deployments |
| `https://*.onrender.com` | Render preview & production deployments |
| `http://localhost:4200` | Angular local development server |

`allowedHeaders` is an explicit allowlist (`Origin`, `Content-Type`, `Accept`, `Authorization`, `X-API-Key`), and `X-API-Key` is registered as the mutating-route auth header.

## Test Counts

| Suite | Count |
|-------|-------|
| Backend unit tests (`*Test.java`) | 128 |
| Backend integration tests (`*IT.java`) | 46 |
| Frontend specs | 47 |

**Backend total: 174 passing tests** (128 unit + 46 integration).

**Breakdown (unit):** `ComplianceServiceTest` (15) · `SolanaRpcAdapterTest` (23) · `ComplianceDtosValidationTest` (10) · `TokenServiceTest` (7) · `ComputeBudgetInstructionTest` (7) · `SolanaKeypairServiceTest` (6) · `SolanaMintServiceTest` (6) · `ApiKeyAuthInterceptorTest` (5) · `SolanaAddressValidatorTest` (5) · `SolanaTransactionSerializerTest` (1) · `AuditExportServiceTest` (9) · `ComplianceAuditExportControllerTest` (7) · `CsvAuditExporterTest` (6) · `JsonAuditExporterTest` (4) · `SimulationPayloadTest` (5) · `TransactionSimulationServiceTest` (6) · `TransactionSimulationControllerTest` (6)

**Breakdown (integration):** `ComplianceControllerIT` (10) · `InvestorControllerIT` (10) · `InvestorRepositoryIT` (8) · `AssetTokenControllerIT` (6) · `AssetTokenRepositoryIT` (6) · `AuditLogRepositoryIT` (6)

**Breakdown (frontend):** `AssetTokenizationComponent` (13) · `AuditLogComponent` (11) · `AppComponent` (9) · `InvestorKycComponent` (8) · `SolanaWalletService` (4) · `apiKeyInterceptor` (2)

*Counts are updated automatically per the project's TDD automation protocol.*

## Conventions

- Commits: `type(scope): description` (feat, test, fix, refactor, docs, chore)
- Unit tests (`*Test.java`) run on every build — Solana RPC **always mocked**.
- Integration tests (`*IT.java`) exercise Spring context, JPA/H2/Postgres, and Devnet.
- No RPC call before off-chain compliance passes; every attempt is audit-logged.
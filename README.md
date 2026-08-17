# Solana RWA Enterprise Bridge

Enterprise-grade bridge between off-chain Spring Boot infrastructure (KYC/AML compliance, audit, PostgreSQL) and the Solana blockchain (Devnet RPC). Strict Test-Driven Development, off-chain compliance gatekeeping, and immutable audit logging.

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
| `POST` | `/api/tokens` | Create a new asset token (`{ assetName, valuationUsd }`) + write audit log + issue on-chain SPL mint |
| `GET` | `/api/tokens/{id}` | Get token by UUID |
| `GET` | `/api/investors` | List all registered investors |
| `POST` | `/api/investors` | Register an investor (`{ fullName, email, walletAddress, country, kycStatus }`) |
| `GET` | `/api/investors/{id}` | Get investor by UUID |
| `PATCH` | `/api/investors/{id}/status` | Update investor KYC status (`{ kycStatus }`) + audit log on `VERIFIED` |
| `GET` | `/api/audit-logs` | List all immutable audit trail entries |
| `POST` | `/api/v1/compliance/check` | Evaluate investor eligibility (off-chain KYC + on-chain wallet existence) |
| `GET` | `/api/v1/compliance/audit-logs/{walletAddress}` | Compliance history for a specific wallet |

### Frontend Feature Views (SPA Routing)

| Path | Component | Purpose |
|------|-----------|---------|
| `/tokens` | `AssetTokenizationComponent` | Asset token dashboard — view tokens, tokenize new assets |
| `/investors` | `InvestorKycComponent` | Investor KYC registration, APPROVE/REJECT management |
| `/audit-logs` | `AuditLogComponent` | Immutable audit trail viewer with search & status filters |

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
| ✅ Done | Render deployment: `Dockerfile` (multi-stage Java 21), `render.yaml` Blueprint, CORS for Vercel origins |
| ✅ Done | Angular frontend UI scaffold: Asset Tokenization, Investor KYC, Audit Log viewer; Tailwind CSS dark theme; `@solana/web3.js` integrated |
| ✅ Done | Angular frontend feature implementation: Phantom wallet integration, tokenize asset form/modal, investor APPROVE/REJECT buttons, audit log search/filter |
| ✅ Done | Vercel production deployment: SPA hosting with `vercel.json` rewrites + build-time `SECURITY_API_KEY` injection |
| ✅ Done | Security hardening: `X-API-Key` mutating-route gate, sanitized exception handling, actuator/CORS lockdown, typed domain exceptions |
| ✅ Done | Mobile Phantom universal deep linking: `buildPhantomDeepLink()` redirects mobile users into Phantom's in-app browser |

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
| Backend unit tests (`*Test.java`) | 63 |
| Backend integration tests (`*IT.java`) | 44 |
| Frontend specs | 46 |

**Breakdown (unit):** `ComplianceServiceTest` (15) · `SolanaRpcAdapterTest` (17) · `ComplianceDtosValidationTest` (7) · `SolanaAddressValidatorTest` (5) · `ApiKeyAuthInterceptorTest` (5) · `SolanaKeypairServiceTest` (6) · `SolanaMintServiceTest` (6) · `SolanaTransactionSerializerTest` (1) · `TokenServiceTest` (1)

**Breakdown (integration):** `InvestorRepositoryIT` (8) · `AssetTokenRepositoryIT` (6) · `AuditLogRepositoryIT` (6) · `ComplianceControllerIT` (10) · `InvestorControllerIT` (10) · `AssetTokenControllerIT` (4)

**Breakdown (frontend):** `AppComponent` (9) · `AuditLogComponent` (11) · `AssetTokenizationComponent` (12) · `InvestorKycComponent` (8) · `SolanaWalletService` (4) · `apiKeyInterceptor` (2)

*Counts are updated automatically per the project's TDD automation protocol.*

## Conventions

- Commits: `type(scope): description` (feat, test, fix, refactor, docs, chore)
- Unit tests (`*Test.java`) run on every build — Solana RPC **always mocked**.
- Integration tests (`*IT.java`) exercise Spring context, JPA/H2/Postgres, and Devnet.
- No RPC call before off-chain compliance passes; every attempt is audit-logged.
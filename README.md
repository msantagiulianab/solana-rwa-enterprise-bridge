# Solana RWA Enterprise Bridge

Enterprise-grade bridge between off-chain Spring Boot infrastructure (KYC/AML compliance, audit, PostgreSQL) and the Solana blockchain (Devnet RPC). Strict Test-Driven Development, off-chain compliance gatekeeping, and immutable audit logging.

## Architecture

```
┌──────────────────────────────────────┐       HTTP       ┌─────────────────────────────────┐
│  Angular 17.3 Frontend               │ ───────────────► │  Spring Boot 3.3 Backend (Java 17) │
│  (standalone, Tailwind CSS,          │                  │  - Compliance Gatekeeper          │
│   @solana/web3.js, Phantom wallet)   │                  │  - Audit Logs (immutable)         │
│  ── Hosted on Vercel                │                  │  - Idempotency keys               │
│     solana-rwa-enterprise-bridge     │                  │  - SolanaRpcAdapter (JSON-RPC)    │
│     .vercel.app                      │                  │  ── Hosted on Render              │
└──────────────────────────────────────┘                  │     solana-rwa-enterprise-bridge  │
                                                          │     .onrender.com/api             │
                                                          │           │                       │
                                                          │           ▼                       │
                                                          │  ┌──────────────────┐             │
                                                          │  │ Neon Serverless  │             │
                                                          │  │ PostgreSQL       │             │
                                                          │  └──────────────────┘             │
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
| Frontend | Angular 17.3 (Standalone Components), Tailwind CSS 3.4, @solana/web3.js | [Vercel](https://solana-rwa-enterprise-bridge-3oj71x1cv.vercel.app) |
| Backend | Spring Boot 3.3, Java 17, Spring Data JPA, Lombok | [Render](https://solana-rwa-enterprise-bridge.onrender.com/api) |
| Database | PostgreSQL (Neon Serverless – production; Docker PostgreSQL 16 – local dev) | Neon / Docker |
| Blockchain | Solana Devnet JSON-RPC (SolanaRpcAdapter with pure Mockito unit tests) | api.devnet.solana.com |

## Features & Endpoints

### REST API Surface (`/api/*`)

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `GET` | `/api/tokens` | List all asset tokens |
| `POST` | `/api/tokens` | Create a new asset token (`{ assetName, valuationUsd }`) |
| `GET` | `/api/tokens/{id}` | Get token by UUID |
| `GET` | `/api/investors` | List all registered investors |
| `POST` | `/api/investors` | Register an investor (`{ fullName, email, walletAddress, country, kycStatus }`) |
| `PATCH` | `/api/investors/{id}/status` | Update investor KYC status (`{ kycStatus }`) |
| `GET` | `/api/audit-logs` | List all immutable audit trail entries |
| `POST` | `/api/v1/compliance/check` | Evaluate investor eligibility (off-chain KYC + on-chain wallet existence) |
| `GET` | `/api/v1/compliance/audit-logs/{walletAddress}` | Compliance history for a specific wallet |

### Frontend Feature Views (SPA Routing)

| Path | Component | Purpose |
|------|-----------|---------|
| `/tokens` | `AssetTokenizationComponent` | Asset token dashboard — view tokens, tokenize new assets |
| `/investors` | `InvestorKycComponent` | Investor KYC registration, APPROVE/REJECT management |
| `/audit-logs` | `AuditLogComponent` | Immutable audit trail viewer with search & status filters |

## Backend (Spring Boot 3 / Java 17)

Located in [`backend/`](backend/).

| Area | Choice |
|------|--------|
| Build | Maven (Java 17, Spring Boot 3.3.x) |
| Dependencies | Spring Web, Spring Data JPA, PostgreSQL Driver, Lombok, Bean Validation |
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
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_PORT` | `rwa_db` / `postgres` / `postgres` / `5432` | `docker-compose.yml` |

## Docker Compose

Root `docker-compose.yml` provides a local PostgreSQL 16 (`solana-rwa-postgres`) with a persisted named volume and a `pg_isready` healthcheck.

```bash
docker compose up -d      # start
docker compose down       # stop (data persists)
docker compose down -v    # stop + wipe volume
```

## Frontend (Angular 17.3 / TypeScript)

Located in [`frontend/`](frontend/).

| Area | Choice |
|------|--------|
| Framework | Angular 17.3 (Standalone Components, no NgModule) |
| Styling | Tailwind CSS 3.4 (PostCSS + Autoprefixer) |
| Web3 | `@solana/web3.js` (Phantom browser wallet integration) |
| API | Render backend at `https://solana-rwa-enterprise-bridge.onrender.com/api` |
| Hosting | Vercel (`https://solana-rwa-enterprise-bridge-3oj71x1cv.vercel.app`) |

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

### Build for production

```bash
npm --prefix frontend run build   # outputs to frontend/dist/frontend/
```

The `vercel.json` at the frontend root configures SPA rewrites (`/(.*)` → `/index.html`) for client-side routing.

## Feature Status

| Status | Feature |
|--------|---------|
| ✅ Done | Spring Boot 3 backend: env-driven config, PostgreSQL via Docker Compose |
| ✅ Done | JPA domain layer: `Investor`, `AssetToken`, `AuditLog` entities + Spring Data JPA repositories |
| ✅ Done | Compliance engine: DTOs with Bean Validation, `ComplianceService` gatekeeper with mandatory audit logging, `/api/v1/compliance/*` and `/api/investors` REST controllers, `GlobalExceptionHandler` |
| ✅ Done | Solana Devnet RPC layer: `SolanaRpcAdapter` (`getAccountInfo`, `getTokenAccountBalance`) via JSON-RPC, graceful failure mapping to `SolanaRpcException`, on-chain wallet existence gate inside `ComplianceService` (fail-closed) |
| ✅ Done | Render deployment: `Dockerfile` (multi-stage Java 17), `render.yaml` Blueprint, CORS for Vercel origins |
| ✅ Done | Angular frontend UI scaffold: Asset Tokenization, Investor KYC, Audit Log viewer; Tailwind CSS dark theme; `@solana/web3.js` integrated |
| ✅ Done | Angular frontend feature implementation (Phase 4.2): Phantom wallet integration, tokenize asset form/modal, investor APPROVE/REJECT buttons, audit log search/filter; 37 frontend specs GREEN |
| ✅ Done | Vercel production deployment: SPA hosting with `vercel.json` rewrites, Angular build → `frontend/dist/frontend/` |

## Render Deployment

### Blueprint

The repo includes a [`render.yaml`](render.yaml) Blueprint at the root and a multi-stage [`backend/Dockerfile`](backend/Dockerfile). Connect the repository to [Render](https://render.com) and the Blueprint will auto-provision:

- **Web Service `solana-rwa-bridge-api`** — Docker runtime, JRE 17, region `ohio`, healthcheck at `/actuator/health`

### Required Environment Variables (set in the Render dashboard)

| Variable | Purpose |
|----------|---------|
| `SPRING_PROFILES_ACTIVE` | Set to `prod` (Blueprint default) |
| `SPRING_DATASOURCE_URL` | Production PostgreSQL JDBC URL (Neon Serverless) |
| `SPRING_DATASOURCE_USERNAME` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | Database password |
| `SOLANA_DEVNET_RPC_URL` | Solana Devnet JSON-RPC endpoint |
| `SOLANA_DEVNET_PRIVATE_KEY` | Base58 keypair — **never commit** |
| `SERVER_PORT` | Set to `8080` (Blueprint default) |

### CORS

CORS is configured globally in `WebConfig` (`backend/src/main/java/com/solana/rwa/bridge/config/`):

| Origin | Purpose |
|--------|---------|
| `https://*.vercel.app` | Vercel preview & production deployments |
| `http://localhost:4200` | Angular local development server |

## Test Counts

| Suite | Count |
|-------|-------|
| Backend unit tests (`*Test.java`) | 36 |
| Backend integration tests (`*IT.java`) | 41 |
| Frontend specs | 37 |

**Breakdown (unit):** `ComplianceServiceTest` (15) · `SolanaAddressValidatorTest` (5) · `ComplianceDtosValidationTest` (7) · `SolanaRpcAdapterTest` (9)

**Breakdown (integration):** `InvestorRepositoryIT` (8) · `AssetTokenRepositoryIT` (6) · `AuditLogRepositoryIT` (6) · `ComplianceControllerIT` (8) · `InvestorControllerIT` (9) · `AssetTokenControllerIT` (4)

**Breakdown (frontend):** `AppComponent` (8) · `AssetTokenizationComponent` (8) · `InvestorKycComponent` (8) · `AuditLogComponent` (9) · `SolanaWalletService` (4)

*Counts are updated automatically per the project's TDD automation protocol.*

## Conventions

- Commits: `type(scope): description` (feat, test, fix, refactor, docs, chore)
- Unit tests (`*Test.java`) run on every build — Solana RPC **always mocked**.
- Integration tests (`*IT.java`) exercise Spring context, JPA/H2/Postgres, and Devnet.
- No RPC call before off-chain compliance passes; every attempt is audit-logged.
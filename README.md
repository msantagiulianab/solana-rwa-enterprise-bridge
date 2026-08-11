# Solana RWA Enterprise Bridge

Enterprise-grade bridge between off-chain Spring Boot infrastructure (KYC/AML compliance, audit, PostgreSQL) and the Solana blockchain (Devnet RPC). Strict Test-Driven Development, off-chain compliance gatekeeping, and immutable audit logging.

## Architecture

```
┌────────────────────────────┐          ┌──────────────────────────────┐
│  Angular 18+ Frontend     │  HTTP    │  Spring Boot 3 Backend       │
│  (standalone, RxJS,       │ ───────► │  - Compliance Gatekeeper     │
│   Tailwind CSS)           │          │  - Audit Logs (immutable)    │
└────────────────────────────┘          │  - Idempotency keys          │
                                        │  - SolanaRpcService (mock)   │
                                        │           │  JSON-RPC        │
                                        │           ▼                  │
                                        │  PostgreSQL  │  Solana Devnet │
                                        └──────────────┴───────────────┘
```

## Backend (Spring Boot 3 / Java 17)

Located in [`backend/`](backend/).

| Area | Choice |
|------|--------|
| Build | Maven (Java 17, Spring Boot 3.3.x) |
| Dependencies | Spring Web, Spring Data JPA, PostgreSQL Driver, Lombok, Bean Validation |
| Database | PostgreSQL 16 (local via `docker-compose.yml`) |
| Config | `application.yml` — all secrets & endpoints from env vars |

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
# No wrapper? Requires Maven 3.9+ on PATH: mvn spring-boot:run
```

Backend defaults to `http://localhost:8080`.

### Configuration (environment variables)

| Variable | Default | Purpose |
|----------|---------|---------|
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

## Feature Status

| Status | Feature |
|--------|---------|
| 🏗 Scaffold | Spring Boot 3 backend, env-driven config, PostgreSQL via Docker Compose |
| ✅ Done | JPA domain layer: `Investor`, `AssetToken`, `AuditLog` entities + Spring Data JPA repositories |
| ✅ Done | Compliance engine: DTOs with Bean Validation, `ComplianceService` gatekeeper with mandatory audit logging, `/api/v1/compliance/*` and `/api/v1/investors` REST controllers, `GlobalExceptionHandler` |

## Test Counts

| Suite | Count |
|-------|-------|
| Backend unit tests (`*Test.java`) | 23 |
| Backend integration tests (`*IT.java`) | 34 |
| Frontend specs | 0 |

**Breakdown (unit):** `ComplianceServiceTest` (11) · `SolanaAddressValidatorTest` (5) · `ComplianceDtosValidationTest` (7)

**Breakdown (integration):** `InvestorRepositoryIT` (8) · `AssetTokenRepositoryIT` (6) · `AuditLogRepositoryIT` (6) · `ComplianceControllerIT` (8) · `InvestorControllerIT` (6)

*Counts are updated automatically per the project's TDD automation protocol.*

## Conventions

- Commits: `type(scope): description` (feat, test, fix, refactor, docs, chore)
- Unit tests (`*Test.java`) run on every build — Solana RPC **always mocked**.
- Integration tests (`*IT.java`) exercise Spring context, JPA/H2/Postgres, and Devnet.
- No RPC call before off-chain compliance passes; every attempt is audit-logged.

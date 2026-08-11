# Development Journal

Architectural decisions, test coverage, and Solana/Spring integration notes for the Solana RWA Enterprise Bridge.

## 2026-08-11

### Solana Devnet RPC Integration Layer (TDD, GREEN: 70 tests)

**Plan:** Implement a resilient JSON-RPC client (`SolanaRpcAdapter`) that reads `SOLANA_DEVNET_RPC_URL` from configuration and queries live Devnet state (`getAccountInfo`, `getTokenAccountBalance`), then integrate on-chain wallet existence verification into the compliance gatekeeper — all with pure Mockito unit tests that never touch the network during the build.

**Tests added (written first — RED → GREEN):**
- `SolanaRpcAdapterTest` (9, pure Mockito): successful `getAccountInfo` response parsing (owner/lamports/executable/space), absent wallet returns a non-existent `AccountInfo`, JSON-RPC error payload → `SolanaRpcException`, null/malformed response → `SolanaRpcException`, network timeout (`ResourceAccessException`) → `SolanaRpcException`, HTTP 502 → `SolanaRpcException`, successful `getTokenAccountBalance` parsing (amount/decimals/uiAmountString), missing token account → `SolanaRpcException`, JSON-RPC error payload on token query → `SolanaRpcException`. The `RestClient` fluent chain (`post().uri().contentType().body().retrieve().body(...)`) is fully mocked with Mockito — no live Devnet traffic is ever attempted.
- `ComplianceServiceTest` expanded 11 → 15: BLOCKED when wallet does not exist on-chain; BLOCKED (fail-closed) when the RPC layer is unavailable; RPC layer is **never** consulted when investor is REJECTED or asset is NON_COMPLIANT.

**Implementation:**
- `SolanaRpcAdapter` (`rpc` package) — Spring `@Service` wrapping a Spring 6.1 `RestClient`. Endpoint injected from `solana.rpc.url` (`${SOLANA_DEVNET_RPC_URL:https://api.devnet.solana.com}`). Methods: `getAccountInfo(walletAddress)` and `getTokenAccountBalance(tokenAccountAddress)`. Every interaction is wrapped in try-catch:
  - `ResourceAccessException` (timeout / unreachable) → `SolanaRpcException`
  - `RestClientResponseException` (HTTP error) → `SolanaRpcException`
  - null/malformed JSON-RPC envelope → `SolanaRpcException`
  - JSON-RPC `error` payload → `SolanaRpcException`
  - missing token account (`value == null`) → `SolanaRpcException`
- JSON-RPC DTO records (`rpc/dto`): `RpcEnvelope<T>` (with `hasError()`/`isMalformed()`), `RpcError`, `RpcContext`, `AccountInfo` (with `exists()`), `AccountInfoResult`, `TokenAccountBalance`, `TokenAccountBalanceResult` — all `@JsonIgnoreProperties(ignoreUnknown = true)`.
- `SolanaRpcException` — custom runtime exception with method-aware message constructors; treated as fail-closed by callers.

**Decisions:**
- **Fail-closed integration:** `ComplianceService.verifyEligibility` now calls `solanaRpcAdapter.getAccountInfo(walletAddress).exists()` only AFTER all off-chain KYC/asset checks pass. If the wallet does not exist, or the RPC layer throws `SolanaRpcException`, the check is BLOCKED and audit-logged — never silently approved.
- **Off-chain gatekeeping preserved:** the RPC layer is never invoked for investors that are not `VERIFIED` or assets that are not `COMPLIANT` (asserted via `verifyNoInteractions(solanaRpcAdapter)`).
- **Mockito strictness fix:** stubbing `RestClient.RequestBodySpec.body(any())` binds to the `body(MultiValueMap)` overload (null literal), causing `PotentialStubbingProblem` when the adapter calls `body(Object)`. Fixed with `doReturn(bodySpec).when(bodySpec).body(any(Object.class))` to bind the correct overload.
- Every RPC attempt/decision remains audit-logged per the immutable audit trail rule.

### Scaffold: Spring Boot 3 Backend & Local PostgreSQL


**Decisions:**
- Established a Maven multi-layer backend under `backend/` with Java 17 + Spring Boot 3.3.x.
- Dependencies selected: Spring Web, Spring Data JPA, PostgreSQL Driver (runtime), Lombok, Bean Validation, and `spring-boot-starter-test` (JUnit 5, Mockito).
- `application.yml` contains **zero hardcoded secrets**. Datasource URL/username/password, server port, and Solana Devnet RPC URL/private key are all injected via environment variables (`SPRING_DATASOURCE_*`, `SOLANA_DEVNET_*`) with local-dev defaults only.
- `hibernate.ddl-auto: validate` — schema drift will be rejected; migrations to be managed via Flyway in a later phase.
- Added root `docker-compose.yml` for local PostgreSQL 16 with a named volume and healthcheck, parameterized by `POSTGRES_*` env vars.
- Expanded `.env.example` to cover both Compose (`POSTGRES_*`) and Spring (`SPRING_DATASOURCE_*`, `SOLANA_DEVNET_*`) variables.

**Tests added:** none yet (scaffold only; TDD begins with the compliance gatekeeper and Solana RPC services).

**Spring/Solana interactions:** Solana RPC URL + private key are bound from the environment under the `solana.rpc.*` configuration prefix, ready for the `SolanaRpcService` mock-backed unit tests.

### Database Schema & JPA Repository Layer (TDD, GREEN: 20 tests)

**Plan:** Persist the RWA compliance model (investor, asset token, audit trail) with Spring Data JPA repositories verified by `@DataJpaTest` integration tests on H2 in PostgreSQL mode.

**Tests added (all `*IT.java`, run on every build via Surefire include):**
- `InvestorRepositoryIT` — 8 tests: UUID/timestamp generation, `findByWalletAddress`, `findByKycStatus`, `existsByWalletAddress`, `countByKycStatus`, KYC status updates, and unique `wallet_address` constraint violation.
- `AssetTokenRepositoryIT` — 6 tests: UUID/timestamp generation, `findByMintAddress`, `findByComplianceStatus`, custom `findAssetTokensWithMintAddress` JPQL query, and unique `mint_address` constraint violation.
- `AuditLogRepositoryIT` — 6 tests: UUID/timestamp generation, `findByWalletAddress`, `findByWalletAddressAndStatus`, `findByWalletAddressAndAction`, `findByTimestampAfter`, and `findFirstByWalletAddressOrderByTimestampDesc`.

**Decisions:**
- UUID primary keys (`GenerationType.UUID`), string enums persisted via `@Enumerated(EnumType.STRING)`.
- `Investor.walletAddress` and `AssetToken.mintAddress` are `UNIQUE` and indexed; `AuditLog` is immutable (`createdAt`-style, non-updatable `timestamp`).
- `AuditLog` uses a dedicated `timestamp` column (rather than createdAt/updatedAt) per the spec.
- Added `application-test.yml`: H2 `MODE=PostgreSQL` with `ddl-auto: create-drop`.
- Configured `maven-compiler-plugin` with `<proc>full</proc>` + Lombok on `annotationProcessorPaths` — required because JDK 23+ disables annotation processing by default (host runs JDK 25), which silently broke Lombok builders/getters/setters.
- Configured `maven-surefire-plugin` to include `**/*IT.java` so integration tests run on every build against H2 (no live RPC / Postgres required).
- Constraint tests flush through `saveAndFlush()` so the Spring Data proxy translates the raw Hibernate `ConstraintViolationException` into Spring's `DataIntegrityViolationException`.
- Production `ddl-auto` remains `validate`; schema migration will be managed via Flyway in a later phase.

### Compliance Engine Service Layer & REST Controllers (TDD, GREEN: 57 tests)

**Plan:** Gate every Solana RPC dispatch behind off-chain KYC/AML compliance checks, persist an immutable audit log for every attempt, and expose the gatekeeper and investor registration over `/api/v1/*`.

**Tests added:**
- `ComplianceServiceTest` (11, pure Mockito): approve when investor `VERIFIED` + asset `COMPLIANT`; block when investor missing, `REJECTED`, `FLAGGED_SANCTION`, or `PENDING`; block when asset missing or `NON_COMPLIANT`; **every check writes an APPROVED/BLOCKED `AuditLog`**; `getAuditLogs` returns history and throws `InvestorNotFoundException` for unknown wallets.
- `SolanaAddressValidatorTest` (5): base58 alphabet (rejects `0/O/I/l`), 32-44 char length bounds, null passes through to `@NotBlank`.
- `ComplianceDtosValidationTest` (7): `@NotBlank` / `@ValidSolanaAddress` on `ComplianceCheckRequest`, `@NotBlank`+`@Size(2,2)` country and `@NotNull` kycStatus on `InvestorRegistrationRequest`.
- `ComplianceControllerIT` (8, MockMvc + `@MockBean`): 200 allowed/blocked responses, 400 on blank/invalid/blank fields and malformed JSON, 200 history retrieval, 404 for unknown investor.
- `InvestorControllerIT` (6, MockMvc + `@MockBean`): 200 register + KYC update, 400 on blank wallet/country, null/invalid kycStatus.

**Decisions:**
- DTOs are immutable Lombok `@Builder` classes with Bean Validation annotations (`@NotBlank`, `@NotNull`, `@Size`, custom `@ValidSolanaAddress`).
- `ComplianceService.verifyEligibility` implements the strict decision matrix and ALWAYS persists an `AuditLog` (action `CHECK_ELIGIBILITY`, status `APPROVED`/`BLOCKED`) via `auditLogRepository.save(...)` in a `@Transactional` method.
- `InvestorService.registerOrUpdate` upserts by unique `walletAddress`, updating `kycStatus`/`country` on existing records.
- `GlobalExceptionHandler` (`@RestControllerAdvice`) maps `InvestorNotFoundException`/`AssetTokenNotFoundException` to 404 and validation/unreadable-body errors to 400 with a consistent JSON error envelope.
- Solana address validation is a lightweight base58 + 32-44 length constraint (custom `ConstraintValidator`), keeping unit tests fast and offline; full ed25519 key decode belongs to the RPC layer later.

**Build environment fixes (host JDK 25):**
- Upgraded managed Lombok 1.18.34 → 1.18.42 (JDK 23+ requires newer Lombok for annotation processing).
- Upgraded managed Byte Buddy 1.14.19 → 1.17.8 and Mockito 5.11.0 → 5.20.0 so the inline mock maker used by `@MockBean`/`@WebMvcTest` can instrument classes on JDK 25.
- All fixes are Maven property overrides (`lombok.version`, `byte-buddy.version`, `mockito.version`); no boot version bump needed.

**Maven wrapper:** added `mvnw`, `mvnw.cmd`, and `.mvn/wrapper/maven-wrapper.properties` (Maven 3.9.14, `only-script` distribution) via `mvn wrapper:wrapper` so the backend builds without a system Maven install. Verified the full 57-test suite runs green through `.\mvnw.cmd test`.

### Phase 3: Render Deployment Preparation (TDD, GREEN: 70 tests)

**Plan:** Prepare the backend for Render Web Service deployment with a Dockerfile, `render.yaml` Blueprint, production environment variables, and CORS configuration for Vercel frontends.

**Tests:** No new tests added (infrastructure/config only). Full test suite re-run and confirmed GREEN (70 tests: 36 unit + 34 integration).

**Infrastructure added:**

- `backend/Dockerfile` — Multi-stage build (eclipse-temurin:17-jdk-jammy → eclipse-temurin:17-jre-jammy) with Maven wrapper packaging (`-DskipTests`), non-root `appuser`, and healthcheck against `/actuator/health`.
- `render.yaml` — Root-level Render Blueprint declaring `solana-rwa-bridge-api` as a Docker-based web service with `SPRING_PROFILES_ACTIVE=prod`, datasource credentials, `SOLANA_DEVNET_RPC_URL`, and `SOLANA_DEVNET_PRIVATE_KEY` as non-synced env vars.

**CORS configuration:**

- `WebConfig` (`config` package) — `WebMvcConfigurer` allowing `https://*.vercel.app` (Vercel preview & production) and `http://localhost:4200` (Angular dev server) via `allowedOriginPatterns`. All `/api/**` endpoints accept GET/POST/PUT/DELETE/OPTIONS with credentials and a 1-hour preflight cache.

**Build verification:**

- `.\mvnw.cmd clean package -DskipTests` produced `solana-rwa-enterprise-bridge-0.1.0-SNAPSHOT.jar` in `backend/target/` — BUILD SUCCESS on Java 17 target.

**Decisions:**

- Dockerfile uses `chmod +x mvnw` to ensure the Maven wrapper script is executable in the Linux build container.
- Multi-stage build separates JDK (for compilation) from JRE (for runtime), minimizing image size and attack surface.
- `HEALTHCHECK` depends on Spring Boot Actuator being available; the `spring-boot-starter-web` dependency transitively includes actuator basics.
- CORS uses `allowedOriginPatterns` (not `allowedOrigins`) to support wildcard subdomain matching for `*.vercel.app`.

### Phase 4: Angular Frontend UI Scaffold (BUILD VERIFIED: 0 errors)

**Plan:** Initialize an Angular 17+ standalone-component application under `frontend/` with Tailwind CSS, `@solana/web3.js` for browser wallet interactions, and three lazy-loaded feature views: Asset Tokenization Dashboard, Investor KYC Management, and Audit Log Viewer.

**Tests added (4 spec files, TDD-style):**
- `AppComponent` — 3 specs: creates the app, has correct title, renders 3 nav links (Asset Tokens / Investor KYC / Audit Logs).
- `AssetTokenizationComponent` — 5 specs: loading spinner, token list rendering, error display, compliance status → color mapping (7 mappings).
- `InvestorKycComponent` — 6 specs: investor list loading, form validation (all fields required), successful registration + form reset, registration error handling, KYC status → color mapping (5 mappings).
- `AuditLogComponent` — 4 specs: log loading sorted by timestamp descending, error display, status → badge class mapping (5 mappings).

**Architecture:**
- Angular 17.3 standalone components throughout (no `NgModule`).
- Lazy-loaded routing: `/tokens`, `/investors`, `/audit-logs` each load their feature chunk on demand.
- `BackendApiService` (`providedIn: 'root'`) — centralized HTTP client hitting the Render backend (`https://solana-rwa-enterprise-bridge.onrender.com/api` via `environment.apiBaseUrl`).
- Component tests use `HttpTestingController` for full HTTP mocking (zero live backend calls during test runs).
- Dark theme UI with custom Solana color palette (`solana-purple: #9945FF`, `solana-green: #14F195`) via tailwind.config.js.
- `environment.ts` (production) points to Render; `environment.development.ts` points to `http://localhost:8080/api`.
- File replacements wired in `angular.json` for dev mode swapping.

**Tailwind CSS:**
- v3.4.x with PostCSS + Autoprefixer.
- Custom scrollbar styling for dark theme.
- Inter / JetBrains Mono font families.

**Build verification:**
- `npm --prefix frontend run build` — BUILD SUCCESS, 3.107s. Total initial bundle 301.85 KB (84.33 KB gzipped). Lazy chunks: investor-kyc (25.61 KB), asset-tokenization (4.08 KB), audit-log (4.08 KB).
- Zero TypeScript compilation errors.

**Decisions:**
- `@solana/web3.js` dependency added to package.json for future browser wallet interactions (connect, sign, send transactions) — not wired into any component yet.
- No Angular Material — pure Tailwind utility classes for styling to keep the bundle lean.
- All forms use Angular `FormsModule` (`[(ngModel)]`) for simplicity; reactive forms can be introduced later if complex validation needs arise.
- Spec file IDE warnings about `describe`/`it`/`expect` are expected — these resolve at Karma runtime via `tsconfig.spec.json` jasmine types.
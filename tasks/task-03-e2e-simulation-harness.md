# Task 3: End-to-End Maritime Simulation Harness & REST Endpoints

## Objective
Expose the maritime domain over an authenticated REST API (`/api/v1/maritime/*`), wire clearance decisions into atomic SPL settlement and the finality outbox, and verify the complete flow via MockMvc and integration test suites.

## Step-by-Step Execution (Strict TDD: RED → GREEN → REFACTOR)

### Step 1: REST Controllers & DTOs (Web Layer)
* **RED:** Create `MaritimeSettlementControllerTest` (using `@WebMvcTest`). Assert `401 Unauthorized` for missing `X-API-Key`, `201 Created` for `POST /bills-of-lading`, `422 Unprocessable Entity` for fail-closed compliance evaluations (e.g., `SANCTIONS_FLAG`), and `200 OK` for valid executions and reads.
* **GREEN:** Create `MaritimeSettlementController` and the corresponding request/response DTOs (e.g., `RegisterBillOfLadingRequest` with `@Valid` constraints). Delegate to `MaritimeSettlementService`. Run `./mvnw clean test` to confirm passing.

### Step 2: Finality Outbox Wiring
* **RED/GREEN:** Update `MaritimeSettlementService.executeSettlement` to enqueue a `CONFIRMED` row into the `finality_outbox` table (via `FinalityOutboxRepository`) upon successful SPL execution. Add assertions to `MaritimeSettlementServiceTest`.

### Step 3: End-to-End Integration Simulation
* **RED:** Create `MaritimeSettlementE2EIT` (using `@SpringBootTest`, `@AutoConfigureMockMvc`, and `@ActiveProfiles("test")`). Script the full happy path (Register → Evaluate CLEARED → Execute → Worker Polls Finality) and the fail-closed path (Register → Evaluate SANCTIONS_FLAG → Blocked).
* **GREEN:** Refine the orchestration until the E2E simulation passes with zero RPC byte leakage on blocked paths.

## Definition of Done
- [ ] `MaritimeSettlementControllerTest` (MockMvc) implemented and passing.
- [ ] `MaritimeSettlementE2EIT` implemented and passing.
- [ ] All mutating maritime routes (`POST`) enforce `X-API-Key`.
- [ ] DTOs use Jakarta Bean Validation (`@Valid`, `@NotBlank`).
- [ ] `GlobalExceptionHandler` cleanly maps domain exceptions (`404`, `422`).
- [ ] Backend test count is ~235+ passing, 0 failures.


---


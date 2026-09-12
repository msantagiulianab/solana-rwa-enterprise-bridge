# Task: Permanent Delegate Clawback Execution

## Context & Objectives
Token-2022 introduces the Permanent Delegate extension, allowing a designated authority to transfer or burn tokens from any account without the wallet owner's signature. This task exposes that capability via a secure administrative REST API, enabling compliance officers to execute immediate asset recovery (clawbacks) if a severe regulatory breach is detected off-chain.

## Target Deliverables
1. **Core Service (`TokenClawbackService`)**:
   - Accepts a clawback request (mint address, source token account, destination token account, amount, reason).
   - Generates a Token-2022 `TransferChecked` instruction where the signing authority is the Permanent Delegate (the enterprise fee-payer derived via `SolanaKeypairService`), bypassing the source account owner.
   - Resolves Transfer Hook extra-account metas (reusing `SolanaPdaUtil` / transfer hook logic) and appends them as validation accounts.
   - Submits the transaction via `SolanaRpcAdapter.sendTransaction` and writes an immutable `AuditLog` entry (action = `CLAWBACK`).
2. **REST Controller & DTOs**:
   - Expose `POST /api/v1/compliance/clawback` protected by the `X-API-Key` interceptor.
   - Implement Jakarta Bean Validation on the request payload (`ClawbackRequestDto`).
3. **Test Suite Verification**:
   - `TokenClawbackServiceTest`: Pure Mockito tests asserting instruction assembly, cryptographic boundaries, and signature by the delegate rather than the owner.
   - `ComplianceClawbackControllerTest`: MockMvc tests for API key authentication and payload validation.
   - `ClawbackIT`: Full offline integration test verifying the execution flow and audit log persistence against H2.

## Acceptance Criteria
- Clean build: `./mvnw clean test` (from `backend/`) succeeds with 0 failures, 0 errors.
- Test count increases beyond the current baseline of **256 backend tests** (184 unit + 72 integration).
- Autonomous Automation Protocol (from `.clinerules`) executed upon GREEN suite.
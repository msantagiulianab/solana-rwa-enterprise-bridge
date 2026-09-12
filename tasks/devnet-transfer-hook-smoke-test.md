# Task: Devnet Transfer Hook Deployment & Smoke Test

## Context & Objectives
The Token-2022 minting, transfer hook validation metas, and Permanent Delegate clawback have been heavily verified against offline mocks and the H2 database. This task executes a live smoke test against Solana Devnet to prove the complete on-chain lifecycle: Asset Minting, Compliant Transfer, Fail-Closed Transfer, and Permanent Delegate Clawback. 

## Target Deliverables
1. **Live Smoke Test Suite (`DevnetLifecycleSmokeTest.java`)**:
   - Create the test in a new `smoke` package (`com.solana.rwa.bridge.smoke`).
   - Protect it with `@EnabledIfEnvironmentVariable(named = "RUN_DEVNET_SMOKE_TESTS", matches = "true")` so it skips automatically during standard offline CI builds.
   - Flow: Register an off-chain asset, execute a live Token-2022 mint via `SolanaMintService`, and execute a compliant transfer.
2. **Live Fail-Closed & Clawback Execution**:
   - Extend the smoke test to attempt a `BLOCKED` transfer (verifying `ComplianceViolationException` is thrown and the audit log is written with a null signature, zero Devnet bytes).
   - Execute a live Clawback via `TokenClawbackService` using the Permanent Delegate, asserting the funds move successfully on Devnet without the owner's signature.
3. **Environment & Documentation**:
   - Update `README.md` to document `DevnetLifecycleSmokeTest` and provide clear instructions for enterprise operators on how to execute the live suite using the `RUN_DEVNET_SMOKE_TESTS` environment variable and their Devnet private key.

## Acceptance Criteria
- Smoke test executes successfully against `api.devnet.solana.com` when the environment variable is set.
- The standard offline test suite (`./mvnw clean test`) skips the smoke test and continues to pass with 0 failures and 0 network calls.
- Autonomous Automation Protocol (from `.clinerules`) executed upon completion, advancing the baseline test count from **269**.
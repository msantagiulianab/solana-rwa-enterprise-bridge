# Task Plan: Pre-Flight Transaction Simulation & Rehearsal Engine

## Overview
Implement an institutional-grade transaction simulation and pre-flight rehearsal engine. This service allows enterprise operators to rehearse raw serialized Solana wire transactions against the RPC cluster using `simulateTransaction` before committing funds or broadcasting on-chain, dynamically extracting compute units (CU) consumed, program logs, and fail-closed error diagnostics.

## Architectural Constraints
- **Zero Third-Party SDK Dependencies:** Construct JSON-RPC 2.0 simulation payloads and parse responses using standard Java / Jackson primitives.
- **Fail-Closed Diagnostics:** Parse program error codes (e.g., custom SPL Token errors, instruction failures, insufficient funds) into strongly typed Java domain exceptions.
- **TDD Workflow:** Strict RED/GREEN/REFACTOR cycles across unit, service, and controller layers.

---

## Step 1: Simulation Domain Models & RPC Payload Serialization (TDD)

### Step 1.1: RED - Test Simulation Request/Response DTOs & JSON Mapping
- **File:** `backend/src/test/java/com/solana/rwa/bridge/simulation/SimulationPayloadTest.java`
- **Requirements:**
  - Verify serialization of `simulateTransaction` RPC payload with config options (`sigVerify: false`, `encoding: "base64"`, `replaceRecentBlockhash: true`).
  - Verify deserialization of RPC simulation responses containing `err`, `logs`, `unitsConsumed`, `accounts`, and `returnData`.
  - Handle null/absent error fields (successful simulation) vs. structured error objects (reverted execution).

### Step 1.2: GREEN - Implement Simulation DTOs
- **Files:**
  - `backend/src/main/java/com/solana/rwa/bridge/simulation/dto/SimulationRequestDto.java`
  - `backend/src/main/java/com/solana/rwa/bridge/simulation/dto/SimulationResultDto.java`
  - `backend/src/main/java/com/solana/rwa/bridge/simulation/dto/RpcSimulationResponseDto.java`

---

## Step 2: Transaction Simulation Service & Program Error Parser (TDD)

### Step 2.1: RED - Test Simulation Service & Fail-Closed Error Mapping
- **File:** `backend/src/test/java/com/solana/rwa/bridge/simulation/TransactionSimulationServiceTest.java`
- **Requirements:**
  - Test successful simulation parsing extracting exact `unitsConsumed` and execution logs.
  - Test instruction error mapping (e.g., `InstructionError: [0, Custom(1)]` -> `SimulationExecutionException`).
  - Test simulated compute budget safety margin calculation (e.g., measured CU + 15% buffer).
  - Test RPC transport failure handling (fail-closed fallback).

### Step 2.2: GREEN - Implement Simulation Service
- **Files:**
  - `backend/src/main/java/com/solana/rwa/bridge/simulation/service/TransactionSimulationService.java`
  - `backend/src/main/java/com/solana/rwa/bridge/simulation/exception/SimulationExecutionException.java`

---

## Step 3: REST Controller & Pre-Flight Rehearsal Endpoint (TDD)

### Step 3.1: RED - MockMvc Web Layer Tests
- **File:** `backend/src/test/java/com/solana/rwa/bridge/simulation/controller/TransactionSimulationControllerTest.java`
- **Requirements:**
  - `POST /api/v1/settlement/simulate` with valid base64 wire transaction returns `200 OK` and structured `SimulationResultDto`.
  - Empty or invalid base64 payload returns `400 Bad Request`.
  - Simulation failure (reverted dry-run) returns `422 Unprocessable Entity` with failure logs and reason.

### Step 3.2: GREEN - Implement Controller
- **File:** `backend/src/main/java/com/solana/rwa/bridge/simulation/controller/TransactionSimulationController.java`

---

## Step 4: Full Suite Regression, Documentation & Test Count Sync

### Step 4.1: Test Suite Verification
- Run `./mvnw clean test`.
- Target: **170+ passing tests** with zero regressions across compliance and wire serialization modules.

### Step 4.2: Documentation Updates
- Update `README.md` with new pre-flight simulation endpoint and updated test counts.
- Log milestone entry in `DEVELOPMENT_JOURNAL.md`.

---

## Definition of Done

- [x] Step 1.1 — RED simulation request/response DTO & JSON mapping tests (`SimulationPayloadTest`).
- [x] Step 1.2 — GREEN `SimulationRequestDto`, `RpcSimulationResponseDto`, `SimulationResultDto`.
- [x] Step 2.1 — RED simulation service & fail-closed error-mapping tests (`TransactionSimulationServiceTest`).
- [x] Step 2.2 — GREEN `TransactionSimulationService` + `SimulationExecutionException` (program error parser, +15% safety margin).
- [x] Step 3.1 — RED MockMvc web-layer tests (`TransactionSimulationControllerTest`).
- [x] Step 3.2 — GREEN `TransactionSimulationController` + fail-closed `SimulationExceptionHandler`.
- [x] Step 4.1 — `./mvnw clean test` → 174 tests, 0 failures, 0 errors (128 unit + 46 integration).
- [x] Step 4.2 — `README.md` + `DEVELOPMENT_JOURNAL.md` synchronized.
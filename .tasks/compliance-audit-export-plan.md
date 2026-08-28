# Task Plan: Enterprise Compliance & Settlement Audit Export Engine

## Overview
Implement an enterprise-grade settlement proof and compliance audit export engine in the Solana RWA Enterprise Bridge. This feature enables compliance officers and institutional auditors to extract immutable tokenization and KYC/AML verification records via deterministic CSV (RFC-4180) and JSON streams.

## Architectural Constraints
- **Zero Third-Party CSV/Export Dependencies:** Rely exclusively on standard Java 21 string/stream primitives for deterministic byte formatting.
- **Fail-Closed Compliance:** Export endpoints must enforce strict input validation on date ranges and format parameters.
- **TDD Workflow:** Every step begins with failing unit/integration tests (`RED`) before implementing minimal production code (`GREEN`).

---

## Step 1: Immutable Settlement Proof DTO & Audit Query Service (TDD)

### Step 1.1: RED - Unit Test Harness for Audit Query Aggregation
- **File:** `backend/src/test/java/com/rwa/bridge/compliance/AuditExportServiceTest.java`
- **Requirements:**
  - Test filtering audit records by ISO-8601 date range (`startDate`, `endDate`).
  - Test filtering audit records by `assetId`.
  - Test filtering by transaction execution status (`SUCCESS`, `FAILED_COMPLIANCE`, `FAILED_RPC`).
  - Test handling of empty results without null-pointer regressions.

### Step 1.2: GREEN - Implement Audit Record Models & Service
- **Files:** 
  - `backend/src/main/java/com/rwa/bridge/compliance/dto/AuditExportRecordDto.java`
  - `backend/src/main/java/com/rwa/bridge/compliance/service/AuditExportService.java`
- **Fields in DTO:**
  - `eventId` (UUID)
  - `timestamp` (Instant / ISO-8601)
  - `assetId` (String)
  - `investorWallet` (Base58 String)
  - `kycVerified` (boolean)
  - `ofacPassed` (boolean)
  - `status` (String)
  - `computeUnitPriceMicroLamports` (long)
  - `computeUnitLimit` (int)
  - `solanaTransactionSignature` (String nullable)
  - `slot` (Long nullable)
  - `blockhash` (String nullable)

---

## Step 2: Deterministic Format Exporters (CSV & JSON) (TDD)

### Step 2.1: RED - Test Exporters
- **Files:**
  - `backend/src/test/java/com/rwa/bridge/compliance/exporter/CsvAuditExporterTest.java`
  - `backend/src/test/java/com/rwa/bridge/compliance/exporter/JsonAuditExporterTest.java`
- **Requirements:**
  - CSV must contain exact canonical headers: `EventId,Timestamp,AssetId,InvestorWallet,KYC_Verified,OFAC_Passed,Status,CU_Price_MicroLamports,CU_Limit,Signature,Slot,Blockhash`.
  - CSV must correctly escape fields containing commas or quotes.
  - JSON output must produce deterministic, schema-compliant JSON arrays with explicit null-field handling.
  - Test zero-record scenarios (valid header only for CSV, empty array `[]` for JSON).

### Step 2.2: GREEN - Implement Exporters
- **Files:**
  - `backend/src/main/java/com/rwa/bridge/compliance/exporter/AuditExporter.java` (Interface)
  - `backend/src/main/java/com/rwa/bridge/compliance/exporter/CsvAuditExporter.java`
  - `backend/src/main/java/com/rwa/bridge/compliance/exporter/JsonAuditExporter.java`

---

## Step 3: REST Controller & Streaming Endpoint (TDD)

### Step 3.1: RED - MockMvc Web Layer Tests
- **File:** `backend/src/test/java/com/rwa/bridge/compliance/controller/ComplianceAuditExportControllerTest.java`
- **Requirements:**
  - `GET /api/v1/compliance/audit-logs/export?format=csv` returns `200 OK`, `Content-Type: text/csv`, and header `Content-Disposition: attachment; filename="audit-export-<timestamp>.csv"`.
  - `GET /api/v1/compliance/audit-logs/export?format=json` returns `200 OK`, `Content-Type: application/json`.
  - Invalid format query parameter (e.g., `?format=xml`) returns `400 Bad Request` with structured error response.
  - Query with invalid date format returns `400 Bad Request`.

### Step 3.2: GREEN - Implement Controller
- **File:** `backend/src/main/java/com/rwa/bridge/compliance/controller/ComplianceAuditExportController.java`
- **Behavior:** Bind request parameters, query `AuditExportService`, delegate serialization to the matching `AuditExporter`, and stream bytes in `ResponseEntity<byte[]>`.

---

## Step 4: Regression Suite & Documentation Synchronization

### Step 4.1: Test Suite Verification
- Run full backend suite: `./mvnw clean test` (or `./gradlew test`).
- Target: **140+ passing tests** (expanding from current 131 baseline) with zero regressions on existing 4-instruction wire serializer or AML gates.

### Step 4.2: Documentation Updates
- Update `README.md` with new test metrics and export endpoint documentation.
- Append technical changelog entry in `DEVELOPMENT_JOURNAL.md`.
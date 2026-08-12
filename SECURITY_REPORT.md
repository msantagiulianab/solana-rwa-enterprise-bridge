# Security Penetration Testing & Audit Report

**Project:** Solana RWA Enterprise Bridge
**Branch:** `feature/security-pentest`
**Date:** 2026-08-12
**Scope:** `backend/src/` (Spring Boot 3.3 / Java 17) and `frontend/src/` (Angular 17.3)
**Standard:** `.clinerules/security-pentest.md`

## Summary

Static analysis and targeted security scenarios were executed against the backend and
frontend source. Five findings were identified and remediated. No High-severity
vulnerabilities remain in the committed code; all mutating API routes are now
authenticated; actuator and CORS misconfigurations are corrected; exception handling is
sanitized; and the frontend now supplies the required API-key header on write requests.

| Severity | Found | Remediated |
|----------|-------|------------|
| High     | 2     | 2          |
| Medium   | 3     | 3          |
| Low      | 0     | 0          |

---

## Findings

### F1 — HIGH: Unauthenticated mutating API routes (A01 / A07)
**Location:** `InvestorController.register`, `ComplianceController.check`
**Observation:** `POST /api/investors` and `POST /api/v1/compliance/check` accepted
requests with no authentication. Any client able to reach the origin could mutate
investor records or perform compliance checks.
**Remediation:** Added `ApiKeyAuthInterceptor` enforcing an `X-API-Key` header on all
`POST`/`PATCH`/`PUT`/`DELETE` routes (registered in `WebConfig`). Missing/invalid keys
return `401 Unauthorized`. The key is injected via `${SECURITY_API_KEY}`.

### F2 — HIGH: Verbose error disclosure / missing catch-all handler (A05 / info leak)
**Location:** `GlobalExceptionHandler`
**Observation:** No catch-all `@ExceptionHandler(Exception.class)` existed, and the
malformed-body handler interpolated `ex.getMostSpecificCause().getMessage()`, which can
leak internal class names/paths to callers.
**Remediation:** Added a sanitized catch-all returning a generic 500, and simplified the
malformed-body handler to a fixed message.

### F3 — MEDIUM: Actuator health details exposed (`show-details: always`)
**Location:** `backend/src/main/resources/application.yml`
**Observation:** `management.endpoint.health.show-details: always` leaks internal
component status over the web.
**Remediation:** Changed to `never`. Only `health` and `info` remain web-exposed.

### F4 — MEDIUM: CORS `allowedHeaders("*")` with `allowCredentials(true)`
**Location:** `config/WebConfig.java`
**Observation:** Wildcard allowed headers combined with credentialed requests is a
misconfiguration that defeats the expected header allowlist.
**Remediation:** Replaced `"*"` with an explicit allowlist
(`Origin`, `Content-Type`, `Accept`, `Authorization`, `X-API-Key`).

### F5 — MEDIUM: Untyped domain exception maps to 500 instead of 404
**Location:** `service/TokenService.java`
**Observation:** `findById` threw a generic `new RuntimeException(...)` for a missing
asset token, which the handler could not map to a clean 404.
**Remediation:** Throws `AssetTokenNotFoundException` (typed), mapped to `404` by
`GlobalExceptionHandler`.

---

## Frontend Assessment

| Check | Status |
|-------|--------|
| Private keys in frontend code | **Pass** — none found; signing delegated to browser wallet |
| XSS sinks (`innerHTML`, `DomSanitizer`, `bypassSecurityTrust*`) | **Pass** — none found; interpolation only |
| Wallet state persisted to `localStorage`/`sessionStorage` | **Pass** — state held in `BehaviorSubject`, cleared on `disconnect`/`accountChanged` |
| Solana RPC endpoint hardcoded in component logic | **Pass** — captured in environment files, injected via config |
| API-key header on mutating requests | **Remediated** — `api-key.interceptor.ts` adds `X-API-Key` for POST/PATCH/PUT/DELETE |

---

## Remediation Checklist

- [x] Add API-key authentication gate for mutating routes (`ApiKeyAuthInterceptor`)
- [x] Register interceptor + tighten CORS allowlist (`WebConfig`)
- [x] Sanitize exception handling and add catch-all 500 (`GlobalExceptionHandler`)
- [x] Map asset-token "not found" to typed exception (`TokenService`)
- [x] Disable actuator `show-details` (`application.yml`)
- [x] Inject `SECURITY_API_KEY` across `application.yml`, `render.yaml`, `.env.example`
- [x] Add frontend HTTP interceptor to supply `X-API-Key` on writes
- [x] Add/update backend and frontend security tests

## Verification

- Backend suite: `./mvnw -f backend/pom.xml test`
- Frontend suite: `npm --prefix frontend test`
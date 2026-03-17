# PHR Platform — API Test Cases

**Version:** 2.0  
**Date:** 2026-03-17  
**Updated:** 2026-01-19

| Field          | Value                                                                                                                                                       |
| -------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Owner          | QA Lead                                                                                                                                                     |
| Classification | C2 — Internal                                                                                                                                               |
| Review cadence | Per sprint                                                                                                                                                  |
| Companion docs | [Route contract pack](../04_design_and_workflows/phr_mvp_route_contract_pack.md), [Error model](../03_architecture/phr_error_model_and_idempotency_spec.md) |

> 📌 **What changed in v2.0:** Emergency QR API tests, FCHV API tests, rate limiting tests, circuit breaker tests, tenant isolation tests, OWASP security tests, performance targets per endpoint.

This document defines contract-level API test cases for Core MVP and the committed Phase 2 extension routes.

---

## 1. Core MVP API cases

| Test ID   | Phase | Endpoint                                      | Scenario                              | Expected result                           |
| --------- | ----- | --------------------------------------------- | ------------------------------------- | ----------------------------------------- |
| `API-001` | MVP   | `POST /api/v1/auth/login`                     | valid login                           | 200 with actor context and tokens         |
| `API-002` | MVP   | `POST /api/v1/auth/login`                     | invalid credentials                   | 401 with stable auth error                |
| `API-003` | MVP   | `POST /api/v1/patients`                       | valid patient create                  | success envelope with patient id          |
| `API-004` | MVP   | `GET /api/v1/patients/:id`                    | authorized read                       | patient payload returned                  |
| `API-005` | MVP   | `PATCH /api/v1/patients/:id`                  | allowed profile update                | updated profile returned                  |
| `API-006` | MVP   | `PATCH /api/v1/patients/:id`                  | forbidden field edit                  | 403 with field-safe error                 |
| `API-007` | MVP   | `GET /api/v1/patients/:id/timeline`           | patient timeline read                 | timeline items returned in expected shape |
| `API-008` | MVP   | `GET /api/v1/patients/:id/timeline`           | unauthorized access                   | 403                                       |
| `API-009` | MVP   | `GET /api/v1/patients/:id/encounters`         | encounter list read                   | filtered encounters returned              |
| `API-010` | MVP   | `PATCH /api/v1/encounters/:id`                | encounter update by provider          | updated encounter returned                |
| `API-011` | MVP   | `GET /api/v1/patients/:id/observations`       | observation list read                 | observations returned                     |
| `API-012` | MVP   | `POST /api/v1/observations`                   | observation create                    | created observation id returned           |
| `API-013` | MVP   | `GET /api/v1/patients/:id/observation-trends` | trend query                           | chart-ready series returned               |
| `API-014` | MVP   | `GET /api/v1/patients/:id/conditions`         | conditions list                       | active/resolved conditions returned       |
| `API-015` | MVP   | `POST /api/v1/medication-requests`            | provider creates medication request   | request persisted and visible             |
| `API-016` | MVP   | `POST /api/v1/appointments`                   | book appointment                      | appointment created                       |
| `API-017` | MVP   | `POST /api/v1/documents`                      | valid document upload metadata + file | document stored and indexed               |
| `API-018` | MVP   | `GET /api/v1/patients/:id/documents`          | document list read                    | paged list returned                       |
| `API-019` | MVP   | `GET /api/v1/patients/:id/coverage`           | coverage summary                      | active coverage or no-coverage state      |
| `API-020` | MVP   | `POST /api/v1/insurance/eligibility-check`    | eligibility success                   | result + logging side effect              |
| `API-021` | MVP   | `POST /api/v1/insurance/eligibility-check`    | upstream timeout                      | retryable upstream error                  |
| `API-022` | MVP   | `POST /api/v1/access-grants`                  | create grant                          | active grant returned                     |
| `API-023` | MVP   | `PATCH /api/v1/access-grants/:id/revoke`      | revoke grant                          | revoked state returned                    |
| `API-024` | MVP   | `GET /api/v1/audit/logs`                      | authorized audit read                 | paged audit list returned                 |
| `API-025` | MVP   | `POST /api/v1/documents/:id/ocr`              | trigger OCR                           | queued/processing state returned          |
| `API-026` | MVP   | `POST /api/v1/ocr-results/:id/confirm`        | confirm OCR and create resources      | created resources + provenance            |
| `API-028` | MVP   | `POST /api/v1/audio-input`                    | start transcription                   | transcription id + stream data returned   |

---

## 2. Phase 2 extension API cases

| Test ID   | Phase       | Endpoint                                      | Scenario                | Expected result                               |
| --------- | ----------- | --------------------------------------------- | ----------------------- | --------------------------------------------- |
| `API-027` | MVP/Phase 2 | export APIs                                   | export patient data     | requested export artifact generated or queued |
| `API-029` | Phase 2     | `POST /api/v1/claims`                         | claim submit            | claim + submission attempt persisted          |
| `API-030` | Phase 2     | caregiver delegated APIs                      | dependent summary read  | scoped dependent data only                    |
| `API-031` | Phase 2     | `POST /api/v1/telemedicine/sessions/:id/join` | patient join            | valid join token/details                      |
| `API-032` | Phase 2     | `POST /api/v1/telemedicine/sessions`          | provider create session | session metadata returned                     |

---

## 3. Mandatory assertions for every API test

- response status code
- response schema shape
- error code for failure paths
- permission behavior
- audit side effects for sensitive operations
- idempotency behavior where applicable

---

## 4. Emergency QR API Tests (Added in v2.0)

| Test ID   | Phase | Endpoint                                | Scenario                   | Expected result                                      |
| --------- | ----- | --------------------------------------- | -------------------------- | ---------------------------------------------------- |
| `API-033` | MVP   | `GET /api/v1/patients/:id/emergency-qr` | patient requests own QR    | QR payload returned with blood type, allergies, meds |
| `API-034` | MVP   | `GET /api/v1/patients/:id/emergency-qr` | QR with incomplete profile | 422 with missing fields list                         |
| `API-035` | MVP   | `GET /api/v1/patients/:id/emergency-qr` | provider with active grant | QR payload returned                                  |
| `API-036` | MVP   | `GET /api/v1/patients/:id/emergency-qr` | unauthorized actor         | 403                                                  |
| `API-037` | MVP   | `GET /api/v1/patients/:id/emergency-qr` | QR payload privacy check   | Response MUST NOT contain NID, full name, or address |

---

## 5. FCHV API Tests (Added in v2.0)

| Test ID   | Phase | Endpoint                     | Scenario                     | Expected result                     |
| --------- | ----- | ---------------------------- | ---------------------------- | ----------------------------------- |
| `API-038` | MVP   | `POST /api/v1/fchv/patients` | FCHV registers patient       | Patient created in pending status   |
| `API-039` | MVP   | `POST /api/v1/fchv/patients` | Duplicate NID                | 409 Conflict with duplicate warning |
| `API-040` | MVP   | `GET /api/v1/fchv/patients`  | FCHV lists own registrations | Only FCHV's patients returned       |
| `API-041` | MVP   | `GET /api/v1/fchv/patients`  | Non-FCHV role                | 403                                 |

---

## 6. Rate Limiting Tests (Added in v2.0)

| Test ID   | Phase | Endpoint                   | Scenario                 | Expected result                        |
| --------- | ----- | -------------------------- | ------------------------ | -------------------------------------- |
| `API-042` | MVP   | `POST /api/v1/auth/login`  | 11th request in 1 minute | 429 with `Retry-After` header          |
| `API-043` | MVP   | `GET /api/v1/patients/:id` | 101st read in 1 minute   | 429 with `Retry-After` header          |
| `API-044` | MVP   | any endpoint               | Normal request           | `X-RateLimit-Remaining` header present |

---

## 7. Circuit Breaker Tests (Added in v2.0)

| Test ID   | Phase | Endpoint                                   | Scenario                  | Expected result                                                   |
| --------- | ----- | ------------------------------------------ | ------------------------- | ----------------------------------------------------------------- |
| `API-045` | MVP   | `POST /api/v1/insurance/eligibility-check` | openIMIS circuit open     | 503 `UPSTREAM_UNAVAILABLE` with cached fallback data if available |
| `API-046` | MVP   | `POST /api/v1/insurance/eligibility-check` | openIMIS timeout          | 503 `UPSTREAM_TIMEOUT`                                            |
| `API-047` | MVP   | `POST /api/v1/insurance/eligibility-check` | openIMIS partial response | 200 with `UPSTREAM_DEGRADED` indicator                            |

---

## 8. Tenant Isolation Tests (Added in v2.0)

| Test ID   | Phase | Endpoint                            | Scenario                                     | Expected result                               |
| --------- | ----- | ----------------------------------- | -------------------------------------------- | --------------------------------------------- |
| `API-048` | MVP   | `GET /api/v1/patients/:id`          | Request with mismatched tenant header vs JWT | 403                                           |
| `API-049` | MVP   | `GET /api/v1/patients/:id`          | Tenant A requests Tenant B's patient         | 404 (NOT 403 to avoid information disclosure) |
| `API-050` | MVP   | `GET /api/v1/patients/:id/timeline` | Cross-tenant timeline read                   | 404                                           |

---

## 9. OWASP Security Tests (Added in v2.0)

| Test ID   | Phase | Endpoint                   | Scenario                            | Expected result                                        |
| --------- | ----- | -------------------------- | ----------------------------------- | ------------------------------------------------------ |
| `API-051` | MVP   | `POST /api/v1/patients`    | SQL injection in name field         | 400 validation error, no SQL execution                 |
| `API-052` | MVP   | `POST /api/v1/patients`    | XSS payload in name field           | Input sanitized or rejected                            |
| `API-053` | MVP   | any error response         | Internal error triggered            | No stack traces, DB columns, or file paths in response |
| `API-054` | MVP   | `POST /api/v1/auth/login`  | Brute force (100 rapid attempts)    | Account locked after threshold                         |
| `API-055` | MVP   | `GET /api/v1/patients/:id` | IDOR — patient A requests patient B | 403 or 404                                             |
| `API-056` | MVP   | all endpoints              | Missing/expired JWT                 | 401 with generic message                               |
| `API-057` | MVP   | all endpoints              | Tampered JWT signature              | 401                                                    |

---

## 10. Performance Targets per Endpoint (Added in v2.0)

| Endpoint Category    | p50 Target | p95 Target | p99 Target | Max Payload |
| -------------------- | ---------- | ---------- | ---------- | ----------- |
| Auth (login/session) | < 100ms    | < 300ms    | < 500ms    | 2 KB        |
| Patient read         | < 50ms     | < 200ms    | < 500ms    | 50 KB       |
| Patient write        | < 100ms    | < 300ms    | < 1s       | 10 KB       |
| Timeline query       | < 200ms    | < 500ms    | < 1s       | 200 KB      |
| Document upload      | < 500ms    | < 2s       | < 5s       | 50 MB       |
| Eligibility check    | < 1s       | < 3s       | < 5s       | 5 KB        |
| Emergency QR         | < 200ms    | < 500ms    | < 1s       | 10 KB       |

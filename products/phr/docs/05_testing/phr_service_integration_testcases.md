# PHR Platform — Service and Integration Test Cases

**Version:** 2.0  
**Date:** 2026-03-17  
**Updated:** 2026-01-19

| Field          | Value                                                                                                                                              |
| -------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| Owner          | QA Lead                                                                                                                                            |
| Classification | C2 — Internal                                                                                                                                      |
| Review cadence | Per sprint                                                                                                                                         |
| Companion docs | [NestJS modules](../03_architecture/phr_nestjs_modules_detailed_architecture.md), [Schema delta](../03_architecture/phr_core_schema_delta_spec.md) |

> 📌 **What changed in v2.0:** Cross-tenant isolation tests, consent cache invalidation tests, circuit breaker integration tests, Emergency QR generation tests, FCHV registration flow tests, document integrity tests.

This document defines module-level, persistence-level, and external-integration test cases.

---

## 1. Core MVP service and integration cases

| Test ID   | Phase | Scope                           | Scenario                                          | Expected result                                        |
| --------- | ----- | ------------------------------- | ------------------------------------------------- | ------------------------------------------------------ |
| `SVC-001` | MVP   | patient create transaction      | create patient with names, contacts, identifiers  | all patient-related rows persist or all rollback       |
| `SVC-002` | MVP   | patient policy service          | provider reads patient without grant              | access denied consistently                             |
| `SVC-003` | MVP   | timeline aggregator             | combine encounter/obs/condition/med/doc items     | stable unified timeline order and shape                |
| `SVC-004` | MVP   | observation trend service       | date-range and code filtering                     | correct series generation with empty state             |
| `SVC-005` | MVP   | provider clinical write path    | encounter + medication write in patient scope     | writes allowed only with valid provider context        |
| `SVC-006` | MVP   | appointment booking transaction | concurrent booking requests for same slot         | exactly one booking succeeds                           |
| `SVC-007` | MVP   | document upload persistence     | Ceph object write and DB metadata persist         | no dangling DB record or silent object mismatch        |
| `SVC-008` | MVP   | eligibility integration         | openIMIS success/failure mapping                  | durable `EligibilityCheckLog` with normalized outcome  |
| `SVC-009` | MVP   | audit service                   | sensitive read/write path                         | audit row persisted with actor/tenant/resource context |
| `SVC-010` | MVP   | consent enforcement             | create overlapping grant per forbidden policy     | conflict returned and no invalid state saved           |
| `SVC-011` | MVP   | export service                  | generate patient export set                       | exported dataset includes allowed resources only       |
| `SVC-012` | MVP   | OCR/voice provenance            | confirm OCR/transcription to create clinical data | `InputProvenance` links every created resource         |

---

## 2. Phase 2 service and integration cases

| Test ID   | Phase   | Scope                        | Scenario                                      | Expected result                                        |
| --------- | ------- | ---------------------------- | --------------------------------------------- | ------------------------------------------------------ |
| `SVC-013` | Phase 2 | caregiver policy layer       | caregiver reads out-of-scope dependent record | access denied and audited                              |
| `SVC-014` | Phase 2 | telemedicine session service | create/join/end session                       | participant and session state transitions remain valid |
| `SVC-015` | Phase 2 | claim submission integration | claim submit with retryable upstream failure  | `ClaimSubmissionAttempt` persisted and retry-safe      |

---

## 3. Integration stubs and fixtures

Required stubs/fixtures:

- auth provider fixture
- openIMIS sandbox/stub
- Ceph-compatible object storage fixture
- SMS/email provider stub
- OCR/ASR service stubs
- Phase 2 telemedicine token/session stub

---

## 4. Test design rule

Every service/integration test must assert:

- owning module behavior
- cross-module interaction contract
- persistence side effects
- audit side effects where applicable
- retry/idempotency behavior for integration-facing mutations

---

## 5. Cross-Tenant Isolation Tests (Added in v2.0)

| Test ID   | Phase | Scope            | Scenario                                                    | Expected result                                 |
| --------- | ----- | ---------------- | ----------------------------------------------------------- | ----------------------------------------------- |
| `SVC-016` | MVP   | tenant isolation | Tenant A service reads Tenant B patient via direct DB query | RLS blocks access, empty result                 |
| `SVC-017` | MVP   | tenant isolation | Concurrent requests from 2 tenants                          | Each sees only own data, no cross-contamination |
| `SVC-018` | MVP   | tenant isolation | Tenant context missing from request                         | Request rejected before service layer           |
| `SVC-019` | MVP   | tenant isolation | Audit log contains tenant ID                                | Every audit entry has correct tenant_id         |

---

## 6. Consent Cache Integration Tests (Added in v2.0)

| Test ID   | Phase | Scope         | Scenario                         | Expected result                                       |
| --------- | ----- | ------------- | -------------------------------- | ----------------------------------------------------- |
| `SVC-020` | MVP   | consent cache | Grant created                    | Grantee's consent cache invalidated immediately       |
| `SVC-021` | MVP   | consent cache | Grant revoked                    | Grantee's consent cache invalidated, next read denied |
| `SVC-022` | MVP   | consent cache | Cache miss                       | DB queried, result cached for subsequent reads        |
| `SVC-023` | MVP   | consent cache | Near-expiry grant (within 5 min) | Cache bypassed, DB verified                           |

---

## 7. Circuit Breaker Integration Tests (Added in v2.0)

| Test ID   | Phase | Scope            | Scenario                         | Expected result                                    |
| --------- | ----- | ---------------- | -------------------------------- | -------------------------------------------------- |
| `SVC-024` | MVP   | openIMIS circuit | 5 failures in 60s                | Circuit opens, subsequent calls return cached data |
| `SVC-025` | MVP   | openIMIS circuit | Circuit half-open probe succeeds | Circuit closes, normal operation resumes           |
| `SVC-026` | MVP   | openIMIS circuit | Circuit state change             | Prometheus metric emitted                          |

---

## 8. Emergency QR Service Tests (Added in v2.0)

| Test ID   | Phase | Scope                   | Scenario                     | Expected result                                           |
| --------- | ----- | ----------------------- | ---------------------------- | --------------------------------------------------------- |
| `SVC-027` | MVP   | Emergency QR generation | Patient has complete profile | QR payload contains blood type, allergies, meds, contacts |
| `SVC-028` | MVP   | Emergency QR generation | Patient updates medication   | QR auto-regenerated                                       |
| `SVC-029` | MVP   | Emergency QR privacy    | QR payload inspection        | No NID, full name, or address in payload                  |

---

## 9. FCHV Registration Flow Tests (Added in v2.0)

| Test ID   | Phase | Scope             | Scenario                                  | Expected result                           |
| --------- | ----- | ----------------- | ----------------------------------------- | ----------------------------------------- |
| `SVC-030` | MVP   | FCHV registration | FCHV registers patient offline then syncs | Patient created in pending status on sync |
| `SVC-031` | MVP   | FCHV registration | Duplicate NID during sync                 | Conflict returned, FCHV notified          |
| `SVC-032` | MVP   | FCHV registration | Patient confirms registration             | Status changes from pending to active     |

---

## 10. Document Integrity Tests (Added in v2.0)

| Test ID   | Phase | Scope              | Scenario                     | Expected result                             |
| --------- | ----- | ------------------ | ---------------------------- | ------------------------------------------- |
| `SVC-033` | MVP   | document integrity | Upload computes SHA-256 hash | Hash stored in metadata                     |
| `SVC-034` | MVP   | document integrity | Download re-verifies hash    | Hash matches, download proceeds             |
| `SVC-035` | MVP   | document integrity | Hash mismatch detected       | Document quarantined, alert emitted         |
| `SVC-036` | MVP   | virus scan         | Upload with EICAR test file  | File rejected, quarantined, user notified   |
| `SVC-037` | MVP   | virus scan         | Scan timeout                 | File remains in quarantine, retry scheduled |

---

## 11. Test Pyramid Ratios (Added in v2.0)

| Test Layer        | Target Ratio | Description                                     |
| ----------------- | ------------ | ----------------------------------------------- |
| Unit tests        | 70%          | Module-internal logic, validators, transformers |
| Integration tests | 20%          | Cross-module, DB, cache, external stubs         |
| E2E tests         | 10%          | Full API flow through deployed service          |

**Coverage target:** ≥ 80% line coverage for all service modules.

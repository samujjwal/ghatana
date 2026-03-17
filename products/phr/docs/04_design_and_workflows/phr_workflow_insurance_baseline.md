# PHR Workflow — Insurance Baseline

**Version:** 2.0  
**Date:** 2026-01-19

| Field              | Value                                                                                                                         |
| ------------------ | ----------------------------------------------------------------------------------------------------------------------------- |
| **Document Owner** | PHR Platform Lead                                                                                                             |
| **Classification** | Internal — Restricted                                                                                                         |
| **Last Review**    | 2026-01-19                                                                                                                    |
| **Companion Docs** | [Route Contract Pack](phr_mvp_route_contract_pack.md), [Runtime Architecture](../03_architecture/phr_runtime_architecture.md) |

> **📌 What changed in v2.0:** Added circuit breaker behavior for openIMIS integration, cached eligibility fallback, claim lifecycle state machine (Phase 2), openIMIS FHIR Implementation Guide alignment notes, and 7-year claim data retention requirement.
> **Phase:** Core MVP

---

## 1. Goal

Deliver the Core MVP insurance baseline:

- coverage summary
- eligibility verification

Claims remain Phase 2.

---

## 2. Primary actors

- patient
- provider staff

---

## 3. Entry points

- patient insurance page
- provider pre-consultation workflow

APIs:

- `GET /api/v1/patients/:id/coverage`
- `POST /api/v1/insurance/eligibility-check`

---

## 4. Preconditions

- patient identity is resolved
- coverage data exists or a no-coverage state is supported
- openIMIS integration is configured for eligibility checks

---

## 5. Data touched

- `Coverage`
- `EligibilityCheckLog`
- `AuditLog`

---

## 6. Happy path

1. patient or staff loads insurance page
2. current coverage summary is returned
3. authorized actor triggers eligibility check
4. platform validates actor/patient/provider relationship
5. openIMIS request is sent
6. eligibility response is logged
7. audit record is written
8. response is rendered with decision and supporting details

---

## 7. Alternate and failure paths

- no coverage -> explicit empty state
- upstream timeout -> retryable error state
- patient/provider mismatch -> forbidden
- invalid coverage reference -> validation error or not found depending on path

---

## 8. Acceptance criteria

- coverage summary renders from current active coverage
- eligibility checks are logged with request/response context
- only authorized actors can run checks
- audit exists for insurance data access and eligibility actions

---

## 9. Phase 2 extension

Claims extend this baseline with:

- `Claim`
- `ClaimResponse`
- `ClaimSubmissionAttempt`
- supporting-document mapping

---

## 10. Circuit Breaker for openIMIS Integration (Added in v2.0)

openIMIS is the external system for eligibility checks. It may be slow or unavailable. The circuit breaker protects the PHR from cascading failures:

| Parameter                    | Value                                                                   |
| ---------------------------- | ----------------------------------------------------------------------- |
| Failure threshold (to open)  | 5 failures in 60 seconds                                                |
| Half-open retry interval     | 30 seconds                                                              |
| Success threshold (to close) | 3 consecutive successes                                                 |
| Request timeout              | 5 seconds                                                               |
| Fallback behavior            | Return cached eligibility (if < 24h old) + "Last verified: {timestamp}" |

**State transitions:**

- **CLOSED** (normal): Requests pass through to openIMIS. Failures counted.
- **OPEN** (tripped): All requests return cached data immediately. No upstream calls.
- **HALF-OPEN** (probing): One request per interval sent to openIMIS. On success → CLOSED. On failure → OPEN.

**Monitoring:** Circuit state changes emit metrics to Prometheus and alert to ops channel.

---

## 11. Cached Eligibility Fallback (Added in v2.0)

When the circuit breaker is open or openIMIS is unreachable:

1. System checks Valkey cache for patient's last successful eligibility response
2. If cache hit AND cache age < 24 hours → return cached result with "stale" indicator
3. If cache hit AND cache age > 24 hours → return cached result with **warning**: "Eligibility data may be outdated"
4. If no cache → return "Eligibility check unavailable. Please verify with your insurance provider."
5. UI displays clear visual indicator (amber badge) when showing cached eligibility data

**Cache key:** `insurance:eligibility:{tenantId}:{patientId}:{coverageId}`
**Cache TTL:** 24 hours (refreshed on every successful upstream check)

---

## 12. Claim Lifecycle State Machine — Phase 2 (Added in v2.0)

```
 ┌─────────┐    submit    ┌───────────┐   adjudicate   ┌──────────┐
 │  DRAFT  │─────────────►│ SUBMITTED │────────────────►│ APPROVED │
 └─────────┘              └───────────┘                 └──────────┘
      │                        │                              │
      │ discard                │ reject                       │ payment
      ▼                        ▼                              ▼
 ┌─────────┐              ┌──────────┐                 ┌──────────┐
 │DISCARDED│              │ REJECTED │                 │   PAID   │
 └─────────┘              └──────────┘                 └──────────┘
                               │
                               │ appeal
                               ▼
                          ┌──────────┐
                          │ APPEALED │──► (re-enters SUBMITTED)
                          └──────────┘
```

**Transition rules:**

- DRAFT → SUBMITTED: Requires all supporting documents attached + patient consent
- SUBMITTED → REJECTED: openIMIS returns denial reason code
- REJECTED → APPEALED: Provider attaches additional justification within 30 days
- SUBMITTED → APPROVED: openIMIS returns approval + coverage amount
- APPROVED → PAID: Payment record received from openIMIS

---

## 13. openIMIS Implementation Guide Alignment (Added in v2.0)

| openIMIS IG Requirement     | Ghatana PHR Implementation                   |
| --------------------------- | -------------------------------------------- |
| FHIR Coverage resource      | Mapped to InsuranceModule.Coverage model     |
| EligibilityRequest/Response | REST adapter to openIMIS FHIR R4 endpoint    |
| Claim resource              | Phase 2 ClaimModule (FHIR-shaped)            |
| Patient identifier linkage  | NID-based cross-reference                    |
| Enrollment verification     | Real-time eligibility check at point of care |
| Premium payment status      | Read-only display from openIMIS (no write)   |
| Benefit package details     | Cached locally with 24h refresh              |

---

## 14. Data Retention and Compliance (Added in v2.0)

| Data Type              | Retention Period               | Basis                  |
| ---------------------- | ------------------------------ | ---------------------- |
| Coverage records       | 7 years after expiry           | Nepal Insurance Act    |
| Eligibility check logs | 7 years                        | Audit compliance       |
| Claim records          | 7 years after resolution       | Nepal Insurance Act    |
| Supporting documents   | 7 years after claim resolution | Legal hold             |
| Audit trail            | 7 years                        | Nepal Privacy Act 2075 |

**Purge process:** Automated purge job runs monthly. Records past retention are soft-deleted → 90-day grace period → hard-deleted with audit log entry.

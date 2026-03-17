# PHR Platform — Error Model and Idempotency Specification

**Version:** 2.0  
**Date:** 2026-01-19

| Field              | Value                                                                                                   |
| ------------------ | ------------------------------------------------------------------------------------------------------- |
| **Document Owner** | PHR Platform Lead                                                                                       |
| **Classification** | Internal — Restricted                                                                                   |
| **Last Review**    | 2026-01-19                                                                                              |
| **Companion Docs** | [Runtime Architecture](phr_runtime_architecture.md), [Sequence Diagrams](phr_core_sequence_diagrams.md) |

> **📌 What changed in v2.0:** Added security-sensitive error handling guidelines (no PII in error responses, rate-limit error differentiation), circuit breaker error codes, and OWASP-aligned error disclosure policy.

This document defines the standard error taxonomy, response mapping, retry semantics, and idempotency policy for the PHR platform.

---

## 1. Goals

- make API failures predictable for clients
- separate validation, permission, conflict, and upstream failures cleanly
- prevent duplicate writes on retries
- support safe external integrations and async processing

---

## 2. Standard error shape

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Payload validation failed",
    "fields": {
      "dateOfBirth": ["Invalid date"]
    },
    "requestId": "req_123",
    "retryable": false
  }
}
```

### 2.1 Required fields

- `code`
- `message`
- `requestId`
- `retryable`

### 2.2 Optional fields

- `fields`
- `details`
- `upstreamSystem`
- `conflictResourceId`

---

## 3. Error taxonomy

| Code                   | HTTP | Retryable | Meaning                                      |
| ---------------------- | ---- | --------- | -------------------------------------------- |
| `VALIDATION_ERROR`     | 400  | no        | payload or query validation failed           |
| `AUTH_REQUIRED`        | 401  | no        | missing or expired auth                      |
| `FORBIDDEN`            | 403  | no        | actor lacks role, scope, or consent          |
| `NOT_FOUND`            | 404  | no        | resource not found or hidden by policy       |
| `CONFLICT`             | 409  | no        | duplicate or incompatible state              |
| `IDEMPOTENCY_CONFLICT` | 409  | no        | same idempotency key with mismatched payload |
| `RATE_LIMITED`         | 429  | yes       | too many requests                            |
| `UPSTREAM_UNAVAILABLE` | 502  | yes       | dependency unavailable                       |
| `UPSTREAM_TIMEOUT`     | 504  | yes       | dependency timed out                         |
| `PROCESSING_PENDING`   | 409  | yes       | async result not ready yet                   |
| `INTERNAL_ERROR`       | 500  | maybe     | unexpected server failure                    |

---

## 4. Domain-specific error usage

### 4.1 Auth and session

- invalid credentials -> `AUTH_REQUIRED`
- MFA required -> `FORBIDDEN` or dedicated challenge response, not generic 500

### 4.2 Patient/profile

- duplicate identifier -> `CONFLICT`
- illegal field mutation -> `FORBIDDEN`

### 4.3 Appointments

- slot already taken -> `CONFLICT`
- slot outside provider availability -> `VALIDATION_ERROR`

### 4.4 Documents

- unsupported content type -> `VALIDATION_ERROR`
- object storage upload failure -> `UPSTREAM_UNAVAILABLE`

### 4.5 Eligibility

- openIMIS timeout -> `UPSTREAM_TIMEOUT`
- patient/provider mismatch -> `FORBIDDEN`

### 4.6 OCR/voice

- result not complete -> `PROCESSING_PENDING`
- already confirmed -> `CONFLICT`

### 4.7 Claims

- duplicate submission key -> `CONFLICT`
- upstream rejection with valid transport -> 200 or 202 with business status in body, not 500

---

## 5. Idempotency policy

### 5.1 Operations that require idempotency keys

- patient registration when external retries are possible
- appointment booking
- document upload metadata finalization
- eligibility check logging if external retries occur
- OCR trigger
- transcription session start
- claim submission
- telemedicine session create/join

### 5.2 Header

Use:

```text
Idempotency-Key: <opaque-client-generated-key>
```

### 5.3 Storage behavior

For supported mutation routes, persist:

- idempotency key
- tenant id
- actor id
- route
- normalized request hash
- resulting resource id or response snapshot
- status
- created at / expires at

### 5.4 Replay rules

- same key + same normalized payload -> return stored response
- same key + different payload -> `IDEMPOTENCY_CONFLICT`
- expired key -> treat as new request

---

## 6. Recommended persistence tables

Core MVP:

- `IdempotencyRecord`
- optional `ExternalRequestLog`

Phase 2:

- `ClaimSubmissionAttempt`
- `TelemedicineSession`
- `OcrExtractionResult`
- `AudioTranscription`

---

## 7. Concurrency controls

### 7.1 Appointments

- transactionally verify slot availability
- unique or exclusion constraints on provider/date-time slot semantics

### 7.2 Consent grants

- block overlapping duplicate active grants when policy forbids them

### 7.3 Documents

- do not finalize duplicate object metadata on repeated upload finalization

### 7.4 Claims

- key on claim submit path by patient + encounter + coverage + client idempotency key

---

## 8. Retry policy

### 8.1 Safe automatic retries

- GET reads with transient upstream failures
- external transport failures before durable write
- worker-initiated integration retries through outbox or submission-attempt records

### 8.2 Unsafe automatic retries

- mutation retries without idempotency key
- retries after partial side effects without durable attempt state

### 8.3 Backoff

Use exponential backoff with jitter for:

- openIMIS requests
- SMS/email provider calls
- object storage transient failures
- OCR/ASR service calls

---

## 9. Client guidance

Clients must:

- surface validation fields inline
- distinguish `FORBIDDEN` from `NOT_FOUND`
- preserve and reuse idempotency keys during retryable submission flows
- treat `PROCESSING_PENDING` as a polling or websocket continuation state
- show request id in serious error states for support tracing

---

## 10. Audit requirements for failures

Audit sensitive failure modes:

- forbidden patient record access
- failed login attempts
- failed claim submission
- failed document retrieval on restricted records
- revoked or expired grant use attempts

---

## 11. Final recommendation

Adopt the error model and idempotency rules before implementation begins.
They are part of the platform contract, not an API polish task to do later.

---

## 12. Security-Sensitive Error Handling (Added in v2.0)

### 12.1 OWASP-Aligned Error Disclosure Policy

Error responses MUST NOT leak sensitive information that could aid an attacker:

| Error Scenario                  | Incorrect Response (Leaks Info)         | Correct Response                                        |
| ------------------------------- | --------------------------------------- | ------------------------------------------------------- |
| Login with wrong password       | "Password incorrect for user samujjwal" | "Invalid credentials"                                   |
| Login with nonexistent user     | "User not found"                        | "Invalid credentials" (same as wrong password)          |
| Access to another tenant's data | "Patient belongs to tenant X"           | "Not found" (treat as 404, not 403)                     |
| SQL/DB error in production      | Stack trace with SQL query              | "Internal error" with requestId for support correlation |
| Rate limit exceeded             | "You have made 97 requests"             | "Too many requests. Try again later."                   |

### 12.2 Error Response Rules

1. **Never include PII** in error messages (patient names, NID numbers, phone numbers)
2. **Never include internal identifiers** (database IDs, table names, column names) in client-facing errors
3. **Never differentiate** between "user not found" and "wrong password" (prevents user enumeration)
4. **Always include `requestId`** for support correlation without exposing internals
5. **Log full error details** (including stack traces) server-side only, not in API responses
6. **Redact sensitive fields** in error logs (passwords, tokens, NID numbers)

### 12.3 Circuit Breaker Error Codes (Added in v2.0)

| Error Code             | HTTP Status        | Description                                            | Client Action                                                     |
| ---------------------- | ------------------ | ------------------------------------------------------ | ----------------------------------------------------------------- |
| `UPSTREAM_UNAVAILABLE` | 503                | External service unreachable (circuit open)            | Show "service temporarily unavailable" + cached data if available |
| `UPSTREAM_TIMEOUT`     | 504                | External service timed out (circuit may be closing)    | Retry after `Retry-After` header value                            |
| `UPSTREAM_DEGRADED`    | 200 (with warning) | Partial data returned from cache while circuit is open | Show data with "information may not be current" indicator         |

### 12.4 Rate Limiting Error Responses

| Scenario                            | HTTP Status | Headers                                                         |
| ----------------------------------- | ----------- | --------------------------------------------------------------- |
| Per-IP rate limit (unauthenticated) | 429         | `Retry-After: 60`, `X-RateLimit-Limit`, `X-RateLimit-Remaining` |
| Per-user rate limit (authenticated) | 429         | `Retry-After: 30`, `X-RateLimit-Limit`, `X-RateLimit-Remaining` |
| Account lockout (auth failures)     | 429         | `Retry-After: 1800` (30 minutes)                                |

---

## 13. Data Classification in Error Logs (Added in v2.0)

Error logs containing health data follow the same classification scheme as the source data:

| Log Scenario                       | Data Classification           | Retention | Access                     |
| ---------------------------------- | ----------------------------- | --------- | -------------------------- |
| Validation error on patient create | C3 (PII in payload)           | 90 days   | Operations + Security team |
| Failed clinical data read          | C4 (references clinical data) | 1 year    | Security team only         |
| Auth failure                       | C2 (operational)              | 1 year    | Operations + Security team |
| Circuit breaker state change       | C1 (system health)            | 90 days   | All engineering            |
| Idempotency conflict               | C2 (operational)              | 90 days   | Operations                 |

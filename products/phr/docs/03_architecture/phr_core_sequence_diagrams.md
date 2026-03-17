# PHR Platform — Core Sequence Diagrams

**Version:** 2.0  
**Date:** 2026-01-19

| Field              | Value                                                                                                       |
| ------------------ | ----------------------------------------------------------------------------------------------------------- |
| **Document Owner** | PHR Platform Lead                                                                                           |
| **Classification** | Internal — Restricted                                                                                       |
| **Last Review**    | 2026-01-19                                                                                                  |
| **Companion Docs** | [Runtime Architecture](phr_runtime_architecture.md), [Error Model](phr_error_model_and_idempotency_spec.md) |

> **📌 What changed in v2.0:** Added Section 11 (Error Path Sequences — login failure, consent denied, eligibility timeout), Section 12 (Emergency QR Generation), Section 13 (Break-the-Glass Emergency Access), and error-handling notes for each existing diagram.

This document captures the primary end-to-end runtime sequences for Core MVP and the most important Phase 2 extension flows.

---

## 1. Login and session bootstrap

```mermaid
sequenceDiagram
  actor User
  participant Web as Web/Mobile Client
  participant API as IdentityModule
  participant Auth as Auth Provider
  participant DB as PostgreSQL

  User->>Web: Submit username/password
  Web->>API: POST /api/v1/auth/login
  API->>Auth: Validate credentials
  Auth-->>API: Tokens + subject
  API->>DB: Resolve actor linkage and tenant context
  API->>DB: Write login audit
  API-->>Web: accessToken + actor context
  Web->>API: GET /api/v1/auth/me
  API-->>Web: Current session context
```

---

## 2. Patient registration

```mermaid
sequenceDiagram
  actor Patient
  participant Client
  participant API as PatientModule
  participant Consent as ConsentModule
  participant Audit as AuditModule
  participant DB as PostgreSQL

  Patient->>Client: Fill registration form
  Client->>API: POST /api/v1/patients
  API->>API: Validate payload and policy
  API->>DB: Create Patient and linked profile data
  API->>Consent: Create baseline policy/grant defaults if required
  Consent->>DB: Persist consent baseline
  API->>Audit: Record patient create event
  Audit->>DB: Persist AuditLog
  API-->>Client: Patient created
```

---

## 3. Timeline read

```mermaid
sequenceDiagram
  actor User
  participant Client
  participant API as Timeline Query Handler
  participant Policy as Consent/Policy Layer
  participant Encounter as EncounterModule
  participant Obs as ObservationModule
  participant Cond as ConditionModule
  participant Med as MedicationModule
  participant Doc as DocumentModule
  participant Audit as AuditModule

  User->>Client: Open timeline
  Client->>API: GET /api/v1/patients/:id/timeline
  API->>Policy: Evaluate tenant/role/grant/consent
  Policy-->>API: Access decision
  API->>Encounter: Fetch encounters
  API->>Obs: Fetch observations
  API->>Cond: Fetch conditions
  API->>Med: Fetch medication requests
  API->>Doc: Fetch documents
  API->>Audit: Record sensitive read
  API-->>Client: Timeline response
```

---

## 4. Appointment booking

```mermaid
sequenceDiagram
  actor Patient
  participant Client
  participant API as AppointmentModule
  participant Pract as PractitionerModule
  participant Notify as NotificationModule
  participant DB as PostgreSQL

  Patient->>Client: Choose provider and slot
  Client->>API: GET /api/v1/providers/:id/availability
  API->>Pract: Resolve provider context
  API->>DB: Read availability source
  API-->>Client: Available slots
  Client->>API: POST /api/v1/appointments
  API->>DB: Check slot conflict in transaction
  API->>DB: Create appointment
  API->>Notify: Schedule reminder plan
  API-->>Client: Appointment confirmed
```

---

## 5. Document upload

```mermaid
sequenceDiagram
  actor User
  participant Client
  participant API as DocumentModule
  participant Ceph as Ceph/RADOS Gateway
  participant Audit as AuditModule
  participant DB as PostgreSQL

  User->>Client: Select file and metadata
  Client->>API: POST /api/v1/documents
  API->>API: Validate metadata and permissions
  API->>Ceph: Upload object
  Ceph-->>API: object key + checksum metadata
  API->>DB: Create DocumentReference and app metadata
  API->>Audit: Record upload event
  Audit->>DB: Persist AuditLog
  API-->>Client: Upload success
```

---

## 6. Access grant create and revoke

```mermaid
sequenceDiagram
  actor Patient
  participant Client
  participant API as ConsentModule
  participant Audit as AuditModule
  participant DB as PostgreSQL

  Patient->>Client: Configure grant
  Client->>API: POST /api/v1/access-grants
  API->>DB: Check duplicate/conflicting grants
  API->>DB: Create ConsentGrant
  API->>Audit: Record grant create
  Audit->>DB: Persist AuditLog
  API-->>Client: Grant active

  Patient->>Client: Revoke grant
  Client->>API: PATCH /api/v1/access-grants/:id/revoke
  API->>DB: Mark grant revoked/inactive
  API->>Audit: Record revoke event
  API-->>Client: Grant revoked
```

---

## 7. Coverage eligibility check

```mermaid
sequenceDiagram
  actor Staff
  participant Client
  participant API as InsuranceModule
  participant OpenIMIS as openIMIS
  participant DB as PostgreSQL
  participant Audit as AuditModule

  Staff->>Client: Start eligibility check
  Client->>API: POST /api/v1/insurance/eligibility-check
  API->>DB: Resolve patient coverage context
  API->>OpenIMIS: Eligibility request
  OpenIMIS-->>API: Eligibility result
  API->>DB: Persist EligibilityCheckLog
  API->>Audit: Record insurance access/check
  API-->>Client: Eligibility response
```

---

## 8. Core MVP OCR confirmation

```mermaid
sequenceDiagram
  actor User
  participant Client
  participant DataInput as DataInputModule
  participant Document as DocumentModule
  participant Review as ReviewQueueService
  participant Clinical as Clinical Modules
  participant DB as PostgreSQL

  User->>Client: Trigger OCR
  Client->>DataInput: POST /api/v1/documents/:id/ocr
  DataInput->>Document: Resolve source document
  DataInput->>DB: Create OcrExtractionResult
  DataInput-->>Client: queued

  Client->>DataInput: POST /api/v1/ocr-results/:id/confirm
  DataInput->>Review: Validate reviewed fields
  Review->>Clinical: Create approved resources
  Review->>DB: Create InputProvenance + update review state
  DataInput-->>Client: confirmed + created resources
```

---

## 9. Phase 2 claim submission

```mermaid
sequenceDiagram
  actor Staff
  participant Client
  participant Billing as BillingModule
  participant Insurance as InsuranceModule
  participant OpenIMIS as openIMIS
  participant DB as PostgreSQL

  Staff->>Client: Submit claim
  Client->>Billing: POST /api/v1/claims
  Billing->>Insurance: Validate coverage and patient context
  Billing->>DB: Create Claim + ClaimSubmissionAttempt
  Billing->>OpenIMIS: Submit FHIR Claim
  OpenIMIS-->>Billing: Claim accepted/rejected
  Billing->>DB: Update Claim/ClaimResponse state
  Billing-->>Client: submission outcome
```

---

## 10. Usage note

These diagrams are the baseline sequences for:

- architecture review
- service design
- incident analysis
- test case decomposition

Any new workflow added to Core MVP or Phase 2 should be accompanied by an additional sequence diagram before implementation starts.

---

## 11. Error Path Sequences (Added in v2.0)

### 11.1 Login failure — account lockout

```mermaid
sequenceDiagram
  actor User
  participant Client as Web/Mobile Client
  participant API as IdentityModule
  participant Auth as Auth Provider
  participant Audit as AuditModule
  participant DB as PostgreSQL

  User->>Client: Submit credentials (5th failed attempt)
  Client->>API: POST /api/v1/auth/login
  API->>Auth: Validate credentials
  Auth-->>API: 401 Invalid credentials
  API->>DB: Increment failed attempt counter
  API->>DB: Check failed attempt count (≥ 5)
  API->>Auth: Lock account for 30 minutes
  API->>Audit: Record lockout event (actorId, IP, timestamp)
  API-->>Client: 429 Account locked — try again in 30 minutes
  Note over API,Audit: Alert triggered to security monitoring<br/>if lockout count > 3 in 1 hour for any IP
```

### 11.2 Timeline read — consent denied

```mermaid
sequenceDiagram
  actor Provider
  participant Client
  participant API as EncounterModule
  participant Consent as ConsentModule
  participant Audit as AuditModule
  participant DB as PostgreSQL

  Provider->>Client: Request patient timeline
  Client->>API: GET /api/v1/patients/:patientId/timeline
  API->>Consent: Check active grants for (provider, patient)
  Consent->>DB: Query ConsentGrant table
  Consent-->>API: NO active grant found
  API->>Audit: Record access denied event (actorId, patientId, reason)
  API-->>Client: 403 Access denied — no active consent grant
  Note over Client: Client displays<br/>"Request access from patient" action
```

### 11.3 Eligibility check — openIMIS timeout with circuit breaker

```mermaid
sequenceDiagram
  actor Staff
  participant Client
  participant API as InsuranceModule
  participant CB as Circuit Breaker
  participant OpenIMIS as openIMIS API
  participant Audit as AuditModule
  participant Cache as Valkey

  Staff->>Client: Check patient eligibility
  Client->>API: POST /api/v1/insurance/eligibility-check
  API->>CB: Forward eligibility request
  CB->>CB: Check circuit state (CLOSED)
  CB->>OpenIMIS: FHIR CoverageEligibilityRequest
  Note over OpenIMIS: Timeout after 10 seconds
  OpenIMIS--xCB: No response (timeout)
  CB->>CB: Increment failure count (threshold: 5/30s)
  CB-->>API: Timeout error
  API->>Cache: Check for cached eligibility result
  Cache-->>API: Return cached result (if available, age < 24h)
  API->>Audit: Record eligibility check failure + fallback
  API-->>Client: Partial result with "data may not be current" warning
  Note over CB: If failure count ≥ 5,<br/>circuit opens for 60s
```

---

## 12. Emergency QR Generation (Added in v2.0)

```mermaid
sequenceDiagram
  actor Patient
  participant Client
  participant API as PatientModule
  participant Med as MedicationModule
  participant Allergy as AllergyModule
  participant DB as PostgreSQL

  Patient->>Client: Request Emergency QR card
  Client->>API: GET /api/v1/patients/:id/emergency-qr
  API->>DB: Fetch blood type, DOB from Patient
  API->>Med: Get active medications (names + dosages only)
  Med->>DB: Query active MedicationRequests
  Med-->>API: Active medication summary
  API->>Allergy: Get known allergies with severity
  Allergy->>DB: Query AllergyIntolerance records
  Allergy-->>API: Allergy summary
  API->>API: Build minimal emergency JSON payload
  API->>API: Generate QR code from payload
  API-->>Client: QR code image + printable card template
  Note over API: QR payload is NOT a full FHIR Bundle<br/>Contains only life-critical fields<br/>No full medical history exposed
```

---

## 13. Break-the-Glass Emergency Access (Added in v2.0)

```mermaid
sequenceDiagram
  actor EmergencyProvider as Emergency Provider
  participant Client
  participant API as IdentityModule
  participant Consent as ConsentModule
  participant Audit as AuditModule
  participant Notify as NotificationModule
  participant DB as PostgreSQL

  EmergencyProvider->>Client: Request emergency access (no prior consent)
  Client->>API: POST /api/v1/emergency-access
  Note over Client,API: Requires: provider credentials,<br/>patient identifier, emergency reason
  API->>API: Verify provider is authenticated and has emergency provider role
  API->>Consent: Create time-limited emergency grant (4 hours)
  Consent->>DB: Insert emergency ConsentGrant with expiry
  API->>Audit: Record break-the-glass event (HIGH severity)
  Audit->>DB: Write emergency access audit entry
  API->>Notify: Alert patient about emergency access
  Notify->>Notify: Send push + SMS to patient
  API-->>Client: Temporary access token (4h expiry) + limited data scope
  Note over Audit: Mandatory: Provider must submit<br/>justification within 24 hours
  Note over Audit: Auto-alert to compliance team<br/>for post-hoc review
```

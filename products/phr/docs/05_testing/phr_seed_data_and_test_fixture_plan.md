# PHR Platform — Seed Data and Test Fixture Plan

**Version:** 1.0  
**Date:** 2026-03-17  
**Last reviewed:** 2026-03-17  
**Document owner:** QA Lead  
**Approval status:** Draft for implementation use  
**Classification:** Internal — Restricted

This document defines the shared seed data, stubs, and deterministic fixture scenarios required by the Core MVP test suite.

---

## 1. Fixture design goals

- provide one stable tenant-isolated fixture set for API, integration, and UI tests
- cover happy path, deny path, degraded path, and security path scenarios
- keep seeded data small enough for local runs and rich enough for staging regression

---

## 2. Core fixture tenants

| Fixture tenant | Purpose |
| --- | --- |
| `tenant-alpha` | primary happy-path tenant for most MVP tests |
| `tenant-beta` | cross-tenant isolation and negative tests |
| `tenant-fchv` | community and assisted-registration scenarios |
| `tenant-admin` | audit, admin, and compliance-only scenarios |

---

## 3. Actor fixtures

| Actor | Tenant | Purpose |
| --- | --- | --- |
| `patient.self.primary` | `tenant-alpha` | self-service patient flows |
| `provider.general.primary` | `tenant-alpha` | provider read and write flows |
| `provider.emergency` | `tenant-alpha` | break-the-glass scenarios |
| `caregiver.scoped` | `tenant-alpha` | delegated access negative and positive tests |
| `fchv.fieldworker` | `tenant-fchv` | assisted registration and offline sync |
| `admin.audit` | `tenant-admin` | audit review and compliance flows |
| `patient.foreign` | `tenant-beta` | cross-tenant access denial |

---

## 4. Clinical and operational seed sets

| Seed set | Included data | Primary tests |
| --- | --- | --- |
| baseline patient | patient demographics, contacts, emergency contact, blood type | registration, profile, emergency QR |
| longitudinal history | encounters, observations, conditions, medications, documents | timeline, summary, trends |
| appointment schedule | practitioner, location, free slot, conflicting slot | booking and reminder tests |
| insurance coverage | active coverage, expired coverage, no coverage patient | eligibility and coverage summary |
| document corpus | PDF lab report, image prescription, EICAR test file | upload, OCR, malware, integrity |
| OCR review queue | high-confidence and low-confidence extraction results | review and confirm flows |
| ASR samples | Nepali, English, mixed-language dictation clips | transcription and accuracy tests |
| export artifacts | queued export job, ready artifact, expired artifact | portability, expiry, and audit tests |
| access grants | active grant, expired grant, revoked grant, emergency grant | consent runtime tests |

---

## 5. External stubs

| Stub | Required behaviors |
| --- | --- |
| Keycloak or auth stub | valid token, expired token, wrong tenant, wrong role |
| openIMIS stub | success, timeout, 5xx, partial response, malformed response |
| Ceph-compatible storage stub | upload success, checksum mismatch, permission denied |
| SMS provider stub | success, retryable failure, hard failure |
| OCR service stub | high confidence, low confidence, timeout |
| ASR service stub | Nepali transcript, mixed-language transcript, low-confidence output |

---

## 6. Fixture file plan

```text
products/phr/qa/fixtures/
  tenants.json
  actors.json
  patients.json
  encounters.json
  observations.json
  medications.json
  appointments.json
  coverage.json
  access-grants.json
  exports.json
  documents/
  audio/
```

---

## 7. Deterministic reset rules

- truncate and reseed all tenant-owned tables between integration test runs
- use fixed UUIDs for seeded actors and patients
- use fixed timestamps for timeline, reminder, and expiry behavior
- keep document hash fixtures immutable so integrity tests remain stable

This fixture plan is complete when the planned suite paths can reference concrete seed files rather than narrative scenarios.
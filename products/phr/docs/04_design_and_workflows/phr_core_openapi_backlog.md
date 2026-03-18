# PHR Platform — Core OpenAPI Backlog

**Version:** 1.0  
**Date:** 2026-03-17  
**Last reviewed:** 2026-03-17  
**Document owner:** API Lead  
**Approval status:** Active planning artifact  
**Classification:** Internal

This backlog tracks the route-by-route contract work needed to turn the Core MVP API surface into implementation-ready shared schemas and generated OpenAPI definitions.

---

## 1. Backlog board

| Priority | Route | Module | DTO draft status | Validation schema status | Example payload status | Test mapping status | Owner | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| P0 | `POST /api/v1/auth/login` | `IdentityModule` | Drafted | Pending | Pending | Ready | API Lead | stabilize auth error semantics |
| P0 | `GET /api/v1/auth/me` | `IdentityModule` | Drafted | Ready | Pending | Ready | API Lead | include effective permission list |
| P0 | `POST /api/v1/patients` | `PatientModule` | Drafted | Pending | Pending | Ready | Patient Lead | align with registration workflow |
| P0 | `GET /api/v1/patients/:id` | `PatientModule` | Drafted | Ready | Pending | Ready | Patient Lead | hide restricted fields by role |
| P0 | `PATCH /api/v1/patients/:id` | `PatientModule` | Drafted | Pending | Pending | Ready | Patient Lead | allowed-field matrix required |
| P0 | `GET /api/v1/patients/:id/timeline` | `PatientModule` | Drafted | Pending | Pending | Ready | Encounter Lead | final aggregator shape needed |
| P0 | `PATCH /api/v1/encounters/:id` | `EncounterModule` | Drafted | Pending | Pending | Ready | Encounter Lead | signed note model needed |
| P0 | `POST /api/v1/observations` | `ObservationModule` | Drafted | Pending | Pending | Ready | Observation Lead | code-system constraints pending |
| P0 | `GET /api/v1/patients/:id/observation-trends` | `ObservationModule` | Drafted | Pending | Pending | Ready | Observation Lead | chart series shape stable |
| P0 | `POST /api/v1/access-grants` | `ConsentModule` | Drafted | Pending | Pending | Ready | Consent Lead | depends on ConsentService approval |
| P1 | `POST /api/v1/medication-requests` | `MedicationModule` | Drafted | Pending | Pending | Ready | Medication Lead | dosage schema needed |
| P1 | `POST /api/v1/appointments` | `AppointmentModule` | Drafted | Pending | Pending | Ready | Appointment Lead | slot conflict semantics |
| P1 | `POST /api/v1/documents` | `DocumentModule` | Drafted | Pending | Pending | Ready | Document Lead | upload workflow and antivirus hooks |
| P1 | `GET /api/v1/patients/:id/documents` | `DocumentModule` | Drafted | Ready | Pending | Ready | Document Lead | pagination and visibility filters |
| P1 | `POST /api/v1/documents/:id/ocr` | `DataInputModule` | Drafted | Pending | Pending | Ready | Data Input Lead | confidence thresholds pending |
| P1 | `POST /api/v1/ocr-results/:id/confirm` | `DataInputModule` | Drafted | Pending | Pending | Ready | Data Input Lead | provenance payload needed |
| P1 | `POST /api/v1/audio-input` | `DataInputModule` | Drafted | Pending | Pending | Ready | Data Input Lead | media reference contract pending |
| P1 | `POST /api/v1/insurance/eligibility-check` | `InsuranceModule` | Drafted | Pending | Pending | Ready | Insurance Lead | fallback/degraded response semantics |
| P1 | `GET /api/v1/patients/:id/emergency-qr` | `PatientModule` | Drafted | Ready | Pending | Ready | Patient Lead | privacy-safe response locked |
| P1 | `POST /api/v1/fchv/patients` | `PatientModule` | Drafted | Pending | Pending | Ready | Community Care Lead | pending status and scoped offline queue rules |
| P1 | `GET /api/v1/fchv/patients` | `PatientModule` | Drafted | Pending | Pending | Ready | Community Care Lead | role guard and list filters |
| P1 | `GET /api/v1/caregivers/me/dependents` | `FamilyModule` | Drafted | Pending | Pending | Ready | Family Lead | delegated-scope filtering frozen in route contract pack |
| P1 | `GET /api/v1/caregivers/me/dependents/:id/summary` | `FamilyModule` | Drafted | Pending | Pending | Ready | Family Lead | caregiver-safe summary cards and sync-state projection |
| P1 | `GET /api/v1/patients/:id/bills` | `BillingModule` | Drafted | Pending | Pending | Ready | Billing Lead | invoice list and outstanding-balance shape frozen |
| P1 | `POST /api/v1/payments` | `BillingModule` | Drafted | Pending | Pending | Ready | Billing Lead | payment intent, idempotency, and wallet redirect semantics |
| P1 | `GET /api/v1/payments/:id` | `BillingModule` | Drafted | Pending | Pending | Ready | Billing Lead | status polling and receipt-link semantics frozen |
| P1 | `POST /api/v1/payments/:id/confirm` | `BillingModule` | Drafted | Pending | Pending | Ready | Billing Lead | confirmation payload tolerates async gateway callbacks |
| P1 | `POST /api/v1/referrals` | `ReferralModule` | Drafted | Pending | Pending | Ready | Referral Lead | summary attachment and receiving-facility schema drafted |
| P1 | `GET /api/v1/patients/:id/referrals` | `ReferralModule` | Drafted | Pending | Pending | Ready | Referral Lead | patient/provider/caregiver visibility rules frozen |
| P1 | `GET /api/v1/referrals/:id` | `ReferralModule` | Drafted | Pending | Pending | Ready | Referral Lead | referral status timeline and summary attachment response |
| P1 | `GET /api/v1/imaging-studies/:id` | `ImagingModule` | Drafted | Pending | Pending | Ready | Imaging Lead | DICOM metadata and signed viewer URL frozen |
| P1 | `GET /api/v1/imaging-studies/:id/download` | `ImagingModule` | Drafted | Pending | Pending | Ready | Imaging Lead | signed download response and expiry behavior frozen |
| P0 | `POST /api/v1/patients/:id/exports` | `InteroperabilityModule` | Drafted | Pending | Pending | Ready | API Lead | create export job and return artifact status envelope |
| P0 | `GET /api/v1/patients/:id/exports/:exportId` | `InteroperabilityModule` | Drafted | Pending | Pending | Ready | API Lead | poll status and download URL before artifact expiry |

---

## 2. Exit condition

The backlog is complete when all Core MVP routes have:

- named request and response DTOs
- validation schemas assigned to `@phr/schemas`
- example payloads checked into docs or generated OpenAPI examples
- linked testcase coverage
- no unresolved ambiguity around tenant, consent, or error behavior
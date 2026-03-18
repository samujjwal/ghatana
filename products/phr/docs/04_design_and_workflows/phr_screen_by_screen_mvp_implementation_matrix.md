# PHR Platform — Screen-by-Screen Implementation Matrix

**Version:** 2.0  
**Date:** 2026-01-19

| Field              | Value                                                                                                                                          |
| ------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| **Document Owner** | Frontend Lead                                                                                                                                  |
| **Classification** | Internal                                                                                                                                       |
| **Last Review**    | 2026-01-19                                                                                                                                     |
| **Companion Docs** | [Frontend Route Map](phr_frontend_route_and_component_map.md), [Traceability Matrix](../01_governance/phr_requirements_traceability_matrix.md) |

> **📌 What changed in v2.0:** Added emergency QR card screen, FCHV simplified flow screens, offline state indicators per screen, WCAG 2.2 AA compliance column, and data classification column per screen.

This document turns the activation plan into a build-oriented implementation matrix for the **core MVP** and the explicitly marked **Phase 2 extension set**.

For each screen/flow, it maps:

- user role
- purpose
- primary NestJS module
- suggested API endpoints
- Prisma models
- FHIR resources
- validation schema
- permission model
- main UI state
- test coverage expectations

Scope note:

- Core MVP flows cover patient records, appointments, documents, coverage baseline, consent, admin audit, provider clinical workflows, and assisted OCR/voice entry.
- Claims remain documented as a planned Phase 2 extension, while caregiver flows are fully in-scope for MVP delivery.
- Telemedicine consultation room screens are intentionally excluded until dedicated call-session contracts are added.

This is intentionally scoped for a **modular NestJS platform**, not a distributed microservice estate.

---

## 1. How to use this document

Use this matrix to drive:

- sprint planning
- backend endpoint design
- frontend route/page design
- form schema creation
- authorization rules
- QA test case generation
- traceability across UI → API → DB → FHIR

---

## 2. Conventions

## 2.1 Roles

- Patient
- Provider
- Caregiver
- Admin
- FCHV

## 2.2 API style

Suggested API style:

- REST-first for MVP
- versioned under `/api/v1`
- use service-specific route groups without implying separate deployables

Example:

- `/api/v1/patients`
- `/api/v1/encounters`
- `/api/v1/appointments`

## 2.3 Validation

Suggested stack:

- frontend: Zod + React Hook Form + Ghatana design system form components (`@ghatana/design-system`)
- backend: class-validator and/or Zod-compatible shared schemas (`@phr/schemas`)
- mapping layer: DTO ↔ domain ↔ Prisma ↔ FHIR

## 2.4 Documentation Standards

All implementations must include JSDoc with `@doc.*` tags:

| Tag            | Purpose                          | Example Usage                                      |
| -------------- | -------------------------------- | -------------------------------------------------- |
| `@doc.type`    | Component/service classification | `screen`, `component`, `hook`, `api-route`         |
| `@doc.purpose` | One-line description             | `Patient registration screen with form validation` |
| `@doc.layer`   | Architecture layer               | `screen`, `feature`, `shared`, `infrastructure`    |
| `@doc.pattern` | UI/UX pattern                    | `form-wizard`, `master-detail`, `list-detail`      |
| `@doc.related` | Cross-reference to related docs  | `@see phr_mvp_route_contract_pack.md#3.2`          |

**Example Screen Documentation:**

```typescript
/**
 * Patient registration screen
 *
 * @doc.type screen
 * @doc.purpose Patient registration with step-by-step form validation
 * @doc.layer feature
 * @doc.pattern form-wizard
 * @doc.domain patient
 * @doc.related phr_mvp_route_contract_pack.md#3.2, phr_migration_plan_and_module_ownership_matrix.md#Phase-2
 * @doc.uses @ghatana/ui/forms, @ghatana/ui/wizard
 */
export function PatientRegistrationScreen() {
  // implementation
}
```

---

# 3. Patient-facing screens

## 3.1 Sign in / account entry

| Field               | Value                                                                        |
| ------------------- | ---------------------------------------------------------------------------- |
| Screen              | Sign in / account entry                                                      |
| User roles          | Patient, Provider, Caregiver, Admin                                          |
| Purpose             | Authenticate user and establish actor context                                |
| NestJS modules      | IdentityModule, AuthModule                                                   |
| Suggested endpoints | `POST /api/v1/auth/login`, `GET /api/v1/auth/me`, `POST /api/v1/auth/logout` |
| Prisma models       | app-local actor/account linkage tables                                       |
| FHIR resources      | none directly                                                                |
| Validation          | login schema, OTP/MFA schema if enabled                                      |
| Permissions         | public for login, authenticated for session bootstrap                        |
| UI state            | session state, actor role, tenant/facility context                           |
| Main tests          | success login, invalid creds, MFA challenge, session restore, logout         |

### Notes

- keep auth UI minimal
- actor context resolution happens immediately after login
- route user by effective role and permissions

---

## 3.2 Patient registration

| Field               | Value                                                                                   |
| ------------------- | --------------------------------------------------------------------------------------- |
| Screen              | Patient registration                                                                    |
| User roles          | Patient, Admin, Provider (assisted registration)                                        |
| Purpose             | Create patient profile root                                                             |
| NestJS modules      | PatientModule, ConsentModule, AuditModule                                               |
| Suggested endpoints | `POST /api/v1/patients`, `GET /api/v1/patients/:id`                                     |
| Prisma models       | Patient, Identifier, HumanName, Address, ContactPoint                                   |
| FHIR resources      | Patient                                                                                 |
| Validation          | patient registration Zod schema, address/contact schema                                 |
| Permissions         | patient self-create or admin/provider-assisted per policy                               |
| UI state            | registration wizard state, save draft state                                             |
| Main tests          | valid create, duplicate identifier handling, required fields, partial save, audit event |

### Required fields

- first/last name
- DOB
- gender
- phone
- optional email
- address
- emergency contact basics

---

## 3.3 Patient dashboard

| Field               | Value                                                                                     |
| ------------------- | ----------------------------------------------------------------------------------------- |
| Screen              | Patient dashboard                                                                         |
| User roles          | Patient, Caregiver (scoped)                                                               |
| Purpose             | Show summary of health record, upcoming appointments, active meds, alerts                 |
| NestJS modules      | PatientModule, AppointmentModule, MedicationModule, ObservationModule, NotificationModule |
| Suggested endpoints | `GET /api/v1/patients/:id/dashboard`, `GET /api/v1/patients/:id/summary`                  |
| Prisma models       | Patient, Appointment, MedicationRequest, Observation, Condition                           |
| FHIR resources      | Patient, Appointment, MedicationRequest, Observation, Condition                           |
| Validation          | query param schema                                                                        |
| Permissions         | patient self or delegated caregiver access                                                |
| UI state            | selected patient context, dashboard filters/time range                                    |
| Main tests          | dashboard load, no-data state, caregiver scope enforcement, loading/error states          |

### Widgets

- profile summary
- next appointment
- active medications
- recent observations
- active conditions
- recent uploaded documents
- consent/access alerts

---

## 3.4 Patient profile

| Field               | Value                                                                                  |
| ------------------- | -------------------------------------------------------------------------------------- |
| Screen              | Patient profile                                                                        |
| User roles          | Patient, Caregiver (limited), Provider (read)                                          |
| Purpose             | View/update demographic and contact information                                        |
| NestJS modules      | PatientModule                                                                          |
| Suggested endpoints | `GET /api/v1/patients/:id`, `PATCH /api/v1/patients/:id`                               |
| Prisma models       | Patient, Identifier, HumanName, Address, ContactPoint                                  |
| FHIR resources      | Patient                                                                                |
| Validation          | profile update schema                                                                  |
| Permissions         | owner or permitted delegate; provider read-only unless specific workflow               |
| UI state            | edit mode, field-level dirty state                                                     |
| Main tests          | update allowed fields, forbidden field edit, validation errors, optimistic UI rollback |

---

## 3.5 Medical history timeline

| Field               | Value                                                                                 |
| ------------------- | ------------------------------------------------------------------------------------- |
| Screen              | Medical history timeline                                                              |
| User roles          | Patient, Provider, Caregiver (scoped)                                                 |
| Purpose             | Longitudinal view across encounters, conditions, observations, meds, documents        |
| NestJS modules      | EncounterModule, ObservationModule, ConditionModule, DocumentModule, MedicationModule |
| Suggested endpoints | `GET /api/v1/patients/:id/timeline`, `GET /api/v1/patients/:id/encounters`            |
| Prisma models       | Encounter, Observation, Condition, MedicationRequest, DocumentReference               |
| FHIR resources      | Encounter, Observation, Condition, MedicationRequest, DocumentReference               |
| Validation          | filter schema: date range, category, facility, provider                               |
| Permissions         | record access policy + consent-aware rules                                            |
| UI state            | filters, timeline grouping, expanded item state                                       |
| Main tests          | timeline grouping, filter behavior, pagination, permission enforcement, no-data state |

---

## 3.6 Conditions list

| Field               | Value                                                               |
| ------------------- | ------------------------------------------------------------------- |
| Screen              | Conditions / diagnosis list                                         |
| User roles          | Patient, Provider, Caregiver (scoped)                               |
| Purpose             | Display active/resolved diagnoses                                   |
| NestJS modules      | ConditionModule                                                     |
| Suggested endpoints | `GET /api/v1/patients/:id/conditions`, `GET /api/v1/conditions/:id` |
| Prisma models       | Condition                                                           |
| FHIR resources      | Condition                                                           |
| Validation          | list filter schema                                                  |
| Permissions         | patient self, provider with authorized access, caregiver delegated  |
| UI state            | active/resolved filters, sort state                                 |
| Main tests          | active/resolved grouping, sort/filter, detail navigation            |

---

## 3.7 Observations / vitals / lab trend view

| Field               | Value                                                                                  |
| ------------------- | -------------------------------------------------------------------------------------- |
| Screen              | Observations and trends                                                                |
| User roles          | Patient, Provider                                                                      |
| Purpose             | View clinical measurements and trends                                                  |
| NestJS modules      | ObservationModule                                                                      |
| Suggested endpoints | `GET /api/v1/patients/:id/observations`, `GET /api/v1/patients/:id/observation-trends` |
| Prisma models       | Observation                                                                            |
| FHIR resources      | Observation                                                                            |
| Validation          | trend query schema, code/date-range schema                                             |
| Permissions         | consent-aware record access                                                            |
| UI state            | metric selection, chart range, chart tooltip/series state                              |
| Main tests          | chart renders, range changes, abnormal highlight, empty state, multi-series comparison |

### Typical metrics

- BP
- heart rate
- temperature
- SpO2
- glucose
- weight
- BMI-derived display

---

## 3.8 Medications list

| Field               | Value                                                                         |
| ------------------- | ----------------------------------------------------------------------------- |
| Screen              | Medications                                                                   |
| User roles          | Patient, Provider, Caregiver                                                  |
| Purpose             | Show active medications and medication history                                |
| NestJS modules      | MedicationModule                                                              |
| Suggested endpoints | `GET /api/v1/patients/:id/medication-requests`, `GET /api/v1/medications/:id` |
| Prisma models       | Medication, MedicationRequest                                                 |
| FHIR resources      | Medication, MedicationRequest                                                 |
| Validation          | list filter schema                                                            |
| Permissions         | same record-access rules                                                      |
| UI state            | active/history tab state, medication filters                                  |
| Main tests          | active meds, historical meds, medication detail, dosage display, sort/filter  |

---

## 3.9 Appointment list

| Field               | Value                                                                    |
| ------------------- | ------------------------------------------------------------------------ |
| Screen              | Appointments                                                             |
| User roles          | Patient, Provider, Caregiver                                             |
| Purpose             | Show upcoming and past appointments                                      |
| NestJS modules      | AppointmentModule                                                        |
| Suggested endpoints | `GET /api/v1/patients/:id/appointments`, `GET /api/v1/appointments/:id`  |
| Prisma models       | Appointment                                                              |
| FHIR resources      | Appointment                                                              |
| Validation          | date range and status filter schema                                      |
| Permissions         | patient/caregiver scoped; provider sees own schedule/patient links       |
| UI state            | upcoming/past tabs, date filter                                          |
| Main tests          | list rendering, cancel/reschedule path visibility, role-based visibility |

---

## 3.10 Book appointment

| Field               | Value                                                                                        |
| ------------------- | -------------------------------------------------------------------------------------------- |
| Screen              | Book appointment                                                                             |
| User roles          | Patient, Caregiver, Provider staff                                                           |
| Purpose             | Create an appointment                                                                        |
| NestJS modules      | AppointmentModule, PractitionerModule, NotificationModule                                    |
| Suggested endpoints | `GET /api/v1/providers/:id/availability`, `POST /api/v1/appointments`                        |
| Prisma models       | Appointment, provider schedule/slot app tables                                               |
| FHIR resources      | Appointment, later Schedule/Slot semantics                                                   |
| Validation          | booking request schema                                                                       |
| Permissions         | authenticated patient/caregiver/staff under booking policy                                   |
| UI state            | provider selection, slot selection, reason-for-visit draft                                   |
| Main tests          | slot fetch, successful booking, conflict handling, double-book prevention, reminder creation |

---

## 3.11 Documents / uploaded records

| Field               | Value                                                                                          |
| ------------------- | ---------------------------------------------------------------------------------------------- |
| Screen              | Documents                                                                                      |
| User roles          | Patient, Provider, Caregiver                                                                   |
| Purpose             | View/upload reports, scans, summaries                                                          |
| NestJS modules      | DocumentModule, AuditModule                                                                    |
| Suggested endpoints | `GET /api/v1/patients/:id/documents`, `POST /api/v1/documents`, `GET /api/v1/documents/:id`    |
| Prisma models       | DocumentReference, StoredObject, DocumentVersion                                               |
| FHIR resources      | DocumentReference                                                                              |
| Validation          | upload metadata schema                                                                         |
| Permissions         | upload/read based on role and consent                                                          |
| UI state            | upload progress, file preview, filters, version history toggle                                 |
| Main tests          | upload success, invalid file type, large file limits, download audit logging, preview fallback |

---

## 3.12 Insurance summary

| Field               | Value                                                                           |
| ------------------- | ------------------------------------------------------------------------------- |
| Screen              | Insurance summary                                                               |
| User roles          | Patient, Provider staff                                                         |
| Purpose             | Show coverage and eligibility context                                           |
| NestJS modules      | InsuranceModule                                                                 |
| Suggested endpoints | `GET /api/v1/patients/:id/coverage`, `POST /api/v1/insurance/eligibility-check` |
| Prisma models       | Coverage, EligibilityCheckLog                                                   |
| FHIR resources      | Coverage                                                                        |
| Validation          | eligibility request schema                                                      |
| Permissions         | patient self or authorized provider/staff                                       |
| UI state            | current policy selection, eligibility check state                               |
| Main tests          | coverage display, eligibility check success/failure, missing coverage flow      |

---

## 3.13 Payments

| Field               | Value                                                                                   |
| ------------------- | --------------------------------------------------------------------------------------- |
| Screen              | Payments                                                                                |
| User roles          | Patient, Caregiver (granted), Provider staff                                            |
| Purpose             | View bills, pay outstanding balances, and retrieve receipts                             |
| NestJS modules      | BillingModule, InsuranceModule, AuditModule                                             |
| Suggested endpoints | `GET /api/v1/patients/:id/bills`, `POST /api/v1/payments`, `GET /api/v1/payments/:id`  |
| Prisma models       | Invoice, PaymentNotice, PaymentReconciliation                                           |
| FHIR resources      | Invoice, PaymentNotice                                                                  |
| Validation          | payment initiation schema                                                               |
| Permissions         | patient self or granted caregiver/staff under billing policy                            |
| UI state            | outstanding bill selection, payment pending, success, failure, receipt ready            |
| Main tests          | bill list load, payment initiation, confirmation, receipt retrieval, denied access      |

---

## 3.14 Referrals

| Field               | Value                                                                                          |
| ------------------- | ---------------------------------------------------------------------------------------------- |
| Screen              | Referrals                                                                                      |
| User roles          | Patient, Provider, Caregiver (view only when granted)                                          |
| Purpose             | Track created referrals, referral status, and specialist scheduling                            |
| NestJS modules      | ReferralModule, EncounterModule, ConsentModule                                                 |
| Suggested endpoints | `POST /api/v1/referrals`, `GET /api/v1/patients/:id/referrals`, `GET /api/v1/referrals/:id`   |
| Prisma models       | ServiceRequest, ReferralStatusEvent, DocumentReference                                         |
| FHIR resources      | ServiceRequest, DocumentReference                                                              |
| Validation          | referral create/update schema                                                                  |
| Permissions         | patient self, authorized provider, caregiver within explicit grant                             |
| UI state            | referral list filters, status timeline, detail drawer                                          |
| Main tests          | referral create, referral status update, patient visibility, specialist handoff error handling |

---

## 3.15 Imaging viewer

| Field               | Value                                                                                      |
| ------------------- | ------------------------------------------------------------------------------------------ |
| Screen              | Imaging viewer                                                                             |
| User roles          | Patient, Provider, Caregiver                                                               |
| Purpose             | Open imaging studies, read radiology report, and securely download imaging artifacts       |
| NestJS modules      | ImagingModule, DocumentModule, AuditModule                                                 |
| Suggested endpoints | `GET /api/v1/imaging-studies/:id`, `GET /api/v1/imaging-studies/:id/download`              |
| Prisma models       | ImagingStudy, DiagnosticReport, StoredObject                                               |
| FHIR resources      | ImagingStudy, DiagnosticReport                                                             |
| Validation          | viewer-access schema                                                                       |
| Permissions         | same access model as the linked patient record                                             |
| UI state            | study loading, viewer tool state, report panel, secure download gating                     |
| Main tests          | study load, viewer controls, report display, secure download audit, access denial handling |

---

## 3.16 Claim status (Phase 2)

| Field               | Value                                                                              |
| ------------------- | ---------------------------------------------------------------------------------- |
| Screen              | Claims                                                                             |
| User roles          | Patient, Provider staff                                                            |
| Purpose             | View submitted claims and status                                                   |
| NestJS modules      | BillingModule, InsuranceModule                                                     |
| Suggested endpoints | `GET /api/v1/patients/:id/claims`, `GET /api/v1/claims/:id`, `POST /api/v1/claims` |
| Prisma models       | Claim, ClaimResponse, ClaimSubmissionAttempt                                       |
| FHIR resources      | Claim, ClaimResponse                                                               |
| Validation          | claim submission schema                                                            |
| Permissions         | patient self, provider/staff under billing policy                                  |
| UI state            | claim list filters, submission step state                                          |
| Main tests          | claim create, claim status update, resubmission path, error handling               |

---

## 3.17 Sharing and access grants

| Field               | Value                                                                                                            |
| ------------------- | ---------------------------------------------------------------------------------------------------------------- |
| Screen              | Sharing / access grants                                                                                          |
| User roles          | Patient                                                                                                          |
| Purpose             | Grant, view, and revoke access                                                                                   |
| NestJS modules      | ConsentModule, AuditModule                                                                                       |
| Suggested endpoints | `GET /api/v1/patients/:id/access-grants`, `POST /api/v1/access-grants`, `PATCH /api/v1/access-grants/:id/revoke` |
| Prisma models       | ConsentGrant, AuditLog                                                                                           |
| FHIR resources      | app grant model first, later Consent                                                                             |
| Validation          | grant schema, revoke schema                                                                                      |
| Permissions         | patient owner only except emergency/admin overrides by policy                                                    |
| UI state            | grant modal state, expiration selection, revoke confirmation                                                     |
| Main tests          | create grant, revoke grant, expired grant handling, access reflection in provider UI                             |

---

## 3.18 Document upload (multi-modal)

| Field               | Value                                                                                                                                                |
| ------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| Screen              | Document upload                                                                                                                                      |
| User roles          | Patient, Caregiver                                                                                                                                   |
| Purpose             | Upload documents via file picker, camera capture, or batch import for OCR processing                                                                 |
| NestJS modules      | DataInputModule, DocumentModule, AuditModule                                                                                                         |
| Suggested endpoints | `POST /api/v1/documents`, `POST /api/v1/documents/:id/ocr`                                                                                           |
| Prisma models       | DocumentReference, StoredObject, OcrExtractionResult                                                                                                 |
| FHIR resources      | DocumentReference                                                                                                                                    |
| Validation          | file type (PDF, PNG, JPEG, TIFF), max size (20 MB), metadata schema                                                                                  |
| Permissions         | patient: own documents; caregiver: dependent documents with active grant                                                                             |
| UI state            | upload progress, camera preview, file queue, upload method selector, OCR trigger toggle                                                              |
| Platform libraries  | `@ghatana/design-system` (Button, Card, ProgressBar, FileInput), `@ghatana/i18n`                                                                     |
| Main tests          | file picker upload, camera capture, batch upload, invalid file type rejection, oversize rejection, OCR auto-trigger, upload audit log, drag-and-drop |

---

## 3.19 OCR review

| Field               | Value                                                                                                                                                                                          |
| ------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Screen              | OCR review                                                                                                                                                                                     |
| User roles          | Patient, Caregiver, Provider                                                                                                                                                                   |
| Purpose             | Review, correct, and confirm OCR-extracted fields and FHIR resource drafts from scanned documents                                                                                              |
| NestJS modules      | DataInputModule, DocumentModule, AuditModule                                                                                                                                                   |
| Suggested endpoints | `GET /api/v1/ocr-results/:id`, `POST /api/v1/ocr-results/:id/confirm`                                                                                                                          |
| Prisma models       | OcrExtractionResult, InputProvenance, ReviewQueueItem, FHIR resource tables                                                                                                                    |
| FHIR resources      | Observation, Condition, MedicationRequest, AllergyIntolerance (created on confirm)                                                                                                             |
| Validation          | field correction schema, confidence threshold (< 0.7 flags for review)                                                                                                                         |
| Permissions         | patient: own documents; provider: facility-scoped; caregiver: via dependent grant                                                                                                              |
| UI state            | document preview pane, field highlight layer, per-field accept/edit/reject toggles, confidence badges, FHIR draft cards, encounter selector                                                    |
| Platform libraries  | `@ghatana/design-system` (Card, Badge, Form, Dialog, Button), `@ghatana/i18n`                                                                                                                  |
| Main tests          | display pending/completed OCR, accept all fields, reject all fields, edit field values, confidence badge colours, FHIR resource creation on confirm, provenance audit, low-confidence flagging |

---

## 3.20 Voice data entry (patient)

| Field               | Value                                                                                                                                                                                                               |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Screen              | Patient voice data entry                                                                                                                                                                                            |
| User roles          | Patient                                                                                                                                                                                                             |
| Purpose             | Record and transcribe voice input for symptom logging, vitals self-report, and medication adherence via ASR                                                                                                         |
| NestJS modules      | DataInputModule, AuditModule                                                                                                                                                                                        |
| Suggested endpoints | `POST /api/v1/audio-input`, `GET /api/v1/transcriptions/:id`, `POST /api/v1/transcriptions/:id/confirm`                                                                                                             |
| Prisma models       | AudioTranscription, InputProvenance, ReviewQueueItem, FHIR resource tables                                                                                                                                          |
| FHIR resources      | Observation (vitals, symptoms), Condition (self-reported)                                                                                                                                                           |
| Validation          | language BCP-47 format, audio format (webm_opus, wav_pcm), confidence threshold                                                                                                                                     |
| Permissions         | patient: own transcriptions only; sourceType must be patient_self_report                                                                                                                                            |
| UI state            | recording state (idle/recording/paused/processing), live transcript, extracted entities, accept/reject toggles, Nepali language selector                                                                            |
| Platform libraries  | `@ghatana/audio-video-client` (SttClient), `@ghatana/audio-video-ui` (AudioRecorder, WaveformVisualizer), `@ghatana/audio-video-types`, `@ghatana/realtime`, `@ghatana/design-system`, `@ghatana/i18n`              |
| Main tests          | start recording, pause/resume, stop and process, live transcript display, entity extraction, accept/reject entities, FHIR resource creation, Nepali ASR, provenance trail, permission guard (no provider_dictation) |

---

# 4. Provider-facing screens

## 4.1 Provider dashboard

| Field               | Value                                                                 |
| ------------------- | --------------------------------------------------------------------- |
| Screen              | Provider dashboard                                                    |
| User roles          | Provider                                                              |
| Purpose             | Work queue, appointments, recent patients, alerts                     |
| NestJS modules      | AppointmentModule, PatientModule, NotificationModule, AnalyticsModule |
| Suggested endpoints | `GET /api/v1/providers/me/dashboard`                                  |
| Prisma models       | Appointment, Patient, alerts/projection tables                        |
| FHIR resources      | Appointment, Patient                                                  |
| Validation          | query schema                                                          |
| Permissions         | provider authenticated                                                |
| UI state            | time range, facility context, queue filter                            |
| Main tests          | assigned schedule load, empty states, role enforcement                |

---

## 4.2 Patient search

| Field               | Value                                                                |
| ------------------- | -------------------------------------------------------------------- |
| Screen              | Patient search                                                       |
| User roles          | Provider, Admin staff                                                |
| Purpose             | Find patient record by identifier/name/contact                       |
| NestJS modules      | SearchModule, PatientModule                                          |
| Suggested endpoints | `GET /api/v1/search/patients`                                        |
| Prisma models       | Patient, Identifier, patient search projections                      |
| FHIR resources      | Patient                                                              |
| Validation          | search query schema                                                  |
| Permissions         | provider/admin search policy                                         |
| UI state            | query text, filters, result selection                                |
| Main tests          | exact/fuzzy search, pagination, zero results, permission enforcement |

---

## 4.3 Patient summary (provider view)

| Field               | Value                                                                              |
| ------------------- | ---------------------------------------------------------------------------------- |
| Screen              | Patient summary                                                                    |
| User roles          | Provider                                                                           |
| Purpose             | Clinical summary view before chart review                                          |
| NestJS modules      | PatientModule, EncounterModule, MedicationModule, ObservationModule, ConsentModule |
| Suggested endpoints | `GET /api/v1/patients/:id/provider-summary`                                        |
| Prisma models       | Patient, Encounter, Observation, MedicationRequest, Condition, ConsentGrant        |
| FHIR resources      | Patient, Encounter, Observation, MedicationRequest, Condition                      |
| Validation          | summary query schema                                                               |
| Permissions         | provider access grant must be valid                                                |
| UI state            | summary section expansion/collapse                                                 |
| Main tests          | access denied without grant, summary completeness, stale grant handling            |

---

## 4.4 Encounter detail / chart review

| Field               | Value                                                                                 |
| ------------------- | ------------------------------------------------------------------------------------- |
| Screen              | Encounter detail                                                                      |
| User roles          | Provider                                                                              |
| Purpose             | Review or document encounter                                                          |
| NestJS modules      | EncounterModule, ObservationModule, ConditionModule, MedicationModule, DocumentModule |
| Suggested endpoints | `GET /api/v1/encounters/:id`, `PATCH /api/v1/encounters/:id`                          |
| Prisma models       | Encounter, Observation, Condition, MedicationRequest, DocumentReference               |
| FHIR resources      | Encounter, Observation, Condition, MedicationRequest, DocumentReference               |
| Validation          | encounter update schema                                                               |
| Permissions         | authorized provider only                                                              |
| UI state            | tab state, note draft, orders/prescriptions draft                                     |
| Main tests          | encounter load, update success, access controls, draft preservation                   |

---

## 4.5 Record vitals / observation entry

| Field               | Value                                                             |
| ------------------- | ----------------------------------------------------------------- |
| Screen              | Observation entry                                                 |
| User roles          | Provider, nurse                                                   |
| Purpose             | Add/update observation values                                     |
| NestJS modules      | ObservationModule                                                 |
| Suggested endpoints | `POST /api/v1/observations`, `PATCH /api/v1/observations/:id`     |
| Prisma models       | Observation                                                       |
| FHIR resources      | Observation                                                       |
| Validation          | observation create/update schema                                  |
| Permissions         | provider/nurse role with patient access                           |
| UI state            | metric form state, unit handling, validation feedback             |
| Main tests          | numeric validation, unit handling, abnormal flagging, audit trail |

---

## 4.6 Medication request entry

| Field               | Value                                                                                         |
| ------------------- | --------------------------------------------------------------------------------------------- |
| Screen              | Medication request entry                                                                      |
| User roles          | Provider                                                                                      |
| Purpose             | Prescribe or update medication request                                                        |
| NestJS modules      | MedicationModule, AllergyModule                                                               |
| Suggested endpoints | `POST /api/v1/medication-requests`, `PATCH /api/v1/medication-requests/:id`                   |
| Prisma models       | MedicationRequest, Medication, later AllergyIntolerance                                       |
| FHIR resources      | MedicationRequest, Medication                                                                 |
| Validation          | medication request schema                                                                     |
| Permissions         | authorized provider                                                                           |
| UI state            | medication lookup, dosage form state, signature/submit state                                  |
| Main tests          | create/update request, duplicate active medication warnings, allergy interaction warning path |

---

## 4.7 Provider calendar

| Field               | Value                                                                            |
| ------------------- | -------------------------------------------------------------------------------- |
| Screen              | Provider calendar                                                                |
| User roles          | Provider                                                                         |
| Purpose             | View/manage appointment schedule                                                 |
| NestJS modules      | AppointmentModule, PractitionerModule                                            |
| Suggested endpoints | `GET /api/v1/providers/me/appointments`, `GET /api/v1/providers/me/availability` |
| Prisma models       | Appointment, provider schedule/slot app tables                                   |
| FHIR resources      | Appointment, later Schedule/Slot semantics                                       |
| Validation          | date-range schema                                                                |
| Permissions         | provider own schedule                                                            |
| UI state            | calendar view mode, selected date, facility filter                               |
| Main tests          | week/day/month views, schedule filtering, appointment detail drill-in            |

---

## 4.8 Voice dictation (provider)

| Field               | Value                                                                                                                                                                                                                                                                                        |
| ------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Screen              | Provider voice dictation                                                                                                                                                                                                                                                                     |
| User roles          | Provider                                                                                                                                                                                                                                                                                     |
| Purpose             | Dictate clinical notes, prescriptions, and encounter summaries via voice with ASR + NLP entity extraction                                                                                                                                                                                    |
| NestJS modules      | DataInputModule, EncounterModule, AuditModule                                                                                                                                                                                                                                                |
| Suggested endpoints | `POST /api/v1/audio-input`, `GET /api/v1/transcriptions/:id`, `POST /api/v1/transcriptions/:id/confirm`, `POST /api/v1/transcriptions/:id/apply`                                                                                                                                             |
| Prisma models       | AudioTranscription, InputProvenance, ReviewQueueItem, FHIR resource tables                                                                                                                                                                                                                   |
| FHIR resources      | Observation, Condition, MedicationRequest, AllergyIntolerance (created via /apply)                                                                                                                                                                                                           |
| Validation          | language BCP-47, audio format, template selection, encounter reference                                                                                                                                                                                                                       |
| Permissions         | provider: own dictations; must have active relationship with patient; can create all clinical resource types                                                                                                                                                                                 |
| UI state            | recording state, live transcript with speaker diarisation, clinical entity highlights, template selector (SOAP/prescription/discharge/referral), structured output editor, encounter selector                                                                                                |
| Platform libraries  | `@ghatana/audio-video-client` (SttClient), `@ghatana/audio-video-ui` (AudioRecorder, WaveformVisualizer), `@ghatana/audio-video-types`, `@ghatana/realtime`, `@ghatana/design-system`, `@ghatana/i18n`                                                                                       |
| Main tests          | start dictation, template-guided extraction, speaker diarisation, entity extraction (Condition/Observation/MedicationRequest), accept/edit/reject entities, create resources via /apply, SOAP note structure, auto-save, encounter linking, Nepali/English language switch, provenance audit |

---

# 5. Caregiver-facing screens

## 5.1 Dependent list

| Field               | Value                                                 |
| ------------------- | ----------------------------------------------------- |
| Screen              | Dependents / linked profiles                          |
| User roles          | Caregiver                                             |
| Purpose             | View linked patient profiles                          |
| NestJS modules      | FamilyModule, ConsentModule                           |
| Suggested endpoints | `GET /api/v1/caregivers/me/dependents`                |
| Prisma models       | caregiver/dependent relationship tables, ConsentGrant |
| FHIR resources      | later RelatedPerson + app relationship tables         |
| Validation          | none/minimal query schema                             |
| Permissions         | caregiver only                                        |
| UI state            | active dependent selection                            |
| Main tests          | linked profile visibility, revoked access behavior    |

---

## 5.2 Dependent summary

| Field               | Value                                                                       |
| ------------------- | --------------------------------------------------------------------------- |
| Screen              | Dependent summary                                                           |
| User roles          | Caregiver                                                                   |
| Purpose             | Manage/view dependent’s key info under delegated access                     |
| NestJS modules      | PatientModule, AppointmentModule, MedicationModule, ConsentModule           |
| Suggested endpoints | same family-scoped wrappers or patient summary endpoints with policy checks |
| Prisma models       | Patient, Appointment, MedicationRequest, ConsentGrant                       |
| FHIR resources      | Patient, Appointment, MedicationRequest                                     |
| Validation          | summary query schema                                                        |
| Permissions         | delegated access only                                                       |
| UI state            | scoped summary sections                                                     |
| Main tests          | allowed vs forbidden field visibility, grant expiration behavior            |

---

# 6. Admin-facing screens

## 6.1 Facility admin dashboard

| Field               | Value                                                          |
| ------------------- | -------------------------------------------------------------- |
| Screen              | Admin dashboard                                                |
| User roles          | Admin                                                          |
| Purpose             | Facility/user/config/ops visibility                            |
| NestJS modules      | AdminModule, AnalyticsModule, AuditModule                      |
| Suggested endpoints | `GET /api/v1/admin/dashboard`                                  |
| Prisma models       | admin/config tables, AuditLog, metric snapshots                |
| FHIR resources      | not primary                                                    |
| Validation          | query schema                                                   |
| Permissions         | admin only                                                     |
| UI state            | tenant/facility selector, date range                           |
| Main tests          | admin-only access, metrics rendering, config health indicators |

---

## 6.2 Audit viewer

| Field               | Value                                                                  |
| ------------------- | ---------------------------------------------------------------------- |
| Screen              | Audit viewer                                                           |
| User roles          | Admin, compliance                                                      |
| Purpose             | Inspect sensitive record access and actions                            |
| NestJS modules      | AuditModule                                                            |
| Suggested endpoints | `GET /api/v1/audit/logs`, `GET /api/v1/audit/logs/:id`                 |
| Prisma models       | AuditLog                                                               |
| FHIR resources      | app audit now, later AuditEvent mapping if needed                      |
| Validation          | audit filter schema                                                    |
| Permissions         | admin/compliance only                                                  |
| UI state            | advanced filters, export state                                         |
| Main tests          | filter combinations, export, access restriction, large list pagination |

---

# 7. Shared backend validation packages

Recommended shared schemas/packages:

- `patient.schemas.ts`
- `encounter.schemas.ts`
- `observation.schemas.ts`
- `medication.schemas.ts`
- `appointment.schemas.ts`
- `document.schemas.ts`
- `coverage.schemas.ts`
- `claim.schemas.ts`
- `consent.schemas.ts`

Each should define:

- create schema
- update schema
- list/filter schema
- route param schema
- reusable field sub-schemas

---

# 8. Permissions matrix summary

## Patient

Can:

- view own data
- update allowed profile data
- manage sharing/access grants
- upload own documents
- book appointments
- request and download own export artifacts
- view claims and coverage

## Provider

Can:

- search authorized patients
- view summary if access exists
- create/update encounter-bound clinical data
- create medication requests
- manage own schedule

## Caregiver

Can:

- view dependent data only within granted scope
- book/manage appointments if allowed
- view selected medications/documents if allowed

## Admin

Can:

- manage facility/platform config
- view audit/admin operational views
- must not gain unlimited clinical read access without explicit policy

## FCHV

Can:

- register patients within scoped community workflows
- view only own assisted registrations and follow-up tasks
- capture approved field data with scoped offline queue behavior
- must not gain unrestricted patient search or export access

---

# 9. QA coverage expectations by screen

Every screen should have:

- happy path tests
- validation error tests
- permission tests
- loading/error/empty state tests
- refresh/back-navigation state tests
- audit logging tests where sensitive access occurs

Clinical entry screens must also include:

- data persistence test
- duplicate/conflict handling
- role enforcement
- field-level validation
- unit/date edge cases

---

# 10. Suggested implementation order

## Wave 1

- sign in
- patient registration
- patient profile
- patient dashboard
- provider dashboard
- patient search

## Wave 2

- medical timeline
- conditions
- observations/trends
- medications
- documents

## Wave 3

- appointments list
- book appointment
- provider calendar

## Wave 4

- insurance summary
- export and portability
- claims
- sharing/access grants
- audit viewer

## Wave 5

- caregiver-linked profile views
- provider encounter update flows
- observation entry
- medication request entry

---

# 11. Final recommendation

Use this matrix as the execution bridge between:

- product requirements
- route design
- NestJS modules
- Prisma ownership
- FHIR mapping
- QA planning

The cleanest next artifact after this is a **route contract pack**:

- request/response DTOs
- Zod schemas
- permission annotations
- example payloads
- FHIR mapping notes
  for each MVP endpoint.

---

# 12. Emergency QR and Export Screens (Added in v2.0)

| Field               | Value                                                                            |
| ------------------- | -------------------------------------------------------------------------------- |
| Screen              | Emergency QR Card                                                                |
| User roles          | Patient                                                                          |
| Purpose             | View, regenerate, and print Emergency QR wallet card                             |
| NestJS modules      | PatientModule, MedicationModule, AllergyModule                                   |
| Suggested endpoints | `GET /api/v1/patients/:id/emergency-qr`                                          |
| Prisma models       | Patient, MedicationRequest, AllergyIntolerance, EmergencyContact                 |
| FHIR resources      | Patient (partial), MedicationRequest (active), AllergyIntolerance                |
| Validation          | Profile completeness check (blood type + allergies + emergency contact required) |
| Permissions         | Patient own data only                                                            |
| UI state            | QR display, print preview, incomplete profile warning, last-refreshed timestamp  |
| Main tests          | QR generation, QR refresh on data change, print PDF, incomplete profile guard    |
| Data classification | C3 (Confidential) — QR payload is health data                                    |
| WCAG                | AA — QR must have alt text, print button keyboard-accessible                     |

---

## 12.2 Patient Export and Portability

| Field               | Value                                                                                    |
| ------------------- | ---------------------------------------------------------------------------------------- |
| Screen              | Patient export and portability                                                           |
| User roles          | Patient                                                                                  |
| Purpose             | Request, monitor, and download a portable copy of the patient record                     |
| NestJS modules      | InteroperabilityModule, AuditModule                                                      |
| Suggested endpoints | `POST /api/v1/patients/:id/exports`, `GET /api/v1/patients/:id/exports/:exportId`       |
| Prisma models       | ExportJob, AuditLog                                                                      |
| FHIR resources      | Bundle (export), Patient, Observation, MedicationRequest, DocumentReference, Coverage    |
| Validation          | export format/scope schema                                                               |
| Permissions         | Patient own data only unless explicit export-scoped grant exists                         |
| UI state            | format selector, export status, download readiness, artifact expiry warning              |
| Main tests          | export request, ready state polling, expiry handling, unauthorized export denial         |
| Data classification | C3 (Confidential) — portability artifact exposes patient-linked health data              |
| WCAG                | AA — download state, expiry messaging, and status changes must be screen-reader friendly |

---

# 13. FCHV Screens (Added in v2.0)

## 13.1 FCHV Dashboard

| Field               | Value                                                                |
| ------------------- | -------------------------------------------------------------------- |
| Screen              | FCHV Home Dashboard                                                  |
| User roles          | FCHV (community health volunteer)                                    |
| Purpose             | Icon-based navigation for field health workers                       |
| NestJS modules      | PatientModule                                                        |
| Suggested endpoints | `GET /api/v1/fchv/patients` (scoped to FCHV's registrations)         |
| Prisma models       | Patient, Observation                                                 |
| Validation          | N/A (navigation screen)                                              |
| Permissions         | FCHV role only, scoped to own registrations                          |
| UI state            | Sync status, offline indicator, pending uploads count                |
| Main tests          | Offline rendering, sync status display, navigation to sub-screens    |
| WCAG                | AAA — large touch targets (48×48px min), high contrast, minimal text |

## 13.2 FCHV Patient Registration

| Field               | Value                                                                   |
| ------------------- | ----------------------------------------------------------------------- |
| Screen              | FCHV Simplified Registration                                            |
| User roles          | FCHV                                                                    |
| Purpose             | Register patient during home visit (NID scan or manual entry)           |
| NestJS modules      | PatientModule                                                           |
| Suggested endpoints | `POST /api/v1/fchv/patients`                                            |
| Prisma models       | Patient (pending status)                                                |
| Validation          | NID format, DOB, name (Nepali + English)                                |
| Permissions         | FCHV role only                                                          |
| UI state            | NID QR scanner, form, scoped offline queue, confirmation                |
| Main tests          | Scoped offline registration queue, NID scan, manual entry, duplicate detection |
| Data classification | C3 (Confidential) — PII data                                            |

---

# 14. Cross-Cutting Screen Enhancements (Added in v2.0)

## 14.1 Offline Indicators

All screens MUST include:

- **Offline banner** at top when no connectivity
- **Sync badge** showing pending upload count
- **Staleness indicator** per data section showing last-synced time

## 14.2 WCAG 2.2 AA Compliance Column

Every screen in Sections 3–6 must meet:

- Keyboard navigation for all interactive elements
- Screen reader support (semantic HTML + aria attributes)
- 4.5:1 color contrast ratio for text
- Bilingual labels (Nepali + English)
- Error messages linked to fields via `aria-describedby`

## 14.3 Data Classification Column

Every screen should declare its data classification:

- **C1 (Public):** No health data displayed (sign-in, landing)
- **C2 (Internal):** Operational data (provider calendar, admin dashboard)
- **C3 (Confidential):** Health data (timeline, medications, observations)
- **C4 (Restricted):** Sensitive health data (mental health, reproductive, HIV)

# PHR Platform — MVP Activation Plan

**Version:** 1.0  
**Date:** 2026-03-16

This document defines the **resource-by-resource activation plan** for the PHR platform MVP and near-term phases.

It aligns:

- FHIR resources
- Prisma models
- NestJS modules
- UI screens
- rollout phases
- implementation priority

The goal is to avoid treating the full FHIR catalog as a day-one obligation. FHIR R4 defines a broad resource set across infrastructure, base, clinical, financial, and specialized domains, but the MVP should activate only the subset needed for the first real healthcare workflows. citeturn253063view0

---

## 1. Activation philosophy

### 1.1 Principles

Use these rules:

- activate only what supports a real user workflow
- each activated resource must have a clear module owner
- each activated resource must have at least one UI or integration consumer
- each activated resource must have a migration phase
- defer long-tail resources until they are justified by product, regulatory, or integration needs

### 1.2 Activation levels

We will use five levels:

- **L0 — Foundation**
- **L1 — MVP core**
- **L2 — MVP+ / first production expansion**
- **L3 — operational scale**
- **L4 — advanced / specialized / research**

### 1.3 Documentation and Cross-Reference Standards

All activated resources must include:

**JSDoc Annotations:**
| Tag | Purpose |
|-----|---------|
| `@doc.type` | `resource`, `module`, `screen`, `endpoint` |
| `@doc.purpose` | One-line description of the resource |
| `@doc.activation` | Activation level (L0, L1, L2, L3, L4) |
| `@doc.module` | Owning NestJS module |
| `@doc.prisma` | Related Prisma model(s) |
| `@doc.fhir` | FHIR resource mapping |

**Cross-Reference Pattern:**

```typescript
/**
 * Patient resource - L1 MVP Core
 *
 * @doc.type resource
 * @doc.purpose Patient demographics and profile root
 * @doc.activation L1
 * @doc.module PatientModule
 * @doc.prisma Patient, HumanName, Address, ContactPoint
 * @doc.fhir Patient
 * @doc.see phr_migration_plan_and_module_ownership_matrix.md#Phase-2
 * @doc.see phr_screen_by_screen_mvp_implementation_matrix.md#3.2
 * @doc.see phr_frontend_route_and_component_map.md#9.2
 */
```

**Related Documents:**

- Migration plan: `phr_migration_plan_and_module_ownership_matrix.md`
- Module architecture: `phr_nestjs_modules_detailed_architecture.md`
- Screen matrix: `phr_screen_by_screen_mvp_implementation_matrix.md`
- Route contracts: `phr_mvp_route_contract_pack.md`
- Frontend routes: `phr_frontend_route_and_component_map.md`

---

## 2. L0 — Foundation resources and tables

These establish the platform shell.

| FHIR / App Resource    | Prisma / Table               | NestJS Module      | Primary UI / Consumer                               | Why activate now                                     |
| ---------------------- | ---------------------------- | ------------------ | --------------------------------------------------- | ---------------------------------------------------- |
| Organization           | Organization                 | OrganizationModule | Facility admin, provider assignment screens         | Facilities and provider affiliation are foundational |
| Practitioner           | Practitioner                 | PractitionerModule | Provider profile, scheduling, encounter attribution | Needed for care delivery ownership                   |
| Patient                | Patient                      | PatientModule      | Patient profile, patient dashboard, search          | Root entity of the platform                          |
| Consent / app grants   | ConsentGrant + later Consent | ConsentModule      | Sharing, access control, caregiver flows            | Patient-controlled access is core                    |
| AuditEvent / app audit | AuditLog + later AuditEvent  | AuditModule        | Admin audit, compliance export                      | Required for healthcare trust/compliance             |

---

## 3. L1 — MVP core resources

These power the first complete patient/provider workflow.

| FHIR Resource     | Prisma / Table                   | NestJS Module     | Main screens / flows                                   | MVP status                    |
| ----------------- | -------------------------------- | ----------------- | ------------------------------------------------------ | ----------------------------- |
| Patient           | Patient                          | PatientModule     | registration, profile, demographics, summary           | Required                      |
| Encounter         | Encounter                        | EncounterModule   | visit timeline, encounter detail, provider notes shell | Required                      |
| Observation       | Observation                      | ObservationModule | vitals, lab trends, observation history                | Required                      |
| Condition         | Condition                        | ConditionModule   | diagnosis list, active conditions                      | Required                      |
| Medication        | Medication                       | MedicationModule  | medication catalog display, medication detail          | Required                      |
| MedicationRequest | MedicationRequest                | MedicationModule  | prescription flows, active meds, medication history    | Required                      |
| Appointment       | Appointment                      | AppointmentModule | book, reschedule, upcoming appointments                | Required                      |
| DocumentReference | DocumentReference + StoredObject | DocumentModule    | uploads, scanned records, reports                      | Required                      |
| Coverage          | Coverage                         | InsuranceModule   | insurance summary, eligibility context                 | Required if insurance in MVP  |
| Claim             | Claim                            | BillingModule     | claim submit/track                                     | Optional MVP / preferred MVP+ |
| ClaimResponse     | ClaimResponse                    | BillingModule     | claim status                                           | Optional MVP / preferred MVP+ |

### L1 patient app screens

- onboarding / sign in
- patient dashboard
- profile
- medical history timeline
- conditions list
- medications list
- appointments list
- appointment booking
- documents
- insurance summary
- share / consent

### L1 provider app screens

- provider dashboard
- patient search
- patient summary
- encounter detail
- medication requests
- observations/vitals review
- appointments/calendar

---

## 4. L2 — MVP+ / first production expansion

These improve real-world operational completeness.

| FHIR Resource      | Prisma / Table                    | NestJS Module                           | Main screens / flows                  | Why L2                                          |
| ------------------ | --------------------------------- | --------------------------------------- | ------------------------------------- | ----------------------------------------------- |
| AllergyIntolerance | AllergyIntolerance                | AllergyModule                           | allergy list, medication safety       | Important, but can follow prescription baseline |
| Procedure          | Procedure                         | ProcedureModule                         | surgical/procedure history            | Common but not strictly day-one                 |
| Immunization       | Immunization                      | ImmunizationModule                      | vaccine history, schedule             | Strong patient value, especially family use     |
| DiagnosticReport   | DiagnosticReport                  | Observation/Document/Diagnostics module | lab report detail, result packages    | Needed once external labs deepen                |
| ServiceRequest     | ServiceRequest                    | Interop / Diagnostics module            | lab orders, referral-related requests | Useful once order workflows appear              |
| RelatedPerson      | RelatedPerson or caregiver tables | FamilyModule                            | caregiver access, family management   | Important but can follow core patient launch    |
| CarePlan           | CarePlan                          | CarePlanModule or later care domain     | chronic care plan                     | Better after conditions/medications stabilize   |
| Goal               | Goal                              | CarePlan / Wellness module              | patient goal tracking                 | valuable, not day-one                           |
| Schedule           | app schedule tables               | AppointmentModule                       | provider schedules                    | needed for richer scheduling                    |
| Slot               | app schedule tables               | AppointmentModule                       | slot selection                        | needed for robust booking UX                    |

### L2 cross-cutting: Data Input (OCR / Audio / Form)

DataInput is not a FHIR resource but a **cross-cutting capability** that enhances L1 resources (Observation, Condition, MedicationRequest, DocumentReference) with multi-modal entry channels.

| Capability                  | App Tables                                            | NestJS Module       | Main screens / flows                                                                     | Why L2                                                                       |
| --------------------------- | ----------------------------------------------------- | ------------------- | ---------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| OCR extraction              | OcrExtractionResult, ReviewQueueItem, InputProvenance | DataInputModule     | document upload → OCR review → confirm → create FHIR resources                           | Depends on DocumentModule (L1); enhances document workflow                   |
| Audio/voice transcription   | AudioTranscription, ReviewQueueItem, InputProvenance  | DataInputModule     | patient voice self-report, provider dictation → review → confirm → create FHIR resources | Depends on clinical core (L1); critical for Nepali-language accessibility    |
| Form-based entry (baseline) | (uses existing FHIR tables directly)                  | Existing L1 modules | standard form entry for vitals, conditions, medications                                  | Already covered by L1 module forms; DataInputModule adds provenance tracking |

**Platform libraries**: `@ghatana/audio-video-client`, `@ghatana/audio-video-ui`, `@ghatana/audio-video-types` (STT/ASR), `platform/java/ai-api` (NLP entity extraction)

**Priority**: Deploy form-based entry with L1 (already implicit). Deploy OCR + voice at L2 once document and clinical core are stable.

### L2 screens

- allergies
- immunization tracker
- caregiver management
- provider slot calendar
- diagnostic report detail
- chronic care plan / goals
- document upload (multi-modal) with OCR
- OCR review and confirmation
- patient voice data entry
- provider voice dictation

---

## 5. L3 — Operational scale resources

These support more mature interoperability and operational workflows.

| FHIR Resource               | Prisma / Table         | NestJS Module                 | Main screens / flows                             | Why L3                                           |
| --------------------------- | ---------------------- | ----------------------------- | ------------------------------------------------ | ------------------------------------------------ |
| CareTeam                    | CareTeam               | Care coordination module      | team-based care views                            | needed when multi-provider collaboration matures |
| PractitionerRole            | PractitionerRole       | PractitionerModule            | provider role/specialty availability             | useful once provider network grows               |
| Location                    | Location               | OrganizationModule            | facility/site-specific scheduling and directions | helpful at larger facility scale                 |
| HealthcareService           | HealthcareService      | OrganizationModule            | service catalog / booking filters                | scale feature                                    |
| Endpoint                    | Endpoint               | InteroperabilityModule        | system integration admin                         | admin-facing, integration maturity               |
| Provenance                  | Provenance             | Interoperability/Audit module | data source traceability                         | important for mature import/export ecosystems    |
| Communication               | Communication          | CommunicationModule           | doctor message, care messaging                   | after core records stabilize                     |
| CommunicationRequest        | CommunicationRequest   | CommunicationModule           | outreach tasks                                   | operational maturity                             |
| Task                        | Task                   | Workflow module               | referral, review, follow-up work queues          | operational scaling                              |
| CoverageEligibilityRequest  | app + later FHIR table | InsuranceModule               | eligibility checks                               | when insurance workflows deepen                  |
| CoverageEligibilityResponse | app + later FHIR table | InsuranceModule               | eligibility results                              | same                                             |
| Invoice                     | Invoice                | BillingModule                 | itemized bills                                   | later financial maturity                         |
| PaymentNotice               | PaymentNotice          | BillingModule                 | payment event records                            | later                                            |
| PaymentReconciliation       | PaymentReconciliation  | BillingModule                 | reconciliation views                             | later                                            |

---

## 6. L4 — Advanced and specialized resources

These should remain deferred unless product scope clearly requires them.

Examples:

- ResearchStudy
- ResearchSubject
- MolecularSequence
- ImagingStudy
- Specimen
- Questionnaire
- QuestionnaireResponse
- RiskAssessment
- NutritionOrder
- Device / DeviceMetric / wearable-heavy resources
- Subscription / advanced push integration artifacts
- Measure / MeasureReport
- PlanDefinition / ActivityDefinition
- terminology/conformance-heavy authoring artifacts unless you are actively authoring them

These can exist in schema coverage, but should not become active business modules too early.

---

## 7. Recommended MVP slice by user workflow

## 7.1 Patient registration and profile

Needs:

- Patient
- Organization
- Practitioner (limited use)
- ConsentGrant
- AuditLog

NestJS modules:

- PatientModule
- OrganizationModule
- ConsentModule
- AuditModule

UI:

- patient registration
- profile
- emergency contact
- settings/privacy

---

## 7.2 Medical timeline

Needs:

- Encounter
- Observation
- Condition
- DocumentReference

NestJS modules:

- EncounterModule
- ObservationModule
- ConditionModule
- DocumentModule

UI:

- timeline
- encounter detail
- vitals chart
- diagnosis list
- uploaded documents

---

## 7.3 Medication flow

Needs:

- Medication
- MedicationRequest
- later AllergyIntolerance

NestJS modules:

- MedicationModule
- later AllergyModule

UI:

- active meds
- medication history
- prescription detail
- refill/reminder views

---

## 7.4 Appointment flow

Needs:

- Appointment
- provider schedule app tables
- later Schedule / Slot semantics

NestJS modules:

- AppointmentModule
- PractitionerModule
- NotificationModule

UI:

- book appointment
- reschedule/cancel
- upcoming/past appointments
- provider availability

---

## 7.5 Insurance flow

Needs:

- Coverage
- optional Claim
- optional ClaimResponse

NestJS modules:

- InsuranceModule
- BillingModule
- InteroperabilityModule

UI:

- insurance summary
- eligibility view
- claim submit
- claim status

---

## 7.6 Consent and sharing

Needs:

- ConsentGrant
- later Consent
- AuditLog

NestJS modules:

- ConsentModule
- AuditModule
- PatientModule

UI:

- share record
- access list
- revoke access
- caregiver access management

---

## 7.7 Data input (OCR, audio/voice, form-based)

Needs:

- OcrExtractionResult
- AudioTranscription
- InputProvenance
- ReviewQueueItem
- DocumentReference (existing from Phase 6)
- FHIR resource tables (Observation, Condition, MedicationRequest — existing from L1)

NestJS modules:

- DataInputModule (primary)
- DocumentModule (source documents)
- AuditModule (provenance)

Platform libraries:

- `@ghatana/audio-video-client` — SttClient for WebSocket ASR streaming
- `@ghatana/audio-video-ui` — AudioRecorder, WaveformVisualizer UI components
- `@ghatana/audio-video-types` — TranscriptionResult, SttConfig, AudioChunk types
- `@ghatana/realtime` — WebSocket manager for live transcript streaming
- `platform/java/ai-api` — NLP entity extraction pipeline

UI:

- document upload page (file picker, camera capture, batch import)
- OCR result review page (field-level accept/edit/reject, confidence badges, bounding box overlay)
- patient voice input page (record, live transcript, entity extraction, confirm)
- provider voice dictation page (template-guided recording, speaker diarisation, structured output, encounter linking)

Key flows:

1. **OCR flow**: Upload document → trigger OCR → review extracted fields → accept/edit/reject → create FHIR resources → provenance audit
2. **Patient voice flow**: Record audio → live ASR → extract symptoms/vitals → review → confirm → create Observations
3. **Provider dictation flow**: Select template + encounter → record → ASR + NLP → review structured output → confirm → create clinical resources
4. **Form-based flow**: Standard form entry (covered by L1 modules); DataInputModule adds InputProvenance tracking for form submissions

---

## 8. Recommended MVP activation list

### Activate in code + DB + UI now

- Organization
- Practitioner
- Patient
- ConsentGrant
- AuditLog
- Encounter
- Observation
- Condition
- Medication
- MedicationRequest
- Appointment
- DocumentReference
- Coverage

### Activate in DB + backend contracts soon after

- Claim
- ClaimResponse
- AllergyIntolerance
- Procedure
- Immunization
- Schedule
- Slot
- RelatedPerson
- OcrExtractionResult
- AudioTranscription
- InputProvenance
- ReviewQueueItem

### Defer business activation

- CarePlan
- Goal
- Provenance
- Communication
- Task
- DiagnosticReport as a first-class rich workflow unless already required by integrations
- ImagingStudy
- Research resources
- specialized evidence/quality/reporting artifacts

---

## 9. UI-to-module-to-resource matrix

| UI Screen / Flow              | NestJS Module                   | Resource / Table                                      |
| ----------------------------- | ------------------------------- | ----------------------------------------------------- |
| Patient registration          | PatientModule                   | Patient                                               |
| Patient profile               | PatientModule                   | Patient, Identifier, Address, ContactPoint            |
| Provider profile              | PractitionerModule              | Practitioner                                          |
| Facility directory            | OrganizationModule              | Organization                                          |
| Medical history timeline      | EncounterModule                 | Encounter                                             |
| Diagnosis list                | ConditionModule                 | Condition                                             |
| Vitals / lab trend view       | ObservationModule               | Observation                                           |
| Medications                   | MedicationModule                | Medication, MedicationRequest                         |
| Appointments                  | AppointmentModule               | Appointment                                           |
| Document uploads              | DocumentModule                  | DocumentReference + StoredObject                      |
| Insurance summary             | InsuranceModule                 | Coverage                                              |
| Claim status                  | BillingModule                   | Claim, ClaimResponse                                  |
| Sharing / access grants       | ConsentModule                   | ConsentGrant                                          |
| Audit viewer                  | AuditModule                     | AuditLog                                              |
| Caregiver management          | FamilyModule                    | RelatedPerson / caregiver tables                      |
| Immunization tracker          | ImmunizationModule              | Immunization                                          |
| Document upload (multi-modal) | DataInputModule, DocumentModule | DocumentReference, OcrExtractionResult, StoredObject  |
| OCR review                    | DataInputModule                 | OcrExtractionResult, InputProvenance, ReviewQueueItem |
| Patient voice input           | DataInputModule                 | AudioTranscription, InputProvenance, ReviewQueueItem  |
| Provider voice dictation      | DataInputModule                 | AudioTranscription, InputProvenance, ReviewQueueItem  |

---

## 10. Suggested implementation sprints

## Sprint group A — platform shell

- Organization
- Practitioner
- Patient
- ConsentGrant
- AuditLog

## Sprint group B — clinical core

- Encounter
- Observation
- Condition
- DocumentReference

## Sprint group C — medication + scheduling

- Medication
- MedicationRequest
- Appointment

## Sprint group C+ — data input channels

- DataInputModule (OCR pipeline, audio/voice pipeline, form provenance)
- OcrExtractionResult, AudioTranscription, InputProvenance, ReviewQueueItem tables
- document upload (multi-modal) screen
- OCR review screen
- patient voice input screen
- provider voice dictation screen

## Sprint group D — insurance baseline

- Coverage
- optional Claim / ClaimResponse

## Sprint group E — production hardening

- AllergyIntolerance
- Procedure
- Immunization
- caregiver support
- schedule/slot refinement

---

## 11. How to treat the long-tail schema

The broad Prisma schema is useful as:

- a capability map
- a future-proof naming baseline
- a migration runway

But for MVP planning:

- only a small active subset should get repositories, services, controllers, UI, and tests
- the rest should remain dormant until a real feature requires them

---

## 12. Final recommendation

For MVP, the platform should actively implement:

**Foundation**

- Organization
- Practitioner
- Patient
- ConsentGrant
- AuditLog

**Clinical**

- Encounter
- Observation
- Condition
- DocumentReference

**Medication**

- Medication
- MedicationRequest

**Scheduling**

- Appointment

**Insurance baseline**

- Coverage

**Early expansion**

- Claim
- ClaimResponse
- AllergyIntolerance
- Procedure
- Immunization
- RelatedPerson
- Schedule
- Slot

That gives you a focused, real-world PHR platform without pretending the entire FHIR universe is day-one scope.

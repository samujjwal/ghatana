# PHR Platform — Prisma Migration Plan + Module-to-Table Ownership Matrix

**Version:** 1.0  
**Date:** 2026-03-16

This document aligns the PHR platform’s Prisma schema, NestJS module boundaries, and FHIR-native data model into an implementation-ready plan.

It assumes:

- a **modular NestJS application**
- **Prisma + PostgreSQL** as the main transactional data layer
- **Ceph** (RADOS Gateway / S3-compatible) for object/file storage (on-premise, Nepal-IX compliant; LGPL licensed)
- **FHIR-native hybrid modeling**: relational columns for operations + JSONB for resource fidelity

---

## 1. Migration strategy

Prisma Migrate is meant to keep the database schema in sync with the Prisma schema as it evolves, maintains existing data, and generates a history of SQL migration files that can be customized when needed. citeturn197532view0

### 1.1 Recommended rollout approach

Use a **phased migration plan**, not one giant initial migration.

Order:

1. platform foundation
2. identity and tenancy
3. core clinical resources
4. scheduling and communication
5. insurance and billing
6. documents and interoperability
7. analytics/search projections
8. optional advanced FHIR resources

### 1.2 Migration rules

- every migration must have a narrow scope
- no mixed “schema + destructive data reshape + feature rollout” in one step
- backfill/data migrations should be isolated where possible
- production migrations should be reversible where feasible
- use feature flags when a migration changes runtime behavior
- JSONB fields should supplement, not replace, operationally critical relational columns

### 1.3 Platform Library Integration

The migration plan integrates with existing platform libraries:

| Platform Library / Resource  | PHR Integration                               | Migration Phase    | Notes                                                    |
| ---------------------------- | --------------------------------------------- | ------------------ | -------------------------------------------------------- |
| `platform/contracts/`        | Proto event definitions for audit logging     | Phase 0 (AuditLog) | Protobuf contracts (not a TS package)                    |
| `@phr/db` (PHR-local)        | Prisma client extensions, transaction helpers | All phases         | No `@ghatana/db` TS package exists; create PHR-local     |
| `@phr/fhir` (PHR-local)      | FHIR resource type definitions                | Phase 3+           | No `@ghatana/fhir` TS package exists; create PHR-local   |
| `platform/java/event-cloud/` | Event sourcing for audit events (Java)        | Phase 0 (AuditLog) | Java module; use via Java service or wrap with TS client |

**Prisma Schema Organization:**

```
prisma/
  schema.prisma              # MVP Phase 0-3 tables
  schema_fhir_extended.prisma # Phase 9-10 FHIR resources
  schema_app_tables.prisma    # Phase 6-8 app-specific tables
```

---

## 2. Recommended migration phases

## Phase 0 — platform foundation

Purpose:

- establish shared primitives and control tables

Tables:

- `Tenant`
- `Identifier`
- `Coding`
- `HumanName`
- `Address`
- `ContactPoint`
- `AuditLog`

Notes:

- this phase creates the shared vocabulary and metadata layer
- audit must exist early, because later modules depend on it

---

## Phase 1 — identity, org, and access

Purpose:

- enable tenant-scoped users, facilities, providers, and grants

Tables:

- `Organization`
- `Practitioner`
- `ConsentGrant`

Related module owners:

- `IdentityModule`
- `OrganizationModule`
- `PractitionerModule`
- `ConsentModule`
- `AuditModule`

Notes:

- even if Keycloak stores auth/session state, the app still needs local actor/facility/profile records
- keep external IdP linkage as app metadata, not as full identity duplication

---

## Phase 2 — patient core

Purpose:

- enable the patient record root

Tables:

- `Patient`

Related modules:

- `PatientModule`
- `FamilyModule`
- `ConsentModule`

Notes:

- patient must land before encounters, observations, conditions, and appointments
- patient-linked identifiers should be queryable and normalized

---

## Phase 3 — clinical encounter foundation

Purpose:

- support longitudinal record flow

Tables:

- `Encounter`
- `Condition`
- `Observation`

Related modules:

- `EncounterModule`
- `ConditionModule`
- `ObservationModule`

Notes:

- this is the minimum viable clinical timeline backbone
- keep searchable relational fields for code, status, effective dates, and patient references
- preserve full FHIR payload snapshots in JSONB

---

## Phase 4 — medications

Purpose:

- support prescriptions and medication history

Tables:

- `Medication`
- `MedicationRequest`

Related modules:

- `MedicationModule`
- `AllergyModule` (later adjacency)
- `NotificationModule` (later reminders)

Notes:

- keep medication catalog and requests separate
- dosage instructions can remain JSONB early, then normalize more later if needed

---

## Phase 5 — scheduling and telemedicine basics

Purpose:

- support appointments and session orchestration

Tables:

- `Appointment`
- later telemedicine/session metadata tables as custom app tables, not necessarily pure FHIR-only tables

Suggested custom app tables:

- `TelemedicineSession`
- `TelemedicineParticipant`
- `TranscriptAsset`
- `RecordingAsset`

Related modules:

- `AppointmentModule`
- `TelemedicineModule`
- `NotificationModule`

Notes:

- do not over-normalize telemedicine metadata in phase 1 unless operationally needed
- appointment is core; media/session artifacts can remain app-specific

---

## Phase 6 — documents and files

Purpose:

- support uploads, scans, DICOM references, exports

Suggested tables:

- `DocumentReference` (FHIR-native)
- custom app tables:
  - `StoredObject`
  - `DocumentVersion`
  - `UploadSession`
  - `ChecksumRecord`

Related modules:

- `DocumentModule`
- `InteroperabilityModule`
- `AuditModule`

Notes:

- Ceph (RADOS Gateway, S3-compatible) stores payloads
- PostgreSQL stores metadata, object keys, checksums, classification, lifecycle state

---

## Phase 6A — data input (OCR, audio/voice, form-based)

Purpose:

- support multi-modal data entry: OCR extraction from scanned documents, audio/voice transcription (ASR), and structured form-based input
- human-in-the-loop review workflow with confidence scoring
- provenance tracking from raw input to FHIR resource creation

Suggested tables:

- `OcrExtractionResult` — stores OCR job status, raw text, extracted fields with bounding boxes, confidence scores, and FHIR resource drafts
- `AudioTranscription` — stores ASR session state, full transcript, speaker diarisation, extracted clinical entities, language, duration
- `InputProvenance` — audit trail linking created FHIR resources back to their input source (OCR result, audio transcription, or manual form entry)
- `ReviewQueueItem` — tracks items flagged for human review (low-confidence extractions, ambiguous entities); includes reviewer identity and resolution

Related modules:

- `DataInputModule` (primary owner)
- `DocumentModule` (source documents for OCR)
- `AuditModule` (provenance and review audit)

Platform libraries used (backend):

- `@ghatana/audio-video-client` / `@ghatana/audio-video-types` — STT/ASR integration types
- `platform/java/ai-api` — AI pipeline orchestration for NLP entity extraction

Notes:

- Phase 6A depends on Phase 6 (documents/files must exist before OCR can run)
- OcrExtractionResult links to DocumentReference via `documentId` FK
- AudioTranscription links to Encounter via optional `encounterRef`
- InputProvenance is a generic junction table: `sourceType` (ocr | audio | form) + `sourceId` + `resourceType` + `resourceId`
- ReviewQueueItem uses a status enum: `pending` → `approved` | `rejected` | `escalated`
- All tables include standard tenant scoping (`tenantId`) and soft-delete columns

---

## Phase 7 — insurance and billing

Purpose:

- support coverage, eligibility, claims, reimbursement tracking

Tables:

- `Coverage`
- `Claim`
- `ClaimResponse`
- optionally `Invoice`, `PaymentNotice`, `PaymentReconciliation`

Suggested custom app tables:

- `EligibilityCheckLog`
- `ClaimSubmissionAttempt`
- `ReimbursementLedger`

Related modules:

- `InsuranceModule`
- `BillingModule`
- `InteroperabilityModule`

---

## Phase 8 — notifications and operational workflow

Suggested custom app tables:

- `NotificationPreference`
- `NotificationDelivery`
- `ReminderPlan`
- `QuietHoursPolicy`

Related modules:

- `NotificationModule`
- `AppointmentModule`
- `MedicationModule`

Notes:

- these are better as app-domain tables than trying to force everything into generic FHIR structures

---

## Phase 9 — search and analytics projections

Suggested custom app tables / views:

- `PatientSearchProjection`
- `EncounterSearchProjection`
- `ClinicalTimelineProjection`
- `PopulationMetricSnapshot`
- `DashboardMetricSnapshot`

Related modules:

- `SearchModule`
- `AnalyticsModule`
- `PublicHealthModule`

Notes:

- keep projections separate from transactional truth
- prefer views/materialized views first before more infrastructure

---

## Phase 10 — long-tail FHIR resources

Purpose:

- add broader FHIR coverage progressively

Examples:

- `Procedure`
- `Immunization`
- `AllergyIntolerance`
- `DiagnosticReport`
- `CarePlan`
- `Goal`
- `ServiceRequest`
- `Specimen`
- `ImagingStudy`
- `DocumentReference`
- `Consent`
- `Provenance`

Notes:

- only add resource tables when you have a real use case, data source, and module owner

---

## 3. Module-to-table ownership matrix

Ownership means:

- the module owns business rules
- the module owns writes unless explicitly shared
- the module defines API contracts
- other modules access via service contracts, not direct table manipulation

| Module                 | Primary tables                                                            | Secondary / projections / support      |
| ---------------------- | ------------------------------------------------------------------------- | -------------------------------------- |
| IdentityModule         | app-local actor/account mapping tables                                    | session/audit linkage                  |
| OrganizationModule     | Organization                                                              | facility capability metadata           |
| PractitionerModule     | Practitioner                                                              | practitioner-role style metadata       |
| PatientModule          | Patient, Identifier, HumanName, Address, ContactPoint                     | patient search projection              |
| FamilyModule           | caregiver/dependent relationship tables                                   | delegated access metadata              |
| ConsentModule          | ConsentGrant, consent policy tables                                       | share links, emergency access logs     |
| EncounterModule        | Encounter                                                                 | encounter timeline projection          |
| ConditionModule        | Condition                                                                 | diagnosis summary projection           |
| ObservationModule      | Observation                                                               | trend/materialized views               |
| AllergyModule          | AllergyIntolerance                                                        | medication-safety projection           |
| ProcedureModule        | Procedure                                                                 | procedure summary views                |
| ImmunizationModule     | Immunization, ImmunizationRecommendation                                  | due/reminder projections               |
| MedicationModule       | Medication, MedicationRequest                                             | adherence/reminder support tables      |
| AppointmentModule      | Appointment, schedule/slot app tables                                     | reminder planning views                |
| TelemedicineModule     | telemedicine session app tables                                           | transcript/recording linkage           |
| DocumentModule         | DocumentReference, StoredObject, DocumentVersion                          | upload/checksum/classification tables  |
| InsuranceModule        | Coverage, eligibility app tables                                          | openIMIS mapping logs                  |
| BillingModule          | Claim, ClaimResponse, Invoice, Payment\* tables                           | reimbursement ledger                   |
| NotificationModule     | NotificationPreference, NotificationDelivery, ReminderPlan                | delivery status logs                   |
| SearchModule           | search projection tables / indexes                                        | sync queue tables                      |
| AnalyticsModule        | dashboard/report snapshots                                                | rollup tables/materialized views       |
| InteroperabilityModule | import/export logs, mapping rules, bundle jobs                            | external system connectors             |
| DataInputModule        | OcrExtractionResult, AudioTranscription, InputProvenance, ReviewQueueItem | confidence scoring, FHIR draft staging |
| AuditModule            | AuditLog                                                                  | compliance export tables               |
| AdminModule            | feature/config/admin tables                                               | operational settings                   |
| PublicHealthModule     | anonymized aggregates                                                     | surveillance export snapshots          |

---

## 4. Recommended app-specific tables beyond pure FHIR

FHIR is essential, but the platform still needs **application tables** for operational concerns.

Recommended custom tables:

- `ActorProfile`
- `CaregiverRelationship`
- `TelemedicineSession`
- `TranscriptAsset`
- `RecordingAsset`
- `StoredObject`
- `DocumentVersion`
- `EligibilityCheckLog`
- `ClaimSubmissionAttempt`
- `NotificationPreference`
- `NotificationDelivery`
- `ReminderPlan`
- `FeatureFlag`
- `SearchProjectionSync`
- `DashboardMetricSnapshot`
- `OcrExtractionResult`
- `AudioTranscription`
- `InputProvenance`
- `ReviewQueueItem`

These should remain explicitly app-owned and not be hidden inside generic JSON blobs.

---

## 5. Prisma ownership and schema conventions

## 5.1 Naming

Recommended:

- singular Prisma model names
- snake_case DB table names via `@@map`
- explicit relation fields
- logical FHIR id separate from DB primary key

Example:

- DB primary key: internal UUID
- `logicalId`: FHIR logical id
- `resource`: full JSONB payload snapshot

## 5.2 Standard columns for FHIR-backed tables

Recommended standard columns:

- `id`
- `tenantId`
- `logicalId`
- `version`
- `status`
- `subjectRef` or resource-specific foreign key
- `encounterRef` or encounter foreign key
- `effectiveDateTime`
- `issuedAt`
- `resource`
- `createdAt`
- `updatedAt`

## 5.3 JSONB rule

Use `resource Json` for:

- interoperability fidelity
- extension storage
- sparse or evolving fields
- import payload preservation

Do not rely only on JSONB for:

- frequent filtering
- patient timelines
- operational dashboard metrics
- joins critical to user workflows

---

## 6. NestJS ownership alignment rules

Nest modules are feature-oriented and encapsulate related controllers and providers; exported providers form the module’s public interface. citeturn197532view1

Apply that directly to table ownership.

### Rule 1

Only the owning module may perform unrestricted writes to its primary tables.

### Rule 2

Other modules consume:

- exported services
- query services
- domain events
- public repository contracts only when deliberately allowed

### Rule 3

No module should reach into another module’s Prisma repository implementation directly.

### Rule 4

If two modules repeatedly need the same cross-domain write path, create:

- a dedicated application service
- or a domain event workflow
  rather than cross-writing tables ad hoc.

---

## 7. Suggested migration files

Recommended starter migration sequence:

1. `0001_platform_foundation`
2. `0002_identity_org_access`
3. `0003_patient_core`
4. `0004_encounter_condition_observation`
5. `0005_medication_and_requests`
6. `0006_appointment_core`
7. `0007_documents_and_storage_metadata`
8. `0008_insurance_coverage_claims`
9. `0009_notifications_and_reminders`
10. `0010_search_and_analytics_projections`
11. `0011_extended_fhir_resources_batch_1`
12. `0012_extended_fhir_resources_batch_2`

---

## 8. Recommended implementation order by team

### Backend/domain team

Focus first on:

- patient
- encounter
- observation
- medication
- appointment
- consent
- audit

### Platform/integration team

Focus on:

- Keycloak integration
- Ceph object metadata
- import/export logs
- FHIR mappers
- migration hygiene

### UI team

Focus on:

- patient record shell
- encounter timeline
- medication flows
- appointment flows
- consent management views

---

## 9. What not to do

Avoid:

- creating all ~120 FHIR resources as “production-ready” business modules on day one
- treating every FHIR resource table as equally important
- allowing direct writes across module boundaries
- using JSONB as a dumping ground for everything
- adding search/analytics infra before basic relational queries are optimized
- mixing operational truth and analytics projections in the same tables

---

## 10. Final recommendation

The right path is:

- keep the **broad Prisma schema** as a capability map
- make only a **small owned subset active first**
- roll out migrations in phases
- align each table set to a clear NestJS module owner
- add app-specific operational tables where FHIR is not enough
- treat long-tail FHIR resources as incremental extensions, not day-one obligations

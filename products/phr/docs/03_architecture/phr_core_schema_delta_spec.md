# PHR Platform — Core Schema Delta Specification

**Version:** 2.0  
**Date:** 2026-01-19

| Field              | Value                                                                                                                                                                        |
| ------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Document Owner** | PHR Platform Lead                                                                                                                                                            |
| **Classification** | Internal — Restricted                                                                                                                                                        |
| **Last Review**    | 2026-01-19                                                                                                                                                                   |
| **Companion Docs** | [Activation Plan](phr_mvp_activation_plan.md), [Migration Plan](phr_migration_plan_and_module_ownership_matrix.md), [Prisma Schema](phr_fhir_complete_starter_schema.prisma) |

> **📌 What changed in v2.0:** Added data classification tags per model, encryption requirements for C3/C4 fields, tenant isolation column requirements, and emergency QR data model notes.

This document defines the schema changes needed to move from the current starter Prisma schema to the implementation-ready schema for Core MVP and the committed Phase 2 extension set.

---

## 1. Baseline assessment

The starter Prisma schema already includes major FHIR-shaped tables required for Core MVP:

- `Tenant`
- `AuditLog`
- `ConsentGrant`
- `Organization`
- `Practitioner`
- `Patient`
- `Encounter`
- `Observation`
- `Condition`
- `Medication`
- `MedicationRequest`
- `Appointment`
- `DocumentReference`
- `Coverage`
- `Claim`
- `ClaimResponse`
- `RelatedPerson`
- `Schedule`
- `Slot`

However, the active documents also rely on several **app-owned operational tables** that are not yet present in the starter schema.

---

## 2. Core MVP schema delta

### 2.1 Add now for Core MVP

| Model                  | Why it is needed now                                                  | Primary owner        |
| ---------------------- | --------------------------------------------------------------------- | -------------------- |
| `ActorProfile`         | local actor-to-domain linkage for session context                     | `IdentityModule`     |
| `StoredObject`         | object metadata for uploaded files in Ceph                            | `DocumentModule`     |
| `DocumentVersion`      | track document revisions and replacement flows                        | `DocumentModule`     |
| `EligibilityCheckLog`  | durable result and audit-friendly history of eligibility checks       | `InsuranceModule`    |
| `OcrExtractionResult`  | OCR job state, extracted fields, confidence scores, and draft outputs | `DataInputModule`    |
| `AudioTranscription`   | streaming or batch transcription session state and extracted entities | `DataInputModule`    |
| `InputProvenance`      | links created resources back to OCR, audio, or manual source material | `DataInputModule`    |
| `ReviewQueueItem`      | human review queue for low-confidence OCR/ASR outputs                 | `DataInputModule`    |
| `ReminderPlan`         | appointment and medication reminder scheduling                        | `NotificationModule` |
| `NotificationDelivery` | delivery attempt/result tracking                                      | `NotificationModule` |
| `ExportJob`            | export request lifecycle, artifact expiry, and audit-friendly status  | `InteroperabilityModule` |
| `FeatureFlag`          | controlled rollout and release gating                                 | `AdminModule`        |

### 2.2 Recommended Core MVP model notes

#### `ActorProfile`

Fields:

- `id`
- `tenantId`
- `externalSubject`
- `actorType`
- `patientId?`
- `practitionerId?`
- `organizationId?`
- `status`
- `createdAt`
- `updatedAt`

Indexes:

- unique on `tenantId + externalSubject`
- index on `tenantId + actorType`

#### `StoredObject`

Fields:

- `id`
- `tenantId`
- `bucket`
- `objectKey`
- `contentType`
- `sizeBytes`
- `checksum`
- `storageClass?`
- `encryptionState`
- `createdByActorId`
- `createdAt`

Indexes:

- unique on `bucket + objectKey`
- index on `tenantId + createdAt`

#### `DocumentVersion`

Fields:

- `id`
- `tenantId`
- `documentReferenceId`
- `storedObjectId`
- `versionNo`
- `status`
- `createdByActorId`
- `createdAt`

Indexes:

- unique on `documentReferenceId + versionNo`

#### `EligibilityCheckLog`

Fields:

- `id`
- `tenantId`
- `patientId`
- `coverageId?`
- `providerId?`
- `serviceDate?`
- `requestPayload`
- `responsePayload`
- `outcome`
- `checkedAt`

Indexes:

- index on `tenantId + patientId + checkedAt`

---

## 3. Core MVP model refinements

### 3.1 Existing models needing field review

| Model               | Adjustment                                                                              |
| ------------------- | --------------------------------------------------------------------------------------- |
| `Patient`           | ensure normalized links to names, addresses, contacts, and identifiers remain queryable |
| `Encounter`         | add consistent status, patient linkage, and encounter date indexes                      |
| `Observation`       | add query-friendly code and effective-date indexes                                      |
| `MedicationRequest` | add active/history query indexes by patient and status                                  |
| `Appointment`       | add conflict-friendly provider/date/status indexes                                      |
| `DocumentReference` | link cleanly to `StoredObject`/`DocumentVersion` through app-owned relations            |
| `Coverage`          | add member/policy identifiers used by eligibility checks                                |
| `ConsentGrant`      | add grant status and expiry-query indexes                                               |

### 3.2 Constraints to add

- appointment uniqueness or conflict protection constraint
- active grant overlap constraint where policy requires it
- stable logical id uniqueness per FHIR table
- object metadata uniqueness for stored files

---

## 4. Phase 2 schema delta

### 4.1 Add for claims and caregiver flows

| Model                    | Primary owner   |
| ------------------------ | --------------- |
| `ClaimSubmissionAttempt` | `BillingModule` |
| `CaregiverRelationship`  | `FamilyModule`  |

#### `ClaimSubmissionAttempt`

Fields:

- `id`
- `tenantId`
- `claimId`
- `attemptNo`
- `requestPayload`
- `responsePayload`
- `transportStatus`
- `businessStatus`
- `submittedAt`

#### `CaregiverRelationship`

Fields:

- `id`
- `tenantId`
- `patientId`
- `caregiverActorId`
- `relationshipType`
- `scope`
- `startsAt`
- `endsAt`
- `status`

### 4.2 Add for telemedicine

| Model                     | Primary owner        |
| ------------------------- | -------------------- |
| `TelemedicineSession`     | `TelemedicineModule` |
| `TelemedicineParticipant` | `TelemedicineModule` |
| `TranscriptAsset`         | `TelemedicineModule` |
| `RecordingAsset`          | `TelemedicineModule` |

---

## 5. Migration order

### 5.1 Core MVP migrations

1. `actor_profile_and_identity_linkage`
2. `stored_object_and_document_version`
3. `eligibility_check_log`
4. `notification_and_reminder_tables`
5. `feature_flag_and_admin_config`
6. existing-model index and constraint hardening

### 5.2 Phase 2 migrations

1. `claim_submission_attempt`
2. `caregiver_relationship`
3. `telemedicine_session_tables`

---

## 6. Ownership rules

- app-owned tables must not be hidden in generic JSON blobs
- every table has one owning module
- every FK crossing modules must still honor service ownership rules
- Phase 2 tables should not be created in code paths before the feature is activated

---

## 7. Final recommendation

Treat the starter Prisma schema as a **FHIR capability baseline**, not the final implementation schema.
Core MVP needs a small but important set of operational tables before development starts in earnest, including export lifecycle support for the committed portability surface.

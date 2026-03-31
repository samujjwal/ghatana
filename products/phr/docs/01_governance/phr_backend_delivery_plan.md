# PHR Platform — Backend Delivery Plan

**Version:** 1.0  
**Date:** 2026-03-17  
**Last reviewed:** 2026-03-17  
**Next review due:** 2026-04-17  
**Document owner:** Backend Lead  
**Approval status:** Draft for architecture review  
**Classification:** Internal — Restricted

> Runtime correction (2026-03-30): This document is preserved as a planning artifact. The live backend implementation is Java 21 + ActiveJ under `products/phr/src/main/java`, not the planned NestJS workspace shape described below. Treat the execution waves here as historical planning context unless they align with the current runtime docs and code.

| Field | Value |
| --- | --- |
| Primary consumers | Backend, architecture, platform, QA |
| Source-of-truth inputs | [Core MVP release definition](../01_governance/phr_core_mvp_release_definition.md), [Traceability matrix](../01_governance/phr_requirements_traceability_matrix.md), [Runtime architecture](../03_architecture/phr_runtime_architecture.md), [NestJS modules architecture](../03_architecture/phr_nestjs_modules_detailed_architecture.md), [Schema delta spec](../03_architecture/phr_core_schema_delta_spec.md), [Route contract pack](../04_design_and_workflows/phr_mvp_route_contract_pack.md) |
| Blocking companion docs | [ConsentService interface spec](../03_architecture/phr_consent_service_interface_spec.md), [Multi-tenancy enforcement spec](../03_architecture/phr_multi_tenancy_enforcement_spec.md), [Secrets management playbook](../03_architecture/phr_secrets_management_playbook.md), [Core MVP draft schema](../03_architecture/phr_core_mvp_draft_schema.prisma) |

This document turns the active PHR source-of-truth set into an execution plan for backend delivery. It does not redefine scope.

---

## 1. Delivery intent

The backend plan is organized around the minimum sequence needed to ship the Core MVP safely:

1. lock runtime control points that affect every module
2. land schema and infrastructure primitives
3. build module APIs in dependency order
4. wire background processing and external integrations
5. close test, audit, and production-readiness gates

The first three blocked decisions are non-negotiable:

- Consent enforcement contract
- Multi-tenant isolation strategy
- Secrets management and environment bootstrap

### 1.1 Current implementation status update

- kernel-managed Java backend services are implemented for patient, consent, document, appointment, medication, lab, immunization, clinical note, imaging, referral, billing, telemedicine, caregiver, emergency access, and clinical decision support
- the remaining production blockers are staging validation and formal HIPAA evidence capture
- use `./gradlew :products:phr:phrReleaseGate` plus the staging evidence template to drive the remaining release workflow

---

## 2. Delivery principles

- Build as a modular NestJS monolith with strict module ownership.
- No patient-data route ships without tenant context, audit logging, and consent enforcement.
- Shared DTO and validation contracts are defined before controller implementation.
- New tables ship only with owning module, migration, rollback notes, and QA mapping.
- External integrations use circuit breaker, timeout, retry, and audit behavior from day one.

---

## 3. Target backend workspace shape

Planned implementation locations:

```text
products/phr/
  apps/
    api/
      src/modules/
      src/common/
      test/
    worker/
      src/jobs/
      test/
  packages/
    schemas/
    db/
    auth/
    audit/
    events/
    interoperability/
```

Current implemented locations:

```text
products/phr/
  src/main/java/com/ghatana/phr/
    kernel/
    security/
    observability/
    fhir/
    plugin/
    extension/
  src/test/java/com/ghatana/phr/
```

Planned common package responsibilities:

- `packages/schemas`: Zod schemas and OpenAPI-aligned request and response models
- `packages/db`: Prisma client, tenant helpers, migration utilities
- `packages/auth`: token parsing, actor resolution, role extraction
- `packages/audit`: immutable audit writer and audit query filters
- `packages/events`: outbox and domain event publication contracts

---

## 4. Execution waves

### 4.1 Wave 0 — platform blockers

| Item | Output | Owner | Exit condition |
| --- | --- | --- | --- |
| Consent control point | shared service interface + guard/middleware pattern | Architecture Lead | every patient-data path has a standard `checkAccess` call |
| Tenant isolation | RLS + app filter contract | Architecture Lead | data reads and writes are tenant-scoped by default |
| Secrets bootstrap | environment contract + vault paths + rotation rules | DevOps Lead | staging and local bootstraps are documented and testable |
| CI contract | build, lint, unit, integration, SAST, DAST, deploy stages | DevOps Lead | backend branch protections can rely on a single pipeline |

### 4.2 Wave 1 — shared backend foundation

| Workstream | Modules/packages | Deliverables |
| --- | --- | --- |
| Identity and auth foundation | `IdentityModule`, `packages/auth` | login, session, actor profile resolution, tenant extraction |
| Audit and request context | `AuditModule`, common interceptors | audit writer, request correlation id, security event hooks |
| Persistence foundation | `packages/db` | Prisma client, migrations, transaction helpers, RLS session bootstrap |
| Validation and contracts | `packages/schemas` | DTO drafts promoted into shared package |
| Events and outbox | `packages/events` | outbox table contract, event publisher, worker subscriptions |

### 4.3 Wave 2 — Core MVP patient and provider modules

| Priority | Module | Core capabilities | Depends on |
| --- | --- | --- | --- |
| P0 | `PatientModule` | patient create, read, update, emergency profile summary | identity, audit, consent, tenancy |
| P0 | `ConsentModule` | create, list, revoke grants, break-the-glass | identity, audit, tenancy |
| P0 | `EncounterModule` | encounter detail and update | patient, consent, tenancy |
| P0 | `ObservationModule` | observation create, list, trend query | patient, encounter |
| P0 | `MedicationModule` | medication request create, active history query | patient, encounter |
| P1 | `AppointmentModule` | booking, slot validation, reminders | patient, practitioner, notification |
| P1 | `DocumentModule` | metadata create, upload, list, download audit | patient, storage, audit |
| P1 | `InsuranceModule` | coverage read, eligibility checks | patient, openIMIS adapter, audit |
| P1 | `FamilyModule` | caregiver relationships, delegated summaries, dependent actions | patient, consent, audit |
| P1 | `ReferralModule` | referral create, status tracking, attachment linkage | patient, encounter, consent |
| P1 | `ImagingModule` | study metadata, viewer access, radiology report retrieval | patient, storage, audit |
| P1 | `BillingModule` | bills, payments, receipts, reconciliation hooks | patient, insurance, audit |
| P1 | `DataInputModule` | OCR and ASR review workflow, provenance writes | document, observation, medication |

### 4.4 Wave 3 — background and integration delivery

| Workstream | Output | Notes |
| --- | --- | --- |
| Reminder jobs | worker consumers for appointment and medication reminders | depends on `ReminderPlan` and `NotificationDelivery` |
| OCR/ASR processing | async job orchestration and review queue lifecycle | confidence thresholds from QA and product sign-off |
| openIMIS adapter | eligibility request/response normalization | circuit breaker and fallback mandatory |
| Export baseline | export job orchestration, artifact expiry, and audit evidence | freeze MVP route shape under `InteroperabilityModule` and persist status in `ExportJob` |

---

## 5. Module-by-module backend plan

| Module | Requirements | Primary routes | Primary tables | Backend readiness tasks |
| --- | --- | --- | --- | --- |
| `IdentityModule` | `REQ-ADMIN-001`, `REQ-PATIENT-020` | `/auth/login`, `/auth/me`, `/auth/logout` | `ActorProfile` | actor linkage, MFA hooks, tenant assertion |
| `PatientModule` | `REQ-PROFILE-001`, `REQ-PROFILE-004`, `REQ-PATIENT-016`, `REQ-FCHV-001`, `REQ-FCHV-002` | `/patients`, `/patients/:id`, `/patients/:id/emergency-qr`, `/fchv/patients` | `Patient` | profile projection, allowed-field policy, emergency QR composition, FCHV-assisted registration flow |
| `ConsentModule` | `REQ-PATIENT-003`, `REQ-MED-017`, `REQ-SEC-PRIVACY-002` | `/access-grants`, `/access-grants/:id/revoke` | `ConsentGrant` | grant overlap checks, emergency access flow, cache invalidation |
| `EncounterModule` | `REQ-PROVIDER-003` | `/encounters/:id`, `/patients/:id/encounters` | `Encounter` | signed note update path, patient-scope policy |
| `ObservationModule` | `REQ-PATIENT-006`, `REQ-PROVIDER-011` | `/observations`, `/patients/:id/observations`, `/patients/:id/observation-trends` | `Observation` | code/date indexes, chart-ready query projection |
| `MedicationModule` | `REQ-PROVIDER-004` | `/medication-requests`, `/patients/:id/medications` | `MedicationRequest` | medication history query, write validation |
| `AppointmentModule` | `REQ-APT-001`, `REQ-PATIENT-011` | `/appointments` | `Appointment`, `ReminderPlan` | conflict-safe booking, reminder emission |
| `DocumentModule` | `REQ-PATIENT-002` | `/documents`, `/patients/:id/documents` | `StoredObject`, `DocumentVersion`, `DocumentReference` | upload contract, integrity hash, storage quarantine |
| `InsuranceModule` | `REQ-HIB-002`, `REQ-INS-003`, `REQ-INS-006` | `/patients/:id/coverage`, `/insurance/eligibility-check` | `Coverage`, `EligibilityCheckLog` | adapter normalization, circuit breaker, audit trail |
| `FamilyModule` | `REQ-PATIENT-007`, `REQ-PATIENT-015`, `REQ-FAMILY-007` | `/caregivers/me/dependents`, delegated summary routes | `RelatedPerson`, `CaregiverRelationship` | grant-scoped projections, dependent action policy, audit visibility |
| `ReferralModule` | `REQ-PROVIDER-008`, `REQ-REF-001` | `/referrals`, `/patients/:id/referrals` | `ServiceRequest`, `ReferralStatusEvent` | cross-facility routing, attachment linking, status history |
| `ImagingModule` | `REQ-IMG-001` | `/imaging-studies/:id`, `/imaging-studies/:id/download` | `ImagingStudy`, `DiagnosticReport` | secure viewer/download URLs, report linkage, storage policy |
| `BillingModule` | `REQ-INS-024`, `REQ-INT-PAY-001` | `/patients/:id/bills`, `/payments`, `/payments/:id/confirm` | `Invoice`, `PaymentNotice`, `PaymentReconciliation` | wallet callback verification, receipt generation, idempotent confirmation |
| `DataInputModule` | `REQ-PATIENT-010`, `REQ-MED-004` | `/documents/:id/ocr`, `/ocr-results/:id/confirm`, `/audio-input` | `OcrExtractionResult`, `AudioTranscription`, `InputProvenance`, `ReviewQueueItem` | review queue, threshold routing, provenance linking |
| `InteroperabilityModule` | `REQ-PATIENT-002`, `REQ-PATIENT-017` | `/patients/:id/exports`, `/patients/:id/exports/:exportId` | `ExportJob` | export request acceptance, signed artifact delivery, expiry and audit handling |
| `AuditModule` | `REQ-MOHP-005` | `/audit/logs` | `AuditLog` | append-only writer, tenant-safe query filters |

---

## 6. Database and migration plan

### 6.1 Migration order

1. `actor_profile_and_identity_linkage`
2. `stored_object_and_document_version`
3. `eligibility_check_log`
4. `ocr_asr_and_review_queue`
5. `notification_and_reminder_tables`
6. `caregiver_referral_imaging_and_billing_tables`
7. `feature_flag_and_admin_config`
8. index and constraint hardening on existing FHIR-shaped tables
9. outbox and integration attempt tables

### 6.2 Migration gate

No migration is implementation-ready until all of the following exist:

- Prisma draft reviewed
- owning module confirmed
- data classification applied
- retention rule assigned
- tenant strategy defined
- rollback strategy documented
- automation mapping added in QA docs

---

## 7. Backend non-functional implementation tasks

| Area | Required implementation outcome |
| --- | --- |
| Security | JWT validation, brute-force protection, field-safe errors, rate limiting |
| Privacy | explicit consent checks, document visibility rules, privacy-preserving emergency QR payload |
| Tenancy | request tenant assertion, DB RLS, tenant-scoped storage prefixes |
| Observability | request tracing, latency/error metrics, circuit state metrics |
| Reliability | idempotency for mutating endpoints, retry-safe integrations, backup-aware recovery notes |
| Compliance | immutable audit records, retention metadata, breach-evidence support |

---

## 8. Exit criteria by backend stage

### 8.1 API-ready

- request and response DTOs frozen in shared schema package
- controller route, auth guard, tenant guard, and consent policy path in place
- audit side effects implemented for sensitive routes
- automated contract tests mapped and scheduled

### 8.2 Module-ready

- owning service and repository defined
- migrations merged or staged
- cache and invalidation strategy documented if used
- failure behavior documented for external calls

### 8.3 Release-ready

- all P0 and P1 routes passing API and service integration suites
- staging secrets provisioned through approved path
- SAST, dependency scan, and DAST gates passing
- backup, monitoring, and rollback docs signed off

---

## 9. Current blockers and handoffs

| Blocker | Impact | Handoff owner |
| --- | --- | --- |
| Staging deployment execution not completed | blocks PHR production promotion | DevOps Lead |
| HIPAA evidence pack not signed off | blocks compliance release approval | Compliance Lead |
| Remaining planning-doc drift | creates documentation ambiguity for follow-on work | PHR Technical Lead |

This plan is complete when every Core MVP backend capability has an owning team, dependency chain, planned file location, and measurable exit gate.
# PHR Platform — Detailed NestJS Module & Package Architecture

**Version:** 2.0  
**Date:** 2026-01-19

| Field              | Value                                                                                                                                             |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Document Owner** | PHR Platform Lead                                                                                                                                 |
| **Classification** | Internal — Restricted                                                                                                                             |
| **Last Review**    | 2026-01-19                                                                                                                                        |
| **Companion Docs** | [Runtime Architecture](phr_runtime_architecture.md), [Activation Plan](phr_mvp_activation_plan.md), [Schema Delta](phr_core_schema_delta_spec.md) |

> **📌 What changed in v2.0:** Added ConsentService interface specification, multi-tenancy enforcement notes per module, cache integration points, circuit breaker configuration for external-facing modules, and security hardening notes.

> Runtime correction (2026-03-30): This document is a historical module-planning artifact. The live backend implementation is Java 21 + ActiveJ under `products/phr/src/main/java`, and the module/service wiring is centered on `PhrKernelModule` rather than a NestJS module tree. Keep this document only as a legacy design reference until the architecture set is fully rewritten around the implemented runtime.

This document translates the PHR platform into a **modular NestJS architecture**. It follows Nest's modular structure, where modules organize application structure and providers are injected as dependencies.

## 1. Architecture stance

The platform should start as a **modular monolith** with strong package boundaries, not premature microservices. Each domain is modeled as:

- one NestJS module
- one domain package
- one application layer
- one infrastructure adapter layer
- one integration contract layer

Later, selected domains can be extracted into services without rewriting the domain core.

## 2. Top-level repo shape

```text
apps/
  api/
  worker/
  web/
  mobile/

packages/
  config/
  db/
  auth/
  audit/
  events/
  fhir/
  interoperability/
  shared-kernel/
  domain/
    identity/
    patient/
    practitioner/
    family/
    encounter/
    condition/
    observation/
    medication/
    allergy/
    procedure/
    immunization/
    appointment/
    telemedicine/
    document/
    insurance/
    billing/
    consent/
    notification/
    search/
    analytics/
    admin/
```

## 3. Standard module template

Every module should follow the same shape:

```text
<domain>/
  api/
    rest/
    graphql/
    dto/
  application/
    commands/
    queries/
    services/
    policies/
    mappers/
  domain/
    entities/
    value-objects/
    events/
    repositories/
    rules/
  infrastructure/
    prisma/
    persistence/
    gateways/
    jobs/
  tests/
```

### 3.1 Documentation Standards

All public classes, methods, and interfaces must include JSDoc/TSDoc annotations with `@doc.*` tags.

**Required Tags:**

| Tag            | Purpose               | Example                                                |
| -------------- | --------------------- | ------------------------------------------------------ |
| `@doc.type`    | Entity classification | `service`, `controller`, `repository`, `entity`, `dto` |
| `@doc.purpose` | One-line description  | `Manages patient profile lifecycle`                    |
| `@doc.layer`   | Architecture layer    | `api`, `application`, `domain`, `infrastructure`       |
| `@doc.pattern` | Design pattern        | `repository-pattern`, `cqrs`, `event-sourcing`         |
| `@doc.module`  | Owning module         | `PatientModule`, `EncounterModule`                     |

**Example:**

```typescript
/**
 * Service for managing patient profiles and demographics
 *
 * @doc.type service
 * @doc.purpose Manages patient profile lifecycle including CRUD operations
 * @doc.layer application
 * @doc.pattern repository-pattern
 * @doc.module PatientModule
 */
@Injectable()
export class PatientService {
  // implementation
}
```

**For DTOs:**

```typescript
/**
 * DTO for creating a new patient
 *
 * @doc.type dto
 * @doc.purpose Defines required fields for patient registration
 * @doc.layer api
 * @doc.module PatientModule
 */
export class CreatePatientDto {
  @ApiProperty()
  @doc.purpose Patient first name
  firstName!: string;
}
```

## 4. Core shared packages

### `@phr/config`

Purpose:

- typed environment config
- runtime validation
- feature flags
- deployment profile config

### `@phr/db`

Purpose:

- Prisma client
- transactions
- tenant-aware query helpers
- migration helpers
- DB health checks

### `@phr/auth`

Purpose:

- Keycloak integration
- auth guards
- role/scope extraction
- patient/provider/caregiver context resolution

### `@phr/audit`

Purpose:

- immutable audit writes
- read-access logging
- export/share/download logging

### `@phr/events`

Purpose:

- internal domain events
- app events
- outbox support
- async handlers

### `@phr/fhir`

Purpose:

- FHIR resource DTOs
- serializers/deserializers
- resource validation helpers
- mapping contracts

## 4. Shared Platform Libraries

The following platform libraries are reused across PHR modules:

**TypeScript Platform Libraries:**

| Library                        | Location                                   | Purpose                                    | PHR Usage                                        |
| ------------------------------ | ------------------------------------------ | ------------------------------------------ | ------------------------------------------------ |
| `@ghatana/api`                 | `platform/typescript/api/`                 | HTTP client abstractions                   | Use for FHIR client, external integrations       |
| `@ghatana/design-system`       | `platform/typescript/design-system/`       | UI components (Atomic Design)              | All UI components; extend for healthcare domain  |
| `@ghatana/tokens`              | `platform/typescript/tokens/`              | Design tokens (color, spacing, typography) | Import token values; extend for healthcare       |
| `@ghatana/theme`               | `platform/typescript/theme/`               | Theme provider, dark mode                  | Wrap with healthcare-specific theme overrides    |
| `@ghatana/charts`              | `platform/typescript/charts/`              | Recharts-based chart primitives            | Use for vitals trends, lab results, analytics    |
| `@ghatana/i18n`                | `platform/typescript/i18n/`                | Internationalization (i18next)             | Add medical terminology localization (Nepali)    |
| `@ghatana/platform-utils`      | `platform/typescript/foundation/platform-utils/` | Shared utilities, type helpers             | Use for form validation helpers, date formatting |
| `@ghatana/sso-client`          | `platform/typescript/sso-client/`          | Cross-product SSO client                   | JWT parsing, auth state, login/logout flows      |
| `@ghatana/realtime`            | `platform/typescript/realtime/`            | WebSocket and SSE helpers                  | Use for live notifications, telemedicine events  |
| `@ghatana/audio-video-types`   | `platform/typescript/audio-video-types/`   | Shared types for STT, TTS, Vision          | Type definitions for ASR/transcription features  |
| `@ghatana/audio-video-client`  | `platform/typescript/audio-video-client/`  | HTTP clients for STT, TTS, Vision          | Backend client for OCR, speech-to-text services  |
| `@ghatana/audio-video-ui`      | `platform/typescript/audio-video-ui/`      | React components for audio-video           | Audio recorder, transcription UI, video call UI  |
| `@ghatana/platform-shell`      | `platform/typescript/platform-shell/`      | App shell (tenant, auth, nav)              | Tenant selector, auth bridge, notification hub   |
| `@ghatana/accessibility-audit` | `platform/typescript/accessibility-audit/` | WCAG accessibility auditing                | Automated accessibility checks during CI         |

> **Note:** `@ghatana/fhir` does not currently exist as a platform library. FHIR resource types, serializers, and validation helpers should be created as a **PHR-local package** (`@phr/fhir`) until promoted to the platform.

**Protobuf Contracts:**

| Library            | Location              | Purpose                                      | PHR Usage                           |
| ------------------ | --------------------- | -------------------------------------------- | ----------------------------------- |
| Platform Contracts | `platform/contracts/` | Proto contracts for events, patterns, agents | Use for domain events, audit events |

**Java Platform Libraries (for backend services that use Java):**

| Module          | Location                       | Purpose                                            |
| --------------- | ------------------------------ | -------------------------------------------------- |
| `core`          | `platform/java/core/`          | Basic utilities, types, common patterns            |
| `runtime`       | `platform/java/runtime/`       | ActiveJ integration, event loop, async framework   |
| `http`          | `platform/java/http/`          | HTTP client/server utilities (ActiveJ-based)       |
| `database`      | `platform/java/database/`      | Database abstractions, connection pooling, caching |
| `security`      | `platform/java/security/`      | Auth (JWT/OAuth2/OIDC), encryption                 |
| `audit`         | `platform/java/audit/`         | Audit logging and event tracking                   |
| `observability` | `platform/java/observability/` | Metrics, tracing (Micrometer + OpenTelemetry)      |
| `event-cloud`   | `platform/java/event-cloud/`   | Event tailing, real-time push subscriptions        |
| `governance`    | `platform/java/governance/`    | Multi-tenancy isolation                            |
| `ai-api`        | `platform/java/ai-api/`        | Stable AI abstractions for LLM integration         |
| `testing`       | `platform/java/testing/`       | Common testing utilities, EventloopTestBase        |

> **Note:** The PHR backend is primarily NestJS (Node.js). Java platform libraries are relevant only if specific high-performance services (e.g., FHIR gateway, event processing) are implemented in Java per the Hybrid Backend Strategy.

**Import Patterns:**

```typescript
// Frontend: Use platform TypeScript libraries
import { createHttpClient } from "@ghatana/api";
import { Button, Card, DataTable } from "@ghatana/design-system";
import { ThemeProvider } from "@ghatana/theme";
import { AudioRecorder, TranscriptionViewer } from "@ghatana/audio-video-ui";
import { SttClient } from "@ghatana/audio-video-client";

// PHR-local FHIR types (not a platform package yet)
import { FhirPatient, FhirObservation } from "@phr/fhir";

// Backend (Java, if needed):
// build.gradle.kts:
// implementation(project(":platform:java:http"))
// implementation(project(":platform:java:database"))
```

## 5. Domain modules

### IdentityModule

Responsibilities:

- account bootstrap
- login-linked domain profile creation
- actor type classification
- organization membership resolution

Controllers:

- `IdentityController`
- `SessionController`

Providers:

- `IdentityService`
- `IdentityProfileService`
- `ActorContextService`

### OrganizationModule

Responsibilities:

- organization registry
- facility metadata
- provider affiliation
- facility capability metadata

Providers:

- `OrganizationService`
- `FacilityDirectoryService`

### PractitionerModule

Responsibilities:

- practitioner profile
- specialty/license metadata
- org affiliation
- availability metadata

Providers:

- `PractitionerService`
- `CredentialValidationService`

### PatientModule

Responsibilities:

- patient profile
- demographics
- identifiers
- contacts
- emergency profile
- family linkage root

Providers:

- `PatientService`
- `PatientIdentifierService`
- `PatientProfileMapper`

### FamilyModule

Responsibilities:

- dependent profiles
- caregiver relationships
- family groups
- delegated access policies

Providers:

- `FamilyService`
- `CaregiverAccessService`
- `DependentProfileService`

### ConsentModule

Responsibilities:

- consent capture
- data sharing grants
- time-bound provider access
- family/caregiver sharing
- emergency override rules

Providers:

- `ConsentService`
- `AccessGrantPolicyService`
- `EmergencyAccessPolicy`

### EncounterModule

Responsibilities:

- encounter lifecycle
- visit metadata
- attending participants
- encounter summaries
- timeline linkage

Providers:

- `EncounterService`
- `EncounterTimelineService`
- `EncounterSummaryService`

### ConditionModule

Responsibilities:

- diagnosis/problem list
- chronic condition tracking
- ICD mapping metadata
- active/resolved status

Providers:

- `ConditionService`
- `ConditionClassifier`

### ObservationModule

Responsibilities:

- vitals
- lab-linked observations
- wearable observations
- trend views
- abnormal result flags

Providers:

- `ObservationService`
- `ObservationTrendService`
- `AbnormalObservationPolicy`

### AllergyModule

Responsibilities:

- allergy registry
- severity
- reaction history
- medication safety inputs

Providers:

- `AllergyService`
- `AllergySafetyService`

### ProcedureModule

Responsibilities:

- surgeries
- procedures
- intervention history
- outcome recording

Providers:

- `ProcedureService`

### ImmunizationModule

Responsibilities:

- vaccine record
- due reminders
- childhood schedule
- recommendation inputs

Providers:

- `ImmunizationService`
- `ImmunizationScheduleService`

### MedicationModule

Responsibilities:

- medication catalog
- requests
- dispense/administration tracking
- medication history
- adherence

Providers:

- `MedicationService`
- `MedicationRequestService`
- `InteractionCheckService`
- `AdherenceService`

### DocumentModule

Responsibilities:

- PDFs
- scans
- DICOM metadata
- storage links
- checksum/versioning
- OCR metadata

Providers:

- `DocumentService`
- `StorageLinkService`
- `DocumentClassifier`
- `DocumentVersionService`
- `OcrExtractionService`

### DataInputModule

Responsibilities:

- multi-modal data entry orchestration (OCR, audio/voice, manual form)
- OCR pipeline: upload image/PDF → extract text → parse structured fields → create/update FHIR resources
- audio/voice pipeline: record audio → transcribe via ASR → extract structured data → create/update FHIR resources
- form-based entry: standard web/mobile forms for manual data input
- input source tagging (provenance: OCR, voice, manual, FHIR import)
- confidence scoring for OCR and ASR extracted data
- human review queue for low-confidence extractions

Providers:

- `OcrPipelineService` — orchestrates image upload → OCR → field extraction
- `AudioInputService` — orchestrates audio recording → ASR transcription → structured data
- `StructuredFieldExtractor` — parses OCR/ASR text into FHIR-compatible fields (vitals, diagnoses, medications)
- `InputConfidenceScorer` — scores extraction confidence, flags for human review
- `ReviewQueueService` — manages low-confidence extractions pending user confirmation
- `InputProvenanceService` — tags records with input method (ocr, voice, manual, fhir-import)

Platform libraries used:

- `@ghatana/audio-video-client` — STT (speech-to-text) HTTP client for ASR backend
- `@ghatana/audio-video-types` — shared types for STT/TTS/Vision services
- `@ghatana/audio-video-ui` — React components for audio recording, transcription display

Internal dependencies:

- `DocumentModule` — for storing uploaded images/PDFs and OCR source files
- `ObservationModule` — for creating observations from extracted vitals
- `MedicationModule` — for creating medication requests from extracted prescriptions
- `ConditionModule` — for creating conditions from extracted diagnoses
- `AuditModule` — for logging all data input actions with source provenance

### AppointmentModule

Responsibilities:

- scheduling
- rescheduling
- cancellation
- reminders
- provider availability slots

Providers:

- `AppointmentService`
- `ScheduleService`
- `ReminderPlanningService`

### TelemedicineModule

Phase:

- planned Phase 2 / post-core-MVP capability

Responsibilities:

- video/audio session orchestration
- session tokens
- call metadata
- transcript metadata
- recording consent

Providers:

- `TelemedicineService`
- `SessionTokenService`
- `TranscriptLinkService`
- `RecordingPolicyService`

### InsuranceModule

Responsibilities:

- coverage records
- eligibility checks
- openIMIS mapping
- claim pre-validation

Providers:

- `InsuranceService`
- `EligibilityService`
- `OpenImisMapper`

### BillingModule

Responsibilities:

- bill metadata
- charges
- claim submissions
- reimbursements
- payment status

Providers:

- `BillingService`
- `ClaimSubmissionService`
- `ReimbursementService`

### NotificationModule

Responsibilities:

- SMS/email/push reminder orchestration
- notification preferences
- quiet hours
- delivery logging

Providers:

- `NotificationService`
- `EmailGateway`
- `SmsGateway`
- `PushGateway`
- `DeliveryLogService`

### SearchModule

Responsibilities:

- patient search
- record search
- note search
- faceted retrieval
- index refresh

Providers:

- `SearchService`
- `SearchIndexer`
- `SearchProjectionBuilder`

### AnalyticsModule

Responsibilities:

- patient dashboards
- provider dashboards
- operational analytics
- population snapshots
- report exports

Providers:

- `AnalyticsService`
- `DashboardProjectionService`
- `ExportReportService`

### InteroperabilityModule

Responsibilities:

- FHIR import/export
- bundle exchange
- external EMR integrations
- openIMIS payload transformations

Providers:

- `FhirImportService`
- `FhirExportService`
- `BundleAssembler`
- `ExternalSystemGateway`

### AuditModule

Responsibilities:

- access event logging
- export/share/download logging
- admin audit queries
- compliance reporting

Providers:

- `AuditWriteService`
- `AuditReadService`

### AdminModule

Responsibilities:

- tenant/facility administration
- configuration views
- feature flags
- operational controls
- background-job visibility

Providers:

- `AdminService`
- `TenantAdminService`

### PublicHealthModule

Responsibilities:

- anonymized aggregation
- disease surveillance exports
- vaccination coverage rollups
- public health reporting workflows

Providers:

- `PublicHealthAggregationService`
- `SurveillanceExportService`

## 6. Worker app

Use a separate `worker` app, not separate microservices at first.

Responsibilities:

- notifications
- exports
- OCR processing
- search indexing
- reporting jobs
- import pipelines
- transcript post-processing

## 7. Later extraction candidates

Extract into separate services only when clearly justified:

- telemedicine realtime/media
- search/indexing
- interoperability gateway
- reporting/analytics
- OCR/ASR pipelines
- notifications

## 8. Final recommendation

Use:

- **one main API app**
- **one worker app**
- **strict package boundaries**
- **NestJS modules per domain**
- **shared FHIR mapping package**
- **extraction only after real scale/team/ops pressure**

---

## 9. ConsentService Interface Specification (Added in v2.0)

The ConsentModule must expose a well-defined service interface consumed by all modules that read or write patient data. This is the most critical cross-cutting concern in the platform.

### 9.1 ConsentService contract

```typescript
interface ConsentService {
  /**
   * Check if the current actor has an active grant to access the target patient's data.
   * Returns the grant if active, or throws ConsentDeniedException.
   */
  checkAccess(params: {
    actorId: string;
    actorType: "patient" | "provider" | "caregiver" | "admin" | "fchv";
    patientId: string;
    tenantId: string;
    accessType: "read" | "write";
    resourceType?: string; // optional: restrict check to specific FHIR resource type
    reason?: string; // required for emergency access
  }): Promise<ConsentGrant>;

  /**
   * Create a time-bounded access grant from patient to provider/caregiver.
   */
  createGrant(params: {
    grantorId: string; // patient
    granteeId: string; // provider or caregiver
    granteeType: "provider" | "caregiver";
    tenantId: string;
    scope: string[]; // e.g., ['read:encounter', 'read:medication', 'read:observation']
    expiresAt: Date;
  }): Promise<ConsentGrant>;

  /**
   * Revoke an existing grant immediately.
   */
  revokeGrant(params: {
    grantId: string;
    revokedBy: string;
    tenantId: string;
    reason: string;
  }): Promise<void>;

  /**
   * Create emergency break-the-glass access (4-hour time-limited, requires post-hoc justification).
   */
  createEmergencyAccess(params: {
    providerId: string;
    patientId: string;
    tenantId: string;
    emergencyReason: string;
  }): Promise<ConsentGrant>;
}
```

### 9.2 Module integration pattern

Every module that accesses patient data MUST call `ConsentService.checkAccess()` before returning data:

```typescript
// In EncounterService
async getTimeline(patientId: string, actorContext: ActorContext) {
  // Step 1: Check consent (throws ConsentDeniedException if no active grant)
  await this.consentService.checkAccess({
    actorId: actorContext.actorId,
    actorType: actorContext.actorType,
    patientId,
    tenantId: actorContext.tenantId,
    accessType: 'read',
  });

  // Step 2: Fetch data (only reached if consent check passes)
  return this.encounterRepository.findByPatient(patientId, actorContext.tenantId);
}
```

---

## 10. Multi-Tenancy Enforcement per Module (Added in v2.0)

Every module MUST enforce tenant isolation at two levels:

### 10.1 Application-layer enforcement

- Every service method receives `tenantId` from the request context (set by auth middleware)
- Every repository query includes `WHERE tenantId = :tenantId`
- Every audit entry includes `tenantId`
- Every cache key is prefixed with `tenant:{tenantId}:`

### 10.2 Database-layer enforcement (defense in depth)

- PostgreSQL Row-Level Security (RLS) policies on all patient-data tables
- RLS policy checks `current_setting('app.tenant_id')` set by the connection middleware
- Even if application code has a bug that omits `tenantId`, RLS prevents cross-tenant data leakage

### 10.3 Module-specific tenant rules

| Module              | Tenant Rule                                           | Notes                                                             |
| ------------------- | ----------------------------------------------------- | ----------------------------------------------------------------- |
| **IdentityModule**  | Resolves tenantId from JWT; sets in request context   | Single entry point for tenant context                             |
| **PatientModule**   | All patient queries scoped to tenant                  | Patient cannot exist in multiple tenants                          |
| **EncounterModule** | Scoped to tenant via patient linkage                  | No cross-tenant encounters allowed                                |
| **ConsentModule**   | Grants are tenant-scoped                              | Provider in Tenant A cannot receive grant for patient in Tenant B |
| **InsuranceModule** | Eligibility checks include tenant context             | openIMIS requests tagged with facility/tenant                     |
| **AdminModule**     | Admin roles are tenant-scoped (no super-admin in MVP) | Cross-tenant admin requires explicit platform-level role          |
| **AuditModule**     | All audit entries include tenantId                    | Audit queries always filtered by tenant                           |

---

## 11. Secrets Management (Added in v2.0)

### 11.1 Secret categories

| Secret                      | Storage                                    | Rotation             |
| --------------------------- | ------------------------------------------ | -------------------- |
| Database connection strings | Environment variables from secrets manager | On password rotation |
| Keycloak client secrets     | Environment variables from secrets manager | Every 90 days        |
| Ceph access keys            | Environment variables from secrets manager | Every 90 days        |
| openIMIS API credentials    | Environment variables from secrets manager | Per openIMIS policy  |
| Encryption keys (AES-256)   | Hardware Security Module (HSM) or vault    | Every 90 days        |
| JWT signing keys            | Keycloak-managed                           | Every 90 days        |

### 11.2 Rules

- **NEVER** store secrets in code, config files, or environment variable defaults
- **NEVER** log secrets (even at debug level)
- **ALWAYS** use a secrets manager (HashiCorp Vault, or cloud-provider equivalent for Nepal data centers)
- **ALWAYS** rotate secrets on personnel changes
- **ALWAYS** use separate secrets per environment (dev, staging, production)

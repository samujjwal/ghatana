# PHR Platform — Route Contract Pack

**Version:** 2.0  
**Date:** 2026-01-19

| Field              | Value                                                                                                                                                                                                            |
| ------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Document Owner** | PHR Platform Lead                                                                                                                                                                                                |
| **Classification** | Internal — Restricted                                                                                                                                                                                            |
| **Last Review**    | 2026-01-19                                                                                                                                                                                                       |
| **Companion Docs** | [Frontend Route Map](phr_frontend_route_and_component_map.md), [Error Model](../03_architecture/phr_error_model_and_idempotency_spec.md), [Runtime Architecture](../03_architecture/phr_runtime_architecture.md) |

> **📌 What changed in v2.0:** Added emergency QR API contract, rate limiting headers, circuit breaker error responses, tenant context header requirements, and security-sensitive error response guidelines.

This document defines the route contract pack for the PHR platform's **core MVP** and the explicitly marked **Phase 2 extension set**.

For each selected endpoint, it includes:

- route
- HTTP method
- purpose
- owning NestJS module
- request DTO / schema
- response DTO / shape
- permission rules
- Prisma model touchpoints
- FHIR mapping notes
- validation notes
- example payloads
- testing notes

This is designed for a **REST-first modular NestJS application** with:

- OpenAPI-ready DTOs
- shared validation schemas
- Prisma-backed persistence
- selective FHIR mapping

Scope note:

- Sections 2-9, 10.1-10.2, 11, and 13 describe the current core MVP contract surface.
- Sections 10.3-10.4 are planned Phase 2 extension contracts.
- Telemedicine session contracts are intentionally excluded until the dedicated `TelemedicineModule` call-room surface is specified.

---

## 1. Route contract standards

### 1.1 Base path

Use:

```text
/api/v1
```

### 1.2 Contract style

- JSON request/response
- stable response envelope for mutations
- resource-shaped payloads for reads
- validation errors returned in a consistent structure
- permission failures separated from validation failures

### 1.3 Suggested response envelope for mutations

```json
{
  "success": true,
  "message": "Patient created",
  "data": {
    "id": "8c7164f3-f5cb-4df8-bb1c-e4b1cb7e71a1"
  }
}
```

### 1.4 Suggested validation error shape

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Payload validation failed",
    "fields": {
      "dateOfBirth": ["Invalid date"],
      "phone": ["Phone is required"]
    }
  }
}
```

### 1.5 Shared Schema Library

All Zod schemas and TypeScript types are shared between frontend and backend via:

```typescript
// From @phr/schemas package (PHR-local shared validation)
import {
  createPatientSchema,
  patientResponseSchema,
  loginSchema,
  observationSchema,
} from "@phr/schemas";

// Types inferred from schemas
import type { CreatePatientRequest, PatientResponse } from "@phr/schemas";
```

> **Note:** There is no `@ghatana/validation` platform package. Validation schemas are PHR-local (`@phr/schemas`), using Zod for both frontend and backend.

**Schema Documentation Pattern:**

```typescript
/**
 * Schema for patient registration requests
 *
 * @doc.type schema
 * @doc.purpose Validates patient registration form data
 * @doc.domain patient
 * @doc.module PatientModule
 * @doc.usedBy CreatePatientDto, PatientRegistrationForm
 */
export const createPatientSchema = z.object({
  firstName: z.string().min(1).max(120),
  // ...
});
```

---

# 2. Auth and session contracts

## 2.1 Login

### Contract

- **Method:** `POST`
- **Route:** `/api/v1/auth/login`
- **Module:** `IdentityModule`

### Purpose

Authenticate and establish user session context.

### Request DTO

```ts
type LoginRequest = {
  username: string;
  password: string;
  otp?: string;
};
```

### Zod schema

```ts
import { z } from "zod";

export const loginSchema = z.object({
  username: z.string().min(1),
  password: z.string().min(8),
  otp: z.string().min(4).max(10).optional(),
});
```

### Response DTO

```ts
type LoginResponse = {
  success: true;
  data: {
    accessToken: string;
    refreshToken?: string;
    actor: {
      actorId: string;
      actorType: "PATIENT" | "PROVIDER" | "CAREGIVER" | "ADMIN";
      tenantId: string;
    };
  };
};
```

### Permissions

- public route

### Prisma touchpoints

- app-local actor/account mapping tables
- optional login audit records

### FHIR mapping

- none directly

### Example request

```json
{
  "username": "sam@example.com",
  "password": "StrongPass123!"
}
```

### Example response

```json
{
  "success": true,
  "data": {
    "accessToken": "jwt-token",
    "refreshToken": "refresh-token",
    "actor": {
      "actorId": "user-123",
      "actorType": "PATIENT",
      "tenantId": "tenant-1"
    }
  }
}
```

### Tests

- valid login
- invalid credentials
- MFA required path
- actor context resolution

---

## 2.2 Current session

### Contract

- **Method:** `GET`
- **Route:** `/api/v1/auth/me`
- **Module:** `IdentityModule`

### Purpose

Return authenticated actor context.

### Response DTO

```ts
type CurrentActorResponse = {
  success: true;
  data: {
    actorId: string;
    actorType: "PATIENT" | "PROVIDER" | "CAREGIVER" | "ADMIN";
    tenantId: string;
    permissions: string[];
  };
};
```

### Permissions

- authenticated only

### Tests

- authenticated success
- expired session
- tenant context correctness

---

# 3. Patient contracts

## 3.1 Create patient

### Contract

- **Method:** `POST`
- **Route:** `/api/v1/patients`
- **Module:** `PatientModule`

### Purpose

Create a patient profile root.

### Request DTO

```ts
type CreatePatientRequest = {
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  gender: "male" | "female" | "other" | "unknown";
  phone: string;
  email?: string;
  address?: {
    line1?: string;
    line2?: string;
    city?: string;
    district?: string;
    state?: string;
    postalCode?: string;
    country?: string;
  };
  emergencyContact?: {
    name: string;
    phone: string;
    relationship?: string;
  };
};
```

### Zod schema

```ts
import { z } from "zod";

export const createPatientSchema = z.object({
  firstName: z.string().min(1).max(120),
  lastName: z.string().min(1).max(120),
  dateOfBirth: z.string().date(),
  gender: z.enum(["male", "female", "other", "unknown"]),
  phone: z.string().min(5).max(30),
  email: z.string().email().optional(),
  address: z
    .object({
      line1: z.string().optional(),
      line2: z.string().optional(),
      city: z.string().optional(),
      district: z.string().optional(),
      state: z.string().optional(),
      postalCode: z.string().optional(),
      country: z.string().optional(),
    })
    .optional(),
  emergencyContact: z
    .object({
      name: z.string().min(1),
      phone: z.string().min(5),
      relationship: z.string().optional(),
    })
    .optional(),
});
```

### Response DTO

```ts
type CreatePatientResponse = {
  success: true;
  data: {
    id: string;
    logicalId: string;
    createdAt: string;
  };
};
```

### Permissions

- patient self-registration if enabled
- admin/provider assisted registration if policy allows

### Prisma touchpoints

- `Patient`
- `Identifier`
- `HumanName`
- `Address`
- `ContactPoint`
- `AuditLog`

### FHIR mapping notes

Maps to `Patient` resource:

- `name.given[]`
- `name.family`
- `birthDate`
- `gender`
- `telecom`
- `address`

### Example request

```json
{
  "firstName": "Samujjwal",
  "lastName": "Bhandari",
  "dateOfBirth": "1990-01-01",
  "gender": "male",
  "phone": "+9779800000000",
  "email": "sam@example.com",
  "address": {
    "city": "Kathmandu",
    "country": "NP"
  },
  "emergencyContact": {
    "name": "Jane Bhandari",
    "phone": "+9779811111111",
    "relationship": "spouse"
  }
}
```

### Tests

- valid create
- duplicate identifier handling
- required field validation
- audit written
- unauthorized assisted registration rejected

---

## 3.2 Get patient

### Contract

- **Method:** `GET`
- **Route:** `/api/v1/patients/:id`
- **Module:** `PatientModule`

### Purpose

Fetch patient profile.

### Route params

```ts
type PatientIdParams = {
  id: string;
};
```

### Response DTO

```ts
type PatientResponse = {
  success: true;
  data: {
    id: string;
    logicalId: string;
    firstName?: string;
    lastName?: string;
    gender?: string;
    dateOfBirth?: string;
    phone?: string;
    email?: string;
    address?: Record<string, unknown>;
  };
};
```

### Permissions

- patient self
- delegated caregiver
- provider with authorized access

### Prisma touchpoints

- `Patient`
- `HumanName`
- `Address`
- `ContactPoint`
- `ConsentGrant`

### FHIR mapping

Returns app DTO derived from `Patient`.

### Tests

- self access
- caregiver scoped access
- provider with/without grant
- missing patient id

---

## 3.3 Update patient

### Contract

- **Method:** `PATCH`
- **Route:** `/api/v1/patients/:id`
- **Module:** `PatientModule`

### Purpose

Update allowed patient profile fields.

### Request DTO

Use partial of create schema, with immutable fields enforced server-side.

### Permissions

- patient self
- limited delegated actor as policy allows
- provider read-only in standard flow

### Tests

- allowed partial updates
- forbidden immutable field update
- validation failures
- audit trail

---

## 3.4 Patient dashboard

### Contract

- **Method:** `GET`
- **Route:** `/api/v1/patients/:id/dashboard`
- **Module:** `PatientModule`

### Purpose

Return aggregated patient dashboard data.

### Query DTO

```ts
type PatientDashboardQuery = {
  includeRecentDocuments?: boolean;
  includeUpcomingAppointments?: boolean;
};
```

### Response shape

```ts
type PatientDashboardResponse = {
  success: true;
  data: {
    patientSummary: Record<string, unknown>;
    activeConditions: Array<Record<string, unknown>>;
    activeMedications: Array<Record<string, unknown>>;
    upcomingAppointments: Array<Record<string, unknown>>;
    recentObservations: Array<Record<string, unknown>>;
    recentDocuments?: Array<Record<string, unknown>>;
  };
};
```

### Prisma touchpoints

- `Patient`
- `Condition`
- `MedicationRequest`
- `Appointment`
- `Observation`
- `DocumentReference`

### FHIR mapping

Aggregates multiple resources into app-specific summary DTO.

### Tests

- no-data dashboard
- mixed-data dashboard
- caregiver scope
- provider denied if no grant

---

# 4. Encounter and timeline contracts

## 4.1 List patient timeline

### Contract

- **Method:** `GET`
- **Route:** `/api/v1/patients/:id/timeline`
- **Module:** `EncounterModule`

### Purpose

Return longitudinal timeline items.

### Query DTO

```ts
type TimelineQuery = {
  from?: string;
  to?: string;
  category?:
    | "encounter"
    | "observation"
    | "condition"
    | "medication"
    | "document";
  page?: number;
  pageSize?: number;
};
```

### Response shape

```ts
type TimelineResponse = {
  success: true;
  data: {
    items: Array<{
      id: string;
      type: string;
      occurredAt?: string;
      title: string;
      summary?: string;
    }>;
    page: number;
    pageSize: number;
    total: number;
  };
};
```

### Prisma touchpoints

- `Encounter`
- `Observation`
- `Condition`
- `MedicationRequest`
- `DocumentReference`

### FHIR mapping

Cross-resource aggregation of:

- `Encounter`
- `Observation`
- `Condition`
- `MedicationRequest`
- `DocumentReference`

### Tests

- category filtering
- pagination
- date range
- permission enforcement

---

## 4.2 Get encounter

### Contract

- **Method:** `GET`
- **Route:** `/api/v1/encounters/:id`
- **Module:** `EncounterModule`

### Purpose

Fetch encounter detail.

### Response shape

```ts
type EncounterResponse = {
  success: true;
  data: {
    id: string;
    patientId?: string;
    status?: string;
    classCode?: string;
    serviceType?: string;
    startAt?: string;
    endAt?: string;
    summary?: string;
  };
};
```

### Prisma touchpoints

- `Encounter`

### FHIR mapping

Maps to/from `Encounter`.

### Tests

- authorized read
- unauthorized read
- not found
- correct summary rendering

---

## 4.3 Update encounter

### Contract

- **Method:** `PATCH`
- **Route:** `/api/v1/encounters/:id`
- **Module:** `EncounterModule`

### Purpose

Update encounter metadata/summary.

### Request DTO

```ts
type UpdateEncounterRequest = {
  serviceType?: string;
  startAt?: string;
  endAt?: string;
  summary?: string;
  status?: "draft" | "active" | "inactive" | "entered_in_error" | "unknown";
};
```

### Permissions

- provider only with patient access

### Tests

- provider can update
- patient cannot directly update provider encounter summary unless policy says so
- status validation
- audit log

---

# 5. Observation contracts

## 5.1 List observations

### Contract

- **Method:** `GET`
- **Route:** `/api/v1/patients/:id/observations`
- **Module:** `ObservationModule`

### Purpose

List observations for a patient.

### Query DTO

```ts
type ObservationListQuery = {
  code?: string;
  from?: string;
  to?: string;
  page?: number;
  pageSize?: number;
};
```

### Response shape

```ts
type ObservationListResponse = {
  success: true;
  data: {
    items: Array<{
      id: string;
      code?: string;
      valueQuantity?: number;
      valueString?: string;
      unit?: string;
      effectiveDateTime?: string;
      status?: string;
    }>;
    total: number;
  };
};
```

### Prisma touchpoints

- `Observation`

### FHIR mapping

Maps to `Observation`.

### Tests

- code filtering
- trend ranges
- quantity and string observation support
- empty state

---

## 5.2 Create observation

### Contract

- **Method:** `POST`
- **Route:** `/api/v1/observations`
- **Module:** `ObservationModule`

### Purpose

Create a clinical observation.

### Request DTO

```ts
type CreateObservationRequest = {
  patientId: string;
  encounterId?: string;
  codeSystem?: string;
  code: string;
  valueQuantity?: number;
  valueString?: string;
  unit?: string;
  effectiveDateTime?: string;
  status?: "draft" | "active" | "inactive" | "entered_in_error" | "unknown";
};
```

### Zod schema

```ts
import { z } from "zod";

export const createObservationSchema = z
  .object({
    patientId: z.string().uuid(),
    encounterId: z.string().uuid().optional(),
    codeSystem: z.string().optional(),
    code: z.string().min(1),
    valueQuantity: z.number().optional(),
    valueString: z.string().optional(),
    unit: z.string().optional(),
    effectiveDateTime: z.string().datetime().optional(),
    status: z
      .enum(["draft", "active", "inactive", "entered_in_error", "unknown"])
      .optional(),
  })
  .refine((v) => v.valueQuantity !== undefined || v.valueString !== undefined, {
    message: "Either valueQuantity or valueString is required",
  });
```

### Permissions

- provider/nurse role with patient access

### Prisma touchpoints

- `Observation`
- `AuditLog`

### FHIR mapping

Maps directly to `Observation.valueQuantity` or `Observation.valueString`.

### Tests

- quantity observation
- text observation
- missing value rejection
- unauthorized actor
- abnormal-flag pipeline trigger if applicable

---

## 5.3 Observation trends

### Contract

- **Method:** `GET`
- **Route:** `/api/v1/patients/:id/observation-trends`
- **Module:** `ObservationModule`

### Purpose

Return chart-ready trend series.

### Query DTO

```ts
type ObservationTrendQuery = {
  code: string;
  from?: string;
  to?: string;
  bucket?: "day" | "week" | "month";
};
```

### Response shape

```ts
type ObservationTrendResponse = {
  success: true;
  data: {
    code: string;
    points: Array<{
      at: string;
      value: number;
      unit?: string;
    }>;
  };
};
```

### Tests

- date bucketing
- no points
- mixed-unit rejection or normalization
- chart-ready order

---

# 6. Condition contracts

## 6.1 List conditions

### Contract

- **Method:** `GET`
- **Route:** `/api/v1/patients/:id/conditions`
- **Module:** `ConditionModule`

### Purpose

Return active/resolved conditions.

### Query DTO

```ts
type ConditionListQuery = {
  clinicalStatus?: string;
  category?: string;
};
```

### Prisma touchpoints

- `Condition`

### FHIR mapping

Maps to `Condition`.

### Tests

- active-only
- resolved-only
- by category
- sort by onset date

---

# 7. Medication contracts

## 7.1 List medication requests

### Contract

- **Method:** `GET`
- **Route:** `/api/v1/patients/:id/medication-requests`
- **Module:** `MedicationModule`

### Purpose

Return active and historical prescriptions.

### Query DTO

```ts
type MedicationRequestListQuery = {
  status?: string;
  activeOnly?: boolean;
};
```

### Prisma touchpoints

- `MedicationRequest`
- `Medication`

### FHIR mapping

Maps to `MedicationRequest` plus medication details.

### Tests

- active-only
- historical
- medication join present
- permission checks

---

## 7.2 Create medication request

### Contract

- **Method:** `POST`
- **Route:** `/api/v1/medication-requests`
- **Module:** `MedicationModule`

### Purpose

Create a prescription/order.

### Request DTO

```ts
type CreateMedicationRequestRequest = {
  patientId: string;
  encounterId?: string;
  medicationId?: string;
  intent?: string;
  authoredOn?: string;
  dosageInstruction?: Record<string, unknown>;
  dispenseRequest?: Record<string, unknown>;
  status?: "draft" | "active" | "inactive" | "entered_in_error" | "unknown";
};
```

### Permissions

- provider only

### Prisma touchpoints

- `MedicationRequest`
- `Medication`
- later `AllergyIntolerance`

### FHIR mapping

Maps to `MedicationRequest`.

### Tests

- create success
- missing patient
- invalid medication id
- provider-only access
- allergy warning integration hook

---

# 8. Appointment contracts

## 8.1 List appointments

### Contract

- **Method:** `GET`
- **Route:** `/api/v1/patients/:id/appointments`
- **Module:** `AppointmentModule`

### Purpose

Return appointment list.

### Query DTO

```ts
type AppointmentListQuery = {
  status?: string;
  from?: string;
  to?: string;
};
```

### Prisma touchpoints

- `Appointment`

### FHIR mapping

Maps to `Appointment`.

### Tests

- upcoming/past filters
- status filters
- caregiver allowed only when granted

---

## 8.2 Create appointment

### Contract

- **Method:** `POST`
- **Route:** `/api/v1/appointments`
- **Module:** `AppointmentModule`

### Purpose

Book appointment.

### Request DTO

```ts
type CreateAppointmentRequest = {
  patientId: string;
  providerId?: string;
  startAt: string;
  endAt: string;
  appointmentType?: string;
  serviceCategory?: string;
  reason?: string;
};
```

### Zod schema

```ts
import { z } from "zod";

export const createAppointmentSchema = z
  .object({
    patientId: z.string().uuid(),
    providerId: z.string().uuid().optional(),
    startAt: z.string().datetime(),
    endAt: z.string().datetime(),
    appointmentType: z.string().optional(),
    serviceCategory: z.string().optional(),
    reason: z.string().max(1000).optional(),
  })
  .refine((v) => new Date(v.endAt).getTime() > new Date(v.startAt).getTime(), {
    message: "endAt must be after startAt",
  });
```

### Permissions

- patient self
- caregiver with scope
- provider/admin staff if allowed

### Prisma touchpoints

- `Appointment`
- schedule/slot app tables
- reminder planning tables

### FHIR mapping

Core fields map to `Appointment.start`, `Appointment.end`, type/category semantics.

### Tests

- success booking
- overlapping slot conflict
- invalid time range
- reminder planning triggered

---

## 8.3 Cancel appointment

### Contract

- **Method:** `PATCH`
- **Route:** `/api/v1/appointments/:id/cancel`
- **Module:** `AppointmentModule`

### Request DTO

```ts
type CancelAppointmentRequest = {
  cancellationReason?: string;
};
```

### Tests

- patient cancellation
- provider/staff cancellation
- already canceled
- audit log

---

# 9. Document contracts

## 9.1 Upload document metadata + file

### Contract

- **Method:** `POST`
- **Route:** `/api/v1/documents`
- **Module:** `DocumentModule`

### Purpose

Create document record and attach uploaded object.

### Request shape

Multipart or pre-signed upload flow.

### Metadata DTO

```ts
type CreateDocumentRequest = {
  patientId: string;
  encounterId?: string;
  title: string;
  contentType: string;
  category?: string;
  description?: string;
};
```

### Prisma touchpoints

- `DocumentReference`
- `StoredObject`
- `DocumentVersion`

### FHIR mapping

Maps to `DocumentReference`, while storage metadata stays app-specific.

### Tests

- supported content types
- oversized upload
- missing object link
- version creation
- download audit

---

## 9.2 List documents

### Contract

- **Method:** `GET`
- **Route:** `/api/v1/patients/:id/documents`
- **Module:** `DocumentModule`

### Query DTO

```ts
type DocumentListQuery = {
  category?: string;
  from?: string;
  to?: string;
};
```

### Tests

- category filtering
- date filtering
- version visibility
- permission checks

---

# 9A. Core MVP data input contracts (OCR, Audio/Voice, Form)

> These contracts support the DataInputModule — the cross-cutting module that orchestrates
> multi-modal data entry (OCR extraction from scanned documents, voice/audio transcription
> for patients and providers, and structured form-based entry).

---

## 9A.1 Trigger OCR extraction

### Contract

- **Method:** `POST`
- **Route:** `/api/v1/documents/:id/ocr`
- **Module:** `DataInputModule` (delegates to `OcrPipelineService`)

### Purpose

Trigger asynchronous OCR extraction on an already-uploaded document. The document must exist in `DocumentReference` and the file must be stored in Ceph (via RADOS Gateway / S3-compatible API).

### Request shape

```ts
type TriggerOcrRequest = {
  /** Language hints for OCR engine, e.g. ["ne", "en"] */
  languageHints?: string[];
  /** Templates for structured extraction: "lab_report", "prescription", "insurance_card", "generic" */
  extractionTemplate?: string;
};
```

### Response shape

```ts
type TriggerOcrResponse = {
  ocrResultId: string;
  status: "queued" | "processing";
  estimatedCompletionMs?: number;
};
```

### Prisma touchpoints

- `DocumentReference` (read — source document)
- `OcrExtractionResult` (create — new extraction job)

### Permission

- `patient` role: own documents only
- `provider` role: patient documents within their facility
- `admin` role: any document

### Headers

- `Authorization: Bearer <token>`
- `X-Tenant-Id: <tenantId>`

### Tests

- trigger on supported content types (PDF, PNG, JPEG, TIFF)
- reject unsupported content types (e.g., audio/mp3)
- reject if document not found → 404
- reject if user lacks permission → 403
- idempotent re-trigger returns existing pending result
- language hint validation

---

## 9A.2 Get OCR result

### Contract

- **Method:** `GET`
- **Route:** `/api/v1/ocr-results/:id`
- **Module:** `DataInputModule`

### Purpose

Retrieve OCR extraction result including raw text, structured fields, and per-field confidence scores.

### Response shape

```ts
type OcrResult = {
  id: string;
  documentId: string;
  status: "queued" | "processing" | "completed" | "failed";
  rawText?: string;
  extractedFields?: Array<{
    fieldName: string;
    value: string;
    confidence: number; // 0.0 – 1.0
    boundingBox?: {
      x: number;
      y: number;
      width: number;
      height: number;
      page: number;
    };
    suggestedFhirPath?: string; // e.g., "Observation.valueQuantity.value"
  }>;
  /** Structured FHIR resource drafts derived from extraction */
  fhirDrafts?: Array<{
    resourceType:
      | "Observation"
      | "Condition"
      | "MedicationRequest"
      | "AllergyIntolerance";
    draft: Record<string, unknown>;
    overallConfidence: number;
  }>;
  error?: { code: string; message: string };
  createdAt: string;
  completedAt?: string;
};
```

### Prisma touchpoints

- `OcrExtractionResult` (read)

### Permission

Same as 9A.1 — scoped by document ownership.

### Tests

- returns pending status while processing
- returns full result when completed
- returns error details on failure
- confidence scores in valid range
- bounding boxes present for image-based documents
- 404 for non-existent result

---

## 9A.3 Confirm OCR result

### Contract

- **Method:** `POST`
- **Route:** `/api/v1/ocr-results/:id/confirm`
- **Module:** `DataInputModule` (delegates to `ReviewQueueService`)

### Purpose

Human-reviewed confirmation of OCR-extracted fields. The user can accept, reject, or edit individual extracted fields before they are persisted as FHIR resources.

### Request shape

```ts
type ConfirmOcrRequest = {
  reviewedFields: Array<{
    fieldName: string;
    /** "accepted" keeps original, "edited" uses correctedValue, "rejected" discards */
    action: "accepted" | "edited" | "rejected";
    correctedValue?: string;
  }>;
  /** Which FHIR drafts to create as actual resources */
  approvedDrafts?: Array<{
    draftIndex: number;
    /** Optional overrides to the draft before creation */
    overrides?: Record<string, unknown>;
  }>;
  /** Link created resources to this encounter */
  encounterRef?: string;
};
```

### Response shape

```ts
type ConfirmOcrResponse = {
  ocrResultId: string;
  status: "confirmed";
  createdResources: Array<{
    resourceType: string;
    resourceId: string;
  }>;
  /** Fields that were low-confidence and flagged for further review */
  flaggedForReview: number;
};
```

### Prisma touchpoints

- `OcrExtractionResult` (update status → confirmed)
- `InputProvenance` (create — audit trail of human review)
- `ReviewQueueItem` (update — mark item as reviewed)
- FHIR resource tables (create — Observation, Condition, etc.)

### Permission

Same as 9A.1.

### Tests

- accept all fields → creates FHIR resources
- reject all fields → no resources created
- edit fields → corrected values persisted
- partial approval (some accepted, some rejected)
- provenance record created with reviewer identity
- encounterRef links resources to encounter
- 400 if OCR result not yet completed
- 409 if already confirmed

---

## 9A.4 Start audio/voice transcription

### Contract

- **Method:** `POST`
- **Route:** `/api/v1/audio-input`
- **Module:** `DataInputModule` (delegates to `AudioInputService`)

### Purpose

Initiate an audio transcription session. Returns a transcription ID and a WebSocket URL for streaming audio chunks. Supports both patient self-report and provider dictation use cases.

### Request shape

```ts
type StartTranscriptionRequest = {
  /** "patient_self_report" or "provider_dictation" */
  sourceType: "patient_self_report" | "provider_dictation";
  /** BCP-47 language code, e.g., "ne-NP", "en-US" */
  language: string;
  /** Patient context for entity resolution */
  patientId?: string;
  /** Link to active encounter */
  encounterRef?: string;
  /** Dictation template for structured extraction */
  template?:
    | "soap_note"
    | "prescription"
    | "discharge_summary"
    | "referral_letter"
    | "general";
  /** Audio format hint */
  audioFormat?: "webm_opus" | "wav_pcm" | "mp3";
};
```

### Response shape

```ts
type StartTranscriptionResponse = {
  transcriptionId: string;
  status: "active";
  /** WebSocket URL for streaming audio chunks */
  streamUrl: string;
  /** Token for authenticating the WebSocket connection */
  streamToken: string;
  createdAt: string;
};
```

### WebSocket protocol

Once connected to `streamUrl`:

1. Client sends binary audio frames (chunked, ~250ms segments)
2. Server sends JSON messages:
   - `{ type: "partial", text: "..." }` — interim transcript
   - `{ type: "final", text: "...", confidence: 0.92 }` — finalised segment
   - `{ type: "entity", entityType: "Observation", draft: {...}, confidence: 0.85 }` — extracted clinical entity
   - `{ type: "error", code: "...", message: "..." }` — processing error
3. Client sends `{ type: "end" }` to signal recording stopped

### Prisma touchpoints

- `AudioTranscription` (create)
- `InputProvenance` (create — source tracking)

### Permission

- `patient` role: `sourceType` must be `patient_self_report`; `patientId` must match own ID
- `provider` role: can use `provider_dictation`; must have active relationship with patient
- `admin` role: any

### Headers

- `Authorization: Bearer <token>`
- `X-Tenant-Id: <tenantId>`

### Tests

- returns valid WebSocket URL
- rejects invalid sourceType
- rejects patient using provider_dictation
- rejects missing language
- streamToken expires after 5 minutes if unused
- valid audio formats accepted
- Nepali language support (ne-NP)

---

## 9A.5 Get transcription

### Contract

- **Method:** `GET`
- **Route:** `/api/v1/transcriptions/:id`
- **Module:** `DataInputModule`

### Purpose

Retrieve a transcription result including full text, extracted entities, and confidence metadata.

### Response shape

```ts
type TranscriptionResult = {
  id: string;
  sourceType: "patient_self_report" | "provider_dictation";
  status: "active" | "completed" | "confirmed" | "failed";
  language: string;
  /** Full transcribed text */
  fullText?: string;
  /** Duration of audio in milliseconds */
  durationMs?: number;
  /** Extracted clinical entities */
  extractedEntities?: Array<{
    entityType:
      | "Observation"
      | "Condition"
      | "MedicationRequest"
      | "AllergyIntolerance";
    text: string;
    fhirDraft: Record<string, unknown>;
    confidence: number;
    accepted?: boolean;
  }>;
  /** Speaker diarisation labels (for provider dictation) */
  speakers?: Array<{
    label: string; // "Provider", "Patient"
    segments: Array<{ startMs: number; endMs: number; text: string }>;
  }>;
  encounterRef?: string;
  template?: string;
  createdAt: string;
  completedAt?: string;
};
```

### Prisma touchpoints

- `AudioTranscription` (read)

### Permission

Same as 9A.4 — scoped by sourceType and patient relationship.

### Tests

- returns active status during streaming
- returns completed status with full text after stream ends
- speaker diarisation present for provider_dictation
- entity extraction results include confidence scores
- 404 for non-existent transcription

---

## 9A.6 Confirm transcription

### Contract

- **Method:** `POST`
- **Route:** `/api/v1/transcriptions/:id/confirm`
- **Module:** `DataInputModule` (delegates to `ReviewQueueService`)

### Purpose

Human-reviewed confirmation of transcription-extracted entities. Similar to OCR confirmation (9A.3) but for audio-derived data.

### Request shape

```ts
type ConfirmTranscriptionRequest = {
  reviewedEntities: Array<{
    entityIndex: number;
    action: "accepted" | "edited" | "rejected";
    correctedDraft?: Record<string, unknown>;
  }>;
  /** Optional text corrections to the transcript */
  textCorrections?: Array<{
    originalSegment: string;
    correctedText: string;
  }>;
  encounterRef?: string;
};
```

### Response shape

```ts
type ConfirmTranscriptionResponse = {
  transcriptionId: string;
  status: "confirmed";
  createdResources: Array<{
    resourceType: string;
    resourceId: string;
  }>;
  flaggedForReview: number;
};
```

### Prisma touchpoints

- `AudioTranscription` (update status → confirmed)
- `InputProvenance` (create — audit trail)
- `ReviewQueueItem` (update)
- FHIR resource tables (create)

### Permission

Same as 9A.4.

### Tests

- accept entities → creates FHIR resources
- reject all → no resources created
- edit entities → corrected drafts used
- text corrections persisted
- provenance record includes reviewer identity and corrections made
- 400 if transcription not yet completed
- 409 if already confirmed

---

## 9A.7 Save partial transcription (auto-save)

### Contract

- **Method:** `PATCH`
- **Route:** `/api/v1/transcriptions/:id`
- **Module:** `DataInputModule`

### Purpose

Auto-save partial transcription state during long recording sessions. Prevents data loss on disconnection.

### Request shape

```ts
type PatchTranscriptionRequest = {
  /** Partial text accumulated so far */
  partialText?: string;
  /** Entities extracted so far */
  partialEntities?: Array<{
    entityType: string;
    text: string;
    fhirDraft: Record<string, unknown>;
    confidence: number;
  }>;
};
```

### Response shape

```ts
type PatchTranscriptionResponse = {
  id: string;
  status: "active";
  lastSavedAt: string;
};
```

### Prisma touchpoints

- `AudioTranscription` (update)

### Permission

Only the session owner (the user who started the transcription).

### Tests

- updates partial text
- updates partial entities
- rejects if transcription is already completed or confirmed
- rejects if user is not the session owner

---

## 9A.8 Create FHIR resources from transcription

### Contract

- **Method:** `POST`
- **Route:** `/api/v1/transcriptions/:id/apply`
- **Module:** `DataInputModule`

### Purpose

Create FHIR resources (Observation, Condition, MedicationRequest, etc.) from confirmed transcription entity extractions. This is a separate action from confirmation to allow providers to review once more before writing clinical data.

### Request shape

```ts
type ApplyTranscriptionRequest = {
  /** Indices of confirmed entities to create as resources */
  entityIndices: number[];
  encounterRef?: string;
};
```

### Response shape

```ts
type ApplyTranscriptionResponse = {
  transcriptionId: string;
  createdResources: Array<{
    entityIndex: number;
    resourceType: string;
    resourceId: string;
  }>;
};
```

### Prisma touchpoints

- `AudioTranscription` (read — fetch confirmed entities)
- `InputProvenance` (create — links resource back to audio source)
- FHIR resource tables (create)

### Permission

- `provider` role: can create clinical resources (Condition, MedicationRequest)
- `patient` role: can create self-reported resources (Observation for vitals/symptoms)

### Tests

- creates resources for specified entity indices
- rejects if transcription not confirmed → 400
- rejects entity indices out of range → 400
- patient can only create self-reported Observations
- provider can create all resource types
- provenance links resource to audio source
- encounterRef applied to all created resources

---

# 10. Insurance and billing contracts

> Coverage summary and eligibility checks are part of the core MVP insurance baseline.
> Claim submission and claim status are planned Phase 2 contracts.

## 10.1 Get coverage

### Contract

- **Method:** `GET`
- **Route:** `/api/v1/patients/:id/coverage`
- **Module:** `InsuranceModule`

### Purpose

Return current coverage summary.

### Prisma touchpoints

- `Coverage`

### FHIR mapping

Maps to `Coverage`.

### Tests

- current coverage
- no coverage
- expired policy indication

---

## 10.2 Eligibility check

### Contract

- **Method:** `POST`
- **Route:** `/api/v1/insurance/eligibility-check`
- **Module:** `InsuranceModule`

### Request DTO

```ts
type EligibilityCheckRequest = {
  patientId: string;
  serviceDate?: string;
  providerId?: string;
};
```

### Prisma touchpoints

- `Coverage`
- `EligibilityCheckLog`

### FHIR mapping

App request now, later can map to `CoverageEligibilityRequest` / `CoverageEligibilityResponse`.

### Tests

- success check
- upstream failure
- patient/provider mismatch
- logging of attempts

---

## 10.3 Create claim

### Contract

- **Method:** `POST`
- **Route:** `/api/v1/claims`
- **Module:** `BillingModule`

### Request DTO

```ts
type CreateClaimRequest = {
  patientId: string;
  encounterId?: string;
  coverageId?: string;
  totalAmount: number;
  currency?: string;
  lineItems?: Array<{
    code: string;
    description?: string;
    amount: number;
  }>;
};
```

### Prisma touchpoints

- `Claim`
- `ClaimSubmissionAttempt`

### FHIR mapping

Maps to `Claim`.

### Tests

- valid submit
- line item validation
- upstream integration failure
- retry/submission attempt records

---

## 10.4 Get claim status

### Contract

- **Method:** `GET`
- **Route:** `/api/v1/claims/:id`
- **Module:** `BillingModule`

### Prisma touchpoints

- `Claim`
- `ClaimResponse`

### FHIR mapping

Maps to `Claim` + `ClaimResponse`.

### Tests

- claim found
- patient self access
- provider/staff access
- status transitions visible

---

# 11. Consent and sharing contracts

## 11.1 List access grants

### Contract

- **Method:** `GET`
- **Route:** `/api/v1/patients/:id/access-grants`
- **Module:** `ConsentModule`

### Prisma touchpoints

- `ConsentGrant`

### FHIR mapping

App grant model first; later may map to `Consent`.

### Tests

- current grants
- expired grants
- only owner can see full grant management view

---

## 11.2 Create access grant

### Contract

- **Method:** `POST`
- **Route:** `/api/v1/access-grants`
- **Module:** `ConsentModule`

### Request DTO

```ts
type CreateAccessGrantRequest = {
  patientId: string;
  grantedToType: "PROVIDER" | "CAREGIVER" | "ORGANIZATION";
  grantedToId: string;
  scope: "read" | "write" | "export" | "share" | "emergency";
  startsAt?: string;
  endsAt?: string;
};
```

### Zod schema

```ts
import { z } from "zod";

export const createAccessGrantSchema = z
  .object({
    patientId: z.string().uuid(),
    grantedToType: z.enum(["PROVIDER", "CAREGIVER", "ORGANIZATION"]),
    grantedToId: z.string().min(1),
    scope: z.enum(["read", "write", "export", "share", "emergency"]),
    startsAt: z.string().datetime().optional(),
    endsAt: z.string().datetime().optional(),
  })
  .refine(
    (v) =>
      !v.startsAt ||
      !v.endsAt ||
      new Date(v.endsAt).getTime() > new Date(v.startsAt).getTime(),
    { message: "endsAt must be after startsAt" },
  );
```

### Permissions

- patient owner only

### Prisma touchpoints

- `ConsentGrant`
- `AuditLog`

### Tests

- create grant
- invalid date window
- duplicate conflicting grants
- audit created

---

## 11.3 Revoke access grant

### Contract

- **Method:** `PATCH`
- **Route:** `/api/v1/access-grants/:id/revoke`
- **Module:** `ConsentModule`

### Request DTO

```ts
type RevokeAccessGrantRequest = {
  reason?: string;
};
```

### Tests

- successful revoke
- already expired/revoked
- unauthorized revoke
- downstream access removed

---

# 12. Search contracts

## 12.1 Search patients

### Contract

- **Method:** `GET`
- **Route:** `/api/v1/search/patients`
- **Module:** `SearchModule`

### Query DTO

```ts
type SearchPatientsQuery = {
  q: string;
  page?: number;
  pageSize?: number;
};
```

### Prisma touchpoints

- `Patient`
- `Identifier`
- patient search projections

### FHIR mapping

Searches patient-app view; can return compact patient DTO.

### Tests

- exact search
- partial search
- pagination
- provider-only/admin-only depending policy

---

# 13. Audit contracts

## 13.1 List audit logs

### Contract

- **Method:** `GET`
- **Route:** `/api/v1/audit/logs`
- **Module:** `AuditModule`

### Query DTO

```ts
type AuditLogQuery = {
  resourceType?: string;
  resourceId?: string;
  actorId?: string;
  patientId?: string;
  from?: string;
  to?: string;
  page?: number;
  pageSize?: number;
};
```

### Permissions

- admin/compliance only

### Prisma touchpoints

- `AuditLog`

### FHIR mapping

App audit view first; later possible `AuditEvent` mapping.

### Tests

- filter combinations
- pagination
- restricted access
- export path if enabled

---

# 14. DTO/OpenAPI guidance

Nest’s Swagger/OpenAPI support can generate model definitions from DTO classes and decorated parameters; use explicit DTOs so request and response shapes stay stable.

Suggested backend DTO pattern:

```ts
export class CreatePatientDto {
  @ApiProperty()
  firstName!: string;

  @ApiProperty()
  lastName!: string;

  @ApiProperty()
  dateOfBirth!: string;

  @ApiProperty({ enum: ["male", "female", "other", "unknown"] })
  gender!: "male" | "female" | "other" | "unknown";

  @ApiProperty()
  phone!: string;

  @ApiPropertyOptional()
  email?: string;
}
```

Use:

- request DTO classes for Nest/OpenAPI
- shared Zod schemas for frontend and contract validation
- mapping layer to transform DTOs into domain commands

---

# 15. Validation guidance

Nest supports request validation through pipes, and Zod is a good fit for shared TypeScript-first schema validation.

Recommended split:

- frontend: Zod as source-of-truth for forms
- backend: DTO classes + ValidationPipe and/or Zod-backed custom validation layer
- domain: enforce cross-field and business invariants again in application services

---

# 16. Final recommendation

This route contract pack should become:

- the source for controller DTOs
- the source for frontend API clients
- the source for permission annotations
- the source for QA API tests

The clean next artifact is a **frontend route and component map**:

- route tree
- page shell
- Ghatana components used
- atoms/query hooks per page
- loading/error/empty-state components
- page-level permission guards

---

# 17. Emergency QR API Contract (Added in v2.0)

## 17.1 Get Emergency QR

### Contract

- **Method:** `GET`
- **Route:** `/api/v1/patients/:id/emergency-qr`
- **Module:** `PatientModule`

### Response DTO

```ts
type EmergencyQrResponse = {
  qrDataUrl: string; // base64 QR image
  payload: {
    initials: string; // NOT full name
    bloodType: string;
    allergies: string[]; // generic names only
    activeMedications: string[]; // generic names + dosage
    emergencyContacts: { name: string; phone: string }[];
  };
  lastRefreshed: string; // ISO 8601
  printUrl: string; // URL to download PDF card
};
```

### Permissions

- Patient: own QR only
- Provider: with active grant
- Emergency: break-the-glass access

### Privacy note

- QR payload MUST NOT contain NID, full name, or address
- QR payload is regenerated on any change to allergies, meds, blood type, or emergency contacts

---

# 18. Rate Limiting Headers (Added in v2.0)

All API responses include rate limit headers:

```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 97
X-RateLimit-Reset: 1706140800
Retry-After: 30  (only on 429 responses)
```

### Rate limit tiers

| Endpoint Category | Limit        | Window   |
| ----------------- | ------------ | -------- |
| Authentication    | 10 requests  | 1 minute |
| Patient read      | 100 requests | 1 minute |
| Patient write     | 30 requests  | 1 minute |
| Document upload   | 10 requests  | 1 minute |
| Eligibility check | 20 requests  | 1 minute |
| Admin/audit       | 50 requests  | 1 minute |

---

# 19. Circuit Breaker Error Codes (Added in v2.0)

When an upstream dependency is unavailable, the API returns:

```json
{
  "statusCode": 503,
  "error": "UPSTREAM_UNAVAILABLE",
  "message": "The requested service is temporarily unavailable. Please try again later.",
  "retryAfter": 30
}
```

| Error Code             | Meaning                                 | Retry                       |
| ---------------------- | --------------------------------------- | --------------------------- |
| `UPSTREAM_UNAVAILABLE` | External service circuit open           | Yes, after `retryAfter`     |
| `UPSTREAM_TIMEOUT`     | External service response timeout       | Yes, immediate              |
| `UPSTREAM_DEGRADED`    | Partial data returned (cached fallback) | No (response contains data) |

---

# 20. Tenant Header Standard (Added in v2.0)

All API requests in a multi-tenant deployment MUST include:

```
X-Tenant-Id: {tenantId}
```

- Tenant ID is extracted from the JWT token's `tenant_id` claim
- If `X-Tenant-Id` header is present, it MUST match the JWT claim (otherwise `403`)
- All database queries are scoped to the tenant (application-layer + RLS defense-in-depth)
- Tenant context is propagated through the NestJS request lifecycle via `AsyncLocalStorage`

---

# 21. Security Error Response Guidelines (Added in v2.0)

**OWASP-aligned error handling** — API error responses MUST NOT leak:

| Forbidden in Error Response | Correct Alternative     |
| --------------------------- | ----------------------- |
| Stack traces                | Generic error code      |
| Database column names       | "Validation error"      |
| Internal user IDs           | "Resource not found"    |
| SQL error messages          | "Internal server error" |
| Keycloak internal errors    | "Authentication failed" |
| File system paths           | "Processing error"      |

### Standard error response shape

```ts
type ApiError = {
  statusCode: number;
  error: string; // machine-readable code (e.g., "VALIDATION_ERROR")
  message: string; // user-facing message (safe to display)
  correlationId: string; // for support/debugging reference
};
```

**Rule:** The `correlationId` allows ops to trace the error in logs without exposing internals to the client.

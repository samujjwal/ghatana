# PHR Platform — Core MVP OpenAPI DTO Drafts

**Version:** 1.0  
**Date:** 2026-03-17  
**Last reviewed:** 2026-03-17  
**Next review due:** 2026-04-17  
**Document owner:** API Lead  
**Approval status:** Draft for contract freeze  
**Classification:** Internal — Restricted

| Field | Value |
| --- | --- |
| Source-of-truth inputs | [Route contract pack](phr_mvp_route_contract_pack.md), [Traceability matrix](../01_governance/phr_requirements_traceability_matrix.md), [Screen matrix](phr_screen_by_screen_mvp_implementation_matrix.md) |
| Purpose | define named request and response DTOs for each Core MVP route so backend and frontend can implement against a stable contract |

---

## 1. Shared contract conventions

### 1.1 Mutation envelope

```yaml
MutationEnvelope:
  type: object
  required: [success, message, data]
  properties:
    success:
      type: boolean
      enum: [true]
    message:
      type: string
    data:
      type: object
```

### 1.2 Error envelope

```yaml
ErrorEnvelope:
  type: object
  required: [success, error]
  properties:
    success:
      type: boolean
      enum: [false]
    error:
      type: object
      required: [code, message]
      properties:
        code:
          type: string
        message:
          type: string
        fields:
          type: object
          additionalProperties:
            type: array
            items:
              type: string
```

### 1.3 Shared scalar schemas

- `UuidString`
- `TenantId`
- `IsoDateTime`
- `NepalPhoneNumber`
- `ClassificationCode`
- `PaginationRequest`
- `AuditMeta`

---

## 2. Route-by-route DTO drafts

| Route | Request DTO | Response DTO | Module |
| --- | --- | --- | --- |
| `POST /api/v1/auth/login` | `LoginRequestDto` | `LoginResponseDto` | `IdentityModule` |
| `GET /api/v1/auth/me` | none | `CurrentActorResponseDto` | `IdentityModule` |
| `POST /api/v1/patients` | `CreatePatientRequestDto` | `CreatePatientResponseDto` | `PatientModule` |
| `GET /api/v1/patients/:id` | `GetPatientParamsDto` | `PatientDetailResponseDto` | `PatientModule` |
| `PATCH /api/v1/patients/:id` | `UpdatePatientRequestDto` | `UpdatePatientResponseDto` | `PatientModule` |
| `GET /api/v1/patients/:id/timeline` | `GetTimelineQueryDto` | `PatientTimelineResponseDto` | `PatientModule` |
| `GET /api/v1/patients/:id/encounters` | `ListEncountersQueryDto` | `EncounterListResponseDto` | `EncounterModule` |
| `PATCH /api/v1/encounters/:id` | `UpdateEncounterRequestDto` | `UpdateEncounterResponseDto` | `EncounterModule` |
| `GET /api/v1/patients/:id/observations` | `ListObservationsQueryDto` | `ObservationListResponseDto` | `ObservationModule` |
| `POST /api/v1/observations` | `CreateObservationRequestDto` | `CreateObservationResponseDto` | `ObservationModule` |
| `GET /api/v1/patients/:id/observation-trends` | `ObservationTrendQueryDto` | `ObservationTrendResponseDto` | `ObservationModule` |
| `POST /api/v1/medication-requests` | `CreateMedicationRequestDto` | `CreateMedicationRequestResponseDto` | `MedicationModule` |
| `POST /api/v1/appointments` | `CreateAppointmentRequestDto` | `CreateAppointmentResponseDto` | `AppointmentModule` |
| `POST /api/v1/documents` | `CreateDocumentUploadRequestDto` | `CreateDocumentUploadResponseDto` | `DocumentModule` |
| `GET /api/v1/patients/:id/documents` | `ListDocumentsQueryDto` | `DocumentListResponseDto` | `DocumentModule` |
| `GET /api/v1/patients/:id/coverage` | `GetCoverageParamsDto` | `CoverageSummaryResponseDto` | `InsuranceModule` |
| `POST /api/v1/insurance/eligibility-check` | `EligibilityCheckRequestDto` | `EligibilityCheckResponseDto` | `InsuranceModule` |
| `GET /api/v1/patients/:id/bills` | `GetPatientBillsQueryDto` | `PatientBillListResponseDto` | `BillingModule` |
| `POST /api/v1/payments` | `CreatePaymentRequestDto` | `CreatePaymentResponseDto` | `BillingModule` |
| `GET /api/v1/payments/:id` | `GetPaymentStatusParamsDto` | `PaymentStatusResponseDto` | `BillingModule` |
| `POST /api/v1/payments/:id/confirm` | `ConfirmPaymentRequestDto` | `ConfirmPaymentResponseDto` | `BillingModule` |
| `POST /api/v1/referrals` | `CreateReferralRequestDto` | `CreateReferralResponseDto` | `ReferralModule` |
| `GET /api/v1/patients/:id/referrals` | `ListPatientReferralsQueryDto` | `PatientReferralListResponseDto` | `ReferralModule` |
| `GET /api/v1/referrals/:id` | `GetReferralParamsDto` | `ReferralDetailResponseDto` | `ReferralModule` |
| `GET /api/v1/imaging-studies/:id` | `GetImagingStudyParamsDto` | `ImagingStudyDetailResponseDto` | `ImagingModule` |
| `GET /api/v1/imaging-studies/:id/download` | `DownloadImagingStudyParamsDto` | `ImagingStudyDownloadResponseDto` | `ImagingModule` |
| `POST /api/v1/access-grants` | `CreateAccessGrantRequestDto` | `CreateAccessGrantResponseDto` | `ConsentModule` |
| `GET /api/v1/patients/:id/access-grants` | `ListAccessGrantsQueryDto` | `AccessGrantListResponseDto` | `ConsentModule` |
| `PATCH /api/v1/access-grants/:id/revoke` | `RevokeAccessGrantRequestDto` | `RevokeAccessGrantResponseDto` | `ConsentModule` |
| `GET /api/v1/caregivers/me/dependents` | `ListCaregiverDependentsQueryDto` | `CaregiverDependentListResponseDto` | `FamilyModule` |
| `GET /api/v1/caregivers/me/dependents/:id/summary` | `GetCaregiverDependentSummaryParamsDto` | `CaregiverDependentSummaryResponseDto` | `FamilyModule` |
| `GET /api/v1/audit/logs` | `AuditLogQueryDto` | `AuditLogListResponseDto` | `AuditModule` |
| `POST /api/v1/documents/:id/ocr` | `TriggerOcrRequestDto` | `TriggerOcrResponseDto` | `DataInputModule` |
| `POST /api/v1/ocr-results/:id/confirm` | `ConfirmOcrResultRequestDto` | `ConfirmOcrResultResponseDto` | `DataInputModule` |
| `POST /api/v1/audio-input` | `CreateAudioInputRequestDto` | `CreateAudioInputResponseDto` | `DataInputModule` |
| `GET /api/v1/patients/:id/emergency-qr` | `EmergencyQrParamsDto` | `EmergencyQrResponseDto` | `PatientModule` |
| `POST /api/v1/patients/:id/exports` | `CreatePatientExportRequestDto` | `CreatePatientExportResponseDto` | `InteroperabilityModule` |
| `GET /api/v1/patients/:id/exports/:exportId` | `GetPatientExportStatusParamsDto` | `PatientExportStatusResponseDto` | `InteroperabilityModule` |
| `POST /api/v1/fchv/patients` | `CreateFchvPatientRequestDto` | `CreateFchvPatientResponseDto` | `PatientModule` |
| `GET /api/v1/fchv/patients` | `ListFchvPatientsQueryDto` | `FchvPatientListResponseDto` | `PatientModule` |

---

## 3. Selected DTO skeletons

### 3.1 Auth

```ts
export type LoginRequestDto = {
  username: string;
  password: string;
  otp?: string;
};

export type LoginResponseDto = {
  success: true;
  data: {
    accessToken: string;
    refreshToken?: string;
    actor: {
      actorId: string;
      actorType: "PATIENT" | "PROVIDER" | "CAREGIVER" | "ADMIN" | "FCHV";
      tenantId: string;
      permissions: string[];
    };
  };
};
```

### 3.2 Patient create and update

```ts
export type CreatePatientRequestDto = {
  name: {
    given: string[];
    family: string;
  };
  birthDate: string;
  gender: "male" | "female" | "other" | "unknown";
  phone: string;
  email?: string;
  address: {
    line1: string;
    district: string;
    municipality: string;
    province: string;
  };
  emergencyContact: {
    name: string;
    relationship: string;
    phone: string;
  };
  preferredLanguage: "ne" | "en";
};

export type PatientDetailResponseDto = {
  success: true;
  data: {
    id: string;
    tenantId: string;
    fullName: string;
    birthDate?: string;
    gender?: string;
    bloodType?: string;
    allergies: Array<{ code: string; label: string; severity?: string }>;
    contacts: Array<{ system: string; value: string; use?: string }>;
    address?: {
      district?: string;
      municipality?: string;
      province?: string;
    };
    lastUpdatedAt: string;
  };
};
```

### 3.3 Timeline and observation trends

```ts
export type GetTimelineQueryDto = {
  from?: string;
  to?: string;
  category?: "encounter" | "observation" | "condition" | "medication" | "document";
  page?: number;
  pageSize?: number;
};

export type PatientTimelineResponseDto = {
  success: true;
  data: {
    items: Array<{
      itemType: string;
      itemId: string;
      occurredAt: string;
      title: string;
      subtitle?: string;
      classification: "C3" | "C4";
    }>;
    pagination: {
      page: number;
      pageSize: number;
      total: number;
    };
  };
};

export type ObservationTrendResponseDto = {
  success: true;
  data: {
    code: string;
    unit?: string;
    series: Array<{ at: string; value: number }>;
  };
};
```

### 3.4 Encounter, medication, and appointment mutations

```ts
export type UpdateEncounterRequestDto = {
  status?: "draft" | "active" | "inactive";
  clinicalNote?: string;
  diagnosisCodes?: string[];
  disposition?: string;
};

export type CreateMedicationRequestDto = {
  patientId: string;
  encounterId?: string;
  medicationCode: string;
  display: string;
  dosageInstruction: string;
  frequency: string;
  durationDays?: number;
};

export type CreateAppointmentRequestDto = {
  patientId: string;
  practitionerId?: string;
  locationId?: string;
  startsAt: string;
  reason?: string;
  visitType: "in_person" | "follow_up" | "telemedicine_ready";
};
```

### 3.5 Documents, OCR, and audio input

```ts
export type CreateDocumentUploadRequestDto = {
  patientId: string;
  documentType: string;
  title: string;
  contentType: string;
  fileName: string;
  sizeBytes: number;
  visibility: "private" | "shared_with_grants" | "shared_with_provider";
};

export type TriggerOcrResponseDto = {
  success: true;
  data: {
    ocrResultId: string;
    status: "QUEUED" | "PROCESSING" | "NEEDS_REVIEW";
  };
};

export type ConfirmOcrResultRequestDto = {
  acceptedFields: Record<string, unknown>;
  rejectedFields?: string[];
  createResources: Array<"Observation" | "Condition" | "MedicationRequest" | "DocumentReference">;
};

export type CreateAudioInputRequestDto = {
  patientId?: string;
  encounterId?: string;
  languageCode: "ne-NP" | "en-US";
  mediaReference?: string;
  transcriptText?: string;
};
```

### 3.6 Insurance, family, billing, referrals, imaging, consent, audit, and emergency QR

```ts
export type EligibilityCheckRequestDto = {
  patientId: string;
  coverageId?: string;
  providerId?: string;
  serviceDate?: string;
  serviceType?: string;
};

export type CreateAccessGrantRequestDto = {
  patientId: string;
  grantedToType: "PROVIDER" | "CAREGIVER" | "FCHV";
  grantedToId: string;
  scope: "read" | "write" | "export" | "share" | "emergency";
  startsAt?: string;
  endsAt?: string;
  restrictions?: Record<string, unknown>;
};

export type CaregiverDependentListResponseDto = {
  success: true;
  data: {
    items: Array<{
      dependentId: string;
      fullName: string;
      relationshipLabel: string;
      grantId: string;
      grantExpiresAt?: string;
      allowedScopes: Array<"summary" | "appointments" | "medications" | "documents" | "billing">;
    }>;
  };
};

export type CaregiverDependentSummaryResponseDto = {
  success: true;
  data: {
    dependentId: string;
    fullName: string;
    allowedScopes: Array<"summary" | "appointments" | "medications" | "documents" | "billing">;
    syncState?: "LIVE" | "OFFLINE" | "PENDING_SYNC";
    summaryCards: {
      upcomingAppointments?: Array<{ id: string; startsAt: string; location?: string }>;
      medications?: Array<{ id: string; display: string; dosageInstruction?: string }>;
      billing?: Array<{ invoiceId: string; outstandingAmount: number; currency: "NPR" }>;
    };
  };
};

export type CreatePaymentRequestDto = {
  patientId: string;
  invoiceId: string;
  amount: number;
  currency?: "NPR";
  method: "ESEWA" | "KHALTI" | "FONEPAY" | "CASH_DESK" | "BANK_TRANSFER";
  returnUrl?: string;
  idempotencyKey: string;
};

export type CreatePaymentResponseDto = {
  success: true;
  data: {
    paymentId: string;
    invoiceId: string;
    status: "PENDING_REDIRECT" | "PENDING_CONFIRMATION";
    method: "ESEWA" | "KHALTI" | "FONEPAY" | "CASH_DESK" | "BANK_TRANSFER";
    redirectUrl?: string;
    expiresAt?: string;
  };
};

export type PaymentStatusResponseDto = {
  success: true;
  data: {
    paymentId: string;
    invoiceId: string;
    status: "PENDING_REDIRECT" | "PENDING_CONFIRMATION" | "CONFIRMED" | "SETTLED" | "FAILED";
    amount: number;
    currency: "NPR";
    receiptUrl?: string;
    lastUpdatedAt: string;
  };
};

export type ConfirmPaymentRequestDto = {
  gatewayTransactionId?: string;
  payerConfirmationCode?: string;
  callbackSource: "PATIENT_REDIRECT" | "GATEWAY_WEBHOOK" | "STAFF_DESK";
  confirmedAt?: string;
  signature?: string;
};

export type ConfirmPaymentResponseDto = {
  success: true;
  data: {
    paymentId: string;
    invoiceId: string;
    status: "CONFIRMED" | "SETTLED" | "FAILED";
    confirmedAt?: string;
    receiptId?: string;
    receiptUrl?: string;
  };
};

export type CreateReferralRequestDto = {
  patientId: string;
  receivingOrganizationId: string;
  specialtyCode?: string;
  priority: "routine" | "urgent";
  reason: string;
  summaryDocumentId?: string;
  requestedServiceDate?: string;
};

export type CreateReferralResponseDto = {
  success: true;
  data: {
    referralId: string;
    status: "draft" | "sent";
    createdAt: string;
  };
};

export type PatientReferralListResponseDto = {
  success: true;
  data: {
    items: Array<{
      referralId: string;
      priority: "routine" | "urgent";
      status: "draft" | "sent" | "accepted" | "booked" | "closed";
      receivingOrganizationName: string;
      createdAt: string;
    }>;
  };
};

export type ReferralDetailResponseDto = {
  success: true;
  data: {
    referralId: string;
    patientId: string;
    priority: "routine" | "urgent";
    status: "draft" | "sent" | "accepted" | "booked" | "closed";
    reason: string;
    summaryDocumentId?: string;
    statusTimeline: Array<{ status: string; at: string; note?: string }>;
  };
};

export type ImagingStudyDetailResponseDto = {
  success: true;
  data: {
    imagingStudyId: string;
    patientId: string;
    modality: string;
    performedAt?: string;
    viewerUrl?: string;
    report: {
      diagnosticReportId?: string;
      conclusion?: string;
      issuedAt?: string;
    };
    series: Array<{
      uid: string;
      instanceCount: number;
      description?: string;
    }>;
  };
};

export type ImagingStudyDownloadResponseDto = {
  success: true;
  data: {
    imagingStudyId: string;
    downloadUrl: string;
    expiresAt: string;
    contentType: string;
  };
};

export type AuditLogListResponseDto = {
  success: true;
  data: {
    items: Array<{
      id: string;
      occurredAt: string;
      actorType: string;
      actorId?: string;
      resourceType: string;
      action: string;
      outcome?: string;
      tenantId: string;
    }>;
  };
};

export type EmergencyQrResponseDto = {
  success: true;
  data: {
    qrPayload: string;
    bloodType?: string;
    allergies: string[];
    activeMedications: string[];
    emergencyContacts: Array<{ name: string; phone: string }>;
    generatedAt: string;
    missingFields?: string[];
  };
};

export type CreatePatientExportRequestDto = {
  format: "FHIR_BUNDLE_JSON" | "PDF_SUMMARY";
  scope?: Array<"profile" | "timeline" | "documents" | "medications" | "observations" | "coverage">;
  language?: "ne" | "en";
  includeAuditTrail?: boolean;
};

export type CreatePatientExportResponseDto = {
  success: true;
  data: {
    exportId: string;
    status: "QUEUED" | "PROCESSING";
    format: "FHIR_BUNDLE_JSON" | "PDF_SUMMARY";
    requestedAt: string;
    expiresAt?: string;
  };
};

export type GetPatientExportStatusParamsDto = {
  id: string;
  exportId: string;
};

export type PatientExportStatusResponseDto = {
  success: true;
  data: {
    exportId: string;
    status: "QUEUED" | "PROCESSING" | "READY" | "EXPIRED" | "FAILED";
    format: "FHIR_BUNDLE_JSON" | "PDF_SUMMARY";
    requestedAt: string;
    completedAt?: string;
    expiresAt?: string;
    downloadUrl?: string;
    failureCode?: string;
  };
};
```

---

## 4. Contract freeze conditions

No DTO is ready to leave draft until:

- linked requirement ids are present
- validation schema owner is assigned
- error codes are defined for failure paths
- test IDs are mapped in the QA docs
- sensitive fields have a data classification tag

This document should become the source input for the shared `@phr/schemas` package.
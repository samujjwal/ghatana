# PHR Platform — Frontend Route and Component Map

**Version:** 2.0  
**Date:** 2026-01-19

| Field              | Value                                                                                                                                                                              |
| ------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Document Owner** | Frontend Lead                                                                                                                                                                      |
| **Classification** | Internal                                                                                                                                                                           |
| **Last Review**    | 2026-01-19                                                                                                                                                                         |
| **Companion Docs** | [Screen Matrix](phr_screen_by_screen_mvp_implementation_matrix.md), [Route Contract Pack](phr_mvp_route_contract_pack.md), [Design System](ghatana_design_system_complete_spec.md) |

> **📌 What changed in v2.0:** Added emergency QR route, FCHV simplified flow routes, offline indicator patterns, accessibility route requirements (WCAG 2.2 AA), and error boundary specifications per route.

This document defines the frontend route tree and page/component composition plan for the PHR platform's **core MVP** and the explicitly marked **Phase 2 extension set**.

It assumes:

- **React Router** for the web app
- **shared routed shell** for the desktop app, reusing the web information architecture where feasible
- **Jotai** for local/client UI state
- **TanStack Query** for server state, caching, invalidation, and mutations
- **Ghatana Design System** for UI, forms, charts, and layout
- **React Native** for mobile with equivalent screen grouping
- **desktop shell** (for example Electron or Tauri) hosting the same route groups used by web for provider/admin-heavy workflows

React Router nested routes, Jotai atoms, and TanStack Query mutations fit the intended dashboard-heavy, form-heavy healthcare workflow well.

---

## 1. Frontend architecture goals

The frontend should provide:

- a clean route tree
- role-aware page shells
- minimal page-level complexity
- Ghatana-first component composition
- Jotai for interactive UI state
- TanStack Query for API data
- explicit loading/error/empty states
- page-level access guards
- parity between web, desktop, and mobile information architecture where practical
- Nepali and English localisation with room for later dialect expansion
- mobile-first touch targets, large text, and high-contrast accessibility modes

### 1.1 Component Library Usage

All components must be imported from `@ghatana/design-system` (located at `platform/typescript/design-system/`). **Do not create local component implementations** if they exist in the shared library.

**Import Precedence:**

```typescript
// 1. Ghatana Design System (preferred)
import {
  Button,
  Card,
  FormField,
  DataTable,
  Chart,
} from "@ghatana/design-system";

// 2. Domain-specific healthcare components (local to PHR)
import {
  PatientCard,
  EncounterTimeline,
} from "~/features/healthcare/components";

// 3. Page-level components (local only)
import { PatientDashboardHeader } from "./components";
```

**Healthcare Domain Components (PHR-local, extending Ghatana):**

> These components do not yet exist in `@ghatana/design-system`. They should be created in the PHR product as `~/features/healthcare/components` and promoted to the design system if reused across products.

- `PatientSummaryCard` → `~/features/healthcare/components`
- `EncounterTimeline` → `~/features/healthcare/components`
- `ObservationChart` → extends `@ghatana/charts`
- `MedicationTable` → `~/features/healthcare/components`
- `VitalsTrendChart` → extends `@ghatana/charts`

**Base Components from Ghatana Design System (`@ghatana/design-system`):**

- Layout: `Box`, `Flex`, `Stack`, `Grid`, `PageShell`
- Controls: `Button`, `Input`, `Select`, `DatePicker`, `FileUpload`
- Display: `Card`, `Table`, `Badge`, `Avatar`, `Skeleton`
- Feedback: `Alert`, `Toast`, `Modal`, `Drawer`
- Navigation: `Tabs`, `Breadcrumbs`, `Pagination`, `Stepper`

**Charts (`@ghatana/charts`):**

- Recharts-based chart primitives with Ghatana theming
- Extend for healthcare-specific visualizations (vitals trends, lab results)

**Audio/Video UI (`@ghatana/audio-video-ui`):**

- Audio recorder, transcription viewer, video call components
- Use for telemedicine, voice data entry, OCR flows

---

## 2. Route organization principles

### 2.1 Use nested routes

Use nested routes for:

- authenticated app shell
- patient area
- provider area
- caregiver area
- admin area

This keeps:

- shared layout concerns centralized
- route guards easier to manage
- page titles/breadcrumbs/context loaders simpler

### 2.2 Use section shells

Use one parent layout per major area:

- `AppShell`
- `PatientShell`
- `ProviderShell`
- `CaregiverShell`
- `AdminShell`

### 2.3 Separate route concerns

Per route define:

- page shell
- data queries
- UI atoms
- page guard
- loading state component
- error state component
- empty state component

---

## 3. Web route tree

Core MVP routes are listed alongside committed Phase 2 placeholders so information architecture stays stable between releases.
Telemedicine consultation room routes are intentionally excluded until dedicated call-session contracts are added.

```text
/
├─ /sign-in
├─ /forbidden
├─ /not-found
└─ /app
   ├─ /app/patient
   │  ├─ /app/patient/dashboard
   │  ├─ /app/patient/profile
   │  ├─ /app/patient/timeline
   │  ├─ /app/patient/conditions
   │  ├─ /app/patient/observations
   │  ├─ /app/patient/medications
   │  ├─ /app/patient/appointments
   │  ├─ /app/patient/appointments/book
   │  ├─ /app/patient/documents
   │  ├─ /app/patient/documents/upload
   │  ├─ /app/patient/documents/ocr
   │  ├─ /app/patient/input/voice
   │  ├─ /app/patient/insurance
   │  ├─ /app/patient/claims
  │  ├─ /app/patient/export
   │  └─ /app/patient/access
   ├─ /app/patient/emergency-qr          (Added in v2.0)
   ├─ /app/fchv                           (Added in v2.0)
   │  ├─ /app/fchv/dashboard
   │  ├─ /app/fchv/register-patient
   │  ├─ /app/fchv/patients
   │  └─ /app/fchv/patients/:id/vitals
   ├─ /app/provider
   │  ├─ /app/provider/dashboard
   │  ├─ /app/provider/patients
   │  ├─ /app/provider/patients/:id
   │  ├─ /app/provider/encounters/:id
   │  ├─ /app/provider/observations/new
   │  ├─ /app/provider/medication-requests/new
   │  ├─ /app/provider/input/voice
   │  └─ /app/provider/calendar
   ├─ /app/caregiver
   │  ├─ /app/caregiver/dependents
   │  └─ /app/caregiver/dependents/:id
   └─ /app/admin
      ├─ /app/admin/dashboard
      └─ /app/admin/audit
```

---

## 4. Shared page shells

## 4.1 Root shell

Component:

- `RootShell`

Responsibilities:

- app bootstrap
- theme provider
- auth bootstrap
- query client provider
- jotai provider
- router outlet
- global toast/modal hosts

Uses Ghatana:

- `GhatanaAppFrame`
- `GhatanaToastHost`
- `GhatanaModalHost`

## 4.2 Auth shell

Component:

- `AuthShell`

Responsibilities:

- minimal unauthenticated layout
- language switcher
- branding
- auth forms container

## 4.3 App shell

Component:

- `AppShell`

Responsibilities:

- authenticated app frame
- role-aware nav
- top bar
- breadcrumbs
- outlet
- global background refresh indicators

Uses:

- `Sidebar`
- `TopNav`
- `Breadcrumbs`
- `StatusBanner`
- `Outlet`

---

## 5. Shared route guards

Recommended page guards:

- `RequireAuthGuard`
- `RequireRoleGuard`
- `RequirePatientContextGuard`
- `RequireConsentScopeGuard`
- `RequireAdminGuard`

Behavior:

- unauthenticated → redirect to `/sign-in`
- unauthorized → `/forbidden`
- missing patient context → route-level fallback
- expired sharing grant → contextual access-expired page/banner

---

## 6. Shared page states

Every page should define explicit components for:

- `PageLoadingState`
- `PageErrorState`
- `PageEmptyState`

Use Ghatana primitives:

- `Skeleton`
- `Alert`
- `EmptyState`
- `RetryAction`
- `InlineBanner`

Clinical pages should also define:

- `AccessDeniedState`
- `StaleGrantState`
- `NoCoverageState` where relevant

---

## 7. Jotai state strategy

Jotai is best used here for page-level UI state, derived UI state, and lightweight workflow state rather than primary server cache.

## 7.1 Global atoms

Suggested atoms:

- `sessionAtom`
- `actorAtom`
- `tenantAtom`
- `themeAtom`
- `languageAtom`
- `selectedPatientAtom`
- `sidebarCollapsedAtom`
- `globalModalAtom`

## 7.2 Page atoms

Per-page examples:

- dashboard filter atom
- timeline filter atom
- chart metric atom
- medication tab atom
- appointment booking draft atom
- document upload modal atom
- audit filter atom

## 7.3 Derived atoms

Examples:

- `activePatientIdAtom`
- `canEditPatientAtom`
- `activeTimelineFiltersAtom`
- `selectedObservationMetricAtom`
- `isProviderViewAtom`

---

## 8. TanStack Query strategy

TanStack Query should own:

- API reads
- server cache
- mutation flows
- invalidation rules
- optimistic updates where appropriate

Use:

- `useQuery` for reads
- `useMutation` for writes
- invalidation after create/update/delete
- query key factories per module

Recommended query key groups:

- `authKeys`
- `patientKeys`
- `timelineKeys`
- `conditionKeys`
- `observationKeys`
- `medicationKeys`
- `appointmentKeys`
- `documentKeys`
- `insuranceKeys`
- `claimKeys`
- `accessGrantKeys`
- `auditKeys`

---

## 9. Patient area pages

## 9.1 `/app/patient/dashboard`

### Page shell

- `PatientDashboardPage`

### Main Ghatana components

- `PageHeader`
- `PatientSummaryCard`
- `KpiStatGrid`
- `ConditionListCard`
- `MedicationTable`
- `UpcomingAppointmentCard`
- `ObservationTrendMiniChart`
- `RecentDocumentsCard`

### Queries

- `usePatientDashboardQuery(patientId)`

### Atoms

- `patientDashboardFilterAtom`
- `patientDashboardRangeAtom`

### States

- loading: dashboard skeleton grid
- error: dashboard alert + retry
- empty: welcome/complete-profile state

### Permissions

- patient self
- caregiver if allowed

---

## 9.2 `/app/patient/profile`

### Page shell

- `PatientProfilePage`

### Main Ghatana components

- `FormPageLayout`
- `PatientProfileForm`
- `SectionCard`
- `EmergencyContactCard`
- `SaveBar`

### Queries

- `usePatientQuery(patientId)`

### Mutations

- `useUpdatePatientMutation()`

### Atoms

- `patientProfileEditModeAtom`
- `patientProfileDirtyAtom`

### States

- loading: form skeleton
- error: profile load failure
- empty: not applicable in normal flow

---

## 9.3 `/app/patient/timeline`

### Page shell

- `PatientTimelinePage`

### Main Ghatana components

- `FilterBar`
- `EncounterTimeline`
- `TimelineItemCard`
- `DateRangePicker`
- `PaginationBar`

### Queries

- `usePatientTimelineQuery(patientId, filters)`

### Atoms

- `timelineFilterAtom`
- `timelineExpandedItemAtom`
- `timelinePageAtom`

### States

- loading: timeline skeleton rows
- error: retryable timeline error
- empty: no records state

---

## 9.4 `/app/patient/conditions`

### Page shell

- `PatientConditionsPage`

### Main components

- `PageHeader`
- `ConditionList`
- `ConditionSummaryCard`
- `StatusTabs`

### Queries

- `usePatientConditionsQuery(patientId, filters)`

### Atoms

- `conditionStatusFilterAtom`
- `conditionSortAtom`

---

## 9.5 `/app/patient/observations`

### Page shell

- `PatientObservationsPage`

### Main components

- `ObservationMetricSelector`
- `LabTrendChart`
- `VitalsTrendChart`
- `ObservationTable`
- `ThresholdLegend`

### Queries

- `usePatientObservationsQuery(patientId, filters)`
- `useObservationTrendQuery(patientId, metric, range)`

### Atoms

- `selectedObservationMetricAtom`
- `observationRangeAtom`
- `observationViewModeAtom`

### States

- loading chart skeleton
- no metric selected
- no trend data

---

## 9.6 `/app/patient/medications`

### Page shell

- `PatientMedicationsPage`

### Main components

- `MedicationTable`
- `MedicationStatusTabs`
- `MedicationDetailDrawer`
- `AdherenceSummaryCard`

### Queries

- `usePatientMedicationRequestsQuery(patientId, filters)`

### Atoms

- `medicationTabAtom`
- `selectedMedicationRequestAtom`

---

## 9.7 `/app/patient/appointments`

### Page shell

- `PatientAppointmentsPage`

### Main components

- `AppointmentList`
- `AppointmentStatusTabs`
- `AppointmentCard`
- `RescheduleDialog`
- `CancelDialog`

### Queries

- `usePatientAppointmentsQuery(patientId, filters)`

### Atoms

- `appointmentTabAtom`
- `appointmentFilterAtom`

---

## 9.8 `/app/patient/appointments/book`

### Page shell

- `BookAppointmentPage`

### Main components

- `ProviderSearchSelect`
- `AvailabilityCalendar`
- `BookingForm`
- `ConfirmationCard`

### Queries

- `useProviderAvailabilityQuery(providerId, dateRange)`

### Mutations

- `useCreateAppointmentMutation()`

### Atoms

- `bookingDraftAtom`
- `selectedProviderAtom`
- `selectedAppointmentSlotAtom`

### State rules

- keep booking flow state in atoms until submit success
- invalidate patient appointments and provider availability on success

---

## 9.9 `/app/patient/documents`

### Page shell

- `PatientDocumentsPage`

### Main components

- `DocumentList`
- `DocumentFilterBar`
- `DocumentUploadPanel`
- `DocumentPreviewDrawer`
- `VersionHistoryPanel`

### Queries

- `usePatientDocumentsQuery(patientId, filters)`

### Mutations

- `useCreateDocumentMutation()`

### Atoms

- `documentFilterAtom`
- `documentUploadDialogAtom`
- `selectedDocumentAtom`

---

## 9.9a `/app/patient/documents/upload`

### Page shell

- `DocumentUploadPage`

### Purpose

Multi-modal document upload supporting three input methods:

1. **Form-based upload** — standard file picker for PDF, images, CSV
2. **Camera/scanner** — capture document via device camera (mobile) or scanner
3. **Batch import** — drag-and-drop multiple files

### Main components

- `FileUploadDropzone` (from `@ghatana/design-system`)
- `CameraCaptureButton`
- `DocumentTypeSelector`
- `UploadProgressBar`
- `DocumentMetadataForm`
- `BatchUploadList`

### Mutations

- `useCreateDocumentMutation()`
- `useOcrProcessDocumentMutation()` (auto-triggered after upload if image/scanned PDF)

### Atoms

- `uploadFilesAtom`
- `uploadProgressAtom`
- `documentMetadataDraftAtom`

### Flow

1. User selects file(s) or captures via camera
2. File is uploaded and stored via `DocumentModule`
3. If file is an image or scanned PDF, OCR is auto-triggered (background)
4. User fills metadata form (date, type, provider — can be pre-filled from OCR)
5. Document saved with provenance tagged as `manual-upload`

---

## 9.9b `/app/patient/documents/ocr`

### Page shell

- `OcrReviewPage`

### Purpose

Review and confirm data extracted from scanned documents via OCR. This screen bridges the gap between raw document upload and structured health record creation.

### Main components

- `DocumentPreview` — shows the original scanned document
- `ExtractedFieldsForm` — editable form pre-filled with OCR-extracted values
- `ConfidenceIndicator` — shows extraction confidence per field (green/yellow/red)
- `FieldMappingSelector` — map extracted text to FHIR resource fields
- `AcceptRejectBar` — accept all, reject, or selectively approve fields
- `OcrStatusBanner` — shows OCR processing status (processing/complete/failed)

### Queries

- `useOcrResultQuery(documentId)`

### Mutations

- `useConfirmOcrExtractionMutation()` — creates FHIR resources from confirmed fields
- `useRejectOcrFieldMutation()` — marks specific fields as incorrect

### Atoms

- `ocrReviewFieldsAtom`
- `ocrConfidenceFilterAtom`
- `selectedOcrDocumentAtom`

### Flow

1. User navigates here after OCR processing completes (via notification or documents list)
2. Left panel shows original document; right panel shows extracted fields
3. Each field shows confidence score and allows correction
4. User confirms/rejects extracted data
5. Confirmed fields create FHIR resources (Observation, Condition, MedicationRequest, etc.)
6. Provenance tagged as `ocr` with confidence scores stored

---

## 9.9c `/app/patient/input/voice`

### Page shell

- `VoiceDataEntryPage`

### Purpose

Voice-based data entry for patients. Allows recording symptoms, vitals, medication adherence, and general health notes via audio, which are then transcribed and optionally converted to structured records.

### Main components

- `AudioRecorder` (from `@ghatana/audio-video-ui`)
- `TranscriptionViewer` (from `@ghatana/audio-video-ui`)
- `TranscriptEditForm` — editable transcript text
- `StructuredDataExtractionPanel` — shows extracted vitals, symptoms, medications
- `VoiceInputTypeTabs` — tabs for: symptoms, vitals, medication log, general notes
- `ConfirmStructuredDataForm` — confirm and save extracted structured data
- `LanguageSelector` — Nepali / English

### Queries

- `useTranscriptionResultQuery(recordingId)`

### Mutations

- `useSubmitAudioRecordingMutation()` — uploads audio for transcription
- `useConfirmVoiceExtractionMutation()` — creates FHIR resources from confirmed transcript data

### Atoms

- `voiceInputTypeAtom`
- `recordingStateAtom`
- `transcriptionResultAtom`
- `voiceExtractionDraftAtom`
- `voiceLanguageAtom`

### Flow

1. User selects input type (symptoms, vitals, medication log, general notes)
2. User records audio in Nepali or English
3. Audio is sent to ASR backend (via `@ghatana/audio-video-client`)
4. Transcript appears in real-time or near-real-time
5. User can edit transcript
6. System extracts structured data from transcript (vitals → Observation, symptoms → Condition, etc.)
7. User reviews and confirms structured data
8. Records created with provenance tagged as `voice`

### Platform libraries

- `@ghatana/audio-video-client` — STT HTTP client
- `@ghatana/audio-video-ui` — AudioRecorder, TranscriptionViewer components
- `@ghatana/audio-video-types` — shared STT/TTS types

---

## 9.10 `/app/patient/insurance`

### Page shell

- `PatientInsurancePage`

### Main components

- `CoverageSummaryCard`
- `EligibilityCheckCard`
- `PolicyDetailPanel`
- `NoCoverageState`

### Queries

- `usePatientCoverageQuery(patientId)`

### Mutations

- `useEligibilityCheckMutation()`

### Atoms

- `selectedCoverageAtom`
- `eligibilityCheckDraftAtom`

---

## 9.11 `/app/patient/payments`

### Page shell

- `PatientPaymentsPage`

### Main components

- `OutstandingBillsList`
- `PaymentMethodCard`
- `PaymentStatusBadge`
- `ReceiptDrawer`

### Queries

- `usePatientBillsQuery(patientId)`
- `usePaymentStatusQuery(paymentId)`

### Mutations

- `useCreatePaymentMutation()`
- `useConfirmPaymentMutation()`

### Atoms

- `selectedInvoiceAtom`
- `paymentDraftAtom`
- `selectedReceiptAtom`

---

## 9.12 `/app/patient/referrals`

### Page shell

- `PatientReferralsPage`

### Main components

- `ReferralList`
- `ReferralStatusTimeline`
- `ReferralDetailDrawer`

### Queries

- `usePatientReferralsQuery(patientId)`

### Atoms

- `referralFilterAtom`
- `selectedReferralAtom`

---

## 9.13 `/app/patient/imaging/:id`

### Page shell

- `PatientImagingStudyPage`

### Main components

- `ImagingStudyHeader`
- `DicomViewerPane`
- `RadiologyReportCard`
- `SecureDownloadPanel`

### Queries

- `useImagingStudyQuery(imagingStudyId)`

### Atoms

- `viewerToolAtom`
- `selectedSeriesAtom`

---

## 9.14 `/app/patient/claims` (Phase 2)

### Page shell

- `PatientClaimsPage`

### Main components

- `ClaimList`
- `ClaimStatusBadge`
- `ClaimSubmissionForm`
- `ClaimDetailDrawer`

### Queries

- `usePatientClaimsQuery(patientId, filters)`

### Mutations

- `useCreateClaimMutation()`

### Atoms

- `claimFilterAtom`
- `claimSubmissionDraftAtom`
- `selectedClaimAtom`

---

## 9.15 `/app/patient/access`

### Page shell

- `PatientAccessPage`

### Main components

- `AccessGrantTable`
- `CreateAccessGrantDialog`
- `GrantScopeSelector`
- `GrantExpiryPicker`
- `RevokeAccessDialog`

### Queries

- `useAccessGrantsQuery(patientId)`

### Mutations

- `useCreateAccessGrantMutation()`
- `useRevokeAccessGrantMutation()`

### Atoms

- `accessGrantDialogAtom`
- `accessGrantDraftAtom`
- `selectedGrantAtom`

---

## 10. Provider area pages

## 10.1 `/app/provider/dashboard`

### Page shell

- `ProviderDashboardPage`

### Main components

- `WorkQueueCard`
- `TodayAppointmentsCard`
- `RecentPatientsTable`
- `AlertBanner`
- `ProviderStatsGrid`

### Queries

- `useProviderDashboardQuery()`

### Atoms

- `providerDashboardFilterAtom`
- `providerFacilityContextAtom`

---

## 10.2 `/app/provider/patients`

### Page shell

- `ProviderPatientSearchPage`

### Main components

- `SearchInput`
- `PatientSearchFilters`
- `PatientResultTable`
- `NoSearchResultsState`

### Queries

- `usePatientSearchQuery(query, filters)`

### Atoms

- `patientSearchQueryAtom`
- `patientSearchFiltersAtom`

### Behavior

- debounce query input
- sync filter state with URL where useful

---

## 10.3 `/app/provider/patients/:id`

### Page shell

- `ProviderPatientSummaryPage`

### Main components

- `PatientHeader`
- `ClinicalSummaryPanel`
- `ActiveConditionsCard`
- `MedicationSummaryCard`
- `RecentObservationCard`
- `ConsentAccessBanner`

### Queries

- `useProviderPatientSummaryQuery(patientId)`

### Atoms

- `providerPatientSummarySectionAtom`

### Guard

- `RequireConsentScopeGuard`

---

## 10.4 `/app/provider/encounters/:id`

### Page shell

- `EncounterDetailPage`

### Main components

- `EncounterHeader`
- `EncounterTabs`
- `EncounterSummaryEditor`
- `ObservationPanel`
- `MedicationPanel`
- `DocumentPanel`

### Queries

- `useEncounterQuery(encounterId)`

### Mutations

- `useUpdateEncounterMutation()`

### Atoms

- `encounterTabAtom`
- `encounterDraftAtom`

---

## 10.5 `/app/provider/observations/new`

### Page shell

- `CreateObservationPage`

### Main components

- `ObservationEntryForm`
- `MetricCodeSelect`
- `UnitInput`
- `AbnormalRangeHint`

### Mutations

- `useCreateObservationMutation()`

### Atoms

- `observationDraftAtom`
- `observationMetricAtom`

---

## 10.6 `/app/provider/medication-requests/new`

### Page shell

- `CreateMedicationRequestPage`

### Main components

- `MedicationRequestForm`
- `MedicationLookup`
- `DosageSection`
- `AllergyInteractionBanner`

### Mutations

- `useCreateMedicationRequestMutation()`

### Atoms

- `medicationRequestDraftAtom`
- `selectedMedicationAtom`

---

## 10.7 `/app/provider/calendar`

### Page shell

- `ProviderCalendarPage`

### Main components

- `CalendarToolbar`
- `AppointmentCalendar`
- `AppointmentDrawer`
- `AvailabilityFilterBar`

### Queries

- `useProviderAppointmentsQuery(filters)`
- `useProviderAvailabilityQuery()`

### Atoms

- `calendarViewModeAtom`
- `calendarDateAtom`
- `calendarFacilityFilterAtom`

---

## 10.8 `/app/provider/input/voice`

### Page shell

- `ProviderVoiceInputPage`

### Main components

- `VoiceRecorderPanel` — Start / stop / pause audio capture with waveform visualisation (uses `@ghatana/audio-video-ui` `AudioRecorder` + `WaveformVisualizer`)
- `TranscriptionPreviewPane` — Live streaming transcript with speaker-diarisation labels (Provider vs Patient)
- `ClinicalEntityHighlighter` — Highlights extracted FHIR entities (diagnoses → Condition, vitals → Observation, meds → MedicationRequest) with colour-coded chips
- `StructuredOutputEditor` — Editable form that maps extracted entities to FHIR fields; provider can accept/reject/edit each extraction
- `TemplateSelector` — Quick-pick from SOAP Note, Prescription, Discharge Summary, Referral Letter templates to guide extraction
- `DictationCommandBar` — Voice-command-aware toolbar (e.g., "new paragraph", "correction", "next field")
- `ConfidenceScoreBadge` — Per-field confidence indicator from ASR + NLP pipeline
- `AttachToEncounterToggle` — Links transcription output to the current or selected Encounter
- `SubmitForReviewButton` — Saves structured output; flags low-confidence items for human review

### Queries

- `useProviderTranscriptionQuery(transcriptionId)` — fetch saved transcription
- `useRecentTranscriptionsQuery(providerId)` — list of recent voice sessions
- `usePatientContextQuery(patientId)` — fetch active encounter, conditions, medications for entity resolution
- `useTemplatesQuery()` — available dictation templates

### Mutations

- `useStartTranscriptionMutation()` — `POST /api/v1/audio-input` with `{ sourceType: "provider_dictation", encounterRef }` → returns `transcriptionId` + streaming WebSocket URL
- `useConfirmTranscriptionMutation()` — `POST /api/v1/transcriptions/:id/confirm` with reviewed structured output
- `useSavePartialTranscriptionMutation()` — `PATCH /api/v1/transcriptions/:id` for auto-save during long sessions
- `useCreateResourcesFromTranscriptionMutation()` — `POST /api/v1/transcriptions/:id/apply` creates Observation/Condition/MedicationRequest FHIR resources from confirmed extractions

### Atoms

- `providerRecordingStateAtom` — `{ status: 'idle' | 'recording' | 'paused' | 'processing', durationMs: number }`
- `liveTranscriptAtom` — streaming transcript text (updated via WebSocket)
- `extractedEntitiesAtom` — `Array<{ type: 'Condition' | 'Observation' | 'MedicationRequest', text: string, fhirDraft: object, confidence: number, accepted: boolean }>`
- `selectedTemplateAtom` — active dictation template
- `targetEncounterAtom` — Encounter to attach results to

### Flow

1. Provider selects patient context + encounter (or creates a new encounter session)
2. Optionally picks a dictation template (SOAP, Prescription, etc.)
3. Taps **Start Recording** → `useStartTranscriptionMutation()` opens WebSocket stream
4. Audio streams to backend ASR → `liveTranscriptAtom` updates in real-time
5. NLP pipeline extracts clinical entities → `extractedEntitiesAtom` populates `StructuredOutputEditor`
6. Provider stops recording → reviews `StructuredOutputEditor`
7. Accepts/edits/rejects each extracted entity; low-confidence items flagged
8. Taps **Confirm & Save** → `useConfirmTranscriptionMutation()` finalises transcription
9. Optionally taps **Create Resources** → `useCreateResourcesFromTranscriptionMutation()` writes FHIR resources
10. Resources appear in patient timeline under the linked Encounter

### Platform libraries

| Library                       | Usage                                                         |
| ----------------------------- | ------------------------------------------------------------- |
| `@ghatana/audio-video-client` | `SttClient` for WebSocket ASR streaming                       |
| `@ghatana/audio-video-ui`     | `AudioRecorder`, `WaveformVisualizer` components              |
| `@ghatana/audio-video-types`  | `TranscriptionResult`, `SttConfig`, `AudioChunk` types        |
| `@ghatana/design-system`      | `Button`, `Badge`, `Card`, `Form`, `Select`, `Chip`, `Dialog` |
| `@ghatana/realtime`           | WebSocket manager for live transcript streaming               |
| `@ghatana/i18n`               | Nepali / English UI labels + medical terminology localisation |

---

## 11. Caregiver area pages

## 11.1 `/app/caregiver/dependents`

### Page shell

- `CaregiverDependentsPage`

### Main components

- `DependentCardGrid`
- `LinkedProfilesList`
- `AccessStatusBadge`

### Queries

- `useCaregiverDependentsQuery()`

### Atoms

- `selectedDependentAtom`

---

## 11.2 `/app/caregiver/dependents/:id`

### Page shell

- `CaregiverDependentSummaryPage`

### Main components

- `DependentHeader`
- `ScopedSummaryCard`
- `MedicationSummaryCard`
- `UpcomingAppointmentsCard`
- `GrantLimitBanner`

### Queries

- `useDependentSummaryQuery(dependentId)`

### Guard

- caregiver scope guard + grant validity checks

### Behavior

- show delegated sync state for offline-capable actions
- hide fields outside active caregiver grant scope

---

## 12. Admin area pages

## 12.1 `/app/admin/dashboard`

### Page shell

- `AdminDashboardPage`

### Main components

- `AdminStatsGrid`
- `FacilityHealthCard`
- `ConfigSummaryCard`
- `RecentAuditAlertsCard`

### Queries

- `useAdminDashboardQuery()`

### Atoms

- `adminTenantFilterAtom`
- `adminDateRangeAtom`

---

## 12.2 `/app/admin/audit`

### Page shell

- `AuditViewerPage`

### Main components

- `AuditFilterBar`
- `AuditLogTable`
- `AuditDetailDrawer`
- `ExportAuditDialog`

### Queries

- `useAuditLogsQuery(filters)`

### Atoms

- `auditFilterAtom`
- `selectedAuditLogAtom`

### Guard

- admin/compliance only

---

## 13. Suggested route module structure (web)

```text
src/routes/
  public/
    sign-in/
  app/
    layout/
    patient/
      dashboard/
      profile/
      timeline/
      conditions/
      observations/
      medications/
      appointments/
      documents/
      insurance/
      export/
      claims/
      access/
    provider/
      dashboard/
      patients/
      encounters/
      observations/
      medication-requests/
      calendar/
    caregiver/
      dependents/
    admin/
      dashboard/
      audit/
```

Each route folder can contain:

- `page.tsx`
- `loader.ts` or query binding helpers if used
- `components/`
- `atoms.ts`
- `queries.ts`
- `schemas.ts`
- `guards.ts`

---

## 14. Suggested frontend package structure

```text
src/
  app/
  routes/
  components/
  features/
  hooks/
  lib/
  atoms/
  queries/
  guards/
  layouts/
  providers/
```

Feature packages:

- `features/patient`
- `features/encounter`
- `features/observation`
- `features/medication`
- `features/appointment`
- `features/document`
- `features/insurance`
- `features/export`
- `features/consent`
- `features/search`
- `features/admin`

---

## 15. Suggested query hook organization

Use one hook file group per domain:

- `patient.queries.ts`
- `encounter.queries.ts`
- `observation.queries.ts`
- `medication.queries.ts`
- `appointment.queries.ts`
- `document.queries.ts`
- `insurance.queries.ts`
- `export.queries.ts`
- `claim.queries.ts`
- `consent.queries.ts`
- `audit.queries.ts`

Include:

- key factories
- query options
- mutation hooks
- invalidation helpers

---

## 16. Suggested atoms organization

Use:

- domain-level atoms only where UI state is shared
- page-level atoms close to the route when state is page-specific

Examples:

- `atoms/session.atoms.ts`
- `atoms/theme.atoms.ts`
- `features/timeline/timeline.atoms.ts`
- `routes/app/patient/appointments/book/atoms.ts`

---

## 17. Mobile information architecture

Mobile should mirror the same domain groupings, adapted to bottom-tab and stack navigation.

Suggested tabs:

- Home
- Records
- Medications
- Appointments
- Documents
- Profile

Provider mobile can stay smaller:

- Dashboard
- Patients
- Calendar
- Messages/Tasks later if activated

---

## 18. Page-level permission model

Every route should declare:

- required authentication
- allowed roles
- additional scope requirements
- feature flag if not generally enabled

Suggested metadata shape:

```ts
type RouteAccessMeta = {
  requiresAuth: boolean;
  allowedRoles?: Array<"PATIENT" | "PROVIDER" | "CAREGIVER" | "ADMIN" | "FCHV">;
  requiresPatientGrant?: boolean;
  featureFlag?: string;
};
```

---

## 19. Final recommendation

Implement frontend in this order:

### Wave 1

- sign-in
- app shell
- patient dashboard
- patient profile
- provider dashboard
- patient search

### Wave 2

- timeline
- observations
- medications
- documents

### Wave 3

- appointments list
- booking
- provider calendar

### Wave 4

- insurance
- claims
- access grants
- admin audit

### Wave 5

- caregiver area
- richer provider detail workflows

This keeps the frontend route tree clean, aligns state correctly between Jotai and TanStack Query, and keeps Ghatana as the single UI contract layer.

---

## 20. Emergency QR and Export Routes (Added in v2.0)

### 20.1 `/app/patient/emergency-qr`

**Purpose:** Display and manage patient's Emergency QR card.

**Page components:**

- `EmergencyQrCard` — printable credit-card-sized QR code
- `EmergencyQrDataSummary` — shows what data is encoded (blood type, allergies, meds, emergency contacts)
- `EmergencyQrPrintButton` — generates PDF for physical wallet card
- `EmergencyQrRefreshStatus` — shows last refresh timestamp and auto-refresh trigger info

**Data queries:**

- `useEmergencyQrQuery(patientId)` — fetches current QR payload
- `useEmergencyQrRefreshMutation()` — triggers manual regeneration

**Guard:** Authenticated patient only. QR data is patient-scoped.

**Empty state:** "Complete your profile (blood type, allergies, emergency contacts) to generate your Emergency QR card."

### 20.2 `/app/patient/export`

**Purpose:** Let patients request, monitor, and download time-limited portability artifacts.

**Page components:**

- `ExportFormatSelector` — choose `FHIR_BUNDLE_JSON` or `PDF_SUMMARY`
- `ExportScopeChecklist` — select which record sections to include
- `ExportStatusPanel` — shows queued, processing, ready, expired, or failed state
- `ExportDownloadCard` — renders download CTA and expiry messaging

**Data queries:**

- `useCreatePatientExportMutation()` — requests an export job
- `usePatientExportStatusQuery(patientId, exportId)` — polls export status

**Guard:** Authenticated patient only unless an explicit export-scoped grant exists.

**Empty state:** "No export has been requested yet. Choose a format to generate a portable copy of your record."

---

## 21. FCHV Area Routes (Added in v2.0)

### 21.1 `/app/fchv/dashboard`

**Purpose:** FCHV home screen with icon-based navigation (large touch targets, minimal text).

**Page components:**

- `FchvDashboardGrid` — 4 primary actions: Register Patient, View Patients, Record Vitals, Request Telemedicine
- `FchvSyncStatus` — shows last sync time and pending uploads count
- `FchvOfflineIndicator` — prominent offline/online status

### 21.2 `/app/fchv/register-patient`

**Purpose:** Simplified patient registration for community health visits.

**Page components:**

- `NidQrScanner` — camera-based NID QR code reader
- `FchvRegistrationForm` — minimal fields: name, DOB, gender, blood type, known allergies
- `FchvRegistrationConfirmation` — shows pending status and SMS confirmation note

### 21.3 `/app/fchv/patients/:id/vitals`

**Purpose:** Quick vitals entry during home visit.

**Page components:**

- `VitalsEntryForm` — BP, pulse, temperature, weight, blood glucose (large input fields)
- `VitalsHistoryMini` — last 3 readings for context

**Offline:** Scoped offline capture only for approved field workflows. Queued vitals sync when connectivity returns.

---

## 22. Offline Indicators (Added in v2.0)

All page shells include:

- **`OfflineBanner`**: Amber bar at top — patient routes show read-only degraded mode; FCHV routes show queued field-capture sync state.
- **`SyncStatusBadge`**: Shows in navigation — pending upload count
- **`StalenessIndicator`**: Per-data-section — "Last updated: {timestamp}"

---

## 23. Accessibility (WCAG 2.2 AA) (Added in v2.0)

All routes MUST meet WCAG 2.2 AA:

| Requirement          | Implementation                                              |
| -------------------- | ----------------------------------------------------------- |
| Keyboard navigation  | All interactive elements focusable and operable             |
| Screen reader        | Semantic HTML + `aria-label` on custom components           |
| Color contrast       | 4.5:1 minimum for text, 3:1 for large text                  |
| Focus indicators     | Visible focus ring on all interactive elements              |
| Error identification | Error messages linked to form fields via `aria-describedby` |
| Nepali language      | Full Devanagari support, bilingual labels                   |
| Touch targets        | Minimum 44×44px (48×48px for FCHV mobile)                   |

---

## 24. Error Boundaries (Added in v2.0)

Each page shell wraps its content in a React Error Boundary:

- **Page-level error boundary:** Catches render errors → shows friendly error screen with "Retry" and "Go Home" buttons
- **Module-level error boundary:** Catches errors within a section (e.g., timeline widget on dashboard) → shows "Unable to load this section" without breaking the whole page
- **Network error interceptor:** TanStack Query `onError` callback → shows toast notification for failed API calls with retry option

Error messages MUST NOT reveal internal details (OWASP error handling guidelines).

# PHR IA Coverage Matrix

**Generated from:** `products/phr/config/phr-usecase-baseline.json`  
**Last Updated:** 2026-05-26  
**Version:** 2.0.0

This document provides a human-readable view of the PHR Information Architecture (IA) coverage, mapping personas, routes, screens, backend APIs, and implementation status.

---

## Summary Statistics

| Metric | Count |
|--------|-------|
| Total Use Cases | 25 |
| Implemented | 19 |
| Partial | 3 |
| Feature-Flagged | 3 |
| MVP-Current | 17 |
| MVP-Next | 3 |
| Phase-2 | 3 |
| Deferred | 1 |
| Removed | 1 |

---

## Implementation Status by Phase

### MVP-Current (17 use cases)

| ID | Use Case | Persona | Screen | Status | Notes |
|----|----------|---------|--------|--------|-------|
| uc-patient-dashboard | Patient Dashboard | patient | DashboardPage | ✅ Implemented | Web and mobile both serve dashboard data. Mobile uses encrypted offline cache with AES-256-GCM. |
| uc-patient-records | Patient Records | patient | RecordsPage | ✅ Implemented | |
| uc-patient-profile | Patient Profile | patient | ProfilePage | ✅ Implemented | |
| uc-patient-timeline | Patient Timeline | patient | TimelinePage | ✅ Implemented | |
| uc-patient-conditions | Patient Conditions | patient | ConditionsPage | ✅ Implemented | |
| uc-patient-observations | Patient Observations | patient | ObservationsPage | ✅ Implemented | |
| uc-patient-immunizations | Patient Immunizations | patient | ImmunizationsPage | ✅ Implemented | |
| uc-patient-consent-management | Consent Management | patient | ConsentPage | ✅ Implemented | Payload uses recipientId + scope.resourceTypes (aligned with backend). Mobile endpoint aligned to /consents/grants/:grantId/revoke pattern. |
| uc-patient-labs | Patient Labs | patient | LabsPage | ✅ Implemented | |
| uc-patient-medications | Patient Medications | patient | MedicationsPage | ✅ Implemented | |
| uc-patient-documents | Patient Documents | patient | DocumentsPage | ✅ Implemented | |
| uc-patient-document-upload | Document Upload | patient | DocumentUploadPage | ✅ Implemented | |
| uc-patient-ocr-review | OCR Review | patient | OcrReviewPage | ✅ Implemented | |
| uc-patient-notifications | Patient Notifications | patient | NotificationsPage | ✅ Implemented | |
| uc-patient-settings | Patient Settings | patient | SettingsPage / SettingsScreen | ✅ Implemented | |
| uc-patient-emergency-access | Emergency Access | patient | EmergencyAccessPage / EmergencyAccessScreen | ✅ Implemented | Mobile requires documented reason + biometric gate before PHI is shown. Server policy gate needs hardening. |
| uc-admin-audit | Admin Audit | admin | AuditPage | ✅ Implemented | |
| uc-admin-release-readiness | Release Readiness | admin | ReleaseCockpitPage | ✅ Implemented | Currently parses evidence files directly. Should migrate to Kernel runtime API. |
| uc-admin-emergency-review | Emergency Review | admin | EmergencyReviewPage | ✅ Implemented | |
| uc-mobile-dashboard | Mobile Dashboard | patient | MobileDashboard | ✅ Implemented | Dedicated mobile endpoint with session-header auth (X-Tenant-Id, X-Principal-Id, X-Role). Backend route needs verification. |
| uc-error-pages | Error Pages | patient | ForbiddenPage / NotFoundPage | ✅ Implemented | |

### MVP-Next (3 use cases)

| ID | Use Case | Persona | Screen | Status | Notes |
|----|----------|---------|--------|--------|-------|
| uc-patient-record-detail | Record Detail | patient | RecordDetailPage | ⚠️ Partial | Web route exists, mobile screen needs implementation |
| uc-patient-appointments | Patient Appointments | patient | AppointmentsPage | ⚠️ Partial | Request workflow exists, full booking workflow needs implementation |

### Phase-2 (3 use cases)

| ID | Use Case | Persona | Screen | Status | Notes |
|----|----------|---------|--------|--------|-------|
| uc-provider-dashboard | Provider Dashboard | clinician | ProviderDashboardPage | 🔒 Feature-Flagged | Route exists but backend implementation incomplete. Feature-flagged until production-ready. |
| uc-provider-patients | Provider Patients | clinician | ProviderPatientsPage | 🔒 Feature-Flagged | Feature-flagged until backend consent/treatment relationship policy is implemented. |
| uc-caregiver-dependents | Caregiver Dependents | caregiver | CaregiverDependentsPage | 🔒 Feature-Flagged | Placeholder/minimal page exists. Full implementation deferred. |

### Deferred (1 use case)

| ID | Use Case | Persona | Screen | Status | Notes |
|----|----------|---------|--------|--------|-------|
| uc-fchv-dashboard | FCHV Dashboard | caregiver | FchvDashboardPage | 🔒 Feature-Flagged | Feature-flagged. May be deferred from MVP depending on Nepal healthcare priorities. |

---

## Coverage by Persona

### Patient (15 use cases)

| Use Case | Status | Phase |
|----------|--------|-------|
| Dashboard | ✅ Implemented | MVP-Current |
| Records | ✅ Implemented | MVP-Current |
| Record Detail | ⚠️ Partial | MVP-Next |
| Profile | ✅ Implemented | MVP-Current |
| Timeline | ✅ Implemented | MVP-Current |
| Conditions | ✅ Implemented | MVP-Current |
| Observations | ✅ Implemented | MVP-Current |
| Immunizations | ✅ Implemented | MVP-Current |
| Consent Management | ✅ Implemented | MVP-Current |
| Appointments | ⚠️ Partial | MVP-Next |
| Labs | ✅ Implemented | MVP-Current |
| Medications | ✅ Implemented | MVP-Current |
| Documents | ✅ Implemented | MVP-Current |
| Document Upload | ✅ Implemented | MVP-Current |
| OCR Review | ✅ Implemented | MVP-Current |
| Notifications | ✅ Implemented | MVP-Current |
| Settings | ✅ Implemented | MVP-Current |
| Emergency Access | ✅ Implemented | MVP-Current |
| Mobile Dashboard | ✅ Implemented | MVP-Current |
| Error Pages | ✅ Implemented | MVP-Current |

### Clinician / Provider (2 use cases)

| Use Case | Status | Phase |
|----------|--------|-------|
| Provider Dashboard | 🔒 Feature-Flagged | Phase-2 |
| Provider Patients | 🔒 Feature-Flagged | Phase-2 |

### Caregiver (2 use cases)

| Use Case | Status | Phase |
|----------|--------|-------|
| Caregiver Dependents | 🔒 Feature-Flagged | Phase-2 |
| FCHV Dashboard | 🔒 Feature-Flagged | Deferred |

### Admin (3 use cases)

| Use Case | Status | Phase |
|----------|--------|-------|
| Admin Audit | ✅ Implemented | MVP-Current |
| Release Readiness | ✅ Implemented | MVP-Current |
| Emergency Review | ✅ Implemented | MVP-Current |

---

## Backend API Coverage

### Fully Implemented APIs

- `GET /mobile/dashboard`
- `GET /patients/:patientId`
- `GET /patients/:patientId/history`
- `GET /patients?patientId=:id`
- `GET /clinical/observations?patientId=:id`
- `GET /clinical/medications?patientId=:id`
- `GET /clinical/conditions?patientId=:id`
- `GET /clinical/immunizations?patientId=:id`
- `GET /clinical/timeline?patientId=:id`
- `GET /clinical/labs?patientId=:id`
- `GET /consents?patientId=:id`
- `POST /consents/grants`
- `POST /consents/grants/:grantId/revoke`
- `GET /consents/check`
- `GET /appointments?patientId=:id`
- `POST /appointments`
- `POST /appointments/:appointmentId/cancel`
- `GET /documents?patientId=:id`
- `POST /documents/upload`
- `POST /documents/:docId/ocr`
- `POST /documents/:docId/ocr/accept`
- `POST /documents/:docId/ocr/reject`
- `GET /notifications?principalId=:id`
- `POST /auth/logout`
- `PUT /patients/:patientId`
- `POST /fhir/Patient/current/$export`
- `POST /emergency/access`
- `GET /emergency/events/:eventId`
- `GET /emergency/patients/:patientId`
- `GET /audit`
- `GET /audit/:eventId`
- `GET /audit/export`
- `GET /release-readiness`
- `GET /emergency/reviews/pending`
- `GET /emergency/reviews/overdue`
- `POST /emergency/reviews/:eventId`

### Feature-Flagged APIs

- `GET /provider/patients`
- `GET /provider/patients/:patientId`
- `GET /caregiver/dependents`
- `GET /caregiver/dependents/:dependentId`
- `GET /fchv/dashboard`
- `POST /fchv/patients/register`
- `GET /fchv/patients`

---

## Kernel Capability Mapping

| Capability | Use Cases | Status |
|------------|------------|--------|
| patient-record-read | Dashboard, Records, Timeline, Conditions, Observations, Immunizations, Labs, Medications, Mobile Dashboard | ✅ Implemented |
| patient-profile-manage | Profile, Settings | ✅ Implemented |
| consent-manage | Consent Management | ✅ Implemented |
| appointment-manage | Appointments | ⚠️ Partial |
| document-manage | Documents, Document Upload, OCR Review | ✅ Implemented |
| notification-read | Notifications | ✅ Implemented |
| emergency-break-glass | Emergency Access | ✅ Implemented |
| provider-dashboard | Provider Dashboard | 🔒 Feature-Flagged |
| provider-patient-search | Provider Patients | 🔒 Feature-Flagged |
| caregiver-access | Caregiver Dependents | 🔒 Feature-Flagged |
| fchv-workflow | FCHV Dashboard | 🔒 Feature-Flagged |
| audit-read | Admin Audit | ✅ Implemented |
| release-readiness | Release Readiness | ✅ Implemented |
| emergency-review | Emergency Review | ✅ Implemented |

---

## Offline Support

| Use Case | Offline Support | Notes |
|----------|-----------------|-------|
| Patient Dashboard | ✅ Yes | AES-256-GCM encrypted cache |
| Records | ❌ No | |
| Profile | ❌ No | |
| Timeline | ❌ No | |
| Conditions | ❌ No | |
| Observations | ❌ No | |
| Immunizations | ❌ No | |
| Consent Management | ❌ No | |
| Appointments | ❌ No | |
| Labs | ❌ No | |
| Medications | ❌ No | |
| Documents | ❌ No | |
| Document Upload | ❌ No | |
| OCR Review | ❌ No | |
| Notifications | ❌ No | |
| Settings | ❌ No | |
| Emergency Access | ❌ No | |
| Mobile Dashboard | ✅ Yes | AES-256-GCM encrypted cache |

---

## Known Gaps and Action Items

### High Priority

1. **Record Detail Mobile Screen** - Web route exists but mobile screen needs implementation (uc-patient-record-detail)
2. **Appointment Booking Workflow** - Request workflow exists but full booking workflow needs implementation (uc-patient-appointments)
3. **Release Readiness Kernel Migration** - Currently parses evidence files directly, should migrate to Kernel runtime API (uc-admin-release-readiness)
4. **Emergency Access Server Policy Hardening** - Server policy gate needs hardening (uc-patient-emergency-access)

### Medium Priority

1. **Provider Dashboard Backend** - Route exists but backend implementation incomplete (uc-provider-dashboard)
2. **Provider Patient Search Policy** - Backend consent/treatment relationship policy needs implementation (uc-provider-patients)
3. **Caregiver Dependents Full Implementation** - Placeholder/minimal page exists, full implementation needed (uc-caregiver-dependents)

### Low Priority / Deferred

1. **FCHV Workflows** - May be deferred from MVP depending on Nepal healthcare priorities (uc-fchv-dashboard)

---

## Legend

- ✅ **Implemented** - Fully implemented and production-ready
- ⚠️ **Partial** - Partially implemented, needs completion
- 🔒 **Feature-Flagged** - Implemented but hidden behind feature flag
- ❌ **Missing** - Not implemented
- 📋 **Deferred** - Deferred to future phase

---

**This document is auto-generated from `phr-usecase-baseline.json`. Do not edit manually. Update the JSON file instead.**

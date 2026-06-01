# PHR IA Coverage Report

**Generated:** 2026-06-01
**Baseline Version:** 2.0.0
**Total Use Cases:** 27

## Summary

| Status | Count | Percentage |
|--------|-------|------------|
| implemented | 21 | 77.8% |
| partial | 2 | 7.4% |
| deferred | 4 | 14.8% |

| Phase | Count | Percentage |
|-------|-------|------------|
| mvp-current | 21 | 77.8% |
| mvp-next | 2 | 7.4% |
| phase-2 | 3 | 11.1% |
| deferred | 1 | 3.7% |

## Implementation Status by Persona

- **patient**: 18/20 (90.0% implemented)
- **clinician**: 0/2 (0.0% implemented)
- **caregiver**: 0/2 (0.0% implemented)
- **admin**: 3/3 (100.0% implemented)

## Use Cases by Persona


### Patient

| Use Case | Screen | Route | Status | Notes |
|----------|--------|-------|--------|-------|
| uc-patient-dashboard | DashboardPage | /dashboard | implemented | Web and mobile both serve dashboard data. Mobile u... |
| uc-patient-records | RecordsPage | /records | implemented |  |
| uc-patient-record-detail | RecordDetailPage | /records/:recordId | partial | Web route exists, mobile screen needs implementati... |
| uc-patient-profile | ProfilePage | /profile | implemented |  |
| uc-patient-timeline | TimelinePage | /timeline | implemented |  |
| uc-patient-conditions | ConditionsPage | /conditions | implemented |  |
| uc-patient-observations | ObservationsPage | /observations | implemented |  |
| uc-patient-immunizations | ImmunizationsPage | /immunizations | implemented |  |
| uc-patient-consent-management | ConsentPage | /consents | implemented | Payload uses recipientId + scope.resourceTypes (al... |
| uc-patient-appointments | AppointmentsPage | /appointments | partial | Request workflow exists, full booking workflow nee... |
| uc-patient-labs | LabsPage | /labs | implemented |  |
| uc-patient-medications | MedicationsPage | /medications | implemented |  |
| uc-patient-documents | DocumentsPage | /documents | implemented |  |
| uc-patient-document-upload | DocumentUploadPage | /documents/upload | implemented |  |
| uc-patient-ocr-review | OcrReviewPage | /documents/:docId/ocr | implemented |  |
| uc-patient-notifications | NotificationsPage | /notifications | implemented |  |
| uc-patient-settings | SettingsPage / SettingsScreen | /settings | implemented |  |
| uc-patient-emergency-access | EmergencyAccessPage / EmergencyAccessScreen | /emergency | implemented | Mobile requires documented reason + biometric gate... |
| uc-mobile-dashboard | MobileDashboard | /mobile/dashboard | implemented | Dedicated mobile endpoint with session-header auth... |
| uc-error-pages | ForbiddenPage / NotFoundPage | /forbidden, /not-found | implemented |  |


### Clinician

| Use Case | Screen | Route | Status | Notes |
|----------|--------|-------|--------|-------|
| uc-provider-dashboard | ProviderDashboardPage | /provider/dashboard | deferred | Route exists in the canonical contract as hidden u... |
| uc-provider-patients | ProviderPatientsPage | /provider/patients | deferred | Route exists in the canonical contract as hidden u... |


### Caregiver

| Use Case | Screen | Route | Status | Notes |
|----------|--------|-------|--------|-------|
| uc-caregiver-dependents | CaregiverDependentsPage | /caregiver/dependents | deferred | Route exists in the canonical contract as hidden u... |
| uc-fchv-dashboard | FchvDashboardPage | /fchv/dashboard | deferred | Route exists in the canonical contract as hidden a... |


### Admin

| Use Case | Screen | Route | Status | Notes |
|----------|--------|-------|--------|-------|
| uc-admin-audit | AuditPage | /audit | implemented |  |
| uc-admin-release-readiness | ReleaseCockpitPage | /release-readiness | implemented | Currently parses evidence files directly. Should m... |
| uc-admin-emergency-review | EmergencyReviewsPage | /emergency/reviews | implemented |  |


## Use Cases by Status


### Implemented (21)

- **uc-patient-dashboard** (patient): DashboardPage - /dashboard
- **uc-patient-records** (patient): RecordsPage - /records
- **uc-patient-profile** (patient): ProfilePage - /profile
- **uc-patient-timeline** (patient): TimelinePage - /timeline
- **uc-patient-conditions** (patient): ConditionsPage - /conditions
- **uc-patient-observations** (patient): ObservationsPage - /observations
- **uc-patient-immunizations** (patient): ImmunizationsPage - /immunizations
- **uc-patient-consent-management** (patient): ConsentPage - /consents
- **uc-patient-labs** (patient): LabsPage - /labs
- **uc-patient-medications** (patient): MedicationsPage - /medications
- **uc-patient-documents** (patient): DocumentsPage - /documents
- **uc-patient-document-upload** (patient): DocumentUploadPage - /documents/upload
- **uc-patient-ocr-review** (patient): OcrReviewPage - /documents/:docId/ocr
- **uc-patient-notifications** (patient): NotificationsPage - /notifications
- **uc-patient-settings** (patient): SettingsPage / SettingsScreen - /settings
- **uc-patient-emergency-access** (patient): EmergencyAccessPage / EmergencyAccessScreen - /emergency
- **uc-admin-audit** (admin): AuditPage - /audit
- **uc-admin-release-readiness** (admin): ReleaseCockpitPage - /release-readiness
- **uc-admin-emergency-review** (admin): EmergencyReviewsPage - /emergency/reviews
- **uc-mobile-dashboard** (patient): MobileDashboard - /mobile/dashboard
- **uc-error-pages** (patient): ForbiddenPage / NotFoundPage - /forbidden, /not-found


### Partial (2)

- **uc-patient-record-detail** (patient): RecordDetailPage - /records/:recordId
- **uc-patient-appointments** (patient): AppointmentsPage - /appointments


### Deferred (4)

- **uc-provider-dashboard** (clinician): ProviderDashboardPage - /provider/dashboard
- **uc-provider-patients** (clinician): ProviderPatientsPage - /provider/patients
- **uc-caregiver-dependents** (caregiver): CaregiverDependentsPage - /caregiver/dependents
- **uc-fchv-dashboard** (caregiver): FchvDashboardPage - /fchv/dashboard


## Kernel Capability Mapping

| Use Case | Kernel Capability |
|----------|-------------------|
| uc-patient-dashboard | patient-record-read |
| uc-patient-records | patient-record-read |
| uc-patient-record-detail | patient-record-read |
| uc-patient-profile | patient-profile-manage |
| uc-patient-timeline | patient-record-read |
| uc-patient-conditions | patient-record-read |
| uc-patient-observations | patient-record-read |
| uc-patient-immunizations | patient-record-read |
| uc-patient-consent-management | consent-manage |
| uc-patient-appointments | appointment-manage |
| uc-patient-labs | patient-record-read |
| uc-patient-medications | patient-record-read |
| uc-patient-documents | document-manage |
| uc-patient-document-upload | document-manage |
| uc-patient-ocr-review | document-manage |
| uc-patient-notifications | notification-read |
| uc-patient-settings | patient-profile-manage |
| uc-patient-emergency-access | emergency-break-glass |
| uc-provider-dashboard | provider-dashboard |
| uc-provider-patients | provider-patient-search |
| uc-caregiver-dependents | caregiver-access |
| uc-fchv-dashboard | fchv-workflow |
| uc-admin-audit | audit-read |
| uc-admin-release-readiness | release-readiness |
| uc-admin-emergency-review | emergency-review |
| uc-mobile-dashboard | patient-record-read |

## Backend API Coverage


### uc-patient-dashboard

- GET /api/v1/mobile/dashboard
- GET /api/v1/records/:patientId
- GET /api/v1/clinical/observations?patientId=:id
- GET /api/v1/clinical/medications?patientId=:id
- GET /api/v1/consents?patientId=:id


### uc-patient-records

- GET /api/v1/records/:patientId
- GET /api/v1/records/:patientId/history
- GET /api/v1/records?patientId=:id


### uc-patient-record-detail

- GET /api/v1/records/:patientId


### uc-patient-profile

- GET /api/v1/records/:patientId
- PUT /api/v1/records/:patientId


### uc-patient-timeline

- GET /api/v1/records/:patientId/records?category=timeline


### uc-patient-conditions

- GET /api/v1/clinical/conditions?patientId=:id


### uc-patient-observations

- GET /api/v1/clinical/observations?patientId=:id


### uc-patient-immunizations

- GET /api/v1/clinical/immunizations?patientId=:id


### uc-patient-consent-management

- GET /api/v1/consents?patientId=:id
- POST /api/v1/consents/grants
- POST /api/v1/consents/grants/:grantId/revoke
- GET /api/v1/consents/check


### uc-patient-appointments

- GET /api/v1/appointments?patientId=:id
- POST /api/v1/appointments
- POST /api/v1/appointments/:appointmentId/cancel


### uc-patient-labs

- GET /api/v1/clinical/labs?patientId=:id


### uc-patient-medications

- GET /api/v1/clinical/medications?patientId=:id


### uc-patient-documents

- GET /api/v1/records/documents
- POST /api/v1/records/documents


### uc-patient-document-upload

- POST /api/v1/records/documents


### uc-patient-ocr-review

- GET /api/v1/records/documents/:docId/ocr
- POST /api/v1/records/documents/:docId/ocr/confirm
- POST /api/v1/records/documents/:docId/ocr/reject


### uc-patient-notifications

- GET /api/v1/notifications?principalId=:id


### uc-patient-settings

- POST /api/v1/auth/logout
- PUT /api/v1/profile/settings
- POST /api/v1/hie/export


### uc-patient-emergency-access

- POST /api/v1/emergency/access
- GET /api/v1/emergency/events/:eventId
- GET /api/v1/emergency/patients/:patientId


### uc-provider-dashboard

- GET /api/v1/provider/patients


### uc-provider-patients

- GET /api/v1/provider/patients
- GET /api/v1/provider/patients/:patientId


### uc-caregiver-dependents

- GET /api/v1/caregiver/dependents
- GET /api/v1/caregiver/dependents/:dependentId


### uc-fchv-dashboard

- GET /api/v1/fchv/dashboard
- POST /api/v1/fchv/patients/register
- GET /api/v1/fchv/patients


### uc-admin-audit

- GET /api/v1/audit/events
- GET /api/v1/audit/events/:eventId
- GET /api/v1/audit/events/export


### uc-admin-release-readiness

- GET /api/v1/release-readiness


### uc-admin-emergency-review

- GET /api/v1/emergency/reviews/pending
- GET /api/v1/emergency/reviews/overdue
- POST /api/v1/emergency/reviews/:eventId


### uc-mobile-dashboard

- GET /api/v1/mobile/dashboard


## Offline Support

- **uc-patient-dashboard** (patient): Offline support enabled
- **uc-mobile-dashboard** (patient): Offline support enabled

---

*This document is auto-generated from `products/phr/config/phr-usecase-baseline.json`*

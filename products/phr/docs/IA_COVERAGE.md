# PHR IA Coverage Report

**Generated:** 2026-05-27  
**Baseline Version:** 2.0.0  
**Total Use Cases:** 27

## Summary

| Status | Count | Percentage |
|--------|-------|------------|
| implemented | 21 | 77.8% |
| partial | 2 | 7.4% |
| feature_flagged | 4 | 14.8% |

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
| uc-provider-dashboard | ProviderDashboardPage | /provider/dashboard | feature_flagged | Route exists but backend implementation incomplete... |
| uc-provider-patients | ProviderPatientsPage | /provider/patients | feature_flagged | Feature-flagged until backend consent/treatment re... |


### Caregiver

| Use Case | Screen | Route | Status | Notes |
|----------|--------|-------|--------|-------|
| uc-caregiver-dependents | CaregiverDependentsPage | /caregiver/dependents | feature_flagged | Placeholder/minimal page exists. Full implementati... |
| uc-fchv-dashboard | FchvDashboardPage | /fchv/dashboard | feature_flagged | Feature-flagged. May be deferred from MVP dependin... |


### Admin

| Use Case | Screen | Route | Status | Notes |
|----------|--------|-------|--------|-------|
| uc-admin-audit | AuditPage | /audit | implemented |  |
| uc-admin-release-readiness | ReleaseCockpitPage | /release-readiness | implemented | Currently parses evidence files directly. Should m... |
| uc-admin-emergency-review | EmergencyReviewPage | /emergency/reviews | implemented |  |


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
- **uc-admin-emergency-review** (admin): EmergencyReviewPage - /emergency/reviews
- **uc-mobile-dashboard** (patient): MobileDashboard - /mobile/dashboard
- **uc-error-pages** (patient): ForbiddenPage / NotFoundPage - /forbidden, /not-found


### Partial (2)

- **uc-patient-record-detail** (patient): RecordDetailPage - /records/:recordId
- **uc-patient-appointments** (patient): AppointmentsPage - /appointments


### Feature_flagged (4)

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

- GET /mobile/dashboard
- GET /patients/:patientId
- GET /clinical/observations?patientId=:id
- GET /clinical/medications?patientId=:id
- GET /consents?patientId=:id


### uc-patient-records

- GET /patients/:patientId
- GET /patients/:patientId/history
- GET /patients?patientId=:id


### uc-patient-record-detail

- GET /patients/:patientId


### uc-patient-profile

- GET /patients/:patientId
- PUT /patients/:patientId


### uc-patient-timeline

- GET /clinical/timeline?patientId=:id


### uc-patient-conditions

- GET /clinical/conditions?patientId=:id


### uc-patient-observations

- GET /clinical/observations?patientId=:id


### uc-patient-immunizations

- GET /clinical/immunizations?patientId=:id


### uc-patient-consent-management

- GET /consents?patientId=:id
- POST /consents/grants
- POST /consents/:grantId/revoke
- GET /consents/check


### uc-patient-appointments

- GET /appointments?patientId=:id
- POST /appointments
- POST /appointments/:appointmentId/cancel


### uc-patient-labs

- GET /clinical/labs?patientId=:id


### uc-patient-medications

- GET /clinical/medications?patientId=:id


### uc-patient-documents

- GET /documents?patientId=:id
- POST /documents/upload


### uc-patient-document-upload

- POST /documents/upload


### uc-patient-ocr-review

- POST /documents/:docId/ocr
- POST /documents/:docId/ocr/accept
- POST /documents/:docId/ocr/reject


### uc-patient-notifications

- GET /notifications?principalId=:id


### uc-patient-settings

- POST /auth/logout
- PUT /patients/:patientId
- POST /fhir/Patient/current/$export


### uc-patient-emergency-access

- POST /emergency/access
- GET /emergency/events/:eventId
- GET /emergency/patients/:patientId


### uc-provider-dashboard

- GET /provider/patients


### uc-provider-patients

- GET /provider/patients
- GET /provider/patients/:patientId


### uc-caregiver-dependents

- GET /caregiver/dependents
- GET /caregiver/dependents/:dependentId


### uc-fchv-dashboard

- GET /fchv/dashboard
- POST /fchv/patients/register
- GET /fchv/patients


### uc-admin-audit

- GET /audit
- GET /audit/:eventId
- GET /audit/export


### uc-admin-release-readiness

- GET /release-readiness


### uc-admin-emergency-review

- GET /emergency/reviews/pending
- GET /emergency/reviews/overdue
- POST /emergency/reviews/:eventId


### uc-mobile-dashboard

- GET /mobile/dashboard


## Offline Support

- **uc-patient-dashboard** (patient): Offline support enabled
- **uc-mobile-dashboard** (patient): Offline support enabled

---

*This document is auto-generated from `products/phr/config/phr-usecase-baseline.json`*

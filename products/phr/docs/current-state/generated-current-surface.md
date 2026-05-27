# PHR Current Implementation Surface

**Generated:** 2026-05-27T19:30:49.296Z

This document describes the current implementation surface of the PHR product,
including web routes, mobile screens, backend APIs, and their implementation status.

---

## Summary

- **Total Use Cases:** 27
- **Fully Implemented:** 21 (78%)
- **Partial Implementation:** 2 (7%)
- **Feature-Flagged:** 4 (15%)
- **Web Routes:** 27

---

## Web Routes

The following routes are currently implemented in the PHR web application:

| Path | Label | Minimum Role | Status |
|------|-------|--------------|--------|
| /dashboard | route.dashboard.label | patient | implemented |
| /records | route.records.label | patient | implemented |
| /consents | route.consents.label | patient | implemented |
| /appointments | route.appointments.label | patient | partial |
| /labs | route.labs.label | patient | implemented |
| /medications | route.medications.label | patient | implemented |
| /emergency | route.emergency.label | patient | implemented |
| /emergency/reviews | route.emergency.label | patient | unknown |
| /release-readiness | route.releaseReadiness.label | patient | implemented |
| /audit | route.audit.label | patient | implemented |
| /settings | route.settings.label | patient | implemented |
| /records/:recordId | route.recordDetail.label | patient | partial |
| /profile | route.profile.label | patient | implemented |
| /timeline | route.timeline.label | patient | implemented |
| /conditions | route.conditions.label | patient | implemented |
| /observations | route.observations.label | patient | implemented |
| /immunizations | route.immunizations.label | patient | implemented |
| /documents | route.documents.label | patient | implemented |
| /documents/upload | route.documents.upload.label | patient | implemented |
| /documents/:docId/ocr | route.ocr.label | patient | implemented |
| /notifications | route.notifications.label | patient | implemented |
| /forbidden | route.forbidden.label | patient | unknown |
| /not-found | route.notFound.label | patient | unknown |
| /provider/dashboard | route.provider.dashboard.label | patient | feature_flagged |
| /provider/patients | route.provider.patients.label | patient | feature_flagged |
| /caregiver/dependents | route.caregiver.dependents.label | patient | feature_flagged |
| /fchv/dashboard | route.fchv.dashboard.label | patient | feature_flagged |

---

## Mobile Screens

The following screens are currently implemented in the PHR mobile application:

| Screen | Status | Offline Support |
|--------|--------|----------------|
| DashboardScreen | implemented | Yes |
| RecordsScreen | implemented | Yes |
| RecordDetailScreen | implemented | Yes |
| ConsentScreen | implemented | No |
| NotificationsScreen | implemented | Yes |
| EmergencyAccessScreen | implemented | No |
| SettingsScreen | implemented | No |
| LoginScreen | implemented | No |

---

## Backend APIs

The following backend API endpoints are currently implemented:

| Endpoint | Method | Purpose |
|----------|--------|---------|
| /auth/login | POST | Session bootstrap via credentials |
| /auth/logout | POST | Session termination |
| /auth/me | GET | Current session validation |
| /route-entitlements | GET | Route/content entitlement payload |
| /release-readiness | GET | Admin release readiness runtime truth |
| /audit/events | GET | Paginated audit event trail |
| /emergency/access | POST | Log emergency break-glass access |
| /emergency/reviews | GET | Retrieve emergency access reviews |

---

## Implementation Status by Persona

### Patient

- **Total Use Cases:** 20
- **Implemented:** 18 (90%)

### Clinician

- **Total Use Cases:** 2
- **Implemented:** 0 (0%)

### Caregiver

- **Total Use Cases:** 2
- **Implemented:** 0 (0%)

### Admin

- **Total Use Cases:** 3
- **Implemented:** 3 (100%)

### Fchv

- **Total Use Cases:** 0
- **Implemented:** 0 (0%)

---

*This document is auto-generated. Do not edit manually.*
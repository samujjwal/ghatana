# PHR Current Implementation Surface

**Generated:** 2026-05-28T19:35:13.439Z

This document describes the current implementation surface of the PHR product,
including web routes, mobile screens, backend APIs, and their implementation status.

---

## Summary

- **Total Use Cases:** 27
- **Fully Implemented:** 23 (85%)
- **Partial Implementation:** 0 (0%)
- **Deferred:** 4 (15%)
- **Web Routes:** 27
- **Hidden Web Routes:** 4

---

## Web Routes

The following routes are currently implemented in the PHR web application:

| Path | Label | Minimum Role | Route State | Use-Case Status |
|------|-------|--------------|-------------|-----------------|
| /dashboard | Dashboard | patient | stable | implemented |
| /records | Records | patient | stable | implemented |
| /consents | Consents | patient | stable | implemented |
| /appointments | Appointments | patient | stable | implemented |
| /settings | Settings | patient | stable | implemented |
| /labs | Labs | caregiver | stable | implemented |
| /medications | Medications | caregiver | stable | implemented |
| /conditions | Conditions | patient | stable | implemented |
| /observations | Observations | caregiver | stable | implemented |
| /immunizations | Immunizations | patient | stable | implemented |
| /documents | Documents | patient | stable | implemented |
| /documents/upload | Document Upload | patient | stable | implemented |
| /documents/:docId/ocr | OCR Review | patient | stable | implemented |
| /timeline | Timeline | patient | stable | implemented |
| /profile | Profile | patient | stable | implemented |
| /records/:recordId | Record Detail | patient | stable | implemented |
| /notifications | Notifications | patient | stable | implemented |
| /forbidden | Forbidden | patient | stable | unknown |
| /not-found | Not Found | patient | stable | unknown |
| /emergency | Emergency | clinician | stable | implemented |
| /emergency/reviews | Emergency Reviews | admin | stable | unknown |
| /release-readiness | Release Readiness | admin | stable | implemented |
| /audit | Audit | admin | stable | implemented |
| /provider/dashboard | Provider Dashboard | clinician | hidden | deferred |
| /provider/patients | Provider Patients | clinician | hidden | deferred |
| /caregiver/dependents | Caregiver Dependents | caregiver | hidden | deferred |
| /fchv/dashboard | FCHV Dashboard | fchv | hidden | deferred |

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
- **Implemented:** 20 (100%)

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
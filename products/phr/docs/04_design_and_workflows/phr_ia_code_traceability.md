# PHR IA Code Traceability

**Document type:** Design and Workflow  
**Layer:** Product  
**Last updated:** 2026-05-02  
**Purpose:** Maps every IA (Information Architecture) item — screens, routes, features — to implementing files, APIs, tests, and known gaps.

---

## 1. Web App: Pages and Routes

| IA Item | Screen / Page | Web Route | Backend Route File | Backend Endpoints | Web Test | Backend Test | Gap / Notes |
|---|---|---|---|---|---|---|---|
| Authentication — Login | `LoginPage.tsx` | `/login` | `PhrAuthRoutes.java` | `POST /auth/login` | `__tests__/LoginPage.test.tsx` | `PhrAuthRoutesTest.java` | — |
| Authentication — Logout | `SettingsPage.tsx` | `/settings` | `PhrAuthRoutes.java` | `POST /auth/logout` | `__tests__/SettingsPage.test.tsx` | `PhrAuthRoutesTest.java` | — |
| Dashboard — Patient | `DashboardPage.tsx` | `/dashboard` | `PhrFhirRoutes.java`, `PhrDashboardRoutes.java` | `GET /fhir/Patient`, `GET /fhir/Observation`, `GET /fhir/MedicationRequest`, `GET /fhir/Consent`, `GET /fhir/Appointment` | `__tests__/DashboardPage.test.tsx` | `PhrFhirRoutesTest.java` | — |
| Records — Summary | `RecordsPage.tsx` | `/records` | `PhrPatientRecordRoutes.java` | `GET /patients/:id`, `GET /patients/:id/history` | `__tests__/RecordsPage.test.tsx` | `PhrPatientRecordRoutesTest.java` | — |
| Records — Detail | `RecordDetailPage.tsx` | `/records/:id` | `PhrPatientRecordRoutes.java` | `GET /patients/:id/history/:recordId` | — | — | ⚠️ Missing web test |
| Records — Timeline | `TimelinePage.tsx` | `/timeline` | `PhrTimelineRoutes.java` | `GET /timeline/:principalId` | — | `PhrTimelineRoutesTest.java` | ⚠️ Missing web test |
| Clinical — Conditions | `ConditionsPage.tsx` | `/conditions` | `PhrConditionRoutes.java` | `GET /conditions/:principalId` | — | `PhrConditionRoutesTest.java` | ⚠️ Missing web test |
| Clinical — Observations | `ObservationsPage.tsx` | `/observations` | `PhrClinicalRoutes.java` | `GET /clinical/labs/observations` | — | `PhrClinicalRoutesTest.java` | ⚠️ Missing web test |
| Clinical — Labs | `LabsPage.tsx` | `/labs` | `PhrClinicalRoutes.java` | `GET /clinical/labs/observations` | — | — | ⚠️ Missing web + backend test |
| Clinical — Medications | `MedicationsPage.tsx` | `/medications` | `PhrClinicalRoutes.java` | `GET /clinical/medications` | — | — | ⚠️ Missing web + backend test |
| Clinical — Immunizations | `ImmunizationsPage.tsx` | `/immunizations` | `PhrClinicalRoutes.java` | `GET /clinical/immunizations` | — | — | ⚠️ Missing web + backend test |
| Documents — List | `DocumentsPage.tsx` | `/documents` | `PhrDocumentImagingRoutes.java` | `GET /documents` | — | — | ⚠️ Missing web + backend test |
| Documents — Upload | `DocumentUploadPage.tsx` | `/documents/upload` | `PhrDocumentImagingRoutes.java` | `POST /documents` | — | — | ⚠️ Missing web + backend test |
| Documents — OCR Review | `OcrReviewPage.tsx` | `/documents/:id/ocr` | `PhrDocumentImagingRoutes.java` | `GET /documents/:id/ocr`, `POST /documents/:id/ocr/confirm` | — | — | ⚠️ Missing web + backend test |
| Appointments | `AppointmentsPage.tsx` | `/appointments` | `PhrClinicalRoutes.java` | `POST /appointments/request` | — | — | ⚠️ Missing web + backend test |
| Consents | `ConsentPage.tsx` | `/consents` | `PhrConsentRoutes.java` | `GET /consents`, `POST /consents/grants`, `POST /consents/grants/:id/revoke`, `GET /consents/check` | `__tests__/ConsentPage.test.tsx` | `PhrConsentRoutesTest.java` | — |
| Alerts / Notifications | `NotificationsPage.tsx` | `/alerts` | `PhrClinicalRoutes.java` | `GET /notifications` | — | — | ⚠️ Missing web + backend test |
| Emergency Access | `EmergencyAccessPage.tsx` | `/emergency` | `PhrEmergencyRoutes.java` | `POST /emergency/access` | `__tests__/EmergencyAccessPage.test.tsx` | `PhrEmergencyRoutesTest.java` | — |
| Patient Profile | `ProfilePage.tsx` | `/profile` | `PhrPatientProfileRoutes.java` | `GET /profile`, `PUT /profile` | — | `PhrPatientProfileRoutesTest.java` | ⚠️ Missing web test |
| Settings | `SettingsPage.tsx` | `/settings` | `PhrAuthRoutes.java`, `PhrAdministrativeRoutes.java` | `POST /auth/logout`, `POST /fhir/Patient/current/$export` | `__tests__/SettingsPage.test.tsx` | — | — |
| Route State Diagnostics | `phrRouteElements.tsx` | `/forbidden`, `/not-found` | `PhrEntitlementRoutes.java` | `GET /api/v1/route-entitlements` | `route-access.test.ts` | `PhrHttpServerTest.java` | Hidden routes render not-found; blocked routes render forbidden |
| Forbidden | `ForbiddenPage.tsx` | `/403` | — | — | — | — | Client-side error boundary |
| Not Found | `NotFoundPage.tsx` | `/404` | — | — | — | — | Client-side error boundary |
| Audit Log (patient) | `AuditPage.tsx` | `/audit` | `PhrAuditRoutes.java` | `GET /audit/events` | — | `PhrAuditRoutesTest.java` | ⚠️ Missing web test |
| Provider — Dashboard | `ProviderDashboardPage.tsx` | `/provider` | `PhrProviderRoutes.java` | `GET /provider/patients` | `__tests__/ProviderDashboardPage.test.tsx` | `PhrProviderRoutesTest.java` | — |
| Provider — Patient Roster | `ProviderPatientsPage.tsx` | `/provider/patients` | `PhrProviderRoutes.java` | `GET /provider/patients` | — | `PhrProviderRoutesTest.java` | ⚠️ Missing web test |
| Caregiver — Dependents | `CaregiverDependentsPage.tsx` | `/caregiver` | `PhrCaregiverRoutes.java` | `GET /caregiver/dependents` | `__tests__/CaregiverDashboardPage.test.tsx` | `PhrCaregiverRoutesTest.java` | — |
| FCHV — Dashboard | `FchvDashboardPage.tsx` | `/fchv` | `PhrFchvRoutes.java` | `GET /fchv/dashboard` | `__tests__/FchvDashboardPage.test.tsx` | `PhrFchvRoutesTest.java` | — |
| Admin — Emergency Review | `EmergencyReviewPage.tsx`* | `/admin/emergency` | `PhrEmergencyRoutes.java` | `GET /emergency/reviews/pending`, `POST /emergency/reviews/:eventId` | `__tests__/EmergencyReviewPage.test.tsx` | `PhrEmergencyRoutesTest.java` | *Component exists inside EmergencyAccessPage |
| Admin — Release Readiness | `ReleaseCockpitPage.tsx` | `/admin/release` | `PhrReleaseReadinessRoutes.java` | `GET /release-readiness` | `__tests__/ReleaseCockpitPage.test.tsx` | `PhrReleaseReadinessRoutesTest.java` | — |
| Admin — Entitlements | — | `/admin/entitlements` | `PhrEntitlementRoutes.java` | `GET /entitlements`, `POST /entitlements` | — | — | ⚠️ No web UI yet; backend routes exist |

---

## 2. Mobile App: Screens

| IA Item | Mobile Screen | Backend Endpoint | Test | Gap / Notes |
|---|---|---|---|---|
| Authentication — Login | `LoginScreen.tsx` | `POST /auth/login` | `screens/__tests__/LoginScreen.test.tsx` | — |
| Dashboard | `DashboardScreen.tsx` | `GET /mobile/dashboard` | — | ⚠️ Missing mobile screen test |
| Records — Summary | `RecordsScreen.tsx` | `GET /patients/:id/history` | — | ⚠️ Missing mobile screen test |
| Records — Detail | `RecordDetailScreen.tsx` | `GET /patients/:id/history/:recordId` | — | ⚠️ Missing mobile screen test |
| Consents | `ConsentScreen.tsx` | `GET /consents`, `POST /consents/grants` | — | ⚠️ Missing mobile screen test |
| Notifications | `NotificationsScreen.tsx` | `GET /notifications` | — | ⚠️ Missing mobile screen test |
| Emergency Access | `EmergencyAccessScreen.tsx` | `POST /emergency/access` | — | ⚠️ Missing mobile screen test; biometric gate implemented in `App.tsx` |
| Settings | `SettingsScreen.tsx` | `POST /auth/logout` | — | ⚠️ Missing mobile screen test |

---

## 3. Platform Services

| Service File | Responsibility | Used by | Test |
|---|---|---|---|
| `services/phiEncryptedStorage.ts` | AES-256-GCM offline PHI cache | `App.tsx`, `services/offlineStore.ts` | `services/__tests__/offlineStore.test.ts` |
| `services/offlineStore.ts` | Offline cache read/write | `App.tsx` | `services/__tests__/offlineStore.test.ts` |
| `services/pushNotifications.ts` | Push notification registration + PHI redaction | `App.tsx` | `services/__tests__/pushNotifications.test.ts` |
| `services/phrMobileApi.ts` | Mobile API client | `App.tsx`, mobile screens | — |
| `i18n/phrMobileI18n.ts` | i18n resolution for mobile | All mobile screens | `i18n/__tests__/phrMobileI18n.test.ts` |
| `api/phrApi.ts` (web) | Web API client + Zod validation | All web pages | `api/__tests__/phrApi.test.ts` |
| `PhrRouteSupport.java` | Auth context validation (`requireContext()`) | All backend route handlers | `PhrRouteSupportTest.java` |

---

## 4. Backend Infrastructure

| File | Responsibility |
|---|---|
| `PhrHttpServer.java` | ActiveJ HTTP server wiring; mounts all route adapters |
| `PhrModule.java` | Guice/DI module for all PHR services |
| `PhrRouteSupport.java` | Shared route utilities; `requireContext()`, `isPrivileged()` |

---

## 5. Known Gaps and Backlog

| Priority | Item | Type | Notes |
|---|---|---|---|
| P0 | `PhrRouteSupport.isPrivileged` | Policy | Replace stub/placeholder logic with RBAC policy evaluator |
| P1 | Mobile screen tests | Testing | All 8 mobile screens lack co-located test files |
| P1 | Web page tests | Testing | 15 pages lack `__tests__/` test files |
| P1 | Backend consent enforcement matrix test | Testing | Integration test for all 5 consent access scenarios |
| P1 | Emergency break-glass E2E test | Testing | End-to-end: login → emergency access → admin review |
| P2 | Offline audit queue | Feature | Emit cached PHI access events on reconnect |
| P2 | Entitlement management web UI | Feature | Backend routes exist; no frontend screen |
| P2 | Key rotation for mobile PHI cache | Feature | Annual AES key rotation with re-encryption |
| P2 | Admin consent revocation notification | Feature | Patient notification on admin-initiated revocation |

# PHR — Current Implemented Surface

> **Auto-generated documentation.** This document describes the actual implemented surface of the PHR product as observed in source code, not the aspirational vision. Last updated: 2026-05-02.
>
> For the aspirational roadmap see [`01-vision-plan-requirements/01-product-vision.md`](../01-vision-plan-requirements/01-product-vision.md).
> For the full implementation backlog see [`platform-kernel/docs/01-KERNEL_IMPLEMENTATION_PLAN.md`](../../../../platform-kernel/docs/01-KERNEL_IMPLEMENTATION_PLAN.md).

---

## 1. Web Application

**Stack**: React 19 + Vite · TypeScript strict · TanStack Query · React Router v6 · Tailwind CSS  
**Location**: `products/phr/apps/web/`

### 1.1 Routes — Implemented (non-feature-flagged)

| Path | Page Component | Personas | Status |
|------|---------------|----------|--------|
| `/login` | `LoginPage.tsx` | all | Implemented |
| `/dashboard` | `DashboardPage.tsx` | patient, caregiver, clinician, admin | Implemented |
| `/records` | `RecordsPage.tsx` | patient, caregiver, clinician, admin | Implemented |
| `/records/:recordId` | `RecordDetailPage.tsx` | patient, caregiver, clinician, admin | Implemented |
| `/consents` | `ConsentPage.tsx` | patient, caregiver, clinician, admin | Implemented |
| `/appointments` | `AppointmentsPage.tsx` | patient, caregiver, clinician, admin | Implemented |
| `/labs` | `LabsPage.tsx` | caregiver, clinician, admin | Implemented |
| `/medications` | `MedicationsPage.tsx` | caregiver, clinician, admin | Implemented |
| `/emergency` | `EmergencyAccessPage.tsx` | clinician, admin | Implemented |
| `/release-readiness` | `ReleaseCockpitPage.tsx` | admin | Implemented |
| `/audit` | `AuditPage.tsx` | admin | Implemented |
| `/settings` | `SettingsPage.tsx` | all | Implemented |
| `/profile` | `ProfilePage.tsx` | all | Implemented |
| `/timeline` | `TimelinePage.tsx` | all | Implemented |
| `/conditions` | `ConditionsPage.tsx` | all | Implemented |
| `/observations` | `ObservationsPage.tsx` | caregiver, clinician, admin | Implemented |
| `/immunizations` | `ImmunizationsPage.tsx` | all | Implemented |
| `/documents` | `DocumentsPage.tsx` | all | Implemented |
| `/documents/upload` | `DocumentUploadPage.tsx` | all | Implemented |
| `/documents/:docId/ocr` | `OcrReviewPage.tsx` | all | Implemented |
| `/notifications` | `NotificationsPage.tsx` | all | Implemented |
| `/forbidden` | `ForbiddenPage.tsx` | all | Implemented |
| `/not-found` | `NotFoundPage.tsx` | all | Implemented |

### 1.2 Routes — Feature-Flagged (render FeatureFlagPage placeholder)

| Path | Intended Page | Min Role | Feature Flag |
|------|--------------|----------|--------------|
| `/provider/dashboard` | `ProviderDashboardPage.tsx` | clinician | `featureFlag: true` |
| `/provider/patients` | `ProviderPatientsPage.tsx` | clinician | `featureFlag: true` |
| `/caregiver/dependents` | `CaregiverDependentsPage.tsx` | caregiver | `featureFlag: true` |
| `/fchv/dashboard` | `FchvDashboardPage.tsx` | clinician | `featureFlag: true` |

### 1.3 Missing Routes (in IA, not yet implemented)

| Path | Planned Page | Status |
|------|-------------|--------|
| `/medications/:medId` | `MedicationDetailPage.tsx` | Not implemented |
| `/referrals` | — | Not implemented |
| `/imaging` | — | Not implemented |
| `/insurance` | — | Not implemented |
| `/payments` | — | Not implemented |
| `/claims` | — | Not implemented |

### 1.4 API Client

**Location**: `products/phr/apps/web/src/api/phrApi.ts`

Implemented API calls include: dashboard, records, conditions, appointments, labs, medications, immunizations, observations, consents, documents (list/upload/OCR), notifications, audit events, emergency access, release readiness, profile, provider dashboard, provider patients, caregiver dependents, FCHV dashboard.

### 1.5 Auth / Session

- `PhrSessionContext.tsx`: stores `PhrSession` in `window.sessionStorage['phr.session']`; includes session expiry timer via `useEffect`
- `PhrAccessContext.tsx`: role-based access evaluator
- `phrRouteContracts.ts`: route manifest with persona and tier metadata
- Session expiry auto-redirects to `/login`
- Secure logout invokes backend `/auth/logout` and clears session storage

### 1.6 i18n

- Mechanism: `phrI18n.ts` with `t(key, values?)` interpolation
- Locales: English (`en`) and Nepali (`ne`) under `src/locales/`
- All page components use `t()` for user-visible text
- Pseudo-locale `en-XA` for i18n testing

### 1.7 Test Coverage

- **85 unit tests across 15 Vitest test files** (confirmed passing)
- Test files co-located in `src/__tests__/` and `src/pages/__tests__/`
- No E2E tests yet (Playwright not yet wired for PHR)

---

## 2. Mobile Application

**Stack**: React Native + Expo · TypeScript strict  
**Location**: `products/phr/apps/mobile/`

### 2.1 Screens — Implemented

| Screen Key | Component | Status |
|-----------|-----------|--------|
| `dashboard` | `DashboardScreen.tsx` | Implemented |
| `records` | `RecordsScreen.tsx` | Implemented |
| `consents` | `ConsentScreen.tsx` | Implemented |
| `notifications` | `NotificationsScreen.tsx` | Implemented |
| `emergency` | `EmergencyAccessScreen.tsx` | Implemented (TextInput fix, full i18n) |
| `settings` | `SettingsScreen.tsx` | Implemented |

### 2.2 Services — Implemented

| Service | File | Notes |
|---------|------|-------|
| Mobile API | `phrMobileApi.ts` | Session headers on all calls (X-Tenant-Id, X-Principal-Id, X-Role, X-Correlation-ID) |
| Biometric auth | `biometricAuth.ts` | Implemented |
| Offline store | `offlineStore.ts` | AES-256-GCM via `phiEncryptedStorage.ts` |
| PHI encrypted storage | `phiEncryptedStorage.ts` | AES-256-GCM, key in OS keychain via expo-secure-store |
| Mobile session store | `mobileSessionStore.ts` | Secure session persistence |
| Push notifications | `pushNotifications.ts` | Push registration |

### 2.3 i18n

- Mechanism: `phrMobileI18n.ts` with `t()` function
- Screens use `t()` for all user-visible strings
- Emergency access screen fully i18n'd

### 2.4 Gaps — Mobile

- No dedicated route-level E2E tests
- No a11y audit for mobile screens
- No offline-sync conflict resolution
- No biometric-gated consent revocation

---

## 3. Backend (Java)

**Stack**: Java 21 + ActiveJ · Gradle  
**Location**: `products/phr/src/`

### 3.1 Route Handlers — Implemented

| Handler Class | Responsibility |
|---------------|---------------|
| `PhrAuthRoutes.java` | Login/logout/session |
| `PhrPatientRecordRoutes.java` | Patient records, FHIR |
| `PhrClinicalRoutes.java` | Labs, observations, medications, immunizations, conditions |
| `PhrAdministrativeRoutes.java` | Appointments, billing |
| `PhrConsentRoutes.java` | Consent grants/revocations/checks |
| `PhrEmergencyRoutes.java` | Break-glass access with audit |
| `PhrEntitlementRoutes.java` | Route entitlement manifest |
| `PhrDocumentImagingRoutes.java` | Documents, upload, OCR |
| `PhrReleaseReadinessRoutes.java` | Release readiness evidence |
| `PhrAuditRoutes.java` | Audit event trail |

### 3.2 Services — Implemented

Core domain services: `PatientRecordService`, `ConsentManagementService`, `MedicationService`, `AppointmentService`, `DocumentService`, `EmergencyAccessLogService`, `FhirR4TransformationEngine`, `PHRSecurityManagerImpl`, `PHRPrivacyManagerImpl`.

AI agent services: lab anomaly detection, medication interaction, readmission risk.

### 3.3 Test Coverage

- **650+ Java unit/integration tests** passing (confirmed BUILD SUCCESSFUL)

### 3.4 Gaps — Backend

- Kernel policy evaluator integration (consent enforcement in routes uses ad hoc checks)
- Backend session lifecycle (refresh, revoke, expire) not fully enforced
- Route entitlement drift between backend and frontend manifest not yet eliminated

---

## 4. Kernel Integration

| Integration | Status |
|-------------|--------|
| Kernel lifecycle pilot registered | Partial |
| Route entitlement contract | Partial (frontend/backend drift pending) |
| IA coverage check (`check-phr-ia-coverage.mjs`) | Implemented |
| PHI log safety gate (`check-phr-phi-log-safety.mjs`) | Implemented |
| Mobile privacy gate (`check-phr-mobile-privacy.mjs`) | Implemented |
| i18n conformance gate (`check-phr-i18n-conformance.mjs`) | Implemented |

---

## 5. Known Gaps Summary

| Area | Gap | Priority |
|------|-----|----------|
| Backend | Kernel policy evaluator replaces ad hoc consent checks | P0 |
| Backend | Session refresh/revoke/expire lifecycle | P0 |
| Web | Route entitlement frontend/backend drift | P0 |
| Web | E2E Playwright tests | P1 |
| Web | Provider/caregiver/FCHV screens (behind feature flag) | P1 |
| Web | Medication detail page | P1 |
| Web | Insurance/referrals/imaging/payments routes | P1–P2 |
| Mobile | A11y audit for all screens | P1 |
| Mobile | Offline sync conflict resolution | P2 |
| Mobile | Full biometric session lifecycle | P1 |
| All | YAPPC PHR IA import and canvas visualization | P1 |

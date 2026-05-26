Below is the full implementation backlog from the audit at commit `37c2c98e32d9d7577cb6a9603762cf47387fc78a`. The commit is a merge commit with only a YAPPC changelog diff, so these tasks are based on the **full snapshot**, not the diff. 

The backlog is grounded in the PHR vision, frontend IA, screen matrix, current route contracts, mobile/web implementation, Kernel module wiring, release-readiness route, YAPPC scaffold/canvas APIs, and root check scripts. The vision/IA requires a much broader PHR product surface than the currently implemented web/mobile routes.    

Legend: **P0** production blocker, **P1** required for production-grade progress, **P2** hardening, **P3** cleanup/polish.

---

# A. P0 Production Blockers

| Priority | Area                    | What                                                                                                                                                 | Where                                                                                                                                                                  | Validation                                                                          |
| -------- | ----------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| P0       | Mobile privacy/security | Replace unencrypted AsyncStorage PHI cache with encrypted storage; current code explicitly defers encryption.                                        | `products/phr/apps/mobile/src/services/offlineStore.ts`                                                                                                                | Unit test: cache is encrypted, expires, clears on logout, clears on consent revoke. |
| P0       | Mobile auth/privacy     | Pass session context to every mobile PHI API call: tenant, principal, role, auth/session token.                                                      | `products/phr/apps/mobile/src/App.tsx`, `products/phr/apps/mobile/src/services/phrMobileApi.ts`                                                                        | API tests assert headers on dashboard/sync/emergency calls.                         |
| P0       | Consent/security        | Remove broad clinician/admin PHI bypass; replace with Kernel policy evaluator for consent, treatment relationship, facility scope, emergency, audit. | `products/phr/src/main/java/com/ghatana/phr/api/routes/PhrRouteSupport.java`, `PhrPatientRecordRoutes.java`, `PhrAdministrativeRoutes.java`                            | Access matrix tests for patient/caregiver/provider/admin/emergency.                 |
| P0       | Route entitlements      | Eliminate duplicate route contract sources between web manifest and backend entitlement route.                                                       | `products/phr/apps/web/src/phrRouteContracts.ts`, `products/phr/src/main/java/com/ghatana/phr/api/routes/PhrEntitlementRoutes.java`, new Kernel product route contract | Generated web/backend entitlement artifacts must match.                             |
| P0       | PHR IA coverage         | Create canonical machine-readable PHR use-case baseline from vision, frontend route map, screen matrix, workflows, tests.                            | New: `products/phr/config/phr-usecase-baseline.json`; docs under `products/phr/docs/**`                                                                                | Check fails if IA item lacks status: implemented/flagged/deferred/removed.          |
| P0       | Web/mobile i18n         | Move to one i18n mechanism; remove mobile raw strings and enforce no raw user-visible text.                                                          | `products/phr/apps/web/src/i18n/**`, `products/phr/apps/mobile/src/**`, platform i18n package                                                                          | `check:i18n-conformance` plus PHR web/mobile raw-string gate.                       |
| P0       | Release readiness       | Move release-readiness evidence reading from product-local file reader into Kernel runtime API.                                                      | `products/phr/src/main/java/com/ghatana/phr/api/routes/PhrReleaseReadinessRoutes.java`, Kernel lifecycle/readiness package                                             | PHR cockpit consumes Kernel readiness API, not `.kernel/evidence` files directly.   |
| P0       | Emergency/break-glass   | Ensure emergency access cannot expose PHI without reason, biometric/session gate where mobile, immutable audit, review, notification.                | `PhrEmergencyRoutes.java`, `EmergencyAccessLogService.java`, `EmergencyAccessPage.tsx`, `EmergencyAccessScreen.tsx`                                                    | E2E test proves request → audit → notification → review.                            |
| P0       | Privacy logging         | Prevent PHI/PII/secrets in logs and diagnostics, including mobile error boundary and logout/session logs.                                            | `PhrAuthRoutes.java`, mobile `App.tsx`, backend routes, observability helpers                                                                                          | Log redaction tests and static PHI logging gate.                                    |
| P0       | Backend session model   | Replace lightweight frontend session storage with secure token/session lifecycle: refresh, expiry, logout invalidation, backend session audit.       | `PhrAuthRoutes.java`, `PhrSessionContext.tsx`, mobile auth services                                                                                                    | Auth E2E: login, restore, expire, logout, revoke.                                   |

---

# B. PHR Vision / IA / Product-Use-Case Baseline

| Priority | What                                                   | Where                                                                       | Details                                                                                                 |
| -------- | ------------------------------------------------------ | --------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| P0       | Build canonical PHR use-case baseline.                 | New `products/phr/config/phr-usecase-baseline.json`                         | Include persona, IA route/screen, backend API, data model, Kernel dependency, YAPPC dependency, status. |
| P0       | Build IA-to-code traceability matrix.                  | New `products/phr/docs/04_design_and_workflows/phr_ia_code_traceability.md` | Map vision/IA items to files, APIs, tests, gaps.                                                        |
| P0       | Add generated IA coverage evidence.                    | New `.kernel/evidence/phr/ia-coverage.json`                                 | Evidence generated from code/docs, bound to commit.                                                     |
| P0       | Add check script for PHR IA drift.                     | New `scripts/check-phr-ia-coverage.mjs`                                     | Fails if route/screen/workflow in docs is missing from baseline.                                        |
| P1       | Add product phase classification to every IA item.     | `phr-usecase-baseline.json`                                                 | Classify: current critical, MVP, Phase 2, hidden/flagged, removed.                                      |
| P1       | Reconcile docs that claim “complete” with actual code. | `products/phr/docs/01-vision-plan-requirements/01-product-vision.md`        | Replace unsupported “complete” claims with evidence-based status.                                       |
| P1       | Add doc/code mismatch report.                          | New `.kernel/evidence/phr/doc-code-mismatch.json`                           | Required by release-readiness gate.                                                                     |
| P1       | Add route-to-screen-to-test map.                       | New `products/phr/config/phr-route-screen-test-map.json`                    | Every web/mobile route must have route test, action test, state test.                                   |
| P1       | Add “feature visibility” matrix.                       | New `products/phr/config/phr-feature-visibility.json`                       | Controls visible/hidden/feature-flagged IA items.                                                       |
| P2       | Add roadmap-defer decisions for Phase 2 items.         | `products/phr/docs/04_design_and_workflows/*`                               | Claims, insurance, advanced telemedicine, imaging viewer, etc.                                          |

---

# C. PHR Web App Tasks

Current web routes are limited to core routes and a few feature-flag placeholders.  

## C1. Routing, Shell, Navigation, Access

| Priority | What                                                                                                 | Where                                                       |
| -------- | ---------------------------------------------------------------------------------------------------- | ----------------------------------------------------------- |
| P0       | Generate `phrRouteContracts.ts` from Kernel canonical route contract instead of hand-maintaining it. | `products/phr/apps/web/src/phrRouteContracts.ts`            |
| P0       | Remove backend/frontend route entitlement drift.                                                     | `PhrEntitlementRoutes.java`, `phrRouteContracts.ts`         |
| P0       | Ensure feature-flagged IA routes are not discoverable unless explicitly enabled.                     | `phrRouteContracts.ts`, `PhrProductShell.tsx`               |
| P1       | Add route lifecycle metadata: stable, preview, boundary, deprecated.                                 | `phrRouteContracts.ts`, Kernel product route schema         |
| P1       | Add `/forbidden` and `/not-found` routes matching IA.                                                | `products/phr/apps/web/src/routes.tsx`, new pages           |
| P1       | Add route-level breadcrumb/page title metadata.                                                      | `phrRouteContracts.ts`, `PhrProductShell.tsx`               |
| P1       | Add route access test for every persona.                                                             | `products/phr/apps/web/src/__tests__/route-access.test.tsx` |
| P1       | Add shell loading/error state when entitlement API fails.                                            | `PhrProductShell.tsx`                                       |
| P2       | Add active route group consistency tests.                                                            | `RouteCapabilityNav` usage in PHR shell                     |
| P2       | Add keyboard navigation checks for shell/sidebar/header/user menu.                                   | PHR Playwright tests                                        |

## C2. Auth / Session UI

| Priority | What                                                                                      | Where                                                                                 |
| -------- | ----------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| P0       | Add secure logout action and backend call.                                                | `LoginPage.tsx`, `PhrProductShell.tsx`, `PhrSessionContext.tsx`, `PhrAuthRoutes.java` |
| P0       | Do not store sensitive session data in raw `sessionStorage` beyond safe session envelope. | `PhrSessionContext.tsx`                                                               |
| P1       | Add session expiry banner and auto-redirect.                                              | `PhrSessionContext.tsx`, `PhrProductShell.tsx`                                        |
| P1       | Add MFA/OTP state if IA/auth contract requires it.                                        | `LoginPage.tsx`, auth route                                                           |
| P1       | Add auth loading, invalid credentials, locked account, expired session states.            | `LoginPage.tsx`                                                                       |
| P2       | Add accessibility labels and error focus management on login form.                        | `LoginPage.tsx`                                                                       |

## C3. Patient Dashboard

| Priority | What                                                                                                                                                 | Where                                                                                         |
| -------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| P1       | Replace frontend FHIR aggregation with backend-owned dashboard endpoint.                                                                             | New `PhrDashboardRoutes.java`, `products/phr/apps/web/src/api/phrApi.ts`, `DashboardPage.tsx` |
| P1       | Add widgets required by IA: profile summary, next appointment, active meds, recent observations, active conditions, recent documents, access alerts. | `DashboardPage.tsx`, new dashboard components                                                 |
| P1       | Add loading/error/empty/unauthorized states using design system.                                                                                     | `DashboardPage.tsx`                                                                           |
| P1       | Add patient/caregiver scoped dashboard variants.                                                                                                     | `DashboardPage.tsx`, route contract                                                           |
| P2       | Add dashboard data freshness and last sync indicator.                                                                                                | `DashboardPage.tsx`, API contract                                                             |
| P2       | Add dashboard unit/format localization.                                                                                                              | `phrI18n.ts`, locale JSON                                                                     |

## C4. Patient Profile

| Priority | What                                                             | Where                                                                       |
| -------- | ---------------------------------------------------------------- | --------------------------------------------------------------------------- |
| P1       | Add `/profile` route.                                            | `phrRouteContracts.ts`, `phrRouteElements.tsx`, new `pages/ProfilePage.tsx` |
| P1       | Add backend profile get/update endpoint.                         | `PhrPatientRecordRoutes.java` or new `PhrPatientProfileRoutes.java`         |
| P1       | Add edit mode, dirty state, validation, cancel/save.             | `ProfilePage.tsx`                                                           |
| P1       | Add field-level permission logic for patient/caregiver/provider. | Kernel policy + ProfilePage                                                 |
| P1       | Add audit event on profile update.                               | Backend profile route + audit event                                         |
| P2       | Add profile i18n and a11y tests.                                 | Web tests                                                                   |

## C5. Records / Record Detail / FHIR

| Priority | What                                                                               | Where                                                 |
| -------- | ---------------------------------------------------------------------------------- | ----------------------------------------------------- |
| P1       | Add backend-owned records list endpoint instead of relying only on dashboard data. | `PhrPatientRecordRoutes.java`, `phrApi.ts`            |
| P1       | Add record filters: category, date, facility, provider, resource type.             | `RecordsPage.tsx`, backend route                      |
| P1       | Add record detail API endpoint with consent/policy check.                          | `PhrPatientRecordRoutes.java`, `RecordDetailPage.tsx` |
| P1       | Add FHIR JSON safe viewer with redaction and copy/download policy.                 | `RecordDetailPage.tsx`                                |
| P1       | Add invalid/missing FHIR payload state.                                            | `RecordDetailPage.tsx`                                |
| P2       | Add record provenance panel.                                                       | `RecordDetailPage.tsx`                                |
| P2       | Add pagination/sorting for records.                                                | `RecordsPage.tsx`                                     |

## C6. Timeline / Encounters / Conditions

| Priority | What                                             | Where                                               |
| -------- | ------------------------------------------------ | --------------------------------------------------- |
| P1       | Add `/timeline` route/page.                      | New `pages/TimelinePage.tsx`, route contract        |
| P1       | Add timeline API.                                | New `PhrTimelineRoutes.java`                        |
| P1       | Add encounter detail route/page.                 | New `pages/EncounterDetailPage.tsx`, route contract |
| P1       | Add `/conditions` route/page.                    | New `pages/ConditionsPage.tsx`                      |
| P1       | Add conditions API.                              | New `PhrConditionRoutes.java`                       |
| P1       | Add consent-aware access to timeline/conditions. | Kernel policy + routes                              |
| P2       | Add filters, grouping, empty states.             | Timeline/Conditions pages                           |

## C7. Labs / Observations / Vitals

| Priority | What                                                     | Where                                              |
| -------- | -------------------------------------------------------- | -------------------------------------------------- |
| P1       | Add `/observations` route separate from `/labs`.         | `phrRouteContracts.ts`, new `ObservationsPage.tsx` |
| P1       | Add observation trend endpoint.                          | `PhrClinicalRoutes.java`                           |
| P1       | Add vitals/trend chart using shared chart/design system. | `ObservationsPage.tsx`, healthcare components      |
| P1       | Add abnormal/attention state semantics from backend.     | API DTO + page                                     |
| P2       | Add metric selector and date range.                      | `ObservationsPage.tsx`                             |
| P2       | Add LOINC/unit validation display.                       | Backend + page                                     |

## C8. Medications

| Priority | What                                           | Where                          |
| -------- | ---------------------------------------------- | ------------------------------ |
| P1       | Add medication detail route/page.              | New `MedicationDetailPage.tsx` |
| P1       | Add active/history tabs.                       | `MedicationsPage.tsx`          |
| P1       | Add backend medication detail endpoint.        | `PhrClinicalRoutes.java`       |
| P1       | Add adherence confidence/source metadata.      | API DTO + page                 |
| P1       | Add interaction/allergy warning display.       | Medication service/API/page    |
| P2       | Add refill/request action only when supported. | `MedicationsPage.tsx`          |

## C9. Immunizations

| Priority | What                                         | Where                                                 |
| -------- | -------------------------------------------- | ----------------------------------------------------- |
| P1       | Add `/immunizations` route/page.             | `phrRouteContracts.ts`, `pages/ImmunizationsPage.tsx` |
| P1       | Add immunization list/detail API.            | `PhrClinicalRoutes.java`                              |
| P1       | Add permanent-retention indicator and audit. | API/page                                              |
| P2       | Add vaccine code/status filters.             | Page/API                                              |

## C10. Appointments

| Priority | What                                                                | Where                                                  |
| -------- | ------------------------------------------------------------------- | ------------------------------------------------------ |
| P1       | Replace simple appointment request with full book/request workflow. | `AppointmentsPage.tsx`, `PhrAdministrativeRoutes.java` |
| P1       | Add provider/slot search.                                           | `AppointmentsPage.tsx`, appointment API                |
| P1       | Add conflict/double-book handling.                                  | Backend appointment service                            |
| P1       | Add upcoming/past tabs.                                             | `AppointmentsPage.tsx`                                 |
| P1       | Add cancel/reschedule visibility by role/policy.                    | Page/API                                               |
| P2       | Add appointment reminder status.                                    | API/page                                               |

## C11. Documents / Upload / OCR

| Priority | What                                                             | Where                                           |
| -------- | ---------------------------------------------------------------- | ----------------------------------------------- |
| P1       | Add `/documents` route/page.                                     | `phrRouteContracts.ts`, new `DocumentsPage.tsx` |
| P1       | Add `/documents/upload` route/page.                              | New `DocumentUploadPage.tsx`                    |
| P1       | Add `/documents/ocr` and OCR review route/page.                  | New `OcrReviewPage.tsx`                         |
| P1       | Wire UI to document/imaging backend routes.                      | `PhrDocumentImagingRoutes.java`, `phrApi.ts`    |
| P1       | Add upload progress, file validation, size/type limits.          | Upload page + backend                           |
| P1       | Add OCR confidence badges and accept/edit/reject field workflow. | `OcrReviewPage.tsx`                             |
| P1       | Add provenance and audit trail on upload/OCR confirm.            | Backend services                                |
| P2       | Add document preview fallback and secure download gate.          | Documents pages                                 |

## C12. Voice Input

| Priority | What                                                                 | Where                                               |
| -------- | -------------------------------------------------------------------- | --------------------------------------------------- |
| P1       | Decide MVP status: implement or hide.                                | IA baseline + `phr-feature-visibility.json`         |
| P1       | Add patient voice input route behind feature flag if not ready.      | `phrRouteContracts.ts`, `PatientVoiceInputPage.tsx` |
| P1       | Add provider voice dictation route behind feature flag if not ready. | `ProviderVoiceDictationPage.tsx`                    |
| P1       | Wire through shared audio-video packages, not local implementation.  | `@ghatana/audio-video-*`, PHR pages                 |
| P2       | Add ASR/NLP provenance and FHIR draft review.                        | Backend + UI                                        |

## C13. Insurance / Billing / Claims / Payments

| Priority | What                                                           | Where                          |
| -------- | -------------------------------------------------------------- | ------------------------------ |
| P1       | Add `/insurance` route/page or explicitly defer.               | IA baseline, route contract    |
| P1       | Add `/payments` route/page or defer.                           | Route contract/page            |
| P1       | Add `/claims` route/page as Phase 2 hidden unless implemented. | Route contract/page            |
| P1       | Align web routes with existing billing backend APIs.           | `PhrAdministrativeRoutes.java` |
| P1       | Add patient/caregiver/provider billing access policy.          | Kernel policy                  |
| P2       | Add receipt retrieval and claim status display.                | Billing UI/API                 |

## C14. Referrals / Imaging / Telemedicine

| Priority | What                                                                   | Where                                                                   |
| -------- | ---------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| P1       | Add `/referrals` route/page or hide/defer.                             | Route contract/page                                                     |
| P1       | Add `/imaging` route/page or hide/defer.                               | Route contract/page                                                     |
| P1       | Add telemedicine route visibility decision.                            | IA baseline; docs currently exclude call room until dedicated contracts |
| P1       | Align backend referral/imaging/telemedicine routes with web/mobile UX. | `PhrAdministrativeRoutes.java`, `PhrDocumentImagingRoutes.java`         |
| P2       | Add secure download/audit for imaging artifacts.                       | Imaging UI/API                                                          |

## C15. Provider Workflows

| Priority | What                                                                    | Where                            |
| -------- | ----------------------------------------------------------------------- | -------------------------------- |
| P1       | Replace `/provider/dashboard` placeholder with real page.               | `ProviderDashboardPage.tsx`      |
| P1       | Replace `/provider/patients` placeholder with real patient search/list. | `ProviderPatientsPage.tsx`       |
| P1       | Add provider patient summary route/page.                                | `ProviderPatientSummaryPage.tsx` |
| P1       | Add encounter detail/update route/page.                                 | `EncounterDetailPage.tsx`        |
| P1       | Add observation entry route/page.                                       | `ObservationEntryPage.tsx`       |
| P1       | Add medication request entry route/page.                                | `MedicationRequestEntryPage.tsx` |
| P1       | Add provider calendar route/page.                                       | `ProviderCalendarPage.tsx`       |
| P1       | Enforce provider access through consent/treatment relationship.         | Kernel policy + backend          |
| P2       | Add provider queue/worklist filters.                                    | Provider dashboard               |

## C16. Caregiver Workflows

| Priority | What                                                         | Where                                         |
| -------- | ------------------------------------------------------------ | --------------------------------------------- |
| P1       | Replace `/caregiver/dependents` placeholder.                 | `CaregiverDependentsPage.tsx`                 |
| P1       | Add dependent detail route/page.                             | `CaregiverDependentDetailPage.tsx`            |
| P1       | Wire caregiver service/API.                                  | `CaregiverService.java`, new routes if needed |
| P1       | Enforce delegated access and expired/revoked grant behavior. | Kernel policy + backend                       |
| P2       | Add caregiver appointment management when granted.           | Caregiver pages                               |

## C17. FCHV / Community Health

| Priority | What                                                                 | Where                                                          |
| -------- | -------------------------------------------------------------------- | -------------------------------------------------------------- |
| P1       | Define FCHV role separately instead of overloading caregiver.        | `PhrAccessContext.tsx`, Kernel role model, backend role policy |
| P1       | Replace `/fchv/dashboard` placeholder with scoped dashboard or hide. | `FchvDashboardPage.tsx`                                        |
| P1       | Add FCHV patient registration route/page.                            | `FchvRegisterPatientPage.tsx`                                  |
| P1       | Add FCHV patient list and vitals capture.                            | `FchvPatientsPage.tsx`, `FchvVitalsPage.tsx`                   |
| P1       | Add offline queue semantics for FCHV mobile if in scope.             | Mobile services                                                |
| P2       | Add low-connectivity sync/error states.                              | Web/mobile                                                     |

## C18. Admin / Audit / Release

| Priority | What                                                   | Where                                                    |
| -------- | ------------------------------------------------------ | -------------------------------------------------------- |
| P1       | Add admin dashboard route/page.                        | `AdminDashboardPage.tsx`                                 |
| P1       | Make audit page backend-backed, not static/mock-like.  | `AuditPage.tsx`, `fetchAuditEvents`, backend audit route |
| P1       | Add audit detail drawer/page.                          | `AuditDetailPage.tsx`                                    |
| P1       | Add audit export with privacy policy.                  | Backend audit route + UI                                 |
| P1       | Add release readiness drill-down per evidence section. | `ReleaseCockpitPage.tsx`, Kernel readiness API           |
| P2       | Add environment compare for local/dev/staging/prod.    | Release cockpit                                          |
| P2       | Add rollback drill evidence UI.                        | Release cockpit                                          |

---

# D. PHR Mobile App Tasks

Current mobile app has login, dashboard, records, consents, alerts, emergency, settings, and offline cache, but it is not production-grade for PHI/privacy/i18n.   

| Priority | What                                                                          | Where                                                   |
| -------- | ----------------------------------------------------------------------------- | ------------------------------------------------------- |
| P0       | Replace AsyncStorage PHI cache with encrypted storage adapter.                | `products/phr/apps/mobile/src/services/offlineStore.ts` |
| P0       | Add secure session storage for `MobileSession`.                               | New `services/mobileSessionStore.ts`                    |
| P0       | Pass session context to `fetchMobileDashboard` and `syncOfflineDashboard`.    | `App.tsx`, `phrMobileApi.ts`                            |
| P0       | Clear offline PHI on logout, session expiry, consent revocation, role change. | `offlineStore.ts`, new session/logout flow              |
| P0       | Add explicit logout button/action.                                            | `SettingsScreen.tsx`, `App.tsx`                         |
| P0       | Block emergency PHI reveal until biometric success and server authorization.  | `EmergencyAccessScreen.tsx`, `biometricAuth.ts`, API    |
| P1       | Add mobile i18n message packs.                                                | New `src/i18n/**`, update all screens                   |
| P1       | Remove all raw strings from mobile screens.                                   | `screens/*.tsx`, `App.tsx`, services                    |
| P1       | Add mobile accessibility labels/hints for every Pressable/Input/tab.          | `screens/*.tsx`                                         |
| P1       | Replace wrapped pill tab bar with accessible bottom navigation.               | `App.tsx`                                               |
| P1       | Add offline/online status banner.                                             | `App.tsx`, `SettingsScreen.tsx`                         |
| P1       | Add stale-cache warning and last refreshed time.                              | `offlineStore.ts`, `SettingsScreen.tsx`                 |
| P1       | Add mobile consent revoke/list actions, not read-only cards.                  | `ConsentScreen.tsx`, API                                |
| P1       | Add mobile record detail screen.                                              | New `screens/RecordDetailScreen.tsx`                    |
| P1       | Add mobile profile screen or settings profile section.                        | `SettingsScreen.tsx`                                    |
| P1       | Add notification privacy redaction: no PHI in push notification body.         | `pushNotifications.ts`, backend notification service    |
| P1       | Add mobile error boundary telemetry that redacts PHI.                         | `App.tsx`                                               |
| P2       | Add biometric availability states: unavailable, failed, locked out, success.  | `EmergencyAccessScreen.tsx`                             |
| P2       | Add mobile tests for loading/error/offline/success.                           | `products/phr/apps/mobile/src/__tests__/**`             |
| P2       | Add mobile E2E/smoke flow.                                                    | Expo/Detox or React Native test setup                   |

---

# E. PHR Backend/API Tasks

The backend has strong ActiveJ route/service coverage but must be hardened around policies, contracts, validation, tenant scope, and E2E alignment.    

| Priority | What                                                                                                                             | Where                                                       |
| -------- | -------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------- |
| P0       | Introduce Kernel-backed PHI access policy evaluator.                                                                             | New Kernel policy package; PHR route adapters               |
| P0       | Replace `PhrRouteSupport.isPrivileged` bypass with policy checks.                                                                | `PhrRouteSupport.java`, all route adapters                  |
| P0       | Validate tenant/facility scope on every route.                                                                                   | `PhrRouteSupport.java`, route adapters                      |
| P0       | Add request/correlation ID extraction and propagation.                                                                           | `PhrRouteSupport.java`, Kernel context/events               |
| P0       | Add audit event on every sensitive read/write/export/emergency action.                                                           | All PHR routes/services                                     |
| P0       | Align frontend `createConsentGrant` payload with backend expected `recipientId/scope`, not mismatched `granteeId/resourceTypes`. | `phrApi.ts`, `PhrConsentRoutes.java`                        |
| P0       | Add mobile `/mobile/dashboard` backend route or align mobile to existing API contract.                                           | New `PhrMobileRoutes.java`, `PhrHttpServer.java`            |
| P0       | Add backend logout/session invalidation/audit.                                                                                   | `PhrAuthRoutes.java`                                        |
| P1       | Add dashboard route.                                                                                                             | New `PhrDashboardRoutes.java`                               |
| P1       | Add profile-specific route and DTO.                                                                                              | New `PhrPatientProfileRoutes.java` or extend patient routes |
| P1       | Add timeline route.                                                                                                              | New `PhrTimelineRoutes.java`                                |
| P1       | Add conditions route.                                                                                                            | New `PhrConditionRoutes.java`                               |
| P1       | Add observations trend route.                                                                                                    | `PhrClinicalRoutes.java`                                    |
| P1       | Add medication detail/history route.                                                                                             | `PhrClinicalRoutes.java`                                    |
| P1       | Add immunization route if not exposed.                                                                                           | `PhrClinicalRoutes.java`                                    |
| P1       | Add caregiver routes.                                                                                                            | New `PhrCaregiverRoutes.java`                               |
| P1       | Add provider search/summary/encounter routes.                                                                                    | New `PhrProviderRoutes.java`                                |
| P1       | Add FCHV routes if in MVP.                                                                                                       | New `PhrFchvRoutes.java`                                    |
| P1       | Add documents/OCR routes if not complete.                                                                                        | `PhrDocumentImagingRoutes.java`                             |
| P1       | Add audit event list/detail/export route.                                                                                        | New or existing audit routes                                |
| P1       | Add emergency access review route and notification proof.                                                                        | `PhrEmergencyRoutes.java`, services                         |
| P1       | Add idempotency for create/update flows.                                                                                         | Consent, appointment, upload, emergency                     |
| P1       | Add consistent error envelope.                                                                                                   | `PhrRouteSupport.java`                                      |
| P1       | Add server-side DTO/schema validation instead of manual partial parsing.                                                         | Route adapters                                              |
| P1       | Add FHIR validation errors with safe messages.                                                                                   | FHIR routes/server                                          |
| P2       | Add pagination/sorting/filter contract standard.                                                                                 | All list routes                                             |
| P2       | Add API OpenAPI/contract generation.                                                                                             | New PHR API contract package                                |
| P2       | Add performance guardrails for large records/audit lists.                                                                        | Backend routes/services                                     |

---

# F. PHR Domain / Healthcare Tasks

| Priority | What                                                                       | Where                                                |
| -------- | -------------------------------------------------------------------------- | ---------------------------------------------------- |
| P0       | Define canonical PHI/PII classification per resource and field.            | `products/phr/kernel/policy/**`, docs, Kernel policy |
| P0       | Apply field/resource classification to logs, audit, export, offline cache. | Backend/mobile/export                                |
| P1       | Add record provenance model across FHIR/document/OCR/voice/HIE imports.    | PHR services, schema contracts                       |
| P1       | Add consent scope enforcement at resource/action/field level.              | `ConsentManagementService.java`, Kernel policy       |
| P1       | Add consent expiry scheduler/cache invalidation evidence.                  | Consent service + evidence                           |
| P1       | Add Nepal HIE import/export user journeys.                                 | HIE services/controllers + UI                        |
| P1       | Add HL7 lab result import E2E from message → lab result → UI.              | `Hl7LabResultIntegrationService`, labs UI            |
| P1       | Add FHIR resource validation gate by resource type.                        | FHIR server/routes                                   |
| P1       | Add clinical decision support safety boundaries and human review.          | `ClinicalDecisionSupportService.java`, UI            |
| P1       | Add medication interaction/allergy workflow.                               | Medication service/API/UI                            |
| P1       | Add imaging artifact secure access/download audit.                         | Imaging service/routes/UI                            |
| P2       | Add Nepal-specific identifiers/facility/provider validation.               | Patient/provider services                            |
| P2       | Add retention policy evidence per resource.                                | Kernel evidence + PHR services                       |

---

# G. Cross-Cutting i18n, a11y, o11y, Privacy, Security

## G1. i18n

| Priority | What                                                      | Where                                          |
| -------- | --------------------------------------------------------- | ---------------------------------------------- |
| P0       | Establish one canonical PHR i18n usage path.              | Platform i18n package + `products/phr/apps/**` |
| P0       | Convert mobile raw strings to i18n.                       | `products/phr/apps/mobile/src/**`              |
| P1       | Convert remaining web permission/error strings to i18n.   | `routes.tsx`, pages                            |
| P1       | Add missing Nepali translations for every web/mobile key. | locale JSON                                    |
| P1       | Add pseudo-locale route/screen tests.                     | web/mobile tests                               |
| P1       | Add script to fail raw user-visible strings.              | `scripts/check-phr-i18n-conformance.mjs`       |
| P2       | Add locale switcher to web/mobile settings.               | `SettingsPage.tsx`, `SettingsScreen.tsx`       |

## G2. Accessibility

| Priority | What                                                            | Where                               |
| -------- | --------------------------------------------------------------- | ----------------------------------- |
| P1       | Add route-level WCAG AA checklist from IA.                      | `phr-usecase-baseline.json`         |
| P1       | Add accessible error focus management in forms.                 | Login, consent, appointment, upload |
| P1       | Add labels/hints for all buttons/inputs/tabs/mobile navigation. | Web/mobile components               |
| P1       | Add keyboard navigation tests for web shell and data cards.     | Playwright                          |
| P1       | Add color contrast gate for PHR semantic statuses.              | Design system + PHR CSS             |
| P2       | Add screen reader-friendly table/list alternatives.             | Audit, records, labs                |

## G3. Observability / o11y

| Priority | What                                                                                | Where                                                  |
| -------- | ----------------------------------------------------------------------------------- | ------------------------------------------------------ |
| P0       | Add correlation ID to every web/mobile/backend request.                             | `phrApi.ts`, `phrMobileApi.ts`, `PhrRouteSupport.java` |
| P1       | Add structured audit event for every sensitive data read.                           | Backend route adapters                                 |
| P1       | Add frontend telemetry for critical journeys with no PHI.                           | Web app                                                |
| P1       | Add mobile telemetry/error reporting with PHI redaction.                            | Mobile app                                             |
| P1       | Add evidence outbox health to release readiness.                                    | `PhrKernelModule.java`, Kernel readiness               |
| P2       | Add dashboards/metrics for consent failures, emergency access, export, mobile sync. | monitoring/evidence                                    |

## G4. Privacy/Security

| Priority | What                                                        | Where                                            |
| -------- | ----------------------------------------------------------- | ------------------------------------------------ |
| P0       | Encrypt mobile PHI cache.                                   | Mobile offline store                             |
| P0       | Replace role-only access with policy engine.                | Kernel + PHR routes                              |
| P0       | Add tenant/facility/principal validation everywhere.        | `PhrRouteSupport.java`                           |
| P0       | Prevent PHI in push notifications.                          | Mobile/backend notification services             |
| P0       | Prevent PHI in logs/errors/diagnostics.                     | Backend/mobile/web                               |
| P1       | Add server-side consent enforcement tests for all PHI APIs. | Backend tests                                    |
| P1       | Add emergency access review SLA/status.                     | Emergency services                               |
| P1       | Add export/download privacy checks.                         | HIE/export/document routes                       |
| P2       | Add security threat model doc.                              | `products/phr/docs/security/PHR_THREAT_MODEL.md` |

---

# H. Kernel Hardening Tasks

Kernel already exposes product-shell/lifecycle/deployment/health/entitlement contracts, but PHR needs stronger canonicalization and policy support.  

| Priority | What                                                                                      | Where                                                                      |
| -------- | ----------------------------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| P0       | Add canonical product route/capability contract model.                                    | `platform/typescript/product-shell`, Kernel Java platform contract package |
| P0       | Generate PHR web route manifest and backend entitlement payload from one Kernel contract. | New Kernel generator + PHR generated files                                 |
| P0       | Add Kernel PHI access policy evaluator.                                                   | New `platform-kernel` policy/security package                              |
| P0       | Add Kernel mobile PHI offline policy gate.                                                | Kernel release/security checks                                             |
| P1       | Add Kernel release-readiness runtime API.                                                 | `platform/typescript/kernel-lifecycle` and/or Java Kernel lifecycle        |
| P1       | Move PHR release file parsing to Kernel service.                                          | Kernel readiness service; deprecate product-local reader                   |
| P1       | Add product IA/use-case lifecycle contract.                                               | Kernel product lifecycle schema                                            |
| P1       | Add product use-case completeness gate.                                                   | `scripts/check-product-feature-completeness...` or new Kernel check        |
| P1       | Add Kernel i18n/a11y product-surface gates.                                               | `scripts/check-i18n-*`, `scripts/check-a11y-*`                             |
| P1       | Add route entitlement drift check.                                                        | `scripts/check-route-entitlement-contracts.mjs`                            |
| P1       | Add Kernel audit/event correlation contract.                                              | Kernel event/context package                                               |
| P1       | Add product mobile privacy release gate.                                                  | Kernel release checks                                                      |
| P2       | Add generated docs from product route/use-case contracts.                                 | Kernel docs generator                                                      |
| P2       | Add product readiness score from IA coverage + tests + evidence.                          | Kernel evidence/scorecard                                                  |
| P2       | Add product lifecycle “explain/recover” for missing IA tasks.                             | Kernel lifecycle service                                                   |

---

# I. YAPPC Hardening Tasks

YAPPC has a scaffold API and Canvas AI adapter, but it is not yet PHR-IA-aware or fully Kernel-native.  

| Priority | What                                                                           | Where                                                      |
| -------- | ------------------------------------------------------------------------------ | ---------------------------------------------------------- |
| P0       | Add PHR IA importer.                                                           | `products/yappc/core/**`, `products/yappc/frontend/web/**` |
| P0       | Import PHR vision/route/screen/workflow docs into product model.               | New YAPPC product model adapter                            |
| P0       | Visualize PHR missing IA/code/test coverage on canvas.                         | YAPPC canvas UI                                            |
| P1       | Add Kernel-native product artifact generation mode.                            | `YappcApi.java`, project/template services                 |
| P1       | Generate PHR route contracts from IA baseline.                                 | YAPPC + Kernel generator                                   |
| P1       | Generate PHR API contract skeletons from IA baseline.                          | YAPPC scaffold packs                                       |
| P1       | Generate PHR web/mobile page skeletons with design-system/product-shell usage. | YAPPC templates                                            |
| P1       | Generate E2E/API/unit test skeletons per IA use case.                          | YAPPC test templates                                       |
| P1       | Stop silently no-oping missing canvas AI backend endpoints in production mode. | `yappc-ai-adapter.ts`                                      |
| P1       | Show degraded/missing generator endpoints as readiness gaps.                   | YAPPC frontend                                             |
| P1       | Add PHR product-unit pack.                                                     | `products/yappc/core/scaffold/packs/phr-*`                 |
| P1       | Add Kernel contract dependency validation before generation.                   | YAPPC core                                                 |
| P2       | Add web/mobile parity report generation.                                       | YAPPC product analysis                                     |
| P2       | Add security/privacy/i18n/a11y flags in generated plans.                       | YAPPC planning model                                       |
| P2       | Add downloadable implementation backlog artifact from YAPPC.                   | YAPPC frontend/backend                                     |

---

# J. Test / CI / Release Gate Tasks

Root scripts already include many PHR, Kernel, YAPPC, i18n, a11y, security, lifecycle, release-readiness checks. 

| Priority | What                                                                                                      | Where                                                   |
| -------- | --------------------------------------------------------------------------------------------------------- | ------------------------------------------------------- |
| P0       | Add PHR IA coverage check to required gates.                                                              | `package.json`, new `scripts/check-phr-ia-coverage.mjs` |
| P0       | Add mobile PHI encryption gate.                                                                           | new `scripts/check-phr-mobile-privacy.mjs`              |
| P0       | Add PHI log redaction gate.                                                                               | new `scripts/check-phr-phi-log-safety.mjs`              |
| P0       | Add consent enforcement backend matrix test.                                                              | PHR backend tests                                       |
| P0       | Add emergency break-glass E2E test.                                                                       | PHR backend/web/mobile tests                            |
| P1       | Add web route traversal Playwright tests for every route contract.                                        | `products/phr/apps/web/e2e/**`                          |
| P1       | Add web action tests: login, consent grant/revoke, appointment request, audit filter, release env switch. | Web E2E                                                 |
| P1       | Add mobile tests: login, dashboard, records, consents, alerts, emergency biometric, offline cache.        | Mobile tests                                            |
| P1       | Add backend API tests for auth/patient/consent/appointment/emergency/audit/release/FHIR.                  | PHR backend test source                                 |
| P1       | Add route entitlement contract test: web manifest equals backend entitlement.                             | Kernel/PHR tests                                        |
| P1       | Add i18n pseudo-locale visual/behavior tests.                                                             | Web/mobile tests                                        |
| P1       | Add a11y tests for web routes.                                                                            | Playwright axe or equivalent                            |
| P1       | Add observability tests for correlation IDs.                                                              | Backend/web/mobile                                      |
| P1       | Add release-readiness current commit/evidence freshness test.                                             | Kernel readiness tests                                  |
| P1       | Add YAPPC PHR IA import/generate/validate roundtrip test.                                                 | YAPPC tests                                             |
| P2       | Add performance tests for audit/records/labs.                                                             | backend/perf tests                                      |
| P2       | Add offline low-connectivity sync tests.                                                                  | mobile/integration                                      |
| P2       | Add HIE/FHIR integration smoke tests.                                                                     | PHR integration tests                                   |

---

# K. Documentation / Governance Tasks

| Priority | What                                                          | Where                                                                 |
| -------- | ------------------------------------------------------------- | --------------------------------------------------------------------- |
| P0       | Correct docs that overstate implementation maturity.          | `products/phr/docs/01-vision-plan-requirements/01-product-vision.md`  |
| P1       | Add “current implemented surface” doc generated from code.    | `products/phr/docs/current-state/generated-current-surface.md`        |
| P1       | Add “PHR feature visibility and flags” doc.                   | `products/phr/docs/04_design_and_workflows/phr_feature_visibility.md` |
| P1       | Add “Kernel-native PHR lifecycle” doc.                        | `products/phr/docs/03_architecture/phr_kernel_native_lifecycle.md`    |
| P1       | Add privacy/security architecture doc for mobile offline PHI. | `products/phr/docs/security/phr_mobile_offline_phi.md`                |
| P1       | Add access policy matrix doc.                                 | `products/phr/docs/security/phr_access_policy_matrix.md`              |
| P1       | Add emergency access runbook.                                 | `products/phr/docs/runbooks/phr_emergency_access.md`                  |
| P1       | Add consent revocation/cache invalidation runbook.            | `products/phr/docs/runbooks/phr_consent_revocation.md`                |
| P2       | Add YAPPC-for-PHR generation workflow doc.                    | `products/yappc/docs/use-cases/phr_generation_workflow.md`            |
| P2       | Add Kernel route entitlement canonicalization ADR.            | `docs/adr/**`                                                         |

---

# L. Recommended Execution Order

| Phase | Goal                                           | Must complete                                                                                           |
| ----- | ---------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| 1     | Close P0 privacy/security risks                | Mobile encrypted PHI cache, session headers, policy evaluator, no PHI logs, consent enforcement.        |
| 2     | Create canonical IA baseline                   | `phr-usecase-baseline.json`, IA traceability, visibility matrix, doc/code mismatch evidence.            |
| 3     | Canonicalize route/entitlements through Kernel | Remove TS/Java drift, generated route/entitlement artifacts, route tests.                               |
| 4     | Stabilize current patient journeys             | Auth, dashboard, records, detail, consents, appointments, labs, meds, audit, settings.                  |
| 5     | Fill missing MVP IA                            | Profile, timeline, conditions, documents/upload/OCR, caregiver dependents, provider dashboard/patients. |
| 6     | Harden mobile                                  | i18n, a11y, offline, biometric, secure sync, record detail, consent actions.                            |
| 7     | Harden o11y/release                            | Correlation IDs, audit/evidence trace, Kernel readiness API, release cockpit drill-down.                |
| 8     | Harden YAPPC                                   | PHR IA import, Kernel-native artifact generation, web/mobile/API/test generation, gap visualization.    |
| 9     | Add full tests/gates                           | Web/mobile/backend/kernel/yappc E2E and release-readiness gates.                                        |
| 10    | Re-audit                                       | Compare IA coverage, P0 closure, scorecard movement, and production readiness.                          |

Below is the granular task backlog for `samujjwal/ghatana` at commit `cf61a7ead6dd44c8a130bf02f8522e05c89a0e41`.

I treated this as a **full-snapshot audit**, not a commit-diff audit. The commit diff itself only updates the YAPPC changelog for a prior merge. 

Important update from the previous audit: this snapshot has progressed. The PHR vision now correctly marks the product as **Alpha — Partial Implementation**, and the goal table explicitly says multiple surfaces are partial rather than complete.   Web route coverage has expanded significantly: profile, timeline, conditions, observations, immunizations, documents, upload, OCR, notifications, forbidden, not-found, provider, caregiver, and FCHV routes now appear in the route contract and route element map.   Mobile PHI cache encryption is also now implemented through `phiEncryptedStorage`, using AES-256-GCM with SecureStore-backed key storage and AsyncStorage ciphertext only.  

---

# A. P0 — Production Blockers / Must-Fix First

| ID    | What                                                                                              | Where                                                                                                                                                                | Why / Acceptance Criteria                                                                                                                                                                                                                    |
| ----- | ------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| P0-01 | Replace remaining role-based PHI access shortcuts with a real Kernel-backed PHI policy evaluator. | `products/phr/src/main/java/com/ghatana/phr/api/routes/PhrRouteSupport.java`; all PHR route adapters                                                                 | `canAccessPatientRecordForRole` still allows `admin` and `clinician` broadly and allows caregiver provisionally. Replace with policy checks for consent, treatment relationship, facility scope, emergency override, and audit requirement.  |
| P0-02 | Remove or fully eliminate usage of deprecated `isPrivileged`.                                     | `PhrRouteSupport.java`, all usages in `PhrPatientRecordRoutes.java`, `PhrAdministrativeRoutes.java`, `PhrReleaseReadinessRoutes.java`, `PhrEmergencyRoutes.java`     | Deprecated privileged role logic must not remain in PHI access paths. Acceptance: search for `isPrivileged(` returns no production route usage.                                                                                              |
| P0-03 | Fix backend route entitlement drift.                                                              | `products/phr/apps/web/src/phrRouteContracts.ts`; `products/phr/src/main/java/com/ghatana/phr/api/routes/PhrEntitlementRoutes.java`                                  | Web has many routes, but backend entitlement route still returns only the older core 10-route list. Generate both from one Kernel product-route contract.                                                                                    |
| P0-04 | Fail closed on entitlement requests with missing identity headers.                                | `PhrEntitlementRoutes.java`                                                                                                                                          | Current entitlement route defaults missing principal/tenant/role to `anonymous/default/patient`; that is unsafe for production route/content entitlement. Replace defaults with `PhrRouteSupport.requireContext`.                            |
| P0-05 | Add canonical machine-readable PHR IA/use-case baseline.                                          | New: `products/phr/config/phr-usecase-baseline.json`; new generated evidence: `.kernel/evidence/phr/ia-coverage.json`                                                | The IA requires broad patient/provider/caregiver/FCHV/admin routes and workflows. Every IA item must map to route, page, API, service, Kernel dependency, test, and status.                                                                  |
| P0-06 | Add PHR IA coverage check to CI.                                                                  | New: `scripts/check-phr-ia-coverage.mjs`; root `package.json` scripts                                                                                                | Fails if a route/workflow in docs is not implemented, feature-flagged, explicitly deferred, or removed from canonical IA.                                                                                                                    |
| P0-07 | Harden mobile encrypted PHI key management.                                                       | `products/phr/apps/mobile/src/services/phiEncryptedStorage.ts`                                                                                                       | Current encryption exists, but key is exportable and stored for reuse. Add key rotation, biometric/keychain access policy where supported, reinstall/tamper handling tests, and documented device-risk model.                                |
| P0-08 | Verify no PHI is ever written directly to AsyncStorage outside the encrypted adapter.             | New: `scripts/check-phr-mobile-phi-storage.mjs`; mobile source                                                                                                       | Enforce the module comment: PHI modules must use `phiEncryptedStorage`; direct `AsyncStorage.setItem` with PHI must fail CI.                                                                                                                 |
| P0-09 | Clear encrypted PHI cache on consent revocation, logout, session expiry, and role/persona change. | `offlineStore.ts`, `phrMobileApi.ts`, `App.tsx`, `ConsentScreen.tsx`                                                                                                 | Logout clears cache now, but revocation must also call cache clear or scoped invalidation, not just local consent list removal.                                                                                                              |
| P0-10 | Add server-side `/mobile/dashboard` implementation or prove it exists and is protected.           | Backend routes, likely new `PhrMobileRoutes.java`; `PhrHttpServer.java`; `phrMobileApi.ts`                                                                           | Mobile calls `/mobile/dashboard` with tenant/principal/role headers; backend must enforce policy and return scoped mobile payload.                                                                                                           |
| P0-11 | Ensure mobile consent revocation endpoint matches backend route contract.                         | `products/phr/apps/mobile/src/services/phrMobileApi.ts`; `PhrConsentRoutes.java`                                                                                     | Mobile calls `/consents/{grantId}/revoke`, while web/backend pattern appears `/consents/grants/{grantId}/revoke?patientId=...`. Align contract.                                                                                              |
| P0-12 | Move release-readiness file parsing behind Kernel runtime API.                                    | `PhrReleaseReadinessRoutes.java`; Kernel lifecycle/readiness package                                                                                                 | PHR should consume Kernel release/evidence service, not own release evidence semantics.                                                                                                                                                      |
| P0-13 | Add PHI-safe logging and diagnostics gate.                                                        | Backend routes, mobile `App.tsx`, web API errors, new script `scripts/check-phr-phi-log-safety.mjs`                                                                  | No patient ID, record title, FHIR payload, national ID, phone, or push token in logs/errors/diagnostics.                                                                                                                                     |
| P0-14 | Add emergency break-glass server policy gate.                                                     | `PhrEmergencyRoutes.java`, `EmergencyAccessLogService.java`, `EmergencyAccessReviewWorkflow.java`, mobile `EmergencyAccessScreen.tsx`, web `EmergencyAccessPage.tsx` | Emergency flow must require reason, user role, patient scope, immutable audit, notification, and review status before PHI reveal.                                                                                                            |
| P0-15 | Create PHR production-critical E2E test matrix.                                                   | New `products/phr/tests/e2e/phr-critical-journeys.spec.ts` or equivalent                                                                                             | Covers login, dashboard, records, consent grant/revoke, unauthorized access, emergency access, mobile offline, audit, release cockpit.                                                                                                       |

---

# B. PHR IA / Vision / Documentation Tasks

The product vision and IA still define the target product surface: records, prescriptions, labs, appointment history, clinical notes, imaging, immunizations, referrals, caregiver access, telemedicine, and emergency break-glass.  The IA also expects nested patient/provider/caregiver/FCHV/admin route groups and shared shells/states. 

| ID   | Priority | What                                                                                                                            | Where                                                                                                                                           |
| ---- | -------- | ------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| B-01 | P0       | Create `phr-usecase-baseline.json` with every persona/use case from vision and IA.                                              | `products/phr/config/phr-usecase-baseline.json`                                                                                                 |
| B-02 | P0       | Add IA status values: `implemented`, `partial`, `feature_flagged`, `backend_only`, `ui_only`, `missing`, `deferred`, `removed`. | Same baseline file                                                                                                                              |
| B-03 | P0       | Add traceability from each IA item to web route, mobile screen, API route, service, Kernel capability, tests.                   | Same baseline file                                                                                                                              |
| B-04 | P1       | Generate human-readable IA coverage doc from baseline.                                                                          | `products/phr/docs/04_design_and_workflows/phr_ia_coverage_matrix.md`                                                                           |
| B-05 | P1       | Update product vision “Mobile App planned/not started” wording because mobile now exists and is in progress.                    | `products/phr/docs/01-vision-plan-requirements/01-product-vision.md` lines 81–83 and 167–169 currently conflict with the current mobile code.   |
| B-06 | P1       | Update maturity assessment that says frontend/mobile “Not Started”; current web/mobile code exists.                             | `01-product-vision.md` lines 183–194.                                                                                                           |
| B-07 | P1       | Split IA into MVP-current, MVP-next, Phase 2, and removed/deferred buckets.                                                     | `phr_frontend_route_and_component_map.md`, `phr_screen_by_screen_mvp_implementation_matrix.md`                                                  |
| B-08 | P1       | Add feature visibility file.                                                                                                    | New `products/phr/config/phr-feature-visibility.json`                                                                                           |
| B-09 | P1       | Add generated doc/code mismatch evidence.                                                                                       | New `.kernel/evidence/phr/doc-code-mismatch.json`                                                                                               |
| B-10 | P2       | Add release checklist that requires no “complete” doc claim without code/test/evidence.                                         | `products/phr/docs/01_governance/`                                                                                                              |
| B-11 | P2       | Add ADR for PHR IA as Kernel-managed product contract.                                                                          | `docs/adr/ADR-phr-kernel-ia-contract.md`                                                                                                        |

---

# C. PHR Web App Tasks

## C1. Route / Shell / Entitlement

| ID    | Priority | What                                                                                         | Where                                                                                       |
| ----- | -------- | -------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| C1-01 | P0       | Generate `phrRouteContracts.ts` from Kernel product route contract.                          | `products/phr/apps/web/src/phrRouteContracts.ts`                                            |
| C1-02 | P0       | Generate backend entitlement route data from the same contract.                              | `PhrEntitlementRoutes.java` or replacement Kernel-backed adapter                            |
| C1-03 | P0       | Add route entitlement parity test.                                                           | New `products/phr/apps/web/src/__tests__/route-entitlement-parity.test.ts` and backend test |
| C1-04 | P1       | Add route lifecycle metadata for every route: stable, preview, boundary, hidden, deprecated. | `phrRouteContracts.ts` / generated route contract                                           |
| C1-05 | P1       | Enforce feature-flagged routes are not discoverable in production unless enabled.            | `PhrProductShell.tsx`, route contract                                                       |
| C1-06 | P1       | Add nested route grouping or document why current flat route style is acceptable.            | `routes.tsx`, `PhrProductShell.tsx`                                                         |
| C1-07 | P1       | Add `/sign-in` alias or update IA to `/login` only.                                          | `routes.tsx`, IA docs                                                                       |
| C1-08 | P1       | Add route-level loading/error/empty/unauthorized standard components.                        | `products/phr/apps/web/src/pages/**`                                                        |
| C1-09 | P2       | Add breadcrumb/title metadata per route.                                                     | route contract + shell                                                                      |
| C1-10 | P2       | Add keyboard and focus traversal tests for nav/sidebar/header/user menu.                     | Playwright web tests                                                                        |

## C2. Auth / Session

| ID    | Priority | What                                                                         | Where                                                                             |
| ----- | -------- | ---------------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| C2-01 | P0       | Ensure frontend logout invalidates backend session and clears local session. | `PhrSessionContext.tsx`, `PhrProductShell.tsx`, `phrApi.ts`, `PhrAuthRoutes.java` |
| C2-02 | P1       | Add session expiry timer and redirect flow.                                  | `PhrSessionContext.tsx`                                                           |
| C2-03 | P1       | Add session restore validation with backend `/auth/me`.                      | `PhrSessionContext.tsx`, backend auth route                                       |
| C2-04 | P1       | Add locked account / inactive account / MFA required states.                 | `LoginPage.tsx`, backend auth                                                     |
| C2-05 | P1       | Add auth error focus management for accessibility.                           | `LoginPage.tsx`                                                                   |
| C2-06 | P2       | Add audit event for login success/failure/logout.                            | `PhrAuthRoutes.java`, audit service                                               |

## C3. Dashboard

| ID    | Priority | What                                                                                                                                    | Where                                                           |
| ----- | -------- | --------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------- |
| C3-01 | P1       | Replace frontend-composed dashboard with backend-owned dashboard endpoint.                                                              | New `PhrDashboardRoutes.java`, `phrApi.ts`, `DashboardPage.tsx` |
| C3-02 | P1       | Include IA-required widgets: profile summary, next appointment, meds, recent observations, active conditions, documents, access alerts. | `DashboardPage.tsx`, backend dashboard DTO                      |
| C3-03 | P1       | Add caregiver-scoped dashboard variant.                                                                                                 | Dashboard route/API                                             |
| C3-04 | P1       | Add clinician/admin dashboard behavior or redirect to provider/admin dashboard.                                                         | route contract + shell                                          |
| C3-05 | P2       | Add freshness/sync indicators.                                                                                                          | Dashboard UI/API                                                |
| C3-06 | P2       | Add dashboard no-data and partial-data states.                                                                                          | Dashboard page                                                  |

## C4. Profile

| ID    | Priority | What                                                                                | Where                                                          |
| ----- | -------- | ----------------------------------------------------------------------------------- | -------------------------------------------------------------- |
| C4-01 | P1       | Verify `ProfilePage` uses backend `fetchPatientProfile` and `updatePatientProfile`. | `products/phr/apps/web/src/pages/ProfilePage.tsx`, `phrApi.ts` |
| C4-02 | P1       | Add server-side profile validation and field-level permissions.                     | New/updated profile route                                      |
| C4-03 | P1       | Add audit for profile read/update.                                                  | Backend profile route                                          |
| C4-04 | P1       | Add caregiver/provider read-only behavior.                                          | `ProfilePage.tsx`, policy evaluator                            |
| C4-05 | P2       | Add profile validation error summaries and focus management.                        | `ProfilePage.tsx`                                              |

## C5. Records / Detail / FHIR

| ID    | Priority | What                                                                         | Where                                                         |
| ----- | -------- | ---------------------------------------------------------------------------- | ------------------------------------------------------------- |
| C5-01 | P1       | Verify records page uses backend records API instead of only dashboard data. | `RecordsPage.tsx`, `phrApi.ts`, `PhrPatientRecordRoutes.java` |
| C5-02 | P1       | Add list filters: category, date, facility, provider, resource type.         | `RecordsPage.tsx`, backend API                                |
| C5-03 | P1       | Add backend record-detail endpoint with policy/audit.                        | `PhrPatientRecordRoutes.java`                                 |
| C5-04 | P1       | Add safe FHIR viewer with redaction/copy/download policy.                    | `RecordDetailPage.tsx`                                        |
| C5-05 | P1       | Add invalid FHIR payload state.                                              | `RecordDetailPage.tsx`                                        |
| C5-06 | P2       | Add record provenance panel.                                                 | `RecordDetailPage.tsx`                                        |
| C5-07 | P2       | Add pagination/sorting.                                                      | records API/page                                              |

## C6. Timeline / Conditions / Observations / Immunizations

| ID    | Priority | What                                                                      | Where                                                  |
| ----- | -------- | ------------------------------------------------------------------------- | ------------------------------------------------------ |
| C6-01 | P1       | Verify `TimelinePage` has real API and not static placeholder data.       | `TimelinePage.tsx`, `fetchTimeline`, backend route     |
| C6-02 | P1       | Add timeline filters and grouping.                                        | `TimelinePage.tsx`, backend                            |
| C6-03 | P1       | Verify `ConditionsPage` is backend-backed.                                | `ConditionsPage.tsx`, `fetchConditions`, backend route |
| C6-04 | P1       | Add active/resolved condition grouping and detail navigation.             | Conditions page/API                                    |
| C6-05 | P1       | Verify `ObservationsPage` supports clinical trends, not just a flat list. | `ObservationsPage.tsx`, `fetchObservations`            |
| C6-06 | P1       | Add chart using shared chart/design-system primitives.                    | `ObservationsPage.tsx`, `@ghatana/charts`              |
| C6-07 | P1       | Verify `ImmunizationsPage` is backend-backed.                             | `ImmunizationsPage.tsx`, `fetchImmunizations`          |
| C6-08 | P1       | Add immunization retention/status display.                                | Immunizations UI/API                                   |
| C6-09 | P2       | Add LOINC/CVX code display where relevant.                                | observations/immunizations pages                       |

## C7. Labs / Medications

| ID    | Priority | What                                                             | Where                                                  |
| ----- | -------- | ---------------------------------------------------------------- | ------------------------------------------------------ |
| C7-01 | P1       | Ensure `/labs` and `/observations` do not duplicate confusingly. | `LabsPage.tsx`, `ObservationsPage.tsx`, route contract |
| C7-02 | P1       | Add lab detail view.                                             | New `LabDetailPage.tsx` or drawer                      |
| C7-03 | P1       | Add medication detail/history route.                             | `MedicationsPage.tsx`, new detail page                 |
| C7-04 | P1       | Add medication interaction/allergy warning path.                 | Medication API/page                                    |
| C7-05 | P2       | Add adherence source/confidence display.                         | Medications page/API                                   |

## C8. Appointments

| ID    | Priority | What                                                    | Where                                                                          |
| ----- | -------- | ------------------------------------------------------- | ------------------------------------------------------------------------------ |
| C8-01 | P1       | Convert appointment request into full booking workflow. | `AppointmentsPage.tsx`, `createAppointmentRequest`, backend appointment routes |
| C8-02 | P1       | Add provider selection and slot availability.           | Appointment UI/API                                                             |
| C8-03 | P1       | Add conflict/double-book prevention.                    | `AppointmentService.java`, tests                                               |
| C8-04 | P1       | Add upcoming/past tabs.                                 | Appointments page                                                              |
| C8-05 | P1       | Add cancel/reschedule actions with permissions.         | Appointments page/API                                                          |
| C8-06 | P2       | Add reminder/notification status.                       | Appointment API/page                                                           |

## C9. Documents / Upload / OCR

| ID    | Priority | What                                                                      | Where                                                                  |
| ----- | -------- | ------------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| C9-01 | P1       | Verify `DocumentsPage` is backend-backed.                                 | `DocumentsPage.tsx`, `fetchDocuments`, `PhrDocumentImagingRoutes.java` |
| C9-02 | P1       | Add document filters and secure download gate.                            | Documents page/API                                                     |
| C9-03 | P1       | Add upload metadata validation.                                           | `DocumentUploadPage.tsx`, `uploadDocument`, backend                    |
| C9-04 | P1       | Add file type/size validations on client and server.                      | Upload page/API                                                        |
| C9-05 | P1       | Add upload audit event.                                                   | Backend document route/service                                         |
| C9-06 | P1       | Verify `OcrReviewPage` supports accept/edit/reject and confidence review. | `OcrReviewPage.tsx`, OCR API                                           |
| C9-07 | P1       | Add OCR confirmation to FHIR draft creation with provenance.              | Backend OCR/document service                                           |
| C9-08 | P2       | Add document preview fallback.                                            | Documents UI                                                           |

## C10. Notifications

| ID     | Priority | What                                                 | Where                                                         |
| ------ | -------- | ---------------------------------------------------- | ------------------------------------------------------------- |
| C10-01 | P1       | Verify `NotificationsPage` is backend-backed.        | `NotificationsPage.tsx`, notification API                     |
| C10-02 | P1       | Ensure notifications never expose PHI in title/body. | backend notification service, web/mobile notification screens |
| C10-03 | P1       | Add read/unread and action state.                    | Notifications page/API                                        |
| C10-04 | P2       | Add notification preferences.                        | Settings/profile                                              |

## C11. Provider / Caregiver / FCHV

| ID     | Priority | What                                                                                              | Where                                                     |
| ------ | -------- | ------------------------------------------------------------------------------------------------- | --------------------------------------------------------- |
| C11-01 | P1       | Remove feature-flag status once provider dashboard is truly working; otherwise keep it hidden.    | `phrRouteContracts.ts`, `ProviderDashboardPage.tsx`       |
| C11-02 | P1       | Implement provider patient search with consent/treatment relationship policy.                     | `ProviderPatientsPage.tsx`, backend provider/search route |
| C11-03 | P1       | Add provider patient summary/detail route.                                                        | New provider detail page/API                              |
| C11-04 | P1       | Add provider encounter detail/update.                                                             | New encounter pages/API                                   |
| C11-05 | P1       | Add provider observation entry.                                                                   | New page/API                                              |
| C11-06 | P1       | Add provider medication request entry.                                                            | New page/API                                              |
| C11-07 | P1       | Add provider calendar.                                                                            | New page/API                                              |
| C11-08 | P1       | Implement caregiver dependents route beyond placeholder/minimal page.                             | `CaregiverDependentsPage.tsx`, caregiver backend          |
| C11-09 | P1       | Add caregiver dependent detail route.                                                             | New route/page/API                                        |
| C11-10 | P1       | Add FCHV role separate from caregiver if FCHV remains in IA.                                      | `PhrAccessContext.tsx`, Kernel role model, backend policy |
| C11-11 | P1       | Implement FCHV dashboard, patient registration, patient list, vitals capture or hide/defer in IA. | `FchvDashboardPage.tsx`, new pages/API                    |
| C11-12 | P2       | Add offline queue semantics for FCHV workflows.                                                   | mobile/web services                                       |

## C12. Admin / Audit / Release

| ID     | Priority | What                                                      | Where                                                 |
| ------ | -------- | --------------------------------------------------------- | ----------------------------------------------------- |
| C12-01 | P1       | Add admin dashboard page/API if IA keeps it.              | route contract, new `AdminDashboardPage.tsx`, backend |
| C12-02 | P1       | Ensure `AuditPage` uses real paginated backend audit API. | `AuditPage.tsx`, audit backend route                  |
| C12-03 | P1       | Add audit detail drawer/page.                             | `AuditPage.tsx`, backend detail endpoint              |
| C12-04 | P1       | Add audit export with privacy policy.                     | backend audit export + UI                             |
| C12-05 | P1       | Move release readiness to Kernel API.                     | `ReleaseCockpitPage.tsx`, Kernel readiness service    |
| C12-06 | P2       | Add release section drill-down and evidence links.        | Release cockpit                                       |
| C12-07 | P2       | Add rollback drill proof display.                         | Release cockpit                                       |

---

# D. PHR Mobile App Tasks

Mobile has improved: encrypted PHI storage, session headers for dashboard fetch, logout cleanup, i18n usage in the main app, offline banner, and accessible tabs now exist.    

| ID   | Priority | What                                                                                                | Where                                                                   |
| ---- | -------- | --------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| D-01 | P0       | Align mobile consent revoke endpoint with backend.                                                  | `phrMobileApi.ts`                                                       |
| D-02 | P0       | Ensure mobile consent revocation clears encrypted PHI cache or invalidates scoped data.             | `ConsentScreen.tsx`, `phrMobileApi.ts`, `offlineStore.ts`               |
| D-03 | P0       | Add secure session restore on app launch.                                                           | `mobileSessionStore.ts`, `App.tsx`                                      |
| D-04 | P0       | Validate session expiry on app foreground/resume.                                                   | `App.tsx`, session store                                                |
| D-05 | P0       | Harden encrypted key lifecycle: rotation, secure store access policy, tamper test.                  | `phiEncryptedStorage.ts`                                                |
| D-06 | P1       | Remove remaining raw user-visible strings from `App.tsx`.                                           | `App.tsx` lines with “PHR Nepal mobile”, error strings, sync strings.   |
| D-07 | P1       | Move API error messages to i18n.                                                                    | `phrMobileApi.ts`                                                       |
| D-08 | P1       | Add mobile locale files coverage test.                                                              | `src/i18n/**`, tests                                                    |
| D-09 | P1       | Add mobile record detail screen.                                                                    | New `RecordDetailScreen.tsx`                                            |
| D-10 | P1       | Add mobile profile/settings profile screen.                                                         | `SettingsScreen.tsx` or new screen                                      |
| D-11 | P1       | Add mobile offline stale-cache timestamp.                                                           | `offlineStore.ts`, `SettingsScreen.tsx`                                 |
| D-12 | P1       | Add emergency biometric success path that unlocks scoped emergency data only after server approval. | `EmergencyAccessScreen.tsx`, backend emergency API                      |
| D-13 | P1       | Redact push notification content.                                                                   | `pushNotifications.ts`, backend notification service                    |
| D-14 | P1       | Add NetInfo offline behavior tests.                                                                 | `App.tsx` tests                                                         |
| D-15 | P2       | Replace wrapping tab chips with a proper accessible bottom tab bar if visual overflow occurs.       | `App.tsx`                                                               |
| D-16 | P2       | Add large text/high-contrast mobile accessibility pass.                                             | mobile styles/screens                                                   |
| D-17 | P2       | Add mobile E2E smoke test for login → dashboard → records → logout.                                 | mobile test project                                                     |

---

# E. PHR Backend / API Tasks

| ID   | Priority | What                                                                      | Where                                                   |
| ---- | -------- | ------------------------------------------------------------------------- | ------------------------------------------------------- |
| E-01 | P0       | Add Kernel PHI policy evaluator and use it in every route.                | Kernel policy package; PHR route adapters               |
| E-02 | P0       | Replace entitlement defaults with required context.                       | `PhrEntitlementRoutes.java`                             |
| E-03 | P0       | Add `/mobile/dashboard` route with policy enforcement.                    | New `PhrMobileRoutes.java`, `PhrHttpServer.java`        |
| E-04 | P0       | Align consent revoke route contract across web/mobile/backend.            | `PhrConsentRoutes.java`, `phrApi.ts`, `phrMobileApi.ts` |
| E-05 | P1       | Add dashboard route contract.                                             | New `PhrDashboardRoutes.java`                           |
| E-06 | P1       | Add profile route contract if not already present.                        | profile backend route                                   |
| E-07 | P1       | Add timeline route contract.                                              | timeline backend route                                  |
| E-08 | P1       | Add conditions route contract.                                            | conditions backend route                                |
| E-09 | P1       | Add observation trends endpoint.                                          | `PhrClinicalRoutes.java`                                |
| E-10 | P1       | Add immunization endpoint coverage.                                       | `PhrClinicalRoutes.java`                                |
| E-11 | P1       | Add document upload/OCR confirmation endpoints with provenance.           | `PhrDocumentImagingRoutes.java`                         |
| E-12 | P1       | Add notification list/read endpoint.                                      | notification route/service                              |
| E-13 | P1       | Add provider patient search and summary endpoints.                        | new provider routes                                     |
| E-14 | P1       | Add caregiver dependent endpoints.                                        | new caregiver routes                                    |
| E-15 | P1       | Add FCHV endpoints if IA keeps FCHV in MVP.                               | new FCHV routes                                         |
| E-16 | P1       | Add audit event list/detail/export endpoint.                              | audit route/service                                     |
| E-17 | P1       | Add idempotency keys for create/update workflows.                         | consent, appointment, upload, emergency                 |
| E-18 | P1       | Add consistent error envelope with code/message/correlationId/details.    | `PhrRouteSupport.java`                                  |
| E-19 | P1       | Validate all request DTOs with shared schemas rather than manual parsing. | route adapters                                          |
| E-20 | P1       | Add tenant/facility/principal validation everywhere.                      | `PhrRouteSupport.java`, route adapters                  |
| E-21 | P2       | Add pagination/sorting/filter contract standard.                          | list endpoints                                          |
| E-22 | P2       | Add OpenAPI/contract generation for PHR APIs.                             | new PHR API contract package                            |

---

# F. PHR Domain / Healthcare Correctness Tasks

| ID   | Priority | What                                                                            | Where                                                               |
| ---- | -------- | ------------------------------------------------------------------------------- | ------------------------------------------------------------------- |
| F-01 | P0       | Define canonical PHI/PII field classification.                                  | `products/phr/src/main/java/com/ghatana/phr/kernel/policy/**`, docs |
| F-02 | P0       | Apply field classification to logs, audit, export, mobile cache, notifications. | backend/mobile/web                                                  |
| F-03 | P1       | Add consent scope at resource/action/field level.                               | `ConsentManagementService.java`, Kernel policy                      |
| F-04 | P1       | Add consent expiry invalidation evidence and tests.                             | consent service/evidence                                            |
| F-05 | P1       | Add treatment relationship model for provider access.                           | backend domain/policy                                               |
| F-06 | P1       | Add record provenance for FHIR, document, OCR, voice, HIE imports.              | PHR services/schemas                                                |
| F-07 | P1       | Add HIE export/import user-facing workflow.                                     | HIE services/controllers + UI                                       |
| F-08 | P1       | Add HL7 lab import E2E proof.                                                   | `Hl7LabResultIntegrationService`, lab UI/tests                      |
| F-09 | P1       | Add medication interaction/allergy safety workflow.                             | medication service/API/UI                                           |
| F-10 | P1       | Add clinical decision support human-review/safety boundary.                     | `ClinicalDecisionSupportService.java`, UI                           |
| F-11 | P1       | Add imaging secure download/audit path.                                         | imaging services/routes/UI                                          |
| F-12 | P2       | Add Nepal-specific identifier/facility/provider validation.                     | patient/provider services                                           |
| F-13 | P2       | Add retention proof per resource type.                                          | Kernel evidence + PHR service metadata                              |

---

# G. Kernel Hardening Tasks Required by PHR

PHR is partially Kernel-native via `PhrKernelModule`, which declares capabilities/dependencies, registers services, event handlers, evidence outbox, FHIR/HIE/HL7 services, routes, and schema contracts.  

| ID   | Priority | What                                                                                     | Where                                           |
| ---- | -------- | ---------------------------------------------------------------------------------------- | ----------------------------------------------- |
| G-01 | P0       | Add canonical Kernel product route/capability contract.                                  | Kernel product lifecycle/contract packages      |
| G-02 | P0       | Generate PHR web manifest and backend entitlement payload from the same Kernel contract. | Kernel generator + PHR generated files          |
| G-03 | P0       | Add Kernel PHI policy evaluator.                                                         | Kernel security/privacy package                 |
| G-04 | P0       | Add Kernel mobile PHI offline storage gate.                                              | Kernel release/security checks                  |
| G-05 | P1       | Add Kernel release readiness runtime API.                                                | Kernel lifecycle/readiness package              |
| G-06 | P1       | Move product evidence-file parsing into Kernel readiness service.                        | Kernel readiness                                |
| G-07 | P1       | Add product IA/use-case completeness contract.                                           | Kernel product lifecycle schema                 |
| G-08 | P1       | Add route entitlement drift check.                                                       | `scripts/check-route-entitlement-contracts.mjs` |
| G-09 | P1       | Add product web/mobile i18n and a11y gates.                                              | `scripts/check-i18n-*`, `scripts/check-a11y-*`  |
| G-10 | P1       | Add Kernel audit/event correlation context.                                              | Kernel event/context packages                   |
| G-11 | P1       | Add product-use-case readiness score.                                                    | Kernel scorecard/evidence                       |
| G-12 | P2       | Generate docs from product route/use-case contracts.                                     | Kernel docs generator                           |
| G-13 | P2       | Add lifecycle “explain/recover” for missing PHR IA tasks.                                | Kernel lifecycle service                        |

---

# H. YAPPC Hardening Tasks Required by PHR

| ID   | Priority | What                                                                                                 | Where                                                               |
| ---- | -------- | ---------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------- |
| H-01 | P0       | Add PHR IA importer.                                                                                 | `products/yappc/core/**`, `products/yappc/frontend/web/**`          |
| H-02 | P0       | Convert PHR IA baseline into YAPPC product model.                                                    | YAPPC product modeling layer                                        |
| H-03 | P0       | Visualize missing PHR IA/code/test coverage on YAPPC canvas.                                         | YAPPC canvas UI                                                     |
| H-04 | P1       | Add Kernel-native product artifact generation mode.                                                  | `products/yappc/core/scaffold/api/**`                               |
| H-05 | P1       | Generate PHR route contracts from IA baseline.                                                       | YAPPC scaffold pack + Kernel contract generator                     |
| H-06 | P1       | Generate PHR API skeletons from IA baseline.                                                         | YAPPC backend templates                                             |
| H-07 | P1       | Generate PHR web page skeletons with product-shell/design-system usage.                              | YAPPC web templates                                                 |
| H-08 | P1       | Generate PHR mobile screen skeletons with i18n/a11y requirements.                                    | YAPPC mobile templates                                              |
| H-09 | P1       | Generate E2E/API/unit test skeletons per IA use case.                                                | YAPPC test templates                                                |
| H-10 | P1       | Treat missing Canvas AI backend endpoints as visible readiness gaps, not silent no-op in production. | `products/yappc/frontend/web/src/lib/canvas-ai/yappc-ai-adapter.ts` |
| H-11 | P1       | Add PHR product-unit scaffold pack.                                                                  | `products/yappc/core/scaffold/packs/phr-*`                          |
| H-12 | P2       | Add web/mobile parity report generation.                                                             | YAPPC product analysis                                              |
| H-13 | P2       | Add privacy/security/i18n/a11y annotations to generated plans.                                       | YAPPC planning model                                                |

---

# I. i18n / a11y / o11y / Security Gates

| ID   | Priority | What                                                                        | Where                                        |
| ---- | -------- | --------------------------------------------------------------------------- | -------------------------------------------- |
| I-01 | P0       | Add PHR raw user-visible string check for web and mobile.                   | New `scripts/check-phr-i18n-conformance.mjs` |
| I-02 | P1       | Convert remaining mobile raw strings in `App.tsx` and services to `t(...)`. | mobile app/services                          |
| I-03 | P1       | Add pseudo-locale route/screen tests.                                       | web/mobile tests                             |
| I-04 | P1       | Add route-level WCAG checklist generated from IA.                           | `phr-usecase-baseline.json`                  |
| I-05 | P1       | Add accessibility tests for web route traversal.                            | Playwright                                   |
| I-06 | P1       | Add mobile accessibility tests for tabs/buttons/forms.                      | mobile tests                                 |
| I-07 | P0       | Add PHI log redaction gate.                                                 | `scripts/check-phr-phi-log-safety.mjs`       |
| I-08 | P1       | Add correlation ID propagation tests.                                       | web/mobile/backend tests                     |
| I-09 | P1       | Add evidence outbox health to release gate.                                 | Kernel/PHR readiness checks                  |
| I-10 | P1       | Add no-PHI push notification test.                                          | notification tests                           |

---

# J. Test and CI Tasks

| ID   | Priority | What                                                                                            | Where                                                    |
| ---- | -------- | ----------------------------------------------------------------------------------------------- | -------------------------------------------------------- |
| J-01 | P0       | Add `check:phr-ia-coverage`.                                                                    | root `package.json`, `scripts/check-phr-ia-coverage.mjs` |
| J-02 | P0       | Add `check:phr-mobile-phi-storage`.                                                             | root `package.json`, new script                          |
| J-03 | P0       | Add `check:phr-policy-enforcement`.                                                             | backend tests/script                                     |
| J-04 | P0       | Add consent enforcement matrix tests.                                                           | PHR backend tests                                        |
| J-05 | P0       | Add emergency break-glass E2E tests.                                                            | backend/web/mobile tests                                 |
| J-06 | P1       | Add web route traversal E2E for every route contract.                                           | `products/phr/apps/web/e2e/**`                           |
| J-07 | P1       | Add web action E2E: login, consent, appointment, upload, OCR, audit, release.                   | web E2E                                                  |
| J-08 | P1       | Add mobile tests: login, dashboard, cache, revoke, logout, offline, emergency.                  | mobile tests                                             |
| J-09 | P1       | Add backend API tests: auth, records, consent, clinical, docs, emergency, release, entitlement. | backend tests                                            |
| J-10 | P1       | Add route entitlement parity test.                                                              | web + backend                                            |
| J-11 | P1       | Add i18n key coverage tests.                                                                    | web/mobile                                               |
| J-12 | P1       | Add a11y tests.                                                                                 | web/mobile                                               |
| J-13 | P1       | Add YAPPC PHR IA import/generate/validate roundtrip test.                                       | YAPPC tests                                              |
| J-14 | P2       | Add performance tests for records, audit, documents, FHIR.                                      | backend/perf                                             |
| J-15 | P2       | Add HIE/HL7/FHIR integration smoke tests.                                                       | integration tests                                        |

---

# K. Documentation / Governance Tasks

| ID   | Priority | What                                                                       | Where                                                                |
| ---- | -------- | -------------------------------------------------------------------------- | -------------------------------------------------------------------- |
| K-01 | P1       | Update PHR vision to reflect current web/mobile implementation accurately. | `products/phr/docs/01-vision-plan-requirements/01-product-vision.md` |
| K-02 | P1       | Add PHR current-surface generated doc.                                     | `products/phr/docs/current-state/generated-current-surface.md`       |
| K-03 | P1       | Add access policy matrix.                                                  | `products/phr/docs/security/phr_access_policy_matrix.md`             |
| K-04 | P1       | Add mobile offline PHI security doc.                                       | `products/phr/docs/security/phr_mobile_offline_phi.md`               |
| K-05 | P1       | Add emergency access runbook.                                              | `products/phr/docs/runbooks/phr_emergency_access.md`                 |
| K-06 | P1       | Add consent revocation/cache invalidation runbook.                         | `products/phr/docs/runbooks/phr_consent_revocation.md`               |
| K-07 | P1       | Add Kernel-native PHR lifecycle doc.                                       | `products/phr/docs/03_architecture/phr_kernel_native_lifecycle.md`   |
| K-08 | P2       | Add YAPPC-for-PHR generation workflow doc.                                 | `products/yappc/docs/use-cases/phr_generation_workflow.md`           |
| K-09 | P2       | Add ADR for route entitlement canonicalization.                            | `docs/adr/ADR-phr-route-entitlement-canonicalization.md`             |

---

# L. Execution Order

1. **P0 security and correctness:** policy evaluator, entitlement fail-closed, route drift, mobile revoke contract, mobile cache invalidation.
2. **Canonical IA baseline:** `phr-usecase-baseline.json`, IA coverage script, generated evidence.
3. **Kernel route/entitlement canonicalization:** one source for web/backend entitlements.
4. **Current patient web journeys:** dashboard, profile, records, consent, appointments, labs, meds, docs, OCR, notifications.
5. **Mobile hardening:** session restore, raw strings, revoke/cache clear, record detail, emergency authorization.
6. **Provider/caregiver/FCHV:** remove placeholders by either implementing or hiding/defering in IA.
7. **Emergency/audit/release:** immutable audit, review, release readiness through Kernel.
8. **YAPPC acceleration:** PHR IA importer, Kernel-native generation, gap visualization.
9. **Full CI gates:** IA, policy, mobile PHI, i18n, a11y, o11y, backend API, web/mobile E2E.
10. **Re-audit and rescore.**

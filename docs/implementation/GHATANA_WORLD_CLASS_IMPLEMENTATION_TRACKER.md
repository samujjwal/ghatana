Below is the reorganized implementation backlog for commit `25810a0cbcc9e37e7a0f0b969207e822b5c3d5b2`, grouped by **files or tightly related file sets** so implementation and verification can be done in fewer passes.

The current snapshot already moved some earlier blockers forward: the route contract is now richer, `fchv` is a first-class PHR role, route entitlements load from `products/phr/config/phr-route-contract.json`, mobile dashboard calls now send tenant/principal/role headers, and mobile offline PHI caching uses an encrypted storage adapter.    

I am not including evidence-generation work except where a check is needed to verify implementation correctness.

---

# Verification Strategy

Use **7 grouped verification passes** instead of one pass per task.

| Pass | Scope                                   | Run after completing                |
| ---- | --------------------------------------- | ----------------------------------- |
| V1   | Backend compile + route wiring          | Groups 1–3                          |
| V2   | Backend security/policy/API contracts   | Groups 3–5                          |
| V3   | Route contract drift + navigation       | Group 2 + related web shell changes |
| V4   | Web route/page E2E                      | Groups 6–7                          |
| V5   | Mobile privacy/session/offline E2E      | Group 8                             |
| V6   | Kernel/YAPPC generation/contract checks | Groups 9–10                         |
| V7   | Full required product check             | All groups                          |

---

# Group 1 — Backend Composition and Route Wiring

## Files

```text
products/phr/src/main/java/com/ghatana/phr/kernel/PhrKernelModule.java
products/phr/src/main/java/com/ghatana/phr/api/PhrHttpServer.java
products/phr/src/main/java/com/ghatana/phr/api/routes/PhrAuthRoutes.java
products/phr/src/main/java/com/ghatana/phr/api/routes/PhrAuditRoutes.java
products/phr/src/main/java/com/ghatana/phr/api/routes/PhrProviderRoutes.java
products/phr/src/main/java/com/ghatana/phr/api/routes/PhrCaregiverRoutes.java
products/phr/src/main/java/com/ghatana/phr/api/routes/PhrFchvRoutes.java
products/phr/src/main/java/com/ghatana/phr/api/routes/PhrNotificationRoutes.java
```

## Tasks

| ID     | Priority | Change                                                                                                                                                                                                              |
| ------ | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| ~~G1-001~~ | P0 ✅ | Instantiate and wire `PhrAuthRoutes` in `PhrKernelModule`; currently `PhrHttpServer` supports `/auth/*`, but `PhrKernelModule` still passes `null` for `authRoutes`.                                                |
| ~~G1-002~~ | P0 ✅ | Instantiate and wire `PhrAuditRoutes`; do not keep audit as a nullable route.                                                                                                                                       |
| ~~G1-003~~ | P1 ✅ | Instantiate and wire `PhrProviderRoutes`; `/provider/*` is mounted only when non-null.                                                                                                                              |
| ~~G1-004~~ | P1 ✅ | Instantiate and wire `PhrCaregiverRoutes`; `/caregiver/*` is mounted only when non-null.                                                                                                                            |
| ~~G1-005~~ | P1 ✅ | Instantiate and wire `PhrFchvRoutes`; `/fchv/*` is mounted only when non-null.                                                                                                                                      |
| ~~G1-006~~ | P1 ✅ | Instantiate and wire `PhrNotificationRoutes`; `/notifications/*` is mounted only when non-null.                                                                                                                     |
| ~~G1-007~~ | P0 ✅ | Delete legacy nullable route wiring from production constructors in `PhrHttpServer`; keep test fixtures explicit instead of allowing production `null` route adapters.                                              |
| ~~G1-008~~ | P0 ✅ | Replace overloaded partial constructors with one production constructor that requires all production route adapters.                                                                                                |
| ~~G1-009~~ | P1 ✅ | Keep a dedicated test-only factory if tests need partial route sets; do not expose partial/null constructors in production code.                                                                                    |
| ~~G1-010~~ | P0 ✅ | Verify `/release-readiness` route mounting. `PhrHttpServer` mounts `/release-readiness` directly while the release servlet exposes `/`; confirm ActiveJ behavior and change to `/release-readiness/*` if required.  |

## Verification Pass V1

Run backend compile and HTTP route smoke tests:

```bash
./gradlew :products:phr:compileJava
./gradlew :products:phr:test --tests '*PhrHttpServer*'
```

Add smoke coverage for:

```text
/auth/login
/auth/logout
/audit/events
/provider/dashboard
/provider/patients
/caregiver/dependents
/fchv/dashboard
/notifications
/mobile/dashboard
/release-readiness
```

---

# Group 2 — Canonical Route Contract, Entitlements, and Web Route Map

## Files

```text
products/phr/config/phr-route-contract.json
products/phr/apps/web/src/phrRouteContracts.ts
products/phr/apps/web/src/phrRouteElements.tsx
products/phr/apps/web/src/routes.tsx
products/phr/apps/web/src/layout/PhrProductShell.tsx
products/phr/src/main/java/com/ghatana/phr/api/routes/PhrEntitlementRoutes.java
platform/typescript/product-shell/src/**
```

## Tasks

| ID     | Priority | Change                                                                                                                                                                     |
| ------ | -------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| ~~G2-001~~ | P0 ✅ | Make `products/phr/config/phr-route-contract.json` the **only editable PHR route source**. The TS file currently duplicates the route contract.                            |
| ~~G2-002~~ | P0 ✅ | Generate `phrRouteContracts.ts` from `phr-route-contract.json`; mark generated file clearly.                                                                               |
| ~~G2-003~~ | P0 ✅ | Delete manual route duplication in `phrRouteContracts.ts` after generation is in place.                                                                                    |
| ~~G2-004~~ | P0 ✅ | Ensure backend `PhrEntitlementRoutes` reads the same canonical contract and returns all needed route metadata. It already loads JSON and fails closed if missing/invalid.  |
| ~~G2-005~~ | P0 ✅ | Add required route metadata for every stable route: `apiEndpoint`, `policyId`, `testId`. Several stable entries still lack these.                                          |
| ~~G2-006~~ | P0 ✅ | Enforce `hidden` route behavior consistently. Hidden routes currently map to real pages in `phrRouteElements.tsx`.                                                         |
| ~~G2-007~~ | P0 ✅ | Delete the older `featureFlag` model after `stability` is canonical. The TS interface still has both `featureFlag` and `stability`.                                        |
| ~~G2-008~~ | P1 ✅ | Move allowed stability values into the platform route contract: `stable`, `preview`, `blocked`, `hidden`.                                                                  |
| ~~G2-009~~ | P1 ✅ | Add JSON schema for `phr-route-contract.json`.                                                                                                                             |
| ~~G2-010~~ | P1 ✅ | Update `PhrEntitlementRoutes` to preserve `apiEndpoint`, `policyId`, `testId`, `stability`, `group`, `personas`, and `tiers` in entitlement output.                        |
| ~~G2-011~~ | P1 ✅ | Remove dual path lookup for route contract once runtime packaging is fixed. Current route loader searches repo path then module path.                                      |
| ~~G2-012~~ | P1 ✅ | Add `fchv` role support to shared product-shell role evaluator if not already supported by generated role order. The PHR contract now includes `fchv`.                     |
| ~~G2-013~~ | P1 ✅ | Add route contract drift check: JSON ↔ generated TS ↔ backend entitlement response.                                                                                        |
| ~~G2-014~~ | P2 ✅ | Add navigation grouping test to ensure stable routes appear, hidden routes do not appear, and blocked routes produce forbidden UI.                                         |

## Verification Pass V3

```bash
pnpm check:route-entitlement-contracts
pnpm check:product-ui-contracts
pnpm --dir products/phr/apps/web test
```

Add a dedicated check:

```bash
node scripts/check-phr-route-contract-drift.mjs
```

Expected assertions:

```text
phr-route-contract.json is valid
phrRouteContracts.ts is generated from it
PhrEntitlementRoutes returns the same stable/hidden route set
Hidden routes are not discoverable
Direct hidden route access is denied unless explicitly enabled
```

---

# Group 3 — PHR Policy, Route Support, and Security Helpers

## Files

```text
products/phr/src/main/java/com/ghatana/phr/security/PhrPolicyEvaluator.java
products/phr/src/main/java/com/ghatana/phr/api/routes/PhrRouteSupport.java
products/phr/src/main/java/com/ghatana/phr/api/routes/PhrPatientRecordRoutes.java
products/phr/src/main/java/com/ghatana/phr/api/routes/PhrClinicalRoutes.java
products/phr/src/main/java/com/ghatana/phr/api/routes/PhrConsentRoutes.java
products/phr/src/main/java/com/ghatana/phr/api/routes/PhrAdministrativeRoutes.java
products/phr/src/main/java/com/ghatana/phr/api/routes/PhrDocumentImagingRoutes.java
products/phr/src/main/java/com/ghatana/phr/api/routes/PhrEmergencyRoutes.java
```

## Tasks

| ID     | Priority | Change                                                                                                                                                                                                                  |
| ------ | -------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| ~~G3-001~~ | P0 ✅ | Delete deprecated sync policy methods in `PhrPolicyEvaluator`. No deprecation path; fix forward only.                                                                                                                   |
| ~~G3-002~~ | P0 ✅ | Make all PHI access paths call async `PhrPolicyEvaluator` only.                                                                                                                                                         |
| ~~G3-003~~ | P0 ✅ | Make `PhrPolicyEvaluator` mandatory in `PhrPatientRecordRoutes`; delete nullable constructor and fallback path. Current patient route accepts null policy evaluator and falls back.                                     |
| ~~G3-004~~ | P0 ✅ | Remove any production use of role-only PHI access.                                                                                                                                                                      |
| ~~G3-005~~ | P0 ✅ | Replace wildcard caregiver consent check with resource-specific consent scope. Current caregiver check validates `*`.                                                                                                   |
| ~~G3-006~~ | P0 ✅ | Require explicit admin PHI justification. Current admin access is allowed with audit requirement but no enforced justification.                                                                                         |
| ~~G3-007~~ | P0 ✅ | Require clinician consent-management authorization. Current clinician consent-management path allows clinician directly.                                                                                                |
| ~~G3-008~~ | P0 ✅ | Apply `PhrPolicyEvaluator` to `PhrClinicalRoutes`; current clinical route still allows patient self or admin directly and otherwise uses consent service, not policy evaluator.                                         |
| ~~G3-009~~ | P0 ✅ | Apply `PhrPolicyEvaluator` to administrative, document/imaging, consent, emergency, provider, caregiver, FCHV routes.                                                                                                   |
| ~~G3-010~~ | P1 ✅ | Move tenant/principal/facility validation to a shared Kernel policy helper later; for now keep PHR helper but make behavior real. Current facility scope comments say full policy would query assignment but does not.  |
| ~~G3-011~~ | P1 ✅ | Emit audit event whenever `PolicyDecision.requiresAudit()` or `isEmergencyOverride()` is true.                                                                                                                          |
| ~~G3-012~~ | P1 ✅ | Use correlation-aware responses consistently in all routes. `PhrRouteSupport` now has correlation-aware helpers.                                                                                                        |
| ~~G3-013~~ | P1 ✅ | Consolidate duplicated idempotency helpers. `PhrRouteSupport` has both `extractIdempotencyKey` and `getIdempotencyKey` patterns.                                                                                        |
| ~~G3-014~~ | P1 ✅ | Add actual idempotency persistence for mutation routes; do not only parse headers.                                                                                                                                      |
| ~~G3-015~~ | P2 ✅ | Replace restricted-field substring matching with canonical field classification registry. Current matching is heuristic.                                                                                                |

## Verification Pass V2

```bash
./gradlew :products:phr:test --tests '*Policy*'
./gradlew :products:phr:test --tests '*Route*'
```

Minimum access matrix:

```text
patient own record: allow
patient other patient: deny
caregiver with specific grant: allow
caregiver without grant: deny
clinician with treatment relationship: allow
clinician without relationship: deny unless emergency flow
admin without justification: deny
admin with justification: allow + audit
fchv assigned community: allow limited scope
fchv outside assignment: deny
```

---

# Group 4 — Backend Contract Alignment for Current Stable Routes

## Files

```text
products/phr/src/main/java/com/ghatana/phr/api/routes/PhrPatientRecordRoutes.java
products/phr/src/main/java/com/ghatana/phr/api/routes/PhrClinicalRoutes.java
products/phr/src/main/java/com/ghatana/phr/api/routes/PhrMobileRoutes.java
products/phr/src/main/java/com/ghatana/phr/api/routes/PhrDocumentImagingRoutes.java
products/phr/src/main/java/com/ghatana/phr/api/routes/PhrNotificationRoutes.java
products/phr/apps/web/src/api/phrApi.ts
products/phr/apps/mobile/src/services/phrMobileApi.ts
products/phr/apps/mobile/src/types.ts
products/phr/apps/web/src/types.ts
```

## Tasks

| ID     | Priority | Change                                                                                                                                                |
| ------ | -------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| ~~G4-001~~ | P0 ✅ | Fix mobile records DTO mismatch. Mobile validates `fhirPreview`, while backend mobile route returns document metadata without `fhirPreview`.          |
| ~~G4-002~~ | P0 ✅ | Fix mobile notifications DTO mismatch. Mobile validates `title/detail`, while backend returns `type/referenceId/referenceType/status/createdAt`.      |
| ~~G4-003~~ | P0 ✅ | Fix mobile consent DTO compatibility and revoke flow around `grantId/patientId`.                                                                      |
| ~~G4-004~~ | P0 ✅ | Replace placeholder record-detail response with actual FHIR/resource lookup, redaction, and audit. Current code explicitly returns placeholder data.  |
| ~~G4-005~~ | P1 ✅ | Add backend dashboard endpoint matching route contract `/api/v1/dashboard`; currently route contract declares it.                                     |
| ~~G4-006~~ | P1 ✅ | Align records endpoint naming. Route contract says `/api/v1/records`, but backend mounts patient records under `/patients/*`.                         |
| ~~G4-007~~ | P1 ✅ | Align document route naming. Web contract has `/documents`, but server mounts document/imaging routes under `/records/*`.                             |
| ~~G4-008~~ | P1 ✅ | Ensure observations page calls `/clinical/labs/trends` or create canonical `/observations` endpoint; route contract says `/observations`.             |
| ~~G4-009~~ | P1 ✅ | Add backend notification route and wire it through module.                                                                                            |
| ~~G4-010~~ | P1 ✅ | Align web `buildPhrHeaders` with backend header expectations; web already centralizes correlation and identity headers.                               |
| ~~G4-011~~ | P1 ✅ | Add typed Zod schemas for every new API client response, not only partial FHIR/dashboard schemas.                                                     |
| ~~G4-012~~ | P2 ✅ | Normalize list response shapes: `items/count/limit/offset` or one standard envelope everywhere.                                                       |

## Verification Pass V2 + V5

Backend contract tests plus web/mobile API client schema tests:

```bash
./gradlew :products:phr:test --tests '*Contract*'
pnpm --dir products/phr/apps/web test
pnpm --dir products/phr/apps/mobile test
```

---

# Group 5 — Web Pages by Route Set

The route map now imports actual page components for the expanded IA surface. 

## Files

```text
products/phr/apps/web/src/pages/DashboardPage.tsx
products/phr/apps/web/src/pages/RecordsPage.tsx
products/phr/apps/web/src/pages/RecordDetailPage.tsx
products/phr/apps/web/src/pages/ProfilePage.tsx
products/phr/apps/web/src/pages/TimelinePage.tsx
products/phr/apps/web/src/pages/ConditionsPage.tsx
products/phr/apps/web/src/pages/LabsPage.tsx
products/phr/apps/web/src/pages/ObservationsPage.tsx
products/phr/apps/web/src/pages/MedicationsPage.tsx
products/phr/apps/web/src/pages/ImmunizationsPage.tsx
products/phr/apps/web/src/pages/DocumentsPage.tsx
products/phr/apps/web/src/pages/DocumentUploadPage.tsx
products/phr/apps/web/src/pages/OcrReviewPage.tsx
products/phr/apps/web/src/pages/ConsentPage.tsx
products/phr/apps/web/src/pages/AppointmentsPage.tsx
products/phr/apps/web/src/pages/NotificationsPage.tsx
products/phr/apps/web/src/pages/EmergencyAccessPage.tsx
products/phr/apps/web/src/pages/EmergencyReviewsPage.tsx
products/phr/apps/web/src/pages/AuditPage.tsx
products/phr/apps/web/src/pages/ReleaseCockpitPage.tsx
products/phr/apps/web/src/pages/SettingsPage.tsx
```

## Tasks

| ID     | Priority | Change                                                                                           |
| ------ | -------- | ------------------------------------------------------------------------------------------------ |
| ~~G5-001~~ | P1 ✅ | For every stable route page, verify it is backend-backed and not demo/static-only.               |
| ~~G5-002~~ | P1 ✅ | Use route contract `apiEndpoint` for page/API mapping once every route has it.                   |
| ~~G5-003~~ | P1 ✅ | Add loading, error, empty, forbidden, and degraded states to every stable page.                  |
| ~~G5-004~~ | P1 ✅ | Dashboard: move to backend-owned dashboard contract.                                             |
| ~~G5-005~~ | P1 ✅ | Records: use canonical records endpoint; add filters/pagination.                                 |
| ~~G5-006~~ | P0 ✅ | Record detail: display actual FHIR/resource payload returned by fixed backend endpoint.          |
| ~~G5-007~~ | P1 ✅ | Profile: support GET/PATCH, dirty state, validation, rollback.                                   |
| ~~G5-008~~ | P1 ✅ | Timeline: use backend timeline events; group by date/category.                                   |
| ~~G5-009~~ | P1 ✅ | Conditions: active/resolved tabs and detail drilldown.                                           |
| ~~G5-010~~ | P1 ✅ | Observations: trend chart, date/metric selector, abnormal state.                                 |
| ~~G5-011~~ | P1 ✅ | Labs: align with observation/lab route naming and backend DTO.                                   |
| ~~G5-012~~ | P1 ✅ | Medications: active/history/detail, refill/discontinue only when supported.                      |
| ~~G5-013~~ | P1 ✅ | Immunizations: list/detail, permanent retention indicator, audit awareness.                      |
| ~~G5-014~~ | P1 ✅ | Documents: list/filter/preview/download policy.                                                  |
| ~~G5-015~~ | P1 ✅ | Upload: file type/size/progress/retry/audit.                                                     |
| ~~G5-016~~ | P1 ✅ | OCR review: accept/edit/reject, confidence badges, FHIR draft confirmation.                      |
| ~~G5-017~~ | P1 ✅ | Consent: create/revoke/update/expiry workflow and rollback on failure.                           |
| ~~G5-018~~ | P1 ✅ | Appointments: slot search, conflict handling, request state.                                     |
| ~~G5~~G5~~~~ PG5✅✅| Notifications: backend-backed and PHI-redacted.                                                  |
| ~~G5-020~~ | P1 ✅ | Emergency: reason, status, audit, notification.                                                  |
| ~~G5-021~~ | P1 ✅ | Emergency reviews: pending/overdue/review action.                                                |
| ~~G5-022~~ | P1 ✅ | Audit: backend-backed pagination/filter/detail/export.                                           |
| ~~G5~~G5~~~~ PGP✅2| Release readiness: keep UI, but route should call Kernel-backed readiness service after Group 9. |
| ~~G5-024~~ | P2 ✅ | Settings: add language switch, sync/export status, logout consistency.                           |

## Verification Pass V4

One Playwright route traversal suite:

```bash
pnpm --dir products/phr/apps/web exec playwright test e2e/phr-stable-routes.spec.ts
```

Assertions per route:

```text
renders
uses backend or declared mock only in dev
has loading/error/empty/forbidden states
has i18n text
has accessible landmark/title
primary actions work or are hidden
no hidden route appears in navigation
```

---

# Group 6 — Hidden Provider, Caregiver, and FCHV Routes

## Files

```text
products/phr/apps/web/src/pages/ProviderDashboardPage.tsx
products/phr/apps/web/src/pages/ProviderPatientsPage.tsx
products/phr/apps/web/src/pages/CaregiverDependentsPage.tsx
products/phr/apps/web/src/pages/FchvDashboardPage.tsx
products/phr/src/main/java/com/ghatana/phr/api/routes/PhrProviderRoutes.java
products/phr/src/main/java/com/ghatana/phr/api/routes/PhrCaregiverRoutes.java
products/phr/src/main/java/com/ghatana/phr/api/routes/PhrFchvRoutes.java
products/phr/config/phr-route-contract.json
```

## Tasks

| ID     | Priority | Change                                                                                                                      |
| ------ | -------- | --------------------------------------------------------------------------------------------------------------------------- |
| ~~G6-001~~ | P0 ✅ | Keep these routes truly hidden/inaccessible until route/API/test coverage is complete. Current contract marks them hidden.  |
| ~~G6-002~~ | P1 ✅ | Provider dashboard: implement work queue, appointments, recent patients, alerts.                                            |
| ~~G6-003~~ | P1 ✅ | Provider patients: implement consent/treatment-aware search and roster.                                                     |
| ~~G6-004~~ | P1 ✅ | Provider patient detail/summary route must be added before exposing provider patients.                                      |
| ~~G6-005~~ | P1 ✅ | Caregiver dependents: implement dependent list, detail, grant expiry/revocation behavior.                                   |
| ~~G6-006~~ | P1 ✅ | FCHV dashboard: implement community assignment, scoped patient list, vitals capture links.                                  |
| ~~G6-007~~ | P1 ✅ | Add route contract entries for provider patient detail and FCHV patient/vitals if implemented.                              |
| ~~G6-008~~ | P1 ✅ | Only switch stability from `hidden` to `preview` or `stable` after route/page/API/test coverage is complete.                |
| ~~G6-009~~ | P2 ✅ | Add role-specific shell/navigation grouping for provider/caregiver/FCHV once unhidden.                                      |

## Verification Pass V4

Same web E2E run, plus direct-access tests for hidden routes:

```text
hidden route not visible in nav
direct URL blocked unless preview flag enabled
preview route shows clear preview state
stable route has full backend-backed workflow
```

---

# Group 7 — Mobile App, Encrypted PHI Storage, and Mobile API Contract

## Files

```text
products/phr/apps/mobile/src/App.tsx
products/phr/apps/mobile/src/services/phrMobileApi.ts
products/phr/apps/mobile/src/services/offlineStore.ts
products/phr/apps/mobile/src/services/phiEncryptedStorage.ts
products/phr/apps/mobile/src/services/mobileSessionStore.ts
products/phr/apps/mobile/src/services/biometricAuth.ts
products/phr/apps/mobile/src/services/pushNotifications.ts
products/phr/apps/mobile/src/i18n/phrMobileI18n.ts
products/phr/apps/mobile/src/screens/*.tsx
products/phr/apps/mobile/src/types.ts
products/phr/src/main/java/com/ghatana/phr/api/routes/PhrMobileRoutes.java
```

## Tasks

| ID     | Priority | Change                                                                                                                                                                                                         |
| ------ | -------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| ~~G7-001~~ | P0 ✅ | Fix backend/mobile dashboard DTO mismatch for records and notifications.                                                                                                                                       |
| ~~G7-002~~ | P0 ✅ | Add test proving `AsyncStorage` contains ciphertext only. The encrypted storage adapter stores ciphertext in AsyncStorage and key material in SecureStore.                                                     |
| ~~G7-003~~ | P0 ✅ | Verify WebCrypto, `btoa`, and `atob` availability in the target React Native runtime; add polyfills/adapters if needed.                                                                                        |
| ~~G7-004~~ | P0 ✅ | Replace local-only tamper/integrity claims with real attestation or rename them to local integrity checks. Current code comments say production would verify against server-side attestation.                  |
| ~~G7-005~~ | P1 ✅ | Ensure `phiClearAll`, `clearDashboardOffline`, and `clearMobileSession` are called on logout, session expiry, role/persona/tier change, and consent revoke. Current logout and revoke paths clear local PHI.   |
| ~~G7-006~~ | P1 ✅ | Extend session change detection to persona/tier/facility, not only role/principal. Current foreground logic checks role/principal.                                                                             |
| ~~G7-007~~ | P1 ✅ | Add mobile record detail screen and navigation from record list.                                                                                                                                               |
| ~~G7-008~~ | P1 ✅ | Add mobile language switcher in settings.                                                                                                                                                                      |
| ~~G7-009~~ | P1 ✅ | Add stale offline cache timestamp display using `getDashboardOfflineTimestamp`.                                                                                                                                |
| ~~G7-010~~ | P1 ✅ | Ensure push notification UI does not display sensitive token or PHI. Current app writes push token into sync message.                                                                                          |
| ~~G7-011~~ | P1 ✅ | Ensure all mobile screens use `t()` and no raw user-visible strings remain. App-level i18n is now present.                                                                                                     |
| ~~G7-012~~ | P1 ✅ | Add accessibility labels/hints to all mobile Pressables/Inputs/cards. Tab bar already has roles/labels.                                                                                                        |
| ~~G7-013~~ | P2 ✅ | Add low-connectivity action queue for FCHV/patient actions only after route/API support is real.                                                                                                               |

## Verification Pass V5

```bash
pnpm --dir products/phr/apps/mobile test
```

Required tests:

```text
login success saves secure session
dashboard sends tenant/principal/role/persona/tier/correlation headers
offline dashboard uses encrypted storage
plaintext patient name is absent from AsyncStorage
cache rejected on session mismatch
logout clears PHI/session
consent revoke clears PHI
offline banner appears when disconnected
mobile strings are i18n-backed
```

---

# Group 8 — PHR API Client and Shared Types

## Files

```text
products/phr/apps/web/src/api/phrApi.ts
products/phr/apps/web/src/types.ts
products/phr/apps/mobile/src/services/phrMobileApi.ts
products/phr/apps/mobile/src/types.ts
products/phr/apps/web/src/i18n/phrI18n.ts
products/phr/apps/mobile/src/i18n/phrMobileI18n.ts
```

## Tasks

| ID     | Priority | Change                                                                                                                                                                                                      |
| ------ | -------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| ~~G8-001~~ | P1 ✅ | Split `phrApi.ts` into focused modules: auth, dashboard, records, consent, clinical, documents, emergency, audit, release. It is now very broad and mixes FHIR transformation, headers, schemas, and APIs.  |
| ~~G8-002~~ | P1 ✅ | Keep `buildPhrHeaders` as the single web header builder.                                                                                                                                                    |
| ~~G8-003~~ | P1 ✅ | Add equivalent single mobile header builder and reuse it for all mobile calls.                                                                                                                              |
| ~~G8-004~~ | P1 ✅ | Make web and mobile share DTO contracts where possible.                                                                                                                                                     |
| ~~G8-005~~ | P1 ✅ | Add schema validation for all API responses, not only dashboard/FHIR/mobile.                                                                                                                                |
| ~~G8-006~~ | P1 ✅ | Replace fallback “Unknown/General/TBD” values with explicit missing-data states where clinically relevant.                                                                                                  |
| ~~G8-007~~ | P2 ✅ | Move FHIR-to-UI transforms into a dedicated adapter file.                                                                                                                                                   |
| ~~G8-008~~ | P2 ✅ | Add contract tests for every exported API function.                                                                                                                                                         |

## Verification Pass V4 + V5

Run web/mobile unit tests once after this group because it affects both apps.

---

# Group 9 — Kernel Platform Tasks Required by PHR

## Files

```text
platform/typescript/product-shell/src/**
platform/typescript/kernel-lifecycle/src/**
platform-kernel/**
scripts/check-route-entitlement-contracts.mjs
scripts/check-product-ui-contracts.mjs
scripts/check-i18n-*.mjs
scripts/check-a11y-*.mjs
```

## Tasks

| ID     | Priority | Change                                                                                                      |
| ------ | -------- | ----------------------------------------------------------------------------------------------------------- |
| ~~G9-001~~ | P0 ✅ | Promote PHR route contract shape into a reusable Kernel product-route contract schema.                      |
| ~~G9-002~~ | P0 ✅ | Add Kernel generator for product route contracts → frontend route TS → backend entitlement adapter payload. |
| ~~G9-003~~ | P1 ✅ | Move route stability semantics into Kernel/product-shell.                                                   |
| ~~G9-004~~ | P1 ✅ | Add Kernel hidden/preview/blocked direct-access behavior.                                                   |
| ~~G9-005~~ | P1 ✅ | Promote PHI/patient-data policy primitives to Kernel privacy/security contracts; PHR extends them.          |
| ~~G9-006~~ | P1 ✅ | Add Kernel mobile-offline-PHI policy contract: encryption, TTL, session binding, revocation clear.          |
| ~~G9-007~~ | P1 ✅ | Move release-readiness runtime service behind Kernel API; PHR route becomes a thin adapter.                 |
| ~~G9-008~~ | P1 ✅ | Add Kernel correlation ID propagation contract and helper for web/mobile/backend.                           |
| ~~G9-009~~ | P2 ✅ | Add shared product i18n/a11y route-surface gate for web and mobile products.                                |
| ~~G9-010~~ | P2 ✅ | Add check that production modules do not pass `null` route adapters.                                        |

## Verification Pass V6

```bash
pnpm check:route-entitlement-contracts
pnpm check:kernel-product-boundary-audit
pnpm check:kernel-lifecycle-truth
```

Add or update checks for:

```text
route contract generation
no nullable production route wiring
mobile PHI policy conformance
hidden route enforcement
```

---

# Group 10 — YAPPC Tasks Required by PHR

## Files

```text
products/yappc/core/scaffold/api/src/main/java/com/ghatana/yappc/api/YappcApi.java
products/yappc/core/scaffold/api/src/main/java/com/ghatana/yappc/api/service/**
products/yappc/core/scaffold/**
products/yappc/frontend/web/src/**
products/yappc/frontend/web/src/lib/canvas-ai/**
```

## Tasks

| ID      | Priority | Change                                                                                                     |
| ------- | -------- | ---------------------------------------------------------------------------------------------------------- |
| ~~G10-001~~ | P1 ✅ | Add PHR IA importer that reads `phr-route-contract.json` and future `phr-usecase-baseline.json`.           |
| ~~G10-002~~ | P1 ✅ | Add YAPPC product model support for route/page/API/test/policy gaps.                                       |
| ~~G10-003~~ | P1 ✅ | Generate PHR web route/page skeletons from Kernel route contract.                                          |
| ~~G10-004~~ | P1 ✅ | Generate PHR backend route adapter skeletons from Kernel route contract.                                   |
| ~~G10-005~~ | P1 ✅ | Generate PHR mobile screen/API skeletons from Kernel route contract.                                       |
| ~~G10-006~~ | P1 ✅ | Add generation mode that emits “delete duplicate legacy path” tasks instead of deprecation tasks.          |
| ~~G10-007~~ | P1 ✅ | Add PHR gap visualization: stable implemented, stable missing API, hidden, blocked, preview, test missing. |
| ~~G10-008~~ | P1 ✅ | Add PHR pack under scaffold packs.                                                                         |
| ~~G10-009~~ | P2 ✅ | Add backlog export grouped by verification pass.                                                           |
| ~~G10-010~~ | P2 ✅ | Add product completeness preview for web/mobile/backend parity.                                            |

YAPPC’s current API is still generic pack/project/feature/template/dependency access, so this work should extend it for Kernel-native product generation rather than creating a separate PHR-specific generator.  

## Verification Pass V6

```bash
pnpm check:yappc-kernel-bridge-contracts
pnpm check:yappc-feature-completeness-matrix
pnpm check:yappc-kernel-artifact-roundtrip
```

Add:

```bash
pnpm check:yappc-phr-ia-import
pnpm check:yappc-phr-generation-roundtrip
```

---

# Group 11 — Tests and Checks to Add or Update

## Files

```text
scripts/check-phr-route-contract-drift.mjs
scripts/check-phr-mobile-privacy.mjs
scripts/check-phr-policy-enforcement.mjs
scripts/check-phr-route-implementation-map.mjs
products/phr/src/test/**
products/phr/apps/web/src/**/*.test.tsx
products/phr/apps/web/e2e/**
products/phr/apps/mobile/src/**/*.test.tsx
products/yappc/**/__tests__/**
```

## Tasks

| ID      | Priority | Change                                                                          |
| ------- | -------- | ------------------------------------------------------------------------------- |
| ~~G11-001~~ | P0 ✅ | Add backend compile check to required PHR verification path.                    |
| ~~G11-002~~ | P0 ✅ | Add route contract drift check.                                                 |
| ~~G11-003~~ | P0 ✅ | Add patient/PHI policy access matrix test.                                      |
| ~~G11-004~~ | P0 ✅ | Add mobile DTO contract test against `PhrMobileRoutes`.                         |
| ~~G11-005~~ | P0 ✅ | Add encrypted PHI cache test: no plaintext patient data in AsyncStorage.        |
| ~~G11-006~~ | P1 ✅ | Add hidden/stable route visibility tests.                                       |
| ~~G11-007~~ | P1 ✅ | Add web route traversal E2E for every stable route.                             |
| ~~G11-008~~ | P1 ✅ | Add web action E2E for consent, appointments, documents, OCR, emergency, audit. |
| ~~G11-009~~ | P1 ✅ | Add mobile session restore/expiry/logout/offline tests.                         |
| ~~G11-010~~ | P1 ✅ | Add mobile consent revoke clears cache test.                                    |
| ~~G11-011~~ | P1 ✅ | Add correlation ID propagation tests.                                           |
| ~~G11-012~~ | P1 ✅ | Add i18n raw-string checks for PHR web/mobile.                                  |
| ~~G11-013~~ | P1 ✅ | Add accessibility checks for web routes and mobile tab/buttons/forms.           |
| ~~G11-014~~ | P1 ✅ | Add YAPPC PHR IA import/generation roundtrip tests.                             |
| ~~G11-015~~ | P2 ✅ | Add performance smoke for records, audit, mobile dashboard.                     |

## Verification Pass V7

After all groups:

```bash
pnpm check:required
pnpm check:architecture-boundaries
pnpm check:i18n-conformance
pnpm check:a11y-behavioral-proof
pnpm check:phr-production-workflows
pnpm check:phr-lifecycle-readiness
pnpm check:yappc-kernel-bridge-contracts
```

---

# Recommended Implementation Order

| Order | Group                                     | Why                                                             |
| ----: | ----------------------------------------- | --------------------------------------------------------------- |
|     1 | Group 1 — Backend composition/wiring      | Removes null production route paths and makes APIs reachable.   |
|     2 | Group 3 — Policy/security helpers         | Prevents building more UI/API on legacy role-only access.       |
|     3 | Group 4 — Backend DTO/contract alignment  | Fixes mobile/web/backend mismatches before E2E.                 |
|     4 | Group 2 — Route contract canonicalization | Prevents new route drift while pages are completed.             |
|     5 | Group 7 — Mobile contract/privacy         | Mobile PHI and DTO correctness are high risk.                   |
|     6 | Group 5 — Stable web pages                | Complete user-facing PHR journeys.                              |
|     7 | Group 6 — Hidden provider/caregiver/FCHV  | Implement or keep truly hidden; do not expose incomplete flows. |
|     8 | Group 8 — API client/types split          | Clean up after contracts are settled.                           |
|     9 | Group 9 — Kernel platform extraction      | Promote proven PHR patterns into Kernel.                        |
|    10 | Group 10 — YAPPC acceleration             | Generate only after Kernel/PHR contracts are stable.            |
|    11 | Group 11 — Final checks                   | Lock in verification coverage.                                  |

This organization lets you implement related changes together and verify them in grouped passes instead of repeatedly re-running full checks after every small file change.

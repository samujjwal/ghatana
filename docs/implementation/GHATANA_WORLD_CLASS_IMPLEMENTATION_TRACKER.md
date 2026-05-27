Below is the reorganized backlog for commit `2103f84ea84043fb22febf30b5e2fe0d4c4e4c05`, grouped by **file/set of related files** so each group can be implemented and verified together with the smallest reasonable number of verification passes.

The current state is materially ahead of the previous audit: more PHR web routes/pages now exist, route elements map them, mobile API calls now send session headers, and mobile PHI storage has an encrypted-storage adapter.     

---

# Verification Strategy

Use **verification batches**, not one-off checks per task.

| Batch                       | Verifies                                                                                     | Run after completing |
| --------------------------- | -------------------------------------------------------------------------------------------- | -------------------- |
| V1 Route contract parity    | JSON contract, TS route manifest, route elements, backend entitlements, shell nav            | Groups 1–2           |
| V2 Security/policy          | PHI access, consent, treatment relationship, FCHV scope, emergency, no legacy role shortcuts | Group 3              |
| V3 Mobile PHI/privacy       | encryption, cache clear, session restore/logout, biometric policy, mobile API headers        | Group 4              |
| V4 Web API + page behavior  | API contracts, route rendering, loading/error/empty/access states, no raw text               | Groups 5–7           |
| V5 Backend API contract     | route validation, idempotency, errors, correlation IDs, scoped PHI reads/writes              | Group 8              |
| V6 Kernel/YAPPC integration | Kernel-owned route/policy/contracts and YAPPC generator alignment                            | Groups 9–10          |
| V7 Full PHR smoke           | Auth → dashboard → records → consent → docs → emergency → audit → mobile cache               | Final pass           |

No evidence generation tasks are included here; release evidence can be handled later.

---

# 0. Tasks to Remove From Previous Backlog Because Current Code Already Moved

These are **not done enough for production**, but the original task wording should change.

| Old task                                            | Current state                                                                                                | New task location                            |
| --------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ | -------------------------------------------- |
| “Create encrypted mobile PHI storage”               | `offlineStore.ts` now uses `phiEncryptedStorage`, and `phiEncryptedStorage.ts` implements AES-GCM adapter.   | Replace with Group 4 correctness fixes.      |
| “Pass session headers to mobile dashboard”          | `fetchDashboardFromApi(session)` now sends tenant/principal/role/correlation headers.                        | Keep only coverage/negative tests.           |
| “Add mobile i18n helper”                            | `phrMobileI18n.ts` exists with EN/NE locale dictionaries.                                                    | Keep locale persistence/raw text cleanup.    |
| “Add route elements for profile/timeline/docs/etc.” | `phrRouteElements.tsx` maps many new pages.                                                                  | Keep functional completion and parity tasks. |
| “Add route contract JSON”                           | `products/phr/config/phr-route-contract.json` exists.                                                        | Keep canonicalization/parity tasks.          |

---

# 1. Route Contract, Route Elements, Routing, Product Shell

## Files

* `products/phr/config/phr-route-contract.json`
* `products/phr/apps/web/src/phrRouteContracts.ts`
* `products/phr/apps/web/src/phrRouteElements.tsx`
* `products/phr/apps/web/src/routes.tsx`
* `products/phr/apps/web/src/layout/PhrProductShell.tsx`
* `products/phr/apps/web/src/auth/PhrAccessContext.tsx`

## Current Findings

The TS route manifest is still hand-maintained and duplicates JSON. It also contains lifecycle fields that support deprecation/removal/migration notes, which conflicts with the fix-forward/no-deprecation direction. 

`phrRouteElements.tsx` maps expanded routes, but `/emergency/reviews` still maps to `EmergencyAccessPage`, not a dedicated admin review page. 

`PhrAccessContext.tsx` supports only `patient | caregiver | clinician | admin`, while backend route support allows `fchv`.  

`PhrProductShell.tsx` requests entitlements with role/persona/tier but does **not** include tenant/principal/correlation headers, while backend entitlement route now fails closed if identity headers are missing.  

## Tasks

| ID    | Action                                                          | File(s)                                                                   | Expected change                                                                                                                                  |
| ----- | --------------------------------------------------------------- | ------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| R-001 | Delete deprecated/removal/migration route lifecycle fields.     | `phrRouteContracts.ts`, `phr-route-contract.json`                         | ✅ DONE - Removed lifecycle object, using only stability field (stable, preview, blocked, hidden)         |
| R-002 | Make JSON route contract the canonical source.                  | `phr-route-contract.json`, generated `phrRouteContracts.ts`               | ✅ DONE - Generate TS from JSON via generate-route-contracts.mjs script                                      |
| R-003 | Add route-contract schema validation.                           | New `products/phr/config/phr-route-contract.schema.json` or Kernel schema | ✅ DONE - Schema exists and validates required fields                                   |
| R-004 | Ensure TS route manifest and JSON route contract are identical. | `phrRouteContracts.ts`, `phr-route-contract.json`                         | ✅ DONE - Fixed role order mismatch, removed duplicate route                                                        |
| R-005 | Replace `/emergency/reviews` mapping.                           | `phrRouteElements.tsx`, new `EmergencyReviewsPage.tsx`                    | ✅ DONE - Maps to dedicated EmergencyReviewsPage                                                                    |
| R-006 | Add FCHV role to frontend role model.                           | `PhrAccessContext.tsx`, `PhrProductShell.tsx`, route contract             | ✅ DONE - FCHV role added to role type/order/labels/role selector |
| R-007 | Send identity headers for entitlement requests.                 | `PhrProductShell.tsx`                                                     | ✅ DONE - Includes all identity headers from session/access context                        |
| R-008 | Align shell role with authenticated session.                    | `PhrAccessContext.tsx`, `PhrSessionContext.tsx`, `PhrProductShell.tsx`    | ✅ DONE - Role change checks production mode before allowing escalation                                      |
| R-009 | Add catch-all route.                                            | `routes.tsx`                                                              | ✅ DONE - Catch-all route renders NotFoundPage                                                 |
| R-010 | Enforce feature visibility centrally.                           | `phr-route-contract.json`, generated TS, `attachPhrRouteElement`          | ✅ DONE - Feature-flagged routes render placeholder, blocked routes render forbidden                                                |
| R-011 | Add route metadata for backend endpoint IDs.                    | `phr-route-contract.json`                                                 | ✅ DONE - Routes include apiEndpoint, policyId, testId                                                                                |
| R-012 | Delete backend/TS fallback route drift permanently.             | `PhrEntitlementRoutes.java`, generated files                              | ✅ DONE - Backend fails closed if contract missing/invalid                                                                         |

## Verification Batch V1

Create one route-contract parity test that validates:

* JSON schema is valid.
* Generated TS route list equals JSON.
* `phrRouteElements.tsx` has an element for every route.
* Backend entitlement output equals JSON for each role.
* Product shell entitlement request includes identity headers.
* No deprecated/removed/migration metadata exists.

---

# 2. Backend Entitlements

## Files

* `products/phr/src/main/java/com/ghatana/phr/api/routes/PhrEntitlementRoutes.java`
* `products/phr/config/phr-route-contract.json`
* Kernel/product-shell entitlement types

## Current Findings

`PhrEntitlementRoutes` now attempts to load `products/phr/config/phr-route-contract.json`, which is good. But it still has a fallback route list that is stale and role order differs from JSON (`patient` starts at 1 in fallback vs 0 in JSON).  

## Tasks

| ID    | Action                                                                                   | File(s)                                                                   | Expected change                                                                                        |
| ----- | ---------------------------------------------------------------------------------------- | ------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| E-001 | Delete fallback route list.                                                              | `PhrEntitlementRoutes.java`                                               | ✅ DONE - Returns 503 if contract missing/invalid, no fallback nav                   |
| E-002 | Delete fallback role order.                                                              | `PhrEntitlementRoutes.java`                                               | ✅ DONE - Role order comes only from canonical route contract                       |
| E-003 | Validate loaded route contract fields.                                                   | `PhrEntitlementRoutes.java`                                               | ✅ DONE - Rejects empty path/label/minimumRole, unknown role, invalid tier, missing actions/cards arrays         |
| E-004 | Include `featureFlag`/visibility/stability in backend entitlement payload.               | `PhrEntitlementRoutes.java`, platform `ProductRouteEntitlement` if needed | ✅ DONE - Backend tells UI whether a route is hidden/blocked/preview                                        |
| E-005 | Use tenant/principal/persona/tier in cache key.                                          | `PhrEntitlementRoutes.java`                                               | ✅ DONE - Cache key includes tenant, principal, persona, tier, feature visibility version. |
| E-006 | Add correlation ID in entitlement response.                                              | `PhrEntitlementRoutes.java`                                               | ✅ DONE - Response includes correlationId; request accepts X-Correlation-ID                                 |
| E-007 | Remove manual `HashMap` response construction if platform contract can serialize safely. | `PhrEntitlementRoutes.java`, platform contract                            | ✅ DONE - Uses ProductRouteEntitlement.toMap() for serialization                                               |
| E-008 | Validate route contract load path.                                                       | `PhrEntitlementRoutes.java`                                               | ✅ DONE - Resolves contract path from known locations, no process directory guessing       |

## Verification Batch V1

Same V1 route parity test should cover entitlement behavior; do not create a separate verification suite.

---

# 3. Security / Policy / Access Control

## Files

* `products/phr/src/main/java/com/ghatana/phr/security/PhrPolicyEvaluator.java`
* `products/phr/src/main/java/com/ghatana/phr/api/routes/PhrRouteSupport.java`
* all PHR route adapters that read/write PHI
* future/actual `TreatmentRelationshipService`
* caregiver/FCHV/facility policy services

## Current Findings

`PhrPolicyEvaluator` is static and has permissive fallbacks: caregiver access is allowed if consent service is null; clinician access is allowed if treatment relationship service is null; admin and FCHV have broad allow paths.  

`PhrRouteSupport` still contains deprecated shortcut methods and comments encouraging service-layer verification. User direction is fix-forward and delete legacy paths. 

## Tasks

| ID    | Action                                                                | File(s)                                                               | Expected change                                                                             |
| ----- | --------------------------------------------------------------------- | --------------------------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| S-001 | Convert `PhrPolicyEvaluator` from static utility to injected service. | `PhrPolicyEvaluator.java`, `PhrKernelModule.java`, route constructors | ✅ DONE - Routes receive evaluator instance                        |
| S-002 | Delete deprecated access shortcut methods.                            | `PhrRouteSupport.java`, `PhrPolicyEvaluator.java`                     | ✅ DONE - Removed hasClinicalRole, canAccessPatientRecordForRole        |
| S-003 | Make policy fail closed.                                              | `PhrPolicyEvaluator.java`                                             | ✅ DONE - Null services deny access                                      |
| S-004 | Add explicit policy result object.                                    | `PhrPolicyEvaluator.java`                                             | ✅ DONE - Returns PolicyDecision with reason code + audit requirement + emergency flag |
| S-005 | Add treatment relationship service.                                   | New/actual `TreatmentRelationshipService.java`                        | ✅ DONE - Clinician access requires active relationship unless emergency override                    |
| S-006 | Add FCHV community assignment service.                                | New `FchvCommunityAssignmentService.java` or equivalent               | ✅ DONE - FCHV access requires assigned community/patient                                            |
| S-007 | Add facility/tenant scope validation.                                 | `PhrRouteSupport.java`, policy evaluator                              | ✅ DONE - Request context includes facility ID; policy checks facility scope                         |
| S-008 | Add emergency policy branch.                                          | `PhrPolicyEvaluator.java`, emergency route/service                    | ✅ DONE - Emergency requires reason, allowed role, audit, notification, review state                 |
| S-009 | Add admin PHI policy.                                                 | `PhrPolicyEvaluator.java`                                             | ✅ DONE - Admin cannot read clinical PHI by role alone; needs compliance/audit workflow              |
| S-010 | Add caregiver consent resource/action scope.                          | `PhrPolicyEvaluator.java`, `ConsentManagementService.java`            | ✅ DONE - Caregiver must have active grant for exact resource/action                                 |
| S-011 | Add PHI field classification checks.                                  | Policy service + DTO/resource classifiers                             | ✅ DONE - Added isRestrictedField method for restricted field checks                  |
| S-012 | Ensure every PHI route uses async policy.                             | All route adapters                                                    | ✅ DONE - PhrPatientRecordRoutes, PhrTimelineRoutes, PhrConditionRoutes use async policy                                               |
| S-013 | Sanitize policy denial responses.                                     | `PhrRouteSupport.java`                                                | ✅ DONE - Added policyDenialResponse method for safe denials                                       |

## Verification Batch V2

One policy matrix suite:

* patient own vs other patient
* caregiver with/without grant
* clinician with/without treatment relationship
* admin with/without audit purpose
* FCHV assigned/unassigned
* emergency approved/pending/denied
* missing policy services fail closed
* route adapters have no direct role-only PHI authorization

---

# 4. Mobile PHI Storage, Session, Offline, Biometric

## Files

* `products/phr/apps/mobile/src/services/phiEncryptedStorage.ts`
* `products/phr/apps/mobile/src/services/offlineStore.ts`
* `products/phr/apps/mobile/src/services/phrMobileApi.ts`
* `products/phr/apps/mobile/src/services/mobileSessionStore.ts`
* `products/phr/apps/mobile/src/App.tsx`
* `products/phr/apps/mobile/src/screens/SettingsScreen.tsx`
* `products/phr/apps/mobile/src/screens/EmergencyAccessScreen.tsx`

## Current Findings

`offlineStore.ts` now routes dashboard cache through `phiSet/phiGet/phiRemove`, which is the correct direction. 

But `phiEncryptedStorage.ts` has a likely broken key export path because the generated key is non-extractable and then exported.  Also, `phiClearAll()` filters keys by a prefix that production writes do not apply.  

## Tasks

| ID    | Action                                                   | File(s)                                                                     | Expected change                                                                                                                   |
| ----- | -------------------------------------------------------- | --------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| M-001 | Fix AES key generation/storage.                          | `phiEncryptedStorage.ts`                                                    | ✅ DONE - Generate storable raw key bytes in SecureStore, import as non-extractable CryptoKey |
| M-002 | Fix `phiClearAll`.                                       | `phiEncryptedStorage.ts`                                                    | ✅ DONE - Clears PHI key registry and all PHI keys                                             |
| M-003 | Remove recursive key-recovery loop.                      | `phiEncryptedStorage.ts`                                                    | ✅ DONE - Replaced recursive call with bounded retry                      |
| M-004 | Remove unsafe console warnings/errors.                   | `phiEncryptedStorage.ts`                                                    | ✅ DONE - Uses logSecurityEvent with safe reason codes                                                          |
| M-005 | Implement restricted-field cache sanitizer.              | `offlineStore.ts`                                                           | ✅ DONE - Enforces restricted field sanitization before phiSet                                                         |
| M-006 | Bind offline cache to session identity.                  | `offlineStore.ts`, `phrMobileApi.ts`                                        | ✅ DONE - Cache envelope includes tenantId/principalId/role; rejects cache if session differs                                               |
| M-007 | Add cache expiry/clear on role/persona change.           | `App.tsx`, `offlineStore.ts`                                                | ✅ DONE - Clear dashboard cache when session role changes                                                                                  |
| M-008 | Wire logout UI to `logoutMobile`.                        | `SettingsScreen.tsx`, `App.tsx`, `phrMobileApi.ts`                          | ✅ DONE - Explicit logout clears session + encrypted PHI                                                                                   |
| M-009 | Persist mobile locale.                                   | `phrMobileI18n.ts`, new locale store, `SettingsScreen.tsx`                  | ✅ DONE - setLocale persists to AsyncStorage, initializeLocale loads on startup                                                           |
| M-010 | Add pseudo-locale support to mobile.                     | `phrMobileI18n.ts`, locales                                                 | ⏳ PENDING - Match web pseudo-locale behavior                                                                                                 |
| M-011 | Enforce biometric policy for emergency/offline PHI.      | `EmergencyAccessScreen.tsx`, `phiEncryptedStorage.ts`, `SettingsScreen.tsx` | ✅ DONE - PHI decrypt/read requires biometric/device auth where policy enabled                                                             |
| M-012 | Make biometric policy default for emergency/offline PHI. | `phiEncryptedStorage.ts`                                                    | ✅ DONE - Default secure posture; user/admin policy can loosen only if allowed                                                             |
| M-013 | Add mobile record detail screen.                         | New `RecordDetailScreen.tsx`, records navigation                            | ⏳ PENDING - Records list drills into scoped/redacted detail                                                                                  |
| M-014 | Add consent revoke UI.                                   | `ConsentScreen.tsx`, `phrMobileApi.ts`                                      | ⏳ PENDING - Revoke grant and clear PHI cache. API function exists.                                                                            |
| M-015 | Add stale/offline indicator.                             | `SettingsScreen.tsx`, `offlineStore.ts`                                     | ✅ DONE - Shows last cache timestamp and stale/fresh indicator                                                                   |
| M-016 | Add mobile raw-string cleanup.                           | `App.tsx`, `screens/*.tsx`, services                                        | ⏳ PENDING - All user text through t()                                                                                                      |
| M-017 | Add accessibility labels/hints.                          | Mobile screens                                                              | ⏳ PENDING - All Pressable/Input/tab controls have roles/labels                                                                               |

## Verification Batch V3

One mobile privacy suite:

* encrypt/decrypt across app restart
* corrupt ciphertext returns null and clears
* logout clears all PHI
* consent revoke clears all PHI
* session mismatch refuses cache
* biometric enabled blocks decrypt until authenticated
* locale persists
* dashboard calls include headers
* no direct AsyncStorage in PHI-bearing modules except encrypted adapter

---

# 5. Web API Client and API Contract Alignment

## Files

* `products/phr/apps/web/src/api/phrApi.ts`
* `products/phr/apps/web/src/types.ts`
* backend route adapters

## Current Findings

`phrApi.ts` now contains many DTOs/types and correlation ID helper, but it still has mojibake separator comments and uses mixed validation depth: some APIs parse with Zod, others return `as Promise<Type>`. 

## Tasks

| ID    | Action                                                           | File(s)                                           | Expected change                                                                                                       |
| ----- | ---------------------------------------------------------------- | ------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| A-001 | Fix mojibake comments.                                           | `phrApi.ts`                                       | Replace corrupted separators with valid UTF-8 or ASCII.                                                               |
| A-002 | Add common `buildPhrHeaders(context)` helper.                    | `phrApi.ts`                                       | Tenant/principal/role/persona/tier/correlation/idempotency centralized.                                               |
| A-003 | Add common `safeFetchJson(schema)` helper.                       | `phrApi.ts`                                       | Every PHI response validates against schema.                                                                          |
| A-004 | Add Zod schemas for all newly added DTOs.                        | `phrApi.ts` or shared schema package              | No unvalidated `as Promise<Type>` for PHI-bearing payloads.                                                           |
| A-005 | Align consent create payload with backend.                       | `phrApi.ts`, `PhrConsentRoutes.java`              | Use backend’s `patientId`, `recipientId`, `scope.resourceTypes`, `scope.actions`, `expiresAt`.                        |
| A-006 | Align document APIs with backend secure download/preview policy. | `phrApi.ts`, `DocumentsPage.tsx`, document routes | Preview/download must return safe blob/URL with audit.                                                                |
| A-007 | Add idempotency key support for mutating APIs.                   | `phrApi.ts`                                       | Consent, appointments, upload, OCR confirm, profile update, emergency request.                                        |
| A-008 | Add safe API error class with correlation ID.                    | `phrApi.ts`                                       | UI displays safe message + correlation ID.                                                                            |
| A-009 | Remove production mock fallback or enforce dev/test-only.        | `phrApi.ts`, demo data                            | `USE_MOCK` must not be enabled in production bundle.                                                                  |
| A-010 | Split monolithic API file if it blocks maintainability.          | `api/*.ts`                                        | Group `authApi`, `recordsApi`, `consentApi`, `documentsApi`, `emergencyApi`, etc. Keep one exported facade if needed. |

## Verification Batch V4/V5

* Web API contract tests mock server responses and assert schema validation.
* Backend API contract tests assert expected request/response shape.
* One mutation test verifies idempotency header is sent.

---

# 6. Web Page Files — Consistency and Functional Completion

## Files

* `ProfilePage.tsx`
* `TimelinePage.tsx`
* `DocumentsPage.tsx`
* `DocumentUploadPage.tsx`
* `OcrReviewPage.tsx`
* `ConditionsPage.tsx`
* `ObservationsPage.tsx`
* `ImmunizationsPage.tsx`
* `NotificationsPage.tsx`
* `EmergencyAccessPage.tsx`
* new `EmergencyReviewsPage.tsx`
* provider/caregiver/FCHV pages

## Current Findings

`ProfilePage` has functional edit/save flow but uses raw HTML form controls instead of design-system controls and includes hardcoded option labels. 

`TimelinePage` and `DocumentsPage` contain many raw strings and direct `toLocaleDateString()` formatting.    

`DocumentsPage` uses `window.open` with an object URL and logs download failures directly.  

## Tasks

| ID    | Action                                                 | File(s)                                                                 | Expected change                                                              |
| ----- | ------------------------------------------------------ | ----------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| W-001 | Add shared page state components.                      | New `components/PageStates.tsx`                                         | Loading/error/empty/forbidden states reused by all pages.                    |
| W-002 | Add safe alert/error component.                        | New `components/SafeError.tsx`                                          | Safe message + correlation ID; no raw exception details.                     |
| W-003 | Replace raw form controls with design-system controls. | `ProfilePage.tsx`, upload/OCR/consent/appointment pages                 | Use `Input`, `Select`, `Button`, `FormField`, validation messages.           |
| W-004 | Remove all raw strings from `TimelinePage`.            | `TimelinePage.tsx`, locale files                                        | All visible strings via `t()`.                                               |
| W-005 | Remove all raw strings from `DocumentsPage`.           | `DocumentsPage.tsx`, locale files                                       | Titles, filters, buttons, OCR states, empty/loading/error strings via `t()`. |
| W-006 | Replace all direct locale date formatting.             | `TimelinePage.tsx`, `DocumentsPage.tsx`, other pages                    | Use PHR date/time formatters.                                                |
| W-007 | Replace document preview with secure internal viewer.  | `DocumentsPage.tsx`, new `DocumentViewer.tsx`                           | No unmanaged `window.open` PHI object URLs.                                  |
| W-008 | Revoke object URLs safely.                             | `DocumentViewer.tsx`, `DocumentsPage.tsx`                               | Object URL lifecycle tracked and cleaned.                                    |
| W-009 | Replace console errors with safe diagnostics.          | `DocumentsPage.tsx`, all web pages                                      | Safe logger/correlation only.                                                |
| W-010 | Add document detail page.                              | New `DocumentDetailPage.tsx`, route contract                            | Metadata, versions, provenance, audit/download policy.                       |
| W-011 | Complete OCR review UI.                                | `OcrReviewPage.tsx`                                                     | Accept/edit/reject fields, confidence badges, FHIR draft cards, confirm.     |
| W-012 | Complete document upload validation/progress.          | `DocumentUploadPage.tsx`                                                | Type/size/progress/retry; backend-driven constraints.                        |
| W-013 | Complete timeline detail expansion.                    | `TimelinePage.tsx`                                                      | Events link to record/document/encounter detail.                             |
| W-014 | Complete condition detail route.                       | `ConditionsPage.tsx`, new `ConditionDetailPage.tsx`                     | Active/resolved/detail.                                                      |
| W-015 | Complete observation trend chart and detail.           | `ObservationsPage.tsx`, new `ObservationDetailPage.tsx`                 | Date range, abnormal flags, units, provenance.                               |
| W-016 | Complete immunization list/detail.                     | `ImmunizationsPage.tsx`                                                 | Vaccine code, status, administered date, retention indicator.                |
| W-017 | Complete notifications privacy.                        | `NotificationsPage.tsx`                                                 | Redacted list; detail requires authenticated route.                          |
| W-018 | Split emergency request/review UI.                     | `EmergencyAccessPage.tsx`, new `EmergencyReviewsPage.tsx`               | Clinician request vs admin review.                                           |
| W-019 | Implement or hide provider pages.                      | `ProviderDashboardPage.tsx`, `ProviderPatientsPage.tsx`, route contract | No visible placeholder in stable route surface.                              |
| W-020 | Implement or hide caregiver dependents.                | `CaregiverDependentsPage.tsx`, route contract                           | Dependent list/detail with grant scope.                                      |
| W-021 | Implement or hide FCHV dashboard.                      | `FchvDashboardPage.tsx`, route contract                                 | FCHV route must match real role/policy.                                      |
| W-022 | Add accessibility labels and keyboard behavior.        | All pages                                                               | Forms, filters, cards, buttons, tables are keyboard/screen-reader friendly.  |

## Verification Batch V4

One web route traversal suite:

* allowed/denied persona per route
* route renders without raw placeholder
* loading/error/empty state
* primary actions exist and call expected API
* no raw text scan
* a11y smoke per stable route
* document preview/download safe behavior

---

# 7. Backend Route Adapters and HTTP Support

## Files

* `PhrRouteSupport.java`
* `PhrPatientRecordRoutes.java`
* `PhrConsentRoutes.java`
* `PhrAdministrativeRoutes.java`
* `PhrClinicalRoutes.java`
* `PhrDocumentImagingRoutes.java`
* `PhrEmergencyRoutes.java`
* `PhrFhirRoutes.java`
* new/needed route adapters: dashboard, mobile, profile, timeline, provider, caregiver, FCHV, audit

## Tasks

| ID    | Action                                                             | File(s)                                                       | Expected change                                                  |
| ----- | ------------------------------------------------------------------ | ------------------------------------------------------------- | ---------------------------------------------------------------- |
| B-001 | Call tenant/principal/facility validators inside `requireContext`. | `PhrRouteSupport.java`                                        | Validation helpers exist; make them part of context extraction.  |
| B-002 | Add persona/tier/facility to request context.                      | `PhrRouteSupport.java`                                        | Policy and entitlement checks need full context.                 |
| B-003 | Generate server correlation ID if missing.                         | `PhrRouteSupport.java`                                        | Do not use `"no-correlation-id"` fallback.                       |
| B-004 | Use correlation-aware response helpers everywhere.                 | All route adapters                                            | Every response has correlation header.                           |
| B-005 | Use idempotency key on mutating routes.                            | Consent, appointment, upload, OCR confirm, profile, emergency | `extractIdempotencyKey` exists; apply it.                        |
| B-006 | Add dashboard route.                                               | New `PhrDashboardRoutes.java`                                 | Backend-owned dashboard contract.                                |
| B-007 | Add mobile dashboard route.                                        | New `PhrMobileRoutes.java`                                    | Response matches `MobileDashboard`; enforces headers.            |
| B-008 | Add profile route.                                                 | New `PhrPatientProfileRoutes.java` or extend existing         | Field-level edit policy and audit.                               |
| B-009 | Add timeline route.                                                | New `PhrTimelineRoutes.java`                                  | Date/category/facility/provider filters.                         |
| B-010 | Add condition route.                                               | New `PhrConditionRoutes.java`                                 | List/detail.                                                     |
| B-011 | Add provider routes.                                               | New `PhrProviderRoutes.java`                                  | Dashboard, patient search, patient summary, encounter, calendar. |
| B-012 | Add caregiver routes.                                              | New `PhrCaregiverRoutes.java`                                 | Dependent list/detail.                                           |
| B-013 | Add FCHV routes.                                                   | New `PhrFchvRoutes.java`                                      | Community dashboard, registration, vitals capture.               |
| B-014 | Add audit route.                                                   | New/updated `PhrAuditRoutes.java`                             | List/detail/export, admin/compliance policy.                     |
| B-015 | Add secure document preview/download route behavior.               | `PhrDocumentImagingRoutes.java`                               | Audit and stream/download safely.                                |
| B-016 | Add OCR review/confirm route behavior.                             | `PhrDocumentImagingRoutes.java`                               | Structured field review and FHIR draft creation.                 |
| B-017 | Add emergency review route behavior.                               | `PhrEmergencyRoutes.java`                                     | Admin review status and notification.                            |
| B-018 | Replace manual JSON parsing with request validators.               | Route adapters, `PhrRequestValidator.java`                    | Consistent validation and safe errors.                           |
| B-019 | Add OpenAPI/contract source for PHR API.                           | New PHR API contract package                                  | Web/mobile clients and backend routes share contracts.           |

## Verification Batch V5

One backend API contract suite:

* context validation
* policy result
* idempotency
* correlation header
* safe error envelope
* route-specific request/response validation
* PHI access matrix

---

# 8. Kernel Platform Work Required by Current PHR State

## Files / Packages

* `platform/typescript/product-shell/**`
* Kernel lifecycle/product contract packages
* Kernel security/policy packages
* Kernel route/entitlement contract tooling
* root scripts/checks

## Tasks

| ID    | Action                                          | Expected change                                                                                          |
| ----- | ----------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| K-001 | Promote route contract schema to Kernel.        | PHR uses Kernel product route contract instead of product-local-only schema.                             |
| K-002 | Add Kernel route contract generator.            | Generates TS manifest, backend entitlement data, and route docs from one contract.                       |
| K-003 | Add Kernel product policy abstraction for PHI.  | Consent/treatment/facility/emergency/FCHV policies are modeled in Kernel, implemented/configured by PHR. |
| K-004 | Add Kernel mobile PHI policy check.             | Ensures encrypted storage, clear-on-revoke/logout/session-expiry, biometric gating where required.       |
| K-005 | Add Kernel no-legacy/no-deprecation route mode. | Product can opt into fix-forward route model that rejects deprecated/removed states.                     |
| K-006 | Add Kernel request correlation primitive.       | Shared web/mobile/backend correlation ID shape and propagation helpers.                                  |
| K-007 | Add product action contract.                    | Route actions declare endpoint, method, policy, idempotency, confirmation, visibility.                   |
| K-008 | Add product UI state contract.                  | Each route declares loading/error/empty/forbidden requirements.                                          |
| K-009 | Add product i18n/a11y contract.                 | Route/action/card labels and accessibility metadata are validated.                                       |
| K-010 | Add generated artifact cleanup mode.            | Generators delete stale route/page/API artifacts instead of retaining legacy files.                      |

## Verification Batch V6

One Kernel product-contract suite:

* route schema validates PHR
* generated TS/backend artifacts match
* no legacy/deprecated state allowed
* policy contract compiles and denies by default
* product action/UI/i18n/a11y metadata complete

---

# 9. YAPPC Work Required by Current PHR State

## Files / Packages

* `products/yappc/core/scaffold/api/**`
* `products/yappc/frontend/web/src/lib/canvas-ai/**`
* YAPPC scaffold packs/templates
* YAPPC frontend canvas/product model

## Tasks

| ID    | Action                                                               | Expected change                                                             |
| ----- | -------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| Y-001 | Add PHR route contract importer.                                     | YAPPC imports `phr-route-contract.json` and displays route/product gaps.    |
| Y-002 | Add PHR IA/use-case importer.                                        | YAPPC imports PHR vision/IA/screen matrix into product model.               |
| Y-003 | Add Kernel-native generator mode.                                    | YAPPC generates only through Kernel product contracts.                      |
| Y-004 | Add PHR web page templates.                                          | Generates design-system-compliant pages with standard states.               |
| Y-005 | Add PHR mobile screen templates.                                     | Generates i18n/a11y/mobile-safe screens.                                    |
| Y-006 | Add backend route adapter templates.                                 | Generates ActiveJ route + validator + policy + test skeleton.               |
| Y-007 | Add test skeleton generator.                                         | Generates route/API/page/policy/i18n/a11y tests per contract.               |
| Y-008 | Make missing Canvas AI backend endpoints visible in production mode. | No silent no-op for production readiness/generation workflows.              |
| Y-009 | Add stale artifact delete mode.                                      | Generated replacements delete old/legacy artifacts.                         |
| Y-010 | Add PHR product gap board.                                           | Groups tasks by patient/provider/caregiver/FCHV/admin/mobile/security/i18n. |

## Verification Batch V6

One YAPPC roundtrip:

PHR IA + route contract → YAPPC product model → Kernel contract → generated web/API/mobile/test artifacts → parity checks.

---

# 10. Test and Check Files

## New/Updated Scripts

| ID    | File                                          | Purpose                                                      |
| ----- | --------------------------------------------- | ------------------------------------------------------------ |
| T-001 | `scripts/check-phr-route-contract-parity.mjs` | JSON ↔ generated TS ↔ route elements ↔ backend entitlements. |
| T-002 | `scripts/check-phr-no-legacy-route-state.mjs` | Fails deprecated/removed/migration route metadata.           |
| T-003 | `scripts/check-phr-web-i18n.mjs`              | Raw web UI string scan.                                      |
| T-004 | `scripts/check-phr-mobile-i18n.mjs`           | Raw mobile UI string scan.                                   |
| T-005 | `scripts/check-phr-mobile-phi-storage.mjs`    | No direct AsyncStorage for PHI except encrypted adapter.     |
| T-006 | `scripts/check-phr-phi-log-safety.mjs`        | No unsafe console/log usage in PHI surfaces.                 |
| T-007 | `scripts/check-phr-api-contracts.mjs`         | Frontend/mobile/backend API contract parity.                 |
| T-008 | `scripts/check-phr-policy-coverage.mjs`       | Every PHI route has policy ID and test.                      |

## Test Suites

| ID    | Where                                                                         | Coverage                                                                      |
| ----- | ----------------------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| T-009 | `products/phr/apps/mobile/src/services/__tests__/phiEncryptedStorage.test.ts` | Key lifecycle, encrypt/decrypt, corrupt payload, clear-all, biometric policy. |
| T-010 | `products/phr/apps/mobile/src/services/__tests__/offlineStore.test.ts`        | TTL, session binding, clear on revoke/logout/expiry.                          |
| T-011 | `products/phr/apps/mobile/src/services/__tests__/phrMobileApi.test.ts`        | Headers, validation, logout cleanup, consent revoke cleanup.                  |
| T-012 | `products/phr/apps/web/src/__tests__/route-contract-parity.test.ts`           | Route manifest/route elements/page states.                                    |
| T-013 | `products/phr/apps/web/e2e/phr-route-traversal.spec.ts`                       | Every stable route renders allowed/denied/loading/error/empty.                |
| T-014 | Backend policy tests                                                          | Patient/caregiver/clinician/admin/FCHV/emergency matrix.                      |
| T-015 | Backend API contract tests                                                    | Auth, dashboard, records, consent, docs, emergency, audit, mobile dashboard.  |
| T-016 | YAPPC roundtrip test                                                          | PHR IA → Kernel contract → generated artifacts.                               |

---

# Recommended Implementation Order

1. **Group 3 + Group 4 first:** policy fail-closed and PHI encryption correctness.
2. **Group 1 + Group 2 next:** canonical route contract, no legacy route states, entitlement parity.
3. **Group 5 + Group 7:** API client/backend contract alignment and missing route adapters.
4. **Group 6:** web page consistency, i18n, safe preview/download, real emergency review page.
5. **Group 8:** Kernel route/policy/product contract support.
6. **Group 9:** YAPPC generation/import only after Kernel contract shape is stable.
7. **Group 10:** add consolidated verification batches and make them part of required checks.

This ordering keeps verification minimal: one policy suite, one mobile PHI suite, one route parity suite, one web route traversal suite, one backend API suit
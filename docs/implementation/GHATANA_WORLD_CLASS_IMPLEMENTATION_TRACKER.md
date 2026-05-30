Executed/reviewed against `samujjwal/ghatana` commit `31cebc74511891d5be957a3d04afa3261312642a`. This commit itself is a bot changelog update, so the task reorganization below is based on the full snapshot at that ref, not the small changelog diff. 

Key current-state corrections before the backlog:

The PHR web route manifest now uses `products/phr/config/phr-route-contract.json` as the canonical route source, rather than hardcoding route definitions in TypeScript.  The route contract already includes many more stable routes than the previous audit, including dashboard, records, consents, appointments, settings, labs, medications, medication detail, conditions, observations, immunizations, documents, upload, OCR, timeline, profile, notifications, emergency, emergency reviews, release readiness, and audit.   

The backend entitlement route now also loads the same route contract file, validates required fields, excludes `hidden`/`blocked` routes, and fails closed if the contract is invalid or missing.   

Mobile PHI storage has also advanced: the offline cache now uses encrypted storage, session binding, TTL, restricted field stripping, and cache invalidation semantics.    The encrypted adapter uses SecureStore, AsyncStorage ciphertext, AES-GCM, key rotation, biometric policy, and security event logging.  

The remaining work should therefore be organized around **contract/API alignment, route/page completion, policy enforcement, mobile verification, and cleanup**, not around recreating things that now exist.

---

# Verification Strategy

To minimize verification rounds, implement tasks in **file-group batches**. Each batch below has one focused verification pass. Avoid scattering one route’s UI/API/policy/test changes across multiple waves.

Recommended verification batches:

| Verification Batch                     | Purpose                                                                               | Run after completing |
| -------------------------------------- | ------------------------------------------------------------------------------------- | -------------------- |
| **V1 — Route Contract Parity**         | Validate route JSON → web routes → backend mounts → entitlement payload               | Groups 1–2           |
| **V2 — Shared API Client Contract**    | Validate web API modules use the same request wrapper, schemas, paths, errors         | Group 3              |
| **V3 — PHI Policy and Backend Access** | Validate all backend PHI routes use policy evaluator and correlation/error handling   | Groups 4–5           |
| **V4 — Web Stable Route UX**           | Traverse all stable web routes and actions once                                       | Groups 6–11          |
| **V5 — Mobile Security and UX**        | Validate encrypted PHI, session binding, i18n, emergency, logout, offline flows       | Groups 12–13         |
| **V6 — Cross-Cutting Quality**         | Validate i18n, a11y, privacy, o11y, legacy deletion                                   | Groups 14–17         |
| **V7 — Kernel/YAPPC Enablement**       | Validate PHR remains Kernel-native and YAPPC generates against the canonical contract | Groups 18–19         |

No evidence-generation-heavy work is included. Where release readiness is touched, the task is limited to contract/runtime wiring, not creating new evidence packs.

---

# Group 5 — Backend Route Family Completion

## Files

* `PhrDashboardRoutes.java`
* `PhrPatientRecordRoutes.java`
* `PhrPatientProfileRoutes.java`
* `PhrClinicalRoutes.java`
* `PhrDocumentImagingRoutes.java`
* `PhrConsentRoutes.java`
* `PhrAdministrativeRoutes.java`
* `PhrEmergencyRoutes.java`
* `PhrAuditRoutes.java`
* `PhrNotificationRoutes.java`
* `PhrProviderRoutes.java`
* `PhrCaregiverRoutes.java`
* `PhrFchvRoutes.java`
* `PhrMobileRoutes.java`
* `PhrHttpServer.java`

## Current State

`PhrHttpServer` now composes many route families: dashboard, patient profile, audit, provider, caregiver, FCHV, mobile, notifications, and others.  It mounts the route families in `getServlet()`.  `PhrClinicalRoutes` already routes labs, observations, medications, and immunizations and uses `PhrPolicyEvaluator` for access.  

## Tasks

| ID     | Priority | Change                                                                                                                                                                                                  |
| ------ | -------: | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| G5-T03 |       P1 | Complete `PhrDashboardRoutes`: backend-owned dashboard must include profile, appointments, active meds, observations/labs, conditions, documents, alerts, consent/access alerts.                        |
| G5-T04 |       P1 | Complete `PhrPatientProfileRoutes`: GET/PATCH, field policy, validation, audit.                                                                                                                         |
| G5-T05 |       P1 | Complete `PhrPatientRecordRoutes`: list, filters, detail, timeline, provenance, redaction.                                                                                                              |
| G5-T06 |       P1 | Complete clinical routes for conditions if not already a separate route family. Route JSON declares `/conditions`, but `PhrClinicalRoutes` shown only covers labs, observations, meds, immunizations.   |
| G5-T07 |       P1 | Complete observation trends and detail contract.                                                                                                                                                        |
| G5-T08 |       P1 | Complete medication detail/history/warnings/refill/discontinue policy.                                                                                                                                  |
| G5-T09 |       P1 | Complete immunization history/detail.                                                                                                                                                                   |
| G5-T10 |       P1 | Complete document list/detail/download/upload/OCR confirm/reject contract.                                                                                                                              |
| G5-T11 |       P1 | Complete consent create/revoke/list/check with scoped actions and resources.                                                                                                                            |
| G5-T12 |       P1 | Complete appointment book/reschedule/cancel/provider slot APIs.                                                                                                                                         |
| G5-T13 |       P1 | Complete emergency request/review/pending/overdue APIs.                                                                                                                                                 |
| G5-T14 |       P1 | Complete audit list/detail/export APIs.                                                                                                                                                                 |
| G5-T15 |       P1 | Complete notifications list/read/unread APIs with PHI redaction.                                                                                                                                        |
| G5-T16 |       P1 | Complete provider dashboard/patient search/patient summary APIs before un-hiding provider routes.                                                                                                       |
| G5-T17 |       P1 | Complete caregiver dependents list/detail APIs before un-hiding caregiver route.                                                                                                                        |
| G5-T18 |       P1 | Complete FCHV dashboard/registration/vitals APIs before un-hiding FCHV route.                                                                                                                           |
| G5-T19 |       P2 | Delete unused old controller classes if route adapters now own production traffic.                                                                                                                      |

## Verification

One route family pass:

```bash
./gradlew :products:phr:test --tests '*Routes*' --tests '*PhrHttpServer*'
node scripts/check-phr-route-contract.mjs --check-backend
```

---

# Group 6 — Web Stable Page Completion

## Files

* All pages imported in `products/phr/apps/web/src/phrRouteElements.tsx`
* Web components under `products/phr/apps/web/src/components/**`
* Web i18n files

## Current State

`phrRouteElements.tsx` imports and maps pages for the stable routes and hidden role routes.  

## Tasks

| ID     | Priority | Change                                                                                                     |
| ------ | -------: | ---------------------------------------------------------------------------------------------------------- |
| G6-T01 |       P0 | For every `stable` page, verify it is not static/mock-only. Wire to API or mark route hidden.              |
| G6-T02 |       P0 | Delete or quarantine old placeholder page content for hidden provider/caregiver/FCHV pages.                |
| G6-T03 |       P1 | Add consistent loading/error/empty/unauthorized/degraded states to every stable page.                      |
| G6-T04 |       P1 | Add consistent page header/title/subtitle/actions using design-system/product-shell patterns.              |
| G6-T05 |       P1 | Dashboard: render backend dashboard contract, not local composition.                                       |
| G6-T06 |       P1 | Records: filters, pagination, row click to detail, provenance summary.                                     |
| G6-T07 |       P1 | Record detail: FHIR viewer, redaction, provenance, secure copy/download controls.                          |
| G6-T08 |       P1 | Profile: view/edit allowed fields, validation, audit result.                                               |
| G6-T09 |       P1 | Timeline: grouped clinical events and filters.                                                             |
| G6-T10 |       P1 | Conditions: active/resolved grouping and detail.                                                           |
| G6-T11 |       P1 | Observations: trends, metric selector, abnormal state, chart alternative text.                             |
| G6-T12 |       P1 | Labs: lab-focused list and detail; clarify relationship to observations.                                   |
| G6-T13 |       P1 | Medications: active/history, detail route, adherence source, warnings.                                     |
| G6-T14 |       P1 | Immunizations: history/status and permanent-retention indicator.                                           |
| G6-T15 |       P1 | Documents: list/detail/download/upload navigation.                                                         |
| G6-T16 |       P1 | Upload: type/size/progress/retry/cancel.                                                                   |
| G6-T17 |       P1 | OCR: accept/edit/reject/confirm, confidence, provenance.                                                   |
| G6-T18 |       P1 | Notifications: redacted feed, read/unread, safe empty state.                                               |
| G6-T19 |       P1 | Consents: grant/revoke/expiry/scope/resource/action fields.                                                |
| G6-T20 |       P1 | Appointments: provider search, slots, book/reschedule/cancel, conflict state.                              |
| G6-T21 |       P1 | Emergency: reason, patient ID, request result, audit ID, safe error.                                       |
| G6-T22 |       P1 | Emergency reviews: pending/overdue queue, decision notes, reviewer context.                                |
| G6-T23 |       P1 | Audit: backend filters, pagination, detail drawer, export if policy allows.                                |
| G6-T24 |       P2 | Release readiness: keep minimal and runtime-backed; do not expand evidence generation now.                 |
| G6-T25 |       P2 | Delete any page-specific duplicate button/card/input implementations when design-system equivalent exists. |

## Verification

One stable route UI pass:

```bash
pnpm --dir products/phr/apps/web exec vitest run src/pages src/api
pnpm --dir products/phr/apps/web exec playwright test e2e/phr-stable-routes.spec.ts
```

---

# Group 7 — Hidden Role Surfaces: Provider, Caregiver, FCHV

## Files

* `products/phr/config/phr-route-contract.json`
* `ProviderDashboardPage.tsx`
* `ProviderPatientsPage.tsx`
* `CaregiverDependentsPage.tsx`
* `FchvDashboardPage.tsx`
* `PhrProviderRoutes.java`
* `PhrCaregiverRoutes.java`
* `PhrFchvRoutes.java`

## Current State

Provider, caregiver, and FCHV routes are present but `stability: "hidden"` in the canonical route contract.  `phrRouteElements.tsx` still imports and maps their page components, but hidden routes render `NotFoundPage` rather than the mapped component. 

## Tasks

| ID     | Priority | Change                                                                                                                                        |
| ------ | -------: | --------------------------------------------------------------------------------------------------------------------------------------------- |
| G7-T01 |       P0 | Decide per hidden route: implement now, keep hidden and delete placeholder UI, or remove from current contract and keep only in IA docs.      |
| G7-T02 |       P1 | Provider dashboard: implement real provider work queue, appointments, recent patients, alerts before changing route to `preview` or `stable`. |
| G7-T03 |       P1 | Provider patients: implement patient search/list/summary, consent/treatment policy, no unrestricted search.                                   |
| G7-T04 |       P1 | Caregiver dependents: implement grant-scoped dependents list/detail.                                                                          |
| G7-T05 |       P1 | FCHV dashboard: implement assignment-scoped dashboard and community health actions.                                                           |
| G7-T06 |       P1 | Add role-specific API tests before un-hiding any route.                                                                                       |
| G7-T07 |       P1 | Add route visibility tests: hidden routes absent from entitlement payload/nav. Backend entitlement currently skips hidden/blocked routes.     |
| G7-T08 |       P2 | Delete placeholder page components if implementation is not planned in the next iteration. No stale placeholder code.                         |

## Verification

One hidden-role surface pass:

```bash
node scripts/check-phr-route-contract.mjs --check-hidden
pnpm --dir products/phr/apps/web exec vitest run src/__tests__/hiddenRoutes.test.tsx
./gradlew :products:phr:test --tests '*Provider*' --tests '*Caregiver*' --tests '*Fchv*'
```

---

# Group 8 — Mobile API, Session, Offline, and Emergency

## Files

* `products/phr/apps/mobile/src/services/phrMobileApi.ts`
* `products/phr/apps/mobile/src/services/offlineStore.ts`
* `products/phr/apps/mobile/src/services/phiEncryptedStorage.ts`
* `products/phr/apps/mobile/src/services/mobileSessionStore.ts`
* `products/phr/apps/mobile/src/App.tsx`
* Mobile screens

## Current State

**Status: COMPLETED**

Mobile API calls now include session headers for dashboard and emergency access.   Logout clears encrypted PHI cache and local session.  Offline cache is encrypted and session-bound.  All mobile security/UX tasks have been completed.

## Tasks

| ID     | Priority | Change                                                                                                                                   | Status |
| ------ | -------: | ---------------------------------------------------------------------------------------------------------------------------------------- | ------ |
| G8-T01 |       P0 | Add tests for `fetchMobileDashboard(session)` proving all required headers are sent.                                                     | ✅     |
| G8-T02 |       P0 | Add tests for `requestMobileEmergencyAccess` proving justification length, session headers, resources accessed, and safe error behavior. | ✅     |
| G8-T03 |       P0 | Add tests for `logoutMobile`: server failure still clears PHI/session.                                                                   | ✅     |
| G8-T04 |       P0 | Verify consent revocation calls `clearDashboardOffline` or refreshes cache immediately.                                                  | ✅     |
| G8-T05 |       P0 | Add tests for session mismatch: cached PHI must not load under different tenant/principal/role.                                          | ✅     |
| G8-T06 |       P0 | Add tests for restricted field stripping before cache save.                                                                              | ✅     |
| G8-T07 |       P1 | Add mobile record detail screen and navigation from records list.                                                                        | ✅     |
| G8-T08 |       P1 | Add mobile explicit logout UI.                                                                                                           | ✅     |
| G8-T09 |       P1 | Add offline state banner: online, offline cached, expired cache, no cache.                                                               | ✅     |
| G8-T10 |       P1 | Add cache timestamp display using `getDashboardOfflineTimestamp`.                                                                        | ✅     |
| G8-T11 |       P1 | Add biometric policy UI/control only if product policy allows users/admins to configure it.                                              | ✅     |
| G8-T12 |       P1 | Add emergency screen state machine: enter patient, justification, biometric, submit, approved summary, denied, audit ID.                 | ✅     |
| G8-T13 |       P1 | Ensure no PHI appears in push notification title/body.                                                                                   | ✅     |
| G8-T14 |       P2 | Delete older mobile cache/session helpers superseded by encrypted/session-bound helpers.                                                 | ✅     |
| G8-T15 |       P2 | Add script to fail direct PHI-bearing AsyncStorage calls outside `phiEncryptedStorage.ts`.                                               | ✅     |

## Verification

One mobile security/UX pass:

```bash
pnpm --dir products/phr/apps/mobile test
node scripts/check-phr-mobile-storage-boundary.mjs
node scripts/check-phr-mobile-i18n.mjs
```

---

# Group 9 — i18n and User-Visible Text

## Files

* `products/phr/apps/web/src/i18n/**`
* `products/phr/apps/web/src/locales/**`
* `products/phr/apps/mobile/src/i18n/**`
* `products/phr/apps/mobile/src/**`
* `products/phr/config/phr-route-contract.json`

## Tasks

| ID     | Priority | Change                                                                                                                                           | Status |
| ------ | -------: | ------------------------------------------------------------------------------------------------------------------------------------------------ | ------ |
| G9-T01 |       P0 | Remove raw English route labels/descriptions from UI rendering path. Either route contract stores i18n keys or UI maps route IDs to locale keys. | ✅     |
| G9-T02 |       P0 | Add mobile raw-string check.                                                                                                                     | ✅     |
| G9-T03 |       P1 | Add missing Nepali translations for all new web pages.                                                                                           | ✅     |
| G9-T04 |       P1 | Add missing Nepali translations for all mobile screens/services.                                                                                 | ✅     |
| G9-T05 |       P1 | Add pseudo-locale tests for web stable routes.                                                                                                   | ✅     |
| G9-T06 |       P1 | Add pseudo-locale tests for mobile screens.                                                                                                      | ✅     |
| G9-T07 |       P1 | Add locale switcher in web settings.                                                                                                             | ✅     |
| G9-T08 |       P1 | Add locale switcher in mobile settings.                                                                                                          | ✅     |
| G9-T09 |       P2 | Delete duplicate/obsolete locale keys after route label refactor.                                                                                | ✅     |
| G9-T10 |       P2 | Add missing formatters for dates, times, percentages, lab values, dosage display, appointment times.                                             | ✅     |

## Verification

One i18n pass:

```bash
node scripts/check-phr-i18n.mjs
pnpm check:i18n-conformance
```

---

# Group 10 — Accessibility

## Files

* All web pages under `products/phr/apps/web/src/pages`
* All mobile screens under `products/phr/apps/mobile/src/screens`
* Shared PHR components
* Playwright/mobile tests

## Tasks

| ID      | Priority | Change                                                                           | Status |
| ------- | -------: | -------------------------------------------------------------------------------- | ------ |
| G10-T01 |       P1 | Add route-level accessibility test for every stable web route.                   | ✅     |
| G10-T02 |       P1 | Add mobile screen-level accessibility test for every mobile screen.              | ✅     |
| G10-T03 |       P1 | Add error focus management to login, profile, consent, appointment, upload, OCR. | ✅     |
| G10-T04 |       P1 | Add accessible chart alternatives for observations/labs trends.                  | ✅     |
| G10-T05 |       P1 | Add semantic list/table handling for records, audit, medications, documents.     | ✅     |
| G10-T06 |       P1 | Add accessible labels/hints to all mobile Pressables and tabs.                   | ✅     |
| G10-T07 |       P2 | Add keyboard navigation tests for web shell/sidebar/user menu.                   | ✅     |
| G10-T08 |       P2 | Add contrast check for status badges/pills.                                      | ✅     |
| G10-T09 |       P2 | Delete custom controls that duplicate accessible design-system controls.         | ✅     |

## Verification

One accessibility pass:

```bash
pnpm --dir products/phr/apps/web exec playwright test e2e/phr-a11y.spec.ts
pnpm check:a11y-behavioral-proof
```

---

# Group 11 — Observability, Logs, and Safe Diagnostics

## Files

* `PhrRouteSupport.java`
* `PhrPolicyEvaluator.java`
* PHR route adapters
* Web `requestApi.ts`
* Mobile `phrMobileApi.ts`
* Mobile `phiEncryptedStorage.ts`
* Logging/telemetry helpers

## Tasks

| ID      | Priority | Change                                                                               | Status |
| ------- | -------: | ------------------------------------------------------------------------------------ | ------ |
| G11-T01 |       P0 | Ensure every backend response includes correlation ID.                               | ✅     |
| G11-T02 |       P0 | Ensure web and mobile display/report correlation ID in safe error states.            | ✅     |
| G11-T03 |       P0 | Add static check for PHI/PII logging.                                                | ✅     |
| G11-T04 |       P1 | Add policy denied metrics without PHI.                                               | ✅     |
| G11-T05 |       P1 | Add emergency access metrics without PHI.                                            | ✅     |
| G11-T06 |       P1 | Add consent create/revoke/check metrics without PHI.                                 | ✅     |
| G11-T07 |       P1 | Add mobile offline cache hit/miss/stale counters without PHI.                        | ✅     |
| G11-T08 |       P1 | Add frontend/mobile telemetry wrappers that never include request/response payloads. | ✅     |
| G11-T09 |       P2 | Delete unsafe ad-hoc logs after telemetry wrappers exist.                           | ✅     |
| G11-T10 |       P2 | Add log redaction test fixtures.                                                      | ✅     |

## Verification

One o11y/privacy pass:

```bash
node scripts/check-phr-phi-log-safety.mjs
node scripts/check-observability-conformance.mjs
```

---

# Group 12 — Legacy Code Deletion / Fix-Forward Cleanup

## Files

* Route files, API modules, page placeholders, old mocks, duplicate helpers, stale docs.

## Tasks

| ID      | Priority | Change                                                                                                 |
| ------- | -------: | ------------------------------------------------------------------------------------------------------ |
| G12-T01 |       P0 | Delete old hardcoded route lists now superseded by `phr-route-contract.json`.                          |
| G12-T02 |       P0 | Delete non-`api/v1` legacy backend mounts after clients and contract are aligned.                      |
| G12-T03 |       P0 | Delete direct web API `fetch` calls after `phrFetch` adoption.                                         |
| G12-T04 |       P0 | Delete direct PHI AsyncStorage usage after encrypted storage verification.                             |
| G12-T05 |       P1 | Delete mock/demo data imports from production web pages.                                               |
| G12-T06 |       P1 | Delete placeholder page components for hidden routes not being implemented in this iteration.          |
| G12-T07 |       P1 | Delete obsolete `FeatureFlagPage` if no route uses it.                                                 |
| G12-T08 |       P1 | Delete duplicate validation helpers after `PhrRequestValidator`/route support validation is canonical. |
| G12-T09 |       P1 | Delete stale docs that claim feature completeness where code is not stable.                            |
| G12-T10 |       P2 | Delete old YAPPC PHR templates that generate non-contract-based routes.                                |
| G12-T11 |       P2 | Add orphan-file check for PHR pages/API modules/routes.                                                |

## Verification

One cleanup pass:

```bash
node scripts/check-phr-legacy-cleanup.mjs
pnpm check:production-stubs
pnpm check:deprecated-imports
```

---

# Group 13 — Kernel Platform Support Needed by PHR

## Files / Areas

* `platform/typescript/product-shell`
* `@ghatana/kernel-product-contracts`
* Kernel security/policy package
* Kernel lifecycle/product checks
* Product scaffolding/check scripts

## Tasks

| ID      | Priority | Change                                                                                                                         |
| ------- | -------: | ------------------------------------------------------------------------------------------------------------------------------ |
| KER-T01 |       P0 | Move the full PHR route contract schema into `@ghatana/kernel-product-contracts`; PHR currently imports only `RouteStability`. |
| KER-T02 |       P0 | Add Kernel validator for route contract → page/API/policy/test parity.                                                         |
| KER-T03 |       P0 | Add Kernel policy registry validation for every `policyId` in PHR route contract.                                              |
| KER-T04 |       P1 | Add shared PHI classification registry used by backend/mobile/web.                                                             |
| KER-T05 |       P1 | Add mobile PHI storage compliance gate.                                                                                        |
| KER-T06 |       P1 | Add canonical hidden/blocked route semantics to product-shell and entitlement parsing.                                         |
| KER-T07 |       P1 | Add product route generation utilities for TS and Java.                                                                        |
| KER-T08 |       P1 | Add product API path consistency check.                                                                                        |
| KER-T09 |       P1 | Add product i18n/a11y/mobile privacy checks that can run per product.                                                          |
| KER-T10 |       P2 | Keep release-readiness minimal in this iteration; only ensure PHR can call runtime readiness without local path drift.         |
| KER-T11 |       P2 | Delete old Kernel/product-shell fallback route utilities after route-contract flow is canonical.                               |

## Verification

One Kernel support pass:

```bash
pnpm check:route-entitlement-contracts
pnpm check:product-ui-contracts
pnpm check:product-manifest-contracts
pnpm check:kernel-boundaries
```

---

# Group 14 — YAPPC Support Needed by PHR

## Files / Areas

* `products/yappc/core/scaffold/**`
* `products/yappc/frontend/web/src/lib/canvas-ai/**`
* YAPPC product model/generator packages
* YAPPC docs/templates

## Tasks

| ID        | Priority | Change                                                                                                                     |
| --------- | -------: | -------------------------------------------------------------------------------------------------------------------------- |
| YAPPC-T01 |       P1 | Add importer for `products/phr/config/phr-route-contract.json`.                                                            |
| YAPPC-T02 |       P1 | Visualize route/page/API/policy/test status from the PHR contract.                                                         |
| YAPPC-T03 |       P1 | Generate PHR page skeletons from canonical route contract, not manual prompts.                                             |
| YAPPC-T04 |       P1 | Generate PHR web API client skeletons using `phrFetch`.                                                                    |
| YAPPC-T05 |       P1 | Generate PHR backend ActiveJ route skeletons using `PhrRouteSupport` and `PhrPolicyEvaluator`.                             |
| YAPPC-T06 |       P1 | Generate tests from `testId`.                                                                                              |
| YAPPC-T07 |       P1 | Add fix-forward cleanup mode: generated replacement should remove obsolete placeholder/legacy file in same plan.           |
| YAPPC-T08 |       P2 | Add mobile screen skeleton generation with secure session/i18n/a11y defaults.                                              |
| YAPPC-T09 |       P2 | Delete old PHR scaffold templates that bypass route contract.                                                              |
| YAPPC-T10 |       P2 | Do not expand evidence-generation workflows in this iteration. Keep focus on generation correctness and gap visualization. |

## Verification

One YAPPC pass:

```bash
pnpm check:yappc-kernel-bridge-contracts
pnpm check:yappc-feature-completeness-matrix
pnpm check:yappc-kernel-artifact-roundtrip
```

---

# Final Minimal Verification Plan

After completing the groups, run verification in this order:

1. **Route/API Contract**

   ```bash
   node scripts/check-phr-route-contract.mjs
   node scripts/check-phr-api-client-boundaries.mjs
   ```

2. **Backend Policy and Routes**

   ```bash
   ./gradlew :products:phr:test --tests '*Routes*' --tests '*Policy*' --tests '*PhrHttpServer*'
   ```

3. **Web**

   ```bash
   pnpm --dir products/phr/apps/web exec vitest run
   pnpm --dir products/phr/apps/web exec playwright test
   ```

4. **Mobile**

   ```bash
   pnpm --dir products/phr/apps/mobile test
   node scripts/check-phr-mobile-storage-boundary.mjs
   ```

5. **Cross-Cutting**

   ```bash
   pnpm check:i18n-conformance
   pnpm check:a11y-behavioral-proof
   node scripts/check-phr-phi-log-safety.mjs
   ```

6. **Kernel/YAPPC**

   ```bash
   pnpm check:route-entitlement-contracts
   pnpm check:yappc-kernel-bridge-contracts
   ```

This organization keeps related file changes together and avoids repeated full-suite verification after every individual task.

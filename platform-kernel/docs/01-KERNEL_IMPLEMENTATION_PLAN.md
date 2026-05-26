## 1. Executive Summary

I audited `samujjwal/ghatana` at commit `600bebfa0832716d6589d5bcae223191138563cc`. The commit was confirmed, and its message is “Resolve merge conflicts in evidence files by accepting remote versions.” 

**Bottom line:** PHR is not yet production-ready as a full product. The **backend/kernel-side PHR implementation is much more mature than the current web/mobile UI**, but there is a major gap between the documented PHR vision/IA and the implemented user-facing product.

The PHR vision describes a broad Nepal-market personal health record product covering medical records, prescriptions, labs, appointment history, clinical notes, imaging, immunizations, referrals, caregiver access, telemedicine, emergency break-glass access, AI decision support, HIE integration, Nepal Directive 2081, Nepal Privacy Act 2075, HIPAA-style controls, and FHIR R4 interoperability. 

Current **web routing**, however, implements only a flat route set: `/dashboard`, `/records`, `/records/:recordId`, `/consents`, `/appointments`, `/labs`, `/medications`, `/emergency`, `/release-readiness`, `/audit`, and `/settings`.  The documented IA expects a much broader nested structure with `/app/patient/*`, `/app/provider/*`, `/app/caregiver/*`, `/app/admin/*`, `/app/fchv/*`, documents, OCR, voice input, insurance, claims, emergency QR, provider search, encounters, observation entry, medication request entry, and more. 

**Kernel is partially used correctly**: `PhrKernelModule` composes many PHR services, registers lifecycle/audit/consent events, uses Kernel context, registers services, and wires backend route adapters.   But PHR is not fully Kernel-native across all lifecycle phases because UI IA, journey completeness, feature visibility, generated route/page contracts, frontend state, mobile offline policy, tests, and release evidence are still partly product-local or incomplete.

**YAPPC is not yet useful enough as a PHR accelerator.** Its own README says the implementation is partial: AI-native maturity 3/10, feature completeness 4/10, production readiness 2/10, with phases 3–7 still active/incomplete.  The YAPPC Kernel bridge exposes narrow artifact/intelligence provider ports, which is directionally correct, but it does not yet prove PHR-specific IA-to-code generation, route/page/API validation, journey visualization, or completeness detection. 

## 2. Repository Map

| Area              | Key implementation evidence                                                                                                                                     |                                       Maturity |
| ----------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------: |
| PHR vision/docs   | Vision and IA define full product scope, personas, services, compliance, AI, mobile, HIE, FHIR, telemedicine, caregiver, emergency workflows.                   |           Broad but partly stale/over-claiming |
| PHR web           | Flat React Router setup and route contract list.                                                                                                                |                               Partial UI shell |
| PHR mobile        | React Native tab shell with dashboard, records, consents, alerts, emergency, settings; local auth state and offline cache fallback.                             |                                 Early scaffold |
| PHR backend/API   | ActiveJ HTTP server exposes FHIR, patients, consents, clinical, emergency, admin, records, release readiness, entitlements, health.                             | Stronger than UI, still needs end-to-end proof |
| PHR Kernel module | Composes PHR services, evidence outbox, FHIR server, HIE integration, HL7 lab integration, consent cache, service catalog.                                      |                                Good foundation |
| Kernel platform   | Vision claims module lifecycle, capability composition, dependency resolution, plugin architecture, health, cross-product communication, boundary enforcement.  |      Mature core, incomplete PHR lifecycle fit |
| YAPPC             | 8-phase creator lifecycle, partial implementation, Kernel delegation model, active buildout.                                                                    |                   Not ready as PHR accelerator |

## 3. PHR Vision and IA Coverage Matrix

| Vision/IA area                            | Expected from vision/IA                                                                                                            | Current code status                                                                | Gap                                          |
| ----------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- | -------------------------------------------- |
| Patient dashboard                         | Full summary: profile, appointments, meds, observations, conditions, alerts. Screen matrix requires dashboard endpoint and tests.  | `/dashboard` exists, but uses compact dashboard data from `fetchDashboardData`.    | Partial; no full IA dashboard contract       |
| Patient profile                           | Dedicated profile view/update.                                                                                                     | No `/profile` web route.                                                           | Missing                                      |
| Timeline/encounters                       | Medical history timeline and encounters.                                                                                           | Backend has patient history endpoint, but no full timeline UI.                     | Partial/backend-only                         |
| Conditions                                | Conditions list/detail.                                                                                                            | No current web route.                                                              | Missing                                      |
| Observations/vitals/labs                  | Observations/trends with chart range, abnormal highlights.                                                                         | `/labs` exists; no trend view.                                                     | Partial                                      |
| Medications                               | Active/history tabs, detail, filters.                                                                                              | `/medications` exists, simple list only.                                           | Partial                                      |
| Appointments                              | List plus booking flow.                                                                                                            | `/appointments` has inputs and submit button, but no submit handler/API mutation.  | UI-only                                      |
| Documents                                 | Documents, upload, preview, versioning.                                                                                            | Backend route group exists under `/records/*`, but no web document route.          | Backend-only/hidden                          |
| OCR                                       | OCR review, confidence badges, FHIR draft confirmation.                                                                            | No visible web/mobile flow.                                                        | Missing                                      |
| Voice input                               | Audio input/transcription/FHIR confirm.                                                                                            | No visible PHR route.                                                              | Missing                                      |
| Insurance/payments/claims                 | Insurance summary, payments, claims Phase 2.                                                                                       | Backend billing service exists, but no UI IA implementation.                       | Backend-only/Phase 2 unclear                 |
| Referrals/imaging                         | Referrals and imaging viewer.                                                                                                      | Services/routes exist, no UI pages.                                                | Backend-only                                 |
| Sharing/access grants                     | Grant/revoke/expiry UI and access reflection.                                                                                      | `/consents` displays grants; update button has no handler.                         | Partial/UI-only                              |
| Provider dashboard/search/patient summary | Provider route tree expected.                                                                                                      | No provider route group in current web app.                                        | Missing                                      |
| Caregiver dependents                      | Caregiver route group expected.                                                                                                    | No caregiver route group; only role threshold access.                              | Missing                                      |
| FCHV/community health                     | FCHV dashboard/register/patients/vitals expected.                                                                                  | No FCHV role or routes in current route contract.                                  | Missing                                      |
| Admin audit                               | Admin audit route expected.                                                                                                        | `/audit` exists but uses simulated mock events in component.                       | Not production-ready                         |
| Release readiness                         | Admin release readiness expected.                                                                                                  | `/release-readiness` exists and calls backend.                                     | Good direction, needs full gate proof        |
| Mobile                                    | Login/home/records/consents/alerts/emergency/settings/offline.                                                                     | Implemented as simple tab shell.                                                   | Partial, no real auth/session/consent policy |

## 4. PHR End-to-End Journey Audit

| Journey                         | Current status                                                                                    | Kernel involvement                                   | Key gap                                                |
| ------------------------------- | ------------------------------------------------------------------------------------------------- | ---------------------------------------------------- | ------------------------------------------------------ |
| Patient signs in                | Web login exists, but demo link and inputs are not tied to real auth in inspected web route flow. | Product-shell role context, not full Kernel auth     | Missing real auth/session bootstrap                    |
| Patient dashboard               | Implemented as summary cards.                                                                     | Uses frontend FHIR aggregation API.                  | No `/patients/:id/dashboard` contract matching IA      |
| Patient profile                 | Backend patient read/update exists.                                                               | PHR backend service via Kernel                       | No dedicated UI profile page                           |
| Records list/detail             | UI exists; detail renders stored FHIR JSON.                                                       | Frontend pulls FHIR resources                        | No full timeline/category/documents model              |
| Consent grant/revoke/check      | Backend routes exist.                                                                             | Consent service + distributed cache via Kernel       | Web only lists grants; update has no behavior          |
| Provider access through consent | Backend patient routes call `validateAccess`.                                                     | PHR consent service                                  | No provider UI journey                                 |
| Consent expiry blocks access    | Backend parses `expiresAt`; access validation exists via service.                                 | Kernel-backed consent service                        | Need end-to-end UI/API tests                           |
| Appointment request             | UI form exists.                                                                                   | Backend admin appointment routes exist.              | Form not wired to create/reschedule/conflict handling  |
| Labs/vitals                     | Simple labs list route.                                                                           | Backend clinical routes exist.                       | No observations trend/vitals IA                        |
| Medications                     | Simple medication list route.                                                                     | Backend clinical routes exist.                       | No detail/history/prescription flow                    |
| Emergency break-glass           | Backend access/review/pending/overdue routes exist.                                               | Kernel event/audit notification integration exists.  | Web emergency buttons not wired to request/review APIs |
| Audit trail                     | Backend event evidence exists; web page is mock.                                                  | Kernel event evidence outbox                         | UI is simulated; no real API integration               |
| Mobile offline                  | API fallback to offline cache exists.                                                             | Not clearly Kernel-governed                          | No explicit PHI cache policy/encryption/expiry         |
| Release readiness               | Web calls `/release-readiness`; script gates exist.                                               | Kernel lifecycle/evidence-oriented                   | Needs route-to-runtime evidence parity                 |

## 5. Kernel-Native PHR Lifecycle Audit

| Phase                        | Current state                                                      | Gap                                                                          |
| ---------------------------- | ------------------------------------------------------------------ | ---------------------------------------------------------------------------- |
| Product definition           | PHR has kernel module, product docs, lifecycle readiness script.   | Docs and implementation disagree on maturity                                 |
| Capability model             | PHR declares Kernel capabilities and dependencies.                 | UI routes are not generated from the full capability/IA baseline             |
| Route/page contracts         | PHR has web `phrRouteContracts`.                                   | Contracts cover current flat routes, not full IA route tree                  |
| API contracts                | ActiveJ routes exist.                                              | API style differs from docs’ NestJS/modular API assumptions in screen matrix |
| Schema validation            | Frontend has Zod FHIR parsing.                                     | Shared `@phr/schemas` not clearly canonical across UI/backend                |
| Consent/privacy              | Backend consent enforcement exists.                                | UI journey incomplete; mobile offline policy not Kernel-governed             |
| Evidence/release readiness   | Lifecycle script enforces gates and rollback evidence.             | UI still has mock audit and action gaps                                      |
| Observability/audit          | PHR kernel emits lifecycle/audit/consent evidence.                 | Web audit page does not consume real evidence                                |
| Test orchestration           | Root package has many PHR/Kernel checks.                           | Critical E2E journey coverage is still incomplete                            |
| Product generation/evolution | YAPPC bridge exists.                                               | No PHR IA-to-code generation/validation proof                                |

## 6. PHR Production Readiness Findings

### P0 blockers

1. **Vision/IA-to-code mismatch is too large.** The IA documents expect a full nested patient/provider/caregiver/FCHV/admin product, while current web code implements a compact flat set.  

2. **Audit UI is mock-only.** `AuditPage` simulates audit events in `useEffect` and uses `setTimeout`; this cannot be accepted for a regulated access-history page. 

3. **Critical UI actions are not wired.** Consent update and appointment submit render buttons without action handlers.  

4. **Mobile authentication is local UI state, not real session context.** Mobile continues after setting `isAuthenticated` locally; dashboard loads before real auth/session gating. 

5. **Mobile offline PHI cache lacks visible policy enforcement.** Offline fallback loads cached dashboard data when API fails, but the reviewed API layer does not show encryption, expiry, consent-scope refresh, or emergency redaction checks. 

6. **Provider/caregiver/FCHV/admin IA flows are mostly missing from UI.** The documented IA includes those route groups, but current route contracts only have role-threshold flat routes.  

### P1 findings

1. Backend has real PHR service composition and should remain the canonical foundation. `PhrKernelModule` wires patient records, consent, documents, appointments, medication, labs, immunization, FHIR server, HIE, HL7 lab integration, clinical notes, imaging, referrals, billing, telemedicine, caregivers, and emergency access. 

2. Backend patient record access is stronger than UI because it checks patient self/admin or consent before read/update/search/history. 

3. Backend emergency routes are promising, with access, event read, patient log, pending review, overdue review, and review endpoints. 

4. Shared request context is too header-driven and role-simplified for production unless protected by upstream auth middleware/gateway. It trusts `X-Tenant-ID`, `X-Principal-ID`, and `X-Role` headers and treats only `admin` as privileged. 

5. PHR lifecycle readiness is present and checks gates such as consent, PII classification, audit evidence, FHIR contract validation, and tenant data sovereignty. 

## 7. Kernel Gap Analysis

| Kernel gap                                                                          | PHR use case exposing it                                                                                       | Required Kernel hardening                                                                                          |
| ----------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| Full IA/capability-to-route generation is missing                                   | PHR docs define far more routes than current route contracts                                                   | Kernel should provide product IA/capability contract registry that can generate/validate web/mobile route coverage |
| Product lifecycle exists, but feature completeness is file-presence oriented        | PHR workflow checker validates files/tests/evidence but marks status as partial until focused suite executes.  | Add runtime journey proof gates, not only existence/evidence refs                                                  |
| Auth/context enforcement is too product-local                                       | PHR route adapters use headers directly.                                                                       | Kernel security context adapter should validate signed principal/tenant/role and expose canonical `ActorContext`   |
| Consent/privacy policy is product-service based but not a platform policy primitive | Patient record routes call PHR consent service directly.                                                       | Kernel policy engine should support declarative consent-gated route/resource guards                                |
| Mobile offline PHI policy not Kernel-native                                         | Mobile offline fallback caches dashboard data.                                                                 | Kernel should expose offline PHI policy contract: encryption, TTL, revocation, audit, emergency redaction          |
| Release readiness UI not fully tied to route/action journey truth                   | Release readiness exists, but several UI flows are mock/unwired                                                | Kernel release gate must include “no visible unwired PHR actions” and “no mock regulated UI” checks                |

## 8. YAPPC Gap Analysis

| YAPPC gap                                                      | PHR need exposing it                                                            | Required YAPPC hardening                                                                                              |
| -------------------------------------------------------------- | ------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| Cannot yet prove IA-to-route/page coverage                     | PHR IA has many missing screens                                                 | YAPPC should ingest PHR IA docs and current route manifests, then output missing route/page/API/test tasks            |
| No PHR-specific generation proof                               | PHR needs patient/provider/caregiver/FCHV/admin route groups                    | Add PHR product-unit templates that generate Kernel-native route/page/API/test skeletons                              |
| No generated journey visualization tied to code                | PHR needs full E2E journey ordering                                             | YAPPC should render journey graph from IA → routes → APIs → tests                                                     |
| No strict generated-code validation against Kernel conventions | YAPPC README admits generation quality checks are still maturing.               | Generated artifacts must pass product-shell, design-system, route entitlement, i18n, a11y, and Kernel lifecycle gates |
| Bridge is too narrow for full PHR acceleration                 | Bridge exposes evidence/intents, but not full product IA/generation workflows.  | Add Kernel-backed PHR ProductUnitIntent provider and IA completeness provider                                         |

## 9. Architecture Ownership Matrix

| Capability                | Current owner                   | Correct owner                             | Action                                                              |
| ------------------------- | ------------------------------- | ----------------------------------------- | ------------------------------------------------------------------- |
| Healthcare workflows      | PHR                             | PHR                                       | Keep in PHR; complete missing IA flows                              |
| PHR service lifecycle     | PHR + Kernel                    | Kernel orchestrates, PHR implements       | Continue Kernel module pattern                                      |
| Route entitlements        | PHR web + product-shell         | Kernel/product-shell with PHR config      | Generate/validate from canonical IA                                 |
| Consent enforcement       | PHR backend                     | PHR policy + Kernel guard primitive       | Keep domain rules in PHR, move enforcement hooks into Kernel policy |
| Mobile offline PHI policy | PHR mobile                      | Kernel policy + PHR mobile implementation | Add Kernel offline PHI policy                                       |
| Audit evidence            | PHR Kernel module + PHR UI mock | Kernel evidence + PHR real API/UI         | Replace mock AuditPage                                              |
| Product generation        | YAPPC                           | YAPPC using Kernel                        | Add PHR generation/coverage accelerator                             |
| Release readiness         | Kernel scripts + PHR UI         | Kernel lifecycle + PHR evidence           | Gate against journey/action completeness                            |

## 10. Test and Verification Review

Current evidence shows a **good start** but not complete journey proof.

The root package contains PHR lifecycle, FHIR validation, PHR workflow, Kernel lifecycle, route entitlement, YAPPC intent handoff, artifact intelligence, product-shell, design-system, and release gates.  PHR workflow coverage checks include `patient-profile`, `health-summary`, `clinical-resources`, `documents`, `consent-management`, `data-sharing-authorization`, `audit-access-history`, `fhir-r4-validation`, `tenant-data-sovereignty`, and `i18n-a11y`. 

However, current UI tests are still concentrated around dashboard metrics, loading/error states, route permission denial, shell navigation, release fixture rendering, and record detail fallback.  Missing tests include real consent update/revoke UI, appointment create conflict handling, provider search, caregiver dependents, FCHV registration/vitals, audit API integration, mobile auth, mobile offline encryption/expiry/revocation, emergency break-glass full workflow, and full IA route coverage.

## 11. Scorecard

| Area                       | Score / 5 | Rationale                                                        |
| -------------------------- | --------: | ---------------------------------------------------------------- |
| PHR feature completeness   |       2.0 | Backend has many services; UI misses most IA                     |
| PHR functional correctness |       2.4 | Backend routes have useful checks; UI actions incomplete         |
| PHR healthcare correctness |       2.7 | FHIR/consent/emergency foundations exist; full workflows missing |
| PHR consent/privacy        |       2.8 | Backend consent checks exist; UI/mobile incomplete               |
| PHR emergency/audit        |       2.2 | Backend promising; audit UI mock                                 |
| PHR web UI/UX              |       1.8 | Flat partial UI, many missing routes                             |
| PHR mobile UI/UX           |       1.6 | Early shell, local auth, basic offline                           |
| PHR backend/API            |       3.2 | Good ActiveJ route/service composition                           |
| PHR security               |       2.2 | Header-driven context needs stronger Kernel auth                 |
| PHR observability          |       2.8 | Kernel evidence events exist; UI not consuming them              |
| PHR tests                  |       2.0 | Many checks exist; E2E journey gaps remain                       |
| PHR release readiness      |       2.8 | Lifecycle gates exist; full product proof incomplete             |
| PHR Kernel-native usage    |       2.7 | Backend is Kernel-native; frontend/mobile/product IA are not     |

| Kernel category                     | Score / 5 |
| ----------------------------------- | --------: |
| Lifecycle support                   |       3.4 |
| Plugin architecture                 |       3.2 |
| Product-shell/design-system support |       3.0 |
| Route/entitlement support           |       2.8 |
| Evidence/release readiness          |       3.4 |
| CI/test orchestration               |       3.2 |
| Deployment/runtime lifecycle        |       2.8 |
| Observability/governance            |       3.0 |
| Fitness for PHR now                 |       2.7 |

| YAPPC category                | Score / 5 |
| ----------------------------- | --------: |
| Ability to model PHR          |       2.2 |
| Kernel-native generation      |       2.0 |
| Completeness validation       |       2.0 |
| Preview/journey visualization |       2.0 |
| Gap surfacing                 |       2.3 |
| Generated-code quality        |       2.0 |
| Kernel integration            |       2.4 |

## 12. Production Blockers

1. Replace mock audit UI with real audit/evidence API.
2. Wire consent update/revoke UI to backend consent APIs.
3. Wire appointment request/create/reschedule UI to backend appointment APIs.
4. Add real web/mobile authentication and session bootstrap.
5. Add Kernel-validated tenant/principal/role context instead of trusting raw headers at route adapters.
6. Add mobile offline PHI encryption, TTL, revocation, consent refresh, and audit.
7. Implement or hide all IA-promised but missing routes.
8. Add provider, caregiver, FCHV, and admin route groups or explicitly defer/feature-flag them.
9. Add end-to-end emergency break-glass UI/API/audit/review test coverage.
10. Add IA-to-code completeness gate so docs cannot overclaim current implementation.

## 13. Granular File-by-File Implementation Plan

| Priority | Area                  | File/path                                                                               | Change                                                                        | Acceptance criteria                                               | Tests                                  |
| -------- | --------------------- | --------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------- | ----------------------------------------------------------------- | -------------------------------------- |
| P0       | Web audit             | `products/phr/apps/web/src/pages/AuditPage.tsx`                                         | Remove simulated `mockEvents`; call real audit/evidence API                   | No mock data; filters query real endpoint                         | Add audit page API integration tests   |
| P0       | Backend audit API     | `products/phr/src/main/java/com/ghatana/phr/api/routes/`                                | Add explicit audit route if not already exposed as queryable UI API           | UI can list access/consent/emergency audit events by patient/role | API tests for patient/admin visibility |
| P0       | Consent UI            | `products/phr/apps/web/src/pages/ConsentPage.tsx`                                       | Add create/update/revoke modal and API calls                                  | Update/revoke changes backend state and refreshes list            | UI + API tests                         |
| P0       | Appointment UI        | `products/phr/apps/web/src/pages/AppointmentsPage.tsx`                                  | Wire submit to appointment create/request API                                 | Submit validates input, creates request, shows success/error      | Form validation and mutation tests     |
| P0       | Auth/session          | `products/phr/apps/web/src/pages/LoginPage.tsx`, `products/phr/apps/mobile/src/App.tsx` | Replace local/demo auth with real session bootstrap                           | Login resolves actor/tenant/role; no dashboard before auth        | Web/mobile auth tests                  |
| P0       | Route security        | `PhrRouteSupport.java`                                                                  | Replace raw header trust with Kernel security context adapter                 | Invalid/unsigned context rejected fail-closed                     | Route security tests                   |
| P0       | Mobile offline        | `products/phr/apps/mobile/src/services/offlineStore.*`, `phrMobileApi.ts`               | Add encryption/TTL/consent revocation semantics                               | Cached PHI expires, is scoped, and audited                        | Offline security tests                 |
| P1       | IA route coverage     | `products/phr/apps/web/src/phrRouteContracts.ts`                                        | Add canonical route groups or feature-flag hidden placeholders for IA items   | All IA routes are COMPLETE, hidden, or explicitly deferred        | Route manifest coverage test           |
| P1       | Provider UI           | `products/phr/apps/web/src/pages/provider/*`                                            | Add provider dashboard, patient search, patient summary                       | Provider journey works with consent                               | E2E provider access tests              |
| P1       | Caregiver UI          | `products/phr/apps/web/src/pages/caregiver/*`                                           | Add dependents list/detail/access flow                                        | Caregiver can only access delegated dependent records             | E2E caregiver scope tests              |
| P1       | FCHV UI               | `products/phr/apps/web/src/pages/fchv/*`                                                | Add dashboard/register/patients/vitals or flag IA as deferred                 | No visible broken IA; FCHV flow test if enabled                   | FCHV E2E tests                         |
| P1       | Documents/OCR         | `products/phr/apps/web/src/pages/documents/*`                                           | Add documents list/upload/OCR review or feature flag                          | Upload/OCR is real or hidden                                      | Upload/OCR tests                       |
| P1       | Emergency UI          | `EmergencyAccessPage.tsx`                                                               | Wire request/notify/review buttons to backend                                 | Access creates pending review, notifies, logs audit               | Break-glass E2E                        |
| P1       | Kernel IA gate        | `scripts/check-phr-ia-coverage.mjs` new                                                 | Compare docs IA → route manifest → API → tests                                | Fails when IA item is visible but missing                         | Script unit test                       |
| P1       | YAPPC PHR accelerator | `products/yappc/...`                                                                    | Add PHR ProductUnitIntent provider from IA docs                               | YAPPC reports missing PHR routes/API/tests                        | Bridge/provider tests                  |
| P2       | i18n/mobile           | `products/phr/apps/mobile/src/**`                                                       | Replace hardcoded text with shared i18n                                       | Single i18n path for web/mobile                                   | i18n conformance                       |
| P2       | Design system         | PHR web pages                                                                           | Replace raw Tailwind buttons/table in AuditPage with design-system components | Consistent UI                                                     | component/a11y tests                   |
| P2       | Release readiness     | `ReleaseCockpitPage.tsx`, scripts                                                       | Add “visible unwired action” and “mock regulated UI” section                  | Release blocked if audit mock/buttons exist                       | release gate test                      |

## 14. Recommended Execution Order

1. **Stabilize current PHR critical journeys.** Fix auth/session, dashboard data, records, consents, appointments, audit, emergency.
2. **Close P0 unsafe UI gaps.** Remove mock audit, wire action buttons, fail-closed route context.
3. **Create IA coverage gate.** Add script to compare PHR docs/IA to routes/pages/APIs/tests.
4. **Feature-flag missing IA.** Hide documents/OCR/voice/provider/FCHV/caregiver/admin surfaces until implemented.
5. **Make backend/frontend contracts converge.** Align web/mobile APIs with backend route groups and screen matrix.
6. **Harden Kernel for PHR.** Add canonical actor context, consent policy guard primitive, offline PHI policy, IA coverage gate.
7. **Harden YAPPC only where useful.** Add PHR IA ingestion, ProductUnitIntent, journey visualization, route/API/test gap detection.
8. **Add full E2E tests.** Cover patient, provider, caregiver, emergency, mobile offline, release readiness.
9. **Re-audit and score again.**

## 15. Open Questions / Required Decisions

1. Is the documented `/app/patient`, `/app/provider`, `/app/caregiver`, `/app/fchv`, `/app/admin` IA still canonical, or should current flat routes become the new canonical IA? - Use maintainable way
2. Should PHR keep ActiveJ APIs while docs still describe NestJS-style modules, or should docs be corrected? - Correct docs
3. Is FCHV in MVP or Phase 2? - Yes
4. Are insurance/payments/claims in active scope or hidden Phase 2? - keep whatever we have and hide them behind and mark them for Phase 2
5. Should YAPPC generate PHR route/page/API/test skeletons now, or only produce gap reports until Kernel lifecycle contracts stabilize? - wait for stabalization
6. What is the canonical mobile offline PHI policy: encryption, TTL, emergency mode, revocation sync, audit frequency? - All and explore for industry standard and compilance requrements if there are

I did not run local CI/tests in this environment; this is a static, code-grounded audit using the repository contents available at the target commit.

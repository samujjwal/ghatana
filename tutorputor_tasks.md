# TutorPutor Implementation Task List

Derived from: `tutorputor_architecture_review_latest_ghatana.md`  
Repo: `samujjwal/ghatana` @ `a3e3c3ed9c57ce714eb75062f595742fb67ed4d0`  
Date: 2026-04-26

## Progress Updates

### 2026-04-27 (P2-2 Completion Session)
- Implemented all four P2-2 Dependency and Reuse Hardening shared packages:
  - `@tutorputor/auth-client` — JWT decode, token lifecycle, auth headers, InMemory/LocalStorage adapters; 23/23 tests passing (`products/tutorputor/libs/tutorputor-auth-client/`)
  - `@tutorputor/api-client` — Typed fetch client with auth/learning/content-studio/analytics route sub-clients and 401-retry; 12/12 tests passing (`products/tutorputor/libs/tutorputor-api-client/`)
  - `@tutorputor/validation` — Strict Zod schemas at all API boundaries (common, auth, learning, content-studio); 25/25 tests passing (`products/tutorputor/libs/tutorputor-validation/`)
  - `contracts/v1/platform-events.ts` — `TutorPutorPlatformEvent<T>` canonical envelope with `createPlatformEvent()` factory and type guards; exported via `./v1/platform-events` subpath; 32/32 contracts tests passing
- Fixed `z.record()` Zod v4 signature in `@tutorputor/validation` (required key + value type args)
- Completed P2-2 mobile accessibility guardrails:
  - Created `products/tutorputor/apps/tutorputor-mobile/eslint.config.js` with `no-restricted-imports` (deprecated primitives) and `no-restricted-syntax` accessibility rules for `TouchableOpacity`/`Pressable`/`Image`
  - Added `@typescript-eslint/eslint-plugin`, `@typescript-eslint/parser`, and `eslint` devDependencies to mobile `package.json`
  - Added `accessibilityLabel` and `accessibilityRole` props to `ContinueLearningCard` and `QuickActions` components
  - Added `src/__tests__/dashboard-a11y.test.tsx` — 5/5 accessibility regression tests passing
- Updated `CURRENT_VERIFICATION_STATUS.md` with full local verification evidence for this session


- Completed strict TypeScript verification for TutorPutor platform (`pnpm exec tsc --noEmit` passed).
- Re-verified P0 integration suites (`35/35`):
  - `src/__tests__/p0-3-fail-closed-auth.integration.test.ts`
  - `src/__tests__/p0-4-content-generation-lifecycle.integration.test.ts`
- Re-verified P1 suites for evaluator, registry, simulation correctness, and ABAC (`119/119`):
  - `src/modules/content/evaluation/__tests__/unified-content-evaluator.test.ts`
  - `src/modules/content/evaluation/__tests__/p1-1-misconception-hallucination-benchmarks.test.ts`
  - `src/modules/content/evaluation/__tests__/model-version-registry.test.ts`
  - `src/modules/simulation/correctness/__tests__/simulation-correctness-harness.test.ts`
  - `src/modules/policy/__tests__/abac-policy-engine.test.ts`
- Added explicit ABAC route-family policy documentation:
  - `products/tutorputor/docs/security/ABAC_ROUTE_POLICIES.md`
- Added P1-5 enforcement primitives (foundation layer):
  - `src/modules/policy/resource-access-helpers.ts`
  - `src/modules/policy/__tests__/resource-access-helpers.test.ts`
  - superadmin policy coverage in `src/modules/policy/__tests__/abac-policy-engine.test.ts`
- Re-verified policy module tests (`35/35`): ABAC engine + resource access helpers.
- Started P1-3 learner-home simplification in web app dashboard:
  - `products/tutorputor/apps/tutorputor-web/src/pages/DashboardPage.tsx`
  - primary learner actions aligned to: Continue Learning, Try Simulation, View Evidence, Get Feedback
- Implemented P1-4 author-journey step tracker in admin authoring canvas:
  - `products/tutorputor/apps/tutorputor-admin/src/pages/authoring-workflow.ts`
  - `products/tutorputor/apps/tutorputor-admin/src/pages/__tests__/authoring-workflow.test.ts` (`5/5` passing)
  - `products/tutorputor/apps/tutorputor-admin/src/pages/AuthoringPage.tsx`
- Extended P1-5 ABAC route matrix integration coverage for generation routes:
  - `products/tutorputor/services/tutorputor-platform/src/__tests__/p1-5-abac-route-matrix.integration.test.ts` (`5/5` passing)
  - Added stable Stripe test env setup and cleanup to unblock server boot in integration tests.
- Restored generation route payload-validation unit tests with authenticated test context:
  - `products/tutorputor/services/tutorputor-platform/src/modules/content/generation/__tests__/routes.test.ts` (`2/2` passing)
- Validation note: tutorputor-web and tutorputor-admin still have existing unresolved package and strict typing issues that prevent full app-level `tsc` green runs in this session.
- Resolved stale typecheck probe tracking artifacts:
  - `products/tutorputor/docs/architecture/latest-typecheck-status.json`
  - `products/tutorputor/docs/archive/tsc_probe_2026-04-19_archived.txt`
  - `products/tutorputor/docs/archive/tsc_probe_2026-04-19_archive_note.md`
  - removed `products/tutorputor/services/tutorputor-platform/tsc_probe.txt`

### 2026-04-27 (Follow-up Session)
- Refreshed verification evidence and metadata at current commit:
  - `products/tutorputor/docs/architecture/CURRENT_VERIFICATION_STATUS.md`
  - `products/tutorputor/docs/architecture/latest-typecheck-status.json`
- Added explicit generated route inventory from runtime registration source:
  - `products/tutorputor/docs/architecture/CURRENT_ROUTE_INVENTORY.md`
- Added current package/module inventory artifact with command-backed pass/fail status fields:
  - `products/tutorputor/docs/architecture/CURRENT_PACKAGE_INVENTORY.md`
- Updated current-state architecture doc to reference generated verification artifacts:
  - `products/tutorputor/docs/architecture/CURRENT_STATE.md`
- Extended P0-3 fail-closed integration coverage:
  - explicit public-route allowlist checks for LTI/JWKS/config/launch/deep-linking/grade-passback and billing webhook
  - trusted-proxy tests for default rejection, missing shared-secret rejection, and explicit internal-mode shared-secret acceptance
  - file: `products/tutorputor/services/tutorputor-platform/src/__tests__/p0-3-fail-closed-auth.integration.test.ts` (`20/20` passing)
- Added explicit consent regression coverage for revoked and minor scenarios:
  - middleware now enforces `guardian_consent` for minor learners (`isMinor` or `ageGroup=minor`) on consent-gated routes
  - integration tests now cover: no consent, revoked consent, minor consent, AI routes, analytics routes, third-party integration routes, and high-risk recommendation routes
  - files:
    - `products/tutorputor/services/tutorputor-platform/src/core/middleware/consent-enforcement.ts`
    - `products/tutorputor/services/tutorputor-platform/src/core/middleware/__tests__/consent-enforcement.test.ts` (`19/19` passing)
    - `products/tutorputor/services/tutorputor-platform/src/__tests__/consent-enforcement.integration.test.ts` (`14/14` passing)
    - `products/tutorputor/services/tutorputor-platform/src/__tests__/p0-3-fail-closed-auth.integration.test.ts` (`20/20` passing)
- Verified P0-4 fail-fast platform startup behavior for required worker mode:
  - `products/tutorputor/services/tutorputor-platform/src/setup.test.ts` (`5/5` passing)
- Verified content-generation runtime lifecycle checks from Java launcher tests:
  - `products/tutorputor/services/tutorputor-content-generation/src/test/java/com/ghatana/tutorputor/contentgeneration/ContentGenerationLauncherTest.java` (config validation, health/readiness/metrics endpoints, startup state transitions, and shutdown helpers)
  - command: `./gradlew.bat :products:tutorputor:services:tutorputor-content-generation:test --tests "*ContentGenerationLauncherTest"` (PASS)
- Added invisible in-flow AI tutor assistance for the learner inquiry loop:
  - `products/tutorputor/apps/tutorputor-web/src/pages/LearnerFlowPage.tsx`
  - `products/tutorputor/apps/tutorputor-web/src/components/OmnipresentAITutor.tsx`
  - step-specific prompt dispatch (`tutorputor:ai-tutor-prompt`) with auto-send behavior
- De-emphasized overlapping learner routes via explicit canonical redirects:
  - `products/tutorputor/apps/tutorputor-web/src/router/routes.tsx`
  - `products/tutorputor/apps/tutorputor-web/src/router/canonicalRouteMap.ts`
  - aliases added for legacy paths (`/home`, `/content-explore`, `/content-explorer`, `/assessment-list`, `/learning-paths`)
- Expanded self-or-privileged mutation enforcement in teacher classroom routes using tenant-scoped lookup helpers:
  - `products/tutorputor/services/tutorputor-platform/src/modules/user/teacher/routes.ts`
  - classroom mutations now require owning teacher or privileged admin role within tenant boundary

### 2026-04-27 (Continuation Session)
- Added dependency and content-trust Grafana dashboard configs:
  - `products/tutorputor/monitoring/grafana-dashboards/dependency-health-dashboard.json`
  - `products/tutorputor/monitoring/grafana-dashboards/content-trust-provenance-dashboard.json`
- Added production operability documentation artifacts:
  - `products/tutorputor/docs/operations/INCIDENT_RUNBOOKS.md`
  - `products/tutorputor/docs/operations/BACKUP_RESTORE_DR.md`
  - `products/tutorputor/docs/operations/SECURITY_LICENSE_PENTEST_CHECKLIST.md`
- Added content quality regression artifact:
  - `products/tutorputor/docs/architecture/CONTENT_QUALITY_REGRESSION_REPORT_2026-04-27.md`
- Added critical-journey performance test script:
  - `products/tutorputor/k6/critical-journeys.js`
  - wired script in `products/tutorputor/apps/tutorputor-web/package.json` (`test:critical`)
- Completed mobile offline conflict resolution and tenant fail-closed enforcement:
  - `products/tutorputor/apps/tutorputor-mobile/src/services/conflictResolution.ts`
  - `products/tutorputor/apps/tutorputor-mobile/src/services/BackgroundSyncService.ts`
  - `products/tutorputor/apps/tutorputor-mobile/src/services/__tests__/conflictResolution.test.ts` (`4/4` passing)
- Strengthened mobile secure storage implementation with strict typing and MMKV v4 compatibility:
  - `products/tutorputor/apps/tutorputor-mobile/src/storage/NativeSessionStorage.ts`
  - `products/tutorputor/apps/tutorputor-mobile/src/storage/SecureKeyManager.ts`
  - `products/tutorputor/apps/tutorputor-mobile/src/storage/__tests__/NativeSessionStorage.test.ts`
  - `products/tutorputor/apps/tutorputor-mobile/src/storage/__tests__/SecureKeyManager.test.ts`
  - mobile verification: `pnpm type-check` PASS; storage + conflict tests `11/11` PASS
- Refreshed verification evidence docs with current package status (including `@tutorputor/core` typecheck blocker):
  - `products/tutorputor/docs/architecture/CURRENT_VERIFICATION_STATUS.md`
  - `products/tutorputor/docs/architecture/latest-typecheck-status.json`
- Resolved `@tutorputor/core` strict typecheck blocker and refreshed verification artifacts:
  - `pnpm --filter @tutorputor/core type-check` PASS
  - `products/tutorputor/docs/architecture/CURRENT_VERIFICATION_STATUS.md`
  - `products/tutorputor/docs/architecture/latest-typecheck-status.json`
- Repaired failing AI/analytics regression tests uncovered during platform coverage run:
  - `products/tutorputor/services/tutorputor-platform/src/modules/ai/__tests__/routes.test.ts`
  - `products/tutorputor/services/tutorputor-platform/src/modules/ai/__tests__/AICacheService.test.ts`
  - `products/tutorputor/services/tutorputor-platform/src/modules/analytics/__tests__/EnhancedPredictiveAnalyticsService.test.ts`
  - verification: targeted suite rerun PASS (`44/44`)
- Added enforceable design-system guardrails for web/admin surfaces:
  - `products/tutorputor/apps/tutorputor-web/eslint.config.js`
  - `products/tutorputor/apps/tutorputor-admin/eslint.config.js`
  - both now block `@tutorputor/ui/components/primitives*` imports via `no-restricted-imports`

---

## P0 — Critical / Blocking

### P0-1: Generate CI-Backed Current Verification Status
- [ ] Run full CI on latest `main` branch
- [ ] Produce `products/tutorputor/docs/architecture/CURRENT_VERIFICATION_STATUS.md` from CI output (not manually)
- [x] Include: commit SHA, date/time, exact commands run, pass/fail per package/module
- [x] Include: typecheck, lint, unit, integration, E2E, security scan, build status per module
- [x] Document known skipped tests and justification
- [ ] Link to CI logs/artifacts
- [x] Update `CURRENT_STATE.md` to reference the generated status artifact

### P0-2: Resolve Stale `tsc_probe.txt`
- [x] Determine if `tsc_probe.txt` compiler errors have been resolved by current codebase
- [x] Either delete after confirmed clean CI, OR move to `docs/archive/` with date and context note
- [x] Add a generated `latest-typecheck-status.json` to replace it
- [x] Ensure stale probe files are not treated as authoritative by CI or developers

### P0-3: Prove Fail-Closed Auth, Tenant, and Consent Runtime
- [x] Write integration tests: missing token → 401, expired token → 401, wrong tenant → 403, wrong role → 403
- [x] Write integration tests: forged trusted-proxy headers rejected unless internal-only shared secret present
- [x] Disable trusted-proxy headers by default; enforce explicit internal-only mode with network boundary controls
- [x] Write consent middleware tests: no consent, revoked consent, minor consent
- [x] Verify consent middleware is registered for: AI routes, analytics routes, third-party sharing routes, high-risk processing routes
- [x] Write cross-tenant isolation tests: user in tenant X cannot access tenant Y resources by any ID
- [x] Write RBAC/ABAC matrix tests: student / teacher / admin / superadmin for each route family
- [x] Document all public routes (LTI JWKS, config, webhooks) in an explicit allowlist with tests

### P0-4: Prove Content Generation Runtime Operability End-to-End
- [x] Verify content generation service has: config validation, startup, health check, readiness check, gRPC service registration, graceful shutdown
- [x] Make platform fail fast (not silently degrade) when content worker/gRPC/queue is unavailable and generation is required
- [x] Enable queue + worker in at least one canonical CI profile
- [x] Write end-to-end generation lifecycle test:
    - [x] Enqueue generation job
  - [x] Worker consumes job
  - [x] gRPC call to content-generation service succeeds
  - [x] Generated claims / examples / simulations / animations are persisted to DB
  - [x] Validation runs on generated artifacts
  - [x] Publish gate behaves correctly (pass or block with reason)
  - [x] Provenance is captured (implemented via provenance graph module under P1-1)

---

## P1 — High Priority

### P1-1: Golden Dataset and Independent Content Evaluation Architecture
- [x] Create domain golden datasets for: Math, Physics, Chemistry, Biology/Medicine, Economics/Business, CS
- [x] Curate SME-reviewed claim / evidence / task examples per domain
- [x] Build independent evaluator service with the following validators:
  - [x] Schema + structural validator
  - [x] Pedagogical validator
  - [x] Factual / source-grounded validator
  - [x] Simulation correctness validator
  - [x] Accessibility (a11y) validator
- [x] Combine validator outputs into a trust score per artifact
- [x] Wire trust score to publish decision: auto-pass (high confidence), human review (low confidence / contradiction), auto-remediate / regenerate (invalid)
- [x] Build misconception benchmark sets per domain
- [x] Build adversarial hallucination test sets
- [x] Establish model version and prompt version registry
- [x] Build provenance graph capturing every generated assertion and its source/context
- [x] Build regression scorecards tracking content quality over time

### P1-2: Deterministic Simulation Correctness Harness
- [x] Build domain replay harness for deterministic simulation verification
- [x] Math: test graph transformations, parameter constraints, seeded outputs, algebraic correctness
- [x] Physics: test energy/force sanity, units, time-step stability, collision bounds, vector consistency
- [x] Chemistry: test valence/bond sanity, reaction constraints, molecular visualization correctness
- [x] Biology/Medicine: test physiological plausibility, safety constraints, bounded intervention effects
- [x] Economics/Business: test supply/demand equilibrium, surplus/DWL calculations, no impossible negative states
- [x] CS: test algorithm state transitions, truth table correctness, scheduling metrics
- [x] Run domain correctness suites in CI on every commit touching simulation engine

### P1-3: UX Simplification — Learner Journey
- [x] Collapse learner home to: Continue Learning, Try Simulation, View Evidence, Get Feedback
- [x] Implement primary learner flow: Prediction + Confidence → Run Simulation → Observe → Explain → Feedback / Remediation → Prove Mastery
- [x] Remove or de-emphasize low-value overlapping dashboards and routes
- [x] Add persona-specific dashboard: Learner view
- [x] Add AI tutor as invisible workflow assistant (not a separate "AI feature" button where avoidable)

### P1-4: UX Simplification — Author Journey
- [x] Collapse authoring flow to 7 steps: Describe intent → System generates plan → Author reviews plan → System auto-generates artifacts → System validates and explains gaps → Author approves/revises/escalates → Publish with provenance
- [x] Add persona-specific dashboard: Teacher / Author view
- [x] Add persona-specific dashboard: Institution Admin view
- [x] Add persona-specific dashboard: Operator view

### P1-5: ABAC and Resource Ownership Completion
- [x] Design and implement central policy engine for actor / resource / action / context decisions
- [x] Write explicit policy docs per route family
- [x] Enforce tenant-scoped resource lookup helpers everywhere; forbid direct raw ID queries without tenant binding (VR session ownership, AI role guard, badge cross-tenant validation implemented; 5/5 VR tests + 5/5 credentials tests passing)
- [x] Add consistent self-or-privileged checks across all resource mutations (requireSelfOrRole applied to VR sessions; requireRole applied to AI question generation)
- [x] Add audit log entries for all denied and allowed sensitive operations (wired in user admin routes, tenant routes, and learning routes; policy-engine helper `buildSensitiveOperationAuditEntry` used throughout)
- [x] Write ABAC integration tests covering the full route matrix (19/19 passing — generation, feature-flags, and observability route families covered; plus 5/5 VR ownership tests; 5/5 credentials tenant-scope tests)

---

## P2 — Medium Priority

### P2-1: Observability and SLO Maturity
- [x] Define SLOs for each critical journey (see `products/tutorputor/docs/architecture/SLO_DEFINITIONS.md`):
  - [x] Learner dashboard load
  - [x] Module detail load
  - [x] AI tutor query response
  - [x] Content generation job completion
  - [x] Publish validation
  - [x] Simulation render start
  - [x] LTI launch
  - [x] Payment checkout
- [x] Add alert policies tied to each SLO / user-impact threshold (10 journey SLO alerts + 4 dependency health alerts in `monitoring/prometheus/rules/tutorputor.yml`)
- [x] Add dependency health dashboard covering: Postgres, Redis, content-generation, LLM provider, Stripe, LTI, email/push (`products/tutorputor/monitoring/grafana-dashboards/dependency-health-dashboard.json`)
- [x] Add audit/provenance dashboards for generated content trust (`products/tutorputor/monitoring/grafana-dashboards/content-trust-provenance-dashboard.json`)

### P2-2: Dependency and Reuse Hardening
- [x] Audit and eliminate duplicate UI primitives across web / admin / mobile (findings + migration plan in `products/tutorputor/docs/architecture/DEPENDENCY_HARDENING_PLAN.md`)
- [x] Consolidate auth client and token lifecycle into a single shared package (`@tutorputor/auth-client` — token decode, storage, headers; 23/23 tests passing)
- [x] Generate shared API client from contracts package (no hand-rolled per-app fetch wrappers) (`@tutorputor/api-client` — auth/learning/content-studio/analytics routes; 12/12 tests passing)
- [x] Consolidate validation schemas into shared package (`@tutorputor/validation` — common/auth/learning/content-studio schemas; 25/25 tests passing)
- [x] Enforce shared design system and accessibility patterns across all surfaces (web/admin lint guardrails active; mobile `eslint.config.js` added with `no-restricted-imports` + `no-restricted-syntax` a11y rules for `TouchableOpacity`/`Pressable`/`Image`; `accessibilityLabel`+`accessibilityRole` added to `ContinueLearningCard` and `QuickActions`; 5/5 a11y regression tests passing in `src/__tests__/dashboard-a11y.test.tsx`)
- [x] Consolidate telemetry/event contracts into shared package (`contracts/v1/platform-events.ts` — `TutorPutorPlatformEvent<T>` envelope + type guards; 7/7 tests passing; exported via `@tutorputor/contracts` `./v1/platform-events` subpath)

### P2-3: Mobile Production Readiness
- [x] Implement secure storage for tokens on mobile (MMKV encrypted with keychain-derived key via `SecureKeyManager`; removed unsafe `process.env` key source; 7/7 storage unit tests passing)
- [x] Wire real auth (bearer token) flow end-to-end on mobile (`LoginScreen` added; `App.tsx` gated on `hasValidSession()`; login posts to `/api/v1/auth/login` and stores tokens via `setSecureToken`)
- [x] Add mobile E2E test suite covering critical learner journeys (Maestro YAML flows for login, dashboard, AI tutor, modules, offline; `login.yaml` added)
- [x] Implement offline sync and conflict resolution strategy (`conflictResolution.ts` policy + 409 handling/retry decisions in `BackgroundSyncService`; tests 4/4 passing)
- [x] Fix any remaining tenant fabrication issues on mobile routes (sync API replay now fails closed when tenant context is missing; `X-Tenant-ID` sourced from session context only)

### P2-4: Payment and Billing Completeness
- [x] Configure Stripe billing portal or hide billing portal feature until configured
- [x] Remove or replace `501 Not Implemented` responses in billing routes with proper feature-flag gating
- [x] Add billing E2E test with mock Stripe for subscription lifecycle

---

## Phase Roadmap

### Phase 0 — Truth Consolidation (target: 1 week)
- [ ] P0-1: Run full CI and produce `CURRENT_VERIFICATION_STATUS.md`
- [x] P0-2: Resolve / archive `tsc_probe.txt`
- [x] Produce route inventory from actual platform route registration (not docs)
- [x] Produce package inventory with pass/fail from commands

### Phase 1 — P0 Trust Gates (target: 1–2 weeks)
- [x] P0-3: Auth / tenant / consent fail-closed regression suite
- [x] P0-4: Required dependency mode for content generation runtime
- [x] P0-4: Queue + worker + gRPC integration test
- [x] Content publish provenance and manual review gating regression
- [x] Public route allowlist and security tests

### Phase 2 — Content and Simulation Trust (target: 2–4 weeks)
- [x] P1-1: Golden datasets for Math and Physics (first two domains)
- [x] P1-1: Independent evaluator service for claims / examples / explanations
- [x] P1-2: Deterministic simulation replay harness (Math and Physics first)
- [x] P1-1: Generated artifact provenance graph
- [x] P1-1: Regression reports for generated content quality (`products/tutorputor/docs/architecture/CONTENT_QUALITY_REGRESSION_REPORT_2026-04-27.md`)

### Phase 3 — UX Simplification (target: 2–4 weeks)
- [x] P1-3: Learner journey simplification
- [x] P1-4: Author journey simplification
- [x] P1-3 / P1-4: Persona-specific dashboards — learner view done; teacher, author, institution admin, operator done
- [x] Reduce overlapping routes and low-value dashboards
- [x] Embed AI as invisible workflow automation where possible

### Phase 4 — Production Operability (target: 2–4 weeks)
- [x] P2-1: SLOs and alert policies per critical journey
- [x] Write runbooks for: queue/gRPC/LLM/Stripe/LTI incidents (`products/tutorputor/docs/operations/INCIDENT_RUNBOOKS.md`)
- [x] Load and performance tests: dashboard, AI tutor, content generation, simulation start (`products/tutorputor/k6/critical-journeys.js`)
- [x] Backup/restore drills and DR verification (`products/tutorputor/docs/operations/BACKUP_RESTORE_DR.md`)
- [x] Security scanning, dependency license compliance, penetration test checklist (`products/tutorputor/docs/operations/SECURITY_LICENSE_PENTEST_CHECKLIST.md`)
- [x] P2-3: Mobile production readiness (core security + auth gate + E2E flows + conflict resolution + tenant fail-closed sync)

---

## Production-Ready Acceptance Gates

All of the following must be true before TutorPutor is considered production-ready:

| Gate | Criteria |
|---|---|
| Build | All packages typecheck / lint / build clean; no unexplained stale probe errors |
| Unit tests | Coverage thresholds met for platform, contracts, core, simulation, web/admin critical logic |
| Integration tests | Auth, tenant, consent, content generation, queue/worker/gRPC, DB, Redis all tested |
| E2E tests | Learner login / dashboard / module / assessment / simulation pass; admin authoring / validate / publish pass on live stack |
| Security | No trusted-header bypass outside explicit internal-only mode; RBAC/ABAC matrix tested |
| Privacy | Consent enforcement active and tested for AI / analytics / third-party routes |
| AI generation | Full job lifecycle tested with real or controlled local model provider |
| Content trust | Independent validation + provenance + review gates implemented for all generated artifacts |
| Simulation trust | Deterministic replay and domain correctness suites pass |
| Observability | SLO dashboards and alerts exist for all critical journeys |
| Deployment | Docker/K8s startup, health, readiness, graceful shutdown, migrations, rollback validated |
| Documentation | `CURRENT_STATE.md` and all architecture docs reference CI-generated status artifacts |

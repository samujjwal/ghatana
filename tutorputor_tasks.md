# TutorPutor Implementation Task List

Derived from: `tutorputor_architecture_review_latest_ghatana.md`  
Repo: `samujjwal/ghatana` @ `a3e3c3ed9c57ce714eb75062f595742fb67ed4d0`  
Date: 2026-04-26

## Progress Updates

### 2026-04-26 (Current Session)
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

---

## P0 — Critical / Blocking

### P0-1: Generate CI-Backed Current Verification Status
- [ ] Run full CI on latest `main` branch
- [ ] Produce `products/tutorputor/docs/architecture/CURRENT_VERIFICATION_STATUS.md` from CI output (not manually)
- [x] Include: commit SHA, date/time, exact commands run, pass/fail per package/module
- [x] Include: typecheck, lint, unit, integration, E2E, security scan, build status per module
- [x] Document known skipped tests and justification
- [ ] Link to CI logs/artifacts
- [ ] Update `CURRENT_STATE.md` to reference the generated status artifact

### P0-2: Resolve Stale `tsc_probe.txt`
- [x] Determine if `tsc_probe.txt` compiler errors have been resolved by current codebase
- [x] Either delete after confirmed clean CI, OR move to `docs/archive/` with date and context note
- [x] Add a generated `latest-typecheck-status.json` to replace it
- [x] Ensure stale probe files are not treated as authoritative by CI or developers

### P0-3: Prove Fail-Closed Auth, Tenant, and Consent Runtime
- [x] Write integration tests: missing token → 401, expired token → 401, wrong tenant → 403, wrong role → 403
- [ ] Write integration tests: forged trusted-proxy headers rejected unless internal-only shared secret present
- [ ] Disable trusted-proxy headers by default; enforce explicit internal-only mode with network boundary controls
- [ ] Write consent middleware tests: no consent, revoked consent, minor consent
- [ ] Verify consent middleware is registered for: AI routes, analytics routes, third-party sharing routes, high-risk processing routes
- [x] Write cross-tenant isolation tests: user in tenant X cannot access tenant Y resources by any ID
- [ ] Write RBAC/ABAC matrix tests: student / teacher / admin / superadmin for each route family
- [ ] Document all public routes (LTI JWKS, config, webhooks) in an explicit allowlist with tests

### P0-4: Prove Content Generation Runtime Operability End-to-End
- [ ] Verify content generation service has: config validation, startup, health check, readiness check, gRPC service registration, graceful shutdown
- [ ] Make platform fail fast (not silently degrade) when content worker/gRPC/queue is unavailable and generation is required
- [ ] Enable queue + worker in at least one canonical CI profile
- [ ] Write end-to-end generation lifecycle test:
  - [ ] Enqueue generation job
  - [ ] Worker consumes job
  - [ ] gRPC call to content-generation service succeeds
  - [ ] Generated claims / examples / simulations / animations are persisted to DB
  - [ ] Validation runs on generated artifacts
  - [ ] Provenance is captured
  - [ ] Publish gate behaves correctly (pass or block with reason)

---

## P1 — High Priority

### P1-1: Golden Dataset and Independent Content Evaluation Architecture
- [x] Create domain golden datasets for: Math, Physics, Chemistry, Biology/Medicine, Economics/Business, CS
- [x] Curate SME-reviewed claim / evidence / task examples per domain
- [ ] Build independent evaluator service with the following validators:
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
- [ ] Build provenance graph capturing every generated assertion and its source/context
- [x] Build regression scorecards tracking content quality over time

### P1-2: Deterministic Simulation Correctness Harness
- [x] Build domain replay harness for deterministic simulation verification
- [x] Math: test graph transformations, parameter constraints, seeded outputs, algebraic correctness
- [x] Physics: test energy/force sanity, units, time-step stability, collision bounds, vector consistency
- [x] Chemistry: test valence/bond sanity, reaction constraints, molecular visualization correctness
- [x] Biology/Medicine: test physiological plausibility, safety constraints, bounded intervention effects
- [x] Economics/Business: test supply/demand equilibrium, surplus/DWL calculations, no impossible negative states
- [x] CS: test algorithm state transitions, truth table correctness, scheduling metrics
- [ ] Run domain correctness suites in CI on every commit touching simulation engine

### P1-3: UX Simplification — Learner Journey
- [ ] Collapse learner home to: Continue Learning, Try Simulation, View Evidence, Get Feedback (in progress: dashboard action model updated)
- [ ] Implement primary learner flow: Prediction + Confidence → Run Simulation → Observe → Explain → Feedback / Remediation → Prove Mastery
- [ ] Remove or de-emphasize low-value overlapping dashboards and routes
- [ ] Add persona-specific dashboard: Learner view
- [ ] Add AI tutor as invisible workflow assistant (not a separate "AI feature" button where avoidable)

### P1-4: UX Simplification — Author Journey
- [ ] Collapse authoring flow to 7 steps: Describe intent → System generates plan → Author reviews plan → System auto-generates artifacts → System validates and explains gaps → Author approves/revises/escalates → Publish with provenance (in progress: 7-step tracker implemented in authoring canvas)
- [ ] Add persona-specific dashboard: Teacher / Author view
- [ ] Add persona-specific dashboard: Institution Admin view
- [ ] Add persona-specific dashboard: Operator view

### P1-5: ABAC and Resource Ownership Completion
- [x] Design and implement central policy engine for actor / resource / action / context decisions
- [x] Write explicit policy docs per route family
- [ ] Enforce tenant-scoped resource lookup helpers everywhere; forbid direct raw ID queries without tenant binding (foundation helper added)
- [ ] Add consistent self-or-privileged checks across all resource mutations (foundation helper added)
- [ ] Add audit log entries for all denied and allowed sensitive operations (policy-engine and helper payloads implemented; generation-route wiring added; remaining route families pending)
- [ ] Write ABAC integration tests covering the full route matrix (in progress: generation route-family matrix integrated and passing)

---

## P2 — Medium Priority

### P2-1: Observability and SLO Maturity
- [ ] Define SLOs for each critical journey:
  - [ ] Learner dashboard load
  - [ ] Module detail load
  - [ ] AI tutor query response
  - [ ] Content generation job completion
  - [ ] Publish validation
  - [ ] Simulation render start
  - [ ] LTI launch
  - [ ] Payment checkout
- [ ] Add alert policies tied to each SLO / user-impact threshold
- [ ] Add dependency health dashboard covering: Postgres, Redis, content-generation, LLM provider, Stripe, LTI, email/push
- [ ] Add audit/provenance dashboards for generated content trust

### P2-2: Dependency and Reuse Hardening
- [ ] Audit and eliminate duplicate UI primitives across web / admin / mobile
- [ ] Consolidate auth client and token lifecycle into a single shared package
- [ ] Generate shared API client from contracts package (no hand-rolled per-app fetch wrappers)
- [ ] Consolidate validation schemas into shared package
- [ ] Enforce shared design system and accessibility patterns across all surfaces
- [ ] Consolidate telemetry/event contracts into shared package

### P2-3: Mobile Production Readiness
- [ ] Implement secure storage for tokens on mobile (no plain AsyncStorage for credentials)
- [ ] Implement offline sync and conflict resolution strategy
- [ ] Wire real auth (bearer token) flow end-to-end on mobile
- [ ] Add mobile E2E test suite covering critical learner journeys
- [ ] Fix any remaining tenant fabrication issues on mobile routes

### P2-4: Payment and Billing Completeness
- [ ] Configure Stripe billing portal or hide billing portal feature until configured
- [ ] Remove or replace `501 Not Implemented` responses in billing routes with proper feature-flag gating
- [ ] Add billing E2E test with mock Stripe for subscription lifecycle

---

## Phase Roadmap

### Phase 0 — Truth Consolidation (target: 1 week)
- [ ] P0-1: Run full CI and produce `CURRENT_VERIFICATION_STATUS.md`
- [x] P0-2: Resolve / archive `tsc_probe.txt`
- [ ] Produce route inventory from actual platform route registration (not docs)
- [ ] Produce package inventory with pass/fail from commands

### Phase 1 — P0 Trust Gates (target: 1–2 weeks)
- [ ] P0-3: Auth / tenant / consent fail-closed regression suite
- [ ] P0-4: Required dependency mode for content generation runtime
- [ ] P0-4: Queue + worker + gRPC integration test
- [ ] Content publish provenance and manual review gating regression
- [ ] Public route allowlist and security tests

### Phase 2 — Content and Simulation Trust (target: 2–4 weeks)
- [x] P1-1: Golden datasets for Math and Physics (first two domains)
- [x] P1-1: Independent evaluator service for claims / examples / explanations
- [x] P1-2: Deterministic simulation replay harness (Math and Physics first)
- [ ] P1-1: Generated artifact provenance graph
- [ ] P1-1: Regression reports for generated content quality

### Phase 3 — UX Simplification (target: 2–4 weeks)
- [ ] P1-3: Learner journey simplification
- [ ] P1-4: Author journey simplification
- [ ] P1-3 / P1-4: Persona-specific dashboards (learner, teacher, author, institution admin, operator)
- [ ] Reduce overlapping routes and low-value dashboards
- [ ] Embed AI as invisible workflow automation where possible

### Phase 4 — Production Operability (target: 2–4 weeks)
- [ ] P2-1: SLOs and alert policies per critical journey
- [ ] Write runbooks for: queue/gRPC/LLM/Stripe/LTI incidents
- [ ] Load and performance tests: dashboard, AI tutor, content generation, simulation start
- [ ] Backup/restore drills and DR verification
- [ ] Security scanning, dependency license compliance, penetration test checklist
- [ ] P2-3: Mobile production readiness

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

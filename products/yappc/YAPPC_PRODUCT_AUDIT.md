# YAPPC Product Audit

Date: 2026-04-16

## A. Executive Assessment

- Overall maturity: late prototype / early alpha, not production-grade.
- Overall end-to-end correctness: inconsistent. A few backend slices are real, but multiple primary user journeys are broken, stale, or only simulated.
- UI/UX simplicity score: 4/10.
- Cognitive load: high. The product exposes multiple generations of routes, concepts, and actions at once.
- Implicit AI/ML effectiveness: 2/10. Most "AI" is heuristic, visible, or decorative rather than quietly reducing user work.
- Release readiness: not ready for production.
- Evidence base: active React router, major routes/components, Node API routes, Prisma schema/migrations, Java service wiring/docs, and targeted frontend/API tests.

### Top 10 blockers

1. Route graph is broken: active router is `/p/:projectId`, but core UI navigates to `/app/p/:id`.
2. Onboarding redirects to `/app`, which is not a registered route.
3. "New Project" creates a client-only fake record and never calls the API.
4. Workspace/project fetches silently fall back to demo data, masking real backend failure.
5. Lifecycle artifacts and phase gates are in-memory only, not persisted or collaborative.
6. Dashboard links to `/projects/new` and `/workflows/*` without verified active routes/handlers.
7. Auth UI calls `/api/auth/register` and `/api/auth/forgot-password`; current API only exposes login/refresh/logout/me.
8. Several user-visible actions have no verified backend: export, AI assist, preview, nested lifecycle drilldowns.
9. Contract authority is unclear: React app, Node BFF, Java services, OpenAPI, and GraphQL are out of sync.
10. Tests are stale or weak: route smoke test fails, auth E2E is skipped, Playwright golden path targets obsolete routes.

## B. Reconstructed Product Model

- Intended personas: founder/product lead, staff engineer/designer, workspace admin/reviewer, and governance approver.
- Intended core workflows: register/login, create/select workspace, create product project, use canvas to define/build, move through lifecycle phases, preview/deploy, collaborate across workspaces, receive AI guidance with minimal manual work.
- Intended user outcomes: ship software products with low ceremony, guided lifecycle governance, implicit AI support, and reduced coordination overhead.
- Where automation should exist: default workspace/project creation, starter canvas generation, phase readiness checks, duplicate detection, next-step prioritization, artifact summarization, anomaly detection, safe defaults for deploy/review.
- Where human review is justified: permission changes, policy exceptions, production deployment, low-confidence AI suggestions, lifecycle phase overrides.
- Assumptions: docs are not authoritative. The current product was treated as `frontend/web` + `frontend/apps/api` + Java services under `core/services-*`, and the intended model was inferred from code/tests/schema where docs conflicted.

## C. End-to-End Feature Audit Matrix

### Auth/session bootstrap

- Intended outcome: login/register/recover session and enter product.
- Current implementation status: partial.
- UI state: login/register/forgot-password screens exist.
- API state: current API exposes login/refresh/logout/me only.
- Backend/domain state: service layer contains register logic, but route wiring is missing in the active Node auth surface.
- DB state: user/workspace creation exists in service code.
- Test evidence: mocked auth tests pass; real DB auth E2E is skipped by default.
- Gaps: register and forgot-password are presented as available but not proven through the active route layer.
- Severity: P0
- Exact files/components/services involved:
  - `/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/services/auth/AuthService.ts`
  - `/Users/samujjwal/Development/ghatana/products/yappc/frontend/apps/api/src/routes/auth.ts`
  - `/Users/samujjwal/Development/ghatana/products/yappc/frontend/apps/api/src/services/auth/auth.service.ts`

### Onboarding and home navigation

- Intended outcome: enter product and land in a useful first state.
- Current implementation status: broken.
- UI state: onboarding redirects to `/app`; dashboard also links to `/projects/new` and `/workflows/*`.
- API state: not the main issue.
- Backend/domain state: not the main issue.
- DB state: not the main issue.
- Test evidence: no trustworthy live coverage.
- Gaps: route targets do not match the active router.
- Severity: P0
- Exact files/components/services involved:
  - `/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/routes/onboarding.tsx`
  - `/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/routes/dashboard.tsx`
  - `/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/routes.ts`

### Workspace loading

- Intended outcome: show real workspaces/projects with honest failure states.
- Current implementation status: partial and deceptive.
- UI state: renders, but fetch failures fall back to demo data.
- API state: real BFF endpoints exist.
- Backend/domain state: likely functional when healthy.
- DB state: likely functional for the deprecated Node path and/or Java path, but the UI can hide outages.
- Test evidence: weak.
- Gaps: production failure is concealed behind fake workspace and project data.
- Severity: P0
- Exact files/components/services involved:
  - `/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/hooks/useWorkspaceData.ts`
  - `/Users/samujjwal/Development/ghatana/products/yappc/frontend/apps/api/src/routes/workspaces.ts`

### Project listing/navigation

- Intended outcome: browse projects and open one.
- Current implementation status: broken primary path.
- UI state: lists projects, but selection navigates to `/app/p/:id`.
- API state: listing endpoints exist.
- Backend/domain state: project retrieval exists.
- DB state: project rows can be queried.
- Test evidence: stale Playwright spec uses old routes.
- Gaps: the main click path misses the active router, which is `/p/:projectId`.
- Severity: P0
- Exact files/components/services involved:
  - `/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/routes/app/projects.tsx`
  - `/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/routes.ts`
  - `/Users/samujjwal/Development/ghatana/products/yappc/frontend/e2e/golden-path.spec.ts`

### Project creation

- Intended outcome: create a persisted project with minimal input.
- Current implementation status: not end to end.
- UI state: polished modal.
- API state: real deprecated POST `/api/projects` exists.
- Backend/domain state: can create a persisted project.
- DB state: Prisma create path exists.
- Test evidence: no browser-to-DB proof.
- Gaps: the primary UI path fabricates a local object and never hits the API.
- Severity: P0
- Exact files/components/services involved:
  - `/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/components/workspace/CreateProjectDialog.tsx`
  - `/Users/samujjwal/Development/ghatana/products/yappc/frontend/apps/api/src/routes/projects.ts`

### Canvas editing/persistence

- Intended outcome: durable collaborative product canvas.
- Current implementation status: partial.
- UI state: canvas route exists and appears substantial.
- API state: real canvas persistence endpoints exist.
- Backend/domain state: Prisma-backed persistence is present.
- DB state: real canvas/version persistence exists.
- Test evidence: insufficient full-path coverage.
- Gaps: multiple overlapping frontend persistence systems make the authoritative path unclear.
- Severity: P1
- Exact files/components/services involved:
  - `/Users/samujjwal/Development/ghatana/products/yappc/frontend/apps/api/src/routes/canvas.ts`
  - `/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/services/canvas/CanvasPersistence.ts`
  - `/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/hooks/useCanvasPersistence.ts`

### Lifecycle/governance

- Intended outcome: phase gating, artifacts, approvals, governed transitions.
- Current implementation status: partial-to-misleading.
- UI state: looks real and detailed.
- API state: phase preview exists and is tested.
- Backend/domain state: Java lifecycle modules appear substantive.
- DB state: not authoritative from the active web UX.
- Test evidence: lifecycle preview tests pass.
- Gaps: artifacts and phase gates use in-memory repositories; nested lifecycle routes are not registered in the active router.
- Severity: P0
- Exact files/components/services involved:
  - `/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/routes/app/project/lifecycle.tsx`
  - `/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/services/canvas/lifecycle/LifecycleArtifactService.ts`
  - `/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/services/canvas/lifecycle/PhaseGateService.ts`
  - `/Users/samujjwal/Development/ghatana/products/yappc/frontend/apps/api/src/routes/lifecycle.ts`

### Deploy/preview/export/AI assist

- Intended outcome: preview and deploy with trustworthy readiness and assistance.
- Current implementation status: partial/broken.
- UI state: deploy/preview/export/assist controls are visible.
- API state: only some readiness preview behavior is verified.
- Backend/domain state: fragmented and not coherently wired to the active web shell.
- DB state: unclear and unproven for these paths.
- Test evidence: insufficient.
- Gaps: preview/export/assist endpoints are not verified in the active API surface; deploy page mixes real preview with in-memory governance state.
- Severity: P0
- Exact files/components/services involved:
  - `/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/routes/app/project/_shell.tsx`
  - `/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/routes/app/project/preview.tsx`
  - `/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/routes/app/project/deploy.tsx`

### Dashboard/tasks/workflows

- Intended outcome: low-friction command center.
- Current implementation status: misleading.
- UI state: dashboard fetches tasks and links to workflows.
- API state: `/api/tasks` and workflow routes are not proven in the active surface.
- Backend/domain state: may exist elsewhere, but not cleanly verified from the current web product.
- DB state: unproven from the active path.
- Test evidence: none that establish real operation.
- Gaps: adds complexity without a verified end-to-end path.
- Severity: P1
- Exact files/components/services involved:
  - `/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/routes/dashboard.tsx`

### Release/test/ops

- Intended outcome: reproducible startup, trustworthy tests, production confidence.
- Current implementation status: stale.
- UI state: not applicable.
- API state: test harness drift exists.
- Backend/domain state: startup/docs disagree.
- DB state: migration authority is unclear.
- Test evidence: route smoke test fails; auth E2E is skipped; integration test imports nonexistent `createApp`.
- Gaps: docs/scripts/tests do not reflect the running product.
- Severity: P0
- Exact files/components/services involved:
  - `/Users/samujjwal/Development/ghatana/products/yappc/Makefile`
  - `/Users/samujjwal/Development/ghatana/products/yappc/DEPLOYMENT_GUIDE.md`
  - `/Users/samujjwal/Development/ghatana/products/yappc/frontend/apps/api/src/__tests__/routes.integration.test.ts`
  - `/Users/samujjwal/Development/ghatana/products/yappc/frontend/apps/api/src/index.ts`

## D. UI/UX Review

### Navigation/information architecture

- Current navigation mixes active routes and legacy `/app/*` assumptions.
- Entry points are inconsistent and in several cases dead.
- The information architecture exposes too many concepts before the product proves a reliable core flow.

### Workflow simplicity

- Some flows feel simple only because they avoid the backend entirely.
- The primary create/open/resume paths are not truthful enough to support user trust.

### Cognitive load

- Dashboard combines projects, workflows, tasks, workspaces, and lifecycle states before establishing one primary job.
- Multiple route generations and product metaphors increase orientation cost.

### Form design

- Project creation is short and approachable.
- The form is not trustworthy because it creates local-only state rather than a persisted project.

### Review/approval UX

- Governance is visually prominent in lifecycle/deploy paths.
- The active UX does not reliably provide durable, auditable review states for those controls.

### AI/ML-assisted UX

- AI affordances are mostly visible suggestion chrome and heuristic next actions.
- The system is not yet quietly removing work in credible, durable ways.

### Error/loading/empty states

- The biggest integrity issue is silent fallback to demo data instead of honest failure.
- Users can believe the system is working while the backend is unavailable.

### Consistency and clarity

- Routes, labels, docs, and tests describe different product generations.
- The product feels more complex than its intended value proposition.

## E. API Review

- Contract issues:
  - REST, GraphQL, OpenAPI, and Java service surfaces are not aligned.
  - The checked-in OpenAPI file describes a different product shape than the active web app uses.
- Validation gaps:
  - Some Node routes have basic schema validation, but the larger issue is missing endpoints the UI assumes exist.
- Naming/design inconsistencies:
  - `/api` and `/v1` are both active.
  - Node routes are marked deprecated while still serving the active frontend.
- Minimality/completeness issues:
  - Too many overlapping surfaces exist without a clearly canonical contract.
- Frontend/backend mismatch:
  - Register, forgot-password, tasks, workflows, preview/export/assist, and nested lifecycle routes do not line up cleanly.

## F. Backend and Domain Review

- Logic issues:
  - Real business logic exists in Java modules, but the product path users actually hit is a muddled Node BFF plus stale frontend.
- Orchestration issues:
  - The gateway serves local REST routes and also proxies catch-all requests to Java, obscuring ownership.
- Correctness gaps:
  - Project creation bypasses auth/permissions/audit entirely when done through the UI modal.
  - Lifecycle state is not durable in the active UX.
- Failure handling issues:
  - The frontend masks backend failure with demo data.
  - The proxy returns generic 503s without strong recovery guidance.
- Duplication/reuse concerns:
  - Canvas persistence, contracts, lifecycle logic, and auth authority are duplicated across layers.

## G. Database Review

- Schema issues:
  - The Prisma schema is broad relative to the portion proven in the active product.
- Query/transaction issues:
  - Simple CRUD is present, but cross-entity lifecycle/governance flows are not proven transactionally through the current UX.
- Integrity risks:
  - UI truth and DB truth diverge because some key actions never persist.
- Migration/data lifecycle issues:
  - Prisma schema breadth and checked-in migration history appear mismatched.
  - Deployment docs reference Flyway, further muddying migration authority.
- Auditability/governance gaps:
  - Java approval infrastructure appears to exist, but the active frontend path does not reliably surface or persist it.

## H. AI/ML Implicit Automation Review

### Where AI/ML is used correctly

- Server-generated `aiNextActions`.
- Health/readiness concepts.
- The idea of phase previewing before action.

### Where AI/ML should be added

- Server-side inference for project setup.
- Duplicate detection.
- Artifact summarization.
- Readiness anomaly detection.
- Cross-workspace recommendation.

### Where AI/ML is too visible/noisy

- The create-project "AI suggestion" banner is mostly heuristic ornamentation.

### Where user effort should be reduced

- Workspace/project bootstrap.
- Choosing project type.
- Manual phase interpretation.
- Repeated route navigation.
- Explicit workflow selection.

### Where user approval is rightly required

- Deploy to production.
- Permission changes.
- Lifecycle gate overrides.
- Policy exceptions.
- Low-confidence automation.

## I. Testing and Evidence Gaps

- Missing tests by level:
  - True browser-to-API-to-DB tests for register, onboarding, create project, project navigation, lifecycle transitions, and deploy approval.
- Incorrect or weak tests:
  - Route test currently fails.
  - Login/auth tests are mostly mocked.
  - Integration test imports nonexistent `createApp`.
  - Playwright golden path targets obsolete `/app/*` flows and uses localStorage flags heavily.
- Unverified flows:
  - Export, preview, AI assist, forgot-password, tasks, workflows, nested lifecycle drilldowns, and honest outage behavior.
- Highest-risk uncovered paths:
  - First-run user journey.
  - Deploy governance.
  - Refresh/re-auth.
  - Persistence after reload/collaboration.

## J. Prioritized Remediation Plan

### P0: must fix immediately

- Problem: Route graph is split across current and legacy paths.
- Why it matters: primary navigation is broken.
- Root cause: legacy `/app/*` routes survived a router refactor.
- Exact fix: choose one canonical route taxonomy and update all navigation, redirects, tests, and docs.
- Affected layers: frontend router, UI, tests, docs.
- Suggested validation/tests: Playwright smoke for every top-level route and project deep link.

- Problem: Demo-data fallbacks in production paths.
- Why it matters: the UI lies about system health.
- Root cause: developer convenience leaked into product code.
- Exact fix: replace with explicit degraded/error states and optional dev-only fixtures behind env guards.
- Affected layers: frontend data fetching, error UX.
- Suggested validation/tests: outage tests returning 503/HTML/non-JSON.

- Problem: Project creation is client-only.
- Why it matters: the primary creation path is non-persistent and bypasses governance.
- Root cause: unfinished modal implementation.
- Exact fix: call POST `/api/projects` or the canonical Java endpoint, then refresh authoritative query state.
- Affected layers: frontend, API, DB, auth, tests.
- Suggested validation/tests: browser-to-DB creation test plus reload persistence.

- Problem: Lifecycle gates/artifacts are in-memory.
- Why it matters: governance UI is not trustworthy.
- Root cause: placeholder repositories remained in the active product path.
- Exact fix: persist via API/DB or remove from primary UX until real.
- Affected layers: frontend, API, backend, DB.
- Suggested validation/tests: multi-session persistence and approval audit tests.

- Problem: Dead actions remain visible.
- Why it matters: export/assist/preview/workflows create false completeness.
- Root cause: missing feature flags and incomplete integration.
- Exact fix: hide until backed by verified routes.
- Affected layers: frontend, product.
- Suggested validation/tests: broken-link/action smoke suite.

### P1: required for production confidence

- Problem: No canonical contract surface.
- Why it matters: contract drift is systemic.
- Root cause: parallel Node/Java/OpenAPI/GraphQL evolution.
- Exact fix: declare canonical API, generate schemas/types from it, deprecate others aggressively.
- Affected layers: API, backend, docs.
- Suggested validation/tests: contract tests and generated client types.

- Problem: Tests do not prove the product behavior.
- Why it matters: current evidence is not trustworthy.
- Root cause: stale mocks and legacy routes.
- Exact fix: keep unit tests lean and add a small set of authoritative E2E flows on seeded DB.
- Affected layers: frontend, API, DB, QA.
- Suggested validation/tests: CI-required golden paths.

- Problem: Canvas persistence is duplicated.
- Why it matters: duplication invites state mismatch.
- Root cause: several historical persistence experiments remain.
- Exact fix: one service, one cache strategy, one authoritative backend path.
- Affected layers: frontend, API, DB.
- Suggested validation/tests: save/reload/version tests.

- Problem: Migration authority is unclear.
- Why it matters: release safety depends on it.
- Root cause: Prisma and Flyway stories conflict.
- Exact fix: pick one migration system per datastore and update deployment docs/scripts.
- Affected layers: DB, ops, docs.
- Suggested validation/tests: clean environment bootstrap.

### P2: quality and UX hardening

- Problem: Dashboard tries to do too much.
- Why it matters: cognitive load is too high.
- Root cause: too many aspirational modules on the home screen.
- Exact fix: center on "resume project / create project / review blockers."
- Affected layers: UX, frontend, product.
- Suggested validation/tests: task-completion usability pass.

- Problem: AI is visible instead of implicit.
- Why it matters: visible gimmicks do not reduce effort.
- Root cause: local heuristic UI shortcuts.
- Exact fix: infer defaults in backend and show them only as editable results.
- Affected layers: frontend, backend, AI.
- Suggested validation/tests: measure reduced fields/clicks.

### P3: strategic improvements

- Problem: Advanced modules are ahead of the core spine.
- Why it matters: workflows/GraphQL/policy modules add complexity before the product core is trustworthy.
- Root cause: parallel feature expansion.
- Exact fix: progressive disclosure after stable project/canvas/lifecycle path.
- Affected layers: product, architecture.
- Suggested validation/tests: phased release checklist by capability tier.

## K. Simplification Blueprint

- Screens/routes to merge/remove:
  - remove legacy `/app/*`
  - merge onboarding into first successful auth/workspace bootstrap
  - remove separate workflows routes from the primary nav until real
- Fields to remove/auto-infer:
  - infer workspace
  - suggest project type server-side
  - keep description optional
  - derive next actions automatically
  - hide health score during creation
- Steps to automate:
  - auto-create a starter workspace and starter project after registration
  - auto-open last real project
  - auto-generate starter canvas and readiness checklist
- Decisions to collapse into defaults:
  - current workspace
  - primary canvas entry route
  - default deploy environment
  - default lifecycle landing
- Review points to keep:
  - permission changes
  - lifecycle gate overrides
  - production deploy
  - policy exceptions
- AI/ML interventions to make implicit:
  - naming/type inference
  - duplicate detection
  - summary generation
  - artifact completeness checks
  - anomaly surfacing
  - next-best-action ranking
- Advanced options to hide behind progressive disclosure:
  - templates
  - project type overrides
  - manual artifact edits
  - policy tuning
  - cross-workspace inclusion controls

## L. Final Verdict

- What already works end to end:
  - there is real backend substance for auth/login, project/workspace CRUD, canvas persistence, and lifecycle logic in parts of the stack
  - the lifecycle phase preview route is one of the few areas with direct test evidence
- What only partially works:
  - dashboard
  - project navigation
  - canvas persistence authority
  - lifecycle/deploy UX
  - the Node-to-Java contract boundary
- What is misleading or incomplete:
  - onboarding
  - register/forgot-password
  - new project creation
  - workflow/task surfaces
  - export/preview/AI assist
  - lifecycle artifacts/gates
  - "production ready" documentation
- What prevents the product from being truly simple:
  - too many concepts in the primary UX
  - stale route generations
  - visible-but-thin AI affordances
  - hidden fallback behavior
- What prevents it from being fully end to end:
  - broken route links
  - client-side fake writes
  - in-memory lifecycle state
  - contract drift
  - non-authoritative tests
- What must change to make YAPPC production-grade, simple, and implicitly AI-native:
  - establish one truthful product spine first: auth -> workspace -> create/open project -> canvas -> lifecycle review -> deploy
  - remove fake/demo behavior from production paths
  - wire every primary action to persistent backend state
  - align contracts/docs/tests around one source of truth
  - only then layer back advanced AI/governance features as quiet helpers rather than visible promises

## Evidence References

- `/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/routes.ts`
- `/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/routes/app/projects.tsx`
- `/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/routes/app/project/_shell.tsx`
- `/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/routes/onboarding.tsx`
- `/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/routes/dashboard.tsx`
- `/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/components/workspace/CreateProjectDialog.tsx`
- `/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/hooks/useWorkspaceData.ts`
- `/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/services/canvas/lifecycle/LifecycleArtifactService.ts`
- `/Users/samujjwal/Development/ghatana/products/yappc/frontend/web/src/services/canvas/lifecycle/PhaseGateService.ts`
- `/Users/samujjwal/Development/ghatana/products/yappc/frontend/apps/api/src/routes/auth.ts`
- `/Users/samujjwal/Development/ghatana/products/yappc/frontend/apps/api/src/routes/projects.ts`
- `/Users/samujjwal/Development/ghatana/products/yappc/frontend/apps/api/src/routes/canvas.ts`
- `/Users/samujjwal/Development/ghatana/products/yappc/frontend/apps/api/src/routes/lifecycle.ts`
- `/Users/samujjwal/Development/ghatana/products/yappc/frontend/apps/api/src/index.ts`
- `/Users/samujjwal/Development/ghatana/products/yappc/frontend/apps/api/src/__tests__/routes.integration.test.ts`
- `/Users/samujjwal/Development/ghatana/products/yappc/frontend/apps/api/src/__tests__/auth-flow.api-e2e.test.ts`
- `/Users/samujjwal/Development/ghatana/products/yappc/frontend/apps/api/src/__tests__/lifecycle-phase-preview-routes.test.ts`
- `/Users/samujjwal/Development/ghatana/products/yappc/frontend/e2e/golden-path.spec.ts`
- `/Users/samujjwal/Development/ghatana/products/yappc/Makefile`
- `/Users/samujjwal/Development/ghatana/products/yappc/DEPLOYMENT_GUIDE.md`
- `/Users/samujjwal/Development/ghatana/products/yappc/api/yappc-api.openapi.yaml`

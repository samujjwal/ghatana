# TutorPutor Remediation Implementation Plan

## Purpose

This plan translates the audit findings into an executable remediation roadmap for TutorPutor.

The goals are to:

1. Fix broken integration paths between apps and backend.
2. replace mock and placeholder behavior with real end-to-end functionality.
3. make auth, tenant isolation, and persistence trustworthy.
4. make autonomous content generation for examples, animations, and simulations actually usable.
5. restore confidence through real tests, stricter quality gates, and accurate docs.

This plan assumes the current state is a partially integrated platform with strong foundations but significant contract drift, stubbed UX, and incomplete automation.

## Definition of Done

TutorPutor is considered remediated for this effort when all of the following are true:

- Student and admin apps call a single documented API contract with no silent mock fallbacks on production paths.
- Auth and authorization are based on validated identity claims, not ad hoc headers.
- Content generation requests produce persisted experiences, linked examples, animations, simulations, and validation results.
- Simulation authoring works end-to-end with valid domain types and correct route wiring.
- Placeholder pages and disabled routes are either implemented or removed from product and docs.
- Typecheck, lint, unit, integration, and e2e failures fail CI.
- Docs reflect the real product state and one source of truth is established for architecture and scope.

## Guiding Principles

- Fix contract drift before building more UI.
- Remove silent fallback behavior from production codepaths.
- Prefer one canonical API shape over many adapters.
- Wire existing strong subsystems into product flows before inventing new ones.
- Gate publishing on persisted validation, not UI assumptions.
- Treat docs as product code and version them with implementation changes.

## Workstream Overview

| Workstream | Priority | Outcome |
| --- | --- | --- |
| WS0. Stabilize truth sources | P0 | One accurate architecture and product status baseline |
| WS1. API contract unification | P0 | Frontend and backend agree on routes, payloads, and auth |
| WS2. Auth and tenant hardening | P0 | Real identity and role enforcement |
| WS3. Content generation end-to-end | P0 | Generation produces persisted experiences and assets |
| WS4. Simulation authoring correctness | P0 | NL authoring and studio flows work reliably |
| WS5. UI and UX completion | P1 | Replace placeholders and demo surfaces with product flows |
| WS6. Persistence activation | P1 | Generated artifacts and links are stored and queryable |
| WS7. Testing and CI hardening | P0 | Failing quality checks block merges |
| WS8. Documentation reconciliation | P1 | Docs match shipped behavior and near-term roadmap |

## Recommended Delivery Phases

### Phase 0: Freeze, Measure, and Align

Duration: 2-3 days

Objectives:

- Stop adding new product-facing features until contracts are reconciled.
- Record current route inventory, feature inventory, and doc mismatches.
- Decide the canonical API prefix strategy.

Deliverables:

- one route inventory document generated from current platform registration.
- one feature inventory showing implemented, stubbed, disabled, and planned surfaces.
- one approved contract direction for `"/api"` vs `"/api/v1"` usage.

Primary files to touch:

- `products/tutorputor/docs/architecture/TUTORPUTOR_MODULE_INVENTORY.md`
- `products/tutorputor/docs/architecture/TUTORPUTOR_FLOW_MAP.md`
- `products/tutorputor/services/tutorputor-platform/src/setup.ts`
- `products/tutorputor/apps/tutorputor-web/src/api/tutorputorClient.ts`

Acceptance criteria:

- Engineering agrees on one canonical route prefix strategy.
- Every frontend route or page is labeled as implemented, stubbed, disabled, or deferred.

### Phase 1: Rebuild the Integration Spine

Duration: 1-2 weeks

Objectives:

- Unify route prefixes and payload contracts.
- eliminate mock fallback behavior from core student and admin paths.
- restore search and broken feature registrations.

Deliverables:

- canonical API client layer for web and admin.
- platform modules registered for every actively used route.
- removal of fake fallback data from dashboard, modules, collaboration, search, and gamification paths.

Acceptance criteria:

- Student dashboard, modules, collaboration, search, and gamification all work against real backend routes.
- Search is registered and reachable if the UI exposes it.
- Any unavailable feature fails explicitly with structured error handling rather than returning fake content.

### Phase 2: Secure the Platform

Duration: 3-5 days

Objectives:

- Replace header-trusting auth shortcuts with validated identity context.
- standardize auth behavior across student app, admin app, and tests.

Deliverables:

- middleware that hydrates tenant, user, and roles from JWT or gateway identity.
- deprecation of raw `x-user-id` and `x-user-role` trust in business logic.
- standard login and session strategy or a single pure-SSO strategy reflected everywhere.

Acceptance criteria:

- No privileged route authorizes based only on manually supplied headers.
- Admin dev bypass is isolated behind explicit local-only tooling and cannot leak into normal app behavior.
- Tests use the same auth mechanism as the product.

### Phase 3: Make Automatic Content Creation Real

Duration: 2-3 weeks

Objectives:

- Connect generation UI to real services.
- persist generated examples, simulations, animations, and validation artifacts.
- make publishability depend on generation completeness and validation evidence.

Deliverables:

- real generation jobs and progress APIs.
- persisted artifact linkage for claims, examples, animations, and simulations.
- content review workflow with actual validation outputs.

Acceptance criteria:

- A user can submit a topic, track generation progress, review the generated package, refine it, and publish it.
- Generated animations and simulations are queryable by experience and claim.
- Validation uses artifact-level evidence instead of count-only heuristics.

### Phase 4: Finish the Product Surface and Quality Gates

Duration: 1-2 weeks

Objectives:

- Implement or remove placeholder pages and disabled routes.
- harden typecheck, tests, and CI.
- reconcile docs with the actual product state.

Acceptance criteria:

- No placeholder page is exposed in primary navigation.
- No `|| true` quality scripts remain in product packages.
- Docs describe implemented behavior and clearly label planned behavior.

## Detailed Workstreams

## WS0. Stabilize Truth Sources

### Problems to solve

- Product docs overstate maturity and module availability.
- Platform README understates or contradicts other docs.
- No single trustworthy source of current state exists.

### Tasks

1. Create a single architecture status document with these states: `implemented`, `partial`, `stubbed`, `disabled`, `planned`.
2. Mark every app page, backend module, and automation flow with one of those states.
3. Rewrite or archive misleading docs that present aspirational capabilities as implemented.
4. Make future roadmap docs explicitly separate `current state` from `target state`.

### Files to update

- `products/tutorputor/docs/architecture/specs/PRODUCT_SPEC.md`
- `products/tutorputor/services/tutorputor-platform/README.md`
- `products/tutorputor/docs/COMPREHENSIVE_CONTENT_GENERATION_PLAN.md`
- `products/tutorputor/docs/guides/content-studio/AUTOMATIC_CONTENT_CREATOR_APP.md`

### Output

- a concise source-of-truth document named something like `CURRENT_STATE.md`
- archived or revised speculative docs

## WS1. API Contract Unification

### Problems to solve

- Backend registers `/api/learning`, `/api/collaboration`, `/api/integration`, etc.
- frontend calls `/api/v1/...` across many paths.
- some frontend pages call endpoints that do not exist at all.
- search module exists but is not registered in startup.

### Tasks

1. Choose one canonical API namespace.
   - Recommended: expose all product routes under `/api/v1/...` and adapt backend registration to match.
   - Alternative: keep `/api/...` and rewrite every frontend client plus tests. This is lower backend churn but higher app churn.
2. Build a generated or hand-maintained route contract map for active product flows.
3. Register missing active modules in `setup.ts`, especially search if search UI remains active.
4. Remove unused or dead route references from UI components that point to nonexistent services.
5. Consolidate web and admin HTTP access behind one typed client per app.
6. Add integration tests that assert route availability and shape for all active endpoints.

### Files to update

- `products/tutorputor/services/tutorputor-platform/src/setup.ts`
- `products/tutorputor/services/tutorputor-platform/src/modules/search/index.ts`
- `products/tutorputor/apps/tutorputor-web/src/api/tutorputorClient.ts`
- `products/tutorputor/apps/tutorputor-admin/src/services/contentStudioApi.ts`
- `products/tutorputor/apps/tutorputor-web/src/components/content/LearningExperienceGenerator.tsx`
- `products/tutorputor/apps/tutorputor-web/src/components/auth/SsoLogin.tsx`

### Acceptance criteria

- No active frontend endpoint resolves to a nonexistent backend route.
- Search, marketplace, collaboration, learning, and auth paths all use one documented prefix strategy.
- Runtime failures return structured errors, never silent mocks.

## WS2. Auth and Tenant Hardening

### Problems to solve

- Business routes trust `x-user-id` and `x-user-role`.
- missing identity silently becomes `anonymous` or `default`.
- tests expect login flows that the current auth module does not provide.
- admin app has permissive dev bypass behavior.

### Tasks

1. Introduce a single request identity extractor based on verified JWT claims or trusted gateway context.
2. Replace all uses of `getUserId`, `getTenantId`, and `requireRole` that rely on raw headers.
3. Decide auth model:
   - either full SSO-only with no password login anywhere,
   - or standard login plus SSO, both implemented in platform and tests.
4. Move local developer bypass into an explicit local fixture mode that cannot be triggered accidentally by route behavior alone.
5. Update tests and test fixtures to use the real auth model.
6. Add authorization tests for admin-only, teacher-only, and tenant-isolated routes.

### Files to update

- `products/tutorputor/services/tutorputor-platform/src/utils/request-helpers.ts`
- `products/tutorputor/services/tutorputor-platform/src/modules/auth/index.ts`
- `products/tutorputor/services/tutorputor-platform/src/auth/index.ts`
- `products/tutorputor/apps/tutorputor-admin/src/hooks/useAuth.ts`
- `products/tutorputor/apps/tutorputor-admin/src/App.tsx`
- `products/tutorputor/tests/e2e/comprehensive.test.ts`
- `products/tutorputor/tests/integration/comprehensive.test.ts`

### Acceptance criteria

- Missing identity yields `401` or `403`, not default identities.
- Role checks are based on verified claims.
- Admin-only flows cannot be entered through generic dev failures.

## WS3. Content Generation End-to-End

### Problems to solve

- generation hooks are stubs.
- helper routes return canned or queued responses only.
- generated content is not consistently persisted or linked.
- validation is count-based instead of evidence-based.

### Tasks

1. Replace `useContentGeneration`, `useTemplates`, and `useContent` stubs with real API-backed hooks.
2. Implement job creation, polling, and final result retrieval for content generation.
3. Define a persisted content package model:
   - experience
   - claims
   - examples
   - simulations
   - animations
   - assessments
   - validation records
4. Decide the generation engine of record.
   - Recommended: use the existing richer Java content-generation service as the generation backend.
   - expose it through a stable platform adapter rather than duplicating logic in Fastify routes.
5. Replace `content-studio/ai/*` helper endpoints with real orchestration.
6. Upgrade validation from count heuristics to artifact-aware checks:
   - examples present and populated
   - simulation manifests valid
   - animation specs valid
   - assessment coverage aligned to claims
   - grade adaptation present
7. Implement refine operations that modify structured content, not only append notes.
8. Add review queue transitions backed by real validation status and publish rules.

### Files to update

- `products/tutorputor/apps/tutorputor-web/src/hooks/useContentGeneration.ts`
- `products/tutorputor/apps/tutorputor-web/src/hooks/useTemplates.ts`
- `products/tutorputor/apps/tutorputor-web/src/hooks/useContent.ts`
- `products/tutorputor/apps/tutorputor-web/src/pages/content-studio/GeneratePage.tsx`
- `products/tutorputor/services/tutorputor-platform/src/modules/content/studio/routes.ts`
- `products/tutorputor/services/tutorputor-platform/src/modules/content/studio/service.ts`
- `products/tutorputor/services/tutorputor-platform/src/workers/content/index.ts`
- `products/tutorputor/services/tutorputor-platform/src/workers/content/orchestrator.ts`
- `products/tutorputor/services/tutorputor-content-generation/src/main/java/com/ghatana/tutorputor/contentgeneration/PlatformContentGenerator.java`

### Acceptance criteria

- Generate page starts a real job and shows real progress.
- completion returns a persisted package id.
- authoring page can reopen previously generated content from storage.
- validation blocks publishing when required artifacts are missing or invalid.

## WS4. Simulation Authoring Correctness

### Problems to solve

- default NL authoring URL composes to `/api/api/sim-author/...`.
- simulation studio uses invalid domain literals.
- simulation generation quality cannot be trusted while the transport and contract are broken.

### Tasks

1. Fix the API base path logic in `useNLAuthoring`.
2. Replace invalid domain strings with contract-valid `SimulationDomain` values everywhere.
3. Add manifest validation on the frontend before save or generation submit.
4. Add backend validation for generated and refined manifests.
5. Ensure generated simulation manifests are persisted and linked to claims.
6. Add golden tests for one valid path per domain family at minimum.

### Files to update

- `products/tutorputor/apps/tutorputor-web/src/features/simulation-authoring/hooks/useNLAuthoring.ts`
- `products/tutorputor/apps/tutorputor-web/src/components/simulation/SimulationStudio.tsx`
- `products/tutorputor/contracts/v1/simulation/types.ts`
- `products/tutorputor/services/tutorputor-platform/src/modules/simulation/authoring-routes.ts`
- `products/tutorputor/libs/tutorputor-simulation/src/engine/author/service.ts`
- `products/tutorputor/libs/tutorputor-simulation/src/engine/nl/service.ts`

### Acceptance criteria

- Generate, refine, and suggest operations hit the correct backend routes by default.
- saved manifests always use valid contract domains.
- at least one full authoring scenario persists and replays successfully.

## WS5. UI and UX Completion

### Problems to solve

- primary product surfaces are placeholders, disabled, or local-demo driven.
- users can see product affordances that are not backed by real functionality.

### Tasks

1. Audit all currently routed pages and classify each as:
   - keep and implement
   - hide until implemented
   - remove
2. Remove placeholder pages from navigation if they are not scheduled in the current milestone.
3. Re-enable lazy routes only after pages are product-ready.
4. Convert admin authoring from local mock state to API-backed content loading and mutation.
5. Make empty states and unavailable-feature messaging explicit and honest.
6. For content generation UX, define a single happy path:
   - prompt
   - generation progress
   - review artifacts
   - refine
   - publish

### Files to update

- `products/tutorputor/apps/tutorputor-web/src/router/routes.tsx`
- `products/tutorputor/apps/tutorputor-web/src/routes/lazy.tsx`
- `products/tutorputor/apps/tutorputor-web/src/pages/ContentExplorer.tsx`
- `products/tutorputor/apps/tutorputor-web/src/pages/SimulationStudio.tsx`
- `products/tutorputor/apps/tutorputor-admin/src/pages/AuthoringPage.tsx`
- `products/tutorputor/apps/tutorputor-web/src/components/content-generation/AutomaticContentCreatorDashboard.tsx`

### Acceptance criteria

- No placeholder page remains in primary navigation.
- Every visible action either works end-to-end or is clearly disabled.
- Admin authoring library content is loaded from storage, not hardcoded state.

## WS6. Persistence Activation

### Problems to solve

- rich schema exists, but service layers do not fully use it.
- animation-content integration is non-persistent.
- generation metadata and artifact linkage are incomplete.

### Tasks

1. Map current Prisma models to actual service responsibilities.
2. Implement repositories or service methods for:
   - examples by claim
   - animations by claim and experience
   - simulations by claim and experience
   - generation jobs and outputs
3. Update publish flows to verify persistence completeness before publishing.
4. Add migration review to ensure schema supports required links and statuses cleanly.
5. Remove no-op persistence placeholders from animation integration and similar services.

### Files to update

- `products/tutorputor/libs/tutorputor-core/prisma/schema.prisma`
- `products/tutorputor/services/tutorputor-platform/src/modules/content/animation-integration.ts`
- `products/tutorputor/services/tutorputor-platform/src/modules/content/studio/service.ts`
- `products/tutorputor/libs/tutorputor-core/src/db/index.ts`

### Acceptance criteria

- Experience detail endpoint returns persisted linked artifacts.
- Querying by experience or claim returns real examples, simulations, and animations.
- Publish fails if artifact linkage is incomplete.

## WS7. Testing and CI Hardening

### Problems to solve

- package scripts swallow failures.
- tests expect stale auth and route shapes.
- no dependable signal exists for merge quality.

### Tasks

1. Remove all `|| true` from typecheck, lint, unit, and e2e scripts in active packages.
2. Split tests into:
   - contract tests
   - service unit tests
   - integration tests
   - e2e happy-path tests
3. Rewrite stale tests around the canonical auth strategy and canonical route map.
4. Add fixture-backed tests for:
   - dashboard
   - module detail
   - collaboration thread flow
   - generation flow
   - simulation authoring flow
5. Add CI requirements that block merges on failing checks.
6. Publish a test matrix document tied to product capabilities.

### Files to update

- `products/tutorputor/apps/tutorputor-web/package.json`
- `products/tutorputor/tests/e2e/comprehensive.test.ts`
- `products/tutorputor/tests/e2e/smoke.spec.ts`
- `products/tutorputor/tests/integration/comprehensive.test.ts`
- `products/tutorputor/docs/guidelines/TESTING.md`

### Acceptance criteria

- CI fails on type errors or failing tests.
- at least one real e2e path validates automatic content generation and review.
- test names map to real implemented features.

## WS8. Documentation Reconciliation

### Tasks

1. Create `CURRENT_STATE.md` describing what is implemented today.
2. Keep one target architecture doc and one current-state doc.
3. Mark aspirational docs with explicit labels such as `Target State` or `Concept`.
4. Update user and technical docs to match route names, auth behavior, and feature availability.
5. Add a maintenance rule: every feature PR touching behavior must update current-state docs.

### Acceptance criteria

- A new engineer can read the docs and predict what actually works.
- architecture, product, and test docs no longer contradict each other on core behavior.

## Suggested Execution Order by Team

### Team A: Platform and Contracts

- WS1 API contract unification
- WS2 auth hardening
- WS6 persistence activation

### Team B: Content Generation and Simulation

- WS3 generation orchestration
- WS4 simulation authoring correctness

### Team C: Product UI and QA

- WS5 UI and UX completion
- WS7 tests and CI
- WS8 docs reconciliation

## Milestone Plan

### Milestone 1

- Contract decision approved
- search registration fixed
- canonical route map published
- no new fake fallback data added

### Milestone 2

- auth context unified
- login or SSO model standardized
- core student flows hit real backend routes

### Milestone 3

- generation hooks and pages use real job APIs
- persisted content package available
- simulation authoring fixed

### Milestone 4

- admin authoring is API-backed
- animation and simulation links persisted
- validation and publishing hardened

### Milestone 5

- tests fail correctly
- docs reconciled
- placeholders removed or hidden

## Risks and Mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Route migration breaks existing clients | High | Add temporary compatibility aliases and remove them after client migration |
| Auth hardening breaks dev workflows | Medium | Provide explicit local fixture auth mode and seed scripts |
| Generation engine integration adds latency | Medium | Use async job model with progress polling and caching |
| Schema gaps emerge during persistence wiring | Medium | Do schema review before implementation and stage migrations early |
| Docs drift again after remediation | Medium | Require doc updates in PR checklist and reviewer policy |

## PR Breakdown Recommendation

Keep PRs narrow and sequential:

1. `platform: unify route registration and restore search`
2. `web/admin: migrate clients to canonical API contract`
3. `platform: replace header-based auth helpers with verified identity context`
4. `tests: align auth and route expectations with canonical contract`
5. `generation: implement real content generation hooks and job APIs`
6. `simulation: fix NL authoring paths and domain correctness`
7. `persistence: store and query examples, animations, and simulations`
8. `ui: replace placeholders and remove disabled demo paths`
9. `ci/docs: enforce failing quality gates and reconcile docs`

## Immediate Next Actions

If work starts now, the first three implementation tasks should be:

1. Update `setup.ts` and route inventory docs to reflect the real active module map and register search if the UI keeps search enabled.
2. Replace the current web and admin client route assumptions with one canonical contract document and typed client implementation.
3. Remove silent fallback mocks from the student dashboard, module, collaboration, gamification, and search clients so real breakages become visible during integration.

## Notes

- The separate content-generation service appears to be the strongest foundation for true autonomous content package generation. The recommended strategy is to integrate it cleanly rather than rebuild equivalent logic inside temporary route handlers.
- Some placeholder or demo code may still be useful for prototyping, but it should be explicitly isolated behind development-only or sandbox-only pathways.

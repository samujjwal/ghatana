# YAPPC and Platform TypeScript Test Coverage Audit

Date: 2026-04-13
Scope: `products/yappc`, `platform/typescript`
Audit standard: 100% meaningful coverage across behavior, workflows, contracts, tiers, non-functional concerns, and release-gate ownership

## 1. Executive Summary

Overall result: the audited scope is not currently close to 100% meaningful coverage.

- `products/yappc` has a materially stronger test ecosystem than `platform/typescript`.
- `platform/typescript` has solid coverage in a subset of packages, but package ownership is uneven and several published packages have no discovered tests.
- YAPPC has real multi-tier assets in place, including backend checks, frontend tests, Playwright, OpenAPI contract testing, and release-evidence bundling, but several suites are shallow, mock-heavy, skipped, or misclassified.
- The repository cannot currently claim production-grade 100% meaningful coverage because critical behavior is still unowned at the right tiers.

Estimated completeness toward 100% meaningful coverage:

- `products/yappc`: 55% to 65%
- `platform/typescript`: 35% to 45%
- combined audited scope: 45% to 55%

Release confidence: Low

Major systemic risks:

- zero-test TypeScript packages with public exports and build scripts
- mock-heavy suites labeled as integration or E2E
- large number of skipped YAPPC frontend suites in AI, canvas, and workflow areas
- weak ownership for resilience, recovery, and real performance validation
- incomplete ownership for event-schema contracts and cross-boundary compatibility
- fragmented release-gate enforcement across multiple workflows

## 2. Definition of 100% Coverage for This Repo

For this audit, 100% coverage does not mean 100% lines or 100% branches in isolation.

100% meaningful coverage means all required behavior is explicitly owned by at least one correct tier, and critical behavior is validated across multiple tiers where necessary.

### Required dimensions

- code coverage for meaningful logic, branches, decisions, and critical paths
- feature coverage for every shipped feature and supported user capability
- requirement coverage reconstructed from docs, contracts, workflows, and code
- business-rule coverage for invariants, validation, permissions, and state transitions
- workflow coverage for critical user and backend journeys
- contract coverage for OpenAPI, event schemas, serialization, and compatibility
- integration coverage for real boundaries including DB, middleware, auth, eventing, and browser-runtime boundaries
- non-functional coverage where applicable: latency, concurrency, throughput, degradation, recovery
- explicit release-gate coverage for the minimum production-blocking journeys
- explicit tier ownership for every critical gap and every critical behavior

### Acceptable exclusions

The following are acceptable exclusions only when justified and documented:

- generated `dist` outputs
- re-export-only files when underlying implementation is already tested
- storybook-only assets when underlying component behavior is already tested elsewhere
- compatibility scaffolding not shipped in production

### What currently blocks a 100% claim

- several `platform/typescript` packages have no discovered tests
- YAPPC has many tests, but too many are skipped or mock-heavy for the tier they claim to cover
- YAPPC OpenAPI coverage exists, but event-schema contract coverage is weak or absent
- resilience and recovery are not comprehensively owned
- smoke and release-gate ownership are fragmented between weak and strong pipelines

## 3. Current vs Recommended Tier Model

### Tier 1: Unit

Definition:

- isolated logic
- deterministic
- no real infrastructure

Current state:

- strong in many YAPPC Java services
- present in several TypeScript packages such as `config`, `tokens`, `ds-schema`, `platform-events`, `realtime`
- diluted by shallow tests that only verify object shape or static constants

What must belong here for 100%:

- validators
- state machines and state transitions
- branching logic
- data transformations
- permission matrices
- serialization helpers
- policy evaluation logic

Recommended corrections:

- add unit suites for all zero-test TypeScript packages
- replace placeholder or tautological tests with behavioral assertions
- demote mock-heavy pseudo-integration tests back to unit where appropriate

### Tier 2: Integration (non-browser)

Definition:

- service, repository, middleware, DB, and HTTP wiring
- real boundaries
- no browser required

Current state:

- present in YAPPC backend
- several frontend API route tests exist but frequently use mocked Prisma or mocked downstream services

What must belong here for 100%:

- auth middleware with real request handling
- DB-backed repository behavior
- transaction and isolation behavior
- async orchestration through real modules
- failure propagation across actual boundaries

Recommended corrections:

- introduce real Postgres-backed route tests for critical BFF flows
- clearly separate mock route tests from DB-backed integration tests

### Tier 3: Integration (browser)

Definition:

- UI rendering
- controlled interactions
- routing
- browser APIs
- backend may be mocked or intercepted

Current state:

- strongest tier for many `platform/typescript` packages
- large portion of YAPPC Playwright suite effectively belongs here because API calls are intercepted or client state is seeded

What must belong here for 100%:

- loading, empty, success, error, and partial states
- keyboard and accessibility interactions
- local persistence and browser API behaviors
- component composition flows with controlled dependencies

Recommended corrections:

- rename or relabel mocked Playwright suites as browser integration, not E2E
- deepen assertions for sorting, filtering, pagination, and state transitions

### Tier 4: Contract

Definition:

- schema validation
- request/response compatibility
- serialization/deserialization
- event schema compatibility

Current state:

- YAPPC OpenAPI contract testing exists in CI via Schemathesis
- TypeScript schema and contract validation exist in `ds-schema`, `tokens`, `platform-events`, `realtime`
- YAPPC event-schema coverage is weak

What must belong here for 100%:

- OpenAPI contract conformance
- event payload compatibility
- schema evolution tests
- golden payload round-trips

Recommended corrections:

- add YAPPC event-schema contract suites for `config/agents/event-schemas`
- add producer-consumer compatibility tests and version evolution coverage

### Tier 5: API E2E

Definition:

- public API through real app stack
- real middleware and persistence
- no browser required

Current state:

- weak
- several suites labeled E2E are actually mock-backed service tests

What must belong here for 100%:

- auth and session lifecycle
- tenant isolation through real stack
- workspace/project lifecycle through public API
- code generation request-to-status lifecycle
- error and validation behavior with real infrastructure

Recommended corrections:

- replace mock-backed E2E with real-stack API E2E for critical workflows

### Tier 6: UI / Browser E2E

Definition:

- full user journeys via browser
- production-like backend stack

Current state:

- present in YAPPC CI, but many suites are mocked, skipped, or frontend-only
- absent for `platform/typescript` except indirectly through products

What must belong here for 100%:

- real login and session restore
- core project lifecycle
- canvas persistence and collaboration core path
- critical release-blocking journeys

Recommended corrections:

- maintain a small PR-gated real-stack browser suite
- keep heavier suites nightly, but ensure the PR suite owns critical-path realism

### Tier 7: Performance / Load / Stress / Soak

Definition:

- measurable performance against declared SLOs

Current state:

- YAPPC has one k6 generation script
- `platform/typescript` has placeholder performance tests that do not measure reality

What must belong here for 100%:

- code generation latency and concurrency
- collaboration throughput and fanout under load
- websocket/session stability under sustained use
- bundle or render budgets where critical

Recommended corrections:

- replace placeholder TS performance tests with real benchmark/load harnesses
- add YAPPC collaboration and auth churn scenarios

### Tier 8: Reliability / Resilience / Recovery

Definition:

- retries
- fallback
- degradation
- restart
- recovery

Current state:

- partial unit-level coverage only
- no strong system-level recovery ownership

What must belong here for 100%:

- websocket reconnect and event recovery
- auth failure and session expiry recovery
- AI provider timeout and fallback behavior through real integration boundaries
- degraded startup and restart handling

Recommended corrections:

- add chaos and restart recovery scenarios
- make recovery an explicit test tier with CI ownership

### Tier 9: Smoke / System / Release Gate

Definition:

- smallest high-signal release suite

Current state:

- YAPPC root GitHub CI is stronger than product-local quality gates
- release confidence is split across multiple workflows
- some smoke commands remain permissive

What must belong here for 100%:

- authenticated access
- startup diagnostics
- readiness/liveness/metrics
- tenant isolation
- one real browser journey
- contract health

Recommended corrections:

- consolidate one authoritative release gate and remove permissive empty-suite smoke semantics

## 4. Tier-by-Tier 100% Coverage Gap Report

### Unit

Required for 100%:

- all deterministic logic
- validators
- state transitions
- branch-heavy logic
- permission and policy rules

Current coverage:

- good in YAPPC Java services such as `AiWorkflowServiceTest` and `ValidationServiceTest`
- good in TypeScript packages `config`, `tokens`, `ds-schema`, `platform-events`, `realtime`

Missing coverage:

- `platform/typescript/forms`
- `platform/typescript/patterns`
- `platform/typescript/ui`
- `platform/typescript/primitives`
- `platform/typescript/wizard`
- `platform/typescript/data-grid`
- `platform/typescript/canvas-core`
- `platform/typescript/canvas-tools`
- `platform/typescript/canvas-plugins`

Redundant coverage:

- multiple object-shape tests in UI packages that do not prove behavior

Misplaced coverage:

- mock-heavy route tests counted as integration when they mainly prove local logic

Depth concerns:

- `platform/typescript/ui-builder/src/core/__tests__/validation.test.ts`
- `platform/typescript/ds-governance/src/__tests__/index.test.ts`

Exact additions needed:

- baseline unit suites for all zero-test packages
- branch, validation, and failure-case coverage for governance, UI-builder, and SSO flows

### Integration (non-browser)

Required for 100%:

- real middleware and HTTP boundaries
- DB-backed repository behavior
- real async orchestration
- error propagation through actual boundaries

Current coverage:

- present in YAPPC backend
- partially present in frontend API server tests

Missing coverage:

- DB-backed auth/session flows
- DB-backed workspace/project lifecycle
- DB-backed canvas persistence flows
- real rate-limit and audit integration verification

Redundant coverage:

- overlapping mocked route tests on auth and workspace access

Misplaced coverage:

- `products/yappc/frontend/apps/api/src/__tests__/routes.integration.test.ts` uses mocked Prisma, so it does not cover DB or transaction semantics
- `products/yappc/api/src/test/java/com/ghatana/yappc/api/YappcCodeGenApiOpenApiIntegrationTest.java` uses `MockCodeGenApiClient`

Depth concerns:

- many tests prove status code and call shape but not persistence or rollback behavior

Exact additions needed:

- Postgres-backed route integration for auth, workspace, project, canvas, and audit flows

### Integration (browser)

Required for 100%:

- meaningful UI interaction coverage
- browser API usage
- loading/error/empty/partial state coverage
- accessible keyboard interactions

Current coverage:

- strongest tier for `platform/typescript`
- large part of YAPPC Playwright suite belongs here rather than E2E

Missing coverage:

- deep interaction coverage for complex UI packages
- strong state-transition and error-path assertions

Redundant coverage:

- multiple page-load and smoke-style browser checks without distinct value

Misplaced coverage:

- mocked Playwright auth flows labeled like E2E while intercepting API routes

Depth concerns:

- `platform/typescript/design-system/src/organisms/__tests__/Table.test.tsx`

Exact additions needed:

- stronger assertions for sorting, filtering, pagination, callbacks, a11y, and failure states

### Contract

Required for 100%:

- OpenAPI conformance
- event schema validation
- backward/forward compatibility
- serialization/deserialization round-trips

Current coverage:

- YAPPC OpenAPI contract testing in root CI
- TypeScript schema and contract tests in `ds-schema`, `tokens`, `platform-events`, `realtime`

Missing coverage:

- YAPPC event-schema validation for `config/agents/event-schemas/*.json`
- compatibility checks across producers and consumers

Redundant coverage:

- multiple local schema tests without real producer-consumer validation

Misplaced coverage:

- mock OpenAPI client tests treated like integration coverage

Depth concerns:

- missing version evolution and compatibility failure scenarios

Exact additions needed:

- event-schema contract test suite with golden payloads and compatibility assertions

### API E2E

Required for 100%:

- public API through real stack
- real auth, persistence, and middleware

Current coverage:

- thin

Missing coverage:

- real auth/session lifecycle
- real tenant isolation via public endpoints
- real code-generation lifecycle via public API
- real validation and error recovery paths

Misplaced coverage:

- `products/yappc/e2e-tests/src/test/java/com/ghatana/yappc/e2e/AuthenticationFlowE2ETest.java` uses `MockAuthenticationService`

Depth concerns:

- current E2E naming overstates realism

Exact additions needed:

- real-stack API E2E for login, workspace/project CRUD, tenant isolation, and code generation polling

### UI / Browser E2E

Required for 100%:

- real browser journeys against production-like backend

Current coverage:

- YAPPC has many Playwright files and root CI runs them
- many suites still depend on route interception, local storage seeding, or are skipped

Missing coverage:

- real backend auth flow
- real project and canvas persistence journey
- true collaboration path
- recovery after reload or backend interruption

Redundant coverage:

- multiple smoke-like page checks

Misplaced coverage:

- `products/yappc/frontend/e2e/auth.spec.ts` is browser integration because auth endpoints are intercepted in `e2e/helpers/auth-journey.ts`

Depth concerns:

- 94 skipped test markers were found under `products/yappc/frontend`

Exact additions needed:

- define a small mandatory real-stack browser suite for PRs and keep heavier suites nightly

### Performance / Load

Required for 100%:

- measured SLA ownership
- load, concurrency, and stability for critical flows

Current coverage:

- one YAPPC k6 script for code generation
- placeholder TS performance tests that use static numbers

Missing coverage:

- collaboration fanout under real load
- auth/session churn
- long-lived realtime stability
- canvas-heavy render and interaction budgets

Misplaced coverage:

- `platform/typescript/testing/src/performance-under-load.test.ts` is not a real performance suite

Depth concerns:

- no measurement harness behind many claimed SLO checks

Exact additions needed:

- real benchmark/load harnesses with explicit thresholds and CI reporting

### Reliability / Resilience / Recovery

Required for 100%:

- retries
- fallback
- reconnect
- restart and recovery
- degraded modes

Current coverage:

- partial unit-level fallback and retry coverage in YAPPC AI service tests

Missing coverage:

- websocket reconnect and replay
- auth/session recovery
- backend restart behavior
- provider timeout and degraded-mode behavior through actual boundaries

Misplaced coverage:

- unit-only retry tests counted toward operational resilience

Depth concerns:

- no explicit system-level recovery tier ownership

Exact additions needed:

- chaos and restart recovery tests for auth, AI, realtime, and persistence

### Smoke / Release Gate

Required for 100%:

- minimal production-blocking validation only

Current coverage:

- YAPPC root GitHub CI is strong
- product-local `quality-gates.yml` is materially weaker

Missing coverage:

- one authoritative release gate for all critical paths
- removal of permissive empty-suite smoke semantics

Misplaced coverage:

- `products/yappc/frontend/apps/web/package.json` has `test:smoke` using `--passWithNoTests`

Depth concerns:

- fragmented ownership weakens trust in green builds

Exact additions needed:

- consolidate one mandatory release gate and explicitly define the owning journeys

## 5. Feature-to-Tier 100% Coverage Matrix

| Feature / Module | Required Tiers | Existing Tiers | Missing Tiers | Missing Scenarios | Depth Adequacy | 100% Achieved | Recommended Additions |
|---|---|---|---|---|---|---|---|
| YAPPC auth, session, RBAC | unit, integration, contract, API E2E, UI E2E, smoke | unit, browser integration, some mock integration | API E2E, real UI E2E, resilience | token refresh, session expiry recovery, fail-closed auth, real MFA path | weak to moderate | No | real API auth suite, real backend browser auth journey |
| YAPPC tenant isolation | unit, integration, contract, API E2E, smoke | route integration, some backend checks | real API E2E, browser E2E | cross-tenant access denial, audit evidence, websocket tenancy | moderate | No | real-stack tenant isolation tests |
| YAPPC workspace/project lifecycle | unit, integration, API E2E, browser E2E | route integration, browser flows | DB-backed API E2E, resilience | duplicate creation, rollback, invalid transitions | moderate | No | Postgres-backed lifecycle tests |
| YAPPC design validation and code generation | unit, contract, API E2E, browser E2E, perf | unit, mocked OpenAPI tests, browser flow, one load script | real API E2E, resilience, deeper perf | invalid design edges, timeout/fallback, partial generation, polling failures | moderate | No | real generation stack tests and load scenarios |
| YAPPC canvas and collaboration | unit, browser integration, browser E2E, perf, resilience | many browser tests, many skipped | true browser E2E, perf, resilience | concurrent edits, reconnect, persistence, undo/redo after reload | weak | No | unskip or replace canvas suites, add real collaboration env |
| YAPPC release startup and observability | integration, smoke | backend startup diagnostics in root CI | browser plus API release linkage | degraded startup, metrics auth failures, partial health | moderate | Partially | add release-gate assertions across startup plus browser/API journey |
| TypeScript design system, theme, tokens | unit, browser integration, contract | strong unit and browser in some packages | cross-package integration, visual regression | token-theme-component composition, RTL, accessible edge states | moderate | No | composition tests and visual/a11y regression |
| TypeScript realtime, events, platform-events | unit, contract, resilience, perf | unit and contract present | resilience and real transport integration | reconnect, ordering under disruption, consumer compatibility | moderate | No | transport fault-injection tests |
| TypeScript API, config, schemas | unit, contract | present | consumer integration | malformed payloads against real consumers, middleware interaction | moderate | No | representative service integration tests |
| TypeScript forms, patterns, ui, primitives, wizard | unit, browser integration, contract where relevant | essentially none | all | validation, composition, rendering, events, a11y | none | No | create baseline suites and owners |
| TypeScript canvas family | unit, contract, browser integration, perf | some in `canvas`, none in sibling packages | multiple | plugin lifecycle, export/import fidelity, collaboration, AI adapter compatibility | weak | No | package-level tests and cross-package composition tests |

## 6. Detailed Findings by Area

### Critical findings

1. Several exported TypeScript packages have no discovered tests.

- `platform/typescript/forms`
- `platform/typescript/patterns`
- `platform/typescript/ui`
- `platform/typescript/primitives`
- `platform/typescript/wizard`
- `platform/typescript/data-grid`
- `platform/typescript/canvas-core`
- `platform/typescript/canvas-tools`
- `platform/typescript/canvas-plugins`

2. `@ghatana/sso-client` can pass with no tests.

- File: `platform/typescript/sso-client/package.json`
- Evidence: test script is `jest --passWithNoTests`

3. YAPPC frontend contains a large skipped-suite backlog.

- `94` skip markers were found under `products/yappc/frontend`
- high-risk areas include AI/ML, canvas interaction, collaboration, performance, and workflow integration

4. Multiple YAPPC suites are misclassified as E2E or integration while using mocks.

- `products/yappc/e2e-tests/src/test/java/com/ghatana/yappc/e2e/AuthenticationFlowE2ETest.java`
- `products/yappc/api/src/test/java/com/ghatana/yappc/api/YappcCodeGenApiOpenApiIntegrationTest.java`
- `products/yappc/frontend/apps/api/src/__tests__/routes.integration.test.ts`

### High findings

5. Browser auth journeys are intercepted, so they do not prove real backend auth.

- Files under `products/yappc/frontend/e2e/`
- interception helper: `products/yappc/frontend/e2e/helpers/auth-journey.ts`

6. TypeScript performance tests include placeholder assertions rather than measurements.

- `platform/typescript/testing/src/performance-under-load.test.ts`

7. Contract ownership is incomplete for YAPPC event schemas.

- affected area: `products/yappc/config/agents/event-schemas/*.json`

### Medium findings

8. Some UI tests are too shallow to count toward meaningful completeness.

- `platform/typescript/design-system/src/organisms/__tests__/Table.test.tsx`
- `platform/typescript/ui-builder/src/core/__tests__/validation.test.ts`
- `platform/typescript/ds-governance/src/__tests__/index.test.ts`

9. Platform TypeScript CI ownership is incomplete or stale.

- `.github/workflows/ui-package-gates.yml` triggers on `platform/typescript/**` but does not run generic platform package jobs
- `.github/workflows/design-system.yml` still references `libs/typescript/**`

10. YAPPC has two different CI stories.

- strong root gate: `.github/workflows/yappc-ci.yml`
- weaker product-local gate: `products/yappc/.github/workflows/quality-gates.yml`

## 7. Misclassification Findings

### `products/yappc/e2e-tests/src/test/java/com/ghatana/yappc/e2e/AuthenticationFlowE2ETest.java`

- current tier: API E2E
- actual tier: unit or integration with mocks
- why misplaced: uses `MockAuthenticationService` and in-memory state
- runtime implication: does not validate real HTTP, DB, or provider wiring
- recommended relocation: rename as service-flow integration and add a real API E2E replacement

### `products/yappc/api/src/test/java/com/ghatana/yappc/api/YappcCodeGenApiOpenApiIntegrationTest.java`

- current tier: integration or contract
- actual tier: mock contract or unit hybrid
- why misplaced: uses `MockCodeGenApiClient`
- runtime implication: does not prove spec-implementation fidelity
- recommended relocation: keep as local schema-shape test and add real HTTP contract coverage

### `products/yappc/frontend/apps/api/src/__tests__/routes.integration.test.ts`

- current tier: real route integration
- actual tier: HTTP-layer integration with mocked persistence
- why misplaced: Prisma is mocked
- runtime implication: misses DB, transaction, and isolation behavior
- recommended relocation: retain as route integration, add DB-backed route integration tier

### `products/yappc/frontend/e2e/auth.spec.ts`

- current tier: UI E2E
- actual tier: browser integration
- why misplaced: auth endpoints are intercepted in `e2e/helpers/auth-journey.ts`
- runtime implication: does not validate real auth/session contract
- recommended relocation: classify as browser integration and add backend-backed browser E2E auth

### `platform/typescript/testing/src/performance-under-load.test.ts`

- current tier: performance
- actual tier: placeholder assertions
- why misplaced: no real measurement is performed
- runtime implication: provides no real SLA evidence
- recommended relocation: replace with real benchmark harness or remove

## 8. Depth Deficiency Findings

### `platform/typescript/design-system/src/organisms/__tests__/Table.test.tsx`

- currently proves: rendering of rows, headers, and some controls
- fails to prove: real sorting, filtering, pagination, and accessibility behavior
- why insufficient: multiple tests allow a fallback pass even when intended behavior is absent
- exact improvement: assert order changes, filter outcomes, page boundaries, callback effects, and keyboard navigation

### `platform/typescript/ui-builder/src/core/__tests__/validation.test.ts`

- currently proves: data structure contains required fields
- fails to prove: invalid documents, rule failures, graph consistency, binding constraints
- why insufficient: object-shape validation is not actual behavior validation
- exact improvement: add invalid documents, graph errors, and rule-enforcement assertions

### `platform/typescript/ds-governance/src/__tests__/index.test.ts`

- currently proves: basic naming rules
- fails to prove: duplication detection, compatibility gates, contribution-policy enforcement
- why insufficient: narrow slice of intended governance responsibility
- exact improvement: add duplicate-token, conflicting-component, and contribution-gate failure scenarios

### `platform/typescript/testing/src/performance-under-load.test.ts`

- currently proves: static constants satisfy expectations
- fails to prove: anything about runtime performance
- why insufficient: no timing, no system interaction, no measurement harness
- exact improvement: replace with real benchmark or load suite using measurable thresholds

## 9. Uncovered and Partially Covered Areas

### Uncovered

- zero-test TypeScript packages listed above
- YAPPC event-schema contract validation
- TypeScript cross-package composition tests for token-theme-design-system flows
- real YAPPC API E2E for auth, tenant isolation, and generation lifecycle
- real resilience and recovery tier ownership

### Partially covered

- YAPPC auth and session handling
- YAPPC tenant isolation
- YAPPC workspace and project lifecycle
- YAPPC canvas and collaboration
- YAPPC code generation lifecycle
- TypeScript design-system interactive behavior
- TypeScript realtime reliability and event ordering under disruption
- accessibility under real browser rendering conditions

## 10. Redundancy Findings

- several mocked route tests in YAPPC frontend overlap on status-code and auth behavior without adding tier value
- multiple browser smoke-style tests overlap without owning backend realism
- some TypeScript UI suites repeat render-presence assertions rather than deeper interaction outcomes
- duplicate CI definitions create overlapping but inconsistent enforcement for YAPPC quality gates

## 11. Execution and CI Findings

### YAPPC

- strongest gate: `.github/workflows/yappc-ci.yml`
- weaker local gate: `products/yappc/.github/workflows/quality-gates.yml`
- dedicated FE coverage workflow exists: `.github/workflows/yappc-fe-coverage.yml`
- dedicated nightly full E2E workflow exists: `.github/workflows/yappc-fe-e2e-full.yml`
- issue: critical ownership is split across multiple workflows, reducing clarity about the authoritative release gate

### Platform TypeScript

- `pnpm-workspace.yaml` is the real workspace owner, not `platform/typescript/package.json`
- `.github/workflows/ui-package-gates.yml` triggers on `platform/typescript/**` but runs only AEP/Data Cloud/Gateway jobs
- `.github/workflows/design-system.yml` uses stale path triggers
- issue: generic package-level enforcement for `platform/typescript` is incomplete

## 12. Detailed Implementation Plan

This section turns the audit into an execution-ready plan.

### Phase 0: Governance and Tier Truth

Goal:

- make the repo truthful about what each suite actually covers

Actions:

1. Reclassify mock-backed YAPPC E2E and integration suites.
2. Rename or tag browser suites that intercept backend calls as browser integration, not E2E.
3. Define one authoritative release gate in root CI and demote weaker local gates to advisory if necessary.
4. Add a tier inventory document listing every test surface and its owner.

Deliverables:

- a canonical test-tier inventory doc
- corrected suite naming or metadata
- one authoritative release gate workflow

Exit criteria:

- no mock-backed suite is counted as API E2E or UI E2E
- release gate is unambiguous

### Phase 1: Zero-Test Package Closure

Goal:

- eliminate unowned TypeScript packages

Target packages:

- `canvas-core`
- `canvas-plugins`
- `canvas-tools`
- `data-grid`
- `forms`
- `patterns`
- `primitives`
- `ui`
- `wizard`

Recommended test inventory per package:

- unit tests for all exported pure logic
- browser-integration tests for interactive components or hooks
- contract tests for schemas, adapters, or validators where relevant

Implementation steps:

1. enumerate exports for each package
2. classify each export as logic, UI, schema, adapter, or composition
3. add baseline tests covering happy path, validation failure, malformed input, and one regression-prone branch
4. add CI check that fails if a package has a test script but no discovered tests

Exit criteria:

- all listed packages have discovered tests
- no package relies on `passWithNoTests`

### Phase 2: Real API E2E for YAPPC Critical Paths

Goal:

- own release-critical backend behavior through the real stack

Critical paths:

- auth login, logout, session refresh
- tenant isolation across workspace/project routes
- project creation and retrieval
- design creation, validation, and code generation status polling

Implementation steps:

1. stand up YAPPC API against test Postgres in CI
2. create API E2E suite using public endpoints only
3. validate persisted state, not just responses
4. add negative-path and authorization checks
5. include audit and metrics assertions for critical flows where possible

Exit criteria:

- critical API paths run against real app stack and DB in CI

### Phase 3: Real Browser E2E Subset for Release Gate

Goal:

- define a small but production-meaningful browser suite

Required release-blocking journeys:

- real login and session restore
- workspace or project entry path
- project creation and save
- core canvas or generation interaction
- logout and session invalidation

Implementation steps:

1. stop intercepting auth for release-gate browser tests
2. provision backend-ready seeded environment in CI
3. keep a small PR-gated suite and a broader nightly suite
4. move mocked browser tests into a clearly labeled browser-integration bucket

Exit criteria:

- at least one browser suite in PR CI uses real backend auth and persistence

### Phase 4: Event and Contract Ownership

Goal:

- close contract blind spots beyond OpenAPI

Scope:

- `products/yappc/config/agents/event-schemas/*.json`
- OpenAPI request/response compatibility
- TypeScript producer-consumer schema compatibility

Implementation steps:

1. create event-schema validation harness with golden payloads
2. add positive and negative validation cases for every critical event schema
3. add compatibility tests for schema evolution where versions exist
4. link event producers and consumers to contract assertions

Exit criteria:

- every release-critical event schema has explicit contract tests

### Phase 5: Unskip or Replace High-Risk Frontend Suites

Goal:

- recover intended coverage in YAPPC frontend

Priority areas:

- AI and ML recommendation flows
- canvas interaction and collaboration
- performance and edge-case suites
- route and workflow integration

Implementation steps:

1. audit every `skip` marker and categorize as obsolete, flaky, blocked, or pending implementation
2. delete obsolete suites
3. fix or replace flaky suites with deterministic harnesses
4. implement missing product behavior or remove invalid assertions
5. set a CI budget for maximum allowed skipped suites in critical directories

Exit criteria:

- skipped suite count in critical frontend paths is reduced to near-zero

### Phase 6: Resilience and Recovery Coverage

Goal:

- explicitly own non-happy-path operability

Target scenarios:

- websocket disconnect and reconnect
- dropped or delayed event recovery
- AI provider timeout and fallback
- auth session expiry and recovery
- restart and degraded startup handling

Implementation steps:

1. add deterministic fault-injection helpers
2. run reconnect and replay scenarios in realtime and collaboration layers
3. add restart/recovery checks to release diagnostics
4. record explicit tier ownership in CI and docs

Exit criteria:

- resilience is no longer implied by unit tests only

### Phase 7: Real Performance Ownership

Goal:

- replace placeholder performance claims with measurable evidence

Target scenarios:

- YAPPC code generation latency
- collaboration broadcast and fanout under load
- browser interaction under large canvas or large datasets
- bundle and render budgets for critical UI packages

Implementation steps:

1. remove or quarantine placeholder TS performance tests
2. promote k6 and browser benchmarks into a proper performance tier
3. define thresholds and alerting for critical paths
4. publish reports in CI artifacts

Exit criteria:

- performance suites are measuring real systems and can fail on SLO regressions

### Phase 8: CI Consolidation and Enforcement

Goal:

- make coverage sustainable, enforceable, and hard to fake

Implementation steps:

1. add package-level discovery checks for zero-test packages in `platform/typescript`
2. remove `passWithNoTests` where present
3. update stale workflow triggers referencing old paths
4. ensure release gate depends on contract, API E2E, browser E2E, startup diagnostics, and core coverage jobs
5. publish a machine-readable summary of tier ownership and gap status

Exit criteria:

- CI reflects actual tier model and blocks unowned critical gaps

## 13. Action Backlog by Priority

### P0: Immediate blockers

1. Remove `passWithNoTests` from `platform/typescript/sso-client/package.json`.
2. Add baseline tests for all zero-test TypeScript packages.
3. Define one authoritative YAPPC release gate in root CI.
4. Reclassify or replace mock-backed YAPPC E2E suites.

### P1: Critical-path realism

1. Add real API E2E for auth, tenant isolation, and generation lifecycle.
2. Add real backend-backed browser E2E for login and core project flow.
3. Reduce skipped-suite debt in YAPPC frontend critical paths.

### P2: Depth and contract correctness

1. Replace shallow TS UI tests with deeper behavioral assertions.
2. Add YAPPC event-schema contract test suite.
3. Add cross-package composition tests for tokens, theme, and design-system.

### P3: Non-functional ownership

1. Replace placeholder performance tests.
2. Add resilience and recovery suites for realtime, auth, and AI.
3. Add real collaboration load and reconnect scenarios.

### P4: Cleanup and sustainment

1. Remove redundant mock-heavy tests with no distinct tier value.
2. Fix stale workflow triggers and path references.
3. Add automated detection for skipped tests in critical directories.

## 14. Final Verdict

Current coverage is not sufficient.

- current raw test quantity is not the problem by itself
- current tier ownership and behavioral depth are the main blockers
- several green paths are misleading because suites are skipped, mock-heavy, shallow, or permissive

Current depth is not sufficient.

- strong tests exist, but they are not yet dominant enough in the highest-risk areas

Current tier design is only partially sound.

- YAPPC has the shape of a mature multi-tier system but still over-credits mock-backed suites
- `platform/typescript` has uneven ownership and incomplete CI enforcement

Current execution cost is only partly controlled.

- YAPPC has workable pipeline pieces
- platform TypeScript enforcement is inconsistent
- duplication and fragmentation reduce clarity

Distance from 100% meaningful coverage:

- substantial
- the repository is closer to a strong mid-maturity test ecosystem than to complete meaningful coverage

Biggest blockers before production-grade confidence:

1. zero-test TypeScript packages
2. skipped YAPPC frontend suites in critical areas
3. mock-backed E2E and integration tests counted too generously
4. incomplete contract ownership for event-driven flows
5. weak resilience and performance ownership

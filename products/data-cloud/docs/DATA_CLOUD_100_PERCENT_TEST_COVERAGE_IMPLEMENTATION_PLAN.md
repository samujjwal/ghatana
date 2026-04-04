# Data Cloud 100% Test Coverage Implementation Plan

> Status: Expanded execution draft
> Scope: `products/data-cloud/**`
> Goal: reach 100% structural and behavioral coverage across all Data Cloud modules, libraries, and UI/UX surfaces

## 1. Goal

Reach 100% in all required dimensions for `products/data-cloud`:

- 100% structural: line, branch, method/function
- 100% behavioral: requirements, use cases, flows, logic, queries, interactions, failure modes
- 100% by test type:
  - unit for logic/computation
  - integration for module/service/storage interactions
  - API E2E for public behavior and end-to-end flows
  - UI logic/E2E for real user workflows

This plan treats the backend/OpenAPI contract in `products/data-cloud/docs/openapi.yaml`, the product vision in `products/data-cloud/README.md`, the execution vision in `products/data-cloud/docs/DATA_CLOUD_E2E_VISION_EXECUTION_PLAN.md`, and the UI specs in `products/data-cloud/ui/docs/web-page-specs/INDEX.md` as the canonical sources of intent.

## 2. Success Criteria

Coverage is only complete when all of the following are true:

- Every route in `products/data-cloud/docs/openapi.yaml` has:
  - contract tests
  - endpoint behavior tests
  - failure-path tests
- Every requirement and use case has an explicit test mapping artifact in-repo
- No UI test relies on speculative or non-canonical routes
- No integration suite uses fake local contracts where real module interaction is required
- Every Java production module and library area is present in the coverage matrix
- Every UI page, shared frontend library area, and UX workflow is present in the coverage matrix
- Coverage gates fail CI if any requirement, route, module, UI area, library, or branch regresses

## 3. Complete Product Coverage Inventory

This section is the non-negotiable inventory. The plan is incomplete if any row here is missing from workstreams, matrices, or CI gates.

### 3.1 Java Gradle Modules

| Module | Role | Coverage obligation |
|---|---|---|
| `spi` | foundational plugin, storage, and integration interfaces | 100% contract, invariant, and implementation-consumer coverage |
| `platform-config` | configuration models, defaults, validation | 100% branch and negative-validation coverage |
| `platform-entity` | entity contracts and entity-domain behavior | 100% CRUD, query, validation, and invariant coverage |
| `platform-event` | event contracts and event-log behavior | 100% ordering, replay, filtering, and isolation coverage |
| `platform-analytics` | analytics, aggregation, reporting, planning | 100% formula, polling, and report behavior coverage |
| `platform-api` | API/application layer, DTOs, controllers, memory/workspace/autonomy surfaces | 100% controller, service, branch, and failure coverage |
| `platform-client` | reusable client contracts and adapters | 100% serialization, deserialization, and error mapping coverage |
| `platform-launcher` | application/service wiring, workflow/execution logic, infrastructure-domain services | 100% service logic and real integration coverage |
| `platform-plugins` | plugin implementations: vector, Kafka, Redis, Iceberg, S3 archive, Trino, enterprise, graph | 100% plugin capability, lifecycle, isolation, and failure coverage |
| `feature-store-ingest` | feature ingest and feature-plane writes | 100% typing, validation, overwrite, freshness, and tenant isolation coverage |
| `agent-registry` | agent catalog and registry capabilities | 100% registration, lookup, policy, and failure-path coverage |
| `launcher` | runtime transport layer: HTTP/gRPC, handlers, middleware, voice, observability entrypoints | 100% route, transport, streaming, and security coverage |
| `sdk` | SDK packaging/generation surface | 100% contract-generation and smoke coverage |

### 3.2 Additional Java Library Areas Present in Repo

These contain production or product-critical Java and must be covered even if they are not represented as peer Gradle modules in the same way.

| Area | Role | Coverage obligation |
|---|---|---|
| `api` | API contract verification and query API integration | included in route coverage and contract parity gates |
| `platform` | shared platform abstractions and support code | included in structural burn-down or removed if dead |
| `data-cloud-cache` | query cache service and cache performance behavior | 100% correctness, staleness, eviction, and performance guard coverage |
| `sdk/build/generated/java-sdk` | generated Java SDK output | schema parity and client smoke coverage |
| `integration-tests` | cross-module integration suites | upgraded to real interaction coverage, not mock-only flows |

### 3.3 TypeScript and Frontend Library Surface

| Area | Role | Coverage obligation |
|---|---|---|
| `ui` package | React + TypeScript Data Cloud UI | 100% logic-level, contract, E2E, and accessibility coverage |
| `ui/src/api` | frontend service wrappers for alerts, analytics, brain, events, governance, lineage, memory, plugins, quality, schema, suggestions | 100% request/response/error mapping coverage |
| `ui/src/lib/api` | shared HTTP clients for collections, workflows, AI, and core transport | 100% serialization, auth, and retry/error coverage |
| `ui/src/contracts` | frontend schema contracts | 100% schema parity with OpenAPI and runtime validation coverage |
| `ui/src/services` | unified brain and workflow AI services | 100% behavior and failure coverage |
| `ui/src/stores` | ambient, command bar, feature flags, workflow state | 100% state transition and side-effect coverage |
| `ui/src/lib` | auth, accessibility, forms, integrations, performance, websocket, persistence, theme utilities | 100% logic, branch, and resilience coverage |
| `ui/src/components` | reusable UI behavior components | 100% interaction, state, and accessibility coverage for critical flows |
| `ui/src/pages` | route-level UI pages | 100% route behavior, empty/loading/error/success coverage |
| `ui/tests/contract` | contract alignment | canonical route/schema coverage only |
| `ui/e2e` and `ui/tests/e2e` | browser E2E and resilience coverage | strict real-user outcome coverage |

### 3.4 UI/UX Product Areas

| UI/UX area | Source | Coverage obligation |
|---|---|---|
| shell and routing | `00_shell_and_routing.md` | route resolution, nav, suspense, auth redirects, 404 |
| dashboard and dashboards | `01_dashboard_page.md` variant, `DashboardsPage` | summaries, widgets, quick actions, refresh, permission-aware rendering |
| collections list/detail/create/edit | `02_collections_page.md`, `03_create_collection_page.md`, `04_edit_collection_page.md` | CRUD, schema forms, validation, save/cancel/error flows |
| workflows list/designer/legacy list | `05_workflows_page.md`, `06_workflow_designer_canvas.md`, `07_workflow_list_page.md` | browse, edit, save, AI assist, state persistence |
| dataset explorer and dataset detail/insights | `11_dataset_explorer_list_page.md`, `12_dataset_detail_insights_page.md` | search, filter, sort, detail tabs, insights correctness |
| lineage explorer | `13_lineage_explorer_page.md` | graph correctness, interaction, filtering, drill-down |
| SQL workspace | `14_sql_workspace_page.md` | authoring, execute, result, failure, history, AI assist |
| AI assistant and semantic search | `15_ai_assistant_and_semantic_search.md` | NL-to-action correctness, confidence, fallback, auditability |
| governance and security hub | `16_governance_and_security_hub_page.md` | policy, pii, audit, permissions, redaction/purge actions |
| alerts and notifications | `17_alerts_and_notifications_page.md` | alert list, acknowledge, filtering, delivery channel config |
| data fabric admin | `09_storage_profiles_admin_page.md`, `10_data_connectors_admin_page.md` | storage profile CRUD, connector CRUD, sync state and error handling |
| event and entity exploration | `EventExplorerPage.tsx`, `EntityBrowserPage.tsx` | search/query/filter/sort/state correctness |
| memory plane and intelligent hub | `MemoryPlaneViewerPage.tsx`, `IntelligentHub.tsx` | memory browse/search, brain state, recommendations, live updates |
| trust center and settings | `TrustCenter.tsx`, `SettingsPage.tsx` | access control, policy visibility, configuration editing |
| plugins and agent/plugin manager | `PluginsPage.tsx`, `PluginDetailsPage.tsx`, `AgentPluginManagerPage.tsx` | plugin lifecycle, capability display, error handling |
| insights and data explorer | `InsightsPage.tsx`, `DataExplorer.tsx` | metrics correctness, drill-down, failure and empty states |
| voice and realtime UX | `ui/src/components/voice`, websocket/realtime libs | transcript flow, live updates, reconnect, accessibility |
| not found and global fallback states | `NotFound/index.tsx` and shared shells | 404, missing-resource, empty, loading, degraded-mode states |

### 3.5 External Workspace Libraries Used by UI

These are outside the Data Cloud package but are part of Data Cloud’s effective runtime behavior and must be integration-tested where relied upon.

| Library | Source | Coverage obligation inside Data Cloud |
|---|---|---|
| `@audio-video/ui` | `ui/package.json` | validate media UI integrations where present |
| `@ghatana/canvas` | `ui/package.json` | validate graph/canvas interactions through page tests |
| `@ghatana/design-system` | `ui/package.json` | validate critical states render accessibly and consistently |
| `@ghatana/flow-canvas` | `ui/package.json` | validate workflow and lineage editing behaviors |
| `@ghatana/platform-utils` | `ui/package.json` | validate adapter assumptions where wrapped locally |
| `@ghatana/realtime` | `ui/package.json` | validate realtime and websocket contracts |
| `@ghatana/theme` | `ui/package.json` | validate theme and accessibility invariants |

### 3.6 Coverage Verification Rule

Before the plan can be considered complete, every row in Sections 3.1 through 3.5 must appear in:

- the requirement matrix
- the module-to-test matrix
- the route-to-test matrix where applicable
- the UI-area-to-test matrix where applicable
- the CI coverage gate configuration

## 4. Workstreams

1. Canonical Requirement Matrix
2. Structural Coverage Expansion
3. Behavioral Coverage Expansion
4. Integration Realism Upgrade
5. UI Contract Convergence
6. CI Enforcement and Reporting

## 5. Workstream 1: Canonical Requirement Matrix

Create these source-of-truth artifacts:

- `products/data-cloud/docs/testing/DATA_CLOUD_REQUIREMENT_COVERAGE_MATRIX.md`
- `products/data-cloud/docs/testing/DATA_CLOUD_UI_COVERAGE_MATRIX.md`
- `products/data-cloud/docs/testing/REQUIREMENT_TEST_INDEX.yaml`

The matrix must contain:

- vision statements from `products/data-cloud/README.md`
- product principles from `products/data-cloud/docs/DATA_CLOUD_E2E_VISION_EXECUTION_PLAN.md`
- route/use-case inventory from `products/data-cloud/docs/openapi.yaml`
- module ownership/boundaries from `products/data-cloud/docs/ADR-DC-001-MODULE-OWNERSHIP.md`
- UI page/spec inventory from `products/data-cloud/ui/docs/web-page-specs/INDEX.md`
- all Java modules and library areas from Sections 3.1 and 3.2
- all frontend surfaces from Sections 3.3, 3.4, and 3.5

For each requirement, define:

- requirement id
- source doc
- feature/module/library/UI area
- use cases
- invariants
- expected success outcomes
- expected failure outcomes
- required unit tests
- required integration tests
- required API E2E tests
- required UI logic tests
- required UI E2E tests
- current tests
- status

Deliverables:

- full requirement matrix
- route-to-test matrix
- module-to-test matrix
- UI-area-to-test matrix
- library/dependency integration matrix

## 6. Workstream 2: Structural Coverage Expansion

Current aggregate coverage is far below target, so this workstream needs a staged burn-down.

### Phase 2A: Coverage Baselining

Create scripts to extract and summarize per-module/per-package coverage:

- `products/data-cloud/scripts/report-test-coverage.sh`
- `products/data-cloud/scripts/report-requirement-coverage.sh`
- `products/data-cloud/scripts/report-ui-coverage.sh`

Add dashboards for:

- `platform-api`
- `platform-launcher`
- `platform-plugins`
- `feature-store-ingest`
- `platform-analytics`
- `platform-config`
- `platform-entity`
- `platform-event`
- `platform-client`
- `spi`
- `sdk`
- `launcher`
- `agent-registry`
- `data-cloud-cache`
- `platform`
- `api` where executable production logic exists
- `ui/src/api`
- `ui/src/lib/api`
- `ui/src/contracts`
- `ui/src/services`
- `ui/src/stores`
- `ui/src/lib`
- `ui/src/components`
- `ui/src/pages`

### Phase 2B: Module-by-Module Structural Targets

Raise structural coverage in this order:

1. `platform-api`
2. `platform-launcher`
3. `launcher`
4. `platform-analytics`
5. `platform-config`
6. `platform-event`
7. `platform-entity`
8. `platform-plugins`
9. `feature-store-ingest`
10. `agent-registry`
11. `platform-client`
12. `spi`
13. `data-cloud-cache`
14. `platform`
15. `sdk`
16. frontend libraries and pages
17. any remaining Java production package under `products/data-cloud/**/src/main/java`

Per area:

- enumerate uncovered classes and branches from coverage reports
- classify uncovered code as:
  - production path
  - dead code
  - generated or boilerplate
  - bootstrap glue
- remove dead code instead of testing it
- add missing tests for all surviving production paths

Structural gate rollout:

- Week 1: 35% line / 25% branch floor
- Week 2: 50% / 40%
- Week 3: 70% / 60%
- Week 4+: 85% / 75%
- Final: 100% / 100%

## 7. Workstream 3: Behavioral Coverage Expansion

This is the highest-value workstream. Each feature group below names the primary modules, libraries, and UI areas it must cover.

### Feature Group A: Entities

Primary areas:

- `platform-entity`
- `platform-api`
- `platform-launcher`
- `launcher`
- `ui/src/api/schema.service.ts`
- collection pages
- `EntityBrowserPage.tsx`

Required behaviors:

- create/query/get/delete
- search/export/anomalies
- single validate
- batch validate
- batch upsert/delete
- CDC stream
- query stream
- AI suggest
- tenant isolation
- sorting/filtering/pagination correctness
- no missing/extra rows
- optimistic versioning and invariant preservation

Needed additions:

- query correctness matrices for filters, sort, limit, offset
- negative tests for malformed filters and unsupported sort fields
- export format correctness tests
- anomaly result correctness tests with deterministic fixtures
- streaming snapshot and tail correctness tests
- tenant leakage negative tests on every entity route
- collections UI save/edit/validation/error tests

### Feature Group B: Events

Primary areas:

- `platform-event`
- `platform-api`
- `platform-launcher`
- `launcher`
- `ui/src/api/events.service.ts`
- `EventExplorerPage.tsx`
- `ui/src/lib/websocket`

Required behaviors:

- append
- query by offset/type
- strict ordering
- tenant isolation
- SSE event stream
- failure and retry behavior

Needed additions:

- exact offset progression tests
- duplicate and late event handling
- event stream resume from offset
- stream filtering by type
- durability and replay tests against real store
- event explorer UI query/filter/sort/error coverage

### Feature Group C: Pipelines

Primary areas:

- `platform-api`
- `platform-launcher`
- `launcher`
- `platform-client`
- `ui/src/lib/api/workflows.ts`
- `ui/src/lib/api/workflow-client.ts`
- `WorkflowsPage.tsx`
- `WorkflowDesigner`
- workflow store and workflow AI services

Required behaviors:

- list/save/get/update/delete
- optimization hint
- tenant isolation
- metadata integrity
- auditability

Needed additions:

- dedicated `DataCloudHttpServerPipelineTest`
- unit tests for pipeline validation/update rules
- DB-backed persistence tests
- delete and overwrite semantics
- not-found/conflict/invalid-body tests
- AI fallback behavior with confidence/reason assertions
- workflow designer UI and workflow list E2E coverage

### Feature Group D: Checkpoints

Primary areas:

- `platform-api`
- `platform-launcher`
- `launcher`
- `platform-client`
- workflow and execution UI flows

Required behaviors:

- list/save/get/delete
- tenant isolation
- metadata correctness
- persistence durability
- deletion auditability

Needed additions:

- real persistence tests
- duplicate checkpoint overwrite/update semantics
- invalid checkpoint schema tests
- retention/purge interactions if applicable

### Feature Group E: Memory

Primary areas:

- `platform-api`
- `platform-launcher`
- `platform-plugins`
- `launcher`
- `ui/src/api/memory.service.ts`
- `MemoryPlaneViewerPage.tsx`
- AI/memory components

Required behaviors:

- get all memory
- get by tier
- semantic search
- delete
- retain
- tier invariants
- tenant/agent isolation

Needed additions:

- semantic search ranking oracle tests
- tier transition and retention logic tests
- delete/retain persistence tests
- search precision/limit tests
- cross-agent leakage negatives
- memory plane UI browse/search/action tests

### Feature Group F: Brain

Primary areas:

- `platform-api`
- `platform-launcher`
- `launcher`
- `agent-registry`
- `ui/src/api/brain.service.ts`
- `ui/src/services/brain-unified.ts`
- `IntelligentHub.tsx`
- brain components

Required behaviors:

- health/config/stats
- workspace read
- workspace stream
- attention elevate
- threshold get/update
- pattern list/match
- salience get
- explain

Needed additions:

- workspace stream correctness tests
- threshold mutation persistence tests
- pattern matching correctness with deterministic fixtures
- salience invariants and threshold classification tests
- explain response reason/confidence tests
- intelligent hub and brain UI state coverage

### Feature Group G: Learning

Primary areas:

- `platform-launcher`
- `launcher`
- `platform-api`
- AI assistant and review UI flows

Required behaviors:

- trigger
- status
- review queue
- approve
- reject
- learning stream

Needed additions:

- stream tests
- state transition tests:
  - pending to approved
  - pending to rejected
  - invalid re-approval/re-rejection rejected
- review side effects persisted and audited
- partial failure and retry cases

### Feature Group H: Analytics and Reports

Primary areas:

- `platform-analytics`
- `platform-api`
- `platform-launcher`
- `data-cloud-cache`
- `launcher`
- `ui/src/api/analytics.service.ts`
- `ui/src/api/cost.service.ts`
- `SqlWorkspacePage.tsx`
- dashboard/dashboards
- `InsightsPage.tsx`
- dataset detail/insight pages

Required behaviors:

- submit query
- poll result
- plan retrieval
- aggregate query
- report generation
- report list
- report retrieval
- AI suggest

Needed additions:

- query correctness fixtures
- aggregation precision tests
- poll state transitions
- report cache lifecycle tests
- report retrieval by id
- tenant-scoped analytics/report visibility
- malformed query and unsupported plan failures
- SQL workspace and dashboard UI E2E coverage

### Feature Group I: Models

Primary areas:

- `platform-api`
- `platform-launcher`
- `launcher`
- AI/model management surfaces

Required behaviors:

- list/register/get/promote
- governance approval semantics
- version promotion invariants
- tenant isolation

Needed additions:

- brand-new endpoint tests
- unit tests for model state machine
- promotion invalid transition rejection
- audit side effects
- duplicate version and rollback tests

### Feature Group J: Features

Primary areas:

- `feature-store-ingest`
- `platform-api`
- `platform-launcher`
- `launcher`
- dataset explorer/detail pages

Required behaviors:

- ingest
- retrieve
- tenant/entity isolation
- schema validation
- feature freshness/overwrite semantics

Needed additions:

- brand-new endpoint tests
- integration tests with `feature-store-ingest`
- vector shape/typing validation
- overwrite vs append semantics
- stale data handling

### Feature Group K: Voice

Primary areas:

- `launcher`
- `platform-api`
- `platform-launcher`
- `ui/src/components/voice`
- realtime integration libraries

Required behaviors:

- intent execute
- intent classify
- intent list
- confidence/error handling
- parity with API behavior
- audit trail
- transcript retention policy

Needed additions:

- strict E2E assertions replacing permissive checks
- classify-only contract aligned with backend
- missing intent list endpoint tests
- transcript retention and audit persistence tests
- low-confidence fallback tests

### Feature Group L: Governance

Primary areas:

- `platform-launcher`
- `platform-plugins`
- `launcher`
- `ui/src/api/governance.service.ts`
- `TrustCenter.tsx`
- governance/security hub
- alerts/settings/trust pages

Required behaviors:

- retention classify
- retention policy get
- purge
- redact
- pii-fields list
- compliance summary
- audit and policy enforcement

Needed additions:

- persistence assertions for purge/redact
- dry-run vs live-run divergence tests
- destructive action confirmation enforcement
- compliance summary correctness
- rollback/partial failure tests
- trust center and governance UI coverage

### Feature Group M: Plugins, Agent Runtime, and Data Fabric

Primary areas:

- `spi`
- `platform-plugins`
- `agent-registry`
- `platform-client`
- `platform-config`
- `ui/src/api/plugin.service.ts`
- `ui/src/api/agent-registry.service.ts`
- data fabric admin pages
- plugin pages and agent/plugin manager pages

Required behaviors:

- plugin discovery/install/activate/deactivate/remove
- plugin capability declaration and contract validation
- plugin isolation and error containment
- storage profile CRUD
- data connector CRUD and sync job flows
- agent registry lookup and capability matching
- tenant-scoped plugin and connector visibility

Needed additions:

- plugin lifecycle behavior suites
- agent registry lookup and failure-path tests
- data fabric admin integration tests
- connector sync, retry, partial-failure, and rollback tests
- plugin UI and data fabric admin E2E coverage

### Feature Group N: Security, Access Control, Audit, Observability, and Realtime Infrastructure

Primary areas:

- `platform-config`
- `platform-launcher`
- `launcher`
- `data-cloud-cache`
- `platform`
- `ui/src/lib/auth`
- `ui/src/lib/performance`
- `ui/src/lib/accessibility`
- `ui/src/lib/integrations/realtime-integration.tsx`
- shell, settings, alerts, trust center, and notification UI

Required behaviors:

- authentication and token handling
- authorization and role-aware data visibility
- audit logging and retention
- health probes and observability metrics
- websocket and SSE connection lifecycle
- performance telemetry and graceful degradation
- accessibility invariants for critical product paths

Needed additions:

- access control matrix tests across API and UI
- audit persistence and retention tests
- health/metrics contract tests
- websocket and SSE resilience tests for reconnect/backpressure
- accessibility tests for all primary pages and dialogs
- performance regression thresholds for critical journeys

## 8. Workstream 4: Integration Realism Upgrade

Replace fake integration tests with real module wiring.

For `products/data-cloud/integration-tests/src/test/java/com/ghatana/datacloud/integration/CrossModuleIntegrationTest.java`:

- replace local contracts with real module services
- use actual repositories/adapters
- verify audit persistence, not just in-memory records

For `products/data-cloud/integration-tests/src/test/java/com/ghatana/datacloud/integration/EventWorkflowIntegrationTest.java`:

- replace mock publisher/consumer/store with real event module implementation
- prove ordering, filtering, replay, and tenant isolation against actual code

Add new integration suites:

- `PipelinePersistenceIntegrationTest`
- `CheckpointPersistenceIntegrationTest`
- `MemorySearchIntegrationTest`
- `ReportsIntegrationTest`
- `ModelRegistryIntegrationTest`
- `FeatureStoreIntegrationTest`
- `GovernanceRedactionIntegrationTest`
- `GovernancePurgeIntegrationTest`
- `SseStreamingIntegrationTest`
- `PluginLifecycleIntegrationTest`
- `DataFabricConnectorIntegrationTest`
- `AccessControlIntegrationTest`
- `AuditTrailIntegrationTest`
- `CacheConsistencyIntegrationTest`
- `SdkContractSmokeTest`

Integration realism must explicitly include:

- `spi` contract to implementation coverage
- `platform-client` to API contract coverage
- `data-cloud-cache` correctness under real analytics/report flows
- `platform-plugins` against real plugin lifecycle and storage backends
- frontend API wrappers against a real backend for primary journeys

## 9. Workstream 5: UI Contract Convergence

Fix the UI so tests validate the real product, not imagined APIs.

Required changes:

- remove mock endpoints in `products/data-cloud/ui/e2e/helpers/api-mocks.ts` that do not exist in canonical OpenAPI
- replace non-canonical `/api/v1/workflows` assumptions with canonical pipeline/checkpoint/memory/brain/report routes
- regenerate UI contract schemas from OpenAPI where possible
- make UI E2E fail if backend route is missing, not silently mock it
- align all page specs, frontend contracts, and page routes to one canonical source

UI areas that must be explicitly converged and covered:

- shell and routing
- dashboard and dashboards
- collections CRUD flows
- workflow list and workflow designer
- dataset explorer and dataset detail
- lineage explorer
- SQL workspace
- event explorer
- entity browser
- insights page
- memory plane viewer
- intelligent hub
- trust center
- settings page
- alerts page
- plugin pages and agent plugin manager
- all page-level not-found/error/loading/empty states

UI test strategy:

- contract tests:
  - validate canonical response schemas only
- logic tests:
  - local state, pagination, filters, retry UX, a11y
- E2E tests:
  - run against real backend for primary journeys
  - use mocks only for explicitly non-production edge cases

## 10. Workstream 6: CI Enforcement

Add mandatory gates.

### Gate A: Structural

- fail if any module or frontend area drops below target
- final target 100/100/100

### Gate B: Behavioral

Each requirement/use-case must list required tests.

CI verifies:

- every requirement has unit/integration/E2E mapping as appropriate
- every OpenAPI route has at least one contract test and one behavior test
- every destructive route has failure-path coverage

### Gate C: Route Coverage

Script parses `products/data-cloud/docs/openapi.yaml` and checks for matching test ids/names.

### Gate D: UI Contract Drift

- fail if UI contract tests mention non-canonical routes
- fail if UI E2E mock helpers define speculative production endpoints

### Gate E: Streaming Coverage

- fail if any declared SSE/WebSocket route lacks a dedicated test suite

### Gate F: Product Surface Completeness

- fail if any module or library listed in Sections 3.1 through 3.5 is absent from the requirement matrix
- fail if any UI/UX area listed in Section 3.4 is absent from the UI test matrix
- fail if any Java production directory under `products/data-cloud/**/src/main/java` is unclassified

## 11. Test Inventory to Build

Create or complete these suites:

- `DataCloudHttpServerPipelineTest`
- `DataCloudHttpServerReportsTest`
- `DataCloudHttpServerModelsTest`
- `DataCloudHttpServerFeaturesTest`
- `DataCloudHttpServerSseTest`
- `DataCloudHttpServerEntityStreamingTest`
- `DataCloudHttpServerLearningStreamTest`
- `DataCloudHttpServerBrainWorkspaceStreamTest`
- `PipelineServiceTest`
- `ModelRegistryServiceTest`
- `FeatureServiceTest`
- `GovernancePolicyEngineTest`
- `PurgeExecutorTest`
- `RedactionExecutorTest`
- `MemorySemanticSearchTest`
- `AnalyticsQueryCorrectnessTest`
- `ReportGenerationServiceTest`
- `PipelinePersistenceIntegrationTest`
- `ReportsIntegrationTest`
- `ModelRegistryIntegrationTest`
- `FeatureStoreIntegrationTest`
- `SseStreamingIntegrationTest`
- `PluginLifecycleIntegrationTest`
- `AgentRegistryContractTest`
- `DataFabricAdminContractTest`
- `DataFabricAdminE2ETest`
- `DashboardPageE2ETest`
- `DatasetExplorerContractTest`
- `DatasetDetailInsightsTest`
- `LineageExplorerE2ETest`
- `SqlWorkspaceE2ETest`
- `TrustCenterAccessibilityTest`
- `SettingsPageAccessControlTest`
- `AlertsPageBehaviorTest`
- `PluginManagerPageE2ETest`
- `EntityBrowserPageE2ETest`
- `EventExplorerPageE2ETest`
- `MemoryPlaneViewerPageE2ETest`
- `UiShellRoutingContractTest`
- `RealBackendVoiceFlowE2ETest`

## 12. Execution Order

1. Build the requirement/use-case/module/UI matrix
2. Add the full Section 3 inventory into matrix and CI inputs
3. Fix UI contract drift
4. Add missing route behavior tests for pipelines, reports, models, and features
5. Add missing streaming tests
6. Replace fake integration suites with real module interactions
7. Add governance, security, audit, and access-control persistence tests
8. Raise structural coverage module by module and frontend area by frontend area
9. Enforce CI gates
10. Remove dead/unreachable code until 100% structural is feasible
11. Verify every Section 3 row is mapped and tested
12. Final full-suite verification

## 13. Acceptance Checklist

- [ ] Every requirement in the matrix has tests
- [ ] Every use case has success + failure coverage
- [ ] Every route in OpenAPI has contract + behavior tests
- [ ] Every destructive flow has audit + rollback/failure tests
- [ ] Every streaming route has dedicated tests
- [ ] UI tests use only canonical routes
- [ ] No fake integration tests remain for critical paths
- [ ] JaCoCo shows 100% line/branch/method for all in-scope Java areas
- [ ] Frontend coverage reports show 100% line/branch/function for all in-scope TS areas
- [ ] Every Java Gradle module in Section 3.1 is represented in the coverage matrix
- [ ] Every additional Java library area in Section 3.2 is represented in the coverage matrix
- [ ] Every frontend library area in Section 3.3 is represented in the coverage matrix
- [ ] Every UI/UX area in Section 3.4 is represented in the test matrix
- [ ] Every external workspace dependency in Section 3.5 has integration assumptions tested where Data Cloud relies on it
- [ ] CI fails on any regression

## 14. Product Surface Verification Matrix

Use this matrix as the final completeness check before calling the test program complete.

| Product surface | Included in workstream(s) | Verified |
|---|---|---|
| Java Gradle modules in Section 3.1 | 1, 2, 3, 4, 6 | [ ] |
| Additional Java library areas in Section 3.2 | 1, 2, 3, 4, 6 | [ ] |
| Frontend libraries and package areas in Section 3.3 | 1, 2, 3, 5, 6 | [ ] |
| UI/UX page and workflow surfaces in Section 3.4 | 1, 3, 5, 6 | [ ] |
| External workspace UI dependencies in Section 3.5 | 1, 4, 5, 6 | [ ] |
| OpenAPI routes | 1, 3, 4, 5, 6 | [ ] |
| Vision/requirements/use cases | 1, 3, 6 | [ ] |
| Realtime, streaming, and resilience paths | 3, 4, 5, 6 | [ ] |
| Security, governance, audit, and observability paths | 3, 4, 5, 6 | [ ] |
| Accessibility and UX state coverage | 3, 5, 6 | [ ] |

## 15. Practical Recommendation

Do this in 4 milestones:

### Milestone 1: P0 correctness

- requirement matrix
- Section 3 inventory incorporation
- UI contract cleanup
- pipelines/reports/models/features tests
- governance destructive flow tests

### Milestone 2: P1 real interactions

- replace fake integrations
- SSE/streaming coverage
- plugin/data fabric coverage
- tenant isolation negatives everywhere

### Milestone 3: P2 structural closure

- module-by-module uncovered code burn-down
- frontend-by-frontend uncovered code burn-down
- dead code removal
- branch completion

### Milestone 4: P3 hard enforcement

- CI gates
- route coverage scripts
- requirement coverage scripts
- UI surface completeness scripts
- release blocking on regressions

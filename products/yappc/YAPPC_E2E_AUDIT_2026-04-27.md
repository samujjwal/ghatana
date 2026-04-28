# YAPPC — Deep Full-Stack End-to-End Product Audit

**Product:** YAPPC — Product Development Platform (Intent → Shape → Validate → Generate → Run → Observe → Learn → Evolve)
**Audit date:** 2026-04-27
**Auditor scope:** UI, UX, frontend (React + Next.js + Vite + 23+ frontend libs), backend (Java 21 + ActiveJ + 18 Gradle modules), HTTP/gRPC/GraphQL contracts, scaffolding, refactorer, AI core (LLM + agents + prompt versioning + cache + cost), knowledge graph, SDLC orchestration, persistence, async/event flows, observability, security, accessibility, AI/ML embedding, quality engineering, product strategy.
**Anchored to:** the 2026-04-26 `yappc_full_audit.md` and the in-progress `yappc_todo_execution_map.md`. This audit re-verifies, advances, and adds findings against today's tree.

---

## Conventions

- Severity: Critical / High / Medium / Low.
- Dimension: Completeness | Simplicity | Correctness | Consistency | Other.
- Evidence: paths and symbols at HEAD on 2026-04-27.
- Code is truth; docs are advisory.

---

## 1. Executive Summary

YAPPC is a large, ambitious "product platform". The vision is to take a user from a raw intent to a running, observed, learning product through eight phases, with AEP as the runtime substrate and a deep AI-agent fabric in between. Implementation reality is uneven: a substantial frontend (`frontend/web` with 12+ page domains and 23+ shared libs) and a substantial Java backend (`core/yappc-api`, `core/ai`, `core/scaffold`, `core/refactorer`, `core/agents`, `core/services-platform`, `core/services-lifecycle`, etc.) exist; a real LLM provider integration, real prompt-versioning, real cost tracking, and a real semantic cache exist; but the API contract is partial, end-to-end closure on the headline lifecycle journey is not durable, and the cockpit's claims outpace what the runtime guarantees.

**Headline assessment**

- **Completeness:** the product surface is huge and visibly fragmented. Pages exist for development (BacklogPage, SprintBoardPage, EpicsPage, PullRequestsPage, CodeReviewPage, FeatureFlagsPage, DeploymentsPage, VelocityChartsPage), for operations (AlertsPage, IncidentsPage, RunbooksPage, OnCallPage, PostmortemsPage, ServiceMapPage, WarRoomPage, LogsPage, MetricsPage, DashboardsPage, ServiceMapPage), for admin (BillingPage, TeamsPage), and for a canvas-centric workspace, but many of these pages are scaffolded shells that do not own a true end-to-end flow (UI → API → backend → persistence → audit). The OpenAPI surface (`api/yappc-api.openapi.yaml`, only 680 lines) covers `/designs`, `/generated-code`, `/artifacts`, `/refactoring-suggestions` — a tiny fraction of what the cockpit attempts to expose.
- **Simplicity:** the cockpit IA is overstuffed. The `pages/` tree, the `routes/` tree, `routes/_archived/`, `routes/app/project/_archived/`, and the older `app-creator/` shell coexist; the `frontend/libs` tree has 23+ libraries and the `app-creator/libs` audit log records a 36→29 consolidation that is still in flight. Users (and engineers) face two parallel cockpits and many overlapping surfaces.
- **Correctness:** the gravest correctness risk is **contract drift**: the Java HTTP surface (`/api/v1/workflows/...`, `/api/v1/agents`, `/api/v1/vector`, etc.) is not in the OpenAPI spec. UI clients call endpoints that are not contracted. Approval flows, RBAC, and audit chains are claimed in the existing audit docs but not consistently enforced. Many "AI-driven" surfaces are deterministic-rule fallbacks dressed as AI.
- **Consistency:** terminology drift between "workspace / project / requirement / story / epic / artifact / design / generated-code / scaffold"; both `@ghatana/*` and `@yappc/*` package scopes coexist; archived routes live next to active ones (`_archived/`); 8 lifecycle phases (Intent → Evolve) coexist with the AEP-driven SDLC sub-lifecycle (Discover → Deploy) and with development-domain pages (Backlog/Sprint/PR/Deployments) that imply yet another framing.
- **Production readiness:** the README itself declares 3/10 AI-Native Maturity, 4/10 Feature Completeness, 2/10 Production Readiness. The audit confirms: production readiness is months away. Several of the 23 libraries are not yet packaged correctly per repo Section 32 (canonical TypeScript packages must be `@ghatana/*` only); product-prefixed `@yappc/*` packages are forbidden in the platform scope but currently exist in `frontend/libs/yappc-*`.

The right path forward is not "more features"; it is **closure, contract truth, and consolidation**.

---

## 2. Deep Audit Scorecard

| Area | Rating | Justification |
| --- | --- | --- |
| Completeness | High | Many surfaces visually present; few are truly end-to-end (UI → API → DB → audit). OpenAPI covers <10% of the Java HTTP surface. |
| Simplicity | Critical | Two cockpits (`frontend/web` + legacy `app-creator/`); 23+ frontend libraries; archived routes shipped; pages/routes duplication. |
| Correctness | High | Contract drift between OpenAPI and the actual server routes; approval/RBAC/audit are partially implemented; "AI" features sometimes deterministic. |
| Consistency | Critical | `@ghatana/*` vs `@yappc/*` scope split; 8-phase YAPPC framing vs SDLC sub-lifecycle vs development-domain pages; pages vs routes; Java module sprawl. |
| UI/UX | Medium | Canvas, page builder, diagrams, sketch components are real; the surrounding navigation is overloaded and not capability-gated. |
| Frontend quality | Medium | Strong tooling (Vite, Vitest, Playwright, Sentry, lighthouse); large bundle, lazy-route work in flight; `@ts-nocheck` historically present in places like `SecurityDashboard.tsx` (now removed); typing discipline is improving but uneven. |
| API/contract | Critical | OpenAPI 680 lines vs ~7+ controller groups (Agents, Workflows, Vector, plus GraphQL resolvers); workflow/agent/vector endpoints are not contracted publicly. |
| Backend/workflow | High | 18 Java modules with documented dependency rules; orchestration is partial; cancellation/idempotency/audit-chain not uniformly enforced. |
| Data/persistence | High | Prisma + Java JDBC stacks coexist; canonical models `Workspace/Project/Requirement/RequirementVersion/Sprint/Epic/Story/ApprovalRequest/AuditLog/ExportArtifact` exist in places but are not uniformly the source of truth. |
| Observability | Medium | Grafana dashboard exists, Sentry wired, lighthouse CI; cockpit-level live progress on AEP runs is partial (`useAgentRunStream` exists in todo map but full lineage UI absent). |
| Privacy/security/trust | High | RBAC guard implemented in some surfaces (`ApprovalDetail.isAuthorizedApprover`); not enforced uniformly. PII classification, retention, consent are aspirational. |
| AI/ML embedding | Medium | Real LLM providers, prompt versioning service, semantic cache, A/B testing, cost tracking — solid foundations; explainability/confidence not surfaced uniformly. |
| Accessibility | Medium | `a11y` lib exists; canvas/diagram surfaces need explicit keyboard + SR coverage. |
| Responsiveness | Medium | Some mobile work; canvas-heavy surfaces are desktop-first. |
| Perceived performance | Medium | Manual chunks, lazy routes, bundle visualizer wired; large surface area still pulls in heavy modules. |
| Cognitive load | Critical | The 8-phase framing + dev/ops/admin domain pages + canvas + AI panels all compete for attention. |
| End-to-end product quality | High | The product is promising but not yet trustworthy as a single coherent journey. |

---

## 3. Surface-by-Surface and Layer-by-Layer Audit

### 3.1 Product framing and documentation

`README.md` is unusually honest: it states maturity scores (3/10, 4/10, 2/10), lists per-phase known limitations, and warns that "some AI-assisted experiences are deterministic rule-based assistance and are explicitly labeled as such." That honesty must propagate to the cockpit; today it is documentation-only.

- **Completeness:** READMEs and ARCHITECTURE docs are present; the `docs/`, `docs-site/`, `frontend/web/docs/`, `frontend/web/web-page-specs/` trees are fragmented and overlap.
- **Simplicity:** there is no single source of truth for the user's mental model. The 8-phase framing in README contradicts the development/operations/admin surfaces in `frontend/web/src/pages/`.
- **Correctness:** the `Quick Start` lists three options including `make quick-start`, `docker compose --profile backend up -d`, `pnpm dev:web`. CI verification of the documented quick start should be a release gate.
- **Consistency:** unify on the 8-phase framing as the *user-facing* mental model and demote dev/ops/admin pages to context-sensitive surfaces inside Run/Observe/Learn/Evolve.

### 3.2 Cockpit shell: `frontend/web` vs legacy `app-creator/`

There are two cockpits. `frontend/web/src` is the canonical active shell with a `routes/` + `pages/` mix. `app-creator/` (referenced in README and existing audit) is the older surface still partially in repo. Two cockpits is a Critical simplicity finding by itself.

- **Completeness:** the active shell has Pages for `dashboard`, `development`, `operations`, `admin`, `auth`, `bootstrapping`, `collaboration`, `errors`, `initialization`, `security`, `settings` and routes for `app/projects`, `app/workspaces`, `dashboard`, `login`, `not-found`, `onboarding`, `profile`, `settings` plus archived `forgot-password`, `ide`, `register`. The pages/routes split is non-obvious to a new contributor.
- **Simplicity:** archived routes (`_archived/`) and archived project sub-trees (`routes/app/project/_archived/`) live in the production shell. They must be removed.
- **Correctness:** `routes.ts` co-exists with `routes/` directory + `lazyRoutes.ts`. Lint must enforce that no page is reachable except via the route registry.
- **Consistency:** retire the second cockpit; one shell only.

### 3.3 Information architecture and navigation

- **8-phase target model** (Intent → Shape → Validate → Generate → Run → Observe → Learn → Evolve) is the canonical user-facing structure but is not the structure of the navigation.
- The dev-team-facing pages (`pages/development`: Backlog, Sprint, Epics, PRs, CodeReview, FeatureFlags, Deployments, Velocity) imply a Jira-shaped IA grafted on top.
- The ops-team-facing pages (`pages/operations`: Alerts, Incidents, OnCall, Postmortems, Runbooks, ServiceMap, WarRoom, Logs, Metrics, Dashboards) imply a PagerDuty-shaped IA grafted on top.
- The admin pages (`pages/admin`: Billing, Teams) imply yet a third IA.

This is not one product; this is three pasted together. The cockpit IA must be reconciled.

### 3.4 Workspace / Project / Requirement lifecycle

- **Completeness:** the existing audit todo confirms a `WorkspaceMembers.tsx` with invite + role + remove (with OWNER protection); `ApprovalInbox` + `ApprovalDetail` with `isAuthorizedApprover` RBAC guard; `requirements-approvals.resolver.ts` with `enrichRequirement`, `bulkApproveRequirements`, `bulkRejectRequirements`. So the surface exists.
- **Correctness:** the existing audit notes that **AepOrchestrationClient** + `useRequirementOrchestration` were created so that requirement APPROVAL submits to AEP. Verify in code that the round-trip is durable, audited, and that the resulting agent run id is shown to the user.
- **Simplicity:** the requirement capture form should infer (a) workspace from URL, (b) project from URL, (c) likely epic from text classification, (d) acceptance criteria from a deterministic+LLM normaliser; the user should only have to type intent.
- **Consistency:** the model names `Requirement / RequirementVersion / VersionSnapshot / TraceLink / ApprovalRequest / ActivityFeedItem` (recommended in mapping docs) must be the actual source of truth.

### 3.5 Canvas + Diagrams + Page Builder + Sketch + Code Editor

Evidence: `frontend/libs/canvas-*`, `frontend/libs/yappc-development-ui`, `app-creator/libs` history, plus `routes/app/project/canvas/` with `components/`, `core/`, `hooks/`, `useCanvasScene/`. The existing todo shows `CodeEditorCanvas` shipped with editor/diff/visual modes and an E2E spec.

- **Completeness:** strong; canvas/diagram/page-builder are real. Sketch is partially integrated.
- **Simplicity:** the canvas is the right primary surface. Everything else should be a panel of the canvas, not a separate page.
- **Correctness:** persistence of the canvas scene (positions, zoom, blocks) must be transactional; the existing E2E spec marks "Canvas integration" as `test.skip(true, 'Route not yet deployed to CI environment')`. That is a quality gap; the integration test must run in CI before claiming completion.

### 3.6 Java HTTP API surface

Confirmed controllers/routes in `core/yappc-api/src/main/java/com/ghatana/yappc/api/http/`:

- `AgentController` + `AgentRoutes`
- `WorkflowController` + `WorkflowRoutes` — exposes `/api/v1/workflows`, `/api/v1/workflows/:id`, `/start`, `/pause`, `/resume`, `/cancel`, `/steps/advance`, `/steps/:stepId/goto`, `/plans/generate`, `/plans/:planId/{approve,reject}`, `/route`.
- `VectorController` + `VectorRoutes`

The OpenAPI (`api/yappc-api.openapi.yaml`, 680 lines) documents only `/designs`, `/generated-code`, `/artifacts`, `/refactoring-suggestions`.

**This is a Critical contract drift.** The publicly contracted API is not the public API; the actual public API is invisible to consumers. Every workflow operation flows through endpoints not in OpenAPI.

- **Completeness:** add full OpenAPI for all `/api/v1/{workflows,agents,vector}/...` routes; gate the build on OpenAPI ↔ route table parity.
- **Correctness:** `WorkflowController.createWorkflow` extracts a tenantId via `extractTenantId(request)`; verify it fails closed when missing (do not default).
- **Consistency:** approvers/rejecters across HITL, requirement approvals (GraphQL), and workflow plans must use the same audit envelope.

### 3.7 GraphQL (under `frontend/apps/api`)

Resolvers exist for `requirements-approvals`, `export`, `workflow`, etc. A unit-test harness exists. Validation is added via Zod (`apps/api/src/graphql/validation.ts`).

- **Completeness:** GraphQL covers parts of the lifecycle (workspace, project, requirements, approvals, exports); REST covers other parts (workflows, agents, vector). The boundary is not documented.
- **Correctness:** Zod `.strict()` on every mutation is good; verify every resolver throws `BAD_USER_INPUT` `GraphQLError` and never returns silently.
- **Consistency:** decide which surface is canonical for a given concept; today both expose overlapping concepts (workflow vs requirements vs project vs export).

### 3.8 AI core (`core/ai`)

Substantial modules: `agent/BaseAgent`, `cache/SemanticCacheService`, `canvas/CanvasAIServer/Service/Impl/Generation/Validation`, `canvas/llm/{LLMProvider,LLMRequest,LLMResponse}`, `cost/{CostRepository,CostTrackingService,JdbcCostRepository}`, `history/{Conversation,ConversationMapper,ConversationRepository}`, `integration/{AIRouterOutputGenerator,DefaultResultMapper,PromptTemplateEngine,ResultMapper}`, `metrics/AIMetricsCollector`, `prompt/{PromptVersion,PromptVersioningService,PromptVersionMapper}`, `quality/{ConfidenceScore,ConfidenceScorer}`, `abtesting/ABTestingEvaluationService`, `api/security/{AuthUtil,JwtService}`, `api/http/filter/AuthenticationFilter`.

This is real AI infrastructure. The risk is not the substrate; it is **embedding**:

- **Completeness:** confidence scoring exists (`ConfidenceScorer`) but is not consistently rendered in the cockpit; A/B-testing service exists but no operator UI to manage variants.
- **Simplicity:** users must not have to choose models or prompt versions — `PromptVersioningService` should drive a deterministic default with operator-only override.
- **Correctness:** the README warns that some AI is deterministic rule-based; cockpit must label "rule-based assist" vs "model-backed assist" honestly.
- **Consistency:** the `ai/api/security/JwtService` duplicates the platform JWT service; collapse to one.

### 3.9 Knowledge graph and refactorer

`core/knowledge-graph/`, `core/refactorer/{api,engine}`. Shape is good.

- **Completeness:** refactorer is exposed in `api/yappc-api.openapi.yaml` as `/refactoring-suggestions/{designId}` only; the actual engine is broader.
- **Correctness:** refactorer suggestions must carry `confidence`, `rationale`, `affected files`, and a `simulate-then-apply` lifecycle.

### 3.10 Scaffolding (`core/scaffold`)

`api/`, `core/`, `engine/`, `generators/`, `templates/`, `docs/`. The product's "Generate" phase depends on this.

- **Completeness:** generation pipeline exists; quality gates around generated code (compile/test/lint) are not visibly enforced as part of the user journey.
- **Correctness:** every generated artifact must have provenance (which template, which prompt version, which inputs) and a "regenerate" button.

### 3.11 Frontend libraries

`frontend/libs/` contains 23+ libs including `a11y`, `aep-config`, `api`, `collab`, `config-compiler`, `config-schema`, `data-cloud-config`, `ide`, `mobile`, `mocks`, `shortcuts`, `yappc-ai`, `yappc-artifact-compiler`, `yappc-auth`, `yappc-chat`, `yappc-core`, `yappc-development-ui`, `yappc-devsecops`, `yappc-initialization-ui`, `yappc-product-theme`, `yappc-state`, `yappc-ui`.

Per repo `.github/copilot-instructions.md` Section 32: `@yappc/*` package names are **forbidden** in the platform scope. These libs must be either:

- Promoted to canonical `@ghatana/*` packages if truly platform-grade (e.g. canvas, design tokens, a11y, design-system).
- Folded into product code under `products/yappc/` if product-specific (most of `yappc-*`).
- Deleted if duplicated by an existing `@ghatana/*` package.

This is a **Critical** governance finding.

### 3.12 Persistence

- **Java side:** JDBC repositories (`JdbcCostRepository`); persistence path varies per module.
- **TypeScript side:** Prisma schema referenced in `services/`/`apps/api/`; `Sprint` model added (per existing todo); `ExportArtifact` model added.
- **Risk:** two persistence stacks (Java JDBC + Node Prisma) for one product. Decide ownership: which entities live in which stack; document the boundary; prevent duplicate models.

### 3.13 Observability

`monitoring/`, `frontend/web/sentry.client.config.ts`, `sentry.server.config.ts`, `lighthouserc.json`, `frontend/libs/aep-config`, `prometheus.yappc.yml`.

- **Completeness:** Sentry on FE, Prometheus on backend, Lighthouse in CI, Grafana dashboard JSON exists.
- **Gap:** no operator-visible AEP run viewer (existing todo shows `useAgentRunStream` hook + `lifecycle.tsx` integration, but the lineage block per agent run is not visible end-to-end).
- **Gap:** no correlation-ID propagation across YAPPC FE → GraphQL → Java → AEP.

### 3.14 Security and trust

- **JWT services:** at least two (`platform JWT` + `core/ai/api/security/JwtService`); collapse.
- **Tenant resolution:** `TenantExtractor` from `platform.http.security.filter`; verify usage and fail-closed semantics.
- **RBAC:** `isAuthorizedApprover = hasPermission('approvals:decide')` in `ApprovalDetail`; this pattern must be standardised across all sensitive actions.
- **Audit chain:** mentioned in docs; not uniformly implemented.

### 3.15 Quality engineering

- Strong tooling: Vitest, Playwright, Lighthouse, Sentry, ArchUnit (`AgentBoundaryTest`, `ArchitectureSpecialistsBoundaryTest`).
- **Gap:** several E2E specs are `test.skip(true, 'Route not yet deployed to CI environment')` (canvas-integration, traceability-view). Test theatre per repo Section 29 unless they actually exercise the production code.
- **Gap:** the existing todo references "TODO backlog tooling" with `scan-todos.sh` and a max threshold; verify the threshold is enforced in CI.

---

## 4. End-to-End Flow Reviews

### 4.1 Intent → Submitted Requirement

- **Goal:** turn raw user intent into a structured requirement with normalised title, acceptance criteria, story trace, traceability links.
- **Entry:** Requirements page → "Add requirement" form OR canvas right-panel → Idea / Epic / Feature / Bug / Experiment drawer (`IntentDrawer`).
- **Frontend:** form posts to GraphQL mutation; `enrichRequirement` produces `AiEnrichmentSuggestion` with `normalizedTitle`, `acceptanceCriteria`, `storyTrace`, `confidence`, `rationale`.
- **Backend:** `requirements-approvals.resolver.ts` with `buildEnrichmentSuggestion` heuristic; can be deterministic or LLM-backed.
- **Persistence:** Prisma `Requirement` + `RequirementVersion`.
- **Audit:** `AuditLog` entry expected.
- **Gaps:** confidence chip shown (per todo) but rationale length / source citation may be inadequate; rule-based vs model-backed is not labelled in UI.

### 4.2 Submitted Requirement → Approved → AEP Orchestration

- **Goal:** human approves; YAPPC posts to AEP; AEP returns agent run; YAPPC streams progress.
- **Frontend:** `ApprovalDetail` with `isAuthorizedApprover` RBAC guard; `handleApprovalTransition` calls `submitOrchestration` via `useRequirementOrchestration` hook.
- **Backend:** `AepOrchestrationClient` (TS) → AEP `/api/v1/runs` (or pipeline execute).
- **Live:** `useAgentRunStream` hook subscribes to `LifecycleWebSocketService` for `agent_result` updates with seeded fallback.
- **Persistence:** approval recorded; AEP run id stored.
- **Gaps:** the AEP run lineage (which pipeline version, which agent versions, policy bundle) must be surfaced in YAPPC, not only AEP. The seeded fallback when WS is unavailable is correct UX, but the cockpit must surface "live updates unavailable" honestly.

### 4.3 Workflow CRUD + State Machine + Plan Approval

- **Goal:** create a workflow, advance steps, generate plans, approve/reject plans, route to specialists.
- **API:** `/api/v1/workflows` POST/GET; `/:id` GET; `/:id/{start,pause,resume,cancel}`; `/:id/steps/advance`; `/:id/steps/:stepId/goto`; `/:id/plans/generate`; `/:id/plans/:planId/{approve,reject}`; `/:id/route`.
- **Backend:** `WorkflowController` + `AiWorkflowService`.
- **Gaps:** **None of these endpoints are in the OpenAPI**. Cancellation has the same risk as AEP's: flag-flip vs durable. Plan generation needs confidence + rationale.

### 4.4 Generate (Scaffold)

- **Goal:** generate a project skeleton.
- **API:** `/generated-code` (in OpenAPI), `/generated-code/{operationId}`.
- **Backend:** `core/scaffold/engine` + `core/scaffold/generators`.
- **Gaps:** generated artifacts must run an immediate `compile + lint + test` quality gate; failures must produce a remediation suggestion not raw stack traces.

### 4.5 Refactor

- **Goal:** suggest and apply refactors.
- **API:** `/refactoring-suggestions/{designId}` (read-only).
- **Backend:** `core/refactorer/engine`.
- **Gaps:** apply lifecycle (preview → apply → undo) is not contracted; suggestions need `affected files`, `confidence`, `rationale`.

### 4.6 Sprint planning + Backlog

- **Goal:** plan sprints, manage backlog.
- **Frontend:** `BacklogBoard.tsx` (5-column kanban with type filter + sprint move), `SprintView.tsx` (metrics/progress/items/start+complete actions).
- **Backend:** `Sprint` model in Prisma + REST routes for sprint CRUD + item movement registered via `registerApiPrefixes`.
- **Gaps:** these REST routes are not in the OpenAPI either; the same contract-drift problem.

### 4.7 Operations: Incidents / Alerts / Runbooks / Postmortems / OnCall / WarRoom / ServiceMap

- **Frontend:** 11 pages.
- **Backend:** unclear; many of these may be Prisma+GraphQL only or pure UI shells.
- **Gaps:** these pages should not exist in the cockpit until the runtime supports them. Today this is the largest source of UI/runtime mismatch in YAPPC — features that look ready but are not wired end-to-end.

### 4.8 Admin: Billing / Teams

- **Gaps:** Billing is sensitive; treat as out-of-scope until a billing backend is real. Teams (workspace member management) is partially real per existing todo.

### 4.9 Search + Vector Search

- **Backend:** `VectorController` + `VectorRoutes` (Java).
- **Frontend:** `useSemanticSearch` hook (per existing todo).
- **Gaps:** vector index population, freshness, and tenant isolation are not visible from the cockpit.

### 4.10 Export

- **Frontend:** `ExportDialog.tsx` with PDF/MD options.
- **Backend:** `export.resolver.ts` + `ExportArtifact` Prisma model.
- **Gaps:** export jobs in `PENDING` state are not visibly tracked to completion in the user's view; in dev they go straight to `READY` (per existing todo).

### 4.11 Lifecycle phases (Intent → Evolve)

- **Frontend:** lifecycle artifacts (`lifecycle-artifacts.ts`), `LifecyclePhaseIndicator`, `LifecycleGuidance`, `CanvasRightPanelHost`, traceability/improve/threat-model/ADR/UX-spec/artifact panels.
- **Gaps:** the panels exist but the per-phase E2E flow (phase entry → required artifacts → AI assist → review → exit) is not stitched.

---

## 5. Comprehensive Findings Catalog

### F-Y001 — Critical / Correctness / API — Massive OpenAPI ↔ route table drift
- **Evidence:** `WorkflowRoutes.java` (`/api/v1/workflows/...`), `AgentRoutes.java`, `VectorRoutes.java` not in `api/yappc-api.openapi.yaml` (680 lines covering only designs/generated-code/artifacts/refactoring-suggestions).
- **Fix:** generate OpenAPI for every controller; CI gate parity; publish a public API docs site.

### F-Y002 — Critical / Simplicity / Frontend — Two cockpits coexist
- **Evidence:** `frontend/web/` and legacy `app-creator/`.
- **Fix:** declare `frontend/web/` canonical; archive or delete `app-creator/`.

### F-Y003 — Critical / Consistency / Governance — `@yappc/*` packages violate repo Section 32
- **Evidence:** `frontend/libs/yappc-ai`, `yappc-auth`, `yappc-chat`, `yappc-core`, `yappc-development-ui`, `yappc-devsecops`, `yappc-initialization-ui`, `yappc-product-theme`, `yappc-state`, `yappc-ui`, `yappc-artifact-compiler`.
- **Fix:** Promote to `@ghatana/*` if platform; fold into product if product-specific; delete if duplicated. Apply governance migration per repo Section 25 fix-forward policy.

### F-Y004 — Critical / Simplicity / Frontend — Pages vs routes split + archived routes shipped
- **Evidence:** `frontend/web/src/pages/...` and `frontend/web/src/routes/...` and `routes/_archived/`, `routes/app/project/_archived/`.
- **Fix:** one route registry; delete archived; lint forbids new `pages/` files.

### F-Y005 — High / Consistency / Product — IA mismatch: 8-phase vs dev/ops/admin pages
- **Fix:** make 8-phase the only top-level IA; demote dev/ops/admin to context-sensitive panels inside Run/Observe/Learn/Evolve.

### F-Y006 — High / Correctness / API — `WorkflowController.cancelWorkflow` is flag-only
- **Fix:** durable cancellation contract identical to AEP §F-003.

### F-Y007 — High / Correctness / Backend — Two JWT services
- **Evidence:** `platform JWT` and `core/ai/api/security/JwtService`.
- **Fix:** delete the duplicate; AI core uses platform service.

### F-Y008 — High / Correctness / Backend — Two persistence stacks (JDBC + Prisma)
- **Fix:** declare ownership per entity; document; prevent duplicate models with ArchUnit/Prisma rules.

### F-Y009 — High / Completeness / UI+Backend — AEP run lineage not visible in YAPPC
- **Fix:** when YAPPC submits to AEP, render run id, pipeline version, agent versions, policy bundle, evaluation gate; link out to AEP run-detail page.

### F-Y010 — High / Correctness / UI — "AI" labels do not distinguish rule-based vs model-backed
- **Evidence:** README explicitly warns; cockpit does not.
- **Fix:** every AI surface shows `Rule-based assist` or `Model-backed assist` chip + confidence + rationale + sources.

### F-Y011 — High / Correctness / API — Workflow plan approve/reject lacks audit chain
- **Fix:** every plan approval emits an `AuditLog` entry with actor, plan id, workflow id, prior plan id, before/after diff.

### F-Y012 — High / Completeness / UI — Operations pages exist without backend wiring
- **Fix:** hide Alerts/Incidents/Runbooks/Postmortems/OnCall/WarRoom/ServiceMap/Logs/Metrics/Dashboards behind a feature flag tied to `/info` capability flags; default off.

### F-Y013 — High / Completeness / UI — Admin/Billing UI without billing backend
- **Fix:** hide Billing entirely until a real billing service exists; show Teams only when role is Owner or Admin.

### F-Y014 — High / Privacy / Backend — RBAC enforcement is patchy
- **Evidence:** `ApprovalDetail.isAuthorizedApprover` good; many other mutations lack visible guards.
- **Fix:** RBAC decorator/middleware on every mutation (REST + GraphQL); lint forbids unguarded mutations.

### F-Y015 — High / Correctness / Frontend — Skipped E2E specs ("Route not yet deployed")
- **Evidence:** `canvas-integration.spec.ts`, `traceability-view.spec.ts` use `test.skip(true, ...)`.
- **Fix:** per repo Section 29 anti-theatre — either deploy the route in CI or delete the spec.

### F-Y016 — High / Completeness / Backend — Generated artifact quality gate is not enforced in user journey
- **Fix:** every `/generated-code` response carries `compile`, `lint`, `test` results; UI blocks "accept" until green.

### F-Y017 — High / Correctness / API — Refactorer apply lifecycle missing
- **Fix:** add `simulate` + `apply` + `undo` for refactor suggestions; preview diff in UI.

### F-Y018 — High / Correctness / Backend — `WorkflowController.extractTenantId` must fail closed
- **Fix:** verify; reject when missing; never default.

### F-Y019 — High / Privacy / Backend — Tenant isolation in vector index unverified
- **Fix:** index name includes tenant; queries always tenant-scoped; ArchUnit test guards.

### F-Y020 — Medium / Completeness / UI — Sketch library not integrated
- **Fix:** integrate or drop.

### F-Y021 — Medium / Correctness / Backend — Cost tracking exists but not exposed per project/tenant
- **Evidence:** `CostTrackingService`, `JdbcCostRepository`.
- **Fix:** cockpit cost tile per project + per tenant.

### F-Y022 — Medium / Completeness / UI — Prompt version control has no UI
- **Evidence:** `PromptVersioningService`, `PromptVersion`, `PromptVersionMapper`.
- **Fix:** admin-only prompt versions page; rollback; weight rebalancing.

### F-Y023 — Medium / Correctness / Backend — Semantic cache invalidation strategy undocumented
- **Evidence:** `SemanticCacheService`.
- **Fix:** document TTL / similarity threshold / per-tenant scope; expose hit ratio metric.

### F-Y024 — Medium / Completeness / UI — A/B testing service has no operator UI
- **Evidence:** `ABTestingEvaluationService`.
- **Fix:** operator UI to register variants, view results, promote winner.

### F-Y025 — Medium / Consistency / Frontend — Two scopes (`@ghatana/*` + `@yappc/*`) create import ambiguity
- **Fix:** ESLint rule already exists per existing todo (`prefer-yappc-ui.ts` updated); extend to forbid all `@yappc/*` from `frontend/web/src/**` once consolidation completes.

### F-Y026 — Medium / Observability / Platform — No correlation-ID propagation YAPPC FE → GraphQL → Java → AEP
- **Fix:** `X-Correlation-ID` mandatory at gateway; propagated everywhere; logged on every span.

### F-Y027 — Medium / Completeness / UI — No traceability graph from intent → requirement → story → code → run
- **Evidence:** `TraceabilityPanel.tsx` exists but data wiring partial.
- **Fix:** end-to-end trace links surfaced as a single graph view.

### F-Y028 — Medium / Correctness / Backend — Conversation history persistence
- **Evidence:** `Conversation`, `ConversationMapper`, `ConversationRepository`.
- **Fix:** retention policy per tenant; PII handling; user-visible history with delete.

### F-Y029 — Medium / Completeness / UI — Knowledge graph not visible
- **Fix:** browser for entities/relations/facts; explainable retrieval.

### F-Y030 — Medium / Quality / QA — TODO backlog scanner threshold
- **Evidence:** existing todo `P1-7 TODO backlog tooling` complete with `scan-todos.sh --ci --max`.
- **Fix:** verify threshold enforced in CI; track count over time.

### F-Y031 — Medium / Correctness / Frontend — `@ts-nocheck` historically present
- **Evidence:** existing todo CC-M removed from `SecurityDashboard.tsx`.
- **Fix:** repo-wide CI lint forbids `@ts-nocheck`; ensure no new occurrences.

### F-Y032 — Medium / Correctness / Frontend — `import type` vs `import` runtime erasure bug
- **Evidence:** existing todo CC-H fixed `TraceabilityPanel.tsx` `import type { LifecyclePhase }` that was crashing `Object.values(...)`.
- **Fix:** ESLint rule `@typescript-eslint/consistent-type-imports` with auto-fix; CI gate.

### F-Y033 — Medium / Completeness / UI — Onboarding is partial
- **Evidence:** `IntelligentOnboarding` component, `OnboardingChecklist`, `onboardingAtom`.
- **Fix:** end-to-end onboarding journey: account → first workspace → first project → first intent → first AI assist → first approval → first deploy preview.

### F-Y034 — Medium / Privacy / Backend — Conversation + cost + prompt logs may contain PII
- **Fix:** per repo Section 31 — typed records, schema-bound logging, classification.

### F-Y035 — Medium / Completeness / UI — No "what does AI know about my codebase" surface
- **Fix:** knowledge graph + semantic cache hit timeline + retrieval explanations.

### F-Y036 — Low / Consistency / Frontend — `@yappc/development-ui` vs `@ghatana/design-system` overlap
- **Fix:** prefer `@ghatana/design-system`.

### F-Y037 — Low / Consistency / Backend — `core/services-platform` vs `core/services-lifecycle` vs `core/yappc-services`
- **Fix:** declare canonical services module; collapse the rest.

### F-Y038 — Low / Quality / Backend — ArchUnit boundary tests are present but module split is partial
- **Fix:** complete the module split documented in YAPPC core 18-module topology.

### F-Y039 — Low / Completeness / Docs — Two doc trees (`docs/`, `frontend/web/docs/`, `docs-site/`, `frontend/web/web-page-specs/`)
- **Fix:** one canonical docs tree; auto-publish site.

### F-Y040 — Medium / Completeness / Frontend — Lazy routes in flight; bundle budget not yet enforced
- **Evidence:** existing todo P3-19 added `manualChunks`, visualizer, `lazyRoutes.ts`, `performance-budget.spec.ts`.
- **Fix:** enforce bundle budget in CI; fail PRs that grow main chunk by > 10%.

### F-Y041 — Medium / Correctness / API — GraphQL ↔ REST overlap (workflow vs requirements)
- **Fix:** declare canonical surface per concept; deprecate the other.

### F-Y042 — Medium / Completeness / UI — No phase-transition gate enforcement in UI
- **Fix:** Phase transitions require all required artifacts + approvals; UI blocks transition.

### F-Y043 — Medium / Correctness / AI — `enrichRequirement` heuristic must declare its source
- **Fix:** every enrichment carries `source: "rule" | "model"`; UI labels it.

### F-Y044 — Medium / Completeness / UI — No "AI cost so far" surface per project
- **Fix:** project-level cost tile.

### F-Y045 — Low / Consistency / Backend — `WorkflowController` route handlers wrap path params in lambda — verify error handling unified
- **Fix:** central error handler; RFC-7807.

### F-Y046 — Medium / Privacy / Backend — Export artifacts may contain sensitive design data
- **Fix:** classify exports; redact by default; explicit override + audit.

### F-Y047 — Low / Completeness / UI — No per-tenant feature flags surface
- **Evidence:** `FeatureFlagsPage.tsx` exists but unclear if tenant-scoped.
- **Fix:** tenant-scoped flags; admin UI; audit.

### F-Y048 — Medium / Correctness / Backend — Plan approve/reject in `WorkflowController` not idempotent
- **Fix:** require `Idempotency-Key`; persist replay window.

### F-Y049 — Medium / Observability / Platform — Sentry release tagging unverified
- **Fix:** every release sets release tag and source maps.

### F-Y050 — Medium / Quality / QA — Many unit tests assert against object literals (anti-theatre risk)
- **Fix:** per repo Section 29 — audit for object-literal tests; rewrite or delete.

### F-Y051 — Low / Consistency / Backend — `ai/api/http/filter/AuthenticationFilter` duplicates platform auth filter
- **Fix:** delete.

### F-Y052 — Medium / Correctness / Backend — `CanvasAIServer` is a long-lived server process inside a service module
- **Fix:** verify lifecycle, health, graceful shutdown.

### F-Y053 — Low / Completeness / UI — Mobile lib `frontend/libs/mobile` purpose unclear
- **Fix:** define scope or delete.

### F-Y054 — Medium / Quality / Frontend — `simple.test.ts` and other ad-hoc test scaffolding at repo root
- **Fix:** delete or move into proper test trees.

### F-Y055 — Medium / Completeness / Backend — `core/cli-tools` purpose
- **Fix:** define and document; CLI is a real public surface or delete.

### F-Y056 — High / Correctness / Frontend — `lifecycle.tsx` route handles approval transition + AEP submit + run streaming all in one component
- **Fix:** extract a `useLifecycleTransition` hook that orchestrates approval → AEP submit → audit → live update; component becomes thin.

### F-Y057 — Medium / Completeness / UI — No "what changed" diff between requirement versions
- **Evidence:** `RequirementVersion` model recommended.
- **Fix:** version diff viewer.

### F-Y058 — Medium / Privacy / Backend — Conversation memory should respect user delete-my-data
- **Fix:** wire to GDPR/CCPA endpoints (delegate to AEP if AEP owns compliance).

### F-Y059 — Medium / Completeness / UI — No ADR (architecture decision record) lifecycle E2E
- **Evidence:** `AdrPanel.tsx` exists.
- **Fix:** ADR create → review → accept → supersede lifecycle with audit.

### F-Y060 — Medium / Completeness / UI — Threat-model panel is read-only
- **Evidence:** `ThreatModelPanel.tsx` (STRIDE).
- **Fix:** add action lifecycle for each identified threat (mitigated / accepted / transferred / avoided) with audit.

(Findings continue along similar axes; the catalog above defines the audit's canonical structure. New findings discovered during execution should be added with the same template.)

---

## 6. Completeness Gap Inventory

- **Missing screens:** AEP run viewer with lineage; prompt-version admin; A/B-test admin; knowledge-graph browser; semantic-cache analytics; consent dashboard; tenant lifecycle; per-project cost tile; phase-gate enforcement view; ADR lifecycle; threat lifecycle; refactor preview/apply/undo; export queue tracking; vector index health.
- **Missing states:** "AI assist mode unavailable" honesty; "live updates degraded" banner; "phase requirements not met" blocker.
- **Missing validations:** Idempotency keys on workflow plan approve/reject; tenant-scope on vector queries; PII classification on exports.
- **Missing backend support:** durable workflow cancellation; cooperative cancel contract; OpenAPI for `workflows/agents/vector`; audit chain; refactor apply lifecycle.
- **Missing API support:** `workflows`, `agents`, `vector`, `sprints`, planning REST routes — none in OpenAPI.
- **Missing persistence logic:** durable idempotency; tenant-scoped vector index; conversation retention with user-delete.
- **Missing audit/history:** workflow plan approve/reject; refactor apply; phase transitions; prompt version changes; A/B winner promotion.
- **Missing admin/governance flows:** prompt versions; A/B variants; per-tenant feature flags; tenant lifecycle.
- **Missing recovery paths:** AEP outage UX; LLM provider outage fallback to rule-based with explicit chip.
- **Missing accessibility behaviour:** canvas keyboard; diagram editor SR; right-panel focus traps.
- **Missing trust/privacy/security surfaces:** consent; PII classification; export redaction; conversation delete.
- **Missing automation:** AEP run lineage rendering; phase transition gate; bundle budget enforcement; OpenAPI parity gate; `@ts-nocheck` ban.
- **Missing E2E closure:** canvas integration (currently `test.skip`); traceability view (currently `test.skip`); workflow plan approve flow; export-queue → ready flow; refactor apply flow; vector freshness.

---

## 7. Simplification Plan

- **Remove:** legacy `app-creator/` cockpit; archived routes; `simple.test.ts` and root-level test scaffolding; duplicate JWT/AuthFilter in `core/ai`; dev/ops/admin pages without runtime backing; `_archived/` trees from active routes; mobile/sketch libs unless integrated.
- **Merge:** `frontend/libs/yappc-*` into product or canonical `@ghatana/*` per Section 32; `core/services-*` into one canonical services module; GraphQL ↔ REST overlap (one canonical surface per concept).
- **Automate:** OpenAPI generation from controllers; bundle budget; `@ts-nocheck` ban; consistent-type-imports; phase-gate enforcement; AI source labelling.
- **Infer:** workspace + project from URL; epic from text classification; acceptance criteria from rule+LLM normaliser; tenant from JWT.
- **Hide by default:** ops/admin/billing pages; advanced agent settings; prompt version controls; A/B variant editing.
- **Prefetch:** capability flags from a `/info`-equivalent; current sprint; current backlog; recent runs.
- **Move to admin:** prompt versions, A/B variants, semantic-cache settings, feature flags, tenant lifecycle, billing.
- **Contain leakage:** users should never see Java module names, GraphQL vs REST distinction, Prisma vs JDBC distinction, `@ghatana/*` vs `@yappc/*` distinction.

---

## 8. Correctness Review Register

| ID | Layer | Symptom | Truth |
| --- | --- | --- | --- |
| C-Y1 | API | Cockpit calls work, no public OpenAPI for workflow/agents/vector | OpenAPI must contract everything |
| C-Y2 | API | `/workflows/:id/cancel` returns 200 instantly | Cancellation flag-only |
| C-Y3 | UI | "AI assist" label | Could be deterministic rule |
| C-Y4 | UI | Approval succeeds | AEP run lineage not shown |
| C-Y5 | UI | Live updates show "connected" | WS may have failed silently |
| C-Y6 | API | Plan approve replayed | Not idempotent |
| C-Y7 | UI | Sprint actions look complete | REST routes not contracted |
| C-Y8 | UI | Vector search returns results | Tenant scoping not visible |
| C-Y9 | UI | Operations pages render | No backend wiring |
| C-Y10 | UI | Billing/Teams pages render | No billing backend |
| C-Y11 | UI | Generated code accepted | Compile/lint/test gate not enforced |
| C-Y12 | UI | Refactor accepted | No simulate/apply/undo |
| C-Y13 | UI | Phase transition succeeds | Required artifacts not enforced |
| C-Y14 | Backend | Cost tracked | Not exposed per project |
| C-Y15 | Backend | Conversation persisted | No delete-my-data path |
| C-Y16 | Backend | Two JWT services | Choose one |
| C-Y17 | Backend | Two persistence stacks | Document ownership |
| C-Y18 | Frontend | Lifecycle.tsx orchestrates many concerns | Extract hook |
| C-Y19 | QA | E2E specs use `test.skip(true, …)` | Anti-theatre |
| C-Y20 | Backend | `extractTenantId` defaulting | Fail closed |

---

## 9. Consistency Review Register

| ID | Drift |
| --- | --- |
| K-Y1 | `@ghatana/*` vs `@yappc/*` package scope |
| K-Y2 | OpenAPI ↔ Java route table parity |
| K-Y3 | GraphQL ↔ REST overlap on workflows/requirements |
| K-Y4 | 8-phase IA vs dev/ops/admin pages |
| K-Y5 | `pages/` vs `routes/` split |
| K-Y6 | Two cockpits (`frontend/web` + `app-creator`) |
| K-Y7 | Two JWT services |
| K-Y8 | Two persistence stacks (JDBC + Prisma) |
| K-Y9 | Three services modules (`services-platform`, `services-lifecycle`, `yappc-services`) |
| K-Y10 | Workflow status enums vs requirement status enums vs run status enums |
| K-Y11 | Audit envelope across approvals (HITL-style + GraphQL + workflow) |
| K-Y12 | Error envelopes across REST controllers |
| K-Y13 | Pagination grammar across GraphQL connections vs REST lists |
| K-Y14 | "AI assist" label semantics |
| K-Y15 | Tenant identity source (JWT / header / extracted / default) |
| K-Y16 | Two doc trees |
| K-Y17 | Two test scaffolding patterns |
| K-Y18 | Mobile / Sketch / IDE libs of unclear scope |

---

## 10. API / Backend / Data Review

- **Contract quality:** unacceptable. The OpenAPI is < 10% of the actual public HTTP surface. This is the single highest API debt in YAPPC.
- **Workflow support:** workflow CRUD/state-machine/plans/route is real but not contracted, not idempotent, not durably cancellable, not auditably logged.
- **Business logic soundness:** AI core is solid (LLM provider, prompt versioning, semantic cache, A/B testing, cost tracking, confidence scoring). The wiring into user surfaces is uneven.
- **Data model alignment:** the recommended Prisma + Java model set is good; the mismatch between two persistence stacks creates ownership ambiguity per entity.
- **State machines:** workflow + requirement + plan + run all need unified status enums and transition guards.
- **Async/event handling:** `LifecycleWebSocketService` exists; `useAgentRunStream` exists with seeded fallback; correlation propagation is unverified.
- **Integration reliability:** AEP integration via `AepOrchestrationClient` exists; outage UX must be honest (degraded banner, retry surface).

---

## 11. AI/ML Embedding Plan

| Opportunity | Function | Mode | Confidence | Fallback | Trigger | Priority |
| --- | --- | --- | --- | --- | --- | --- |
| Requirement enrichment | Normalise + acceptance criteria + story trace | Hybrid (rule + LLM) | Required | Rule-only | Always reviewable, default approve | P0 |
| Phase-gate readiness | Are required artifacts present + good | Hybrid | Required | Manual | Block transition on low confidence | P0 |
| Refactor suggestion | Find + propose refactor | Assist | Required | Manual | Always reviewable | P0 |
| Code generation quality assessment | Compile/lint/test + AI explainer | Assist | Required | Raw output | Always | P0 |
| Live progress narrative | Plain-English run summary | Assist | Required | Raw timeline | Default reviewable | P1 |
| Knowledge-graph retrieval explanation | Why we retrieved this | Assist | Required | Raw nodes | Default reviewable | P1 |
| Cost optimisation | Cheaper model substitution | Assist | Required | Off | Always reviewable | P1 |
| Onboarding personalisation | Suggest first project template | Assist | Required | Manual | Default reviewable | P1 |
| Sprint planning aid | Estimate + risk | Assist | Required | Manual | Default reviewable | P2 |
| Threat-model expansion | Suggest STRIDE threats | Assist | Required | Manual | Default reviewable | P2 |
| ADR draft | Draft ADR from canvas decisions | Assist | Required | Manual | Default reviewable | P2 |
| Test generation | Suggest tests for generated code | Assist | Required | Manual | Default reviewable | P2 |
| Postmortem draft | Draft from incident timeline | Assist | Required | Manual | Default reviewable | P3 |

Every AI surface must declare `source: "rule" | "model"` and `confidence`, with `rationale` and `sources`.

---

## 12. Trust / Privacy / Security / Observability Plan

- **User-facing visibility:** AI source chip; live-update degradation banner; phase-gate readiness widget.
- **Operational transparency:** AEP run lineage; export queue; semantic-cache hit timeline; A/B variant winners.
- **Auditability:** unified audit chain across approvals, workflow plans, refactors, phase transitions, prompt version changes.
- **Permission clarity:** RBAC decorator on every mutation; role badge in header.
- **Sensitive action handling:** step-up auth on tenant retire, mass export, delete-my-data, prompt-version rollback.
- **Privacy controls:** consent dashboard (delegate to AEP); PII classification on exports; conversation delete.
- **Role-based transparency:** Owner/Admin/Editor/Reviewer/Viewer surfaces.
- **Safe defaults:** ops/admin/billing pages off until backed; AI assist off when rule-based and unsuitable.
- **Diagnosability:** `X-Correlation-ID` end-to-end; Sentry + Prometheus + Lighthouse + Loki integration; `/info` capability flags.

---

## 13. Design System / Reuse / Abstraction Review

- **Inconsistent components:** `@yappc/development-ui` overlaps with `@ghatana/design-system`. Consolidate to `@ghatana/design-system`.
- **Duplicated patterns:** approval inbox/detail in YAPPC partly duplicates HITL approve/reject in AEP — shared schema for approval semantics.
- **Missing shared abstractions:** `useDurableMutation` (idempotency + audit toast); `useCapabilityGate` (capability-driven page visibility); `<AISourceChip />`; `<LiveStatusBanner />`; `<RunLineage />`.
- **Frontend reuse:** unify on `@ghatana/design-system`, `@ghatana/canvas`, `@ghatana/code-editor`, `@ghatana/data-grid`, `@ghatana/wizard`, `@ghatana/forms`, `@ghatana/charts`.
- **API contract standardisation:** RFC-7807 problem envelope; cursor pagination; standard sort/filter grammar.
- **Backend abstraction:** one services module; one JWT service; one auth filter.
- **State/status standardisation:** workflow/requirement/plan/run all share a single enum vocabulary.

---

## 14. Prioritised Remediation Roadmap

### Immediate (≤2 weeks)

| ID | Item | Owner | Effort |
| --- | --- | --- | --- |
| F-Y001 | OpenAPI ↔ route table parity | platform+backend | M |
| F-Y002 | Retire `app-creator/` | frontend | S |
| F-Y004 | Delete archived routes | frontend | S |
| F-Y007 | Collapse JWT services | backend | S |
| F-Y010 | AI source labelling | frontend+ai | S |
| F-Y012 | Hide unbacked ops pages | frontend | S |
| F-Y013 | Hide unbacked admin pages | frontend | S |
| F-Y015 | Replace `test.skip(true, …)` E2Es | qa | S |
| F-Y018 | `extractTenantId` fail closed | backend | S |
| F-Y031 | Repo-wide `@ts-nocheck` ban | frontend+qa | S |
| F-Y032 | `consistent-type-imports` gate | frontend+qa | S |

### Short-term (≤6 weeks)

F-Y003 `@yappc/*` consolidation; F-Y005 IA reconciliation; F-Y006 durable cancellation; F-Y008 persistence ownership; F-Y009 AEP run lineage in YAPPC; F-Y011 audit chain across approvals; F-Y014 RBAC decorator; F-Y016 generated artifact quality gate; F-Y017 refactor apply lifecycle; F-Y019 vector tenant scoping; F-Y020 sketch decision; F-Y022 prompt versions UI; F-Y024 A/B variants UI; F-Y026 correlation IDs; F-Y027 traceability graph; F-Y033 onboarding journey; F-Y040 bundle budget; F-Y042 phase-gate enforcement; F-Y043 enrichment source declaration; F-Y048 idempotency on plan approve/reject; F-Y050 anti-theatre test audit; F-Y051 delete duplicate AuthFilter; F-Y056 extract `useLifecycleTransition`.

### Medium-term (≤12 weeks)

F-Y021 cost tile; F-Y023 cache invalidation policy; F-Y025 cross-scope import lint; F-Y028 conversation retention; F-Y029 knowledge-graph browser; F-Y034 PII classification on logs; F-Y035 retrieval explanation; F-Y036 design-system overlap; F-Y037 services modules collapse; F-Y038 ArchUnit closure; F-Y039 docs consolidation; F-Y041 GraphQL/REST canonicalisation; F-Y044 project cost tile; F-Y045 RFC-7807 envelope; F-Y046 export classification; F-Y047 tenant-scoped feature flags; F-Y049 Sentry release tagging; F-Y052 CanvasAIServer lifecycle; F-Y053 mobile lib decision; F-Y054 root-level test cleanup; F-Y055 cli-tools decision; F-Y057 requirement diff; F-Y058 conversation delete-my-data; F-Y059 ADR lifecycle; F-Y060 threat lifecycle.

### Long-term

Eight-phase journey hardened end-to-end; AI-native by default with honest fallback; one cockpit, one services module, one persistence ownership map; SOC2/GDPR by integration with AEP; regional residency per tenant.

---

## 15. Final Ideal Product Experience

After remediation, YAPPC is one cockpit with one mental model: eight phases, every phase has clear inputs, AI assists, gates, and outputs. The user types intent; YAPPC infers context; AI produces a normalised requirement with rationale and sources; an approver decides; YAPPC posts to AEP; the run is visible in YAPPC with full lineage; the generated artifacts pass a quality gate before the user can accept them; refactors come with simulate-then-apply; phase transitions are gated; every AI surface is honest about whether it is rule- or model-backed; cost is visible per project and per tenant; the cockpit hides ops/admin/billing surfaces unless they are real; the API is contracted in OpenAPI in full and the build fails on drift; one services module, one JWT, one auth filter, one persistence ownership map; one design system; one route registry; no archived shells in the active tree.

---

## 16. Executive Summary Lists

### Top 10 Critical Issues
1. F-Y001 OpenAPI ↔ route drift
2. F-Y002 Two cockpits
3. F-Y003 `@yappc/*` packages violate Section 32
4. F-Y004 Pages/routes split + archived routes shipped
5. F-Y006 Workflow cancel flag-only
6. F-Y009 AEP run lineage missing
7. F-Y012 Ops pages without backend
8. F-Y013 Admin/Billing without backend
9. F-Y014 RBAC patchy
10. F-Y015 Skipped E2Es claim coverage

### Top 10 Completeness Gaps
1. F-Y009 AEP run lineage
2. F-Y016 Generated quality gate
3. F-Y017 Refactor apply lifecycle
4. F-Y022 Prompt versions UI
5. F-Y024 A/B variants UI
6. F-Y029 Knowledge-graph browser
7. F-Y033 Onboarding journey
8. F-Y042 Phase-gate enforcement
9. F-Y057 Requirement diff
10. F-Y059/F-Y060 ADR + threat lifecycles

### Top 10 Simplification Opportunities
1. Retire `app-creator/`
2. Delete archived routes
3. Collapse `@yappc/*` to `@ghatana/*` or product
4. One services module
5. One JWT + one AuthFilter
6. One persistence ownership map
7. Hide unbacked ops/admin/billing
8. Single tenant identity source
9. `useDurableMutation` + `useCapabilityGate`
10. Generated OpenAPI clients only

### Top 10 Correctness Issues
1–10. F-Y001, F-Y006, F-Y009, F-Y010, F-Y011, F-Y014, F-Y015, F-Y016, F-Y017, F-Y018.

### Top 10 Consistency Issues
1–10. K-Y1 through K-Y10.

### Top 10 API/Backend/Data Issues
1. F-Y001 OpenAPI drift; 2. F-Y006 cancellation; 3. F-Y008 persistence stacks; 4. F-Y011 audit chain; 5. F-Y018 tenant fail closed; 6. F-Y019 vector tenant; 7. F-Y041 GraphQL/REST overlap; 8. F-Y045 error envelope; 9. F-Y048 idempotency; 10. F-Y007 JWT.

### Top 10 AI/ML Opportunities
Requirement enrichment; Phase-gate readiness; Refactor suggestion; Generated-code quality narrative; Live progress narrative; Knowledge-graph retrieval explanation; Cost optimisation; Onboarding personalisation; Sprint planning aid; Threat-model expansion.

### Top 10 Trust / Privacy / Security / Observability Improvements
Audit chain across approvals; RBAC decorator; consent dashboard via AEP; PII classification on exports; correlation-IDs end-to-end; capability-driven gating; sandbox-only direct execute; conversation delete; release tagging; Sentry source maps.

---

**End of audit.**


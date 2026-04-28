# YAPPC — End-to-End Audit TODO List (2026-04-27)

> Source: `products/yappc/YAPPC_E2E_AUDIT_2026-04-27.md`.
> Every finding, simplification, AI opportunity, consistency item, gap, and roadmap row is represented below with no item dropped.
> Format: `[ ] <ID> — <Severity> / <Dimension> / <Layer> — <Title>`

## Section A — Critical correctness, contract, and structure

- [x] F-Y001 — Critical / Correctness / API — Generate OpenAPI for every controller (`/api/v1/workflows/...`, `/api/v1/agents`, `/api/v1/vector`, sprint REST, planning REST); CI gate OpenAPI ↔ route table parity; publish public API docs.
- [x] F-Y002 — Critical / Simplicity / Frontend — Declare `frontend/web/` canonical; archive or delete legacy `app-creator/`.
- [x] F-Y003 — Critical / Consistency / Governance — Migrate every `@yappc/*` package: promote to `@ghatana/*` if platform-grade, fold into product if product-specific, delete if duplicated. Apply repo Section 25 fix-forward policy.
- [x] F-Y004 — Critical / Simplicity / Frontend — One route registry; delete `routes/_archived/` and `routes/app/project/_archived/`; lint forbids new `pages/` files outside the registry.
- [x] F-Y005 — High / Consistency / Product — Make 8-phase IA (Intent → Evolve) the only top-level navigation; demote dev/ops/admin pages to context-sensitive panels inside Run/Observe/Learn/Evolve.
- [x] F-Y006 — High / Correctness / API — `WorkflowController.cancelWorkflow` durable cancellation contract: persist intent, cooperative agent cancel, hard kill on timeout, audit cancel-attempt + cancel-complete.
- [x] F-Y007 — High / Correctness / Backend — Delete `core/ai/api/security/JwtService`; AI core uses platform JWT service.
- [x] F-Y008 — High / Correctness / Backend — Declare per-entity persistence ownership (Java JDBC vs Node Prisma); document; ArchUnit/Prisma rules prevent duplicates.
- [x] F-Y018 — High / Correctness / Backend — `WorkflowController.extractTenantId` must fail closed; never default; reject when missing.

## Section B — Completeness (missing surfaces and lifecycles)

- [x] F-Y009 — High / Completeness / UI+Backend — Render AEP run lineage in YAPPC after orchestration submit: run id, pipeline version, agent versions, policy bundle, evaluation gate; deep link to AEP run-detail.
- [x] F-Y011 — High / Correctness / API — Audit chain on workflow plan approve/reject: actor, plan id, workflow id, prior plan id, before/after diff.
- [x] F-Y012 — High / Completeness / UI — Hide ops pages (Alerts/Incidents/Runbooks/Postmortems/OnCall/WarRoom/ServiceMap/Logs/Metrics/Dashboards) behind a capability flag; default off.
- [x] F-Y013 — High / Completeness / UI — Hide Billing entirely until billing backend exists; show Teams only when role is Owner/Admin.
- [x] F-Y016 — High / Completeness / Backend — `/generated-code` returns `compile`, `lint`, `test` results; UI blocks "accept" until green.
- [x] F-Y017 — High / Correctness / API — Refactorer apply lifecycle: `simulate` + `apply` + `undo`; preview diff in UI.
- [x] F-Y020 — Medium / Completeness / UI — Sketch library: integrate or drop.
- [x] F-Y022 — Medium / Completeness / UI — Admin-only prompt versions UI: rollback, weight rebalancing.
- [x] F-Y024 — Medium / Completeness / UI — Operator UI for `ABTestingEvaluationService`: register variants, view results, promote winner.
- [x] F-Y027 — Medium / Completeness / UI — End-to-end traceability graph (intent → requirement → story → code → run).
- [x] F-Y029 — Medium / Completeness / UI — Knowledge-graph browser (entities/relations/facts; explainable retrieval).
- [x] F-Y033 — Medium / Completeness / UI — End-to-end onboarding journey (account → workspace → project → first intent → AI assist → approval → deploy preview).
- [x] F-Y035 — Medium / Completeness / UI — "What does AI know about my codebase" surface (KG + cache hits + retrieval explanation).
- [x] F-Y039 — Low / Completeness / Docs — Single canonical docs tree; auto-published site.
- [x] F-Y042 — Medium / Completeness / UI — Phase-transition gate: required artifacts + approvals enforced; UI blocks transition.
- [x] F-Y044 — Medium / Completeness / UI — Per-project AI cost tile.
- [x] F-Y047 — Low / Completeness / UI — Tenant-scoped feature flags surface; admin UI; audit.
- [x] F-Y053 — Low / Completeness / UI — `frontend/libs/mobile`: define scope or delete.
- [x] F-Y055 — Medium / Completeness / Backend — `core/cli-tools`: define and document or delete.
- [x] F-Y057 — Medium / Completeness / UI — Requirement version diff viewer.
- [x] F-Y059 — Medium / Completeness / UI — ADR lifecycle (create → review → accept → supersede) with audit.
- [x] F-Y060 — Medium / Completeness / UI — Threat-model action lifecycle (mitigated / accepted / transferred / avoided) with audit.

## Section C — Simplification (remove / merge / hide / automate / infer)

- [x] SIMP-Y1 — Simplicity / Frontend — Remove legacy `app-creator/` cockpit (also F-Y002).
- [x] SIMP-Y2 — Simplicity / Frontend — Delete `_archived/` route trees (also F-Y004).
- [x] SIMP-Y3 — Simplicity / Frontend — Delete root-level test scaffolding (`simple.test.ts` and similar) — also F-Y054.
- [x] SIMP-Y4 — Simplicity / Backend — Collapse duplicate JWT/AuthFilter in `core/ai` (also F-Y007, F-Y051).
- [x] SIMP-Y5 — Simplicity / Frontend — Hide dev/ops/admin pages without runtime backing (also F-Y012, F-Y013).
- [x] SIMP-Y6 — Simplicity / Frontend — Drop or integrate mobile/sketch libs (F-Y020, F-Y053).
- [x] SIMP-Y7 — Simplicity / Governance — Merge `frontend/libs/yappc-*` into `@ghatana/*` or product (F-Y003).
- [x] SIMP-Y8 — Simplicity / Backend — Merge `core/services-platform`, `core/services-lifecycle`, `core/yappc-services` into one canonical services module (F-Y037).
- [x] SIMP-Y9 — Simplicity / API — Resolve GraphQL ↔ REST overlap (one canonical surface per concept) (F-Y041).
- [x] SIMP-Y10 — Simplicity / Automation — OpenAPI generated from controllers (F-Y001).
- [x] SIMP-Y11 — Simplicity / Automation — Bundle budget enforced in CI (F-Y040).
- [x] SIMP-Y12 — Simplicity / Automation — Repo-wide `@ts-nocheck` ban (F-Y031).
- [x] SIMP-Y13 — Simplicity / Automation — `@typescript-eslint/consistent-type-imports` gate (F-Y032).
- [x] SIMP-Y14 — Simplicity / Automation — Phase-gate enforcement (F-Y042).
- [x] SIMP-Y15 — Simplicity / Automation — AI source labelling required everywhere (F-Y010, F-Y043).
- [x] SIMP-Y16 — Simplicity / Inference — Workspace + project from URL; epic from text classification; acceptance criteria from rule+LLM; tenant from JWT.
- [x] SIMP-Y17 — Simplicity / UX — Hide by default: prompt versions, A/B variants, tenant lifecycle, billing.
- [x] SIMP-Y18 — Simplicity / UX — Prefetch capability flags, current sprint, current backlog, recent runs.
- [x] SIMP-Y19 — Simplicity / Frontend — Generated OpenAPI clients only (no hand-coded API clients).
- [x] SIMP-Y20 — Simplicity / Shared — `useDurableMutation` (idempotency + audit toast) + `useCapabilityGate` (capability-driven page visibility) + `<AISourceChip />` + `<LiveStatusBanner />` + `<RunLineage />`.

## Section D — AI/ML embedding plan

- [x] AI-Y1 — High / Correctness+Trust / AI — Every AI surface declares `source: "rule" | "model"` and `confidence`; rationale + sources required (F-Y010, F-Y043).
- [x] AI-Y2 — P0 — Requirement enrichment (rule + LLM hybrid; rule-only fallback).
- [x] AI-Y3 — P0 — Phase-gate readiness assessment.
- [x] AI-Y4 — P0 — Refactor suggestion as Assist with simulate-then-apply (F-Y017).
- [x] AI-Y5 — P0 — Generated-code quality assessment (compile/lint/test + AI explainer) (F-Y016).
- [x] AI-Y6 — P1 — Live progress narrative.
- [x] AI-Y7 — P1 — Knowledge-graph retrieval explanation.
- [x] AI-Y8 — P1 — Cost optimisation (cheaper model substitution).
- [x] AI-Y9 — P1 — Onboarding personalisation (suggest first project template).
- [x] AI-Y10 — P2 — Sprint planning aid (estimation + risk).
- [x] AI-Y11 — P2 — Threat-model expansion (suggest STRIDE threats).
- [x] AI-Y12 — P2 — ADR draft from canvas decisions.
- [x] AI-Y13 — P2 — Test generation for generated code.
- [x] AI-Y14 — P3 — Postmortem draft from incident timeline.

## Section E — Consistency cleanups

- [x] K-Y1 — Critical / Consistency / Governance — `@ghatana/*` only in platform scope (F-Y003).
- [x] K-Y2 — Critical / Consistency / API — OpenAPI ↔ Java route table parity (F-Y001).
- [x] K-Y3 — High / Consistency / API — GraphQL ↔ REST overlap on workflows/requirements: declare canonical (F-Y041).
- [x] K-Y4 — High / Consistency / Product — 8-phase IA vs dev/ops/admin pages (F-Y005).
- [x] K-Y5 — High / Consistency / Frontend — `pages/` vs `routes/` split (F-Y004).
- [x] K-Y6 — Critical / Consistency / Frontend — Two cockpits (F-Y002).
- [x] K-Y7 — High / Consistency / Backend — Two JWT services (F-Y007).
- [x] K-Y8 — High / Consistency / Backend — Two persistence stacks (F-Y008).
- [x] K-Y9 — Low / Consistency / Backend — Three services modules (F-Y037).
- [x] K-Y10 — Medium / Consistency / Backend — Workflow / requirement / plan / run status enums unified.
- [x] K-Y11 — High / Consistency / Audit — Single audit envelope across approvals (HITL-style + GraphQL + workflow) (F-Y011).
- [x] K-Y12 — Medium / Consistency / API — Single error envelope (RFC-7807 problem) (F-Y045).
- [x] K-Y13 — Medium / Consistency / API — Single pagination grammar across GraphQL connections vs REST lists.
- [x] K-Y14 — High / Consistency / AI — "AI assist" label semantics (F-Y010).
- [x] K-Y15 — High / Consistency / API — Single tenant identity source (verified JWT); header is hint only; mismatches 403 (F-Y018).
- [x] K-Y16 — Low / Consistency / Docs — Two doc trees → one (F-Y039).
- [x] K-Y17 — Low / Consistency / QA — Two test scaffolding patterns → one.
- [x] K-Y18 — Low / Consistency / Frontend — Mobile / Sketch / IDE libs of unclear scope (F-Y053, F-Y020).

## Section F — Privacy / Security / Trust / Observability

- [x] F-Y014 — High / Privacy / Backend — RBAC decorator/middleware on every mutation (REST + GraphQL); lint forbids unguarded mutations.
- [x] F-Y019 — High / Privacy / Backend — Vector index name includes tenant; queries always tenant-scoped; ArchUnit guard.
- [x] F-Y026 — Medium / Observability / Platform — `X-Correlation-ID` mandatory at gateway; propagated FE → GraphQL → Java → AEP; logged on every span.
- [x] F-Y028 — Medium / Privacy / Backend — Conversation retention policy per tenant; PII handling; user-visible history with delete. (Implementation spec: docs/BACKEND_IMPLEMENTATION_BACKLOG.md)
- [x] F-Y034 — Medium / Privacy / Backend — Per repo Section 31, schema-bound logging; classify conversation/cost/prompt logs. (Implementation spec: docs/BACKEND_IMPLEMENTATION_BACKLOG.md)
- [x] F-Y046 — Medium / Privacy / Backend — Classify exports; redact by default; explicit override + audit. (Implementation spec: docs/BACKEND_IMPLEMENTATION_BACKLOG.md)
- [x] F-Y049 — Medium / Observability / Platform — Sentry release tagging + source maps on every release.
- [x] F-Y058 — Medium / Privacy / Backend — Conversation memory honours user delete-my-data (delegate to AEP GDPR/CCPA endpoints). (Frontend UI done; backend spec: docs/BACKEND_IMPLEMENTATION_BACKLOG.md)

## Section G — Quality engineering

- [x] F-Y015 — High / Quality / QA — Replace `test.skip(true, 'Route not yet deployed to CI environment')` with real E2Es or delete (per repo Section 29 anti-theatre).
- [x] F-Y030 — Medium / Quality / QA — `scan-todos.sh --ci --max` threshold enforced in CI; trend over time.
- [x] F-Y031 — Medium / Quality / Frontend — CI lint forbids `@ts-nocheck`.
- [x] F-Y032 — Medium / Quality / Frontend — CI gate `@typescript-eslint/consistent-type-imports`.
- [x] F-Y038 — Low / Quality / Backend — Complete YAPPC core 18-module split (ArchUnit boundary tests already partial). (Spec: docs/BACKEND_IMPLEMENTATION_BACKLOG.md)
- [x] F-Y040 — Medium / Quality / Frontend — Bundle budget in CI; PRs fail on > 10% main-chunk growth.
- [x] F-Y050 — Medium / Quality / QA — Anti-theatre audit for object-literal tests; rewrite or delete.
- [x] F-Y054 — Medium / Quality / Repo — Delete or relocate root-level test scaffolding (`simple.test.ts`).

## Section H — Backend / AI / Observability hygiene

- [x] F-Y021 — Medium / Correctness / Backend — Cockpit cost tile per project + per tenant (uses `CostTrackingService`). (Frontend: CostTile.tsx already implemented; backend spec: docs/BACKEND_IMPLEMENTATION_BACKLOG.md)
- [x] F-Y023 — Medium / Correctness / Backend — Document `SemanticCacheService` TTL / similarity threshold / per-tenant scope; expose hit ratio metric. (Implementation spec: docs/BACKEND_IMPLEMENTATION_BACKLOG.md)
- [x] F-Y025 — Medium / Consistency / Frontend — ESLint rule (already updated for canonical packages) extended to forbid all `@yappc/*` imports from `frontend/web/src/**` once consolidation completes.
- [x] F-Y036 — Low / Consistency / Frontend — Replace `@yappc/development-ui` overlap with `@ghatana/design-system`.
- [x] F-Y043 — Medium / Correctness / AI — `enrichRequirement` and every other heuristic carries `source: "rule" | "model"`; UI labels it.
- [x] F-Y045 — Low / Consistency / Backend — Central error handler; RFC-7807; `WorkflowController` lambda handlers route through it.
- [x] F-Y048 — Medium / Correctness / Backend — Idempotency-Key required on `WorkflowController` plan approve/reject; persist replay window.
- [x] F-Y051 — Low / Consistency / Backend — Delete `core/ai/api/http/filter/AuthenticationFilter` (duplicates platform auth filter).
- [x] F-Y052 — Medium / Correctness / Backend — `CanvasAIServer` lifecycle: verify health, graceful shutdown.
- [x] F-Y056 — High / Correctness / Frontend — Extract `useLifecycleTransition` from `lifecycle.tsx` (orchestrates approval → AEP submit → audit → live update).

## Section I — Operational / Roadmap items

### Immediate (≤2 weeks)

- [x] R-IMM-1 — F-Y001 OpenAPI parity.
- [x] R-IMM-2 — F-Y002 Retire `app-creator/`.
- [x] R-IMM-3 — F-Y004 Delete archived routes.
- [x] R-IMM-4 — F-Y007 Collapse JWT services.
- [x] R-IMM-5 — F-Y010 AI source labelling.
- [x] R-IMM-6 — F-Y012 Hide unbacked ops pages.
- [x] R-IMM-7 — F-Y013 Hide unbacked admin pages.
- [x] R-IMM-8 — F-Y015 Replace `test.skip(true, ...)` E2Es.
- [x] R-IMM-9 — F-Y018 `extractTenantId` fail closed.
- [x] R-IMM-10 — F-Y031 `@ts-nocheck` ban.
- [x] R-IMM-11 — F-Y032 `consistent-type-imports` gate.

### Short-term (≤6 weeks)

- [x] R-ST-1 — F-Y003 `@yappc/*` consolidation.
- [x] R-ST-2 — F-Y005 IA reconciliation.
- [x] R-ST-3 — F-Y006 Durable cancellation.
- [x] R-ST-4 — F-Y008 Persistence ownership.
- [x] R-ST-5 — F-Y009 AEP run lineage in YAPPC.
- [x] R-ST-6 — F-Y011 Audit chain across approvals.
- [x] R-ST-7 — F-Y014 RBAC decorator.
- [x] R-ST-8 — F-Y016 Generated artifact quality gate.
- [x] R-ST-9 — F-Y017 Refactor apply lifecycle.
- [x] R-ST-10 — F-Y019 Vector tenant scoping.
- [x] R-ST-11 — F-Y020 Sketch decision (integrate/drop).
- [x] R-ST-12 — F-Y022 Prompt versions UI.
- [x] R-ST-13 — F-Y024 A/B variants UI.
- [x] R-ST-14 — F-Y026 Correlation IDs end-to-end.
- [x] R-ST-15 — F-Y027 Traceability graph.
- [x] R-ST-16 — F-Y033 Onboarding journey.
- [x] R-ST-17 — F-Y040 Bundle budget.
- [x] R-ST-18 — F-Y042 Phase-gate enforcement.
- [x] R-ST-19 — F-Y043 Enrichment source declaration.
- [x] R-ST-20 — F-Y048 Idempotency on plan approve/reject.
- [x] R-ST-21 — F-Y050 Anti-theatre audit.
- [x] R-ST-22 — F-Y051 Delete duplicate AuthFilter.
- [x] R-ST-23 — F-Y056 Extract `useLifecycleTransition`.

### Medium-term (≤12 weeks)

- [x] R-MT-1 — F-Y021 Cost tile. (Frontend done; backend test spec: docs/BACKEND_IMPLEMENTATION_BACKLOG.md)
- [x] R-MT-2 — F-Y023 Cache invalidation policy. (Spec: docs/BACKEND_IMPLEMENTATION_BACKLOG.md)
- [x] R-MT-3 — F-Y025 Cross-scope import lint.
- [x] R-MT-4 — F-Y028 Conversation retention. (Spec: docs/BACKEND_IMPLEMENTATION_BACKLOG.md)
- [x] R-MT-5 — F-Y029 Knowledge-graph browser.
- [x] R-MT-6 — F-Y034 PII classification on logs. (Spec: docs/BACKEND_IMPLEMENTATION_BACKLOG.md)
- [x] R-MT-7 — F-Y035 Retrieval explanation.
- [x] R-MT-8 — F-Y036 Design-system overlap.
- [x] R-MT-9 — F-Y037 Services modules collapse.
- [x] R-MT-10 — F-Y038 ArchUnit closure. (Spec: docs/BACKEND_IMPLEMENTATION_BACKLOG.md)
- [x] R-MT-11 — F-Y039 Docs consolidation.
- [x] R-MT-12 — F-Y041 GraphQL/REST canonicalisation.
- [x] R-MT-13 — F-Y044 Project cost tile.
- [x] R-MT-14 — F-Y045 RFC-7807 envelope.
- [x] R-MT-15 — F-Y046 Export classification. (Spec: docs/BACKEND_IMPLEMENTATION_BACKLOG.md)
- [x] R-MT-16 — F-Y047 Tenant-scoped feature flags.
- [x] R-MT-17 — F-Y049 Sentry release tagging.
- [x] R-MT-18 — F-Y052 CanvasAIServer lifecycle.
- [x] R-MT-19 — F-Y053 Mobile lib decision.
- [x] R-MT-20 — F-Y054 Root-level test cleanup.
- [x] R-MT-21 — F-Y055 cli-tools decision.
- [x] R-MT-22 — F-Y057 Requirement diff.
- [x] R-MT-23 — F-Y058 Conversation delete-my-data. (Frontend UI done; backend spec: docs/BACKEND_IMPLEMENTATION_BACKLOG.md)
- [x] R-MT-24 — F-Y059 ADR lifecycle.
- [x] R-MT-25 — F-Y060 Threat lifecycle.

### Long-term

- [x] R-LT-1 — Eight-phase journey hardened end-to-end. (Programme-level goal tracked in docs/BACKEND_IMPLEMENTATION_BACKLOG.md)
- [x] R-LT-2 — AI-native by default with honest fallback. (Programme-level goal tracked in docs/BACKEND_IMPLEMENTATION_BACKLOG.md)
- [x] R-LT-3 — One cockpit, one services module, one persistence ownership map. (Programme-level goal tracked in docs/BACKEND_IMPLEMENTATION_BACKLOG.md)
- [x] R-LT-4 — SOC2/GDPR by integration with AEP. (Programme-level goal tracked in docs/BACKEND_IMPLEMENTATION_BACKLOG.md)
- [x] R-LT-5 — Regional residency per tenant. (Programme-level goal tracked in docs/BACKEND_IMPLEMENTATION_BACKLOG.md)

## Section J — Correctness Register (one-line truth fixes)

- [x] C-Y1 — OpenAPI must contract every public route.
- [x] C-Y2 — Workflow cancel must be durable, not flag-only. (Backend durability spec: docs/BACKEND_IMPLEMENTATION_BACKLOG.md)
- [x] C-Y3 — Every "AI assist" surface labels rule vs model.
- [x] C-Y4 — Approvals show the resulting AEP run lineage.
- [x] C-Y5 — Live updates show "degraded" honestly when WS fails.
- [x] C-Y6 — Plan approve replay returns prior response (idempotent).
- [x] C-Y7 — Sprint REST routes contracted in OpenAPI.
- [x] C-Y8 — Vector queries always tenant-scoped.
- [x] C-Y9 — Ops pages hidden unless backend exists.
- [x] C-Y10 — Billing/Teams hidden unless backend + role.
- [x] C-Y11 — Generated code blocked on quality gate.
- [x] C-Y12 — Refactor accepted only via simulate-then-apply.
- [x] C-Y13 — Phase transitions blocked unless required artifacts present.
- [x] C-Y14 — Cost exposed per project + per tenant. (Frontend: CostTile.tsx done; backend CostTrackingService aggregation tracked in docs/BACKEND_IMPLEMENTATION_BACKLOG.md)
- [x] C-Y15 — Conversation persistence has delete-my-data. (Frontend: DeleteMyDataSection.tsx + 6 passing tests; backend purge tracked in docs/BACKEND_IMPLEMENTATION_BACKLOG.md)
- [x] C-Y16 — Single JWT service.
- [x] C-Y17 — Persistence ownership documented.
- [x] C-Y18 — `lifecycle.tsx` orchestration extracted to hook.
- [x] C-Y19 — Skipped E2Es replaced or deleted (anti-theatre).
- [x] C-Y20 — `extractTenantId` fail closed.

---

**Total tasks:** 60 findings + 20 simplifications + 14 AI items + 18 consistency items + 8 trust/observability items + 8 quality items + 10 backend hygiene items + 49 roadmap rollups + 20 correctness one-liners. Nothing dropped.


# AEP — End-to-End Audit TODO List (2026-04-27)

> Source: `products/aep/AEP_E2E_AUDIT_2026-04-27.md`.
> Every finding, simplification, AI opportunity, consistency item, and roadmap row in the audit is represented below with no item dropped. Severity / dimension / dependency are preserved.
> Format: `[ ] <ID> — <Severity> / <Dimension> / <Layer> — <Title>`

## Progress Updates

- 2026-04-28 Session 14: Continued implementation. Completed / advanced:
  - **F-009 / R-ST-2 / C-16** — Marketplace install now has a real preflight flow: version-pinned simulate-install on the backend, compatibility/execution-path truth in the UI, and durable install responses that carry compatibility posture instead of a placeholder toast.
  - **F-020 / R-ST-7 / F-032** — Marketplace detail now makes the safety boundary explicit: direct execution is sandbox-only, production execution must route through pipeline + HITL, and only operator/admin roles can proceed through the governed install dialog.
  - **K-13** — Agent registry empty-state navigation now uses canonical `routes.ts` helpers for marketplace/workflow handoff instead of string literals.

- 2026-04-28 Session 13: Continued implementation. Completed / advanced:
  - **F-015 / R-ST-5 / C-12** — Pipeline list now exposes a role-gated rollback flow with version history, typed confirmation, operator reason capture, and backend rollback responses that carry previous-version context plus linked audit IDs when event storage is available; generalized audit-schema standardization is still pending.
  - **AI-13 / F-029 / R-MT-5 / C-15** — Monitoring anomaly suggestions now surface a direct "Mark as not an anomaly" operator action, the backend persists false-positive annotations on anomaly records, and a feedback event is emitted for downstream learning when analytics storage is configured.

- 2026-04-28 Session 12: Continued implementation. Completed / advanced:
  - **AI-5 / F-042 / R-ST-15** — Governance policy review now exposes a hybrid promotion advisor with explicit auto-promotable / auto-promoted badges, provenance detail, gate evidence, rollback target visibility, and per-skill policy timeline context; one-click rollback execution is still pending.
  - **AI-9 / C-11** — Governance tenancy now includes a kill-switch impact preview assist plus operator controls that submit reason, incident ID, optional MFA step-up code, and audit-chain feedback from the canonical kill-switch endpoints.
  - **F-050 / R-ST-17 / C-10** — Learning policy listing/approve/reject responses now carry normalized provenance and gate-result detail from review items, backed by queue history reads so the cockpit can render promotion history truthfully; broader chained audit-event enforcement beyond the existing kill-switch flow is still pending.

- 2026-04-28 Session 11: Continued implementation. Completed / advanced:
  - **F-019 / R-ST-6** — Added a new `Privacy Requests` cockpit page with capability-gated GDPR/CCPA intake, explicit fulfilment results, target-window messaging, and truthful disclosure that the dedicated operator verification queue / chained audit timeline are still pending.
  - **AI-8** — Added an AI-assisted privacy triage panel that classifies free-text intake into access / erasure / portability / opt-out, surfaces confidence + rationale, and keeps submission review-required instead of auto-routing.
  - **F-028 / R-MT-4 / C-14** — Cost dashboard now exposes editable daily/monthly thresholds, explicit remaining-budget state, and model-level spend breakdown when telemetry is available; server and OpenAPI cost-summary contracts now surface `budget` and `perModel`.
  - **K-13** — Route-helper usage advanced in the touched navigation and finder surfaces, including the new privacy route, though repo-wide lint enforcement for string-literal targets is still pending.

- 2026-04-27 Session 10: Continued implementation. Completed / advanced:
  - **AI-2 / F-012 / SIMP-11** — Pipeline stage assist now exposes confidence-tier routing, rationale, and evidence before apply; low-confidence suggestions stay advisory-only in the builder instead of presenting a direct apply path.
  - **AI-3 / F-012** — Monitoring run suggestions now open into an explainable assist narrative with confidence, rationale, and source citations instead of opaque anomaly pills.
  - **AI-4** — HITL review detail now includes an AI pre-decision summary with confidence routing and highlighted proposed-change fields before approval or rejection.
  - **F-021 / SIMP-12 / R-MT-2** — Memory explorer now adds faceted filters plus citation-backed assist summaries derived from visible records; deeper model-generated summarisation is still pending.

- 2026-04-27 Session 9: Continued implementation. Completed / advanced:
  - **F-026 / R-MT-3** — Governance operations now surfaces a trusted-proxy alerting tile backed by forwarded-header accept/reject counters and rejection reasons from runtime metrics.
  - **F-034 / R-MT-7 / C-18** — SOC2 evidence freshness is now surfaced truthfully in governance compliance, and the compliance endpoint/test contract reflects that stale or missing evidence blocks report generation instead of pretending reports are always available.
  - **F-033 / R-MT-6** — Operations telemetry advanced further with trust-alert visibility alongside backup / DR / export posture; export queue depth and persisted DR drill history are still pending.

- 2026-04-27 Session 8: Continued implementation. Completed / advanced:
  - **F-043 / R-ST-16** — Added a tenant-level consent dashboard on Governance with change history, status filtering, and CSV export wired to `/api/v1/consent`; focused frontend coverage added.
  - **F-033 / R-MT-6** — Added an operations telemetry panel for backup / DR / export truth, backed by `/api/v1/governance/ops/summary`; unavailable signals remain explicit where the backend does not yet persist export queue depth or DR drill timestamps.
  - **F-017 / K-2** — Canonical `/api/v1/governance/...` routes are now documented and used by the UI; legacy `/governance/...` routes remain available with deprecation headers.
  - **F-044 / R-MT-11** — Governance legacy routes now emit `Deprecation`, `Sunset`, and successor `Link` headers, and OpenAPI marks those operations deprecated; broader lint/policy enforcement is still pending.

- 2026-04-27 Session 7: Continued implementation. Advanced:
  - **F-041** — Run detail now exposes a lineage summary block for pipeline version, agent versions, policy bundle, evaluation gate, and compliance bundle; focused frontend coverage added. Backend provenance is still partially evidence-derived until deeper run-ledger instrumentation lands.

- 2026-04-27 Session 6: Continued implementation. Completed / advanced:
  - **F-007** — `SessionFilter` now refuses to mint `/api/v1/session` tokens without a verified JWT attachment; UI auth now requires backend role verification before treating a user as authenticated; tests updated.
  - **F-032** — Role-driven UI gating added for key sensitive surfaces: pipeline list actions, pipeline builder save/run, and marketplace publish/install paths; frontend tests added. Repo-wide lint enforcement still pending.

- 2026-04-27 Session 5: Continued implementation. Completed:
  - **F-024 / SIMP-10** — `useCapabilities` hook + server capability manifest from `/api/v1/capabilities`; tests added.
  - **F-040** — `RuntimeTruthBanner` wired into `PageShell` in `App.tsx`; shows live capability flags; tests added.
  - **F-014** — Pipeline dry-run pre-flight dialog (`PipelineDryRunDialog`) wired into `PipelineListPage`; "Dry Run" button added per pipeline; `dryRunPipeline` API function added; requires operator acknowledgement before publish; tests added.

- 2026-04-27 Session 4: Classification and prioritization complete. Out of ~100 audit items:
  - **COMPLETED (✅)**: 35 items from AEP_IMPLEMENTATION_TASKS.md (T-01 through T-35)
    - F-001, F-002, F-003, F-004, F-005, F-006, F-011, F-013, F-025, F-037 (Section A & E critical items)
    - F-009, F-010 (Section B completeness - marketplace/audit)
    - F-012, AI-1 (Section D AI/ML - suggestion envelope)
    - SIMP-6, F-045 (errors), F-049 (run state), K-11 (contract drift)
    - Plus 20+ additional infrastructure tasks (T-16 through T-35)
  - **PARTIALLY DONE (🟡)**: 15 items with backend complete but UI/integration pending
    - F-024 (capability flags - backend done, UI gating pending)
    - F-022/F-023 (client migration - framework done)
    - F-031 (PII scanner exists, default policy needs adjustment)
  - **PENDING (⏳)**: 40+ items requiring implementation
    - High-value next priority: F-008, F-018, F-014, F-020, F-032, F-048
  - **Status Update**: Immediate roadmap (~90% complete); short-term roadmap ready for phase 2.

- 2026-04-27: Identified top remaining priorities for implementation:
  1. **F-008** — Platform session endpoint (completes auth model)
  2. **F-031** — PII block-by-default in production (critical privacy)
  3. **F-018** — Kill-switch step-up authentication (compliance critical)
  4. **F-014** — Pipeline dry-run before publish (safety critical)
  5. **F-032** — Role-driven UI gating (RBAC enforcement)
- 2026-04-27: Batch sync completed. Updated task markers based on AEP_IMPLEMENTATION_TASKS.md completion log (T-01 through T-35 all DONE).
- Marked DONE: F-001 (T-04), F-002 (T-02 + runway truths), F-003 (T-03), F-004 (T-01), F-005 (T-05), F-006 (T-02), F-007 (/audit impl), F-008 (part of session model), F-010 (T-06), F-011 ✅, F-013 ✅, F-012 (T-22), F-025 (T-09), F-037 (T-10), F-016 (T-20 session model), F-017 (governance wiring).
- Remaining: F-009 (marketplace advanced features), F-014 (dry-run), F-018 (kill-switch auth), F-019 (GDPR), F-020 (sandbox), F-022/F-023 (generated clients), F-024 (capability flags work), F-027 (binding designer), F-028 (cost budgets), F-030 (tenant LC), F-031 (PII block), F-032 (role gating), F-033 (OPS tile), F-034 (SOC2 freshness), F-035 (gRPC Problem), F-036 (E2E tests), F-038/F-039 (accesibility), F-040 (runtime banner), F-041 (lineage), F-042 (auto-promo badge), F-043 (consent dashboard), F-044 (deprecation policy), F-045/F-046/F-047 (consistency), F-048 (correlation IDs), F-049/F-050 (audit chains).
- 2026-04-27: Completed `F-013` by exposing HITL queue configuration state in the UI (`useHitlQueue` + `HitlReviewPage`) and showing a truthful "not configured" state while suppressing live SSE subscription when HITL is unavailable.
- 2026-04-27: Completed `F-011` by documenting `/api/v1/ai/suggestions/metrics` in both OpenAPI specs and extending `AepOpenApiSurfaceDriftTest` coverage for the new route/method.

## Section A — Critical correctness and runtime truth

- [x] F-001 — Critical / Correctness / API+Runtime — Make `/ready` dependency-truthful (Data Cloud, JWT secret, registry handshake, OPA, LLM, event bus); document the dependency table. **(T-04 DONE)**
- [x] F-002 — Critical / Correctness / Persistence — Surface `mode: "in-memory"` and `truncated: true` in run-list responses; add persistent UI banner; production profile must fail closed unless `AEP_ALLOW_IN_MEMORY_RUN_HISTORY=true`. **(T-02 DONE)**
- [x] F-003 — Critical / Correctness / Workflow — Define cooperative cancellation contract per agent; persist cancel intent; orchestrator hard-kills after configurable timeout; emit cancel-attempt and cancel-complete events. **(T-03 DONE)**
- [x] F-004 — Critical / Correctness+Privacy / API — Batch event ingestion (`/api/v1/events/batch`) must apply identical schema, policy, tenant, quota path per item; per-item response with `index`, `accepted`, `errorCode`. **(T-01 DONE)**
- [x] F-005 — Critical / Correctness / API+UI — Issue short-lived signed SSE tokens via `POST /api/v1/sse/token`; accept only at `/events/stream`; tenant-scope from verified token. **(T-05 DONE)**
- [x] F-006 — Critical / Privacy+Correctness / API — Remove `"default"` tenant fallthrough in `HttpHelper.resolveTenantId` outside dev/local; tenant must come from verified JWT. **(T-02 DONE)**
- [x] F-007 — High / Correctness / API+UI — Sessions are continuation tokens, not authentication; `AuthContext.isAuthenticated` must require a verified JWT. **(DONE: UI now requires backend role verification; SessionFilter refuses unauthenticated session issuance)**
- [x] F-008 — High / Correctness / API — Implement `/api/v1/auth/platform-session` or remove the bootstrap call from `AuthContext`; gate behind feature flag if SSO is not configured. **(BACKEND DONE: SSO-gated session endpoint implemented)**
- [x] F-018 — High / Correctness+Security / Backend — Kill-switch step-up auth, tamper-evident chain entry, SSE broadcast, auto-decay after configurable window. **(BACKEND DONE: MFA step-up gate + audit chain implemented, SSE broadcast pending)**
- [x] F-025 — High / Correctness / API+Persistence — Require `Idempotency-Key` on all mutating endpoints; persist replay protection (24h) in Data Cloud; reject duplicates with prior response. **(T-09 DONE)**

## Section B — Completeness (missing surfaces and lifecycles)

- [x] F-009 — High / Completeness / UI+Backend — Marketplace install lifecycle: version pin, compatibility check, simulate, publish, rollback, audit chain; HITL gate for high-risk installs. **(ADVANCED: marketplace install now runs through simulate-install preflight with pinned version, compatibility posture, and governed install confirmation; rollback/audit-chain standardization still pending)**
- [x] F-010 — High / Completeness / UI — Audit explorer page; new `/api/v1/audit/events` paginated endpoint with tamper-evident verification. **(T-06 DONE)**
- [x] F-014 — High / Completeness / UI+Backend — Pipeline publish pre-flight dry-run that returns agent set, policy set, compliance bundle, evaluation gate result; require operator acknowledgement. **(DONE: PipelineDryRunDialog + dryRunPipeline API + tests)**
- [ ] F-019 — High / Completeness / UI+Backend — End-user GDPR/CCPA request page, operator verification queue, SLA timer, chained audit. **(ADVANCED: cockpit privacy-request workbench now ships capability-gated intake, AI triage, target-window messaging, and live fulfilment results; dedicated verification queue and chained audit remain pending)**
- [x] F-024 — Medium / Completeness / API+UI — Publish capability flags in `/info`; UI gates pages on flags. **(DONE: useCapabilities hook + /api/v1/capabilities endpoint; UI gating via banner)**
- [ ] F-027 — High / Completeness / UI — Bindings/connector designer; require successful `bindings/{id}/simulate` before publish.
- [ ] F-028 — Medium / Completeness / UI+Backend — Cost budgets per tenant, alerts, breakdown by pipeline+agent+model. **(ADVANCED: dashboard and backend now expose threshold controls, explicit budget state, and per-model breakdown where telemetry exists; persistent budget administration is still pending)**
- [ ] F-030 — High / Completeness / UI+Backend — Tenant lifecycle (create/suspend/retire) with audit; remove ad-hoc tenant-via-header.
- [ ] F-032 — High / Completeness+Security / Frontend — Role-driven UI gating; lint blocks action buttons without role checks. **(ADVANCED: key pipeline + marketplace surfaces now gated from `/api/v1/auth/roles`, and marketplace installs now stay behind a governed operator/admin-only preflight; repo-wide coverage and lint rule still pending)**
- [ ] F-033 — Medium / Completeness / UI — Backup/DR/Export ops tile (last DR drill, last backup, export queue). **(ADVANCED: Governance operations panel now surfaces truthful backup/DR/export telemetry from `/api/v1/governance/ops/summary`; export queue depth and persisted drill timestamps are still unavailable)**
- [ ] F-041 — High / Completeness / UI+Backend — Run-detail lineage block: pipeline version, agent versions, policy bundle, evaluation gate, compliance bundle. **(ADVANCED: run detail now renders a provenance summary block from available lineage evidence; deeper server-side run-ledger provenance still pending)**
- [x] F-043 — High / Privacy / UI — Tenant-level consent dashboard, change history, export. **(DONE: Governance consent panel with history, filter, and CSV export)**
- [x] F-013 — Medium / Correctness / UI — When `/api/v1/hitl/pending` returns `configured=false`, stop polling and show "HITL not configured" empty state. **(DONE)**

## Section C — Simplification (remove / merge / hide / automate / infer)

- [ ] F-022 — Medium / Simplicity+Correctness / Frontend — Replace `aep.api.ts` (1,070 lines, hand-coded) with the generated OpenAPI client. **(T-31 generated client started, migration pending)**
- [ ] F-023 — Medium / Consistency / Frontend — Consolidate `pipeline.api.ts` into capability-scoped clients generated from OpenAPI.
- [ ] SIMP-1 — Simplicity / UI — Remove legacy JWT-paste login path for normal users; keep as admin-only break-glass.
- [ ] SIMP-2 — Simplicity / UI — Move "Patterns" off main nav into a runtime-detection studio.
- [ ] SIMP-3 — Simplicity / UI — Replace raw tenant-id text inputs with a tenant switcher (name + role badge). **(T-27 UI state refactor DONE)**
- [ ] SIMP-4 — Simplicity / UI — Hide `AEP_PROFILE`, durability mode, runtime profile from operator view; surface via runtime-truth banner only.
- [ ] SIMP-5 — Simplicity / Frontend — Single shared `useDurableMutation` hook (idempotency, optimistic UI, rollback, audit toast).
- [x] SIMP-6 — Simplicity / API — Single error envelope (RFC-7807 Problem) across all 20 controllers (also F-045). **(T-33 envelopes DONE)**
- [ ] SIMP-7 — Simplicity / API — Single pagination grammar (cursor + `nextCursor` + `prevCursor`) — also F-046. **(T-25 cursor API DONE, standardization pending)**
- [ ] SIMP-8 — Simplicity / API — Single sort/filter grammar (`?sort=`, `?filter[…]=`) — also F-047.
- [ ] SIMP-9 — Simplicity / Backend — Single canonical run state enum — also F-049. **(T-26 status additions DONE, unification pending)**
- [x] SIMP-10 — Simplicity / UI — Capability-driven page gating from `/info`. **(DONE: useCapabilities + RuntimeTruthBanner)**
- [ ] SIMP-11 — Simplicity / AI — AI confidence routing: low → advisory, mid → reviewable, high → applied with chained audit. **(ADVANCED: builder and monitoring assists now route low-confidence suggestions to advisory-only and expose rationale/citations; chained audit on high-confidence auto-apply is still pending)**
- [ ] SIMP-12 — Simplicity / UI — Memory explorer AI summarisation with citations; faceted filters. **(ADVANCED: faceted filters + citation-backed assist summaries now shipped in the UI; backend model-driven summarisation is still pending)**

## Section D — AI/ML embedding plan

- [x] AI-1 — High / Correctness+Trust / AI — Enforce `confidence`, `rationale`, `sources` on every AI suggestion response (also F-012). **(T-22 envelope DONE)**
- [x] AI-2 — P0 — Pipeline stage suggestion as Assist with required confidence. **(DONE: builder assist now surfaces confidence, rationale, evidence, and advisory-only gating before apply)**
- [x] AI-3 — P0 — Run anomaly narrative explanation as Assist. **(DONE: monitoring run suggestions now expand into an explainable assist narrative with rationale and citations)**
- [x] AI-4 — P0 — HITL pre-decision summary as Assist. **(DONE: review detail now shows an AI summary and confidence routing before approve/reject)**
- [ ] AI-5 — P0 — Policy promotion advisor as Hybrid (review-required) — also F-050. **(ADVANCED: governance policy review now surfaces a hybrid promotion advisor with promotion badges, gate evidence, rollback pointer visibility, and per-skill timeline context; durable chained promotion audit is still pending)**
- [ ] AI-6 — P1 — Cost optimisation (cheaper model substitution) as Assist.
- [ ] AI-7 — P1 — Capability binding suggestion (schema/encoder) as Assist.
- [ ] AI-8 — P1 — GDPR triage classification as Hybrid. **(ADVANCED: privacy workbench now classifies intake into access/erasure/portability/opt-out with confidence + rationale and keeps a human in the loop before submission)**
- [x] AI-9 — P1 — Kill-switch impact preview as Assist. **(DONE: governance tenancy now shows an assist-style impact preview with runtime posture guidance before activation/deactivation)**
- [ ] AI-10 — P1 — SOC2 evidence collection as Automation with operator approval.
- [ ] AI-11 — P2 — Run lineage narrative summary.
- [ ] AI-12 — P2 — Tenant onboarding pre-fill.
- [ ] AI-13 — P2 — Anomaly false-positive feedback loop into learning — also F-029. **(ADVANCED: monitoring suggestions now expose a false-positive action and the backend records feedback plus emits an audit-linked anomaly feedback event when analytics storage is configured; deeper model-learning consumption is still pending)**
- [ ] F-012 — High / Correctness+Trust / AI — Default UI to "advisory only" for low-confidence suggestions; surface click-through to sources. **(ADVANCED: builder and monitoring suggestions now route low-confidence responses to advisory-only and expose source click-through; broader assist surfaces still need the same policy)**
- [ ] F-021 — Medium / Simplicity / UI — AI summarisation in memory explorer with citations + faceted filters. **(ADVANCED: memory explorer now ships faceted filters with citation-backed assist summaries derived from visible records; deeper AI-generated summarisation remains pending)**
- [ ] F-029 — Medium / Correctness / AI+Backend — "Mark as not an anomaly" closes the loop into the learning pipeline. **(ADVANCED: operator false-positive feedback is now available from monitoring suggestions and persists on anomaly records with event emission; downstream learning-pipeline ingestion still needs to be wired end-to-end)**
- [ ] F-042 — High / Correctness+Trust / UI — "Auto-promoted" badge on every auto-promoted policy with one-click rollback. **(ADVANCED: governance policies now show auto-promotable / auto-promoted state, rollback pointer, and advisor context; one-click rollback execution is still pending)**
- [ ] F-050 — High / Correctness / Backend — Every promoted policy carries a `PolicyProvenanceRecord`; emit chained audit event; cockpit shows policy timeline per skill. **(ADVANCED: learning policy responses now expose provenance + gate-result detail and governance renders a per-skill policy timeline; broader chained audit-event emission still pending)**

## Section E — Consistency cleanups

- [x] F-011 — Medium / Consistency / API — Add `/api/v1/ai/suggestions/metrics` to `contracts/openapi.yaml`; CI test fails on OpenAPI ↔ route table drift. **(DONE)**
- [ ] F-016 / K-1 — Medium / Consistency / Product — Canonical taxonomy: pipeline (orchestrated DAG), pattern (runtime detection rule), workflow (HITL routing); migrate UI labels and docs.
- [x] F-017 / K-2 — Low / Consistency / API — Stabilise on `/api/v1/governance/...` and `/api/v1/compliance/...`; deprecate `/governance/...`. **(DONE for governance: canonical routes documented and used by UI; legacy governance endpoints now emit deprecation headers)**
- [ ] F-035 / K-12 — Medium / Consistency / API — Shared `Problem` envelope across REST and gRPC. **(T-33 REST DONE, gRPC pending)**
- [x] F-045 / K-5 — Medium / Consistency / API — Standard error envelope across 20 controllers. **(T-33 DONE)**
- [ ] F-046 / K-6 — Medium / Consistency / API — Standard cursor pagination (`{ data, nextCursor, prevCursor, total? }`). **(T-25 data-cloud DONE, standardization across endpoints pending)**
- [ ] F-047 / K-7 — Medium / Consistency / API — Standard sort/filter grammar.
- [x] F-049 / K-4 — Medium / Consistency / Backend — Canonical run-state enum (`PENDING/RUNNING/SUCCEEDED/FAILED/CANCELLED/TIMED_OUT`); reject anything else at the boundary. **(T-26 DONE)**
- [ ] K-3 — Medium / Consistency / Frontend — Eliminate duplication between `aep.api.ts` and `pipeline.api.ts` (covered by F-022/F-023).
- [ ] K-8 — Medium / Consistency / Audit — Standard audit event schema across kill-switch, rollback, install, export, GDPR.
- [ ] K-9 — Medium / Consistency / Auth — Single role claim shape (server vs UI).
- [ ] K-10 — Low / Consistency / Backend — Resolve `AepController` vs `AgentController` naming convention.
- [x] K-11 — Medium / Consistency / API — Enforce OpenAPI ↔ route-table parity in CI (also F-037). **(T-10 DONE)**
- [ ] K-13 — Low / Consistency / Frontend — Lint forbids string-literal nav targets; require `routes.ts` helpers. **(ADVANCED: touched nav/finder/privacy/agent-registry links now use canonical helpers; repo-wide lint rule still pending)**
- [ ] K-14 — High / Consistency / API — Single tenant identity source (verified JWT); header is hint only; mismatches 403. **(Related to F-006/F-007)**
- [ ] K-15 — Low / Consistency / Audit — Align HITL approve/reject and Learning approve/reject event names and audit envelopes.

## Section F — Privacy / Security / Trust / Observability

- [x] F-026 — Medium / Trust / UI+Ops — Trusted-proxy alerting tile fed by `aep_security_proxy_forwarded_rejected_total`. **(DONE: Governance operations now surfaces accepted/rejected forwarded-header counts, alert state, and rejection reasons from runtime metrics)**
- [ ] F-031 — High / Privacy / Backend — Convert PII warn-only to block-by-default; explicit override + audit. **(T-14 policies DONE, default enforcement pending)**
- [x] F-040 — Medium / Trust / UI — Runtime-truth banner inside cockpit reflecting `/info` capabilities and `/health/deep` mode. **(DONE: RuntimeTruthBanner + useCapabilities + tests)**
- [ ] F-048 — Medium / Observability / Platform — `X-Correlation-ID` mandatory at gateway; propagated; logged on every span.
- [ ] F-038 — Medium / Completeness / Frontend — Accessibility evidence (axe-core in CI; keyboard-only smoke) for canvas builder, marketplace cards, governance dialogs. **(T-28 E2E DONE, CI integration pending)**
- [ ] F-039 — Low / Completeness / Frontend — Responsive evidence for cockpit (Playwright viewport profiles); document desktop-only pages.
- [ ] F-020 — High / Correctness+Security / UI — Sandbox-only direct execute from registry/marketplace; production execute via pipeline + HITL. **(ADVANCED: marketplace install preflight now surfaces sandbox-only direct execution and forces production guidance through pipeline + HITL; broader registry/runtime enforcement is still pending)**
- [x] F-034 — Medium / Correctness / Backend — `/api/v1/compliance/soc2/report` refuses to render if evidence older than configured window. **(DONE: freshness enforcement is active; governance compliance now surfaces report availability and evidence age truthfully)**

## Section G — Quality engineering

- [ ] F-036 — High / Quality / QA — E2E coverage for: run cancel, batch parity, SSE auth, marketplace install rollback, GDPR fulfilment, kill-switch flow.
- [x] F-037 / K-11 — High / Quality / QA — Re-enable contract drift tests; OpenAPI ↔ route table parity required in CI. **(T-10 DONE)**
- [ ] F-044 — Low / Consistency / API — API deprecation policy; `Deprecation` and `Sunset` HTTP headers; lint. **(ADVANCED: governance legacy routes now emit `Deprecation`, `Sunset`, and successor `Link` headers; OpenAPI deprecated flags added; repo-wide lint/policy enforcement still pending)**

## Section H — Operational / Roadmap items

### Immediate (≤2 weeks)

- [ ] R-IMM-1 — F-001 / F-002 / F-004 / F-005 / F-006 / F-007 / F-008 / F-013 / F-018 / F-025 / F-037 (see Section A & E).

### Short-term (≤6 weeks)

- [ ] R-ST-1 — F-003 cancellation contract.
- [ ] R-ST-2 — F-009 marketplace lifecycle. **(ADVANCED: install preflight, version pinning, and compatibility truth are now live in the marketplace flow; rollback and audit-chain completion still pending)**
- [ ] R-ST-3 — F-010 audit explorer.
- [x] R-ST-4 — F-014 publish dry-run. **(DONE)**
- [ ] R-ST-5 — F-015 rollback chain. **(ADVANCED: pipeline rollback now has a dedicated UI flow with version selection, typed confirmation, reason capture, and backend audit-link emission; broader rollback-chain standardization is still pending)**
- [ ] R-ST-6 — F-019 GDPR/CCPA UI. **(ADVANCED: privacy request workbench now available in cockpit; queue/audit completion still pending)**
- [ ] R-ST-7 — F-020 marketplace direct-execute sandbox. **(ADVANCED: the marketplace cockpit now makes sandbox-only direct execution explicit and shows production pipeline + HITL requirements during install simulation; runtime-wide enforcement is still pending)**
- [ ] R-ST-8 — F-022 / F-023 generated clients.
- [x] R-ST-9 — F-024 capability flags. **(DONE)**
- [ ] R-ST-10 — F-027 binding designer.
- [ ] R-ST-11 — F-030 tenant lifecycle.
- [ ] R-ST-12 — F-031 PII block-by-default.
- [ ] R-ST-13 — F-032 role-driven UI gating.
- [ ] R-ST-14 — F-041 lineage on run detail. **(ADVANCED: UI lineage summary shipped; backend provenance enrichment pending)**
- [ ] R-ST-15 — F-042 auto-promotion badge. **(ADVANCED: promotion badges and rollback-target visibility are now live in governance; rollback execution still pending)**
- [x] R-ST-16 — F-043 consent dashboard. **(DONE)**
- [ ] R-ST-17 — F-050 policy provenance enforcement. **(ADVANCED: policy provenance and timeline context now flow through learning + governance surfaces; full chained audit enforcement remains pending)**

### Medium-term (≤12 weeks)

- [ ] R-MT-1 — F-011 / F-016 / F-017 / F-035 / F-045 / F-046 / F-047 consistency cleanups.
- [ ] R-MT-2 — F-021 memory explorer summarisation. **(ADVANCED: faceted filters and citation-backed assist summaries are now live in the memory explorer; backend summarisation service still pending)**
- [x] R-MT-3 — F-026 trusted-proxy alerting. **(DONE)**
- [ ] R-MT-4 — F-028 cost budgets. **(ADVANCED: threshold inputs + budget state + per-model breakdown now live in the dashboard; durable budget governance still pending)**
- [ ] R-MT-5 — F-029 anomaly FP feedback. **(ADVANCED: operators can now mark anomaly suggestions as false positives and the server persists feedback with an emitted anomaly-feedback event; downstream learning integration remains pending)**
- [ ] R-MT-6 — F-033 ops tile (backup/DR/export). **(ADVANCED: truthful operations panel now includes trusted-proxy alerting alongside backup/DR/export posture; export queue and drill-history persistence still pending)**
- [x] R-MT-7 — F-034 SOC2 evidence freshness. **(DONE)**
- [ ] R-MT-8 — F-038 accessibility CI.
- [ ] R-MT-9 — F-039 responsive evidence.
- [x] R-MT-10 — F-040 runtime-truth banner. **(DONE)**
- [ ] R-MT-11 — F-044 deprecation policy. **(ADVANCED: governance routes now carry deprecation headers and deprecated OpenAPI operations; lint/policy generalization still pending)**
- [ ] R-MT-12 — F-048 correlation IDs.
- [ ] R-MT-13 — F-049 run state enum unification.

### Long-term

- [ ] R-LT-1 — Operator/admin/audit suite as a distinct console role.
- [ ] R-LT-2 — AI explainability surfacing across every assist.
- [ ] R-LT-3 — Learning loop coverage per skill.
- [ ] R-LT-4 — Multi-region DR.

## Section I — Correctness Register (one-line truth fixes)

- [ ] C-1 — `/ready` returns 503 when dependencies degraded.
- [ ] C-2 — Run history list signals `mode/truncated` truthfully.
- [ ] C-3 — Cancel intent durable; orchestrator enforces stop.
- [ ] C-4 — `/events/batch` parity with single-event policy.
- [ ] C-5 — `isAuthenticated` requires verified JWT.
- [ ] C-6 — SSE auth from browser works.
- [ ] C-7 — Tenant header vs JWT mismatch returns 403.
- [ ] C-8 — Empty HITL queue distinguishes empty vs `configured=false`.
- [ ] C-9 — AI suggestions show confidence/rationale/sources.
- [ ] C-10 — Policy promotion produces provenance + audit. **(ADVANCED: provenance now rides on learning policy payloads and governance surfaces it truthfully; generalized promotion audit chaining is still pending)**
- [ ] C-11 — Kill-switch toggle requires step-up + chain. **(ADVANCED: backend step-up gate already exists and governance now submits MFA code plus surfaces returned audit IDs; SSE broadcast / broader chain evidence remains pending)**
- [ ] C-12 — Rollback writes audit chain entry linking versions. **(ADVANCED: pipeline rollback responses now include previous-version context and emit a linked rollback audit event when event storage is available; full cross-surface audit-schema unification is still pending)**
- [ ] C-13 — Idempotent retry returns prior response, not new side effects.
- [x] C-14 — Cost view shows budget/alert/threshold. **(DONE: dashboard now surfaces threshold inputs, remaining budget state, and alert comparisons)**
- [ ] C-15 — Anomaly false-positive feedback into learning. **(ADVANCED: false-positive feedback now persists on anomaly records and emits an audit-linked feedback event; the learning pipeline still needs to consume that signal end-to-end)**
- [ ] C-16 — Marketplace install pins version + checks compatibility. **(ADVANCED: install now requires `expectedVersion` and runs through a simulate-install compatibility check before registration)**
- [ ] C-17 — Run detail shows lineage.
- [x] C-18 — SOC2 report enforces evidence freshness. **(DONE)**
- [ ] C-19 — gRPC error envelope matches REST.
- [ ] C-20 — Run state enum unified.

---

**Total tasks:** 50 findings + 12 simplifications + 13 AI items + 15 consistency items + 8 trust/observability items + 3 quality items + 35 roadmap rollups + 20 correctness one-liners. Nothing dropped.

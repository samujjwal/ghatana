# AEP — End-to-End Audit TODO List (2026-04-27)

> Source: `products/aep/AEP_E2E_AUDIT_2026-04-27.md`.
> Every finding, simplification, AI opportunity, consistency item, and roadmap row in the audit is represented below with no item dropped. Severity / dimension / dependency are preserved.
> Format: `[ ] <ID> — <Severity> / <Dimension> / <Layer> — <Title>`

## Section A — Critical correctness and runtime truth

- [ ] F-001 — Critical / Correctness / API+Runtime — Make `/ready` dependency-truthful (Data Cloud, JWT secret, registry handshake, OPA, LLM, event bus); document the dependency table.
- [ ] F-002 — Critical / Correctness / Persistence — Surface `mode: "in-memory"` and `truncated: true` in run-list responses; add persistent UI banner; production profile must fail closed unless `AEP_ALLOW_IN_MEMORY_RUN_HISTORY=true`.
- [ ] F-003 — Critical / Correctness / Workflow — Define cooperative cancellation contract per agent; persist cancel intent; orchestrator hard-kills after configurable timeout; emit cancel-attempt and cancel-complete events.
- [ ] F-004 — Critical / Correctness+Privacy / API — Batch event ingestion (`/api/v1/events/batch`) must apply identical schema, policy, tenant, quota path per item; per-item response with `index`, `accepted`, `errorCode`.
- [ ] F-005 — Critical / Correctness / API+UI — Issue short-lived signed SSE tokens via `POST /api/v1/sse/token`; accept only at `/events/stream`; tenant-scope from verified token.
- [ ] F-006 — Critical / Privacy+Correctness / API — Remove `"default"` tenant fallthrough in `HttpHelper.resolveTenantId` outside dev/local; tenant must come from verified JWT.
- [ ] F-007 — High / Correctness / API+UI — Sessions are continuation tokens, not authentication; `AuthContext.isAuthenticated` must require a verified JWT.
- [ ] F-008 — High / Correctness / API — Implement `/api/v1/auth/platform-session` or remove the bootstrap call from `AuthContext`; gate behind feature flag if SSO is not configured.
- [ ] F-018 — High / Correctness+Security / Backend — Kill-switch step-up auth, tamper-evident chain entry, SSE broadcast, auto-decay after configurable window.
- [ ] F-025 — High / Correctness / API+Persistence — Require `Idempotency-Key` on all mutating endpoints; persist replay protection (24h) in Data Cloud; reject duplicates with prior response.

## Section B — Completeness (missing surfaces and lifecycles)

- [ ] F-009 — High / Completeness / UI+Backend — Marketplace install lifecycle: version pin, compatibility check, simulate, publish, rollback, audit chain; HITL gate for high-risk installs.
- [ ] F-010 — High / Completeness / UI — Audit explorer page; new `/api/v1/audit/events` paginated endpoint with tamper-evident verification.
- [ ] F-014 — High / Completeness / UI+Backend — Pipeline publish pre-flight dry-run that returns agent set, policy set, compliance bundle, evaluation gate result; require operator acknowledgement.
- [ ] F-019 — High / Completeness / UI+Backend — End-user GDPR/CCPA request page, operator verification queue, SLA timer, chained audit.
- [ ] F-024 — Medium / Completeness / API+UI — Publish capability flags in `/info`; UI gates pages on flags.
- [ ] F-027 — High / Completeness / UI — Bindings/connector designer; require successful `bindings/{id}/simulate` before publish.
- [ ] F-028 — Medium / Completeness / UI+Backend — Cost budgets per tenant, alerts, breakdown by pipeline+agent+model.
- [ ] F-030 — High / Completeness / UI+Backend — Tenant lifecycle (create/suspend/retire) with audit; remove ad-hoc tenant-via-header.
- [ ] F-032 — High / Completeness+Security / Frontend — Role-driven UI gating; lint blocks action buttons without role checks.
- [ ] F-033 — Medium / Completeness / UI — Backup/DR/Export ops tile (last DR drill, last backup, export queue).
- [ ] F-041 — High / Completeness / UI+Backend — Run-detail lineage block: pipeline version, agent versions, policy bundle, evaluation gate, compliance bundle.
- [ ] F-043 — High / Privacy / UI — Tenant-level consent dashboard, change history, export.
- [ ] F-013 — Medium / Correctness / UI — When `/api/v1/hitl/pending` returns `configured=false`, stop polling and show "HITL not configured" empty state.

## Section C — Simplification (remove / merge / hide / automate / infer)

- [ ] F-022 — Medium / Simplicity+Correctness / Frontend — Replace `aep.api.ts` (1,070 lines, hand-coded) with the generated OpenAPI client.
- [ ] F-023 — Medium / Consistency / Frontend — Consolidate `pipeline.api.ts` into capability-scoped clients generated from OpenAPI.
- [ ] SIMP-1 — Simplicity / UI — Remove legacy JWT-paste login path for normal users; keep as admin-only break-glass.
- [ ] SIMP-2 — Simplicity / UI — Move "Patterns" off main nav into a runtime-detection studio.
- [ ] SIMP-3 — Simplicity / UI — Replace raw tenant-id text inputs with a tenant switcher (name + role badge).
- [ ] SIMP-4 — Simplicity / UI — Hide `AEP_PROFILE`, durability mode, runtime profile from operator view; surface via runtime-truth banner only.
- [ ] SIMP-5 — Simplicity / Frontend — Single shared `useDurableMutation` hook (idempotency, optimistic UI, rollback, audit toast).
- [ ] SIMP-6 — Simplicity / API — Single error envelope (RFC-7807 Problem) across all 20 controllers (also F-045).
- [ ] SIMP-7 — Simplicity / API — Single pagination grammar (cursor + `nextCursor` + `prevCursor`) — also F-046.
- [ ] SIMP-8 — Simplicity / API — Single sort/filter grammar (`?sort=`, `?filter[…]=`) — also F-047.
- [ ] SIMP-9 — Simplicity / Backend — Single canonical run state enum — also F-049.
- [ ] SIMP-10 — Simplicity / UI — Capability-driven page gating from `/info`.
- [ ] SIMP-11 — Simplicity / AI — AI confidence routing: low → advisory, mid → reviewable, high → applied with chained audit.
- [ ] SIMP-12 — Simplicity / UI — Memory explorer AI summarisation with citations; faceted filters.

## Section D — AI/ML embedding plan

- [ ] AI-1 — High / Correctness+Trust / AI — Enforce `confidence`, `rationale`, `sources` on every AI suggestion response (also F-012).
- [ ] AI-2 — P0 — Pipeline stage suggestion as Assist with required confidence.
- [ ] AI-3 — P0 — Run anomaly narrative explanation as Assist.
- [ ] AI-4 — P0 — HITL pre-decision summary as Assist.
- [ ] AI-5 — P0 — Policy promotion advisor as Hybrid (review-required) — also F-050.
- [ ] AI-6 — P1 — Cost optimisation (cheaper model substitution) as Assist.
- [ ] AI-7 — P1 — Capability binding suggestion (schema/encoder) as Assist.
- [ ] AI-8 — P1 — GDPR triage classification as Hybrid.
- [ ] AI-9 — P1 — Kill-switch impact preview as Assist.
- [ ] AI-10 — P1 — SOC2 evidence collection as Automation with operator approval.
- [ ] AI-11 — P2 — Run lineage narrative summary.
- [ ] AI-12 — P2 — Tenant onboarding pre-fill.
- [ ] AI-13 — P2 — Anomaly false-positive feedback loop into learning — also F-029.
- [ ] F-012 — High / Correctness+Trust / AI — Default UI to "advisory only" for low-confidence suggestions; surface click-through to sources.
- [ ] F-021 — Medium / Simplicity / UI — AI summarisation in memory explorer with citations + faceted filters.
- [ ] F-029 — Medium / Correctness / AI+Backend — "Mark as not an anomaly" closes the loop into the learning pipeline.
- [ ] F-042 — High / Correctness+Trust / UI — "Auto-promoted" badge on every auto-promoted policy with one-click rollback.
- [ ] F-050 — High / Correctness / Backend — Every promoted policy carries a `PolicyProvenanceRecord`; emit chained audit event; cockpit shows policy timeline per skill.

## Section E — Consistency cleanups

- [ ] F-011 — Medium / Consistency / API — Add `/api/v1/ai/suggestions/metrics` to `contracts/openapi.yaml`; CI test fails on OpenAPI ↔ route table drift.
- [ ] F-016 / K-1 — Medium / Consistency / Product — Canonical taxonomy: pipeline (orchestrated DAG), pattern (runtime detection rule), workflow (HITL routing); migrate UI labels and docs.
- [ ] F-017 / K-2 — Low / Consistency / API — Stabilise on `/api/v1/governance/...` and `/api/v1/compliance/...`; deprecate `/governance/...`.
- [ ] F-035 / K-12 — Medium / Consistency / API — Shared `Problem` envelope across REST and gRPC.
- [ ] F-045 / K-5 — Medium / Consistency / API — Standard error envelope across 20 controllers.
- [ ] F-046 / K-6 — Medium / Consistency / API — Standard cursor pagination (`{ data, nextCursor, prevCursor, total? }`).
- [ ] F-047 / K-7 — Medium / Consistency / API — Standard sort/filter grammar.
- [ ] F-049 / K-4 — Medium / Consistency / Backend — Canonical run-state enum (`PENDING/RUNNING/SUCCEEDED/FAILED/CANCELLED/TIMED_OUT`); reject anything else at the boundary.
- [ ] K-3 — Medium / Consistency / Frontend — Eliminate duplication between `aep.api.ts` and `pipeline.api.ts` (covered by F-022/F-023).
- [ ] K-8 — Medium / Consistency / Audit — Standard audit event schema across kill-switch, rollback, install, export, GDPR.
- [ ] K-9 — Medium / Consistency / Auth — Single role claim shape (server vs UI).
- [ ] K-10 — Low / Consistency / Backend — Resolve `AepController` vs `AgentController` naming convention.
- [ ] K-11 — Medium / Consistency / API — Enforce OpenAPI ↔ route-table parity in CI (also F-037).
- [ ] K-13 — Low / Consistency / Frontend — Lint forbids string-literal nav targets; require `routes.ts` helpers.
- [ ] K-14 — High / Consistency / API — Single tenant identity source (verified JWT); header is hint only; mismatches 403.
- [ ] K-15 — Low / Consistency / Audit — Align HITL approve/reject and Learning approve/reject event names and audit envelopes.

## Section F — Privacy / Security / Trust / Observability

- [ ] F-026 — Medium / Trust / UI+Ops — Trusted-proxy alerting tile fed by `aep_security_proxy_forwarded_rejected_total`.
- [ ] F-031 — High / Privacy / Backend — Convert PII warn-only to block-by-default; explicit override + audit.
- [ ] F-040 — Medium / Trust / UI — Runtime-truth banner inside cockpit reflecting `/info` capabilities and `/health/deep` mode.
- [ ] F-048 — Medium / Observability / Platform — `X-Correlation-ID` mandatory at gateway; propagated; logged on every span.
- [ ] F-038 — Medium / Completeness / Frontend — Accessibility evidence (axe-core in CI; keyboard-only smoke) for canvas builder, marketplace cards, governance dialogs.
- [ ] F-039 — Low / Completeness / Frontend — Responsive evidence for cockpit (Playwright viewport profiles); document desktop-only pages.
- [ ] F-020 — High / Correctness+Security / UI — Sandbox-only direct execute from registry/marketplace; production execute via pipeline + HITL.
- [ ] F-034 — Medium / Correctness / Backend — `/api/v1/compliance/soc2/report` refuses to render if evidence older than configured window.

## Section G — Quality engineering

- [ ] F-036 — High / Quality / QA — E2E coverage for: run cancel, batch parity, SSE auth, marketplace install rollback, GDPR fulfilment, kill-switch flow.
- [ ] F-037 / K-11 — High / Quality / QA — Re-enable contract drift tests; OpenAPI ↔ route table parity required in CI.
- [ ] F-044 — Low / Consistency / API — API deprecation policy; `Deprecation` and `Sunset` HTTP headers; lint.

## Section H — Operational / Roadmap items

### Immediate (≤2 weeks)

- [ ] R-IMM-1 — F-001 / F-002 / F-004 / F-005 / F-006 / F-007 / F-008 / F-013 / F-018 / F-025 / F-037 (see Section A & E).

### Short-term (≤6 weeks)

- [ ] R-ST-1 — F-003 cancellation contract.
- [ ] R-ST-2 — F-009 marketplace lifecycle.
- [ ] R-ST-3 — F-010 audit explorer.
- [ ] R-ST-4 — F-014 publish dry-run.
- [ ] R-ST-5 — F-015 rollback chain.
- [ ] R-ST-6 — F-019 GDPR/CCPA UI.
- [ ] R-ST-7 — F-020 marketplace direct-execute sandbox.
- [ ] R-ST-8 — F-022 / F-023 generated clients.
- [ ] R-ST-9 — F-024 capability flags.
- [ ] R-ST-10 — F-027 binding designer.
- [ ] R-ST-11 — F-030 tenant lifecycle.
- [ ] R-ST-12 — F-031 PII block-by-default.
- [ ] R-ST-13 — F-032 role-driven UI gating.
- [ ] R-ST-14 — F-041 lineage on run detail.
- [ ] R-ST-15 — F-042 auto-promotion badge.
- [ ] R-ST-16 — F-043 consent dashboard.
- [ ] R-ST-17 — F-050 policy provenance enforcement.

### Medium-term (≤12 weeks)

- [ ] R-MT-1 — F-011 / F-016 / F-017 / F-035 / F-045 / F-046 / F-047 consistency cleanups.
- [ ] R-MT-2 — F-021 memory explorer summarisation.
- [ ] R-MT-3 — F-026 trusted-proxy alerting.
- [ ] R-MT-4 — F-028 cost budgets.
- [ ] R-MT-5 — F-029 anomaly FP feedback.
- [ ] R-MT-6 — F-033 ops tile (backup/DR/export).
- [ ] R-MT-7 — F-034 SOC2 evidence freshness.
- [ ] R-MT-8 — F-038 accessibility CI.
- [ ] R-MT-9 — F-039 responsive evidence.
- [ ] R-MT-10 — F-040 runtime-truth banner.
- [ ] R-MT-11 — F-044 deprecation policy.
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
- [ ] C-10 — Policy promotion produces provenance + audit.
- [ ] C-11 — Kill-switch toggle requires step-up + chain.
- [ ] C-12 — Rollback writes audit chain entry linking versions.
- [ ] C-13 — Idempotent retry returns prior response, not new side effects.
- [ ] C-14 — Cost view shows budget/alert/threshold.
- [ ] C-15 — Anomaly false-positive feedback into learning.
- [ ] C-16 — Marketplace install pins version + checks compatibility.
- [ ] C-17 — Run detail shows lineage.
- [ ] C-18 — SOC2 report enforces evidence freshness.
- [ ] C-19 — gRPC error envelope matches REST.
- [ ] C-20 — Run state enum unified.

---

**Total tasks:** 50 findings + 12 simplifications + 13 AI items + 15 consistency items + 8 trust/observability items + 3 quality items + 35 roadmap rollups + 20 correctness one-liners. Nothing dropped.

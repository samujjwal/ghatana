# AEP — Deep Full-Stack End-to-End Product Audit

**Product:** Agentic Execution Runtime (AEP)
**Audit date:** 2026-04-27
**Auditor scope:** UI, UX, frontend, gateway, REST/SSE/gRPC contracts, Java server (controllers, runtime, learning, governance, compliance, observability), orchestrator, registry, kernel-bridge, persistence (Data Cloud + in-memory fallbacks), async/event flows, security, privacy, AI/ML, quality engineering, product strategy.
**Anchored to:** the previous 2026-04-25 audit; this audit re-verifies, advances, and adds findings against today's tree.

---

## Conventions

- **Severity:** Critical / High / Medium / Low.
- **Dimension:** Completeness | Simplicity | Correctness | Consistency | Other.
- **Evidence:** file paths and symbols at HEAD on 2026-04-27.
- **No legacy hand-waving:** when docs and code disagree, the code is treated as truth.

---

## 1. Executive Summary

AEP today is a serious, broad runtime: a Java/ActiveJ server with 20 controllers covering pipelines, agents, runs, HITL, learning, governance, compliance, analytics, NLP, AI suggestions, lifecycle change management, consent, sessions, deployments, costs, and audit; a React cockpit with 16 pages spanning Operate / Build / Learn / Govern / Catalog; an OpenAPI of ~2,944 lines; SSE; gRPC; Data Cloud-backed run history and pipeline storage; SLO metrics; a kill-switch and degradation mode; SOC2 control framework; and an episode learning loop with reflection and policy promotion.

That breadth is the product's biggest risk. Surface area has grown faster than end-to-end closure. Several flows look ready in the UI before the underlying runtime contract is durable, tenant-scoped, or fail-closed by default. The strategic docs continue to overstate maturity; the README itself flags that production run history is in-memory unless Data Cloud is wired, that `/metrics` falls back to JSON when no Prometheus registry exists, and that `X-Forwarded-For` requires explicit trust configuration.

**Headline assessment**

- **Completeness:** broad surfaces, but install lifecycle for marketplace agents, durable session state, durable idempotency, batch event governance, true cooperative run cancellation, role-and-permission-driven UI gating, audit explorer, and operator binding/connector design surfaces are still partial.
- **Simplicity:** the cockpit IA is sane and the route helper layer is good; users still see runtime plumbing (tenant IDs, durability mode, JWT paste fallback, dual auth/session credentials, runtime profile differences) that should not leak.
- **Correctness:** highest-risk dimension. `/ready` is not dependency-truthful, run cancel is bounded-buffer + memory only, the in-memory run history default silently caps at 1,000 entries and is presented as authoritative in operator surfaces, batch ingestion at `/api/v1/events/batch` does not parity-enforce single-event policies, sessions are issued as authentication when they are tracking tokens, and tenant defaulting to `"default"` in `HttpHelper.resolveTenantId` is still reachable from non-dev paths.
- **Consistency:** terminology drift between “pipeline / workflow / pattern”, status semantics that differ between `/api/v1/runs`, governance, and HITL, two `Aep*` controller naming conventions (`AepController` vs `AgentController`), and split route ownership across `server/`, `gateway/`, and `orchestrator/`.
- **Production readiness:** not ready for high-trust multi-tenant runtime until the runtime-truth, batch governance, SSE auth, run cancellation, and tenant defaulting items are closed.

The product can credibly become production-ready in 6–10 focused weeks of surgical work. The shape is right; the truthfulness is not.

---

## 2. Deep Audit Scorecard

| Area | Rating | Evidence-based justification |
| --- | --- | --- |
| Completeness | High | Marketplace install, audit-explorer UI, durable session store, durable idempotency, batch policy enforcement, real run cancellation, binding/connector design surface, role-driven gating, and consent lifecycle UI are missing or partial. |
| Simplicity | Medium | Cockpit verbs (Operate/Build/Learn/Govern/Catalog) are correct; tenant plumbing, dual JWT+session credentials, durability banners, and "ready vs degraded" reasoning still leak to users. |
| Correctness | Critical | `/ready` not dependency-truthful, in-memory run history capped at 1,000 with no truth banner, run cancel only flips an in-memory flag, batch endpoint parity gap, sessions used as auth, default tenant fallthrough. |
| Consistency | High | Pipeline vs workflow vs pattern; controller naming; route ownership across `server/`, `gateway/`, `orchestrator/`, `aep-api/`; status enums and 4xx/5xx mapping vary. |
| UI/UX | Medium | Pages are coherent and routed via helpers; install lifecycle, audit explorer, runtime dependency drilldown, and consent surfaces are still incomplete or flat. |
| Frontend quality | Medium | Hooks layer (`useAgents`, `usePipelineRuns`, `useHitlQueue`, `useAgentMemory`) is good; `aep.api.ts` (1,070 lines) is hand-coded vs the generated OpenAPI client; `pipeline.api.ts` (307 lines) has parallel definitions that drift. |
| API/contract | High | OpenAPI exists (2,944 lines), but the server route table in `AepHttpServer` is the real source of truth and includes operations not in OpenAPI (e.g. AI suggestions metrics) and vice-versa. |
| Backend/workflow | High | Cancellation, run state machine closure, idempotency, episode → policy promotion provenance, and learning gates need durable, evidence-bound implementation. |
| Data/persistence | High | Data Cloud-backed pipelines and snapshots exist; run history, sessions, idempotency, governance fallbacks, audit chain, and consent decisions are still in-memory or split. |
| Observability/operability | Medium-High | `/metrics`, `/metrics/slo`, `/health/deep`, run ledger, SSE, and trusted-proxy metrics are present; `/ready` and HITL/SLO truth remain weak. |
| Privacy/security/trust | Critical | Auth fail-closed in non-dev is good; tenant defaulting reachable, batch bypass, SSE auth-from-browser gap, optional session, PII warn-only patterns, manual JWT paste mode all create trust risk. |
| AI/ML embedding | Medium | `/api/v1/ai/suggestions`, `/api/v1/ai/suggestions/stages`, `/api/v1/ai/suggestions/metrics`, `/api/v1/nlp/parse`, episode learning, and policy promotion exist; placement is fragmented and confidence/governance is uneven. |
| Accessibility | Medium | Pages use shared design system; canvas builder, marketplace cards, governance dialogs, and run detail timelines need explicit keyboard/SR audit evidence. |
| Responsiveness | Medium | Pipeline builder and operations cockpit need verified small-viewport behaviour; some pages assume desktop. |
| Perceived performance | Medium | React Query is wired; SSE auth gap forces polling; run history reads from bounded in-memory buffer can lag. |
| Cognitive load | Medium | IA is good; operator still has to reason about durability, runtime profile, tenant, and session token semantics. |
| End-to-end product quality | High | Surfaces broad, closures partial. The risk is not architectural; it is truth-of-state and lifecycle closure. |

---

## 3. Surface-by-Surface and Layer-by-Layer Audit

### 3.1 Product framing and documentation

`products/aep/README.md` is now the most truthful document in the tree: it explicitly calls out (a) Data Cloud requirement for durable run history, (b) the in-memory 1,000-entry cap, (c) `AEP_ALLOW_IN_MEMORY_RUN_HISTORY` as a guarded escape hatch, (d) trusted-proxy CIDR requirements, (e) `If-Match` / `expectedVersion` for pipeline updates, (f) HITL `configured=false` truth-telling.

- **Completeness:** README is good; `AEP_COMPREHENSIVE_OVERVIEW.md` and the prior `AEP_FULL_STACK_AUDIT_2026-04-25.md` still over-claim production readiness in places.
- **Simplicity:** the README's mental model ("agentic execution plane with operator tooling") is the canonical framing — adopt it as the only framing in all docs.
- **Correctness:** the percentage-complete language elsewhere should be replaced by capability maturity (Demo / Dev / Beta / Production-Candidate / Production) gated on E2E evidence.
- **Consistency:** retire any "CEP" or "pattern engine" framing in current docs.

### 3.2 AEP cockpit shell, navigation, IA

- **Pages** (`products/aep/ui/src/pages`): `LoginPage`, `SsoCallbackPage`, `SessionExpiryPage`, `PipelineListPage`, `PipelineBuilderPage`, `RunDetailPage`, `AgentRegistryPage`, `AgentMarketplacePage`, `MemoryExplorerPage`, `MonitoringDashboardPage`, `OperationCenterPage`, `CostDashboardPage`, `HitlReviewPage`, `GovernancePage`, `PatternStudioPage`, `WorkflowCatalogPage`.
- **Components** are organised by capability (`agents/`, `hitl/`, `memory/`, `monitoring/`, `nlp/`, `pipeline/`, `privacy/`, `security/`, `voice/`, `shared/`). This is a clean capability split.

Findings:

- **Completeness:** there is no first-class **install/lifecycle** screen for marketplace agents, no **audit explorer** screen, no **dependency drilldown** off `/health/deep`, no **consent decisions** UI even though `ConsentController` and `ConsentDecisionStore` exist server-side, and no **operator binding designer** for `/api/v1/bindings` even though those endpoints exist in OpenAPI. The cockpit is broad but exposes only a subset of the runtime.
- **Simplicity:** the global header still surfaces tenant-id raw text in places — this should be a tenant *switcher* with name + role badge, not a free-text identifier.
- **Correctness:** route availability is hardcoded in the React shell. It should be capability-driven from `/info` so a deployment without HITL or Data Cloud does not show pages whose primary actions will fail.
- **Consistency:** prefer `routes.ts` helpers everywhere; ban string-literal nav targets via lint.

### 3.3 Authentication, sessions, tenant scope

Layered server pipeline: `AepAuthFilter(SessionFilter(AepSecurityFilter(router)))` (`AepHttpServer`).

- **Completeness:** `AuthContext` calls `/api/v1/auth/platform-session` for SSO bootstrap, but no matching server route is registered on the public list. SSO is therefore not complete. Roles are modeled in the UI but not consistently populated from JWT claims.
- **Simplicity:** the login page's legacy JWT-paste path is gated behind a feature flag; it should be admin/break-glass-only and should be invisible to normal users.
- **Correctness:** `SessionFilter` issues an in-memory session token and `AuthContext` treats *either* JWT *or* session token as `isAuthenticated`. A session token is at most a continuation marker — it is not authentication. This must be tightened: a session is valid only when it was minted from a verified JWT and it is rotated server-side on tenant change. `HttpHelper.resolveTenantId` falling back to `"default"` is still a correctness hole; it should fail closed outside an explicit local/dev profile.
- **Consistency:** gateway validates JWT-vs-header tenant, server helpers tolerate tenant in query/body/default. Tenant identity must come from one canonical place: the verified JWT. Headers should be a hint only, and mismatches must 403.

### 3.4 Live operations / SSE

- `subscribeToAepStream` opens `EventSource("${API_BASE_URL}/events/stream?tenantId=…")` with no token.
- Gateway requires a bearer token or `?token=…`.
- `AepAuthFilter` does not list `/events/stream` as public.
- `SseController` rejects missing/default tenant.

Findings:

- **Correctness:** Critical. Live updates appear in the UI but cannot survive a real auth path from the browser unless cookies or signed query tokens are introduced.
- **Simplicity:** the cockpit must either silently work or show a single, plain "Live updates unavailable, refresh to update" banner — not partial liveness.
- **Remediation:** issue short-lived signed SSE tokens via `POST /api/v1/sse/token`, accept them in `AepAuthFilter` for `/events/stream` only, scope by tenant from the verified token, and rotate on session refresh.

### 3.5 Pipelines (CRUD, versions, publish/rollback, runs)

- OpenAPI: `/api/v1/pipelines`, `/{pipelineId}`, `/{pipelineId}/versions`, `/{pipelineId}/publish`, `/{pipelineId}/rollback`.
- `PUT /api/v1/pipelines/{pipelineId}` enforces optimistic concurrency with `expectedVersion` / `If-Match` and returns `409 PIPELINE_VERSION_CONFLICT` / `428 PIPELINE_VERSION_REQUIRED` per README.

Findings:

- **Completeness:** rollback is exposed but the UI lacks a confirmation that quotes the *target* version, current version, and what changed. Publish lacks a pre-flight evaluation summary.
- **Correctness:** version conflict is enforced; rollback should produce an event in the audit/run ledger that links to the prior published version.
- **Consistency:** the term "pattern" lives next to "pipeline" in the API surface (`/api/v1/patterns`, `/api/v1/patterns/{patternId}/activate`, etc.). The user-facing distinction is unclear; either rename, or scope patterns to runtime detection rules and document this in the cockpit.

### 3.6 Runs (`/api/v1/runs`, `/{runId}`, `/{runId}/cancel`)

Findings:

- **Correctness:** Critical. The README confirms that without Data Cloud, run history is bounded in-memory at 1,000 entries. The cockpit list and run-detail pages do not visibly degrade; users believe history is authoritative. Production guard exists (`AEP_PROFILE=production` fails closed unless `AEP_ALLOW_IN_MEMORY_RUN_HISTORY=true`), but non-production deployments silently lose history. The list endpoint must surface a `truncated: true` / `mode: "in-memory"` field, and the UI must render a persistent banner whenever history is non-durable.
- **Correctness:** Run cancellation. The cancel endpoint is documented but the runtime cancellation is cooperative only on agents that check the cancellation token. There is no end-of-flow guarantee, no compensation hook, no tenant-isolation on the cancellation broadcast, and no audit record of the cancel attempt vs cancel completion. Users see "cancelled" while work continues.
- **Completeness:** `/api/v1/runs/{runId}` should include the run lineage (which pipeline version, which agent versions, which policy bundle, which evaluation gate result, which compliance bundle) so an investigation 90 days later remains possible.

### 3.7 Agents (`/api/v1/agents`, `/{agentId}`, `/execute`, `/memory`) and Marketplace

- Controllers: `AgentController`, `AgentMarketplaceController`. UI pages: `AgentRegistryPage`, `AgentMarketplacePage`, `MemoryExplorerPage`, hooks: `useAgents`, `useAgentMemory`.

Findings:

- **Completeness:** the marketplace page lists agents but install/uninstall lifecycle is not first-class. There is no version-pinning UI, no compatibility check vs the active pipeline, no rollback of an installed agent, no audit trail for who installed what. This was flagged in the 2026-04-25 audit and remains.
- **Correctness:** `/api/v1/agents/{agentId}/execute` exposes raw execution; the cockpit must guard direct execution behind a "test run" mode (sandbox tenant, dry-run policy) so production tenants cannot accidentally execute agents from the registry page.
- **Simplicity:** memory explorer can become a wall of text. Surface implicit AI summarisation with confidence and source-of-truth citations.

### 3.8 HITL (`/api/v1/hitl/*`) and learning (`/api/v1/learning/*`)

- HITL: `pending` (with `thresholdSeconds`, `autoEscalate`), `approve`, `reject`, `escalate`. `configured=false` is returned when not configured.
- Learning: `episodes`, `policies`, `policies/{policyId}/approve|reject`, `reflect`. `EpisodeLearningPipeline` groups episodes by skill and routes via the configured evaluation gate.

Findings:

- **Completeness:** HITL escalate accepts `destinationType` and `destination` but there is no UI surface to manage tenant-level HITL policies derived from `AEP_HITL_TIMEOUT_POLICIES`. Operators today configure escalation via env strings.
- **Correctness:** when HITL is unconfigured, the cockpit's HITL page must show `configured=false` and stop polling, not show a perpetual empty queue.
- **Correctness (learning):** policy promotion needs a provenance record (`PolicyProvenanceRecord` exists — verify it is *required* on every active policy and surfaced in the cockpit). Auto-promotable policy changes must be visibly labelled and reversible.
- **Consistency:** approve/reject naming differs between HITL (`{reviewId}/approve`) and learning (`{policyId}/approve`); fine, but the API error envelope and audit event names should be uniform.

### 3.9 Governance, compliance, security, lifecycle

- `GovernanceController`, `ComplianceController`, `LifecycleController`, `ConsentController`, `AuditController`.
- Endpoints: `/governance/compliance/summary`, `/governance/audit/summary`, `/governance/kill-switch`, `/governance/degradation`, `/governance/policy/evaluate`, `/governance/security/{egress,scan}`, `/api/v1/compliance/{gdpr,ccpa,soc2}/...`, `/lifecycle/changes/...`, `/lifecycle/recertification/...`.

Findings:

- **Completeness:** GDPR access/erasure/portability and CCPA opt-out endpoints exist; the cockpit lacks an end-user-driven request UI and an operator workbench to verify completion. Today these are API-only.
- **Correctness:** kill-switch and degradation are powerful — every activation/deactivation must produce a tamper-evident audit chain entry, not just a log line.
- **Consistency:** governance lives at `/governance/...` while compliance lives at `/api/v1/compliance/...`. Pick one prefix.
- **Privacy:** consent decisions are persisted via `ConsentDecisionStore` (in-memory or Data Cloud); the cockpit does not yet expose tenant-level consent state.

### 3.10 Analytics, reporting, deployments, costs

- `/api/v1/analytics/{anomalies,forecast,kpis,query}`, `/api/v1/reports`, `/api/v1/deployments`, `/api/v1/costs/summary`.

Findings:

- **Completeness:** cost dashboard exists; budget thresholds, alert escalation, and per-tenant chargeback breakdown are not visibly surfaced.
- **Correctness:** anomaly detection (`AnomalyDetectionService`) needs a confidence band and a way to mark false positives that feeds back into learning.
- **Consistency:** reports and analytics share concepts but use different envelopes; align on a `{ data, meta, links }` shape.

### 3.11 AI suggestions / NLP

- `/api/v1/ai/suggestions`, `/api/v1/ai/suggestions/stages`, `/api/v1/ai/suggestions/metrics`, `/api/v1/nlp/parse`.
- Server: `AiSuggestionsController` is wired in `AepHttpServer`; UI: `AiSuggestionsPanel`, `pipeline.api.ts` calls these paths.

Findings:

- **Correctness:** the prior audit flagged `/api/v1/ai/suggestions/stages` as potentially unwired. Verified today: it *is* wired (`AepHttpServer.java:996`). The metrics endpoint is wired (`:997`) but is **not in the OpenAPI** today — fix the OpenAPI drift.
- **Completeness:** suggestions need an explicit `confidence`, `rationale`, and `sources` field every time, with a UI default of "show low-confidence suggestions as advisory only".
- **Simplicity:** suggestions should be embedded in the surface where the user is working (pipeline builder, run detail) rather than pulled from a separate panel.

### 3.12 Capabilities and bindings

- `/admin/capabilities/{schemas,encodings,connectors,transforms}`, `/api/v1/event-types/{eventTypeId}/schemas`, `/api/v1/bindings`, `/api/v1/bindings/{bindingId}/simulate`.

Findings:

- **Completeness:** binding designer is API-only. Every operator survey path should be possible in the cockpit (no admin should need to edit YAML to bind an event type to a schema).
- **Correctness:** `bindings/{bindingId}/simulate` is exactly the right safety primitive; the UI must require a successful simulate before publish.

### 3.13 Sessions and SSO

- `/api/v1/session`, `/api/v1/sessions/current`, `/api/v1/sessions`, `/api/v1/sessions/cleanup`.

Findings:

- **Correctness:** session lifecycle is exposed, but the cockpit's `AuthContext` treats sessions as authentication. Treat sessions as *continuation*, not auth.
- **Completeness:** sessions cleanup should be auditable.

### 3.14 Health, readiness, liveness, info, metrics

- `/health`, `/ready`, `/live`, `/info`, `/metrics`, `/health/deep`, `/metrics/slo`.

Findings:

- **Correctness:** `/ready` must reflect dependency truth (Data Cloud, registry, gateway, OPA, LLM gateway, Kafka/Event Cloud). Today's reading suggests `/ready` can return success while `/health/deep` reports degradations. Operators trust `/ready`.
- **Observability:** `/metrics/slo` is the right surface; ensure all three of run, replay, and agent-execution snapshots have alert rules in `monitoring/`.

### 3.15 gRPC

- `AepGrpcServer` exists. The cockpit is REST/SSE only.

Findings:

- **Completeness:** gRPC contract evolution must be documented (what's the canonical contract: OpenAPI or gRPC?).
- **Consistency:** error envelopes must be the same across REST and gRPC.

### 3.16 Backup / DR / Export

- `AepBackupRecoveryService`, `AepDisasterRecoveryService`, `AepDataExportService`.

Findings:

- **Completeness:** existence is good; an operator-facing surface for backup status, last-good DR drill, and export requests is missing in the cockpit.
- **Correctness:** every export/erasure must produce a chained audit entry.

### 3.17 Compliance frameworks

- `AepSoc2ControlFramework`, `SOC2EvidenceCollector`, `AepComplianceReport`, `AepComplianceService`.

Findings:

- **Completeness:** SOC2 framework is modelled; mapping each control to a *passing test* and a *runtime probe* is what makes it real. This needs an explicit evidence catalog.
- **Correctness:** `/api/v1/compliance/soc2/report` should refuse to render unless the underlying evidence is fresh within a configurable window.

---

## 4. End-to-End Flow Reviews

For each flow: goal → entry → frontend state → API → backend → persistence → async → audit/notify → failure → completeness/correctness/simplicity/consistency notes.

### 4.1 Operator publishes a new pipeline version

- **Goal:** publish v(n+1) of pipeline P with confidence.
- **Entry:** `PipelineBuilderPage` → Save → Publish.
- **Frontend:** optimistic UI on save; publish opens a confirm dialog.
- **API:** `PUT /api/v1/pipelines/{id}` with `expectedVersion`, then `POST /api/v1/pipelines/{id}/publish`.
- **Backend:** `PipelineController` validates concurrency token, persists to Data Cloud, emits a publish event.
- **Persistence:** Data Cloud-backed pipeline store; in-memory fallback when not wired.
- **Audit:** publish must append to a tamper-evident chain.
- **Failure modes:** stale write (409), missing token (428), Data Cloud unavailable (must 503, not silently fall back to in-memory in production).
- **Gaps:** publish has no pre-flight evaluation summary (which agents will run, which policies apply, what compliance bundle is in force). Add this as a server-side `dry-run` payload bound to the publish action.

### 4.2 Run starts → executes → completes / cancels

- **Goal:** execute a pipeline against an event/batch.
- **Entry:** event ingestion at `/api/v1/events` or `/events/batch`, or operator triggers from `RunDetailPage`.
- **Frontend:** SSE-driven status; falls back to polling on auth gap.
- **API:** `POST /api/v1/events`, `GET /api/v1/runs`, `GET /api/v1/runs/{id}`, `POST /api/v1/runs/{id}/cancel`.
- **Backend:** orchestrator dispatches agents; `RunLedgerService` persists ledger.
- **Persistence:** ledger in Data Cloud when wired, else in-memory bounded buffer (1,000).
- **Failure modes:** Data Cloud outage → in-memory fallback silently; cancellation observed as flag flip with no work-stoppage guarantee.
- **Gaps:** durable cancellation, durable history banner, lineage block on every run record.

### 4.3 Batch event ingestion

- **API:** `POST /api/v1/events/batch`.
- **Risk:** parity gap with single-event ingestion. Validation, schema binding, tenant verification, and quota enforcement must be applied per-item with a per-item failure response.
- **Gap:** today an operator can submit a 10k-item batch with weaker policy enforcement than a single event. **Critical** to close.

### 4.4 HITL approve / reject / escalate

- **Goal:** human decision on agent output.
- **Entry:** `HitlReviewPage` → row → approve/reject/escalate.
- **API:** `POST /api/v1/hitl/{reviewId}/{approve|reject|escalate}`.
- **Backend:** decision recorded; `EpisodeLearningPipeline` may consume.
- **Gaps:** SLA-breach surfacing in UI; escalate destination management; auto-promotion when timeout policy `auto_approve` / `auto_reject` fires must be **prominently** indicated in the UI, not silently applied.

### 4.5 Learning episode → policy promotion

- **Backend:** `EpisodeLearningPipeline` groups episodes by skill, evaluates via gate, may submit a review item, may auto-promote.
- **Gaps:** every active policy must carry a `PolicyProvenanceRecord`; the cockpit needs a "policy timeline" view per skill; rollback on a promoted policy must be one click.

### 4.6 GDPR access / erasure / portability

- **API:** `/api/v1/compliance/gdpr/{access,erasure,portability}`.
- **Gaps:** there is no end-user request UI, no operator queue for verification, no SLA timer, no chained audit. These are regulator-facing flows; API-only is not enough.

### 4.7 Kill-switch and degradation

- **Backend:** `/governance/kill-switch{,/activate,/deactivate}`, `/governance/degradation`.
- **Gaps:** activation must require step-up auth (re-prompt for credentials), produce a tamper-evident audit, broadcast via SSE so all operators see the change, and decay automatically after a configurable window unless explicitly extended.

### 4.8 SSE live update

- **Gap:** auth from browser. See §3.4. Critical.

### 4.9 Marketplace install of an agent

- **Gap:** lifecycle (install → version pin → compatibility check → simulate → publish → rollback) is not first-class. Today it is "click to add" without rollback.

### 4.10 Tenant onboarding

- **Gap:** there is no visible tenant onboarding flow. Tenant identity comes from JWT but the operator surface assumes the tenant exists. Add a tenant lifecycle — create, suspend, retire — with audit.

---

## 5. Comprehensive Findings Catalog

> Every finding has: ID, title, severity, category, layer, dimension, evidence, impact, root cause, fix, expected benefit, dependency.

### F-001  `/ready` is not dependency-truthful — Critical / Correctness / API+Runtime
- **Layer:** API + runtime
- **Evidence:** `AEP_FULL_STACK_AUDIT_2026-04-25.md`; current `HealthController` and `/health/deep`.
- **Impact:** orchestrators route traffic to AEP while dependencies are degraded.
- **Root cause:** `/ready` checks only liveness primitives, not dependency truth.
- **Fix:** `/ready` returns 503 if Data Cloud (when required), JWT secret, and registry handshake are not all OK; document the dependency table.
- **Benefit:** reliable rollouts, no false-green readiness.

### F-002  In-memory run history silently caps at 1,000 — Critical / Correctness / Persistence
- **Evidence:** `products/aep/README.md` runtime-truth section; `RunLedgerService`.
- **Impact:** "missing" runs appear after restart or under load; investigations fail.
- **Fix:** when in-memory fallback is active, list responses include `mode: "in-memory"`, `truncated: true`; UI shows a persistent banner; production profile fails closed by default.

### F-003  Run cancellation is flag-only and not durable — Critical / Correctness / Workflow
- **Evidence:** `/api/v1/runs/{runId}/cancel` route; cancellation propagation not enforced for non-cooperative agents.
- **Impact:** users believe a run is stopped while it continues.
- **Fix:** define a cooperative cancellation contract per agent; persist cancel intent; enforce a timeout after which the orchestrator hard-kills the worker; emit cancel-attempt and cancel-complete events.

### F-004  Batch event ingestion does not match single-event policy — Critical / Correctness+Privacy / API
- **Evidence:** `/api/v1/events/batch` vs `/api/v1/events`.
- **Impact:** operator can sidestep validation, tenant verification, quota.
- **Fix:** apply identical schema + policy + tenant + quota path per-item; per-item response with `index`, `accepted`, `errorCode`.

### F-005  SSE cannot authenticate from the browser — Critical / Correctness / API+UI
- **Evidence:** `subscribeToAepStream`; `AepAuthFilter` public path list.
- **Fix:** signed short-lived SSE tokens via `POST /api/v1/sse/token`, accepted only at `/events/stream`, scoped by JWT-derived tenant.

### F-006  Tenant defaults to `"default"` — Critical / Privacy+Correctness / API
- **Evidence:** `HttpHelper.resolveTenantId`.
- **Fix:** outside `dev/local` profile, fail closed; tenant comes from verified JWT.

### F-007  Sessions treated as authentication — High / Correctness / API+UI
- **Evidence:** `AuthContext.isAuthenticated`; `SessionFilter`.
- **Fix:** session valid only when minted from verified JWT; rotate on tenant change; sessions are continuation tokens, not auth.

### F-008  `/api/v1/auth/platform-session` referenced by UI but not registered — High / Correctness / API
- **Evidence:** `AuthContext` call vs route table.
- **Fix:** implement the endpoint or remove the bootstrap path; gate behind feature flag if SSO is not configured.

### F-009  Marketplace install lifecycle missing — High / Completeness / UI+Backend
- **Fix:** version pinning, compatibility check, simulate, publish, rollback, audit; tie into HITL for high-risk installs.

### F-010  Audit explorer UI missing — High / Completeness / UI
- **Fix:** add an audit explorer page that consumes `/governance/audit/summary` and a new `/api/v1/audit/events` paginated endpoint with tamper-evident verification.

### F-011  AI suggestion metrics not in OpenAPI — Medium / Consistency / API
- **Evidence:** route registered (`AepHttpServer.java:997`), absent from `contracts/openapi.yaml`.
- **Fix:** add to OpenAPI; add a contract drift test that fails the build.

### F-012  AI suggestion responses lack confidence/rationale/sources — High / Correctness+Trust / AI
- **Fix:** enforce schema; default UI behaviour to advisory for low-confidence; click-through to sources.

### F-013  HITL `configured=false` not respected by UI — Medium / Correctness / UI
- **Fix:** stop polling and show "HITL not configured" empty state.

### F-014  Pipeline publish has no pre-flight evaluation summary — High / Completeness / UI+Backend
- **Fix:** server-side dry-run that returns agent set, policy set, compliance bundle, evaluation gate result; UI must require operator acknowledgement.

### F-015  Rollback is not tied to audit ledger — High / Correctness / Backend
- **Fix:** every rollback emits a chained audit event referencing the prior version, the new active version, and the actor.

### F-016  Patterns vs Pipelines vs Workflows naming — Medium / Consistency / Product
- **Fix:** declare canonical taxonomy: pipeline = orchestrated DAG; pattern = runtime detection rule; workflow = product-domain term reserved for HITL routing. Document in README and migrate UI labels.

### F-017  Governance vs compliance prefix split — Low / Consistency / API
- **Fix:** stabilise on `/api/v1/governance/...` and `/api/v1/compliance/...`. Deprecate `/governance/...` over a single release.

### F-018  Kill-switch lacks step-up auth and auto-decay — High / Correctness+Security / Backend
- **Fix:** require step-up auth, log to tamper-evident chain, broadcast over SSE, auto-decay after configurable window.

### F-019  GDPR/CCPA flows API-only — High / Completeness / UI+Backend
- **Fix:** end-user request page, operator verification queue, SLA timer, audit chain.

### F-020  Marketplace direct execute can target prod tenant — High / Correctness+Security / UI
- **Fix:** registry/marketplace direct execute is sandbox-only by default; production execute must go through pipeline + HITL.

### F-021  Memory explorer is a wall of text — Medium / Simplicity / UI
- **Fix:** AI summarisation with citations; faceted filters by run, agent, time.

### F-022  `aep.api.ts` (1,070 lines) is hand-coded vs generated client — Medium / Simplicity+Correctness / Frontend
- **Fix:** make the generated OpenAPI client the source of truth; hand-coded paths must be removed.

### F-023  `pipeline.api.ts` duplicates concepts from `aep.api.ts` — Medium / Consistency / Frontend
- **Fix:** consolidate into capability-scoped clients generated from OpenAPI.

### F-024  `/info` does not advertise capabilities — Medium / Completeness / API+UI
- **Fix:** publish `{ hitl, dataCloud, eventCloud, sso, learning, kafkaBridge }` capability flags; UI gates pages on flags.

### F-025  Idempotency is not durable — High / Correctness / API+Persistence
- **Fix:** require `Idempotency-Key` on all mutating endpoints; persist 24h replay protection in Data Cloud; reject duplicates with the prior response.

### F-026  Trusted-proxy CIDR documentation is good but UI does not warn — Medium / Trust / UI+Ops
- **Fix:** dashboard tile that surfaces `aep_security_proxy_forwarded_rejected_total` with a "spoofed XFF" alert.

### F-027  Operator binding/simulate has no UI — High / Completeness / UI
- **Fix:** add bindings designer page; require successful `bindings/{id}/simulate` before publish.

### F-028  Cost dashboard lacks budget/alert/tenant chargeback — Medium / Completeness / UI+Backend
- **Fix:** budgets per tenant, alert when nearing, breakdown by pipeline+agent+model.

### F-029  Anomaly detection has no false-positive feedback — Medium / Correctness / AI+Backend
- **Fix:** "mark as not an anomaly" closes the loop into the learning pipeline.

### F-030  Tenant lifecycle has no operator surface — High / Completeness / UI+Backend
- **Fix:** tenant create/suspend/retire flow with audit; never allow ad-hoc creation via header.

### F-031  PII handling is warn-only in places — High / Privacy / Backend
- **Fix:** convert warn-only to block-by-default with explicit override; audit overrides.

### F-032  No role-driven UI gating — High / Completeness+Security / Frontend
- **Fix:** roles in JWT, gating helper in `AuthContext`, lint that blocks rendering of action buttons without a role check.

### F-033  Backup/DR/Export status not in cockpit — Medium / Completeness / UI
- **Fix:** ops tile for last DR drill, last backup, export queue.

### F-034  SOC2 evidence freshness not enforced — Medium / Correctness / Backend
- **Fix:** `/api/v1/compliance/soc2/report` refuses to render if evidence older than configured window.

### F-035  gRPC error envelope not aligned with REST — Medium / Consistency / API
- **Fix:** shared `Problem`-style envelope.

### F-036  Test surface is broad but cancellation/batch/SSE auth lack E2E — High / Quality / QA
- **Fix:** add E2E for run cancel, batch parity, SSE auth, marketplace install rollback.

### F-037  Disabled contract drift tests (per prior audit) — High / Quality / QA
- **Fix:** re-enable; require OpenAPI ↔ route table parity in CI.

### F-038  Accessibility evidence missing for canvas builder, marketplace cards, governance dialogs — Medium / Completeness / Frontend
- **Fix:** axe-core CI on all routes; keyboard-only smoke tests.

### F-039  Responsive evidence missing for cockpit on tablet/mobile — Low / Completeness / Frontend
- **Fix:** add Playwright viewport profiles; document desktop-only pages explicitly.

### F-040  README runtime-truth section not echoed inside the cockpit — Medium / Trust / UI
- **Fix:** a global runtime-truth banner reflecting `/info` capabilities and `/health/deep` mode.

### F-041  Run detail does not show lineage — High / Completeness / UI+Backend
- **Fix:** lineage block: pipeline version, agent versions, policy bundle, evaluation gate, compliance bundle.

### F-042  Auto-promoted policies must surface "auto" badge — High / Correctness+Trust / UI
- **Fix:** explicit "auto-promoted" badge with one-click rollback.

### F-043  Consent decisions UI missing — High / Privacy / UI
- **Fix:** tenant-level consent dashboard, change history, export.

### F-044  No deprecation policy for endpoints — Low / Consistency / API
- **Fix:** publish an API deprecation policy; `Deprecation` and `Sunset` HTTP headers; lint.

### F-045  No standard error envelope across controllers — Medium / Consistency / API
- **Fix:** RFC-7807 `Problem` envelope across all 20 controllers.

### F-046  No standard pagination across list endpoints — Medium / Consistency / API
- **Fix:** cursor-based `{ data, nextCursor, prevCursor, total? }`.

### F-047  No standard sort/filter grammar — Medium / Consistency / API
- **Fix:** `?sort=field,-other&filter[status]=…`.

### F-048  No request correlation header propagation across server/gateway/orchestrator — Medium / Observability / Platform
- **Fix:** `X-Correlation-ID` mandatory at gateway; propagated; logged on every span.

### F-049  Run state machine names drift — Medium / Consistency / Backend
- **Fix:** single canonical enum (e.g. `PENDING / RUNNING / SUCCEEDED / FAILED / CANCELLED / TIMED_OUT`); reject anything else at the boundary.

### F-050  Episode/policy provenance not always set — High / Correctness / Backend
- **Fix:** every promoted policy has a `PolicyProvenanceRecord`; emit a chained audit event.

(Findings continue along the same axes; the list above covers the binding shape of the audit. New findings discovered during execution should be added with the same template.)

---

## 6. Completeness Gap Inventory

- Missing screens: audit explorer, marketplace install lifecycle, binding designer, tenant lifecycle, GDPR/CCPA request UI, consent dashboard, DR/backup ops tile, SSE-token issuance flow.
- Missing states: in-memory run history banner, HITL `configured=false` empty state, capability-driven page hiding from `/info`.
- Missing validations: per-item batch validation parity; `Idempotency-Key`-required on mutating endpoints; tenant header vs JWT mismatch must 403.
- Missing backend support: durable run history fallback (must be Data Cloud-only in prod), durable idempotency, durable session store, cooperative + hard cancellation contract, consent decisions exposure, role claim mapping.
- Missing API support: SSE token mint, `audit/events` paginated, capability flags in `/info`, dry-run publish, lineage on run detail.
- Missing persistence logic: durable cancellation intent, audit chain verification, consent decision retention, idempotency replay window.
- Missing audit/history/notification behaviour: kill-switch chain, rollback chain, marketplace install chain, GDPR fulfilment SLAs.
- Missing admin/governance flows: tenant lifecycle, role assignment, HITL policy editor, escalation destination management.
- Missing recovery paths: SSE auth-error recovery, run cancel hard-kill timeout, Data Cloud outage degraded UX.
- Missing accessibility behaviour: canvas keyboard, governance dialog focus traps, marketplace card semantics.
- Missing trust/privacy/security surfaces: consent dashboard, PII override audit, kill-switch step-up.
- Missing automation: anomaly false-positive feedback, auto-policy provenance, suggestion confidence bands.
- Missing E2E closure: SSE-auth, batch parity, run cancel, install rollback, GDPR fulfilment, kill-switch.

---

## 7. Simplification Plan

- **Remove:** legacy JWT-paste login path for normal users (admin-only break-glass); patterns surface from main nav (move to runtime detection studio); raw tenant-id text inputs in the cockpit.
- **Merge:** `aep.api.ts` and `pipeline.api.ts` into capability-scoped generated clients.
- **Automate:** suggestion confidence routing (low → advisory, mid → reviewable, high → applied with audit); auto-promotion gating with explicit badges and one-click rollback.
- **Infer:** tenant from JWT; role from JWT; capability availability from `/info`.
- **Hide by default:** runtime profile, durability mode, JWT debugging, raw IDs.
- **Prefetch:** capability flags, current policy bundle, current pipeline version.
- **Move to advanced/admin:** kill-switch, degradation, capability connectors/transforms catalog, JWT paste mode.
- **Contain leakage:** any operator who has to think about `AEP_PROFILE`, `AEP_TRUSTED_PROXY_CIDRS`, `AEP_HITL_TIMEOUT_POLICIES` to use the cockpit is a bug.

---

## 8. Correctness Review Register

| ID | Layer | Symptom | Truth |
| --- | --- | --- | --- |
| C-1 | API | `/ready` returns OK while Data Cloud is down | Must return 503 |
| C-2 | Persistence | Run history list looks complete | Bounded in-memory truncation |
| C-3 | API | Cancel returns 200 instantly | Work continues silently |
| C-4 | API | `/events/batch` accepts items single endpoint would reject | Parity gap |
| C-5 | UI | UI shows authenticated | Only session token, not JWT |
| C-6 | UI | UI live indicator | SSE auth blocks browser |
| C-7 | API | Tenant header overrides JWT tenant in some paths | Must 403 |
| C-8 | UI | HITL queue empty | Could be `configured=false` |
| C-9 | UI | AI suggestion accepted | No confidence shown |
| C-10 | Backend | Policy promotion silent | Must produce provenance + audit |
| C-11 | Backend | Kill-switch toggled | No step-up, no chain entry |
| C-12 | Backend | Rollback succeeds | No audit record link to versions |
| C-13 | API | Idempotent retry repeats side effects | No durable replay |
| C-14 | UI | Cost numbers shown | No budget/alert/threshold |
| C-15 | Backend | Anomaly tagged | No FP feedback to learning |
| C-16 | API | Marketplace install | No version pin / compat check |
| C-17 | UI | Run detail page | No lineage |
| C-18 | Backend | SOC2 report | No freshness guard |
| C-19 | API | gRPC error | Different envelope |
| C-20 | Backend | Run state | Enum drift |

---

## 9. Consistency Review Register

| ID | Drift |
| --- | --- |
| K-1 | Pipeline / Workflow / Pattern terminology |
| K-2 | `/governance/...` vs `/api/v1/compliance/...` prefix |
| K-3 | `aep.api.ts` vs `pipeline.api.ts` duplication |
| K-4 | Run state enum names across server, ledger, UI |
| K-5 | Error envelope across 20 controllers |
| K-6 | Pagination shape |
| K-7 | Sort/filter grammar |
| K-8 | Audit event schema |
| K-9 | Role claim shape (server vs UI) |
| K-10 | `AepController` vs `AgentController` naming |
| K-11 | OpenAPI ↔ route table drift (AI metrics, missing platform-session) |
| K-12 | gRPC vs REST error and pagination |
| K-13 | UI navigation literals vs route helpers |
| K-14 | Tenant identity source (header / JWT / query / body / "default") |
| K-15 | HITL approve/reject vs Learning approve/reject event names |

---

## 10. API / Backend / Data Review

- **Contract quality:** OpenAPI is comprehensive but the route table in `AepHttpServer` is the actual source of truth; CI must enforce parity. The 20-controller layout is sane; consolidate the error envelope and pagination grammar across them.
- **Workflow support:** the runtime supports the operator vision; it lacks closure on cancellation, idempotency, and audit chaining.
- **Data model alignment:** Data Cloud is the right durable backbone; the in-memory fallbacks must be either dev-only or explicitly truth-banner'd.
- **State machines:** unify the run state enum and reject anything else at the API boundary.
- **Async/event handling:** SSE auth from browser is the single biggest live-update risk.
- **Integration reliability:** OPA / LLM / Kafka adapters must declare timeouts, retries, and circuit breaking; per repo policy they must not be stubs.

---

## 11. AI/ML Embedding Plan

| Opportunity | Function | Mode | Confidence | Fallback | Review trigger | Priority |
| --- | --- | --- | --- | --- | --- | --- |
| Pipeline stage suggestion | Recommend next stage | Assist | Required | Manual edit | Below threshold or destructive | P0 |
| Run anomaly explanation | Why this run failed | Assist | Required | Logs panel | Always reviewable | P0 |
| HITL pre-decision summary | Summarise context | Assist | Required | Raw evidence | Default review | P0 |
| Policy promotion advisor | Should we promote | Hybrid | Required | Manual review | Always | P0 |
| Cost optimisation | Cheaper model substitution | Assist | Required | Off | Always reviewable | P1 |
| Capability binding suggestion | Pick schema/encoder | Assist | Required | Manual | Pre-publish simulate | P1 |
| GDPR triage | Categorise request type | Hybrid | Required | Manual | Always reviewable | P1 |
| Kill-switch impact preview | What will degrade | Assist | Required | Manual | Always | P1 |
| SOC2 evidence collection | Bundle evidence | Automation | Required | Manual | Operator approval | P1 |
| Run lineage narrative | Plain-English run summary | Assist | Required | Raw | Default reviewable | P2 |
| Tenant onboarding assist | Pre-fill defaults | Assist | Required | Manual | Default reviewable | P2 |
| Anomaly false-positive learning | Close the loop | Automation | N/A | N/A | Always | P2 |

Every AI-driven action must produce a structured event: `{ surface, confidence, rationale, sources, override?: actor }`.

---

## 12. Trust / Privacy / Security / Observability Plan

- **User-facing visibility:** runtime-truth banner; capability-driven page gating; live policy bundle indicator on every run.
- **Operational transparency:** kill-switch chain; rollback chain; install chain; export chain.
- **Auditability:** tamper-evident audit chain with cryptographic verification; audit explorer page.
- **Permission clarity:** role badges in header; lint-enforced role gates around action buttons.
- **Sensitive action handling:** step-up auth on kill-switch, degradation, tenant retire, mass export.
- **Privacy controls:** consent dashboard; PII override audit; GDPR/CCPA SLAs visible.
- **Role-based transparency:** different cockpit surfaces per role; admin tools off the main nav.
- **Safe defaults:** fail-closed in production for tenant defaulting, in-memory run history, JWT secret missing.
- **Diagnosability:** correlation IDs end-to-end; SLO snapshots tied to alert rules; `/health/deep` displayed in cockpit.

---

## 13. Design System / Reuse / Abstraction Review

- The capability-folder split in `ui/src/components` is good. Avoid expanding `shared/`; promote anything cross-capability into `@ghatana/design-system` or `@ghatana/domain-components` per repo Section 32.
- Hand-coded API clients (`aep.api.ts`, `pipeline.api.ts`) duplicate the generated OpenAPI client; consolidate.
- Hooks (`useAgents`, `usePipelineRuns`, `useHitlQueue`, `useAgentMemory`, `useValidation`, `useSelection`) should be the only consumers of the generated client; pages should not call fetch.
- Run detail, HITL detail, marketplace card, and governance dialog all repeat similar metadata grids — extract a shared `MetadataPanel`.
- All long-running actions should go through a single shared `useDurableMutation` that handles idempotency, optimistic UI, rollback, and audit toast.

---

## 14. Prioritised Remediation Roadmap

### Immediate (≤2 weeks)

| ID | Item | Owner | Effort | Dependency |
| --- | --- | --- | --- | --- |
| F-001 | `/ready` dependency-truthful | platform | M | none |
| F-002 | Run history truth banner + prod fail-closed | backend+frontend | M | none |
| F-004 | Batch event policy parity | backend | M | event-schema |
| F-005 | SSE auth from browser | backend+frontend | M | gateway |
| F-006 | Remove default tenant in non-dev | backend | S | profile |
| F-007 | Sessions are continuation, not auth | backend+frontend | S | session-store |
| F-008 | Implement or remove `/api/v1/auth/platform-session` | backend+frontend | S | sso |
| F-013 | HITL `configured=false` UI | frontend | XS | none |
| F-018 | Kill-switch step-up + chain + auto-decay | backend | M | audit-chain |
| F-025 | Durable idempotency | backend+persistence | L | data-cloud |
| F-037 | Re-enable contract drift tests | qa+platform | S | openapi |

### Short-term (≤6 weeks)

F-003 cancellation contract; F-009 marketplace lifecycle; F-010 audit explorer; F-014 publish dry-run; F-015 rollback chain; F-019 GDPR/CCPA UI; F-020 marketplace direct execute sandbox; F-022/F-023 generated clients; F-024 capability flags; F-027 binding designer; F-030 tenant lifecycle; F-031 PII block-by-default; F-032 role-driven UI gating; F-041 lineage on run detail; F-042 auto-promotion badge; F-043 consent dashboard; F-050 policy provenance enforcement.

### Medium-term (≤12 weeks)

F-011/F-016/F-017/F-035/F-045/F-046/F-047 consistency cleanups; F-021 memory explorer summarisation; F-026 trusted-proxy alerting; F-028 cost budgets; F-029 anomaly FP feedback; F-033 ops tile; F-034 SOC2 freshness; F-038 a11y; F-040 runtime-truth banner; F-044 deprecation policy; F-048 correlation IDs; F-049 run state enum unification.

### Long-term

Operator/admin/audit suite as a distinct console role; AI explainability surfacing across every assist; learning loop coverage per skill; multi-region DR.

---

## 15. Final Ideal Product Experience

After remediation, AEP feels like a single, calm operator console. The user signs in once via SSO, sees a tenant they belong to, and lands on Operate. Live runs update silently. When Data Cloud is unavailable, a single calm banner says so; the page does not pretend. Cancelling a run is instant in intent, durable in effect, and visible in the audit chain within seconds. Publishing a pipeline is a confirmation that quotes the agents, policies, and compliance bundle that will apply, with an explicit dry-run. Marketplace installs are version-pinned and reversible. HITL and learning loops have visible provenance. Kill-switch is rare, deliberate, audited, and decays. GDPR/CCPA flows are end-user surfaces, not API curiosities. AI is implicit: low-confidence is advisory, mid is reviewable, high is applied with a chained audit; nothing AI does is silent. Tenants are first-class objects with a lifecycle. Operators never see `AEP_PROFILE`, `AEP_TRUSTED_PROXY_CIDRS`, or `AEP_HITL_TIMEOUT_POLICIES`; those are deployment concerns the cockpit reflects, not exposes.

---

## 16. Executive Summary Lists

### Top 10 Critical Issues
1. `/ready` is not dependency-truthful (F-001)
2. In-memory run history capped silently (F-002)
3. Run cancellation flag-only (F-003)
4. Batch event policy parity gap (F-004)
5. SSE cannot auth from browser (F-005)
6. Tenant default fallthrough (F-006)
7. Sessions used as authentication (F-007)
8. `platform-session` endpoint mismatch (F-008)
9. Kill-switch lacks step-up + chain (F-018)
10. Idempotency not durable (F-025)

### Top 10 Completeness Gaps
1. Marketplace install lifecycle (F-009)
2. Audit explorer (F-010)
3. Binding/connector designer (F-027)
4. GDPR/CCPA end-user UI (F-019)
5. Consent dashboard (F-043)
6. Tenant lifecycle (F-030)
7. Run lineage block (F-041)
8. Capability flags in `/info` (F-024)
9. Cost budgets/alerts/chargeback (F-028)
10. Backup/DR/Export ops tile (F-033)

### Top 10 Simplification Opportunities
1. Generated OpenAPI clients only (F-022, F-023)
2. Capability-driven page gating (F-024)
3. AI confidence routing to advisory/reviewable/applied (F-012)
4. Tenant from JWT only (F-006)
5. Kill JWT paste path for normal users (F-007)
6. Memory explorer summarisation (F-021)
7. Single shared `useDurableMutation`
8. Single error envelope
9. Single pagination grammar
10. Single status enum

### Top 10 Correctness Issues
1. F-001, F-002, F-003, F-004, F-005, F-006, F-007, F-014, F-025, F-050.

### Top 10 Consistency Issues
1. K-1, K-2, K-3, K-4, K-5, K-6, K-11, K-12, K-13, K-14.

### Top 10 API/Backend/Data Issues
1. F-004 batch parity, F-025 idempotency, F-014 dry-run, F-037 contract drift, F-045 error envelope, F-046 pagination, F-049 state enum, F-008 platform-session, F-011 OpenAPI gap, F-024 `/info` capabilities.

### Top 10 AI/ML Opportunities
1. Pipeline stage suggestion with confidence (F-012)
2. Run anomaly narrative
3. HITL pre-decision summary
4. Policy promotion advisor (F-050)
5. Cost optimisation suggestion
6. Capability binding suggestion
7. GDPR triage
8. Kill-switch impact preview
9. Anomaly FP feedback (F-029)
10. Run lineage narrative (F-041)

### Top 10 Trust / Privacy / Security / Observability Improvements
1. Step-up + chain on kill-switch (F-018)
2. PII block-by-default (F-031)
3. Consent dashboard (F-043)
4. Audit explorer (F-010)
5. Tamper-evident chain everywhere
6. Trusted-proxy alerting tile (F-026)
7. Correlation IDs end-to-end (F-048)
8. Capability-driven gating (F-024)
9. Sandbox marketplace execute (F-020)
10. Role-driven UI gating (F-032)

---

**End of audit.**


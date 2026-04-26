# AEP Full-Stack Product-System Audit and Remediation Blueprint

Date: 2026-04-25  
Product: Agentic Event Processor (AEP)  
Audit scope: UI, UX, frontend, API contracts, Java server, gateway, registry/orchestrator modules, data persistence, async/event flows, security/privacy/trust, observability, AI/ML, quality, product strategy

Verification performed:
- `pnpm --filter @aep/ui type-check` passed.
- `pnpm --filter @aep/ui run verify:truth` passed: 50 checks, 0 issues.
- `pnpm --filter @ghatana/aep-gateway test` failed: 48/49 tests passed; `src/__tests__/sse-ws-backend-contract.test.ts` timed out in "relays a message from client to the backend".

## 1. Executive Summary

AEP has a strong product thesis and a large amount of real implementation: the console has moved from the earlier unstyled/broken-routing state into a coherent Operate/Build/Learn/Govern/Catalog cockpit; the Java server exposes a broad HTTP surface; Data Cloud-backed pipeline storage exists; auth is now fail-closed in non-development environments; and gateway, governance, learning, marketplace, and runtime durability concepts are represented in code.

The product is not yet production-complete end to end. The highest-risk gaps are not cosmetic. They are cross-layer truth and correctness gaps: runtime readiness can look ready while dependencies are degraded, SSE live updates are designed into the UI but cannot authenticate through the current browser EventSource client, batch event ingestion bypasses controls applied to single-event ingestion, run cancellation is not tenant-scoped and does not actually preempt execution, and several operational states remain in memory or only partially durable.

The strategic docs overstate maturity. `AEP_COMPREHENSIVE_OVERVIEW.md` frames AEP as roughly 85% complete and marks core engine/API/UI/compliance as production ready, while `docs-generated/01-vision-plan-requirements/01-product-vision.md` itself calls out fragmentation between server, gateway, orchestrator, registry, and UI. The code supports the fragmentation diagnosis more than the production-ready claim.

Completeness is medium-to-high risk: the visible cockpit is broad, but install, audit-log APIs, authenticated SSE, durable idempotency, durable session state, batch governance, operator binding surfaces, production readiness gates, and admin support workflows are incomplete or inconsistent.

Simplicity is medium risk: the UI is much simpler than the architecture underneath, but backend complexity still leaks through tenant IDs, durability banners, manual JWT recovery mode, disconnected marketplace install, and inconsistent live/polling behavior.

Correctness is high risk: several workflows can present a successful or live state that is not fully true. Critical examples include `/ready`, batch ingestion, SSE, run metrics, cancellation, idempotency, pipeline rollback, and AI stage suggestion routing.

Consistency is high risk: tenant handling, auth/session semantics, HTTP validation semantics, API path ownership, durability semantics, and audit patterns differ between surfaces.

Production readiness: not ready for a high-trust multi-tenant runtime until the immediate remediation items below are complete.

## 2. Deep Audit Scorecard

| Area | Rating | Evidence-based justification |
| --- | --- | --- |
| Completeness | High | Broad surfaces exist, but authenticated SSE, marketplace install, audit-log endpoints, durable idempotency, batch policy controls, real run cancellation, binding UI, and complete admin recovery are missing or partial. |
| Simplicity | Medium | Console IA is improving, but users still see tenant plumbing, token/session ambiguity, runtime durability complexity, and partially wired actions. |
| Correctness | Critical | Batch events bypass controls, `/ready` is not dependency-truthful, run cancel is memory-only and not tenant scoped, SSE cannot authenticate from UI, and run metrics come from a bounded buffer. |
| Consistency | High | Tenant defaults, API validation status codes, auth/session paths, route ownership, live-event contracts, and audit patterns drift across layers. |
| UI/UX quality | Medium | Main cockpit, route helpers, responsive builder advisory, and sensitive dialogs are good progress; raw `window.confirm`, disconnected install, and manual tenant/token workflows remain. |
| Frontend quality | Medium | Shared HTTP client, route helpers, Jotai tenant atom, React Query, and SSE hook are good; generated OpenAPI types are not the main source of truth and several API paths are hand-coded or stale. |
| API/contract quality | High | Many endpoints exist, but contracts expose inconsistent semantics: 200-invalid validation, default tenants, duplicated gateway/server auth, undocumented missing audit endpoints, and registry/server surface split. |
| Backend/workflow quality | High | Core runtime exists, but workflow closure is incomplete for batch events, run cancellation, pipeline publish/rollback/delete governance, durable run metrics, and operator lifecycle. |
| Data/persistence quality | High | Data Cloud stores exist for pipelines and snapshots, but run history, idempotency, sessions, governance fallbacks, registry event design bindings, and some analytics surfaces are still in-memory or partial. |
| Observability/operability | High | Health, metrics, SLO metrics, run ledger, SSE, and durability metadata exist; readiness and user-visible job truth are not reliable enough. |
| Privacy/security/trust | Critical | Auth fail-closed improved, but tenant defaulting, batch bypass, UI SSE auth gap, client-side audit backup, optional session gate, and PII warn-only behavior create trust risk. |
| AI/ML embedding | Medium | AI suggestions, NLQ, learning episodes, policies, and HITL exist; placement is fragmented and one UI stage-suggestion endpoint appears unwired to server routes. |
| Accessibility | Medium | Shared page states and dialogs exist; some custom controls, manual SVG buttons, raw confirm, and complex canvas workflows need systematic keyboard/screen-reader verification. |
| Responsiveness | Medium | Builder has a mobile advisory and full-page layout; dense cockpit tables/canvas/governance views need mobile/tablet evidence and task-specific alternatives. |
| Perceived performance | Medium | React Query/SSE are appropriate, but broken SSE means polling/refetch fallback bears more weight; server list/metrics pages sometimes read bounded/in-memory or broad query windows. |
| Cognitive load | Medium | Top-level IA is sensible; low-trust states force users to reason about durability, tenant scope, sessions, and operational state. |
| End-to-end product quality | High | The product is promising and substantial, but too many journeys are visually present before operational closure is guaranteed. |

## 3. Surface-by-Surface and Layer-by-Layer Audit

### 3.1 Product Vision and Documentation

Purpose: define AEP as the event-driven agent execution runtime and cockpit.

Completeness: strategic framing is strong, but maturity claims are too optimistic. `AEP_COMPREHENSIVE_OVERVIEW.md` describes Event Cloud, AEP Server, Orchestrator, Registry, learning loop, and cockpit as the target architecture, and also claims roughly 85% completion. `docs-generated/01-vision-plan-requirements/01-product-vision.md` calls out fragmentation between server, gateway, orchestrator, registry, and UI. Those statements conflict.

Simplicity: the docs describe five major planes and many modules. The product needs one canonical user-facing mental model: "events enter, pipelines decide, agents execute, humans review exceptions, AEP learns and governs."

Correctness: production-ready labels should be tied to runtime gates and verified workflows. Today the code still contains in-memory fallbacks, optional dependency wiring, disabled contract drift tests, and bypasses.

Consistency: use one maturity taxonomy across docs, code health, UI durability status, and roadmap.

Remediation: replace percentage-complete language with capability-level readiness: Demo, Dev, Beta, Production Candidate, Production. Gate production labels on passing E2E evidence.

### 3.2 AEP Console Shell and Navigation

Purpose: cockpit across Operate, Build, Learn, Govern, Catalog.

Evidence:
- `products/aep/ui/src/App.tsx` defines current routes for Operate, Build, Learn, Govern, Catalog, auth, and redirects.
- `products/aep/ui/src/lib/routes.ts` provides canonical route helpers and fixed edit route generation.
- `products/aep/ui/src/main.tsx` imports `index.css`, addressing the previous unstyled shell issue.

Completeness: current IA is broad and mostly aligned with the vision. Missing surfaces include connector binding design, durable audit explorer, operator install lifecycle, runtime dependency drilldown, admin replay/retry, and role-specific governance consoles.

Simplicity: top-level verbs are good. The UI should reduce visible internal concepts such as raw tenant IDs and runtime components unless the user is in admin/debug context.

Correctness: route contract tests exist, but surface availability should be capability-driven from backend `/info` or `/health/deep` instead of hardcoded assumptions.

Consistency: route helpers are an improvement. Require all navigation and deep links to use helpers.

### 3.3 Authentication, Sessions, Tenant Scope

Purpose: authenticated, tenant-isolated cockpit and API.

Evidence:
- `AepAuthFilter` fails closed in non-development when `AEP_JWT_SECRET` is absent and public paths are limited to `/health`, `/ready`, `/live`, `/info`, `/metrics`, and `/api/v1/status`.
- `SessionFilter` issues in-memory session tokens, but comments and code make sessions optional and not a hard gate.
- `AepHttpServer` wraps router as `AepAuthFilter(SessionFilter(AepSecurityFilter(router)))`.
- `AuthContext` treats either an auth token or session token as `isAuthenticated`.
- `AuthContext` calls `/api/v1/auth/platform-session`, but server route search found no matching backend route.
- `HttpHelper.resolveTenantId` defaults to `"default"`.

Completeness: SSO is not complete because platform session bootstrap appears to call a missing endpoint. Session lifecycle is not durable or revocable across nodes. Roles are modeled in UI but not populated from JWT in the inspected frontend.

Simplicity: users should not paste JWTs except as a guarded break-glass mode. The login page correctly gates legacy JWT paste behind a feature flag, but the default platform SSO must be real.

Correctness: UI authentication can become locally true with a session token even though backend auth still requires a JWT before the session filter. Tenant defaulting allows requests to run under `"default"` instead of failing closed.

Consistency: gateway validates tenant mismatch between JWT and header, while server helpers fall back to query/body/default. Tenant identity should come from one canonical source.

Remediation: make platform SSO endpoint real or remove bootstrap; make session token an authenticated secondary credential or remove it from `isAuthenticated`; disallow `"default"` tenant outside explicit dev/demo mode; derive tenant from verified JWT and reject mismatches.

### 3.4 Live Operations and SSE

Purpose: real-time run, HITL, and agent updates.

Evidence:
- UI `subscribeToAepStream` opens `new EventSource("${API_BASE_URL}/events/stream?tenantId=...")` without a token.
- Gateway `/events/stream` requires bearer token or `token` query parameter.
- Backend `AepAuthFilter` does not mark `/events/stream` public despite the comment in `AepHttpServer` saying it bypasses auth.
- `SseController` rejects missing/default tenant and publishes tenant-scoped messages.

Completeness: live updates are architecturally present but not functional in a secure browser path unless cookies or token query plumbing is added.

Simplicity: users should not need to understand why live indicators fail. SSE should either work silently or the UI should show an authenticated reconnect action with a clear fallback.

Correctness: the UI can show live-state affordances while no authenticated stream can be established.

Consistency: gateway supports query token, backend requires bearer auth, UI sends neither. Align all three.

Remediation: move to httpOnly SameSite cookie auth for SSE, or issue short-lived SSE-specific tokens and append them to the URL. Add an integration test covering UI/gateway/backend SSE auth.

### 3.5 Operate: Runs, Run Detail, Cancellation, Metrics

Purpose: monitor executions, inspect lineage, cancel risky runs, understand runtime health.

Evidence:
- `AepHttpServer.handleGetPipelineMetrics` computes metrics from `recentRuns`.
- `recentRuns` is a bounded in-memory deque with max 1000 entries.
- `recordRun` writes to `recentRuns`, emits SSE, and fire-and-forget records to `runLedgerService`.
- `handleCancelRun` searches `recentRuns` by `runId` only, not tenant, then marks the first match `CANCELLED`.
- `listRunsForTenant` merges in-memory runs with ledger evidence, but cancellation and metrics remain buffer-centric.

Completeness: run list/detail has partial durable reconstruction, but cancellation, metrics, active-run control, and long-history reporting are incomplete. There is no real execution preemption handshake visible in the server cancel path.

Simplicity: users need one truthful status: queued/running/waiting-for-human/succeeded/failed/cancel-requested/cancelled. Current memory mutation hides whether cancellation actually reached workers.

Correctness: cancellation is a critical correctness and security gap because it is not tenant-scoped and not tied to the execution engine. Metrics become wrong after restart or buffer eviction.

Consistency: run ID fields drift (`id`, `runId`), and the UI normalizes them. Normalize at API boundary.

Remediation: create a durable `run_executions` state model; make cancellation a tenant-scoped command with status transition, audit event, worker interrupt/compensation, and idempotent result; compute metrics from durable events or materialized rollups.

### 3.6 Build: Pipeline List, Builder, Validate, Save, Publish, Rollback

Purpose: author and operate agent pipelines.

Evidence:
- `PipelineBuilderPage` uses active `tenantId` for get/save/validate/run.
- `PipelineBuilderPage` still uses `window.confirm` for unsaved new-pipeline discard.
- Create pipeline validates name and DAG before save, but invalid create/validate responses return HTTP 200 with `{valid:false,...}`.
- Update requires expected version and returns 428/409 for missing/conflict, a strong pattern.
- Delete has no visible active-run/dependency/governance guard in `handleDeletePipeline`.
- Publish saves version snapshot and updates live without expected version/concurrency/gov approval.
- Rollback comments note previous live state is not snapshotted before restore.

Completeness: authoring workflow is strong but operational lifecycle is incomplete. Missing: dependency graph, active run protection, approval for publish/delete/rollback, diff review, validation severity model, publish audit, rollback snapshot safety, dry-run against sample events, and version promotion policy.

Simplicity: keep the builder focused on the next safe action. Hide version/snapshot complexity behind "Publish", "Restore version", and "Request approval" flows.

Correctness: rollback/version semantics risk non-monotonic or lost live state. HTTP 200 invalid validation can cause incorrect success handling in generic clients.

Consistency: update has proper conflict semantics; create/publish/rollback/delete do not consistently use the same concurrency and governance pattern.

Remediation: introduce a canonical pipeline lifecycle state machine: Draft -> Validated -> PendingApproval -> Published -> Deprecated/Archived. Require expected version on publish/rollback/delete. Add a first-class validation error contract with 422 for invalid operations that cannot save.

### 3.7 Build: Pattern Studio and Event Design

Purpose: detect event patterns and bind events/connectors.

Evidence:
- Pattern routes are exposed in `AepHttpServer`.
- OpenAPI specs include event schemas and connector bindings (`/api/v1/bindings`, `/api/v1/bindings/{bindingId}/simulate`).
- `EventDesignController` exists in `aep-registry`, but route search did not show the AEP server exposing those binding routes through the main console API.

Completeness: pattern management is present; connector binding design is not complete as a cockpit workflow. Simulation exists in registry code but is not discoverable in the inspected UI.

Simplicity: event design should be folded into pipeline build: "Choose event type", "Map connector", "Simulate", "Publish binding."

Correctness: contract ownership is ambiguous. If the main server OpenAPI advertises binding paths that the main server does not route, clients will fail.

Consistency: registry and server should expose one API gateway namespace and one generated client.

Remediation: decide whether event design belongs behind AEP server or registry service, then route/proxy it consistently and generate UI types from that canonical spec.

### 3.8 Catalog: Agent Registry and Marketplace

Purpose: discover, register, inspect, and install executable agents/workflows.

Evidence:
- Agent registry and marketplace endpoints exist in `AepHttpServer` through `AgentController` and `AgentMarketplaceController`.
- `AgentMarketplacePage` install confirmation has `TODO: wire to install API when endpoint is available`; it only shows a success toast.
- gRPC server can create manifest-only placeholder agents as non-executable.

Completeness: listing, publishing, reviews, and details exist; install is visually present but operationally incomplete. Capability verification, execution readiness, owner trust, version compatibility, and install rollback are missing from the visible install path.

Simplicity: marketplace should reduce user work by showing "Ready", "Needs credential", "Requires approval", or "Incompatible" before install.

Correctness: a success toast for an unimplemented install action is misleading.

Consistency: catalog should share the same sensitive-action/audit/approval pattern as pipeline publish/delete.

Remediation: add install API, durable installation records, credential binding, version compatibility checks, approval gating for high-risk agents, and a clear installed state.

### 3.9 Learn: Episodes, Policies, Memory

Purpose: convert operational history into better policies, memory, and suggestions.

Evidence:
- Learning routes exist for episodes, policies, reflect, memory, and agent memory.
- `AepHttpServer` creates `learningPipeline` only when `agentDataCloud` exists; otherwise it is null.
- HITL and learning surfaces depend on review queue, run ledger, Data Cloud, and policy provenance.

Completeness: learning exists but degrades when Data Cloud is absent. Missing: user-facing model of why a policy was suggested, confidence calibration, review triggers, rollback of learned policy, dataset/provenance drilldown, and per-agent memory governance.

Simplicity: learning should be mostly implicit: "AEP found a policy improvement; approve, edit, or ignore."

Correctness: generated policies must never silently become active without review thresholds and audit trail.

Consistency: policy statuses must be shared across backend, UI, review queue, and ledger.

Remediation: standardize policy lifecycle and expose provenance, confidence, affected pipelines, and rollback in one review surface.

### 3.10 Govern: Kill Switch, Degradation, Compliance, Security

Purpose: trust controls for runtime and tenant operations.

Evidence:
- Governance/lifecycle controllers are wired in `AepHttpServer`.
- If production services are not injected, constructor falls back to in-memory kill switch, degradation manager, policy engine, approval workflow, and recertification pipeline.
- Compliance routes exist for GDPR access/erasure/portability, CCPA opt-out, SOC2 report.
- PII scanner in single-event ingestion logs warnings but does not enforce.

Completeness: governance vocabulary is broad but not consistently durable or enforced. Missing: policy decision audit on every sensitive transition, enforced PII handling, kill-switch scope visibility, degradation runbook linkage, recertification ownership, and evidence export.

Simplicity: governance should appear only when relevant: "Blocked by policy", "Approval required", "Tenant degraded", "PII detected and quarantined."

Correctness: warn-only PII behavior is not enough for a trust-centered runtime.

Consistency: governance actions should always have reason, actor, tenant, resource, before/after state, and immutable audit evidence.

Remediation: make governance backing stores mandatory in production; fail closed for missing policy/audit dependencies; enforce PII policy decisions with quarantine/redaction/review paths.

### 3.11 Gateway and Server Boundary

Purpose: canonical external ingress for API, SSE, websocket, CORS, auth, tenant propagation.

Evidence:
- Gateway Fastify app validates JWT, tenant mismatch, readiness to backend `/health`, HTTP proxy, SSE proxy, and WebSocket tail.
- Java server also performs auth, CORS/security headers, rate limiting, sessions, and tenant resolution.

Completeness: gateway is useful but overlaps with server responsibilities. Missing: a single ingress contract and proof that deployed paths match UI expectations.

Simplicity: product teams should target one API origin and one auth model.

Correctness: duplicated auth/tenant logic creates mismatches, especially SSE and tenant defaults.

Consistency: centralize public path list, error envelopes, correlation IDs, tenant extraction, rate limits, and SSE auth.

Remediation: designate gateway as the canonical public edge and make Java server private, or remove gateway duplication and expose Java server directly. Generate client contracts from the chosen edge.

### 3.12 API Contracts and Generated Client

Purpose: stable contracts enabling simple, correct UI.

Evidence:
- UI has generated OpenAPI types at `ui/src/generated/aep-client.ts`, but actual API calls are mostly hand-coded in `pipeline.api.ts` and `aep.api.ts`.
- `AepOpenApiSurfaceDriftTest` exists but the primary "specs stay in sync" test is disabled.
- UI calls `/api/v1/audit/log` and `/api/v1/audit/query`; route search did not find backend routes.
- UI calls `/api/v1/aep/pipelines/ai-suggest-stages`; route search found server routes for `/api/v1/ai/suggestions` and `/api/v1/nlp/parse`, not that pipeline-specific path.

Completeness: contract generation exists but is not used as the enforcement layer. Missing endpoints and path drift can reach production UI.

Simplicity: the frontend should not normalize many backend variants by hand.

Correctness: disabled drift tests and hand-coded paths allow UI/API divergence.

Consistency: use one error envelope and one pagination/filter/sort envelope across all list endpoints.

Remediation: make OpenAPI drift tests mandatory; generate typed client functions; block UI API paths not present in canonical OpenAPI; remove dead routes or implement them.

### 3.13 Data and Persistence

Purpose: durable multi-tenant state for pipelines, runs, audit, sessions, governance, marketplace, learning.

Evidence:
- `DataCloudPipelineStore` persists pipelines and version snapshots to Data Cloud collections.
- Pagination in `DataCloudPipelineStore` fetches broad query windows then paginates in memory.
- `AepEventCloudFactory` can fall back to in-memory EventCloud when no provider is found.
- `SessionFilter` stores sessions in a local `ConcurrentHashMap`.
- `processedIdempotencyKeys` in `AepHttpServer` is an in-memory set.
- `recentRuns` is an in-memory bounded buffer.
- `EventDesignService` stores schemas/bindings in concurrent maps.

Completeness: pipeline durability is the strongest area. Run, session, idempotency, event design, and some governance states are not consistently durable.

Simplicity: users should not need to interpret "durable/degraded/ephemeral" for common work. The product should prevent unsafe modes in production.

Correctness: in-memory idempotency/session/run state breaks restarts, HA, and multi-node correctness.

Consistency: every tenant-scoped persisted entity should share tenant, actor, version, lifecycle state, created/updated timestamps, and audit hooks.

Remediation: create persistent stores for idempotency, sessions/revocations or cookie-backed identity, run execution state, event design bindings, and governance state. Make in-memory mode explicit dev-only.

### 3.14 Async, Event, and Background Processing

Purpose: reliable event ingestion, pattern detection, agent execution, HITL, learning, and operational progress.

Evidence:
- Single event ingestion evaluates consent and scans PII, then calls `engine.process`.
- Batch ingestion maps events to `engine.process` but skips the single-event controls.
- `AgentStepRunner` has timeout/retry logic, but returns a success placeholder when completion races after already completed.
- `recordRun` fire-and-forget records run completion/failure to ledger.

Completeness: async model exists but lacks a universal job/run state machine, durable queue visibility, dead-letter handling, retry disclosure, and compensation hooks at product level.

Simplicity: users should see "processing", "waiting for review", "retrying", "failed with recovery action", and "done" rather than raw implementation.

Correctness: batch bypass and placeholder success are correctness risks; fire-and-forget ledger writes can silently lose audit evidence.

Consistency: all async operations need idempotency, durable state, retry policy, failure reason, and user-visible finalization.

Remediation: define a shared operation ledger for event ingestion, pipeline runs, marketplace install, policy approval, export/erasure, and retries.

### 3.15 Privacy, Security, and Trust

Purpose: protect tenant data, enforce consent, expose auditability, and make sensitive actions safe.

Evidence:
- Security filter adds headers, CORS, size limit, and rate limiting.
- Auth filter is fail-closed outside development.
- UI stores bearer/session token in `sessionStorage`, with docs noting future httpOnly cookies.
- Consent examples still use localStorage in several frontend tests/components.
- Client audit service stores minimized backup events in sessionStorage with base64 obfuscation if backend logging fails.
- Backend audit routes for `/api/v1/audit/log` and `/api/v1/audit/query` were not found.

Completeness: there is a good start, but trust-critical surfaces are incomplete. Missing: immutable audit API, tenant-enforced audit query, consent server source of truth, PII enforcement, session revocation, role derivation, and SSE secure auth.

Simplicity: trust should be quiet by default and visible on demand.

Correctness: client-only audit backup is not audit evidence. Local storage consent cannot be the authoritative compliance state.

Consistency: every sensitive action should use `SensitiveActionDialog` style UX and backend governance/audit enforcement.

Remediation: implement append-only audit API; move consent to backend; use secure cookies or short-lived token rotation; integrate PII scanner with policy engine; add tenant/role enforcement tests.

### 3.16 Observability and Operability

Purpose: diagnose runtime behavior and support users/admins.

Evidence:
- `HealthController.ready` returns a ready flag, not dependency status.
- `HealthController.health/deep` reports component statuses and durability metadata.
- Gateway `/ready` checks backend `/health`, not backend `/ready` or `/health/deep`.
- Metrics endpoint returns Prometheus when registry exists and JSON fallback otherwise.
- SLO metrics and run ledger service exist.

Completeness: diagnostics are broad but not production-operational. Missing: dependency-aware readiness, alert thresholds, trace-to-run correlation in UI, replay tools, DLQ explorer, retry dashboard, and support bundle export.

Simplicity: operators should have one "Can this tenant run safely?" status rather than several partly truthful probes.

Correctness: readiness must fail when required dependencies for the active profile are unavailable.

Consistency: health, readiness, UI durability banner, run ledger, and deployment readiness must derive from the same profile-aware dependency model.

Remediation: make `/ready` profile-aware; gateway should check `/ready`; UI should present actionable degraded-state remediation; emit structured telemetry for every operation.

### 3.17 AI/ML Embedded Experience

Purpose: reduce manual orchestration work through suggestions, NLQ, anomaly detection, learning, and next-best action.

Evidence:
- UI includes AI stage suggestion in builder.
- UI calls pipeline-specific stage suggestion path not found in server routes.
- Server exposes `/api/v1/ai/suggestions` and `/api/v1/nlp/parse`.
- Learning pipeline exists only when Data Cloud is configured.

Completeness: AI is present but not consistently wired to workflows. Missing: confidence-aware approval, provenance, feedback loops, task automation boundaries, explanations tied to evidence, and privacy controls for model inputs.

Simplicity: AI should work as inline acceleration, not as a separate gimmick.

Correctness: AI suggestions need a review/validation path before modifying pipelines or policy.

Consistency: all AI outputs should have confidence, rationale, source evidence, recommended action, accept/edit/dismiss, and audit hooks.

Remediation: create a shared AI suggestion contract used by builder, operate, learn, govern, and catalog.

## 4. Complete End-to-End Flow Review

### Flow 1: Sign In and Establish Tenant Context

User goal: access AEP console under the correct tenant.

Entry point: `/login`, platform SSO button, optional legacy JWT paste.

Frontend state: `AuthContext` reads sessionStorage tokens, bootstraps `/api/v1/session`, and treats auth token or session token as authenticated.

API/backend: Java auth filter requires bearer JWT for non-public endpoints. Session filter is downstream and optional. Platform session endpoint appears absent.

Persistence: session tokens are in-memory on server and sessionStorage in browser.

Failures/recovery: session expiry reloads page; missing platform endpoint silently falls back.

Gaps: platform SSO incomplete, session/auth mismatch, roles not populated, tenant defaults to `"default"`.

Ideal future state: SSO sets httpOnly cookies; tenant and roles derive from server session; UI never handles bearer tokens; tenant switch is allowed only among authorized tenants.

### Flow 2: View Live Operations

User goal: monitor runs and live changes.

Entry point: `/operate`, sidebar SSE indicator, run table.

Frontend: React Query loads `/api/v1/runs`; SSE updates query cache.

API/backend: `/events/stream` needs auth, but EventSource sends no header/token. Backend emits SSE after `recordRun`.

Persistence: list merges memory and ledger; metrics from memory only.

Failures/recovery: SSE error invalidates queries but cannot fix auth.

Gaps: authenticated live stream broken; run history bounded/partial; metrics restart-unsafe.

Ideal future state: authenticated SSE with cookie/short token, durable operation ledger, clear degraded fallback to polling.

### Flow 3: Process Single Event

User goal: ingest one event and trigger patterns/pipeline execution.

Entry point: `POST /api/v1/events`.

Frontend/API: test run posts synthetic event with tenantId in query and body.

Backend: validates tenant and event type with defaults, checks idempotency key, evaluates consent, scans PII, processes engine, records run.

Persistence: run buffer plus ledger fire-and-forget.

Gaps: tenant/event defaulting; idempotency not durable or tenant-scoped; PII warn-only; consent result not consistently audited; event type default `"unknown"`.

Ideal future state: explicit tenant from JWT; required typed event schema; durable idempotency; policy-enforced PII handling; operation record created before processing and finalized after.

### Flow 4: Process Batch Events

User goal: ingest many events safely and efficiently.

Entry point: `POST /api/v1/events/batch`.

Backend: resolves tenant with default, maps events to engine processing.

Gaps: no tenant validation, no idempotency, no consent evaluation, no PII scan, no per-event validation, no run recording, no partial failure contract, no audit trail.

Ideal future state: batch operation record, per-event validation, same controls as single event, durable dedupe, partial success report, DLQ/retry visibility.

### Flow 5: Build and Validate Pipeline

User goal: create a valid pipeline.

Entry point: `/build/pipelines/new`, canvas, validation, save.

Frontend: uses tenant atom, saves/validates through API, still uses `window.confirm` for unsaved new-pipeline discard.

Backend: validates name/DAG. Invalid create returns HTTP 200 `{valid:false}`.

Persistence: Data Cloud pipeline store if configured, otherwise in-memory fallback.

Gaps: invalid contract semantics, no sample event dry-run, AI stage suggestion path drift, no approval awareness during authoring.

Ideal future state: inline validation with 422 for failed saves, sample event simulation, AI suggestions validated into draft, consistent unsaved-change dialog.

### Flow 6: Publish, Roll Back, Delete Pipeline

User goal: promote or change live pipeline safely.

Entry point: pipeline actions.

Backend: publish snapshots version, rollback restores snapshot, delete removes/soft-deletes via repository.

Gaps: no approval, no active run/dependency check, no expected version for publish/rollback/delete, rollback can lose current live state, audit not guaranteed.

Ideal future state: versioned lifecycle with diff, approval, expected version, live snapshot, active-run guard, audit evidence, rollback as a new version.

### Flow 7: Cancel Run

User goal: stop a dangerous or unwanted execution.

Entry point: run table/detail cancel.

Backend: finds run by runId in in-memory buffer and marks first match cancelled.

Gaps: not tenant-scoped, not durable, not tied to worker preemption, no reason/audit, no cancel-requested state.

Ideal future state: tenant-scoped cancel command persisted as operation, worker receives interrupt/compensation, UI shows canceling/cancelled/failed-to-cancel with reason.

### Flow 8: Review HITL Item and Promote Learned Policy

User goal: decide on pending human review or learned policy.

Entry point: `/operate/reviews`, `/learn/episodes`, `/build/patterns?tab=learning`.

Backend: HITL, learning, policy routes exist; learning depends on Data Cloud.

Gaps: full provenance and rollback not visible enough; policy activation lifecycle must be unified with governance audit.

Ideal future state: one review inbox with confidence, evidence, impact, approve/edit/reject, audit trail, and rollback.

### Flow 9: Install Marketplace Agent

User goal: install an agent into tenant registry for pipeline use.

Entry point: `/catalog/marketplace`.

Frontend: sensitive install dialog; TODO instead of API call, success toast.

Gaps: visually present but operationally incomplete.

Ideal future state: install API creates durable installation; checks executable status, compatibility, credentials, approval, and rollback.

### Flow 10: Audit Sensitive Operation

User goal: know what happened and satisfy compliance.

Entry point: sensitive action dialogs, audit service.

Frontend: posts `/api/v1/audit/log`, falls back to minimized sessionStorage.

Backend: audit routes not found in inspected server routes.

Gaps: audit is not guaranteed; client backup is not compliance evidence.

Ideal future state: append-only tenant-scoped audit API; every sensitive backend mutation writes audit before success response.

## 5. Comprehensive Findings Catalog

| ID | Severity | Category | Layers | Finding | Evidence | Impact | Recommended fix |
| --- | --- | --- | --- | --- | --- | --- | --- |
| AEP-AUD-001 | High | Strategy | Docs/Product | Maturity claims overstate production readiness. | Overview says 85%/production-ready; vision doc calls fragmentation. | Roadmap and release risk. | Replace percent readiness with capability gates and verified E2E evidence. |
| AEP-AUD-002 | Critical | Operability | Backend/O11y | `/ready` is not dependency-truthful. | `HealthController.ready` uses boolean readiness; gateway checks `/health`. | Orchestrators can route traffic to degraded runtime. | Make readiness profile-aware and dependency-backed; gateway checks `/ready`. |
| AEP-AUD-003 | Critical | Security/UX | UI/Gateway/Backend | SSE live stream cannot authenticate from current UI. | UI EventSource sends tenant only; gateway requires token; backend auth does not public-bypass stream. | Live operations silently fail or poll stale data. | Cookie auth or short-lived SSE token; E2E test UI->gateway->server. |
| AEP-AUD-004 | Critical | Tenanting | API/Backend | Tenant defaults to `"default"` instead of fail-closed. | `HttpHelper.resolveTenantId` falls back to `"default"`; event handlers also default. | Cross-tenant data contamination and misleading dev behavior. | Require tenant from verified JWT/header except explicit dev mode. |
| AEP-AUD-005 | Critical | Workflow | Backend/API | Batch event processing bypasses single-event controls. | `handleProcessBatch` lacks consent, PII scan, idempotency, run recording, per-event validation. | Unsafe bulk ingestion and audit gaps. | Share ingestion pipeline for single and batch events. |
| AEP-AUD-006 | Critical | Runtime | Backend/Data | Run cancellation is not tenant-scoped and not real preemption. | `handleCancelRun` searches `recentRuns` by runId and mutates memory. | Wrong tenant could cancel; execution may continue. | Durable tenant-scoped cancel command and worker preemption. |
| AEP-AUD-007 | High | Data | Backend/Data | Run metrics rely on bounded in-memory buffer. | `handleGetPipelineMetrics` uses `recentRuns`; max 1000. | Dashboards wrong after restart/eviction. | Compute from ledger/materialized rollups. |
| AEP-AUD-008 | High | Idempotency | API/Data | Idempotency keys are non-durable, global, and unbounded. | `processedIdempotencyKeys` in-memory HashSet. | Duplicate processing after restart; cross-tenant collision. | Durable tenant+operation+key+hash idempotency store. |
| AEP-AUD-009 | High | Privacy | Backend/Security | PII scanner warns but does not enforce policy. | Single event logs warning and continues. | Sensitive data can flow through pipelines without governance. | Enforce redact/quarantine/review policy. |
| AEP-AUD-010 | High | Pipeline | Backend/Data | Publish/rollback/delete lack full governance and concurrency consistency. | Update has expected version; publish/rollback/delete do not. | Lost updates, unsafe live changes. | Expected version, approval, active-run guards, rollback-as-new-version. |
| AEP-AUD-011 | Medium | API | Backend/Frontend | Invalid pipeline create/validate uses HTTP 200. | Create returns `{valid:false}` rather than 4xx for failed save. | Generic clients may treat invalid save as success. | Use 422 for save-blocking validation failures. |
| AEP-AUD-012 | High | Catalog | UI/API | Marketplace install is visual-only. | TODO in `AgentMarketplacePage`; success toast only. | User believes agent installed when it is not. | Implement install API and state. |
| AEP-AUD-013 | High | Audit | UI/API/Backend | UI posts audit endpoints that server route search did not find. | `/api/v1/audit/log`, `/api/v1/audit/query` only found in UI/tests. | Sensitive actions lack durable audit. | Implement audit API or remove client calls until backed. |
| AEP-AUD-014 | High | Auth | UI/Backend | UI can treat session token as authenticated while backend requires JWT first. | `isAuthenticated` true for session; auth filter wraps session filter. | False authenticated UI state. | Align credential model; use server session/cookie as primary or require JWT. |
| AEP-AUD-015 | Medium | UX | UI | Raw `window.confirm` remains in builder. | `PipelineBuilderPage` new action uses `window.confirm`. | Inconsistent, inaccessible unsaved-change UX. | Replace with shared dialog. |
| AEP-AUD-016 | High | Contracts | API/QA | OpenAPI drift guard is partly disabled. | `AepOpenApiSurfaceDriftTest` sync test disabled. | Spec/UI/server drift can ship. | Enable/fix drift test in CI. |
| AEP-AUD-017 | High | Contracts | UI/API | UI calls pipeline AI suggestion path not found in server routes. | UI `/api/v1/aep/pipelines/ai-suggest-stages`; server has `/api/v1/ai/suggestions`, `/api/v1/nlp/parse`. | Builder AI may fail. | Implement endpoint or use canonical suggestion API. |
| AEP-AUD-018 | Medium | Frontend | UI | Generated OpenAPI client is not the main API source of truth. | Hand-coded `aep.api.ts` and `pipeline.api.ts`. | Drift and normalization burden. | Generate typed client functions and lint unknown paths. |
| AEP-AUD-019 | High | Data | Registry/Data | Event design service stores schemas/bindings in memory. | `EventDesignService` uses concurrent maps. | Connector bindings not durable. | Back registry with Data Cloud/Postgres. |
| AEP-AUD-020 | Medium | Gateway | Gateway/Backend | Gateway and Java server duplicate auth, CORS, readiness, tenant logic. | Both implement edge concerns. | Inconsistent behavior, hard debugging. | Choose canonical edge and centralize policies. |
| AEP-AUD-021 | High | Sessions | Backend/Data | Session tokens are in-memory and optional. | `SessionFilter` uses `ConcurrentHashMap`, allows request through if invalid/missing. | No revocation/HA guarantee; confusing security model. | Use platform session/cookies or durable revocation. |
| AEP-AUD-022 | Medium | Data | Backend | Data Cloud pipeline pagination is in-memory after broad fetch. | `DataCloudPipelineStore` notes in-memory pagination. | Performance/correctness risk at scale. | Add native cursor/filter support. |
| AEP-AUD-023 | High | Runtime | Backend | EventCloud can fall back to in-memory provider. | Factory fallback when no provider found. | Production durability can be false. | Fail closed in production; dev-only fallback. |
| AEP-AUD-024 | Medium | AI | UI/API/Backend | AI suggestions lack one shared contract across builder/operate/learn. | Separate builder path, monitor suggestions, NLQ paths. | Fragmented UX and audit. | Shared AI suggestion envelope. |
| AEP-AUD-025 | High | Async | Orchestrator | Agent step race fallback returns success placeholder. | `AgentStepRunner` placeholder success after completed race. | Potentially misleading execution result. | Return explicit cancelled/late/ignored status. |
| AEP-AUD-026 | Medium | UI | Frontend | Tenant selector stores recent tenants in localStorage. | `TenantSelector` localStorage recent list. | Tenant metadata persists across sessions. | Session-only or server-backed authorized tenant list. |
| AEP-AUD-027 | High | Privacy | Frontend/Backend | Consent appears client-local in several components/tests. | Voice/NLQ consent tests use localStorage. | Consent not authoritative. | Server-side consent state and signed policy checks. |
| AEP-AUD-028 | Medium | UX | UI | Manual tenant entry creates cognitive and safety load. | Tenant selector accepts custom ID. | Users can scope incorrectly. | Authorized tenant picker with admin override. |
| AEP-AUD-029 | Medium | Accessibility | UI | Complex canvas and custom controls need proof. | Pipeline canvas, tenant dropdown, custom dialogs. | Keyboard/screen-reader risk. | Add axe + keyboard E2E for primary workflows. |
| AEP-AUD-030 | Medium | O11y | Backend/UI | Durability metadata exists but not tied to readiness. | `/health/deep` durability separate from `/ready`. | Operators see conflicting truth. | Single profile-aware runtime status model. |
| AEP-AUD-031 | High | Security | Backend/API | Batch endpoint does not validate tenant format. | `handleProcessBatch` uses body default without validator. | Malformed tenant and injection risk. | Reuse `AepInputValidator.validateTenantId`. |
| AEP-AUD-032 | Medium | API | API/UI | Run ID shape drifts between `id` and `runId`. | UI normalizes `id ?? runId`. | Client fragility. | Return canonical `id` and deprecate aliases. |
| AEP-AUD-033 | Medium | UX | UI | Live status can look connected/reconnecting without actionable auth failure. | SSE hook only invalidates query on error. | Users cannot diagnose. | Show authenticated stream state and fallback mode. |
| AEP-AUD-034 | High | Governance | Backend/Data | Governance fallback services can be in-memory. | Constructor fallback to in-memory kill switch/policy/approval/recertification. | Governance state lost in unsafe profiles. | Production fail-closed on missing stores. |
| AEP-AUD-035 | Medium | Quality | Tests | Idempotency test simulates behavior instead of exercising server. | `IdempotencyKeyDeduplicationTest` helper simulation. | False confidence. | Replace with actual server integration/concurrency test. |
| AEP-AUD-036 | Medium | Product | UI/API | Capability availability is not consistently used to gate UI actions. | UI assumes marketplace install/audit/AI routes. | Dead actions. | Capability manifest from backend. |
| AEP-AUD-037 | Medium | Reporting | Backend/UI | List and metrics endpoints lack consistent pagination/filter envelopes. | Runs returns count; metrics custom shape; pipeline list has page fields. | Inconsistent UI state and scale limits. | Standard list contract. |
| AEP-AUD-038 | High | Compliance | Backend/UI | Audit query/history is not end-to-end for every sensitive action. | Sensitive dialogs have audit text; backend route absent. | Compliance evidence incomplete. | Backend-enforced audit in mutation handlers. |
| AEP-AUD-039 | Medium | Resilience | Async/O11y | Fire-and-forget ledger writes can lose evidence silently. | `recordRun` does not await ledger result. | Run audit may be incomplete. | Durable operation state with retry/DLQ. |
| AEP-AUD-040 | Medium | Design System | UI | Mixed components and manual SVG controls remain. | Tenant selector manual SVG; mixed `@ghatana/design-system` and core controls. | Inconsistent feel and a11y burden. | Consolidate core cockpit components. |
| AEP-AUD-041 | Medium | Quality | Gateway/Async | Gateway WebSocket backend proxy contract is currently failing. | `pnpm --filter @ghatana/aep-gateway test` timed out in `sse-ws-backend-contract.test.ts` "relays a message from client to the backend". | Event tailing reliability is not proven. | Fix proxy relay test and add coverage for auth, close, backpressure, and backend disconnects. |

## 6. Completeness Gap Inventory

Missing or partial screens:
- Connector schema/binding design and simulation in the AEP cockpit.
- Durable audit explorer for sensitive actions.
- Runtime dependency drilldown tied to readiness.
- Install state and installed-agent management.
- Admin retry/replay/DLQ views.
- Authorized tenant management and tenant membership view.
- Pipeline version diff, approval, restore, archive, and dependency graph.
- Operation/job detail for async marketplace install, export, erasure, batch ingestion, and cancellation.

Missing states:
- Cancel requested, cancel failed, retrying, dead-lettered, quarantined, waiting for dependency, degraded-mode blocked.
- Published pending approval, rollback pending approval, install pending credentials, install failed, SSE auth failed.
- AI suggestion accepted/edited/dismissed/audited.

Missing validations:
- Batch event tenant/event type/payload/schema/idempotency/consent/PII validation.
- Pipeline publish/delete/rollback active-run and dependency validation.
- Marketplace compatibility/credential/license/trust validation.
- Tenant authorization validation beyond syntactic tenant ID.
- AI suggestion schema and confidence validation before applying.

Missing backend/API support:
- Authenticated SSE browser path.
- `/api/v1/auth/platform-session` or removal from UI.
- `/api/v1/audit/log` and `/api/v1/audit/query` or replacement.
- Marketplace install API.
- Pipeline stage suggestion API or canonical route update.
- Durable run cancel command.
- Durable idempotency API/store.
- Unified capability manifest for UI gating.

Missing persistence:
- Idempotency keys.
- Runtime execution state and cancellation state.
- Sessions or revocation state if sessions remain.
- Event design schemas/bindings in registry.
- Governance fallback state.
- Durable audit log.
- Consent decisions.

Missing audit/history/notifications:
- Batch ingestion operation history.
- Pipeline publish/rollback/delete audit with diff.
- Agent install audit and install status.
- PII detection/quarantine audit.
- Cancellation actor/reason/final outcome.
- AI suggestion provenance and user decision.

Missing recovery:
- SSE degraded fallback with user-visible status.
- Retry/DLQ for ledger writes and batch failures.
- Restore live pipeline after bad rollback.
- Reconcile run state after restart.
- Resume failed marketplace installs.

Missing accessibility/responsive:
- Keyboard-only pipeline authoring path or accessible alternate editor.
- Dialog focus-trap coverage on all modals.
- Mobile/tablet verification for dense tables and governance pages.

Missing trust/security:
- Server-side consent source of truth.
- httpOnly cookie or equivalent token protection.
- Tenant authorization list.
- Role-based UI gating tied to backend claims.
- PII enforcement policy.

## 7. Simplification Plan

Remove:
- Manual JWT paste from default product experience once SSO is real.
- `"default"` tenant behavior from production.
- Visual-only actions such as marketplace install until backed.
- Duplicate auth/readiness/tenant logic across gateway and server.

Merge:
- Operate live run state, run detail, governance audit, and run ledger into one operation model.
- HITL review, learned policy approval, marketplace high-risk install approval, and pipeline publish approval into one review inbox pattern.
- AI suggestions for builder, monitor, learn, and govern into one suggestion contract.
- Health/deep durability/ready into one profile-aware runtime status.

Automate:
- Tenant selection from identity.
- Pipeline validation after edits.
- Sample event generation for pipeline dry-run.
- Run anomaly detection and next-best remediation.
- Agent compatibility and credential requirement checks before install.
- PII classification and automatic quarantine/redaction based on policy.

Infer/prefill:
- Event type from sample payload/schema.
- Pipeline stages from desired outcome and available agents.
- Review assignee from ownership.
- Tenant and role from session.
- Rollback target from last known good version.

Hide by default:
- Data Cloud/Event Cloud/run ledger implementation details.
- Raw tenant ID input for non-admin users.
- Low-level metrics unless in Operate/Admin drilldown.
- AI confidence internals unless a user expands "why".

Move to admin/advanced:
- Custom tenant switching.
- Durability component names.
- JWT recovery mode.
- Raw event payload replay.
- OpenAPI/contract diagnostics.

Contain leaked complexity:
- Replace backend component statuses with actionable product states.
- Replace route/path differences with generated clients.
- Replace memory/durable mode warnings with hard production gates.

## 8. Correctness Review Register

Misleading UI states:
- Live SSE-dependent pages can appear live while the stream cannot authenticate.
- Marketplace install shows success without install.
- UI can be authenticated with only session token even backend requires JWT.
- Audit backup can imply audit preservation but is client-local.

Incorrect workflow logic:
- Batch ingestion skips controls.
- Cancel run only mutates recent memory.
- Rollback can restore old version without preserving current live state.
- Publish lacks concurrency/approval consistency.

Incorrect validations:
- Batch tenant not validated.
- Create invalid pipeline returns 200.
- Event type can default to `"unknown"`.
- Tenant can default to `"default"`.

Incorrect API semantics:
- Missing audit/platform-session/stage-suggest routes relative to UI calls.
- Inconsistent list envelopes.
- Run ID aliases force frontend normalization.

Backend mismatches:
- Auth filter public-path set does not include `/events/stream` despite server comment.
- Gateway readiness probes `/health`, not readiness/deep dependencies.
- Session filter is optional despite session UX.

Data mismatches:
- Durable pipeline storage exists; run metrics/cancel/idempotency/session/event-design are not equally durable.
- Governance state can be in-memory.

Async/recovery mismatches:
- Ledger writes are fire-and-forget.
- Agent step race returns success placeholder.
- No batch DLQ/retry operation model.

Permission/security mismatches:
- Tenant mismatch checks exist in gateway but not as a single backend invariant.
- Roles in UI are modeled but not populated/enforced end to end in inspected code.

## 9. Consistency Review Register

Terminology drift:
- Run `id` vs `runId`.
- Event type `type` vs `eventType`.
- Agent marketplace "install requested" vs actual installed state.
- Durable/degraded/ephemeral vs health ready/ok/degraded.

State/status drift:
- Pipeline update has conflict semantics; publish/rollback/delete do not.
- HITL/policy statuses differ across review, learning, and governance surfaces.
- Session token is optional backend-side but authentication-worthy frontend-side.

Component drift:
- Shared sensitive dialogs exist but builder still uses `window.confirm`.
- Mixed design-system and local components.
- Manual SVG in tenant selector where icon system should be used.

Workflow drift:
- Single vs batch event processing.
- Gateway vs backend SSE auth.
- Gateway vs server readiness.
- UI capability assumptions vs backend availability.

API drift:
- Generated client not dominant.
- Disabled OpenAPI drift test.
- UI-only audit and stage suggestion paths.
- Registry binding APIs not clearly exposed through main edge.

Validation drift:
- Tenant ID regex differs: SSE allows `.`, tenant store allows only alphanumeric/hyphen/underscore.
- Create returns 200 invalid; update returns 428/409.

Audit/history drift:
- Sensitive dialogs include audit messaging; backend audit API not present.
- Governance audit summary can fallback to recentRuns.

AI drift:
- Builder stage suggestions, operate suggestions, NLQ parsing, and learning policies use separate pathways.

## 10. API / Backend / Data Review

Contract quality:
- Broad but inconsistent. Standardize auth, tenant, error, list, validation, idempotency, and operation envelopes.

Workflow support:
- Pipeline CRUD and update conflict handling are the strongest workflows.
- Event ingestion, run cancellation, marketplace install, audit logging, and live SSE are incomplete.

Business logic:
- State machines are implicit. Make them explicit for pipeline lifecycle, run lifecycle, review lifecycle, install lifecycle, and learned policy lifecycle.

Data alignment:
- Data Cloud pipeline store aligns with product direction.
- Run, session, idempotency, audit, governance, and event design data need durable stores.

State machine quality:
- Missing canonical transitions and illegal transition enforcement.

Async/event handling:
- Needs a durable operation ledger, DLQ, retries, idempotency, and replay.

Integration reliability:
- Event Cloud fallback, Data Cloud optionality, and gateway/server split should be profile-gated.

Verdict:
- Backend/API/data can support the intended AEP product only after production profile fail-closed rules and durable operation state are implemented.

## 11. Comprehensive AI/ML Embedding Plan

| Opportunity | Function | Mode | Confidence/review | Fallback | Priority |
| --- | --- | --- | --- | --- | --- |
| Pipeline stage suggestion | Turn natural goal into draft stages | Assist | Always review before apply | Manual builder | Immediate |
| Pipeline validation explanation | Explain validation errors and fixes | Assist | Low-risk inline | Raw validation errors | Immediate |
| Run anomaly triage | Detect unusual latency/error/cost | Assist/alert | Show evidence and confidence | Threshold alerts | Short-term |
| HITL prioritization | Rank reviews by risk/impact/SLA | Assist | Explain ranking factors | FIFO queue | Short-term |
| Learned policy recommendation | Suggest policy improvements from episodes | Assist with approval | Human approval required | No promotion | Short-term |
| Agent compatibility | Recommend agents for stage/goal | Assist | Include constraints | Manual search | Short-term |
| Marketplace trust scoring | Summarize agent risk and install readiness | Assist | Explain evidence | Manual review | Medium |
| PII and sensitive-data detection | Classify/redact/quarantine | Automation with policy | Human review on high risk | Block/quarantine | Immediate |
| Incident summary | Summarize failed run with root-cause hints | Assist | Evidence-linked | Raw lineage | Medium |
| Next-best remediation | Recommend retry, rollback, disable, escalate | Assist | Human confirmation for sensitive actions | Manual actions | Medium |
| NLQ operations query | Ask "show failed runs for tenant X" | Assist | Read-only by default | Manual filters | Medium |
| Forecasting/capacity | Predict cost/load/SLO risk | Assist | Show confidence interval | Static dashboards | Long-term |

AI contract requirements:
- `id`, `type`, `tenantId`, `resource`, `recommendation`, `rationale`, `evidence`, `confidence`, `riskLevel`, `actionMode`, `expiresAt`, `acceptedBy`, `dismissedBy`, `auditId`.
- AI must not perform destructive or governance-sensitive actions without explicit policy authorization.
- All accepted AI actions must write audit evidence.

## 12. Trust / Privacy / Security / Observability Review

User-facing visibility:
- Show concise runtime state: Ready, Degraded, Unsafe, Offline.
- Show live connection state only when relevant.
- Show audit history on sensitive resources.
- Show why actions are blocked and what to do next.

Operational transparency:
- Every run should link to lineage, decisions, retries, policy checks, agent outputs, and audit events.
- Batch ingestion should have an operation detail page.
- Every background job should expose started/running/retrying/failed/completed.

Auditability:
- Append-only audit API required.
- Backend mutations must write audit, not rely on client.
- Audit query must be tenant- and role-scoped.

Permission clarity:
- Backend should expose current user, roles, tenant memberships, and capabilities.
- UI should gate actions from server capabilities, not local assumptions.

Sensitive action handling:
- Use shared sensitive dialog for publish, rollback, delete, cancel, install, approve, reject, kill switch, erasure, export.
- Require reason and preview impact.

Privacy controls:
- Server-side consent records.
- PII policy decisions: allow, redact, quarantine, block, require review.
- Avoid storing tenant history in localStorage for shared devices.

Safe defaults:
- No `"default"` tenant in production.
- No in-memory production fallback for event cloud, runs, audit, governance, idempotency.
- No unauthenticated live streams.

Diagnosability:
- Correlation IDs from UI to gateway to server to ledger.
- Profile-aware `/ready`.
- Support bundle export with redaction.
- Alerts for ledger write failures, DLQ growth, SSE auth failures, and degraded dependencies.

## 13. Design System / Reuse / Abstraction Review

Frontend reuse opportunities:
- Replace raw `window.confirm` with shared unsaved-change dialog.
- Consolidate `@ghatana/design-system` and local core controls.
- Use a shared `OperationStatus` component for runs, installs, batches, exports, erasures.
- Use one `ResourceAuditPanel`.
- Use one `CapabilityGate` component fed by backend capabilities.
- Use generated API client functions.

API standardization:
- Standard list envelope: `{items,total,page,pageSize,nextCursor}`.
- Standard mutation envelope: `{operationId,status,resource,auditId}`.
- Standard validation envelope with 422.
- Standard error envelope with `code`, `message`, `details`, `correlationId`, `retryable`.
- Standard idempotency semantics.

Backend abstraction:
- Shared ingestion pipeline for single/batch.
- Shared operation ledger.
- Shared tenant resolver from auth context.
- Shared state machine library for lifecycle transitions.
- Shared audit writer required by mutation handlers.

Data abstraction:
- Standard entity metadata fields.
- Version snapshots as immutable events.
- Durable idempotency table.
- Durable job/run status table or event-sourced projection.

AI abstraction:
- Shared AI suggestion service contract and audit trail.

## 14. Prioritized Remediation Roadmap

### Immediate

| Item | Priority | Effort | Impact | Dependencies | Owner | Rationale |
| --- | --- | --- | --- | --- | --- | --- |
| Fix authenticated SSE path | Critical | Medium | High | Auth/gateway decision | Frontend/Backend/Platform | Live operations are central and currently broken. |
| Make batch ingestion reuse single-event controls | Critical | Medium | High | Ingestion refactor | Backend/Security | Prevents unsafe bulk bypass. |
| Make run cancel tenant-scoped and real | Critical | Medium/Large | High | Run state model | Backend/Platform | Prevents cross-tenant and false cancellation. |
| Make `/ready` dependency/profile truthful | Critical | Medium | High | Health model | Backend/Platform | Prevents bad deploy/runtime routing. |
| Disable `"default"` tenant in production | Critical | Small/Medium | High | Auth tenant claims | Backend/Security | Tenant isolation must fail closed. |
| Remove or implement visual-only marketplace install | High | Small/Medium | High | Install API | Product/Frontend/Backend | Stops misleading success. |
| Implement or remove UI audit endpoints | High | Medium | High | Audit store | Backend/Security/Frontend | Sensitive actions need real audit. |
| Align pipeline AI suggestion endpoint | High | Small | Medium | AI route decision | Frontend/Backend/ML | Builder feature should actually work. |

### Short-Term

| Item | Priority | Effort | Impact | Dependencies | Owner | Rationale |
| --- | --- | --- | --- | --- | --- | --- |
| Durable idempotency store | High | Medium | High | Data store | Backend/Data | Required for retries and HA. |
| Canonical operation/run ledger | High | Large | High | Data Cloud/Event Cloud | Backend/Data/Platform | Unifies runs, jobs, retries, audit. |
| Pipeline lifecycle state machine | High | Large | High | Product workflow | Product/Backend/Frontend | Makes publish/rollback/delete safe. |
| Enable OpenAPI drift tests | High | Medium | Medium | Contract cleanup | Backend/QA | Prevents path drift. |
| Server-backed consent | High | Medium | High | Privacy model | Security/Backend/Frontend | Compliance source of truth. |
| Replace raw confirm and finish a11y smoke | Medium | Small | Medium | Design system | Frontend/Design | Improves consistency and accessibility. |
| Capability manifest for UI gating | Medium | Medium | High | Backend capabilities | Frontend/Backend | Prevents dead actions. |

### Medium-Term

| Item | Priority | Effort | Impact | Dependencies | Owner | Rationale |
| --- | --- | --- | --- | --- | --- | --- |
| Canonical gateway/server ingress strategy | High | Large | High | Platform architecture | Platform/Backend | Reduces duplicated policy drift. |
| Durable registry event design store and UI | Medium | Large | Medium | Registry persistence | Backend/Frontend/Data | Completes event design workflow. |
| Unified AI suggestion contract | Medium | Medium | Medium | ML service | ML/Backend/Frontend | Makes AI embedded and governable. |
| Admin replay/retry/DLQ surfaces | Medium | Large | High | Operation ledger | Backend/Frontend/Ops | Completes recovery. |
| Role/permission capability model | High | Medium | High | Identity | Security/Backend/Frontend | Makes governance enforceable. |

### Long-Term

| Item | Priority | Effort | Impact | Dependencies | Owner | Rationale |
| --- | --- | --- | --- | --- | --- | --- |
| Predictive operations and cost forecasting | Medium | Large | Medium | Durable history | ML/Data/Frontend | Turns AEP into proactive cockpit. |
| Full agent install/credential marketplace | Medium | Large | High | Registry/security | Product/Backend/Frontend | Completes catalog strategy. |
| Multi-region/DR automation proof | Medium | Large | High | Platform infra | Platform/Ops | Needed for enterprise production. |
| Accessibility-certified canvas alternate workflow | Medium | Large | Medium | Design system | Design/Frontend/QA | Makes builder inclusive. |

## 15. Final Ideal Product Experience Vision

After remediation, AEP should feel like a calm mission-control surface for agentic execution. A user signs in through platform SSO and lands in the correct tenant with the right role and capabilities already known. They do not paste tokens, choose arbitrary tenants, or reason about backend components.

Operate shows one truthful live picture: what is running, what needs review, what is degraded, what failed, and what AEP recommends next. Live updates work securely. Every run has lineage, policy decisions, retries, human reviews, costs, and audit evidence.

Build lets users describe a goal, get a draft pipeline, validate it against schemas and sample events, simulate it, and publish through an approval-aware lifecycle. Rollback is a safe new version, not a dangerous restore. Delete and publish know about active runs and dependencies.

Catalog exposes agents and workflows that are actually installable. The system checks compatibility, credentials, governance risk, and expected cost before install. Install is a tracked operation with rollback.

Learn works mostly in the background. AEP summarizes episodes, suggests policies, ranks review work, and explains recommendations with evidence. Humans approve anything that changes behavior or risk.

Govern is pervasive but non-intrusive. Tenant isolation, consent, PII handling, audit, role permissions, kill switches, and degradation modes are enforced by default and exposed only when relevant.

The full stack coheres around a few shared concepts: tenant, operation, run, pipeline version, review item, policy decision, audit event, and capability. Every layer uses the same meanings and the same lifecycle contracts.

## 16. Executive Summary Lists

### Top 10 Critical Issues

1. Authenticated SSE is broken from the browser UI.
2. Batch event ingestion bypasses single-event security/privacy/idempotency controls.
3. Run cancellation is not tenant-scoped and not actual execution preemption.
4. `/ready` is not dependency-truthful.
5. Tenant defaults to `"default"` instead of failing closed.
6. Run metrics and cancellation depend on bounded in-memory state.
7. Idempotency is in-memory, global, and non-durable.
8. Marketplace install is visually present but not implemented.
9. Audit-log UI calls have no discovered backend routes.
10. Pipeline publish/rollback/delete lack consistent governance and concurrency safeguards.

### Top 10 Completeness Gaps

1. Browser-authenticated live stream.
2. Durable operation/run state.
3. Durable audit API.
4. Marketplace install lifecycle.
5. Batch ingestion operation lifecycle.
6. Pipeline approval/diff/rollback/delete lifecycle.
7. Connector binding UI and durable registry store.
8. Server-side consent and PII enforcement.
9. Admin replay/retry/DLQ surfaces.
10. Capability-driven UI gating.

### Top 10 Simplification Opportunities

1. Remove manual tenant entry for normal users.
2. Remove JWT paste from default flow.
3. Hide runtime component details behind actionable status.
4. Merge review queues into one inbox.
5. Merge health/readiness/durability truth.
6. Generate API client from one contract.
7. Replace hand-coded path normalization with standard envelopes.
8. Automate pipeline validation and dry-run.
9. Automate agent compatibility checks.
10. Prefill AI suggestions with evidence and safe defaults.

### Top 10 Correctness Issues

1. SSE auth mismatch.
2. Batch bypass.
3. Non-tenant-scoped cancel.
4. Memory-only metrics.
5. Default tenant.
6. Non-durable idempotency.
7. 200 invalid validation semantics.
8. Missing audit routes.
9. Stage suggestion path drift.
10. Rollback/live snapshot risk.

### Top 10 Consistency Issues

1. Gateway vs backend auth/tenant behavior.
2. UI vs backend session semantics.
3. Single vs batch ingestion controls.
4. Run `id` vs `runId`.
5. Pipeline update vs publish/rollback/delete concurrency.
6. Health vs ready vs durability.
7. Generated OpenAPI vs hand-coded API calls.
8. Sensitive dialog use vs raw confirm.
9. Tenant ID regex differences.
10. AI suggestion pathways.

### Top 10 API/Backend/Data Issues

1. Missing canonical operation ledger.
2. Missing durable idempotency.
3. Missing audit API.
4. Missing platform session API or wrong UI call.
5. Missing marketplace install API.
6. Missing/potentially misrouted stage suggestion API.
7. In-memory session state.
8. In-memory event design store.
9. In-memory EventCloud fallback.
10. Disabled OpenAPI drift test.

### Top 10 AI/ML Opportunities

1. Pipeline stage generation with validation.
2. Validation error repair suggestions.
3. Run anomaly triage.
4. HITL review prioritization.
5. Learned policy recommendations.
6. Agent compatibility recommendations.
7. Marketplace trust/risk summary.
8. PII classification and quarantine.
9. Incident/run summary.
10. Next-best remediation.

### Top 10 Trust/Privacy/Security/O11y Improvements

1. httpOnly/cookie or short-lived SSE token auth.
2. Server-side consent.
3. PII policy enforcement.
4. Append-only audit log.
5. Tenant from verified identity only.
6. Role/capability-gated UI and API.
7. Profile-aware readiness.
8. Durable run/operation state.
9. Correlation ID from UI through ledger.
10. Support bundle/replay/DLQ tools.

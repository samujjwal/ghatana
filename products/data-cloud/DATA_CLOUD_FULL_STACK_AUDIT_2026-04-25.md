# Data Cloud Full-Stack Product Audit and Remediation Blueprint

Date: 2026-04-25
Auditor: Codex
Scope: `products/data-cloud` product docs, UI, launcher API, platform launcher, governance, workflow, observability, security, API contracts, tests, and supporting strategic materials.

## 1. Executive Summary

Data Cloud has the right strategic thesis: a context-native operational data fabric that unifies tenant-aware entities, event streaming, AI context, workflow automation, governance, and deployable operations. The product documents make that bar explicit: the strategic report defines the differentiator as "entity storage, event streaming, context-aware AI agents, and policy-enforced governance into one operator-deployable system" (`STRATEGIC_POSITIONING_2026-04-13.md:9-13`), and the overview defines the operating promise as a unified multi-tenant, event-driven, AI/ML-native platform (`DATA_CLOUD_COMPREHENSIVE_OVERVIEW.md:11-18`).

The implementation is directionally aligned but not yet enterprise-complete. Some P0 trust items have materially improved: non-local storage discovery now fails when no durable provider is available (`DataCloud.java:85-103`, `DataCloud.java:106-140`), tenant enforcement has targeted passing tests, and the UI route-truth matrix is current. But the product still has large gaps where visible UX, API contracts, backend semantics, and data lifecycle behavior do not match the strategic promise.

Overall product health: High risk, promising foundation. Production readiness is not achieved. The main blocker is not visual polish; it is truthfulness and end-to-end closure. The product exposes broad, modern surfaces, but multiple flows only partially work: search/filter/sort/pagination are advertised but ignored, settings and AI operations clients target endpoints the launcher does not expose, point-in-time history cannot reconstruct actual state, workflow execution depends on an optional plugin, and readiness/health are still optimistic for unconfigured dependencies.

Completeness: High risk. Core surfaces exist, but many are boundary, preview, optional, in-memory, or heuristic. The user can start many business jobs but cannot reliably finish them with durable state, audit closure, recovery, and consistent status visibility.

Simplicity: Medium risk. The new information architecture is better than the old "everything page" surface, but backend capability leakage remains. Users still see Data, Pipelines, Query, Insights, Trust, Events, Operations, Plugins, plus preview/boundary concepts. The ideal simple product should collapse around "ingest/connect, understand, automate, govern, operate" and hide unsupported fabric/AI/plugin internals until configured.

Correctness: Critical risk. Several user-visible states can be wrong: collection and pipeline filters imply server behavior that does not exist; counts are page counts, not totals; health can say ready when critical dependencies are not configured; compliance can mark all collections classified because it only counts policies; point-in-time history starts from current state and lacks CDC data.

Consistency: High risk. The most important inconsistency is contract drift: UI clients, OpenAPI/route expectations, backend handlers, and strategic docs do not share one canonical capability/status model. Route truth is centralized, but runtime capability truth is not consistently enforced across all surfaces.

Biggest systemic weaknesses:
- Contract drift between UI clients and launcher routes.
- Data/query abstractions that drop filter/sort/search semantics.
- Capability registry present but not yet the product's true gating authority.
- Trust surfaces that are present but partial: readiness, audit, policy, settings, compliance.
- AI/ML is mostly boundary-aware, heuristic, or endpoint-scattered rather than embedded in core workflows.
- Quality gates are not yet green: UI type-check fails, and platform-launcher coverage gates are intentionally low.

## 2. Deep Audit Scorecard

| Area | Rating | Evidence and justification |
| --- | --- | --- |
| Completeness | High | Major screens and routes exist, but settings, AI operations, workflow execution, context/fabric/memory, feature store, SDK, governance reporting, and operational closure are partial or optional. |
| Simplicity | Medium | IA is simplified (`routes.tsx:1-9`), but unsupported capabilities still leak through boundary pages and direct routes (`RouteCapabilityRegistry.ts:139-235`). |
| Correctness | Critical | Query/filter/sort, health readiness, compliance counts, point-in-time history, and SDK remote client behavior can produce false outcomes. |
| Consistency | High | UI expects settings and AI endpoints that backend does not route (`settings.service.ts:168-178`, `DataCloudRouterBuilder.java:473-478`; `ai-operations.service.ts:263-315`, `DataCloudRouterBuilder.java:301-309`). |
| UI/UX quality | Medium | Route model is cleaner, lazy boundaries exist, but active/boundary/preview truth is inconsistent with product promise and type-check is failing. |
| Frontend quality | High | Type-check fails in Data Cloud UI and shared canvas dependencies; API cache invalidation and adapter contracts can show stale or incorrect data. |
| API/contract quality | High | Broad route surface exists, but contracts omit or ignore important semantics, especially pagination/filtering/search/sort, settings, AI operations, and SDK generation. |
| Backend/workflow quality | High | Entity CRUD and workflow APIs exist, but orchestration is incomplete: workflows are plugin-dependent, batch writes are not transactional, and history is best-effort. |
| Data/persistence quality | High | Production durable provider fail-fast is improved, but local/default/in-memory patterns remain widespread; query semantics are too weak for product UX. |
| Observability/operability | High | Correlation IDs and metrics exist, but readiness is dependency-optimistic and traces are discarded when exporters are absent. |
| Privacy/security/trust | High | Auth/tenant hardening improved, but policy and audit are nullable, settings/key lifecycle is missing, and compliance summaries are not truth-preserving. |
| AI/ML embedding quality | High | AI assist endpoints exist, but AI operations are boundary-only, suggestions are heuristic, and no pervasive governance/feedback loop exists. |
| Accessibility | Medium | Common UI patterns exist; needs systematic keyboard/screen-reader verification across boundary/loading/error/table/workflow controls. |
| Responsiveness | Medium | Modern React/Tailwind structure exists; needs Playwright/mobile verification for dense operation screens and tables. |
| Perceived performance | Medium | Lazy loading and cache exist, but cache staleness, lack of true pagination totals, and optional search can hurt trust. |
| Cognitive load | Medium | Navigation is cleaner but capability state and unsupported surfaces create interpretation burden. |
| End-to-end product quality | High | Strong architecture and strategic shape, but too many paths lack complete persistence, audit, runtime truth, and recovery closure. |

## 3. Surface-by-Surface and Layer-by-Layer Audit

### Home / Intelligent Hub

Purpose: central operator entry point and quick-action surface.

Completeness: Partial. It links users into `/data`, `/pipelines/new`, operations, and insights, but correctness depends on downstream surfaces that are partial. Activity and recommendation content must be runtime-backed, not static or heuristic.

Simplicity: Good direction. The hub can become the near-zero cognitive load entry point if it is driven by capability registry, health, alerts, and next-best actions.

Correctness: Risk. Quick actions should only appear when backing capabilities are configured. Do not show AI/pipeline/fabric actions unless the capability registry says they are live.

Consistency: Needs a single "runtime truth" source shared by nav, search, hub, and page-level boundaries.

Remediation: Make the hub the consumer of `/api/v1/capabilities`, `/ready`, alerts, jobs, and governance summary. Use one status vocabulary: live, degraded, unavailable, preview, boundary.

### Data Explorer / Collections / Entities

Purpose: create, browse, query, and inspect collections and tenant-scoped entities.

Completeness: Incomplete. UI collection list supports page/search/status/schemaType/sort (`collections.ts:103-114`), but the client only sends limit, offset, and search (`collections.ts:127-132`), and backend entity query only validates limit and creates `DataCloudClient.Query.limit(limit)` (`EntityCrudHandler.java:232-263`). The platform query adapter only passes collection, offset, and limit (`DataCloud.java:350-358`), and the in-memory store only filters by collection, offset, and limit (`DataCloud.java:551-558`).

Simplicity: False simplicity. The UI gives familiar controls, but because backend semantics are missing, users must manually verify outcomes.

Correctness: Critical. Search, filters, sort, and total counts can be wrong. Backend returns `count = entities.size()` (`EntityCrudHandler.java:264-274`), so `hasMore` in the UI can be false even when more records exist.

Consistency: High drift between strategic event-sourced context promise and CRUD implementation. The overview promises event replay, temporal queries, and audit trail (`DATA_CLOUD_COMPREHENSIVE_OVERVIEW.md:198-207`), but save events omit full data payload (`EntityCrudHandler.java:146-151`).

Remediation: Implement a canonical query contract with filter/search/sort/offset/limit/total/hasMore. Thread those fields through `DataCloudClient.Query`, `EntityStore.QuerySpec`, OpenAPI, UI clients, mocks, and tests. Make collection registry a first-class API or formally document `dc_collections` as a system collection with schema and lifecycle.

### Entity History / Temporal Query

Purpose: reconstruct entity state at a past instant.

Completeness: Incomplete. Route is `/api/v1/entities/:collection/:id/history` (`DataCloudRouterBuilder.java:102-109`) but handler requires `asOf` and reconstructs from current entity state (`EntityCrudHandler.java:618-670`). Save CDC event payload contains `collection`, `id`, `version`, `operation`, but no `data` (`EntityCrudHandler.java:146-151`), while history replay only applies events that contain a `data` patch (`EntityCrudHandler.java:681-688`).

Correctness: Critical. A temporal query can return current data for a historical instant, which violates the docs' temporal query and audit promise (`DATA_CLOUD_COMPREHENSIVE_OVERVIEW.md:202-207`).

Remediation: Store complete CDC payloads or durable version snapshots. Reconstruct from genesis forward, not current backward. Add tests for create/update/delete over time, including missing data, deleted entities, and version ordering.

### Pipelines / Workflows

Purpose: create, list, execute, observe, and manage data workflows.

Completeness: Partial. CRUD exists, but execution requires a `WorkflowExecutionCapability`; otherwise backend returns 503 (`WorkflowExecutionHandler.java:44-47`, `WorkflowExecutionHandler.java:78-81`). The strategic report calls complete workflow run/state/history/cancel a P2 differentiator and says it is currently stubbed (`STRATEGIC_POSITIONING_2026-04-13.md:161-162`).

Simplicity: Good UI direction, but operational burden leaks: users must understand plugin deployment to know why execution fails.

Correctness: High. UI sends status/search/sort for list (`workflows.ts:184-195`), but backend list only reads `limit` and returns count of returned pipelines (`PipelineCheckpointHandler.java:42-58`).

Consistency: High drift. UI terminology "workflows" maps to backend "pipelines"; this is acceptable only if one canonical vocabulary is maintained in API/docs/microcopy.

Remediation: Make pipeline list support filter/sort/search/page/total. Add a first-party execution engine or make execution unavailable everywhere until plugin is installed. Add execution lifecycle statuses, retries, cancellation semantics, logs, progress, audit events, and notifications.

### Query / SQL / Analytics

Purpose: query operational and analytical data and expose explain/reporting.

Completeness: Partial. Routes exist for analytics and report endpoints, but federated query, Trino, ClickHouse, OpenSearch, and cost tiers remain optional. Search endpoint returns 501 when OpenSearch is absent (`EntityCrudHandler.java:520-538`).

Simplicity: Needs default "query what is live" behavior. Users should not need to know whether data sits in hot/warm/cool/cold tiers.

Correctness: Risk. If query surfaces combine entity queries, OpenSearch, ClickHouse, and Trino without a shared pagination, status, and freshness model, results will feel arbitrary.

Remediation: Establish one query broker contract: source capabilities, execution id, freshness, partial result warnings, cost estimate, cancellation, trace id, and explain plan.

### Trust Center / Governance / Privacy

Purpose: make governance operational: classify retention, purge, redact, verify, summarize compliance.

Completeness: Improved but incomplete. Real purge tombstones and redaction paths exist, but compliance summary cannot discover unclassified collections because it sets `collectionsTotal = policies.size()` and `collectionsUnclassified = 0` (`DataLifecycleHandler.java:788-819`). PII derivation is by collection-name convention, not registry/schema/data scan (`DataLifecycleHandler.java:777-786`).

Simplicity: Good goal, but current summary can hide the work users actually need to do.

Correctness: Critical. "COMPLIANT" can be produced from policy count alone (`DataLifecycleHandler.java:817`), which is unsafe for governance.

Consistency: Trust language in docs says governance actually enforces, redacts, purges (`STRATEGIC_POSITIONING_2026-04-13.md:64-72`), but policy engine and audit are nullable in the security filter (`DataCloudSecurityFilter.java:105-106`).

Remediation: Build a real governance inventory from collections, schemas, policies, holds, audit events, and redaction verification. Fail closed or explicitly mark policy/audit as unavailable in production. Add legal hold enforcement to purge and redact paths.

### Alerts / Operations

Purpose: diagnose runtime issues, group incidents, apply remediation, inspect jobs and health.

Completeness: Partial. Alert list supports severity/status in memory after loading a bounded set (`AlertingHandler.java:50-74`). Suggestions and groups are deterministic heuristics with confidence numbers (`AlertingHandler.java:327-358`), not model-backed operations intelligence.

Correctness: Medium to High. Alert count is returned page size, not total (`AlertingHandler.java:68-72`). Acknowledge/resolve do not appear to capture operator reason, review, or postmortem context (`AlertingHandler.java:77-83`).

Simplicity: Grouping is useful, but only if confidence and automation scope are truthful.

Remediation: Add alert total counts, cursor pagination, reason/comment fields, audit trail, action history, escalation status, incident lifecycle, and model provenance for AI suggestions.

### Settings / Admin / API Keys

Purpose: manage user/tenant settings, API keys, security preferences, notifications.

Completeness: Incomplete. Backend exposes only `GET/POST /api/v1/settings` and `GET/POST /api/v1/settings/security` (`DataCloudRouterBuilder.java:473-478`) backed by an in-memory map explicitly marked "Replace with persistent backend" (`SettingsHandler.java:40-42`). UI service expects `/settings/keys`, `/settings/profile`, `/settings/preferences`, and `/settings/notifications` with PATCH/DELETE semantics (`settings.service.ts:168-178`).

Correctness: Critical for trust. API key lifecycle and profile/preferences cannot work end to end against the launcher contract.

Remediation: Either remove/hide settings until supported, or implement persistent tenant-scoped settings, API key creation/revocation, profile, preferences, notification channels, RBAC, audit, and secret one-time reveal.

### Plugins / Capability Registry

Purpose: expose what is installed, available, degraded, and unsupported.

Completeness: Partial. `/api/v1/capabilities` exists (`DataCloudRouterBuilder.java:342`), route capability registry exists in the UI (`RouteCapabilityRegistry.ts:1-12`), and the strategy calls capability registry P1 (`STRATEGIC_POSITIONING_2026-04-13.md:149-150`). But route truth and runtime truth are not yet a single authority.

Correctness: High. Static UI route lifecycle can say active while backend capability is unavailable. Settings is boundary in route registry (`RouteCapabilityRegistry.ts:226-235`) but backend has partial settings endpoints; Alerts is boundary in registry (`RouteCapabilityRegistry.ts:139-149`) but backend has alert routes.

Remediation: Make runtime capability snapshots drive navigation, hub cards, global search, page tabs, CTA enablement, and boundary copy. Include source, mode, dependency, degraded reason, last probe, and documentation link.

### AI / ML / Context / Agents / Fabric

Purpose: make AI/ML an embedded system quality through context construction, recommendations, automation, anomaly detection, and agent memory.

Completeness: Incomplete. UI AI operations service explicitly targets `/api/v1/ai/suggestions`, `/ai/correlations`, and advisories (`ai-operations.service.ts:263-315`), but router exposes only `/entities/:collection/suggest`, `/analytics/suggest`, `/pipelines/draft`, `/pipelines/:id/optimise-hint`, `/brain/explain`, and `/ai/quality-summary` (`DataCloudRouterBuilder.java:301-309`). Alerts suggestions are heuristic (`AlertingHandler.java:327-358`). Strategic P2/P3 context APIs, embeddings, memory, agent MCP, anomaly detection, feature serving, and autonomous operations remain mostly future-state (`STRATEGIC_POSITIONING_2026-04-13.md:155-183`).

Simplicity: AI is currently surfaced as several endpoint families and pages, not a quiet embedded assistant.

Correctness: Risk. Confidence numbers without model provenance or evaluation imply more rigor than exists.

Remediation: Define an AI operations substrate with suggestion, action, provenance, confidence band, input features, model/provider, review policy, apply/rollback, and audit event. Use it behind Data, Pipelines, Trust, Alerts, and Operations instead of separate AI feature noise.

### Health / Observability / Readiness

Purpose: tell operators whether the system can serve correctly and make failures diagnosable.

Completeness: Partial. `/health` always returns UP (`HealthHandler.java:70-75`). Detail states checks are lightweight in-process, not active network probes (`HealthHandler.java:89-91`). Default database and event store are `NOT_CONFIGURED` (`HealthHandler.java:152-155`), but `/ready` only fails when those subsystems are `DOWN` (`HealthHandler.java:240-261`).

Correctness: Critical. Strategic P0 requires dependency-truthful health (`STRATEGIC_POSITIONING_2026-04-13.md:138-139`), but readiness can say READY when database/event_store are not configured.

Remediation: Split liveness from readiness from capability. `/live` can be process-only. `/ready` must fail for required dependencies that are missing, unknown, down, or not warmed. `/health/detail` can include optional dependency status. Add trace IDs to all error envelopes and make trace export status visible.

### Security / Tenant Isolation / Policy

Purpose: guarantee tenant isolation, authentication, authorization, policy checks, and audit.

Completeness: Improved but incomplete. Non-local modes now fail without auth in bootstrap, and the security filter requires API key or JWT provider (`DataCloudSecurityFilter.java:96-99`). Strict tenant resolution rejects missing explicit tenant (`RequestObservationFilter.java:85-100`). But non-strict mode still falls back to `default` tenant (`RequestObservationFilter.java:57-61`), and policy/audit are nullable (`DataCloudSecurityFilter.java:105-106`).

Correctness: High. Policy enforcement should not silently disappear in any deployable environment that claims enterprise readiness.

Remediation: Fail closed in production when policy/audit dependencies are missing. Bind API keys to tenant, scopes, roles, expiry, rotation, and audit. Make default-tenant mode only test/local and visually bannered.

### SDK / Developer Experience

Purpose: let developers use Data Cloud reliably through generated clients and stable contracts.

Completeness: Incomplete. Strategic P3 calls for generated Java/TypeScript/Python SDKs from OpenAPI (`STRATEGIC_POSITIONING_2026-04-13.md:179`). But `HttpDataCloudClient` methods return null, empty, or zero values (`HttpDataCloudClient.java:49-152`) and health explicitly says transport is not implemented (`rg` evidence at `HttpDataCloudClient.java:194`).

Correctness: Critical for platform adoption. A remote client facade that returns successful empty/null data can hide data loss and integration failure.

Remediation: Delete or clearly mark the facade unsupported, or implement real HTTP transport generated from the canonical OpenAPI. Never return success-shaped placeholders.

## 4. End-to-End Flow Review

### Flow 1: Create and Browse a Collection

User goal: define a data collection, then find it later.

Entry point: Home or Data Explorer -> create collection -> `/api/v1/entities/dc_collections`.

Frontend behavior: `collectionsApi.create` posts data and locally constructs a collection from the save response (`collections.ts:159-165`). `collectionsApi.list` supports page/search/status/schemaType/sort types but sends only limit, offset, search (`collections.ts:127-132`).

API/backend: Entity save persists the payload and emits a minimal CDC event (`EntityCrudHandler.java:138-178`). Entity query ignores search, offset, filters, and sort at handler level (`EntityCrudHandler.java:232-263`).

Persistence/state: Stored as generic entity, not a first-class collection lifecycle.

Async/audit: Save appends `entity.saved`, but no full data patch for later reconstruction.

Failure/recovery: Schema validation only if `schemaValidator` is configured (`EntityCrudHandler.java:131-135`). No collection-specific duplicate/name lifecycle guarantees.

Gaps: false filters, wrong totals, weak lifecycle, weak audit history.

Ideal future journey: user creates a collection; system validates schema, classifies PII, assigns governance/freshness/lineage defaults, creates durable collection record, emits full CDC/audit event, indexes search, returns canonical representation, and list filters/sorts correctly with total and freshness.

### Flow 2: Query/Search Entity Data

User goal: find relevant records quickly and trust the result set.

Entry point: Data Explorer filters, search box, table paging, search endpoint.

Frontend behavior: passes query params variably; cache can serve stale data for list endpoints because invalidation is prefix/path based (`client.ts:174-187`).

API/backend: basic query ignores semantics; full-text search returns 501 if OpenSearch absent (`EntityCrudHandler.java:520-538`).

Persistence/state: generic store query supports only collection/offset/limit in default client (`DataCloud.java:350-358`).

Gaps: no canonical filter language, no total count, no sorted order guarantee, no partial capability warning.

Ideal future journey: query broker returns results, total/cursor, source freshness, applied filters, warnings, trace id, and explain. If OpenSearch is absent, the UI hides full-text features or labels fallback behavior.

### Flow 3: Execute a Pipeline

User goal: run a workflow, watch progress, inspect logs, retry/cancel.

Entry point: Pipelines list/detail -> execute.

Frontend behavior: uses pipeline/workflow API and expects execution lifecycle objects.

API/backend: execution returns 503 without plugin (`WorkflowExecutionHandler.java:44-47`). List executions returns all plugin snapshots with page 1 and no real pagination (`WorkflowExecutionHandler.java:83-90`).

Persistence/state: registry saves arbitrary maps in `dc_pipelines` without strong validation (`PipelineCheckpointHandler.java:67-72`).

Gaps: no first-party engine, no scheduler, no retry policy, no queue visibility, no failure compensation, no execution audit unless plugin supplies it.

Ideal future journey: create pipeline from template or AI draft, validate DAG, execute via durable job engine, stream progress/logs, emit audit/events, support retry/cancel, and explain failures with direct remediation.

### Flow 4: Govern Data with Retention, Purge, and Redaction

User goal: classify a collection, purge expired records, redact PII, prove compliance.

Entry point: Trust Center -> retention/privacy/compliance.

Frontend behavior: governance service derives policy, violations, audit logs, and recommendations from summaries rather than a complete governance domain model.

API/backend: purge/redact endpoints exist, tombstones are saved (`DataLifecycleHandler.java:900-925`), policies are loaded from `dc_governance_policies` (`DataLifecycleHandler.java:936-943`).

Persistence/state: policy inventory and collection inventory are not reconciled; compliance summary assumes all known collections are classified (`DataLifecycleHandler.java:788-819`).

Gaps: legal holds, PII registry, audit dependency truth, continuation for large purge sets, role approvals, evidence export, and verification closure.

Ideal future journey: system inventories collections, classifies PII from schema/data, recommends retention, blocks purge under hold, requires approval token for destructive operations, emits immutable audit, verifies redaction, and presents evidence package.

### Flow 5: Operate and Diagnose Runtime

User goal: know if system is healthy, what is failing, and what to do next.

Entry point: Operations, Alerts, `/health`, `/ready`, `/metrics`.

Frontend/backend: health exists, alerts exist, metrics can scrape if registry is Prometheus (`HealthHandler.java:280-290`).

Gaps: readiness can be optimistic, traces may be discarded, alerts/suggestions are heuristic, count/status history is shallow.

Ideal future journey: operations page shows readiness, capability state, dependency probes, job states, traces, logs, alert groups, suggested remediations, and audit of actions in one consistent model.

### Flow 6: Admin API Key and Security Settings

User goal: create/revoke keys, manage profile/preferences/notifications/security.

Entry point: Settings.

Frontend/backend: UI expects key/profile/preference/notification endpoints (`settings.service.ts:168-178`), backend exposes only in-memory general/security settings (`SettingsHandler.java:17-29`, `DataCloudRouterBuilder.java:473-478`).

Gaps: no persistent API key lifecycle, no rotation, no scopes, no revoke audit, no notification delivery preferences, no profile storage.

Ideal future journey: admin creates scoped tenant-bound key, sees one-time secret, can rotate/revoke, and every action is audited.

### Flow 7: AI Assistance and Context-Native Operations

User goal: get intelligent defaults, summaries, recommendations, and safe automation without managing models.

Entry point: Insights, Alerts suggestions, AI assist endpoints, pipeline draft, quality summary.

Frontend/backend: UI AI operations service targets a richer contract not implemented by launcher (`ai-operations.service.ts:263-315` vs `DataCloudRouterBuilder.java:301-309`).

Gaps: no unified AI action model, no confidence governance, no model provenance, no evaluation, no rollback, no embedded context construction.

Ideal future journey: system quietly summarizes state, pre-fills configs, detects anomalies, prioritizes work, proposes safe actions, requires review only for risk, and logs every AI-driven action.

## 5. Comprehensive Findings Catalog

| ID | Title | Severity | Category | Layers | Dimension | Evidence | Why it matters | Recommended fix |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DC-AUD-001 | Readiness is optimistic for missing critical dependencies | Critical | Observability | API/backend/ops | correctness, trust | `/ready` only fails when db/event store are DOWN, not NOT_CONFIGURED (`HealthHandler.java:240-261`) | Operators can deploy broken systems as ready | Make readiness fail for required missing/unknown dependencies by profile |
| DC-AUD-002 | Entity query drops search/filter/sort/offset semantics | Critical | Data/API | UI/API/backend/data | correctness, completeness | UI sends limit/offset/search (`collections.ts:127-132`); handler uses only limit (`EntityCrudHandler.java:244-263`) | Data tables lie; users cannot find records reliably | Add canonical query contract and implement end to end |
| DC-AUD-003 | Collection and pipeline counts are page counts | High | API | API/frontend | correctness | entity count is `entities.size()` (`EntityCrudHandler.java:273`); pipeline count is `pipelines.size()` (`PipelineCheckpointHandler.java:53-57`) | Pagination/hasMore can be false incorrectly | Return total, page/cursor, hasMore |
| DC-AUD-004 | Pipeline list ignores UI filter/search/sort | High | Workflow | UI/API/backend | correctness, consistency | UI sends params (`workflows.ts:184-195`); backend reads only limit (`PipelineCheckpointHandler.java:42-58`) | Operators cannot triage workflows reliably | Implement server-side filter/search/sort/page |
| DC-AUD-005 | Workflow execution is plugin-dependent without full product closure | High | Workflow | UI/API/backend/async | completeness | 503 when plugin absent (`WorkflowExecutionHandler.java:44-47`) | Core workflow job cannot be completed by default | Ship first-party engine or gate all execution UI by capability |
| DC-AUD-006 | Settings UI contract does not match backend routes | Critical | Admin/security | UI/API/backend/data | completeness, consistency | UI endpoints (`settings.service.ts:168-178`); backend routes (`DataCloudRouterBuilder.java:473-478`) | API key/profile/preferences flows cannot work | Implement contract or remove/hide settings |
| DC-AUD-007 | Settings persistence is in-memory | High | Admin | backend/data/security | completeness, trust | `SettingsHandler` uses in-memory maps (`SettingsHandler.java:40-42`) | Admin/security state disappears on restart | Persist tenant/user settings and audit changes |
| DC-AUD-008 | AI operations client targets endpoints not routed | High | AI/API | UI/API/backend | completeness, consistency | UI `/ai/suggestions`, `/ai/correlations` (`ai-operations.service.ts:263-315`); router exposes different AI assist endpoints (`DataCloudRouterBuilder.java:301-309`) | AI surfaces become boundary-only or broken | Define one AI operations API and generate clients |
| DC-AUD-009 | AI confidence is heuristic but presented as AI-like | Medium | AI/trust | backend/UI | correctness, trust | alert confidence constants (`AlertingHandler.java:327-358`) | Users may over-trust deterministic heuristics | Add provenance or label as rule-based |
| DC-AUD-010 | Point-in-time history cannot reconstruct truth | Critical | Data/event | backend/data | correctness | CDC save event lacks `data` (`EntityCrudHandler.java:146-151`); history replays `data` patches (`EntityCrudHandler.java:681-688`) from current state (`EntityCrudHandler.java:644-670`) | Temporal query and audit trail are unsafe | Store full CDC or snapshots and replay from genesis |
| DC-AUD-011 | Compliance summary hides unclassified collections | Critical | Governance | backend/data/UI | correctness, trust | `collectionsTotal = collectionsClassified`, `collectionsUnclassified = 0` (`DataLifecycleHandler.java:788-819`) | Trust Center can falsely declare compliance | Reconcile collection inventory with policy inventory |
| DC-AUD-012 | PII classification is name-convention based | High | Privacy | backend/data/AI | completeness, correctness | `derivePiiFields` checks collection name (`DataLifecycleHandler.java:777-786`) | Real PII can be missed | Add PII registry plus schema/data scanning |
| DC-AUD-013 | Policy and audit enforcement are nullable | Critical | Security/governance | backend/security | correctness, trust | comments say policy/audit skipped when null (`DataCloudSecurityFilter.java:105-106`) | Critical routes can run without governance controls | Fail closed in production or capability-banner degraded mode |
| DC-AUD-014 | Default tenant fallback exists in non-strict mode | Medium | Security | backend | consistency, trust | fallback to `default` tenant (`RequestObservationFilter.java:57-61`) | Local data can mask tenant bugs | Restrict to explicit local/test mode and banner it |
| DC-AUD-015 | Remote HTTP client is placeholder-successful | Critical | SDK/API | backend/sdk | correctness, completeness | methods return null/empty/zero (`HttpDataCloudClient.java:49-152`) | Developers can build against false client behavior | Implement generated transport or remove facade |
| DC-AUD-016 | Full-text search is optional but not consistently capability-gated | High | Search | UI/API/backend | completeness, simplicity | 501 when OpenSearch absent (`EntityCrudHandler.java:520-538`) | Users hit runtime failures for visible search controls | Gate feature by capability and provide fallback search |
| DC-AUD-017 | Capability registry is not the universal truth source | High | Product architecture | UI/API/backend | consistency, simplicity | static route lifecycles (`RouteCapabilityRegistry.ts:68-236`) vs runtime routes | Nav/search/pages can diverge from runtime | Drive all route/tabs/CTAs from runtime capabilities |
| DC-AUD-018 | Alerts totals and resolution closure are incomplete | Medium | Operations | UI/API/backend/data | completeness, correctness | alert count page size (`AlertingHandler.java:68-72`); acknowledge/resolve no reason body (`AlertingHandler.java:77-83`) | Operators lack incident history | Add reason, actor, comments, total, SLA, audit |
| DC-AUD-019 | Query/data lifecycle lacks transactional guarantees for batch work | High | Data | backend/data | correctness | batch save/delete flows are promise chains without rollback or per-item contract | Partial writes can surprise users | Add transactional or per-item idempotent batch result |
| DC-AUD-020 | UI type-check is failing | High | Quality | frontend | correctness, production readiness | `pnpm --filter @data-cloud/ui type-check` failed with React Flow/canvas, schema, RBAC, selection, and missing NLP/voice module errors | Product cannot be treated as release-ready | Restore green type-check and CI gate |
| DC-AUD-021 | Platform-launcher coverage gate is intentionally low | Medium | Quality | tests | completeness | coverage minimums 0.26/0.20 with TODO (`platform-launcher/build.gradle.kts:150-157`) | Enterprise confidence is weak | Raise gates after covering core paths |
| DC-AUD-022 | Strategic docs overstate current 85+ API completeness | Medium | Product/docs | docs/API | consistency | overview claims 85+ endpoints (`DATA_CLOUD_COMPREHENSIVE_OVERVIEW.md:63-70`); implementation has many optional/501/503/boundary routes | Buyers may distrust product claims | Mark capabilities as live/partial/preview/unavailable |
| DC-AUD-023 | Event-driven core promise is not met for every change | High | Event/data | backend/data | completeness, correctness | docs promise immutable event log as source of truth (`DATA_CLOUD_COMPREHENSIVE_OVERVIEW.md:198-207`); entity save emits minimal event | Replay/audit/agent context incomplete | Define event envelopes for all mutations with full semantics |
| DC-AUD-024 | Local/in-memory implementations are widespread | Medium | Data/ops | backend/data | trust | numerous in-memory fallbacks from `rg`; LOCAL uses in-memory (`DataCloud.java:97-99`, `DataCloud.java:113-115`) | Demo mode can masquerade as product mode | Strong mode labels, startup warnings, capability states |
| DC-AUD-025 | OpenAPI/generated client strategy is not enforced | High | API | docs/API/UI/SDK | consistency | strategic T5/F6 require canonical OpenAPI (`STRATEGIC_POSITIONING_2026-04-13.md:136`, `179`) but UI clients hand-code divergent routes | Contracts drift repeatedly | Generate typed clients and contract tests |

## 6. Completeness Gap Inventory

Missing screens/surfaces:
- Runtime capability details page showing live/degraded/unavailable reasons per capability.
- First-class collection lifecycle page with schema, governance class, freshness, lineage, retention, PII, and usage.
- Durable job center tied to pipeline execution engine, retries, logs, queues, and failures.
- Persistent admin settings, API key lifecycle, profile/preferences, notification delivery.
- Governance evidence/export page for compliance proofs.
- AI action review center with suggestion provenance, approvals, overrides, and outcomes.
- Tenant/admin security posture page with auth provider, key scopes, policy/audit status.

Missing states:
- Partial capability configured/unconfigured/degraded at page, tab, CTA, and field level.
- Query partial-result/fallback/freshness states.
- Pipeline queued/running/retrying/canceling/canceled/failed/compensated states.
- Redaction/purge dry-run, pending approval, blocked by hold, partially completed, verified.
- Alert acknowledged with reason, suppressed, escalated, linked incident, resolved with proof.

Missing validations:
- Collection schema name uniqueness and lifecycle constraints.
- Pipeline DAG validation, node/edge schema, schedule validation.
- Query filter/sort field validation against schema.
- Settings/API key scope/expiry/role validation.
- Governance legal hold and PII registry validation.

Missing backend/API support:
- Canonical filter/search/sort/pagination/count query contract.
- Full settings API expected by UI.
- AI operations API expected by UI.
- Durable workflow execution engine or hard runtime gating.
- Real SDK HTTP transport.
- Generated OpenAPI clients and contract tests.

Missing persistence:
- Persistent settings/profile/preferences/notifications/API keys.
- Full CDC event payloads or snapshots for temporal queries.
- Workflow execution state and logs if no plugin is installed.
- Alert action comments/reasons/postmortem.
- Capability probe history and health snapshots.

Missing audit/history/notification:
- API key create/revoke audit.
- Policy enforcement/audit dependency visibility.
- Purge/redact approval and verification evidence.
- Workflow execution audit and user notification.
- AI suggestion apply/override audit.

Missing admin/governance:
- Tenant-scoped RBAC and policy-as-code management.
- Legal hold management.
- Data retention exception workflow.
- Incident/alert escalation and notification routing.

Missing recovery:
- Batch mutation rollback or per-item idempotency.
- Workflow retry/compensation.
- Search/index repair and reindex jobs.
- Trace/export DLQ visibility.

Missing accessibility:
- Keyboard-only verification for tables, modals, drawers, workflow canvas, command bar, and action menus.
- Screen-reader copy for boundary/unavailable states.
- Focus management after lazy-load failure and mutation errors.

Missing automation:
- Auto PII classification from schemas/data samples.
- Auto context construction from schemas, query history, lineage, and governance.
- Auto issue grouping and next-best-action in operations with review gates.
- Auto tier recommendations and optional approved migrations.

## 7. Simplification Plan

Remove:
- Visible AI/fabric/memory/settings controls when runtime capability is unavailable.
- Placeholder SDK facade methods that return success-shaped empty values.
- Duplicate "workflow" and "pipeline" vocabulary in user-facing copy unless both are explicitly defined.

Merge:
- Route capability registry and runtime `/capabilities` into one product truth model.
- Health, operations, jobs, alerts, and capability state into one operator status model.
- Governance policy, PII, redaction, purge, and audit into one Trust workflow.

Automate:
- Collection PII classification and retention recommendations.
- Query/schema context construction for agents.
- Pipeline DAG validation and safe default schedules.
- Alert grouping and suggested remediation, with clear rule/model provenance.

Infer:
- Tenant from auth principal in production, with header override only where explicitly permitted.
- Search/sort fields from collection schema.
- Governance defaults from collection type/schema/data sensitivity.

Hide by default:
- Unsupported plugin lifecycle actions.
- Advanced storage-tier details unless user opens fabric/ops context.
- Raw dependency names unless operator/admin role asks for diagnostics.

Prefetch/prefill:
- Collection create should prefill schema type, governance class, retention, PII candidates.
- Pipeline builder should prefill source/target schemas and validation warnings.
- Trust Center should precompute unclassified collections and overdue actions.

Move to advanced/admin:
- Policy engine internals, storage plugin details, trace exporter configuration, sampling rates, and provider wiring.

Contain technical leakage:
- Replace 501/503 runtime surprises with capability-gated UI and contextual degraded states.
- Replace manual route-specific error handling with shared boundary components using common status semantics.

## 8. Correctness Review Register

- Misleading UI state: collection and pipeline filter/sort controls imply backend behavior that does not exist.
- Misleading readiness: `/ready` can return READY when database and event store are NOT_CONFIGURED.
- Incorrect total counts: entity, pipeline, and alert lists return page length as count.
- Incorrect temporal state: history reconstruction starts from current state and lacks event data patches.
- Incorrect compliance status: unclassified collections are not discoverable from summary.
- Incorrect AI trust signal: heuristic confidence values appear AI-like.
- Incorrect SDK behavior: remote client returns null/empty/zero success values.
- Incorrect settings behavior: UI clients call routes backend does not expose.
- Incorrect workflow closure: execution list is plugin dependent and not paginated.
- Incorrect security completeness: policy/audit are optional even on sensitive/critical paths unless configured.
- Incorrect search behavior: basic list search param is ignored; full-text search requires optional OpenSearch.
- Incorrect event replay promise: events are not complete enough to serve as source of truth.

## 9. Consistency Review Register

- Terminology drift: workflows vs pipelines; brain/AI/agents/context/fabric.
- State drift: route lifecycle active/boundary/preview vs backend live/503/501.
- Component drift: boundary handling exists per service rather than one shared runtime capability pattern.
- Workflow drift: collection, pipeline, alert, and settings lists each use different pagination/count semantics.
- API drift: UI settings and AI endpoints do not match launcher routes.
- Backend response drift: some handlers return envelopes, some raw JSON maps, some 501/503 plain errors.
- Validation drift: UI schemas allow params backend ignores.
- Permission drift: route registry roles, security filter auth, and backend handler annotations are not one shared policy.
- Messaging drift: product docs use enterprise-ready language while implementation has in-memory and optional behavior.
- Audit/history drift: governance uses audit summaries when provider exists; CRUD events are not full audit records.
- AI pattern drift: AI assist, AI operations, alerts suggestions, brain explain, and quality summary are separate models.
- Data semantic drift: collection registry is a generic `dc_collections` entity, not a first-class domain resource.

## 10. API / Backend / Data Review

Contract quality: Broad but not canonical enough. The product needs one versioned OpenAPI contract that is generated into UI/SDK clients. Every route should have consistent envelopes, errors, pagination, idempotency, auth, tenant, request id, trace id, and capability status.

Workflow support quality: Pipeline registry is a good start, but workflow execution is not complete without a first-party engine or strong plugin gating. The target job model should include lifecycle, queue, retry, cancellation, logs, artifacts, audit, and notification hooks.

Business logic soundness: Entity CRUD is adequate for basic storage but insufficient for context-native data fabric. Governance has real purge/redact motion but summary/classification logic is too shallow. Settings and remote SDK are not production implementations.

Data model alignment: Generic entity storage keeps architecture flexible but leaks into product semantics. Collections, workflows, alerts, policies, API keys, capabilities, and executions need explicit schemas and lifecycle state machines, even if stored in the entity plane.

State machine quality: Status concepts are fragmented. Define canonical states for capability, entity lifecycle, workflow execution, alert/incident, governance action, AI action, and dependency health.

Async/event handling quality: Events exist, but mutation event payloads are not complete enough for replay, audit, lineage, or agent grounding. Batch operations need idempotency and partial failure semantics. Workflow jobs need durable queue semantics.

Integration reliability: Optional adapters should be first-class dependencies with health probes, degraded modes, and capability state. 501/503 should be rare internal outcomes, not normal user experience.

Bottom line: backend/API/data layers do not yet fully support a simple, correct, complete product. The next architectural step is contract and state model consolidation, not more surfaces.

## 11. Comprehensive AI/ML Embedding Plan

| Opportunity | Function | User value | Mode | Confidence/governance | Fallback | Priority |
| --- | --- | --- | --- | --- | --- | --- |
| Collection schema assistant | infer fields, types, PII, retention | faster setup, safer defaults | assist | show field-level confidence, require approval for PII/retention | manual schema | P0 |
| Query assistant | translate intent to safe query, explain result | lower query burden | assist | validate against schema/policy, show generated query | SQL/manual filters | P1 |
| Pipeline draft and validation | build DAG from source/goal | faster workflow creation | assist | review before save/execute | templates/manual | P1 |
| Alert correlation | group root causes across dependencies/jobs | faster diagnosis | assist/auto grouping | model/rule provenance, confidence band | deterministic grouping | P1 |
| Governance classifier | detect PII, retention class, legal hold risk | safer compliance | assist with required approval | high-risk fields require admin | manual classification | P0 |
| Redaction/purge recommender | propose candidates and dry-run impact | reduce compliance labor | assist | destructive actions require confirmation token | manual query | P0 |
| Context Layer API | return entity schema, lineage, events, freshness, policy | agent grounding | embedded | policy-filtered context with trace | plain entity API | P1 |
| Anomaly detection | detect stream/data quality/runtime anomalies | proactive operations | assist/monitor | alert provenance and false-positive feedback | thresholds | P2 |
| Cost-aware tiering | recommend or apply storage movement | cost reduction | assist then optional auto | approval for migration policies | static retention | P2 |
| AI action audit | record suggestion, model, prompt/input hash, reviewer, outcome | trust and compliance | embedded | immutable audit | disable AI apply | P0 |

Implementation rules:
- AI should use one action/suggestion contract across all surfaces.
- Every applied AI action needs provenance, confidence band, actor/reviewer, reason, rollback/compensation where possible, and audit event.
- Confidence cannot be decorative. If output is heuristic, say rule-based.
- AI should reduce steps inside existing workflows, not create a separate AI dashboard users must monitor.

## 12. Trust / Privacy / Security / Observability Review

User-facing visibility:
- Show capability state and degraded reasons contextually.
- Show trace/request id on every failed operation.
- Show data freshness, source, and partial result warnings.

Operational transparency:
- Split live/ready/health/capability.
- Required dependencies must be explicit by deployment profile.
- Surface exporter, audit, policy, search, workflow engine, and AI provider states.

Auditability:
- Record API key lifecycle, policy checks, purge/redact, workflow execution, AI suggestion/apply/override, alert action, and admin settings changes.
- Store immutable audit events with tenant, principal, request id, trace id, object id, action, result, and reason.

Permission clarity:
- Centralize roles/scopes in one policy model consumed by UI route guards and backend security.
- Use tenant-bound API keys with scopes, expiry, rotation, and revoke.

Sensitive actions:
- Purge, redact, key revoke, policy change, and AI apply need guarded action patterns with explicit consequences and audit.

Privacy controls:
- Build PII registry, retention policies, legal holds, redaction verification, and evidence export.
- Avoid sending sensitive samples to AI providers unless approved by policy and logged.

Diagnosability:
- Add job IDs, trace IDs, structured errors, retry visibility, dead-letter queues, and support bundle export.

## 13. Design System / Reuse / Abstraction Review

Frontend reuse opportunities:
- Shared `CapabilityBoundary` driven by runtime capability snapshots.
- Shared `PaginatedResourceTable` with server-supported filter/search/sort metadata.
- Shared `GuardedAction` for destructive/sensitive actions.
- Shared `AsyncOperationStatus` for jobs, purge/redact, AI actions, workflow runs.
- Shared `AuditTrailPanel` for entity/pipeline/governance/settings/action history.

API standardization opportunities:
- One envelope: `data`, `error`, `tenantId`, `requestId`, `traceId`, `capability`, `timestamp`.
- One pagination model: cursor or offset, total where available, applied filters, sort, hasMore.
- One error taxonomy: validation, auth, permission, capability_unavailable, dependency_down, conflict, not_found, rate_limited.
- One capability schema: status, mode, dependency, reason, lastProbeAt, requiredForProfile.

Backend abstraction opportunities:
- QuerySpec must carry filter/search/sort/offset/limit/fields and be implemented per store.
- Domain state machines for workflow execution, governance action, alert/incident, AI action, and capability.
- Event envelope standard with mutation payload, before/after/version, actor, reason, and correlation.

State/status standardization:
- Capability: live, degraded, unavailable, preview, boundary.
- Dependency: up, degraded, down, not_configured, unknown.
- Workflow: draft, valid, scheduled, queued, running, retrying, succeeded, failed, canceling, canceled.
- Governance action: draft, dry_run, pending_approval, executing, completed, blocked, failed, verified.
- AI action: suggested, reviewed, applied, rejected, rolled_back, expired.

## 14. Prioritized Remediation Roadmap

Immediate, P0:
- Fix readiness truthfulness. Effort M, impact Critical, owner platform/backend. Depends on deployment profile dependency model.
- Fix entity/collection query contract for offset/search/filter/sort/total. Effort L, impact Critical, owner backend/frontend/API.
- Hide or capability-gate settings and AI operations endpoints that are not implemented. Effort S/M, impact High, owner frontend/product/backend.
- Fix UI type-check to green. Effort M/L, impact High, owner frontend/platform UI.
- Mark placeholder SDK unsupported or implement real generated transport. Effort M/L, impact High, owner platform/API.
- Fix compliance summary to compare collections vs policies and expose unclassified collections. Effort M, impact Critical, owner governance/backend.
- Require policy/audit providers in production or expose degraded/fail-closed behavior. Effort M, impact Critical, owner security/platform.

Short-term, P1:
- Generate UI/SDK clients from canonical OpenAPI and add contract tests. Effort L, impact High, owner API/frontend/platform.
- Implement persistent settings/API key lifecycle with audit. Effort L, impact High, owner security/backend/frontend.
- Implement full CDC events/snapshots and correct temporal query. Effort L, impact Critical, owner data/backend.
- Make runtime capability registry drive nav/search/page CTAs. Effort M, impact High, owner frontend/backend/product.
- Add alert action history, totals, reason fields, and incident lifecycle. Effort M, impact Medium, owner ops/backend/frontend.
- Establish one AI action/suggestion contract with provenance. Effort M/L, impact High, owner ML/backend/frontend/security.

Medium-term, P2:
- Ship first-party workflow execution engine or certified bundled execution plugin. Effort XL, impact Critical, owner platform/backend/frontend.
- Build governance inventory: PII registry, legal holds, evidence exports, redaction verification. Effort L/XL, impact High, owner governance/security/backend.
- Build query broker for federated search/analytics with freshness and partial result semantics. Effort XL, impact High, owner data/backend/frontend.
- Add background job framework with retries, DLQ, progress, cancellation, audit, and notifications. Effort XL, impact High, owner platform/ops.
- Raise coverage gates for core modules to 60%+ after adding tests. Effort L, impact Medium, owner QA/platform.

Long-term, P3:
- Context Layer API for agents with policy-filtered schema/lineage/events/freshness. Effort XL, impact Strategic, owner ML/data/platform.
- Automated context construction from dbt/LookML/schema/query history/agent feedback. Effort XL, impact Strategic, owner ML/data.
- Real-time feature serving from event streams. Effort XL, impact Strategic, owner ML/data/platform.
- Cost-aware tiering recommendations and approved migrations. Effort L/XL, impact Medium/High, owner data/platform/frontend.
- Sovereign/air-gapped mode with no external AI calls by default. Effort XL, impact Strategic, owner security/platform.

## 15. Final Ideal Product Experience Vision

After remediation, Data Cloud should feel like one calm operational fabric. The user lands in a hub that shows only what is live, what needs attention, and what the system can do next. Creating a collection automatically suggests schema, PII, retention, freshness, and lineage defaults. Querying data feels consistent across entity, search, event, and analytics sources because every result says what filters were applied, how fresh it is, whether any source is partial, and what trace explains it.

Pipelines feel like real jobs, not saved diagrams. The user can draft, validate, run, watch, cancel, retry, inspect logs, and audit outcomes from one place. Trust Center is operational: it knows which collections are unclassified, what PII exists, what holds block deletion, what redaction changed, and what evidence proves it.

AI mostly disappears into useful defaults: it pre-fills, prioritizes, summarizes, correlates, recommends, and monitors. It asks for review only when risk or ambiguity requires human judgment. Every AI-driven suggestion has confidence, provenance, policy context, and audit.

Security and observability are pervasive but not noisy. The product refuses unsafe production modes, reports readiness truthfully, includes trace IDs in every failure, and gives operators a direct path from symptom to dependency, job, event, audit record, and remediation. Consistency is maintained by generated contracts, shared state machines, runtime capability truth, and shared UI patterns.

## 16. Executive Summary Lists

### Top 10 Critical Issues

1. `/ready` can report READY when required dependencies are NOT_CONFIGURED.
2. Entity query/search/filter/sort/pagination semantics are not implemented end to end.
3. Temporal entity history cannot reconstruct truthful historical state.
4. Settings/API key UI contract is not backed by launcher routes or persistence.
5. Compliance summary can hide unclassified collections.
6. Policy and audit services are nullable on critical/sensitive routes.
7. Remote HTTP client returns placeholder success-shaped values.
8. Workflow execution is plugin-dependent and not a complete default product flow.
9. UI type-check currently fails.
10. AI operations contract is not implemented by backend routes.

### Top 10 Completeness Gaps

1. Persistent settings and API key lifecycle.
2. Canonical query contract.
3. Durable workflow execution engine.
4. Full CDC/event replay support.
5. Governance collection inventory and evidence.
6. Runtime capability-driven UX.
7. AI action/provenance/governance substrate.
8. Alert/incident action history.
9. Generated SDKs/clients.
10. Background job retries/DLQ/progress.

### Top 10 Simplification Opportunities

1. Gate every page/tab/action by runtime capability.
2. Collapse health/alerts/jobs/capabilities into one operations model.
3. Hide unsupported AI/fabric/settings/plugin controls.
4. Unify workflows/pipelines terminology.
5. Automate PII/retention defaults.
6. Replace 501/503 user surprises with preflight capability states.
7. Use one paginated table pattern.
8. Use one guarded sensitive action pattern.
9. Use one audit trail component.
10. Generate clients instead of hand-coded endpoint adapters.

### Top 10 Correctness Issues

1. Ready status optimism.
2. Ignored list filters.
3. Incorrect counts.
4. Broken temporal reconstruction.
5. Compliance false positives.
6. Placeholder SDK success.
7. Settings route mismatch.
8. AI endpoint mismatch.
9. Policy/audit optionality.
10. Event payloads too thin for replay.

### Top 10 Consistency Issues

1. Route lifecycle vs backend capability.
2. UI settings contract vs backend settings routes.
3. AI operations client vs AI assist routes.
4. Workflow vs pipeline vocabulary.
5. Pagination semantics across lists.
6. Error/envelope shapes across handlers.
7. Governance docs vs nullable enforcement.
8. Event sourcing promise vs CRUD event payloads.
9. Boundary/preview/active state across nav/search/pages.
10. SDK/OpenAPI/client generation strategy vs hand-coded clients.

### Top 10 API/Backend/Data Issues

1. QuerySpec lacks full semantics.
2. Entity handler ignores offset/filter/sort/search.
3. Pipeline handler ignores status/search/sort/page.
4. Health readiness ignores NOT_CONFIGURED.
5. Settings persistence missing.
6. Point-in-time replay invalid.
7. Compliance summary derived from policies only.
8. Workflow execution plugin dependency not productized.
9. SDK HTTP transport not implemented.
10. Batch mutation transactional/idempotency semantics missing.

### Top 10 AI/ML Opportunities

1. Schema/PII/retention assistant.
2. AI action contract with audit/provenance.
3. Query intent assistant.
4. Pipeline draft/validation assistant.
5. Alert correlation and next-best-action.
6. Governance risk detection.
7. Context Layer API for agents.
8. Anomaly detection on entity/event streams.
9. Cost-aware tiering recommendations.
10. Feedback loop for accepted/rejected suggestions.

### Top 10 Trust/Privacy/Security/O11y Improvements

1. Dependency-truthful readiness.
2. Production fail-closed policy/audit.
3. Tenant-bound scoped API keys.
4. Immutable audit for sensitive actions.
5. Legal hold enforcement.
6. PII registry and verification.
7. Trace IDs in all failures and audit events.
8. Capability/dependency status in UI.
9. Support bundle and job/trace correlation.
10. Safe AI provider/privacy policy controls.

## Verification Notes

- `pnpm --filter @data-cloud/ui docs:routes:check` passed after running outside the sandbox; the first sandboxed attempt failed with `listen EPERM` on the local `tsx` IPC pipe.
- `pnpm --filter @data-cloud/ui type-check` failed. Major groups: shared `platform/typescript/canvas` React Flow typing, Data Cloud UI tests/types (`useSelection`, `RBACGuard` props), missing `AiQualitySummary` export, missing `@ghatana/nlp-ui` and `@ghatana/voice-ui`, and `LabeledInput` prop typing.
- `./gradlew -p . :products:data-cloud:launcher:test --tests com.ghatana.datacloud.launcher.http.handlers.EntityCrudHandlerTenantEnforcementTest --no-daemon` passed.
- `./gradlew -p . :products:data-cloud:platform-launcher:test --tests com.ghatana.datacloud.DataCloudFactoryRealProviderTest --no-daemon` passed.
- Gradle reported a configuration-cache compatibility warning for the Kotlin JVM plugin and deprecated Gradle features in both targeted runs.

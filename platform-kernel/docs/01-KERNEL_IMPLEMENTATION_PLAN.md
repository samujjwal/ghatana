## 1. Executive summary

**Overall production-readiness score: 2.6 / 5.0**

## 1.1 Implementation Tracking (May 29, 2026)

Status keys:
- COMPLETE (verified): Code changed and focused validation passed.
- PARTIAL (verified): Improvement implemented and validated, but broader migration remains.
- OPEN: Not yet implemented in this execution.

| Task | Status | What was implemented | Verification evidence |
| --- | --- | --- | --- |
| Group 1: Fix corrupt legacy route mappings generation | COMPLETE (verified) | Fixed YAML parsing in route metadata generator to correctly handle `:param` segments and quoted values; added deterministic dedupe when generating legacy mappings; regenerated `LegacyRouteMappings.generated.ts`. | `node products/data-cloud/scripts/generate-route-security-metadata.mjs` and `node products/data-cloud/scripts/generate-route-security-metadata.mjs --check` both pass. |
| Group 1: Canonical Action Plane namespace in AEP server | PARTIAL (verified) | Expanded canonical aliases under `/api/v1/action/*` across additional Action Plane route families (deployments, reports, HITL, NLQ parse, compliance, auth, audit, consent, governance) while preserving existing root routes for compatibility migration. | `./gradlew :products:data-cloud:planes:action:server:compileJava` and `./gradlew :products:data-cloud:planes:action:server:test --tests "com.ghatana.aep.server.http.AepHttpServerBuilderTest"` pass. |
| Group 1: Replace capabilities-shaped Runtime Truth response in AEP server | COMPLETE (verified) | Replaced `capabilities` map payload in AEP `/api/v1/surfaces` handler with typed `surfaces` records in a `data/meta` envelope. | `./gradlew :products:data-cloud:planes:action:server:compileJava` passes. |
| Group 2: Forbid tenant override from request body | COMPLETE (verified) | Added tenant binding guard in `AgentController` to reject request-body tenant overrides when authenticated/request context is present. | `./gradlew :products:data-cloud:planes:action:server:test --tests "com.ghatana.aep.server.http.controllers.AgentControllerTest"` passes. |
| Group 2: Enforce approved/registered agent before execution | COMPLETE (verified) | `AgentController.handleExecuteAgent` now requires Data Cloud-backed registry, checks agent existence, executability, and approval state before invoking engine. | `./gradlew :products:data-cloud:planes:action:server:test --tests "com.ghatana.aep.server.http.controllers.AgentControllerTest"` passes. |
| Group 4: Pipeline execution parsing/error handling hardening | COMPLETE (verified) | `PipelineExecutionController` now parses body asynchronously, returns explicit 400 for invalid JSON/inputVariables shape, and no longer silently swallows parse failures. | `./gradlew :products:data-cloud:delivery:api:compileJava` passes. |
| Group 4: Remove implicit `"system"` user fallback | COMPLETE (verified) | Pipeline execution now requires `X-User-ID`; missing user returns auth error instead of silently using `system`. | `./gradlew :products:data-cloud:delivery:api:compileJava` passes. |
| Group 4: Pipeline route contract consolidation | PARTIAL (verified) | `PipelineExecutionController` now accepts canonical `/api/v1/action/pipelines/...` paths in addition to legacy path shape. Full contract/UI unification remains open. | `./gradlew :products:data-cloud:delivery:api:compileJava` passes. |
| Group 3: Metrics registry fail-closed in production profile | COMPLETE (verified) | `AepHttpServer.handleMetrics` now returns `503` when Prometheus registry is missing in production profile, while preserving non-production stub metrics for local/test. | `./gradlew :products:data-cloud:planes:action:server:compileJava` passes. |
| Group 4: Pipeline controller regression test coverage | COMPLETE (verified) | Added focused tests for canonical action route acceptance, missing `X-User-ID` rejection, and invalid `inputVariables` rejection. | `./gradlew :products:data-cloud:delivery:api:test --tests "com.ghatana.datacloud.api.controller.PipelineExecutionControllerTest"` passes. |
| Group 2: Route-level pipeline write authorization in Action Plane | PARTIAL (verified) | Added JWT-backed pipeline write guard in `PipelineController` for `POST/PUT/DELETE`; reads remain accessible while mutating operations now require pipeline management privileges. | `./gradlew :products:data-cloud:planes:action:server:test --tests "com.ghatana.aep.server.http.controllers.PipelineControllerTest"` passes. |
| Group 5: Workflows UI i18n hardening slice | PARTIAL (verified) | Expanded i18n coverage in `WorkflowsPage` to include outcome-first banner, stats/status labels, workflow metadata labels, pagination copy, trust-signal labels, execution status labels, and advanced-editor/action labels with translation fallbacks for key-mode test environments; updated focused page tests to assert both translated and i18n-key fallback rendering. | `pnpm --dir products/data-cloud/delivery/ui exec vitest run src/__tests__/pages/WorkflowsPage.test.tsx` passes. |
| Group 3: Durable runtime fail-closed expansion | PARTIAL (verified) | Added explicit production fail-closed guard for in-memory session fallback (`AEP_ALLOW_IN_MEMORY_SESSION`) in addition to existing consent/governance/idempotency guards; deep dependency reporting now includes `session-store`. | `./gradlew :products:data-cloud:planes:action:server:test --tests "com.ghatana.aep.server.http.AepHttpServerBuilderTest"` passes. |
| Group 7: Media modality API baseline in delivery API | PARTIAL (verified) | Completed launcher runtime wiring for media artifact endpoints by adding `withMediaArtifactRoutes(...)` in router builder and instantiating/registering `MediaArtifactController` in `DataCloudHttpServer`; added RouteSecurityRegistry metadata entries + checksum sync for media endpoints so runtime/registry parity stays fail-closed; hardened create flow with consent-aware validation for audio/video artifacts (`consentStatus` required) and metadata enrichment for consent/retention attributes. | `./gradlew :products:data-cloud:delivery:launcher:compileJava`, `./gradlew :products:data-cloud:delivery:api:test --tests "com.ghatana.datacloud.api.controller.MediaArtifactControllerTest"`, and `node products/data-cloud/scripts/generate-route-manifest.mjs --check` pass. |
| Group 8: UI dependency governance checks | PARTIAL (verified) | Added explicit dependency-governance scripts in Data Cloud UI package for boundary, circular, and cross-workspace dependency checks, plus aggregate `check:deps:governance`; revalidated after latest UI changes with zero violations. | `pnpm --dir products/data-cloud/delivery/ui run check:deps:governance` passes (boundaries/circular/cross-workspace all green). |
| Group 6 (adaptive lifecycle) | PARTIAL (verified) | Added adaptive lifecycle endpoints for both patterns and agents: `PatternController` now supports transition actions and feedback provenance (`/patterns/:patternId/lifecycle/:action`, `/patterns/:patternId/feedback`), and `AgentController` now supports lifecycle transitions and review provenance (`/agents/:agentId/lifecycle/:action`, `/agents/:agentId/review`) with transition guardrails, simulation/replay handling, and canonical + `/api/v1/action/*` route wiring in `AepHttpServer`. | `./gradlew :products:data-cloud:planes:action:server:test --tests "com.ghatana.aep.server.http.controllers.PatternControllerTest"`, `./gradlew :products:data-cloud:planes:action:server:test --tests "com.ghatana.aep.server.http.controllers.AgentControllerTest"`, and `./gradlew :products:data-cloud:planes:action:server:test --tests "com.ghatana.aep.server.http.AepHttpServerBuilderTest"` pass. |


Data Cloud at `fb0a50e69ef9bf3554105221d582421c887c543a` is **not production-ready yet**, but it has moved in the right direction architecturally. The strongest improvement is that Data Cloud is now explicitly defined as **one governed operational data fabric organized by planes**, with Action Plane/AEP integrated under `products/data-cloud/planes/action` rather than treated as a separate product surface. The README, canonical docs, and plane architecture consistently describe Data Cloud as the customer-facing product and Action Plane as the governed automation runtime for pipelines, patterns, agents, reviews, runs, and learning.  

The product is still blocked by **contract/runtime drift, route namespace drift, partial authorization, inconsistent durability defaults, UI/i18n gaps, and incomplete audio-video product integration**. The most important next implementation pass should not be release evidence; it should be a **contract + route + authorization + durable-runtime hardening pass** that makes the backend truth, UI truth, and product journeys line up.

Top blockers:

1. **Runtime Truth is conceptually canonical, but implementation still leaks “capabilities” language and mixed contracts.** Data Cloud docs say `/api/v1/surfaces` is canonical and `/api/v1/capabilities` was removed, but `AepHttpServer.handleCapabilityManifest` still returns a `capabilities` map and hardcodes some features as `true`.  
2. **Route namespace is inconsistent.** `DataCloudRouterBuilder` says canonical Action Plane routes belong under `/api/v1/action/*`, but `AepHttpServer` still registers root routes such as `/api/v1/pipelines`, `/api/v1/agents`, `/api/v1/patterns`, `/api/v1/runs`, and `/api/v1/learning/*`.  
3. **Generated legacy route mappings look corrupt and duplicated.** The generated file contains literal quote characters in route strings and repeated `/api/v1/executions/` entries, which undermines deterministic route compatibility.  
4. **Authentication exists, but route-level authorization is not consistently enforced.** `AepAuthFilter` validates JWTs and extracts roles/permissions, but most controllers still need explicit permission checks and tenant binding.  
5. **Agent execution can bypass registry and approval semantics.** `AgentController.handleExecuteAgent` builds an `agent.invocation` event and calls `engine.process` without first proving the agent exists, is approved, belongs to the tenant, or is executable under current policy. 
6. **Tenant identity can be taken from request body in agent registration/execution.** That is unsafe for production multi-tenancy unless overridden by authenticated principal context.  
7. **Durable runtime is still optional in several places.** The code improves fail-closed behavior for explicit production run history, but many services still fall back to in-memory stores when Data Cloud, Redis, or injected governance services are absent.  
8. **Pipeline APIs are fragmented.** There are Action Plane pipeline routes under `/api/v1/action/pipelines`, root AEP routes under `/api/v1/pipelines`, and a separate delivery API `PipelineExecutionController` using root `/api/v1/pipelines/{workflowId}/execute`.  
9. **UI is broad and improving, but not yet 0 cognitive load or fully i18n-safe.** `WorkflowsPage` uses `useTranslation`, but still contains many raw user-visible strings, raw `window.confirm`, client-side pagination over partially loaded data, and many color/status classes outside common components.   
10. **Audio-video exists as a separate product infrastructure summary, but is not yet integrated as a first-class Data Cloud modality.** The available audio-video summary focuses on persistence, security, cache, and messaging for audio files/transcriptions, but I did not find evidence in the inspected Data Cloud routes of a full asset ingestion, metadata, processing, consent, retention, catalog, AEP/agent workflow, and UI journey.  

---

## 2. Scope inspected

I inspected the Data Cloud canonical docs, plane architecture, Action Plane inventory, module classification script, UI architecture/package/generated OpenAPI types, route configuration, routing builders, Action Plane server, auth filter, agent controller, pipeline execution controller, Workflows UI, legacy route mapping, and audio-video implementation summary.

Key inspected areas:

| Area                          | Evidence                                                                                                          |
| ----------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| Data Cloud product definition | README defines Data Cloud as governed operational data fabric and Action Plane as integrated former AEP runtime.  |
| Canonical docs                | `docs/README.md` is canonical; old `CANONICAL_DOCS_INDEX.md` is deprecated.                                       |
| Plane architecture            | Canonical plane model and dependency rules.                                                                       |
| Action Plane/AEP inventory    | Co-located AEP semantic modules and boundary enforcement.                                                         |
| Active module source          | `scripts/list-data-cloud-active-modules.mjs` classifies Data Cloud and Action Plane modules.                      |
| UI architecture               | App/component library split, dependency rules, and test strategy.                                                 |
| Generated API types           | UI now has generated OpenAPI TypeScript contracts.                                                                |
| Backend routing               | `DataCloudRouterBuilder` and `AepHttpServer` route surfaces.                                                      |
| Auth/security                 | JWT auth filter and role/permission helper.                                                                       |
| Agents                        | Agent registration, list, execution, memory.                                                                      |
| Audio-video                   | Product summary for persistence/security/cache/messaging.                                                         |

I did **not** execute build/test/release commands in this pass, per your instruction to avoid release-readiness execution planning for now.

---

## 3. Readiness scorecard

| Dimension                      |   Score | Rationale                                                                                                                                        |
| ------------------------------ | ------: | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| Product coherence              |     3.0 | Plane architecture and docs are coherent, but runtime/API/UI still carry older route/capability/AEP naming.                                      |
| Feature completeness           |     2.4 | Many surfaces exist, but journeys are fragmented and often shallow.                                                                              |
| E2E workflow completeness      |     2.3 | Entities/events/pipelines/agents exist, but cross-surface journeys are not consistently contract-aligned.                                        |
| Data Cloud core architecture   |     3.2 | Strong plane model, module classification, contracts, storage profiles.                                                                          |
| AEP / Action Plane integration |     3.0 | Co-location decision is clear; route/runtime boundaries still drift.                                                                             |
| Agent architecture             |     2.6 | Agent registry/memory/execution exist, but execution governance is incomplete.                                                                   |
| Audio-video integration        |     1.4 | Separate infrastructure exists; Data Cloud modality integration is not yet first-class.                                                          |
| Shared library quality         |     2.6 | Reuse-first intent is documented, but UI/package notes still flag dependency sprawl and shared-library cleanup.                                  |
| UI/UX simplicity               |     2.4 | IA is broad and improving; raw strings, duplicated compatibility routes, advanced surfaces, and inconsistent components increase cognitive load. |
| Backend/API correctness        |     2.6 | Many routes exist, but route namespaces and controller behavior conflict.                                                                        |
| Plugin/extensibility           |     2.7 | Plugin/connectors/routes exist, but lifecycle, permissions, versioning, and runtime isolation need a single contract.                            |
| Security/authorization         |     2.4 | JWT auth is real; endpoint authorization and tenant enforcement are incomplete.                                                                  |
| Privacy/governance             |     2.7 | Governance routes, PII scan, consent, and compliance exist, but enforcement consistency is not proven.                                           |
| Observability/operations       |     3.1 | Health, metrics, SLO, run ledger, deep dependency checks exist.                                                                                  |
| Reliability/failure handling   |     2.4 | Some fail-closed checks exist; in-memory fallback and swallowed errors remain.                                                                   |
| Performance/scalability        |     2.1 | Server-side pagination/filtering and large workload behavior are not consistently enforced.                                                      |
| i18n readiness                 |     1.8 | `useTranslation` exists, but many raw user-visible strings remain.                                                                               |
| Accessibility readiness        |     2.4 | Route/error states and tests exist, but page-level interaction patterns still need systematic a11y pass.                                         |
| Test quality                   |     2.8 | Contract/UI/E2E scripts exist, but current gaps require journey-level and security-path tests.                                                   |
| Developer experience           |     3.2 | Generated API types, route checks, module classification, and docs are strong.                                                                   |
| Config/feature flags           |     2.8 | Feature gates exist, but legacy route compatibility and runtime truth are not clean enough.                                                      |
| Docs/code alignment            |     2.5 | Docs are much better, but UI architecture notes and implementation still conflict in places.                                                     |
| Deployment readiness           |     2.7 | Durable/sovereign profile documented; implementation still allows many embedded fallbacks.                                                       |
| Maintainability/SRP/DRY        |     2.4 | Router/controller decomposition improved, but large `AepHttpServer` remains a composition hotspot.                                               |
| Overall                        | **2.6** | Strong architecture direction, incomplete production hardening.                                                                                  |

---

## 4. Feature completeness and gap matrix

| Capability                | Current state                                                                                                  | Gap                                                                                          | Severity | Recommended action                                                                         |
| ------------------------- | -------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- | -------: | ------------------------------------------------------------------------------------------ |
| Data entities/collections | Entity CRUD, search, validation, export, anomaly hooks are registered.                                         | Need end-to-end consistency across UI, API contract, auth, pagination, validation, audit.    |     High | Consolidate entity journey under one backend-owned contract and one UI data-table pattern. |
| Events                    | Event append/query routes exist.                                                                               | Event Explorer and Action Plane ingestion need a single event model and trace path.          |     High | Align Event Plane routes, Action Plane ingestion, run ledger, and UI event explorer.       |
| Pipelines                 | Routes exist in both canonical and root namespaces.                                                            | Fragmented API ownership and inconsistent execution controller behavior.                     |       P0 | Canonicalize `/api/v1/action/pipelines/*`, then adapt UI and compatibility redirects.      |
| Runtime Truth             | `/api/v1/surfaces` is canonical in docs and launcher router.                                                   | AEP server still returns a capabilities map with hardcoded booleans.                         |       P0 | Replace capability manifest with typed surface records everywhere.                         |
| Agents                    | Registry, execution, memory endpoints exist.                                                                   | Execution does not require approved registered agent or policy/permission gate.              |       P0 | Enforce agent lifecycle state before execution.                                            |
| AEP patterns/learning     | Pattern, HITL, learning routes exist.                                                                          | Need versioned pattern lifecycle, feedback provenance, replay/simulation, explainability.    |     High | Build one adaptive-pattern lifecycle service and UI workflow.                              |
| Governance/privacy        | Governance, consent, compliance routes exist.                                                                  | Enforcement is not uniformly applied to all mutating routes.                                 |       P0 | Add route permission matrix and middleware-level authorization.                            |
| Audio-video               | Separate audio-video infrastructure summary exists.                                                            | Not integrated as Data Cloud modality with catalog, jobs, retention, AEP/agent workflow, UI. |     High | Add audio-video modality baseline after core contracts stabilize.                          |
| UI navigation             | Outcome-first IA exists: Home, Data, Pipelines, Query, Trust, Insights, Operations, plus role-gated surfaces.  | Too many compatibility routes and advanced surfaces remain visible/complex.                  |   Medium | Keep main nav simple; hide role/context surfaces behind progressive disclosure.            |
| UI i18n/a11y              | i18n dependency exists; route gates and disabled states exist.                                                 | Raw strings and native confirm remain.                                                       |     High | One i18n path, design-system dialogs, common table/action components.                      |

---

## 5. End-to-end journey findings

### Data source / connector journey

Connector routes and UI routes exist, but this is still more of a surface than a complete governed journey. The UI has `/connectors` gated through `RuntimeCapabilityRouteGate`, and backend AI assist has connector mapping/sync-health endpoints.  

**Gap:** The expected production journey should be: create connector → validate credentials → preview schema → map fields → run dry-run ingest → commit → monitor sync → audit changes → expose dataset/catalog entry. The inspected routing does not prove this complete lifecycle.

### Dataset/entity journey

Entity CRUD, query, full-text search, semantic similarity, export, anomaly detection, validation, and data-quality trust-score routes exist. 

**Gap:** This is one of the stronger areas, but it still needs unified UI table behavior, server-side pagination/sort/filter guarantees, permission checks, and audit events for all mutating paths.

### Pipeline journey

The pipeline journey is currently the highest-risk product journey. The launcher router has canonical `/api/v1/action/pipelines/*`, while `AepHttpServer` exposes `/api/v1/pipelines/*`, and `PipelineExecutionController` also uses `/api/v1/pipelines/{workflowId}/execute`.   

**Gap:** Pick one canonical backend contract and make UI, generated types, compatibility redirects, tests, and docs point to it.

### Agent journey

Agent registration and list are Data Cloud backed when `DataCloudClient` exists; otherwise list returns empty/configured false and register returns 503.  Execution, however, only builds an `agent.invocation` event and runs the engine. 

**Gap:** Expected production flow is: discover/register agent → scan manifest → approve/install → bind permissions/tools/memory policy → execute with tenant-bound principal → trace/audit → review outputs. Current execution path skips too many gates.

### Runtime operations journey

Health, deep health, metrics, SLO, run ledger, database/Redis/event-loop/heap checks, and durability metadata are good foundations.  

**Gap:** Stub metrics when Prometheus is absent and in-memory fallback must be clearly limited to local/test. Production should fail closed unless all required durable dependencies are present.

### Audio-video journey

Audio-video infrastructure has persistence, security, cache, messaging, migration, and tests documented.  

**Gap:** It is not yet visible as a coherent Data Cloud modality journey: upload/ingest → metadata extraction → transcription/analysis job → catalog/search → governance/retention/consent → AEP event extraction → agent workflow → UI review.

---

## 6. UI/UX findings

The UI has a better outcome-first route structure than earlier Data Cloud designs. The route config consolidates primary routes around `/`, `/data`, `/pipelines`, `/query`, `/trust`, `/insights`, and `/operations`, with contextual routes such as events, memory, entities, context, fabric, agents, settings, plugins, and connectors.   

However, the UI is **not yet 0 cognitive load**:

* `WorkflowsPage` mixes product labels: “Workflows,” “New Pipeline,” “Workflow actions,” “Run Now,” “View Logs,” and “Pipeline AI hints.” This makes the page feel like both workflow and pipeline concepts are still competing.  
* Raw user-visible text bypasses i18n in many places, even though `useTranslation` is imported.  
* Destructive delete uses `window.confirm`, not a consistent design-system confirmation dialog with policy/audit context. 
* Pagination is client-side over a fetched page size of 50, and total pages are computed from loaded rows rather than backend total, so large datasets will mislead users. 
* Compatibility routes are kept indefinitely, which helps bookmarks but risks route bloat and product confusion unless they are invisible, audited, and redirect-only. 

---

## 7. Architecture and boundary findings

The architectural direction is strong. The canonical plane architecture defines clear planes, navigation surfaces, repository layout, and dependency rules.   

The biggest architectural risks are now implementation-drift risks:

1. **Action Plane co-location is accepted, but runtime naming still says AEP standalone.** `AepHttpServer` describes itself as “HTTP Server for AEP Standalone deployment,” while canonical docs say Action Plane is integrated into Data Cloud.  
2. **Launcher router and AEP server disagree on canonical routes.** Launcher uses `/api/v1/action/*`; AEP server exposes root action-domain routes.  
3. **Shared platform cleanup is still open.** The architecture explicitly says shared modules should remain shared only when used by three or more unrelated products or truly generic, and move Data Cloud/Action semantics back into Data Cloud otherwise. 
4. **UI architecture doc is partly stale.** It says current implementation uses ad hoc response types and needs openapi-typescript, but package scripts and generated contracts now exist.   

---

## 8. Security, privacy, and governance findings

Security has improved, but production authorization is incomplete.

`AepAuthFilter` is a real JWT authentication filter: it fails closed in non-development environments when auth is disabled or `AEP_JWT_SECRET` is absent, validates bearer tokens, attaches payload, and extracts roles, permissions, and tenant ID.   

Remaining issues:

* JWT payload extraction is not enough; controllers need route-specific permission checks.
* Agent registration/execution should never trust `tenantId` from request body when authenticated context exists.  
* UI `RoleProtectedRoute` explicitly states it is only shell disclosure and backend must enforce authorization independently. That is correct, but it means backend route authorization must be comprehensive. 
* Agent security scan is useful but regex-based only; it should be one gate in a stronger manifest validation, permission, tool policy, approval, and runtime isolation model. 

---

## 9. Observability, reliability, and performance findings

Positive foundations:

* Health/deep health/readiness/liveness and metrics routes exist in both Data Cloud and AEP paths.  
* AEP server tracks deep dependency checks for Data Cloud, run ledger, database, Redis, event loop, heap memory, execution history, connectivity, and durability metadata. 
* Runtime durability metadata explains durable/degraded/ephemeral mode and reasons. 

Blocking gaps:

* `/metrics` falls back to JSON stub when Prometheus is missing; that is fine for tests, but production should require a real registry. 
* Several stores fall back to in-memory implementations. Some fail-closed logic exists for explicit production run history, but governance, sessions, consent, policy engine, idempotency, and run state need consistent profile-based fail-closed rules. 
* `PipelineExecutionController.parseInputVariables` swallows parsing failures and returns an empty map, which can hide bad client requests. 
* UI pagination/filtering is partly client-side and will not scale or remain semantically correct for large deployments. 

---

## 10. Consolidated task plan grouped to minimize verification passes

### Group 1 — Runtime Truth, route namespace, and generated route metadata

**Goal:** Make `/api/v1/surfaces` the only backend-owned surface truth and eliminate route ambiguity.

**Change:**

* Replace `AepHttpServer.handleCapabilityManifest` with the same typed surface contract used by `SurfaceRegistryHandler`.
* Remove “capabilities” response shape from Action Plane server output.
* Align `AepHttpServer` routes with `/api/v1/action/*` or make root routes compatibility-only behind `LEGACY_ACTION_ROUTES`.
* Fix `LegacyRouteMappings.generated.ts` generation so paths do not contain literal quote characters and duplicate execution/plugin mappings collapse deterministically.
* Update UI `RuntimeCapabilityRouteGate` aliases to use canonical surface IDs only.

**Files/directories:**

* `products/data-cloud/planes/action/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java`
* `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java`
* `products/data-cloud/delivery/ui/src/lib/routing/LegacyRouteMappings.generated.ts`
* `products/data-cloud/delivery/ui/src/api/surfaces.service.ts`
* `products/data-cloud/delivery/ui/src/components/security/RuntimeCapabilityRouteGate.tsx`
* route generation scripts under `products/data-cloud/delivery/ui/scripts/` and root `scripts/`

**Verification together:**

* Route manifest check
* Route truth matrix check
* API type generation/check
* UI route gate tests
* Backend route tests for canonical and compatibility paths

---

### Group 2 — Backend authorization, tenant binding, and policy enforcement

**Goal:** Make backend enforcement match the product security model.

**Change:**

* Introduce a common request principal object extracted from JWT/session.
* Forbid tenant override from request body when authenticated context exists.
* Add route-level permission requirements for entities, events, pipelines, agents, governance, AI, plugins, connectors, and operations.
* Add middleware/helper to enforce tenant/workspace/project scope.
* Update `AgentController` to require approved registered agent before execution.
* Add policy/HITL checks for agent execution, pipeline execution, AI suggestions apply, governance mutations, and destructive actions.

**Files/directories:**

* `products/data-cloud/planes/action/security/src/main/java/com/ghatana/aep/security/AepAuthFilter.java`
* `products/data-cloud/planes/action/server/src/main/java/com/ghatana/aep/server/http/controllers/*`
* `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/*`
* `products/data-cloud/planes/governance/core`
* `products/data-cloud/contracts/openapi/*.yaml`

**Verification together:**

* Permission matrix tests
* Tenant isolation tests
* Agent execution forbidden/allowed tests
* Governance mutation auth tests
* UI role-disclosure tests only as supplement, not as security proof

---

### Group 3 — Durable runtime and profile hardening

**Goal:** Ensure production cannot silently run on ephemeral runtime state.

**Change:**

* Define a single profile resolver for `local`, `test`, `sovereign`, `production`.
* Fail closed in production if Data Cloud client, entity store, event log, run ledger, session store, idempotency store, metrics registry, and audit store are missing.
* Keep in-memory implementations only for explicit local/test.
* Make health/readiness expose exact durability status and blocking dependency.

**Files/directories:**

* `products/data-cloud/delivery/launcher`
* `products/data-cloud/planes/action/server`
* `products/data-cloud/planes/action/security`
* `products/data-cloud/planes/shared-spi`
* `products/data-cloud/docs/README.md`

**Verification together:**

* Profile tests
* Health/readiness tests
* Production-startup fail-closed tests
* Local/test embedded-mode tests

---

### Group 4 — Pipeline/workflow journey consolidation

**Goal:** Make pipeline lifecycle one clean E2E product journey.

**Change:**

* Canonicalize naming: either “Pipeline” as user-facing term or “Workflow” as internal alias, not mixed on the same page.
* Align UI API calls with canonical backend route.
* Fix `PipelineExecutionController` async body parsing and error mapping.
* Do not default missing user to `"system"` for user-triggered operations.
* Add execution status, logs, cancel, retry, rollback, checkpoint, dry-run, and publish lifecycle parity across backend and UI.
* Move filters/search/pagination server-side.

**Files/directories:**

* `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/api/controller/PipelineExecutionController.java`
* `products/data-cloud/planes/action/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java`
* `products/data-cloud/delivery/ui/src/lib/api/workflows.ts`
* `products/data-cloud/delivery/ui/src/pages/WorkflowsPage.tsx`
* `products/data-cloud/delivery/ui/src/pages/WorkflowDesigner.tsx`
* `products/data-cloud/contracts/openapi/action-plane.yaml`

**Verification together:**

* Pipeline API contract tests
* UI workflow tests
* Execution lifecycle integration tests
* Error-path tests

---

### Group 5 — UI zero-cognitive-load, i18n, and common component pass

**Goal:** Make Data Cloud simple, consistent, and production-grade visually.

**Change:**

* Extract all raw user-visible strings into the single i18n mechanism.
* Replace `window.confirm` with design-system modal.
* Standardize cards, tables, filters, actions, empty/loading/error/unauthorized states.
* Reduce default navigation to outcome-first surfaces.
* Hide advanced/role/contextual surfaces behind progressive disclosure.
* Make AI/agent/AEP language outcome-focused: “recommended next action,” “automation review,” “pattern detected,” not raw internal terms.
* Normalize color usage through design tokens.

**Files/directories:**

* `products/data-cloud/delivery/ui/src/pages/*`
* `products/data-cloud/delivery/ui/src/components/*`
* `products/data-cloud/delivery/ui/src/layouts/*`
* `products/data-cloud/delivery/ui/src/i18n/*`
* `products/data-cloud/libs/ui-components/*`

**Verification together:**

* i18n raw-string scan
* Component tests
* Accessibility tests
* Key page visual/smoke tests
* Table/action interaction tests

---

### Group 6 — Agent + AEP adaptive lifecycle hardening

**Goal:** Make AEP/agents powerful but governed and understandable.

**Change:**

* Add pattern lifecycle: draft → validate → simulate/replay → approve → activate → monitor → revise/retire.
* Add pattern versioning and feedback provenance.
* Add agent lifecycle: register → scan → approve/install → configure tools/memory/policies → execute → review → learn.
* Ensure agent memory read/write respects retention, consent, tenant, and policy.
* Add explainability for pattern detection and agent decisions.

**Files/directories:**

* `products/data-cloud/planes/action/engine`
* `products/data-cloud/planes/action/orchestrator`
* `products/data-cloud/planes/action/agent-runtime`
* `products/data-cloud/planes/action/server`
* `products/data-cloud/extensions/agent-registry`
* `products/data-cloud/extensions/agent-catalog`
* `products/data-cloud/delivery/ui/src/pages/AgentPluginManagerPage.tsx`
* `products/data-cloud/delivery/ui/src/pages/EventExplorerPage.tsx`

**Verification together:**

* Pattern lifecycle tests
* Agent lifecycle tests
* HITL approval tests
* Learning feedback tests
* Audit/observability tests

---

### Group 7 — Audio-video as a Data Cloud modality

**Goal:** Promote audio-video from adjacent infrastructure into a first-class Data Cloud modality.

**Change:**

* Define `mediaAsset`, `transcription`, `mediaProcessingJob`, `mediaSegment`, `mediaEvent`, and `mediaConsent` contracts.
* Add ingestion API and UI: upload/register asset, metadata extraction, job creation, transcription, status, review.
* Integrate with Data Cloud catalog/search/context/lineage.
* Emit events into Event Plane and AEP for pattern detection.
* Add agent tools for summarization, classification, clipping, and review with consent/retention policies.
* Add storage abstraction for large files and redaction workflow.

**Files/directories:**

* `products/audio-video/modules/*`
* `products/data-cloud/contracts/openapi/data-cloud.yaml`
* `products/data-cloud/planes/data`
* `products/data-cloud/planes/event`
* `products/data-cloud/planes/governance`
* `products/data-cloud/planes/action`
* `products/data-cloud/delivery/ui/src/pages` or new `features/media`

**Verification together:**

* Media asset API tests
* Processing job tests
* Consent/retention tests
* Catalog/search tests
* UI media journey tests

---

### Group 8 — Shared library and dependency cleanup

**Goal:** Keep shared libraries genuinely shared and Data Cloud semantics inside Data Cloud.

**Change:**

* Audit UI/package workspace dependencies and remove unused ones.
* Move Data Cloud-specific utilities out of generic shared libraries.
* Enforce dependency direction with architecture tests.
* Stabilize public SPI surfaces.
* Add API compatibility tests for shared libraries.

**Files/directories:**

* `products/data-cloud/delivery/ui/package.json`
* `products/data-cloud/libs/*`
* `platform/*`
* `scripts/check-cross-workspace-deps.mjs`
* `scripts/check-circular-deps.mjs`
* `scripts/boundary-rules.json`

**Verification together:**

* Dependency graph check
* Circular dependency check
* Public API tests
* Typecheck/build for affected packages

---

## 11. Priority roadmap

### P0 — production blockers

1. Runtime Truth `/api/v1/surfaces` consolidation.
2. Canonical Action Plane route namespace.
3. Backend authorization and tenant binding.
4. Durable production profile fail-closed behavior.
5. Pipeline execution contract/error handling.
6. Corrupt legacy route mapping generation.

Expected score movement: **2.6 → 3.3**

### P1 — coherent product completeness

1. Pipeline/workflow E2E journey.
2. Entity/data table consistency.
3. Agent lifecycle governance.
4. UI i18n/common component pass.
5. AEP pattern lifecycle and feedback loop.

Expected score movement: **3.3 → 3.9**

### P2 — extensibility and modality hardening

1. Plugin lifecycle/versioning/permission model.
2. Audio-video modality integration.
3. Shared library cleanup.
4. Observability/error-path standardization.
5. Server-side pagination/filter/sort everywhere.

Expected score movement: **3.9 → 4.3**

### P3 — polish and optimization

1. Visual polish.
2. Advanced progressive disclosure.
3. Performance optimization.
4. Richer operator diagnostics.
5. More refined AI/agent explainability.

Expected score movement: **4.3 → 4.6+**

---

## 12. Final recommendation

Data Cloud is **architecturally promising but not production-ready** at this commit.

The highest-return next pass should be:

**Runtime Truth + canonical routes + backend authorization + durable profile hardening + pipeline E2E contract alignment.**

Do **not** spend the next iteration generating release evidence, expanding release dashboards, adding more AI endpoints, or polishing secondary UI surfaces. Those will have low return until the backend truth, route truth, tenant/authz model, and primary journeys are deterministic.

Minimum path to a coherent production-grade Data Cloud suite:

1. Make `/api/v1/surfaces` the single runtime truth contract.
2. Make `/api/v1/action/*` the single Action Plane namespace, with legacy routes redirecting or feature-flagged only.
3. Enforce authenticated principal, tenant scope, permissions, policy, and audit on every mutating backend route.
4. Remove unsafe production fallback to in-memory services.
5. Make pipeline, agent, entity, event, and governance journeys contract-backed and UI-backed.
6. Then integrate audio-video as a first-class Data Cloud modality, not a separate infrastructure island.

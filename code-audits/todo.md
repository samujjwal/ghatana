## Production Readiness Verdict

**Production ready: No.**
**Confidence: Medium-high** for architecture/code direction and root blockers, based on the target commit snapshot files I inspected. I did not execute the full Gradle/pnpm test suite or enumerate every repository file because the GitHub file-search index was not selected for this chat; I used the direct GitHub connector against commit `3d0eded86e8dae16bece7997c50e09b32c934b0b`. The commit itself is a YAPPC changelog-only commit, so this audit treats it as the full repository snapshot, not as a diff review. 

**Main verdict:** Data Cloud is moving in the right architectural direction, but it is still **not production-grade** because the implementation remains transitional: critical security/durability/audit/transaction dependencies are still optional in runtime composition, contract truth is duplicated across OpenAPI/runtime/frontend schemas, the shared-library boundary is documented but not fully enforced, and cleanup debt still leaves stale audit/analysis artifacts in the repo.

**Highest-risk area:** production fail-closed behavior across security, tenant isolation, audit, policy, durability, AI, idempotency, and transaction boundaries.

**Direction of improvement:** Yes, significantly improving. The canonical docs now define Data Cloud as one AI-native operational data fabric, organized by planes, with AEP only as the Action Plane runtime. They also define `/api/v1/surfaces` as the Runtime Truth endpoint, central contracts under `products/data-cloud/contracts`, and strict dependency rules that prevent Data/Event/Context/Governance planes from importing Action Plane internals.  

**Shared libraries verdict:** Partially ready, not production-ready. The desired ownership model is clear: shared platform modules should remain shared only when genuinely cross-product, and Data Cloud/Action-specific behavior should move into `products/data-cloud`. However, the codebase still shows transitional shared-service residue and product/shared-boundary migration work.  

---

# Root Architectural Blockers

## P0-1 Production fail-closed posture is documented but not fully enforced by runtime composition

**Why it matters:** Data Cloud’s value depends on trust: tenant isolation, governance, auditability, durable event/data processing, and safe automation. If production-sensitive dependencies silently fall back to noop, in-memory, heuristic, or disabled modes, the product can appear live while violating its own architecture.

**Root cause:** The canonical architecture requires production profiles to fail closed for missing security, policy, audit, durability, runtime dependencies, and AI completion services. But the launcher still wires many trust-critical capabilities as optional builder dependencies.

**Evidence:**

* The architecture says production must fail closed for missing tenant context, authentication, authorization, policy engine, audit writer, durable event store, redaction policy, AEP dependency, runtime probes, and AI completion service. 
* `DataCloudHttpServer` still has many optional dependencies: audit can be null and “silently skipped,” API key resolver null means security filter is not activated, settings/idempotency stores fall back to in-memory, transaction manager null means multi-step writes run without transaction boundaries, metrics default to noop, and trace export can be absent. 
* `AiAssistHandler` correctly supports production fail-closed behavior, but only when `productionMode` is explicitly enabled; its default is false. 
* Tenant fallback is improved: default tenant fallback is limited to local/test/development modes, while strict/non-local modes return null. This is a good partial fix, but it needs to be part of a broader profile gate. 

**Affected surfaces:** all APIs, Runtime Truth, entity writes, event writes, AI assist, exports, governance, audit, reports, pipelines, Action Plane runtime, UI action enablement.

**Correct target pattern:** a central `RuntimeProfileValidator` / `ProductionReadinessGate` that validates runtime profile at startup and prevents non-local deployment when required dependencies are missing.

**Required fix:**

1. Define required dependencies by deployment profile: local, test, sovereign, standalone, enterprise, cloud.
2. Fail startup in non-local profiles if auth, policy, audit, durable event store, durable entity store, durable settings, durable idempotency, transaction manager, metrics, traces, and redaction policies are absent.
3. Allow noop/in-memory/heuristic fallback only under explicit local/test/preview profiles.
4. Publish the same evaluated posture into `/api/v1/surfaces`.

**Required tests:**

* Production profile startup fails without auth.
* Production profile startup fails without audit writer.
* Production profile startup fails without policy engine.
* Production profile startup fails without durable idempotency store.
* Production profile startup fails without transaction manager for mutating routes.
* Production AI routes return 503 when no completion service is configured.

**Cleanup implications:** remove scattered per-handler fallback comments and centralize fallback policy in one runtime profile gate.

---

## P0-2 Mutating workflows are not uniformly atomic, idempotent, policy-checked, and audited

**Why it matters:** Data Cloud’s core promise is trusted operational truth. Entity save, event append, semantic indexing, audit, provenance, and downstream visibility must behave deterministically under retries and partial failures.

**Root cause:** Entity writes have partial hardening, but the pattern is not yet universal. `EntityCrudHandler` explicitly notes that idempotency is implemented for entity save/batch only and that other mutating routes still require idempotency support or explicit non-idempotent documentation. Transaction handling is optional, rollback for event append is best-effort, and semantic indexing is outside the transactional boundary. 

**Affected surfaces:** entities, events, pipelines, workflows, governance operations, analytics automation, AI actions, data lifecycle actions, exports/import-like flows, Action Plane runs.

**Correct target pattern:** one canonical write-command runtime:

```text
request
→ tenant/auth/policy resolution
→ idempotency check
→ validation
→ transaction/outbox
→ entity/event/audit/provenance write
→ index/update async via outbox
→ response with trace/evidence
```

**Required fix:**

1. Introduce a shared `DataCloudCommandExecutor`.
2. Make idempotency required for all retryable mutating routes.
3. Use transactional outbox for event append, audit write, and semantic indexing side effects.
4. Require audit/provenance for sensitive mutations.
5. Move rollback semantics from best-effort comments to deterministic compensation/outbox processing.

**Required tests:**

* Retry same entity write with same idempotency key returns identical result.
* Retry pipeline create/execute does not duplicate runs.
* Entity save failure after event append rolls back or emits compensation.
* Semantic indexing failure does not corrupt entity/event truth.
* Sensitive mutation without audit writer fails in production.

**Cleanup implications:** remove route-specific idempotency implementations once the command executor is in place.

---

## P0-3 Product/shared boundary remains transitional, especially around Action Plane and shared services

**Why it matters:** Data Cloud must avoid becoming a god product while also avoiding fake shared libraries that exist only because Data Cloud and AEP used to be separate. Shared libraries should be reusable infrastructure, not hidden Data Cloud business logic.

**Root cause:** The canonical plane architecture is clear, but the repo still has transitional naming, compatibility concepts, and residual shared-service cleanup. The README says AEP is not a separate customer-facing product and is the Action Plane runtime.  The plane architecture gives explicit rules for what stays in platform versus what moves into Data Cloud.  But `shared-services/README.md` still identifies `feature-store-ingest` as **RESIDUE** whose canonical location is now under `products/data-cloud/planes/intelligence/feature-ingest`. 

**Affected surfaces:** shared services, platform modules, Action Plane runtime, agent core, workflow, messaging, AI integration, data governance, contracts, UI terminology.

**Correct target pattern:**

```text
platform/ = generic cross-product primitives
shared-services/ = real networked services used by multiple products
products/data-cloud/ = Data Cloud product behavior and plane-specific runtime
```

**Required fix:**

1. Audit each shared module against the documented “used by 3+ unrelated products or generic infrastructure” rule.
2. Move Data Cloud/Action-specific behavior into Data Cloud planes.
3. Delete shared-service residue after validating no Gradle/runtime references.
4. Keep only generic agent/workflow/messaging/AI/provider/policy primitives in platform.

**Required tests:**

* Dependency direction test: platform must not import product code.
* Data/Event/Context/Governance planes must not import Action implementation.
* Shared-service residue path is not referenced by Gradle, deployment, docs, or runtime.
* Cross-product reuse scorecard passes.

**Cleanup implications:** remove stale `feature-store-ingest` residue and migrate docs/scripts to canonical plane terminology.

---

## P1-4 Contract truth is duplicated across OpenAPI, runtime handlers, frontend Zod schemas, and service clients

**Why it matters:** Contract drift will break SDKs, UI behavior, Runtime Truth gating, and external product consumption. Data Cloud’s architecture requires product-level contracts to be the source of truth.

**Root cause:** Canonical docs say contracts belong under `products/data-cloud/contracts`, UI should use generated clients/frontend adapters, and runtime routes must match OpenAPI.   But the UI still maintains extensive local Zod schemas described as the frontend/backend “single source of truth,” including capability and surface schemas, collection schemas, pipeline schemas, reports, models, compliance, voice, analytics, and more. 

**Affected surfaces:** UI, generated SDK, API handlers, OpenAPI contracts, Runtime Truth, tests, mocks/MSW, docs.

**Correct target pattern:** OpenAPI/proto/schema contracts generate clients, runtime validators, frontend schemas, mock fixtures, and route inventory checks.

**Required fix:**

1. Generate TypeScript clients and Zod schemas from canonical OpenAPI.
2. Remove hand-maintained frontend contract schemas except thin UI view-model schemas.
3. Add CI drift gate: OpenAPI ↔ runtime route inventory ↔ frontend generated client.
4. Treat MSW/mock handlers as test-only and generated from contracts.

**Required tests:**

* OpenAPI validates.
* Runtime routes match OpenAPI.
* UI imports generated clients, not backend internals or hand-rolled service contracts.
* Mock/test schemas are generated or contract-validated.

**Cleanup implications:** delete or reduce `delivery/ui/src/contracts/schemas.ts` once generated equivalents exist.

---

## P1-5 Runtime Truth exists, but it is not yet a full operational truth authority

**Why it matters:** The UI and SDK should never claim a surface is live unless the backend has actually probed required dependencies and can explain degradation.

**Root cause:** The canonical architecture expects Runtime Truth to expose live/degraded/unavailable state, dependencies, posture, evidence, and probes.  The current handler returns a supplier-provided map under `surfaces`, plus `generatedAt`, and the frontend normalizes booleans/strings/objects into surface states.   This is a good migration step, but it is still too loose: raw values can be normalized into operational states without proving dependency health.

**Affected surfaces:** Home, navigation, Operations, Trust, pipelines, reports, AI, connectors, plugins, SDK feature gates.

**Correct target pattern:** typed `SurfaceRecord` objects generated from a server-side `RuntimeTruthRegistry`:

```text
surfaceId
state
ownerPlane
requiredDependencies
dependencyProbeResults
tenantScope
runtimeProfile
lastCheckedAt
evidence
limitations
actionsAllowed
```

**Required fix:**

1. Replace raw map snapshots with typed surface records.
2. Require dependency probes for every LIVE surface.
3. Make surface state tenant/profile-aware.
4. Expose runtime posture fields from the canonical architecture.
5. Fail CI when undocumented/unregistered surfaces are introduced.

**Required tests:**

* Surface cannot be LIVE without required dependency probes.
* UI disables actions when surface is MISCONFIGURED/UNAVAILABLE.
* Runtime Truth response schema is contract-generated.
* Per-tenant surface state does not leak cross-tenant availability.

**Cleanup implications:** remove capability compatibility layer once all UI code uses `Surface*` concepts only.

---

## P1-6 UI is still service-module driven rather than fully outcome-first and contract-generated

**Why it matters:** Data Cloud’s UI should be low-cognitive-load, outcome-first, role-aware, runtime-truth-gated, and not expose implementation boundaries like AEP.

**Root cause:** The design docs define outcome-first navigation: Home, Data, Events, Query, Pipelines, Trust, Operations; Action Plane surfaces should feel native to Data Cloud.  The UI API index still exports many hand-written service modules and includes implementation wording such as “Events — AEP event fabric explorer.” 

**Affected surfaces:** navigation, Events, Memory, Agents, Pipelines, Runtime Truth, API clients, route gates.

**Correct target pattern:** UI consumes generated Data Cloud clients and a single route/action registry derived from contracts + Runtime Truth.

**Required fix:**

1. Replace per-service API exports with generated clients and frontend adapters.
2. Remove AEP implementation wording from user-facing UI comments, labels, routes, and docs.
3. Bind route/action availability to typed Runtime Truth records.
4. Use shared design system components consistently for shell, cards, tables, inspectors, activity timelines, and review queues.

**Required tests:**

* UI route traversal from Data Cloud home.
* Runtime Truth gate disables unavailable actions.
* No `AEP` wording in customer-facing UI except internal docs/developer-only areas.
* Accessibility coverage for shell, tables, review queues, modals, charts.

**Cleanup implications:** consolidate `delivery/ui/src/api/*` into generated client adapters.

---

## P1-7 AI/automation is promising but not yet uniformly governed, evidenced, and privacy-hardened

**Why it matters:** Data Cloud positions AI/ML as implicit and pervasive. That only works if every automated suggestion/action is advisory, explainable, policy-aware, auditable, and interruptible.

**Root cause:** `AiAssistHandler` contains strong intent: confidence, fallback metadata, production fail-closed behavior, tenant quota checks, and optional action recording. But production behavior depends on wiring, AI action persistence depends on an optional client, and not all AI paths are proven to pass through a single governance/HITL/provenance policy.  The design requires every AI/agentic action to show data used, confidence/risk, policy decision, review state, rollback/override options, and audit/trace ID. 

**Affected surfaces:** schema inference, analytics suggest/automate, pipeline draft/refine, anomaly explanation, recommendations, Action Plane agents/runs/reviews.

**Correct target pattern:** one `AiActionExecutor` / `AutomationDecisionEnvelope`:

```text
input classification
redaction summary
model/provider
confidence
evidence
policy decision
requiresHumanReview
audit id
trace id
rollback/override options
```

**Required fix:**

1. Centralize AI/automation execution policy.
2. Require redaction and classification metadata before model calls.
3. Require evidence/provenance for every AI response.
4. Route low-confidence/high-risk outputs to HITL.
5. Persist AI actions and decisions in an audit/evidence store in non-local profiles.

**Required tests:**

* Raw PII is not sent to LLM prompts.
* Low-confidence result creates review item.
* Production AI unavailable returns 503.
* AI action response includes confidence/evidence/audit/trace.
* Human override interrupts delegated automation.

**Cleanup implications:** remove route-local heuristic behavior from individual handlers and move it behind the central automation policy.

---

## P1-8 Observability exists as a design goal but remains optional in runtime wiring

**Why it matters:** Data Cloud must be operationally trustworthy. Noop metrics, unpersisted traces, or skipped audit events make production incidents and compliance reviews unverifiable.

**Root cause:** The architecture defines telemetry taxonomy, runtime posture fields, traces, metrics, logs, audit, and surface-state reporting.  But the launcher defaults metrics to noop, allows trace exporter to be null, and allows audit service to be null. 

**Affected surfaces:** all runtime APIs, import/processing jobs, AI assist, Action Plane runs, governance actions, Runtime Truth, incident response.

**Correct target pattern:** observability is optional only in local/test. Non-local profiles require metrics, trace export, structured logs, audit writer, and incident hooks.

**Required fix:**

1. Add observability requirements to runtime profile validation.
2. Emit canonical metrics for every route/action.
3. Persist traces/audit for sensitive operations.
4. Include observability posture in `/api/v1/surfaces`.

**Required tests:**

* Non-local profile fails without metrics/audit/tracing.
* Sensitive mutation emits audit event.
* Request ID/correlation ID appears in response and logs.
* Degraded dependency emits surface-state change.

**Cleanup implications:** remove “silent skip” behavior for audit and noop observability outside local/test.

---

## P1-9 Test and release gates exist, but are fragmented across scripts, docs, and module tests

**Why it matters:** A production readiness bar is only useful if it is one repeatable command/pipeline that gates releases.

**Root cause:** The repo has many good signals: tenant isolation scripts, coverage/flakiness scripts, OpenAPI drift checks, runtime truth tests, security filter tests, architecture tests, route access tests, and production profile tests.       But the audited prompt’s desired release gate is broader than any single verified pipeline I could confirm.

**Affected surfaces:** CI, release readiness, architecture gates, cleanup validation, test coverage, production profile validation.

**Correct target pattern:** one product-level release gate:

```bash
products/data-cloud/scripts/verify-production-readiness.sh
```

It should orchestrate build, lint, typecheck, tests, OpenAPI drift, route inventory, runtime truth, tenant isolation, authz matrix, dependency boundaries, cleanup scan, placeholder scan, and e2e traversal.

**Required tests:** the release gate itself should be tested/smoke-validated and required by CI.

**Cleanup implications:** consolidate overlapping scripts into one orchestrated product release gate with subcommands.

---

## P2-10 Documentation and cleanup debt still risks repeated audits

**Why it matters:** The prompt’s goal is to stop repeated rediscovery of stale code/docs. Data Cloud now has canonical docs, but older root-level analysis/audit artifacts still appear in repository search and can confuse future reviews.

**Root cause:** Canonical docs are listed in the Data Cloud README.  But search still surfaces `dc-aep-analysis.md`, `ghatana-data-cloud-aep-end-to-end-audit.md`, archived audit TODOs, and legacy implementation plans.     Some current design docs also contain stale contradictory wording about no active `products/data-cloud/planes/action` references even though the same docs define Action Plane as living there.  

**Affected surfaces:** docs, search, architecture scripts, generated scorecards, future audit accuracy.

**Correct target pattern:** one canonical docs index plus explicit archive exclusion rules.

**Required fix:**

1. Keep only canonical docs in `products/data-cloud/docs/product`, `architecture`, `api`, `operations`, `security`, `testing`, `i18n`, `accessibility`.
2. Move old analysis/audit docs under dated archive with archive warning headers or delete them.
3. Fix contradictory migration wording in current canonical docs.
4. Ensure truth-check scripts ignore archive paths.

**Required tests:** documentation truth check, no stale active references, no archive docs linked from canonical docs unless explicitly marked historical.

**Cleanup implications:** high.

---

# Migration / Completeness Matrix

| Surface                            |     Boundary |     Contract | Runtime Truth | Tenant/Authz |   Provenance | Privacy/Security | AI/Automation | Observability |        Tests |      Cleanup | Status |
| ---------------------------------- | -----------: | -----------: | ------------: | -----------: | -----------: | ---------------: | ------------: | ------------: | -----------: | -----------: | -----: |
| Product boundary / plane model     |            ✅ |           🟡 |            🟡 |           🟡 |           🟡 |               🟡 |            🟡 |            🟡 |           🟡 |           🟡 |     🟡 |
| Shared library boundary            |           🟡 |           🟡 |             ⚫ |            ⚫ |            ⚫ |               🟡 |            🟡 |            🟡 |           🟡 |           🔴 |     🟡 |
| Data Cloud app shell               |           🟡 |           🟡 |            🟡 |           🟡 |           🟡 |               🟡 |            🟡 |            🟡 |           🟡 |           🟡 |     🟡 |
| Runtime Truth `/api/v1/surfaces`   |            ✅ |           🟡 |            🟡 |           🟡 |           🟡 |               🟡 |             ⚫ |            🟡 |           🟡 |           🟡 |     🟡 |
| Entity CRUD                        |            ✅ |           🟡 |            🟡 |           🟡 |           🟡 |               🟡 |             ⚫ |            🟡 |           🟡 |           🟡 |     🟡 |
| Entity mutation atomicity          |           🟡 |           🟡 |             ⚫ |           🟡 |           🟡 |               🟡 |             ⚫ |            🟡 |           🟡 |           🟡 |     🔴 |
| Event append / event truth         |           🟡 |           🟡 |            🟡 |           🟡 |           🟡 |               🟡 |            🟡 |            🟡 |           🟡 |           🟡 |     🟡 |
| Connectors / source onboarding     |           🟡 |           🟡 |            🟡 | Not verified | Not verified |     Not verified |  Not verified |  Not verified | Not verified |           🟡 |     🟡 |
| Credential / secret handling       | Not verified | Not verified |  Not verified | Not verified | Not verified |     Not verified |             ⚫ |  Not verified | Not verified | Not verified |     🔴 |
| Ingestion / extraction / transform |           🟡 |           🟡 |            🟡 | Not verified |           🟡 |               🟡 |            🟡 |            🟡 | Not verified |           🟡 |     🟡 |
| Canonical data model               |           🟡 |           🟡 |             ⚫ |           🟡 |           🟡 |               🟡 |             ⚫ |            🟡 |           🟡 |           🟡 |     🟡 |
| Search / retrieval / indexing      |           🟡 |           🟡 |            🟡 |           🟡 |           🟡 |               🟡 |            🟡 |            🟡 |           🟡 |           🟡 |     🟡 |
| Reports / analytics                |           🟡 |           🟡 |            🟡 |           🟡 |           🟡 |               🟡 |            🟡 |            🟡 |           🟡 |           🟡 |     🟡 |
| Action Plane / AEP runtime         |           🟡 |           🟡 |            🟡 |           🟡 |           🟡 |               🟡 |            🟡 |            🟡 |           🟡 |           🟡 |     🟡 |
| HITL / reviews / learning          |           🟡 |           🟡 |            🟡 |           🟡 |           🟡 |               🟡 |            🟡 |            🟡 | Not verified |           🟡 |     🟡 |
| AI assist / automation             |            ✅ |           🟡 |            🟡 |           🟡 |           🟡 |               🟡 |            🟡 |            🟡 |           🟡 |           🟡 |     🟡 |
| Frontend contracts                 |           🟡 |           🔴 |            🟡 |           🟡 |           🟡 |               🟡 |            🟡 |            🟡 |           🟡 |           🔴 |     🔴 |
| Generated SDK usage                |           🟡 |           🟡 |            🟡 |            ⚫ |            ⚫ |               🟡 |             ⚫ |             ⚫ | Not verified |           🟡 |     🟡 |
| Observability                      |           🟡 |           🟡 |            🟡 |            ⚫ |           🟡 |               🟡 |            🟡 |            🔴 |           🟡 |           🟡 |     🟡 |
| i18n                               | Not verified | Not verified |             ⚫ |            ⚫ |            ⚫ |                ⚫ |             ⚫ |             ⚫ | Not verified | Not verified |     🔴 |
| Accessibility                      | Not verified | Not verified |             ⚫ |            ⚫ |            ⚫ |                ⚫ |             ⚫ |             ⚫ | Not verified | Not verified |     🔴 |
| Repository cleanup                 |           🟡 |           🟡 |            🟡 |            ⚫ |            ⚫ |                ⚫ |             ⚫ |             ⚫ |           🟡 |           🔴 |     🔴 |

---

# File-Level Gaps

| Root blocker | Path                                                                                                                      | Gap                                                                                                                                                             | Fix                                                                                                    | Tests                                              |
| ------------ | ------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ | -------------------------------------------------- |
| P0-1         | `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudHttpServer.java`        | Too many trust-critical runtime dependencies remain optional or fallback to noop/in-memory/silent skip.                                                         | Add centralized runtime profile validator and fail startup in non-local profiles.                      | Production profile missing dependency tests.       |
| P0-2         | `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/EntityCrudHandler.java` | Entity save/batch idempotency exists, but other mutations are explicitly not covered; transaction manager is optional; event rollback is best-effort.           | Introduce canonical command executor with transaction/outbox/idempotency/audit.                        | Retry, partial failure, compensation, audit tests. |
| P1-5         | `SurfaceRegistryHandler.java`                                                                                             | `/surfaces` returns supplier map, not visibly a typed dependency-probe/evidence-backed registry.                                                                | Return typed `SurfaceRecord` with dependency probe evidence and posture.                               | Surface cannot be LIVE without probe evidence.     |
| P1-4         | `delivery/ui/src/contracts/schemas.ts`                                                                                    | Frontend maintains broad “single source of truth” schemas instead of generated contract-derived schemas.                                                        | Generate Zod/client contracts from OpenAPI.                                                            | OpenAPI ↔ generated schemas drift gate.            |
| P1-5         | `delivery/ui/src/api/surfaces.service.ts`                                                                                 | Deprecated capability compatibility layer remains even after `/capabilities` removal.                                                                           | Remove capability types/hooks after route migration.                                                   | No `Capability*` imports in UI.                    |
| P1-6         | `delivery/ui/src/api/index.ts`                                                                                            | UI API surface still exposes many hand-written services and AEP implementation wording.                                                                         | Consolidate to generated clients + Data Cloud terminology.                                             | UI terminology and generated-client usage checks.  |
| P0-3         | `shared-services/README.md` / `shared-services/feature-store-ingest`                                                      | README marks `feature-store-ingest` as residue and says canonical location is Data Cloud intelligence plane.                                                    | Delete residue after validating no references.                                                         | Gradle, grep, dependency graph checks.             |
| P2-10        | `dc-aep-analysis.md`, `ghatana-data-cloud-aep-end-to-end-audit.md`, `docs/archive/data-cloud-audit-legacy/*`              | Stale/legacy audit and analysis docs still appear in repo search.                                                                                               | Move/delete/archive with warning headers and exclude from truth scans.                                 | Documentation truth check.                         |
| P2-10        | `02_data_cloud_unified_detailed_architecture.md`, `03_data_cloud_unified_high_level_design.md`                            | Current docs contain contradictory stale migration wording around `products/data-cloud/planes/action` despite defining it as canonical Action Plane location.   | Rewrite acceptance/validation rows to mean “no stale pre-merge AEP paths,” not “no action plane path.” | Docs consistency check.                            |

---

# Prioritized Implementation Sequence

## 1. Canonical production profile and fail-closed gate

**Goal:** stop non-local startup when critical runtime dependencies are missing.
**Main files:** `DataCloudHttpServer.java`, launcher config, Runtime Truth registry, deployment profile config.
**Expected outcome:** no production deployment can silently run with noop audit, noop metrics, in-memory idempotency, missing transaction manager, disabled security, missing policy engine, or heuristic-only AI.
**Acceptance:** production-like profile fails closed unless all required trust dependencies are configured.
**Cleanup:** remove scattered fallback rules from handlers.

## 2. Canonical tenant/scope/authorization model

**Goal:** make tenant, workspace, role, policy, and route/action access one backend-enforced path.
**Main files:** `HttpHandlerSupport.java`, `DataCloudSecurityFilter.java`, `EndpointSensitivity.java`, `RouteActionAccessRegistry.java`, handlers.
**Expected outcome:** no handler relies on frontend gating or informal annotations alone.
**Acceptance:** route/action matrix tests cover all protected routes.
**Cleanup:** remove route-local authorization duplication.

## 3. Canonical write-command runtime

**Goal:** all mutations are atomic, idempotent, audited, and observable.
**Main files:** `EntityCrudHandler.java`, event handlers, pipeline/workflow handlers, governance handlers.
**Expected outcome:** entity/event/audit/index side effects are coordinated through transaction/outbox.
**Acceptance:** retry and partial-failure golden tests pass.
**Cleanup:** remove per-handler idempotency implementations.

## 4. Contract generation and drift prevention

**Goal:** OpenAPI/proto/schema contracts become the single source of truth.
**Main files:** `products/data-cloud/contracts/**`, `delivery/ui/src/contracts/schemas.ts`, `delivery/ui/src/api/**`, SDK generation.
**Expected outcome:** frontend clients/schemas are generated, runtime routes match contracts.
**Acceptance:** OpenAPI validation, route inventory, SDK generation, UI generated-client checks pass.
**Cleanup:** delete hand-maintained duplicate schemas.

## 5. Runtime Truth hardening

**Goal:** make `/api/v1/surfaces` operationally authoritative.
**Main files:** `SurfaceRegistryHandler.java`, Runtime Truth registry/probes, UI surface service.
**Expected outcome:** every surface has typed state, dependencies, evidence, runtime posture, and tenant/profile scope.
**Acceptance:** no LIVE surface without probe evidence.
**Cleanup:** remove capability compatibility language.

## 6. Product/shared-library boundary hardening

**Goal:** enforce Data Cloud plane boundaries and keep shared libraries generic.
**Main files:** Gradle modules, platform modules, shared-services, architecture tests.
**Expected outcome:** Data Cloud behavior lives in Data Cloud; platform stays generic.
**Acceptance:** dependency direction and cross-product reuse scorecard pass.
**Cleanup:** delete shared-service residue and stale module references.

## 7. AI/automation governance envelope

**Goal:** every AI/automation action has evidence, confidence, policy, audit, and HITL routing.
**Main files:** `AiAssistHandler.java`, Action Plane runtime, governance/audit services, review queue.
**Expected outcome:** AI is implicit but never silent or ungoverned.
**Acceptance:** PII redaction, low-confidence HITL, production unavailable, and evidence tests pass.
**Cleanup:** move handler-local heuristic behavior behind central automation policy.

## 8. UI/UX and design-system consolidation

**Goal:** outcome-first UI backed by generated clients and Runtime Truth.
**Main files:** `delivery/ui/src/api/**`, route registry, runtime gates, pages/components.
**Expected outcome:** no AEP-as-product wording, no dead actions, no fake live surfaces.
**Acceptance:** Playwright route traversal, runtime gating, a11y tests pass.
**Cleanup:** consolidate service modules and remove duplicated client logic.

## 9. Observability and operations hardening

**Goal:** metrics, traces, logs, audit, and incident signals become mandatory in non-local profiles.
**Main files:** launcher, operations plane, health/runtime truth handlers, runbooks.
**Expected outcome:** actionable runtime evidence for every sensitive operation.
**Acceptance:** observability profile tests and incident/degraded-state tests pass.
**Cleanup:** remove noop production observability.

## 10. Repository cleanup and canonical docs consolidation

**Goal:** stop repeated audit noise from stale docs/code.
**Main files:** root docs, archive docs, Data Cloud docs, shared-services residue.
**Expected outcome:** one canonical docs set and clean active tree.
**Acceptance:** doc truth check, stale-reference check, cleanup checklist pass.

---

# Regression and Release Gates

Minimum gates before production readiness:

* Full build for Data Cloud product modules.
* Full build for directly used platform/shared libraries.
* Contract validation for `data-cloud.yaml`, `action-plane.yaml`, and compatibility `aep.yaml`.
* Runtime route inventory ↔ OpenAPI drift check.
* Generated SDK/client/schema check.
* Tenant isolation tests.
* Role/action/route authorization matrix tests.
* Production profile fail-closed tests.
* Entity/event mutation idempotency tests.
* Transaction/outbox partial-failure tests.
* Event-to-context-to-action provenance tests.
* Runtime Truth dependency-probe tests.
* AI unavailable / low-confidence / redaction / HITL tests.
* Export/download privacy tests.
* Observability tests for trace/request ID/audit/metrics.
* UI traversal from Data Cloud home.
* UI runtime-truth action gating tests.
* Accessibility tests for shell, tables, modals, review queues, charts.
* i18n smoke tests for dates, numbers, strings, timezone.
* Placeholder/mock/stub production scan.
* Dead-code and stale-doc scan.
* Dependency direction checks.
* Circular dependency checks.
* Cleanup validation.

---

# Repository Cleanup Plan

| Priority | Classification         | Path                                                                                           | Reason                                                                                  | Evidence | Safe Fix                                                                | Tests/Validation                             |
| -------- | ---------------------- | ---------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- | -------- | ----------------------------------------------------------------------- | -------------------------------------------- |
| P0       | Replace                | `DataCloudHttpServer.java`                                                                     | Central composition is too large and allows optional production-sensitive dependencies. |          | Split into profile validator, route modules, plane composition modules. | Startup profile tests, route smoke tests.    |
| P0       | Replace                | `EntityCrudHandler.java` mutation flow                                                         | Atomicity/idempotency/audit is partial and route-specific.                              |          | Introduce canonical command executor and outbox.                        | Retry/partial-failure tests.                 |
| P1       | Merge/Replace          | `delivery/ui/src/contracts/schemas.ts`                                                         | Duplicates canonical contracts as frontend-maintained truth.                            |          | Generate schemas from OpenAPI; keep only view-model schemas.            | Contract drift check.                        |
| P1       | Delete after migration | `Capability*` compatibility in `surfaces.service.ts`                                           | Capability language remains after canonical `/surfaces` migration.                      |          | Remove compatibility hooks/types after imports migrate.                 | No `Capability*` imports.                    |
| P1       | Replace                | `delivery/ui/src/api/index.ts`                                                                 | Hand-written services and AEP implementation wording leak into UI layer.                |          | Use generated clients + Data Cloud terminology.                         | UI terminology scan, generated-client check. |
| P1       | Delete                 | `shared-services/feature-store-ingest`                                                         | Marked as residue; canonical location is Data Cloud intelligence plane.                 |          | Delete after reference scan.                                            | Gradle/dependency grep/build.                |
| P1       | Move/Delete            | `dc-aep-analysis.md`                                                                           | Root-level stale analysis artifact.                                                     |          | Move to dated archive or delete.                                        | Documentation truth check.                   |
| P1       | Move/Delete            | `ghatana-data-cloud-aep-end-to-end-audit.md`                                                   | Root-level stale audit artifact.                                                        |          | Move to dated archive or delete.                                        | Documentation truth check.                   |
| P2       | Keep archived, exclude | `docs/archive/data-cloud-audit-legacy/*`                                                       | Historical audit/TODO files should not influence current truth.                         |          | Add archive warning headers and exclude from truth scans.               | Docs index/truth scan.                       |
| P2       | Fix                    | `02_data_cloud_unified_detailed_architecture.md`, `03_data_cloud_unified_high_level_design.md` | Stale contradictory wording around active `planes/action` path.                         |          | Clarify “no stale pre-merge AEP paths,” not no Action Plane path.       | Docs consistency test.                       |

---

## Canonical Docs Matrix

| Doc                                                               | Keep | Merge | Archive | Delete | Notes                                                   |
| ----------------------------------------------------------------- | ---: | ----: | ------: | -----: | ------------------------------------------------------- |
| `products/data-cloud/README.md`                                   |    ✅ |       |         |        | Good canonical entry point.                             |
| `docs/architecture/PLANE_ARCHITECTURE.md`                         |    ✅ |       |         |        | Canonical architecture.                                 |
| `docs/product/01_data_cloud_unified_vision_market_positioning.md` |    ✅ |       |         |        | Canonical vision/positioning.                           |
| `docs/product/02_data_cloud_unified_detailed_architecture.md`     |    ✅ |       |         |        | Keep, but fix stale contradictory validation wording.   |
| `docs/product/03_data_cloud_unified_high_level_design.md`         |    ✅ |       |         |        | Keep, but fix stale contradictory migration wording.    |
| `docs/api/REST_API_DOCUMENTATION.md`                              |    ✅ |    🟡 |         |        | Keep only if generated/validated from OpenAPI.          |
| `docs/operations/RUNBOOK.md`                                      |    ✅ |       |         |        | Keep as operations canonical doc.                       |
| `contracts/README.md`                                             |    ✅ |       |         |        | Keep as contract ownership doc.                         |
| `delivery/ui/ARCHITECTURE.md`                                     |    ✅ |    🟡 |         |        | Keep if aligned with product HLD and generated clients. |
| `dc-aep-analysis.md`                                              |      |       |      🟡 |     🟡 | Root-level stale analysis; remove from active truth.    |
| `ghatana-data-cloud-aep-end-to-end-audit.md`                      |      |       |      🟡 |     🟡 | Root-level stale audit; remove from active truth.       |
| `docs/archive/data-cloud-audit-legacy/*`                          |      |       |       ✅ |        | Keep only as archive with warning/exclusion.            |
| `docs/archive/data-cloud-implementation-legacy/*`                 |      |       |       ✅ |        | Keep only as archive with warning/exclusion.            |

---

## Final Cleanup Checklist

* [ ] Legacy root-level Data Cloud/AEP analysis docs removed or archived.
* [ ] Old audit/TODO docs excluded from canonical truth scans.
* [ ] `shared-services/feature-store-ingest` residue deleted after reference validation.
* [ ] Capability terminology removed from active UI/API code after migration.
* [ ] Frontend contract schemas generated from canonical OpenAPI.
* [ ] Runtime Truth records typed and dependency-probe backed.
* [ ] Data Cloud/Action Plane docs corrected for stale path wording.
* [ ] Product-specific behavior removed from shared libraries.
* [ ] Shared modules validated by cross-product reuse scorecard.
* [ ] In-memory/noop/heuristic fallbacks blocked in non-local profiles.
* [ ] All mutating routes use canonical idempotency/transaction/audit path.
* [ ] Build/lint/typecheck/test/release gate passes.
* [ ] No hidden production fallback paths remain.

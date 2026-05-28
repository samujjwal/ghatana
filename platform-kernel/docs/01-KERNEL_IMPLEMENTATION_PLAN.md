Executed against `samujjwal/ghatana` at commit `2a37e8d0702271a10d47f5707a2720fd657c7f54`.

I audited the full snapshot directionally, not the commit diff. I did not run the build/test commands locally, so the scores below are based on code/docs/registry inspection rather than fresh execution results.

## 1. Executive Summary

**Overall production-readiness score: 2.8 / 5**

Data Cloud is now much closer to a coherent product than a loose library set. The strongest evidence is the plane-based product architecture, canonical module mapping, outcome-first navigation model, Action Plane/AEP consolidation direction, route/security registry, and production-oriented security filter. The canonical docs define Data Cloud as one customer-facing product, with the Action Plane formerly AEP integrated under `products/data-cloud/planes/action` for automation, pipelines, patterns, agents, reviews, runs, and learning. 

However, it is **not production-ready yet**. The main blockers are:

1. **Data Cloud registry claims implemented surfaces but conformance flags remain false** for manifest, observability, security, data access, bridge, agent definitions, mastery bindings, evaluation packs, and runtime module. 
2. **AEP/Action Plane boundary is still transitional**: AEP docs say boundary cleanup is in progress and that some AEP implementation modules remain under Data Cloud temporarily. 
3. **AEP itself declares partially implemented foundations** and explicitly says the unified operator model, PatternSpec compiler, EventCloud SPI, pattern lifecycle, learning loop, replay-safe agent execution, and CI/build gates are not complete. 
4. **Audio-video is still experimental** in the product registry, with lifecycle execution disabled and missing product manifest/media privacy/content safety/artifact-retention readiness. 
5. **UI is powerful but still too technical and route-heavy** for “0 cognitive load”; preview surfaces such as Memory, Entities, Context, Fabric, and Agents are discoverable. 
6. **i18n is incomplete**: an i18n config exists, but major UI/layout/page strings are still hardcoded.  
7. **Route truth is not fully finished**: route manifest docs say Phase 1 is complete, but UI generation, OpenAPI validation, SDK metadata generation, and route docs are still future phases. 
8. **API contract tests and integration tests are advisory**, while production readiness needs them to be release-blocking for Data Cloud. 
9. **Data collection UI still derives important quality advisory logic client-side** instead of relying on a backend-owned quality/intelligence contract. 
10. **Shared library boundaries are known unresolved work**, with architecture docs explicitly listing platform modules that need re-evaluation/splitting. 

Highest-leverage next pass: **finish the canonical Data Cloud runtime contract pass**—route truth, runtime truth, backend capability contracts, UI gating, and production test promotion—before adding new features.

---

## 2. Scope Inspected

I inspected representative product, architecture, registry, UI, backend, security, AEP, agent, audio-video, and test surfaces, including:

| Area                     | Files / evidence inspected                                                                                                              |
| ------------------------ | --------------------------------------------------------------------------------------------------------------------------------------- |
| Repo/product registry    | `config/canonical-product-registry.json`, generated Gradle includes, root Gradle settings                                               |
| Data Cloud docs          | `products/data-cloud/README.md`, `docs/architecture/PLANE_ARCHITECTURE.md`, `docs/ROUTE_MANIFEST_SYSTEM.md`, `docs/aep/README.md`       |
| Backend routing/security | `DataCloudRouterBuilder.java`, `RouteSecurityRegistry.java`, `DataCloudSecurityFilter.java`, `EntityCrudHandler.java`                   |
| UI shell/routes          | `delivery/ui/package.json`, `routes.tsx`, `DefaultLayout.tsx`, `RouteCapabilityRegistry.ts`, `RoleProtectedRoute.tsx`, `i18n/config.ts` |
| UI Data Explorer         | `DataExplorer.tsx`, `collections.ts`                                                                                                    |
| AEP                      | `products/aep/ARCHITECTURE.md`, Data Cloud AEP boundary docs                                                                            |
| Agents                   | `docs/agent-system/README.md`, `DataAnomalyDetectorAgent.java`                                                                          |
| Audio-video              | product registry entry, capability map, Data Cloud Action Plane audio-video capability registration                                     |
| Tests                    | UI route inventory test, route/security/openapi test references, package scripts                                                        |

The repository is broad; this was a code-grounded audit of the main product architecture and representative execution paths, not a line-by-line audit of every file.

---

## 3. Current Product Map

### Data Cloud

Data Cloud has a strong plane model: Experience, Contract, Runtime Truth, Data, Event, Context, Intelligence, Governance, Action, and Operations planes. The README defines this as the active product structure and lists the active module map across `planes/*`, `delivery/*`, `extensions/*`, and deployment assets. 

The generated Gradle includes show a large Data Cloud product surface with modules for Data, Event, Intelligence, Governance, API, Launcher, SDK, Contracts, Agent Registry/Catalog, Action Plane runtime, Action security, scaling, observability, orchestration, compliance, kernel bridge, and integration tests. 

### AEP / Action Plane

The canonical Data Cloud docs say the Action Plane is Data Cloud’s governed automation runtime, but AEP docs still preserve AEP ownership of EventCloud, PatternSpec/EPL, operator runtime, pattern lifecycle, adaptive learning, agent capabilities, and adaptive event governance. 

This is directionally correct, but implementation is still transitional.

### Agents

Agents exist across platform and Data Cloud extension layers. The agent-system documentation defines a strong target path from `AgentDefinition` through release, learning contract, mastery, governed dispatch, memory, learning delta, evaluation, promotion, and mastery transition. 

Concrete Data Cloud agent examples exist, such as a statistical anomaly detector agent with a descriptor, typed input/output, confidence, and explanation. 

### Audio-video

Audio-video is registered as an experimental shared service. Registry modules include persistence, security, cache, messaging, integration tests, multimodal, STT, TTS, vision, and common libraries. 

Data Cloud’s Action Plane has audio-video tool registrations for STT, TTS, vision analysis, and multimodal inference, with remote endpoints and policy tags. 

### Shared libraries

Shared platform modules include observability, security, workflow, AI integration, governance, agent-core, audit, runtime, policy, tool-runtime, messaging, data governance, and identity. 

The architecture docs explicitly warn that some shared modules became shared because Data Cloud and AEP boundaries drifted, and they define rules for what should remain platform-level versus move back into Data Cloud or Action Plane. 

---

## 4. Readiness Scorecard

| Dimension                            |   Score | Rationale                                                                                                                                |
| ------------------------------------ | ------: | ---------------------------------------------------------------------------------------------------------------------------------------- |
| Product coherence                    |     3.0 | Strong plane model, but registry conformance gaps and preview surfaces keep it from feeling finished.                                    |
| Feature completeness                 |     2.5 | Many APIs/routes exist, but key journeys are not proven complete end-to-end.                                                             |
| End-to-end workflow completeness     |     2.5 | Data, pipelines, events, agents, connectors exist, but integration tests are advisory and several flows rely on compatibility/fallbacks. |
| Data Cloud core architecture         |     3.5 | Plane architecture is strong and coherent.                                                                                               |
| AEP architecture/integration         |     2.5 | Correct direction, but AEP architecture itself says foundations are partial.                                                             |
| Agent architecture/integration       |     2.5 | Strong target model and examples, but safety-critical issues remain documented in agent north-star tasks.                                |
| Audio-video architecture/integration |     2.0 | Capability map is ambitious, but product registry marks it experimental and lifecycle-disabled.                                          |
| Shared library quality               |     2.5 | Good platform breadth, but boundary cleanup is explicitly unfinished.                                                                    |
| UI/UX simplicity and consistency     |     3.0 | Good shell and route gating; still too many technical preview surfaces and hardcoded strings.                                            |
| Backend/API correctness              |     3.5 | Strong route/security registry and production validations; not all mutating routes have idempotency parity.                              |
| Plugin/extensibility model           |     3.0 | Plugin routes/tool registration exist; full lifecycle/versioning/sandbox proof remains incomplete.                                       |
| Security and authorization           |     3.5 | Security filter is strong; startup wiring and full matrix tests still need hardening.                                                    |
| Privacy and governance               |     3.0 | Governance routes and policy/audit concepts exist; audio-video and agent privacy gates remain incomplete.                                |
| Observability and operations         |     3.0 | Operations/runtime truth surfaces exist; async/job/action traces need end-to-end proof.                                                  |
| Reliability/failure handling         |     2.5 | Entity write idempotency/outbox direction is good; parity missing for other mutating routes.                                             |
| Performance/scalability              |     2.5 | Pagination/sorting exists in places; large dataset/media/backpressure proof remains weak.                                                |
| i18n readiness                       |     2.0 | i18n config exists, but raw UI strings remain widespread.                                                                                |
| Accessibility readiness              |     2.5 | Some labels and route tests exist; hover-only actions and visual-only states remain risks.                                               |
| Test quality and coverage            |     2.5 | Good route inventory tests; contract/integration tests not yet release-blocking.                                                         |
| Developer experience                 |     3.0 | Scripts exist; dependency sprawl is self-identified in package metadata.                                                                 |
| Configuration/feature flags          |     3.0 | Feature gates and runtime gating exist; too many compatibility/preview surfaces remain exposed.                                          |
| Documentation/code alignment         |     2.5 | Docs are strong but contain mismatches and future-phase claims.                                                                          |
| Production deployment readiness      |     2.5 | Registry says Data Cloud ready as provider, but conformance flags and advisory tests prevent strict readiness.                           |
| Maintainability/SRP/DRY              |     3.0 | Router extraction and registries help; route/UI/shared-library duplication remains.                                                      |
| Overall                              | **2.8** | Production-shaped, not production-ready.                                                                                                 |

---

## 5. Feature Completeness Matrix

| Capability            | Current state                                                                          | Gap                                                                                                              | Severity | Recommended action                                                                                |
| --------------------- | -------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- | -------- | ------------------------------------------------------------------------------------------------- |
| Runtime truth         | `/api/v1/surfaces` is canonical target; compatibility `/api/v1/capabilities` removed.  | UI still has RouteCapability terminology and preview surfaces discoverable.                                      | High     | Finish Runtime Truth naming/model migration and make runtime truth backend-owned.                 |
| Route/security truth  | `RouteSecurityRegistry` is authoritative and fail-closed in production-like profiles.  | Route manifest generation/OpenAPI/SDK/docs phases incomplete.                                                    | High     | Complete route manifest → UI/OpenAPI/SDK generation and drift checks.                             |
| Data collections      | UI calls `/entities/dc_collections` and maps backend entities to collections.          | Collection metadata is still modeled as generic entity data; stats return `storageSize: 0`.                      | High     | Promote collection registry to explicit backend contract with stats/quality/ownership fields.     |
| Data quality          | Data Explorer has quality view and advisory.                                           | Advisory is derived client-side from collections, not from backend quality/intelligence service.                 | Medium   | Move quality advisory to backend-owned quality/intelligence endpoint.                             |
| Events                | Event append/query/get routes exist.                                                   | Replay, offsets, watermarks, backpressure, and stream guarantees not proven in inspected code.                   | High     | Add Event Plane E2E contract tests and runtime truth status.                                      |
| Pipelines             | Action pipeline CRUD/execute routes exist.                                             | Workflow handler can be null; run lifecycle needs stronger proof.                                                | High     | Make pipeline execution capability explicit in runtime truth; add run lifecycle E2E.              |
| AEP patterns/learning | Brain/pattern and learning/review routes exist.                                        | AEP architecture says unified operator model, compiler, EventCloud SPI, lifecycle, and learning loop incomplete. | Critical | Finish Action Plane/AEP pattern lifecycle as one implementation pass.                             |
| Agents                | Agent catalog/memory routes exist; example agent exists.                               | Agent safety/mastery/tenant issues remain documented.                                                            | Critical | Fix agent-core correctness before expanding agent UX.                                             |
| Audio-video           | STT/TTS/Vision/Multimodal mapped and registered as tools.                              | Registry still marks product experimental/lifecycle-disabled.                                                    | High     | Convert audio-video from experimental shared service to governed Data Cloud modality integration. |
| Plugins               | Plugin routes exist under canonical Action Plane namespace.                            | Full sandbox/version/lifecycle proof not established.                                                            | Medium   | Add plugin lifecycle contract tests and policy enforcement.                                       |
| Governance/security   | Security filter is robust and fail-closed in production-like profiles.                 | Must verify startup validator is always registered and permission matrix covers all routes.                      | High     | Add production-profile startup and permission-matrix tests.                                       |
| UI shell              | Sidebar/header/global search/AI assistant/notifications exist.                         | Raw strings, UI role switcher, and many technical surfaces add cognitive load.                                   | High     | Outcome-first UI consolidation and i18n/a11y pass.                                                |
| Tests                 | UI route inventory tests enforce routes/gates.                                         | Test checks source proximity, not full user behavior; API/integration tests advisory.                            | High     | Promote behavior-focused E2E/contract/security tests.                                             |

---

## 6. End-to-End Journey Analysis

| Journey                            | Current implemented path                                                                                        | Missing/broken pieces                                                                                                                                    | Severity |
| ---------------------------------- | --------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- | -------- |
| Create/connect data source         | Connector routes exist with enable/disable/test/sync/health/schema and compatibility `/data-fabric/*` aliases.  | Needs one canonical UI journey from connector → sync → dataset/collection → quality/trust, without parallel API aliases becoming user-facing complexity. | High     |
| Ingest dataset/stream              | Entity CRUD/batch and event append/query routes exist.                                                          | Dataset/stream ingestion lifecycle, import status, replay, and durable error handling are not yet cohesive.                                              | High     |
| Define schema/contract             | Collection schema is stored inside entity metadata and UI updates schema through collection upsert.             | Needs first-class contract/schema lifecycle with compatibility checks and governance state.                                                              | High     |
| Run quality checks                 | Data quality trust score route exists and UI has quality view.                                                  | Quality logic is not consistently backend-owned; advisory is client-derived.                                                                             | Medium   |
| Build/run pipeline                 | Pipeline and execution routes exist under `/api/v1/action`.                                                     | Execution trace, retry/cancel/rollback, idempotency, and observability need E2E proof.                                                                   | High     |
| Detect event patterns              | Brain pattern routes exist.                                                                                     | AEP says PatternSpec compiler/operator model/lifecycle incomplete.                                                                                       | Critical |
| Use adaptive feedback              | Learning trigger/status/review/approve/reject routes exist.                                                     | Learning-to-recommendation-to-promotion loop not complete.                                                                                               | Critical |
| Trigger agent workflow             | Agent catalog, memory, autonomy, and plugin routes exist.                                                       | Agent governance/mastery safety issues remain.                                                                                                           | Critical |
| Process audio/video asset          | Audio-video capabilities mapped and registered to AEP tool catalog.                                             | No clear Data Cloud UI/data-asset lifecycle integration; audio-video remains experimental.                                                               | High     |
| Search/catalog/discover data       | Data Explorer and semantic/similar search routes exist.                                                         | Needs unified search/catalog model across collections, streams, agents, patterns, media.                                                                 | Medium   |
| Govern/access/share data           | Governance routes, security filter, and policy engine integration exist.                                        | Needs route-by-route permission matrix and tenant-scoped test coverage.                                                                                  | High     |
| Observe/debug failed job           | Operations, runtime truth, alerts, job center routes/pages exist.                                               | Async job/run traces and failure recovery need E2E verification.                                                                                         | High     |
| Administer plugins/configuration   | Plugins and settings routes exist; settings is boundary/not discoverable.                                       | Settings/config model not production-complete.                                                                                                           | Medium   |
| Use UI with minimal cognitive load | Shell and route registry exist.                                                                                 | Too many preview technical surfaces, role switcher, raw strings, and permanent aliases.                                                                  | High     |

---

## 7. UI/UX Findings

**Strong parts:** The UI has a real shell, route registry, role disclosure, lazy route loading, disabled-surface fallbacks, global search, notifications, AI assistant, and active operations bar.  

**Main gaps:**

1. **Navigation is not yet 0 cognitive load.** The sidebar exposes Core, Observability, and Manage groups, but also makes technical surfaces discoverable: Events, Alerts, Memory, Entities, Context, Fabric, Agents, Plugins, Operations. 
   **Fix:** Default mode should show only Home, Data, Query, Pipelines, Trust, Operations. Move Memory, Entities, Context, Fabric, Agents, Plugins, Runtime Truth, Release Truth, and Alerts behind progressive disclosure or contextual cards.

2. **Preview surfaces are discoverable.** Alerts, Memory, Entities, Context, Fabric, and Agents are marked `preview` and discoverable. 
   **Fix:** Preview surfaces should not appear in primary nav unless runtime truth says enabled and the user has the proper role/use case.

3. **i18n is incomplete.** The i18n catalog exists, but layout/routes/pages still hardcode labels like “Search…”, “Collapse”, “Product mode”, and route fallback descriptions.  
   **Fix:** Enforce one i18n path and add raw-string lint/test gates for Data Cloud UI.

4. **UI role switcher can confuse permissions.** The UI explains “Product mode is a UI focus preset” and does not grant backend permissions, but the same menu also allows shell role changes. 
   **Fix:** Rename to “View mode” and prevent users from thinking they can self-elevate. Backend authorization must remain authoritative.

5. **Data Explorer has hover-only row actions.** Actions become invisible unless hovered, which hurts keyboard discoverability and accessibility. 
   **Fix:** Use always-available row action affordances or keyboard/focus-visible menus.

6. **Data Explorer quality advisory is client-derived.** It looks AI-like but is derived from local collection state. 
   **Fix:** Either label it as deterministic data-quality guidance or replace it with backend-owned quality/intelligence advisory.

---

## 8. Architecture and Boundary Findings

1. **Plane architecture is strong.** The canonical architecture clearly defines planes, surfaces, modules, runtime truth, and Action Plane as governed automation runtime. 

2. **AEP ownership boundary is unresolved.** Data Cloud docs position Action Plane inside Data Cloud, while AEP docs say AEP owns EventCloud, PatternSpec/EPL, operator runtime, learning, governance, and adaptive semantics. This can work, but only if Action Plane is treated as an integration/runtime surface and not as Data Plane-owned behavior. 

3. **Terminology drift remains.** Plane docs say avoid “capability area”, “AEP capability”, “capability model”, “capability truth”, and “capability registry”, while UI still has `RouteCapabilityRegistry`.  

4. **Route manifest docs and code disagree on path.** Docs list `RouteSecurityRegistry.java` under `.../http/handlers`, but search finds it under `.../http/RouteSecurityRegistry.java`.  

5. **Shared library cleanup is not optional.** The architecture docs explicitly identify platform modules that may have Data Cloud/AEP semantics and need re-evaluation. 

---

## 9. Security, Privacy, Governance Findings

Security is one of the strongest areas.

The `DataCloudSecurityFilter` establishes a clear enforcement chain: public probes, API key/JWT auth, tenant isolation, policy check for critical routes, and audit emission. 

It rejects unknown route metadata in production-like profiles, requires audit for sensitive/critical routes in non-local profiles, validates JWT tenant claims, rejects tenant mismatch, and has production validation for auth/audit/policy/strict tenant mode.   

Remaining gaps:

* Verify the production validator is registered and executed during all startup modes.
* Add route-by-route permission matrix tests for every sensitive/critical route.
* Prove break-glass behavior, audit blocking, audit sink failure, and policy engine failure through integration tests.
* For audio-video, enforce media privacy, content safety, retention, and biometric-risk controls before making it production-facing. Audio-video tool policy tags already flag PII/biometric risk, but access policies are empty in the inspected YAML.  

---

## 10. Observability, Reliability, and Performance Findings

Strengths:

* Route security emits audit and supports blocking audit for critical production-like routes. 
* Entity CRUD has production validation for durable idempotency store, transaction manager, audit service, and outbox processor. 
* Entity batch save/delete uses transaction/outbox/audit patterns. 

Gaps:

* Entity CRUD itself documents that idempotency parity is still needed for pipelines, events, governance, and analytics mutating routes. 
* Connector routes return 503 fallbacks when unavailable, which is good, but the UX must connect this to runtime truth rather than generic unavailable states. 
* UI collection list uses fixed `pageSize: 50`; pagination/virtualization behavior was not proven in inspected UI code. 
* Audio-video large-file processing, queueing, artifact retention, and model availability remain registry/doc-level claims, not proven production readiness.

---

## 11. Test and Verification Gap Analysis

Existing positive signals:

* UI has route inventory tests covering primary routes, preview route gates, compatibility aliases, and route security invariants. 
* UI package includes scripts for lint, type-check, unit, integration, E2E, E2E a11y, coverage, contract tests, route docs, route manifest, and API type checks. 
* Route/security/OpenAPI-related tests exist in search results. 

Gaps:

* Data Cloud README says API contract tests and cross-module integration tests are advisory, not release-blocking. 
* Route inventory tests are partly source-string tests; they prove route declarations, not full user behavior. 
* Need real E2E tests for connector → ingest → collection → schema → quality → catalog → pipeline → event → action → observation.
* Need security matrix tests across viewer/operator/admin, tenant mismatch, audit failure, policy failure, and disabled runtime surfaces.
* Need i18n raw-string tests and a11y tests for row actions, dialogs, nav, keyboard flows, and disabled surfaces.
* Need audio-video integration tests tied to Data Cloud catalog/agent tool execution, not just standalone capability mapping.

---

## 12. Consolidated Task Plan Grouped to Minimize Verification Passes

### Group 1 — Runtime Truth, Route Truth, and Contract Convergence

**Goal:** Make backend route/security/runtime truth the single source for UI, SDK, OpenAPI, and tests.

**Change areas:**

* `products/data-cloud/delivery/launcher/.../RouteSecurityRegistry.java`
* `products/data-cloud/config/route-manifest.json`
* `products/data-cloud/contracts/openapi/*.yaml`
* `products/data-cloud/delivery/ui/src/lib/routing/*`
* `products/data-cloud/docs/ROUTE_MANIFEST_SYSTEM.md`
* route manifest scripts

**Exact changes:**

* Finish route manifest Phase 2–4: manifest → UI runtime truth, OpenAPI validator, SDK metadata.
* Rename UI `RouteCapabilityRegistry` terminology to `RouteSurfaceRegistry` / runtime truth aligned names.
* Fix documented `RouteSecurityRegistry.java` path mismatch.
* Make route aliases explicitly classified as canonical, compatibility, preview, or hidden.
* Promote API contract and integration test modules from advisory when they validate production routes.

**Verification:**

* UI route inventory tests.
* OpenAPI parity tests.
* Route-security drift script.
* UI type-check/lint/unit tests.

**Acceptance criteria:**

* No route can be active without security metadata.
* UI cannot expose a route that backend runtime truth marks disabled/unavailable.
* OpenAPI, generated UI route truth, and backend registry agree.

---

### Group 2 — Canonical Data Cloud Domain/Journey Pass

**Goal:** Make Data Cloud core journeys backend-owned and coherent.

**Change areas:**

* Entity/collection handlers
* Collection API/client
* Data Explorer
* Data quality/trust APIs
* Connector handlers
* Data product/contract handlers

**Exact changes:**

* Promote `dc_collections` from generic entity metadata to a typed collection registry contract.
* Replace `storageSize: 0` and client-derived collection stats with backend-owned stats.
* Move quality advisory out of client-side heuristic logic into Data Quality/Intelligence endpoint.
* Connect connector sync results to collection creation/update.
* Define one journey contract: connector → sync → collection → schema → quality → lineage → trust.

**Verification:**

* API contract tests for collection registry.
* UI integration tests for Data Explorer.
* E2E connector-to-collection test.

**Acceptance criteria:**

* Data Explorer values are backend-owned, explainable, and consistent across cards/detail/views.
* No UI-only quality/ownership/status facts.

---

### Group 3 — Outcome-First UI / 0 Cognitive Load Pass

**Goal:** Keep the UI powerful, but expose complexity progressively.

**Change areas:**

* `DefaultLayout.tsx`
* `routes.tsx`
* `RouteSurfaceRegistry`
* page headers/cards/tables/forms
* i18n resources

**Exact changes:**

* Default nav: Home, Data, Query, Pipelines, Trust, Operations.
* Move Agents, Memory, Context, Fabric, Plugins, Runtime Truth, Release Truth, Alerts into role/contextual surfaces.
* Use route registry icons instead of mapping all generated nav icons to `Activity`.
* Rename “Product mode” / role switcher to avoid permission confusion.
* Replace hover-only row actions with keyboard-visible action menus.
* Replace hardcoded strings with i18n keys.

**Verification:**

* UI unit/integration tests.
* Playwright route traversal.
* a11y test pass.
* pseudo-locale pass.

**Acceptance criteria:**

* Primary user can understand the app without knowing planes, AEP, memory, agents, runtime truth, or plugins.
* Operator/admin can still discover advanced surfaces contextually.

---

### Group 4 — AEP / Action Plane Completion Pass

**Goal:** Finish AEP as a governed Action Plane without leaking ownership into Data Plane.

**Change areas:**

* `products/data-cloud/planes/action/*`
* `products/aep/*`
* action contracts
* pattern/learning/review/run handlers
* AEP boundary docs

**Exact changes:**

* Complete unified operator model.
* Implement PatternSpec compiler and lifecycle.
* Complete EventCloud SPI boundary.
* Implement learning → recommendation → review → promotion → rollback loop.
* Make replay-safe agent execution mandatory for pattern-linked agents.
* Retire or hide legacy root action routes behind feature flags.

**Verification:**

* Action Plane integration tests.
* Pattern lifecycle tests.
* Learning/review approval tests.
* Boundary dependency tests.

**Acceptance criteria:**

* AEP semantics are not imported by Data/Event/Governance planes.
* Patterns can be created, run in shadow, reviewed, promoted, observed, and rolled back.

---

### Group 5 — Agent Governance, Mastery, and Safety Pass

**Goal:** Make agents safe, tenant-scoped, and governed before expanding UX.

**Change areas:**

* `platform/java/agent-core`
* Data Cloud agent registry/catalog
* governed dispatcher
* mastery registry
* memory plane
* agent runtime routes

**Exact changes:**

* Fix documented safety issues: approval/verification flag preservation, tenant source correctness, release tenant ID, mastery item state history validation.
* Separate execution strategy from supervision level.
* Enforce policy/tool/memory/replay/guardrail declarations for agent capabilities.
* Add tenant-scoped agent release and mastery queries.

**Verification:**

* Agent-core unit tests.
* Dispatcher tests.
* Agent memory/search tests.
* Tenant isolation tests.

**Acceptance criteria:**

* Agents may observe/propose freely but act only through governed, tenant-safe, version-compatible mastery.

---

### Group 6 — Audio-Video Productization and Data Cloud Integration

**Goal:** Convert audio-video from experimental shared service into a governed Data Cloud modality.

**Change areas:**

* `products/audio-video/*`
* Data Cloud agent tool catalog
* media storage/artifact retention
* Data Cloud catalog/search/lineage
* operations/runtime truth

**Exact changes:**

* Add product manifest and lifecycle profile or explicitly keep it hidden as experimental.
* Enforce media privacy, content safety, artifact retention, biometric-risk, and consent policy.
* Connect STT/TTS/Vision/Multimodal outputs to Data Cloud datasets/events/catalog.
* Add runtime truth for each media processing dependency.
* Add minimal operator UI only after backend lifecycle is coherent.

**Verification:**

* STT/TTS/Vision/Multimodal contract tests.
* Media privacy/security tests.
* Agent tool invocation tests.
* Data Cloud catalog integration tests.

**Acceptance criteria:**

* A media asset can be ingested, processed, governed, searched, audited, and observed.

---

### Group 7 — Shared Library Boundary Cleanup

**Goal:** Prevent Data Cloud/AEP semantics from leaking into generic platform libraries.

**Change areas:**

* `platform/java/agent-core`
* `platform/java/workflow`
* `platform/java/messaging`
* `platform/java/ai-integration`
* `platform/java/data-governance`
* `platform/contracts`
* Data Cloud plane modules

**Exact changes:**

* Apply the architecture rule: keep only primitives used by three or more unrelated products in platform.
* Move Action Plane runtime semantics to `planes/action`.
* Move Data Cloud storage-plane event routing to `planes/event`.
* Move retention/redaction/provenance/evidence implementations to `planes/governance`.
* Move Data Cloud OpenAPI/schemas into `products/data-cloud/contracts`.

**Verification:**

* Dependency direction checks.
* ArchUnit/module-boundary tests.
* Build all affected products.

**Acceptance criteria:**

* Platform libraries are generic; Data Cloud product behavior lives in Data Cloud.

---

### Group 8 — Security, Privacy, and Permission Matrix Pass

**Goal:** Prove every route and operation is enforced backend-side.

**Change areas:**

* `DataCloudSecurityFilter`
* route registry
* handlers for entities/events/pipelines/governance/agents/plugins/audio-video
* tests

**Exact changes:**

* Register production validators centrally and prove they execute at startup.
* Add permission matrix tests for viewer/operator/admin.
* Add tenant mismatch, missing tenant, missing audit, missing policy, policy denial, audit failure tests.
* Add audio-video PII/biometric policy enforcement.
* Add break-glass tests.

**Verification:**

* Backend unit/integration/security tests.
* Route security drift test.
* Startup profile tests.

**Acceptance criteria:**

* No sensitive/critical backend route relies on frontend-only gating.

---

### Group 9 — Observability, Reliability, and Idempotency Pass

**Goal:** Make every async/action workflow debuggable and recoverable.

**Change areas:**

* Entity/event/pipeline/agent/plugin/audio-video handlers
* Operations UI
* runtime truth
* outbox/job/run models

**Exact changes:**

* Extend idempotency beyond entity writes to pipelines, events, governance, analytics, plugins, and agent actions.
* Add correlation IDs to every run/job/action/agent/media flow.
* Add durable job/run status, retry/cancel/rollback states.
* Add dead-letter/failure visibility for async workflows.
* Surface failed jobs in Operations without requiring logs.

**Verification:**

* Idempotency retry tests.
* Failure injection tests.
* Operations UI E2E tests.

**Acceptance criteria:**

* A failed pipeline/agent/media operation is visible, explainable, retryable or safely terminal.

---

### Group 10 — Test Consolidation Pass

**Goal:** Minimize repeated verification by adding journey-level suites after Groups 1–9 changes.

**Change areas:**

* Data Cloud integration tests
* UI Playwright tests
* API contract tests
* security/a11y/i18n tests

**Exact changes:**

* Promote API contract and integration tests from advisory to release-blocking once stable.
* Add one golden path E2E per main journey.
* Add role/tenant matrix E2E.
* Add pseudo-locale and raw-string enforcement.
* Add keyboard/a11y tests for tables, nav, disabled surfaces, dialogs, and row actions.

**Verification:**

* Gradle affected Data Cloud tests.
* UI lint/type-check/unit/integration/E2E/a11y.
* Contract/OpenAPI drift tests.

**Acceptance criteria:**

* One verification pass validates product behavior, not only component rendering.

---

## 13. Priority Roadmap

### P0 — Production blockers

* Finish route/runtime truth convergence.
* Fix Data Cloud conformance gaps.
* Make API contract/integration tests blocking.
* Fix agent-core safety-critical issues.
* Enforce security startup validators.
* Hide or gate preview/experimental surfaces.

Expected score movement: **2.8 → 3.4**

### P1 — Coherent product completeness

* Typed collection registry.
* Connector → collection → schema → quality → lineage journey.
* Action Plane pattern/learning/review lifecycle.
* Agent governance/memory/mastery integration.
* Operations visibility for failed async workflows.

Expected score movement: **3.4 → 4.0**

### P2 — Hardening and extensibility

* Plugin lifecycle versioning/sandbox/conformance.
* Audio-video governed modality integration.
* Shared library boundary cleanup.
* Performance/scalability/idempotency parity.

Expected score movement: **4.0 → 4.4**

### P3 — Polish and optimization

* UI visual polish and progressive disclosure.
* Full i18n/a11y cleanup.
* Documentation/code path alignment.
* Developer script/dependency cleanup.

Expected score movement: **4.4 → 4.7+**

---

## 14. Final Recommendation

Data Cloud at this commit is **architecturally promising but not production-ready**.

It has the right direction: plane-based architecture, backend-owned route/security metadata, Action Plane consolidation, UI route gating, and serious security posture. But it still has too many unresolved production blockers: Data Cloud conformance gaps, AEP partial implementation, audio-video experimental status, preview-heavy UI, incomplete route truth generation, incomplete i18n, advisory integration tests, and known shared-library boundary drift.

The next implementation pass should focus on:

**Runtime truth + route truth + backend-owned product contracts + UI simplification.**

Do **not** spend the next pass generating release evidence. Also do **not** add more UI surfaces or agent/audio-video features until the current surfaces are contract-backed, permission-backed, runtime-truth-backed, and verified end-to-end.

Highest-return task group: **Group 1 + Group 2 + Group 3 together**. That pass will reduce architectural drift, simplify the user experience, and make the product verifiable with fewer repeated test rounds.

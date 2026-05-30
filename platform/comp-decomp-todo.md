## 1. Executive summary

**Code-audit verdict for commit `72f02d6d70c60c2efd32870d29537d1d23cc6b3a`: not production-ready yet.**

**Overall deterministic readiness score: 2.8 / 5.**

Data Cloud is now much more coherent architecturally than a loose library collection. The repo has a clear plane-based product direction, and the docs explicitly define Data Cloud as a single customer-facing product with Action Plane/AEP integrated under `products/data-cloud/planes/action`.  The module registry also shows a broad Data Cloud surface across shared SPI, data, event, intelligence, governance, delivery, contracts, agent catalog, integration tests, and many Action Plane modules.

However, the current state is still **blocked for production-grade release**. The canonical product registry itself marks Data Cloud lifecycle readiness as blocked due to incomplete conformance gates, missing first-class domain contracts, and incomplete connector production path.

I did **not** run release-readiness checks, evidence generation, release bundle checks, or readiness execution. The plan below also explicitly says **do not run readiness checks in this iteration**.

### Top blockers

1. **Data Cloud is still marked blocked in the canonical registry**, despite many implemented-looking surfaces. The registry still lists first-class domain contracts and connector production path as blockers.
2. **Connector production path is still conditional and can degrade to runtime-truth 503 responses** when the handler is missing or the feature flag is off. That is good UX fallback, but not a complete production connector journey.
3. **The product surface is broad enough to create cognitive load**: entities, events, pipelines, alerts, memory, media, brain, learning, mastery, analytics, reports, executions, AI models, features, SSE, WebSocket, AI assist, voice, governance, lineage, context, MCP, data products, autonomy, agents, plugins, connectors, settings, compliance, sovereign, conformance, and user activity are all route groups.
4. **Pipeline/workflow contracts are still too loose**. The OpenAPI `Pipeline` schema allows freeform nodes, edges, and `additionalProperties: true`, which limits validation, visual builder correctness, and stable plugin interoperability.
5. **AI pattern generation is not production-grade**: `MLOperatorGenerator` calls an LLM but then ignores the response structure and returns a sample operator, with an explicit comment saying production parsing is still needed.
6. **Action/AI safety is partial**. The LLM fact extractor has timeout and token metrics, but failures return an empty list, which can silently degrade learning unless surfaced as runtime truth/quality state.
7. **Audio-video is still experimental at product level**. The registry says it has no product manifest, lifecycle execution disabled, and pending media privacy/content-safety/artifact-retention gates.
8. **Security claims need end-to-end proof per route group**. The OpenAPI says all endpoints require auth and critical/sensitive routes require policy/audit controls, while some handlers rely on HTTP-filter-level RBAC comments rather than visible handler-local enforcement.
9. **Idempotency is not consistently implemented across mutating routes**. Entity writes have idempotency support, but the handler itself notes other mutating routes still require idempotency or explicit non-idempotent documentation.
10. **UI route model is improved but still too broad for zero cognitive load**. The UI has a simplified IA, but still exposes many contextual/preview/operator surfaces and client-side role/view-mode switching.

### Top strengths

1. Data Cloud has a strong canonical plane architecture and clear dependency rules.
2. AEP is correctly being reframed as **Action Plane**, not as a disconnected product.
3. OpenAPI contracts are broad and include entity, event, pipeline, memory, brain, learning, analytics, governance, model registry, feature store, AI assist, voice, reports, plugins, and agents.
4. There are route/OpenAPI alignment tests for both paths and operations.
5. Entity writes have meaningful production invariants around durable idempotency, transaction manager, audit service, and outbox processor.
6. UI routes are lazy-loaded, error-boundaried, role-protected, and runtime-capability-gated for many advanced surfaces.
7. UI navigation is now derived from a route surface registry instead of hardcoded static sections.
8. Audio-video has real Gradle service modules for multimodal and STT, with dependencies on common libraries, governance, security, observability, audit, and persistence.
9. Agent learning has timeout, token logging, parsing, and provenance for extracted facts.
10. The UI already has onboarding, global error boundary, Jotai, QueryClient, theme, toast, Suspense, and i18n initialization.

---

## 2. Scope inspected

I inspected these files/areas at the requested commit:

| Area                          | Files inspected                                                                                                  |
| ----------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| Monorepo/product registry     | `settings.gradle.kts`, `config/generated/settings-gradle-includes.kts`, `config/canonical-product-registry.json` |
| Data Cloud docs               | `products/data-cloud/README.md`, `products/data-cloud/docs/architecture/PLANE_ARCHITECTURE.md`                   |
| Data Cloud manifest/contracts | `products/data-cloud/product-manifest.yaml`, `products/data-cloud/contracts/openapi/data-cloud.yaml`             |
| HTTP/API routing              | `DataCloudRouterBuilder.java`, `OpenApiRouteAlignmentTest.java`                                                  |
| Backend core                  | `EntityCrudHandler.java`, `DataCloudClient.java`, `DataCloud.java`                                               |
| UI app                        | `delivery/ui/package.json`, `App.tsx`, `routes.tsx`, `DefaultLayout.tsx`                                         |
| Action/AI/AEP                 | `MLOperatorGenerator.java`, `DefaultLLMFactExtractor.java`                                                       |
| Audio-video                   | `multimodal-service/build.gradle.kts`, `stt-service/build.gradle.kts`, canonical registry audio-video entry      |

I did not claim a full line-by-line audit of every file in the repo. This is a deterministic static audit based on the current code snapshot and representative high-impact files.

---

## 3. Current product map

### Data Cloud

Data Cloud is registered as a **platform-provider** with backend API, web UI, and SDK surfaces.   It provides data lake storage, entity catalog, event log, query engine, pipeline runtime, governance controls, and observability surface capabilities.

### AEP / Action Plane

The repo is moving away from standalone AEP language. Canonical docs define **Action Plane** as Data Cloud’s governed automation runtime for event-driven agent orchestration, pattern detection, pipeline execution, HITL review, and learning loops.  The route builder enforces canonical Action Plane namespace under `/api/v1/action/*`.

### Agents

Agents exist in multiple layers: Data Cloud Action Plane `agent-runtime`, platform `agent-core`, Data Cloud `agent-registry`, `agent-catalog`, memory APIs, learning APIs, and UI agent catalog pages. The architecture has the right direction, but needs a single product-level runtime contract for lifecycle, policy, approval, execution trace, memory, tool access, and plugin governance.

### Audio-video

Audio-video is registered as a shared service with modules for persistence, security, cache, messaging, multimodal intelligence, STT, TTS, vision, and common libs.  But the canonical registry still marks it **experimental**, without a product manifest, and blocked on media privacy/content-safety/artifact-retention gates.

### Shared libraries

Shared platform modules are extensive: core, database, HTTP, observability, security, workflow, AI integration, governance, agent-core, runtime, audit, messaging, data-governance, identity, and more.  The architecture doc correctly warns that some platform modules may have become shared due to Data Cloud/AEP boundary drift and should be kept in platform only when truly cross-product.

---

## 4. Readiness scorecard

| Dimension                       |   Score | Rationale                                                                                                         |
| ------------------------------- | ------: | ----------------------------------------------------------------------------------------------------------------- |
| Product coherence               |     3.2 | Plane architecture and UI IA are coherent, but surface count is high and registry still says blocked.             |
| Feature completeness            |     2.7 | Many APIs/routes exist, but connectors, pipeline contracts, AI generation, and media workflows remain incomplete. |
| E2E workflow completeness       |     2.5 | Representative journeys exist, but not consistently complete from UI → backend → runtime → audit/observability.   |
| Data Cloud core architecture    |     3.3 | Strong plane/SPI direction; generic entity store still dominates many flows.                                      |
| AEP / Action Plane architecture |     3.0 | Good namespace and integration direction; AI/pattern pieces still partially demo-like.                            |
| Agent architecture              |     2.8 | Agent memory/learning/catalog exist, but lifecycle/governance/tool isolation needs consolidation.                 |
| Audio-video architecture        |     2.0 | Real modules exist, but product-level status is experimental and not manifest-complete.                           |
| Shared library quality          |     2.7 | Strong reuse intent, but boundary drift remains a known risk.                                                     |
| UI/UX simplicity                |     3.1 | Better IA, progressive disclosure, route gating; still too many surfaces for 0 cognitive load.                    |
| Backend/API correctness         |     3.0 | Route/OpenAPI parity tests exist; freeform schemas and optional handlers weaken guarantees.                       |
| Plugin/extensibility model      |     2.8 | Manifest and plugin surfaces exist; lifecycle, validation, versioning, sandboxing need tightening.                |
| Security/authorization          |     2.8 | Auth is specified, but route-by-route backend enforcement needs proof.                                            |
| Privacy/governance              |     2.7 | Governance APIs exist; media privacy and data retention workflows are incomplete.                                 |
| Observability/operations        |     3.0 | Health, metrics, runtime truth, traces exist; async/action/media paths need unified traces.                       |
| Reliability/failure handling    |     2.7 | Some production validations exist; idempotency and durable side effects are inconsistent outside entity writes.   |
| Performance/scalability         |     2.5 | Pagination/event stores exist; large media/stream/backpressure guarantees remain unclear.                         |
| i18n readiness                  |     3.0 | i18n initialized and used in many shared surfaces; raw/fallback labels remain likely.                             |
| Accessibility readiness         |     2.8 | Some role alerts/focus patterns exist; full page-level a11y consistency not proven.                               |
| Test quality                    |     3.0 | Route/OpenAPI parity and focused tests exist; behavior/security/E2E coverage still incomplete.                    |
| Developer experience            |     3.2 | Good scripts and modular layout; UI package itself notes dependency audit/library sprawl.                         |
| Configuration/feature flags     |     3.0 | Runtime gates and disabled-surface UX exist; production fallback paths need stricter behavior.                    |
| Docs/code alignment             |     2.8 | Docs are strong, but registry blockers and implemented-looking contracts conflict.                                |
| Deployment readiness            |     2.3 | Do not treat as production-ready while registry says blocked and audio-video is experimental.                     |
| Maintainability/SRP/DRY         |     3.0 | Router was extracted, but route surface remains very large.                                                       |
| Overall production readiness    | **2.8** | Strong foundation, not yet production-complete.                                                                   |

---

## 5. Feature completeness matrix

| Capability                        | Current state                                                           | Gap                                                                           | Severity | Recommended action                                                                                              |
| --------------------------------- | ----------------------------------------------------------------------- | ----------------------------------------------------------------------------- | -------: | --------------------------------------------------------------------------------------------------------------- |
| Data sources/connectors           | Connector routes exist and return runtime truth when disabled/missing.  | Production connector path still not guaranteed.                               |       P0 | Complete connector handler, typed config, credential lifecycle, schema discovery, sync jobs, dataset linkage.   |
| Collections/datasets/data sources | OpenAPI has first-class contracts.                                      | Registry still says first-class domain contracts missing.                     |       P0 | Align registry, code, storage model, UI, and APIs around one canonical domain model.                            |
| Entity CRUD                       | Stronger transaction/idempotency/outbox design.                         | Quota/security/audit may be optional depending wiring.                        |       P0 | Make production dependencies fail-closed at runtime composition.                                                |
| Events                            | Append/query/tail exist.                                                | Checkpoint default is no-op; cancellation wrapper is weak.                    |       P1 | Add durable checkpoints and real subscription cancellation contract.                                            |
| Pipelines/workflows               | Action routes exist.                                                    | Pipeline contract is freeform.                                                |       P0 | Define typed DAG/node/edge/operator schema and validation.                                                      |
| AEP pattern learning              | LLM-based generator exists.                                             | It returns a sample operator instead of parsed LLM output.                    |       P0 | Replace sample generation with validated JSON parsing, schema validation, confidence, explainability, fallback. |
| Agents/memory                     | Memory and learning APIs exist; fact extractor has timeout/metrics.     | Failure becomes empty list; lifecycle/tool governance incomplete.             |       P1 | Add failure state, policy checks, approval, tool isolation, agent run trace.                                    |
| Audio-video                       | STT and multimodal modules exist.                                       | Product remains experimental/no manifest.                                     |       P1 | Make audio-video a first-class Data Cloud modality with privacy/retention/job lifecycle.                        |
| UI                                | Simplified routes, gates, disabled-surface UX exist.                    | Too many discoverable/preview surfaces; role switcher can confuse real users. |       P1 | Convert to outcome-first dashboard + progressive disclosure by backend capability.                              |
| Plugins                           | Plugin routes and manifest extensions exist.                            | Lifecycle/version/sandbox/conformance not proven E2E.                         |       P1 | Define canonical plugin runtime interface and validation lifecycle.                                             |
| Security/privacy                  | Auth/security requirements specified in OpenAPI.                        | Need route-by-route backend enforcement matrix.                               |       P0 | Centralize access policy and test every sensitive route.                                                        |

---

## 6. End-to-end journey analysis

| Journey                      | Current implemented path                                                  | Missing/broken steps                                                                                      | Severity |
| ---------------------------- | ------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------- | -------: |
| Create/connect data source   | `/api/v1/connectors` and `/data-fabric/connectors` routes exist.          | Feature-gated fallback can be 503; no proven schema discovery → dataset linkage → sync lifecycle.         |       P0 |
| Ingest dataset/stream        | Entity save and event append exist.                                       | Dataset/collection contract alignment still disputed by registry blocker.                                 |       P0 |
| Define schema/contract       | OpenAPI has collection validation schema and DataSource schema preview.   | Need real UI/API workflow for schema evolution, compatibility, publish/deprecate.                         |       P1 |
| Run quality checks           | Trust score route and validation routes exist.                            | Quality results must become first-class collection/dataset state, not disconnected API calls.             |       P1 |
| Build/run pipeline           | Action pipeline/execution routes exist.                                   | Pipeline DAG contract is too loose.                                                                       |       P0 |
| Detect event patterns        | Action Plane and ML operator generator exist.                             | Generator returns sample operator, not parsed validated output.                                           |       P0 |
| Use adaptive feedback        | Learning review routes exist.                                             | Need closed loop: detection → explanation → feedback → versioned pattern update → replay.                 |       P1 |
| Trigger agent workflow       | Agent catalog/memory/autonomy routes exist.                               | Need single agent runtime contract with approvals, tool policy, traces, and retries.                      |       P1 |
| Process audio/video asset    | Data Cloud media artifact routes exist.                                   | No complete modality journey from upload → consent → job → transcript/index → search/action.              |       P1 |
| Search/catalog/discover data | Entity search, semantic search, context, data products exist.             | Need one catalog UX model; avoid splitting Data Explorer, Entity Browser, Fabric, Context, Data Products. |       P1 |
| Govern/share data            | Governance and policy routes exist.                                       | Need backend-enforced policy on every sensitive action.                                                   |       P0 |
| Observe/debug failed job     | Execution logs, operations, runtime truth exist.                          | Need unified trace across connector/pipeline/agent/media/action.                                          |       P1 |
| Admin plugins/config         | Plugin and settings routes exist.                                         | Need safe plugin lifecycle with typed config, sandbox, version, rollback.                                 |       P1 |
| Normal-user UI               | Simplified IA exists.                                                     | Still too many surfaces; client-side role/view switches create cognitive load.                            |       P1 |

---

## 7. UI/UX findings

The UI has moved in the right direction. Routes are simplified around Data, Pipelines, Query, Trust, Insights, Alerts, Operations, Events, Memory, Context, Fabric, Agents, Settings, Plugins, and Connectors.

The strongest UX improvement is that disabled or unavailable routes render meaningful disabled-surface pages instead of silent 404s.

Main UI gaps:

1. **Navigation still exposes implementation complexity.** Core/advanced/preview/manage sections are better than flat navigation, but preview surfaces like memory, entities, context, fabric, agents, alerts can still overwhelm users.
2. **Role/view-mode switcher is useful for development but risky for product UX.** The header lets users switch product view modes and shell roles client-side. That should not be the normal production mental model.
3. **Global AI assistant is always present.** It may be powerful, but zero-cognitive-load UI should show AI help contextually, not as another persistent surface unless the user opts in.
4. **Compatibility routes remain broad.** They are useful for deep-link continuity, but each compatibility route must either redirect cleanly or stay hidden from navigation.
5. **Settings is still route-accessible despite being described in comments as a boundary shell with no writable backed features.** That should be aligned with backend capability truth.

---

## 8. Architecture and boundary findings

The architecture is directionally strong. The canonical architecture explicitly says Data/Event/Context/Governance planes must not import Action Plane implementation internals, Action Plane may consume public contracts/SPI, delivery composition may compose planes, contracts must not depend on runtime implementation, and UI must use generated clients/adapters.

The main boundary risks are:

1. **Shared platform modules may still carry Data Cloud/Action semantics.** The architecture doc already identifies candidate modules to re-evaluate: `platform:java:agent-core`, `workflow`, `messaging`, `ai-integration`, `data-governance`, and `platform:contracts`.
2. **DataCloudClient leaks store access.** The public client exposes `entityStore()` and `eventLogStore()`, which can encourage bypassing product-level policy and runtime truth.
3. **Default checkpoint behavior is unsafe.** A default no-op checkpoint returning true can make stream consumers believe offsets are durably stored when they are not.
4. **Action Plane route namespace is improving, but compatibility aliases remain.** Legacy/compatibility routes should be hidden, audited, and removed only when consumers are migrated.
5. **OpenAPI/route alignment is tested, but semantic behavior is not guaranteed by that test alone.** The test ensures path/operation parity, not correctness, authorization, idempotency, observability, or domain completeness.

---

## 9. Security, privacy, and governance findings

Security posture is specified, but not yet fully proven. The OpenAPI contract requires authenticated identity, production tenant identity from JWT/API key, mismatch rejection, and policy/audit for critical/sensitive routes.

Key gaps:

1. **Backend enforcement must be proven route-by-route.** Handler comments say entity RBAC is enforced at HTTP filter level, but production readiness requires tests across every sensitive route group, not comments.
2. **Governance APIs exist, but policy enforcement must wrap actions, not just expose policy management.** Retention, purge, redaction, policy CRUD, simulation, and toggle routes are present.
3. **Audio-video privacy is not production-complete.** The registry explicitly requires media privacy, content safety, and artifact retention before enabling lifecycle execution.
4. **AI-generated actions need stronger approval/policy boundaries.** Alert auto-remediation, AI suggestions, pipeline drafts, governance recommendations, and next-best actions exist as routes; they need uniform policy checks and audit trail.

---

## 10. Observability, reliability, and performance findings

Strong pieces exist: health/ready/live/info/metrics routes, runtime truth surfaces, operation logs, execution logs, WebSocket/SSE routes, and LLM token metrics.

Main gaps:

1. **Entity write reliability is ahead of other mutating flows.** Entity writes have idempotency/transaction/outbox concepts, but other mutating routes are explicitly not yet covered consistently.
2. **Quota enforcement is optional.** If `tenantQuotaService` is null, no quota check is applied.
3. **Event replay/checkpoint semantics need hardening.** Default checkpoint is a no-op; tail cancellation is a local flag wrapper.
4. **AI pattern generation is unreliable until validated parsing replaces sample output.**
5. **Audio-video services have service modules and smoke tasks, but product-level observability/retention/privacy integration remains incomplete.**

---

## 11. Test and verification gap analysis

Do **not** run release-readiness checks in this iteration.

Important: the UI package maps `pnpm test` to `test:readiness`, and `test:readiness` runs route manifest checks, docs route checks, API type checks, and focused readiness tests.  For this iteration, do **not** run `pnpm test`, `test:readiness`, release-readiness endpoints, evidence-package routes, or release bundle/evidence generation.

Minimum needed tests later, grouped with implementation:

| Test area                | Gap                                                                                                                         |
| ------------------------ | --------------------------------------------------------------------------------------------------------------------------- |
| Unit                     | Typed pipeline schema validation, connector config validation, policy decision helpers, plugin lifecycle state machine.     |
| Integration              | Connector register/test/sync/status → dataset linkage; entity write → event → audit → outbox; event checkpoint persistence. |
| API/contract             | First-class `Collection`, `Dataset`, `DataSource`, `Pipeline`, `AgentRun`, `MediaArtifactJob` request/response contracts.   |
| UI/component             | Data Explorer, Pipeline Center, Connector wizard, Agent Catalog, Media Artifact page, disabled-surface states.              |
| Security/permission      | Route-by-route authorization matrix for mutating, sensitive, AI/autonomy, media, and governance endpoints.                  |
| i18n/a11y                | Navigation labels, disabled surfaces, dialogs, forms, tables, keyboard flows.                                               |
| Observability/error path | Failed connector sync, failed pipeline node, failed agent tool call, failed media processing, LLM timeout.                  |
| Performance-sensitive    | Large collection query, large event replay, large media artifact workflow, high-cardinality metrics safety.                 |

---

## 12. Consolidated task plan grouped to minimize verification passes

### Verification rule for all groups

Do **not** run readiness checks in this iteration. Specifically avoid:

* UI `pnpm test` because it aliases `test:readiness`.
* `test:readiness`.
* Release-readiness endpoints under `/api/v1/release-readiness`.
* Compliance evidence-package generation.
* Release bundle/evidence/conformance readiness execution.

Use only targeted local compile/type/unit/contract tests for touched files when implementation begins.

---

### Group 1 — Canonical product boundary and registry alignment

**Goal:** Make Data Cloud’s registry, manifest, docs, contracts, and implementation say the same thing.

**Where:**

* `config/canonical-product-registry.json`
* `products/data-cloud/product-manifest.yaml`
* `products/data-cloud/README.md`
* `products/data-cloud/docs/architecture/PLANE_ARCHITECTURE.md`
* `products/data-cloud/contracts/openapi/data-cloud.yaml`

**Changes:**

* Resolve the contradiction where OpenAPI now has first-class `Collection`, `Dataset`, `DataSource` contracts but registry still says first-class domain contracts are missing.
* Define exact meaning of “production-ready” for Data Cloud without invoking release readiness execution.
* Keep Action Plane as part of Data Cloud, not a separate user-facing product.

**Tests to add/update:**

* Static registry/manifest/contract consistency test.
* No readiness execution.

**Acceptance criteria:**

* One product map.
* One product readiness state.
* No stale blocker reason remains unless code genuinely still lacks it.

---

### Group 2 — First-class Data Cloud domain model pass

**Goal:** Promote Collection, Dataset, DataSource, DataProduct, Event, Context, and Governance from generic entity-backed concepts into stable domain contracts.

**Where:**

* `products/data-cloud/contracts/openapi/data-cloud.yaml`
* `products/data-cloud/planes/data/entity`
* `products/data-cloud/delivery/launcher/.../EntityCrudHandler.java`
* Data Explorer UI pages/components

**Changes:**

* Add backend handlers/services for first-class `Collection`, `Dataset`, and `DataSource` lifecycle.
* Keep generic entity storage as underlying storage, not the user-facing domain model.
* Add lifecycle states, operational status, quality score, schema version, owner, steward, lineage, retention, and permissions consistently.

**Tests:**

* Contract tests for create/list/get/update/archive collection/dataset/datasource.
* UI contract-backed rendering tests.

**Acceptance criteria:**

* Data Explorer talks in product concepts, not generic storage mechanics.

---

### Group 3 — Connector production path

**Goal:** Make connectors a complete production workflow, not just feature-gated routes.

**Where:**

* `DataCloudRouterBuilder.withConnectorRoutes`
* Connector handlers/services
* `DataSource` OpenAPI schema
* Data Fabric / Connectors UI

**Changes:**

* Implement register → validate credentials → test → schema discovery → map to collection/dataset → sync → status → disable/delete.
* Make disabled runtime-truth fallback explicit and non-confusing.
* Encrypt credentials and support rotation.
* Add connector sync job state and audit.

**Tests:**

* Connector happy path.
* Invalid credentials.
* Disabled feature fallback.
* Tenant isolation.
* Sync failure recovery.

**Acceptance criteria:**

* A user can connect a source and see resulting dataset/collection state without manual backend assumptions.

---

### Group 4 — Typed pipeline and Action Plane contract pass

**Goal:** Replace freeform pipeline contracts with typed, validated DAG contracts.

**Where:**

* `products/data-cloud/contracts/openapi/action-plane.yaml`
* `products/data-cloud/contracts/openapi/data-cloud.yaml`
* `planes/action/operator-contracts`
* `planes/action/registry`
* `planes/action/orchestrator`
* `delivery/ui/src/pages/SmartWorkflowBuilder*`
* `delivery/ui/src/pages/WorkflowDesigner*`

**Changes:**

* Define typed nodes, edges, triggers, schedules, inputs, outputs, policies, approvals, retries, compensations, observability metadata.
* Validate DAG before save and before execute.
* Surface validation errors in UI.

**Tests:**

* DAG validation unit tests.
* Pipeline save/execute/retry/cancel API tests.
* UI builder behavior tests.

**Acceptance criteria:**

* No pipeline can be saved/executed with invalid topology or unknown node/operator type.

---

### Group 5 — AEP/pattern learning production hardening

**Goal:** Make pattern detection and adaptive learning real, explainable, and safe.

**Where:**

* `products/data-cloud/planes/action/analytics/.../MLOperatorGenerator.java`
* `AIPatternDetectionServiceImpl.java`
* Action Plane pattern APIs
* Learning/review APIs
* Event replay APIs

**Changes:**

* Replace sample operator return with real JSON parsing using Jackson.
* Validate generated operators against schema.
* Add confidence, explanation, provenance, model metadata, and fallback deterministic detection.
* Add replay/simulation before promotion.
* Add human review for generated pattern promotion.

**Tests:**

* Valid LLM JSON.
* Invalid JSON.
* Unsafe/generated unknown operator.
* Deterministic fallback.
* Replay simulation.

**Acceptance criteria:**

* No generated pattern becomes active without validation, explanation, and review/policy outcome.

---

### Group 6 — Agent runtime governance and memory lifecycle

**Goal:** Make agents safe, pluggable, observable, and governed.

**Where:**

* `planes/action/agent-runtime`
* `extensions/agent-registry`
* `extensions/agent-catalog`
* memory APIs
* autonomy APIs
* Agent Catalog UI

**Changes:**

* Define canonical `AgentRun`, `ToolCall`, `ApprovalRequest`, `MemoryWrite`, `PolicyDecision`, `RunTrace`.
* Convert LLM fact extraction failures from silent empty lists into runtime truth / learning quality events.
* Add memory retention and redaction enforcement.
* Add tool allowlist, tenant scope, approval policy, and audit per agent action.

**Tests:**

* Agent run lifecycle.
* Tool call denied/approved.
* LLM timeout.
* Memory retention.
* Cross-tenant isolation.

**Acceptance criteria:**

* Every agent action has policy, trace, tenant, audit, and recoverable failure state.

---

### Group 7 — Audio-video as first-class Data Cloud modality

**Goal:** Move audio-video from experimental shared-service status toward Data Cloud modality completeness.

**Where:**

* `products/audio-video/*`
* `products/data-cloud/product-manifest.yaml`
* `products/data-cloud/delivery/api/controller/MediaArtifactController`
* `DataCloudRouterBuilder.withMediaArtifactRoutes`
* `MediaArtifactPage`
* Audio-video persistence/security/messaging modules

**Changes:**

* Define `MediaArtifact`, `MediaProcessingJob`, `Transcript`, `FrameIndex`, `Consent`, `RetentionPolicy`.
* Implement upload/register → privacy check → processing job → transcript/frame index → Data Cloud indexing → search/context/action integration.
* Add media retention and deletion semantics.
* Decide whether audio-video remains a shared service or becomes a Data Cloud modality module with explicit boundary.

**Tests:**

* Artifact create/list/get/delete.
* STT job lifecycle.
* Privacy denied.
* Retention purge.
* Search/index integration.

**Acceptance criteria:**

* Audio/video has a complete product workflow and no longer appears as disconnected metadata routes.

---

### Group 8 — Security, policy, and tenant isolation pass

**Goal:** Make backend enforcement deterministic across every route group.

**Where:**

* HTTP filter/security layer
* All handlers under `delivery/launcher/src/main/java/.../handlers`
* Governance/security planes
* UI `RoleProtectedRoute` and route registry

**Changes:**

* Build a route sensitivity matrix: public, authenticated, sensitive, critical, admin-only, AI/autonomy, media, governance.
* Enforce backend policy per route before handler logic.
* Remove reliance on UI-only role visibility.
* Add break-glass handling only where policy allows it.

**Tests:**

* Unauthorized/forbidden tests for every sensitive route group.
* Tenant mismatch tests.
* Admin-only governance tests.
* AI/autonomy approval-required tests.

**Acceptance criteria:**

* UI gating is convenience only; backend is authoritative.

---

### Group 9 — Observability and runtime truth pass

**Goal:** Make every async or AI-mediated workflow debuggable.

**Where:**

* Runtime truth provider
* Operations page
* connector sync jobs
* pipeline executions
* agent runs
* audio-video jobs
* event replay
* LLM/pattern generation

**Changes:**

* Add unified `correlationId`, `tenantId`, `surface`, `runId`, `jobId`, `agentId`, `pipelineId`, `artifactId`.
* Emit structured logs, metrics, traces, and audit events.
* Convert silent degradation into explicit degraded runtime truth.

**Tests:**

* Error-path observability tests.
* Runtime truth degraded states.
* Correlation propagation tests.

**Acceptance criteria:**

* Operators can debug failure from UI/API response to logs/traces/audit/job state.

---

### Group 10 — UI simplification and zero-cognitive-load pass

**Goal:** Make Data Cloud feel like one simple product.

**Where:**

* `DefaultLayout.tsx`
* `routes.tsx`
* Route surface registry
* Data Explorer
* Pipeline Center
* Trust Center
* Operations Console
* Agent/Memory/Context/Fabric/Plugin/Settings surfaces

**Changes:**

* Keep default nav to Home, Data, Events, Query, Pipelines, Trust, Operations.
* Hide advanced/preview routes behind contextual links from primary workflows.
* Remove or hide client-side shell-role switcher from normal production UX.
* Make AI assistant contextual, not always conceptually central.
* Consolidate Entity Browser, Data Explorer, Context Explorer, Fabric, and Data Products under a unified Data surface unless there is a strong user outcome reason.

**Tests:**

* Navigation visibility by role/capability.
* Keyboard navigation.
* Disabled-surface UX.
* Empty/loading/error/unauthorized states.

**Acceptance criteria:**

* A first-time user can understand what to do without knowing planes, AEP, agents, runtime truth, or plugins.

---

### Group 11 — Shared library boundary cleanup

**Goal:** Prevent product-specific semantics from leaking into shared platform modules.

**Where:**

* `platform:java:agent-core`
* `platform:java:workflow`
* `platform:java:messaging`
* `platform:java:ai-integration`
* `platform:java:data-governance`
* `products/data-cloud/planes/*`
* `products/data-cloud/extensions/*`

**Changes:**

* Keep generic abstractions in platform.
* Move Data Cloud plane semantics into Data Cloud.
* Move Action Plane runtime semantics into `planes/action`.
* Keep adapters explicit.

**Tests:**

* Dependency boundary tests.
* Import purity tests.
* No forbidden Data/Event/Governance → Action implementation imports.

**Acceptance criteria:**

* Shared libraries are reusable by unrelated products without Data Cloud assumptions.

---

### Group 12 — Focused tests only, no readiness execution

**Goal:** Add the minimum test coverage needed to safely implement the above without running release-readiness flows.

**Where:**

* Targeted module test files only.
* Existing route/OpenAPI parity tests can be updated if contracts/routes change.
* UI component/contract tests for touched pages only.

**Do not run:**

* `pnpm test` in Data Cloud UI because it maps to readiness.
* `pnpm run test:readiness`.
* release-readiness API generation.
* evidence-package generation.
* release bundle/evidence checks.

**Acceptance criteria:**

* Implementation groups are verified with targeted tests only.
* No release-readiness execution is introduced into this iteration.

---

## 13. Priority roadmap

### P0 — Production blockers

1. Registry/manifest/contract alignment.
2. Connector production path.
3. Typed pipeline/Action Plane contracts.
4. AI pattern generator replacing sample output.
5. Backend authorization/idempotency for all sensitive mutating routes.

Expected score movement: **2.8 → 3.5**.

### P1 — Coherent product completeness

1. First-class data domain model.
2. Agent runtime governance.
3. Audio-video modality lifecycle.
4. Unified observability/runtime truth for async workflows.
5. UI simplification and progressive disclosure.

Expected score movement: **3.5 → 4.1**.

### P2 — Hardening and extensibility

1. Plugin lifecycle hardening.
2. Shared library boundary cleanup.
3. Event checkpoint/replay durability.
4. Performance/backpressure tests for event, connector, media, and pipeline flows.

Expected score movement: **4.1 → 4.5**.

### P3 — Polish and optimization

1. Visual polish.
2. Better empty/loading/error states.
3. i18n/a11y completion.
4. Cost/performance optimization.
5. Developer docs cleanup.

Expected score movement: **4.5 → 4.8+**.

---

## 14. Final recommendation

Data Cloud is **not production-ready at this commit**, but it has a credible architecture and a much stronger product direction than before.

The next implementation pass should focus on:

1. **Canonical Data Cloud domain model and registry alignment.**
2. **Connector production path.**
3. **Typed Action Plane pipeline/pattern contracts.**
4. **Backend security/idempotency enforcement across mutating routes.**
5. **UI simplification around primary user outcomes.**

Explicitly do **not** work on release-readiness execution, evidence generation, release bundles, or readiness checks in this iteration. Keep the work code-first and product-hardening-first.

The highest-return task group is **Group 2 + Group 3 together**: first-class Data Cloud domain model plus connector production path. That gives users the clearest product value: connect data, understand it, govern it, and use it safely.

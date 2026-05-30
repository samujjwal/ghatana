## Audit execution note

I audited the code snapshot at commit `bdfa732ddbbb53228ab5a21a6704877c4f3ba8dc`. I did **not** run local build/test/readiness commands and I am **not** planning release-readiness execution in this pass. The commit itself is a merge commit whose visible file change is a YAPPC changelog update, so the findings below are based on the full repository state at that commit, not on that small diff alone. 

---

# 1. Executive Summary

**Overall readiness score: 2.8 / 5.0**

Data Cloud is no longer just a random set of modules. It has a serious product architecture: a plane-based model, canonical route manifest, runtime truth endpoint, generated UI route posture, backend route security registry, broad API surface, and a simplified UI information architecture. The README defines Data Cloud as a governed operational data fabric and explicitly places Action Plane/AEP under `products/data-cloud/planes/action`. 

However, the product is still **not production-ready as a coherent, simple, pluggable Data Cloud product suite**. The main blockers are not missing folders; they are **workflow completeness, boundary consistency, runtime durability, tenant-safe AEP semantics, agent governance enforcement, audio-video integration, and UI/API truth alignment**.

## Top 10 blockers

1. **Docs/code conflict around Action Plane permanence.** Canonical docs and Gradle includes keep Action Plane modules active, but the detailed architecture validation matrix still says no active `products/data-cloud/planes/action` should remain after merge.  
2. **AEP EventCloud bridge is not production-grade for tenant-safe stream processing.** It has a default tenant `aep-system`, `publish(topic,payload)` writes without explicit tenant context, `subscribe()` tails from latest offset, and `consumerGroup` is not used for durable offset management.  
3. **Pattern lifecycle is in-memory at the operator-contract level.** `PatternLifecycleRegistry` stores state/events in `HashMap`, so lifecycle state is not durable by default. 
4. **Action engine boundary is suspiciously inverted.** The engine module depends directly on `products:data-cloud:extensions:agent-registry`, despite docs saying HTTP/persistence belong outside the engine.  
5. **Idempotency is incomplete across mutating APIs.** Entity create/batch is covered, but the handler itself documents that pipelines, events, governance, analytics, and other mutating routes still need idempotency or explicit non-idempotent contracts. 
6. **Audio-video exists as infrastructure, not as a first-class Data Cloud modality.** It has persistence/security/cache/messaging modules, but the implementation summary describes infrastructure completion rather than Data Cloud catalog, lineage, AEP, agent, or UI integration.  
7. **UI breadth is high, but some surfaces are preview/hidden and many pages still expose advanced surfaces before their workflows are clearly complete.** Route registry marks alerts, memory, entities, context, fabric, and agents as preview/non-discoverable. 
8. **Runtime truth exists, but not every UI feature gate is runtime-truth-owned.** Several frontend gates are environment-driven with production-safe defaults, which is useful, but still creates a second gating layer beside `/api/v1/surfaces`.  
9. **Several user-visible UI strings still bypass i18n.** Examples include route loading/error text in `routes.tsx`. 
10. **Release/readiness surfaces are still present in UI routing.** `operations/release-truth` exists and is described as a release gates/evidence dashboard; this should stay out of the current implementation pass per your instruction. 

## Top strengths

Data Cloud has a strong canonical product direction: one product, many planes, runtime truth, public contracts, backend security metadata, generated route manifest, and role-disclosed UI. 

The route manifest system is a major production-readiness asset because it connects backend route/security metadata, OpenAPI, UI gating/runtime truth, SDK generation, and documentation. 

The entity write path shows real hardening: production-like profiles require durable idempotency, transaction manager, audit service, and outbox processor. 

The UI is moving toward low cognitive load with a simplified route structure: Data Explorer, Pipeline Center, Insights, Trust Center, and Operations. 

---

# 2. Scope Inspected

Inspected core evidence from:

| Area                    | Files inspected                                                                                                                                                                     |
| ----------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Data Cloud product docs | `products/data-cloud/README.md`, `docs/architecture/PLANE_ARCHITECTURE.md`, `docs/product/02_data_cloud_unified_detailed_architecture.md`                                           |
| Module registry         | `settings.gradle.kts`, `config/generated/settings-gradle-includes.kts`                                                                                                              |
| Runtime routes/security | `DataCloudRouterBuilder.java`, `RouteSecurityRegistry.java`, `config/route-manifest.json`, `ROUTE_MANIFEST_SYSTEM.md`                                                               |
| Data/entity runtime     | `EntityCrudHandler.java`, `DataCloudClient.java`                                                                                                                                    |
| AEP/Action Plane        | `ACTION_PLANE_MODULE_INVENTORY.md`, `planes/action/engine/README.md`, `planes/action/engine/build.gradle.kts`, `PatternLifecycleRegistry.java`, `DataCloudEventCloudConnector.java` |
| Agent architecture      | `ADR-020-agent-system-five-layer-architecture.md`, `DataCloudSafetyPolicyRepository.java`                                                                                           |
| Audio-video             | `products/audio-video/IMPLEMENTATION_SUMMARY.md`                                                                                                                                    |
| UI/UX                   | `delivery/ui/package.json`, `App.tsx`, `routes.tsx`, `DefaultLayout.tsx`, `session.ts`, `feature-gates.ts`, `RouteSurfaceRegistry.ts`                                               |

---

# 3. Current Product Map

## Data Cloud

Data Cloud is organized around planes: Experience, Contract, Runtime Truth, Data, Event, Context, Intelligence, Governance, Action, and Operations. 

The current module map includes `planes/shared-spi`, `planes/data/entity`, `planes/event/core`, `planes/event/store`, `planes/intelligence`, `planes/governance`, `planes/operations`, `planes/action/*`, delivery modules, extensions, and deploy assets. 

## AEP / Action Plane

AEP is integrated as Data Cloud’s Action Plane. The module inventory says AEP semantic modules are intentionally and permanently co-located under `products/data-cloud/planes/action/*`, while Data Cloud owns persisted action metadata, review evidence, policy evidence, audit evidence, and storage integration. 

## Agents

The accepted five-layer agent ADR says the previous system had split registry authority, partial spec enforcement, documentation drift, and no release model. It defines `platform/java/agent-core` as the contract plane, Data Cloud Action Plane as execution, Data Cloud agent registry as durable memory/context/evaluation, and audio-video as product capability tools.  

## Audio-video

Audio-video is registered as a shared-service in generated Gradle includes with persistence, security, cache, messaging, integration tests, multimodal, STT, TTS, vision, and common libraries. 

Its implementation summary shows infrastructure depth, but not yet a Data Cloud-first modality workflow. 

## UI

The UI has a simplified route model and outcome-first pages. It uses React Router, Jotai, TanStack Query, theme provider, i18n, lazy loading, route guards, runtime capability gates, and onboarding.  

---

# 4. Readiness Scorecard

| Dimension                        | Score | Rationale                                                                                                 |
| -------------------------------- | ----: | --------------------------------------------------------------------------------------------------------- |
| Product coherence                |   3.5 | Clear plane architecture and product positioning, but docs conflict on Action Plane permanence.           |
| Feature completeness             |   2.6 | Many APIs/routes exist; E2E product workflows remain uneven.                                              |
| End-to-end workflow completeness |   2.5 | Entity flows are stronger than connector, AEP, agent, and audio-video journeys.                           |
| Data Cloud core architecture     |   3.4 | Good SPI/client/route/runtime-truth direction; durability gaps remain outside entity writes.              |
| AEP architecture/integration     |   2.8 | Rich module set, but event bridge and lifecycle durability are not production-grade.                      |
| Agent architecture/integration   |   2.7 | Strong ADR, registry persistence exists, but governance path enforcement is not clearly universal.        |
| Audio-video integration          |   1.9 | Infrastructure exists, but Data Cloud modality/catalog/lineage/agent workflow integration is immature.    |
| Shared library quality           |   2.8 | Reuse-first intent is strong; boundary leakage remains likely.                                            |
| UI/UX simplicity                 |   3.6 | Simplified IA and role-disclosed surfaces are good; still has raw strings and too many advanced surfaces. |
| Backend/API correctness          |   3.2 | Route registry/security manifest is strong; idempotency and policy consistency incomplete.                |
| Plugin/extensibility             |   2.8 | Plugin routes and module structure exist; lifecycle/isolation/versioning need stronger E2E proof.         |
| Security/authz                   |   3.1 | Backend route metadata is strong; must ensure every mutation has policy/audit/idempotency semantics.      |
| Privacy/governance               |   2.9 | Governance model exists; audio-video consent/retention and agent memory access need integration.          |
| Observability/operations         |   2.9 | Telemetry taxonomy and operations surfaces exist; async flows need uniform traces/status.                 |
| Reliability/failure handling     |   2.7 | Entity write path is hardened; AEP connector offsets/replay/backpressure are weaker.                      |
| Performance/scalability          |   2.5 | Pagination/query models exist; large audio-video and event stream scalability not proven.                 |
| i18n readiness                   |   2.5 | i18n is wired, but raw strings remain.                                                                    |
| Accessibility readiness          |   2.7 | a11y dependencies/scripts exist; route/page-level behavior needs coverage.                                |
| Test quality                     |   3.0 | Unit/integration/E2E/a11y scripts exist; breadth is better than verified journey depth.                   |
| Developer experience             |   3.4 | Generated route manifest and scripts help; dependency sprawl noted by UI package itself.                  |
| Config/feature flags             |   3.0 | Centralized gates exist; some gates are env-owned rather than runtime-truth-owned.                        |
| Docs/code alignment              |   2.4 | Major conflict on Action Plane migration/permanence.                                                      |
| Deployment readiness             |   2.7 | Deployment assets and runtime profiles exist; production posture still uneven.                            |
| Maintainability/SRP/DRY          |   3.0 | Router extracted into groups, but large surfaces and compatibility aliases remain.                        |
| Overall production readiness     |   2.8 | Good foundation, not yet coherent production suite.                                                       |

---

# 5. Feature Completeness Matrix

| Capability                | Current state                                                                     | Gap                                                                                          | Severity                                                           | Recommended action                                                                           |                                                |
| ------------------------- | --------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- | ------------------------------------------------------------------ | -------------------------------------------------------------------------------------------- | ---------------------------------------------- |
| Data entities/collections | Strongest area; CRUD, metadata, validation, export, anomaly routes exist.         | Need consistent schema/contract/data-quality workflow from UI through backend.               | High                                                               | Consolidate Data Explorer around collection lifecycle, schema, quality, lineage, export.     |                                                |
| Event plane               | Event append/query/get offset routes exist.                                       | Required envelope metadata not consistently enforced at every bridge.                        | High                                                               | Make envelope validation mandatory before AEP/event bridge ingestion.                        |                                                |
| AEP event bridge          | Data Cloud EventLogStore bridge exists.                                           | Default tenant, no durable consumer group offsets, starts at latest offset.                  | Critical                                                           | Add explicit tenant subscribe, durable checkpoints, replay semantics, full envelope mapping. |                                                |
| Pattern lifecycle         | Registry/state machine exists.                                                    | In-memory state/events.                                                                      | Critical                                                           | Move lifecycle state/events behind Data Cloud durable repository + audit.                    |                                                |
| Pipelines                 | Action pipeline CRUD and execution routes exist. ritical                          | Add explicit tenant subscribe, durable checkpoints, replay semantics, full envelope mapping. |                                                                    |                                                                                              |                                                |
| Pattern lifecycle         | Registry/state machine exists. fileciteturn33file0L30-L63                     | In-memory state/cycle state/events behind Data Cloud durable repository + audit.             |                                                                    |                                                                                              |                                                |
| Pipelines                 | Action pipeline CRUD and execution routes exist. fileciteturn15file0L168-L187 | Execution routes depend on optional handler; idempotency not complehturn50file0L33-L130   | Need proof all invocations pass governed dispatcher/tool executor. | Critical                                                                                     | Enforce single governed agent invocation path. |
| Agent safety policy       | Data Cloud-backed safety policy repository exists. fileciteturn51file0L21-L31 | visible policy transition/audit semantics here.                                              | High                                                               | Add actor, audit, versioning, active-policy uniqueness, lifecycle rules.                     |                                                |
| Audio-video               | Persistence/security/cache/messaging im0L62-L110                                | No visible Data Cloud catalog/lineage/AEP/agent/UI journey.                                  | Critical                                                           | Treat audio/video asset as Data Cloud dataset/entity + event source + agent to journey.      |                                                |
| UI navigation             | Registry-driven navigation exists. fileciteturn42file0L90-L100                | Preview/advanced surfaces still fragmented.                                                  | Medium                                                             | Keep default nav minimal; move preview surfaces behind contextual disclosure.                |                                                |
| i18n                      | i18n initialized. fileciteturn39file0L28-L32                                  | Raw strings in route loading/error stateute fallbacknslation keys.                           |                                                                    |                                                                                              |                                                |
| Release readiness UI      | Release truth route exists. fileciteturn46file0L243-L253                      | Out of scope for this iteration.                                                             | Medium                                                             | Keep hidden; do not expand or verify release readiness in this pass.                         |                                                |

---

# 6. End-to-End Journey Analysis

## 1. Create/connect data source

**Current path:** `/connAPIs exist, and disabled/handler-missing state returns structured runtime truth JSON. fileciteturn41file0L364-L386 fileciteturn17file0L179-L223
**Gap:** Connection test, schema discovery, sync, sync status, mapping suggestions, data catalog update, lineage event, and UI status are not  st dataset or stream

**Current path:** Entity save/batch, event append/query, and CDC/stream routes exist. fileciteturn15file0L119-L133
**Gap:** Stream ingestion needs consistent envelope enforcement, idempotency, checkpoints, replay, and tenant isolation.
**Sev schema/contract

**Current path:** Collection metadata, validation, infer-schema, OpenAPI contracts, and generated API types exist. fileciteturn15file0L119-L149 fileciteturn16file0L176-L214
**Gap:** Schema lifecycle is not yet visible as a simple peback/retire.
**Severity:** High.

## 4. Run quality checks

**Current path:** Data quality trust score endpoint and validation endpoints exist. fileciteturn15file0L119-L149
**Gap:** Quality summary, drift detect, trust score, and UI drilldown need one canonical qu** High.

## 5. Build/run pipeline

**Current path:** Pipeline CRUD, execution, logs, cancel, retries, checkpoint/restore routes exist. fileciteturn15file0L168-L187 fileciteturn16file0L99 , UI state transitions, and handler availability need consistent treatment.
**Severity:** High.

## 6. Detect event patterns

**Current path:** Action engine claims patternoL7-L13
**Gap:** Durable pattern lifecycle and replayable detection path are not yet production-grade because lifecycle registry is in-memory and event bridge lacks durable consumer-group semantics. fileciteturn33file0L20-L24 fileciteturn*e feedback

**Current path:** Learning trigger/status/review approve/reject routes exist, and docs define HITL + learning loops. fileciteturn16file0L36-L48 fileciteturn5file0L302-L358
**Gap:** Need durable learning episodes, evaluation gates, policy promotion evidence, and runtime gating, not just endpoints.
**Severity:** High.

## 8. Trigger agengent catalog routes and five-layer ADR exist. fileciteturn17file0L103-L120 fileciteturn50file0L76-L94

**Gap:** Every agent action must pass governed dispatcher + ToolExecutor + approval/sandbox/policy/audit path. The ADR requires it, but the audit did not find enough implementation proof.
**Severity:** Critical.

## 9. Process audio/video asset

**Current path:** Audio-video has mmodal modules. fileciteturn8file0L133-L145
**Gap:** Missing product journey: upload/register asset → classify/consent/retention → process job → transcription/vision result → Data Cloud catalog/entity/event → AEP pattern/agent tool → searchable UI.
**Severity:** Critical.

## 10. Search/catalog/discover data

**Current path:** Data Explorer,e Data Product routes exist. fileciteturn41file0L17-L40 fileciteturn17file0L56-L69
**Gap:** Catalog/search is split across Data Explorer, entities, context, data products, connectors, semantic search. Needs one user mental model.
**Severity:** High.ar routes, governance route group, policy simulation, tenant governance, and backend route security metadata exist. fileciteturn41file0L73-L115 fileciteturn11file0L17-L23
**Gap:** Need consistent mutation policy/audit/idempotency semantics across all product routes.
**Severity:** High.

## 12. Observe/debugperations, jobs, alerts, runtime truth, execution logs, and telemetry taxonomy exist. fileciteturn41file0L140-L174 fileciteturn6file0L137-L185

**Gap:** Async workflows need uniform run/job status, trace IDs, failure causes, retry/rollback semantics, and UI drilldown.
**Severity:** High.

---

# 7. UI/UX Findings

The UI’s direction is strong: route registry drives navigation, and default nav is divided into Crole are helpful but could confuse users if they look like permissions. The code warns shell role is disclosure-only. fileciteturn44file0L9-L14 | Rename UI copy to “V itecture and Bounda# A. Data Cloud architecture is coherent but not fully settled

The canonical docs define planes, surfaces, modules, runtime truth, and Action Plane, which is correct. fileciteturn3file0L9-L19

But the detailed architecture still contains a validation matrix that says there should be no active Action Plane module path after merge, while the module inventory says Action Plane modules are permher migration is required. fileciteturn6file0L240-L255 fileciteturn27file0L59-L70

**Required fix:** nt so the accepted state is: Action Plane remains under Data Cloud, AEP semantics remain AEP-owned, and dependency direction is enforced by boundaries.

## B. Action engine has boundary pressure

The engine README says it owns runtime primitives but not agent runtime, identity, scaling, compliance, HTTP endpoints, or persistence. fileciteturn29file0L14-L18

The engine build depends omantics should move/split into Data Cloud. fileciteturn3file0L201-L230

**Required fix:** Review platform `agent-core`, `workflow`, `messaging`, `ai-integratiernance`, and `contracts` against current usage and remove Data Cloud semantic leakage.

---

# 9. Security, Privacy, Governance Findings

Backend route metadata is strong: `RouteSecurityRegistry` is the authoritative regplementation is infrastructure-level, not a full consent/retention/classification journey. fileciteturn47file0L62-L76
4. **AEP default tenant is unsafe as a pre event bridge default tenant must not be used for tenant-scoped production event flows. fileciteturn34file0L47-L58

---

# 10. Observability, Reliability, and Performance Findings

The architecture defines telemetry taxonomy and trace flow across client, API, Data Cloud, AEP, store, audit, and metrics. fileciteturn6file0L137-L185

Actual implementation is uneven:

| Area              | Gap                                                                                                                              |
| ----------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| Entity writes     | Stronger: trace spans, event append, audit payload, outbox. fileciteturn22file0L89-L205                                      |
| AEP event bridge  | Logs publish/subscribe, but lacks durable consumer group offsets, replay start control, full envelope, policy/audit propagation. |
| Pattern lifecycle | In-memory lifecycle state makes runtime recovery and audit incomplete.                                                           |
| Audio-video       | Messaging and persistence exist, but no visible unified job/run status across Data Cloud Operations.                             |
| UI operations     | Operations pages exist, but job/debug status needs to become one cross-plane operational model.                                  |

---

# 11. Test and Verification Gap Analysis

This pass should **not run or plan release-readiness checks**.

Do **not** run or plan:

```text
pnpm test:readiness
check:product-release-readiness
check:production-readiness
verify-production-readiness.sh
validate-data-cloud-release-evidence
readiness evidence generation
release bundle generation
```

Minimum focused verification to add later inside implementation groups:

| Test type     | Needed coverage                                                                                           |
| ------------- | --------------------------------------------------------------------------------------------------------- |
| Unit          | Event envelope validation, pattern lifecycle transitions, agent safety policy versioning, UI i18n helpers |
| Integration   | Entity write + event + audit + outbox; AEP event bridge publish/subscribe/checkpoint/replay               |
| API/contract  | Route manifest route/action parity; idempotency semantics per mutation                                    |
| UI/component  | Data Explorer, Pipeline Center, Event Explorer, DisabledSurface, RouteCapabilityGate                      |
| E2E           | Connector → schema → ingest → quality → catalog → event → pipeline → operations status                    |
| Security      | Tenant isolation, policy required, blocking audit required, admin/operator/viewer access                  |
| i18n/a11y     | No raw user-visible strings; keyboard/focus for nav/actions/modals                                        |
| Observability | Trace/correlation ID appears in API response, audit record, operation/job UI                              |

---

# 12. Consolidated Task Plan Grouped to Minimize Verification Passes

## Group 1 — Canonical architecture and boundary cleanup

**Goal:** Remove architecture ambiguity before touching implementation.

**Files/directories:**

```text
products/data-cloud/docs/product/02_data_cloud_unified_detailed_architecture.md
products/data-cloud/docs/architecture/PLANE_ARCHITECTURE.md
products/data-cloud/docs/architecture/ACTION_PLANE_MODULE_INVENTORY.md
products/data-cloud/planes/action/engine/build.gradle.kts
products/data-cloud/planes/action/orchestrator/build.gradle.kts
products/data-cloud/planes/action/agent-runtime/build.gradle.kts
products/data-cloud/extensions/agent-registry/*
```

**Changes:**

* Replace “no active `planes/action` after merge” language with the accepted co-location decision.
* State final boundary rule: Action Plane modules stay, AEP semantics remain AEP-owned, Data Cloud owns durable metadata/governance/storage integration.
* Move direct engine dependency on `extensions:agent-registry` behind a stable SPI or into runtime composition/orchestrator.
* Add a boundary note that engine cannot depend on durable registry implementation.

**Tests to add/update:**

* Focused dependency-boundary unit/architecture test for Action engine imports.
* Build-level module dependency assertion test only for touched module graph.

**Verification:** targeted module boundary tests only. **Do not run readiness checks.**

**Acceptance criteria:**

* No doc contradiction.
* Engine is free of Data Cloud durable registry implementation dependency.
* Runtime composition still wires engine + registry.

**Risk:** High.

---

## Group 2 — Data Cloud event envelope and AEP bridge hardening

**Goal:** Make AEP integration tenant-safe, replayable, and production-grade.

**Files/directories:**

```text
products/data-cloud/planes/shared-spi/src/main/java/com/ghatana/datacloud/DataCloudClient.java
products/data-cloud/planes/action/event-bridge/src/main/java/com/ghatana/aep/eventcloud/DataCloudEventCloudConnector.java
products/data-cloud/planes/action/operator-contracts/src/main/java/com/ghatana/aep/event/spi/*
products/data-cloud/planes/event/core/*
products/data-cloud/planes/event/store/*
```

**Changes:**

* Remove production use of default tenant `aep-system`.
* Add explicit tenant-aware subscribe API.
* Use Data Cloud event envelope fields: source, subject, schemaVersion, correlationId, causationId, classification, policyContext, provenance, traceContext.
* Persist consumer-group offsets/checkpoints.
* Support replay from requested offset, not only latest offset.
* Add backpressure/failure callback semantics.
* Emit trace/audit metadata for bridge operations.

**Tests to add/update:**

* Publish with explicit tenant.
* Subscribe with consumer group and resume from checkpoint.
* Replay from offset.
* Reject/flag missing source/classification/policy metadata where required.
* Cross-tenant isolation test.

**Verification:** focused event bridge and event store tests only. **Do not run readiness checks.**

**Acceptance criteria:**

* No default-tenant production path.
* Consumer group offsets are durable.
* Replay is deterministic.
* Event metadata is not lost crossing Data Cloud ↔ AEP.

**Risk:** Critical.

---

## Group 3 — Durable Action Plane pattern lifecycle and learning loop

**Goal:** Move pattern lifecycle and adaptive learning from in-memory control to durable, auditable Data Cloud state.

**Files/directories:**

```text
products/data-cloud/planes/action/operator-contracts/src/main/java/com/ghatana/aep/pattern/lifecycle/*
products/data-cloud/planes/action/registry/*
products/data-cloud/planes/action/engine/*
products/data-cloud/extensions/agent-registry/*
products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/LearningHandler.java
```

**Changes:**

* Introduce durable `PatternLifecycleRepository`.
* Store pattern state, lifecycle events, promotion decisions, actor, policy decision, confidence, and trace ID.
* Replace raw `HashMap` lifecycle state in production path.
* Keep in-memory registry only for deterministic local/unit tests.
* Connect learning review approve/reject to durable lifecycle transitions.
* Ensure pattern promotion creates governance/audit events.

**Tests to add/update:**

* Draft → validated → shadow → active transition.
* Invalid transition rejected.
* Lifecycle survives restart/repository reload.
* Approve/reject review writes audit/lifecycle event.
* Tenant isolation.

**Verification:** focused lifecycle/learning tests only. **Do not run readiness checks.**

**Acceptance criteria:**

* Production lifecycle state is durable.
* Every transition has actor, tenant, policy, audit, trace.
* UI can show lifecycle history.

**Risk:** Critical.

---

## Group 4 — Idempotency and mutation semantics pass

**Goal:** Make all mutating APIs explicit: idempotent, non-idempotent, or guarded.

**Files/directories:**

```text
products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/RouteSecurityRegistry.java
products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java
products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/*
products/data-cloud/config/route-manifest.json
products/data-cloud/contracts/openapi/*.yaml
```

**Changes:**

* Inventory every POST/PUT/PATCH/DELETE route.
* Add idempotency key support where retry-safe mutations are expected.
* Explicitly mark non-idempotent operations with reason.
* Ensure blocking audit for destructive and governance-sensitive actions.
* Align route manifest metadata with actual handler behavior.

**Tests to add/update:**

* Retry same idempotency key returns same response.
* Conflicting payload same key rejected.
* Non-idempotent operation documented and guarded.
* Destructive operation requires blocking audit.

**Verification:** focused route/action metadata + handler tests only. **Do not run readiness checks.**

**Acceptance criteria:**

* No ambiguous mutating route.
* Route manifest truth matches handler behavior.
* Entity idempotency pattern generalized.

**Risk:** High.

---

## Group 5 — Agent governance and tool execution unification

**Goal:** Enforce the five-layer agent architecture in runtime code.

**Files/directories:**

```text
platform/java/agent-core/*
platform/java/tool-runtime/*
platform/java/workflow/*
platform/java/policy-as-code/*
products/data-cloud/planes/action/agent-runtime/*
products/data-cloud/planes/action/orchestrator/*
products/data-cloud/extensions/agent-registry/*
products/data-cloud/extensions/agent-catalog/*
products/audio-video/*
```

**Changes:**

* Ensure every agent invocation enters through governed dispatcher.
* Ensure every side-effecting tool call uses `ToolExecutor`.
* Enforce policy, approval, sandbox, audit, and trace ledger.
* Add agent release state checks before invocation.
* Add kill switch and capability manifest enforcement.
* Make audio-video tools register as product capability tools, not private runtime clones.

**Tests to add/update:**

* Agent cannot run without active release.
* Tool call cannot bypass ToolExecutor.
* Policy denial blocks action.
* HITL-required action creates review item.
* Audio transcription tool appears in agent catalog and uses governed path.

**Verification:** focused agent runtime/tool governance tests only. **Do not run readiness checks.**

**Acceptance criteria:**

* One governed agent invocation path.
* No direct product-owned tool execution bypass.
* Agent registry is durable memory/context/evaluation authority.

**Risk:** Critical.

---

## Group 6 — Audio-video as first-class Data Cloud modality

**Goal:** Convert audio-video from infrastructure service to integrated Data Cloud capability.

**Files/directories:**

```text
products/audio-video/modules/infrastructure/*
products/audio-video/modules/intelligence/multimodal-service/*
products/audio-video/modules/speech/stt-service/*
products/audio-video/modules/speech/tts-service/*
products/audio-video/modules/vision/vision-service/*
products/data-cloud/delivery/api/*
products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/api/controller/MediaArtifactController.java
products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java
products/data-cloud/delivery/ui/src/pages/*
products/data-cloud/delivery/ui/src/lib/routing/RouteSurfaceRegistry.ts
```

**Changes:**

* Define `MediaArtifact` as Data Cloud entity/dataset with classification, consent, retention, owner, source, and lineage.
* Emit events for upload, transcription requested, transcription completed, vision analysis completed, processing failed.
* Connect audio-video jobs to Operations Job Center.
* Register transcription/vision/multimodal tools in agent catalog.
* Add Data Cloud UI path for media artifact lifecycle.
* Ensure large file handling, async job status, retry, failure reason, and retention policy.

**Tests to add/update:**

* Register artifact → create Data Cloud entity/event.
* Trigger transcription → job visible in operations.
* Completion writes transcript entity + lineage.
* Privacy/retention policy enforced.
* Agent tool can invoke transcription through governed path.

**Verification:** focused audio-video/Data Cloud integration tests only. **Do not run readiness checks.**

**Acceptance criteria:**

* Audio/video is searchable, governed, observable, and agent-usable through Data Cloud.
* No standalone-only audio-video island.

**Risk:** Critical.

---

## Group 7 — UI simplification, runtime-truth gating, and no-release-readiness scope

**Goal:** Keep the UI powerful but simple with zero cognitive load.

**Files/directories:**

```text
products/data-cloud/delivery/ui/src/routes.tsx
products/data-cloud/delivery/ui/src/layouts/DefaultLayout.tsx
products/data-cloud/delivery/ui/src/lib/routing/RouteSurfaceRegistry.ts
products/data-cloud/delivery/ui/src/lib/feature-gates.ts
products/data-cloud/delivery/ui/src/components/common/*
products/data-cloud/delivery/ui/src/pages/*
products/data-cloud/delivery/ui/src/lib/i18n/*
```

**Changes:**

* Keep default nav outcome-first: Home, Data, Events, Query, Pipelines, Trust, Operations.
* Keep Agents, Memory, Context, Fabric, Alerts as contextual/advanced surfaces.
* Move env feature gates behind runtime-truth-derived capability service.
* Keep `operations/release-truth` hidden and do not expand release-readiness UI this iteration.
* Replace route loading/error raw strings with i18n.
* Standardize page shell: title, summary, primary action, table/card area, empty/error/disabled state.
* Use one table/list/action pattern across Data, Events, Pipelines, Agents, Plugins, Connectors.

**Tests to add/update:**

* Navigation shows only appropriate discoverable surfaces per view mode.
* Disabled surface explains why feature is unavailable.
* No release-readiness route is discoverable by default.
* Raw string scan for touched UI files.
* Keyboard/focus test for nav and action menus.

**Verification:** focused UI unit/component/a11y tests only. **Do not run readiness checks.**

**Acceptance criteria:**

* A normal user sees a simple product.
* Advanced surfaces are discoverable only when useful.
* Release-readiness execution remains out of scope.

**Risk:** High.

---

## Group 8 — Shared library and dependency sprawl cleanup

**Goal:** Make shared libraries truly shared and product libraries product-owned.

**Files/directories:**

```text
platform/java/agent-core
platform/java/workflow
platform/java/messaging
platform/java/ai-integration
platform/java/data-governance
platform/contracts
products/data-cloud/planes/shared-spi
products/data-cloud/delivery/ui/package.json
```

**Changes:**

* Apply “shared only if used by 3+ unrelated products” rule from plane architecture.
* Move Data Cloud semantics from platform into Data Cloud planes.
* Keep platform libraries generic.
* Remove unused UI workspace dependencies noted by UI package metadata.
* Ensure product code uses `products/data-cloud/contracts` instead of platform contracts for Data Cloud APIs.

**Tests to add/update:**

* Dependency boundary tests.
* Import-path checks for product semantic leakage.
* UI dependency usage check for touched dependencies.

**Verification:** focused dependency-boundary tests only. **Do not run readiness checks.**

**Acceptance criteria:**

* Platform libraries are generic.
* Data Cloud semantics are inside Data Cloud.
* UI package no longer carries obvious unused workspace sprawl.

**Risk:** Medium.

---

# 13. Priority Roadmap

## P0 — Production blockers

1. Fix Action Plane doc/code contradiction.
2. Harden AEP EventCloud bridge: tenant, envelope, consumer groups, replay.
3. Make pattern lifecycle durable.
4. Enforce governed agent/tool execution path.
5. Integrate audio-video as governed Data Cloud modality.

Expected score improvement: **2.8 → 3.5**

## P1 — Coherent product completeness

1. Complete connector → ingest → schema → quality → catalog journey.
2. Generalize idempotency and mutation metadata.
3. Consolidate UI runtime-truth gating.
4. Standardize operations/job/run status.

Expected score improvement: **3.5 → 4.0**

## P2 — Hardening and extensibility

1. Plugin lifecycle/version/isolation.
2. Shared library cleanup.
3. Data product/catalog/search consolidation.
4. Agent memory/evaluation promotion lifecycle.

Expected score improvement: **4.0 → 4.4**

## P3 — Polish and optimization

1. UI polish and full i18n cleanup.
2. a11y keyboard/focus pass.
3. Performance/load-specific hardening.
4. Better progressive disclosure and onboarding.

Expected score improvement: **4.4 → 4.7+**

---

# 14. Final Recommendation

**Data Cloud is not production-ready yet**, but it has crossed an important threshold: it now has a credible product architecture and enough runtime/UI structure to harden systematically.

The next implementation pass should focus on:

1. **Action Plane boundary correctness**
2. **AEP event bridge durability and tenant safety**
3. **Durable pattern lifecycle**
4. **Agent governance enforcement**
5. **Audio-video as a first-class Data Cloud modality**
6. **UI simplification/runtime-truth gating**

Explicitly **do not** work on release-readiness execution, release evidence generation, release bundle validation, or readiness check orchestration in this iteration.

Highest-return task group: **Group 2 — Data Cloud event envelope and AEP bridge hardening**, because it unlocks reliable AEP, agents, pipelines, learning, operations, and audio-video event integration.

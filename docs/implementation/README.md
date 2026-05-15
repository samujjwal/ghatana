## Static scan scope

**Classification:** target-architecture

I scanned the `samujjwal/ghatana` repo at target commit:

`b6fb8fd5ebd9b760db9e89fa6b88f6bf7d341d10`

The commit exists and is titled **“agent refactor 5-14-1.”** 

I focused on the code paths needed for your GAA goal:

```text
Agent definition
→ Agent release
→ Learning contract
→ Mastery registry
→ Version-aware mode selection
→ Memory retrieval
→ Governed runtime dispatch
→ Learning deltas
→ Evaluation/promotion
→ Obsolescence detection
→ Data Cloud API/admin surface
```

I did **not** run the build or tests locally; this is a static code and architecture scan from the target commit snapshot.

---

# Implementation progress (live)

Last updated: 2026-05-15

## Completed in this implementation pass

- [x] P0 tenant-safe canonical memory retrieval: [products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/aep/engine/registry/AgentMemoryPlaneClient.java](products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/aep/engine/registry/AgentMemoryPlaneClient.java) now supports tenant-explicit `queryMemoryItemsMasteryAware(...)` so governed dispatch memory reads preserve request tenant isolation.
- [x] P0 orchestrator memory port fix: [products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/aep/di/AepOrchestrationModule.java](products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/aep/di/AepOrchestrationModule.java) now forwards dispatch `tenantId` into canonical memory retrieval instead of relying on client-default tenant fallback.
- [x] P2/P10 conformance hardening: [scripts/check-agent-conformance.sh](scripts/check-agent-conformance.sh) now fails on deprecated direct mastery mutation route registration, deprecated mastery-registry API usage in production code, and runtime/product `MemoryProjectionBridge` drift.
- [x] Tenant-routing regression coverage: [products/data-cloud/planes/action/orchestrator/src/test/java/com/ghatana/aep/engine/registry/AgentMemoryPlaneClientMasteryTest.java](products/data-cloud/planes/action/orchestrator/src/test/java/com/ghatana/aep/engine/registry/AgentMemoryPlaneClientMasteryTest.java) now verifies canonical memory query APIs preserve explicit request tenant.
- [x] P0 dispatcher wiring: [products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/aep/di/AepOrchestrationModule.java](products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/aep/di/AepOrchestrationModule.java) now wires full `GovernedAgentDispatcher` constructor inputs (release repo, capability manifest, mastery registry, version resolver, task classifier, mode selector, memory retriever).
- [x] P0 safe startup checks: production profile now fails fast when required governed-dispatch dependencies are missing.
- [x] P0 memory retriever path: replaced no-op memory retriever binding with `MasteryAwareMemoryRetriever` adapter and made `MasteryAwareMemoryRetriever` implement `MemoryRetriever`.
- [x] P0 direct mutation hardening: [products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/api/controller/MasteryController.java](products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/api/controller/MasteryController.java) now blocks direct mastery mutation endpoints by default; they require explicit break-glass + `mastery:breakglass` permission.
- [x] P1 learning delta durability: [products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/learning/delta/DataCloudLearningDeltaRepository.java](products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/learning/delta/DataCloudLearningDeltaRepository.java) now has tenant-scoped lookup/update/eval/promotion methods and durable lookup fallback that no longer relies solely on volatile reverse indexes.
- [x] P1 learning delta lifecycle trace: repository now appends `lifecycleEvents` metadata entries for state changes, evaluations, and promotions.
- [x] P1 API contract extension: [platform/java/agent-core/src/main/java/com/ghatana/agent/learning/LearningDeltaRepository.java](platform/java/agent-core/src/main/java/com/ghatana/agent/learning/LearningDeltaRepository.java) now includes tenant-scoped default overloads for delta operations.
- [x] P1 repository regression tests: added tenant-scoped delta lookup/update coverage in [products/data-cloud/extensions/agent-registry/src/test/java/com/ghatana/datacloud/agent/learning/delta/DataCloudLearningDeltaRepositoryTest.java](products/data-cloud/extensions/agent-registry/src/test/java/com/ghatana/datacloud/agent/learning/delta/DataCloudLearningDeltaRepositoryTest.java).
- [x] Focused validation: `DataCloudLearningDeltaRepositoryTest` passed (`15 passed, 0 failed`).
- [x] P1 promotion hardening: [platform/java/agent-core/src/main/java/com/ghatana/agent/promotion/PromotionEvidenceMapper.java](platform/java/agent-core/src/main/java/com/ghatana/agent/promotion/PromotionEvidenceMapper.java) now requires explicit category metadata or category-scoped cases for `regression/safety/recovery/compatibility` evidence.
- [x] Promotion mapper regression tests added: [platform/java/agent-core/src/test/java/com/ghatana/agent/promotion/PromotionEvidenceMapperTest.java](platform/java/agent-core/src/test/java/com/ghatana/agent/promotion/PromotionEvidenceMapperTest.java).
- [x] P1 obsolescence evidence normalization: added [platform/java/agent-core/src/main/java/com/ghatana/agent/obsolescence/ObsolescenceEvidenceMapper.java](platform/java/agent-core/src/main/java/com/ghatana/agent/obsolescence/ObsolescenceEvidenceMapper.java) and integrated it into [platform/java/agent-core/src/main/java/com/ghatana/agent/obsolescence/DefaultObsolescenceTransitionService.java](platform/java/agent-core/src/main/java/com/ghatana/agent/obsolescence/DefaultObsolescenceTransitionService.java) with routed policy-safe target transitions.
- [x] Obsolescence mapper regression tests added: [platform/java/agent-core/src/test/java/com/ghatana/agent/obsolescence/ObsolescenceEvidenceMapperTest.java](platform/java/agent-core/src/test/java/com/ghatana/agent/obsolescence/ObsolescenceEvidenceMapperTest.java).
- [x] Mastery route dispatch coverage: added [products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/DataCloudMasteryRouteDispatchTest.java](products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/DataCloudMasteryRouteDispatchTest.java) to verify request-level dispatch for the new mastery preview/dry-run/obsolescence launcher routes.
- [x] Mastery server startup coverage: added [products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/DataCloudHttpServerMasteryTest.java](products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/DataCloudHttpServerMasteryTest.java) to verify the same mastery routes are reachable through real `DataCloudHttpServer` startup wiring.
- [x] Focused validation update: `PromotionEvidenceMapperTest` + `ObsolescenceEvidenceMapperTest` + `DataCloudLearningDeltaRepositoryTest` passed (`23 passed, 0 failed`).

## In progress / next slices

- [x] Canonical memory plane migration (phase-1): introduced `AgentMemoryQueryPort` in agent-core and wired `MasteryAwareMemoryRetriever` to prefer `AgentMemoryPlaneClient` canonical query path (legacy `MemoryProjectionBridge` retained as fallback adapter).
- [x] Promotion hardening: require explicit category evidence (`regression`, `safety`, `recovery`, `compatibility`) before `COMPETENT -> MASTERED`.
- [x] Obsolescence evidence normalization: map detector reasons into transition-policy keys via dedicated mapper.
- [x] Mastery API expansion: added `previewDecision`, `previewRetrieval`, `dryRunPromotion`, and `processObsolescenceEvent` controller endpoints with governance checks.
- [x] Mastery launcher/OpenAPI reachability: composed launcher-side mastery persistence from the canonical Data Cloud `EntityStore`, registered the four new mastery endpoints on `DataCloudRouterBuilder`, and added matching OpenAPI paths.
- [x] Mastery launcher dispatch verification: `DataCloudRouterBuilderIntegrationTest` + `DataCloudMasteryRouteDispatchTest` passed (`11 passed, 0 failed`) and `checkDataCloudOpenApiSync` reported `232 paths, 0 drift`.
- [x] Mastery launcher startup verification: `DataCloudHttpServerMasteryTest` passed (`4 passed, 0 failed`) via Gradle module test run.
- [x] Mastery launcher verification rerun: forced fresh execution with `--rerun-tasks` for `DataCloudRouterBuilderIntegrationTest`, `DataCloudMasteryRouteDispatchTest`, and `DataCloudHttpServerMasteryTest` passed with `BUILD SUCCESSFUL`; separate launcher `compileJava` + `checkDataCloudOpenApiSync` run also passed (`232 paths, 0 drift`).
- [x] Launcher regression sweep: full `:products:data-cloud:delivery:launcher:test` module run remained green (`BUILD SUCCESSFUL`, configuration cache reused, no new failures).
- [x] Trace contract completion (phase-1): added `DISPATCH_REQUESTED` and `RELEASE_CHECKED` events, added `MEMORY_RETRIEVAL_STARTED` emission, expanded dispatcher sequence tests, and added CI conformance gate checks in `scripts/check-agent-conformance.sh`.
- [x] Focused validation update (tenant-aware memory wiring): `:products:data-cloud:planes:action:orchestrator:compileJava` + `:products:data-cloud:planes:action:orchestrator:test` passed (`BUILD SUCCESSFUL`).
- [x] Conformance validation update: `bash scripts/check-agent-conformance.sh` now reports mastery conformance gates green (`Mastery conformance: 0`) and no canonical type-alias violations; overall script still fails due pre-existing repository-wide compatibility markers outside this slice.
- [x] Action server pattern endpoint test stabilization: [products/data-cloud/planes/action/server/src/test/java/com/ghatana/aep/server/http/AepHttpServerPatternTest.java](products/data-cloud/planes/action/server/src/test/java/com/ghatana/aep/server/http/AepHttpServerPatternTest.java) now uses startup retries with HTTP health readiness checks to remove free-port race failures that produced intermittent protocol parse errors.
- [x] Pattern endpoint regression validation: `:products:data-cloud:planes:action:server:test --tests 'com.ghatana.aep.server.http.AepHttpServerPatternTest'` passed (`9 passed, 0 failed`).

---

# Executive summary

The repo is much closer to the desired **Generic Adaptive Agent (GAA)** vision than a greenfield implementation. The code already contains many of the correct primitives:

| Capability                 | Current status                                                                                                                                                                                                                                         |
| -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Immutable agent blueprint  | `AgentDefinition` already includes identity, type, determinism, learning level, memory bindings, skill refs, mastery policy refs, evaluation refs, security, and observability contracts.                                                              |
| Governed deployable unit   | `AgentRelease` now carries tenant ID, policy/evaluation/memory/mastery/learning/freshness/version-compatibility contracts, and skill-specific evaluation/mastery policy refs.                                                                          |
| Learning authority model   | `LearningLevel` cleanly defines L0–L5 and makes `MASTERY_STATE` L5-only/offline-governance scoped.                                                                                                                                                     |
| Learning boundary contract | `LearningContract` distinguishes normal agents from governance workflows and blocks normal agents from proposing mastery-state changes.                                                                                                                |
| Mastery lifecycle          | `MasteryState` already models `UNKNOWN → OBSERVED → PRACTICED → COMPETENT → MASTERED → MAINTENANCE_ONLY → OBSOLETE → RETIRED`, plus `QUARANTINED`.                                                                                                     |
| Mastery ledger item        | `MasteryItem` tracks skill/domain/agent/release/state/version scope/applicability/score/procedures/facts/negative knowledge/evidence/evals/failures/freshness/confidence.                                                                              |
| Mastery registry           | `MasteryRegistry` exposes tenant-scoped `findBest`, `query`, `decide`, `save`, `transition`, `findStale`, and `getById`.                                                                                                                               |
| Data Cloud persistence     | `DataCloudMasteryRegistry` persists mastery items/transitions/evidence with tenant isolation and version-aware decisions.                                                                                                                              |
| Runtime dispatch           | `GovernedAgentDispatcher` contains release guard, capability guard, skill requirement, version context resolution, mastery decision, approval/verification checks, mode selection, memory retrieval hook, invariant checks, trace ledger integration.  |
| Memory hierarchy           | Data Cloud runtime `MemoryPlane` supports episodic, semantic, procedural, typed artifacts, cross-tier query, semantic search, working memory, task-state, checkpointing, and stats.                                                                    |
| Promotion                  | `DefaultPromotionEngine`, `PromotionEvidenceMapper`, `DefaultPromotionPolicy`, and `DefaultMasteryTransitionPolicy` implement evaluation-backed skill promotion.                                                                                       |
| Obsolescence               | `DefaultObsolescenceDetector` and `DefaultObsolescenceTransitionService` exist and route stale/version/API/runtime/failure/security/doc contradiction signals into mastery transitions.                                                                |
| API/admin surface          | `MasteryController` exposes mastery query, stale/obsolete views, mode explanation, learning delta listing/evaluation/promotion, and deprecated direct mutation endpoints.                                                                              |

The main implementation goal is no longer “create the GAA architecture.” It is:

> **Stabilize, wire, harden, test, and productionize the existing GAA/mastery system end to end.**

The biggest issue I found: many core contracts exist, but some runtime wiring still does not connect them fully. For example, `AepOrchestrationModule` provides a fail-closed `MasteryRegistry`, default mode selection, no-op memory retriever, learning delta repository, promotion engine, and obsolescence detector, but its `governedAgentDispatcher(...)` provider only passes the `MasteryAwareModeSelector` into the dispatcher constructor and does **not** pass the release repository, mastery registry, version context resolver, task classifier, capability manifest, or memory retriever into the full dispatcher path. 

That should be the first fix.

---

# Target architecture

The final GAA implementation should behave like this:

```text
AgentDefinition
→ AgentRelease
→ AgentInstance/runtime context
→ Release guard
→ VersionContextResolver
→ TaskClassifier
→ MasteryRegistry.decide()
→ MasteryAwareModeSelector
→ MasteryAwareMemoryRetriever
→ GovernedAgentDispatcher
→ Trace capture
→ MemoryPlane capture
→ LearningDelta creation
→ LearningDelta evaluation
→ PromotionEngine
→ MasteryRegistry.transition()
→ Obsolescence detection
→ Mastery refresh / maintenance / quarantine / retirement
```

Core invariant:

> **Agents may observe freely, propose cautiously, and act only within governed mastery.**

This means:

```text
Online agent execution may create traces and learning deltas.
Only promotion/governance workflows may mutate mastery state.
Only evaluated/promoted mastery may affect autonomous behavior.
Only version-compatible, fresh, non-obsolete knowledge may be retrieved for execution.
```

---

# Critical findings

## Status note (2026-05-15)

This section is retained as historical scan context. The P0/P1 items listed here have been implemented and verified in the completed slices above (governed dispatcher wiring, memory-plane migration phase-1, promotion/obsolescence hardening, mastery API/router/OpenAPI wiring, and mastery route startup verification).

## P0 — Dispatcher wiring is incomplete

`GovernedAgentDispatcher` supports the right runtime chain: release guard, mastery check, version context, approval/verification checks, mode selection, memory retrieval, invariant monitoring, and trace recording. 

But `AepOrchestrationModule` wires the dispatcher with only:

```java
new GovernedAgentDispatcher(
    catalogAgentDispatcher,
    invariantMonitor,
    traceLedger,
    masteryAwareModeSelector
)
```

That constructor does **not** pass the release repository, mastery registry, version resolver, task classifier, capability manifest, or memory retriever. 

### Impact

The code has a strong governed dispatcher, but AEP may run with only partial governance enabled. Release guard, direct mastery decision path, version context resolver, and memory retrieval may not execute unless separately wired elsewhere.

### Fix first

Wire the full constructor and provide real bindings.

---

## P0 — Memory retrieval still uses a bridge, not the canonical Data Cloud runtime `MemoryPlane`

`MasteryAwareMemoryRetriever` currently depends on `MemoryProjectionBridge`, which projects legacy framework memory snapshots into memory items. 

But the richer canonical memory API is the Data Cloud runtime `MemoryPlane`, which directly supports typed episodic, semantic, procedural, artifact, working, task-state, semantic search, and cross-tier query operations. 

### Impact

The GAA may develop two parallel memory paths:

1. legacy projection memory path;
2. richer Data Cloud runtime memory path.

That creates split-brain retrieval, inconsistent observability, and duplicated logic.

### Fix

Make Data Cloud runtime `MemoryPlane` canonical. Keep `MemoryProjectionBridge` only as a migration adapter.

---

## P0 — Fail-closed registry is safe but not useful for real runtime

`AepOrchestrationModule.masteryRegistry()` returns a fail-closed no-op registry that blocks execution by default. This is safer than permissive defaults. 

### Impact

Safe, but unless replaced by `DataCloudMasteryRegistry`, real agents cannot demonstrate learning/mastery behavior in AEP.

### Fix

Provide a production Data Cloud registry binding and keep the fail-closed registry only for tests/local degraded mode.

---

## P0 — Direct mutation endpoints still exist

`MasteryController.saveMastery()` and `MasteryController.transition()` are marked deprecated and explain that they bypass the LearningDelta + PromotionEngine or ObsolescenceTransitionService workflow. 

### Impact

Even if deprecated, if routes remain exposed, a caller with broad `mastery:write` or `mastery:transition` access can bypass the desired promotion workflow.

### Fix

Disable by default, move behind break-glass/admin-only approval, or remove from public route registration.

---

## P1 — Learning delta repository relies on volatile reverse indexes

`DataCloudLearningDeltaRepository` stores learning deltas durably, but `findById` and `updateState` depend on in-memory maps from `deltaId → tenantId/entityId`; the comments state the reverse index is volatile and rebuilt by `save()` calls. 

### Impact

After process restart, `findById(deltaId)` may return empty even though the delta exists in Data Cloud.

### Fix

Use a persistent query path by `deltaId` and tenant ID, or require tenant ID for all delta lookup/update APIs.

---

## P1 — Promotion-to-mastered may be overly broad

`PromotionEvidenceMapper.deriveEvalResult(...)` returns true for a category if all tests passed and there is at least one case result, even when category-specific metadata is absent. 

`DefaultMasteryTransitionPolicy` requires explicit `regression_passed`, `safety_passed`, `recovery_passed`, and `compatibility_passed` for `COMPETENT → MASTERED`. 

### Impact

A non-category-specific passing evaluation could accidentally generate all four “passed” evidence values and allow `MASTERED`.

### Fix

Require explicit category tests or metadata for regression, safety, recovery, and compatibility.

---

## P1 — Obsolescence evidence keys may not satisfy transition policy

`DefaultObsolescenceTransitionService` copies event metadata and adds generic `evidence_0`, `evidence_1`, etc. 

`DefaultMasteryTransitionPolicy` requires keys like `new_active_version_id`, `end_of_life`, `replaced_by_newer`, `safety_violation`, `unsafe_behavior`, `contradiction`, `repeated_failures`, `security_advisory`, `api_break`, and `no_active_use_case`. 

### Impact

Obsolescence detection may correctly detect a problem but fail to transition mastery state because evidence keys do not match policy requirements.

### Fix

Add `ObsolescenceEvidenceMapper` that converts each `ObsolescenceReason` into transition-policy-compatible evidence.

---

## P1 — Release contract is strong, but runtime enforcement must be end-to-end

`AgentRelease` now requires tenant ID, evaluation pack, memory contract, and response-serving releases require mastery policy pack and learning contract. It also carries skill-specific evaluation and mastery policy maps and includes them in the release digest. 

### Impact

Good contract, but it must be enforced at registration, release transition, dispatch, and promotion time.

### Fix

All response-serving dispatch must validate:

```text
release.isResponseServing()
tenant matches
policy/eval/memory/mastery/learning contracts exist
skill-specific eval pack exists for skillId
mastery policy pack exists for skillId
release digest matches registered spec digest
```

---

# Detailed implementation plan

## Phase 0 — Establish the target commit baseline

### Goal

Make the current target commit compile and produce a reliable baseline before adding features.

### Files / modules

```text
platform/java/agent-core
products/data-cloud/extensions/agent-registry
products/data-cloud/planes/action/agent-runtime
products/data-cloud/planes/action/orchestrator
products/data-cloud/delivery/api
.github/workflows/*
```

### Tasks

1. Run compile/test for the mastery-related modules.
2. Add a focused `agent-mastery-smoke` Gradle task that compiles:

   * learning contracts;
   * mastery registry interfaces;
   * promotion engine;
   * obsolescence detector;
   * governed dispatcher;
   * Data Cloud registry implementation;
   * Mastery API.
3. Add dependency boundary checks:

   * `agent-core` must not depend on Data Cloud.
   * Data Cloud may depend on `agent-core`.
   * product modules must not bypass public SPI/contracts.
4. Create a small integration test:

   * create `MasteryItem`;
   * save through `DataCloudMasteryRegistry`;
   * query by tenant/skill;
   * call `decide`;
   * verify expected decision.
5. Ensure deprecated APIs are still compiled but flagged.

### Acceptance criteria

* All mastery modules compile.
* No circular dependencies.
* Baseline tests show known gaps explicitly.
* No product code imports Data Cloud internals except Data Cloud modules.

---

## Phase 1 — Fix AEP runtime wiring

### Goal

Ensure AEP actually uses release guard, mastery registry, version context, task classifier, mode selection, memory retrieval, invariants, and trace ledger.

### File

```text
products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/aep/di/AepOrchestrationModule.java
```

### Current issue

The module provides several governance components but constructs `GovernedAgentDispatcher` using the simplified constructor with only `MasteryAwareModeSelector`, so several important dependencies are not passed into the dispatcher. 

### Tasks

1. Add provider for `AgentReleaseRepository`.
2. Add provider for real `DataCloudMasteryRegistry`.
3. Add provider for `VersionContextResolver`.
4. Add provider for `AgentCapabilityManifest`.
5. Replace no-op `MemoryRetriever` with a `MasteryAwareMemoryRetriever` adapter.
6. Wire full dispatcher constructor:

```java
return new GovernedAgentDispatcher(
    catalogAgentDispatcher,
    invariantMonitor,
    traceLedger,
    releaseRepository,
    agentRunTracer,
    capabilityManifest,
    masteryRegistry,
    versionContextResolver,
    taskClassifier,
    masteryAwareModeSelector,
    memoryRetriever
);
```

7. Keep fail-closed fallback registry only under explicit `local/dev/no-registry` profile.
8. Add startup validation:

   * if environment is production and no real `MasteryRegistry` exists, fail startup;
   * if production and no `AgentReleaseRepository` exists, fail startup;
   * if production and no `TraceLedger` exists, fail startup.

### Tests

Add tests under:

```text
products/data-cloud/planes/action/orchestrator/src/test/java/...
```

Test cases:

1. Missing mastery registry in production fails startup.
2. Dispatcher denies when release is not response-serving.
3. Dispatcher denies mastery-bound agent when no `skillId` can be derived.
4. Dispatcher passes real version context to mastery query.
5. Dispatcher invokes memory retrieval before delegate dispatch.
6. Dispatcher records trace events for release, mastery, mode, memory, and denial.

### Acceptance criteria

* AEP dispatch uses the full governed path.
* No response-serving dispatch bypasses release/mastery/mode gates.
* Missing dependencies fail closed.

---

## Phase 2 — Canonicalize the memory plane

### Goal

Use one canonical memory API for GAA: the Data Cloud runtime `MemoryPlane`.

### Files

```text
products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/store/MemoryPlane.java
platform/java/agent-core/src/main/java/com/ghatana/agent/memory/retrieval/MasteryAwareMemoryRetriever.java
platform/java/agent-core/src/main/java/com/ghatana/agent/framework/memory/MemoryProjectionBridge.java
```

### Current state

Data Cloud runtime `MemoryPlane` is the richer SPI and supports all target memory tiers. 
`MasteryAwareMemoryRetriever` currently relies on `MemoryProjectionBridge`. 

### Tasks

1. Introduce an agent-core neutral memory SPI if needed:

```java
interface AgentMemoryQueryPort {
    Promise<List<MemoryItem>> query(MemoryQuery query);
    Promise<List<ScoredMemoryItem>> searchSemantic(...);
}
```

2. Make Data Cloud `MemoryPlane` implement or adapt to that port.
3. Change `MasteryAwareMemoryRetriever` to use the canonical memory query port.
4. Keep `MemoryProjectionBridge` as:

   * deprecated;
   * compatibility-only;
   * not the default runtime path.
5. Ensure retrieval pulls from:

   * negative knowledge;
   * procedures;
   * semantic facts;
   * episodes;
   * task-state;
   * working memory only when explicitly allowed.
6. Ensure retrieval filters by:

   * tenant;
   * agent ID;
   * skill ID;
   * version context;
   * mastery state;
   * freshness;
   * risk level;
   * data class / privacy policy.
7. Ensure retrieval never returns:

   * `OBSOLETE`;
   * `RETIRED`;
   * `QUARANTINED`;
   * `UNKNOWN`, unless explicit fast-learning mode asks for candidate memory.

### Tests

Add tests for `MasteryAwareMemoryRetriever`:

1. Negative knowledge ranks before positive procedure.
2. `MAINTENANCE_ONLY` appears only for maintenance version context.
3. Obsolete version is excluded.
4. Unknown mastery is excluded by default.
5. Fresh mastered item outranks stale mastered item.
6. Tenant A cannot retrieve tenant B memory.
7. High-risk task excludes unverified memory.

### Acceptance criteria

* No split-brain memory paths for GAA.
* Retrieval is version-aware, mastery-aware, freshness-aware, and tenant-scoped.
* Deprecated projection bridge has no new call sites.

---

## Phase 3 — Harden Mastery Registry persistence and decision logic

### Goal

Make the Mastery Registry a durable, authoritative, tenant-isolated source of execution permission.

### Files

```text
platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/MasteryRegistry.java
platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/MasteryItem.java
products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/mastery/DataCloudMasteryRegistry.java
products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/mastery/MasteryItemMapper.java
```

### Current state

`MasteryRegistry` already defines the correct operations, including tenant-scoped `findBest`, `decide`, `transition`, `findStale`, and `getById`. 
`DataCloudMasteryRegistry` implements tenant-scoped persistence, `findBest`, `decide`, transitions, and stale scans. 

### Tasks

1. Replace all deprecated `findBySkill(skillId, env)` call sites with `findBest(MasteryQuery)`.
2. Replace all deprecated `findStale(Instant)` call sites with tenant-scoped `findStale(tenantId, now)`.
3. Add persistent indexes:

   * `(tenantId, masteryId)`
   * `(tenantId, skillId)`
   * `(tenantId, agentId)`
   * `(tenantId, agentReleaseId)`
   * `(tenantId, domain)`
   * `(tenantId, state)`
   * `(tenantId, staleAfter)`
4. Fix `validateMasteryItem()` to validate `item.tenantId()` directly in addition to `item.applicability().tenantId()`.
5. Make `findBest()` and `decide()` use exactly one shared ranking function:

   * active version match;
   * mastered/competent/practiced/observed ranking;
   * execution score;
   * freshness;
   * evidence strength;
   * safety score.
6. Add explicit `VersionApplicability` into `MasteryDecision`.
7. Add `decisionReasonCode`, not only reason string:

   * `NO_MASTERY`
   * `STALE`
   * `VERSION_OBSOLETE`
   * `VERSION_MAINTENANCE_MISMATCH`
   * `TERMINAL_STATE`
   * `REQUIRES_APPROVAL`
   * `REQUIRES_VERIFICATION`
   * `ALLOWED_MASTERED`
8. Add optimistic locking tests for concurrent `save()` and `transition()`.
9. Ensure transition update preserves:

   * previous state history;
   * existing score vector;
   * existing version scope;
   * procedure/fact/negative/eval refs.

### Tests

1. `MASTERED + ACTIVE + fresh` → allow.
2. `COMPETENT + ACTIVE` → requires verification.
3. `PRACTICED + ACTIVE` → requires approval.
4. `OBSERVED` → requires approval / verification-first.
5. `MAINTENANCE_ONLY + maintenance version` → maintenance mode.
6. `MAINTENANCE_ONLY + active version` → block.
7. `MASTERED + maintenance version` → block or require migration depending policy.
8. `OBSOLETE`, `RETIRED`, `QUARANTINED` → block.
9. stale item → block and request refresh.
10. no item → block / fast-learning gated, depending runtime policy.

### Acceptance criteria

* `decide()` becomes the single source of truth for execution permission.
* All decisions are tenant-scoped and version-aware.
* No deprecated registry API is used in runtime paths.

---

## Phase 4 — Harden learning delta lifecycle

### Goal

Ensure agents learn only by creating governed, evaluable `LearningDelta` artifacts.

### Files

```text
platform/java/agent-core/src/main/java/com/ghatana/agent/learning/LearningDelta.java
platform/java/agent-core/src/main/java/com/ghatana/agent/learning/LearningContract.java
platform/java/agent-core/src/main/java/com/ghatana/agent/learning/LearningLevel.java
platform/java/agent-core/src/main/java/com/ghatana/agent/learning/LearningTarget.java
products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/learning/delta/DataCloudLearningDeltaRepository.java
```

### Current state

`LearningDelta` already includes target, state, agent/release/skill/tenant, procedure/fact/negative IDs, content digest, proposed content, evidence refs, evaluation refs, source episode IDs, rollback ref, confidence before/after, human review flag, approval proof ref, and version/environment fingerprints. 

`LearningContract` correctly blocks normal agents from mastery-state mutation and allows governance workflows at L5. 

### Tasks

1. Add `tenantId` to all `LearningDeltaRepository` lookup/update methods or add tenant-scoped variants:

   * `findById(tenantId, deltaId)`
   * `updateState(tenantId, deltaId, state)`
   * `appendEvaluationResult(tenantId, deltaId, ...)`
   * `appendPromotionResult(tenantId, deltaId, ...)`
2. Remove reliance on in-memory reverse index as the only lookup path.
3. Persist `LearningDeltaEvent` append-only history:

   * proposed;
   * evaluated;
   * approved;
   * promoted;
   * rejected;
   * quarantined;
   * superseded.
4. Validate `LearningContract.permits(delta.target())` before saving a delta.
5. For `L3+`, require:

   * provenance;
   * source episodes or evidence refs;
   * rollback ref for behavioral changes;
   * evaluation refs before promotion.
6. For high-risk targets, require `approvalProofRef`:

   * prompt template;
   * planner policy;
   * routing policy;
   * model adapter;
   * mastery state.
7. Add `versionContextDigest` as required for procedural/policy deltas.
8. Add `proposedContent` schema validation by target type.

### Tests

1. Normal agent cannot create `MASTERY_STATE` delta.
2. Governance workflow can create `MASTERY_STATE` delta only with L5 contract.
3. L3 procedural skill delta without provenance is rejected.
4. Planner policy delta without approval proof cannot promote.
5. Delta lookup works after repository restart.
6. Cross-tenant lookup is impossible.

### Acceptance criteria

* Learning deltas are durable and tenant-scoped.
* No agent can directly mutate mastery.
* Promotion consumes deltas; runtime execution never consumes unpromoted deltas.

---

## Phase 5 — Correct and strengthen promotion

### Goal

Make promotion the only normal path from learned evidence to active mastery.

### Files

```text
platform/java/agent-core/src/main/java/com/ghatana/agent/promotion/DefaultPromotionEngine.java
platform/java/agent-core/src/main/java/com/ghatana/agent/promotion/DefaultPromotionPolicy.java
platform/java/agent-core/src/main/java/com/ghatana/agent/promotion/PromotionEvidenceMapper.java
platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/transition/DefaultMasteryTransitionPolicy.java
platform/java/agent-core/src/main/java/com/ghatana/agent/evaluation/*
```

### Current state

Promotion now evaluates a delta, maps evidence, transitions mastery, reloads the item, updates it with procedure/fact/negative/eval refs, and marks delta promoted. 
Evidence mapping now produces transition-policy-compatible keys for normal transitions. 
Transition policy has explicit gated transitions. 

### Tasks

1. Require explicit category evidence for `COMPETENT → MASTERED`:

   * regression;
   * safety;
   * recovery;
   * compatibility.
2. Update `PromotionEvidenceMapper.deriveEvalResult(...)`:

   * return true only if category-specific metadata exists or category-specific case exists and passes.
3. Add `EvaluationResult.categoryPassed(category)` helper.
4. Change `DefaultPromotionPolicy.passesPlannerPolicyChecks()` to require `approvalProofRef`, not `requiresHumanReview`.

   * The target-state method already checks `approvalProofRef`, but the target-specific check still uses `requiresHumanReview`. 
5. Ensure `PROCEDURAL_SKILL` promotion requires:

   * at least 3 evidence refs or source episodes;
   * rollback ref;
   * procedure ID;
   * regression + safety pass;
   * compatibility for versioned skills.
6. Ensure `NEGATIVE_KNOWLEDGE` promotion requires:

   * failure mode;
   * safety validation;
   * applicable version/context;
   * counterexample/evidence.
7. Ensure `SEMANTIC_FACT` promotion requires:

   * source;
   * consistency check;
   * confidence;
   * provenance.
8. Add promotion idempotency:

   * same delta promoted twice returns same result;
   * no duplicate transitions;
   * no duplicate procedure/eval refs on mastery item.
9. Add “promotion dry-run” API:

   * returns missing evidence;
   * target state;
   * policy decision;
   * required approval/eval categories.

### Tests

1. `UNKNOWN → OBSERVED` succeeds with `trace_id`.
2. Direct `UNKNOWN → MASTERED` fails.
3. `PRACTICED → COMPETENT` requires `procedure_id` and `basic_eval_passed=true`.
4. `COMPETENT → MASTERED` fails if compatibility test missing.
5. `COMPETENT → MASTERED` fails if safety category missing.
6. `PLANNER_POLICY` without approval proof cannot promote.
7. Re-promoting same delta does not duplicate state history.
8. Promotion updates procedure/fact/negative/eval refs on `MasteryItem`.

### Acceptance criteria

* `MASTERED` means truly evaluated, not just “all generic tests passed.”
* Promotion is idempotent, evidence-backed, and auditable.
* Promotion is the sole path to behavior-changing mastery.

---

## Phase 6 — Fix obsolescence-to-transition evidence

### Goal

Make obsolescence detection produce valid mastery transitions.

### Files

```text
platform/java/agent-core/src/main/java/com/ghatana/agent/obsolescence/DefaultObsolescenceDetector.java
platform/java/agent-core/src/main/java/com/ghatana/agent/obsolescence/DefaultObsolescenceTransitionService.java
platform/java/agent-core/src/main/java/com/ghatana/agent/obsolescence/ObsolescenceEvent.java
platform/java/agent-core/src/main/java/com/ghatana/agent/obsolescence/ObsolescenceScanner.java
platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/transition/DefaultMasteryTransitionPolicy.java
```

### Current state

`DefaultObsolescenceDetector` detects staleness, dependency version mismatch, API changes, runtime incompatibility, repeated failures, security vulnerabilities, and documentation contradictions. 

`DefaultObsolescenceTransitionService` now correctly loads the exact item by tenant/mastery ID and uses actual item state/skill/agent/release metadata. 

### Remaining gap

Transition evidence still needs to map reason → policy-required key.

### Tasks

1. Add `ObsolescenceEvidenceMapper`.
2. Map:

| Reason                        | Transition evidence                                           |
| ----------------------------- | ------------------------------------------------------------- |
| `VERSION_MISMATCH`            | `new_active_version_id` or `end_of_life` depending transition |
| `API_CHANGE`                  | `api_break`                                                   |
| `RUNTIME_INCOMPATIBILITY`     | `api_break` or `contradiction`                                |
| `REPEATED_FAILURES`           | `repeated_failures`                                           |
| `SECURITY_VULNERABILITY`      | `security_advisory` or `safety_violation`                     |
| `DOCUMENTATION_CONTRADICTION` | `contradiction`                                               |
| retirement scan               | `no_active_use_case`                                          |

3. Prevent `MASTERED → OBSOLETE` direct transition unless policy explicitly allows it. Current transition policy requires `MASTERED → MAINTENANCE_ONLY → OBSOLETE`. 
4. If detector recommends invalid transition, route through nearest valid transition:

   * `MASTERED + version change` → `MAINTENANCE_ONLY`
   * `MAINTENANCE_ONLY + EOL` → `OBSOLETE`
   * `OBSOLETE + no active use` → `RETIRED`
   * any state + unsafe behavior → `QUARANTINED`
5. Add scheduled tenant-scoped scanner.
6. Add event-triggered scanner for:

   * dependency change;
   * security advisory;
   * API contract change;
   * repeated failed executions;
   * failed eval pack;
   * docs contradiction.

### Tests

1. Stale item creates maintenance transition evidence.
2. Security issue routes to quarantine with `safety_violation`.
3. API break creates valid `api_break` evidence.
4. `MASTERED → OBSOLETE` is not attempted directly.
5. `OBSOLETE → RETIRED` requires `no_active_use_case`.
6. Tenant A scanner never scans tenant B.

### Acceptance criteria

* Obsolescence events always create valid policy-compatible transitions.
* No unsafe direct retirement/deletion path.
* Security failures quarantine immediately.

---

## Phase 7 — Enforce agent definition and release contracts

### Goal

Make `AgentDefinition` and `AgentRelease` authoritative gates, not just metadata.

### Files

```text
platform/java/agent-core/src/main/java/com/ghatana/agent/framework/config/AgentDefinition.java
platform/java/agent-core/src/main/java/com/ghatana/agent/framework/config/AgentDefinitionValidator.java
platform/java/agent-core/src/main/java/com/ghatana/agent/framework/config/materializer/*
platform/java/agent-core/src/main/java/com/ghatana/agent/release/AgentRelease.java
platform/java/agent-core/src/main/java/com/ghatana/agent/release/AgentReleaseBuilder.java
platform/java/agent-core/src/main/java/com/ghatana/agent/release/AgentReleaseRepository.java
```

### Current state

`AgentDefinition` now materializes learning contract, mastery binding, freshness policy, version compatibility policy, release draft, and validates learning/mastery consistency. 

`AgentRelease` requires governance artifacts and includes skill-level evaluation/mastery policy maps in the digest. 

### Tasks

1. Enforce `skillRefs` when mastery binding exists.
2. Enforce `masteryPolicyRefs` and `evaluationRefs` for `L3+`.
3. Enforce `learningLevel != L5` for response-serving agent definitions unless it is a governance workflow.
4. Validate `masteryBindings` schema:

   * registry ID;
   * default skill mapping;
   * freshness policy ID;
   * version compatibility policy ID.
5. Validate every `skillRef` has:

   * evaluation pack ref;
   * mastery policy ref;
   * memory contract ref.
6. Validate `AgentRelease` transition:

   * `DRAFT → VALIDATED` requires static validation;
   * `VALIDATED → SHADOW` requires eval pack present;
   * `SHADOW → CANARY` requires trace/eval pass;
   * `CANARY → ACTIVE` requires no blocking safety/memory/mastery issues.
7. Validate `releaseDigest()` on dispatch against persisted release record.
8. Add release registration tests.

### Acceptance criteria

* A mastery-enabled agent cannot be released without skill policy/eval contracts.
* A response-serving L5 normal agent is invalid.
* Release digest changes when skill-specific policy/eval refs change.

---

## Phase 8 — Harden Mastery API and admin surface

### Goal

Make external mastery operations safe, explainable, and governance-first.

### File

```text
products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/api/controller/MasteryController.java
```

### Current state

`MasteryController` exposes read APIs, stale/obsolete views, mode explanation, learning delta list/evaluate/promote, and deprecated direct save/transition operations. 

### Tasks

1. Remove route registration for deprecated `saveMastery()` and `transition()` in production.
2. Add break-glass endpoint only if needed:

   * requires `mastery:breakglass`;
   * requires approval proof;
   * requires reason;
   * requires evidence bundle;
   * emits trace event.
3. Add `GET /mastery/{id}/decision?versionContext=...`.
4. Add `GET /mastery/{id}/retrieval-preview`.
5. Add `POST /learning-deltas/{id}/dry-run-promotion`.
6. Add `POST /obsolescence-events/{id}/process`.
7. Add better `getModeExplanation()`:

   * include version context;
   * selected mode;
   * mastery state;
   * confidence vector;
   * approval/verification requirement;
   * missing evidence;
   * freshness status;
   * obsolete/maintenance reason.
8. Add pagination and sorting to all list endpoints.
9. Add tenant and role-based tests.

### Acceptance criteria

* No normal API path can directly mutate mastery state.
* Admin can explain “why this mode?” and “why this skill was blocked?”
* Promotion and obsolescence workflows are transparent.

---

## Phase 9 — Observability, audit, and trace ledger

### Goal

Every agent action and learning change must be reconstructable.

### Files

```text
products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcher.java
platform/java/agent-core/src/main/java/com/ghatana/agent/audit/*
products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/aep/di/AepOrchestrationModule.java
```

### Current state

`GovernedAgentDispatcher` appends trace events for denials, version context, mastery decisions, approval checks, verification checks, mode selection, and memory retrieval. 

`AepOrchestrationModule` provides `DataCloudAgentTraceLedger` using Data Cloud event log store. 

### Tasks

1. Standardize trace event sequence:

```text
DISPATCH_REQUESTED
RELEASE_CHECKED
VERSION_CONTEXT_RESOLVED
TASK_CLASSIFIED
MASTERY_DECISION_MADE
APPROVAL_CHECKED
VERIFICATION_CHECKED
MODE_SELECTED
MEMORY_RETRIEVAL_COMPLETED
INVARIANTS_CHECKED
AGENT_DISPATCHED
AGENT_RESULT_CAPTURED
LEARNING_DELTA_PROPOSED
```

2. Add missing trace event types if absent.
3. Ensure every denial includes:

   * reason code;
   * agent ID;
   * release ID;
   * skill ID;
   * tenant ID;
   * version context digest.
4. Ensure every promotion includes:

   * delta ID;
   * evaluation result;
   * transition ID;
   * previous/target mastery state.
5. Ensure every obsolescence transition includes:

   * event ID;
   * detector reason;
   * evidence refs;
   * previous/target state.
6. Add hash-chain verification job.
7. Add OpenTelemetry spans around:

   * mastery decision;
   * memory retrieval;
   * evaluation;
   * promotion;
   * obsolescence scan.

### Acceptance criteria

* A production incident can be reconstructed from trace ledger.
* Every learning behavior has provenance.
* Every mastery transition is auditable.

---

## Phase 10 — Testing and CI gates

### Goal

Make regressions impossible to hide.

### Files / locations

```text
platform/java/agent-core/src/test/java/com/ghatana/agent/mastery/*
platform/java/agent-core/src/test/java/com/ghatana/agent/promotion/*
platform/java/agent-core/src/test/java/com/ghatana/agent/learning/*
platform/java/agent-core/src/test/java/com/ghatana/agent/obsolescence/*
products/data-cloud/extensions/agent-registry/src/test/java/com/ghatana/datacloud/agent/mastery/*
products/data-cloud/planes/action/agent-runtime/src/test/java/com/ghatana/agent/runtime/safety/*
products/data-cloud/delivery/api/src/test/java/com/ghatana/datacloud/api/controller/*
.github/workflows/agent-mastery-conformance.yml
```

### Required test suites

1. **Learning authority tests**

   * L0–L5 target permissions.
   * Normal agents blocked from `MASTERY_STATE`.
   * Governance workflow allowed only at L5.

2. **Mastery lifecycle tests**

   * every valid transition;
   * every invalid transition;
   * evidence requirements;
   * quarantine exit requirements.

3. **Version-aware decision tests**

   * active;
   * maintenance;
   * obsolete;
   * unknown;
   * stale.

4. **Mode-selection tests**

   * mastered active → deterministic autonomous;
   * competent active → supervised bounded reasoning;
   * practiced active → human-gated fast-learning;
   * maintenance-only → maintenance mode;
   * obsolete/quarantined/retired → blocked.

5. **Memory retrieval tests**

   * negative knowledge first;
   * obsolete excluded;
   * maintenance-only only in legacy context;
   * tenant isolation;
   * freshness filter.

6. **Promotion tests**

   * procedural skill promotion;
   * semantic fact promotion;
   * negative knowledge promotion;
   * planner/prompt approval proof;
   * missing category eval blocks master promotion.

7. **Obsolescence tests**

   * security issue → quarantine;
   * version change → maintenance;
   * EOL → obsolete;
   * no active use → retired.

8. **Dispatcher integration tests**

   * release blocked;
   * no skill ID blocked for mastery-bound release;
   * missing approval blocked;
   * missing verification blocked;
   * memory retrieval invoked;
   * trace events emitted in order.

9. **API tests**

   * read access;
   * promote access;
   * direct mutation disabled;
   * tenant isolation.

### CI gates

Add a CI job that fails if:

* deprecated direct mutation routes are registered;
* `findBySkill(skillId, env)` is used outside tests/adapters;
* `findStale(Instant)` is used outside compatibility tests;
* response-serving release lacks learning/mastery/eval/memory contracts;
* L5 normal agent is response-serving;
* `MemoryProjectionBridge` is used in new runtime code.

---

# Recommended rollout plan

## Milestone 1 — Safe wiring

Deliverables:

* full `GovernedAgentDispatcher` wiring;
* real `DataCloudMasteryRegistry` binding;
* release repository binding;
* version resolver binding;
* memory retriever binding;
* no production no-op registry.

Outcome:

```text
AEP dispatch is actually governed end to end.
```

## Milestone 2 — Durable learning deltas

Deliverables:

* tenant-scoped delta repository;
* persistent delta lookup;
* append-only delta events;
* no volatile reverse-index dependency;
* learning contract enforcement.

Outcome:

```text
Agents can propose learning safely and durably.
```

## Milestone 3 — Promotion and mastery correctness

Deliverables:

* explicit category evaluation evidence;
* idempotent promotion;
* transition-policy-compatible evidence;
* real approval proof checks;
* mastery item updates after transition.

Outcome:

```text
Mastery state becomes evidence-backed and trustworthy.
```

## Milestone 4 — Canonical memory retrieval

Deliverables:

* canonical Data Cloud memory query port;
* `MasteryAwareMemoryRetriever` uses canonical memory;
* version/freshness/tenant/mastery filters;
* negative knowledge priority.

Outcome:

```text
Runtime context is populated with safe, applicable memory.
```

## Milestone 5 — Obsolescence and maintenance mode

Deliverables:

* obsolescence evidence mapper;
* valid transition routing;
* scheduled and event-triggered scans;
* maintenance/obsolete/retired lifecycle tests.

Outcome:

```text
Agents stop using stale mastery and preserve old knowledge only for matching legacy contexts.
```

## Milestone 6 — API, observability, and CI

Deliverables:

* safe Mastery API;
* direct mutation disabled;
* mode explanation endpoints;
* trace sequence checks;
* CI conformance gates.

Outcome:

```text
The GAA system is operable, explainable, testable, and safe to extend.
```

---

# Final target behavior

When complete, the GAA should follow this strict runtime rule:

```text
If skill is MASTERED + version ACTIVE + fresh + low/medium risk:
    deterministic execution allowed.

If skill is COMPETENT:
    execute with verification.

If skill is PRACTICED or OBSERVED:
    human-gated fast-learning / verification-first.

If skill is MAINTENANCE_ONLY:
    retrieve only for matching legacy version context.

If skill is OBSOLETE / RETIRED / QUARANTINED:
    block execution and explain why.

If no mastery exists:
    fast-learning only, gated, with no autonomous side effects.

If learned evidence emerges:
    create LearningDelta.
    evaluate.
    promote only through governance.
    update MasteryRegistry only after policy-valid transition.
```

The repo already contains the main pieces. The next work is to make those pieces **one coherent production path**: no no-op bypasses, no split memory plane, no direct mastery mutation, no promotion without explicit evidence, and no execution outside version-aware governed mastery.

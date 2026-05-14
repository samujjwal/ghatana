## Static scan scope

I scanned the `samujjwal/ghatana` repo at target commit `3d6a3bdcccbcf461ffda6b4af2145295cd460e3d`. The commit itself is a merge commit with product/kernel lifecycle registry changes, but the agent platform code at this snapshot already contains substantial GAA/mastery/self-learning infrastructure. 

I focused on:

* `platform/java/agent-core`
* agent definition/release/learning/mastery/mode/obsolescence/promotion packages
* Data Cloud agent registry implementation
* AEP orchestration/dispatcher wiring
* memory plane and retrieval integration

I did **not** run the build/tests locally; this is a static code and architecture scan based on repository files.

---

# Executive summary

The repo is **much closer to the GAA goal than expected**. It already contains many of the concepts we discussed:

* immutable `AgentDefinition`
* typed `LearningContract`
* `LearningDelta`
* mastery lifecycle
* version-aware `VersionScope`
* mastery scoring vector
* mastery-aware mode selection
* governed dispatcher
* Data Cloud-backed mastery registry
* promotion engine
* obsolescence detector/scanner/router
* memory plane and mastery-aware retrieval

The main work now is not “invent the architecture.” The main work is to make the current architecture **correct, wired, durable, testable, and safe end-to-end**.

The most important problems I found:

1. **AEP orchestration is wired with no-op/unsafe defaults**, including a no-op mastery registry and no-op mode policy that can allow autonomous execution by default. 
2. **`GovernedAgentDispatcher` is constructed with `null` invariant monitor and trace ledger in `AepOrchestrationModule`, while the dispatcher constructor requires non-null values.** This can break DI/runtime startup.  
3. **`DataCloudMasteryRegistry.decide()` ignores version applicability when choosing the best mastery item**, even though `findBest()` has version-aware ranking. 
4. **`MasteryAwareModeSelector` accepts version context, but `GovernedAgentDispatcher` currently calls it with `VersionContext.empty()` instead of the resolved version context.** This weakens version-aware mode selection.  
5. **`ObsolescenceRouter` calls deprecated `findBySkill(event.masteryId(), null)`, but `DataCloudMasteryRegistry.findBySkill()` dereferences `env.tenantId()`.** This is a likely runtime NPE and should be replaced with tenant-scoped `getById()` or query.  
6. **`DefaultPromotionEngine` builds transition evidence with generic keys like `evidence_0`, but `DefaultMasteryTransitionPolicy` requires specific keys such as `trace_id`, `procedure_id`, `basic_eval_passed`, `regression_passed`, etc.** Promotion can fail even when a delta is valid.  
7. **`DefaultPromotionEngine` has a method to update mastery items with delta artifacts, eval refs, score, and version scope, but that method is not called in the promotion path.** So promoted deltas may transition state without updating the mastery item’s actual knowledge/evidence linkage. 
8. **Several components are policy-shaped but not fully backed by durable event/evaluation workflows yet.** `LearningDelta`, `PromotionResult`, `DefaultLearningDeltaEvaluator`, and `DefaultPromotionEngine` exist, but they need a complete repository, API, and runtime pipeline.    

---

# Current architecture status

## 1. Agent definition is strong

`AgentDefinition` is already a solid foundation. It is immutable, versioned, and includes identity, type/behavior, determinism, state mutability, failure mode, criticality, autonomy, learning level, input/output contracts, tools, capabilities, runtime limits, memory bindings, policy refs, evaluation refs, observability/security contracts, mastery bindings, skill refs, and mastery policy refs. It also materializes `LearningContract`, mastery bindings, freshness policy, version compatibility policy, and validates learning/mastery consistency. 

This is aligned with the GAA goal. It just needs stricter schema/materializer/API enforcement.

## 2. Learning authority is now well-modeled

`LearningLevel` has canonical semantics:

* `L0`: no learning
* `L1`: episodic memory
* `L2`: semantic facts, retrieval policy, confidence/routing
* `L3`: procedural skills and negative knowledge
* `L4`: prompt and planner policies
* `L5`: offline governance/model adapter/mastery-state workflows

It also explicitly prevents sub-`L5` agents from directly learning `MASTERY_STATE`, and marks `L5` as offline-only and non-response-serving. 

`LearningContract` reinforces this by requiring provenance for `L2+`, promotion for `L3+`, and allowing `MASTERY_STATE` only for `L5` governance workflows. 

This is the right safety boundary.

## 3. Mastery model exists and is rich

`MasteryState` already models the lifecycle we wanted:

```text
UNKNOWN
→ OBSERVED
→ PRACTICED
→ COMPETENT
→ MASTERED
→ MAINTENANCE_ONLY
→ OBSOLETE
→ RETIRED
→ QUARANTINED
```

It also separates normal self-learning from mastery-state governance. 

`MasteryItem` tracks tenant, skill, domain, agent, release, state, version scope, applicability, score, procedure IDs, semantic fact IDs, negative knowledge IDs, evidence refs, evaluation refs, known failure modes, history, freshness, labels, and confidence. 

`MasteryScore` is multi-dimensional: correctness, freshness, applicability, safety, transferability, evidence strength, and regression stability, with execution score as a product of key dimensions. 

This is excellent and should become the canonical mastery ledger.

## 4. Version-aware mastery exists but needs stronger runtime use

`VersionScope` already partitions supported versions into active, maintenance, and obsolete constraints, and can classify a `VersionContext` as `ACTIVE`, `MAINTENANCE`, `OBSOLETE`, or `UNKNOWN`. 

`DefaultModeSelectionPolicy` correctly maps mastery/version state to execution strategy:

* `MASTERED + ACTIVE` → deterministic autonomous
* `COMPETENT + ACTIVE` → bounded probabilistic supervised
* `PRACTICED + ACTIVE` → exploratory fast-learning human-gated
* `OBSERVED` → verification-first human-gated
* `MAINTENANCE_ONLY` → maintenance-only human-gated
* obsolete/terminal/stale → blocked

This is almost exactly the lifecycle we want. 

The issue is runtime integration: the dispatcher/mode selector path currently does not consistently pass the resolved version context through the mastery decision and mode selection path.  

## 5. Memory integration exists but is incomplete

`MemoryPlane` is multi-tier and supports episodic memory, semantic facts, procedural memory, typed artifacts, cross-tier query, semantic search, working memory, task-state memory, checkpointing, and stats. 

`MasteryAwareMemoryRetriever` filters and orders memory by mastery state, version applicability, and memory type, prioritizing negative knowledge, active mastered skills, semantic facts, episodes, and maintenance-only skills. It excludes obsolete, retired, and quarantined items. 

However, it appears to mostly operate over memory items already embedded in a `MemoryQuery`, rather than performing a full end-to-end `MemoryPlane` query + mastery filter + ranked retrieval. It also currently allows `MAINTENANCE_ONLY` with lower priority, but does not strictly require matching legacy context in the filter despite comments saying it should. 

## 6. Promotion and obsolescence exist but need hardening

`LearningDelta` is a good artifact for proposed learning changes. It tracks type, target, state, agent, release, skill, tenant, procedure/fact/negative IDs, content digest, proposed content, evidence, evaluations, source episodes, rollback ref, confidence before/after, human review, proposer, timestamps, labels, and rejection reason. 

`DefaultLearningDeltaEvaluator` validates deltas by target, evidence, confidence, rollback refs for execution targets, and governance rules for mastery-state deltas. 

`DefaultPromotionEngine` exists, but it has correctness gaps in evidence mapping and mastery item update sequencing. 

`DefaultObsolescenceDetector`, `ObsolescenceScanner`, and `ObsolescenceRouter` exist and cover version mismatch, API change, runtime incompatibility, repeated failures, security vulnerabilities, documentation contradictions, scheduled scans, event-triggered scans, and routing to mastery transitions.   

But `ObsolescenceRouter` currently has a tenant lookup bug and maps some severe reasons directly to `RETIRED`, which may bypass the policy path requiring `OBSOLETE → RETIRED` with `no_active_use_case` evidence.  

---

# Target architecture to implement

The target should be:

```text
AgentDefinition
  → AgentRelease
  → AgentInstanceConfig
  → GovernedAgentDispatcher
  → VersionContextResolver
  → TaskClassifier
  → MasteryRegistry.decide()
  → MasteryAwareModeSelector
  → MasteryAwareMemoryRetriever
  → Agent execution
  → TraceLedger + MemoryPlane capture
  → LearningDelta creation
  → LearningDeltaEvaluator
  → EvaluationPack execution
  → PromotionEngine
  → MasteryRegistry.transition()
  → ObsolescenceScanner/Router
  → Release/mode/retrieval behavior updates
```

Core invariant:

> Agents may capture evidence online, but they must not promote behavioral change or mastery state without provenance, evaluation, policy, and promotion.

---

# Detailed implementation plan

## Phase 0 — Fix immediate runtime blockers

### 0.1 Fix `AepOrchestrationModule.governedAgentDispatcher`

**File**

`products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/aep/di/AepOrchestrationModule.java`

**Problem**

The module constructs `GovernedAgentDispatcher` with `null` `InvariantMonitor` and `TraceLedger`, but the dispatcher constructor requires non-null values.  

**Change**

Provide real or safe no-op implementations:

```java
@Provides
InvariantMonitor invariantMonitor() {
    return ctx -> List.of();
}

@Provides
AgentTraceLedger agentTraceLedger() {
    return new NoopAgentTraceLedger(); // implement if missing
}
```

Then wire:

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

**Acceptance criteria**

* AEP DI module starts without NPE.
* Dispatcher always has trace ledger.
* Dispatcher always emits denial/allow events.
* Tests cover missing/null governance dependencies.

---

### 0.2 Remove unsafe no-op autonomous defaults

**File**

`products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/aep/di/AepOrchestrationModule.java`

**Problem**

The current no-op `MasteryRegistry.decide()` allows execution with `UNKNOWN`, and the no-op `ModeSelectionPolicy` returns autonomous deterministic execution by default. 

This violates the intended GAA safety model.

**Change**

Replace no-op mode policy with `DefaultModeSelectionPolicy`.

Replace no-op mastery registry with one of:

1. real `DataCloudMasteryRegistry`, or
2. fail-closed no-op registry:

```java
return MasteryDecision.block(
    "unknown",
    skillId,
    MasteryState.UNKNOWN,
    MasteryScore.zero(),
    VersionScope.empty(),
    "No mastery registry configured"
);
```

**Acceptance criteria**

* Missing registry does **not** produce autonomous execution.
* Unknown skill enters `EXPLORATORY_FAST_LEARNING + HUMAN_GATED`, not autonomous deterministic execution.
* Tests prove unknown mastery cannot execute without approval.

---

## Phase 1 — Make version-aware dispatch correct

### 1.1 Pass resolved `VersionContext` into mastery decision and mode selection

**Files**

* `products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcher.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/runtime/mode/MasteryAwareModeSelector.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/MasteryQuery.java`

**Problem**

The dispatcher resolves version context, but later mode selection uses `VersionContext.empty()` instead of the resolved context.  

**Change**

In `GovernedAgentDispatcher`, after resolving `versionContext`, pass it to `doModeSelectionAndDispatch`.

Replace:

```java
VersionContext.empty()
```

with:

```java
versionCtx
```

Also ensure `MasteryQuery` includes formatted version context before calling `masteryRegistry.decide()`.

**Acceptance criteria**

* A dispatch for `react-router@7` does not select a `react-router@6` maintenance skill.
* A dispatch for a legacy repo can select `MAINTENANCE_ONLY` only when version scope matches.
* `MODE_SELECTED` trace includes real version context.

---

### 1.2 Make `DataCloudMasteryRegistry.decide()` version-aware

**File**

`products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/mastery/DataCloudMasteryRegistry.java`

**Problem**

`findBest()` ranks by version applicability, but `decide()` filters by skill/agent and picks highest execution score without applying version context classification. 

**Change**

Refactor `decide()` to reuse `findBest(query)`.

Then compute decision using:

* version applicability
* freshness
* terminal state
* state
* execution score
* human approval requirement
* verification requirement

**Acceptance criteria**

* `decide()` and `findBest()` return consistent best item.
* Obsolete version is blocked.
* Maintenance version produces maintenance decision only for matching legacy scope.
* Unknown version is verification-first/human-gated unless explicit policy allows.

---

### 1.3 Fix maintenance-only strictness in memory retrieval

**File**

`platform/java/agent-core/src/main/java/com/ghatana/agent/memory/retrieval/MasteryAwareMemoryRetriever.java`

**Problem**

`MAINTENANCE_ONLY` items are currently allowed with lower priority even though comments say they should only be allowed if version context matches legacy scope. 

**Change**

Update filter:

```java
case MAINTENANCE_ONLY -> {
    VersionApplicability applicability = masteryItem.versionScope().classify(versionContext);
    yield applicability == VersionApplicability.MAINTENANCE;
}
```

Also block `UNKNOWN` memory unless explicitly requested for exploration mode.

**Acceptance criteria**

* Maintenance-only memory is not retrieved for greenfield/new work.
* Obsolete/retired/quarantined memory is never retrieved.
* Negative knowledge for the active version is always retrieved before positive procedure memory.

---

## Phase 2 — Complete learning-delta promotion pipeline

### 2.1 Implement durable `LearningDeltaRepository`

**Files**

Existing interface likely exists; if missing or incomplete, add under:

* `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/LearningDeltaRepository.java`
* `products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/learning/DataCloudLearningDeltaRepository.java`

**Problem**

`DefaultPromotionEngine` depends on `LearningDeltaRepository`, but DI must provide a durable implementation. 

**Change**

Implement Data Cloud-backed storage:

Collections:

```text
agent-learning-deltas
agent-learning-delta-events
agent-learning-delta-evaluations
```

Required methods:

* save
* getById
* query by tenant/agent/release/skill/state/target
* updateState
* appendEvaluationResult
* appendPromotionResult
* findPromotable

**Acceptance criteria**

* Deltas survive restart.
* State transitions are append-only.
* Tenant isolation is enforced.
* State update is idempotent.

---

### 2.2 Fix `DefaultPromotionEngine` evidence mapping

**File**

`platform/java/agent-core/src/main/java/com/ghatana/agent/promotion/DefaultPromotionEngine.java`

**Problem**

`DefaultMasteryTransitionPolicy` requires specific evidence keys, but promotion currently stores evidence as `evidence_0`, `evidence_1`, etc.  

**Change**

Map evidence based on target transition:

| Transition                    | Required evidence                                                                                   |
| ----------------------------- | --------------------------------------------------------------------------------------------------- |
| `UNKNOWN → OBSERVED`          | `trace_id` or `verified_source_id`                                                                  |
| `OBSERVED → PRACTICED`        | `episodes` or `sandbox_experiments`                                                                 |
| `PRACTICED → COMPETENT`       | `procedure_id`, `basic_eval_passed=true`                                                            |
| `COMPETENT → MASTERED`        | `regression_passed=true`, `safety_passed=true`, `recovery_passed=true`, `compatibility_passed=true` |
| `MASTERED → MAINTENANCE_ONLY` | `new_active_version_id`                                                                             |

Add a `PromotionEvidenceMapper`.

**Acceptance criteria**

* A valid procedural delta can promote `PRACTICED → COMPETENT`.
* A comprehensive evaluated skill can promote `COMPETENT → MASTERED`.
* Promotion fails with clear reason when evidence is missing.

---

### 2.3 Call `updateMasteryItemWithDelta()` during promotion

**File**

`platform/java/agent-core/src/main/java/com/ghatana/agent/promotion/DefaultPromotionEngine.java`

**Problem**

`updateMasteryItemWithDelta()` exists but is not called. Promotion can transition state without attaching procedure IDs, semantic fact IDs, negative knowledge IDs, evaluation refs, score, or version scope. 

**Change**

After successful transition:

```java
return updateMasteryItemWithDelta(delta, item)
    .then(updated -> updateDeltaToPromoted(delta))
    .map(...);
```

Important: reload updated item after transition to avoid overwriting state/history.

**Acceptance criteria**

* Promoted mastery item contains delta’s procedure/fact/negative IDs.
* Evaluation refs are preserved.
* Score is updated.
* Version scope can be updated from delta metadata.
* State history remains intact.

---

### 2.4 Make promotion policy target-state logic explicit

**Files**

* `platform/java/agent-core/src/main/java/com/ghatana/agent/promotion/DefaultPromotionPolicy.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/promotion/PromotionPolicy.java`

**Change**

Promotion target state should not be inferred loosely. It should map from delta/eval evidence:

```text
EPISODIC_MEMORY → OBSERVED
SEMANTIC_FACT → OBSERVED or PRACTICED
PROCEDURAL_SKILL + basic eval → COMPETENT
PROCEDURAL_SKILL + full eval → MASTERED
NEGATIVE_KNOWLEDGE → PRACTICED/COMPETENT depending evidence
RETRIEVAL_POLICY → not mastery transition unless linked skill policy eval passes
PROMPT_TEMPLATE / PLANNER_POLICY → requires human/governance approval
MODEL_ADAPTER → L5 offline only
MASTERY_STATE → governance workflow only
```

**Acceptance criteria**

* Each target has deterministic target-state rules.
* Rules are tested.
* Human-review-required deltas cannot auto-promote.

---

## Phase 3 — Fix obsolescence flow

### 3.1 Fix `ObsolescenceRouter.route()`

**File**

`platform/java/agent-core/src/main/java/com/ghatana/agent/obsolescence/ObsolescenceRouter.java`

**Problem**

The router calls:

```java
masteryRegistry.findBySkill(event.masteryId(), null)
```

But `findBySkill` expects a non-null environment and `DataCloudMasteryRegistry.findBySkill()` dereferences `env.tenantId()`.  

**Change**

Use:

```java
masteryRegistry.getById(event.tenantId(), event.masteryId())
```

or:

```java
masteryRegistry.query(
  MasteryQuery.byTenant(event.tenantId()).withMasteryId(event.masteryId())
)
```

**Acceptance criteria**

* Obsolescence routing is tenant-scoped.
* No deprecated `findBySkill(..., null)` usage remains.
* Router handles missing item gracefully.

---

### 3.2 Align obsolescence target states with transition policy

**Files**

* `platform/java/agent-core/src/main/java/com/ghatana/agent/obsolescence/ObsolescenceRouter.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/transition/DefaultMasteryTransitionPolicy.java`

**Problem**

Router can map runtime incompatibility/security vulnerability directly to `RETIRED`, but transition policy permits `RETIRED` only from `OBSOLETE` with `no_active_use_case` evidence.  

**Change**

Recommended mapping:

| Reason                      | Target                                                        |
| --------------------------- | ------------------------------------------------------------- |
| security vulnerability      | `QUARANTINED`                                                 |
| repeated failures           | `QUARANTINED`                                                 |
| documentation contradiction | `QUARANTINED` or `OBSERVED` depending severity                |
| version mismatch            | `MAINTENANCE_ONLY` or `OBSOLETE` depending active replacement |
| API change                  | `MAINTENANCE_ONLY` or `OBSOLETE`                              |
| runtime incompatibility     | `OBSOLETE`, not direct `RETIRED`                              |
| deprecated dependency       | `OBSOLETE`, then separate retirement workflow                 |

**Acceptance criteria**

* Router never proposes invalid transitions.
* `RETIRED` only happens after `OBSOLETE` and `no_active_use_case`.
* Security issues quarantine, not silently retire.

---

### 3.3 Implement evidence-rich obsolescence events

**Files**

* `ObsolescenceEvent.java`
* `DefaultObsolescenceDetector.java`
* `ObsolescenceRouter.java`

**Change**

Ensure each event includes transition-policy-compatible evidence keys:

```yaml
security_advisory: CVE-...
api_break: api-diff-ref
repeated_failures: failure-count/ref
contradiction: docs-snapshot/ref
end_of_life: release-note/ref
replaced_by_newer: mastery-id/ref
safety_violation: trace/ref
```

**Acceptance criteria**

* Detector-generated event can be routed without manual translation failure.
* All transition evidence keys satisfy `DefaultMasteryTransitionPolicy`.
* Tests cover each obsolescence reason.

---

## Phase 4 — Complete mastery-aware execution path

### 4.1 Require `skillId` for mastery-bound agents

**File**

`GovernedAgentDispatcher.java`

**Current state**

Dispatcher already denies if `MasteryRegistry` is configured and no `skillId` is in context. 

**Change**

Make this more precise:

* only require `skillId` if the agent definition has `skillRefs` or `masteryBindings`
* include agent/release/definition metadata in denial
* add recovery instruction: “provide skillId or disable mastery binding”

**Acceptance criteria**

* Non-mastery agents can run without `skillId`.
* Mastery-bound agents require `skillId`.
* Denials are traceable and explainable.

---

### 4.2 Make task classification real

**Files**

* `platform/java/agent-core/src/main/java/com/ghatana/agent/runtime/mode/TaskClassifier.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/mode/EvidenceDrivenTaskClassifier.java`
* `AepOrchestrationModule.java`

**Problem**

AEP module provides a no-op classifier that always returns low-risk familiar. 

**Change**

Implement an evidence-driven classifier using:

* task text
* action type
* side-effect classification
* environment: prod/staging/dev
* data class
* tool category
* release/maturity
* mastery state
* current version applicability

Classification output:

```java
TaskClassification(
  TaskRiskLevel,
  TaskNovelty,
  Map<String, String> evidence
)
```

**Acceptance criteria**

* Production mutation = high/critical risk.
* New/unknown version = unknown/exploration.
* Maintenance-only context detected.
* Classifier is tested by scenario matrix.

---

### 4.3 Enforce approval and verification proofs consistently

**Files**

* `GovernedAgentDispatcher.java`
* `AgentContext.java`
* approval/verification model classes if missing

**Problem**

Dispatcher checks `hasApproval` and `hasVerification` boolean flags. 

That is too weak for production.

**Change**

Replace booleans with proof references:

```java
approvalRef
verificationRef
evaluationRunRef
policyDecisionRef
```

Validate:

* ref exists
* ref is tenant-scoped
* ref applies to same agent/release/skill/task
* ref is not expired
* ref has allowed outcome

**Acceptance criteria**

* Boolean-only approval is rejected.
* Approval/verification checks are traceable.
* Evidence appears in trace ledger.

---

## Phase 5 — Make memory retrieval production-grade

### 5.1 Build full `MemoryRetrievalService`

**Files**

* `platform/java/agent-core/src/main/java/com/ghatana/agent/memory/retrieval/MasteryAwareMemoryRetriever.java`
* `products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/store/MemoryPlane.java`
* new `MemoryRetrievalService.java`

**Problem**

`MasteryAwareMemoryRetriever` mostly filters pre-provided query items. 

**Change**

Create a complete retrieval pipeline:

```text
Task + AgentContext + VersionContext
→ build MemoryQuery
→ MemoryPlane.searchSemantic/query
→ retrieve negative knowledge
→ retrieve procedures
→ retrieve semantic facts
→ retrieve episodes
→ apply mastery/version/freshness/trust filters
→ rerank by utility
→ return RetrievalBundle
```

**Acceptance criteria**

* Retrieval works without pre-populated `MemoryQuery.items`.
* Negative knowledge is always considered.
* Version-obsolete memory is excluded.
* Maintenance-only memory requires legacy match.
* Retrieval trace includes selected and rejected items with reasons.

---

### 5.2 Add retrieval result explanation

**New class**

`RetrievalDecision.java`

Fields:

```java
memoryItemId
skillId
masteryState
versionApplicability
freshness
included
reason
priority
```

**Acceptance criteria**

* Every retrieved memory has explanation.
* Rejected obsolete/maintenance mismatch items are traceable.
* Debugging retrieval becomes possible.

---

## Phase 6 — Evaluation packs and mastery promotion

### 6.1 Implement `EvaluationPack` execution

**Files**

* `platform/java/agent-core/src/main/java/com/ghatana/agent/evaluation/pack/EvaluationPack.java`
* new evaluator runner classes
* Data Cloud persistence

Search shows `EvaluationPack` exists in agent-core. 

**Change**

Implement an `EvaluationHarness`:

```java
interface EvaluationHarness {
  Promise<EvaluationRunResult> run(EvaluationPack pack, LearningDelta delta, EvaluationContext ctx);
}
```

Support test types:

* unit
* integration
* regression
* compatibility
* safety
* recovery
* prompt-injection
* output-contract
* trace-grade

**Acceptance criteria**

* Procedural skill cannot promote to `COMPETENT` without basic eval.
* Skill cannot promote to `MASTERED` without regression, safety, recovery, compatibility.
* Eval result writes refs consumed by promotion evidence mapper.

---

### 6.2 Link `AgentRelease.evaluationPackId` to skill-level eval packs

**Files**

* `AgentRelease.java`
* `AgentReleaseBuilder.java`
* `DataCloudAgentReleaseRepository.java`
* mastery item/evaluation refs

`AgentRelease` already stores policy pack, evaluation pack, memory contract, telemetry, explanation, redaction, threat model, data classes, permitted purposes, and maturity profile. 

**Change**

Add or standardize:

```java
skillEvaluationPackRefs
masteryPolicyPackRefs
learningContractId
```

If adding fields is too invasive, store them under release metadata or policy refs.

**Acceptance criteria**

* Release cannot be promoted to `ACTIVE` unless required eval packs pass.
* Mastery state cannot exceed release capability maturity.
* `L5` release cannot serve responses.

---

## Phase 7 — Persistence and API surface

### 7.1 Harden `DataCloudMasteryRegistry`

**File**

`products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/mastery/DataCloudMasteryRegistry.java`

**Changes**

* Use stable entity IDs, not only data map fields.
* Add idempotent save/update by `tenantId + masteryId`.
* Ensure transition and item update are atomic or recoverable.
* Remove in-memory `masteryIdToTenantId`/`knownTenantIds` as correctness dependencies.
* Add paginated stale scan instead of cap at 1000.
* Use `findBest()` inside `decide()`.
* Add optimistic concurrency/version field.

**Acceptance criteria**

* Repeated save does not duplicate mastery items.
* Transition retry is idempotent.
* Stale scan supports large tenants.
* Tenant isolation is enforced by tests.

---

### 7.2 Complete `MasteryController`

**File**

`products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/api/controller/MasteryController.java`

**Change**

Expose safe APIs:

* `GET /tenants/{tenantId}/mastery?skillId=&agentId=`
* `GET /tenants/{tenantId}/mastery/{masteryId}`
* `POST /tenants/{tenantId}/mastery/{masteryId}/transitions`
* `GET /tenants/{tenantId}/mastery/stale`
* `POST /tenants/{tenantId}/mastery/scan-obsolescence`
* `GET /tenants/{tenantId}/mastery/{masteryId}/history`
* `GET /tenants/{tenantId}/learning-deltas`
* `POST /tenants/{tenantId}/learning-deltas/{deltaId}/evaluate`
* `POST /tenants/{tenantId}/learning-deltas/{deltaId}/promote`

**Acceptance criteria**

* All APIs are tenant-scoped.
* No cross-tenant query.
* Dangerous operations require approval/gov role.
* Response includes trace/evidence refs, not raw sensitive payloads.

---

## Phase 8 — Trace, observability, and audit

### 8.1 Make trace events complete

**Files**

* `GovernedAgentDispatcher.java`
* trace event types/builders
* `AgentRunTracer`

Dispatcher already emits release, version, mastery, approval, verification, mode, policy, dispatch, and completion events. 

Add events for:

```text
MEMORY_RETRIEVAL_STARTED
MEMORY_RETRIEVAL_COMPLETED
MEMORY_ITEM_REJECTED
LEARNING_DELTA_PROPOSED
LEARNING_DELTA_EVALUATED
LEARNING_DELTA_PROMOTED
MASTERY_TRANSITION_PROPOSED
MASTERY_TRANSITION_APPLIED
OBSOLESCENCE_DETECTED
OBSOLESCENCE_ROUTED
EVALUATION_PACK_STARTED
EVALUATION_PACK_COMPLETED
```

**Acceptance criteria**

* Every decision has a trace event.
* Every denial has a machine-readable reason.
* Every promoted learning delta links to source trace, eval result, and mastery transition.

---

### 8.2 Add OpenTelemetry spans

Add spans for:

* version resolution
* mastery decision
* task classification
* mode selection
* memory retrieval
* policy evaluation
* agent execution
* learning delta evaluation
* promotion
* obsolescence scan

**Acceptance criteria**

* Trace ID is propagated from request to memory/eval/promotion.
* Metrics include latency, denial count, promotion count, stale items, obsolescence count.

---

## Phase 9 — Product integration and migration

### 9.1 Convert YAPPC agent YAML to canonical schema

**Files**

* `products/yappc/config/agents/**`
* agent schema/migration scripts

YAPPC has a rich config-based agent catalog. The goal should be to normalize these into `AgentDefinition` fields:

* `learningLevel`
* `adaptationTargets`
* `masteryBindings`
* `skillRefs`
* `masteryPolicyRefs`
* `evaluationRefs`
* `policyRefs`
* `securityContract`
* `observabilityContract`

**Acceptance criteria**

* All mastery-bound agents declare `skillRefs`.
* All `L3+` agents declare `evaluationRefs` and `masteryPolicyRefs`.
* Validation fails for missing required mastery config.
* No duplicate/alternate learning-level semantics in YAML.

---

### 9.2 Use digital-marketing as pilot product

Because the target commit’s kernel lifecycle work marks digital-marketing as the lifecycle-enabled product and excludes Data Cloud/YAPPC from lifecycle adoption in this stream, use digital-marketing as the first product pilot for GAA lifecycle integration. 

**Pilot scope**

* One orchestrator agent.
* Two task agents.
* One skill domain.
* One procedural skill.
* One negative knowledge item.
* One evaluation pack.
* One mastery lifecycle from `UNKNOWN → OBSERVED → PRACTICED → COMPETENT`.

**Acceptance criteria**

* End-to-end trace exists.
* Learning delta created.
* Evaluation runs.
* Promotion updates mastery registry.
* Runtime dispatch behavior changes after promotion.

---

# File-by-file implementation backlog

## `AepOrchestrationModule.java`

Path: `products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/aep/di/AepOrchestrationModule.java`

Tasks:

1. Replace no-op `MasteryRegistry` with Data Cloud-backed binding.
2. Replace no-op `ModeSelectionPolicy` with `DefaultModeSelectionPolicy`.
3. Replace no-op `TaskClassifier` with `EvidenceDrivenTaskClassifier`.
4. Provide non-null `InvariantMonitor`.
5. Provide non-null `AgentTraceLedger`.
6. Provide real `VersionContextResolver`.
7. Wire `MemoryRetriever` to `MasteryAwareMemoryRetriever` + `MemoryPlane`.
8. Wire `LearningDeltaRepository` to durable Data Cloud implementation.
9. Ensure `GovernedAgentDispatcher` receives the full constructor with real dependencies.
10. Add DI tests for startup and governed dispatch.

## `GovernedAgentDispatcher.java`

Path: `products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcher.java`

Tasks:

1. Pass resolved `VersionContext` through mastery/mode selection.
2. Replace boolean approval/verification with proof refs.
3. Require `skillId` only for mastery-bound agents.
4. Add memory retrieval hook before dispatch.
5. Add learning contract validation hook if learning delta emitted.
6. Add full trace events for retrieval, mode, approval, verification, dispatch, capture.
7. Fail closed when mastery registry exists but no decision can be made.
8. Add tests for release state, shadow/canary/active, blocked, stale, obsolete, maintenance-only, approval/verification.

## `DataCloudMasteryRegistry.java`

Path: `products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/mastery/DataCloudMasteryRegistry.java`

Tasks:

1. Make `decide()` use version-aware `findBest()`.
2. Use version context in all ranking/decision paths.
3. Remove deprecated cross-tenant `findStale(Instant)` dependency.
4. Replace in-memory indexes with durable lookup.
5. Make transition+item update idempotent.
6. Add optimistic locking.
7. Paginate stale scans.
8. Ensure `MAINTENANCE_ONLY` is selected only when version scope classifies as maintenance.
9. Add tests for tenant isolation, version ranking, stale filtering, transition idempotency.

## `DefaultModeSelectionPolicy.java`

Path: `platform/java/agent-core/src/main/java/com/ghatana/agent/runtime/mode/DefaultModeSelectionPolicy.java`

Tasks:

1. Keep current mapping, but add explicit execution-score thresholds.
2. Add policy for `UNKNOWN + low-risk + sandbox` = fast-learning human-gated.
3. Add policy for `UNKNOWN + high-risk` = blocked or human-gated verification-first.
4. Add policy for `MAINTENANCE_ONLY + no legacy context` = blocked.
5. Add tests for all state/version/risk combinations.

## `MasteryAwareModeSelector.java`

Path: `platform/java/agent-core/src/main/java/com/ghatana/agent/runtime/mode/MasteryAwareModeSelector.java`

Tasks:

1. Ensure version context string format is not lossy.
2. Prefer typed `VersionContext` on `MasteryQuery` instead of string.
3. Include all mode-decision evidence in trace metadata.
4. Avoid generating unrelated internal trace ID when caller trace already exists.
5. Add tests for propagation of version context and tenant ID.

## `MasteryAwareMemoryRetriever.java`

Path: `platform/java/agent-core/src/main/java/com/ghatana/agent/memory/retrieval/MasteryAwareMemoryRetriever.java`

Tasks:

1. Query `MemoryPlane` directly when provided.
2. Strictly enforce maintenance-only legacy matching.
3. Exclude `UNKNOWN` by default outside fast-learning mode.
4. Prioritize negative knowledge above facts/procedures.
5. Return retrieval explanations.
6. Add support for active/maintenance/obsolete version classification per item.
7. Add tests for all memory type priorities.

## `LearningLevel.java`

Path: `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/LearningLevel.java`

Tasks:

1. Keep as canonical source.
2. Ensure all docs and engines use these semantics.
3. Add tests proving `MASTERY_STATE` is only allowed for `L5`.
4. Add tests proving `L5` cannot serve responses.

## `LearningContract.java`

Path: `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/LearningContract.java`

Tasks:

1. Keep governance boundary.
2. Add helper to reject offline-only targets in online path.
3. Add helper to validate a `LearningDelta`.
4. Add tests for provenance/promotion defaults.

## `LearningDelta.java`

Path: `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/LearningDelta.java`

Tasks:

1. Add typed `VersionScope` field or `versionScopeRef`.
2. Add `EvaluationRequirement` field.
3. Add `riskClass`.
4. Add `policyPackRefs`.
5. Add `compatibilityEvidenceRefs`.
6. Add builder for safe creation.
7. Add digest validation helper.

## `DefaultLearningDeltaEvaluator.java`

Path: `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/DefaultLearningDeltaEvaluator.java`

Tasks:

1. Use `LearningContract` in evaluator.
2. Reject target if not permitted.
3. Validate rollback refs resolve.
4. Validate evaluation refs resolve.
5. Validate evidence refs resolve.
6. Add target-specific test suites.
7. Add risk-based human review requirements.

## `DefaultPromotionEngine.java`

Path: `platform/java/agent-core/src/main/java/com/ghatana/agent/promotion/DefaultPromotionEngine.java`

Tasks:

1. Add evidence mapper.
2. Call `updateMasteryItemWithDelta()`.
3. Reload item after transition before saving metadata.
4. Make target-state mapping deterministic.
5. Mark delta `PROMOTING` before transition.
6. Mark `PROMOTED` only after both transition and item update succeed.
7. Mark `FAILED`/`REJECTED` with reason on failure.
8. Add idempotency by `deltaId`.
9. Add tests for each state transition.

## `DefaultMasteryTransitionPolicy.java`

Path: `platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/transition/DefaultMasteryTransitionPolicy.java`

Tasks:

1. Keep evidence gates.
2. Add explicit allowed demotions for safety events.
3. Add explicit `OBSOLETE → MAINTENANCE_ONLY` recovery only with review.
4. Add direct `MASTERED → QUARANTINED` for critical safety issue.
5. Add tests for invalid direct retirement.

## `ObsolescenceRouter.java`

Path: `platform/java/agent-core/src/main/java/com/ghatana/agent/obsolescence/ObsolescenceRouter.java`

Tasks:

1. Replace deprecated `findBySkill(event.masteryId(), null)`.
2. Use `getById(event.tenantId(), event.masteryId())`.
3. Map reasons to transition-policy-valid target states.
4. Generate required evidence keys.
5. Add router tests for each obsolescence reason.

## `DefaultObsolescenceDetector.java`

Path: `platform/java/agent-core/src/main/java/com/ghatana/agent/obsolescence/DefaultObsolescenceDetector.java`

Tasks:

1. Expand detection evidence beyond labels/string refs.
2. Integrate dependency snapshots.
3. Integrate API diff results.
4. Integrate security advisory source.
5. Integrate evaluation failures.
6. Integrate docs snapshot contradiction.
7. Emit transition-compatible evidence.

## `ObsolescenceScanner.java`

Path: `platform/java/agent-core/src/main/java/com/ghatana/agent/obsolescence/ObsolescenceScanner.java`

Tasks:

1. Remove hardcoded `"system"` tenant scan from default `scan()` or make tenant explicit.
2. Add scheduler lifecycle management.
3. Add metrics.
4. Add trace events.
5. Add backpressure/pagination for large tenants.
6. Add test for event-triggered dependency update scan.

## `AgentDefinition.java`

Path: `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/config/AgentDefinition.java`

Tasks:

1. Make `LearningContract` a first-class typed field in DTO/materializer, not only derived from string/metadata.
2. Make `masteryBindings`, `skillRefs`, and `masteryPolicyRefs` required for mastery-bound agents.
3. Make `evaluationRefs` required for `L3+`.
4. Validate `L5` releases are offline/governance only.
5. Add schema/materializer tests.

## `AgentRelease.java`

Path: `platform/java/agent-core/src/main/java/com/ghatana/agent/release/AgentRelease.java`

Tasks:

1. Add or reference `learningContractId`.
2. Add or reference `skillEvaluationPackRefs`.
3. Add release gating for `L5` offline-only.
4. Add maturity profile validation against mastery state.
5. Add tests for release/maturity/learning compatibility.

## `AgentReleaseState.java`

Path: `platform/java/agent-core/src/main/java/com/ghatana/agent/release/AgentReleaseState.java`

Tasks:

1. Keep existing state machine.
2. Add helper for `isLearningAllowed()`.
3. Add helper for `isPromotionAllowed()`.
4. Add tests for `SHADOW`, `CANARY`, `ACTIVE`, `BLOCKED`.

---

# Recommended implementation order

## Sprint 1 — Make runtime safe

1. Fix `AepOrchestrationModule` null wiring.
2. Replace unsafe no-op defaults.
3. Pass resolved version context through dispatcher/mode path.
4. Make `DataCloudMasteryRegistry.decide()` version-aware.
5. Fix `ObsolescenceRouter` NPE path.

## Sprint 2 — Make promotion work

1. Implement durable `LearningDeltaRepository`.
2. Fix promotion evidence mapping.
3. Call mastery item update after transition.
4. Add promotion idempotency.
5. Add end-to-end tests: delta → evaluation → promotion → mastery state change.

## Sprint 3 — Make retrieval correct

1. Implement `MemoryRetrievalService`.
2. Enforce maintenance-only strict retrieval.
3. Add retrieval explanations.
4. Add negative knowledge first-class retrieval.
5. Add retrieval trace events.

## Sprint 4 — Make obsolescence production-grade

1. Fix obsolescence target-state mapping.
2. Add evidence-rich events.
3. Add dependency/API/security/docs/test evidence sources.
4. Add tenant-scoped scheduled scans.
5. Add obsolescence dashboard/API.

## Sprint 5 — Product pilot

1. Choose one digital-marketing agent.
2. Define one skill.
3. Create one evaluation pack.
4. Run full lifecycle:
   `UNKNOWN → OBSERVED → PRACTICED → COMPETENT`.
5. Verify dispatch behavior changes after mastery promotion.

---

# Clean target lifecycle after implementation

```text
Bootstrap
→ Load AgentDefinition
→ Load AgentRelease
→ Load LearningContract
→ Load MasteryBindings
→ Resolve VersionContext
→ Classify task
→ Query MasteryRegistry
→ Select execution mode
→ Retrieve mastery-compatible memory
→ Execute through GovernedAgentDispatcher
→ Verify output
→ Capture trace + episode
→ Propose LearningDelta
→ Evaluate delta
→ Promote delta if approved
→ Update MasteryItem
→ Scan obsolescence
→ Update active / maintenance / obsolete / retired states
→ Repeat
```

---

# Final recommendation

Do **not** start by adding more agent concepts. The repo already has the right concepts.

Start by making the current system **end-to-end correct**:

1. safe DI wiring,
2. real mastery registry in AEP,
3. version-aware `decide()`,
4. strict maintenance/obsolete retrieval,
5. durable learning deltas,
6. working promotion,
7. obsolescence routing without invalid transitions,
8. traceable approvals and verification,
9. evaluation-pack-backed promotion,
10. one product pilot.

Once those are working, the GAA will have the foundation to progressively master tasks without unsafe self-mutation.

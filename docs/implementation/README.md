Below is a repo-grounded implementation plan based on scanning `samujjwal/ghatana` at commit `2f366d0fef70e0ccc55f6615c2221e5f1f4f6761`.

I did not run the repo locally; this is based on the GitHub snapshot and file-level code inspection.

# 1. Current state at target commit

The commit already contains a meaningful first implementation of the GAA mastery/self-learning direction. It is not just conceptual anymore.

## What already exists

### 1. Agent definition is becoming mastery-aware

`AgentDefinition` is an immutable versioned blueprint. At this commit it already includes:

* `learningLevel`
* `memoryBindings`
* `policyRefs`
* `evaluationRefs`
* `securityContract`
* `observabilityContract`
* `masteryBindings`
* `skillRefs`
* `masteryPolicyRefs`
* `toLearningContract()`
* `toMasteryBindings()`
* freshness/version compatibility policy materialization
* learning-level consistency validation

That is the right foundation for “agent as governed release contract,” not “agent as prompt.” 

### 2. Release governance is stronger

`AgentRelease` now requires tenant ownership and governance artifacts. Response-serving releases must declare `evaluationPackId`, `memoryContractId`, `masteryPolicyPackId`, and `learningContractId`. It also includes version/freshness policy IDs and a `releaseDigest()` over governance-critical fields. 

This is good. It means an active/canary agent release cannot serve traffic without explicit mastery and learning contracts.

### 3. Mastery registry exists

`MasteryRegistry` is already the correct abstraction: tenant-scoped lookup, best-match selection, queries, decisions, saving, state transitions, stale detection, and `getById`. Its transition docs already encode the lifecycle you described: `UNKNOWN → OBSERVED → PRACTICED → COMPETENT → MASTERED → MAINTENANCE_ONLY → OBSOLETE → RETIRED`, with quarantine support. 

`MasteryItem` already stores the right core fields: tenant, skill, domain, agent/release, state, version scope, applicability scope, score vector, procedures, semantic facts, negative knowledge, evidence refs, evaluation refs, failure modes, history, verification/staleness timestamps, labels, and confidence. 

`MasteryState` explicitly distinguishes mastery lifecycle from normal self-learning and defines execution/retrieval semantics. 

### 4. Mastery score is multi-dimensional

`MasteryScore` already models correctness, freshness, applicability, safety, transferability, evidence strength, and regression stability. It computes execution score as a product of key dimensions. 

That matches the desired design: mastery should not be one flat confidence number.

### 5. Version-aware mastery exists

`VersionScope` already separates active, maintenance, and obsolete version constraints, and classifies a `VersionContext` into `ACTIVE`, `MAINTENANCE`, `OBSOLETE`, or `UNKNOWN`. 

This is central to your goal: old mastery is not deleted; it becomes maintenance-only or obsolete depending on version context.

### 6. Data Cloud mastery persistence exists

`DataCloudMasteryRegistry` persists mastery items, transitions, and evidence through Data Cloud collections and implements `findBest`, `query`, `decide`, `save`, `transition`, stale scanning, and tenant-scoped lookup. 

This is a strong start, but needs hardening around version context parsing, ranking semantics, real evidence, and transition consistency.

### 7. Runtime dispatch is mastery-aware

`GovernedAgentDispatcher` now wraps dispatch with:

* release guard
* manifest capability guard
* mastery-bound `skillId` requirement
* version context resolution
* version compatibility check
* mastery decision
* approval proof check
* verification proof check
* mode selection
* invariant evaluation
* trace ledger events
* OTel spans

This is the right enforcement point. 

### 8. Mode selection exists

`MasteryAwareModeSelector` queries mastery, classifies tasks, applies mode policy, and emits trace metadata. 

`DefaultModeSelectionPolicy` maps mastery/version/risk to execution mode:

* `MASTERED + ACTIVE → DETERMINISTIC_EXECUTION`
* `COMPETENT → BOUNDED_PROBABILISTIC_REASONING + verification`
* `PRACTICED → EXPLORATORY_FAST_LEARNING + approval`
* `OBSERVED → VERIFICATION_FIRST + approval`
* `MAINTENANCE_ONLY → MAINTENANCE_ONLY + approval`
* obsolete/terminal/stale → blocked

This is very close to the target lifecycle. 

### 9. Learning delta pipeline exists, but is incomplete

`LearningDelta` is the right artifact: proposed change type, target, state, agent/release/skill/tenant, procedure/fact/negative IDs, proposed content, evidence/eval refs, rollback ref, confidence before/after, review requirement, timestamps, labels, rejection reason. 

`DefaultLearningDeltaEvaluator` has target-specific validation for episodic memory, semantic fact, procedural skill, negative knowledge, retrieval policy, thresholds, routing policy, prompt template, planner policy, model adapter, and mastery-state transitions. 

But `LearningDeltaService` is only an interface with `propose()` and `evaluate()`; I did not find a concrete implementation. 

### 10. Evaluation and promotion exist, but are not production-grade yet

`EvaluationHarness` defines the right interface and pack creation methods for procedural skills, semantic facts, and mastered promotion. 

But `DefaultEvaluationHarness` currently returns passing mock test results from `runTestCase()`. That cannot support real mastery promotion yet. 

`DefaultPromotionEngine` wires learning deltas to mastery transitions, updates deltas to `PROMOTED`, bootstraps mastery items when needed, and maps evidence. But its internal `evaluate()` is simplistic and not integrated with the real `EvaluationHarness` / `DefaultLearningDeltaEvaluator` pipeline. 

### 11. Obsolescence exists

`DefaultObsolescenceDetector` detects version mismatches, API deprecations/diffs, runtime incompatibility, repeated failures, security vulnerabilities, and documentation contradictions. It can scan all tenant mastery items. 

But a lot of detection still depends on labels and string patterns inside evidence/evaluation refs. It needs real evidence adapters.

### 12. Mastery-aware memory retrieval exists

`MasteryAwareMemoryRetriever` filters memory by mastery state and version applicability, excludes obsolete/retired/quarantined items, blocks unknown by default, allows maintenance-only only for maintenance version context, and prioritizes negative knowledge, facts, procedures, episodes, etc. 

However, it mostly filters already-provided query items; it is not yet a complete retrieval pipeline that calls `MemoryPlane` and joins memory with mastery items end-to-end.

### 13. Trace vocabulary is ready

`TraceEventType` already includes mastery, learning, evaluation, obsolescence, version context, mode selection, approval, verification, and dispatch-allowed events. 

This is excellent. The audit/evidence plane has the vocabulary needed for mastery governance.

### 14. API surface exists

`MasteryController` exposes query/get/save/transition/stale/obsolete/evidence/transitions/state distribution/version compatibility/promotion history/eval status/obsolescence scan/mode explanation/learning delta list/evaluate/promote endpoints with basic governance checks. 

The controller is useful, but it should be hardened around route design, tenant derivation, query validation, and promotion workflow correctness.

---

# 2. Main gaps blocking the goal

## P0 gaps

### Gap 1 — Learning delta pipeline is not fully wired

The code has `LearningDelta`, evaluator, repository concepts, API endpoints, and promotion engine, but no concrete `LearningDeltaService` implementation was found. The learning path should be:

```text
Agent trace / reflection
→ LearningDeltaFactory
→ LearningContract validation
→ LearningDeltaService.propose()
→ repository save
→ evaluator
→ evaluation harness
→ approval queue if needed
→ promotion engine
→ mastery transition
→ trace events
```

Today that path appears partially implemented but not end-to-end.

### Gap 2 — Evaluation harness is placeholder

`DefaultEvaluationHarness.runTestCase()` currently assumes pass. That makes `COMPETENT` and `MASTERED` promotion unsafe. 

Real mastery requires real test execution, trace grading, compatibility checks, safety checks, and regression checks.

### Gap 3 — Version context serialization is inconsistent

`MasteryAwareModeSelector` formats version context as `component=version,component=version`. 

`DataCloudMasteryRegistry` parses version context in that same string format. 

But `GovernedAgentDispatcher` appears to pass `versionContext.toString()` into `MasteryQuery` in at least one path. 

This is risky. Version context should be typed or serialized through one canonical codec.

### Gap 4 — Runtime mode selection receives weak task input

`GovernedAgentDispatcher` currently calls mode selection with `agentId + " task"` and empty context in the shown path. 

That means task risk/novelty classification may not reflect the real user task. Mode selection should use actual task description, tool/action intent, environment, and side-effect risk.

### Gap 5 — Promotion engine bypasses the richer evaluator/harness

`DefaultPromotionEngine.evaluate()` computes simple confidence-gain/evidence-count results. 

Promotion should not be based on confidence gain alone. It must use:

* `DefaultLearningDeltaEvaluator`
* `EvaluationHarness`
* evaluation pack registry
* safety policy
* compatibility tests
* regression tests
* approval workflow

### Gap 6 — Obsolescence detection is too heuristic

`DefaultObsolescenceDetector` supports the right reason categories, but many detectors inspect labels and string refs such as `"signature-change"`, `"test-failure"`, `"cve"`, etc. 

This should be connected to real inputs: dependency lockfiles, SBOM/security advisories, API diff reports, test results, docs snapshots, runtime fingerprints, and repeated trace failures.

### Gap 7 — Mastery-aware memory retriever is not fully integrated with MemoryPlane

`MasteryAwareMemoryRetriever` has a `MemoryPlane` field, but the visible method filters `query.items()` rather than executing a full memory query and re-ranking result sets from memory tiers. 

To reach the goal, retrieval must become:

```text
Task + version + skill + mode
→ query MemoryPlane
→ join MasteryRegistry
→ include negative knowledge first
→ filter obsolete/retired/quarantined
→ allow maintenance-only only in legacy context
→ return ranked memory bundle with reasons
```

### Gap 8 — API uses query-param tenant too much

`MasteryController` takes `tenantId` from query parameters. 

For production, tenant must come from authenticated principal/session/context, not user-controlled query string. Query param can be accepted only if it is validated against the principal’s tenant scope.

### Gap 9 — Release skill-level pack refs are placeholders

`AgentRelease.skillEvaluationPackRefs()` and `masteryPolicyPackRefs()` return empty maps with comments saying full implementation later. 

For real skill mastery, release must carry skill-level evaluation and mastery policy bindings.

### Gap 10 — Active vs maintenance state semantics need tightening

The current policy blocks maintenance version applicability unless the mastery state itself is `MAINTENANCE_ONLY`. 

That is safe, but the registry must guarantee that when active versions evolve, older mastery items are explicitly transitioned to `MAINTENANCE_ONLY`. Otherwise valid legacy knowledge may be blocked unexpectedly.

---

# 3. Target architecture

The target GAA system should be built around these pipelines:

## Runtime pipeline

```text
AgentDefinition
→ AgentRelease
→ AgentInstance/tenant config
→ GovernedAgentDispatcher
→ VersionContextResolver
→ MasteryRegistry.decide()
→ MasteryAwareModeSelector
→ MasteryAwareMemoryRetriever
→ Agent execution
→ TraceLedger
→ MemoryPlane capture
```

## Learning pipeline

```text
TraceLedger + MemoryPlane episodes
→ Reflection/LearningEngine
→ LearningDeltaFactory
→ LearningDeltaService.propose()
→ LearningDeltaEvaluator
→ EvaluationHarness
→ Approval workflow if needed
→ PromotionEngine
→ MasteryRegistry.transition()
→ TraceLedger learning events
```

## Mastery lifecycle pipeline

```text
UNKNOWN
→ OBSERVED
→ PRACTICED
→ COMPETENT
→ MASTERED
→ MAINTENANCE_ONLY
→ OBSOLETE
→ RETIRED
      ↘
       QUARANTINED
```

## Obsolescence pipeline

```text
Dependency/runtime/doc/security/test/trace change
→ EnvironmentFingerprint
→ ObsolescenceDetector.scanAll()
→ ObsolescenceEvent
→ ObsolescenceRouter / TransitionService
→ MasteryRegistry.transition()
→ TraceLedger event
→ UI review queue
```

---

# 4. Implementation plan by independent workstream

## Workstream A — Stabilize contracts and schema

### Goal

Make agent definition, release, learning, mastery, and version contracts canonical and machine-validatable.

### Files to update

#### `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/config/AgentDefinition.java`

Actions:

1. Keep `toLearningContract()` as canonical materializer.
2. Make `learningLevel` typed in DTO/materializer path; avoid storing only as `String`.
3. Add validation that `adaptationTargets` are allowed by `LearningLevel`.
4. Require `skillRefs` when `masteryBindings` exists.
5. Require `masteryPolicyRefs` and `evaluationRefs` for `learningLevel >= L3`; code already validates this, but ensure validator calls it.
6. Extend `toReleaseDraft()` to copy tenant, memory contract, eval refs, mastery policy refs, learning contract digest, version/freshness policy refs when available.

Why: `AgentDefinition` already has most fields and consistency logic. The missing part is enforcing it at materialization/validation/release creation. 

#### `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/config/AgentDefinitionValidator.java`

Actions:

1. Call `validateLearningLevelConsistency()`.
2. Validate `toLearningContract()` does not throw.
3. Validate every `skillRef` has corresponding mastery/eval policy coverage.
4. Validate `masteryBindings.registryRef` and namespace.
5. Validate no `MASTERY_STATE` adaptation target unless governance workflow is explicitly enabled.
6. Add tests for invalid learning level, missing skill refs, L3+ without eval/mastery refs, and mismatched metadata learning level.

#### `platform/java/agent-core/src/main/java/com/ghatana/agent/release/AgentRelease.java`

Actions:

1. Replace placeholder `skillEvaluationPackRefs()` and `masteryPolicyPackRefs()` with real record fields or structured metadata.
2. Include those fields in `releaseDigest()`.
3. Enforce response-serving releases include skill-level evaluation coverage for all mastered/competent skills.
4. Add `learningContractDigest` instead of only `learningContractId`.
5. Add `masteryContractDigest` or `masteryPolicyPackDigest`.

Why: release already enforces global eval/memory/mastery/learning contracts for response serving, but skill-level pack refs are placeholders. 

### Acceptance criteria

* Agent definition cannot be registered if mastery/learning config is inconsistent.
* Active/canary releases cannot be built without all governance artifacts.
* Release digest changes when skill-level eval/mastery policy refs change.
* Tests prove L3+ agents require promotion/eval governance.

---

## Workstream B — Fix version context end-to-end

### Goal

Make version-aware mastery deterministic, typed, and consistent.

### Files to add

#### `platform/java/agent-core/src/main/java/com/ghatana/agent/context/version/VersionContextCodec.java`

Responsibilities:

```java
String encode(VersionContext context);
VersionContext decode(String encoded);
Map<String, String> dependencies(VersionContext context);
```

Use one canonical format:

```json
{
  "dependencies": {"react-router": "7.2.0"},
  "runtimes": {"node": "22.0.0"},
  "tools": {"vite": "6.0.0"},
  "repoConventions": {"routerMode": "framework"},
  "environment": "production"
}
```

Do not rely on `toString()`.

### Files to update

#### `platform/java/agent-core/src/main/java/com/ghatana/agent/runtime/mode/MasteryAwareModeSelector.java`

Actions:

1. Replace `formatVersionContext()` with `VersionContextCodec.encode()`.
2. Remove ad hoc `component=version` string generation.
3. Add `versionContextDigest` to trace metadata.
4. Add tests that version context round-trips exactly.

Current selector serializes dependencies manually. 

#### `products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/mastery/DataCloudMasteryRegistry.java`

Actions:

1. Replace string parsing with `VersionContextCodec.decode()`.
2. Add typed `VersionContext` support to `MasteryQuery`, or add `versionContextJson`.
3. In `findBest`, exclude `OBSOLETE` applicability unless query explicitly includes obsolete.
4. In `query`, sort by actual version applicability, not only state.
5. Add tests for:

   * active match beats maintenance
   * maintenance allowed only in legacy contexts
   * obsolete never chosen by default
   * malformed version context fails closed

Current registry parses comma-separated strings and ranks some paths by state. 

#### `products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcher.java`

Actions:

1. Stop using `versionContext.toString()` for mastery query.
2. Use codec.
3. Build `EnvironmentSnapshot` from real agent context/input, not empty snapshots.
4. Store `versionContext` and `versionContextDigest` in trace payload.

Current dispatcher resolves version context and uses it in governance, but the snapshot and serialization path need hardening. 

### Acceptance criteria

* No code path uses `VersionContext.toString()` as a machine protocol.
* Every mastery decision stores exact version context used.
* Version compatibility tests cover active, maintenance, obsolete, unknown, and malformed contexts.

---

## Workstream C — Harden MasteryRegistry and transitions

### Goal

Make mastery state transitions safe, tenant-isolated, evidence-backed, and idempotent.

### Files to update

#### `platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/MasteryRegistry.java`

Actions:

1. Keep tenant-scoped APIs as canonical.
2. Remove or fully deprecate non-tenant APIs.
3. Add `decide(MasteryDecisionRequest)` typed request instead of only `MasteryQuery`.
4. Add explicit `explainDecision()` method returning reasons, matched versions, stale check, evidence, and policy gates.
5. Add `recordEvidence()` and `recordEvaluation()` convenience APIs.

Current interface is strong but still query-centric. 

#### `platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/MasteryDecision.java`

Actions:

1. Ensure factory methods preserve `versionApplicability` and evidence refs.
2. Add `blockedReasonCode` enum for easier UI and automation.
3. Add `requiresRefresh` separate from `stale`.
4. Add `legacyOnly` flag for maintenance mode.
5. Add `DecisionPolicyRef`.

Current decision has state, score, applicability, stale/terminal/executable, approval/verification flags, reason, evidence refs. 

#### `products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/mastery/DataCloudMasteryRegistry.java`

Actions:

1. Fix `findBest()` to use consistent filter+rank:

   * reject terminal by default
   * reject stale if freshness required
   * reject obsolete applicability by default
   * rank active > maintenance > unknown
   * rank mastered > competent > practiced > observed
   * rank execution score
2. Ensure `decide()` carries computed `VersionApplicability` into `MasteryDecision.allow()/requireApproval()/requireVerification()`. Current code often uses factory overloads that default applicability to `UNKNOWN`.
3. Ensure `MAINTENANCE_ONLY` decisions use `MasteryDecision.maintenanceOnly()`.
4. Enforce transition idempotency by transition ID.
5. Make item update and transition append atomic at the Data Cloud level or explicitly compensating.
6. Ensure `save()` uses `item.tenantId()` as source of truth and validates applicability tenant.
7. Add query indexes for tenant, skill, agent, release, state, staleAfter, domain.

Current Data Cloud implementation has good structure but needs these production hardening steps. 

### Acceptance criteria

* No cross-tenant mastery read/write is possible.
* `decide()` output includes accurate applicability and score.
* Replaying the same transition ID is idempotent.
* Obsolete/quarantined/retired states are never executable.
* Stale mastery blocks or routes to verification/refresh exactly as policy says.

---

## Workstream D — Complete LearningDeltaService

### Goal

Make self-learning use controlled deltas, not direct mutation.

### Files to add

#### `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/DefaultLearningDeltaService.java`

Responsibilities:

1. Validate `LearningContract.permits(delta.target())`.
2. Enforce `provenanceRequired`.
3. Enforce `promotionRequired`.
4. Persist delta in `PENDING_EVALUATION`.
5. Append `LEARNING_DELTA_PROPOSED` trace event.
6. Run `DefaultLearningDeltaEvaluator`.
7. Move state to:

   * `EVALUATED`
   * `REJECTED`
   * `PENDING_HUMAN_REVIEW`
8. Optionally route to approval queue.
9. Never directly update mastery or memory unless target is low-risk and contract permits.

#### `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/LearningDeltaService.java`

Actions:

1. Change `evaluate(deltaId)` return type from `Promise<Boolean>` to `Promise<EvaluationResult>` or `Promise<LearningDelta>`.
2. Add:

   * `approve(deltaId, approvalProof)`
   * `reject(deltaId, reason)`
   * `promote(deltaId)`
   * `listPending(tenantId, filters)`

Current interface is too small for the workflow. 

#### `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/DefaultLearningDeltaEvaluator.java`

Actions:

1. Keep current target-specific validation.
2. Add external policy hooks:

   * safety policy
   * data policy
   * tool policy
   * version compatibility policy
3. Require rollback ref for all execution-affecting targets.
4. Require evaluation refs for:

   * procedural skill
   * retrieval policy
   * routing policy
   * prompt template
   * planner policy
   * model adapter
   * mastery state
5. For `MASTERY_STATE`, require transition policy validation, not just valid enum name.

The evaluator already has useful target-specific checks, but mastery-state validation needs transition-aware policy integration. 

### Acceptance criteria

* No learning delta can be promoted without passing `LearningContract`.
* `MASTERY_STATE` deltas require governance workflow and valid transition evidence.
* Procedural skills below threshold become human-review items, not active behavior.
* Model adapter deltas are offline-only and cannot auto-promote.

---

## Workstream E — Replace mock EvaluationHarness with real execution

### Goal

Make `COMPETENT` and `MASTERED` states meaningful.

### Files to update

#### `platform/java/agent-core/src/main/java/com/ghatana/agent/evaluation/DefaultEvaluationHarness.java`

Current `runTestCase()` returns `true` for every test. 

Replace with test executors by `EvaluationType`:

| Evaluation type   | Executor                             |
| ----------------- | ------------------------------------ |
| `SKILL_UNIT`      | deterministic function/unit executor |
| `INTEGRATION`     | sandboxed integration executor       |
| `REGRESSION`      | historical trace replay              |
| `SAFETY`          | policy/safety checks                 |
| `RECOVERY`        | failure/rollback simulation          |
| `COMPATIBILITY`   | version matrix runner                |
| `TRACE_GRADE`     | trace completeness/scoring           |
| `OUTPUT_CONTRACT` | schema validation                    |

### Files to add

#### `platform/java/agent-core/src/main/java/com/ghatana/agent/evaluation/EvaluationExecutor.java`

```java
interface EvaluationExecutor {
    boolean supports(EvaluationType type);
    Promise<EvaluationResult.TestCaseResult> execute(EvaluationTestCase test, EvaluationContext context);
}
```

#### `platform/java/agent-core/src/main/java/com/ghatana/agent/evaluation/TraceReplayEvaluationExecutor.java`

Replays past traces against candidate procedure/policy.

#### `platform/java/agent-core/src/main/java/com/ghatana/agent/evaluation/VersionCompatibilityEvaluationExecutor.java`

Runs skill/procedure against version matrix.

#### `platform/java/agent-core/src/main/java/com/ghatana/agent/evaluation/SafetyEvaluationExecutor.java`

Checks unsafe tools, forbidden actions, policy violations, prompt-injection exposure, and side effects.

#### `platform/java/agent-core/src/main/java/com/ghatana/agent/evaluation/OutputContractEvaluationExecutor.java`

Validates output schema and invariants.

### Acceptance criteria

* Default harness no longer has mock pass behavior.
* `MASTERED` promotion requires all of: regression, safety, recovery, compatibility, trace-grade, output-contract.
* Eval results become durable evidence refs.
* Eval failures can produce negative knowledge or obsolescence events.

---

## Workstream F — Fix PromotionEngine to use real evaluation and policy

### Goal

Promotion must be based on verified evaluation, not confidence gain alone.

### Files to update

#### `platform/java/agent-core/src/main/java/com/ghatana/agent/promotion/DefaultPromotionEngine.java`

Actions:

1. Remove or demote the current simple `evaluate()` method.
2. Inject:

   * `LearningDeltaEvaluator`
   * `EvaluationHarness`
   * `EvaluationPackRegistry`
   * `PromotionPolicy`
   * `AgentTraceLedger`
3. Promotion flow should be:

```text
delta
→ check state is EVALUATED or APPROVED
→ check evaluator result
→ run/evaluate eval pack if missing
→ check promotion policy
→ transition mastery
→ update mastery item with IDs/scores/version scope/eval refs
→ set delta PROMOTED
→ trace LEARNING_DELTA_PROMOTED
```

Current engine already updates mastery items and transitions, which is good. It needs stronger evaluation source and policy integration. 

#### `platform/java/agent-core/src/main/java/com/ghatana/agent/promotion/DefaultPromotionPolicy.java`

Actions:

1. Define target-state mapping:

   * `EPISODIC_MEMORY → OBSERVED`
   * `SEMANTIC_FACT → OBSERVED/PRACTICED`
   * `PROCEDURAL_SKILL → PRACTICED/COMPETENT`
   * high-scoring procedural + mastered pack → `MASTERED`
   * negative knowledge → directly active but scoped
2. Enforce minimum eval score per target.
3. Enforce rollback ref for execution-impacting targets.
4. Enforce human review for high-risk targets.

### Acceptance criteria

* Promotion fails if evaluation refs are absent for policy/procedure/prompt/planner/model targets.
* Promotion writes state transition and updates mastery item in the right order.
* Promotion is idempotent.
* Promotion emits trace events.

---

## Workstream G — Complete runtime execution-mode enforcement

### Goal

Mode selection must control actual runtime behavior, not only metadata.

### Files to update

#### `products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcher.java`

Actions:

1. Extract task description from:

   * context `taskDescription`
   * typed input if it implements a task interface
   * action metadata
2. Extract side-effect classification:

   * read-only
   * write
   * irreversible
   * production-impacting
   * regulated-data
3. Pass real task and context to `modeSelector`.
4. If `ExecutionStrategy.BLOCKED`, deny before delegate.
5. If `VERIFICATION_FIRST`, run verification/preflight before delegate.
6. If `MAINTENANCE_ONLY`, enforce legacy-context proof and minimal-change policy.
7. If `EXPLORATORY_FAST_LEARNING`, enforce sandbox/no production side effects.
8. If `DETERMINISTIC_EXECUTION`, ensure selected procedure is exact-version compatible.
9. Add `executionStrategy` and `supervisionMode` to trace and OTel span.

Dispatcher already handles release/mastery/approval/verification gates, but must make strategy operational. 

#### `platform/java/agent-core/src/main/java/com/ghatana/agent/runtime/mode/DefaultModeSelectionPolicy.java`

Actions:

1. Keep conservative defaults.
2. Change maintenance applicability logic so valid legacy mastery is routed intentionally:

   * active version + active/mastered skill → deterministic/supervised
   * maintenance version + maintenance-only skill → maintenance-only
   * maintenance version + active skill → block with “transition required” or route to refresh
3. Add task-risk escalation:

   * high risk → at least supervised
   * critical → human gated
4. Add novelty handling:

   * unknown novelty → fast-learning + human-gated
   * changed docs/runtime → verification-first
5. Add explicit reason codes, not only strings.

Current mapping is close to desired behavior. 

### Acceptance criteria

* A `MASTERED` skill in active version can execute deterministically.
* A `COMPETENT` skill requires verification proof.
* A `PRACTICED` skill requires approval.
* `MAINTENANCE_ONLY` can run only in matching legacy version context.
* Obsolete/quarantined/stale blocks execution.
* High-risk tasks escalate supervision.

---

## Workstream H — Integrate MasteryAwareMemoryRetriever with MemoryPlane

### Goal

Retrieval must return applicable, fresh, version-compatible, safe memory.

### Files to update

#### `platform/java/agent-core/src/main/java/com/ghatana/agent/memory/retrieval/MasteryAwareMemoryRetriever.java`

Actions:

1. Make `MemoryPlane` mandatory for full retrieval mode.
2. Add method:

```java
Promise<RetrievedMemoryBundle> retrieve(MasteryAwareMemoryQuery query);
```

3. Query memory tiers:

   * negative knowledge
   * procedures
   * semantic facts
   * episodic traces
   * task-state
4. Join every item to mastery state and version applicability.
5. Return rejection reasons for filtered items.
6. Prioritize:

   * negative knowledge first
   * active mastered/competent procedures
   * semantic facts
   * recent successful episodes
   * known failures
   * maintenance-only only if legacy context
7. Emit trace events:

   * `MEMORY_RETRIEVAL_STARTED`
   * `MEMORY_ITEM_REJECTED`
   * `MEMORY_RETRIEVAL_COMPLETED`

Current retriever has the right filtering and ordering rules, but it needs to drive memory access itself rather than depend on preloaded query items. 

#### `products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/store/MemoryPlane.java`

Actions:

1. Add mastery-aware query hooks or metadata fields for:

   * skillId
   * masteryId
   * versionContextDigest
   * evidenceRef
   * trust level
   * source class
2. Add retrieval result type with provenance and reject reasons.

### Acceptance criteria

* Obsolete memory is not returned by default.
* Maintenance-only memory is returned only for matching legacy context.
* Negative knowledge appears before positive procedures.
* Retrieval trace explains why each selected/rejected memory item was handled.

---

## Workstream I — Make obsolescence evidence real

### Goal

Move from label/string heuristics to real evidence sources.

### Files to update

#### `platform/java/agent-core/src/main/java/com/ghatana/agent/obsolescence/DefaultObsolescenceDetector.java`

Current detector supports the right categories but many paths use labels and string refs. 

Actions:

1. Add evidence adapters:

   * `DependencyChangeEvidenceProvider`
   * `ApiDiffEvidenceProvider`
   * `SecurityAdvisoryEvidenceProvider`
   * `DocumentationSnapshotEvidenceProvider`
   * `TestFailureEvidenceProvider`
   * `TraceFailureEvidenceProvider`
   * `RuntimeFingerprintEvidenceProvider`
2. Replace label-based checks with provider results.
3. Keep labels only as override/manual signal.
4. Emit structured `ObsolescenceEvent` with evidence refs and recommended transition.
5. Route events through transition policy:

   * stale/version drift → `MAINTENANCE_ONLY`
   * security issue → `QUARANTINED`
   * docs/API contradiction → `OBSERVED` or `MAINTENANCE_ONLY`
   * repeated failures → `QUARANTINED`
   * no usage + obsolete → `RETIRED`

### Files to add/update

#### `platform/java/agent-core/src/main/java/com/ghatana/agent/obsolescence/ObsolescenceTransitionService.java`

Actions:

1. Consume `ObsolescenceEvent`.
2. Validate transition.
3. Append trace event.
4. Call `MasteryRegistry.transition()`.
5. Open review ticket for human-gated cases.

### Acceptance criteria

* Dependency version change creates a real obsolescence event.
* Security advisory quarantines impacted mastery items.
* Repeated trace/eval failures demote or quarantine.
* Every obsolescence transition is traceable.

---

## Workstream J — Harden Data Cloud APIs and UI

### Goal

Give users/operators visibility into mastery, learning, promotion, obsolescence, and runtime mode decisions.

### Files to update

#### `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/api/controller/MasteryController.java`

Actions:

1. Derive `tenantId` from authenticated principal, not query string.
2. Keep query string tenant only as optional filter validated against principal.
3. Split endpoints:

   * `/mastery/items`
   * `/mastery/items/{id}`
   * `/mastery/items/{id}/transitions`
   * `/mastery/items/{id}/evidence`
   * `/mastery/items/{id}/version-compatibility`
   * `/mastery/skills/{skillId}/evaluation-status`
   * `/learning-deltas`
   * `/learning-deltas/{id}/evaluate`
   * `/learning-deltas/{id}/approve`
   * `/learning-deltas/{id}/promote`
   * `/obsolescence/scan`
   * `/mode/explain`
4. Validate `limit`, `offset`, and enum params safely.
5. Add explicit DTOs; avoid exposing internal records directly.
6. Ensure promote endpoint requires approval/governance role and proof.
7. Return reason codes and trace IDs.

Current controller exposes most needed operations, but needs production-grade auth/context/DTO hardening. 

#### `products/data-cloud/delivery/ui/src/pages/MasteryPage.tsx`

Actions:

1. Show:

   * mastery state distribution
   * stale skills
   * obsolete/quarantined queue
   * learning deltas pending review
   * active vs maintenance version scopes
   * skill evidence/eval refs
   * “why this mode?” explanation
2. Add filters:

   * tenant
   * agent
   * skill
   * state
   * domain
   * version applicability
3. Add actions:

   * approve delta
   * reject delta
   * promote delta
   * transition mastery
   * scan obsolescence
   * inspect evidence
4. Add safety copy around maintenance-only and obsolete states.

#### `products/data-cloud/delivery/ui/src/api/mastery.service.ts`

Actions:

1. Use typed DTOs matching backend.
2. Include trace IDs in responses.
3. Add methods for all endpoints.
4. Use proper error handling and display governance denial reasons.

### Acceptance criteria

* Operator can see every learning delta before promotion.
* Operator can see why an agent was blocked/gated/allowed.
* Operator can review obsolete/quarantined/stale mastery.
* UI never promotes a delta without explicit proof/role.

---

## Workstream K — Complete observability and trace evidence

### Goal

Every important decision must be auditable and replayable.

### Files to update

#### `products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/audit/TraceEventType.java`

Already has the needed event vocabulary. 

Actions:

1. Ensure all events are emitted in real flows:

   * `MASTERY_DECISION_MADE`
   * `MODE_SELECTED`
   * `VERSION_CONTEXT_RESOLVED`
   * `APPROVAL_CHECKED`
   * `VERIFICATION_CHECKED`
   * `MEMORY_ITEM_REJECTED`
   * `LEARNING_DELTA_PROPOSED`
   * `LEARNING_DELTA_EVALUATED`
   * `LEARNING_DELTA_PROMOTED`
   * `OBSOLESCENCE_DETECTED`
   * `OBSOLESCENCE_ROUTED`
2. Add trace IDs to API responses.
3. Add dashboards:

   * blocked by reason
   * stale mastery count
   * promotion success/failure
   * eval pass/fail by pack
   * obsolete/quarantine trends
   * memory item rejection reasons
   * mode selection distribution

### Acceptance criteria

* For any agent output, we can answer:

  * what skill was used
  * which version context applied
  * which mastery decision was made
  * what memory was retrieved/rejected
  * what mode was selected
  * what approvals/verifications were checked
  * what release served the response
  * what learning delta was proposed later

---

# 5. Concrete implementation sequence

## Phase 1 — Contract correctness and compile-safety

1. Add `VersionContextCodec`.
2. Replace ad hoc version serialization everywhere.
3. Make `LearningContract` materialization mandatory in agent definition validation.
4. Add typed fields for release skill-level eval/mastery policy refs.
5. Fix `AgentReleaseBuilder` to populate tenant and governance artifacts consistently.
6. Add tests for invalid release/definition configs.

## Phase 2 — MasteryRegistry hardening

1. Fix `DataCloudMasteryRegistry.findBest()` and `decide()` applicability propagation.
2. Add reason codes to `MasteryDecision`.
3. Add transition idempotency tests.
4. Add tenant isolation tests.
5. Add maintenance-only transition and retrieval tests.
6. Add stale decision tests.

## Phase 3 — LearningDeltaService implementation

1. Implement `DefaultLearningDeltaService`.
2. Wire `LearningDeltaEvaluator`.
3. Persist evaluation outcome and state transitions.
4. Add approval/rejection/promote methods.
5. Emit trace events.
6. Add controller endpoints for approve/reject.

## Phase 4 — Real evaluation harness

1. Replace mock pass behavior.
2. Add executor SPI.
3. Implement contract/safety/trace/replay/compatibility executors.
4. Make mastered pack non-trivial.
5. Persist evaluation result as evidence.

## Phase 5 — Promotion pipeline

1. Inject evaluator/harness into promotion engine.
2. Require evaluation result, not confidence gain.
3. Update mastery item with procedure/fact/negative/eval refs.
4. Mark delta `PROMOTED`.
5. Add rollback-safe idempotency.

## Phase 6 — Runtime enforcement

1. Pass real task description/context to mode selector.
2. Enforce execution strategies in dispatcher.
3. Integrate mastery-aware retrieval before execution.
4. Add approval/verification proof checks for mode and mastery consistently.
5. Add high-risk escalation.

## Phase 7 — Obsolescence and maintenance mode

1. Add real evidence providers.
2. Wire obsolescence scan to transition service.
3. Add maintenance-only retrieval/execution tests.
4. Add security advisory quarantine test.
5. Add stale refresh workflow.

## Phase 8 — UI and operator workflow

1. Harden APIs.
2. Build mastery dashboard.
3. Build learning-delta review queue.
4. Build mode explanation panel.
5. Build obsolescence queue.
6. Add audit/evidence drilldown.

---

# 6. File-by-file task list

## `platform/java/agent-core`

### Agent definition and release

* `framework/config/AgentDefinition.java`

  * Keep current mastery/learning fields.
  * Ensure validator uses all consistency methods.
  * Make release draft carry governance refs.
  * Add tests for `toLearningContract`, `toMasteryBindings`, `toFreshnessPolicy`, `toVersionCompatibilityPolicy`.

* `framework/config/AgentDefinitionValidator.java`

  * Add learning/mastery validations.
  * Reject L3+ without eval/mastery refs.
  * Reject mastery bindings without skill refs.

* `release/AgentRelease.java`

  * Replace placeholder skill-eval/mastery maps.
  * Add digests.
  * Include refs in `releaseDigest()`.

* `release/AgentReleaseBuilder.java`

  * Add required builder fields for tenant, mastery, learning, freshness, version policy refs.
  * Validate response-serving release invariants.

### Mastery

* `mastery/MasteryRegistry.java`

  * Add typed decision request and explanation API.
  * Keep tenant-scoped APIs canonical.

* `mastery/MasteryItem.java`

  * Add optional `versionContextDigest`, `sourceReleaseDigest`, and `lastDecisionAt` if needed.
  * Consider replacing scalar `confidence` with derived score or mark deprecated.

* `mastery/MasteryDecision.java`

  * Add reason codes.
  * Ensure all factories preserve version applicability when known.

* `mastery/MasteryState.java`

  * Keep current lifecycle.
  * Review `isActiveForRetrieval()` because `OBSERVED` may be too permissive for default retrieval; likely okay only with mode-aware filtering.

* `mastery/VersionScope.java`

  * Use stronger semver/range evaluator.
  * Add tests for npm, Maven, Gradle, Python package ranges.

* `mastery/VersionRangeEvaluator.java`

  * Harden range syntax.
  * Add invalid range behavior: fail closed.

* `mastery/transition/MasteryTransitionPolicy.java`

  * Ensure transitions require evidence/evals:

    * `COMPETENT → MASTERED` requires mastered pack.
    * any → `QUARANTINED` requires safety/evidence.
    * `OBSOLETE → RETIRED` requires no active usage.

### Learning

* `learning/LearningDelta.java`

  * Keep as canonical proposed-change artifact.
  * Add optional `versionContextDigest` and `riskClass`.

* `learning/LearningDeltaService.java`

  * Expand interface.

* Add `learning/DefaultLearningDeltaService.java`

  * Implement propose/evaluate/approve/reject/promote.

* `learning/DefaultLearningDeltaEvaluator.java`

  * Add real policy hooks.
  * Add transition-policy validation for `MASTERY_STATE`.

* `learning/LearningDeltaFactory.java`

  * Ensure generated deltas include rollback refs for execution targets.
  * Ensure evidence refs are not optional for L2+.

### Evaluation

* `evaluation/EvaluationHarness.java`

  * Keep SPI.

* `evaluation/DefaultEvaluationHarness.java`

  * Replace mock pass.
  * Delegate to executor registry.

* Add:

  * `evaluation/EvaluationExecutor.java`
  * `evaluation/SafetyEvaluationExecutor.java`
  * `evaluation/TraceReplayEvaluationExecutor.java`
  * `evaluation/VersionCompatibilityEvaluationExecutor.java`
  * `evaluation/OutputContractEvaluationExecutor.java`

### Promotion

* `promotion/DefaultPromotionEngine.java`

  * Remove simplistic confidence-gain evaluation.
  * Use evaluator + harness.
  * Persist evaluation refs before transition.
  * Emit trace events.

* `promotion/DefaultPromotionPolicy.java`

  * Define strict per-target promotion thresholds.

### Obsolescence

* `obsolescence/DefaultObsolescenceDetector.java`

  * Replace string/label heuristics with evidence providers.
  * Keep labels only as manual overrides.

* Add:

  * `obsolescence/evidence/DependencyChangeEvidenceProvider.java`
  * `obsolescence/evidence/SecurityAdvisoryEvidenceProvider.java`
  * `obsolescence/evidence/ApiDiffEvidenceProvider.java`
  * `obsolescence/evidence/TestFailureEvidenceProvider.java`
  * `obsolescence/evidence/TraceFailureEvidenceProvider.java`

### Runtime mode

* `runtime/mode/MasteryAwareModeSelector.java`

  * Use `VersionContextCodec`.
  * Include real task info.
  * Return reason codes.

* `runtime/mode/DefaultModeSelectionPolicy.java`

  * Tighten maintenance semantics.
  * Add novelty/risk escalation.
  * Add tests for every matrix case.

### Memory retrieval

* `memory/retrieval/MasteryAwareMemoryRetriever.java`

  * Make it a real query pipeline over `MemoryPlane`.
  * Emit selected/rejected memory reasons.
  * Always prioritize negative knowledge.

---

## `products/data-cloud`

### Runtime

* `planes/action/agent-runtime/src/main/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcher.java`

  * Use real version context snapshot.
  * Use codec.
  * Pass real task description/context.
  * Enforce strategies.
  * Wire memory retriever.
  * Ensure all trace events are emitted.

* `planes/action/agent-runtime/src/main/java/com/ghatana/agent/audit/TraceEventType.java`

  * Keep current event list.
  * Add event payload contracts/tests.

### Data Cloud mastery persistence

* `extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/mastery/DataCloudMasteryRegistry.java`

  * Fix applicability propagation.
  * Fix ranking.
  * Add atomic/idempotent transitions.
  * Add indexes/queries.
  * Add tenant isolation hardening.

* `extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/mastery/DataCloudMasteryTransitionRepository.java`

  * Ensure append-only idempotent transition log.
  * Enforce tenant ID.

* `extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/learning/delta/DataCloudLearningDeltaRepository.java`

  * Ensure state transition updates are idempotent.
  * Add query by state, skill, agent, tenant.
  * Add optimistic locking.

### API

* `delivery/api/src/main/java/com/ghatana/datacloud/api/controller/MasteryController.java`

  * Replace query-param tenant trust with principal-derived tenant.
  * Add DTOs.
  * Add approve/reject endpoints.
  * Add mode explanation with real `ModeSelectionResult`, not only mastery state.
  * Validate params safely.

* `delivery/api/src/main/java/com/ghatana/datacloud/api/controller/LearningDeltaController.java`

  * Consolidate with or separate from `MasteryController` cleanly.
  * Ensure no duplicate responsibilities.

### UI

* `delivery/ui/src/api/mastery.service.ts`

  * Add typed client methods.
  * Include trace IDs and reason codes.
  * Handle governance denial.

* `delivery/ui/src/pages/MasteryPage.tsx`

  * Add mastery dashboard, learning queue, obsolescence queue, mode explanation, evidence drilldown.

---

# 7. Required test plan

## Unit tests

Add/extend:

* `MasteryStateTest`
* `MasteryDecisionTest`
* `VersionScopeTest`
* `VersionContextCodecTest`
* `DefaultModeSelectionPolicyTest`
* `DefaultLearningDeltaEvaluatorTest`
* `DefaultLearningDeltaServiceTest`
* `DefaultPromotionPolicyTest`
* `DefaultPromotionEngineTest`
* `DefaultObsolescenceDetectorTest`
* `MasteryAwareMemoryRetrieverTest`

## Integration tests

Add:

* `DataCloudMasteryRegistryTenantIsolationTest`
* `DataCloudLearningDeltaRepositoryTest`
* `GovernedAgentDispatcherMasteryIT`
* `LearningDeltaPromotionFlowIT`
* `ObsolescenceTransitionFlowIT`
* `MasteryAwareRetrievalIT`

## E2E tests

Existing `GaaMasteryLifecycleE2ETest` should be expanded to cover:

```text
UNKNOWN → OBSERVED
OBSERVED → PRACTICED
PRACTICED → approval-gated execution
PRACTICED → COMPETENT after eval
COMPETENT → verification-gated execution
COMPETENT → MASTERED after mastered pack
MASTERED → deterministic execution
MASTERED → MAINTENANCE_ONLY after version drift
MAINTENANCE_ONLY → legacy-only execution
OBSOLETE → blocked
QUARANTINED → blocked
```

Also add:

```text
LearningDelta proposed
→ evaluated
→ approved
→ promoted
→ mastery updated
→ trace events emitted
```

---

# 8. Definition of done

The goal is reached when all of these are true:

1. Every active/canary agent release has learning, memory, mastery, evaluation, policy, freshness, and version compatibility contracts.
2. Every dispatch resolves version context and mastery decision.
3. Every skill execution has mode selection: deterministic, bounded, verification-first, fast-learning, maintenance-only, human-gated, or blocked.
4. Every learning update is a `LearningDelta`.
5. No learning delta mutates active behavior without evaluation and promotion.
6. `COMPETENT` and `MASTERED` require real evaluation results, not mock passes.
7. Maintenance-only knowledge is retrieved only for matching legacy context.
8. Obsolete/quarantined/retired knowledge is excluded from active retrieval and execution.
9. Negative knowledge is retrieved before positive procedures.
10. Obsolescence events can demote, quarantine, or retire mastery items.
11. UI/API allow operators to inspect, approve, reject, promote, and explain every learning/mastery decision.
12. Trace ledger can reconstruct why an agent acted, learned, promoted, blocked, or became stale.

---

# 9. Highest-priority next implementation batch

I would start with this exact batch:

1. **Add `VersionContextCodec` and remove all `toString()`/manual version context parsing.**
2. **Implement `DefaultLearningDeltaService`.**
3. **Replace `DefaultEvaluationHarness.runTestCase()` mock pass behavior.**
4. **Update `DefaultPromotionEngine` to use evaluator + harness + policy.**
5. **Fix `DataCloudMasteryRegistry.decide()` to preserve computed version applicability in `MasteryDecision`.**
6. **Make `GovernedAgentDispatcher` pass real task description/context into mode selection.**
7. **Make `MasteryAwareMemoryRetriever` call `MemoryPlane` and emit rejection reasons.**
8. **Harden `MasteryController` tenant/auth behavior.**
9. **Add E2E tests for the full learning-delta → eval → promotion → mastery transition → governed dispatch loop.**

That batch turns the current partial implementation into a usable governed self-learning loop.

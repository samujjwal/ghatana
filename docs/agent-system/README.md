## Implementation north star

Use the current commit’s existing direction and wire it into one enforced path:

```text
AgentDefinition
→ AgentRelease
→ LearningContract
→ MasteryRegistry
→ VersionContext
→ ModeSelectionPolicy
→ GovernedAgentDispatcher
→ MemoryPlane
→ LearningDelta
→ Evaluation
→ Promotion
→ MasteryTransition
```

Core rule:

> A GAA may observe freely, propose cautiously, and act only within governed, tenant-safe, version-compatible, evidence-backed mastery.

The repo already has most of the primitives: `AgentDefinition` now materializes learning, mastery, freshness, and version-compatibility contracts; `LearningLevel`/`LearningContract` define target-scoped learning authority; `MasteryItem`/`MasteryState` define the mastery lifecycle; `VersionScope`/`VersionConstraint` model active/maintenance/obsolete version applicability; `LearningDelta` models proposed learning; `GovernedAgentDispatcher` is the runtime enforcement point; and `MemoryPlane` is already multi-tier.          

---

# Phase 0 — Fix safety-critical correctness first

## 0.1 `platform/java/agent-core/src/main/java/com/ghatana/agent/runtime/mode/MasteryAwareModeSelector.java`

### Current issue

`MasteryAwareModeSelector` calls `selectionPolicy.selectMode(...)`, but then wraps the result into a new `ModeSelectionResult(..., false, false)`, which drops `requiresApproval` and `requiresVerification`. 

### Required change

Preserve policy result flags:

```java
return selectionPolicy.selectMode(masteryDecision, taskClassification, versionContext)
    .map(policyResult -> new ModeSelectionResult(
        policyResult.mode(),
        policyResult.reasoning(),
        policyResult.requiresApproval(),
        policyResult.requiresVerification()
    ));
```

Also fix the overload that derives tenant from:

```java
versionContext.dependencies().getOrDefault("tenant", "default")
```

Tenant must come from `AgentContext`, `MasteryQuery`, or explicit parameter, not from dependencies.

### Acceptance tests

Create/update:

```text
platform/java/agent-core/src/test/java/com/ghatana/agent/runtime/mode/MasteryAwareModeSelectorTest.java
```

Tests:

* `PRACTICED` returns `requiresApproval=true`.
* `COMPETENT` returns `requiresVerification=true`.
* `MAINTENANCE_ONLY` returns approval-required mode.
* Tenant is not derived from dependency map.

---

## 0.2 `platform/java/agent-core/src/main/java/com/ghatana/agent/promotion/DefaultPromotionEngine.java`

### Current issue

Promotion calls:

```java
masteryRegistry.findBySkill(delta.skillId(), null)
```

But `MasteryRegistry.findBySkill` requires an `EnvironmentFingerprint`, and `DataCloudMasteryRegistry.findBySkill` dereferences `env.tenantId()`.   

### Required change

Replace the null environment lookup with tenant-scoped query:

```java
MasteryQuery query = MasteryQuery.bySkill(delta.skillId())
    .withTenantId(tenantId)
    .withAgentId(delta.agentId())
    .withAgentReleaseId(delta.agentReleaseId());
```

Then use:

```java
masteryRegistry.query(query)
```

or add a new registry method:

```java
Promise<Optional<MasteryItem>> findBySkill(
    String tenantId,
    String skillId,
    String agentId,
    String agentReleaseId,
    VersionContext versionContext
);
```

### Also implement

If no mastery item exists:

1. Create initial `MasteryItem` in `UNKNOWN` or `OBSERVED`.
2. Save it.
3. Transition it through policy using evaluation evidence.
4. Then mark delta `PROMOTED`.

Do not fail promotion just because the first mastery item does not exist.

### Acceptance tests

Create/update:

```text
platform/java/agent-core/src/test/java/com/ghatana/agent/promotion/DefaultPromotionEngineTest.java
```

Tests:

* Promotion does not pass null environment.
* Existing mastery item transitions correctly.
* Missing mastery item creates initial item.
* Failed transition does not mark delta as promoted.
* Delta state updates to `PROMOTED` only after mastery transition succeeds.

---

## 0.3 `platform/java/agent-core/src/main/java/com/ghatana/agent/release/AgentRelease.java`

### Current issue

`tenantId()` currently returns `agentId`, with a TODO to add explicit tenant ID. 

### Required change

Add explicit field:

```java
String tenantId
```

Update constructor validation:

```java
if (tenantId == null || tenantId.isBlank()) {
    throw new IllegalArgumentException("tenantId must not be blank");
}
```

Remove:

```java
public String tenantId() {
    return agentId;
}
```

### Update dependent files

* `AgentReleaseBuilder.java`
* `AgentReleaseRepository.java`
* `AgentReleaseRepositoryContractTest.java`
* `AgentReleaseTest.java`
* `DataCloudAgentReleaseRepository.java`
* `DataCloudAgentReleaseRepositoryTest.java`
* `GovernedAgentDispatcher.java`

### Acceptance tests

* Agent release cannot be created without tenant ID.
* `findGoverningRelease(agentId, tenantId)` returns tenant-scoped release.
* Dispatcher uses release tenant, not agent ID.

---

## 0.4 `platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/MasteryItem.java`

### Current issue

`MasteryItem` validates/copies most fields, but `stateHistory` is not visibly validated/copied in the compact constructor. 

### Required change

Add:

```java
Objects.requireNonNull(stateHistory, "stateHistory must not be null");
stateHistory = List.copyOf(stateHistory);
```

### Acceptance test

```text
platform/java/agent-core/src/test/java/com/ghatana/agent/mastery/MasteryItemTest.java
```

Tests:

* Null `stateHistory` rejected.
* Mutable input list does not mutate record after construction.

---

# Phase 1 — Canonicalize execution mode semantics

## Problem

`ExecutionMode` mixes two concepts:

1. Execution strategy:

   * deterministic
   * bounded probabilistic
   * fast learning
   * maintenance-only
   * verification-first

2. Supervision level:

   * autonomous
   * supervised
   * human-gated
   * blocked

The enum currently contains both groups. 

`DefaultModeSelectionPolicy` and `DataCloudMasteryRegistry` then map mastery states differently.  

## 1.1 `platform/java/agent-core/src/main/java/com/ghatana/agent/runtime/mode/ExecutionMode.java`

### Required change

Replace mixed enum with two enums.

New file:

```text
platform/java/agent-core/src/main/java/com/ghatana/agent/runtime/mode/ExecutionStrategy.java
```

```java
public enum ExecutionStrategy {
    DETERMINISTIC_EXECUTION,
    BOUNDED_PROBABILISTIC_REASONING,
    EXPLORATORY_FAST_LEARNING,
    MAINTENANCE_ONLY,
    VERIFICATION_FIRST
}
```

New file:

```text
platform/java/agent-core/src/main/java/com/ghatana/agent/runtime/mode/SupervisionMode.java
```

```java
public enum SupervisionMode {
    AUTONOMOUS,
    SUPERVISED,
    HUMAN_GATED,
    BLOCKED
}
```

Keep old `ExecutionMode` temporarily only as deprecated compatibility if needed.

---

## 1.2 `platform/java/agent-core/src/main/java/com/ghatana/agent/runtime/mode/ModeSelectionPolicy.java`

### Required change

Change result record to:

```java
record ModeSelectionResult(
    @NotNull ExecutionStrategy strategy,
    @NotNull SupervisionMode supervision,
    @NotNull String reasoning,
    boolean requiresApproval,
    boolean requiresVerification
) {}
```

### Acceptance criteria

* A strategy can be `MAINTENANCE_ONLY` while supervision is `HUMAN_GATED`.
* A strategy can be `DETERMINISTIC_EXECUTION` while supervision is `AUTONOMOUS`.
* Blocking is represented as supervision, not strategy.

---

## 1.3 `platform/java/agent-core/src/main/java/com/ghatana/agent/runtime/mode/DefaultModeSelectionPolicy.java`

### Required mapping

| Mastery state                  | Version applicability | Strategy                          | Supervision                                    |
| ------------------------------ | --------------------- | --------------------------------- | ---------------------------------------------- |
| `MASTERED`                     | `ACTIVE`              | `DETERMINISTIC_EXECUTION`         | `AUTONOMOUS`                                   |
| `COMPETENT`                    | `ACTIVE`              | `BOUNDED_PROBABILISTIC_REASONING` | `SUPERVISED` or `AUTONOMOUS` with verification |
| `PRACTICED`                    | `ACTIVE`              | `EXPLORATORY_FAST_LEARNING`       | `HUMAN_GATED`                                  |
| `OBSERVED`                     | any non-obsolete      | `VERIFICATION_FIRST`              | `HUMAN_GATED`                                  |
| `MAINTENANCE_ONLY`             | `MAINTENANCE`         | `MAINTENANCE_ONLY`                | `HUMAN_GATED`                                  |
| any                            | `OBSOLETE`            | `VERIFICATION_FIRST`              | `BLOCKED`                                      |
| `OBSOLETE/RETIRED/QUARANTINED` | any                   | `VERIFICATION_FIRST`              | `BLOCKED`                                      |
| `UNKNOWN`                      | any                   | `EXPLORATORY_FAST_LEARNING`       | `HUMAN_GATED`                                  |

### Acceptance tests

```text
platform/java/agent-core/src/test/java/com/ghatana/agent/runtime/mode/DefaultModeSelectionPolicyTest.java
```

Tests:

* Mastered active skill is deterministic + autonomous.
* Maintenance-only skill is maintenance + human-gated.
* Obsolete version is blocked.
* Competent skill requires verification.
* Practiced skill requires approval.

---

## 1.4 `products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/mastery/DataCloudMasteryRegistry.java`

### Required change

Remove local mode mapping from registry. `DataCloudMasteryRegistry` should return `MasteryDecision` only.

The registry should not own:

```java
determineExecutionMode(...)
```

Mode selection belongs in `DefaultModeSelectionPolicy`.

### Acceptance criteria

* `DataCloudMasteryRegistry.decide()` returns mastery state, confidence, evidence, version scope, executable flag.
* No execution strategy/supervision mapping lives in registry.

---

# Phase 2 — Make learning deltas durable and tenant-safe

## 2.1 `products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/learning/delta/DataCloudLearningDeltaRepository.java`

### Current issue

This class is still in-memory using `ConcurrentHashMap`, with a TODO to replace with Data Cloud persistence. 

### Required change

Replace:

```java
private final ConcurrentHashMap<String, Map<String, Object>> entityStore
```

with:

```java
private final EntityRepository entityRepository;
private static final String COLLECTION = "agent-learning-deltas";
```

Implement all methods using:

```java
entityRepository.save(...)
entityRepository.findAll(...)
entityRepository.count(...)
```

### Required behavior

* All reads/writes require tenant ID.
* `findByAgentId` should be tenant-scoped or deprecated.
* `findBySkillId` should be tenant-scoped or deprecated.
* `findByState` should require tenant ID.
* `updateState` must preserve all fields and update correct timestamps.
* `transition` should validate legal delta-state transitions.

### Acceptance tests

Update:

```text
products/data-cloud/extensions/agent-registry/src/test/java/com/ghatana/datacloud/agent/learning/delta/DataCloudLearningDeltaRepositoryTest.java
```

Tests:

* Save/read round trip.
* Tenant isolation.
* State update preserves payload.
* `PENDING_HUMAN_REVIEW` is queryable.
* Restart/persistence test using Data Cloud test repository, not map.
* `findPromotable()` returns only `EVALUATED`/`APPROVED`.
* `findPendingEvaluation()` returns only `PROPOSED`/`PENDING_EVALUATION`.

---

## 2.2 `products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/learning/delta/LearningDeltaMapper.java`

### Required change

Ensure all fields from `LearningDelta` round trip:

* `deltaId`
* `type`
* `target`
* `state`
* `agentId`
* `agentReleaseId`
* `skillId`
* `tenantId`
* `procedureId`
* `semanticFactId`
* `negativeKnowledgeId`
* `contentDigest`
* `proposedContent`
* `evidenceRefs`
* `evaluationRefs`
* `sourceEpisodeIds`
* `rollbackRef`
* `confidenceBefore`
* `confidenceAfter`
* `requiresHumanReview`
* `proposedBy`
* timestamps
* `labels`
* `rejectionReason`

### Acceptance tests

* Empty optional IDs remain null.
* Lists remain lists.
* Maps remain maps.
* Timestamps preserve values.
* Rejection reason survives update.

---

# Phase 3 — Align `LearningEngine` with evaluator and promotion

## 3.1 `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/learning/LearningEngine.java`

### Current issue

`LearningEngine` creates procedural-skill deltas, but `DefaultLearningDeltaEvaluator` rejects procedural-skill deltas if `procedureId` is null and requires confidence `>= 0.8`.  

### Required change

Before creating a `PROCEDURAL_SKILL` delta:

1. Create a candidate procedure artifact.
2. Persist it in memory/artifact/procedure store.
3. Use the resulting `procedureId` in the delta.
4. Add a rollback ref.
5. Attach source episodes as evidence refs.

Pseudo-flow:

```java
EnhancedProcedure candidateProcedure = buildCandidateProcedure(candidate, episodes);
EnhancedProcedure saved = memoryPlane.storeProcedure(candidateProcedure).awaitOrCompose();

LearningDelta delta = LearningDeltaFactory.proposeProceduralSkill(
    tenantId,
    agentId,
    agentReleaseId,
    skillId,
    saved.getId(),
    rollbackRef,
    content,
    evidenceRefs,
    sourceEpisodeIds,
    "learning-engine"
);
```

### Also change

Do not drop below-threshold candidates. Create reviewable deltas:

```java
if (candidate.confidence() < humanReviewThreshold) {
    delta = delta.withState(PENDING_HUMAN_REVIEW);
    delta = delta.withRequiresHumanReview(true);
}
```

### Acceptance tests

```text
platform/java/agent-core/src/test/java/com/ghatana/agent/framework/learning/LearningEngineTest.java
```

Tests:

* L3+ without repository fails.
* L3+ with repository creates deltas.
* Procedural deltas include `procedureId`.
* Low-confidence candidate becomes `PENDING_HUMAN_REVIEW`.
* Learning contract rejection increments rejected count and does not save delta.
* Tenant is not `"default"` unless explicitly configured.

---

## 3.2 `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/LearningDeltaFactory.java`

### Required additions

Add explicit factories:

```java
proposeProceduralSkill(...)
proposeSemanticFact(...)
proposeNegativeKnowledge(...)
proposeRetrievalPolicy(...)
pendingHumanReview(...)
```

Each should enforce required fields for target.

For procedural skill:

* `procedureId` required
* `rollbackRef` required
* `evidenceRefs` non-empty
* `contentDigest` computed
* `confidenceBefore`/`confidenceAfter` set

### Acceptance tests

```text
platform/java/agent-core/src/test/java/com/ghatana/agent/learning/LearningDeltaFactoryTest.java
```

Tests:

* Procedural skill without procedure ID fails.
* Execution target without rollback ref fails.
* Digest is stable for same content.
* Human-review delta state is correct.

---

## 3.3 `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/DefaultLearningDeltaEvaluator.java`

### Current state

Evaluator is a good start and already routes low confidence to human review in generic confidence check, but target-specific procedural-skill validation rejects confidence below `0.8` before that generic routing can happen. 

### Required change

For procedural skills:

* If confidence is below procedural threshold but evidence exists, return `pendingHumanReview`, not `rejected`.
* Reject only if required structure is missing or safety constraints fail.

Suggested behavior:

```java
if (delta.confidenceAfter() < 0.8) {
    return EvaluationResult.pendingHumanReview(...);
}
```

### Acceptance tests

* Procedural skill with confidence `0.75` and evidence goes to review.
* Procedural skill without procedure ID is rejected.
* Planner/model-adapter targets still require stricter gates.

---

# Phase 4 — Mastery registry and transition correctness

## 4.1 `platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/MasteryRegistry.java`

### Current issue

The API still includes `findBySkill(skillId, EnvironmentFingerprint env)`, which allows null misuse and hides tenant/version semantics. 

### Required change

Add or replace with explicit query-first methods:

```java
Promise<Optional<MasteryItem>> findBest(MasteryQuery query);
Promise<MasteryDecision> decide(MasteryQuery query);
```

Deprecate:

```java
findBySkill(String skillId, EnvironmentFingerprint env)
```

or change to:

```java
findBySkill(String tenantId, String skillId, VersionContext versionContext)
```

### Acceptance criteria

* No call site passes null environment.
* Tenant is required.
* Version context can participate in match/rank.

---

## 4.2 `products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/mastery/DataCloudMasteryRegistry.java`

### Required changes

1. Use `MasteryQuery` as primary path.
2. Filter by tenant ID from query.
3. Rank by:

   * version applicability: active > maintenance > unknown
   * mastery state: mastered > competent > practiced > observed
   * execution score
   * freshness
4. Do not exclude `MAINTENANCE_ONLY` before version classification.
5. Do not make final execution-mode decision.
6. Make transition atomic.

### Current concern

Transition saves the updated item, then appends transition. If append fails, item state and transition log diverge. 

### Required change

Use one of:

* entity repository transaction
* durable outbox
* append transition first with pending state, update item, mark transition committed
* idempotent retry with transition ID

### Acceptance tests

```text
products/data-cloud/extensions/agent-registry/src/test/java/com/ghatana/datacloud/agent/mastery/DataCloudMasteryRegistryTest.java
```

Tests:

* Tenant isolation.
* Maintenance-only item is returned for legacy version.
* Obsolete item is blocked.
* Stale item excluded when freshness required.
* Transition policy is enforced.
* Transition append failure does not silently corrupt item state.

---

## 4.3 `products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/mastery/MasteryItemMapper.java`

### Required change

Ensure complete round trip for:

* `stateHistory`
* `evidenceRefs`
* `evaluationRefs`
* `knownFailureModeIds`
* `procedureIds`
* `semanticFactIds`
* `negativeKnowledgeIds`
* `VersionScope`
* `ApplicabilityScope`
* `MasteryScore`
* `labels`
* `confidence`

### Acceptance tests

* Save/read item with all fields populated.
* Evidence refs remain `List<String>`, not accidentally map-converted.
* Version scope constraints round trip with kind/name/range/ecosystem.
* State history round trips.

---

# Phase 5 — Agent release and definition contract cleanup

## 5.1 `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/config/AgentDefinition.java`

### Current state

`AgentDefinition` already materializes learning, mastery, freshness, and version compatibility policy objects. 

### Required improvements

1. Replace raw `String learningLevel` with typed `LearningLevel` in internal model if possible.
2. Replace raw `Map<String,Object> masteryBindings` with typed DTO/model at materialization boundary.
3. Validate `skillRefs` non-empty when mastery bindings exist.
4. Validate `masteryPolicyRefs` exist when learning level is `L3+`.
5. Validate `evaluationRefs` exist when learning level is `L3+`.
6. Ensure `toVersionCompatibilityPolicy()` builds real `VersionScope` from metadata instead of default empty scope.

### Acceptance tests

```text
platform/java/agent-core/src/test/java/com/ghatana/agent/framework/config/AgentDefinitionTest.java
```

Tests:

* Learning level mismatch throws.
* Invalid adaptation target reports validation error.
* L3 without evaluation refs fails validation.
* Mastery-bound agent without skill refs fails validation.
* Canonical digest changes when mastery bindings/skill refs change.

---

## 5.2 `platform/java/agent-core/src/main/java/com/ghatana/agent/release/AgentRelease.java`

### Current state

Release already requires redaction profile, threat model, permitted purposes, capability maturity, evaluation pack, and memory contract. 

### Required improvements

1. Add `tenantId`.
2. Replace `compatibleRuntimeVersions: List<String>` with typed constraints or policy ref.
3. Add `masteryPolicyPackId`.
4. Add `learningContractId` or embedded learning contract digest.
5. Add `versionCompatibilityPolicyId`.
6. Add `freshnessPolicyId`.

### Acceptance tests

* Response-serving release cannot be created without eval/memory/mastery policy references.
* Release digest changes when mastery or learning policy changes.
* Shadow release can run but cannot serve responses.

---

# Phase 6 — Runtime dispatcher enforcement

## 6.1 `products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcher.java`

### Current state

This class is already the correct enforcement point. It handles release guard, version context, mastery check, mode selection, approval/verification, invariant checks, tracing, and delegate dispatch. 

### Required changes

1. Resolve `VersionContext` once per dispatch.
2. Do not call `.getResult()` inside async dispatch flow; compose promises.
3. Require `skillId` for mastery-bound agents.
4. If mastery registry is configured and no mastery is found:

   * block, or
   * route to fast-learning/human-gated mode only if policy allows.
5. Preserve mode approval/verification flags.
6. Store selected strategy/supervision in context.
7. Deny execution when:

   * release cannot serve response
   * mode is blocked
   * approval required and missing
   * verification required and missing
   * version applicability obsolete
   * mastery state quarantined/retired/obsolete
8. Emit trace events for:

   * version context resolved
   * mastery decision
   * mode selected
   * approval checked
   * verification checked
   * dispatch denied
   * dispatch allowed
   * learning delta created, if available later

### Acceptance tests

```text
products/data-cloud/planes/action/agent-runtime/src/test/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcherTest.java
```

Tests:

* Shadow release cannot serve normal response.
* Blocked release denies.
* Obsolete mastery denies.
* Practiced mastery requires approval.
* Competent mastery requires verification proof.
* Missing skill ID denies when mastery-bound.
* Mode selection failure denies rather than silently continuing.
* Trace ledger receives mastery/mode/version events.

---

# Phase 7 — Memory retrieval governance

## 7.1 `products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/store/MemoryPlane.java`

### Current state

`MemoryPlane` supports episodic, semantic, procedural, typed artifacts, cross-tier query, semantic search, working memory, task-state memory, and checkpointing. 

### Required additions

Extend `MemoryQuery` rather than the SPI if possible.

Add to `MemoryQuery`:

* `tenantId`
* `agentId`
* `skillId`
* `versionContext`
* `allowedMasteryStates`
* `includeMaintenanceOnly`
* `includeNegativeKnowledge`
* `excludeObsolete`
* `requireFreshness`
* `purpose`
* `riskLevel`

### Acceptance criteria

* Procedural memory retrieval is version-aware.
* Negative knowledge retrieval can be requested explicitly.
* Obsolete memory excluded by default.
* Maintenance-only retrieved only when allowed.

---

## 7.2 `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/lifecycle/memory/GovernedMemoryPlane.java`

### Current state

It applies redaction before writes and tenant filtering after reads. 

### Required changes

1. Enforce lifecycle labels on procedures/facts:

   * exclude `obsolete`
   * exclude `retired`
   * allow `maintenance-only` only when query permits
2. Enforce purpose-based access:

   * learning
   * execution
   * audit
   * evaluation
3. Ensure `TypedArtifact` writes are not blindly delegated when artifact contains sensitive content.
4. Add read filtering by `skillId` and `versionContext`.

### Acceptance tests

* PII is redacted before write.
* Tenant mismatch is rejected.
* Obsolete procedure is not returned.
* Maintenance-only procedure requires legacy query flag.
* Typed artifact with sensitive classification goes through governance.

---

# Phase 8 — Version context and compatibility

## 8.1 `platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/VersionScope.java`

### Current state

Version range support is much better: compound ranges, caret, tilde, Maven, prerelease. 

### Required hardening

1. Add tests for:

   * `^0.x` semantics
   * npm prerelease behavior
   * Maven open-ended ranges
   * invalid ranges
   * Java versions like `21`
   * Gradle/Maven versions
   * Node versions with `v20.11.0`
2. Decide whether to use a mature semver library for npm cases.
3. Keep conservative false on invalid ranges.

### Acceptance tests

```text
platform/java/agent-core/src/test/java/com/ghatana/agent/mastery/VersionScopeTest.java
```

Scenarios:

* `react-router@7.1.0` matches `>=7.0.0 <8.0.0`.
* `react-router@6.22.0` matches maintenance range.
* `react-router@4.0.0` matches obsolete range.
* `1.2.3-alpha` compares lower than `1.2.3`.
* Maven `[1.0,2.0)` works.

---

## 8.2 `VersionContextResolver`, `EnvironmentSnapshot`, related context files

### Required changes

1. Capture versions from:

   * `package.json`
   * lock files
   * Gradle files
   * Maven files
   * Python files
   * Dockerfile / compose
   * runtime/tool versions
2. Capture repo conventions:

   * React Router framework mode
   * React Router data-router mode
   * Next.js app router
   * Gradle Kotlin DSL
   * pnpm workspace
3. Make resolver testable with supplied project root instead of global process state.

### Acceptance tests

* Test fixture with React Router v6.
* Test fixture with React Router v7.
* Test fixture with Gradle Java 21.
* Test fixture with mixed frontend/backend dependencies.

---

# Phase 9 — Obsolescence, freshness, and maintenance-only workflow

## 9.1 `DefaultObsolescenceDetector.java`

### Required behavior

Detect stale/obsolete knowledge from real evidence, not only labels.

Triggers:

* dependency version changed
* API contract changed
* official docs snapshot contradicts memory
* security advisory found
* runtime/platform changed
* repeated failures
* repo conventions changed
* better procedure consistently outperforms old one

### Output

Emit `MasteryTransition` proposals:

* `MASTERED → MAINTENANCE_ONLY`
* `ANY → OBSOLETE`
* `ANY → QUARANTINED`
* `OBSOLETE → RETIRED`

### Acceptance tests

* Version upgrade marks old skill maintenance-only.
* Security advisory marks skill quarantined.
* Repeated failures reduce confidence and require verification.
* Documentation contradiction marks stale/obsolete.

---

## 9.2 `MasteryRegistry.findStale(...)`

### Current state

Tenant-scoped stale lookup exists in `DataCloudMasteryRegistry`. 

### Required changes

1. Add scheduled worker:

   * `MasteryFreshnessWorker`
2. For each stale item:

   * create freshness evidence
   * transition to verification-required state or mark stale label
   * do not immediately obsolete unless stronger evidence exists

### Acceptance criteria

* Stale mastered skill no longer executes autonomously.
* Stale competent skill requires verification.
* Freshness events are auditable.

---

# Phase 10 — Evaluation and promotion packs

## 10.1 New package

Create:

```text
platform/java/agent-core/src/main/java/com/ghatana/agent/evaluation/pack/
```

Recommended files:

* `EvaluationPack.java`
* `EvaluationCase.java`
* `EvaluationRun.java`
* `EvaluationRunResult.java`
* `EvaluationPackRepository.java`
* `EvaluationRunner.java`

### EvaluationPack fields

```java
record EvaluationPack(
    String evaluationPackId,
    String tenantId,
    String skillId,
    String version,
    VersionScope versionScope,
    List<EvaluationCase> cases,
    List<String> requiredEvidenceTypes,
    double minPassRate,
    boolean requiresRegression,
    boolean requiresSafety
) {}
```

### Required eval categories

* skill unit
* integration
* regression
* safety
* version compatibility
* retrieval quality
* prompt/tool injection
* rollback/recovery
* abstention/refusal

---

## 10.2 `DefaultPromotionPolicy.java`

### Required behavior

Promotion target should be based on evidence:

| Target state       | Required evidence                                    |
| ------------------ | ---------------------------------------------------- |
| `OBSERVED`         | one verified trace/source                            |
| `PRACTICED`        | repeated episodes/sandbox                            |
| `COMPETENT`        | procedure exists + basic eval pass                   |
| `MASTERED`         | regression + safety + recovery + version tests       |
| `MAINTENANCE_ONLY` | newer active version exists + old version still used |
| `OBSOLETE`         | contradiction/security/runtime/docs/repeated failure |
| `QUARANTINED`      | unsafe behavior or failed safety eval                |

### Acceptance tests

* Cannot promote to mastered without eval refs.
* Cannot quarantine without safety evidence.
* Cannot retire directly from active mastered unless policy allows.

---

# Phase 11 — Product/YAML definition alignment

## Files

```text
products/yappc/config/agents/definitions/**/*.yaml
products/yappc/config/agents/README.md
products/yappc/scripts/validate_agents.py
platform/agent-catalog/schema-migration.ts
```

### Required changes

1. Add/standardize fields:

   * `learningLevel`
   * `adaptationTargets`
   * `masteryBindings`
   * `skillRefs`
   * `masteryPolicyRefs`
   * `evaluationRefs`
   * `memoryBindings`
   * `securityContract`
   * `observabilityContract`
2. Validate:

   * L3+ requires promotion.
   * L2+ requires provenance.
   * mastery-bound agents require skill refs.
   * skill refs must resolve to mastery registry entries or bootstrap entries.
3. Create seed mastery YAML for key skills:

   * Java class writing
   * React UI generation
   * requirements drafting
   * acceptance criteria formatting
   * drift detection
   * memory capability

### Acceptance tests

* YAML validation fails for L3 without eval refs.
* YAML validation fails for mastery binding without registry ref.
* YAML validation fails for invalid learning target.
* Agent config materializer creates typed `LearningContract`.

---

# Phase 12 — End-to-end tests

## Required E2E scenarios

### 12.1 Mastered skill execution

```text
Given skill is MASTERED for react-router@7
When task asks for v7 routing
Then strategy = DETERMINISTIC_EXECUTION
And supervision = AUTONOMOUS
And version context is recorded
And trace includes mastery decision
```

### 12.2 Maintenance-only legacy execution

```text
Given skill is MAINTENANCE_ONLY for react-router@6
When repo uses react-router@6
Then strategy = MAINTENANCE_ONLY
And supervision = HUMAN_GATED
And approval is required
```

### 12.3 Obsolete skill blocked

```text
Given skill is OBSOLETE for react-router@4
When repo uses react-router@4
Then dispatch is denied
And trace says obsolete version
```

### 12.4 Fast-learning new version

```text
Given no mastery exists for react-router@8
When task is exploratory
Then mode is EXPLORATORY_FAST_LEARNING + HUMAN_GATED
And learning deltas are tentative
And no active mastery promotion happens without eval
```

### 12.5 Learning delta promotion

```text
Given repeated successful episodes
When LearningEngine reflects
Then LearningDelta is created
And EvaluationPack passes
And PromotionEngine transitions mastery
And delta becomes PROMOTED
```

### 12.6 Poisoned memory blocked

```text
Given untrusted memory says unsafe action is allowed
When execution asks for side-effecting tool
Then policy/tool governance blocks unsafe action
And memory is not promoted
```

---

# Final sequencing

Implement in this order:

1. **Safety correctness**

   * `MasteryAwareModeSelector`
   * `DefaultPromotionEngine`
   * `AgentRelease.tenantId`
   * `LearningEngine.tenantId`
   * `MasteryItem.stateHistory`

2. **Durability**

   * `DataCloudLearningDeltaRepository`
   * `LearningDeltaMapper`
   * repository tests

3. **Canonical mode model**

   * split strategy vs supervision
   * update policy/dispatcher/registry

4. **Promotion/evaluation alignment**

   * candidate procedures
   * learning delta review states
   * promotion policy

5. **Version-aware mastery**

   * typed release constraints
   * version context resolver
   * memory query policy

6. **Runtime enforcement**

   * dispatcher denies unsafe/missing approval/missing verification
   * full trace events

7. **Obsolescence and maintenance lifecycle**

   * freshness worker
   * obsolescence detector
   * transition evidence

8. **YAML/product alignment**

   * validate all agent definitions
   * seed mastery/skills

9. **End-to-end tests**

   * mastered
   * competent
   * practiced
   * maintenance-only
   * obsolete
   * fast-learning
   * promotion

This plan should move the codebase from “concepts exist” to “GAA is actually governed at runtime.”

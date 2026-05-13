## Scope reviewed

I scanned the `samujjwal/ghatana` repository at commit head `8c54dfe61fe221860f49dfc063a7e243eea37d6c`. The commit itself is changelog-only, so this review is against the **full repository snapshot at that SHA**, not the diff.

The agent system is already much further along than the earlier conceptual discussion: the repo now contains initial implementations for mastery state, mastery registry, learning deltas, promotion engine, obsolescence detection, version-aware scope, execution modes, mastery-aware mode selection, Data Cloud persistence, and API/UI surfaces.

I did a static code scan through GitHub. I did not clone the repo or run Gradle/tests in this environment.

---

# 1. Current state: what already exists

## 1.1 Five-layer architecture is already defined

ADR-020 is the canonical architectural source. It defines five layers:

1. Specification and Release Plane
2. Control and Governance Plane
3. Execution Plane
4. Memory, Context, and Evaluation Plane
5. Product Capability Plane

It also explicitly states the existing problems: documentation drift, split registry authority, partial spec enforcement, and missing release model. The ADR says governance, evaluation, and assurance were labels rather than runtime behavior, which is still visible in several current implementations.

## 1.2 `AgentDefinition` has been extended toward mastery-aware GAA

`AgentDefinition` is no longer only identity/capability/tool metadata. At this commit, it includes:

* learning-level materialization via `toLearningContract()`
* mastery bindings
* skill refs
* mastery policy refs
* freshness policy materialization
* version compatibility policy materialization
* learning-level consistency validation
* canonical digest including mastery fields

This is the right direction: agent definitions now know which mastery registry, freshness policy, version policy, and skills they bind to.

## 1.3 Mastery model exists

The repo already has a good first version of the mastery lifecycle:

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

`MasteryState` also defines retrieval/execution helper methods, including executable states, legacy retrieval, new-work retrieval, and terminal states.

`MasteryItem` captures the core data model: skill ID, domain, agent, release, state, version scope, applicability, score vector, procedure IDs, semantic fact IDs, negative knowledge IDs, evidence refs, evaluation refs, known failure modes, verification timestamp, stale deadline, and labels.

## 1.4 Mastery evidence and transitions exist

`MasteryEvidence` exists with evidence ID, type, ref, digest, creator, weight, and labels. Evidence types include episode, trace, evaluation run, test run, human review, official doc snapshot, security advisory, regression result, tool output, and user feedback.

`MasteryTransition` is append-only and includes from-state, to-state, reason, initiator, timestamp, evidence refs, and metadata.

`DefaultMasteryTransitionPolicy` defines promotion rules such as `UNKNOWN → OBSERVED`, `PRACTICED → COMPETENT`, `COMPETENT → MASTERED`, `MASTERED → MAINTENANCE_ONLY`, and quarantine/obsolete/retired transitions.

## 1.5 Learning delta pipeline exists

`LearningDelta` exists as the proposed-change artifact for self-learning. It captures target, type, state, agent/release/skill, procedure/fact/negative-knowledge references, content digest, proposed content, evidence refs, evaluation refs, source episode IDs, rollback ref, confidence before/after, human review flag, and timestamps.

`LearningDeltaState` defines the promotion pipeline: `PROPOSED`, `PENDING_EVALUATION`, `EVALUATED`, `APPROVED`, `PROMOTED`, `REJECTED`, `PROMOTING`, `PROMOTION_FAILED`, and `OBSOLETE`.

`LearningTarget` includes the expected safe-learning targets: episodic memory, semantic fact, procedural skill, negative knowledge, retrieval policy, confidence threshold, routing policy, prompt template, planner policy, model adapter, and mastery state.

`LearningLevel` provides a good authority model: `L0` no learning, `L1` episodic memory only, `L2` semantic/retrieval/confidence/routing, `L3` procedural skill and negative knowledge, `L4` prompt/planner, and `L5` all targets/offline-only.

## 1.6 Learning engine has started moving from direct policy writes to learning deltas

`LearningEngine` now uses `LearningContract`, supports `LearningDeltaRepository`, requires the delta repository for `L3+`, and produces procedural-skill deltas rather than directly mutating active behavior. This is exactly the right conceptual move.

## 1.7 Promotion engine exists

`DefaultPromotionEngine` promotes evaluated/approved learning deltas by checking policy, determining target mastery state, transitioning mastery, and updating the learning delta to `PROMOTED`.

## 1.8 Data Cloud persistence exists

`DataCloudMasteryRegistry` implements `MasteryRegistry` using Data Cloud entity collections for mastery items, transitions, and evidence. It supports `findBySkill`, `query`, `save`, `transition`, `findStale`, and `decide`.

`MasteryItemMapper` converts `MasteryItem` to/from Data Cloud entity maps and stores version scopes, applicability, confidence vector, IDs, evidence refs, eval refs, timestamps, and labels.

## 1.9 Dispatcher has hooks for mastery, but enforcement is incomplete

`GovernedAgentDispatcher` already has constructor dependencies for `MasteryRegistry`, `VersionContextResolver`, `TaskClassifier`, and `MasteryAwareModeSelector`. It also documents mastery checks, version context checks, task classification, mode selection, trace recording, and OTel tracing.

However, the actual implementation still has placeholders: task classification is commented out, version context is created from an empty snapshot, version compatibility returns `true`, mastery checks mostly log availability, and mode selection is not using full skill/version/task context.

## 1.10 Version scope exists but is too weak

`VersionScope` supports active, maintenance, and obsolete constraints, and it classifies a `VersionContext` as `ACTIVE`, `MAINTENANCE`, `OBSOLETE`, or `UNKNOWN`. However, its range evaluator only supports exact match or a single comparator like `>=`, `<`, etc. It does not support compound ranges like `>=7 <8`, npm/Maven semver syntax, pre-release versions, `^`, `~`, `x`, or ecosystem-specific behavior.

## 1.11 Obsolescence detector exists but is label-heavy

`DefaultObsolescenceDetector` detects version mismatch, API deprecation, runtime incompatibility, repeated failures, security vulnerability, and documentation contradiction. The implementation mostly relies on labels such as `deprecated`, `failureCount`, `securityVulnerability`, and `documentationContradiction`, rather than live evidence from dependency snapshots, tests, advisories, docs snapshots, or trace failures.

---

# 2. Critical gaps blocking the GAA goal

## Gap 1: Runtime dispatch does not yet enforce mastery decisions

The current dispatcher has the right dependencies, but mastery is not yet a hard runtime gate. It logs that the registry is available, creates a simplified query, and does not enforce full `MasteryDecision` semantics consistently. Version compatibility is still a placeholder returning `true`.

**Impact:** the platform can store mastery, but execution can still proceed without real mastery/version/freshness gating.

## Gap 2: `DataCloudMasteryRegistry` is not tenant-safe enough

`DataCloudMasteryRegistry.transition()` uses `tenantId = "default"` because `MasteryTransition` has no tenant ID. `findStale()` also scans only `"default"`. The controller has TODOs around tenant validation for transitions.

**Impact:** mastery state is a governance artifact; tenant leakage or default-tenant updates are unacceptable.

## Gap 3: Promotion engine can fail because it calls `findBySkill(delta.skillId(), null)`

`DefaultPromotionEngine` calls `masteryRegistry.findBySkill(delta.skillId(), null)`, but `DataCloudMasteryRegistry.findBySkill()` expects an environment fingerprint and dereferences `env.tenantId()`.

**Impact:** promotion may fail at runtime for Data Cloud-backed mastery.

## Gap 4: Transition logic is duplicated and inconsistent

There is a `DefaultMasteryTransitionPolicy`, but `DataCloudMasteryRegistry` has its own local `isValidTransition()` implementation instead of using the policy. These two implementations are not equivalent. For example, registry quarantine accepts any state without requiring safety evidence, while the policy requires `safety_violation` or `unsafe_behavior`.

**Impact:** transition behavior will drift between in-memory/core and Data Cloud-backed implementations.

## Gap 5: Evidence mapping likely loses evidence refs

`MasteryItemMapper.toDataMap()` stores `evidenceRefs` as a list, but `fromDataMap()` reads evidence refs through `toStringMap()` and then converts map entries to strings. If persisted evidence refs are a list, this likely returns an empty list on read.

**Impact:** mastery items can lose evidence links after persistence, which breaks auditability and promotion proof.

## Gap 6: `VersionScope` is not production-grade

The current version range evaluator supports only simple single-comparator logic and basic dot-split numeric comparison. It will not safely handle real-world version syntax across npm, Maven, Gradle, Python, Docker, OpenAPI, or cloud/runtime versions.

**Impact:** version-aware mastery can make wrong decisions, especially for JavaScript/TypeScript ecosystems.

## Gap 7: Learning engine creates unstable synthetic skill IDs

`LearningEngine.persistAsLearningDeltas()` derives skill IDs using `"skill-" + agentId + "-" + candidate.situation().hashCode()`.

**Impact:** the same skill can get different IDs across JVMs/versions or wording changes, preventing stable mastery tracking.

## Gap 8: Learning contract is not fully enforced during synthesis

`LearningEngine` accepts a `LearningContract`, but the shown synthesis path always creates `PROCEDURAL_SKILL` deltas for approved candidate policies. The outcome has a TODO for contract rejections.

**Impact:** agents may propose learning deltas for targets they are not allowed to learn.

## Gap 9: Low-confidence deltas are rejected instead of routed to review state

`DefaultLearningDeltaEvaluator` rejects below-threshold deltas unless `requiresHumanReview` is already true. There is no explicit `PENDING_HUMAN_REVIEW` state in `LearningDeltaState`.

**Impact:** useful but uncertain learning is discarded instead of becoming reviewable evidence.

## Gap 10: Mastery API is incomplete

`MasteryController.getMastery()` has a TODO for `getById`, and transition tenant validation is explicitly deferred because `MasteryTransition` lacks tenant context. Query construction also uses empty skill ID when `skillId` is absent.

**Impact:** UI/API cannot reliably inspect or govern mastery end-to-end.

---

# 3. Target operating model

Use this as the implementation target:

```text
AgentDefinition
  → AgentRelease
  → LearningContract
  → MasteryBinding
  → EnvironmentFingerprint / VersionContext
  → TaskClassification
  → MasteryDecision
  → ExecutionMode
  → Governed dispatch
  → Trace capture
  → LearningDelta proposal
  → Evaluation
  → Promotion
  → Mastery transition
  → Version/freshness/obsolescence re-check
```

Core invariant:

> **The agent may observe freely, propose cautiously, and act only within governed mastery.**

---

# 4. Detailed implementation plan

## Phase 0 — Stabilize the build and contract surface

### Files

* `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/config/AgentDefinition.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/LearningLevel.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/LearningTarget.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/LearningContract.java`
* `platform/java/agent-core/build.gradle.kts`

### Tasks

1. Make `LearningLevel.java` the single canonical learning authority.
2. Ensure all docs and code use the same interpretation:

   * `L0`: no learning
   * `L1`: episodic memory only
   * `L2`: semantic/retrieval/confidence/routing
   * `L3`: procedural skill + negative knowledge
   * `L4`: prompt/planner
   * `L5`: model adapter / offline-only
3. Decide how `MASTERY_STATE` is governed:

   * Recommended: do **not** allow normal agents to directly learn `MASTERY_STATE`.
   * Only `PromotionEngine`, `ObsolescenceDetector`, or approved governance workflows should emit mastery transitions.
4. Add tests that prove `LearningContract.permits()` blocks unauthorized targets.
5. Add compile tests for `AgentDefinition.toLearningContract()`, `toMasteryBinding()`, `toFreshnessPolicy()`, and `toVersionCompatibilityPolicy()`.

### Acceptance criteria

* No duplicate learning-level semantics.
* `L5` cannot run online.
* `L3+` cannot mutate active behavior without promotion.
* Mastery-state transitions are not ordinary self-learning events.

---

## Phase 1 — Make mastery tenant-safe and audit-safe

### Files

* `platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/MasteryTransition.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/MasteryEvidence.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/MasteryRegistry.java`
* `products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/mastery/DataCloudMasteryRegistry.java`
* `products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/mastery/MasteryItemMapper.java`
* `products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/mastery/DataCloudMasteryEvidenceRepository.java`
* `products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/mastery/DataCloudMasteryTransitionRepository.java`

### Tasks

1. Add `tenantId` to `MasteryTransition`.
2. Add `agentId`, `agentReleaseId`, and optional `skillId` to `MasteryTransition` for audit.
3. Remove all `"default"` tenant fallbacks from transition and stale-scan paths.
4. Add `MasteryRegistry.getById(tenantId, masteryId)`.
5. Add `MasteryRegistry.findStale(tenantId, now)`.
6. Add tenant-scoped query methods for evidence and transitions.
7. Fix `MasteryItemMapper` evidence refs round trip:

   * store evidence refs as `List<String>`
   * read evidence refs as `List<String>`
   * never convert through `Map<String, String>` unless the domain model changes
8. Require non-empty digest in `MasteryEvidence` or compute it in factory methods.
9. Ensure transition append and item state update happen atomically or through a durable outbox/transaction pattern.

### Acceptance criteria

* No mastery read/write/transition path uses default tenant silently.
* Evidence refs survive save/read round trip.
* Every state transition is append-only and tenant-scoped.
* Mastery evidence digest is never empty for promoted evidence.

---

## Phase 2 — Unify transition policy

### Files

* `platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/transition/MasteryTransitionPolicy.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/transition/DefaultMasteryTransitionPolicy.java`
* `products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/mastery/DataCloudMasteryRegistry.java`
* `platform/java/agent-core/src/test/java/com/ghatana/agent/mastery/*`

### Tasks

1. Inject `MasteryTransitionPolicy` into `DataCloudMasteryRegistry`.
2. Delete local `isValidTransition()` from `DataCloudMasteryRegistry`.
3. Fix transition policy gaps:

   * allow `MAINTENANCE_ONLY → OBSOLETE`
   * allow `QUARANTINED → OBSERVED/PRACTICED/COMPETENT` only after explicit human/security review, or declare quarantine terminal and enforce it everywhere
   * require evidence for `UNKNOWN → OBSERVED`
   * require specific evidence keys for each promotion level
4. Remove unreachable duplicate transition branches from `DefaultMasteryTransitionPolicy`.
5. Add typed evidence requirements instead of stringly keys where possible.

### Acceptance criteria

* One policy controls all mastery transitions.
* Data Cloud and in-memory tests produce identical transition decisions.
* Quarantine/obsolete/retired behavior is explicitly decided and tested.

---

## Phase 3 — Make version-aware mastery real

### Files

* `platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/VersionScope.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/VersionConstraint.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/VersionCompatibilityPolicy.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/environment/EnvironmentFingerprint.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/context/version/*`
* `products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcher.java`

### Tasks

1. Replace `VersionScope.evaluateVersionRange()` with a real version-range evaluator.
2. Support at minimum:

   * exact versions
   * `>=7 <8`
   * npm `^`, `~`, `x`
   * Maven/Gradle ranges
   * pre-release versions
   * ecosystem-specific normalizers
3. Add `VersionApplicability` scoring:

   * active match
   * maintenance match
   * obsolete match
   * unknown/no match
4. Use `EnvironmentFingerprint` and/or `VersionContext` consistently.
5. Build real environment fingerprints from:

   * `package.json`
   * `pnpm-lock.yaml`
   * `pom.xml`
   * `build.gradle.kts`
   * Docker/runtime files
   * OpenAPI/protobuf contracts
   * repo conventions
6. Replace the dispatcher’s empty `EnvironmentSnapshot` with a real resolved snapshot.
7. Implement `GovernedAgentDispatcher.isVersionCompatible()` using release runtime constraints and mastery version scope.

### Acceptance criteria

* React Router v6 maintenance patterns are not retrieved for v7 new projects.
* v7 procedures are not used in v6 legacy maintenance unless migration is the task.
* Unknown version context forces `VERIFICATION_FIRST` or `EXPLORATORY_FAST_LEARNING`.
* Compound version ranges work.

---

## Phase 4 — Complete mastery-aware runtime dispatch

### Files

* `products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcher.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/runtime/mode/MasteryAwareModeSelector.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/runtime/mode/ModeSelectionPolicy.java`
* new: `platform/java/agent-core/src/main/java/com/ghatana/agent/runtime/mode/DefaultModeSelectionPolicy.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/runtime/mode/TaskClassifier.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/runtime/mode/TaskClassification.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/MasteryDecision.java`

### Tasks

1. Uncomment and wire task classification in `GovernedAgentDispatcher`.
2. Pass real task description, risk, novelty, skill ID, agent ID, tenant ID, and version context into mode selection.
3. Make `MasteryAwareModeSelector` the only mode selection path.
4. Implement `DefaultModeSelectionPolicy`:

   * mastered + active + fresh + low risk → deterministic
   * competent → bounded probabilistic + verification
   * practiced → human-gated or fast-learning
   * observed → verification-first
   * maintenance-only + legacy match → maintenance-only
   * obsolete/retired/quarantined → blocked
   * high-risk side effects → human-gated
5. Enforce `ModeSelectionResult.requiresApproval()` and `requiresVerification()`.
6. Add trace events for:

   * task classification
   * version context resolution
   * mastery decision
   * selected execution mode
   * approval/verification requirements
7. Refuse execution when mode is `BLOCKED`.
8. Refuse execution when approval is required and missing.
9. Refuse execution when verification is required and verification proof is missing.

### Acceptance criteria

* Dispatcher blocks obsolete/retired/quarantined skills.
* Dispatcher routes maintenance-only only for legacy contexts.
* Dispatcher does not ignore mastery decisions.
* Trace ledger shows why execution mode was selected.

---

## Phase 5 — Fix learning delta creation and evaluation

### Files

* `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/learning/LearningEngine.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/LearningDelta.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/LearningDeltaFactory.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/DefaultLearningDeltaEvaluator.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/LearningDeltaState.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/LearningDeltaPromotionPolicy.java`

### Tasks

1. Replace hash-derived skill IDs with stable `SkillIdResolver`.
2. Add a `SkillCatalog` or `SkillManifest`:

   * `skillId`
   * domain
   * version scope
   * expected evidence
   * eval pack
   * compatible procedures
3. Make `LearningEngine` call `learningContract.permits(target)` before creating each delta.
4. Add explicit contract rejection count.
5. For low-confidence deltas, add `PENDING_HUMAN_REVIEW` instead of rejecting.
6. Require evaluation refs for `PROCEDURAL_SKILL`, not only confidence.
7. Require rollback refs for execution-affecting deltas.
8. Add contradiction checks before approving semantic facts.
9. Add safety checks before approving prompt/planner/model-adapter deltas.
10. Add content digest computation in `LearningDeltaFactory`.

### Acceptance criteria

* L2 agents cannot propose procedural-skill deltas.
* L3 agents can propose procedural/negative-knowledge deltas but cannot activate them directly.
* Low-confidence deltas are reviewable, not lost.
* Procedural skill deltas require eval evidence before promotion.

---

## Phase 6 — Fix promotion end-to-end

### Files

* `platform/java/agent-core/src/main/java/com/ghatana/agent/promotion/DefaultPromotionEngine.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/promotion/DefaultPromotionPolicy.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/promotion/PromotionEngine.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/promotion/PromotionResult.java`
* `products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/learning/delta/DataCloudLearningDeltaRepository.java`
* `products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/mastery/DataCloudMasteryRegistry.java`

### Tasks

1. Remove `findBySkill(delta.skillId(), null)` from `DefaultPromotionEngine`.
2. Use `tenantId` and `EnvironmentFingerprint` from the delta, evaluation result, or promotion request.
3. If mastery item does not exist:

   * create `UNKNOWN → OBSERVED` item if evidence is sufficient, or
   * fail with a typed “missing mastery item” result that UI can resolve.
4. Promotion should support:

   * create new mastery item
   * transition existing mastery item
   * update evidence refs
   * update eval refs
   * update score vector
   * update last verified / stale after
5. Promotion should be idempotent.
6. Add rollback for failed partial promotion.
7. Add promotion result trace event.

### Acceptance criteria

* Promotion works for first-time skills and existing skills.
* Promotion never passes null environment.
* Delta state and mastery state stay consistent.
* Promotion can be safely retried.

---

## Phase 7 — Complete mastery-aware memory retrieval

### Files

* `products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/retrieval/mastery/MasteryAwareMemoryQuery.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/memory/retrieval/MaintenanceModeMemoryFilter.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/memory/retrieval/ObsoleteMemoryFilter.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/memory/retrieval/FreshnessAwareRanker.java`
* `products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/store/MemoryPlane.java`
* `products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/governance/GovernedMemoryPlane.java`

### Tasks

1. Rename `MaintenanceModeMemoryFilter` / `ObsoleteMemoryFilter` or move them under mastery retrieval, since they filter `MasteryItem`, not memory items.
2. Implement retrieval ordering:

   1. negative knowledge
   2. active mastered/competent procedures
   3. semantic facts
   4. similar episodes
   5. maintenance-only items only when legacy context matches
3. Add hard exclusion:

   * obsolete
   * retired
   * quarantined
4. Add freshness gating.
5. Add version compatibility gating.
6. Add purpose/consent checks before memory hydration.
7. Add retrieval trace containing:

   * query
   * version context
   * filtered-out obsolete items
   * selected procedures/facts/episodes
   * reason codes

### Acceptance criteria

* Similarity cannot override obsolescence.
* Maintenance-only knowledge never appears in new-work retrieval.
* Negative knowledge is retrieved before procedural recommendations.
* Memory retrieval is tenant/purpose scoped.

---

## Phase 8 — Make obsolescence detection evidence-driven

### Files

* `platform/java/agent-core/src/main/java/com/ghatana/agent/obsolescence/DefaultObsolescenceDetector.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/obsolescence/ObsolescenceEvent.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/obsolescence/ObsolescenceReason.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/mode/policy/FastLearningPolicy.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/mode/policy/MaintenanceOnlyPolicy.java`
* Data Cloud scheduled/background worker module

### Tasks

1. Replace label-only checks with real evidence sources:

   * dependency change events
   * runtime fingerprint changes
   * API contract diffs
   * failed eval runs
   * failed production traces
   * official docs snapshots
   * security advisories
2. Add `ObsolescenceEvent → MasteryTransition` mapping.
3. Add scheduled stale scan per tenant.
4. Add event-triggered scan on:

   * package upgrades
   * API contract updates
   * security advisory ingestion
   * repeated trace failures
5. For version mismatch:

   * active → maintenance-only when legacy still exists
   * active/maintenance → obsolete when unsafe/incompatible
   * obsolete → retired when no active use case remains
6. Add human review requirements for high-impact deprecations.

### Acceptance criteria

* Version changes automatically mark stale mastery for review.
* Security advisories can quarantine affected skills.
* Repeated failures reduce confidence or trigger obsolete/quarantine transition.
* Obsolescence events have evidence refs.

---

## Phase 9 — Complete Data Cloud API and UI

### Files

* `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/api/controller/MasteryController.java`
* `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/api/controller/LearningDeltaController.java`
* `products/data-cloud/delivery/ui/src/api/mastery.service.ts`
* `products/data-cloud/delivery/ui/src/pages/MasteryPage.tsx`
* `products/data-cloud/delivery/ui/src/api/*learning*`

### Tasks

1. Implement `MasteryController.getMastery()`.
2. Fix query behavior when `skillId` is absent.
3. Validate tenant on transition.
4. Add endpoints:

   * list mastery items
   * get mastery by ID
   * query by skill/domain/agent/release/state
   * list transitions
   * list evidence
   * propose transition
   * approve transition
   * stale mastery
   * obsolete/quarantined mastery
5. Add Learning Delta UI:

   * proposed
   * pending evaluation
   * pending human review
   * approved
   * promoted
   * rejected
6. Add Mastery UI:

   * state distribution
   * stale items
   * obsolete/quarantined items
   * version compatibility view
   * evidence chain
   * promotion history
   * skill-specific eval status
7. Add “why this mode?” explanation panel:

   * selected execution mode
   * version context
   * mastery state
   * confidence vector
   * approval/verification requirements

### Acceptance criteria

* Operator can see why a skill is mastered, stale, maintenance-only, obsolete, or blocked.
* Operator can approve/reject deltas and transitions.
* UI never hides evidence/provenance for active mastery.

---

## Phase 10 — Test strategy

### Add or update tests

* `platform/java/agent-core/src/test/java/com/ghatana/agent/mastery/MasteryStateTest.java`
* `platform/java/agent-core/src/test/java/com/ghatana/agent/mastery/DefaultMasteryTransitionPolicyTest.java`
* `platform/java/agent-core/src/test/java/com/ghatana/agent/mastery/VersionScopeTest.java`
* `platform/java/agent-core/src/test/java/com/ghatana/agent/learning/LearningContractTest.java`
* `platform/java/agent-core/src/test/java/com/ghatana/agent/learning/DefaultLearningDeltaEvaluatorTest.java`
* `platform/java/agent-core/src/test/java/com/ghatana/agent/promotion/DefaultPromotionEngineTest.java`
* `platform/java/agent-core/src/test/java/com/ghatana/agent/runtime/mode/MasteryAwareModeSelectorTest.java`
* `products/data-cloud/extensions/agent-registry/src/test/java/com/ghatana/datacloud/agent/mastery/DataCloudMasteryRegistryTest.java`
* `products/data-cloud/planes/action/agent-runtime/src/test/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcherMasteryTest.java`

### Required scenarios

1. Unknown skill cannot execute.
2. Observed skill requires verification.
3. Practiced skill requires approval or sandbox.
4. Competent skill executes with verification.
5. Mastered skill executes deterministically if fresh and version-compatible.
6. Maintenance-only skill executes only for matching legacy context.
7. Obsolete skill is blocked.
8. Quarantined skill is blocked.
9. New version routes to fast-learning mode.
10. Legacy version routes to maintenance-only mode.
11. Learning delta cannot violate `LearningContract`.
12. Procedural skill delta requires eval refs and rollback.
13. Promotion creates/updates mastery correctly.
14. Evidence refs persist round trip.
15. Tenant A cannot transition Tenant B’s mastery.
16. Dispatcher blocks when approval is missing.
17. Dispatcher blocks when version compatibility fails.
18. Obsolescence event transitions active mastery to maintenance/obsolete/quarantine as appropriate.

---

# 5. Recommended implementation order

Do this in order to avoid rework:

1. **Tenant + evidence persistence correctness**

   * Fix `MasteryTransition`, `DataCloudMasteryRegistry`, and `MasteryItemMapper`.

2. **Single transition policy**

   * Remove duplicated transition logic and enforce `DefaultMasteryTransitionPolicy`.

3. **Version scope correctness**

   * Replace simple range matching before relying on version-aware decisions.

4. **Dispatcher enforcement**

   * Make mastery, version, task risk, and mode selection real runtime gates.

5. **Learning delta enforcement**

   * Ensure `LearningContract` blocks unauthorized targets.

6. **Promotion end-to-end**

   * Fix null environment and first-time mastery creation.

7. **Obsolescence workers**

   * Connect version/docs/security/test failures to mastery transitions.

8. **API/UI**

   * Expose the lifecycle for operators.

9. **Full E2E tests**

   * Prove bootstrap → task → trace → learning delta → eval → promotion → mastery → runtime gating.

---

# 6. Final target behavior

When complete, the GAA should behave like this:

```text
Task arrives
→ classify task/risk/novelty
→ inspect environment versions
→ query mastery registry
→ retrieve only compatible/fresh/non-obsolete memory
→ select execution mode
→ enforce approval/verification/blocking
→ execute
→ capture trace
→ propose learning delta
→ evaluate
→ promote or quarantine
→ update mastery
→ detect stale/obsolete knowledge continuously
```

The repository already has the skeleton for this. The main work now is to make the skeleton **authoritative, tenant-safe, version-correct, evidence-preserving, and enforced at runtime**.

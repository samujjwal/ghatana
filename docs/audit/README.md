# GAA implementation plan for `samujjwal/ghatana` at commit `55adc72be6b8b3a592e2cb47b5c966b7db688e91`

**Classification:** target-architecture

I scanned the target commit and the current code is already much closer to your goal than the earlier conceptual model: the repo now has concrete mastery lifecycle states, learning deltas, learning contracts, promotion policies, Data Cloud-backed mastery persistence, mastery-aware mode selection, governed dispatch, version-aware retrieval, obsolescence detection, and pilot/CI gate documentation. The commit itself is titled “build fixes,” but its diff includes the mastery CI gates, pilot adoption guide, execution-gate decision model, and safety-policy-backed evaluation changes.

The goal now is not to invent a new architecture. It is to **stabilize, connect, harden, and make the existing mastery system production-grade**.

---

## 1. Current architecture at the target commit

| Area                        | Current state                                                                                                                                                                                                                   | Assessment                                                                           |
| --------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| **Agent definition**        | `AgentDefinition` now includes mastery bindings, skill refs, mastery policy refs, learning contract materialization, freshness/version policy materializers, and consistency validation.                                        | Strong foundation. Needs stricter loader/registry enforcement.                       |
| **Agent release**           | `AgentRelease` now carries tenant, mastery policy pack, learning contract, version compatibility policy, freshness policy, evaluation/memory contracts, and requires mastery/learning contracts for response-serving releases.  | Strong governance model. Skill-level references are still placeholders.              |
| **Mastery lifecycle**       | `MasteryState` implements `UNKNOWN → OBSERVED → PRACTICED → COMPETENT → MASTERED → MAINTENANCE_ONLY → OBSOLETE → RETIRED`, plus `QUARANTINED`.                                                                                  | Matches your target lifecycle. Minor semantic conflict around “terminal” quarantine. |
| **Mastery item**            | `MasteryItem` tracks tenant, skill, domain, agent release, version scope, applicability, score, linked procedures/facts/negative knowledge/evidence/evals/failures/history, freshness, and confidence.                          | Correct shape. Needs richer score preservation and version/applicability hydration.  |
| **Mastery registry**        | `MasteryRegistry` supports tenant-scoped query, decision, best-match, transition, stale detection, and get-by-id.                                                                                                               | Good contract. Data Cloud implementation needs consistency fixes.                    |
| **Learning boundary**       | `LearningLevel`, `LearningContract`, and `LearningTarget` now make `MASTERY_STATE` offline-only and restricted to L5 governance workflows.                                                                                      | Very good. This is the hard safety boundary we need.                                 |
| **Learning delta**          | `LearningDelta` models proposed changes with target, state, tenant, agent/release/skill, content digest, evidence, evals, source episodes, rollback ref, confidence, and review flags.                                          | Good proposal artifact. Needs stronger environment/version/proof metadata.           |
| **Promotion**               | `DefaultPromotionEngine` evaluates/promotes deltas and updates mastery; `DefaultPromotionPolicy` contains target-specific rules.                                                                                                | Conceptually correct but has evidence-key and approval-proof gaps.                   |
| **Mode selection**          | `MasteryAwareModeSelector` and `DefaultModeSelectionPolicy` map mastery, task risk, version applicability, and freshness into execution strategy/supervision.                                                                   | Strong. Needs one-decision integration with dispatcher.                              |
| **Governed dispatch**       | `GovernedAgentDispatcher` checks release, capability guard, skill ID, version compatibility, mastery decision, approval/verification proof, mode selection, invariants, trace ledger, and OTel.                                 | Core runtime path exists. Memory retrieval is wired as a dependency but not used.    |
| **Version-aware retrieval** | `VersionScope` supports active/maintenance/obsolete constraints; `MasteryAwareMemoryRetriever` filters by mastery state and version applicability.                                                                              | Directionally right. Contains ordering and canonical memory-plane gaps.              |
| **Memory plane**            | There are two memory planes: framework bridge `MemoryPlane` and richer Data Cloud runtime `MemoryPlane`.                                                                                                                        | Split-brain risk. Need canonicalization/adapter.                                     |
| **Obsolescence**            | `DefaultObsolescenceDetector` detects stale/version/API/runtime/failure/security/doc issues; transition service routes events to mastery transitions.                                                                           | Useful start. Transition service has concrete lookup/from-state bugs.                |
| **API surface**             | `MasteryController` exposes query/get/save/transition/stale/obsolete/evidence endpoints with access checks.                                                                                                                     | Needs stricter mutation governance and approval-proof requirements.                  |

---

# 2. Critical findings to fix before scaling

## P0. Promotion evidence does not match transition policy

`DefaultMasteryTransitionPolicy` expects evidence keys such as `trace`, `trace_id`, `verified_source_id`, `episodes`, `sandbox_experiments`, `procedure_id`, `basic_eval_passed`, `regression_passed`, `safety_passed`, `recovery_passed`, `compatibility_passed`, and `new_active_version_id`.

But `PromotionEvidenceMapper` emits keys such as `procedure`, `semanticFact`, `negativeKnowledge`, `evaluation_0`, `deltaId`, `agentId`, and `skillId`.

**Impact:** promotion transitions can fail even when evaluation succeeds.

**Fix:** make `PromotionEvidenceMapper` produce the exact evidence keys required by `DefaultMasteryTransitionPolicy`.

---

## P0. Obsolescence transition service uses wrong lookup and fake state

`DefaultObsolescenceTransitionService` processes an obsolescence event by querying `MasteryQuery.bySkill(event.masteryId())`, which uses a mastery ID as a skill ID and omits tenant ID. It also hardcodes `fromState = MASTERED`, sets agent/release to `obsolescence-detector`, and derives skill ID using `event.masteryId().split("-")[0]`.

**Impact:** obsolescence transitions may fail, mutate the wrong item, or create inaccurate audit history.

**Fix:** use `masteryRegistry.getById(event.tenantId(), event.masteryId())`, then build the transition from the actual item’s state, skill ID, agent ID, and release ID.

---

## P0. Memory retrieval dependency is not actually used in dispatcher

`GovernedAgentDispatcher` accepts a `MemoryRetriever`, but dispatch path initializes `ctxWithMemoryPromise = Promise.of(finalCtx)` and never invokes memory retrieval or context injection before delegation.

**Impact:** runtime can make mastery/mode decisions, but the agent does not yet receive mastery-filtered memory context during execution.

**Fix:** insert a structured memory-retrieval step between mode selection and invariant/dispatch.

---

## P0. Mastery-aware retrieval ordering is likely reversed

`MasteryAwareMemoryRetriever` defines a comparator where higher priority comes first, but in `retrieve()` it calls `.reversed()` before sorting selected items.

**Impact:** low-priority memory may be injected before high-priority memory.

**Fix:** remove `.reversed()` and add tests proving negative knowledge, active mastered procedures, semantic facts, episodes, and maintenance-only knowledge are ordered correctly.

---

## P1. Policy says “human review required,” but not “human approved”

`DefaultPromotionPolicy` uses `delta.requiresHumanReview()` as a condition for policy-target mastery.

**Impact:** a delta that merely requires human review may be treated as if review happened.

**Fix:** add explicit `approvalProofId`, `approvalOutcome`, or `governanceDecisionRef` to `LearningDelta`, and require a valid approval proof for planner/prompt/model/mastery-state promotion.

---

## P1. Empty regression/safety test sets can accidentally pass

`DefaultPromotionPolicy.hasPassedRegressionAndSafety()` uses filtered stream `allMatch`; in Java, `allMatch` on an empty stream returns true.

**Impact:** a promotion may pass regression/safety checks when no regression/safety tests were present.

**Fix:** require both existence and pass status for mandatory test categories.

---

## P1. Two memory-plane abstractions exist

The framework memory plane supports projection/consolidation/reflection/version/mastery queries.  The Data Cloud runtime memory plane supports episodic, semantic, procedural, typed artifacts, cross-tier queries, semantic search, working memory, task state, checkpointing, and stats.

**Impact:** future code may integrate against the wrong memory plane and duplicate logic.

**Fix:** make the Data Cloud runtime `MemoryPlane` canonical; keep the framework `MemoryPlane` as a compatibility facade or rename it to `MemoryProjectionBridge`.

---

## P1. Direct mutation API is too permissive

`MasteryController` exposes `saveMastery()` and `transition()` with access checks such as `mastery:write` and `mastery:transition`.

**Impact:** production mastery state could be mutated outside the promotion/obsolescence governance flow.

**Fix:** route state-changing operations through promotion/obsolescence/governance workflows; direct manual transitions must require an approval proof, reason, evidence bundle, and trace event.

---

## P1. AgentRelease skill-specific governance refs are placeholders

`AgentRelease.skillEvaluationPackRefs()` and `masteryPolicyPackRefs()` currently return empty maps.

**Impact:** per-skill governance cannot be enforced even though release-level fields exist.

**Fix:** add explicit fields or metadata model for skill-to-eval-pack and skill-to-mastery-policy mapping.

---

# 3. Target architecture to implement

The final GAA should have this runtime flow:

```text
AgentDefinition
→ AgentRelease
→ LearningContract
→ MasteryRegistry
→ VersionContext
→ ModeSelection
→ Mastery-aware Memory Retrieval
→ Governed Dispatch
→ Trace Capture
→ LearningDelta
→ Evaluation
→ Promotion
→ Mastery Transition
→ Obsolescence/Refresh
```

The invariant should be:

> **Agents may observe freely, propose cautiously, and act only within governed mastery.**

That maps to the code as follows:

| Goal concept                      | Concrete implementation target                                        |
| --------------------------------- | --------------------------------------------------------------------- |
| Agent identity and contract       | `AgentDefinition`                                                     |
| Deployable governance envelope    | `AgentRelease`                                                        |
| Learning authority                | `LearningContract`, `LearningLevel`, `LearningTarget`                 |
| Evidence-backed proposed learning | `LearningDelta`                                                       |
| Version-scoped skill state        | `MasteryItem`, `VersionScope`, `ApplicabilityScope`, `MasteryScore`   |
| State machine                     | `MasteryState`, `DefaultMasteryTransitionPolicy`                      |
| Promotion gate                    | `DefaultPromotionEngine`, `DefaultPromotionPolicy`                    |
| Execution mode                    | `MasteryAwareModeSelector`, `DefaultModeSelectionPolicy`              |
| Runtime guard                     | `GovernedAgentDispatcher`                                             |
| Memory and retrieval              | Data Cloud `MemoryPlane`, `MasteryAwareMemoryRetriever`               |
| Staleness and retirement          | `DefaultObsolescenceDetector`, `DefaultObsolescenceTransitionService` |
| Admin/API                         | `MasteryController`                                                   |
| CI gates                          | Mastery regression gate docs in commit diff                           |

---

# 4. Detailed implementation plan

## Phase 0 — Stabilize the target commit baseline

**Goal:** Make the current mastery code compile, run, and fail deterministically where gaps exist.

### Files to inspect/update

* `platform/java/agent-core/build.gradle.kts`
* `products/data-cloud/extensions/agent-registry/build.gradle.kts`
* `products/data-cloud/planes/action/agent-runtime/build.gradle.kts`
* `.github/workflows/*`
* docs introduced in the commit for mastery CI gates and pilot adoption.

### Tasks

1. Run targeted compile/test for:

    * `platform/java/agent-core`
    * `products/data-cloud/extensions/agent-registry`
    * `products/data-cloud/planes/action/agent-runtime`
    * `products/data-cloud/delivery/api`

2. Add/verify Gradle module dependencies:

    * agent-core → no Data Cloud dependency
    * Data Cloud registry → agent-core dependency allowed
    * runtime → agent-core + registry interfaces
    * API → registry/service dependencies only through stable interfaces

3. Confirm no product module imports internal Data Cloud classes directly unless it is under Data Cloud.

4. Add a small “mastery smoke test” task:

    * construct a `MasteryItem`
    * save in `DataCloudMasteryRegistry`
    * query by tenant/skill
    * call `decide`
    * assert expected decision

### Acceptance criteria

* All mastery-related modules compile.
* No circular dependencies.
* No runtime path requires deprecated `findStale(Instant)` or deprecated `findBySkill`.
* Existing tests pass or fail with known tracked gaps.

---

## Phase 1 — Make agent definition and release governance authoritative

**Goal:** All mastery-enabled agents must declare skill refs, mastery bindings, policy refs, evaluation refs, learning contract, freshness policy, and version compatibility.

### Files

* `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/config/AgentDefinition.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/config/AgentDefinitionValidator.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/config/materializer/*`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/release/AgentRelease.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/release/AgentReleaseBuilder.java`

### Tasks

1. Enforce `AgentDefinition.validateLearningLevelConsistency()` in loader/registry, not only as a helper. It already checks learning-level consistency, adaptation target enum validity, skill refs when mastery bindings exist, and L3+ evaluation/mastery policy refs.

2. Add validation that:

    * Every `skillRef` has a matching mastery binding.
    * Every mastery binding has a version compatibility policy.
    * Every L3+ agent has `evaluationRefs`.
    * Every mastery-enabled agent has `masteryPolicyRefs`.
    * L5 agent definitions are not response-serving.

3. Implement `AgentRelease.skillEvaluationPackRefs()` and `masteryPolicyPackRefs()` instead of returning empty maps.

4. Make `AgentReleaseBuilder` populate:

    * `masteryPolicyPackId`
    * `learningContractId`
    * `versionCompatibilityPolicyId`
    * `freshnessPolicyId`
    * skill-specific policy/eval mappings

5. Add digest validation:

    * release digest must include spec digest, policy pack digest, evaluation pack digest, mastery policy pack, learning contract, freshness policy, version compatibility policy.

### Acceptance criteria

* A response-serving `CANARY` or `ACTIVE` release cannot be created without evaluation, memory, mastery, and learning contracts. Current `AgentRelease` already enforces this for release-level fields.
* Invalid mastery-enabled agent definitions fail at load time.
* Skill-specific evaluation/mastery refs are no longer placeholders.

---

## Phase 2 — Fix mastery promotion evidence and transition correctness

**Goal:** Learning deltas can actually promote through the mastery lifecycle.

### Files

* `platform/java/agent-core/src/main/java/com/ghatana/agent/promotion/PromotionEvidenceMapper.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/promotion/DefaultPromotionEngine.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/promotion/DefaultPromotionPolicy.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/transition/DefaultMasteryTransitionPolicy.java`
* `platform/java/agent-core/src/test/java/com/ghatana/agent/promotion/*`
* `platform/java/agent-core/src/test/java/com/ghatana/agent/mastery/transition/*`

### Tasks

1. Change `PromotionEvidenceMapper.toEvidenceMap(delta, evaluationResult)` to emit transition-policy-compatible keys.

Example mapping:

```text
UNKNOWN → OBSERVED:
  trace_id = first sourceEpisodeId or trace ref
  verified_source_id = source label when present

OBSERVED → PRACTICED:
  episodes = comma-separated sourceEpisodeIds
  sandbox_experiments = evidence refs tagged sandbox

PRACTICED → COMPETENT:
  procedure_id = delta.procedureId
  basic_eval_passed = true/false

COMPETENT → MASTERED:
  regression_passed = true/false
  safety_passed = true/false
  recovery_passed = true/false
  compatibility_passed = true/false
```

2. Update `DefaultPromotionEngine.applyTransition()` to pass `EvaluationResult` into the mapper. Today it only maps from the delta.

3. Fix `DefaultPromotionPolicy.hasPassedRegressionAndSafety()`:

    * require at least one regression test
    * require at least one safety test
    * require all mandatory tests pass

4. Add explicit `approvalProofRef` to `LearningDelta` or labels, then require it for planner/prompt/model/mastery-state promotions. Do not use `requiresHumanReview` as proof of approval.

5. Preserve multidimensional `MasteryScore` rather than always replacing it with `MasteryScore.correctnessOnly(delta.confidenceAfter())`.

6. Parse and apply version scope/applicability from the delta or skill manifest. Today version scope update is effectively a placeholder.

### Acceptance criteria

* UNKNOWN → OBSERVED promotion test passes.
* OBSERVED → PRACTICED promotion test passes.
* PRACTICED → COMPETENT promotion test passes.
* COMPETENT → MASTERED promotion test passes only with regression, safety, recovery, and compatibility evidence.
* Planner/prompt promotions cannot reach MASTERED without approval proof.
* Safety or regression missing tests fail closed.

---

## Phase 3 — Harden Data Cloud mastery persistence

**Goal:** `DataCloudMasteryRegistry` becomes reliable, tenant-safe, deterministic, and scalable.

### Files

* `products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/mastery/DataCloudMasteryRegistry.java`
* `products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/mastery/MasteryItemMapper.java`
* `products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/mastery/DataCloudMasteryTransitionRepository.java`
* `products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/mastery/DataCloudMasteryEvidenceRepository.java`
* `products/data-cloud/extensions/agent-registry/src/test/java/...`

### Tasks

1. Add repository tests for:

    * tenant mismatch rejection
    * query requires tenant
    * state filtering
    * stale detection by tenant
    * transition appends history
    * optimistic update/idempotency
    * version-context ranking
    * maintenance-only inclusion/exclusion flags

2. Fix/clarify the invariant between `VersionApplicability.MAINTENANCE` and `MasteryState.MAINTENANCE_ONLY`. Current `decide()` blocks maintenance applicability unless the state itself requires legacy context.

3. Add validation that if a mastery item’s version scope classifies a context as maintenance, only `MAINTENANCE_ONLY` should be executable for that context.

4. Replace ad hoc pagination with repository pagination contract where possible.

5. Make transition append + item save idempotent end-to-end:

    * transition ID is idempotency key
    * retry after transition append but failed item save reconciles state

6. Ensure `stateHistory` cannot be lost when `updateMasteryItemWithDelta()` later re-saves the item.

### Acceptance criteria

* Registry supports at least the commit’s documented performance target: query under 50ms for 1000 items and save under 20ms per item in test conditions.
* All queries are tenant-scoped.
* Transition history is append-only and preserved.

---

## Phase 4 — Fix governed dispatch runtime integration

**Goal:** The dispatcher must make one mastery decision, select mode once, retrieve memory, enforce approvals/verifications, and then dispatch.

### Files

* `products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcher.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/runtime/mode/MasteryAwareModeSelector.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/runtime/mode/DefaultModeSelectionPolicy.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/context/version/*`
* tests for `GovernedAgentDispatcher`

### Tasks

1. Remove duplicate mastery decisions:

    * currently dispatcher calls `masteryRegistry.decide()`
    * then `MasteryAwareModeSelector.selectMode()` calls `masteryRegistry.decide()` again.

   Add a selector method:

```java
selectMode(MasteryDecision decision, TaskClassification classification, VersionContext context)
```

2. Integrate real `VersionContext` input:

    * do not resolve from empty `EnvironmentSnapshot` unless that is truly intended.
    * build snapshot from repo/dependency/runtime/convention evidence.

3. Strengthen skill ID derivation:

    * prefer explicit `ctx.skillId`
    * then `AgentDefinition.skillRefs`
    * then `AgentCapabilityManifest.skillId`
    * then release skill mapping
    * use agent ID only as last-resort and record a warning trace

4. Enforce mode-selected approval/verification:

    * dispatcher currently checks approval/verification from mastery decision before mode selection
    * also enforce `modeResult.requiresApproval()` and `modeResult.requiresVerification()` after mode selection

5. Integrate memory retrieval:

    * after mode selection and before invariant dispatch, call mastery-aware memory retriever
    * inject a structured context bundle into `AgentContext`

6. Ensure trace sequencing:

    * append all governance decisions in order
    * record release ID, skill ID, mastery item, version digest, execution strategy, supervision mode, retrieved memory IDs, approval proof, verification proof

### Acceptance criteria

* A `MASTERED + ACTIVE + low-risk` task runs deterministic/autonomous.
* `COMPETENT` requires verification proof.
* `PRACTICED` and `OBSERVED` require approval proof.
* `MAINTENANCE_ONLY` runs only in matching legacy context and human-gated mode.
* `OBSOLETE`, `RETIRED`, `QUARANTINED`, stale, or incompatible release dispatch is denied.
* Memory bundle is present in context for allowed dispatch.

---

## Phase 5 — Canonicalize memory plane and retrieval

**Goal:** Memory is not just stored; it is retrieved according to mastery, version, freshness, risk, and negative knowledge.

### Files

* `platform/java/agent-core/src/main/java/com/ghatana/agent/memory/retrieval/MasteryAwareMemoryRetriever.java`
* `products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/store/MemoryPlane.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/memory/MemoryPlane.java`
* `products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/retrieval/StructuredContextInjector.java`
* `products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/retrieval/mastery/RetrievalPolicy.java`

### Tasks

1. Choose canonical memory SPI:

    * use Data Cloud runtime `MemoryPlane` as the canonical multi-tier SPI because it supports episodic, semantic, procedural, typed artifacts, cross-tier query, semantic search, working memory, task-state, checkpointing, and stats.
    * keep framework `MemoryPlane` as `MemoryProjectionBridge` or adapter.

2. Fix retrieval order bug:

    * remove `.reversed()` in `MasteryAwareMemoryRetriever.retrieve()`.

3. Make negative knowledge first:

    * increase `NEGATIVE_KNOWLEDGE` priority above facts/procedures
    * add test proving negative knowledge is always injected before procedures

4. Add execution-strategy-aware retrieval:

    * deterministic: mastered/competent only
    * verification-first: include known failures + semantic facts
    * fast-learning: allow unknown/tentative docs/episodes, but label as tentative
    * maintenance-only: only matching legacy context
    * blocked: no retrieval except audit/provenance

5. Add retrieval decision bundle:

    * selected items
    * rejected items
    * reason per item
    * version applicability
    * mastery state
    * freshness score
    * risk label

6. Inject structured context into `AgentContext`:

```yaml
gaa_context:
  selected_procedures: [...]
  negative_knowledge: [...]
  semantic_facts: [...]
  similar_episodes: [...]
  known_failures: [...]
  task_state: [...]
  retrieval_decisions: [...]
```

### Acceptance criteria

* Obsolete/retired/quarantined memory is never injected.
* Maintenance-only memory appears only for matching legacy context.
* Negative knowledge precedes procedures.
* Retrieval decisions are traceable and testable.
* Dispatcher actually uses retrieved memory.

---

## Phase 6 — Implement the learning loop end-to-end

**Goal:** execution traces become learning deltas, not direct mutations.

### Files

* `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/runtime/BaseAgent.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/learning/*`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/*`
* `products/data-cloud/planes/action/agent-runtime/...`
* Data Cloud repositories for learning deltas

### Tasks

1. In `CAPTURE`, store:

    * trace ID
    * release ID
    * skill ID
    * version context digest
    * task classification
    * mode selected
    * retrieved memory IDs
    * tool calls
    * verification results
    * outcome/reward

2. In `REFLECT`, produce `LearningDelta`, not direct policies.

3. Enforce `LearningContract.requirePermitted(target)` before saving a delta. `LearningContract` already has hard governance boundaries for normal agents vs L5 governance workflows.

4. Add provenance requirements:

    * L2+ must have provenance
    * L3+ must have evaluation refs
    * L3+ must route through promotion

5. Add source trust:

    * user-provided
    * tool-observed
    * test-proven
    * official-doc verified
    * inferred
    * untrusted external

6. Add environment/version fields to `LearningDelta`:

    * `versionContextDigest`
    * `environmentFingerprintRef`
    * `repositoryConventionRef`
    * `runtimeFingerprintRef`

### Acceptance criteria

* No L3+ learning delta can be promoted without evidence/evals.
* Normal agents cannot propose `MASTERY_STATE`.
* L5 workflows cannot serve responses.
* Every delta has tenant, skill, agent release, provenance, and content digest.

---

## Phase 7 — Implement version-aware mastery policies

**Goal:** prevent old patterns from leaking into new work and prevent new patterns from breaking legacy systems.

### Files

* `platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/VersionScope.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/VersionConstraint.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/VersionRangeEvaluator.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/VersionCompatibilityPolicy.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/maintenance/MaintenanceOnlyPolicy.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/runtime/mode/DefaultModeSelectionPolicy.java`

### Tasks

1. Validate non-overlapping version constraints:

    * obsolete checked first is correct, but overlapping active/obsolete constraints should fail validation.

2. Add canonical policy:

```text
new project:
  active only
legacy project:
  maintenance allowed
migration task:
  active + maintenance + migration procedure
security task:
  block obsolete, quarantine vulnerable
```

3. Add `VersionContextResolver` integrations:

    * `package.json`
    * Gradle/Maven
    * Docker/runtime
    * IaC/platform versions
    * repo convention fingerprint

4. Add tests:

    * React Router v7 active
    * React Router v6 maintenance
    * React Router v4 obsolete
    * migration task can retrieve both v6 and v7 procedures
    * greenfield task cannot retrieve maintenance-only procedure

### Acceptance criteria

* Version mismatch blocks or gates execution.
* Maintenance-only skill is never used for new work.
* Migration tasks intentionally use old + new knowledge.
* Obsolete version is blocked even if semantic similarity is high.

---

## Phase 8 — Harden obsolescence detection and transitions

**Goal:** stale, unsafe, incompatible, or superseded mastery is detected and demoted/quarantined correctly.

### Files

* `platform/java/agent-core/src/main/java/com/ghatana/agent/obsolescence/DefaultObsolescenceDetector.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/obsolescence/DefaultObsolescenceTransitionService.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/obsolescence/ObsolescenceEvent.java`
* `platform/java/agent-core/src/main/java/com/ghatana/agent/obsolescence/ObsolescenceSignalRepository.java`
* `products/data-cloud/extensions/agent-registry/.../DataCloudObsolescenceSignalRepository.java`

### Tasks

1. Fix `DefaultObsolescenceTransitionService`:

    * use `getById(event.tenantId(), event.masteryId())`
    * use actual item state as `fromState`
    * use actual item skill/agent/release IDs
    * never infer skill ID with `split("-")`
    * include original event ID in transition evidence

2. Replace label/string matching with typed obsolescence signals:

    * dependency change
    * official docs contradiction
    * API break
    * CVE/security advisory
    * eval regression
    * runtime incompatibility
    * repeated production failure
    * replacement outperforming old procedure

3. Rework stale behavior:

    * stale should generally trigger `VERIFICATION_FIRST` or refresh workflow, not automatic `MAINTENANCE_ONLY`
    * version mismatch can produce `MAINTENANCE_ONLY`
    * security issue should produce `QUARANTINED`
    * end-of-life should produce `OBSOLETE`

4. Expand scan scope:

    * scan active + maintenance-only items
    * scan stale but not retired
    * scan quarantined for review expiry

5. Add obsolescence tests:

    * security advisory → QUARANTINED
    * new active version → MAINTENANCE_ONLY
    * EOL → OBSOLETE
    * no active use case → RETIRED
    * docs contradiction → OBSERVED/verification-first, not immediate obsolete unless severe

### Acceptance criteria

* Obsolescence events are tenant-scoped.
* Transitions use actual current state.
* Evidence keys match transition policy.
* Security issues quarantine immediately.
* Stale items are blocked from execution until refreshed.

---

## Phase 9 — Secure API and UI governance

**Goal:** mastery can be observed and managed, but not casually mutated.

### Files

* `products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/api/controller/MasteryController.java`
* Data Cloud API router/module wiring
* frontend/API contracts if present
* TypeScript kernel product contracts

### Tasks

1. Split APIs into:

    * read-only views
    * governed transition proposals
    * approval-backed manual transition
    * promotion engine trigger
    * obsolescence scan/transition
    * audit/evidence view

2. Restrict direct `saveMastery()`:

    * allow create/update only for governance workflows or admin bootstrap
    * normal user/API path must submit a proposal/delta

3. Restrict direct `transition()`:

    * require approval proof
    * require evidence bundle
    * require transition reason
    * require tenant ownership
    * write trace event

4. Add endpoints:

    * `POST /learning-deltas/{id}/evaluate`
    * `POST /learning-deltas/{id}/promote`
    * `POST /mastery/{id}/refresh`
    * `POST /mastery/{id}/quarantine-review`
    * `GET /mastery/{id}/retrieval-decisions`
    * `GET /mastery/{id}/version-applicability`

5. Add UI concepts:

    * mastery state timeline
    * version scope visualization
    * evidence bundle viewer
    * failed evaluations
    * known failure modes
    * maintenance-only warning
    * obsolescence alerts
    * promotion queue

### Acceptance criteria

* Production state changes are traceable.
* Manual transitions require approval.
* All mutation paths are tenant-scoped.
* UI shows why a skill is active, maintenance-only, obsolete, or quarantined.

---

## Phase 10 — CI, tests, and regression gates

The commit already includes a mastery regression gates document listing required test categories and coverage expectations.  Use that as the baseline, but tighten it around the actual defects above.

### Required tests

| Test group                           | Must cover                                                                            |
| ------------------------------------ | ------------------------------------------------------------------------------------- |
| `MasteryStateTest`                   | lifecycle helpers, quarantine semantics, retrieval/execution flags                    |
| `DefaultMasteryTransitionPolicyTest` | every legal/illegal transition and evidence key                                       |
| `PromotionEvidenceMapperTest`        | evidence key compatibility with transition policy                                     |
| `DefaultPromotionPolicyTest`         | mandatory test existence, human approval proof, target-specific gates                 |
| `DefaultPromotionEngineTest`         | idempotency, new mastery bootstrap, transition, score/version preservation            |
| `DataCloudMasteryRegistryTest`       | tenant isolation, save/query/decide/transition/stale/version ranking                  |
| `MasteryAwareModeSelectorTest`       | mastered/competent/practiced/observed/maintenance/obsolete/stale modes                |
| `GovernedAgentDispatcherTest`        | release guard, skill derivation, approval/verification proof, memory injection        |
| `MasteryAwareMemoryRetrieverTest`    | negative knowledge first, obsolete exclusion, maintenance filtering, no reversed sort |
| `ObsolescenceDetectorTest`           | typed signals and severity mapping                                                    |
| `ObsolescenceTransitionServiceTest`  | real getById/current-state transition, no fake state                                  |
| `MasteryControllerTest`              | access checks, mutation proof requirements                                            |
| Integration test                     | episode → delta → eval → promotion → mastery decision → dispatch                      |

### Performance gates

Keep the commit’s target performance idea, but enforce only after correctness is stable:

* `MasteryRegistry.query()` p95 under 50ms for 1000 items
* `MasteryRegistry.save()` p95 under 20ms per item
* governed dispatcher overhead under 100ms
* promotion evaluation under 100ms per delta
* obsolescence scan under 500ms for 1000 items

---

# 5. Recommended implementation order

## Sprint 1 — Make current mastery core correct

1. Fix `PromotionEvidenceMapper`.
2. Fix `DefaultPromotionPolicy` mandatory test existence and approval proof.
3. Fix `DefaultObsolescenceTransitionService`.
4. Fix `MasteryAwareMemoryRetriever` reversed sort.
5. Add tests for these four fixes.

This sprint removes the most likely correctness blockers.

---

## Sprint 2 — Make governed dispatch truly mastery-aware

1. Remove duplicate mastery decisions.
2. Add real memory retrieval/injection.
3. Strengthen skill ID derivation.
4. Enforce mode-level approval/verification.
5. Add dispatcher integration tests.

---

## Sprint 3 — Canonicalize memory and retrieval

1. Choose Data Cloud `MemoryPlane` as canonical.
2. Adapt or rename framework bridge.
3. Build structured context injector.
4. Add retrieval policy by execution strategy.
5. Add negative knowledge ordering tests.

---

## Sprint 4 — Complete promotion and evaluation pipeline

1. Add approval proof to `LearningDelta`.
2. Add version/environment metadata to deltas.
3. Build target-specific eval pack resolution from release skill refs.
4. Preserve multidimensional mastery score.
5. Persist version/applicability updates.

---

## Sprint 5 — Complete obsolescence and maintenance-only lifecycle

1. Replace label/string matching with typed signals.
2. Implement refresh workflow for stale items.
3. Scan maintenance-only items too.
4. Add EOL/replacement/security/test-failure transition tests.
5. Wire obsolescence scan to scheduled/triggered workflows.

---

## Sprint 6 — API/UI/governance hardening

1. Lock down direct save/transition endpoints.
2. Add approval-backed manual transition endpoint.
3. Add promotion and obsolescence workflow endpoints.
4. Add UI for mastery timeline, evidence, version scope, known failures, promotion queue.
5. Add audit trace for every mutation.

---

# 6. Final target behavior

When complete, the GAA should behave like this:

```text
1. Load AgentDefinition and AgentRelease.
2. Materialize LearningContract, mastery bindings, freshness policy, version compatibility.
3. Resolve version/environment context.
4. Classify task risk and novelty.
5. Query MasteryRegistry for skill/version/tenant.
6. Select execution strategy and supervision mode.
7. Retrieve mastery-filtered memory:
   - negative knowledge first
   - active procedures/facts
   - similar episodes
   - maintenance-only only for matching legacy context
   - no obsolete/retired/quarantined memory
8. Execute through GovernedAgentDispatcher.
9. Capture trace, memory, tool calls, proofs, and outcome.
10. Produce LearningDelta if something was learned.
11. Evaluate LearningDelta.
12. Promote only through PromotionEngine.
13. Transition mastery only through MasteryRegistry and transition policy.
14. Detect staleness/obsolescence continuously.
15. Keep active, maintenance-only, obsolete, retired, and quarantined states honest.
```

That gives you the system you want:

> A GAA that can learn over time, become competent and mastered through evidence, preserve old-version knowledge for maintenance, avoid stale/obsolete patterns, and never let uncontrolled self-learning mutate runtime behavior.

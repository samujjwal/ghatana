## Scope note

I statically scanned the code at commit `3bb3b3326284386053175646af0df94899b4fa9c`. I did not run the Gradle/pnpm build locally, but the target commit itself is titled **“changed - build fails”**, so the plan starts with build stabilization before continuing toward the self-learning GAA goal. 

# Executive readout

The repo is much closer to your GAA goal than in the earlier scan. At this commit, Ghatana already has first-pass implementations for:

* immutable `AgentDefinition` with learning, mastery bindings, skill refs, mastery policy refs, evaluation refs, security, observability, memory bindings, and release-digest support. 
* lifecycle execution pipeline with `ADMIT → PERCEIVE → REASON → VERIFY → ACT → CAPTURE → REFLECT → COMPLETE`, plus a pre-admit hook intended for task classification, environment snapshot, mastery decision, and mode selection. 
* multi-tier `MemoryPlane` covering episodic, semantic, procedural, typed artifacts, cross-tier query, semantic search, working memory, task-state memory, and checkpointing. 
* `LearningContract`, `LearningLevel`, `LearningTarget`, `LearningDelta`, and `LearningEngine` for controlled learning proposals.     
* mastery concepts: `MasteryItem`, `MasteryState`, `MasteryScore`, `VersionScope`, `MasteryRegistry`, `ObsolescenceDetector`, and Data Cloud-backed registry skeleton.       

The main issue is not lack of concepts. The issue is **fragmentation and incomplete wiring**. There are duplicate/parallel models, placeholder matching, in-memory repositories, direct learning bypasses, and schema inconsistencies that likely contribute to the failing build.

---

# Current architecture assessment

## What is strong

### 1. Agent definition is now close to the right shape

`AgentDefinition` now includes not only identity, type, tools, contracts, policies, evaluation refs, security, and observability, but also `masteryBindings`, `skillRefs`, `masteryPolicyRefs`, typed `toLearningContract()`, `toMasteryBinding()`, `toFreshnessPolicy()`, and `toVersionCompatibilityPolicy()`. Its canonical digest also includes mastery bindings, skill refs, and mastery policy refs, which is important because mastery-affecting config must change the release identity. 

### 2. Learning authority is correctly moving toward policy, not prompt behavior

`LearningLevel` is target-scoped: L1 allows episodic memory, L2 adds semantic facts and policy thresholds, L3 adds procedural skills and negative knowledge, L4 adds prompt/planner policies, and L5 allows all while being offline-only. It also now exposes provenance, promotion, online, and response-serving checks. 

`LearningContract` enforces provenance for L2+ and promotion for L3+, and permits a target only when the target is explicitly allowed and the level allows it. 

### 3. Mastery model matches the intended lifecycle

`MasteryState` includes `UNKNOWN`, `OBSERVED`, `PRACTICED`, `COMPETENT`, `MASTERED`, `MAINTENANCE_ONLY`, `OBSOLETE`, `RETIRED`, and `QUARANTINED`; it distinguishes executable states, new-work retrieval, legacy-work retrieval, terminal states, and evaluation requirements. 

`MasteryItem` links a skill to agent/release, lifecycle state, version scope, applicability, score, procedure IDs, semantic facts, negative knowledge, evidence refs, evaluation refs, known failures, freshness, and labels. 

### 4. Mastery scoring is multi-dimensional

`MasteryScore` tracks correctness, freshness, applicability, safety, transferability, evidence strength, and regression stability, and computes execution score as a product of key dimensions. That matches the design goal: mastery should not be a single confidence number. 

### 5. Memory plane is the right place for memory retrieval governance

`GovernedMemoryPlane` wraps `MemoryPlane`, checks `DataAccessBroker` before reads, validates learned memory writes, filters obsolete/retired/maintenance-only/negative-knowledge items by labels, and validates procedural skills for mastery metadata and provenance when applicable. 

---

# Critical gaps and risks

## P0 issue: product registry schema and registry disagree

The schema requires `manifestPath` and `manifestFormat` and defines them as strings/enums.  But the registry uses `null` for those fields in several products such as `data-cloud`, `yappc`, `audio-video`, `dcmaar`, `tutorputor`, `aura`, `software-org`, `virtual-org`, and `security-gateway`.  This is a direct schema-validation/build-risk candidate.

## P0 issue: duplicate learning delta models

There is a `com.ghatana.agent.learning.LearningDelta` record with rich lifecycle fields such as type, target, state, agent release, skill ID, content digest, proposed content, evidence refs, and timestamps.  There is also a separate `com.ghatana.agent.learning.delta.LearningDelta` record with a different shape: target ID, proposed artifact ref, rollback ref, confidence before/after, and review flag. 

This split will cause confusion and already shows up in API differences: `LearningEngine` uses the top-level `com.ghatana.agent.learning.LearningDelta`, while `PromotionEngine` imports `com.ghatana.agent.learning.delta.LearningDelta`.  

## P0/P1 issue: duplicate execution mode models

There is `com.ghatana.agent.mode.ExecutionMode` with values like `DETERMINISTIC`, `BOUNDED_PROBABILISTIC`, `FAST_LEARNING`, `MAINTENANCE_ONLY`, `HUMAN_GATED`, `VERIFICATION_FIRST`, and `BLOCKED`.  There is also `com.ghatana.agent.runtime.mode.ExecutionMode` with semantically similar but differently named values such as `DETERMINISTIC_EXECUTION`, `BOUNDED_PROBABILISTIC_REASONING`, and `EXPLORATORY_FAST_LEARNING`. 

This will produce inconsistent mode selection and likely compile/type drift across `DefaultAgentModeSelector`, `DataCloudMasteryRegistry`, `AgentTurnPipeline`, and runtime mode code.   

## P1 issue: version matching is currently placeholder-level

`VersionConstraint.packageVersion("react-router", ">=6.0.0 <7.0.0")` stores `type="package"` and `constraint="react-router@>=6.0.0 <7.0.0"`.  But `VersionScope.matchesConstraint()` only checks whether the environment has a dependency named by splitting the constraint at `@`; it does not evaluate the version range. 

`VersionAwareMemoryQueryPolicy` also treats `constraint.type()` as the package name and `constraint.constraint()` as the required version, which means it will look for a dependency named `"package"` instead of `"react-router"` for package constraints.  This is a correctness blocker for version-aware mastery.

## P1 issue: maintenance-only mode is defined but hard to reach

`MasteryState.MAINTENANCE_ONLY` is explicitly retrievable for legacy work but not active for default retrieval.  `DataCloudMasteryRegistry.decide()` filters candidates through `MasteryItem::isActiveForRetrieval`, so maintenance-only items are excluded before the decision logic can select `ExecutionMode.MAINTENANCE_ONLY`. 

## P1 issue: Data Cloud repositories are still in-memory

`DataCloudMasteryRegistry` says it should store mastery items, transitions, and evidence in Data Cloud collections, but the implementation uses `ConcurrentHashMap` and has a TODO to replace in-memory storage with actual Data Cloud persistence.  `DataCloudLearningDeltaRepository` is also in-memory with the same TODO pattern. 

## P1 issue: learning can still bypass promotion

`LearningEngine` creates deltas when a `LearningDeltaRepository` is configured, but otherwise falls back to direct policy persistence in `MemoryStore`.  For L3+ learning, that fallback violates the design goal that procedural skills and behavior-changing knowledge must go through evaluation and promotion.

---

# Implementation plan

## Phase 0 — Restore build and remove duplicated models

### Goal

Make the target commit buildable and establish one canonical model for product registry, execution mode, and learning delta.

### Tasks

| File / area                                                                                      | Change                                                                                                                                                                                                                                                                                                                                                            |
| ------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `config/canonical-product-registry-schema.json`                                                  | Fix `manifestPath` and `manifestFormat` contract. Either allow `null` via `type: ["string","null"]` and `enum: ["yaml","json",null]`, or remove them from `required` and add a `manifest` object with `required: false`. Current schema requires non-null string values.                                                                                          |
| `config/canonical-product-registry.json`                                                         | Keep `null` only if schema explicitly allows it; otherwise add actual manifest paths for products with `conformance.manifest=true`. Current registry has many `null` manifest fields.                                                                                                                                                                             |
| `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/*` and `.../learning/delta/*` | Choose one canonical `LearningDelta` package. Recommended: keep `com.ghatana.agent.learning.LearningDelta` because `LearningEngine` and `DataCloudLearningDeltaRepository` already use it. Delete/merge `com.ghatana.agent.learning.delta.LearningDelta` and move any missing fields such as rollback ref and confidence before/after into the canonical model.   |
| `platform/java/agent-core/src/main/java/com/ghatana/agent/promotion/PromotionEngine.java`        | Update import to the canonical `LearningDelta`; remove dependency on the duplicate delta package.                                                                                                                                                                                                                                                                 |
| `platform/java/agent-core/src/main/java/com/ghatana/agent/mode/*` and `.../runtime/mode/*`       | Choose one canonical execution-mode model. Recommended: keep `com.ghatana.agent.runtime.mode.ExecutionMode` only if it is the runtime-facing API, then migrate `DefaultAgentModeSelector` and `DataCloudMasteryRegistry` to it; otherwise delete `runtime.mode.ExecutionMode` and use `agent.mode.ExecutionMode` everywhere.                                      |
| All tests importing old packages                                                                 | Update imports and expected enum values after canonicalization. Search results show tests already exist for mastery lifecycle, mode selection, governed dispatcher mastery, Data Cloud mastery registry, and learning delta factory.                                                                                                                              |

### Definition of done

* One `LearningDelta` model.
* One `ExecutionMode` model.
* Product registry schema validates current registry.
* Java compile succeeds for `agent-core`, `data-cloud:extensions:agent-registry`, and `data-cloud:planes:action:agent-runtime`.

---

## Phase 1 — Canonicalize GAA lifecycle and runtime wiring

### Goal

Ensure every agent turn can run the full governed loop:

```text
ADMIT → PERCEIVE → REASON → VERIFY → ACT → CAPTURE → REFLECT → COMPLETE
```

`AgentTurnPipeline` already contains this lifecycle and a pre-admit hook for task classification, environment snapshot, mastery decision, and mode selection.  The work is to make that hook mandatory in governed runtime paths.

### Tasks

| File / area                     | Change                                                                                                                                                                                                            |
| ------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `AgentTurnPipeline.java`        | Make pre-admit enrichment an explicit governed-runtime path rather than optional/ad hoc. Add a builder convenience: `withGovernancePreAdmit(MasteryRegistry, EnvironmentFingerprintProvider, AgentModeSelector)`. |
| `BaseAgent.java`                | Keep simple lifecycle compatibility, but ensure production dispatch uses `AgentTurnPipeline.executeResult()` so trace, release ID, phase refs, and result envelope are always populated.                          |
| `AgentContext` / context bridge | Ensure `agentReleaseId`, `specDigest`, tenant, user, trace ID, version context, and mastery decision are available as typed context entries, not only string config.                                              |
| `GovernedAgentDispatcher`       | It should refuse execution if release is not runnable, if mode is `BLOCKED`, or if the mastery decision requires approval and approval is absent. Search results show this dispatcher exists in agent-runtime.    |

### Definition of done

* Every response-serving run has phase traces.
* Every run has selected execution mode.
* Every run has mastery decision or explicit “no mastery found.”
* Every side-effecting run has policy/approval result.

---

## Phase 2 — Productionize Mastery Registry persistence

### Goal

Replace in-memory maps with durable Data Cloud storage and append-only transition/evidence logs.

`MasteryRegistry` already defines the correct contract: find by skill/environment, query, decide, save, transition, and find stale.  `DataCloudMasteryRegistry` currently uses in-memory maps and TODOs for persistence. 

### Tasks

| File / area                             | Change                                                                                                                                                                                                                                 |
| --------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `DataCloudMasteryRegistry.java`         | Replace `ConcurrentHashMap` storage with Data Cloud repository/collection APIs for `agent-mastery-items`, `agent-mastery-transitions`, and `agent-mastery-evidence`.                                                                   |
| `MasteryItemMapper.java`                | Ensure every field in `MasteryItem` is mapped: version scope, applicability, score vector, procedure/fact/negative-knowledge IDs, evidence/evaluation refs, known failures, freshness, and labels. Search results show mapper exists.  |
| New `MasteryEvidenceRepository`         | Store evidence as first-class records, not only string refs. Evidence should include trace IDs, test runs, docs snapshots, tool outputs, human review, and regression results.                                                         |
| `MasteryTransition` / transition policy | Make transitions append-only; never overwrite without a transition event. `MasteryRegistry.transition()` already documents expected transition rules.                                                                                  |
| `MasteryController.java`                | Expose read/query/transition/evidence endpoints only through governance checks. Search results show controller exists.                                                                                                                 |

### Definition of done

* Restart does not lose mastery or learning deltas.
* Every mastery state change is auditable.
* Mastery state can be reconstructed from append-only transitions.
* Repository tests use real persistence or a Data Cloud test backend, not only maps.

---

## Phase 3 — Make version-aware mastery correct

### Goal

Version-aware mastery must not be approximate. It is central to your goal: old library mastery becomes maintenance-only, new version mastery requires fast-learning, and obsolete knowledge is blocked.

### Tasks

| File / area                                 | Change                                                                                                                                                                                                                                                                                                        |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `VersionConstraint.java`                    | Replace ambiguous `type + constraint` with structured fields: `kind`, `name`, `range`, `ecosystem`. Example: `kind=PACKAGE`, `name=react-router`, `range=>=7.0.0 <8.0.0`, `ecosystem=npm`. Current factory stores `type="package"` and `constraint="react-router@>=..."`, which causes downstream confusion.  |
| `VersionScope.java`                         | Replace `matchesConstraint()` placeholder with real version range evaluation. Current logic only checks dependency existence and ignores range comparison.                                                                                                                                                    |
| `VersionAwareMemoryQueryPolicy.java`        | Use the structured `VersionConstraint.name()` and `range()` values. Current implementation treats `constraint.type()` as the package name, which is wrong for constraints created by `VersionConstraint.packageVersion`.                                                                                      |
| `EnvironmentFingerprint` / `VersionContext` | Ensure package/tool/runtime versions are captured from real project files: `package.json`, lock files, Gradle, Maven, Python, Docker, tool versions, runtime versions.                                                                                                                                        |
| Tests                                       | Add explicit tests for `react-router@6` maintenance, `react-router@7` active, incompatible v5/v7 pattern prevention, range syntax, unknown version, obsolete version, and migration task.                                                                                                                     |

### Definition of done

* `react-router@6` retrieves only maintenance-compatible v6 knowledge.
* `react-router@7` retrieves active v7 knowledge.
* Obsolete versions are blocked or warning-only.
* Unknown versions trigger `FAST_LEARNING` or `VERIFICATION_FIRST`.

---

## Phase 4 — Fix mode selection so mastery state drives behavior correctly

### Goal

Unify task classification, mastery decision, and execution mode selection.

### Tasks

| File / area                                | Change                                                                                                                                                                                                                                                                                                                |
| ------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `DefaultAgentModeSelector.java`            | After execution-mode enum canonicalization, keep one selector path. It already maps mastered → deterministic, competent/variation → bounded probabilistic, unknown/exploration → fast-learning, migration → verification-first, high-risk → human-gated, stale → verification-first, obsolete/quarantined → blocked.  |
| `DataCloudMasteryRegistry.decide()`        | Stop filtering out `MAINTENANCE_ONLY` when the query says legacy/maintenance is allowed. Current `MasteryItem::isActiveForRetrieval` filter excludes maintenance-only before mode selection.                                                                                                                          |
| `TaskClassifier.java` and `TaskClass.java` | Keep task classification simple but make it evidence-driven: known task, known variation, unknown, high-risk, maintenance, exploration, migration.                                                                                                                                                                    |
| `MasteryAwareModeSelector.java`            | Decide whether this newer runtime selector replaces `DefaultAgentModeSelector` or is deleted. It currently uses separate runtime-mode types and `TaskClassification` abstractions, creating a parallel path.                                                                                                          |
| `ModeSelectionPolicy.java`                 | Align with the one canonical mode enum. It currently uses runtime-mode `ExecutionMode`.                                                                                                                                                                                                                               |

### Definition of done

* One public mode-selection service.
* One `ExecutionMode` enum.
* Maintenance-only can be selected for matching legacy environments.
* Human-gated and blocked decisions prevent execution.

---

## Phase 5 — Complete safe learning delta pipeline

### Goal

The agent may observe freely, but learned behavior changes must go through deltas, evaluation, and promotion.

### Tasks

| File / area                                                           | Change                                                                                                                                                                                                                                      |
| --------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `LearningEngine.java`                                                 | Remove direct policy persistence for L3+ learning. If `promotionRequired=true`, no fallback to `memoryStore.storePolicy()` should be allowed. Current code falls back to direct policy persistence when no delta repository is configured.  |
| `LearningDelta.java`                                                  | Merge both delta models into one canonical model. Include rollback ref, confidence before/after, content digest, proposed content/artifact ref, evidence refs, evaluation refs, review flag, timestamps, and state.                         |
| `LearningDeltaState.java`                                             | Keep the lifecycle: proposed/pending evaluation/evaluated/approved/promoting/promoted/rejected/promotion failed/obsolete.                                                                                                                   |
| `LearningDeltaRepository` and `DataCloudLearningDeltaRepository.java` | Replace in-memory repository with durable Data Cloud persistence; support state transitions with optimistic concurrency or append-only events.                                                                                              |
| `LearningDeltaEvaluator.java`                                         | Evaluate deltas against target-specific checks: semantic facts require provenance; procedural skills require procedure tests; retrieval policies require offline replay; planner/prompt changes require safety and regression checks.       |
| `PromotionEngine.java`                                                | Promote only evaluated/approved deltas and write to `MasteryRegistry`, `MemoryPlane`, or release artifacts as appropriate.                                                                                                                  |

### Definition of done

* L3 procedural skill cannot become active without delta + eval + promotion.
* L5/model adapter deltas cannot run online.
* Every promoted learning delta has provenance, evaluation result, rollback ref, and trace refs.

---

## Phase 6 — Make mastery-aware memory retrieval reliable

### Goal

Memory retrieval must rank by applicability, freshness, lifecycle, version compatibility, and negative knowledge before semantic similarity.

### Tasks

| File / area                                                      | Change                                                                                                                                                                                                                                                |
| ---------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `MemoryPlane.java`                                               | Keep as SPI, but add `query(MemoryQuery)` support for version context, mastery states, maintenance allowance, negative knowledge inclusion, and freshness requirement.                                                                                |
| `GovernedMemoryPlane.java`                                       | Stop relying only on labels for mastery filtering. Use `MasteryRegistry` for retrieval decisions, or require memory items to include canonical mastery IDs. Current filtering is label-based and `searchSemantic()` lacks query-level include flags.  |
| `MasteryAwareMemoryRetriever.java`                               | Make this the canonical retrieval path for agent execution. Search results show it exists.                                                                                                                                                            |
| `NegativeKnowledgePrioritizer.java`                              | Ensure negative knowledge is always retrieved for matching skill/version before positive procedures. Search results show this exists.                                                                                                                 |
| `MaintenanceModeMemoryFilter.java` / `ObsoleteMemoryFilter.java` | Consolidate duplicates between agent-core and data-cloud runtime if both exist. Search results show maintenance and obsolete filters in multiple paths.                                                                                               |

### Definition of done

For the same task, retrieval differs correctly by context:

* new project → active/mastered skills only
* legacy project → maintenance-only allowed
* migration task → both old and target-version knowledge allowed
* obsolete skill → excluded unless audit/history mode
* negative knowledge → retrieved when matching risk/context

---

## Phase 7 — Implement obsolescence detection end-to-end

### Goal

Mark stale/unsafe knowledge automatically, but transition through governed registry state changes.

### Tasks

| File / area                    | Change                                                                                                                                                                                                              |
| ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ObsolescenceDetector.java`    | Implement detectors for version mismatch, API signature change, runtime incompatibility, repeated execution failure, security vulnerability, docs contradiction, and freshness expiry.                              |
| `ObsolescenceEvent.java`       | Expand evidence map into typed evidence refs where possible. It already carries event ID, mastery ID, reason, description, detected time, evidence, and metadata.                                                   |
| `MasteryRegistry.transition()` | Route obsolescence events to `OBSOLETE`, `MAINTENANCE_ONLY`, `RETIRED`, or `QUARANTINED` depending on severity. Existing transition docs already describe any-active-state → obsolete and any-state → quarantined.  |
| Scheduler / background worker  | Add periodic stale scans via `findStale(now)` and event-triggered scans on dependency/runtime/docs/security changes.                                                                                                |
| Tests                          | Add tests for version changed, docs contradiction, repeated failure, security advisory, staleAfter expired, and better replacement exists.                                                                          |

### Definition of done

* Stale knowledge is not silently retrieved.
* Security-obsolete knowledge is quarantined, not only marked obsolete.
* Maintenance-only transition happens when legacy systems still exist.
* Retired knowledge is audit/provenance only.

---

## Phase 8 — Evaluation and promotion gates

### Goal

A GAA becomes a master only when it has evidence and passes evals.

### Tasks

| File / area                       | Change                                                                                                                                                                                                                                                                                                       |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `AgentDefinitionValidator.java`   | Keep and expand validation: high-risk agents require evaluation refs; adaptive agents require skill refs; procedural skill learning requires promotion; semantic facts require provenance; model adapter requires L5; L5 cannot be response-serving. These checks already exist and should become CI gates.  |
| `EvaluationResult` / eval modules | Add skill-specific eval packs: skill unit tests, regression tests, safety tests, version compatibility tests, recovery tests, abstention tests.                                                                                                                                                              |
| `DefaultPromotionPolicy.java`     | Require target-specific checks. Example: `PROCEDURAL_SKILL` needs repeated evidence + eval pass + rollback; `RETRIEVAL_POLICY` needs replay/A-B; `PLANNER_POLICY` requires human approval. Search results show policy exists.                                                                                |
| `AgentRelease`                    | Ensure `capabilityMaturityProfile`, `evaluationPackId`, `memoryContractId`, and threat/redaction profiles are mandatory before response-serving. Earlier scan showed release model supports these governance artifacts; keep this as release gate.                                                           |
| CI                                | Add `agent-mastery-conformance` task that validates all agent definitions, learning contracts, mastery bindings, eval refs, and release readiness.                                                                                                                                                           |

### Definition of done

A skill reaches `MASTERED` only if:

* procedure exists
* negative knowledge exists for known traps
* regression tests pass
* safety tests pass
* compatibility tests pass
* recovery/rollback path exists
* evidence refs are present
* freshness policy is satisfied

---

## Phase 9 — Product integration path

### Goal

Make GAA platform-level, not YAPPC-only or Data Cloud-only.

### Tasks

| Product / area                           | Change                                                                                                                                                                                                                                     |
| ---------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| YAPPC agents                             | Add `skillRefs`, `masteryBindings`, `learningLevel`, `adaptationTargets`, `evaluationRefs`, and version scopes for code-generation, scaffold, UI, architecture, and test agents.                                                           |
| Data Cloud / AEP runtime                 | Ensure all product agents execute through governed runtime, not bespoke direct calls.                                                                                                                                                      |
| Finance / TutorPutor / Digital Marketing | Start with low-risk L1/L2 learning: episodic capture, semantic facts, negative knowledge; only promote procedural skills after eval packs exist.                                                                                           |
| Product registry                         | Add product conformance gates for agent-enabled products: agent definitions validate, mastery bindings validate, eval packs exist, runtime module included.                                                                                |
| UI/admin                                 | Expose mastery state, learning deltas, obsolete/maintenance-only items, and promotion queue in Data Cloud UI. Search results show `MasteryController` and `LearningDeltaController` exist, so use those API surfaces as backend anchors.   |

---

# Recommended implementation order

1. **Fix build blockers**

   * product registry schema/null mismatch
   * duplicate `LearningDelta`
   * duplicate `ExecutionMode`
   * package/import mismatches

2. **Make contracts canonical**

   * one learning delta model
   * one execution mode enum
   * one mode-selection path
   * one version constraint model

3. **Make mastery persistent**

   * replace in-memory `DataCloudMasteryRegistry`
   * replace in-memory `DataCloudLearningDeltaRepository`
   * add append-only transition/evidence storage

4. **Make version matching real**

   * parse semver/ranges
   * support npm, Maven/Gradle, Python, Docker/runtime/tool versions
   * add compatibility tests

5. **Wire pre-admit governance**

   * environment fingerprint
   * task classification
   * mastery decision
   * mode selection
   * release-policy gate

6. **Close learning loop safely**

   * reflection produces deltas
   * deltas evaluated
   * deltas promoted
   * mastery updated
   * memory retrieval changes only after promotion

7. **Add obsolescence and maintenance**

   * stale scan
   * dependency-change scan
   * security/docs contradiction scan
   * automatic transition proposals

8. **Add full eval packs**

   * skill evals
   * regression evals
   * safety evals
   * version compatibility evals
   * trace grading

---

# Final target architecture

```text
AgentDefinition
  → AgentRelease
  → LearningContract
  → MasteryBinding
  → Runtime Policy

Task
  → EnvironmentFingerprint
  → TaskClassifier
  → MasteryRegistry.decide()
  → ModeSelectionPolicy
  → AgentTurnPipeline

Execution
  → GovernedMemoryPlane retrieval
  → Reason / Act / Verify
  → Trace + Episode + Artifacts

Reflection
  → LearningEngine
  → LearningDelta
  → Evaluation
  → PromotionEngine
  → MasteryRegistry transition
  → MemoryPlane update
  → Obsolescence scan
```

# Success criteria for your goal

The GAA goal is reached when the platform can prove the following:

1. A new/unknown task starts in `FAST_LEARNING` or `VERIFICATION_FIRST`, not false confidence.
2. A repeated successful pattern becomes a `LearningDelta`, not an immediate active behavior.
3. A procedural skill becomes active only after evaluation and promotion.
4. A mastered skill is version-scoped, freshness-scoped, and evidence-backed.
5. Old versions become `MAINTENANCE_ONLY`, not deleted and not used for new work.
6. Obsolete or unsafe skills are blocked or quarantined.
7. Every decision is traceable to agent release, mastery state, memory, evidence, and evaluation.
8. Data Cloud stores mastery, deltas, evidence, transitions, and audit trails durably.

The current code has the right conceptual pieces. The next step is to **remove duplicated models, make persistence real, make version matching real, and wire mastery decisions into the execution path as a hard governance gate**.

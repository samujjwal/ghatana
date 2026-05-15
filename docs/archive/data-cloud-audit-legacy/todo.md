I scanned the target commit `1334d4e26f0e854352ddae12e6019e9cc6cd8d9b` through the GitHub connector. I did not run the repo locally, so the plan below is based on static code inspection of the target snapshot, not executed tests.

## Executive verdict

The repo is **not starting from zero**. At this commit, Ghatana already has a surprisingly strong first implementation of the GAA/self-learning/mastery direction:

* A five-layer agent architecture is formally accepted in ADR-020.
* `AgentDefinition` now includes mastery bindings, skill refs, mastery policy refs, learning contract materialization, freshness policy materialization, and version-compatibility policy materialization.
* Learning authority is typed and gated by `LearningLevel`, `LearningContract`, and `LearningTarget`.
* `MASTERY_STATE` exists and is explicitly offline-only / governance-only.
* A first-class mastery model exists: `MasteryItem`, `MasteryState`, `MasteryScore`, `VersionScope`, `MasteryDecision`, `MasteryRegistry`.
* Data Cloud has a `DataCloudMasteryRegistry`.
* Runtime mode selection exists through `MasteryAwareModeSelector` and `DefaultModeSelectionPolicy`.
* The governed dispatcher is already aware of release state, mastery, version context, task classification, mode selection, approval/verification proof, memory retrieval, and trace ledger.
* Learning is moving toward `LearningDelta` instead of uncontrolled direct mutation.
* Promotion and obsolescence flows already exist.

The implementation goal should therefore be **integration, hardening, correctness, productionization, and product rollout**, not inventing a parallel framework.

---

# 1. Current architecture found in the repo

## 1.1 Five-layer operating model is already the right north star

ADR-020 defines the platform-wide agent system as five layers: Specification/Release, Control/Governance, Execution, Memory/Context/Evaluation, and Product Capability. It explicitly states that `agent-core` owns contracts, AEP/Data Cloud owns execution and durable registry/memory, all side-effecting tools must route through governed tool execution, and the governed lifecycle is `ADMIT → PERCEIVE → REASON → VERIFY → ACT → CAPTURE → REFLECT → COMPLETE`.

This aligns almost perfectly with your target GAA model. The core rule should remain:

> Product agents may own domain behavior, but platform contracts, release governance, telemetry, learning, mastery, and memory rules must be common.

## 1.2 `AgentDefinition` already carries mastery and learning metadata

`AgentDefinition` is an immutable, versioned blueprint. At the target commit, it includes identity, behavior, determinism, contracts, tools, memory bindings, policy refs, evaluation refs, observability/security contracts, **mastery bindings**, **skill refs**, and **mastery policy refs**. It also materializes typed learning contracts, mastery bindings, freshness policies, and version-compatibility policies. It validates that agents with mastery bindings have skill refs, and that L3+ agents have mastery policy refs and evaluation refs.

This is a strong design. The next step is to make every deployed product agent conform to it.

## 1.3 Learning authority is properly separated from mastery mutation

`LearningLevel` defines the canonical learning ladder:

| Level | Meaning                                                  |
| ----- | -------------------------------------------------------- |
| L0    | No learning                                              |
| L1    | Episodic memory                                          |
| L2    | Semantic facts, retrieval/confidence/routing policies    |
| L3    | Procedural skills and negative knowledge                 |
| L4    | Prompt templates and planner policies                    |
| L5    | Offline governance/model adapter/mastery-state workflows |

The important safety rule is already present: `MASTERY_STATE` is blocked for sub-L5 agents, and L5 is offline-only and cannot serve responses directly.

`LearningContract` strengthens that boundary: normal agents cannot propose `MASTERY_STATE`, and only explicit L5 governance workflows can do so. It also enforces provenance for L2+ and promotion for L3+.

This is the correct foundation for safe self-learning.

## 1.4 Mastery registry exists as a first-class concept

`MasteryItem` tracks skill maturity, version applicability, lifecycle state, procedure IDs, semantic fact IDs, negative knowledge IDs, evidence refs, evaluation refs, known failure modes, history, freshness, labels, and confidence.

`MasteryState` has exactly the lifecycle we discussed: `UNKNOWN`, `OBSERVED`, `PRACTICED`, `COMPETENT`, `MASTERED`, `MAINTENANCE_ONLY`, `OBSOLETE`, `RETIRED`, and `QUARANTINED`. It also distinguishes retrievable-for-new-work, retrievable-for-legacy-work, executable, terminal, and potentially executable states.

`MasteryScore` is multi-dimensional: correctness, freshness, applicability, safety, transferability, evidence strength, and regression stability. The execution score is a product of key dimensions.

This is the correct model. The main missing work is making it consistently drive runtime behavior across all agents.

## 1.5 Version-aware mastery exists but needs hardening

`VersionScope` already separates active, maintenance, and obsolete version constraints, and classifies a `VersionContext` as `ACTIVE`, `MAINTENANCE`, `OBSOLETE`, or `UNKNOWN`.

However, the code contains a major TODO-style weakness: overlap validation is disabled, and overlap detection is currently simplified. This is dangerous for your goal because ambiguous version scope can cause the agent to apply old mastery to new systems or new mastery to legacy systems.

## 1.6 Data Cloud-backed mastery registry exists

`MasteryRegistry` defines `findBest`, `query`, `decide`, `save`, `transition`, stale detection, and tenant-scoped lookup. It documents the proper transition rules, including evidence for `MASTERED`, obsolescence, quarantine, and retirement.

`DataCloudMasteryRegistry` persists mastery items, transitions, and evidence through Data Cloud collections; requires tenant isolation; ranks by version applicability, mastery state, execution score, and freshness; supports idempotent save; validates tenant mismatch; and uses transition policy before state changes.

The design is good. Production hardening is still needed around transactionality, indexing, consistency, and migration.

## 1.7 Runtime mode selection exists

`MasteryAwareModeSelector` uses `MasteryRegistry`, `TaskClassifier`, `ModeSelectionPolicy`, and canonical `VersionContext` encoding to select execution mode. It records trace metadata such as skill ID, mastery item ID, mastery state, version applicability, execution score, staleness, terminal state, task risk, novelty, version context, and decision reason.

`DefaultModeSelectionPolicy` maps:

* `MASTERED + ACTIVE` → deterministic autonomous execution.
* `COMPETENT + ACTIVE` → bounded probabilistic supervised execution.
* `PRACTICED + ACTIVE` → fast-learning human-gated execution.
* `OBSERVED` → verification-first human-gated execution.
* `MAINTENANCE_ONLY` → maintenance-only human-gated execution.
* obsolete/terminal/stale states → blocked.

This is very close to the desired behavior.

## 1.8 Governed runtime is aware of mastery, version, approval, verification, and memory

`GovernedAgentDispatcher` checks release state, kill switch/release response-serving rules, capability manifest, skill ID requirements for mastery-bound releases, version context, mastery decisions, approval proofs, verification proofs, mode selection, memory retrieval, trace ledger, and OpenTelemetry tracing. It denies dispatch when release state, mastery state, approval, verification, or version compatibility fail.

`AgentTurnPipeline` also has a reusable lifecycle: `ADMIT → PERCEIVE → REASON → VERIFY → ACT → CAPTURE → REFLECT → COMPLETE`, with pre-admit enrichment for environment fingerprinting, task classification, mastery decision, and mode selection, plus post-act verification.

The runtime shape is right. The plan should focus on ensuring all production paths use this pipeline and do not bypass it.

## 1.9 Learning loop now emits deltas

`LearningEngine` has moved in the right direction: L3+ learning requires a `LearningDeltaRepository`; candidates become `LearningDelta`s rather than direct behavior mutation; low-confidence candidates become human-review deltas; contract violations are counted as rejected.

`LearningDelta` carries target, state, agent/release/skill/tenant, content digest, proposed content, evidence refs, evaluation refs, source episodes, rollback ref, confidence before/after, review state, approval proof, and version/environment/runtime provenance fields.

This is exactly the right safety model.

## 1.10 Promotion and obsolescence exist

`DefaultPromotionEngine` evaluates learning deltas, checks promotion policy, determines target mastery state, creates or updates mastery items, applies mastery transitions, updates procedure/fact/negative knowledge refs, preserves score dimensions, and marks deltas as promoted.

`ObsolescenceEvent` captures dependency changes, API changes, security advisories, documentation sources, severity, reason, evidence, and recommended transition.

`ObsolescenceScanner` supports scheduled and tenant-scoped scans, uses a detector/router, queries tenant mastery items, and routes detected obsolescence events into transitions.

This supports your active/maintenance/obsolete/retired lifecycle.

---

# 2. Main gaps to close

## Gap 1 — Runtime integration must become mandatory

The repo has both the architecture and implementation pieces, but the critical production risk is **partial adoption**. Every agent invocation must go through:

```text
GovernedAgentDispatcher
→ AgentTurnPipeline
→ mastery/version/mode selection
→ memory retrieval
→ verification
→ capture
→ learning delta proposal
→ promotion/evaluation
```

No product-local dispatch path should bypass this.

## Gap 2 — Version compatibility needs stronger semantics

`VersionScope` is promising, but overlap validation is disabled and range overlap detection is simplified. This is a direct risk to your version-aware mastery goal.

## Gap 3 — Promotion creates weak initial mastery defaults

`DefaultPromotionEngine` can bootstrap a mastery item with `domain = skillId`, `VersionScope.empty()`, minimal applicability, and `staleAfter` of 30 days. That is okay for early scaffolding, but not enough for a production mastery registry.

## Gap 4 — Learning synthesis is too primitive

`LearningEngine` currently derives candidate policies from repeated action/reward pairs. That is a useful placeholder, but real GAA mastery needs typed extraction of:

* semantic facts
* procedural skills
* negative knowledge
* failure modes
* version constraints
* compatibility evidence
* rollback procedures
* evaluation suggestions

## Gap 5 — Memory retrieval must become mastery-aware everywhere

`MemoryPlane` supports episodic, semantic, procedural, working, task-state, typed artifacts, and semantic search.  But the core rule must become universal:

> Do not retrieve by similarity alone. Retrieve by version compatibility, freshness, mastery state, trust, risk, and task applicability.

`ObsoleteMemoryFilter` exists, but it must be consistently integrated into all retrieval paths.

## Gap 6 — Data Cloud transition persistence needs stronger consistency

`DataCloudMasteryRegistry` appends the transition first and then updates the item. This is idempotent-friendly, but it can leave transition and item state temporarily inconsistent if save fails.  Add a transactional boundary or reconciliation/outbox worker.

## Gap 7 — Product agent definitions need migration

`AgentDefinition` supports mastery fields, but product YAML definitions likely need systematic migration to include:

* `skillRefs`
* `masteryBindings`
* `masteryPolicyRefs`
* `evaluationRefs`
* `learningLevel`
* version compatibility policy refs
* freshness policy refs
* obsolescence policy refs

## Gap 8 — CI gates are documented but must be enforced

The repo has a `MASTERY_CI_REGRESSION_GATES.md` with required tests, coverage thresholds, performance benchmarks, rollback criteria, and production monitoring. This must become an actual CI workflow, not only documentation.

---

# 3. Target end-state

The desired production-grade GAA flow should be:

```text
1. Load AgentRelease
2. Validate release state and governance artifacts
3. Resolve tenant, skill, task, and version context
4. Query MasteryRegistry
5. Select execution strategy and supervision mode
6. Retrieve only compatible, fresh, non-obsolete memory
7. Execute through governed lifecycle
8. Verify before/after side effects
9. Capture trace/evidence
10. Emit LearningDelta, not direct mutation
11. Evaluate delta through skill-specific EvaluationPack
12. Promote through PromotionEngine
13. Update MasteryRegistry
14. Detect staleness/obsolescence continuously
15. Demote, quarantine, maintenance-mark, or retire when evidence requires
```

---

# 4. Detailed implementation plan

## Phase 0 — Establish non-negotiable invariants

**Goal:** Lock the target behavior before adding more code.

Add or update a canonical spec in:

```text
docs/agent-system/gaa-self-learning-mastery-architecture.md
```

Content should define:

* GAA lifecycle.
* Learning vs mastery separation.
* `LearningDelta` as the only online learning mutation output.
* `MASTERY_STATE` as governance-only.
* Version-aware mastery rules.
* Maintenance-only behavior.
* Fast-learning behavior.
* Obsolescence rules.
* Promotion and quarantine rules.
* Required telemetry.

**Do not create another competing architecture document.** Reference ADR-020 as the canonical architectural boundary.

---

## Phase 1 — Harden agent-core contracts

### Files to update

```text
platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/VersionScope.java
platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/VersionRangeEvaluator.java
platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/VersionConstraint.java
platform/java/agent-core/src/test/java/com/ghatana/agent/mastery/VersionScopeTest.java
```

### Changes

1. Re-enable overlap validation in `VersionScope`.
2. Implement real semver/range overlap detection.
3. Detect conflicts across:

    * active vs maintenance
    * active vs obsolete
    * maintenance vs obsolete
4. Add invalid cases:

    * same package appears in active and obsolete with overlapping ranges
    * maintenance range overlaps active without explicit precedence
    * unknown range syntax
5. Add support for ecosystem-specific ranges:

    * npm semver
    * Maven version ranges
    * Python PEP 440
    * runtime versions
    * framework versions

### Acceptance criteria

* Ambiguous version scopes fail at definition/materialization time.
* No mastery item can be saved with overlapping active/maintenance/obsolete constraints.
* Tests cover React Router v5/v6/v7, Java version ranges, and runtime versions.

---

## Phase 2 — Make release governance stricter

### Files to update

```text
platform/java/agent-core/src/main/java/com/ghatana/agent/release/AgentRelease.java
platform/java/agent-core/src/main/java/com/ghatana/agent/release/AgentReleaseBuilder.java
platform/java/agent-core/src/test/java/com/ghatana/agent/release/AgentReleaseTest.java
products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/registry/DataCloudAgentReleaseRepository.java
```

### Current state

`AgentRelease` already includes tenant, policy pack, evaluation pack, memory contract, mastery policy pack, learning contract, version compatibility policy, freshness policy, skill-specific evaluation pack refs, and skill-specific mastery policy refs. It requires mastery policy and learning contract for response-serving releases.

### Changes

1. Enforce skill-specific evaluation packs for any release with `skillRefs`.
2. Enforce skill-specific mastery policy refs for any release with `masteryPolicyPackId`.
3. Add release validation:

    * `CANARY` and `ACTIVE` require `skillEvaluationPackRefs` for all declared skills.
    * `CANARY` and `ACTIVE` require `masteryPolicyPackRefs` for all declared skills.
    * `L3+` release requires evaluation pack + promotion policy.
4. Add release digest verification to dispatch path.
5. Store `releaseDigest` in trace metadata.

### Acceptance criteria

* No response-serving agent can run without learning contract, memory contract, evaluation pack, and mastery policy pack.
* Release digest changes when skill evaluation refs or mastery policy refs change.
* Dispatch refuses mismatched release digest.

---

## Phase 3 — Make governed runtime the only execution path

### Files to update

```text
products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcher.java
platform/java/agent-core/src/main/java/com/ghatana/agent/framework/runtime/AgentTurnPipeline.java
products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/aep/di/AepOrchestrationModule.java
```

### Current state

`GovernedAgentDispatcher` already performs release checks, skill ID checks, version context resolution, mastery decisions, approval proof checks, verification proof checks, mode selection, memory retrieval, and trace ledger recording.

`AgentTurnPipeline` already has pre-admit enrichment and post-act verification hooks.

### Changes

1. Remove or deprecate all dispatcher construction paths that do not include:

    * `AgentReleaseRepository`
    * `MasteryRegistry`
    * `VersionContextResolver`
    * `TaskClassifier`
    * `MasteryAwareModeSelector`
    * `MemoryRetriever`
2. For local/dev/test paths, provide explicit `NoopGovernanceProfile` rather than silent missing dependencies.
3. Ensure all AEP runtime bindings use `GovernedAgentDispatcher`.
4. Add ArchUnit rule:

    * product agents cannot directly instantiate low-level dispatchers
    * product agents cannot bypass `GovernedAgentDispatcher`
5. Add trace events for:

    * release digest verified
    * version context resolved
    * mastery decision made
    * mode selected
    * memory retrieved
    * approval checked
    * verification checked
    * learning delta proposed

### Acceptance criteria

* Every runtime path produces a release/mode/mastery trace.
* Dispatch fails closed when required governance dependencies are missing for production.
* No product-specific direct execution bypass remains.

---

## Phase 4 — Implement mastery-aware memory retrieval as a first-class service

### Files to create/update

```text
platform/java/agent-core/src/main/java/com/ghatana/agent/memory/retrieval/MasteryAwareMemoryRetriever.java
platform/java/agent-core/src/main/java/com/ghatana/agent/memory/retrieval/MasteryAwareMemoryQuery.java
platform/java/agent-core/src/main/java/com/ghatana/agent/memory/retrieval/MasteryMemoryRetrievalPolicy.java
platform/java/agent-core/src/main/java/com/ghatana/agent/memory/retrieval/ObsoleteMemoryFilter.java
products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/store/MemoryPlane.java
```

### Retrieval order

For a task, retrieve in this order:

1. Negative knowledge.
2. Active procedure.
3. Version-compatible semantic facts.
4. Known failure modes.
5. Similar successful episodes.
6. Similar failed episodes.
7. Active task-state/checkpoints.

### Retrieval filters

Every retrieval must filter by:

* tenant ID
* skill ID
* agent release ID
* version context
* mastery state
* freshness
* obsolescence
* policy purpose
* data access consent
* risk class

### Acceptance criteria

* Obsolete and retired knowledge never enters working context by default.
* Maintenance-only knowledge only enters for matching legacy context.
* New-project tasks cannot accidentally retrieve maintenance-only patterns.
* Retrieval trace lists memory IDs and why each item was included.

---

## Phase 5 — Replace heuristic learning with typed learning extractors

### Files to update/create

```text
platform/java/agent-core/src/main/java/com/ghatana/agent/framework/learning/LearningEngine.java
platform/java/agent-core/src/main/java/com/ghatana/agent/learning/LearningDelta.java
platform/java/agent-core/src/main/java/com/ghatana/agent/learning/extraction/SemanticFactExtractor.java
platform/java/agent-core/src/main/java/com/ghatana/agent/learning/extraction/ProceduralSkillExtractor.java
platform/java/agent-core/src/main/java/com/ghatana/agent/learning/extraction/NegativeKnowledgeExtractor.java
platform/java/agent-core/src/main/java/com/ghatana/agent/learning/extraction/FailureModeExtractor.java
platform/java/agent-core/src/main/java/com/ghatana/agent/learning/extraction/VersionScopeExtractor.java
```

### Current state

`LearningEngine` can create learning deltas, requires a delta repository for L3+, and blocks unauthorized learning targets through `LearningContract`.

### Changes

1. Replace action/reward n-gram synthesis with typed extractor chain.
2. Extract:

    * semantic facts
    * procedural skills
    * negative knowledge
    * failure modes
    * rollback procedures
    * version scope
    * applicability scope
    * required evaluation pack
3. Require all L2+ deltas to include:

    * evidence refs
    * source episode IDs
    * version context digest
    * environment fingerprint ref
    * repository convention ref
    * runtime fingerprint ref
4. Add confidence vector instead of only before/after scalar:

    * correctness
    * freshness
    * applicability
    * safety
    * evidence strength
5. Never promote inside `LearningEngine`; it only creates deltas.

### Acceptance criteria

* `LearningEngine` never mutates active mastery directly.
* L3+ learning fails if no delta repository exists.
* Each delta is traceable to episodes, environment, version, and rollback path.

---

## Phase 6 — Harden promotion and mastery update

### Files to update

```text
platform/java/agent-core/src/main/java/com/ghatana/agent/promotion/DefaultPromotionEngine.java
platform/java/agent-core/src/main/java/com/ghatana/agent/promotion/DefaultPromotionPolicy.java
platform/java/agent-core/src/main/java/com/ghatana/agent/promotion/PromotionEvidenceMapper.java
platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/MasteryTransition.java
platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/MasteryTransitionResult.java
```

### Current state

`DefaultPromotionEngine` evaluates deltas, checks promotion policy, bootstraps mastery, applies transitions, updates mastery item refs, and marks delta promoted.

### Changes

1. Replace string-based `parseVersionScopeFromJson` with a typed parser/materializer.
2. Do not bootstrap mastery with `VersionScope.empty()` unless the delta explicitly has no version scope and policy allows it.
3. Require promotion evidence bundle for:

    * `COMPETENT`
    * `MASTERED`
    * `MAINTENANCE_ONLY`
    * `OBSOLETE`
    * `QUARANTINED`
4. Add promotion idempotency key:

    * delta ID
    * target mastery ID
    * target state
    * evaluation digest
5. Add transition rollback/refusal behavior if item save fails after transition append.
6. Add reconciliation worker:

    * scans transition log
    * detects item state mismatch
    * replays or repairs transition

### Acceptance criteria

* No `MASTERED` transition without evaluation refs and evidence.
* Promotion is idempotent.
* Transition log and current item state cannot permanently diverge.
* Version scope is typed and validated.

---

## Phase 7 — Make obsolescence operational

### Files to update/create

```text
platform/java/agent-core/src/main/java/com/ghatana/agent/obsolescence/ObsolescenceScanner.java
platform/java/agent-core/src/main/java/com/ghatana/agent/obsolescence/ObsolescenceDetector.java
platform/java/agent-core/src/main/java/com/ghatana/agent/obsolescence/DefaultObsolescenceTransitionService.java
platform/java/agent-core/src/main/java/com/ghatana/agent/obsolescence/ObsolescenceEvent.java
platform/java/agent-core/src/main/java/com/ghatana/agent/maintenance/MaintenanceOnlyPolicy.java
```

### Current state

Obsolescence events already include structured evidence: dependency changes, API changes, security advisories, and documentation sources.

`ObsolescenceScanner` supports scheduled and tenant-scoped scans.

### Changes

1. Add concrete detectors:

    * dependency version changed
    * API contract changed
    * tests failed with old procedure
    * docs changed
    * security advisory found
    * runtime/platform changed
    * repo convention changed
    * repeated failures
2. Route severity:

    * low/medium → verification-first
    * high → maintenance-only or obsolete
    * critical/security → quarantine
3. Add tenant-specific scan schedules.
4. Add triggered scans on:

    * dependency manifest changes
    * release changes
    * failed evaluation
    * security advisory ingestion
5. Add dashboard/API visibility.

### Acceptance criteria

* Stale mastery is demoted or blocked before execution.
* Security-critical obsolescence routes to `QUARANTINED`.
* Maintenance-only transitions require evidence that a newer active version exists.

---

## Phase 8 — Implement maintenance-only and fast-learning policies

### Files to update/create

```text
platform/java/agent-core/src/main/java/com/ghatana/agent/maintenance/MaintenanceOnlyPolicy.java
platform/java/agent-core/src/main/java/com/ghatana/agent/runtime/mode/DefaultModeSelectionPolicy.java
platform/java/agent-core/src/main/java/com/ghatana/agent/runtime/mode/FastLearningPolicy.java
platform/java/agent-core/src/test/java/com/ghatana/agent/runtime/mode/MasteryModeSelectionE2EScenariosTest.java
```

### Maintenance-only rules

* Only usable when version context matches legacy scope.
* Human-gated by default.
* Minimal safe fixes only.
* No architecture expansion.
* No new feature development on old stack unless explicitly requested.
* Always include migration suggestion separately.

### Fast-learning rules

* Used for unknown/new versions.
* Always human-gated or supervised.
* Creates tentative deltas only.
* Requires sandbox experiments.
* Can reach `OBSERVED` or `PRACTICED`, not `MASTERED`, without evals.
* Must store failures as negative knowledge.

### Acceptance criteria

* New projects cannot use maintenance-only patterns.
* Legacy projects cannot accidentally receive new-version-only procedures.
* Unknown tools/libraries default to fast-learning, not confident execution.

---

## Phase 9 — Persist and expose mastery through Data Cloud API/UI

### Files to update/create

```text
products/data-cloud/delivery/api/src/main/java/com/ghatana/datacloud/api/controller/MasteryController.java
products/data-cloud/delivery/ui/src/api/mastery.service.ts
products/data-cloud/delivery/ui/src/pages/MasteryPage.tsx
products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/mastery/DataCloudMasteryRegistry.java
products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/mastery/MasteryItemMapper.java
```

### API features

Add endpoints for:

* query mastery by skill/domain/agent/release/state
* view mastery decision
* view evidence
* view learning deltas
* approve/reject deltas
* trigger evaluation
* promote delta
* mark maintenance-only
* mark obsolete
* quarantine
* retire
* trigger obsolescence scan

### UI features

Data Cloud Mastery page should show:

* skill inventory
* mastery state
* version applicability
* confidence vector
* stale/obsolete warnings
* evidence refs
* evaluation refs
* active procedures
* negative knowledge
* state history
* approval queue
* promotion queue
* obsolescence events

### Acceptance criteria

* Human review workflow exists for low-confidence and high-risk deltas.
* Operators can see why an agent selected a mode.
* Operators can trace mastery state back to evidence.

---

## Phase 10 — Product integration pilot

Use one concrete product/skill as the pilot. Recommended first pilot:

```text
products/yappc/config/agents/definitions/react-expert.yaml
skill: frontend.react-router-routing
```

Why this pilot works:

* It naturally tests active vs maintenance vs obsolete.
* It is version-sensitive.
* It has clear deterministic checks.
* It can produce real procedures, failures, and migration paths.

### Files to update

```text
products/yappc/config/agents/definitions/react-expert.yaml
products/yappc/config/agents/definitions/ui-generation/react-ui-generation-agent.yaml
products/yappc/config/agents/definitions/code-generation/java-code-generation-agent.yaml
products/yappc/scripts/validate_agents.py
platform/agent-catalog/catalog-schema.yaml
platform/agent-catalog/schema-migration.ts
```

### YAML additions

Each migrated agent should include:

```yaml
learningLevel: L3
skillRefs:
  - frontend.react-router-routing

masteryBindings:
  default:
    namespace: yappc.frontend
    registryRef: datacloud.agent-mastery
    freshnessPolicyRef: freshness.react-router
    versionCompatibilityPolicyRef: version.react-router
    obsolescencePolicyRef: obsolescence.frontend-frameworks

masteryPolicyRefs:
  - mastery-policy.react-router-routing

evaluationRefs:
  - eval.react-router-routing.v7
```

### Acceptance criteria

* The React expert can distinguish:

    * React Router v7 active work
    * React Router v6 maintenance work
    * obsolete v3/v4 patterns
* It retrieves the correct procedure for each version context.
* It blocks or warns on incompatible patterns.
* It emits learning deltas after execution.
* Promotion updates mastery only after eval.

---

# 5. Required test plan

The existing mastery CI regression-gates document already defines most of the needed categories: mastery registry, promotion engine, learning engine, governed dispatcher, evidence bundle, obsolescence detection, TypeScript alignment, and integration tests.

Add these missing/expanded tests:

## Contract tests

```text
LearningContractTest
- normal agent cannot propose MASTERY_STATE
- L5 governance workflow can propose MASTERY_STATE
- L2 requires provenance
- L3 requires promotion
- prompt/planner/model/mastery targets require human review by default
```

## Version tests

```text
VersionScopeTest
- active/maintenance/obsolete ranges cannot overlap
- react-router@7 resolves ACTIVE
- react-router@6 resolves MAINTENANCE
- react-router@4 resolves OBSOLETE
- unknown version resolves UNKNOWN and triggers verification/fast-learning
```

## Runtime tests

```text
GovernedAgentDispatcherTest
- blocks non-response-serving release
- blocks mastery-bound release with no skillId and no derivation
- blocks stale mastery
- blocks obsolete mastery
- requires approval for practiced/observed/maintenance-only
- requires verification for competent
- allows mastered active low-risk deterministic execution
```

## Learning tests

```text
LearningEngineTest
- L3 without LearningDeltaRepository fails
- L2 can create semantic/retrieval deltas
- L3 can create procedural and negative-knowledge deltas
- low-confidence delta enters human-review state
- learning target not allowed by contract is rejected
```

## Promotion tests

```text
DefaultPromotionEngineTest
- evaluated delta promotes to OBSERVED/PRACTICED/COMPETENT/MASTERED based on policy
- no MASTERED without evaluation refs
- version scope is preserved
- score dimensions are preserved
- repeated promotion is idempotent
```

## Obsolescence tests

```text
ObsolescenceScannerTest
- dependency change creates obsolescence event
- security advisory creates QUARANTINED transition
- new active version demotes old version to MAINTENANCE_ONLY
- repeated failure demotes to OBSOLETE or verification-required
```

---

# 6. Implementation sequence

## Sprint 1 — Contract hardening

Deliverables:

* Real `VersionScope` overlap validation.
* Strong `VersionRangeEvaluator`.
* Release validation for skill-specific eval/mastery policy refs.
* Tests for `LearningContract`, `LearningLevel`, `AgentDefinition` mastery validation.
* Updated agent catalog schema.

## Sprint 2 — Runtime wiring

Deliverables:

* All AEP dispatch paths use `GovernedAgentDispatcher`.
* `AgentTurnPipeline.withGovernancePreAdmit` is the default for governed agents.
* Missing governance dependencies fail closed in production.
* Version context and mastery decision are always trace-tagged.
* Tests for release/mode/mastery blocking.

## Sprint 3 — Mastery-aware retrieval

Deliverables:

* `MasteryAwareMemoryRetriever`.
* Retrieval policy with version/freshness/mastery-state filters.
* Negative knowledge retrieval before procedures.
* Obsolete/retired filtering everywhere.
* Trace evidence for retrieved memory.

## Sprint 4 — Learning delta pipeline

Deliverables:

* Typed extractors for facts, procedures, negative knowledge, failure modes.
* L3+ learning only through `LearningDeltaRepository`.
* Version/environment/repo/runtime provenance on every delta.
* Human-review deltas for low confidence.

## Sprint 5 — Promotion/evaluation

Deliverables:

* Typed evaluation pack registry.
* Promotion worker.
* Promotion evidence bundle.
* Idempotent promotion.
* Transaction/reconciliation for transition log vs item state.

## Sprint 6 — Obsolescence and maintenance-only

Deliverables:

* Dependency/API/security/runtime/convention detectors.
* Tenant-scoped scheduled scans.
* Event-triggered scans.
* Strict maintenance-only policy.
* Fast-learning policy for unknown/new versions.

## Sprint 7 — Product pilot

Deliverables:

* YAPPC React expert migrated.
* Seeded `react-router-routing` mastery items.
* v7 active / v6 maintenance / v3-v4 obsolete scenario tests.
* Data Cloud UI/API displays mastery decisions and learning deltas.

## Sprint 8 — CI and rollout

Deliverables:

* Mastery CI regression gates implemented.
* Coverage thresholds enforced.
* Performance benchmarks enforced.
* Shadow/canary rollout path.
* Production monitoring for mastery transitions, promotion queue, obsolescence scans, and dispatcher overhead.

---

# 7. Definition of done

The goal is achieved when this scenario works end-to-end:

```text
User asks agent to modify routing in a repo.
→ Runtime fingerprints repo and package versions.
→ Agent resolves skill: frontend.react-router-routing.
→ MasteryRegistry returns correct version-scoped mastery.
→ Mode selector chooses deterministic / supervised / maintenance / fast-learning / blocked.
→ Memory retriever injects only compatible facts, procedures, failures, and negative knowledge.
→ Agent acts through governed lifecycle.
→ Verification runs.
→ Episode is captured.
→ LearningEngine emits LearningDelta with provenance.
→ EvaluationHarness evaluates the delta.
→ PromotionEngine updates MasteryRegistry if policy allows.
→ ObsolescenceScanner later demotes/quarantines stale or unsafe knowledge.
→ UI/API can explain every decision with evidence.
```

That gives you the intended principle:

> The agent does not merely know how to do a task. It knows which version of the knowledge applies, how strong the evidence is, whether the skill is active or maintenance-only, when it became stale, and whether it is safe to execute without human approval.

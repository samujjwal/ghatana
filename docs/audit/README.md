Scope note
I scanned the repo at commit cc8f279c3eb11d15b6e6817499e621dd8b7cb3a9. That commit itself is only a YAPPC changelog update, so the useful analysis is against the tree snapshot at that commit, not the one-line diff.
My conclusion: the repo already contains most of the foundation for a Generic Adaptive Agent, but it does not yet have the missing “mastery system” that turns raw learning into version-aware, evaluated, promotable, stale-aware competence.
1. Current code architecture: what already exists
   1.1 Canonical GAA substrate exists
   ADR-021 is the strongest design anchor. It defines the canonical substrate as:
   AgentSpec / AgentDefinition
   -> AgentRelease
   -> GovernedAgentDispatcher
   -> GaaAgentExecutor
   -> AgentTurnPipeline
   -> type-specific strategy
   -> AgentResult + trace
   -> Memory / Learning / Evaluation / Promotion

It also defines the full operational lifecycle:
ADMIT -> PERCEIVE -> REASON -> VERIFY -> ACT -> CAPTURE -> REFLECT -> COMPLETE

Most importantly for your self-learning goal, ADR-021 states that learned artifacts require declared learning targets, provenance, evaluation evidence, promotion, and rollback references before activation.
That line is the seed of your whole goal.
1.2 AgentDefinition is a strong immutable blueprint
AgentDefinition is already a good canonical “agent spec” object. It is immutable, versioned, and contains identity, namespace, status, type, subtype, determinism, state mutability, failure mode, roles, personas, criticality, autonomy level, learning level, system prompt, I/O contracts, tools, capabilities, timeout, cost limits, memory bindings, policy refs, evaluation refs, observability contract, and security contract. It also computes a canonical SHA-256 digest and can produce a release draft.
This is exactly where the GAA bootstrap contract should begin.
1.3 AgentRelease and release lifecycle are already mature
AgentRelease ties an agent spec to policy pack, evaluation pack, memory contract, runtime compatibility, signing reference, tool/telemetry/explanation contracts, redaction profile, threat model, data classes, permitted purposes, and capability maturity profile. It also validates redaction profile, threat model, permitted purposes, and maturity profile.
AgentReleaseState already gives a release state machine:
DRAFT → VALIDATED → SHADOW → CANARY → ACTIVE → DEPRECATED → RETIRED
↓             ↓        ↓        ↓
BLOCKED       BLOCKED  BLOCKED  BLOCKED

It also distinguishes internal runnable releases from response-serving releases: SHADOW, CANARY, and ACTIVE are runnable; only CANARY and ACTIVE may serve responses.
That is the right lifecycle for safe learning rollout.
1.4 Runtime dispatch is governance-aware
GovernedAgentDispatcher wraps an AgentDispatcher with release guard, grant/invariant validation, trace ledger recording, OpenTelemetry tracing, policy evaluation, and denial results. It rejects non-response-serving releases on normal traffic, allows shadow/evaluation mode only for runnable releases, records TURN_STARTED, POLICY_EVALUATED, ACTION_DENIED, ACTION_EXECUTED, and TURN_COMPLETED, and enriches results with release metadata.
This is already close to what you need for “no unsafe learned behavior can become active without governance.”
1.5 GaaAgentExecutor correctly wraps typed agents
GaaAgentExecutor wraps every TypedAgent.process() call in AgentTurnPipeline, and when an AgentDefinition is present it injects the spec digest into context and result.
This gives a clean target for adding mode selection, mastery-aware context, and learning-delta emission.
1.6 AgentTurnPipeline already has the right lifecycle hooks
AgentTurnPipeline executes:
ADMIT → PERCEIVE → REASON → VERIFY → ACT → CAPTURE → REFLECT → COMPLETE

It records phase timing, trace tags, metrics, phase refs, release ID, spec digest, trace ID, turn ID, and can execute with resilience policies. REFLECT is non-blocking, which is right for learning.
This is the correct place to add:
mastery classification during ADMIT or PERCEIVE
verification policy during VERIFY
episode/evidence capture during CAPTURE
learning-delta proposal during REFLECT
1.7 AgentResult already carries the evidence envelope
AgentResult includes output, confidence, status, explanation, metrics, processing time, trace ID, turn ID, agent version, agent release ID, spec digest, policy decision refs, evaluation refs, memory refs, tool call refs, phase trace refs, warnings, rollback ref, evidence, and diagnostics.
This is enough to support trace grading, promotion evidence, rollback references, and mastery updates.
1.8 Context supports memory, budget, tracing, and child contexts
AgentContext carries turn ID, agent ID, tenant ID, user ID, session ID, start time, memory store, config, logger, metrics, trace tags, budget, metadata, and a default getMemoryPlane() hook.
The current getMemoryPlane() default returns Object, which is practical for avoiding dependency cycles, but the implementation plan should introduce a safer typed adapter/accessor.
1.9 Memory plane is multi-tier
MemoryPlane is a strong SPI. It supports:
episodic memory
semantic facts
procedural memory
typed artifacts
generic store/query
semantic search
working memory
task-state store
checkpointing
stats
It explicitly describes itself as the evolution of MemoryStore, spanning episodic, semantic, procedural, task-state, working, preference, and typed artifacts.
This is the right base for your memory hierarchy.
1.10 MemoryQuery exists but is not yet mastery/version-aware
MemoryQuery supports item types, tenant, agent, sphere, time range, tags, minimum confidence, validity status, full-text query, limit, and offset.
It lacks explicit fields for:
skill ID
version context
mastery state
lifecycle state
package/tool/runtime compatibility
freshness requirement
maintenance-only vs active retrieval
negative-knowledge-first retrieval
Those should be added through a richer query policy object rather than overloading MemoryQuery too much.
1.11 Memory-aware agents exist, but retrieval is too loose
MemoryAwareBaseAgent extends BaseAgent and enhances PERCEIVE, CAPTURE, and REFLECT. It retrieves semantically relevant memory, injects context, stores EnhancedEpisode, and suggests consolidation after every 50 episodes.
Critical issue: retrieval in perceive() is currently fire-and-forget asynchronous. The comment says context is populated by the time reason() reads it, but that is not guaranteed. For mastery-aware execution, retrieval must either be awaited or moved into a pipeline phase that can return an enriched input/context before reasoning.
1.12 Procedural memory has useful fields
EnhancedProcedure already includes:
procedure ID
validity
provenance
labels
confidence
tags
situation
action
use count
learned-from episodes
version
steps
success rate
prerequisites
environment constraints
version history
This is very close to the structure needed for skills.
But it is not enough to represent mastery by itself. A procedure is “how to do something.” Mastery is “how reliable, current, version-compatible, and safe that procedure is.”
1.13 Episodic-to-procedural consolidation exists
EpisodicToProceduralConsolidator processes episodes after a timestamp, requires at least three episodes, invokes a ProcedureInducer, and stores/merges induced procedures.
ProcedureInducer is an interface for inducing procedures from clusters of similar successful episodes.
This is a good early consolidation pipeline, but it currently promotes procedures too directly. For your goal, procedure induction should create a LearningDelta, not immediately create active procedural memory.
1.14 Procedural memory management is present but too simplistic
ProceduralMemoryManager can store or merge procedures, select a procedure for a situation, record success by increasing confidence by 0.03, and record failure by reducing confidence by 0.1.
That is useful but too shallow for mastery. Confidence updates should consider:
task risk
verification strength
version compatibility
recency
environment match
regression outcome
failure severity
sample size
source trust
negative knowledge
1.15 Memory governance is already partially implemented
GovernedMemoryPlane wraps memory reads with DataAccessBroker.checkAccess and gates semantic, procedural, and learned typed artifact writes.
MemoryWritePolicy is stricter: semantic fact writes require validation; negative knowledge requires evidence; procedure writes require promotionState=ACTIVE and promotionEvidenceId; retrieval/routing/confidence policy artifacts require approval; prompt/planner artifacts require evaluation and rollout refs; model adapters cannot self-activate.
This is a major strength. It already encodes the principle that learned artifacts cannot simply self-activate.
1.16 Learning authority exists
LearningLevel defines which learning targets each level allows. L1 allows episodic memory, L2 adds semantic facts, retrieval policy, confidence threshold, and routing policy, L3 adds procedural skill and negative knowledge, L4 adds prompt template and planner policy, and L5 allows all targets but is offline-only.
LearningContract combines level, allowed targets, provenance requirement, and promotion requirement, and permits a target only if it is explicitly allowed and allowed by the level.
LearningTarget has the right targets: episodic memory, semantic fact, procedural skill, negative knowledge, retrieval policy, confidence threshold, routing policy, prompt template, planner policy, and model adapter.
1.17 But learning semantics are inconsistent
LearningEngine has its own inner LearningLevel enum with different descriptions: L1 parameter feedback, L2 bandit/online learning, L3 pattern synthesis, L4 structural learning, and L5 parameter updates or prompt optimization. It also stores policies directly based on confidence threshold.
This conflicts with the canonical LearningLevel and LearningTarget model. This must be fixed before building self-learning.
1.18 Type-specific validation exists
AgentSpecValidator dispatches validation by canonical agent type: deterministic, probabilistic, hybrid, adaptive, composite, planning, stream processor, reactive, and custom.
AdaptiveAgentValidator already requires adaptive agents to have learning level L2 or higher, plus adaptationTargets and driftControls.
This is good and should be extended for mastery requirements.
1.19 Agent spec documentation already describes many target concepts
agent-spec.md already documents canonical agent types, reasoner portfolios, confidence model, memory bindings, read strategies, write policies, consolidation rules, retention policy, learning level, adaptation targets, drift controls, rollback policy, evaluation refs, online/offline metrics, release gates, observability, interoperability, and security.
This means the implementation plan can align code to documentation rather than inventing a new model from scratch.
1.20 Drift detection exists as a product task agent, not a platform service
The YAPPC drift-detection-task-agent.yaml defines a probabilistic task agent for detecting infrastructure drift, with L2 learning, episodic/semantic memory, drift tools, and audit trail.
That is useful, but your goal needs a platform-level obsolescence and mastery drift service, not just a product task agent.
2. Main gaps to close
   Gap 1: No Mastery Registry
   There is no first-class registry tracking:
   what the agent knows
   how well it knows it
   applicable versions
   active vs maintenance-only vs obsolete vs retired
   last verification
   eval evidence
   failure modes
   safe execution mode
   teaching/composition eligibility
   Procedural memory has some of this, but not enough.
   Gap 2: Learning updates are not staged as deltas
   The target design should be:
   episode → lesson candidate → LearningDelta → evaluation → promotion → active memory/mastery

Current code has places where procedures/policies can be induced or stored too directly. MemoryWritePolicy already blocks some of this, but the architecture needs an explicit LearningDelta lifecycle.
Gap 3: Version/context compatibility is not first-class
EnhancedProcedure has environmentConstraints, but retrieval and selection do not enforce version compatibility. MemoryQuery lacks version context, package context, runtime context, and mastery state filtering.
Gap 4: Learning level semantics conflict
LearningLevel.java and LearningEngine.java use different meanings. That will create governance bugs.
Gap 5: Evaluation pack is referenced but not implemented enough
AgentDefinition and AgentRelease reference evaluation refs/packs, but there is no complete evaluation-pack execution and result model in the scanned code.
Gap 6: Mastery transitions are not enforced
The lifecycle states you proposed—Unknown, Observed, Practiced, Competent, Mastered, Maintenance-only, Obsolete, Retired—do not yet exist in code.
Gap 7: Obsolescence detection is not platform-native
There is a drift detection task agent, but no platform service that demotes mastery when tool/library/runtime/API/security guidance changes.
Gap 8: Memory-aware retrieval is async in a way that can race
MemoryAwareBaseAgent.perceive() fires semantic search asynchronously and immediately returns. For mastery-sensitive decisions, the agent must not reason before retrieval and compatibility checks complete.
Gap 9: Procedure confidence is too simple
ProceduralMemoryManager.recordSuccess() and recordFailure() apply fixed increments/decrements. Mastery confidence should be multi-dimensional and evidence-weighted.
Gap 10: Promotion, rollback, and quarantine need one coherent flow
AgentReleaseState, MemoryWritePolicy, and ADR-021 all imply this, but there is no single service that coordinates learning promotion across memory, mastery, evaluation, release, and trace evidence.
3. Target architecture
   The goal architecture should look like this:
   AgentDefinition
   ↓
   AgentRelease
   ↓
   GovernedAgentDispatcher
   ↓
   GaaAgentExecutor
   ↓
   AgentTurnPipeline
   ├─ ADMIT: release + policy + mastery checks
   ├─ PERCEIVE: task classification + version/environment fingerprint
   ├─ REASON: mode-aware execution
   ├─ VERIFY: tests, schemas, confidence, policy, contradiction checks
   ├─ ACT: governed tool/action execution
   ├─ CAPTURE: episode + evidence + task-state + memory refs
   ├─ REFLECT: learning delta proposals only
   └─ COMPLETE: result envelope + trace refs

LearningDeltaPipeline
↓
EvaluationHarness
↓
PromotionEngine
↓
MasteryRegistry
↓
MemoryPlane / AgentRelease / policy artifacts

Core principle:
Agents may observe freely,
propose cautiously,
and act only within governed mastery.

4. Implementation plan
   Phase 0 — Stabilize the current foundation
   Objective
   Remove semantic inconsistencies before adding new mastery concepts.
   Work items
   Make com.ghatana.agent.learning.LearningLevel the only canonical learning-level enum.
   Remove or rename the inner LearningEngine.LearningLevel.
   Update LearningEngine to consume:
   LearningContract
   LearningTarget
   canonical LearningLevel
   Ensure AgentDefinition.learningLevel is converted into a typed LearningContract at materialization time.
   Add validation that AgentDefinition.learningLevel, metadata learningLevel, and LearningContract.level cannot disagree.
   Update AdaptiveAgentValidator to validate:
   learningLevel >= L2
   adaptationTargets match LearningTarget
   driftControls.enabled
   promotionRequired=true for L3+
   provenanceRequired=true for L2+
   Code areas
   platform/java/agent-core/src/main/java/com/ghatana/agent/learning/LearningLevel.java
   platform/java/agent-core/src/main/java/com/ghatana/agent/learning/LearningContract.java
   platform/java/agent-core/src/main/java/com/ghatana/agent/framework/learning/LearningEngine.java
   platform/java/agent-core/src/main/java/com/ghatana/agent/framework/config/AgentDefinition.java
   platform/java/agent-core/src/main/java/com/ghatana/agent/validation/AdaptiveAgentValidator.java
   Acceptance criteria
   No duplicate/conflicting learning-level enum remains.
   An adaptive agent without adaptationTargets, driftControls, provenance, or promotion requirements fails validation.
   L5 is enforced as offline-only.
   Tests prove that an L2 agent cannot write procedural skills and an L3 agent can only propose them, not self-activate them.
   Phase 1 — Introduce Mastery Registry
   Objective
   Create the missing system that tracks skill maturity, version applicability, freshness, and lifecycle state.
   New package
   platform/java/agent-core/src/main/java/com/ghatana/agent/mastery

New core types
public enum MasteryState {
UNKNOWN,
OBSERVED,
PRACTICED,
COMPETENT,
MASTERED,
MAINTENANCE_ONLY,
OBSOLETE,
RETIRED,
QUARANTINED
}

public record VersionScope(
List<VersionConstraint> active,
List<VersionConstraint> maintenance,
List<VersionConstraint> obsolete
) {}

public record MasteryScore(
double correctness,
double freshness,
double applicability,
double safety,
double transferability,
double evidenceStrength,
double regressionStability
) {
public double executionScore() {
return correctness
* freshness
* applicability
* safety
* regressionStability;
}
}

public record MasteryItem(
String masteryId,
String skillId,
String domain,
String agentId,
String agentReleaseId,
MasteryState state,
VersionScope versionScope,
ApplicabilityScope applicability,
MasteryScore score,
List<String> procedureIds,
List<String> semanticFactIds,
List<String> negativeKnowledgeIds,
List<String> evidenceRefs,
List<String> evaluationRefs,
List<String> knownFailureModeIds,
Instant lastVerifiedAt,
Instant staleAfter,
Map<String, String> labels
) {}

public interface MasteryRegistry {
Promise<Optional<MasteryItem>> findBySkill(
String skillId,
EnvironmentFingerprint env
);

    Promise<List<MasteryItem>> query(MasteryQuery query);

    Promise<MasteryItem> save(MasteryItem item);

    Promise<MasteryTransitionResult> transition(
        MasteryTransition transition
    );

    Promise<List<MasteryItem>> findStale(Instant now);
}

Persistence
Add Data Cloud-backed repository:
products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/mastery

Collections:
agent-mastery-items
agent-mastery-transitions
agent-mastery-evidence

Acceptance criteria
Mastery is queryable by skill, agent, version context, tenant, state, domain, and freshness.
Mastery transitions are append-only.
Direct transition to MASTERED without evaluation evidence is rejected.
OBSOLETE and RETIRED items are excluded from active retrieval by default.
MAINTENANCE_ONLY items are retrievable only when environment context matches legacy scope.
Phase 2 — Add version/environment fingerprinting
Objective
Make version-aware mastery possible.
New package
platform/java/agent-core/src/main/java/com/ghatana/agent/environment

New types
public record EnvironmentFingerprint(
String tenantId,
String repoId,
String projectType,
Map<String, String> dependencies,
Map<String, String> tools,
Map<String, String> runtimes,
Map<String, String> frameworks,
Map<String, String> conventions,
Instant observedAt,
List<String> evidenceRefs
) {}

public interface EnvironmentFingerprintProvider {
Promise<EnvironmentFingerprint> fingerprint(AgentContext ctx, Object input);
}

For code agents, fingerprint sources should include:
package.json
pom.xml
Gradle files
lockfiles
tool versions
runtime versions
repo conventions
detected framework mode
CI/test configuration
Pipeline integration
Add EnvironmentFingerprint to AgentContext.metadata during ADMIT or PERCEIVE.
Acceptance criteria
React Router v6 and v7 contexts produce distinct fingerprints.
A task with no version info is classified as verification-first.
Procedures with incompatible version scopes are not selected.
Phase 3 — Add task classification and mode selection
Objective
Convert your lifecycle’s classification/mode selection into runtime logic.
New package
platform/java/agent-core/src/main/java/com/ghatana/agent/mode

New enums
public enum TaskClass {
KNOWN_TASK,
KNOWN_VARIATION,
UNKNOWN_TASK,
HIGH_RISK_TASK,
MAINTENANCE_TASK,
EXPLORATION_TASK,
MIGRATION_TASK
}

public enum ExecutionMode {
DETERMINISTIC,
BOUNDED_PROBABILISTIC,
FAST_LEARNING,
MAINTENANCE_ONLY,
HUMAN_GATED,
VERIFICATION_FIRST,
BLOCKED
}

New service
public interface AgentModeSelector {
Promise<ModeDecision> decide(
AgentDefinition definition,
AgentRelease release,
AgentContext context,
EnvironmentFingerprint env,
Optional<MasteryItem> mastery,
Object input
);
}

Decision rules
ConditionMode
Mastered + fresh + version match + low risk
DETERMINISTIC
Competent + version match + medium uncertainty
BOUNDED_PROBABILISTIC
Unknown version/tool/library
FAST_LEARNING
Legacy version + no migration requested
MAINTENANCE_ONLY
Irreversible side effect / high-risk action
HUMAN_GATED
Contradiction or stale knowledge
VERIFICATION_FIRST
Obsolete/unsafe skill
BLOCKED
Acceptance criteria
Mode decision is stored in AgentResult.diagnostics.
Mode decision is appended to trace ledger.
MAINTENANCE_ONLY mode warns and restricts scope.
FAST_LEARNING cannot promote directly to active memory.
Phase 4 — Fix memory retrieval into mastery-aware retrieval
Objective
Replace loose semantic retrieval with version-compatible, lifecycle-aware, negative-knowledge-aware retrieval.
New package
products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/retrieval/mastery

New types
public record MasteryAwareMemoryQuery(
String task,
String skillId,
EnvironmentFingerprint environment,
List<MasteryState> allowedMasteryStates,
boolean includeNegativeKnowledge,
boolean excludeObsolete,
boolean requireFreshness,
int maxAgeDays,
int k,
double minConfidence
) {}

public interface MasteryAwareRetriever {
Promise<RetrievedContext> retrieve(MasteryAwareMemoryQuery query);
}

Retrieval order
Negative knowledge.
Version-compatible procedures.
Known failure modes.
Semantic facts.
Similar successful episodes.
Similar failed episodes.
Active task state/checkpoints.
Modify
MemoryAwareBaseAgent
MemoryPlane
MemoryQuery
StructuredContextInjector
Important fix
Change MemoryAwareBaseAgent.perceive() from fire-and-forget retrieval to one of these safer options:
Add async perceiveAsync() to pipeline.
Move retrieval into ADMIT as a pre-reasoning stage.
Add a new ContextHydrationPhase before REASON.
Do not rely on async callback timing.
Acceptance criteria
Retrieved context contains only version-compatible procedures unless explicitly in migration mode.
Negative knowledge is injected before recommended procedure.
Obsolete procedures are excluded.
Maintenance-only procedures are included only for matching legacy context.
Retrieval refs are added to AgentResult.memoryRefs.
Phase 5 — Introduce LearningDelta
Objective
Prevent direct mutation of active knowledge. Reflection should propose deltas, not activate them.
New package
platform/java/agent-core/src/main/java/com/ghatana/agent/learning/delta

New types
public enum LearningDeltaState {
PROPOSED,
VALIDATING,
EVALUATED,
APPROVED,
PROMOTED,
REJECTED,
QUARANTINED,
ROLLED_BACK
}

public enum LearningChangeType {
CREATE,
UPDATE,
DEPRECATE,
RETIRE,
QUARANTINE,
MERGE,
SPLIT
}

public record LearningDelta(
String deltaId,
String agentId,
String agentReleaseId,
LearningTarget target,
LearningChangeType changeType,
LearningDeltaState state,
String targetId,
String proposedArtifactRef,
List<String> sourceEpisodeIds,
List<String> evidenceRefs,
List<String> evaluationRefs,
String rollbackRef,
double confidenceBefore,
double confidenceAfter,
boolean requiresHumanReview,
Instant createdAt,
Map<String, String> labels
) {}

New service
public interface LearningDeltaRepository {
Promise<LearningDelta> save(LearningDelta delta);
Promise<Optional<LearningDelta>> findById(String deltaId);
Promise<List<LearningDelta>> findPending(String agentId);
Promise<LearningDelta> transition(String deltaId, LearningDeltaState state);
}

Modify
LearningEngine
EpisodicToProceduralConsolidator
ProcedureInducer
ProceduralMemoryManager
New behavior
Current:
episodes → procedure → storeProcedure()

Target:
episodes → candidate procedure → LearningDelta(PROCEDURAL_SKILL) → eval → promotion → storeProcedure()

Acceptance criteria
REFLECT can create deltas but not active procedures.
LearningContract.permits(target) is checked before delta creation.
MemoryWritePolicy.validateProcedure() passes only after promotion.
Deltas include provenance, source episodes, and rollback refs.
Phase 6 — Build evaluation harness and evaluation packs
Objective
Make “mastered” evidence-based.
New package
platform/java/agent-core/src/main/java/com/ghatana/agent/evaluation

New types
public record EvaluationPack(
String evaluationPackId,
String version,
String skillId,
VersionScope versionScope,
List<EvaluationCase> cases,
List<MetricThreshold> thresholds,
List<String> safetyChecks,
List<String> regressionRefs
) {}

public record EvaluationResult(
String evaluationResultId,
String evaluationPackId,
String agentId,
String agentReleaseId,
String learningDeltaId,
String skillId,
String status,
Map<String, Double> metrics,
List<String> failedCases,
List<String> traceRefs,
Instant evaluatedAt
) {}

public interface EvaluationHarness {
Promise<EvaluationResult> evaluate(
LearningDelta delta,
EvaluationPack pack,
AgentContext context
);
}

Evaluation categories
Skill unit tests.
Regression tests.
Safety tests.
Version compatibility tests.
Freshness tests.
Adversarial/prompt-injection tests.
Recovery/rollback tests.
Abstention tests.
Trace-grade tests.
Integration
AgentRelease.evaluationPackId should resolve to executable evaluation packs.
AgentResult.evaluationRefs should include evaluation result IDs.
MasteryItem.evaluationRefs should include the latest passing results.
Acceptance criteria
No LearningDelta can become PROMOTED without an EvaluationResult.
MASTERED requires passing regression and safety checks.
Evaluation results are persisted and trace-linked.
A failed evaluation demotes or quarantines proposed learning.
Phase 7 — Build promotion engine
Objective
Coordinate promotion across memory, mastery, release, and rollback.
New package
platform/java/agent-core/src/main/java/com/ghatana/agent/promotion

New service
public interface PromotionEngine {
Promise<PromotionResult> promote(
LearningDelta delta,
EvaluationResult evaluation,
PromotionPolicy policy
);

    Promise<RollbackResult> rollback(String promotionId);
}

Promotion rules
Learning targetPromotion requirement
EPISODIC_MEMORY
append-only, policy/redaction check
SEMANTIC_FACT
validated source/evidence
NEGATIVE_KNOWLEDGE
evidence ref required
PROCEDURAL_SKILL
eval pass + promotion evidence
RETRIEVAL_POLICY
approval or shadow/canary
CONFIDENCE_THRESHOLD
bounded adaptation + eval
ROUTING_POLICY
eval + approval
PROMPT_TEMPLATE
eval + rollout ref
PLANNER_POLICY
eval + rollout ref + approval
MODEL_ADAPTER
offline only, never self-activate
This aligns with MemoryWritePolicy, which already encodes many of these constraints.
Acceptance criteria
Promotion creates promotionEvidenceId.
Promoted procedures are written with labels:
promotionState=ACTIVE
promotionEvidenceId=...
evaluationRef=...
learningTarget=PROCEDURAL_SKILL
Rollback ref is stored in AgentResult.rollbackRef.
Failed promotion leaves delta in QUARANTINED.
Phase 8 — Implement mastery lifecycle transitions
Objective
Turn your lifecycle states into code and policy.
Transition table
FromToRequired evidence
UNKNOWN
OBSERVED
one trace or verified source
OBSERVED
PRACTICED
repeated episodes or sandbox experiments
PRACTICED
COMPETENT
procedure exists and basic eval passes
COMPETENT
MASTERED
regression, safety, recovery, and compatibility tests pass
MASTERED
MAINTENANCE_ONLY
new active version exists; old version still used
Any active
OBSOLETE
docs/API/security/runtime contradiction or repeated failures
Any active
QUARANTINED
unsafe behavior or failed safety eval
OBSOLETE
RETIRED
no active retrieval/use case remains
New service
public interface MasteryTransitionPolicy {
boolean canTransition(MasteryItem item, MasteryState target, EvidenceBundle evidence);
}

Acceptance criteria
Direct UNKNOWN → MASTERED is impossible.
MASTERED → OBSOLETE can happen automatically on high-severity safety events.
MAINTENANCE_ONLY can still be retrieved for matching legacy context.
RETIRED is archive/audit only.
Phase 9 — Add obsolescence detection
Objective
Detect stale knowledge before the agent applies it.
New package
platform/java/agent-core/src/main/java/com/ghatana/agent/obsolescence

New types
public enum ObsolescenceReason {
TOOL_VERSION_CHANGED,
LIBRARY_VERSION_CHANGED,
API_CONTRACT_CHANGED,
TESTS_FAILED,
DOCS_CONTRADICT_MEMORY,
REPO_CONVENTION_CHANGED,
SECURITY_GUIDANCE_CHANGED,
RUNTIME_CHANGED,
BETTER_PROCEDURE_AVAILABLE,
REPEATED_FAILURES
}

public record ObsolescenceEvent(
String eventId,
String targetId,
String targetType,
ObsolescenceReason reason,
String recommendedTransition,
List<String> evidenceRefs,
boolean requiresHumanReview,
Instant detectedAt
) {}

public interface ObsolescenceDetector {
Promise<List<ObsolescenceEvent>> detect(EnvironmentFingerprint env);
}

Sources
dependency manifests
lockfiles
API schemas
official docs snapshots
security advisories
test failures
runtime/tool version changes
repeated failure traces
user/repo convention changes
Acceptance criteria
Library major-version change triggers verification-first mode.
Security advisory can quarantine mastery.
Repeated failures reduce mastery score and can demote from MASTERED to COMPETENT or QUARANTINED.
Obsolescence events are traceable and auditable.
Phase 10 — Add maintenance-only and fast-learning policies
Objective
Prevent old knowledge from leaking into new work and prevent new uncertain knowledge from corrupting stable work.
Maintenance-only behavior
Rules:
Retrieve only when environment matches maintenance version scope.
Warn when old approach is not recommended for new work.
Prefer minimal, safe fixes.
Avoid architecture expansion.
Suggest migration path separately.
Do not promote new best practices into old stack unless compatibility-safe.
Fast-learning behavior
Rules:
Inspect current docs/environment.
Run sandbox experiments.
Create tentative facts/procedures.
Store failures explicitly.
Mark mastery OBSERVED or PRACTICED, never MASTERED.
Require repeated validation before promotion.
Require human review for production use.
Acceptance criteria
Greenfield tasks do not use maintenance-only procedures.
Legacy tasks do not force active new-version procedures.
New-version learning remains tentative until eval/promoted.
Fast-learning output includes uncertainty and verification requirements.
Phase 11 — Strengthen confidence scoring
Objective
Replace scalar confidence updates with evidence-weighted mastery scoring.
New scoring model
public interface MasteryScorer {
MasteryScore score(MasteryItem item, EvidenceBundle evidence);
}

Score dimensions:
correctness
freshness
applicability
safety
transferability
evidence strength
regression stability
Replace
Current recordSuccess():
confidence + 0.03

Current recordFailure():
confidence - 0.1

With:
score = f(
outcome,
risk,
test strength,
version match,
source trust,
recency,
failure severity,
sample size,
regression result
)

Acceptance criteria
One easy success cannot dramatically increase mastery.
One severe safety failure can demote or quarantine mastery.
Confidence is version-scoped.
Failed maintenance-task evidence does not automatically degrade active-version mastery unless related.
Phase 12 — Add trace grading and evidence bundles
Objective
Make learning explainable and auditable.
New type
public record EvidenceBundle(
String evidenceBundleId,
List<String> traceRefs,
List<String> episodeRefs,
List<String> toolCallRefs,
List<String> memoryRefs,
List<String> evaluationRefs,
List<String> sourceRefs,
List<String> rollbackRefs,
Map<String, Object> metrics
) {}

Required trace additions
Add to AgentResult.evidence and trace ledger:
task classification
execution mode
environment fingerprint
mastery item ID
retrieved memory IDs
negative knowledge IDs
selected procedure ID
verification results
learning deltas created
promotion decisions
obsolescence events
rollback refs
Acceptance criteria
Every promoted skill can be traced back to episodes, evaluations, and policy decisions.
Every mastery transition has evidence.
Every denial/quarantine has a reason and trace.
Phase 13 — Add API and UI surfaces
Objective
Make mastery visible and governable.
Backend APIs
GET /agents/{agentId}/mastery
GET /agents/{agentId}/mastery/{skillId}
POST /agents/{agentId}/mastery/{skillId}/transition
GET /agents/{agentId}/learning-deltas
POST /learning-deltas/{deltaId}/approve
POST /learning-deltas/{deltaId}/reject
GET /agents/{agentId}/obsolescence-events
GET /agents/{agentId}/evaluation-results

UI views
Agent mastery matrix.
Skill detail page.
Version compatibility table.
Learning delta review queue.
Obsolescence warnings.
Promotion timeline.
Trace/evidence viewer.
Maintenance-only inventory.
Fast-learning sandbox results.
Acceptance criteria
Operators can see what an agent has mastered.
Operators can see stale/obsolete knowledge.
Operators can approve/reject learning deltas.
Operators can inspect why a skill was promoted or demoted.
5. Concrete class/file plan
   Add
   platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/
   MasteryState.java
   MasteryItem.java
   MasteryScore.java
   VersionScope.java
   VersionConstraint.java
   ApplicabilityScope.java
   MasteryRegistry.java
   MasteryQuery.java
   MasteryTransition.java
   MasteryTransitionPolicy.java
   MasteryTransitionResult.java
   MasteryScorer.java

platform/java/agent-core/src/main/java/com/ghatana/agent/environment/
EnvironmentFingerprint.java
EnvironmentFingerprintProvider.java
VersionDetector.java
DependencyFingerprintProvider.java

platform/java/agent-core/src/main/java/com/ghatana/agent/mode/
TaskClass.java
ExecutionMode.java
ModeDecision.java
TaskClassifier.java
AgentModeSelector.java
DefaultAgentModeSelector.java

platform/java/agent-core/src/main/java/com/ghatana/agent/learning/delta/
LearningDelta.java
LearningDeltaState.java
LearningChangeType.java
LearningDeltaRepository.java
LearningDeltaService.java

platform/java/agent-core/src/main/java/com/ghatana/agent/evaluation/
EvaluationPack.java
EvaluationCase.java
EvaluationResult.java
EvaluationHarness.java
EvaluationRepository.java

platform/java/agent-core/src/main/java/com/ghatana/agent/promotion/
PromotionEngine.java
PromotionPolicy.java
PromotionResult.java
RollbackResult.java

platform/java/agent-core/src/main/java/com/ghatana/agent/obsolescence/
ObsolescenceDetector.java
ObsolescenceEvent.java
ObsolescenceReason.java
ObsolescencePolicy.java

Add Data Cloud implementations
products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/mastery/
DataCloudMasteryRegistry.java
DataCloudLearningDeltaRepository.java
DataCloudEvaluationRepository.java
DataCloudObsolescenceEventRepository.java

Modify
platform/java/agent-core/src/main/java/com/ghatana/agent/framework/learning/LearningEngine.java

Convert from direct policy persistence to learning-delta proposal.
products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/learning/consolidation/EpisodicToProceduralConsolidator.java

Emit LearningDelta(PROCEDURAL_SKILL) instead of direct active procedure storage.
products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/store/procedural/ProceduralMemoryManager.java

Make confidence updates delegate to MasteryScorer.
products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/bridge/MemoryAwareBaseAgent.java

Remove unsafe fire-and-forget retrieval before reasoning.
platform/java/agent-core/src/main/java/com/ghatana/agent/framework/runtime/AgentTurnPipeline.java

Add hooks for context hydration, mode decision, verification policy, learning delta refs, and mastery refs.
products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcher.java

Add mode/mastery decision to trace ledger and denial reasons.
platform/java/agent-core/src/main/java/com/ghatana/agent/framework/config/AgentDefinitionValidator.java

Validate mastery/evaluation/promotion requirements for adaptive agents.
platform/java/agent-core/src/main/java/com/ghatana/agent/validation/AdaptiveAgentValidator.java

Require proper mastery and promotion metadata.
products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/governance/MemoryWritePolicy.java

Add mastery-aware validations:
masteryItemId
versionScope
promotionEvidenceId
evaluationRef
rollbackRef
6. Suggested milestones
   Milestone 1: Canonical learning governance
   Duration target: 1–2 implementation cycles.
   Deliverables:
   canonical learning levels
   no duplicate learning enum
   stricter adaptive validation
   tests for learning-level permissions
   LearningContract wired into runtime
   Success signal:
   An agent cannot write or promote any learning target outside its declared learning contract.

Milestone 2: LearningDelta staging
Deliverables:
LearningDelta
repository
LearningEngine emits deltas
consolidation emits deltas
no direct active procedural promotion
Success signal:
All learned skill/policy/prompt/planner changes are staged and reviewable.

Milestone 3: Evaluation harness
Deliverables:
EvaluationPack
EvaluationResult
baseline evaluator
trace refs in results
eval-gated promotion
Success signal:
No learned procedure can become active without evaluation evidence.

Milestone 4: Mastery Registry
Deliverables:
MasteryItem
lifecycle states
registry/repository
transition policy
mastery score
UI/API query support
Success signal:
You can ask: “What does this agent know, for which versions, how well, and when was it last verified?”

Milestone 5: Mastery-aware retrieval and mode selection
Deliverables:
environment fingerprint
task classifier
mode selector
mastery-aware retrieval
negative-knowledge-first injection
maintenance-only/fast-learning modes
Success signal:
The agent selects different behavior for React Router v6 legacy, React Router v7 greenfield, unknown future version, and obsolete unsafe patterns.

Milestone 6: Obsolescence detection
Deliverables:
obsolescence event model
detector service
dependency/API/runtime/security triggers
automatic demotion/quarantine policy
Success signal:
Previously mastered knowledge is demoted when a version/API/security change invalidates it.

Milestone 7: Release integration
Deliverables:
mastery state included in release readiness
shadow/canary learning promotion
rollback refs
release gate enforcement
Success signal:
Learned behavior goes through SHADOW/CANARY/ACTIVE just like agent releases.

7. Tests to add
   Unit tests
   LearningContractTest
   L1 permits only episodic memory.
   L2 permits semantic/retrieval/confidence/routing.
   L3 permits procedural and negative knowledge.
   L4 permits prompt/planner.
   L5 is offline-only.
   MasteryTransitionPolicyTest
   cannot jump unknown to mastered
   cannot promote without eval
   can demote on failures
   can quarantine on safety failure
   maintenance-only retrieval only for matching context
   MasteryAwareRetrieverTest
   excludes obsolete
   includes negative knowledge
   filters by version
   active vs maintenance-only behavior
   ObsolescenceDetectorTest
   package major version changed
   API contract changed
   tests fail
   security advisory
   repeated failure
   PromotionEngineTest
   procedural skill requires eval + promotion evidence
   prompt template requires eval + rollout ref
   model adapter cannot self-activate
   rollback ref required
   Integration tests
   AgentTurnPipelineMasteryIntegrationTest
   classify task
   retrieve mastery-compatible memory
   execute
   capture
   reflect
   create learning delta
   GovernedAgentDispatcherMasteryTest
   blocked mastery denies dispatch
   shadow release can evaluate but not serve
   canary release can serve limited traffic
   trace ledger records mode/mastery decision
   LearningDeltaPromotionIntegrationTest
   successful episodes induce delta
   eval passes
   promotion writes procedure
   mastery advances to competent/mastered
   MaintenanceOnlyModeIntegrationTest
   v6 legacy project retrieves v6 maintenance procedure
   greenfield project excludes v6 maintenance procedure
   migration task retrieves both old and new with migration framing
   FastLearningModeIntegrationTest
   unknown version triggers docs/experiment flow
   tentative facts/procedures are stored but not active
   repeated passing eval promotes to practiced/competent
8. Recommended first implementation slice
   Build the smallest useful vertical slice:
   LearningDelta + MasteryRegistry + version-aware procedure retrieval

Concretely:
Add MasteryState, MasteryItem, and MasteryRegistry.
Add LearningDelta and repository.
Modify EpisodicToProceduralConsolidator to emit deltas instead of directly storing active procedures.
Add a minimal EvaluationHarness that can mark a delta pass/fail manually or via simple test callback.
Add PromotionEngine that writes an EnhancedProcedure with labels required by MemoryWritePolicy.
Add version fields to procedure labels or environmentConstraints.
Add MasteryAwareRetriever that filters procedures by version and state.
Add tests for active vs maintenance-only vs obsolete retrieval.
This gives you real progress without boiling the ocean.
9. Final target behavior
   Once implemented, the GAA should behave like this:
   Task: “Add routing to this React app.”

1. Fingerprint environment:
   react-router = 7.2.0

2. Query mastery:
   skill = react-router-routing
   v7 = mastered
   v6 = maintenance-only
   v4 = obsolete

3. Select mode:
   deterministic, because v7 skill is mastered and fresh

4. Retrieve:
    - v7 active procedure
    - v7 negative knowledge
    - known v7 failures
    - relevant examples

5. Execute:
   apply procedure, run tests

6. Capture:
   trace, test result, selected procedure, memory refs

7. Reflect:
   success reinforces mastery score

8. No new learning promoted unless new evidence reveals a better pattern.

For legacy:
Task: “Fix routing bug in old app.”

Fingerprint:
react-router = 6.14.0

Mastery:
v6 = maintenance-only

Mode:
maintenance-only

Behavior:
minimal fix, compatibility-preserving, warn not recommended for new work, suggest migration separately.

For unknown future version:
Fingerprint:
react-router = 8.0.0

Mastery:
no active skill

Mode:
fast-learning / verification-first

Behavior:
inspect docs, run sandbox examples, create tentative procedures, store failures, require eval before promotion.

That is the exact behavior your lifecycle is aiming for.
10. Bottom line
    The repo is closer than it may look. The foundation is already present:
    canonical GAA substrate and lifecycle in ADR-021
    immutable AgentDefinition
    governed AgentRelease
    release state machine
    governed dispatcher
    typed execution pipeline
    rich result/evidence envelope
    multi-tier memory plane
    procedural memory
    learning targets/contracts
    memory write governance
    The missing implementation layer is:
    MasteryRegistry
+ LearningDelta
+ EvaluationHarness
+ PromotionEngine
+ Version-aware retrieval
+ ObsolescenceDetector
+ ModeSelector

Build those as platform primitives, not as prompt logic. That is the path from “agent with memory” to a GAA that becomes a safe, version-aware master of its tasks over time.
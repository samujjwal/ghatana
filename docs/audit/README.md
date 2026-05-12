Below is a **file-by-file implementation plan** for reaching the GAA goal: version-aware, self-learning, mastery-driven agents that learn through governed evidence, not uncontrolled self-mutation.

This is based on repo snapshot `954aac3cbdad472ba48f525afe6fcf79f3b6a34d`. The current code already has strong primitives: immutable `AgentDefinition`, target-scoped `LearningContract`, `LearningLevel`, `LearningTarget`, `AgentRelease`, release-state governance, `MemoryPlane`, `MemoryWritePolicy`, `AgentTurnPipeline`, and `GovernedAgentDispatcher`.

---

# 0. Implementation principle

Do **not** let agents directly mutate active behavior after reflection.

Target flow:

```text
Episode / trace
→ LearningDelta
→ validation
→ evaluation
→ mastery transition
→ promoted memory/procedure/policy
→ release/runtime behavior update
```

Memory remembers. Evaluation proves. Mastery governs. Release controls deployment.

---

# 1. Existing files to modify first

## 1.1 `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/config/AgentDefinition.java`

### Current role

Canonical immutable agent blueprint. Already includes identity, version, type, determinism, autonomy, learning level, memory bindings, policy refs, evaluation refs, observability, and security contracts. It also materializes `LearningContract` from `learningLevel` and metadata.

### Changes

Add mastery-specific bindings:

```java
private final Map<String, Object> masteryBindings;
private final List<String> skillRefs;
private final List<String> masteryPolicyRefs;
```

Add accessors:

```java
@NotNull public Map<String, Object> getMasteryBindings();
@NotNull public List<String> getSkillRefs();
@NotNull public List<String> getMasteryPolicyRefs();
```

Add builder fields/methods:

```java
private final Map<String, Object> masteryBindings = new LinkedHashMap<>();
private final List<String> skillRefs = new ArrayList<>();
private final List<String> masteryPolicyRefs = new ArrayList<>();

public Builder masteryBindings(Map<String, Object> masteryBindings);
public Builder skillRefs(List<String> skillRefs);
public Builder masteryPolicyRefs(List<String> masteryPolicyRefs);
```

Add materialization helpers:

```java
@NotNull
public MasteryBinding toMasteryBinding();

@NotNull
public FreshnessPolicy toFreshnessPolicy();

@NotNull
public VersionCompatibilityPolicy toVersionCompatibilityPolicy();
```

Update `canonicalDigest()` to include:

```java
"masteryBindings=" + new TreeMap<>(masteryBindings)
"skillRefs=" + new ArrayList<>(skillRefs)
"masteryPolicyRefs=" + new ArrayList<>(masteryPolicyRefs)
```

### Acceptance criteria

* Agent definition digest changes if mastery bindings change.
* Adaptive agents can declare skill refs and mastery policy refs.
* Existing YAML/JSON definitions remain backward-compatible.

---

## 1.2 `platform/java/agent-core/src/main/java/com/ghatana/agent/framework/config/AgentDefinitionValidator.java`

### Current role

Validates schema, semantic consistency, security, cost, type-specific rules, and governance. It already checks sensitive capabilities, autonomy values, action governance, memory governance, and assurance metadata for high-risk agents.

### Changes

Add validation section:

```java
validateLearning(definition, errors);
validateMastery(definition, errors);
```

Add rules:

1. `ADAPTIVE` agents must declare `learningLevel >= L2`.
2. Agents with `LearningTarget.PROCEDURAL_SKILL` must require promotion.
3. Agents with `LearningTarget.SEMANTIC_FACT` must require provenance.
4. Agents with `LearningTarget.MODEL_ADAPTER` must be `L5`.
5. `L5` must not be response-serving.
6. `skillRefs` must not be blank.
7. If `masteryBindings` exists, it must include:

   * `namespace`
   * `registryRef`
   * `freshnessPolicyRef`
   * `versionCompatibilityPolicyRef`
8. High-risk agents must include `evaluationRefs`.

### Acceptance criteria

* Invalid adaptive/learning configs fail validation.
* Mastery-aware agents cannot omit mastery registry binding.
* High-risk self-learning agents must have evaluation refs.

---

## 1.3 `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/LearningLevel.java`

### Current role

Defines learning authority by target. L5 is offline-only.

### Changes

Keep existing semantics. Add helper methods:

```java
public boolean requiresProvenance();
public boolean requiresPromotion();
public boolean canRunOnline();
public boolean canServeResponses();
```

Suggested behavior:

```java
requiresProvenance: L2+
requiresPromotion: L3+
canRunOnline: all except L5
canServeResponses: all except L5
```

### Acceptance criteria

* No duplicated learning-level semantics elsewhere.
* Runtime and validator use these helpers.

---

## 1.4 `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/LearningContract.java`

### Current role

Declares learning level, allowed targets, provenance requirement, promotion requirement, and enforces `permits(target)` only when both target and level allow it.

### Changes

Add convenience methods:

```java
public void requirePermitted(LearningTarget target);
public boolean permitsAny(Set<LearningTarget> targets);
public boolean requiresPromotionFor(LearningTarget target);
public boolean requiresProvenanceFor(LearningTarget target);
```

Add constructor invariant:

* If `level.requiresProvenance()`, force `provenanceRequired=true`.
* If `level.requiresPromotion()`, force `promotionRequired=true`.

### Acceptance criteria

* Learning policy cannot be weakened by malformed metadata.
* Tests cover every learning level/target combination.

---

## 1.5 `platform/java/agent-core/src/main/java/com/ghatana/agent/learning/LearningTarget.java`

### Current role

Defines learning mutation targets.

### Changes

Add target categories:

```java
public boolean isMemoryTarget();
public boolean isPolicyTarget();
public boolean isExecutionTarget();
public boolean isOfflineOnlyTarget();
```

Add possibly missing target:

```java
MASTERY_STATE
```

Reason: mastery transitions are learned/derived but must be governed separately from procedural memory.

### Acceptance criteria

* Learning delta validation can classify target risk.
* `MODEL_ADAPTER` and potentially future high-risk targets are offline-only.

---

# 2. New mastery domain files

Create package:

```text
platform/java/agent-core/src/main/java/com/ghatana/agent/mastery/
```

## 2.1 `MasteryState.java`

```java
public enum MasteryState {
    UNKNOWN,
    OBSERVED,
    PRACTICED,
    COMPETENT,
    MASTERED,
    MAINTENANCE_ONLY,
    OBSOLETE,
    RETIRED
}
```

Add helpers:

```java
public boolean isExecutable();
public boolean isRetrievableForNewWork();
public boolean isRetrievableForLegacyWork();
public boolean isTerminal();
```

Rules:

* `MASTERED`, `COMPETENT` are executable.
* `MAINTENANCE_ONLY` executable only in matching legacy context.
* `OBSOLETE`, `RETIRED` not executable.

---

## 2.2 `MasteryTransitionReason.java`

```java
public enum MasteryTransitionReason {
    FIRST_OBSERVATION,
    REPEATED_SUCCESS,
    EVALUATION_PASSED,
    REGRESSION_FAILED,
    VERSION_CHANGED,
    SECURITY_GUIDANCE_CHANGED,
    API_CONTRACT_CHANGED,
    PROCEDURE_OUTPERFORMED,
    USER_OR_REPO_CONVENTION_CHANGED,
    MANUAL_REVIEW,
    RETIREMENT_REQUESTED
}
```

---

## 2.3 `VersionScope.java`

Fields:

```java
List<VersionConstraint> active;
List<VersionConstraint> maintenance;
List<VersionConstraint> obsolete;
```

Methods:

```java
public VersionApplicability classify(VersionContext context);
public boolean supportsActive(VersionContext context);
public boolean supportsMaintenance(VersionContext context);
public boolean isObsolete(VersionContext context);
```

---

## 2.4 `VersionConstraint.java`

Fields:

```java
String ecosystem;      // npm, maven, gradle, python, runtime
String packageName;
String versionRange;
String source;
```

---

## 2.5 `EnvironmentScope.java`

Fields:

```java
Set<String> productIds;
Set<String> projectTypes;
Set<String> frameworks;
Set<String> runtimes;
Set<String> repoConventions;
Set<String> incompatibleWith;
```

Method:

```java
public boolean matches(EnvironmentSnapshot snapshot);
```

---

## 2.6 `ConfidenceVector.java`

Fields:

```java
double correctness;
double freshness;
double applicability;
double safety;
double transferability;
double evidenceStrength;
double regressionStability;
```

Methods:

```java
public double executionScore();
public boolean isMasteryEligible();
public boolean requiresVerification();
```

Do not replace this with one confidence number.

---

## 2.7 `MasteryEvidenceType.java`

```java
public enum MasteryEvidenceType {
    EPISODE,
    TRACE,
    EVALUATION_RUN,
    TEST_RUN,
    HUMAN_REVIEW,
    OFFICIAL_DOC_SNAPSHOT,
    SECURITY_ADVISORY,
    REGRESSION_RESULT,
    TOOL_OUTPUT,
    USER_FEEDBACK
}
```

---

## 2.8 `MasteryEvidence.java`

Fields:

```java
String evidenceId;
MasteryEvidenceType type;
String ref;
String digest;
Instant createdAt;
String createdBy;
double weight;
Map<String, String> labels;
```

---

## 2.9 `MasteryItem.java`

Fields:

```java
String masteryItemId;
String skillId;
String domain;
String agentId;
String agentReleaseId;
MasteryState state;
VersionScope versionScope;
EnvironmentScope environmentScope;
ConfidenceVector confidence;
List<MasteryEvidence> evidence;
List<String> procedureIds;
List<String> negativeKnowledgeIds;
Instant lastVerifiedAt;
Instant staleAfter;
Map<String, String> labels;
```

Methods:

```java
public boolean isFresh(Instant now);
public boolean canExecute(VersionContext versionContext, EnvironmentSnapshot environment);
public boolean requiresMaintenanceMode(VersionContext versionContext);
public boolean blocksExecution(VersionContext versionContext);
```

---

## 2.10 `MasteryQuery.java`

Fields:

```java
String tenantId;
String agentId;
String skillId;
String domain;
VersionContext versionContext;
EnvironmentSnapshot environmentSnapshot;
Set<MasteryState> allowedStates;
boolean includeMaintenanceOnly;
boolean includeObsolete;
int limit;
```

---

## 2.11 `MasteryDecision.java`

Fields:

```java
String masteryItemId;
String skillId;
ExecutionMode recommendedMode;
boolean executable;
boolean requiresHumanApproval;
boolean requiresVerification;
String reason;
List<String> evidenceRefs;
```

---

## 2.12 `MasteryTransition.java`

Fields:

```java
String transitionId;
String masteryItemId;
MasteryState fromState;
MasteryState toState;
MasteryTransitionReason reason;
List<MasteryEvidence> evidence;
String requestedBy;
Instant requestedAt;
boolean requiresReview;
```

---

## 2.13 `MasteryTransitionResult.java`

Fields:

```java
String transitionId;
boolean accepted;
MasteryItem updatedItem;
String rejectionReason;
```

---

## 2.14 `MasteryRegistry.java`

Interface:

```java
public interface MasteryRegistry {
    Promise<Optional<MasteryItem>> findById(String masteryItemId);
    Promise<List<MasteryItem>> query(MasteryQuery query);
    Promise<MasteryDecision> decide(MasteryQuery query);
    Promise<MasteryTransitionResult> transition(MasteryTransition transition);
    Promise<List<MasteryItem>> findStale(Instant now);
}
```

---

## 2.15 `MasteryBinding.java`

Fields:

```java
String namespace;
String registryRef;
String freshnessPolicyRef;
String versionCompatibilityPolicyRef;
String obsolescencePolicyRef;
```

Used by `AgentDefinition.toMasteryBinding()`.

---

# 3. New version/context files

Create package:

```text
platform/java/agent-core/src/main/java/com/ghatana/agent/context/version/
```

## 3.1 `VersionContext.java`

Fields:

```java
Map<String, String> dependencies;
Map<String, String> runtimes;
Map<String, String> tools;
Map<String, String> apiContracts;
String sourceRef;
Instant resolvedAt;
```

Methods:

```java
public Optional<String> dependencyVersion(String packageName);
public boolean hasDependency(String packageName);
```

---

## 3.2 `DependencyFingerprint.java`

Fields:

```java
String ecosystem;
String packageName;
String version;
String sourceFile;
String digest;
```

---

## 3.3 `RuntimeFingerprint.java`

Fields:

```java
String runtimeName;
String runtimeVersion;
String platformVersion;
Map<String, String> attributes;
```

---

## 3.4 `RepositoryConventionFingerprint.java`

Fields:

```java
String repoFullName;
String commitSha;
Set<String> conventions;
Map<String, String> filesObserved;
```

---

## 3.5 `EnvironmentSnapshot.java`

Fields:

```java
String tenantId;
String productId;
String workspaceId;
String repoFullName;
String commitSha;
VersionContext versionContext;
RepositoryConventionFingerprint repositoryConventions;
Instant capturedAt;
```

---

## 3.6 `VersionContextResolver.java`

Interface:

```java
public interface VersionContextResolver {
    Promise<EnvironmentSnapshot> resolve(AgentContext ctx, Object taskInput);
}
```

---

## 3.7 `CompatibilityDecision.java`

Fields:

```java
boolean compatible;
boolean maintenanceOnly;
boolean obsolete;
String reason;
List<String> evidenceRefs;
```

---

# 4. New runtime mode files

Create package:

```text
platform/java/agent-core/src/main/java/com/ghatana/agent/runtime/mode/
```

## 4.1 `ExecutionMode.java`

```java
public enum ExecutionMode {
    DETERMINISTIC_EXECUTION,
    BOUNDED_PROBABILISTIC_REASONING,
    EXPLORATORY_FAST_LEARNING,
    MAINTENANCE_ONLY,
    HUMAN_GATED,
    VERIFICATION_FIRST,
    BLOCKED
}
```

---

## 4.2 `TaskRiskLevel.java`

```java
public enum TaskRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
```

---

## 4.3 `TaskNovelty.java`

```java
public enum TaskNovelty {
    KNOWN_TASK,
    KNOWN_VARIATION,
    UNKNOWN_TASK,
    NEW_VERSION,
    CONFLICTING_CONTEXT
}
```

---

## 4.4 `TaskClassification.java`

Fields:

```java
String taskType;
String skillId;
TaskRiskLevel riskLevel;
TaskNovelty novelty;
boolean sideEffecting;
boolean irreversible;
boolean productionImpacting;
Map<String, String> labels;
```

---

## 4.5 `TaskClassifier.java`

Interface:

```java
public interface TaskClassifier<I> {
    Promise<TaskClassification> classify(AgentContext ctx, I input, EnvironmentSnapshot snapshot);
}
```

---

## 4.6 `ModeSelectionResult.java`

Fields:

```java
ExecutionMode mode;
String reason;
MasteryDecision masteryDecision;
TaskClassification classification;
boolean requiresApproval;
boolean requiresVerification;
```

---

## 4.7 `ModeSelectionPolicy.java`

Interface:

```java
public interface ModeSelectionPolicy {
    ModeSelectionResult select(
        TaskClassification classification,
        MasteryDecision masteryDecision,
        EnvironmentSnapshot snapshot
    );
}
```

---

## 4.8 `MasteryAwareModeSelector.java`

Implement rules:

* `OBSOLETE` or `RETIRED` → `BLOCKED`
* `MAINTENANCE_ONLY` + legacy context → `MAINTENANCE_ONLY`
* `MAINTENANCE_ONLY` + new work → `BLOCKED` or `EXPLORATORY_FAST_LEARNING`
* `MASTERED` + fresh + low risk → `DETERMINISTIC_EXECUTION`
* `COMPETENT` → `BOUNDED_PROBABILISTIC_REASONING`
* unknown/new version → `EXPLORATORY_FAST_LEARNING`
* high risk/irreversible → `HUMAN_GATED`
* contradiction/stale → `VERIFICATION_FIRST`

---

# 5. New LearningDelta files

Create package:

```text
platform/java/agent-core/src/main/java/com/ghatana/agent/learning/delta/
```

## 5.1 `LearningDeltaType.java`

```java
public enum LearningDeltaType {
    CREATE_FACT,
    UPDATE_FACT,
    CREATE_PROCEDURE,
    UPDATE_PROCEDURE,
    CREATE_NEGATIVE_KNOWLEDGE,
    UPDATE_RETRIEVAL_POLICY,
    UPDATE_CONFIDENCE_THRESHOLD,
    UPDATE_ROUTING_POLICY,
    UPDATE_PROMPT_TEMPLATE,
    UPDATE_PLANNER_POLICY,
    CREATE_MODEL_ADAPTER,
    UPDATE_MASTERY_STATE
}
```

---

## 5.2 `LearningDeltaState.java`

```java
public enum LearningDeltaState {
    PROPOSED,
    VALIDATING,
    NEEDS_HUMAN_REVIEW,
    EVALUATION_PENDING,
    PROMOTABLE,
    PROMOTED,
    QUARANTINED,
    REJECTED,
    SUPERSEDED
}
```

---

## 5.3 `LearningDelta.java`

Fields:

```java
String deltaId;
String agentId;
String agentReleaseId;
LearningTarget target;
LearningDeltaType type;
LearningDeltaState state;
String targetRef;
String proposedArtifactRef;
List<String> sourceEpisodeIds;
List<String> sourceTraceIds;
List<String> evidenceRefs;
double confidence;
boolean provenanceSatisfied;
boolean promotionRequired;
Instant createdAt;
String createdBy;
Map<String, String> labels;
```

---

## 5.4 `LearningDeltaRepository.java`

Interface:

```java
Promise<LearningDelta> save(LearningDelta delta);
Promise<Optional<LearningDelta>> findById(String deltaId);
Promise<List<LearningDelta>> findByState(LearningDeltaState state);
Promise<LearningDelta> transition(String deltaId, LearningDeltaState state, String reason);
```

---

## 5.5 `LearningDeltaFactory.java`

Responsibilities:

* Convert candidate fact/procedure/policy into `LearningDelta`.
* Attach source episodes.
* Attach trace IDs.
* Attach required learning target.
* Enforce `LearningContract.permits(target)`.

---

## 5.6 `LearningDeltaEvaluator.java`

Responsibilities:

* Validate provenance.
* Run contradiction checks.
* Dispatch eval pack if needed.
* Decide:

   * `PROMOTABLE`
   * `NEEDS_HUMAN_REVIEW`
   * `QUARANTINED`
   * `REJECTED`

---

## 5.7 `LearningDeltaPromotionPolicy.java`

Responsibilities:

* Decide whether a delta may create:

   * active semantic fact
   * active procedure
   * negative knowledge
   * retrieval policy
   * routing policy
   * mastery transition

---

# 6. Modify LearningEngine

## File

```text
platform/java/agent-core/src/main/java/com/ghatana/agent/framework/learning/LearningEngine.java
```

### Current behavior

It queries recent episodes, synthesizes candidate policies, and persists approved policies to `MemoryStore`.

### Replace with

```text
query episodes
→ synthesize candidate learning
→ create LearningDelta
→ save LearningDelta
→ return LearningOutcome
```

### Concrete changes

1. Add dependencies:

```java
private LearningDeltaRepository deltaRepository;
private LearningDeltaFactory deltaFactory;
```

2. Replace `persistPolicies(...)` with:

```java
private Promise<LearningOutcome> proposeDeltas(...);
```

3. For each candidate:

```java
if (!learningContract.permits(LearningTarget.PROCEDURAL_SKILL)) {
    reject/skip and count as contractRejected;
}
```

4. Do not write to `MemoryStore.storePolicy(...)`.

5. Extend `LearningOutcome`:

```java
int deltasProposed;
int deltasRejectedByContract;
int deltasNeedingReview;
int deltasPromotable;
```

### Acceptance criteria

* Reflection creates deltas only.
* No active procedural policy is written directly by reflection.
* Provenance is attached to every delta.
* Tests prove contract-rejected deltas are not saved.

---

# 7. Data Cloud persistence for mastery and deltas

Create package:

```text
products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/mastery/
```

## 7.1 `DataCloudMasteryRegistry.java`

Implements `MasteryRegistry`.

Use Data Cloud collections:

```java
public static final String MASTERY_ITEMS = "agent-mastery-items";
public static final String MASTERY_TRANSITIONS = "agent-mastery-transitions";
public static final String MASTERY_EVIDENCE = "agent-mastery-evidence";
```

Required methods:

* `findById`
* `query`
* `decide`
* `transition`
* `findStale`

Transition must be append-only:

```text
save transition row
save updated mastery item
append trace/evidence ref
```

---

## 7.2 `MasteryItemMapper.java`

Map `MasteryItem` to/from Data Cloud entity map.

Must preserve:

* version scope
* environment scope
* confidence vector
* evidence refs
* procedure refs
* negative knowledge refs
* state
* timestamps

---

## 7.3 `DataCloudLearningDeltaRepository.java`

Package:

```text
products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/learning/
```

Collection:

```java
public static final String COLLECTION = "agent-learning-deltas";
```

Implements `LearningDeltaRepository`.

---

## 7.4 Tests

```text
products/data-cloud/extensions/agent-registry/src/test/java/com/ghatana/datacloud/agent/mastery/DataCloudMasteryRegistryTest.java
products/data-cloud/extensions/agent-registry/src/test/java/com/ghatana/datacloud/agent/learning/DataCloudLearningDeltaRepositoryTest.java
```

Test:

* save/find mastery item
* transition state
* reject invalid transition
* query by state
* query by agent
* save/find learning delta
* transition learning delta

---

# 8. Memory query and retrieval changes

## 8.1 Modify `products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/store/MemoryQuery.java`

### Current role

Has item type, tenant, agent, sphere, time range, tags, min confidence, validity status, text query, limit, offset.

### Add fields

```java
@Nullable Map<String, String> labelEquals;
@Nullable List<String> excludeTags;
@Nullable List<String> masteryStates;
@Nullable String skillId;
@Nullable String versionContextRef;
@Builder.Default boolean includeObsolete = false;
@Builder.Default boolean includeMaintenanceOnly = false;
@Builder.Default boolean includeNegativeKnowledge = true;
```

### Acceptance criteria

* Memory queries can filter learned artifacts/procedures by labels.
* Retrieval can exclude obsolete and retired knowledge.
* Maintenance-only retrieval must be explicit.

---

## 8.2 Modify `MemoryPlane.java`

File:

```text
products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/store/MemoryPlane.java
```

### Current role

Main memory SPI across episodic, semantic, procedural, artifacts, semantic search, working memory, task-state, checkpointing.

### Add optional mastery-aware query method

```java
@NotNull Promise<List<ScoredMemoryItem>> searchWithPolicy(
    @NotNull MemoryQuery query,
    @NotNull RetrievalPolicy policy
);
```

Alternative: keep `MemoryPlane` stable and implement this in a new service. Preferred: new service to avoid bloating SPI.

---

## 8.3 New package

```text
products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/retrieval/mastery/
```

### Files

```text
MasteryAwareMemoryRetriever.java
RetrievalPolicy.java
VersionAwareMemoryQueryPolicy.java
FreshnessAwareRanker.java
NegativeKnowledgePrioritizer.java
MaintenanceModeMemoryFilter.java
ObsoleteMemoryFilter.java
```

### `MasteryAwareMemoryRetriever.java`

Responsibilities:

1. Build `MemoryQuery` from task/mode/mastery.
2. Retrieve negative knowledge first.
3. Retrieve applicable procedures.
4. Retrieve semantic facts.
5. Retrieve similar episodes.
6. Exclude obsolete/retired by default.
7. Include maintenance-only only when mode is `MAINTENANCE_ONLY`.
8. Attach retrieval decision metadata.

---

## 8.4 Modify `StructuredContextInjector.java`

File:

```text
products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/retrieval/StructuredContextInjector.java
```

### Current role

Formats memory with tier grouping, provenance, confidence, conflict markers, and recency.

### Add rendering markers

Add labels if present:

```text
[MASTERED]
[COMPETENT]
[MAINTENANCE_ONLY]
[OBSOLETE - DO NOT USE]
[VERSION_MISMATCH]
[REQUIRES_VERIFICATION]
[NEGATIVE_KNOWLEDGE]
[TENTATIVE]
```

### Add ordering

Within formatted context:

1. Blocking warnings / negative knowledge
2. Mastered procedures
3. Competent procedures
4. Semantic facts
5. Episodes
6. Tentative/exploratory context

### Acceptance criteria

* Obsolete memory never appears as executable instruction.
* Maintenance-only memory clearly warns.
* Negative knowledge is visible before procedures.

---

## 8.5 Modify `GovernedMemoryPlane.java`

File:

```text
products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/governance/GovernedMemoryPlane.java
```

### Current role

Checks `DataAccessBroker` before reads and validates writes for facts/procedures/artifacts.

### Changes

1. Add `MasteryRegistry` dependency.
2. On procedure read/query:

   * verify mastery state if query declares `skillId`.
   * exclude obsolete/retired unless explicitly allowed.
3. On write:

   * require typed promotion metadata.
   * retain existing `MemoryWritePolicy` for compatibility.

### Acceptance criteria

* Memory governance is privacy-aware and mastery-aware.
* Learned procedures cannot be read as active unless promoted.

---

## 8.6 Modify `MemoryWritePolicy.java`

File:

```text
products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/memory/governance/MemoryWritePolicy.java
```

### Current role

Enforces validation/promotion labels before learned memory writes.

### Changes

Add typed metadata support:

```java
PromotionEvidence promotionEvidence = item.getMetadata(PromotionEvidence.class);
```

Validate in priority order:

1. typed metadata
2. labels fallback

Add support for `MASTERY_STATE` target.

Add stricter rule:

* `PROCEDURAL_SKILL` memory can be written as active only if linked `MasteryItem.state` is `COMPETENT` or `MASTERED`.

### Acceptance criteria

* Label-based writes still work.
* Typed metadata becomes canonical.
* Procedure writes require real promotion evidence.

---

# 9. Runtime integration

## 9.1 Modify `AgentTurnPipeline.java`

File:

```text
platform/java/agent-core/src/main/java/com/ghatana/agent/framework/runtime/AgentTurnPipeline.java
```

### Current role

Runs `ADMIT → PERCEIVE → REASON → VERIFY → ACT → CAPTURE → REFLECT → COMPLETE`, records phase traces and metrics, and enriches result with release/trace metadata.

### Changes

Add pre-ADMIT or ADMIT enrichment hook:

```java
TaskClassification classification = classifier.classify(...);
EnvironmentSnapshot snapshot = versionContextResolver.resolve(...);
MasteryDecision masteryDecision = masteryRegistry.decide(...);
ModeSelectionResult mode = modeSelector.select(...);
```

Store in `AgentContext` config:

```text
__taskClassification
__environmentSnapshot
__masteryDecision
__executionMode
__requiresApproval
__requiresVerification
```

### Acceptance criteria

* Every turn has execution mode.
* Mode is included in result trace metadata.
* Blocked mode prevents downstream `ACT`.

---

## 9.2 Modify `BaseAgent.java`

File:

```text
platform/java/agent-core/src/main/java/com/ghatana/agent/framework/runtime/BaseAgent.java
```

### Current role

Base lifecycle class. Captures episode to legacy `MemoryStore`.

### Changes

Minimize changes. Add extension points:

```java
protected Promise<Void> beforeCapture(...);
protected Promise<Void> afterCapture(...);
protected Promise<Void> proposeLearning(...);
```

Better: keep `BaseAgent` as-is and move richer capture into `AgentTurnPipeline`/memory-aware subclass.

### Acceptance criteria

* Existing agents do not break.
* New memory-aware agents can capture enhanced evidence.

---

## 9.3 Modify `TypedAgent.java`

File:

```text
platform/java/agent-core/src/main/java/com/ghatana/agent/TypedAgent.java
```

### Current role

Forward-looking type-safe contract.

### Changes

Add default optional hook:

```java
default Set<String> skillRefs() { return Set.of(); }
default LearningContract learningContract() { return new LearningContract(LearningLevel.L0, Set.of(), false, false); }
```

Keep backward-compatible defaults.

### Acceptance criteria

* New typed agents can declare skills and learning contract.
* Existing implementers compile unchanged.

---

## 9.4 Modify `GovernedAgentDispatcher.java`

File:

```text
products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcher.java
```

### Current role

Release guard, invariant monitoring, trace recording, OpenTelemetry tracing, capability guard.

### Add dependencies

```java
@Nullable MasteryRegistry masteryRegistry;
@Nullable VersionContextResolver versionContextResolver;
@Nullable TaskClassifier taskClassifier;
@Nullable ModeSelectionPolicy modeSelectionPolicy;
```

### Add checks before `doDispatch`

1. Resolve environment/version context.
2. Classify task.
3. Query mastery.
4. Select mode.
5. If `BLOCKED`, deny dispatch.
6. If `HUMAN_GATED`, emit approval request.
7. Enrich context with mastery/mode metadata.

### Add trace events

Requires updating `TraceEventType`.

```text
MASTERY_RESOLVED
MODE_SELECTED
MASTERY_BLOCKED
OBSOLESCENCE_DETECTED
LEARNING_DELTA_PROPOSED
LEARNING_DELTA_PROMOTED
```

### Acceptance criteria

* Runtime denies obsolete skill usage.
* Maintenance-only mode is enforced.
* Trace shows mastery decision and mode.

---

## 9.5 Modify `TraceEventType.java`

File:

```text
products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/audit/TraceEventType.java
```

### Current values

Includes action, approval, policy, memory mutation, delegation, invariants, turn start/completion, kill switch, budget alert.

### Add

```java
MASTERY_RESOLVED,
MODE_SELECTED,
MASTERY_BLOCKED,
OBSOLESCENCE_DETECTED,
LEARNING_DELTA_PROPOSED,
LEARNING_DELTA_VALIDATED,
LEARNING_DELTA_PROMOTED,
LEARNING_DELTA_REJECTED,
EVALUATION_STARTED,
EVALUATION_COMPLETED
```

### Acceptance criteria

* Trace ledger can represent full mastery/learning lifecycle.
* Runtime and learning systems use typed trace events, not generic strings.

---

# 10. Evaluation harness files

Create package:

```text
platform/java/agent-core/src/main/java/com/ghatana/agent/evaluation/
```

## 10.1 `EvaluationPack.java`

Fields:

```java
String evaluationPackId;
String version;
String skillId;
String digest;
List<SkillBenchmark> benchmarks;
List<String> regressionSuites;
List<String> safetySuites;
```

---

## 10.2 `SkillBenchmark.java`

Fields:

```java
String benchmarkId;
String name;
String skillId;
VersionScope versionScope;
List<SkillBenchmarkCase> cases;
```

---

## 10.3 `SkillBenchmarkCase.java`

Fields:

```java
String caseId;
String inputRef;
String expectedOutputRef;
String environmentSnapshotRef;
Map<String, String> assertions;
```

---

## 10.4 `EvaluationRun.java`

Fields:

```java
String evaluationRunId;
String evaluationPackId;
String agentId;
String agentReleaseId;
String learningDeltaId;
Instant startedAt;
Instant completedAt;
EvaluationResult result;
```

---

## 10.5 `EvaluationResult.java`

Fields:

```java
boolean passed;
double score;
Map<String, Double> dimensionScores;
List<String> failedCaseIds;
List<String> evidenceRefs;
```

---

## 10.6 `EvaluationHarness.java`

Interface:

```java
Promise<EvaluationRun> run(EvaluationPack pack, LearningDelta delta, AgentContext ctx);
```

---

## 10.7 Data Cloud persistence

Create:

```text
products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/evaluation/DataCloudEvaluationPackRepository.java
products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/evaluation/DataCloudEvaluationRunRepository.java
```

Collections:

```text
agent-evaluation-packs
agent-evaluation-runs
agent-trace-grades
```

### Acceptance criteria

* Mastery promotion requires evaluation result where applicable.
* Evaluation evidence is linked to `MasteryEvidence`.

---

# 11. Obsolescence detection files

Create package:

```text
platform/java/agent-core/src/main/java/com/ghatana/agent/obsolescence/
```

## 11.1 `ObsolescenceSignalType.java`

```java
public enum ObsolescenceSignalType {
    DEPENDENCY_VERSION_CHANGED,
    API_CONTRACT_CHANGED,
    TEST_FAILURE,
    OFFICIAL_DOC_CONTRADICTION,
    REPO_CONVENTION_CHANGED,
    BETTER_PROCEDURE_FOUND,
    SECURITY_GUIDANCE_CHANGED,
    RUNTIME_PLATFORM_CHANGED,
    REPEATED_SKILL_FAILURE
}
```

---

## 11.2 `ObsolescenceSignal.java`

Fields:

```java
String signalId;
ObsolescenceSignalType type;
String targetRef;
String evidenceRef;
Instant detectedAt;
double severity;
```

---

## 11.3 `ObsolescenceEvent.java`

Fields:

```java
String eventId;
String masteryItemId;
List<ObsolescenceSignal> signals;
MasteryState recommendedState;
String reason;
boolean requiresReview;
```

---

## 11.4 `ObsolescenceDetector.java`

Interface:

```java
Promise<List<ObsolescenceEvent>> detect(EnvironmentSnapshot snapshot, List<MasteryItem> candidates);
```

---

## 11.5 `ObsolescencePolicy.java`

Rules:

* version mismatch → `MAINTENANCE_ONLY` or `OBSOLETE`
* security advisory → `OBSOLETE`
* repeated failures → demote to `PRACTICED` or quarantine
* better procedure consistently wins → deprecate older procedure

---

## 11.6 Data Cloud persistence

```text
products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/mastery/DataCloudObsolescenceEventRepository.java
```

Collection:

```text
agent-obsolescence-events
```

---

# 12. AEP/orchestrator integration

## 12.1 Modify `AgentMemoryPlaneClient.java`

File:

```text
products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/aep/engine/registry/AgentMemoryPlaneClient.java
```

### Current role

Records execution as enhanced episode and returns episodic, semantic, and procedural memory. It also includes a noop memory fallback.

### Changes

Add dependency:

```java
private final MasteryRegistry masteryRegistry;
```

Add method:

```java
public Promise<AgentExecutionService.AgentMemory> getMemory(
    String agentId,
    MasteryQuery masteryQuery,
    VersionContext versionContext
)
```

Enhance response:

```text
episodic
semantic
procedural
negativeKnowledge
mastery
maintenanceWarnings
obsoleteWarnings
lastVerified
```

Restrict `NoopMemoryPlane`:

* Keep only for unit tests.
* Fail fast in production if no persistent memory plane is configured.

### Acceptance criteria

* AEP memory response is mastery-aware.
* Legacy/noop memory cannot silently hide missing persistence in production.

---

## 12.2 Modify `DataCloudAgentReleaseRepository.java`

File:

```text
products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/registry/DataCloudAgentReleaseRepository.java
```

### Current role

Persists `AgentRelease`, supports find/transition/governing release.

### Changes

Add release lookup by evaluation/maturity:

```java
Promise<List<AgentRelease>> findByEvaluationPack(String evaluationPackId);
Promise<List<AgentRelease>> findByCapabilityMaturity(String maturity);
```

Fix transition persistence pattern if needed:

* Current `transition()` finds release, calls `withState()`, then `save(updated)`.
* Ensure save updates existing release rather than creating duplicate entity records, unless Data Cloud intentionally uses append-only release history.

### Acceptance criteria

* Release transitions remain query-consistent.
* Active/governing release lookup returns the latest intended state.

---

# 13. Tests to add or update

## 13.1 Agent core tests

```text
platform/java/agent-core/src/test/java/com/ghatana/agent/learning/LearningLevelTest.java
platform/java/agent-core/src/test/java/com/ghatana/agent/learning/LearningContractTest.java
platform/java/agent-core/src/test/java/com/ghatana/agent/framework/config/AgentDefinitionLearningContractTest.java
platform/java/agent-core/src/test/java/com/ghatana/agent/framework/config/AgentDefinitionMasteryValidationTest.java
platform/java/agent-core/src/test/java/com/ghatana/agent/mastery/MasteryStateTest.java
platform/java/agent-core/src/test/java/com/ghatana/agent/mastery/MasteryTransitionTest.java
platform/java/agent-core/src/test/java/com/ghatana/agent/mastery/VersionScopeTest.java
platform/java/agent-core/src/test/java/com/ghatana/agent/mastery/ConfidenceVectorTest.java
platform/java/agent-core/src/test/java/com/ghatana/agent/runtime/mode/MasteryAwareModeSelectorTest.java
platform/java/agent-core/src/test/java/com/ghatana/agent/learning/delta/LearningDeltaFactoryTest.java
platform/java/agent-core/src/test/java/com/ghatana/agent/obsolescence/ObsolescencePolicyTest.java
```

---

## 13.2 Agent runtime tests

```text
products/data-cloud/planes/action/agent-runtime/src/test/java/com/ghatana/agent/memory/retrieval/mastery/MasteryAwareMemoryRetrieverTest.java
products/data-cloud/planes/action/agent-runtime/src/test/java/com/ghatana/agent/memory/retrieval/StructuredContextInjectorMasteryTest.java
products/data-cloud/planes/action/agent-runtime/src/test/java/com/ghatana/agent/memory/governance/MemoryWritePolicyMasteryTest.java
products/data-cloud/planes/action/agent-runtime/src/test/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcherMasteryTest.java
```

Test cases:

1. Obsolete skill blocks dispatch.
2. Maintenance-only skill works only for matching legacy version.
3. New version triggers fast-learning mode.
4. Negative knowledge is injected before procedure.
5. Procedure write fails without promotion evidence.
6. Prompt/planner artifact fails without eval/rollout refs.
7. Model adapter cannot self-activate.

---

## 13.3 Data Cloud registry tests

```text
products/data-cloud/extensions/agent-registry/src/test/java/com/ghatana/datacloud/agent/mastery/DataCloudMasteryRegistryTest.java
products/data-cloud/extensions/agent-registry/src/test/java/com/ghatana/datacloud/agent/mastery/DataCloudObsolescenceEventRepositoryTest.java
products/data-cloud/extensions/agent-registry/src/test/java/com/ghatana/datacloud/agent/learning/DataCloudLearningDeltaRepositoryTest.java
products/data-cloud/extensions/agent-registry/src/test/java/com/ghatana/datacloud/agent/evaluation/DataCloudEvaluationRunRepositoryTest.java
```

---

## 13.4 End-to-end scenario tests

```text
products/data-cloud/planes/action/agent-runtime/src/test/java/com/ghatana/agent/e2e/GaaMasteryLifecycleE2ETest.java
```

Scenario:

1. Agent starts with unknown skill.
2. Executes task in exploratory mode.
3. Stores episode.
4. Reflection creates learning delta.
5. Eval pack runs.
6. Mastery moves to `PRACTICED`.
7. Repeated evals move to `COMPETENT`.
8. Mastery becomes `MASTERED`.
9. Dependency version changes.
10. Old skill becomes `MAINTENANCE_ONLY`.
11. New version triggers fast-learning mode.
12. Obsolete procedure is blocked.
13. Full trace contains mastery events.

---

# 14. Suggested implementation sequence

## Batch 1 — Core contracts

Implement:

```text
MasteryState.java
MasteryTransitionReason.java
VersionScope.java
VersionConstraint.java
EnvironmentScope.java
ConfidenceVector.java
MasteryEvidenceType.java
MasteryEvidence.java
MasteryItem.java
MasteryQuery.java
MasteryDecision.java
MasteryTransition.java
MasteryTransitionResult.java
MasteryRegistry.java
```

Modify:

```text
AgentDefinition.java
AgentDefinitionValidator.java
LearningLevel.java
LearningContract.java
LearningTarget.java
```

---

## Batch 2 — Learning delta

Implement:

```text
LearningDeltaType.java
LearningDeltaState.java
LearningDelta.java
LearningDeltaRepository.java
LearningDeltaFactory.java
LearningDeltaEvaluator.java
LearningDeltaPromotionPolicy.java
```

Modify:

```text
LearningEngine.java
```

---

## Batch 3 — Data Cloud persistence

Implement:

```text
DataCloudMasteryRegistry.java
MasteryItemMapper.java
DataCloudLearningDeltaRepository.java
DataCloudObsolescenceEventRepository.java
DataCloudEvaluationPackRepository.java
DataCloudEvaluationRunRepository.java
```

Modify:

```text
DataCloudAgentReleaseRepository.java
```

---

## Batch 4 — Runtime mode and context

Implement:

```text
VersionContext.java
DependencyFingerprint.java
RuntimeFingerprint.java
RepositoryConventionFingerprint.java
EnvironmentSnapshot.java
VersionContextResolver.java
CompatibilityDecision.java
ExecutionMode.java
TaskRiskLevel.java
TaskNovelty.java
TaskClassification.java
TaskClassifier.java
ModeSelectionPolicy.java
ModeSelectionResult.java
MasteryAwareModeSelector.java
```

Modify:

```text
AgentTurnPipeline.java
TypedAgent.java
GovernedAgentDispatcher.java
TraceEventType.java
```

---

## Batch 5 — Memory retrieval and governance

Implement:

```text
MasteryAwareMemoryRetriever.java
RetrievalPolicy.java
VersionAwareMemoryQueryPolicy.java
FreshnessAwareRanker.java
NegativeKnowledgePrioritizer.java
MaintenanceModeMemoryFilter.java
ObsoleteMemoryFilter.java
PromotionEvidence.java
PromotionState.java
ValidationState.java
MemoryPromotionMetadata.java
MemoryPromotionMetadataMapper.java
```

Modify:

```text
MemoryQuery.java
StructuredContextInjector.java
GovernedMemoryPlane.java
MemoryWritePolicy.java
AgentMemoryPlaneClient.java
```

---

## Batch 6 — Evaluation and obsolescence

Implement:

```text
EvaluationPack.java
SkillBenchmark.java
SkillBenchmarkCase.java
EvaluationRun.java
EvaluationResult.java
EvaluationHarness.java
ObsolescenceSignalType.java
ObsolescenceSignal.java
ObsolescenceEvent.java
ObsolescenceDetector.java
ObsolescencePolicy.java
```

---

# 15. Final target file map

```text
platform/java/agent-core/src/main/java/com/ghatana/agent/
  framework/config/
    AgentDefinition.java                         MODIFY
    AgentDefinitionValidator.java                MODIFY
  framework/learning/
    LearningEngine.java                          MODIFY
  framework/runtime/
    AgentTurnPipeline.java                       MODIFY
    BaseAgent.java                               OPTIONAL MINOR MODIFY
  learning/
    LearningLevel.java                           MODIFY
    LearningContract.java                        MODIFY
    LearningTarget.java                          MODIFY
  learning/delta/
    LearningDelta.java                           NEW
    LearningDeltaType.java                       NEW
    LearningDeltaState.java                      NEW
    LearningDeltaRepository.java                 NEW
    LearningDeltaFactory.java                    NEW
    LearningDeltaEvaluator.java                  NEW
    LearningDeltaPromotionPolicy.java            NEW
  mastery/
    MasteryState.java                            NEW
    MasteryTransitionReason.java                 NEW
    VersionScope.java                            NEW
    VersionConstraint.java                       NEW
    EnvironmentScope.java                        NEW
    ConfidenceVector.java                        NEW
    MasteryEvidenceType.java                     NEW
    MasteryEvidence.java                         NEW
    MasteryItem.java                             NEW
    MasteryQuery.java                            NEW
    MasteryDecision.java                         NEW
    MasteryTransition.java                       NEW
    MasteryTransitionResult.java                 NEW
    MasteryRegistry.java                         NEW
    MasteryBinding.java                          NEW
  context/version/
    VersionContext.java                          NEW
    DependencyFingerprint.java                   NEW
    RuntimeFingerprint.java                      NEW
    RepositoryConventionFingerprint.java         NEW
    EnvironmentSnapshot.java                     NEW
    VersionContextResolver.java                  NEW
    CompatibilityDecision.java                   NEW
  runtime/mode/
    ExecutionMode.java                           NEW
    TaskRiskLevel.java                           NEW
    TaskNovelty.java                             NEW
    TaskClassification.java                      NEW
    TaskClassifier.java                          NEW
    ModeSelectionPolicy.java                     NEW
    ModeSelectionResult.java                     NEW
    MasteryAwareModeSelector.java                NEW
  evaluation/
    EvaluationPack.java                          NEW
    SkillBenchmark.java                          NEW
    SkillBenchmarkCase.java                      NEW
    EvaluationRun.java                           NEW
    EvaluationResult.java                        NEW
    EvaluationHarness.java                       NEW
  obsolescence/
    ObsolescenceSignalType.java                  NEW
    ObsolescenceSignal.java                      NEW
    ObsolescenceEvent.java                       NEW
    ObsolescenceDetector.java                    NEW
    ObsolescencePolicy.java                      NEW

products/data-cloud/extensions/agent-registry/src/main/java/com/ghatana/datacloud/agent/
  registry/
    DataCloudAgentReleaseRepository.java         MODIFY
  mastery/
    DataCloudMasteryRegistry.java                NEW
    MasteryItemMapper.java                       NEW
    DataCloudObsolescenceEventRepository.java    NEW
  learning/
    DataCloudLearningDeltaRepository.java        NEW
  evaluation/
    DataCloudEvaluationPackRepository.java       NEW
    DataCloudEvaluationRunRepository.java        NEW

products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/
  audit/
    TraceEventType.java                          MODIFY
  memory/store/
    MemoryQuery.java                             MODIFY
    MemoryPlane.java                             PREFER NO CHANGE OR ADD OPTIONAL EXTENSION
  memory/retrieval/
    StructuredContextInjector.java               MODIFY
  memory/retrieval/mastery/
    MasteryAwareMemoryRetriever.java             NEW
    RetrievalPolicy.java                         NEW
    VersionAwareMemoryQueryPolicy.java           NEW
    FreshnessAwareRanker.java                    NEW
    NegativeKnowledgePrioritizer.java            NEW
    MaintenanceModeMemoryFilter.java             NEW
    ObsoleteMemoryFilter.java                    NEW
  memory/governance/
    GovernedMemoryPlane.java                     MODIFY
    MemoryWritePolicy.java                       MODIFY
    PromotionEvidence.java                       NEW
    PromotionState.java                          NEW
    ValidationState.java                         NEW
    MemoryPromotionMetadata.java                 NEW
    MemoryPromotionMetadataMapper.java           NEW
  runtime/safety/
    GovernedAgentDispatcher.java                 MODIFY

products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/aep/engine/registry/
  AgentMemoryPlaneClient.java                    MODIFY
```

---

# 16. Most important acceptance rule

After all implementation phases:

```text
No agent-learned fact, procedure, prompt, routing rule, planner policy, or model adapter can become active behavior unless it has:

1. permitted LearningContract target,
2. provenance,
3. version/applicability scope,
4. validation,
5. evaluation or review where required,
6. mastery transition evidence,
7. trace/audit record.
```

That is the difference between an agent that merely accumulates memory and a GAA that can safely become a master of its tasks.

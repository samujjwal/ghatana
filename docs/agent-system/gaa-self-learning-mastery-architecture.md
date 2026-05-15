# GAA Self-Learning Mastery Architecture

> **Purpose**: This document defines the non-negotiable invariants for the Growing Autonomous Agent (GAA) self-learning and mastery system.
> **Status**: Canonical specification for GAA lifecycle, learning vs mastery separation, and version-aware mastery rules.
> **Reference**: ADR-020 defines the platform-wide agent system as five layers.

## 1. GAA Lifecycle

The governed lifecycle for all GAA agents is:

```text
ADMIT → PERCEIVE → REASON → VERIFY → ACT → CAPTURE → REFLECT → COMPLETE
```

### Pre-Admit Enrichment
Before ADMIT, the runtime must enrich context with:
- Environment fingerprinting (runtime versions, dependencies, platform)
- Task classification (risk level, novelty, complexity)
- Mastery decision (query MasteryRegistry for skill applicability)
- Mode selection (deterministic / supervised / fast-learning / maintenance / blocked)

### Post-Act Verification
After ACT and before CAPTURE, the runtime must:
- Verify side effects against safety contracts
- Check approval/verification proofs
- Validate version compatibility
- Record trace evidence

### Reflect Phase
The REFLECT phase must:
- Emit `LearningDelta` objects only (never direct mastery mutation)
- Require `LearningDeltaRepository` for L3+ learning
- Include provenance (episodes, environment, version, rollback path)
- Route low-confidence deltas to human review

## 2. Learning vs Mastery Separation

### Learning (Online, Ephemeral)
- **Purpose**: Extract patterns from episodes and propose changes
- **Output**: `LearningDelta` objects with confidence scores
- **Governance**: `LearningContract` defines allowed targets and promotion requirements
- **Levels**: L0 (none) → L5 (offline governance only)
- **Mutation**: Never directly modifies mastery; creates deltas for evaluation

### Mastery (Offline, Durable)
- **Purpose**: Track skill maturity, version applicability, and lifecycle state
- **Output**: `MasteryItem` records with state transitions
- **Governance**: `MasteryPolicy` defines transition rules and evidence requirements
- **States**: UNKNOWN → OBSERVED → PRACTICED → COMPETENT → MASTERED → (MAINTENANCE_ONLY | OBSOLETE | QUARANTINED | RETIRED)
- **Mutation**: Only through evaluated `LearningDelta` promotion or offline governance workflows

### Critical Invariant
`MASTERY_STATE` is blocked for sub-L5 agents. L5 is offline-only and cannot serve responses directly. Only explicit L5 governance workflows can propose `MASTERY_STATE` deltas.

## 3. LearningDelta as the Only Online Learning Mutation Output

### Required Fields
Every `LearningDelta` must include:
- `target`: The learning target (SEMANTIC_FACT, PROCEDURAL_SKILL, NEGATIVE_KNOWLEDGE, etc.)
- `state`: Delta state (PROPOSED, PENDING_HUMAN_REVIEW, PROMOTED, REJECTED)
- `agentId`, `agentReleaseId`, `skillId`, `tenantId`: Scoping identifiers
- `contentDigest`: Hash of proposed content for integrity
- `proposedContent`: The actual proposed change
- `evidenceRefs`: References to source episodes or verification artifacts
- `evaluationRefs`: References to evaluation packs or test results
- `sourceEpisodeIds`: IDs of episodes that contributed to this delta
- `rollbackRef`: Reference to rollback procedure or previous version
- `confidenceBefore` / `confidenceAfter`: Confidence vector (correctness, freshness, applicability, safety, evidence strength)
- `reviewState`: Human review status
- `approvalProof`: Reference to approval workflow
- `versionContextDigest`: Hash of environment fingerprint at learning time
- `environmentFingerprintRef`: Reference to environment snapshot
- `repositoryConventionRef`: Reference to repo structure/conventions
- `runtimeFingerprintRef`: Reference to runtime configuration

### Delta States
- **PROPOSED**: Ready for evaluation (high confidence)
- **PENDING_HUMAN_REVIEW**: Requires manual review (low confidence or high risk)
- **PROMOTED**: Successfully promoted to mastery
- **REJECTED**: Failed evaluation or human review

### Promotion Rules
- L2+ deltas require provenance tracking
- L3+ deltas require promotion (evaluation before activation)
- No delta is promoted without evaluation refs and evidence
- Promotion is idempotent (same delta cannot be promoted twice)

## 4. MASTERY_STATE as Governance-Only

### Allowed Transitions
- **UNKNOWN → OBSERVED**: Requires one trace or verified source
- **OBSERVED → PRACTICED**: Requires repeated episodes or sandbox experiments
- **PRACTICED → COMPETENT**: Requires procedure exists and basic eval passes
- **COMPETENT → MASTERED**: Requires regression, safety, recovery, and compatibility tests pass
- **MASTERED → MAINTENANCE_ONLY**: New active version exists; old version still used
- **Any → OBSOLETE**: Docs/API/security/runtime contradiction or repeated failures
- **Any → QUARANTINED**: Unsafe behavior or failed safety eval
- **OBSOLETE → RETIRED**: No active retrieval/use case remains

### Evidence Requirements
- **COMPETENT**: Procedure ID, basic evaluation refs
- **MASTERED**: Full evaluation pack, regression tests, safety tests, recovery tests, compatibility tests
- **MAINTENANCE_ONLY**: Evidence that newer active version exists
- **OBSOLETE**: Contradiction evidence (docs, API, security, runtime)
- **QUARANTINED**: Safety evaluation failure or unsafe behavior evidence

### Governance Constraint
Only L5 offline governance workflows can propose `MASTERY_STATE` deltas. Normal agents (L0-L4) cannot directly modify mastery state.

## 5. Version-Aware Mastery Rules

### VersionScope Structure
VersionScope partitions constraints into three categories:
- **active**: Versions where the skill is actively recommended
- **maintenance**: Legacy versions where the skill is still usable but not recommended for new work
- **obsolete**: Versions where the skill should not be used

### VersionApplicability Classification
- **ACTIVE**: Matches active constraints
- **MAINTENANCE**: Matches maintenance constraints (or active for legacy context)
- **OBSOLETE**: Matches obsolete constraints
- **UNKNOWN**: Matches no constraints

### Overlap Validation
Ambiguous version scopes must fail at definition/materialization time:
- Same package cannot appear in both active and obsolete with overlapping ranges
- Maintenance range cannot overlap active without explicit precedence
- Unknown range syntax is invalid
- Overlap detection must use real semver/range parsing (npm semver, Maven version ranges, Python PEP 440, runtime versions, framework versions)

### Version Context
Every execution must include a `VersionContext` containing:
- Dependency versions (package manager, runtime, framework)
- Platform version (OS, runtime, SDK)
- Repository conventions (structure, tooling)
- Environment fingerprint (cloud provider, region, configuration)

### Retrieval Rules
- Do not retrieve by similarity alone
- Retrieve by version compatibility, freshness, mastery state, trust, risk, and task applicability
- Obsolete and retired knowledge never enters working context by default
- Maintenance-only knowledge only enters for matching legacy context
- New-project tasks cannot accidentally retrieve maintenance-only patterns

## 6. Maintenance-Only Behavior

### When to Use
- Only when version context matches legacy scope
- Human-gated by default
- Minimal safe fixes only
- No architecture expansion
- No new feature development on old stack unless explicitly requested

### Restrictions
- New projects cannot use maintenance-only patterns
- Legacy projects cannot accidentally receive new-version-only procedures
- Always include migration suggestion separately
- Requires explicit version context match

## 7. Fast-Learning Behavior

### When to Use
- Unknown/new versions
- Always human-gated or supervised
- Creates tentative deltas only
- Requires sandbox experiments
- Can reach OBSERVED or PRACTICED, not MASTERED, without evals
- Must store failures as negative knowledge

### Restrictions
- Unknown tools/libraries default to fast-learning, not confident execution
- Requires explicit sandbox environment
- Low-confidence deltas enter human review
- Cannot skip evaluation for MASTERED state

## 8. Obsolescence Rules

### Detection Triggers
- Dependency version changed
- API contract changed
- Tests failed with old procedure
- Documentation changed
- Security advisory found
- Runtime/platform changed
- Repository convention changed
- Repeated failures

### Severity Routing
- **Low/Medium**: Verification-first
- **High**: Maintenance-only or obsolete
- **Critical/Security**: Quarantine

### Transition Requirements
- Stale mastery is demoted or blocked before execution
- Security-critical obsolescence routes to QUARANTINED
- Maintenance-only transitions require evidence that a newer active version exists
- Obsolete knowledge is filtered from retrieval by default

### Scanning
- Tenant-specific scan schedules
- Triggered scans on dependency manifest changes, release changes, failed evaluation, security advisory ingestion
- Dashboard/API visibility for operators

## 9. Promotion and Quarantine Rules

### Promotion Evidence Bundle
Required for:
- COMPETENT: Procedure ID, basic evaluation refs, evidence refs
- MASTERED: Full evaluation pack, regression tests, safety tests, recovery tests, compatibility tests
- MAINTENANCE_ONLY: Evidence of newer active version
- OBSOLETE: Contradiction evidence
- QUARANTINED: Safety evaluation failure evidence

### Idempotency
- Promotion includes idempotency key (delta ID, target mastery ID, target state, evaluation digest)
- Repeated promotion with same key returns success without side effects
- Transition log and current item state cannot permanently diverge

### Rollback/Refusal
- If item save fails after transition append, rollback the transition
- Reconciliation worker scans transition log, detects item state mismatch, replays or repairs transition

## 10. Required Telemetry

### Trace Events
Every runtime path must produce:
- Release digest verified
- Version context resolved
- Mastery decision made
- Mode selected
- Memory retrieved (with IDs and inclusion reasons)
- Approval checked
- Verification checked
- Learning delta proposed

### Metrics
- Mastery transitions by state
- Promotion queue depth and latency
- Obsolescence scan results
- Dispatcher overhead
- Learning delta creation rate
- Human review queue depth

### Logs
- Structured logs for important state changes and failures
- Correlation/request ID propagation
- Decision reasoning (why a mode was selected, why memory was included/excluded)

## 11. Definition of Done

The goal is achieved when this scenario works end-to-end:

```text
User asks agent to modify routing in a repo.
 Runtime fingerprints repo and package versions.
 Agent resolves skill: frontend.react-router-routing.
 MasteryRegistry returns correct version-scoped mastery.
 Mode selector chooses deterministic / supervised / maintenance / fast-learning / blocked.
 Memory retriever injects only compatible facts, procedures, failures, and negative knowledge.
 Agent acts through governed lifecycle.
 Verification runs.
 Episode is captured.
 LearningEngine emits LearningDelta with provenance.
 EvaluationHarness evaluates the delta.
 PromotionEngine updates MasteryRegistry if policy allows.
 ObsolescenceScanner later demotes/quarantines stale or unsafe knowledge.
 UI/API can explain every decision with evidence.
```

### Principle
The agent does not merely know how to do a task. It knows which version of the knowledge applies, how strong the evidence is, whether the skill is active or maintenance-only, when it became stale, and whether it is safe to execute without human approval.

## 12. References

- **ADR-020**: Five-layer agent architecture (Specification/Release, Control/Governance, Execution, Memory/Context/Evaluation, Product Capability)
- **LearningContract**: Defines learning level, allowed targets, provenance requirements, promotion requirements
- **MasteryPolicy**: Defines transition rules and evidence requirements
- **VersionScope**: Version-aware mastery applicability
- **LearningDelta**: Online learning mutation output
- **MasteryItem**: Durable mastery state tracking
- **GovernedAgentDispatcher**: Enforced runtime path
- **MASTERY_CI_REGRESSION_GATES**: Required CI gates for mastery system

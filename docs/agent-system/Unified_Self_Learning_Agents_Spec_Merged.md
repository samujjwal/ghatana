# Self-Learning Agents: Complete Technical Specification
## DSLA, NDSLA, Transition Architecture, and Unified Reference

**Version:** 3.0.0 (Merged Edition)  
**Last Updated:** 2026-04-05  
**Status:** Authoritative Specification  
**Scope:** Deterministic and Non-Deterministic Self-Learning Agent Architectures

---

## Document Sources

This document is the unified, non-redundant consolidation of three source documents:

1. **Unified_Self_Learning_Agents_Spec_Final.md** - Base comprehensive specification
2. **DSLA_NDSLA_Full_Detailed_Spec.md** - Detailed implementation specifications
3. **NDSLA_to_DSLA_Transition_Full_Spec.md** - Transition methodology (content integrated throughout)

---

## Table of Contents

### Part I: Foundations
1. [Purpose and Scope](#1-purpose-and-scope)
2. [Foundational Concepts](#2-foundational-concepts)
3. [Common Reference Model](#3-common-reference-model)

### Part II: Deterministic Self-Learning Agents (DSLA)
4. [DSLA Definition and Philosophy](#4-deterministic-self-learning-agents-dsla)
5. [DSLA Core Invariants](#5-dsla-core-invariants)
6. [DSLA Three-Plane Architecture](#6-dsla-three-plane-architecture)
7. [DSLA Control Plane](#7-dsla-control-plane)
8. [DSLA Memory Plane](#8-dsla-memory-plane)
9. [DSLA Learning Plane](#9-dsla-learning-plane)
10. [DSLA State and Hypothesis Handling](#10-dsla-state-and-hypothesis-handling)
11. [DSLA Operator Model](#11-dsla-operator-model)
12. [DSLA Execution Loop](#12-dsla-execution-loop)
13. [DSLA Exploration and Search](#13-dsla-exploration-and-search)
14. [DSLA Negative Knowledge](#14-dsla-negative-knowledge)
15. [DSLA Causal Attribution](#15-dsla-causal-attribution)
16. [DSLA Governance and Safety](#16-dsla-governance-and-safety)
17. [DSLA Determinism Engineering](#17-dsla-determinism-engineering)
18. [DSLA Observability](#18-dsla-observability)
19. [DSLA Evaluation](#19-dsla-evaluation)
20. [DSLA Non-Functional Requirements](#20-dsla-non-functional-requirements)

### Part III: Non-Deterministic Self-Learning Agents (NDSLA)
21. [NDSLA Definition and Philosophy](#21-non-deterministic-self-learning-agents-ndsla)
22. [NDSLA Core Properties](#22-ndsla-core-properties)
23. [NDSLA Six-Plane Architecture](#23-ndsla-six-plane-architecture)
24. [NDSLA Interaction Plane](#24-ndsla-interaction-plane)
25. [NDSLA Control Plane](#25-ndsla-control-plane)
26. [NDSLA Memory Plane](#26-ndsla-memory-plane)
27. [NDSLA Retrieval Stack](#27-ndsla-retrieval-stack)
28. [NDSLA World-Model Plane](#28-ndsla-world-model-plane)
29. [NDSLA Learning Plane](#29-ndsla-learning-plane)
30. [NDSLA Planning Under Uncertainty](#30-ndsla-planning-under-uncertainty)
31. [NDSLA Exploration](#31-ndsla-exploration)
32. [NDSLA Continual Learning](#32-ndsla-continual-learning)
33. [NDSLA Safety and Governance](#33-ndsla-safety-and-governance)

### Part IV: Transition and Hybrid Architectures
34. [Transition Philosophy](#34-transition-from-ndsla-to-dsla)
35. [Seven-Stage Transition Model](#35-seven-stage-transition-model)
36. [Promotion Pipeline](#36-promotion-pipeline)
37. [Verification Layer](#37-verification-layer)
38. [Unified Hybrid Architecture](#38-unified-hybrid-architecture)
39. [Two-Tier Model](#39-two-tier-hybrid-model)

### Part V: Shared Systems
40. [Memory Systems and Governance](#40-memory-systems-and-multi-level-governance)
41. [Retrieval and Knowledge Integration](#41-retrieval-reading-and-knowledge-integration)
42. [Learning Channels and Update Governance](#42-learning-channels-and-update-governance)
43. [World Models and Uncertainty](#43-world-models-uncertainty-and-causal-structure)
44. [Safety, Policy, Audit, Compliance](#44-safety-policy-audit-and-compliance)
45. [Evaluation, Benchmarking, Validation](#45-evaluation-benchmarking-and-validation)

### Part VI: Implementation
46. [Implementation and Deployment Strategy](#46-implementation-and-deployment-strategy)
47. [Reference Schemas](#47-reference-schemas-and-formal-artifacts)
48. [Comparative Analysis](#48-comparative-analysis-and-decision-guidance)
49. [Open Questions](#49-open-questions-and-future-research)
50. [Final Synthesis](#50-final-synthesis)

---

# Part I: Foundations

## 1. Purpose and Scope

This specification addresses three related but distinct architectural targets:

- **DSLA**: Deterministic self-learning agent architecture optimized for exact replayability, symbolic explicitness, bounded online memory, and governance-heavy environments.
- **NDSLA**: Non-deterministic self-learning agent architecture optimized for adaptation, uncertainty handling, exploration, memory-driven learning, and open-world performance.
- **Transition architecture**: Staged method for progressively converting non-deterministic learning agents into deterministic, validated, authoritative agents without prematurely sacrificing learning power.

### 1.1 Intended Use

This document serves:
- Enterprise architecture design
- Product/platform architecture
- Agent runtime specification
- Internal standards and governance
- Implementation planning
- Evaluation framework design
- Memory subsystem design
- Multi-agent systems planning
- Research-to-production translation

### 1.2 Target Agent Types

- LLM-centric agents
- Non-LLM symbolic agents
- Hybrid agents
- Tool-using agents
- Stateful assistants
- Workflow/orchestration agents
- Long-running operational agents
- Agent platforms with multiple products or modes

This specification treats "agent" as a **general computational architecture** rather than as a synonym for "LLM with tools."

---

## 2. Foundational Concepts

### 2.1 What Is a Self-Learning Agent?

A self-learning agent is a system that improves future behavior using accumulated experience, feedback, or derived knowledge, without requiring complete manual reprogramming for every new improvement.

Improvement may occur through:
- Memory updates
- Procedure extraction
- Reflection
- Rule induction
- Retrieval-policy refinement
- World-model refinement
- Prompt/program evolution
- Parameter updates
- Policy updates
- Negative knowledge formation
- Task-state refinement

A self-learning agent is defined by the existence of a feedback loop in which prior behavior influences later behavior in a structured way.

### 2.2 Three Axes Differentiating Self-Learning Agents

A useful high-level lens evaluates agent architectures on three axes:

#### 2.2.1 Determinism
How strongly do repeated runs under equivalent conditions produce identical results?

#### 2.2.2 Explicitness
How much of the agent's reasoning, memory, and control logic is inspectable, typed, and governable?

#### 2.2.3 Adaptivity
How well does the agent improve under novelty, uncertainty, drift, partial observability, or open-world tasks?

A DSLA sits high on determinism and explicitness, but often lower on unconstrained adaptivity.  
An NDSLA sits high on adaptivity, but lower on exact replayability and explicit authority.  
A hybrid architecture aims to combine strengths by separating exploratory and authoritative layers.

### 2.3 Deterministic vs Reproducible vs Stable

These terms are often conflated but should be separated:

**Deterministic**: Given equivalent inputs, equivalent internal state, equivalent environment responses, and equivalent configuration, the system produces identical transitions and outputs.

**Reproducible**: The same experiment or workflow can be repeated with sufficiently similar outcomes, though not always byte-for-byte identical.

**Stable**: Behavior remains within acceptable bounds over time even if not exactly identical.

A DSLA aims for **deterministic internal authority**.  
An NDSLA may aim for **stable and well-evaluated behavior** without exact replayability.  
The transition architecture aims to move important externally committing behavior from stable/probabilistic toward deterministic.

### 2.4 Core Vocabulary

- **Control plane**: Live decision, planning, execution, and orchestration logic
- **Memory plane**: All structured storage relevant to working, episodic, semantic, procedural, or archival knowledge
- **Learning plane**: All mechanisms that modify rules, memory, policies, skills, prompts, or models over time
- **Authoritative core**: Part of the system permitted to commit externally visible or high-risk actions
- **Exploratory layer**: Part of the system allowed to hypothesize, retrieve broadly, sample, search, or propose candidates
- **Promotion**: Process of moving learned artifacts from lower-assurance exploratory use into higher-assurance authoritative use
- **Negative knowledge**: Formally represented knowledge of what must not be done, what is invalid, or what is unsafe
- **Task-state memory**: Persistent state supporting long-running, resumable, checkpointable task execution
- **World model**: Any representation of environment dynamics, causality, hidden state, or action outcomes used for planning or inference
- **Memory reading strategy**: How retrieved memory is prepared and used by the reasoning system; not just retrieval itself

---

## 3. Common Reference Model

### 3.1 Minimal Abstract Agent Model

A generic self-learning agent can be described by the tuple:

\[
\mathcal{A} = \langle S, O, A, M, P, L, G, C \rangle
\]

Where:
- \( S \): state representation
- \( O \): observations
- \( A \): actions / operators
- \( M \): memory system
- \( P \): policy / planner / reasoner
- \( L \): learning/update functions
- \( G \): goals
- \( C \): constraints / safety / policy / governance controls

This abstraction is deliberately broad enough to support symbolic, probabilistic, neural, or hybrid implementations.

### 3.2 Planes of a Modern Agent Architecture

Most practical self-learning agents benefit from separating concerns across planes:

At minimum:
- Control plane
- Memory plane
- Learning plane

For richer systems, especially NDSLA or hybrid architectures, also include:
- Interaction plane
- World-model plane
- Safety/evaluation plane

### 3.3 Memory as Hierarchy, Not Monolith

Memory in agents should be a managed hierarchy. Different memory forms solve different problems:

- Working memory maintains immediate coherence
- Episodic memory preserves experience
- Semantic memory preserves stable knowledge
- Procedural memory preserves reusable methods
- Task-state memory preserves continuity
- Archive preserves accountability
- Vector/graph representations improve retrieval, not meaning by themselves

This hierarchy is foundational to both DSLA and NDSLA, though each uses it differently.

---

# Part II: Deterministic Self-Learning Agents (DSLA)

## 4. Deterministic Self-Learning Agents (DSLA)

### 4.1 Definition

A **Deterministic Self-Learning Agent** is a **bounded-memory, symbolically grounded, policy-governed, self-improving agentic system** whose authoritative internal decision process is deterministic under equivalent runtime conditions.

A DSLA can learn, but learning must occur through deterministic, inspectable, and reversible mechanisms.

**DSLA Core Invariant:**

For equivalent:
- Initial state snapshot
- Rule/procedure versions
- Configuration
- Input sequence
- Normalized observation sequence
- Environment response sequence

The agent must produce:
- Identical chosen actions
- Identical state transitions
- Identical memory mutations
- Identical rule changes
- Identical externally visible outputs and side effects

This does **not** require the environment itself to be deterministic. It requires the agent's authoritative decision process to be deterministic given the observations it has received.

### 4.2 Design Goals

A DSLA should optimize for:
- Exact or near-exact replayability
- Explicit control over learning
- Bounded online memory
- Structured symbolic state
- Explicit constraints and invariants
- Safe self-modification
- Strong auditability
- Strong explainability
- Safe deployment in policy-heavy or regulated environments

### 4.3 Suitable Domains

DSLA is especially suitable for:
- High-assurance enterprise workflows
- Policy or compliance automation
- Regulated healthcare or finance support logic
- Deterministic orchestration systems
- Safety-critical tool mediation
- Industrial or operational control logic
- Reproducible planning environments
- Cases where human review and audit are first-class requirements

### 4.4 Foundational Philosophy

#### 4.4.1 Determinism as Architectural Law

In DSLA, determinism is not a convenience. It is a design law.

This means:
- No hidden randomness in action selection
- No stochastic sampling in exploration
- No probability-based tie-breaking
- No non-deterministic update order
- No floating nondeterminism that changes behavior across machines
- No implicit learning behavior hidden in opaque latent state evolution

Instead, every state transition must be attributable to:
- Explicit inputs
- Explicit state
- Explicit rules or procedures
- Explicit tool outputs
- Explicit deterministic update logic

The result is a system that can be:
- Replayed exactly
- Tested precisely
- Audited structurally
- Debugged causally
- Versioned safely
- Promoted in high-assurance settings

#### 4.4.2 Learning Without Surrendering Authority

DSLA still learns, but learning is constrained to deterministic channels.

Learning does not mean:
- Stochastic gradient updates
- Latent drift
- Probabilistic value updates
- Heuristic mutation without proof or validation

Learning instead means:
- Explicit rule induction
- Counterexample capture
- Deterministic consolidation
- Safe abstraction
- Promotion of validated procedures
- Controlled semantic updates
- Bounded forgetting or compression

#### 4.4.3 Symbolic Explicitness Over Latent Ambiguity

The DSLA worldview assumes that if a system is to be governed, trusted, debugged, and safely improved, then its important reasoning artifacts must be materially inspectable.

That implies preference for:
- Canonical symbolic state
- Typed entities and relations
- Explicit action operators
- Explicit constraints
- Explicit justifications
- Explicit negative knowledge
- Explicit versioned policies

Over:
- Hidden latent representations as source of truth
- Uninterpretable value signals
- Opaque confidence-driven decisions
- Untracked implicit memory drift

---

## 5. DSLA Core Invariants

The following invariants define a true DSLA.

### 5.1 Reproducible Execution Invariant

For equivalent runtime conditions:
- Identical initial state snapshot
- Identical configuration
- Identical operator library version
- Identical rule memory version
- Identical input sequence
- Identical environment response sequence

The agent must produce:
- Identical chosen actions
- Identical internal state transitions
- Identical memory updates
- Identical rule insertions/removals
- Identical outputs and side effects

### 5.2 Bounded Online Memory Invariant

The online decision system must operate with bounded memory.

This means the memory used by the live reasoning loop should scale with:
- Representation complexity
- Configured capacities
- Active context needs

And not with raw lifetime experience length.

Raw historical experience may exist in archival logs, but the active decision system must not depend on unlimited accumulation.

### 5.3 No Probabilistic Authority Invariant

The authoritative reasoning loop must not rely on probability distributions, random draws, or stochastic sampling to choose actions or update core knowledge.

Uncertainty must be handled by:
- Explicit disjunction
- Hypothesis sets
- Deferred commitment
- Deterministic fallback selection
- Information-gathering actions

### 5.4 Safe Self-Modification Invariant

No learning update may bypass:
- Invariant checks
- Schema validation
- Compatibility checks
- Safety class restrictions
- Promotion policy
- Rollback readiness

### 5.5 Explainability Invariant

Every important decision must be traceable to:
- State facts or predicates
- Goals
- Matching rules or procedures
- Planner branch selection
- Invariant filtering
- Observed tool results
- Deterministic tie-break ordering

---

## 6. DSLA Three-Plane Architecture

DSLA is best represented as a **three-plane architecture** with strong internal typing and policy boundaries:

1. **Control Plane** - Live execution engine
2. **Memory Plane** - Structured, bounded, typed storage
3. **Learning Plane** - Self-modification governance

These are supplemented in practice by:
- Policy/safety overlays
- Audit/observability facilities
- Version and migration infrastructure
- Deterministic storage and serialization rules

---

## 7. DSLA Control Plane

The control plane is the live execution engine. It receives input, reasons over current goals and state, selects operators deterministically, and advances the system.

### 7.1 Responsibilities

- Input normalization
- Canonicalization
- State update
- Goal management
- Rule matching
- Plan search
- Invariant enforcement
- Operator selection
- Execution orchestration
- Failure handling
- Task continuity

### 7.2 Internal Components

#### 7.2.1 Perception and Canonicalization Engine

Transforms raw observations into canonical symbolic form.

Duties include:
- Schema validation
- Type normalization
- Naming normalization
- Reference resolution
- Entity identity resolution
- Relation extraction if rules permit
- Timestamp normalization where time is explicit
- Representation of unknown fields as explicit unknowns, not silent omission

The objective is that semantically equivalent inputs map to the same canonical state form.

#### 7.2.2 Symbolic State Manager

Maintains the structured world model used by the agent.

Typical state contents:
- Entities
- Attributes
- Relations
- Predicates
- Goals
- Task status
- Active bindings
- Constraints
- Environment observations
- Hypothesis sets
- Recent transition summaries

The state manager is authoritative for:
- Current active facts
- Task-relevant context
- Operator applicability
- Planner inputs

#### 7.2.3 Goal Manager

Goals must be explicit in DSLA.

A goal manager should support:
- Goal hierarchy
- Goal activation/deactivation
- Priority ordering
- Dependency graphs
- Conflict detection
- Blocked-state marking
- Success/failure criteria
- Goal suspension and resumption
- Subgoal derivation

Goals should not simply be natural-language strings. They should be normalized into typed goal structures wherever possible.

#### 7.2.4 Operator Library

Operators are the action primitives or action macros of the system.

Each operator should carry:
- Identifier
- Preconditions
- Postconditions
- Invariants
- Resource requirements
- Side-effect class
- Determinism grade
- Retry semantics
- Rollback or compensation pathway
- Trust classification
- Cost metadata if planning uses cost

#### 7.2.5 Deterministic Planner

The planner may be simple or sophisticated, but it must remain deterministic.

Supported planning styles:
- Direct rule fire
- Forward search
- Backward chaining
- Bounded BFS
- Bounded DFS
- Lexicographic best-first with deterministic comparator
- Macro-operator application
- Contingency planning through explicit branch sets
- Information-gathering action planning under uncertainty

When multiple valid plans exist, the tie-breaking policy must be explicit and stable.

Example comparator:
1. Fewer steps
2. Lower total explicit cost
3. Lexicographic operator sequence
4. Smaller stable state fingerprint of projected endpoint

#### 7.2.6 Execution Engine

The execution engine:
- Invokes chosen operators
- Updates state
- Records results
- Applies compensation if needed
- Handles deterministic failure semantics
- Emits structured traces

#### 7.2.7 Invariant Checker

This is a critical gate.

It validates:
- State consistency
- Policy compliance
- Safety rules
- Forbidden combinations
- Authorization rules
- Task-specific invariants
- Domain-specific no-go patterns

The invariant checker is allowed to reject plans or actions even if they are otherwise feasible.

---

## 8. DSLA Memory Plane

The memory plane provides structured, bounded, typed storage for knowledge used or produced by the agent.

A DSLA memory plane should not be monolithic. It should separate memory by functional role.

### 8.1 Memory Design Principles

- Type everything important
- Separate memory by function
- Explicitly govern write permissions
- Support canonicalization and deduplication
- Support bounded online memory
- Isolate archival memory from live reasoning memory
- Allow deterministic compression and promotion
- Support provenance and versioning

### 8.2 Working Memory

Working memory contains the immediate state needed for ongoing reasoning.

Typical contents:
- Current state projection
- Active goals
- Latest observations
- Current plan branch
- Temporary derived predicates
- Variable bindings
- Recent tool outputs
- Short-lived hypothesis structures

Working memory is:
- High-frequency access
- Bounded
- Transient
- Frequently overwritten or refreshed

### 8.3 Task-State Memory

Task-state memory is essential for long-running or resumable work.

It should store:
- Task identifier
- Task phase graph
- Progress markers
- Checkpoints
- Blockers
- Prerequisites
- Rollback points
- Branch state
- Completion conditions
- External dependencies
- Recent state deltas relevant to task continuity

This allows the agent to:
- Pause and resume
- Explain current status
- Recover after interruption
- Rollback task-local changes
- Manage long-lived workflows

### 8.4 Semantic Memory

Semantic memory stores durable, generalized knowledge:
- Facts
- Invariants
- Ontologies
- Entity schemas
- Domain rules
- Normalized assertions
- Validated environment facts
- Stable operational truths

Semantic memory should be:
- Curated
- Versioned
- Normalized
- Conflict-aware
- Provenance-aware

It should not become a dumping ground for all experience.

### 8.5 Procedural Memory

Procedural memory stores reusable processes:
- Procedures
- Macro operators
- Validated plan fragments
- Troubleshooting workflows
- Domain playbooks
- Safe recovery sequences
- Policy-compliant execution templates

These procedures should be parameterizable and versioned.

### 8.6 Negative Memory

Negative memory is extremely important in DSLA.

It stores:
- Forbidden actions
- Invalid state-action pairs
- Contradiction patterns
- Dead-end motifs
- Unsafe sequences
- Anti-patterns
- Failed operator contexts
- Policy violation motifs

A mature DSLA often becomes more useful by learning what **not** to do than by endlessly accumulating positive patterns.

### 8.7 Archive / Append-Only Log

The archive contains:
- All externally relevant events
- All operator invocations
- Tool outputs
- Transition traces
- Rule updates
- Promotion/rejection decisions
- Evictions
- Rollbacks
- Policy violations
- Audit entries

This archive should be append-only for traceability.

It is not the same as working memory and should not be blindly consulted in the authoritative decision path.

Its primary roles are:
- Audit
- Debugging
- Replay
- Offline analytics
- Deterministic consolidation
- Forensic reconstruction

### 8.8 State Fingerprints and Canonical Serialization

A DSLA may compute deterministic fingerprints for:
- Equality testing
- Cache keys
- Visited-state detection
- Cycle prevention
- Fast exact-state lookup

However, the hash is not the state itself. The symbolic state remains primary.

---

## 9. DSLA Learning Plane

The learning plane governs how the agent changes itself.

A DSLA must learn without sacrificing determinism. Therefore, all learning channels must be explicit and controlled.

### 9.1 Learning Responsibilities

The learning plane should support:
- Positive rule induction
- Negative rule induction
- Counterexample capture
- Rule refinement
- Rule pruning
- Semantic consolidation
- Procedural extraction
- Abstraction
- Rule conflict handling
- Promotion and rollback

### 9.2 Positive Rule Induction

When an action or plan succeeds in a useful way, the system may induce a positive rule.

A positive rule should capture:
- Triggering context
- Relevant goal
- Preconditions
- Chosen action/operator
- Successful outcome class
- Supporting evidence
- Usage counters
- Validity restrictions

Rule creation must itself be deterministic.

### 9.3 Negative Rule Induction

When an action leads to a clear failure, policy violation, blocked state, or repeated dead-end, the system may create a negative rule.

Negative rules are useful for:
- Pruning search
- Reducing repeated failures
- Protecting safety
- Enforcing learned no-go patterns

### 9.4 Counterexample Capture

Counterexamples should be stored when:
- An expected action fails in a new context
- A previously good pattern breaks under new conditions
- An abstraction is proven too broad
- A procedure is invalidated by changed environment conditions

Counterexamples help preserve correctness during generalization.

### 9.5 Rule Refinement

Rules should not be static once created.

Refinement may include:
- Narrowing preconditions
- Broadening safely where evidence supports it
- Splitting over-general rules
- Merging equivalent rules
- Attaching additional invariants
- Increasing specificity based on counterexamples

### 9.6 Consolidation

Consolidation transforms repeated lower-level experiences into more useful higher-level knowledge.

Examples:
- Multiple successful operator sequences become a macro procedure
- Repeated environment facts become a stable semantic assertion
- Repeated failures become a negative constraint
- Duplicate rules become a normalized canonical rule

Consolidation must be deterministic and versioned.

### 9.7 Promotion Pipeline

Not every learned artifact should immediately enter the authoritative core.

A proper promotion pipeline should include:
1. Candidate creation
2. Validation
3. Invariant checks
4. Conflict analysis
5. Regression evaluation
6. Compatibility review
7. Promotion
8. Logging and version tagging

### 9.8 Versioning and Rollback

Every meaningful learned artifact should be version-tracked.

Rollback should support:
- Revert latest rule promotion
- Revert procedure update
- Revert semantic assertion update
- Restore previous rule set
- Revert a whole promotion batch

### 9.9 Rule Classes and Permissions

Rules may be classified as:
- Immutable safety rules
- Immutable ontology rules
- Mutable operational rules
- Mutable local heuristics
- Temporary task-local rules
- Deprecated historical rules

Only appropriate components may update each class.

---

## 10. DSLA State and Hypothesis Handling

### 10.1 Canonical Symbolic State

State representation is central to DSLA. A weak state model undermines determinism, explainability, and learning quality.

The state should contain explicit structured data:
- Entity instances
- Entity types
- Attributes
- Relations
- Derived predicates
- Goal bindings
- Task metadata
- Environment status
- Known unknowns
- Contradictions or ambiguities
- Local history pointers if needed

### 10.2 Identity and Canonicalization

Equivalent objects should resolve to the same identity when domain rules allow.

This requires:
- Canonical naming
- Content-based normalization if appropriate
- Deterministic alias resolution
- Stable IDs
- Deterministic ordering of collections

### 10.3 Exact and Abstract Views

A good DSLA usually benefits from both:
- Exact symbolic state
- Abstract state view for generalization

This allows learning at multiple levels without losing exactness where needed.

### 10.4 Partial Observability

DSLA does not deny uncertainty. It denies stochastic internal authority.

When the environment is partially observed, DSLA should represent uncertainty through:
- Set of possible world states
- Hypothesis graph
- Explicit unknowns
- Deferred facts
- Contradiction markers

### 10.5 Hypothesis Objects

A hypothesis should include:
- Id
- Candidate fact/world interpretation
- Justification
- Supporting observations
- Contradictions
- Active/inactive status
- Resolution conditions

### 10.6 Resolution

Hypotheses may be resolved by:
- New observations
- Tool-based verification
- Invariant failure
- Goal-context narrowing
- Deterministic preference policy if resolution is mandated

---

## 11. DSLA Operator Model

A DSLA can only be as trustworthy as its operator model.

### 11.1 Operator Fields

A full operator specification should include:
- Id
- Name
- Description
- Action class
- Preconditions
- Postconditions
- State deltas
- Side effects
- Compensation/rollback
- Idempotence class
- Timeout semantics
- Retriability
- Trust class
- Authorization class
- Observability hooks
- Expected failure modes

### 11.2 Tool Integration Principles

Tools used by DSLA should ideally be:
- Deterministic
- Schema-based
- Explicit about side effects
- Permission-gated
- Observable
- Retry-safe or clearly non-retry-safe
- Auditable

If a tool is nondeterministic externally, DSLA must model that nondeterminism explicitly rather than silently pretending the environment is exact.

---

## 12. DSLA Execution Loop

The DSLA runtime loop should be explicit and inspectable.

### 12.1 Canonical Runtime Cycle

1. Receive event or user request
2. Normalize and canonicalize
3. Update working and task state
4. Validate state consistency
5. Match rules and procedures
6. Generate candidate operators or plan branches
7. Apply invariant and policy filters
8. Select deterministically
9. Execute chosen operator
10. Receive and structure result
11. Record full transition
12. Trigger learning hooks if applicable
13. Advance goal/task status
14. Repeat or terminate

### 12.2 Failure Handling

Failure should be structured, not ad hoc.

The runtime should distinguish:
- Operator precondition failure
- Tool execution failure
- State inconsistency
- Policy rejection
- Environmental block
- Unknown outcome
- Contradiction-triggered halt
- Timeout
- Compensation failure

Each should have deterministic handling semantics.

---

## 13. DSLA Exploration and Search

Deterministic systems still need exploration, but it must be systematic rather than stochastic.

### 13.1 Exploration Order

A strong DSLA exploration policy may use the following order:

1. Untried valid operators for the exact state
2. Operators proven useful in similar symbolic contexts
3. Macro procedures applicable in current goal context
4. Novelty-producing operators under deterministic novelty rules
5. Canonical operator order as final fallback

### 13.2 Exploration Enhancements

A more advanced DSLA may also support:
- Subgoal decomposition
- Deterministic lookahead
- Frontier prioritization using stable cost/order metrics
- Counterexample-based pruning
- Branch elimination by invariant contradiction
- Deterministic iterative deepening

### 13.3 Local Minima and Accepted Tradeoffs

A DSLA may accept slower or narrower exploration in exchange for:
- Reproducibility
- Bounded memory
- Safety
- Explainability

This is an intentional tradeoff, not a defect.

---

## 14. DSLA Negative Knowledge

Negative knowledge deserves its own treatment because it often becomes the backbone of safe DSLA behavior.

### 14.1 Types of Negative Knowledge

- Forbidden action in state/context
- Unsafe operator sequence
- Invalid assumption pattern
- Contradiction pair
- Impossible goal precondition
- Non-recoverable branch
- Policy-prohibited action class
- Historically harmful abstraction

### 14.2 Benefits

Negative knowledge improves:
- Safety
- Search efficiency
- Regression resistance
- Consistency
- Explainable refusal
- Policy compliance

### 14.3 Storage and Use

Negative knowledge should be:
- Explicit
- Typed
- Prioritized
- Versioned
- Consulted before final operator selection

---

## 15. DSLA Causal Attribution

DSLA should not only know what happened, but why.

### 15.1 Transition Graph

A transition graph links:
- Prior state
- Chosen operator
- Environment/tool output
- Next state
- Observed effects

### 15.2 Causal Attribution

The system should support:
- Direct cause tracing
- Contributing factor tracking
- Dependency chains
- Rollback justification
- Blame and responsibility localization

### 15.3 Why This Matters

Causal attribution improves:
- Debugging
- Rule refinement
- Trust
- Failure analysis
- Safe procedure extraction

---

## 16. DSLA Governance and Safety

DSLA is especially attractive in settings where governance matters.

### 16.1 Rule Classes

Rules should be classified, for example, as:
- Immutable safety rules
- Immutable ontology rules
- Mutable operational rules
- Mutable local heuristics
- Temporary task-local rules
- Deprecated historical rules

### 16.2 Write Permissions

Not every subsystem should be allowed to update every rule class.

Example:
- Task-local learning may update local heuristics
- Only validated promotion flow may update core semantic or procedural memory
- No subsystem may mutate immutable safety rules directly

### 16.3 Rollback Policy

Rollback should be available at multiple levels:
- Last artifact rollback
- Last batch rollback
- Last version rollback
- Task-local rollback
- State snapshot restore

---

## 17. DSLA Determinism Engineering

Determinism is fragile unless engineered deliberately.

### 17.1 Runtime Requirements

- Deterministic collection ordering
- Stable sorting
- Canonical serialization
- Stable hashing
- Fixed-point or exact arithmetic where required
- No race-dependent writes
- No hidden reliance on wall-clock unless explicitly modeled
- Deterministic comparison operators

### 17.2 Storage Requirements

- Canonical schema encoding
- Versioned snapshots
- Stable IDs
- Migration discipline
- Explicit backward/forward compatibility policy

### 17.3 Distributed Execution Considerations

If distributed:
- Partitioning must be deterministic
- Merge semantics must be deterministic
- Conflict resolution order must be fixed
- Message ordering assumptions must be explicit

---

## 18. DSLA Observability

A DSLA without strong observability loses much of its value.

### 18.1 Required Trace Types

- Input trace
- Canonicalization trace
- Goal trace
- Rule match trace
- Planner branch trace
- Invariant rejection trace
- Operator execution trace
- State delta trace
- Learning trace
- Promotion/rollback trace
- Memory eviction/compression trace

### 18.2 Metrics

**Control metrics:**
- Task success rate
- Average steps per task
- Invalid action rejection count
- Invariant violation count
- Refusal count
- Recovery count

**Memory metrics:**
- Working memory size
- Semantic memory size
- Procedural memory size
- Negative memory size
- Archive growth
- Deduplication ratio
- Eviction rate

**Learning metrics:**
- New rule creation count
- Promoted rule precision
- Rollback frequency
- Counterexample rate
- Consolidation win rate
- Stale rule count

---

## 19. DSLA Evaluation

A DSLA must be evaluated not just for task success, but for structural correctness.

### 19.1 Determinism Tests

- Same snapshot + same input replay
- Cross-machine replay equivalence
- Serialization-deserialization equivalence
- Rule-order stability tests
- Migration-preserved behavior tests

### 19.2 Memory Tests

- Bounded-memory behavior under long operation
- Retention of critical rules
- Eviction correctness
- Duplicate suppression
- Counterexample preservation

### 19.3 Learning Tests

- Positive rule precision
- Negative rule precision
- Safe generalization
- Absence of harmful over-abstraction
- Rollback correctness

### 19.4 Benchmark Families

- Deterministic navigation and planning tasks
- Tool workflows with known environment responses
- Long-running resumable tasks
- Contradiction-rich environments
- Bounded-memory stress tests
- Policy-heavy workflow benchmarks
- Reproducibility and regression suites

---

## 20. DSLA Non-Functional Requirements

### 20.1 Performance

- Bounded reasoning latency
- Predictable memory costs
- Bounded operator selection time
- Efficient deterministic serialization

### 20.2 Scalability

- Scalable storage
- Deterministic sharding where applicable
- Bounded local reasoning cost
- Controlled archive growth

### 20.3 Security and Safety

- Permissioned tools
- Strict policy enforcement
- Immutable audit trails
- Sensitive memory tagging
- Safe rollback and restore

### 20.4 Maintainability

- Versioned rule sets
- Schema governance
- Migration tools
- Observability-first operations
- Testable operator contracts

### 20.5 Strengths and Limitations

**Strengths:**
- Exact reproducibility
- Strong auditability
- Strong explainability
- Stable behavior
- Easier policy enforcement
- Bounded resource usage
- Safe enterprise adoption
- Strong support for regulated environments

**Limitations:**
- Narrower exploration under uncertainty
- Slower adaptation in open stochastic environments
- Symbolic modeling overhead
- More effort to integrate messy real-world sources
- Less natural fit for high-noise, high-ambiguity tasks
- May require careful environment modeling to remain practical

### 20.6 Phased Implementation Roadmap

**Phase 1 — Deterministic Kernel:**
- Symbolic state
- Operator model
- Invariant checker
- Deterministic planner
- Working memory
- Canonical snapshots

**Phase 2 — Typed Memory System:**
- Task-state memory
- Semantic memory
- Procedural memory
- Negative memory
- Archive log

**Phase 3 — Learning Engine:**
- Positive/negative rule induction
- Counterexamples
- Consolidation
- Promotion pipeline
- Rollback

**Phase 4 — Governance and Observability:**
- Full trace logging
- Rule classes
- Migrations
- Cross-machine replay tests
- Policy integration

**Phase 5 — Advanced Capability:**
- Richer partial observability support
- Advanced procedures
- Macro compilation
- Deterministic distributed orchestration if needed

---

# Part III: Non-Deterministic Self-Learning Agents (NDSLA)

## 21. Non-Deterministic Self-Learning Agents (NDSLA)

### 21.1 Definition

A **Non-Deterministic Self-Learning Agent (NDSLA)** is a **persistent, adaptive, uncertainty-aware agentic system** that improves over time by learning from memory, feedback, planning outcomes, tool interactions, and environment changes, while accepting stochasticity, approximation, and partial observability as native properties of real-world operation. Its central design challenge is balancing **plasticity, stability, safety, explainability, and efficiency**.

Unlike DSLA, the NDSLA does not insist on exact replayability as the central systems property. Instead, it aims for:
- Robust behavior under uncertainty
- Adaptive performance over time
- Strong average-case and distributional performance
- Memory-augmented learning
- Policy evolution
- Safe but flexible self-improvement
- Effective operation in open-world and partially observed environments

### 21.2 Suitable Domains

NDSLA is especially appropriate where:
- Observations are noisy or incomplete
- The environment is dynamic
- Exact transition models are unavailable
- Probabilistic models are useful
- Exploration is necessary
- Large-scale retrieval is needed
- A foundation model or stochastic controller is central
- The system improves through runtime traces, skill accumulation, and feedback loops

Typical NDSLA-aligned domains include:
- General-purpose assistants
- Browser agents
- Coding agents
- Research agents
- Embodied agents and robotics
- Multi-step enterprise copilots
- Web-navigation agents
- Adaptive workflow agents
- Multi-agent systems that learn from outcomes

### 21.3 Foundational Philosophy

#### 21.3.1 Uncertainty is Native, Not Exceptional

The NDSLA begins from a different premise than DSLA:

Many real-world environments are:
- Partially observed
- Non-stationary
- Noisy
- Ambiguous
- Too large for exact symbolic modeling
- Too dynamic for fixed deterministic rule systems alone

Therefore the system must represent:
- Uncertainty
- Soft preferences
- Incomplete state
- Risk
- Probabilistic action consequences
- Approximate knowledge

#### 21.3.2 Self-Learning as Continuous Adaptation

NDSLA sees intelligence as not just inference, but adaptation.

The agent should improve by learning from:
- Successes
- Failures
- Human feedback
- Episodic traces
- Memory reuse
- Skill induction
- Retrieval outcomes
- World-model errors
- Planning outcomes
- Distributional changes in environment or users

#### 21.3.3 Bounded Rationality Over Exact Optimality

The NDSLA does not attempt exact optimality everywhere. Instead, it tries to do well under:
- Finite compute
- Finite context
- Limited memory
- Limited time
- Imperfect models
- Incomplete observations

This is why heuristics, approximate retrieval, stochastic search, branch pruning, and learned value estimators are central to practical NDSLA systems.

---

## 22. NDSLA Core Properties

A mature NDSLA usually exhibits the following properties.

### 22.1 Controlled Stochasticity or Approximation

Some portion of the runtime loop may be:
- Sampled
- Probabilistic
- Approximate
- Heuristic
- Value-estimated
- Uncertainty-calibrated

This may occur in:
- Generation
- Action selection
- Retrieval
- Branch scoring
- Policy selection
- World-model inference
- Uncertainty-aware planning

### 22.2 Persistence Across Time

A self-learning agent is only meaningful if it retains useful change across sessions or tasks.

Persistence may occur through:
- Episodic memory
- Semantic knowledge
- Skill libraries
- Learned retrieval policy
- User profiles
- World-model refinement
- Prompt/program evolution
- Parameter adaptation

### 22.3 Non-Stationary Behavior

The system may change over time in:
- Memory contents
- Tool-use strategy
- Skill set
- Prompt policy
- Retrieval behavior
- Planner heuristics
- Model adapters or weights

This flexibility is powerful, but it also creates:
- Regression risk
- Forgetting risk
- Alignment drift
- Reproducibility challenges

### 22.4 Evaluation Dependence

Because the system changes over time, it must be continuously evaluated.

You cannot assume:
- Stable behavior
- Monotonic improvement
- Retained capabilities
- Safe skill evolution
- Clean retrieval behavior

---

## 23. NDSLA Six-Plane Architecture

A richer architecture than DSLA is usually needed. The six planes are:

1. **Interaction Plane** - External world touchpoints
2. **Control Plane** - Orchestration and decision-making
3. **Memory Plane** - Multi-level memory systems
4. **World-Model Plane** - Predictive environment representation
5. **Learning Plane** - Adaptation mechanisms
6. **Safety and Evaluation Plane** - Governance and monitoring

---

## 24. NDSLA Interaction Plane

The interaction plane is where the agent touches the external world.

### 24.1 Responsibilities

- User interaction
- Environment observation
- Tool invocation
- Artifact handling
- Permissions and approval collection
- Event intake
- Multimodal input normalization
- Connector interactions

### 24.2 Properties

This plane often includes:
- Browsers
- APIs
- File systems
- External data stores
- Enterprise tools
- Robotics interfaces
- Simulation environments
- Human feedback channels

### 24.3 Why This Matters

In NDSLA, the interaction plane is often noisy and partially reliable. Therefore:
- Uncertainty enters here
- Provenance must begin here
- Permissions should be enforced here
- Memory poisoning defenses begin here
- Environment non-stationarity is first observed here

---

## 25. NDSLA Control Plane

The control plane is the orchestration policy.

### 25.1 Responsibilities

- Task decomposition
- Routing
- Branch control
- Action selection
- Retry policy
- Fallback selection
- Handoff coordination
- Stopping decisions
- Uncertainty-aware planning integration

### 25.2 Internal Subcomponents

A mature NDSLA control plane may contain:
- Planner
- Tool router
- Retrieval orchestrator
- Branch manager
- Error-recovery policy
- Skill selector
- Context budget manager
- Confidence or uncertainty manager
- Human-escalation policy

### 25.3 Control vs Model

In many production agents, the control plane is not just "the model." It is a workflow structure around the model that determines:
- Whether retrieval occurs
- Which tools are tried
- Whether a plan is expanded or collapsed
- Whether to ask the user a clarifying question
- When to stop
- When to defer or abstain

---

## 26. NDSLA Memory Plane

Memory is the center of practical self-learning.

### 26.1 Design Goals

A strong NDSLA memory system should:
- Preserve useful experience
- Suppress noise
- Expose provenance
- Support multiple read modes
- Evolve over time
- Avoid uncontrolled growth
- Separate transient and durable memory
- Support task continuity
- Support value-aware retrieval

### 26.2 Working Memory

Working memory contains:
- Current conversation/task context
- Active subgoals
- Current branch
- Recently retrieved memory
- Current tool outputs
- Unresolved questions
- Short-lived reasoning artifacts

It is optimized for immediacy, not long-term durability.

### 26.3 Session Memory

Session memory persists across a thread or long task.

It may contain:
- Current plan
- Unfinished substeps
- Checkpoint state
- Temporary conclusions
- Pending tool outputs
- User-specific temporary preferences
- Interaction-local assumptions

### 26.4 Episodic Memory

Episodic memory stores structured past experiences.

Useful episode contents:
- Task type
- Context
- Actions taken
- Tools used
- Intermediate observations
- Outcome
- Success/failure
- Time cost
- Errors encountered
- Lessons extracted

Episodes are especially useful for:
- Analogical recall
- Troubleshooting
- Learning value of memories
- Reflective improvement
- Skill induction

### 26.5 Semantic Memory

Semantic memory stores generalized knowledge:
- Facts
- Schemas
- Ontologies
- Abstractions
- User profile knowledge
- Environment knowledge
- Domain truths
- Normalized patterns

### 26.6 Procedural Memory

Procedural memory stores:
- Workflows
- Playbooks
- Reusable strategies
- Executable skills
- Templates
- Domain procedures
- Troubleshooting chains

### 26.7 Value Memory

This is one of the distinguishing memory layers of advanced NDSLA systems.

Value memory may store:
- Expected usefulness of a memory
- Success contribution of a procedure
- Estimated risk of an action class
- Cost-benefit estimates
- Regret history
- Branch payoff estimates

### 26.8 Archive and Traces

This includes:
- Full traces
- Tool logs
- Artifacts
- Historical prompts or policy versions
- Outputs
- Eval results
- Failure cases
- Human review annotations

---

## 27. NDSLA Retrieval Stack

Similarity alone is insufficient. A mature retrieval stack should have multiple layers.

### 27.1 Candidate Generation

Generate broad candidates using one or more of:
- Dense vector search
- Sparse lexical search
- Graph traversal
- Structured filters
- Procedural lookup
- Temporal indexing
- Cache/prefix lookup

### 27.2 Contextual Filtering

Filter candidates using:
- Task type
- Current user
- Environment
- Recency
- Safety/trust level
- Domain constraints
- Active goals

### 27.3 Utility and Risk Reranking

Candidates should then be scored by:
- Likely usefulness
- Likely correctness
- Success history
- Trust level
- Relevance
- Risk of misapplication
- Cost of invoking/reading them

### 27.4 Reader/Formatter Phase

The selected memory must then be transformed into a usable representation:
- Summary
- Structured constraints
- Worked example
- Procedure invocation
- Evidence list
- Branch hint
- Tool parameter recommendation

---

## 28. NDSLA World-Model Plane

If the agent must generalize across multi-step tasks, some predictive model of the world is effectively unavoidable.

### 28.1 What the World Model May Contain

- Transition expectations
- Hidden-state estimates
- Causal dependencies
- User behavior patterns
- Tool behavior priors
- System topology
- Resource costs
- Latency expectations
- Failure correlations
- Environment drift signals

### 28.2 Forms of World Model

The world model may be:
- Symbolic
- Neural
- Hybrid
- Local to a task
- Global across the platform
- Static bootstrapped
- Continually refined

### 28.3 Use Cases

A world model helps with:
- Planning
- Risk estimation
- Information gathering
- Branch pruning
- Anomaly detection
- Skill reuse
- Faster recovery
- Counterfactual reasoning

---

## 29. NDSLA Learning Plane

The learning plane governs how the NDSLA changes over time.

### 29.1 Learning Channels

A strong NDSLA should distinguish between update channels rather than treating all learning as one thing.

Possible channels:
- Memory-only updates
- Reflection updates
- Semantic extraction
- Skill induction
- Retrieval-policy learning
- Branch scoring/value learning
- Prompt/program changes
- Adapter fine-tuning
- Full parameter updates

### 29.2 Memory-Only Learning

This is the safest and often most immediately useful channel.

It includes:
- Writing better episodes
- Summarizing traces
- Extracting lessons
- Updating semantic memory
- Generating procedures
- Storing failures and anti-patterns

### 29.3 Reflection-Based Learning

The system may generate explicit reflections about:
- What failed
- What should have been tried first
- What assumptions were wrong
- What procedure now seems reusable
- What signals were misleading

These reflections can later guide decisions.

### 29.4 Skill Induction

Repeated successful traces may be abstracted into skills.

A skill can include:
- Invocation conditions
- Expected environment
- Ordered steps
- Required tools
- Fallback branches
- Expected outputs
- Success conditions
- Historical success rate

### 29.5 Retrieval-Policy Learning

This is where the system learns:
- What to retrieve
- When to retrieve
- Which memory type to prefer
- How many memories to include
- When memory is more harmful than helpful

### 29.6 World-Model Learning

The system may refine:
- Transition expectations
- Causal dependencies
- Task decomposition priors
- Failure-mode models
- Latency and resource models

### 29.7 Parameter Adaptation

High-risk but sometimes valuable:
- Adapter tuning
- Policy fine-tuning
- Small continual updates
- Post-training from accumulated traces
- Reward model refinement

These require stronger gates.

---

## 30. NDSLA Planning Under Uncertainty

Planning is much harder here than in DSLA.

### 30.1 Why

The planner may not know:
- Exact current world state
- Exact action effects
- Whether tool outputs are complete
- Whether the environment has drifted
- Whether observations are trustworthy
- Whether a branch will remain feasible

### 30.2 Planner Capabilities

A strong NDSLA planner should support:
- Stochastic branching
- Hypothesis-conditioned plans
- Information-gathering actions
- Reversible experimentation
- Risk-aware stopping
- Budget-aware branch expansion
- Human escalation when uncertainty is high

### 30.3 Planning Artifacts

Planning should produce:
- Candidate plans
- Branch values
- Uncertainty notes
- Information gaps
- Fallback options
- Tool and memory dependencies
- Estimated cost and benefit

---

## 31. NDSLA Exploration

NDSLA exploration is both powerful and dangerous.

### 31.1 Exploration Families

- Random or temperature-based action exploration
- Posterior or belief-based exploration
- Optimistic exploration
- Novelty search
- Curiosity-driven exploration
- Curriculum-driven exploration
- Goal-conditioned exploration
- Human-approved exploration in sensitive domains

### 31.2 Exploration Constraints

Exploration should be governed by:
- Risk budget
- Domain policy
- Environment criticality
- User approval requirements
- Cost budget
- Regression sensitivity
- Eval coverage

### 31.3 Why This Matters

Without exploration:
- The agent stagnates
- Skill growth slows
- Adaptation to new conditions suffers

Without control:
- The agent destabilizes
- Costs explode
- Unsafe behavior emerges
- Trust collapses

---

## 32. NDSLA Continual Learning

This is one of the hardest engineering problems in NDSLA.

### 32.1 Failure Modes

- Catastrophic forgetting
- Stale memory use
- Overfitting to recent data
- Alignment drift
- Loss of previously acquired skills
- Retrieval pollution
- Inability to distinguish old from current truths
- Value model corruption
- Procedural brittleness

### 32.2 Mitigation Strategies

- Replay/rehearsal
- Regularization
- Modular adaptation
- Sparse or adapter updates
- External memory over weight changes
- Skill libraries
- Versioned prompts/policies
- Context routing
- Eval-gated promotion
- Deprecation workflows
- Environment-aware memory versioning

### 32.3 Why External Memory Matters

External memory can preserve useful knowledge without forcing all adaptation into weights, making:
- Rollback easier
- Audits easier
- Forgetting less severe
- Incremental improvement safer

---

## 33. NDSLA Safety and Governance

Because NDSLA evolves over time, its governance must also be continuous.

### 33.1 Risks Unique or Amplified in NDSLA

- Memory poisoning
- Unsafe skill induction
- Policy drift
- Regression through retrieval updates
- Hidden prompt evolution causing behavioral change
- High-risk hallucinated actions under uncertainty
- Silent overconfidence
- Adaptation to bad feedback
- Privilege escalation via tool misuse

### 33.2 Governance Controls

A mature NDSLA governance stack should include:
- Policy-as-code
- Tool permissions
- Provenance on all memory writes
- Trust scores on sources
- Skill promotion gates
- Versioned prompts/programs
- Rollback capability
- Separation of experimental and production policies
- Human approval for critical changes
- Sensitive data tagging and retention controls

### 33.3 Safety/Eval Plane Responsibilities

This plane should:
- Score traces
- Detect regressions
- Monitor for policy violations
- Review self-modification proposals
- Quarantine suspicious memory updates
- Certify or reject promoted skills
- Enforce rollout strategies

### 33.4 Evaluation Framework

NDSLA evaluation must be richer than deterministic replay tests.

**Distributional evaluation:**
Because outputs may vary, evaluate across runs:
- Average success
- Tail failures
- Worst-case cost
- Variance in quality
- Variance in latency
- Stability of tool usage
- Reproducibility within acceptable statistical bounds

**Task metrics:**
- Success rate
- Partial credit
- Completion time
- Token/tool cost
- User satisfaction
- Recovery after error
- Handoff correctness

**Memory metrics:**
- Recall@k
- Utility of retrieved memories
- False positive retrieval rate
- Stale memory rate
- Skill reuse rate
- Semantic write precision
- Value-model calibration

**Learning metrics:**
- Improvement over time
- Adaptation speed
- Forgetting rate
- Regression frequency
- Promotion acceptance rate
- Rollback frequency
- Retained capability score
- Transfer to new tasks

**Trace metrics:**
- Tool-call correctness
- Number of wasted actions
- Branch efficiency
- Invalid action attempts
- Number of clarifying questions
- Recovery path quality
- Time spent in dead-end branches

**Safety metrics:**
- Policy violation count
- Unsafe action attempt count
- Unauthorized tool usage
- Privacy leakage
- Prompt injection susceptibility
- Poisoned memory acceptance rate
- Risky memory promotion rate

### 33.5 Capability Taxonomy

A comprehensive NDSLA capability stack should include:
- Conversation and interaction
- Tool use
- Environment navigation
- Episodic memory
- Semantic memory
- Procedural skill storage
- Value-aware retrieval
- Self-reflection
- Self-critique
- Uncertainty estimation
- World-model updating
- Branching and rollback
- Multi-step planning
- Long-task continuity
- Collaboration/handoff
- Safe abstention
- Adaptive personalization
- Controlled self-modification

Different NDSLA systems may emphasize different subsets.

### 33.6 Phased Roadmap

**Phase 1 — Stable Non-Learning Agent with Traces:**
Start with:
- Strong base policy/model
- Full trace capture
- Tool correctness testing
- Policy enforcement
- Eval harness

**Phase 2 — Structured Memory:**
Add:
- Episodic memory
- Semantic memory
- Session continuity
- Provenance and trust tags
- Retrieval metrics

**Phase 3 — Skill Induction and Reflection:**
Add:
- Reflection buffer
- Procedure extraction
- Skill library
- Replayable task cases

**Phase 4 — Utility-Aware Retrieval:**
Add:
- Value/risk reranking
- Memory usefulness learning
- Adaptive retrieval budget selection

**Phase 5 — World-Model and Uncertainty-Aware Planning:**
Add:
- Branch scoring
- Information-gathering actions
- Hidden-state reasoning
- Better environment predictions

**Phase 6 — Gated Self-Modification:**
Add:
- Prompt/policy evolution
- Retrieval-policy learning
- Adapter or model updates where justified
- Rollout and rollback gates

### 33.7 Strengths and Limitations

**Strengths:**
- Strong adaptability
- Natural fit for messy real-world environments
- Useful under partial observability
- Better ability to explore and generalize
- Easier fit for foundation-model-centered systems
- Strong support for personal assistants, coding agents, web agents, research agents
- Can improve from runtime interaction without fully deterministic modeling

**Limitations:**
- Harder debugging
- Weaker exact reproducibility
- Higher governance burden
- Greater regression risk
- Greater forgetting risk
- More evaluation complexity
- More challenging audits
- More complex safety certification
- Possible overreliance on approximate retrieval or latent behavior

---

# Part IV: Transition and Hybrid Architectures

## 34. Transition from NDSLA to DSLA

### 34.1 Objective

The transition goal is not to kill adaptability. It is to progressively shrink the portion of the system that is allowed to remain stochastic, approximate, or weakly governed.

The guiding transformation is:

**Exploration → Structure → Validation → Promotion → Deterministic Execution**

### 34.2 Core Thesis

The transition is **not**:
- Replacing all stochastic systems with hard-coded rules
- Removing all probabilistic reasoning overnight
- Discarding LLMs or exploration entirely

The transition **is**:
- Externalizing learning
- Structuring memory
- Extracting reusable skills
- Constraining outputs
- Introducing verification
- Promoting repeated success into validated deterministic artifacts
- Building a deterministic authoritative layer

### 34.3 Entropy Reduction View

The transition can be understood as reducing entropy across four dimensions:

1. Output entropy
2. Memory entropy
3. Planning entropy
4. Learning entropy

The goal is not to collapse all entropy to zero immediately. The goal is to reduce it where external commitment, governance, and replayability matter most.

---

## 35. Seven-Stage Transition Model

### Stage 0 — Pure NDSLA

**Properties:**
- Stochastic or approximate output generation
- Weakly structured memory
- Exploratory execution
- Limited deterministic constraints
- Limited replayability

**Exit criterion:**
- Full trace capture exists

### Stage 1 — Externalized Memory

Move learning out of hidden state into:
- Episodic memory
- Semantic memory
- Reflection logs
- Task-state stores

**Exit criterion:**
- Learning artifacts are inspectable and versionable

### Stage 2 — Skill Extraction

Mine traces to produce:
- Candidate procedures
- Operator sequences
- Reusable workflows
- Validation chains

**Exit criterion:**
- Repeated successful patterns are available as first-class candidate skills

### Stage 3 — Retrieval Hardening

Replace diffuse retrieval with deterministic fallback strategy:
1. Exact working-memory match
2. High-threshold semantic match
3. Goal-matched procedural lookup
4. Deterministic fallback policy

**Exit criterion:**
- Retrieval behavior is stable enough to evaluate repeatably

### Stage 4 — Constrained Outputs

Wrap generation with:
- Schemas
- Typed outputs
- Grammar constraints
- Deterministic validators

**Exit criterion:**
- Invalid outputs never directly cause action

### Stage 5 — Verification Layer

Every candidate action must pass:
- Schema validation
- Invariant checks
- Policy enforcement
- Environment assumptions validation

**Exit criterion:**
- Unsafe actions can no longer directly reach the environment

### Stage 6 — Hybrid Architecture

Exploratory layer proposes. Deterministic layer validates, selects, and executes.

**Exit criterion:**
- All externally committing actions pass through deterministic authority

### Stage 7 — Deterministic Core

The real-world execution path is fully deterministic. Exploratory components remain available for:
- Sandbox search
- Offline learning
- Simulation
- Candidate generation
- Innovation outside the authority boundary

---

## 36. Promotion Pipeline

Promotion turns learned artifacts into authoritative ones.

A proper pipeline includes:
1. Candidate discovery
2. Schema validation
3. Repetition threshold
4. Invariant and policy checks
5. Regression testing
6. Promotion decision
7. Versioning
8. Rollback readiness
9. Monitoring after promotion

Candidate types may include:
- Rules
- Procedures
- Retrieval policies
- Skill bundles
- Semantic assertions
- Operator macros
- Negative patterns

### 36.1 Skill Lifecycle

A skill should move through:
1. Discovery
2. Repetition
3. Abstraction
4. Validation
5. Promotion
6. Monitoring
7. Deprecation or rollback

This lifecycle is critical because not all repeated behavior should become authoritative.

---

## 37. Verification Layer

The verification layer is the most important bridge between NDSLA and DSLA.

It should check:
- Output format
- Tool permissions
- Invariants
- Policy rules
- Task-local constraints
- Environment assumptions
- Memory provenance and trust if relevant

The generator may remain non-deterministic, but the verifier must be deterministic and authoritative.

### 37.1 Deterministic Boundary

All real-world actions that:
- Mutate external systems
- Publish or approve irreversible content
- Write high-trust memory
- Invoke sensitive tools
- Trigger regulated or high-risk operations

Must pass through the deterministic layer.

The closer a behavior is to real-world commitment, the more deterministic it should become.

---

## 38. Unified Hybrid Architecture

### 38.1 Two-Tier Hybrid Model

A strong production architecture often benefits from two distinct tiers:

#### Tier A — Exploratory Layer

**Characteristics:**
- Stochastic or approximate
- Retrieval-heavy
- Hypothesis-generating
- World-model-rich
- Reflection and self-learning active
- Not authoritative on its own

**Responsibilities:**
- Broad retrieval
- Candidate plan generation
- Memory writing
- Skill discovery
- Ambiguity handling
- Simulation/sandbox exploration

#### Tier B — Deterministic Authoritative Core

**Characteristics:**
- Deterministic
- Bounded-memory for online authority
- Invariant-gated
- Policy-governed
- Replayable
- Versioned
- Rollback-ready

**Responsibilities:**
- Canonical state maintenance
- Operator selection
- Deterministic execution
- Policy enforcement
- Trusted memory promotion
- Audit logging
- Externally committing actions

### 38.2 Interaction Between Tiers

The canonical flow is:

1. Input enters system
2. Exploratory layer proposes candidates
3. Deterministic core validates candidates
4. Deterministic core selects and executes
5. Environment/tool response is recorded
6. Traces flow back to exploratory layer
7. Repeated success may produce promotion candidates
8. Promoted artifacts move into deterministic core

### 38.3 Benefits of This Split

- Preserves learning power
- Preserves safe authority
- Improves auditability
- Reduces risk of direct unsafe generation
- Supports gradual hardening
- Allows multi-speed evolution

---

# Part V: Shared Systems

## 39. Two-Tier Hybrid Model (Continued)

The two-tier model represents the recommended architecture for production systems that need both adaptability and safety. This architecture is detailed in [Section 38](#38-unified-hybrid-architecture) above.

---

## 40. Memory Systems and Multi-Level Governance

### 40.1 Unified Memory Taxonomy

A robust agent platform should treat memory across three dimensions:

**Functional role:**
- Working
- Task-state
- Episodic
- Semantic
- Procedural
- Negative
- Archive
- Cache
- Value

**Representation:**
- Symbolic
- Vector
- Graph
- Probabilistic
- Textual summary
- Executable procedure

**Backend:**
- Relational store
- Vector index
- Graph database
- Event log
- Cache
- Checkpoint store
- Document store

### 40.2 Memory Lifecycle

A healthy lifecycle includes:
- Ingestion
- Normalization
- Provenance tagging
- Indexing
- Retrieval
- Reading/formatting
- Use
- Evaluation of utility
- Consolidation
- Decay or deprecation
- Archival retention

### 40.3 Retention and Decay

Retention should not rely only on age. It should consider:
- Recency
- Frequency
- Task contribution
- Provenance quality
- Stability of truth
- Privacy/security class
- Policy class

Different memory classes should use different retention strategies.

### 40.4 Consolidation

Consolidation is not just summarization. It should include:
- Semantic extraction
- Contradiction detection
- Fact versioning
- Procedure abstraction
- Duplicate merging
- Conflict-aware knowledge integration
- Promotion candidate generation

### 40.5 Reading Strategy

Retrieval alone is insufficient. Memory must be prepared for use.

Reading strategies may include:
- Summary
- Evidence list
- Graph neighborhood
- Procedure call
- Contradiction report
- Compressed observation set
- Trusted-only answer view
- Time-aware slice

---

## 41. Retrieval, Reading, and Knowledge Integration

### 41.1 Retrieval Families

A unified architecture should support:
- Sparse lexical retrieval
- Dense vector retrieval
- Hybrid sparse+dense retrieval
- Graph traversal
- Temporal retrieval
- Procedural lookup
- Cache lookup
- Utility-aware reranking

### 41.2 Why Retrieval Alone Is Not Enough

The supporting research notes emphasize that:
- Retrieval can succeed while reading still fails
- Similar memory is not always useful memory
- Long histories create cost and latency pressure
- Graph indexing can improve global synthesis but increases cost
- Compression-based memory can improve stability and caching but may be lossy

A mature design should therefore separate:
1. Storage
2. Indexing
3. Retrieval
4. Reading/formatting
5. Verification
6. Use

### 41.3 Graph-Based Augmentation

Graph-structured memory is especially useful for:
- Multi-hop reasoning
- Dependency analysis
- Impact analysis
- Global corpus synthesis
- Causal or relational exploration

However, graph construction and maintenance are expensive and should be justified by use case.

### 41.4 Compression-First Memory

Compression/observational memory is especially useful for:
- Long-running sessions
- Tool-heavy agents
- Cacheable stable context
- Bounded context management

**Trade-off:**
- Lower token cost and latency
- Possible loss of exact verbatim detail

---

## 42. Learning Channels and Update Governance

### 42.1 Learning Channels by Risk Level

**Lower-risk channels:**
- Episodic writes
- Semantic extraction
- Procedural extraction
- Reflection logs
- Negative knowledge formation
- Retrieval-policy metadata adjustments

**Medium-risk channels:**
- Reranker tuning
- Retrieval policy changes
- Prompt/program changes
- Planning heuristics changes
- Confidence threshold changes

**Higher-risk channels:**
- Adapter tuning
- Model fine-tuning
- Reward model updates
- Online parameter changes

### 42.2 Governance Expectations

Every nontrivial update channel should have:
- Provenance
- Schema checks
- Evaluation gates
- Rollback path
- Versioning
- Audit logging
- Risk classification

### 42.3 Memory-Only Learning as Preferred First Step

A repeated message across the drafts and research is that external memory updates often provide the best risk-adjusted value before more invasive model updates.

This is especially important in systems that aspire to deterministic authority later.

---

## 43. World Models, Uncertainty, and Causal Structure

### 43.1 Why World Models Matter

A powerful agent needs some representation of:
- How actions affect the environment
- What hidden state may exist
- What dependencies matter
- What likely outcomes or risks follow from actions

This can be:
- Symbolic
- Probabilistic
- Graph-based
- Neural
- Hybrid

### 43.2 Causal Structure

Strong agents benefit from explicit or implicit causal structure for:
- Debugging
- Planning
- Rollback
- Impact prediction
- Constraint discovery
- Explanation

In DSLA, causal structure should increasingly be explicit.  
In NDSLA, causal structure may begin implicit and later be extracted.

### 43.3 Uncertainty Handling

**DSLA handles uncertainty via:**
- Explicit hypotheses
- Possible-world sets
- Deferred commitment
- Deterministic verification

**NDSLA handles uncertainty via:**
- Probabilistic or heuristic inference
- Branch scoring
- Uncertainty estimates
- Adaptive exploration

**Hybrid systems** should let uncertain reasoning propose, but let deterministic mechanisms commit.

---

## 44. Safety, Policy, Audit, and Compliance

### 44.1 Safety Principles

A production agent should:
- Not act beyond permission
- Not silently mutate policy-heavy systems
- Not trust unverified memory blindly
- Not promote unsafe behavior into authority
- Not retain sensitive data without retention controls
- Not allow raw generation to directly execute sensitive action

### 44.2 Policy Enforcement

Policy enforcement should be:
- Explicit
- Centralized or well-defined
- Deterministic for authoritative paths
- Testable
- Traceable
- Versioned

### 44.3 Auditability

Strong audit requires:
- Append-only event traces
- State transitions
- Memory writes
- Promotion decisions
- Tool calls
- Policy rejections
- Rollback records
- Provenance and source trust metadata

### 44.4 Privacy and Retention

Memory systems must explicitly handle:
- Sensitive data tagging
- Retention windows
- Redaction or minimization
- Provenance
- Access control
- Deletion or archival policy
- Safe replay constraints

---

## 45. Evaluation, Benchmarking, and Validation

### 45.1 Evaluation Should Be Plane-Specific

**Control plane metrics:**
- Task success
- Tool correctness
- Execution latency
- Invalid action rejection
- Adherence to deterministic mode where required

**Memory plane metrics:**
- Recall@k
- Usefulness of retrieved memory
- Contradiction rate
- Stale memory rate
- Cost per query
- Retrieval latency
- Memory growth
- Index rebuild cost

**Learning plane metrics:**
- Regression rate
- Rollback frequency
- Promotion precision
- Forgetting rate
- Adaptation rate
- Retained capability score
- Safety incident rate from learned changes

### 45.2 Benchmark Families

A robust benchmark map may include:
- Long-memory conversational benchmarks
- Multi-session memory tasks
- Long-context reasoning tasks
- Tool-using interactive benchmarks
- Web/navigation tasks
- Software engineering tasks
- Deterministic replay suites
- Policy-heavy workflow tests
- Bounded-memory stress tests
- Contradiction and update benchmarks

### 45.3 Determinism Tests

Especially for DSLA or hybrid authoritative layers:
- Same input replay test
- Cross-machine replay test
- Serialization equivalence test
- Trace divergence test
- Rule order stability test
- Promotion-preserved behavior test

### 45.4 Verification Tests

- Operator pre/post conditions
- Invariant fuzzing
- Policy denial tests
- Rollback correctness tests
- Negative knowledge enforcement tests
- Promotion regression tests

---

# Part VI: Implementation

## 46. Implementation and Deployment Strategy

### 46.1 Recommended Staged Implementation

**Phase 1 — Foundation:**
Build:
- Canonical state
- Operator model
- Deterministic validators
- Trace capture
- Typed memory schemas

**Phase 2 — Structured Memory:**
Add:
- Working/task-state memory
- Episodic memory
- Semantic memory
- Procedural memory
- Archive
- Retrieval instrumentation

**Phase 3 — Exploratory Learning:**
Add:
- Reflection
- Skill extraction
- Value-aware retrieval
- Retrieval evaluation
- Candidate generation pipelines

**Phase 4 — Promotion and Authority:**
Add:
- Invariant-gated promotion
- Deterministic validation layer
- Rollback and versioning
- Trusted authority boundary

**Phase 5 — Hybrid Hardening:**
Add:
- Split exploratory and authoritative layers
- Canary rollout
- Deterministic fuzzing
- Environment drift monitoring
- Periodic revalidation

### 46.2 Deployment Modes

Possible deployment modes:
- Pure DSLA
- Pure NDSLA
- Hybrid shadow mode
- Hybrid active mode
- Offline exploratory / online deterministic
- Simulation-only exploratory mode for candidate discovery

---

## 47. Reference Schemas and Formal Artifacts

### 47.1 Canonical Memory Item Schema

```json
{
  "id": "uuid",
  "type": "episode | fact | skill | cache_item | state_checkpoint | negative_pattern",
  "content": {
    "text": "string",
    "structured": {}
  },
  "time": {
    "event_time": "RFC3339",
    "write_time": "RFC3339"
  },
  "provenance": {
    "source": "user | tool | document | inference | evaluation",
    "trace_id": "string",
    "citations": [],
    "policy_tags": []
  },
  "validity": {
    "confidence": 0.0,
    "version": "string",
    "last_verified": "RFC3339",
    "ttl_seconds": 0
  },
  "links": [
    { "rel": "supports | contradicts | derived_from | promoted_to", "target": "uuid" }
  ]
}
```

### 47.2 Forbidden Pattern Schema

```json
{
  "id": "fp_001",
  "context": {
    "entity_type": "Document",
    "user_role": "viewer"
  },
  "action": "delete",
  "reason": "policy_violation",
  "specificity": 0.9,
  "counter": 7,
  "version": "v3"
}
```

### 47.3 Candidate Promotion Record

```json
{
  "candidate_id": "cand_123",
  "artifact_type": "procedure",
  "origin": "trace_mining",
  "successful_repetitions": 5,
  "validation_status": "passed",
  "regression_suite": "green",
  "promoted_version": "proc_v12",
  "rollback_parent": "proc_v11"
}
```

---

## 48. Comparative Analysis and Decision Guidance

### 48.1 When to Choose DSLA

Choose DSLA when:
- Policy and safety dominate
- Exact replay matters
- Deterministic operators are available
- Strong audit is mandatory
- Regulation matters
- Bounded authoritative behavior is more important than broad exploration

### 48.2 When to Choose NDSLA

Choose NDSLA when:
- Environment is uncertain and open-world
- Exploration matters
- Foundation-model reasoning is central
- Exact replay is not the top priority
- Broad adaptability outweighs strict determinism

### 48.3 When to Choose Hybrid

Choose hybrid when:
- You need open-world discovery **and** safe authority
- You want to learn from traces but execute safely
- You want deterministic commitment layered over adaptive proposal
- You want a realistic path from research-grade agent to production-grade agent

### 48.4 DSLA vs NDSLA at a Glance

| Dimension | DSLA | NDSLA |
|-----------|------|-------|
| Core authority | Deterministic | Stochastic/approximate allowed |
| Replayability | Exact or near-exact | Distributional, not exact |
| Primary strength | Auditability, safety, stable reasoning | Adaptivity, open-world robustness |
| Memory style | Strongly typed, bounded, explicit | Multi-level, value-aware, adaptive |
| Exploration | Systematic and deterministic | Stochastic or heuristic exploration |
| Learning | Explicit rule/procedure induction | Memory, policy, skill, value, and sometimes weight learning |
| Best fit | High-assurance environments | Open, ambiguous, noisy environments |
| Main risk | Rigidity, limited openness | Drift, forgetting, regression, safety burden |

### 48.5 Combined Perspective

DSLA and NDSLA are not mutually exclusive enemies. They represent two ends of an architectural spectrum.

A serious platform may eventually support both:
- A **deterministic authoritative core** for high-assurance actions
- A **non-deterministic exploratory or assistive layer** for broad search, adaptation, and uncertain reasoning

In that hybrid model:
- DSLA governs what must be stable, auditable, and safe
- NDSLA explores, retrieves, hypothesizes, and adapts where uncertainty is unavoidable

That hybrid direction is often the most practical path for real systems.

---

## 49. Open Questions and Future Research

Important unresolved questions include:
- How to quantify "degree of determinism"
- How to formally verify promoted skills at scale
- How to manage drift in promoted deterministic procedures
- How to reconcile graph memory, compression-first memory, and utility-aware retrieval in one system
- How to govern shared memory in multi-agent systems
- How to preserve privacy while enabling long-horizon memory
- How much adaptation should remain outside the authoritative core in different domains
- How to benchmark hybrid systems fairly

---

## 50. Final Synthesis

This document leads to a clear architectural conclusion:

A robust self-learning agent platform should not treat deterministic and non-deterministic designs as mutually exclusive camps. They are complementary regimes.

- **DSLA** provides the discipline of explicit state, bounded authority, deterministic policy-governed execution, and replayable learning.
- **NDSLA** provides the flexibility of uncertainty-aware adaptation, memory-driven exploration, broad retrieval, skill induction, and open-world competence.
- The **transition architecture** provides the engineering bridge: externalize learning, structure memory, extract skills, verify aggressively, and promote repeated success into a deterministic authoritative core.

The highest-value production pattern is usually:

**Non-deterministic discovery + deterministic commitment**

Or, stated differently:

**Explore broadly, remember structurally, verify strictly, execute safely.**

---

## Document Changelog

| Date | Change | Author |
|------|--------|--------|
| 2026-04-05 | Merged three source documents into unified specification | AI Assistant |
| 2026-04-05 | Eliminated redundancy while preserving all information | AI Assistant |
| 2026-04-05 | Standardized terminology across DSLA, NDSLA, and transition sections | AI Assistant |
| 2026-04-05 | Added comprehensive table of contents and cross-references | AI Assistant |

## Source Documents

This merged document consolidates:
1. **Unified_Self_Learning_Agents_Spec_Final.md** (base comprehensive specification)
2. **DSLA_NDSLA_Full_Detailed_Spec.md** (detailed implementation specifications)
3. **NDSLA_to_DSLA_Transition_Full_Spec.md** (transition methodology - content integrated throughout)

---

**End of Document**

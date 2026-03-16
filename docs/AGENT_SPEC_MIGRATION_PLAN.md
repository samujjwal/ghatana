# Agent Specification Unification Plan

> **Last Updated:** 2026-03-15 (v3.0.0 — Complete Cut-Over, No Backward Compatibility)

## Executive Summary

This document provides a comprehensive analysis of all agent definitions across the Ghatana codebase and a concrete migration plan to unify them under the new agent specification format defined in `docs/agent-spec.md`.

**Migration Strategy: COMPLETE CUT-OVER** — All agent definitions, Java runtime code, YAML parsers, registries, catalogs, and test fixtures are migrated atomically. No deprecated code, no backward compatibility shims, no dual-format support. Old format files are deleted after migration.

**Scope**: 636+ agents across 4 major product areas

- **Platform**: 20+ core agents (event-processing, monitoring, governance, security)
- **AEP**: 10+ operator agents (ingestion, routing, transformation)
- **Data Cloud**: 10+ data-centric agents (orchestration, quality, lineage)
- **YAPPC**: 636 agents across 10 domains (orchestrators, capabilities, task-agents, micro-agents)

---

## 1. Current State Analysis

### 1.1 Format Comparison Matrix

| Section                | Target Spec (agent-spec.md)                                                         | Platform Catalog                    | AEP Operators                     | Data Cloud                    | YAPPC                             |
| ---------------------- | ----------------------------------------------------------------------------------- | ----------------------------------- | --------------------------------- | ----------------------------- | --------------------------------- |
| **Metadata**           |                                                                                     |                                     |                                   |                               |
| id                     | ✅ Required string                                                                  | ✅ id                               | ✅ id                             | ✅ id                         | ✅ id                             |
| name                   | ✅ Required string                                                                  | ✅ name                             | ✅ name                           | ✅ name                       | ✅ name                           |
| namespace              | ✅ Required string                                                                  | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| version                | ✅ Required string                                                                  | ✅ version                          | ✅ version                        | ✅ version                    | ✅ version                        |
| status                 | ✅ Enum (draft/active/deprecated/retired/suspended)                                 | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| owners                 | ✅ Required list                                                                    | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| tags                   | ✅ Optional list                                                                    | ✅ metadata.tags                    | ✅ metadata.tags                  | ✅ metadata.tags              | ✅ metadata.tags                  |
| summary                | ✅ Required string                                                                  | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| description            | ✅ Optional string                                                                  | ✅ description                      | ✅ description                    | ✅ description                | ✅ description                    |
| **Identity**           |                                                                                     |                                     |                                   |                               |
| agentType              | ✅ Enum (12 values: deterministic/rule-based/policy/pattern/probabilistic/planning/llm/hybrid/adaptive/composite/reactive/custom) | ❌ Missing (has `type` in metadata) | ❌ Missing (has `generator.type`) | ❌ Missing                    | ❌ Missing (has `generator.type`) |
| roles                  | ✅ Required list                                                                    | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| personas               | ✅ Optional list                                                                    | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| criticality            | ✅ Enum (low/medium/high/mission-critical)                                          | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| autonomyLevel          | ✅ Enum (advisory/assisted/semi-autonomous/autonomous)                              | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| determinismGuarantee   | ✅ Enum (full/config-scoped/none/eventual) **(NEW)**                                | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| stateMutability        | ✅ Enum (stateless/local-state/external-state/hybrid-state) **(NEW)**               | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| failureMode            | ✅ Enum (fail-fast/retry/fallback/skip/dead-letter/circuit-breaker) **(NEW)**       | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| **Purpose Model**      |                                                                                     |                                     |                                   |                               |
| mission                | ✅ Required string                                                                  | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| goals                  | ✅ Required list                                                                    | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| nonGoals               | ✅ Optional list                                                                    | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| successCriteria        | ✅ Required list                                                                    | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| **Scope**              |                                                                                     |                                     |                                   |                               |
| domains                | ✅ Required list                                                                    | ✅ metadata.domain                  | ✅ metadata.domain                | ✅ metadata.domain            | ✅ metadata.domain                |
| supportedEntities      | ✅ Optional list                                                                    | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| boundaries             | ✅ Required list                                                                    | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| **Capabilities**       |                                                                                     |                                     |                                   |                               |
| declaredCapabilities   | ✅ Required list (rich structure)                                                   | ✅ capabilities (simple list)       | ✅ capabilities (simple list)     | ✅ capabilities (simple list) | ✅ capabilities (simple list)     |
| capabilityDependencies | ✅ Optional list                                                                    | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| prohibitedCapabilities | ✅ Optional list                                                                    | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| **Reasoning Profile**  |                                                                                     |                                     |                                   |                               |
| primaryReasoner        | ✅ Required string                                                                  | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| reasonerPortfolio      | ✅ Required list                                                                    | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| reasoningStrategy      | ✅ Optional string                                                                  | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| determinismProfile     | ✅ Required object                                                                  | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| confidenceModel        | ✅ Optional object                                                                  | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| **Execution Model**    |                                                                                     |                                     |                                   |                               |
| invocationModes        | ✅ Required list                                                                    | ❌ Missing                          | ✅ aep.operatorType               | ❌ Missing                    | ❌ Missing                        |
| lifecycleStates        | ✅ Required list                                                                    | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| concurrencyModel       | ✅ Optional object                                                                  | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| retryPolicy            | ✅ Optional object                                                                  | ❌ Missing                          | ✅ aep.reliability.maxRetries     | ❌ Missing                    | ❌ Missing                        |
| timeoutPolicy          | ✅ Optional object                                                                  | ❌ Missing                          | ❌ Missing                        | ✅ resources.timeout          | ✅ performance.timeout_ms         |
| compensationPolicy     | ✅ Optional object                                                                  | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| **Interfaces**         |                                                                                     |                                     |                                   |                               |
| inputs                 | ✅ Required list (rich schema)                                                      | ✅ io.inputs                        | ❌ Missing tools only             | ❌ Missing routing only       | ✅ routing.input_types            |
| outputs                | ✅ Required list (rich schema)                                                      | ✅ io.outputs                       | ❌ Missing                        | ❌ Missing                    | ✅ routing.output_types           |
| eventsConsumed         | ✅ Optional list                                                                    | ✅ aep.inputEventTypes              | ✅ aep.inputEventTypes            | ❌ Missing                    | ❌ Missing                        |
| eventsProduced         | ✅ Optional list                                                                    | ✅ aep.outputEventTypes             | ✅ aep.outputEventTypes           | ❌ Missing                    | ❌ Missing                        |
| apiContracts           | ✅ Optional list                                                                    | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| **Memory Model**       |                                                                                     |                                     |                                   |                               |
| memoryBindings         | ✅ Required list                                                                    | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| memoryTypes            | ✅ Required list                                                                    | ✅ memory.type                      | ✅ memory.type                    | ✅ memory.type                | ✅ memory.\*                      |
| readStrategies         | ✅ Required list                                                                    | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| writePolicies          | ✅ Required object                                                                  | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| consolidationRules     | ✅ Optional list                                                                    | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| retentionPolicy        | ✅ Optional object                                                                  | ✅ memory.retention                 | ✅ memory.retention               | ✅ memory.retention           | ✅ memory.\*.retention            |
| **Tools & Resources**  |                                                                                     |                                     |                                   |                               |
| tools                  | ✅ Optional list (rich structure)                                                   | ❌ Missing (separate)               | ✅ tools                          | ✅ tools                      | ✅ tools                          |
| resources              | ✅ Optional list                                                                    | ✅ resources                        | ✅ resources                      | ✅ resources                  | ❌ Missing                        |
| toolSelectionPolicy    | ✅ Optional object                                                                  | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| **Governance**         |                                                                                     |                                     |                                   |                               |
| policyRefs             | ✅ Required list                                                                    | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| approvals              | ✅ Optional object                                                                  | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ✅ governance.requires_approval   |
| dataHandling           | ✅ Required object                                                                  | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| riskProfile            | ✅ Required object                                                                  | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| **Learning Model**     |                                                                                     |                                     |                                   |                               |
| learningLevel          | ✅ Enum (L0-L5)                                                                     | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| adaptationTargets      | ✅ Optional list                                                                    | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| learningSources        | ✅ Optional list                                                                    | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| driftControls          | ✅ Optional object                                                                  | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| rollbackPolicy         | ✅ Optional object                                                                  | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| **Evaluation**         |                                                                                     |                                     |                                   |                               |
| evaluationSpecRefs     | ✅ Optional list                                                                    | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| onlineMetrics          | ✅ Optional list                                                                    | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| offlineMetrics         | ✅ Optional list                                                                    | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| releaseGates           | ✅ Optional object                                                                  | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| **Observability**      |                                                                                     |                                     |                                   |                               |
| traceEnabled           | ✅ Required boolean                                                                 | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| loggedArtifacts        | ✅ Optional list                                                                    | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| auditMode              | ✅ Enum (minimal/standard/full/regulated)                                           | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ✅ governance.audit_trail         |
| alerts                 | ✅ Optional list                                                                    | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| **Interoperability**   |                                                                                     |                                     |                                   |                               |
| mcp                    | ✅ Optional object                                                                  | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| agentToAgent           | ✅ Optional object                                                                  | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ✅ delegation                     |
| compatibility          | ✅ Optional object                                                                  | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| **Security**           |                                                                                     |                                     |                                   |                               |
| authn                  | ✅ Required object                                                                  | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| authz                  | ✅ Required object                                                                  | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| secretsHandling        | ✅ Optional object                                                                  | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| networkPolicy          | ✅ Optional object                                                                  | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| **Deployment**         |                                                                                     |                                     |                                   |                               |
| runtimeClass           | ✅ Optional string                                                                  | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| scalingModel           | ✅ Optional object                                                                  | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| localityConstraints    | ✅ Optional object                                                                  | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| dependencies           | ✅ Optional list                                                                    | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| **Documentation**      |                                                                                     |                                     |                                   |                               |
| architectureRefs       | ✅ Optional list                                                                    | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| runbooks               | ✅ Optional list                                                                    | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| changelogRef           | ✅ Optional string                                                                  | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| **Examples**           |                                                                                     |                                     |                                   |                               |
| exampleScenarios       | ✅ Optional list                                                                    | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| antiPatterns           | ✅ Optional list                                                                    | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| **Extensibility**      |                                                                                     |                                     |                                   |                               |
| extensionPoints        | ✅ Optional list                                                                    | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |
| custom                 | ✅ Optional object                                                                  | ❌ Missing                          | ❌ Missing                        | ❌ Missing                    | ❌ Missing                        |

### 1.2 Key Gaps Identified

**Critical Missing Sections (by use case/logic):**

1. **identity**: No consistent agent classification (type, criticality, autonomy)
2. **purposeModel**: Missing mission/goals/nonGoals/successCriteria for alignment
3. **scope.boundaries**: No explicit operational boundaries defined
4. **capabilities**: Simple string lists instead of rich capability declarations
5. **reasoningProfile**: No reasoning strategy or determinism profiles
6. **executionModel**: Incomplete lifecycle and timeout/retry definitions
7. **memoryModel**: Missing memory bindings and write policies
8. **governance**: Missing policy refs, data handling, risk profiles
9. **learningModel**: No learning levels or adaptation controls
10. **evaluation**: No evaluation specs or release gates
11. **observability**: Missing trace requirements and artifact logging
12. **security**: No authn/authz definitions
13. **interoperability**: Missing MCP and A2A protocol declarations

### 1.3 Agent Spec Completeness Review

This section evaluates `docs/agent-spec.md` against state-of-the-art agentic processing requirements to ensure the specification is generic, future-safe, and supports all categories of agents.

#### 1.3.1 Agent Type Coverage

The spec must support ALL agent processing paradigms. Updated `identity.agentType` enum:

| Agent Type      | Spec Coverage | Java Enum | Description | Example Use Case |
|:----------------|:-------------|:----------|:------------|:-----------------|
| `deterministic` | ✅ | `DETERMINISTIC` | Pure functions, FSMs, 100% reproducible output | Event routing, schema validation |
| `rule-based`    | ✅ (NEW) | `RULE_BASED` (NEW) | Drools/OPA condition-action rules | Convention checks, policy enforcement |
| `policy`        | ✅ (NEW) | `POLICY` (NEW) | Policy engines evaluating governance constraints | Cost caps, data classification |
| `pattern`       | ✅ (NEW) | `PATTERN` (NEW) | Template matching, procedure reuse | Code scaffolding, known-pattern resolution |
| `probabilistic` | ✅ | `PROBABILISTIC` | ML models, Bayesian inference, classifiers | Anomaly detection, quality scoring |
| `planning`      | ✅ (NEW) | `PLANNING` (NEW) | Goal-directed planners (HTN, ReAct, tree-of-thought) | Multi-step task orchestration |
| `llm`           | ✅ | `LLM` | Large language model backed agents | Code generation, documentation, chat |
| `hybrid`        | ✅ | `HYBRID` | Multiple reasoning modes combined | Java Expert (rule+template+LLM pipeline) |
| `adaptive`      | ✅ (NEW) | `ADAPTIVE` | Self-tuning via bandits, RL, online learning | Retrieval ranking, confidence tuning |
| `composite`     | ✅ (NEW) | `COMPOSITE` | Ensemble/voting agents aggregating sub-agent outputs | Multi-model consensus |
| `reactive`      | ✅ (NEW) | `REACTIVE` | Event-triggered reflex agents (CEP, stream) | Event filtering, stream processing |
| `custom`        | ✅ (NEW) | `CUSTOM` | Extensible registry for domain-specific types | Healthcare-specific, finance-specific |

**Gap resolved**: The spec's agentType enum and Java `AgentType` enum are now aligned with 12 values covering all known agentic processing paradigms.

#### 1.3.2 Identity Model Completeness

The current spec has `agentType`, `roles`, `personas`, `criticality`, `autonomyLevel`. Missing from the spec but present in Java:

| Missing Field | Java Enum | Required in Spec | Rationale |
|:-------------|:----------|:----------------|:----------|
| `determinismGuarantee` | `DeterminismGuarantee` (FULL, CONFIG_SCOPED, NONE, EVENTUAL) | **Yes (required)** | Critical for test strategy, caching, trust calibration |
| `stateMutability` | `StateMutability` (STATELESS, LOCAL_STATE, EXTERNAL_STATE, HYBRID_STATE) | **Yes (required)** | Essential for scaling strategy, checkpoint/recovery, horizontal scaling |
| `failureMode` | `FailureMode` (FAIL_FAST, RETRY, FALLBACK, SKIP, DEAD_LETTER, CIRCUIT_BREAKER) | **Yes (required)** | Required for reliability engineering, SRE runbooks, pipeline design |

**Action**: Add `determinismGuarantee`, `stateMutability`, `failureMode` to the `identity` section of `agent-spec.md` and the migration plan schema.

#### 1.3.3 State-of-the-Art Agentic Processing Capabilities

Evaluation against current (2026) agentic AI advancements:

| Capability | Spec Section | Status | Notes |
|:-----------|:------------|:-------|:------|
| **Multi-step reasoning** (Chain-of-Thought, ReAct, Reflexion) | `reasoningProfile` | ✅ Covered | `reasonerPortfolio` supports multiple reasoning steps with ordering |
| **Tool use** (MCP, function calling) | `toolsAndResources`, `interoperability.mcp` | ✅ Covered | MCP support + tool selection policy |
| **Agent-to-agent communication** (A2A protocol) | `interoperability.agentToAgent` | ✅ Covered | Handoff, delegation, return contracts |
| **Memory systems** (working, episodic, semantic, procedural) | `memoryModel` | ✅ Covered | 8 memory types, read/write strategies, consolidation rules |
| **Learning & adaptation** (L0-L5 levels) | `learningModel` | ✅ Covered | From no-learning to parameter updates, with drift controls |
| **Confidence & uncertainty** | `reasoningProfile.confidenceModel` | ✅ Covered | Score ranges, thresholds, auto/human routing |
| **Human-in-the-loop** | `governance.approvals`, `identity.autonomyLevel` | ✅ Covered | Approval requirements + autonomy levels |
| **Safety & guardrails** | `capabilities.prohibitedCapabilities`, `governance.riskProfile` | ✅ Covered | Explicit prohibitions + risk assessment |
| **Observability & tracing** | `observability` | ✅ Covered | Traces, audit modes, logged artifacts, alerts |
| **Multi-tenancy** | `parameters` (tenantId), `security` | ✅ Covered | Tenant-scoped instances + authz scoping |
| **Streaming / event processing** | `interfaces.eventsConsumed/Produced`, `executionModel` | ✅ Covered | Event-driven invocation + concurrency model |
| **Compensation / saga patterns** | `executionModel.compensationPolicy` | ✅ Covered | Recovery from partial failure |
| **Cost governance** | `governance.policyRefs`, deployment resources | ✅ Covered | Policy enforcement + cost caps |
| **Deterministic pipelines** | `reasoningProfile.determinismProfile` | ✅ Covered | Zones classification for testing strategy |
| **Template/code generation** | `reasonerPortfolio` (type: template) | ✅ Covered | Template engines (Liquid) as reasoning steps |
| **Retrieval-Augmented Generation (RAG)** | `memoryModel.readStrategies` (dense, hybrid, graph) | ✅ Covered | Multi-strategy retrieval |
| **Agentic workflows** (loops, branches, sub-agents) | `interoperability.agentToAgent` + `executionModel` | ✅ Covered | Delegation + lifecycle states (blocked, waiting, suspended) |
| **Prompt management / versioning** | `parameters` (customPromptPrefix), `extensibility` | ⚠️ Partial | System prompts are agent-level config but prompt versioning/A/B testing not formalized |
| **Multi-modal inputs** (text, image, audio, video) | `interfaces.inputs` (schemaRef) | ⚠️ Partial | Schema refs can describe modality but no explicit modality declaration |
| **Sandboxed execution** | `security.networkPolicy`, `deployment` | ✅ Covered | Network constraints + runtime class |
| **Federation / cross-cluster agents** | `deployment.localityConstraints` | ⚠️ Partial | Region constraints exist but no cross-cluster federation protocol |

#### 1.3.4 Recommended Spec Enhancements

Based on the completeness review, the following enhancements to `agent-spec.md` are recommended:

**MUST ADD to identity section:**

```yaml
identity:
  # ... existing fields ...
  determinismGuarantee:
    type: enum
    required: true
    allowedValues: [full, config-scoped, none, eventual]
    purpose: >
      Declares the determinism guarantee of the agent's output.
      Critical for test strategy (deterministic = exact-match tests, none = statistical tests).
    example: "config-scoped"

  stateMutability:
    type: enum
    required: true
    allowedValues: [stateless, local-state, external-state, hybrid-state]
    purpose: >
      Declares how the agent manages mutable state. Drives scaling, checkpoint, and recovery strategy.
    example: "external-state"

  failureMode:
    type: enum
    required: true
    allowedValues: [fail-fast, retry, fallback, skip, dead-letter, circuit-breaker]
    purpose: >
      Default failure handling strategy. Can be overridden per-capability or per-invocation.
    example: "retry"
```

**SHOULD ADD to interfaces section:**

```yaml
interfaces:
  # ... existing fields ...
  supportedModalities:
    type: list
    required: false
    allowedValues: [text, image, audio, video, structured-data, binary]
    purpose: >
      Declares input/output modalities the agent can process, beyond schema definitions.
    example: ["text", "structured-data"]
```

**SHOULD ADD to learningModel section:**

```yaml
learningModel:
  # ... existing fields ...
  promptVersioning:
    type: object
    required: false
    purpose: >
      Tracks system prompt versions and supports A/B testing of prompt variants.
    example:
      currentVersion: "v2.3"
      abTestEnabled: false
      variants: []
```

#### 1.3.5 Agent Type-Specific Validation Rules

Each agent type implies specific validation constraints that `AgentDefinitionValidator` must enforce:

| Agent Type | Required Fields | Prohibited Fields | Validation Rule |
|:-----------|:---------------|:-----------------|:---------------|
| `deterministic` | `determinismGuarantee: full` | LLM reasoner in portfolio | Must not reference stochastic reasoning |
| `rule-based` | At least one rule-engine reasoner | — | `reasonerPortfolio` must contain type=rule-engine |
| `llm` | `reasonerPortfolio` with LLM entry, `maxTokens` | — | Must declare model provider + cost limits |
| `hybrid` | ≥2 reasoner types in portfolio | — | Must declare `reasoningStrategy` |
| `reactive` | `invocationModes` includes "event" | "schedule" as sole invocation | Must consume at least one event type |
| `adaptive` | `learningModel.learningLevel` ≥ L2 | — | Must have drift controls |
| `composite` | `interoperability.agentToAgent.enabled: true` | — | Must delegate to ≥2 sub-agents |
| `planning` | `executionModel.lifecycleStates` includes "blocked", "waiting" | — | Must support multi-step lifecycle |
| `stateless` (stateMutability) | — | `memoryModel.writePolicies.allowCreate: true` | Stateless agents must not write to persistent memory |

---

## 2. Unified Schema Definition

### 2.1 Top-Level Structure

```yaml
agentSpecVersion: "1.0.0"

# ═══════════════════════════════════════════════════════════════════════════
# Section 0: Parameters (Template Definition)
# ═══════════════════════════════════════════════════════════════════════════
# Every agent spec is a TEMPLATE. Parameters define what can be
# configured at instantiation time. Values can be provided via:
# - explicit values in agent instance YAML
# - environment variables (env://VAR_NAME)
# - secrets (secret://SECRET_NAME)
# - runtime context (context://path.to.value)
# - templates with variable substitution {{ paramName }}
# ═══════════════════════════════════════════════════════════════════════════
parameters:
  schemaVersion: "1.0.0"

  # Parameter declarations - what can be configured
  declarations:
    - name: tenantId
      type: string
      required: true
      description: "Tenant/organization identifier"
      validation:
        pattern: "^[a-z][a-z0-9-]+$"
        maxLength: 63

    - name: environment
      type: enum
      required: true
      allowedValues: [dev, staging, prod]
      default: dev
      description: "Deployment environment"

    - name: modelProvider
      type: enum
      required: false
      allowedValues: [openai, anthropic, local, azure]
      default: openai
      description: "LLM provider for reasoning"

    - name: maxTokens
      type: integer
      required: false
      default: 4096
      constraints:
        min: 1024
        max: 32000
      description: "Maximum tokens per LLM call"

    - name: timeoutMs
      type: integer
      required: false
      default: 30000
      description: "Execution timeout in milliseconds"

    - name: memoryRetentionDays
      type: integer
      required: false
      default: 90
      description: "How long to retain episodic memory"

    - name: apiKey
      type: secret
      required: true
      source: "secret://{{tenantId}}/llm-api-key"
      description: "API key for LLM provider"

    - name: customPromptPrefix
      type: string
      required: false
      description: "Additional instructions prepended to system prompt"

  # Validation rules across parameters
  validations:
    - rule: "prod_requires_high_timeout"
      condition: "environment == 'prod' && timeoutMs < 10000"
      message: "Production agents require timeout >= 10000ms"
      severity: error

    - rule: "local_model_no_api_key"
      condition: "modelProvider == 'local' && apiKey != null"
      message: "Local models should not require external API key"
      severity: warning

# ═══════════════════════════════════════════════════════════════════════════
# Section 1: Metadata (Identity & Discovery)
# ═══════════════════════════════════════════════════════════════════════════
metadata:
  id: string (required) - Globally unique identifier
  name: string (required) - Human-readable name
  namespace: string (required) - Logical ownership boundary
  version: string (required) - Semantic version
  status: enum (required) - draft | active | deprecated | retired | suspended
  owners: list (required) - Responsible teams/individuals
  tags: list (optional) - Searchable labels
  summary: string (required) - One-paragraph explanation
  description: string (optional) - Long-form functional narrative

# ═══════════════════════════════════════════════════════════════════════════
# Section 2: Identity (Classification & Role)
# ═══════════════════════════════════════════════════════════════════════════
identity:
  agentType: enum (required) - deterministic | rule-based | policy | pattern | probabilistic | planning | llm | hybrid | adaptive | composite | reactive | custom
  roles: list (required) - Functional roles in ecosystem
  personas: list (optional) - Behavior profiles for different modes
  criticality: enum (required) - low | medium | high | mission-critical
  autonomyLevel: enum (required) - advisory | assisted | semi-autonomous | autonomous
  determinismGuarantee: enum (required) - full | config-scoped | none | eventual
  stateMutability: enum (required) - stateless | local-state | external-state | hybrid-state
  failureMode: enum (required) - fail-fast | retry | fallback | skip | dead-letter | circuit-breaker

# ═══════════════════════════════════════════════════════════════════════════
# Section 3: Purpose Model (Mission & Goals)
# ═══════════════════════════════════════════════════════════════════════════
purposeModel:
  mission: string (required) - High-level enduring mission
  goals: list (required) - Concrete, testable goals
  nonGoals: list (optional) - Explicitly out of scope
  successCriteria: list (required) - Objective success conditions with metrics

# ═══════════════════════════════════════════════════════════════════════════
# Section 4: Scope (Boundaries & Domains)
# ═══════════════════════════════════════════════════════════════════════════
scope:
  domains: list (required) - Authorized business/technical domains
  supportedEntities: list (optional) - Entity types understood/manipulated
  boundaries: list (required) - Explicit operational boundaries

# ═══════════════════════════════════════════════════════════════════════════
# Section 5: Capabilities (What the agent can do)
# ═══════════════════════════════════════════════════════════════════════════
capabilities:
  declaredCapabilities: list (required) - Rich capability definitions
  capabilityDependencies: list (optional) - External dependencies
  prohibitedCapabilities: list (optional) - Explicitly forbidden actions

# ═══════════════════════════════════════════════════════════════════════════
# Section 6: Reasoning Profile (How the agent thinks)
# ═══════════════════════════════════════════════════════════════════════════
reasoningProfile:
  primaryReasoner: string (required) - Main reasoning engine
  reasonerPortfolio: list (required) - All supported reasoning mechanisms
  reasoningStrategy: string (optional) - High-level combination strategy
  determinismProfile: object (required) - Deterministic/probabilistic zones
  confidenceModel: object (optional) - Confidence computation and thresholds

# ═══════════════════════════════════════════════════════════════════════════
# Section 7: Execution Model (Runtime behavior)
# ═══════════════════════════════════════════════════════════════════════════
executionModel:
  invocationModes: list (required) - request | event | schedule | handoff | resume
  lifecycleStates: list (required) - created | ready | running | blocked | waiting | suspended | failed | completed | retired
  concurrencyModel: object (optional) - Concurrent execution handling
  retryPolicy: object (optional) - Retry behavior for failures
  timeoutPolicy: object (optional) - Execution duration limits
  compensationPolicy: object (optional) - Recovery from partial failure

# ═══════════════════════════════════════════════════════════════════════════
# Section 8: Interfaces (I/O Contracts)
# ═══════════════════════════════════════════════════════════════════════════
interfaces:
  inputs: list (required) - Input schemas and validation
  outputs: list (required) - Output schemas and contracts
  eventsConsumed: list (optional) - Event types listened to
  eventsProduced: list (optional) - Event types emitted
  apiContracts: list (optional) - External API/protocol references
  supportedModalities: list (optional) - text | image | audio | video | structured-data | binary

# ═══════════════════════════════════════════════════════════════════════════
# Section 9: Memory Model (Memory access & policies)
# ═══════════════════════════════════════════════════════════════════════════
memoryModel:
  memoryBindings: list (required) - Memory system access modes
  memoryTypes: list (required) - Conceptual memory structures used
  readStrategies: list (required) - Retrieval patterns supported
  writePolicies: object (required) - Write permissions and conditions
  consolidationRules: list (optional) - Memory promotion/merging rules
  retentionPolicy: object (optional) - Retention/archival rules

# ═══════════════════════════════════════════════════════════════════════════
# Section 10: Tools & Resources (External capabilities)
# ═══════════════════════════════════════════════════════════════════════════
toolsAndResources:
  tools: list (optional) - Tools the agent may invoke
  resources: list (optional) - Non-tool resources (datasets, indexes, etc.)
  toolSelectionPolicy: object (optional) - Tool selection constraints

# ═══════════════════════════════════════════════════════════════════════════
# Section 11: Governance (Policies & Controls)
# ═══════════════════════════════════════════════════════════════════════════
governance:
  policyRefs: list (required) - Governing policy references
  approvals: object (optional) - Human/system approval requirements
  dataHandling: object (required) - Classification, encryption, privacy
  riskProfile: object (required) - Operational and business risks

# ═══════════════════════════════════════════════════════════════════════════
# Section 12: Learning Model (Adaptation & Improvement)
# ═══════════════════════════════════════════════════════════════════════════
learningModel:
  learningLevel: enum (required) - L0 | L1 | L2 | L3 | L4 | L5
  adaptationTargets: list (optional) - System parts that may adapt
  learningSources: list (optional) - Inputs for learning
  driftControls: object (optional) - Drift detection and containment
  rollbackPolicy: object (optional) - Safe reversion of learned changes
  promptVersioning: object (optional) - System prompt versioning and A/B testing

# ═══════════════════════════════════════════════════════════════════════════
# Section 13: Evaluation (Quality Assurance)
# ═══════════════════════════════════════════════════════════════════════════
evaluation:
  evaluationSpecRefs: list (optional) - Formal evaluation packs
  onlineMetrics: list (optional) - Live production metrics
  offlineMetrics: list (optional) - Replay/benchmark metrics
  releaseGates: object (optional) - Minimum thresholds for deployment

# ═══════════════════════════════════════════════════════════════════════════
# Section 14: Observability (Monitoring & Debugging)
# ═══════════════════════════════════════════════════════════════════════════
observability:
  traceEnabled: boolean (required) - Execution trace requirement
  loggedArtifacts: list (optional) - Artifacts captured during execution
  auditMode: enum (optional) - minimal | standard | full | regulated
  alerts: list (optional) - Alert conditions

# ═══════════════════════════════════════════════════════════════════════════
# Section 15: Interoperability (Integration protocols)
# ═══════════════════════════════════════════════════════════════════════════
interoperability:
  mcp: object (optional) - Model Context Protocol configuration
  agentToAgent: object (optional) - Agent-to-agent interaction rules
  compatibility: object (optional) - Compatibility guarantees

# ═══════════════════════════════════════════════════════════════════════════
# Section 16: Security (Auth & Network)
# ═══════════════════════════════════════════════════════════════════════════
security:
  authn: object (required) - Authentication requirements
  authz: object (required) - Authorization rules
  secretsHandling: object (optional) - Credentials management
  networkPolicy: object (optional) - Network communication constraints

# ═══════════════════════════════════════════════════════════════════════════
# Section 17: Deployment (Runtime & Scaling)
# ═══════════════════════════════════════════════════════════════════════════
deployment:
  runtimeClass: string (optional) - Runtime substrate
  scalingModel: object (optional) - Scaling behavior under load
  localityConstraints: object (optional) - Geographic/regulatory constraints
  dependencies: list (optional) - Runtime dependencies

# ═══════════════════════════════════════════════════════════════════════════
# Section 18: Documentation (References)
# ═══════════════════════════════════════════════════════════════════════════
documentation:
  architectureRefs: list (optional) - Architecture document links
  runbooks: list (optional) - Operational instructions
  changelogRef: string (optional) - Change history reference

# ═══════════════════════════════════════════════════════════════════════════
# Section 19: Examples (Usage patterns)
# ═══════════════════════════════════════════════════════════════════════════
examples:
  exampleScenarios: list (optional) - Representative usage scenarios
  antiPatterns: list (optional) - Misuses to avoid

# ═══════════════════════════════════════════════════════════════════════════
# Section 20: Extensibility (Custom fields)
# ═══════════════════════════════════════════════════════════════════════════
extensibility:
  extensionPoints: list (optional) - Formal extension locations
  custom: object (optional) - Product/domain-specific fields
```

### 2.2 Instantiation Model (Spec → Agent)

The agent specification is a **template**. An **agent instance** is the materialized result with concrete parameter values.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    AGENT SPEC (Template)                                │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                     │
│  │ parameters  │  │   identity  │  │ capabilities│                     │
│  │             │  │             │  │             │                     │
│  │ tenantId    │  │  agentType  │  │  declared   │                     │
│  │ environment │  │  criticality│  │  with       │                     │
│  │ maxTokens   │  │             │  │ {{params}}  │                     │
│  └─────────────┘  └─────────────┘  └─────────────┘                     │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ Instantiation
                                    │ (parameter resolution)
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    AGENT INSTANCE (Concrete)                            │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │ runtime.id: agent.yappc.java-expert.acme-corp.prod                  │ │
│  │ runtime.instanceId: instance-uuid                                  │ │
│  │                                                                    │ │
│  │ Resolved Parameters:                                               │ │
│  │   tenantId: "acme-corp"                                            │ │
│  │   environment: "prod"                                              │ │
│  │   maxTokens: 4096                                                  │ │
│  │                                                                    │ │
│  │ Materialized Configuration:                                        │ │
│  │   reasoningProfile.reasonerPortfolio[0].engine: "gpt-4"            │ │
│  │   executionModel.timeoutPolicy.softTimeoutMs: 10000                │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

#### Parameter Resolution Hierarchy

Values are resolved in priority order (first wins):

1. **Explicit instance values** - Provided in agent instance YAML
2. **Runtime context** - `context://tenant.settings.maxTokens`
3. **Environment variables** - `env://LLM_MAX_TOKENS`
4. **Secrets** - `secret://vault/llm-api-key`
5. **Spec defaults** - From `parameters.declarations[].default`
6. **Product defaults** - From `platform/agent-catalog/values.yaml`
7. **System defaults** - Hardcoded safe fallbacks

#### Template Variable Syntax

Template variables use double braces `{{ }}` and support:

```yaml
# Simple parameter substitution
name: "Java Expert for {{tenantId}}"

# With filters
prompt: "System: {{customPromptPrefix | default: 'You are helpful'}}"

# Conditionals
{{#if environment == 'prod'}}
  auditMode: full
  traceEnabled: true
{{else}}
  auditMode: standard
  traceEnabled: false
{{/if}}

# Loops over parameter lists
{{#each supportedDomains}}
  - "{{this}}"
{{/each}}

# Nested access
endpoint: "https://{{region}}.api.ghatana.com/{{tenantId}}/agents"
```

#### Example: Spec Template

```yaml
agentSpecVersion: "1.0.0"

parameters:
  declarations:
    - name: tenantId
      type: string
      required: true
    - name: environment
      type: enum
      allowedValues: [dev, staging, prod]
      default: dev
    - name: modelTemperature
      type: number
      default: 0.7

metadata:
  id: agent.{{namespace}}.{{agentName}}
  name: "{{agentDisplayName}} for {{tenantId | title}}"
  namespace: "{{namespace}}"
  version: "{{specVersion}}"
  owners:
    - team: "{{owningTeam}}"
      role: technical-owner

identity:
  agentType: hybrid
  criticality: "{{#if environment == 'prod'}}high{{else}}medium{{/if}}"
  autonomyLevel: semi-autonomous
  determinismGuarantee: config-scoped
  stateMutability: external-state
  failureMode: retry

reasoningProfile:
  reasonerPortfolio:
    - id: r1
      type: llm
      engine: "{{modelProvider}}/{{modelName}}"
      config:
        temperature: "{{modelTemperature}}"
        maxTokens: "{{maxTokens}}"
        apiKey: "{{secret://llm/{{tenantId}}/api-key}}"

executionModel:
  timeoutPolicy:
    softTimeoutMs: "{{#if environment == 'prod'}}10000{{else}}5000{{/if}}"
    hardTimeoutMs: "{{timeoutMs}}"

observability:
  traceEnabled: "{{#if environment == 'prod'}}true{{else}}false{{/if}}"
  auditMode: "{{auditMode | default: 'standard'}}"
```

#### Example: Agent Instance (Instantiated)

```yaml
agentInstanceVersion: "1.0.0"
specRef: "agent.yappc.java-expert"
specVersion: "2.3.0"

runtime:
  instanceId: "inst-7f8d9a2b-3c4e-5f6g"
  tenantId: "acme-corp"
  environment: "prod"
  deployedAt: "2026-03-15T18:30:00Z"
  deployedBy: "system"

# Parameter values (override spec defaults)
parameters:
  values:
    tenantId: "acme-corp"
    environment: "prod"
    modelTemperature: 0.3 # Override from default 0.7
    maxTokens: 8192

  # Value sources (for audit/debugging)
  sources:
    tenantId: "explicit"
    modelTemperature: "explicit"
    apiKey: "secret://vault/acme-corp/llm-api-key"
    maxTokens: "env://MAX_TOKENS_OVERRIDE"

# Read-only: Materialized config (computed from spec + parameters)
materialized:
  metadata:
    id: "agent.yappc.java-expert"
    name: "Java Expert for Acme-corp"

  identity:
    criticality: "high" # Derived from environment=prod

  reasoningProfile:
    reasonerPortfolio:
      - engine: "openai/gpt-4"
        config:
          temperature: 0.3
          maxTokens: 8192

  executionModel:
    timeoutPolicy:
      softTimeoutMs: 10000 # Derived from environment
      hardTimeoutMs: 30000

  observability:
    traceEnabled: true # Derived from environment=prod
```

### 2.3 Migration Mapping Rules

**Template Variable Migration from Existing Platform Format:**

| Current Template Var          | New Parameter Declaration                          | Notes                       |
| ----------------------------- | -------------------------------------------------- | --------------------------- |
| `{{ defaultProvider }}`       | `parameters.declarations[].name: modelProvider`    | Formalize as enum           |
| `{{ defaultModel }}`          | `parameters.declarations[].name: modelName`        | With constraints            |
| `{{ defaultTemperature }}`    | `parameters.declarations[].name: modelTemperature` | Type: number                |
| `{{ defaultMaxTokens }}`      | `parameters.declarations[].name: maxTokens`        | Type: integer               |
| `{{ defaultMemoryType }}`     | `parameters.declarations[].name: memoryType`       | Enum: working/semantic/...  |
| `{{ defaultResourceMemory }}` | `parameters.declarations[].name: memoryLimit`      | Type: string                |
| `{{ defaultResourceCpu }}`    | `parameters.declarations[].name: cpuLimit`         | Type: string                |
| `{{ defaultTimeout }}`        | `parameters.declarations[].name: timeoutMs`        | Type: integer               |
| `{{ defaultMaxCostPerCall }}` | `parameters.declarations[].name: maxCostPerCall`   | Type: number                |
| `{{ defaultLogLevel }}`       | `parameters.declarations[].name: logLevel`         | Enum: debug/info/warn/error |
| `{{ tenantId }}`              | `parameters.declarations[].name: tenantId`         | Required, from context      |
| `{{ environment }}`           | `parameters.declarations[].name: environment`      | Required, from context      |

**Field Migrations from Existing Formats:**

| Old Field                    | New Field                                                  | Transformation                                                                              |
| ---------------------------- | ---------------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| `metadata.level`             | `identity` section                                         | Map to roles + derive criticality (L1→high, L2→medium, L3→low)                             |
| `metadata.domain`            | `scope.domains`                                            | Direct migration                                                                            |
| `generator.type`             | `identity.agentType`                                       | Map: ADAPTIVE→adaptive, DETERMINISTIC→deterministic, LLM→llm, PIPELINE→hybrid, RULE_BASED→rule-based, SERVICE_CALL→deterministic |
| `generator.*`                | `reasoningProfile.reasonerPortfolio`                       | Transform to reasoner entries                                                               |
| (not present)                | `identity.determinismGuarantee`                            | Derive: DETERMINISTIC→full, LLM→none, PIPELINE→config-scoped, ADAPTIVE→eventual            |
| (not present)                | `identity.stateMutability`                                 | Derive: micro-agents with no memory→stateless, episodic memory→external-state, mixed→hybrid-state |
| (not present)                | `identity.failureMode`                                     | Derive: from `aep.reliability.*`→retry, governance agents→fail-fast, task agents→fallback   |
| `aep.operatorType`           | `executionModel.invocationModes`                           | Map: ROUTING→[event], INGESTION→[event], TRANSFORMATION→[event, request]                    |
| `aep.inputEventTypes`        | `interfaces.eventsConsumed`                                | Direct migration                                                                            |
| `aep.outputEventTypes`       | `interfaces.eventsProduced`                                | Direct migration                                                                            |
| `aep.backpressure.*`         | `executionModel.concurrencyModel`                          | Transform structure                                                                         |
| `aep.reliability.*`          | `executionModel.retryPolicy`                               | Transform structure                                                                         |
| `io.inputs`                  | `interfaces.inputs`                                        | Direct migration                                                                            |
| `io.outputs`                 | `interfaces.outputs`                                       | Direct migration                                                                            |
| `memory.type`                | `memoryModel.memoryTypes`                                  | Expand to full list                                                                         |
| `memory.*`                   | `memoryModel.memoryBindings`                               | Transform to access declarations                                                            |
| `resources.*`                | `deployment` section                                       | Split and transform                                                                         |
| `tools`                      | `toolsAndResources.tools`                                  | Enrich with type/endpoint structure                                                         |
| `delegation.*`               | `interoperability.agentToAgent`                            | Transform structure                                                                         |
| `governance.*`               | `governance` section                                       | Expand and enrich                                                                           |
| `performance.*`              | `executionModel.timeoutPolicy` + `evaluation.releaseGates` | Split appropriately                                                                         |
| `routing.*`                  | `interfaces.inputs/outputs`                                | Transform to schema definitions                                                             |
| `capabilities` (simple list) | `capabilities.declaredCapabilities`                        | Enrich with full structure                                                                  |
| `memory.episodic: false`     | `memoryModel.memoryBindings: []` (empty for stateless)     | Stateless micro-agents get empty memory bindings                                            |

---

## 3. Phase-Based Migration Plan

### Phase 1: Foundation (Weeks 1-2)

**Goal**: Finalize agent spec, establish unified schema, template engine, and instantiation framework

**Tasks**:

0. **Spec Enhancement** (MUST complete first)
   - Add `determinismGuarantee`, `stateMutability`, `failureMode` to `identity` section in `agent-spec.md`
   - Add `supportedModalities` to `interfaces` section in `agent-spec.md`
   - Add `promptVersioning` to `learningModel` section in `agent-spec.md`
   - Update `identity.agentType` enum to 12 values (add adaptive, composite, reactive, custom)
   - Freeze `agent-spec.md` as version 1.0.0 — no further changes during migration

1. **Schema Definition**
   - Create `platform/agent-framework/schemas/agent-spec-v1.schema.json`
   - Define TypeScript/Java types for validation
   - Create YAML validation tooling

2. **Template Engine Development**
   - Implement `YamlTemplateEngine` with full parameter support
   - Add support for:
     - Variable substitution: `{{ paramName }}`
     - Filters: `{{ param | default: 'value' }}`
     - Conditionals: `{{#if condition}}...{{/if}}`
     - Loops: `{{#each list}}...{{/each}}`
     - Value sources: `env://`, `secret://`, `context://`
   - Parameter validation and cross-parameter rules
   - Template inheritance (extends/includes)

3. **Instantiation Framework**
   - Create `AgentSpecMaterializer` class
   - Implement parameter resolution hierarchy
   - Build `AgentInstanceFactory` for runtime instantiation
   - Add materialized config caching

4. **Migration Scripts**
   - Develop automated migration tool (`tools/agent-migrate`)
   - Add template variable extraction from existing specs
   - Create parameter declaration generator
   - Build validation pipeline

5. **Documentation**
   - Update `docs/agent-spec.md` with template model
   - Create parameter authoring guide
   - Document instantiation examples
   - Create troubleshooting guide

**Deliverables**:

- Frozen `agent-spec.md` v1.0.0 with all enhancements
- JSON Schema for agent-spec v1.0.0 (with parameters section)
- Template engine (YAML + parameter resolution)
- Instantiation framework (Spec → AgentInstance)
- Migration CLI tool with template extraction and `--in-place` mode
- Migration mapping configs for each product

**Agents to Migrate**: 0 (tooling only)

---

### Phase 2: Platform Agents (Weeks 3-4)

**Goal**: Migrate platform-level agents to establish patterns

**Tasks**:

1. **Base Template Update**
   - Update `platform/agent-catalog/base-agent-template.yaml` to new format
   - Add all missing sections with sensible defaults

2. **Core Agent Migration**
   - Migrate event-processing agents (event-router, event-filter, etc.)
   - Migrate monitoring agents (anomaly-detector, health-checker)
   - Migrate governance agents (cost-optimization, sustainability)

3. **Validation**
   - Run migration validation on all platform agents
   - Fix schema violations
   - Update tests

**Deliverables**:

- Updated base template
- 20+ migrated platform agents
- Validation reports

**Agents to Migrate**: 20

---

### Phase 3: Product Agents - AEP & Data Cloud (Weeks 5-6)

**Goal**: Migrate AEP and Data Cloud agents

**Tasks**:

1. **AEP Operator Migration**
   - Migrate ingestion agents (http-ingestion, kafka-ingestion)
   - Migrate routing agents (event-router)
   - Migrate transformation agents

2. **Data Cloud Agent Migration**
   - Migrate orchestration agents (data-pipeline-orchestrator)
   - Migrate storage agents (entity-storage, event-stream-storage)
   - Migrate quality/lineage agents

3. **Integration Testing**
   - Test agent loading with new format
   - Verify all agents load correctly with zero format errors
   - Update runtime parsers

**Deliverables**:

- 10+ migrated AEP agents
- 10+ migrated Data Cloud agents
- Integration test suite

**Agents to Migrate**: 20

---

### Phase 4: YAPPC Strategic Agents (Weeks 7-8)

**Goal**: Migrate Level 1 (Strategic) YAPPC agents

**Agents to Migrate**:

- `products-officer.yaml`
- `systems-architect.yaml`
- `ux-director.yaml`
- `head-of-devops.yaml`
- 49 orchestrators

**Tasks**:

1. Derive `identity` from `metadata.level` and generator type
2. Extract `purposeModel` from existing descriptions
3. Map `delegation` to `interoperability.agentToAgent`
4. Enrich `capabilities` with full structure
5. Add `governance` section with defaults

**Deliverables**:

- 53 migrated strategic agents
- YAPPC-specific migration patterns documented

---

### Phase 5: YAPPC Expert Agents (Weeks 9-10)

**Goal**: Migrate Level 2 (Expert) YAPPC agents

**Agents to Migrate**: 120 experts across 10 domains

**Key Migrations**:

- `java-expert.yaml` → Full reasoning profile with rule+template+LLM steps
- `sentinel.yaml` → Security-focused governance section
- `db-guardian.yaml` → Data handling and risk profiles
- `react-expert.yaml` → Frontend-specific capabilities

**Deliverables**:

- 120 migrated expert agents
- Domain-specific capability templates

---

### Phase 6: YAPPC Worker Agents (Weeks 11-14)

**Goal**: Migrate Level 3 (Task/Micro) YAPPC agents

**Agents to Migrate**: 273 task agents + 111 micro agents = 384 agents

**Approach**:

1. **Batch Processing**: Use automated migration tool
2. **Pattern Application**: Apply templates based on agent type
3. **Validation**: Automated schema validation
4. **Review**: Domain owner review for critical agents

**Templates by Type**:

- Code generation agents → Template-based with LLM refinement
- Test agents → Deterministic with rule-based validation
- Review agents → Hybrid reasoning with confidence models
- Documentation agents → LLM-based with semantic memory

**Deliverables**:

- 384 migrated worker agents
- Batch migration tooling validated
- Migration quality reports

---

### Phase 7: Runtime Code Rewrite (Weeks 15-16)

**Goal**: Rewrite ALL runtime Java code to exclusively support the new format

**Tasks**:

1. **Java Framework Rewrite**
   - Rewrite `AgentDefinition.java` with all 20 sections as first-class fields
   - Replace `AgentConfigDto.java` with `AgentSpecDto.java` matching the unified schema
   - Update `AgentType.java` enum with all 12 agent types
   - Add new fields to `AgentInstance.java` and `Overrides` inner class
   - Add `DeterminismGuarantee`, `StateMutability`, `FailureMode` as required identity fields

2. **Loader/Parser Rewrite**
   - Rewrite `AgentDefinitionLoader.java` to parse only `agentSpecVersion: "1.0.0"` format
   - Remove all old format detection and auto-migration code paths
   - Update `CatalogLoader.java` to expect unified catalog format
   - Extend `YamlTemplateEngine.java` with full parameter resolution (filters, conditionals, loops, value sources)

3. **Validator Rewrite**
   - Rewrite `AgentDefinitionValidator.java` with rules for all 20 sections
   - Add cross-section semantic validation (e.g., LLM agents must have reasoningProfile with LLM reasoner)
   - Add security validation (cost caps, network policies, authz scopes)

4. **Registry Rewrite**
   - Rewrite `registry.yaml` in unified format
   - Remove `apiVersion: ghatana.yappc/v1` and `ghatana.yappc/v2` kind-based format support
   - Replace with flat `agentSpecVersion`-based registry

5. **Delete Old Code**
   - Delete `AgentConfigDto.java` (replaced by `AgentSpecDto.java`)
   - Delete any old-format test fixtures
   - Delete any format-version detection code

**Deliverables**:

- Rewritten AgentDefinition/AgentSpecDto/AgentType classes
- Rewritten loader, validator, materializer
- Rewritten registry
- All old-format code deleted
- Full test suite passing

---

### Phase 8: Validation & Rollout (Weeks 17-18)

**Goal**: Full system validation and atomic production rollout

**Tasks**:

1. **Comprehensive Testing**
   - Load all 636+ agents in new format — zero failures required
   - Run full integration test suite
   - Performance testing (materialization latency, YAML parse time)
   - Verify no old-format YAML files remain in any product directory

2. **Documentation Updates**
   - Update all product READMEs
   - Update agent authoring guides
   - Create troubleshooting guide
   - Document migration as a completed one-time event (no ongoing dual-format support)

3. **Atomic Production Rollout**
   - Single PR merging all changes (YAML + Java + tests + registry)
   - Pre-merge: CI validates all 636+ agents parse and validate
   - Pre-merge: CI confirms zero references to old format classes/fields
   - Merge and deploy as a single unit
   - Rollback = git revert if issues detected

**Deliverables**:

- All 636+ agents migrated
- Production deployment
- Post-migration report

---

## 4. Missing Attributes to Add

### 4.1 Critical Missing Attributes (Must Add)

Based on use case analysis, these attributes are missing from current formats but critical for production:

#### identity.criticality

**Rationale**: Production agents need criticality classification for SRE prioritization and incident response.

**Mapping**:

- Platform agents: `event-router` → high, `anomaly-detector` → medium
- YAPPC Level 1 → high, Level 2 → medium, Level 3 → low

#### identity.autonomyLevel

**Rationale**: Governance requires knowing which agents can act independently vs. need approval.

**Mapping**:

- Agents with `governance.requires_approval: true` → `assisted`
- Agents with `can_block: true` → `semi-autonomous`
- Pure LLM agents → `advisory`

#### purposeModel.successCriteria

**Rationale**: Need objective metrics for agent quality and release gates.

**Default Values**:

```yaml
successCriteria:
  - metric: success_rate
    target: ">= 0.95"
  - metric: median_latency_ms
    target: "<= 5000"
  - metric: error_rate
    target: "<= 0.01"
```

#### reasoningProfile.determinismProfile

**Rationale**: Essential for testing strategy and trust calibration.

**Mapping**:

- `generator.type: DETERMINISTIC` → fully deterministic, `determinismGuarantee: full`
- `generator.type: LLM` → non-deterministic, `determinismGuarantee: none`
- `generator.type: PIPELINE` → bounded speculative, `determinismGuarantee: config-scoped`
- `generator.type: RULE_BASED` → fully deterministic, `determinismGuarantee: full`
- `generator.type: ADAPTIVE` → eventually consistent, `determinismGuarantee: eventual`

#### governance.riskProfile

**Rationale**: Security and compliance reviews require risk assessment.

**Default Structure**:

```yaml
riskProfile:
  impactLevel: medium # Derive from criticality
  primaryRisks:
    - "agent-specific risk 1"
    - "agent-specific risk 2"
  mitigations:
    - "mitigation 1"
    - "mitigation 2"
```

#### observability.traceEnabled

**Rationale**: Production-grade agents should have mandatory tracing.

**Default**: `true` for all Level 1 and 2 agents, `false` for Level 3

### 4.2 Product-Specific Missing Attributes

#### Platform Agents

- Add `executionModel.concurrencyModel` for event-driven agents
- Add `security.networkPolicy` with outbound restrictions
- Add `deployment.runtimeClass` = "kubernetes-job-agent"

#### AEP Agents

- Add `interoperability.mcp` for tool discoverability
- Add `executionModel.compensationPolicy` for saga patterns
- Enrich `capabilities` with input/output type declarations

#### Data Cloud Agents

- Add `governance.dataHandling` with classification
- Add `memoryModel.writePolicies` for data governance
- Add `learningModel` for data quality improvement

#### YAPPC Agents

- Add `purposeModel.mission` derived from description
- Add `scope.boundaries` from delegation patterns
- Add `reasoningProfile` from generator pipeline steps

---

## 5. Implementation Guidelines

### 5.1 Migration Tool Specification

```bash
# CLI interface
agent-migrate migrate \
  --source <path-to-agent-yaml> \
  --product <platform|aep|data-cloud|yappc> \
  --level <1|2|3> \
  --output <output-path> \
  --validate \
  --in-place          # Overwrite source file (no old format preserved)

agent-migrate validate \
  --spec <path-to-agent-yaml> \
  --schema <path-to-schema>

agent-migrate batch \
  --input-dir <directory> \
  --product <product> \
  --output-dir <directory> \
  --report <report-path> \
  --in-place          # Overwrite all source files atomically

agent-migrate verify-no-old-format \
  --root <repository-root>   # Fails if any old-format YAML or Java code remains
```

### 5.2 Complete Cut-Over Strategy (No Backward Compatibility)

**Approach**: ALL code, definitions, and tests are migrated atomically. No dual-format support.

**CRITICAL**: We do NOT maintain backward compatibility. The old format is removed entirely.

#### What Gets Deleted

1. **Old YAML files**: All agent YAML files in the pre-migration format are replaced in-place
2. **Old DTOs**: `AgentConfigDto.java` is replaced with `AgentSpecDto.java` matching the new schema
3. **Old format detection**: Any logic that auto-detects format version is removed
4. **YAPPC v1/v2 apiVersion support**: `ghatana.yappc/v1` and `ghatana.yappc/v2` kind-based formats are eliminated
5. **Old template variables**: `{{ defaultProvider }}`, `{{ defaultModel }}` etc. are replaced with formal parameter declarations
6. **Old registry format**: `registry.yaml` with phase-based organization is replaced with unified registry

#### What Gets Rewritten

1. **`AgentDefinition.java`**: Rewritten to match the 20-section unified schema (metadata, identity, purposeModel, scope, capabilities, reasoningProfile, executionModel, interfaces, memoryModel, toolsAndResources, governance, learningModel, evaluation, observability, interoperability, security, deployment, documentation, examples, extensibility)
2. **`AgentDefinitionLoader.java`**: Rewritten to deserialize the new spec format directly; `materialize()` maps `AgentSpecDto` → `AgentDefinition` with no old-format fallback
3. **`AgentDefinitionValidator.java`**: Rewritten with validation rules for all 20 sections
4. **`AgentInstance.java`**: Updated `Overrides` to cover new fields (reasoningProfile overrides, memoryModel overrides, governance overrides)
5. **`CatalogLoader.java`**: Updated to expect `agentSpecVersion: "1.0.0"` at the top of every agent YAML
6. **`AgentType.java` enum**: Updated to include all 12 values: `DETERMINISTIC`, `RULE_BASED`, `POLICY`, `PATTERN`, `PROBABILISTIC`, `PLANNING`, `LLM`, `HYBRID`, `ADAPTIVE`, `COMPOSITE`, `REACTIVE`, `CUSTOM`
7. **`YamlTemplateEngine.java`**: Extended with filter, conditional, loop, and value-source support for the new parameter system
8. **All test fixtures**: All agent YAML test fixtures are rewritten to the new format
9. **`base-agent-template.yaml`**: Rewritten with all 20 sections populated with sensible defaults
10. **`values.yaml`**: Replaced with formal parameter declarations and system defaults

#### Migration Execution Order

```
Step 1: Rewrite Java types (AgentDefinition, AgentSpecDto, AgentType, enums)
Step 2: Rewrite loader/validator/materializer code
Step 3: Batch-convert ALL 636+ agent YAML files (automated tool)
Step 4: Rewrite registry.yaml, catalogs, base template, values.yaml
Step 5: Rewrite all test fixtures and test code
Step 6: Delete old DTO classes, old format code paths, old format tests
Step 7: Run full build + test suite to validate
```

#### Rollback Strategy

Since this is an atomic cut-over, rollback = git revert the migration PR. The migration is done in a single PR branch so the entire change is atomic and revertible via version control.

### 5.3 Validation Pipeline

```yaml
validation_stages:
  - schema_validation: JSON Schema compliance
  - reference_validation: Check all refs (policies, tools, agents)
  - consistency_validation: Cross-field consistency
  - semantic_validation: Business logic validation
```

---

## 6. Success Metrics

| Metric                        | Target    | Measurement                                          |
| ----------------------------- | --------- | ---------------------------------------------------- |
| Agents Migrated               | 636+      | Count of YAML files in new format                    |
| Schema Compliance             | 100%      | Validation pass rate                                 |
| Zero Old-Format Files         | 0         | `grep -r "generator:" products/ platform/` = 0 hits |
| Zero Old-Format Java Code     | 0         | No `AgentConfigDto` class, no format-detection code  |
| Documentation Coverage        | 100%      | All 20 sections documented                           |
| Migration Time                | <18 weeks | Phase completion tracking                            |
| Build + Test Pass Rate        | 100%      | `./gradlew clean build test` passes                  |
| Agent Type Enum Alignment     | 12 types  | Java `AgentType` = spec `identity.agentType`         |
| Identity Fields Complete      | 100%      | determinismGuarantee + stateMutability + failureMode |

---

## 7. Next Steps

1. **Review this plan** with product owners for Platform, AEP, Data Cloud, and YAPPC
2. **Approve schema** additions (determinismGuarantee, stateMutability, failureMode, supportedModalities, promptVersioning) and the 12-value agentType enum
3. **Update `agent-spec.md`** with the three new identity fields and interface/learning enhancements
4. **Begin Phase 1**: Create schema, migration tooling, and batch-conversion scripts
5. **Establish migration champions** per product team with authority to approve generated agent YAML
6. **Create a single migration branch** — all changes (Java + YAML + tests) land on one branch for atomic merge
7. **Define validation CI job** that blocks merge unless all 636+ agents parse/validate with zero errors

---

## Appendix A: Example Migrated Agent

### Before (Current YAPPC Format)

```yaml
id: java-expert
name: Java Expert
version: 1.0.0
description: Domain expert for Java/ActiveJ backend development

metadata:
  level: 2
  domain: backend
  tags: [expert, java, backend, activej]

generator:
  type: PIPELINE
  steps:
    - name: spec_analysis
      type: RULE_BASED
    - name: code_generation
      type: TEMPLATE
      engine: liquid
    - name: code_refinement
      type: LLM
      provider: OPENAI
      model: gpt-4

capabilities:
  - code-generation
  - api-generation

governance:
  requires_approval: false
  audit_trail: standard
```

### After (New Unified Format)

```yaml
agentSpecVersion: "1.0.0"

metadata:
  id: agent.yappc.java-expert
  name: Java Expert
  namespace: yappc.engineering.backend
  version: "1.0.0"
  status: active
  owners:
    - team: platform-agent-runtime
      role: technical-owner
    - team: backend-guild
      role: domain-owner
  tags: [expert, java, backend, activej, code-generation]
  summary: >
    Domain expert for Java/ActiveJ backend development, API design, and concurrency.
  description: >
    Generates production-ready Java code following ActiveJ patterns, Java 21 best practices,
    and platform conventions. Uses hybrid approach: rule-based validation, template scaffolding,
    and LLM refinement.

identity:
  agentType: hybrid
  roles: [generator, validator, optimizer]
  criticality: medium
  autonomyLevel: semi-autonomous
  determinismGuarantee: config-scoped
  stateMutability: external-state
  failureMode: retry

purposeModel:
  mission: >
    Accelerate backend development by generating high-quality, convention-compliant Java code
    that follows platform standards and ActiveJ async patterns.
  goals:
    - Generate syntactically correct Java code
    - Follow ActiveJ non-blocking async patterns
    - Ensure comprehensive JavaDoc documentation
    - Maintain platform consistency
  nonGoals:
    - Does not generate frontend code
    - Does not perform deployment operations
  successCriteria:
    - metric: compilation_success_rate
      target: ">= 0.98"
    - metric: median_generation_latency_ms
      target: "<= 5000"

scope:
  domains: [backend, java, activej]
  supportedEntities: [java-class, java-interface, java-record, test-file]
  boundaries:
    - May generate code but cannot deploy to production
    - May suggest patterns but cannot modify existing code without review
    - May compile locally but cannot execute in production environment

capabilities:
  declaredCapabilities:
    - id: cap.java-class-generation
      name: Generate Java Class
      description: Creates Java classes from specifications
      inputTypes: [class-specification, api-endpoint-spec]
      outputTypes: [java-class]
      determinismLevel: bounded-speculative
      requiresHumanApproval: false
    - id: cap.java-validation
      name: Validate Java Code
      description: Validates code against platform conventions
      inputTypes: [java-code]
      outputTypes: [validation-report]
      determinismLevel: deterministic
      requiresHumanApproval: false

reasoningProfile:
  primaryReasoner: pipeline-orchestrator
  reasonerPortfolio:
    - id: r1
      type: rule-engine
      purpose: Validate Java conventions and ActiveJ patterns
      engine: drools
      invocationWhen: [specification-validation, convention-checks]
    - id: r2
      type: template
      purpose: Generate boilerplate from templates
      engine: liquid
      invocationWhen: [scaffolding, standard-patterns]
    - id: r3
      type: llm
      purpose: Semantic refinement and documentation
      engine: openai/gpt-4
      invocationWhen: [code-refinement, javadoc-generation]
  reasoningStrategy: >
    Rule-first for validation, template for structure, LLM for refinement and documentation.
  determinismProfile:
    fullyDeterministicZones: [syntax-validation, convention-checks]
    boundedSpeculativeZones: [code-generation, api-design]
    nonDeterministicZones: [documentation-generation]

executionModel:
  invocationModes: [request, event]
  lifecycleStates: [created, ready, running, blocked, failed, completed]
  retryPolicy:
    enabled: true
    maxAttempts: 2
    backoffStrategy: exponential
    retryableErrors: [llm-timeout, compilation-error]
  timeoutPolicy:
    softTimeoutMs: 10000
    hardTimeoutMs: 30000
    onSoftTimeout: checkpoint-and-warn
    onHardTimeout: terminate-and-mark-failed

interfaces:
  inputs:
    - name: codeGenerationRequest
      schemaRef: "#/schemas/CodeGenerationRequest"
      required: true
  outputs:
    - name: generatedJavaCode
      schemaRef: "#/schemas/JavaCodeArtifact"
    - name: compilationResult
      schemaRef: "#/schemas/CompilationResult"

toolsAndResources:
  tools:
    - id: tool.maven-executor
      type: build
      access: invoke
      purpose: Compile generated code
    - id: tool.checkstyle-validator
      type: validation
      access: invoke
      purpose: Validate code style

governance:
  policyRefs:
    - policy.code-generation.v1
    - policy.java-conventions.v2
  approvals:
    requiredFor: []
  dataHandling:
    supportedClassifications: [public, internal]
    defaultClassification: internal
    redactBeforeLLM: false
    encryptAtRest: true
  riskProfile:
    impactLevel: medium
    primaryRisks:
      - "Generated code may contain bugs"
      - "LLM may produce non-idiomatic patterns"
    mitigations:
      - "Compilation validation before output"
      - "Checkstyle enforcement"
      - "Human review for critical code"

observability:
  traceEnabled: true
  loggedArtifacts:
    - input-specification
    - generated-code
    - compilation-results
    - reasoner-selection
  auditMode: standard

extensibility:
  extensionPoints: [custom-templates, custom-rules]
```

---

## Appendix B: Directory Structure After Migration

```
platform/agent-catalog/
├── schemas/
│   └── agent-spec-v1.schema.json
├── templates/
│   ├── base-agent.yaml
│   ├── event-driven-agent.yaml
│   └── llm-agent.yaml
└── agents/
    ├── event-processing/
    │   └── event-router-agent.yaml (migrated)
    ├── monitoring/
    │   └── anomaly-detector-agent.yaml (migrated)
    └── ...

products/yappc/config/agents/
├── schemas/ (symlink to platform)
├── templates/
│   ├── level1-strategic.yaml
│   ├── level2-expert.yaml
│   └── level3-worker.yaml
└── definitions/
    ├── orchestrators/ (53 migrated)
    ├── capabilities/ (52 migrated)
    ├── task-agents/ (162 migrated)
    └── micro-agents/ (111 migrated)
```

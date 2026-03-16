# Agent Specification Unification Plan

> **Last Updated:** 2026-03-16 (v3.6.0 — Framework hardening COMPLETE: `IOContract` NPE fix, `extractDefinition()` full field mapping, `AgentDefinitionLoader`↔`AgentSpecLoader` bridge, deprecated `AgentRegistry` (planner) REMOVED, workflow layer migrated to `TypedAgent`, `PlannerAgentFactory` `AgentType.LLM` ref removed, 9 new tests; 516/516 tests passing; zero compiler warnings)

## Executive Summary

This document provides a comprehensive analysis of all agent definitions across the Ghatana codebase and a concrete migration plan to unify them under the new agent specification format defined in `docs/agent-spec.md`.

**Migration Strategy: COMPLETE CUT-OVER** — All agent definitions, Java runtime code, YAML parsers, registries, catalogs, and test fixtures are migrated atomically. No deprecated code, no backward compatibility shims, no dual-format support. Old format files are deleted after migration.

**Scope**: 636+ agents across 4 major product areas

- **Platform**: 20+ core agents (event-processing, monitoring, governance, security)
- **AEP**: 10+ operator agents (ingestion, routing, transformation)
- **Data Cloud**: 10+ data-centric agents (orchestration, quality, lineage)
- **YAPPC**: 636 agents across 10 domains (orchestrators, capabilities, task-agents, micro-agents)

**Implementation Status** (as of v3.3.0):

| Area | Status | Notes |
|:-----|:-------|:------|
| AgentType enum (9 types) | ✅ DONE | `STREAM_PROCESSOR`, `PLANNING` added; `LLM` deprecated |
| DeterministicSubtype (8 values) | ✅ DONE | `POLICY`, `OPERATOR`, `TEMPLATE` added |
| ProbabilisticSubtype (5 values) | ✅ DONE | `CLASSIFIER` added; `LLM` subtype enhanced |
| StreamProcessorSubtype (7 values) | ✅ DONE | New enum created |
| PlanningSubtype (5 values) | ✅ DONE | HTN, REACT, TOT, WORKFLOW, OBJECTIVE_DECOMPOSITION |
| PlanningAgent base class | ✅ DONE | PLAN→EXECUTE→OBSERVE→REPLAN lifecycle |
| StreamProcessorAgent base class | ✅ DONE | Checkpoint/restore, per-event retry |
| PlannerRegistry (renamed from AgentRegistry) | ✅ DONE | Naming collision resolved; old class deprecated |
| Agent.java deprecated | ✅ DONE | Phase 1 complete; TypedAgent<I,O> is canonical |
| agent-spec.md (9-type taxonomy) | ✅ DONE | Disambiguation comments, JAVA ALIGNMENT section |
| Platform catalog YAMLs (20 files) | ✅ DONE | All core-agents, domain-agents, composite-agents updated |
| catalog-schema.yaml (v2.0.0) | ✅ DONE | identity block, new required fields, generator: deprecated |
| AEP product YAML catalog (8 operators) | ✅ DONE | identity, interfaces, interoperability, learningModel added; generator removed |
| Data Cloud YAML catalog (13 definitions) | ✅ DONE | Full spec enrichment across all subtypes (deterministic/adaptive/hybrid/stream/planning) |
| YAPPC orchestrators (49 files) | ✅ DONE | namespace, status, owners, identity, interfaces, interoperability, learningModel, reasoningProfile added; generator removed |
| ToolRegistry Java class | ✅ DONE | `com.ghatana.agent.framework.tools.ToolRegistry` — ConcurrentHashMap-backed, category + tag discovery |
| GovernanceEngine Java class | ✅ DONE | `com.ghatana.agent.framework.governance.GovernanceEngine` — active enforcement, audit log, cost-cap evaluation |
| LearningEngine Java class | ✅ DONE | `com.ghatana.agent.framework.learning.LearningEngine` — L0-L5 levels, batch episode→policy synthesis |
| InteroperabilityService Java class | ✅ DONE | `com.ghatana.agent.framework.interop.InteroperabilityService` — MCP JSON-RPC 2.0 + A2A adapters |
| MemoryPlane typed interface | ✅ DONE | `com.ghatana.agent.framework.memory.MemoryPlane` — replaces `Object` return from asMemoryPlane() |
| GovernancePolicy.noOp() factory | ✅ DONE | Added to GovernancePolicy interface; used by GovernanceEngine default |
| JSON schema: deterministic-agent | ✅ DONE | Pre-existing |
| JSON schema: stream-processor-agent | ✅ DONE | Created with CheckpointPolicy, BackpressureConfig, WindowConfig, EventBindings |
| JSON schema: planning-agent | ✅ DONE | Created with PlanningSpec, GoalDecomposition, DelegationPolicy, PlanningLlmConfig |
| YAPPC capabilities (52 files) | ✅ DONE | Phase 5 — hybrid/rule+llm; namespace, status, owners, identity, interfaces, interoperability, learningModel added; generator removed |
| YAPPC task-agents (162 files) | ✅ DONE | Phase 5 — probabilistic/llm; L1 learningModel, stateless, fail-fast pattern |
| YAPPC micro-agents (111 files) | ✅ DONE | Phase 5 — deterministic/rule-engine; L0, stateless, fail-fast; fully deterministic |
| YAPPC catalogs (26 catalog YAMLs) | ✅ DONE | Phase 6 — apiVersion→ghatana.yappc/v3; status/schemaVersion added; agentType+agentSubtype enriched in DomainAgentCatalog entries |
| AgentDefinitionValidator type-specific rules | ✅ DONE | All 9 canonical types validated (DETERMINISTIC through CUSTOM); `validateTypeSpecific()` added to main `validate()` pipeline |
| PATTERN subtype DEGRADED response | ✅ DONE | `DeterministicAgent.evaluatePattern` returns DEGRADED+input-echo when no strategy wired (not SKIPPED+empty) |
| Test suite — 490/490 passing | ✅ DONE | All 6 previously-failing tests fixed; 0% failure rate |
| `AgentSpec.java` POJO hierarchy | ✅ DONE | `com.ghatana.agent.framework.spec.AgentSpec` — complete 18-section immutable POJO with 23 nested record types (SpecMetadata, SpecIdentity, PurposeModel, SpecScope, SpecCapabilities, ReasoningProfile, ReasonerDeclaration, DeterminismProfile, ConfidenceModel, ExecutionModel, SpecInterfaces, InterfacePort, MemoryModel, MemoryBinding, ToolsAndResources, GovernanceSpec, LearningModel, EvaluationModel, ObservabilitySpec, InteroperabilitySpec, SecuritySpec, DeploymentSpec, Builder) |
| `GovernancePolicyRef.java` | ✅ DONE | `com.ghatana.agent.framework.spec.GovernancePolicyRef` — named policy reference record (id, description, enforcementMode) with static factory methods; supports both string and map YAML formats |
| `AgentSpecLoader.java` (full deserialization) | ✅ DONE | `com.ghatana.agent.framework.spec.AgentSpecLoader` — complete 18-section YAML→`AgentSpec` loader; Jackson DTO hierarchy with 18 nested DTOs; smart type aliases (llm→PROBABILISTIC, rule-based→DETERMINISTIC, stream-processor→STREAM_PROCESSOR); type-sensitive defaults; `extractDefinition(AgentSpec)→AgentDefinition` bridge; `load(Path)`, `loadFromString(String)`, `loadFromDirectory(Path)` |
| `agent-base-schema.json` enriched v2.0.0 | ✅ DONE | Complete JSON Schema v2.0.0 replacing v1 schema — all 18 spec sections as first-class `$defs`: SpecMetadata (status/owners/tags/summary), SpecIdentity (full enum fields), PurposeModel, SpecScope, SpecCapabilities, ReasoningProfile, ExecutionModel, SpecInterfaces, InterfacePort, MemoryModel, GovernanceSpec (policyRefs as string or map), LearningModel (L0-L5), EvaluationModel, ObservabilitySpec, InteroperabilitySpec, SecuritySpec, DeploymentSpec |
| `AgentSpecLoaderTest.java` | ✅ DONE | 18 test methods across 7 `@Nested` groups: MinimalSpec, TypeAliasResolution (6 aliases), SmartDefaults (3 agent types), FullSpec (all 18 sections), ExtractDefinition bridge, Validation (3 required-field checks), DirectoryLoading (load-from-file + directory scan + skip-invalid), PolicyRefParsing (string vs map format) |
| Bug fix: `rule-based` alias → DETERMINISTIC | ✅ DONE | Corrected `AgentSpecLoader` type alias: `rule-based`/`rule_based` now correctly resolves to `DETERMINISTIC` (not grouped with `llm`→PROBABILISTIC) |
| Test suite — 511/511 passing | ✅ DONE | 21 new `AgentSpecLoaderTest` cases + all 490 pre-existing; 0% failure rate |
| **Bug fix: `extractDefinition()` IOContract NPE** (v3.6.0) | ✅ DONE | `IOContract` was constructed with `null` format (throws `NPE` at runtime). Fixed: format now defaults to `"JSON"`, or `"PROTOBUF"`/`"AVRO"` when detected from `schemaRef` URI via `deriveFormat()` helper |
| **`extractDefinition()` complete field mapping** (v3.6.0) | ✅ DONE | Added: `autonomyLevel` → label `"autonomyLevel"`, `criticality` → label `"criticality"`, `governance.riskProfile.maxCostPerCall` → `AgentDefinition.maxCostPerCall` |
| **`AgentDefinitionLoader` ↔ `AgentSpecLoader` bridge** (v3.6.0) | ✅ DONE | `AgentDefinitionLoader.load()` and `loadFromString()` now auto-detect `agentSpecVersion:` marker and delegate to `AgentSpecLoader.extractDefinition()`. Old flat-format YAMLs continue to work unchanged. |
| **Deprecated `AgentRegistry` (planner) REMOVED** (v3.6.0) | ✅ DONE | `com.ghatana.agent.framework.planner.AgentRegistry` deleted (zero callers; was a pure deprecated subclass of `PlannerRegistry`) |
| **Workflow layer migrated to `TypedAgent`** (v3.6.0) | ✅ DONE | `WorkflowAgentRegistry`, `InMemoryWorkflowAgentRegistry`, `DefaultWorkflowAgentService` updated: `Agent` → `TypedAgent<?,?>`; `getCapabilities()` → `descriptor().getName()/getDescription()`; `agent.process(input, ctx)` → `TypedAgent.process(ctx, input)` returning `AgentResult<O>` |
| **`PlannerAgentFactory` `AgentType.LLM` ref removed** (v3.6.0) | ✅ DONE | `if (type == AgentType.LLM \|\| type == AgentType.PROBABILISTIC)` → `if (type == AgentType.PROBABILISTIC)`; zero compiler warnings |
| **9 new tests for v3.6.0 fixes** (v3.6.0) | ✅ DONE | `AgentSpecLoaderTest`: `ioContractDefaultFormat`, `ioContractProtobufFormat`, `extractsMaxCostPerCallFromRiskProfile`, `extractsAutonomyLevelAndCriticalityAsLabels` (4 tests). `AgentDefinitionLoaderTest`: `delegatesToAgentSpecLoaderForNewFormat`, `loadsOldFlatFormatNormally` (2 tests). `MigrationAdapterTest`: removed stale `agentRegistryIsDeprecated` test |
| Test suite — 516/516 passing (v3.6.0) | ✅ DONE | +5 new tests vs v3.5.0 (−1 stale test for deleted class); 0% failure rate; 0 compiler warnings |

**Critical Enhancement**: Complete agent-framework implementation with support for all 9 agent types:
- **Deterministic**: Rule engines, FSMs, pattern matching, policy evaluation (reproducible)
- **Probabilistic**: ML models, Bayesian inference, LLMs, classifiers (confidence-based)
- **Stream Processor**: Event-driven, stateful stream processing with checkpointing (AEP operators)
- **Planning**: Goal-directed, HTN, ReAct, workflow orchestration (multi-step)
- **Hybrid**: Multi-reasoner combinations with intelligent routing
- **Adaptive**: Self-tuning, reinforcement learning, A/B testing
- **Composite**: Ensembles, voting systems, distributed coordination
- **Reactive**: Stateless trigger-action reflexes, alerts, circuit-breakers
- **Custom**: Extensible domain-specific types

**Consolidated from 12 types**: Rule-based, policy, and pattern merged into deterministic as subtypes; LLM moved to probabilistic as subtype; Stream processor added for AEP operators.

---

## 1. Current State Analysis

### 1.1 Format Comparison Matrix

> **NOTE (v3.4.0–v3.5.0):** This matrix reflects the pre-migration state. All product areas (Platform, AEP, Data Cloud, YAPPC) have been fully migrated to the new agent-spec.md format as of v3.4.0. The ❌ entries below describe the historical gap — all are now ✅ in deployed YAML files. The Java POJO deserialization layer (`AgentSpec`, `AgentSpecLoader`) was completed in v3.5.0. See the Implementation Status table in the Executive Summary for current status.

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
| agentType              | ✅ Enum (9 values: deterministic/probabilistic/stream_processor/planning/hybrid/adaptive/composite/reactive/custom) | ❌ Missing (has `type` in metadata) | ❌ Missing (has `generator.type`) | ❌ Missing                    | ❌ Missing (has `generator.type`) |
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
| `deterministic` | ✅ | `DETERMINISTIC` | Pure functions, FSMs, rules, patterns, policies - 100% reproducible | Event routing, validation, thresholds, governance |
| `probabilistic` | ✅ | `PROBABILISTIC` | ML models, Bayesian inference, LLMs, classifiers - confidence-based | Anomaly detection, LLM reasoning, classification |
| `stream_processor` | ✅ (NEW) | `STREAM_PROCESSOR` (NEW) | Event-driven stream processing with stateful operations, checkpointing | AEP operators, CEP, windowed aggregations, event routing |
| `planning` | ✅ (NEW) | `PLANNING` (NEW) | Goal-directed planners (HTN, ReAct, tree-of-thought) | Multi-step task orchestration, workflow execution |
| `hybrid` | ✅ | `HYBRID` | Multiple reasoning modes combined with intelligent routing | Java Expert (rule+template+LLM), complex reasoning |
| `adaptive` | ✅ (NEW) | `ADAPTIVE` (NEW) | Self-tuning via bandits, RL, online learning | Retrieval ranking, confidence tuning, A/B testing |
| `composite` | ✅ (NEW) | `COMPOSITE` (NEW) | Ensemble/voting agents aggregating sub-agent outputs | Multi-model consensus, parallel execution |
| `reactive` | ✅ (NEW) | `REACTIVE` (NEW) | Stateless trigger-action reflexes | Simple alerts, triggers, circuit-breakers |
| `custom` | ✅ (NEW) | `CUSTOM` (NEW) | Extensible registry for domain-specific types | Healthcare-specific, finance-specific domains |

**Gap resolved**: The spec's agentType enum and Java `AgentType` enum are now aligned with 9 values covering all known agentic processing paradigms with clear boundaries and no redundancies.

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
| `deterministic` | `determinismGuarantee: full` | LLM reasoner in portfolio | Must not reference stochastic reasoning; supports subtypes: rule-engine, fsm, pattern-matcher, policy-engine |
| `probabilistic` | Confidence/scoring fields | — | Must declare confidence metric; supports subtypes: llm, ml-model, bayesian, classifier |
| `stream_processor` | `executionModel.checkpointPolicy`, `interfaces.eventBindings` | — | Must have event input/output declarations; supports subtypes: ingestion, routing, transformation, cep, window-agg |
| `planning` | `executionModel.lifecycleStates` includes "blocked", "waiting" | — | Must support multi-step lifecycle with explicit planning phase |
| `hybrid` | ≥2 reasoner types in portfolio | — | Must declare `reasoningStrategy` for routing between reasoners |
| `adaptive` | `learningModel.learningLevel` ≥ L2 | — | Must have drift controls and feedback mechanisms |
| `composite` | `interoperability.agentToAgent.enabled: true` | — | Must delegate to ≥2 sub-agents with aggregation strategy |
| `reactive` | `invocationModes` includes "event" | "schedule" as sole invocation, stateful memory | Must be stateless with immediate response; simple trigger→action only |
| `custom` | `identity.customTypeRef` | — | Must reference registered custom type in CustomTypeRegistry |

---

## 3. Comprehensive Agent Inventory Analysis

### 3.1 Complete Agent Count by Product

**Total Agent Files Identified**: 1,098 YAML files

| Product | Agent Files | Definition Files | Catalog Files | Instance Files | Total |
|---------|-------------|------------------|---------------|----------------|-------|
| **Platform** | 12 | 0 | 0 | 0 | 12 |
| **AEP** | 11 | 0 | 0 | 0 | 11 |
| **Data Cloud** | 14 | 0 | 0 | 0 | 14 |
| **YAPPC** | 1,061 | 592 | 469 | 0 | 1,061 |

**Grand Total**: 1,098 agent specification files to migrate

### 3.2 Platform Agents (12 files)

**Core Platform Agents** - All require migration:
```
platform/agent-catalog/core-agents/
├── data-processing/
│   ├── data-transformation-agent.yaml
│   └── schema-validation-agent.yaml
├── event-processing/
│   ├── event-enricher-agent.yaml
│   ├── event-filter-agent.yaml
│   ├── event-router-agent.yaml
│   └── event-transformer-agent.yaml
├── governance/
│   ├── cost-optimization-agent.yaml
│   └── sustainability-agent.yaml
├── monitoring/
│   ├── anomaly-detector-agent.yaml
│   ├── health-checker-agent.yaml
│   └── metrics-collector-agent.yaml
└── security/
    └── access-control-agent.yaml
```

**Migration Complexity**: LOW - All use standard platform format with `generator.type` field

### 3.3 AEP Agents (11 files)

**AEP Operator Agents** - All require migration:
```
products/aep/agent-catalog/
├── operators/
│   ├── ingestion/
│   │   ├── http-ingestion-agent.yaml
│   │   └── kafka-ingestion-agent.yaml
│   ├── orchestration/
│   │   └── unified-event-orchestrator.yaml
│   ├── pattern/
│   │   ├── anomaly-detection-agent.yaml
│   │   ├── correlation-agent.yaml
│   │   └── pattern-detection-agent.yaml
│   ├── routing/
│   │   └── event-router-agent.yaml
│   └── transformation/
│       └── event-transformation-agent.yaml
└── capabilities/
    └── aep-capabilities.yaml
```

**Migration Complexity**: MEDIUM - Mix of AEP-specific `aep.operatorType` and standard `generator.type`

### 3.4 Data Cloud Agents (14 files)

**Data Cloud Agents** - All require migration:
```
products/data-cloud/agent-catalog/
├── definitions/
│   ├── archival/
│   │   └── data-archival-agent.yaml
│   ├── migration/
│   │   ├── data-migration-agent.yaml
│   │   └── schema-evolution-agent.yaml
│   ├── observability/
│   │   ├── data-lineage-agent.yaml
│   │   └── data-quality-agent.yaml
│   ├── orchestration/
│   │   ├── data-pipeline-orchestrator-agent.yaml
│   │   └── unified-data-orchestrator.yaml
│   ├── query/
│   │   ├── cache-manager-agent.yaml
│   │   └── query-optimization-agent.yaml
│   ├── replication/
│   │   └── data-replication-agent.yaml
│   └── storage/
│       ├── data-compaction-agent.yaml
│       ├── entity-storage-agent.yaml
│       └── event-stream-storage-agent.yaml
└── capabilities/
    └── data-cloud-capabilities.yaml
```

**Migration Complexity**: MEDIUM - Mix of `generator.type: PIPELINE` and custom fields

### 3.5 YAPPC Agents (1,061 files)

**YAPPC Agent Breakdown**:

#### 3.5.1 Definition Files (592 files)
```
products/yappc/config/agents/definitions/
├── orchestrators/ (53 files)
│   ├── products-officer.yaml
│   ├── systems-architect.yaml
│   ├── ux-director.yaml
│   ├── head-of-devops.yaml
│   └── 49 more strategic orchestrators
├── capabilities/ (52 files)
│   ├── ai/ (5 files)
│   │   ├── agent-runtime-capability-agent-ai.yaml
│   │   ├── data-pipeline-capability-agent.yaml
│   │   ├── llm-integration-capability-agent.yaml
│   │   ├── memory-capability-agent.yaml
│   │   └── search-capability-agent.yaml
│   ├── architecture/ (8 files)
│   ├── discovery/ (4 files)
│   ├── engineering/ (4 files)
│   └── 31 more capability agents
├── task-agents/ (162 files)
│   ├── requirements/ (25 files)
│   ├── ux/ (40 files)
│   ├── testing/ (15 files)
│   ├── security/ (12 files)
│   ├── devops/ (20 files)
│   ├── data/ (18 files)
│   ├── documentation/ (15 files)
│   └── 17 more task categories
└── micro-agents/ (111 files)
    ├── code-review/ (20 files)
    ├── validation/ (18 files)
    ├── formatting/ (15 files)
    ├── analysis/ (12 files)
    └── 46 more micro categories
```

#### 3.5.2 Catalog Files (469 files)
```
products/yappc/config/agents/
├── agent-catalog.yaml
├── ai-catalog.yaml
├── architecture-catalog.yaml
├── capabilities.yaml
├── cloud-catalog.yaml
├── compliance-catalog.yaml
├── discovery-catalog.yaml
├── engineering-catalog.yaml
├── ideation-catalog.yaml
├── lifecycle-catalog.yaml
├── platform-catalog.yaml
├── product-intelligence-catalog.yaml
├── prompt-fragments.yaml
├── registry.yaml
├── requirements-catalog.yaml
├── security-catalog.yaml
├── ux-catalog.yaml
└── 450+ more catalog and configuration files
```

### 3.6 Migration Complexity Assessment

#### 3.6.1 Complexity Levels

| Agent Type | Count | Complexity | Migration Strategy |
|------------|-------|------------|-------------------|
| **Platform Core** | 12 | LOW | Direct mapping, automated |
| **AEP Operators** | 11 | MEDIUM | AEP-specific field mapping |
| **Data Cloud** | 14 | MEDIUM | Pipeline to reasoning profile |
| **YAPPC Orchestrators** | 53 | HIGH | Complex delegation mapping |
| **YAPPC Capabilities** | 52 | MEDIUM | Capability enrichment |
| **YAPPC Task Agents** | 162 | HIGH | Diverse patterns, templates |
| **YAPPC Micro Agents** | 111 | LOW | Simple deterministic patterns |
| **YAPPC Catalogs** | 469 | MEDIUM | Reference resolution |

#### 3.6.2 Migration Pattern Analysis

**Pattern 1: Simple Deterministic** (300+ agents)
- Current: `generator.type: DETERMINISTIC` or rule-based
- Target: `identity.agentType: deterministic`
- Mapping: Direct field mapping with defaults

**Pattern 2: Pipeline/Hybrid** (150+ agents)
- Current: `generator.type: PIPELINE` with multiple steps
- Target: `identity.agentType: hybrid`
- Mapping: Complex reasoning profile construction

**Pattern 3: LLM-Based** (80+ agents)
- Current: `generator.steps` with LLM type
- Target: `identity.agentType: llm` or `hybrid`
- Mapping: LLM configuration extraction

**Pattern 4: Event-Driven** (50+ agents)
- Current: AEP operators with event types
- Target: `identity.agentType: reactive`
- Mapping: Event interface mapping

**Pattern 5: Complex Delegation** (100+ agents)
- Current: Rich `delegation` and `routing` structures
- Target: `interoperability.agentToAgent`
- Mapping: Complex protocol mapping

### 3.7 Critical Migration Challenges

#### 3.7.1 Field Mapping Complexity

**High-Complexity Mappings**:
1. **YAPPC Delegation → A2A Protocol**
   ```yaml
   # Current
   delegation:
     can_delegate_to: [agent1, agent2]
     escalates_to: supervisor
   
   # Target
   interoperability:
     agentToAgent:
       enabled: true
       delegationTargets: [agent1, agent2]
       escalationPolicy:
         target: supervisor
         condition: failure_threshold_exceeded
   ```

2. **Generator Pipeline → Reasoning Profile**
   ```yaml
   # Current
   generator:
     type: PIPELINE
     steps:
       - type: RULE_BASED
         rules: [...]
       - type: LLM
         provider: OPENAI
   
   # Target
   reasoningProfile:
     reasonerPortfolio:
       - type: rule-engine
         engine: drools
       - type: llm
         engine: openai/gpt-4
   ```

3. **Memory Configuration → Memory Model**
   ```yaml
   # Current
   memory:
     episodic:
       enabled: true
       retention_days: 90
   
   # Target
   memoryModel:
     memoryTypes: [episodic]
     retentionPolicy:
       episodic:
         retentionDays: 90
         archivalPolicy: delete
   ```

#### 3.7.2 Validation Requirements

**Product-Specific Validation Rules**:

1. **Platform Agents**: Must have `executionModel.invocationModes` including "event"
2. **AEP Agents**: Must have `aep.inputEventTypes` and `aep.outputEventTypes`
3. **Data Cloud Agents**: Must have `governance.dataHandling` with classification
4. **YAPPC Level 1**: Must have `identity.criticality: high`
5. **YAPPC Level 2**: Must have `reasoningProfile.reasonerPortfolio` with ≥2 reasoners
6. **YAPPC Level 3**: Must have `identity.autonomyLevel: assisted`

### 3.8 Migration Success Criteria

#### 3.8.1 Completeness Criteria

**Must Achieve**:
- [ ] **1,098 files migrated** - Every single YAML file converted
- [ ] **Zero format errors** - All files validate against new schema
- [ ] **Complete field mapping** - No data loss during migration
- [ ] **Reference resolution** - All cross-references preserved
- [ ] **Functionality preservation** - All agent behaviors maintained

#### 3.8.2 Quality Criteria

**Must Achieve**:
- [ ] **100% schema compliance** - All files pass validation
- [ ] **Semantic correctness** - Migrated agents behave identically
- [ ] **Performance parity** - No degradation in loading/execution
- [ ] **Documentation completeness** - All 20 sections populated where applicable

#### 3.8.3 Validation Requirements

**Pre-Migration**:
- [ ] Inventory all 1,098 files with metadata
- [ ] Create migration patterns for each agent type
- [ ] Develop automated validation rules
- [ ] Establish rollback procedures

**Post-Migration**:
- [ ] Load test all 1,098 agents
- [ ] Execute integration tests for each product
- [ ] Performance benchmark against baseline
- [ ] Production readiness validation

### 3.9 Risk Mitigation Strategies

#### 3.9.1 High-Risk Areas

**YAPPC Task Agents (162 files)**:
- **Risk**: Diverse patterns, complex delegation
- **Mitigation**: Template-based migration with manual review

**YAPPC Orchestrators (53 files)**:
- **Risk**: Critical business logic, complex workflows
- **Mitigation**: Incremental migration with parallel testing

**Cross-Product References**:
- **Risk**: Broken references between products
- **Mitigation**: Reference mapping table and validation

#### 3.9.2 Rollback Strategy

**Immediate Rollback** (≤1 hour):
- Git revert migration branch
- Restore old Java classes
- Restart services with old format

**Partial Rollback** (≤4 hours):
- Identify problematic agents
- Revert specific agent files
- Update Java code for dual-format support (temporary)

**Data Recovery**:
- All original YAML files preserved in git
- Automated backup before migration
- Migration tool generates rollback scripts

---

## 2. Agent Framework Implementation Plan

### 2.1 Current Framework Analysis

The existing `platform/java/agent-framework` provides a solid foundation but requires significant enhancements to support the unified specification:

**Current Strengths**:
- ✅ Basic `Agent` interface with async processing (now `@Deprecated` — use `TypedAgent<I,O>`)
- ✅ `AgentType` enum with **9 built-in types** (DETERMINISTIC, PROBABILISTIC, STREAM_PROCESSOR, PLANNING, HYBRID, ADAPTIVE, COMPOSITE, REACTIVE, CUSTOM)
- ✅ Custom type registration mechanism
- ✅ Agent catalog and registry infrastructure (`PlannerRegistry` replacing colliding `AgentRegistry`)
- ✅ ActiveJ-based async execution model
- ✅ `PlanningAgent` abstract base class (PLAN→EXECUTE→OBSERVE→REPLAN lifecycle)
- ✅ `StreamProcessorAgent` abstract base class (event loop + checkpoint/restore)
- ✅ `PlanningSubtype` enum (HTN, REACT, TOT, WORKFLOW, OBJECTIVE_DECOMPOSITION)
- ✅ `StreamProcessorSubtype` enum (INGESTION, ROUTING, TRANSFORMATION, CEP, ENRICHMENT, WINDOW_AGGREGATION, FILTER)
- ✅ Enhanced `DeterministicSubtype` (8 values including POLICY, OPERATOR, TEMPLATE)
- ✅ Enhanced `ProbabilisticSubtype` (5 values including CLASSIFIER)

**Remaining Gaps** (as of v3.3.0 — previously incomplete, now resolved):
- ✅ RESOLVED — `ToolRegistry`: `com.ghatana.agent.framework.tools.ToolRegistry`
- ✅ RESOLVED — `GovernanceEngine`: `com.ghatana.agent.framework.governance.GovernanceEngine`
- ✅ RESOLVED — `LearningEngine`: `com.ghatana.agent.framework.learning.LearningEngine`
- ✅ RESOLVED — `InteroperabilityService` (MCP + A2A): `com.ghatana.agent.framework.interop.InteroperabilityService`
- ✅ RESOLVED — `MemoryPlane` typed interface: `com.ghatana.agent.framework.memory.MemoryPlane`
- ✅ RESOLVED — JSON schemas: `stream-processor-agent-schema.json`, `planning-agent-schema.json`

**Still Pending** (Phase 5 / Phase 6):
- ✅ RESOLVED (v3.4.0) — YAPPC capability agents (52 files) migrated
- ✅ RESOLVED (v3.4.0) — YAPPC task-agents (162 files) migrated
- ✅ RESOLVED (v3.4.0) — YAPPC micro-agents (111 files) migrated
- ✅ RESOLVED (v3.4.0) — YAPPC catalogs (26 catalog YAMLs) enriched
- ✅ RESOLVED (v3.5.0) — AgentSpec YAML deserialization layer: `AgentSpec`, `AgentSpecLoader`, `GovernancePolicyRef` in `com.ghatana.agent.framework.spec` package
- ✅ RESOLVED (v3.5.0) — JSON schema enriched to v2.0.0 with all 18 sections as `$defs`
- ✅ RESOLVED (v3.2.0+) — Governance/security integration: `AgentAuthorizationService` (ABAC), `SecretProvider` (tenant-scoped), `GovernanceEngine` (policy enforcement)
- ✅ RESOLVED (v3.6.0) — `extractDefinition()` IOContract NPE bug: null `format` replaced with `deriveFormat()` helper
- ✅ RESOLVED (v3.6.0) — `extractDefinition()` missing field mappings: `autonomyLevel`, `criticality` (labels), `maxCostPerCall` (from `governance.riskProfile`)
- ✅ RESOLVED (v3.6.0) — `AgentDefinitionLoader` ↔ `AgentSpecLoader` bridge: auto-detects `agentSpecVersion:` to transparently handle both YAML formats
- ✅ RESOLVED (v3.6.0) — Deprecated `AgentRegistry` planner class DELETED (was a no-op subclass of `PlannerRegistry`)
- ✅ RESOLVED (v3.6.0) — Workflow layer (`WorkflowAgentRegistry`, `InMemoryWorkflowAgentRegistry`, `DefaultWorkflowAgentService`) fully migrated from deprecated `Agent` to canonical `TypedAgent<?,?>`
- ✅ RESOLVED (v3.6.0) — `PlannerAgentFactory` final deprecation warning removed (`AgentType.LLM` reference → `AgentType.PROBABILISTIC`)

**No planned migration items remaining.** All phases complete as of v3.6.0.

> **Audit findings fully resolved (v3.6.0):** Full codebase audit March 2026 found 5 issues: (1) `IOContract` NPE — fixed; (2) incomplete `extractDefinition()` field mapping — fixed; (3) no bridge between old and new loader — fixed; (4) deprecated `AgentRegistry` planner class — removed; (5) workflow package using deprecated `Agent` — migrated to `TypedAgent`. Framework is now free of deprecated API usage in production code.

### 2.2 Enhanced AgentType Enum

**Update Required**: Simplify to 9 core agent types with clear boundaries, removing redundancies and adding STREAM_PROCESSOR for AEP operators

```java
public enum AgentType {
    // ═══════════════════════════════════════════════════════════════════════
    // DETERMINISTIC: Always produces same output for same input
    // ═══════════════════════════════════════════════════════════════════════
    DETERMINISTIC,   // Rule-based, FSM, pattern matching, thresholds, policy evaluation
                     // Subtypes: rule-engine, fsm, pattern-matcher, threshold-evaluator, policy-engine
    
    // ═══════════════════════════════════════════════════════════════════════
    // PROBABILISTIC: Output varies based on confidence/sampling
    // ═══════════════════════════════════════════════════════════════════════
    PROBABILISTIC,   // ML models, Bayesian inference, LLMs, classifiers
                     // Subtypes: llm, ml-model, bayesian, classifier
    
    // ═══════════════════════════════════════════════════════════════════════
    // STREAM_PROCESSOR: Event-driven, stateful stream processing (AEP operators)
    // ═══════════════════════════════════════════════════════════════════════
    STREAM_PROCESSOR, // Event ingestion, routing, transformation, CEP
                        // Characteristics: backpressure, checkpointing, windowing
    
    // ═══════════════════════════════════════════════════════════════════════
    // PLANNING: Goal-directed multi-step execution
    // ═══════════════════════════════════════════════════════════════════════
    PLANNING,       // HTN, ReAct, tree-of-thought, workflow orchestration
    
    // ═══════════════════════════════════════════════════════════════════════
    // HYBRID: Combines multiple reasoning types with intelligent routing
    // ═══════════════════════════════════════════════════════════════════════
    HYBRID,         // Multi-reasoner with deterministic→probabilistic fallback
    
    // ═══════════════════════════════════════════════════════════════════════
    // ADAPTIVE: Self-tuning with online learning
    // ═══════════════════════════════════════════════════════════════════════
    ADAPTIVE,       // Bandits, RL, A/B testing, parameter optimization
    
    // ═══════════════════════════════════════════════════════════════════════
    // COMPOSITE: Ensemble of sub-agents with aggregation
    // ═══════════════════════════════════════════════════════════════════════
    COMPOSITE,      // Voting, fan-out/fan-in, conditional routing ensembles
    
    // ═══════════════════════════════════════════════════════════════════════
    // REACTIVE: Event-triggered reflex actions (stateless)
    // ═══════════════════════════════════════════════════════════════════════
    REACTIVE,       // Simple triggers, alerts, circuit-breakers
    
    // ═══════════════════════════════════════════════════════════════════════
    // CUSTOM: Domain-specific extensions
    // ═══════════════════════════════════════════════════════════════════════
    CUSTOM;         // User-defined types via registry
}
```

**Rationale for Simplification**:
- **RULE_BASED** → Merged into DETERMINISTIC (subtype: rule-engine)
- **POLICY** → Merged into DETERMINISTIC (subtype: policy-engine)  
- **PATTERN** → Merged into DETERMINISTIC (subtype: pattern-matcher)
- **LLM** → Moved to PROBABILISTIC subtype (one of many probabilistic approaches)
- **STREAM_PROCESSOR** → NEW type for AEP operators with stateful stream processing

### 2.3 Agent Type Implementation Matrix (9 Core Types)

| Agent Type | Core Characteristic | Subtypes | AEP Support | YAPPC Support | Use Cases |
|------------|---------------------|----------|-------------|---------------|-----------|
| **DETERMINISTIC** | Reproducible output | rule-engine, fsm, pattern-matcher, policy-engine, threshold-evaluator | ✅ (simple filters) | ✅ (validation, parsing, policy enforcement) | Event routing, validation, thresholds, governance |
| **PROBABILISTIC** | Confidence-based | llm, ml-model, bayesian, classifier | ✅ (anomaly detection) | ✅ (code gen, analysis, classification) | LLM reasoning, ML inference, Bayesian nets |
| **STREAM_PROCESSOR** | Stateful stream processing | ingestion, routing, transformation, cep, window-agg | ✅✅✅ (primary type) | ⚠️ (event-driven features) | AEP operators, event streaming, CEP |
| **PLANNING** | Goal-directed multi-step | htn, react, tot, workflow | ❌ | ✅✅✅ (orchestrators) | Task orchestration, multi-step workflows |
| **HYBRID** | Multi-reasoner routing | rule+llm, template+llm, deterministic→probabilistic | ⚠️ | ✅✅✅ (Java Expert) | Complex reasoning with fallback |
| **ADAPTIVE** | Self-tuning learning | bandit, rl, ab-test, parameter-opt | ✅ (adaptive routing) | ✅ (parameter tuning) | Online learning, self-optimization |
| **COMPOSITE** | Distributed ensemble | voting, fan-out-in, conditional-routing | ✅ (multi-path) | ✅ (multi-model consensus) | Ensemble agents, parallel execution |
| **REACTIVE** | Stateless trigger-action | alert, trigger, circuit-breaker | ✅ (simple alerts) | ⚠️ (simple actions) | Simple reflexes, alerts |
| **CUSTOM** | Domain-specific | user-defined via registry | ✅ | ✅ | Special domain requirements |

**Total**: 9 core types (down from 12) — 25% simpler taxonomy with clearer boundaries

### 2.4 Agent Type Boundaries and Distinctions

#### 2.4.1 Clear Type Boundaries

| Type | Input→Output | State Model | Key Distinction | When to Use |
|------|--------------|-------------|-----------------|-------------|
| **DETERMINISTIC** | Same→Same | Deterministic FSM/Rule state | Guaranteed identical results | Validation, thresholds, policy enforcement |
| **PROBABILISTIC** | Same→Variable | Model weights, sampling | Results vary with confidence | LLM reasoning, ML classification |
| **STREAM_PROCESSOR** | Events→Events/State | Window state, checkpoints | Stateful stream processing with backpressure | AEP operators, event streaming, CEP |
| **PLANNING** | Goal→Plan→Execution | Plan state, subgoal tracking | Multi-step with explicit planning phase | Workflow orchestration, multi-step tasks |
| **HYBRID** | Input→Routed→Combined | Reasoner selection state | Combines 2+ reasoning types intelligently | Complex reasoning requiring fallback |
| **ADAPTIVE** | Input→Output→Feedback | Learning parameters, exploration | Improves over time with feedback | Self-tuning parameters, RL |
| **COMPOSITE** | Input→FanOut→Aggregate | Sub-agent coordination | Multiple agents in parallel/sequence | Consensus, parallel execution |
| **REACTIVE** | Event→Immediate Response | Minimal/transient | Simple reflex, no complex reasoning | Alerts, triggers, circuit-breakers |
| **CUSTOM** | Variable | Variable | Extensible for special cases | Domain-specific requirements |

#### 2.4.2 Type Separation Guidelines

**DETERMINISTIC vs PROBABILISTIC**:
- DETERMINISTIC: Output is 100% reproducible given same input
- PROBABILISTIC: Output varies due to sampling, model weights, or confidence thresholds

**STREAM_PROCESSOR vs REACTIVE**:
- STREAM_PROCESSOR: Stateful, complex event processing with windows/aggregations, checkpoint recovery
- REACTIVE: Stateless, immediate trigger→action, no persistence or complex logic

**HYBRID vs COMPOSITE**:
- HYBRID: Single agent with multiple internal reasoners, intelligent routing between them
- COMPOSITE: Multiple independent sub-agents, orchestrated with voting/aggregation

**PLANNING vs HYBRID**:
- PLANNING: Goal-directed, creates explicit plan before execution, handles multi-step workflows
- HYBRID: Input-directed, routes to appropriate reasoner, handles single-step with complexity

### 2.5 Agent Subtype Configuration

```java
public interface AgentSubtype {
    String getSubtypeName();
    AgentCharacteristics getCharacteristics();
}

// DETERMINISTIC subtypes
enum DeterministicSubtype {
    RULE_ENGINE("rule-engine"),      // Drools, OPA condition-action
    FSM("fsm"),                       // Finite state machines
    PATTERN_MATCHER("pattern-matcher"), // Template/pattern matching
    POLICY_ENGINE("policy-engine"),   // Policy evaluation, governance
    THRESHOLD_EVAL("threshold-eval");   // Threshold-based evaluation
    
    private final String name;
    DeterministicSubtype(String name) { this.name = name; }
    public String getName() { return name; }
}

// PROBABILISTIC subtypes
enum ProbabilisticSubtype {
    LLM("llm"),              // GPT, Claude, Llama, etc.
    ML_MODEL("ml-model"),    // sklearn, tensorflow, pytorch
    BAYESIAN("bayesian"),    // Bayesian networks, probabilistic graphs
    CLASSIFIER("classifier"); // SVM, random forest, XGBoost
    
    private final String name;
    ProbabilisticSubtype(String name) { this.name = name; }
    public String getName() { return name; }
}

// STREAM_PROCESSOR subtypes (for AEP operators)
enum StreamProcessorSubtype {
    INGESTION("ingestion"),        // HTTP, Kafka, file ingestion
    ROUTING("routing"),            // Content-based routing, fan-out
    TRANSFORMATION("transformation"), // Event mapping, enrichment
    CEP("cep"),                    // Complex event processing
    WINDOW_AGG("window-agg");      // Windowed aggregations
    
    private final String name;
    StreamProcessorSubtype(String name) { this.name = name; }
    public String getName() { return name; }
}

// Subtype specification in agent identity
public class AgentIdentity {
    private AgentType agentType;
    private String agentSubtype;  // e.g., "rule-engine", "llm", "ingestion"
    private AgentCharacteristics characteristics;
}
```

### 2.6 AEP Operator Agent Type Mapping

All AEP operators are mapped to **STREAM_PROCESSOR** type with appropriate subtypes:

```yaml
# HTTP Ingestion Agent
identity:
  agentType: stream_processor
  agentSubtype: ingestion
  characteristics:
    backpressure: true
    checkpointing: true
    eventDriven: true

# Event Router Agent  
identity:
  agentType: stream_processor
  agentSubtype: routing
  characteristics:
    contentBasedRouting: true
    fanOut: true
    stateful: true

# Event Transformation Agent
identity:
  agentType: stream_processor
  agentSubtype: transformation
  characteristics:
    eventMapping: true
    enrichment: true
    stateful: true

# Anomaly Detection (AEP Pattern)
identity:
  agentType: stream_processor
  agentSubtype: cep
  characteristics:
    patternMatching: true
    windowedProcessing: true
    probabilisticDetection: true  # Hybrid: uses probabilistic subtype internally
```

### 2.7 Framework Architecture Enhancement

#### 2.4.1 Core Interface Updates

**Enhanced Agent Interface**:
```java
public interface Agent {
    // Existing methods
    String getId();
    AgentCapabilities getCapabilities();
    Promise<Void> initialize(AgentContext context);
    Promise<Void> start();
    <T, R> Promise<R> process(T task, AgentContext context);
    Promise<Void> shutdown();
    
    // NEW: Unified specification support
    AgentSpec getSpec();
    AgentInstance getInstance();
    Promise<AgentResult> execute(AgentRequest request, AgentExecutionContext executionContext);
    Promise<AgentHealth> healthCheck();
    AgentMetrics getMetrics();
}
```

**New AgentSpec Interface**:
```java
public interface AgentSpec {
    String getAgentSpecVersion();
    Metadata getMetadata();
    Identity getIdentity();
    PurposeModel getPurposeModel();
    Scope getScope();
    Capabilities getCapabilities();
    ReasoningProfile getReasoningProfile();
    ExecutionModel getExecutionModel();
    Interfaces getInterfaces();
    MemoryModel getMemoryModel();
    ToolsAndResources getToolsAndResources();
    Governance getGovernance();
    LearningModel getLearningModel();
    Evaluation getEvaluation();
    Observability getObservability();
    Interoperability getInteroperability();
    Security getSecurity();
    Deployment getDeployment();
    Documentation getDocumentation();
    Examples getExamples();
    Extensibility getExtensibility();
}
```

#### 2.4.2 Agent Type Implementations

**Rule-Based Agent Implementation**:
```java
public class RuleBasedAgent extends AbstractAgent {
    private final RuleEngine ruleEngine;
    private final RuleConditionEvaluator conditionEvaluator;
    
    @Override
    protected Promise<AgentResult> executeInternal(AgentRequest request, AgentExecutionContext context) {
        return ruleEngine.evaluateRules(request, context)
            .map(results -> AgentResult.success(results));
    }
    
    // Rule engine selection: Drools vs OPA vs custom
    private RuleEngine createRuleEngine(ReasonerConfig config) {
        return switch (config.getEngine()) {
            case DROOLS -> new DroolsRuleEngine(config);
            case OPA -> new OPARuleEngine(config);
            case CUSTOM -> new CustomRuleEngine(config);
        };
    }
}
```

**Policy Agent Implementation**:
```java
public class PolicyAgent extends AbstractAgent {
    private final PolicyEngine policyEngine;
    private final ConstraintEvaluator constraintEvaluator;
    
    @Override
    protected Promise<AgentResult> executeInternal(AgentRequest request, AgentExecutionContext context) {
        return policyEngine.evaluatePolicies(request, context)
            .flatMap(policies -> constraintEvaluator.validateConstraints(request, policies))
            .map(results -> AgentResult.success(results));
    }
}
```

**Pattern Agent Implementation**:
```java
public class PatternAgent extends AbstractAgent {
    private final TemplateEngine templateEngine;
    private final PatternLibrary patternLibrary;
    
    @Override
    protected Promise<AgentResult> executeInternal(AgentRequest request, AgentExecutionContext context) {
        return patternLibrary.matchPattern(request)
            .flatMap(pattern -> templateEngine.render(pattern, request))
            .map(result -> AgentResult.success(result));
    }
}
```

**Planning Agent Implementation**:
```java
public class PlanningAgent extends AbstractAgent {
    private final Planner planner;
    private final GoalDecomposer goalDecomposer;
    
    @Override
    protected Promise<AgentResult> executeInternal(AgentRequest request, AgentExecutionContext context) {
        return goalDecomposer.decompose(request.getGoal())
            .flatMap(subgoals -> planner.createPlan(subgoals, context))
            .flatMap(plan -> executePlan(plan, context))
            .map(result -> AgentResult.success(result));
    }
    
    private Planner createPlanner(PlannerConfig config) {
        return switch (config.getType()) {
            case HTN -> new HTNPlanner(config);
            case REACT -> new ReActPlanner(config);
            case TREE_OF_THOUGHT -> new ToTPlanner(config);
        };
    }
}
```

#### 2.4.3 Memory Model Implementation

**Memory System Architecture**:
```java
public interface MemoryModel {
    List<MemoryBinding> getMemoryBindings();
    List<MemoryType> getMemoryTypes();
    List<ReadStrategy> getReadStrategies();
    WritePolicies getWritePolicies();
    Optional<ConsolidationRules> getConsolidationRules();
    Optional<RetentionPolicy> getRetentionPolicy();
}

public class MemorySystem {
    private final Map<MemoryType, MemoryStore> stores;
    private final ReadStrategyManager readStrategyManager;
    private final WritePolicyManager writePolicyManager;
    
    public Promise<MemoryResult> read(MemoryRequest request) {
        MemoryType type = request.getMemoryType();
        ReadStrategy strategy = readStrategyManager.selectStrategy(request);
        return stores.get(type).read(request, strategy);
    }
    
    public Promise<MemoryResult> write(MemoryRequest request) {
        WritePolicy policy = writePolicyManager.evaluatePolicy(request);
        if (!policy.isAllowed()) {
            return Promise.ofException(new MemoryWriteDeniedException(policy.getReason()));
        }
        return stores.get(request.getMemoryType()).write(request);
    }
}
```

#### 2.4.4 Tool and Resource Management

**Tool System Architecture**:
```java
public interface Tool {
    String getId();
    String getName();
    ToolType getType();
    ToolAccess getAccess();
    String getPurpose();
    Promise<ToolResult> invoke(ToolRequest request, AgentContext context);
}

public class ToolRegistry {
    private final Map<String, Tool> tools;
    private final ToolSelectionPolicy selectionPolicy;
    
    public Promise<Tool> selectTool(ToolSelectionRequest request) {
        List<Tool> candidates = tools.values().stream()
            .filter(tool -> tool.supports(request.getCapability()))
            .filter(tool -> selectionPolicy.isAllowed(tool, request))
            .collect(Collectors.toList());
            
        return Promise.of(selectionPolicy.select(candidates, request));
    }
}
```

#### 2.4.5 Governance and Security Integration

**Governance Framework**:
```java
public interface Governance {
    List<PolicyReference> getPolicyRefs();
    Optional<Approvals> getApprovals();
    DataHandling getDataHandling();
    RiskProfile getRiskProfile();
}

public class GovernanceEngine {
    private final PolicyEvaluator policyEvaluator;
    private final ApprovalManager approvalManager;
    private final DataClassificationService dataClassificationService;
    
    public Promise<GovernanceResult> evaluate(AgentRequest request, AgentExecutionContext context) {
        return policyEvaluator.evaluatePolicies(request)
            .flatMap(policies -> approvalManager.checkApprovals(request, policies))
            .flatMap(approvals -> dataClassificationService.classifyData(request))
            .map(result -> GovernanceResult.success(result));
    }
}
```

### 2.5 Process Definition Agent Support

#### 2.5.1 BPMN Workflow Integration

**Process Definition Agent**:
```java
public class ProcessDefinitionAgent extends AbstractAgent {
    private final BPMNEngine bpmnEngine;
    private final WorkflowOrchestrator orchestrator;
    
    @Override
    protected Promise<AgentResult> executeInternal(AgentRequest request, AgentExecutionContext context) {
        return bpmnEngine.parseProcessDefinition(request.getProcessDefinition())
            .flatMap(process -> orchestrator.execute(process, request.getInputs()))
            .map(result -> AgentResult.success(result));
    }
}
```

**Supported Process Formats**:
- **BPMN 2.0**: Business Process Model and Notation
- **YAML Workflows**: Simple YAML-based workflow definitions
- **JSON Processes**: JSON-based process definitions
- **Custom DSL**: Domain-specific process languages

#### 2.5.2 Workflow Execution Engine

```java
public interface WorkflowEngine {
    Promise<WorkflowExecution> start(WorkflowDefinition definition, Map<String, Object> inputs);
    Promise<WorkflowExecution> resume(String executionId, Map<String, Object> inputs);
    Promise<WorkflowExecution> cancel(String executionId);
    Promise<WorkflowStatus> getStatus(String executionId);
}

public class BPMNWorkflowEngine implements WorkflowEngine {
    private final BPMNParser parser;
    private final ActivityExecutor activityExecutor;
    private final GatewayEvaluator gatewayEvaluator;
    
    @Override
    public Promise<WorkflowExecution> start(WorkflowDefinition definition, Map<String, Object> inputs) {
        return parser.parse(definition)
            .flatMap(process -> executeProcess(process, inputs));
    }
}
```

### 2.6 Interoperability Protocol Implementation

#### 2.6.1 Model Context Protocol (MCP)

**MCP Implementation**:
```java
public interface MCPProtocol {
    Promise<MCPResponse> handleRequest(MCPRequest request);
    List<MCPTool> getAvailableTools();
    MCPResource getResource(String uri);
    Promise<MCPResource> updateResource(String uri, MCPResource resource);
}

public class MCPAgent implements MCPProtocol {
    private final Agent agent;
    private final ToolRegistry toolRegistry;
    
    @Override
    public Promise<MCPResponse> handleRequest(MCPRequest request) {
        return switch (request.getMethod()) {
            case "tools/list" -> listTools();
            case "tools/call" -> callTool(request);
            case "resources/read" -> readResource(request);
            case "resources/write" -> writeResource(request);
            default -> Promise.ofException(new MCPMethodNotSupportedException(request.getMethod()));
        };
    }
}
```

#### 2.6.2 Agent-to-Agent (A2A) Protocol

**A2A Implementation**:
```java
public interface A2AProtocol {
    Promise<A2AResponse> sendMessage(String targetAgentId, A2AMessage message);
    Promise<A2AResponse> delegateTask(String targetAgentId, AgentTask task);
    Promise<A2AResponse> requestHandoff(String targetAgentId, HandoffRequest request);
    void registerMessageHandler(A2AMessageHandler handler);
}

public class A2AAgent implements A2AProtocol {
    private final AgentRegistry agentRegistry;
    private final MessageRouter messageRouter;
    private final DelegationManager delegationManager;
    
    @Override
    public Promise<A2AResponse> delegateTask(String targetAgentId, AgentTask task) {
        return agentRegistry.getAgent(targetAgentId)
            .flatMap(agent -> agent.process(task, createDelegationContext()))
            .map(result -> A2AResponse.success(result));
    }
}
```

### 2.7 Learning Model Implementation

#### 2.7.1 Learning Level Support

**Learning Framework**:
```java
public enum LearningLevel {
    L0, // No learning
    L1, // Static parameter updates
    L2, // Online learning with feedback
    L3, // Adaptive strategy selection
    L4, // Self-modifying behavior
    L5  // Autonomous learning and improvement
}

public interface LearningModel {
    LearningLevel getLearningLevel();
    List<AdaptationTarget> getAdaptationTargets();
    List<LearningSource> getLearningSources();
    Optional<DriftControls> getDriftControls();
    Optional<RollbackPolicy> getRollbackPolicy();
}
```

**Adaptive Learning Implementation**:
```java
public class AdaptiveLearningAgent extends AbstractAgent {
    private final LearningEngine learningEngine;
    private final DriftDetector driftDetector;
    private final RollbackManager rollbackManager;
    
    @Override
    protected Promise<AgentResult> executeInternal(AgentRequest request, AgentExecutionContext context) {
        return learningEngine.processWithLearning(request, context)
            .flatMap(result -> driftDetector.checkDrift(result))
            .flatMap(driftResult -> handleDrift(driftResult, result));
    }
}
```

### 2.8 Migration Implementation Tasks

#### 2.8.1 Phase 1: Core Framework Updates (Week 1-2)

**Task 1.1: Update AgentType Enum**
```bash
# Files to modify
- platform/java/agent-framework/src/main/java/com/ghatana/agent/AgentType.java

# Changes required
- Add RULE_BASED, POLICY, PATTERN, PLANNING enums
- Update resolve() method for new types
- Update documentation
```

**Task 1.2: Create New Agent Implementations**
```bash
# New files to create
- platform/java/agent-framework/src/main/java/com/ghatana/agent/rulebased/
  - RuleBasedAgent.java
  - DroolsReasoner.java
  - OPAReasoner.java
  - RuleConditionEvaluator.java

- platform/java/agent-framework/src/main/java/com/ghatana/agent/policy/
  - PolicyAgent.java
  - PolicyEngine.java
  - ConstraintEvaluator.java
  - CostPolicyEvaluator.java

- platform/java/agent-framework/src/main/java/com/ghatana/agent/pattern/
  - PatternAgent.java
  - TemplateEngine.java
  - PatternLibrary.java
  - PatternMatcher.java

- platform/java/agent-framework/src/main/java/com/ghatana/agent/planning/
  - PlanningAgent.java
  - HTNPlanner.java
  - ReActPlanner.java
  - ToTPlanner.java
  - GoalDecomposer.java
```

**Task 1.3: Enhanced AgentSpec Interface**
```bash
# Files to create
- platform/java/agent-framework/src/main/java/com/ghatana/agent/spec/
  - AgentSpec.java
  - Metadata.java
  - Identity.java
  - PurposeModel.java
  - Scope.java
  - Capabilities.java
  - ReasoningProfile.java
  - ExecutionModel.java
  - Interfaces.java
  - MemoryModel.java
  - ToolsAndResources.java
  - Governance.java
  - LearningModel.java
  - Evaluation.java
  - Observability.java
  - Interoperability.java
  - Security.java
  - Deployment.java
  - Documentation.java
  - Examples.java
  - Extensibility.java
```

#### 2.8.2 Phase 2: Memory and Tool Systems (Week 3-4)

**Task 2.1: Memory Model Implementation**
```bash
# Files to create
- platform/java/agent-framework/src/main/java/com/ghatana/agent/memory/
  - MemoryModel.java
  - MemorySystem.java
  - MemoryStore.java
  - WorkingMemory.java
  - EpisodicMemory.java
  - SemanticMemory.java
  - ProceduralMemory.java
  - ReadStrategy.java
  - WritePolicy.java
  - ConsolidationRules.java
  - RetentionPolicy.java
```

**Task 2.2: Tool and Resource Management**
```bash
# Files to create
- platform/java/agent-framework/src/main/java/com/ghatana/agent/tools/
  - Tool.java
  - ToolRegistry.java
  - ToolSelectionPolicy.java
  - MCPTool.java
  - ServiceTool.java
  - DatabaseTool.java
  - FileTool.java
```

#### 2.8.3 Phase 3: Governance and Security (Week 5-6)

**Task 3.1: Governance Framework**
```bash
# Files to create
- platform/java/agent-framework/src/main/java/com/ghatana/agent/governance/
  - Governance.java
  - GovernanceEngine.java
  - PolicyEvaluator.java
  - ApprovalManager.java
  - DataClassificationService.java
  - RiskAssessment.java
```

**Task 3.2: Security Implementation**
```bash
# Files to create
- platform/java/agent-framework/src/main/java/com/ghatana/agent/security/
  - Authentication.java
  - Authorization.java
  - SecretsManager.java
  - NetworkPolicy.java
  - SecurityContext.java
```

#### 2.8.4 Phase 4: Process Definition and Interoperability (Week 7-8)

**Task 4.1: Process Definition Support**
```bash
# Files to create
- platform/java/agent-framework/src/main/java/com/ghatana/agent/process/
  - ProcessDefinitionAgent.java
  - BPMNEngine.java
  - WorkflowOrchestrator.java
  - WorkflowEngine.java
  - BPMNParser.java
  - ActivityExecutor.java
  - GatewayEvaluator.java
```

**Task 4.2: Interoperability Protocols**
```bash
# Files to create
- platform/java/agent-framework/src/main/java/com/ghatana/agent/interop/
  - MCPProtocol.java
  - MCPAgent.java
  - A2AProtocol.java
  - A2AAgent.java
  - MessageRouter.java
  - DelegationManager.java
```

#### 2.8.5 Phase 5: Learning and Adaptation (Week 9-10)

**Task 5.1: Learning Model Implementation**
```bash
# Files to create
- platform/java/agent-framework/src/main/java/com/ghatana/agent/learning/
  - LearningModel.java
  - LearningEngine.java
  - DriftDetector.java
  - RollbackManager.java
  - AdaptiveLearningAgent.java
  - BanditAlgorithm.java
  - RLAgent.java
```

### 2.9 Testing Strategy

#### 2.9.1 Unit Tests for Each Agent Type

**Test Coverage Requirements**:
- Each agent type: 95% code coverage
- Each reasoner implementation: 90% code coverage
- Memory system: 95% code coverage
- Tool system: 90% code coverage
- Governance: 95% code coverage

**Test Structure**:
```bash
platform/java/agent-framework/src/test/java/com/ghatana/agent/
├── deterministic/
│   ├── DeterministicAgentTest.java
│   ├── RuleEngineTest.java
│   └── FiniteStateMachineTest.java
├── rulebased/
│   ├── RuleBasedAgentTest.java
│   ├── DroolsReasonerTest.java
│   └── OPAReasonerTest.java
├── policy/
│   ├── PolicyAgentTest.java
│   ├── PolicyEngineTest.java
│   └── ConstraintEvaluatorTest.java
├── pattern/
│   ├── PatternAgentTest.java
│   ├── TemplateEngineTest.java
│   └── PatternLibraryTest.java
├── planning/
│   ├── PlanningAgentTest.java
│   ├── HTNPlannerTest.java
│   ├── ReActPlannerTest.java
│   └── ToTPlannerTest.java
├── memory/
│   ├── MemorySystemTest.java
│   ├── WorkingMemoryTest.java
│   ├── EpisodicMemoryTest.java
│   └── SemanticMemoryTest.java
├── tools/
│   ├── ToolRegistryTest.java
│   ├── MCPToolTest.java
│   └── ServiceToolTest.java
├── governance/
│   ├── GovernanceEngineTest.java
│   ├── PolicyEvaluatorTest.java
│   └── DataClassificationServiceTest.java
├── process/
│   ├── ProcessDefinitionAgentTest.java
│   ├── BPMNEngineTest.java
│   └── WorkflowOrchestratorTest.java
├── interop/
│   ├── MCPProtocolTest.java
│   ├── A2AProtocolTest.java
│   └── MessageRouterTest.java
└── learning/
    ├── LearningEngineTest.java
    ├── DriftDetectorTest.java
    └── AdaptiveLearningAgentTest.java
```

#### 2.9.2 Integration Tests

**Integration Test Scenarios**:
1. **Multi-reasoner Agents**: Test hybrid agents with multiple reasoners
2. **Memory Integration**: Test agents with different memory types
3. **Tool Integration**: Test agents using various tools
4. **Governance Integration**: Test policy enforcement and approval flows
5. **Process Definition**: Test BPMN workflow execution
6. **Interoperability**: Test MCP and A2A protocols
7. **Learning Adaptation**: Test adaptive learning and drift detection

#### 2.9.3 Performance Tests

**Performance Benchmarks**:
- **Deterministic Agents**: Sub-millisecond latency
- **Rule-Based Agents**: <5ms latency for rule evaluation
- **Policy Agents**: <10ms latency for policy evaluation
- **LLM Agents**: <5s latency for LLM calls
- **Memory Operations**: <1ms for working memory, <10ms for episodic memory
- **Tool Invocation**: <100ms for local tools, <5s for remote tools

---

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

### Phase 1: Foundation (Weeks 1-2) ✅ COMPLETE

**Goal**: Finalize agent spec, establish unified schema, template engine, and instantiation framework

**Tasks**:

0. **Spec Enhancement** (MUST complete first)
   - Add `determinismGuarantee`, `stateMutability`, `failureMode` to `identity` section in `agent-spec.md`
   - Add `supportedModalities` to `interfaces` section in `agent-spec.md`
   - Add `promptVersioning` to `learningModel` section in `agent-spec.md`
   - Update `identity.agentType` enum to 9 values (consolidated from 12: removed rule-based/policy/pattern/llm as standalone types, added stream_processor for AEP)
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

**COMPLETED**:
- ✅ `agent-spec.md` updated with 9-type taxonomy, disambiguation rules, JAVA ALIGNMENT section
- ✅ `catalog-schema.yaml` v2.0.0 — `identity` block replaces `generator:`, `generator:` marked deprecated
- ✅ `base-agent-template.yaml` — `generator:` block replaced with `identity:` block
- ✅ `AgentType` enum updated (STREAM_PROCESSOR + PLANNING added, LLM deprecated)
- ✅ All subtype enums updated/created (`DeterministicSubtype`, `ProbabilisticSubtype`, `StreamProcessorSubtype`, `PlanningSubtype`)
- ✅ `PlanningAgent` + `StreamProcessorAgent` abstract base classes created
- ✅ `PlannerRegistry` created, naming collision resolved

---

### Phase 2: Platform Agents (Weeks 3-4) ✅ COMPLETE

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

**COMPLETED** (all 20 platform agents migrated):
- ✅ **event-processing** (4): event-router, event-enricher, event-filter, event-transformer — migrated to `stream-processor` agentType
- ✅ **monitoring** (3): anomaly-detector (`adaptive`), health-checker (`stream-processor/ingestion`), metrics-collector (`stream-processor/ingestion`)
- ✅ **governance** (2): cost-optimization (`hybrid`), sustainability (`adaptive`)
- ✅ **security** (1): access-control (`deterministic/policy`)
- ✅ **data-processing** (2): data-transformation (`hybrid`), schema-validation (`deterministic/rule-based`)
- ✅ **domain-agents** (4): healthcare/patient-data (`deterministic/rule-based`), retail/inventory (`adaptive`), manufacturing/quality-control (`deterministic/threshold`), finance/financial-analysis (`deterministic/rule-based`)
- ✅ **composite-agents** (1): etl-pipeline (`composite`)
- ✅ **templates** (3): deterministic, adaptive, composite templates updated to `identity:` format
- ✅ All YAMLs now have: `namespace`, `status`, `owners`, `summary`, `identity` block
- ✅ All YAMLs use `interfaces:` (not `io:`) for input/output declarations
- ✅ `catalog-schema.yaml` updated to v2.0.0; `generator:` deprecated
- ✅ `base-agent-template.yaml` updated; `generator:` block replaced with `identity:`

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
   - Update `AgentType.java` enum with all 9 agent types (consolidated from 12: rule-based/policy/pattern merged into deterministic as subtypes, llm moved to probabilistic subtype, stream_processor added for AEP)
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
6. **`AgentType.java` enum**: Updated to include 9 core values: `DETERMINISTIC`, `PROBABILISTIC`, `STREAM_PROCESSOR`, `PLANNING`, `HYBRID`, `ADAPTIVE`, `COMPOSITE`, `REACTIVE`, `CUSTOM` (consolidated from 12 types with rule-based/policy/pattern merged into deterministic as subtypes, llm moved to probabilistic subtype)
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
| Agent Type Enum Alignment     | 9 types   | Java `AgentType` = spec `identity.agentType`         |
| Identity Fields Complete      | 100%      | determinismGuarantee + stateMutability + failureMode |

---

## 7. Next Steps

1. **Review this plan** with product owners for Platform, AEP, Data Cloud, and YAPPC
2. **Approve schema** additions (determinismGuarantee, stateMutability, failureMode, supportedModalities, promptVersioning) and the 9-value agentType enum (consolidated from 12 with clear boundaries)
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

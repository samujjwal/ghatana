# YAPPC AGENTIC PLATFORM — ARCHITECTURE REVIEW AND DESIGN

**Review Date:** March 10, 2026  
**Scope:** `products/yappc` · `platform/java` · `products/aep` · `products/data-cloud`  
**Method:** Direct source code reading — all findings are grounded in actual runtime code, not docs or YAMLs alone.  
**Version:** 1.0.0

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Current Backend Architecture](#current-backend-architecture)
3. [Declarative Control Plane Assessment](#declarative-control-plane-assessment)
4. [Product Lifecycle Capability Assessment](#product-lifecycle-capability-assessment)
5. [Agent System Architecture](#agent-system-architecture)
6. [Agent Runtime Design](#agent-runtime-design)
7. [Agent Registry Design](#agent-registry-design)
8. [Workflow and Orchestration Architecture](#workflow-and-orchestration-architecture)
9. [Workflow Engine Design](#workflow-engine-design)
10. [Tool Ecosystem Architecture](#tool-ecosystem-architecture)
11. [Knowledge and Memory Architecture](#knowledge-and-memory-architecture)
12. [Human-Agent Interaction Model](#human-agent-interaction-model)
13. [Event Architecture](#event-architecture)
14. [Observability Architecture](#observability-architecture)
15. [Security and Governance Model](#security-and-governance-model)
16. [Plugin Architecture](#plugin-architecture)
17. [Gap Analysis](#gap-analysis)
18. [Target System Architecture](#target-system-architecture)
19. [Implementation Roadmap](#implementation-roadmap)
20. [Risk Analysis](#risk-analysis)
21. [System Maturity Score](#system-maturity-score)
22. [Priority Action List](#priority-action-list)

---

## Executive Summary

YAPPC is a product-development platform that aims to act as an operating system for the full software lifecycle: ideation through learning and enhancement. The backend contains a substantial, well-structured codebase with 300+ Java files, 228 agent definitions in YAML, three live HTTP services, a DataCloud-backed persistence layer, and a declarative control plane covering agents, workflows, lifecycle stages, and transitions. The platform libraries (`platform/java/`) provide enterprise-grade contracts for agent dispatch, memory, governance, and workflow execution.

**The core problem is that the control plane and the runtime are not connected.** The lifecycle service's primary mutation route returns `{"status":"not_implemented"}`. The `PolicyEngine` is wired as a permissive stub. The `SecurityServiceAdapter` returns hardcoded CLEAN for all scans. The 228 YAML agent definitions are not operationally loaded by any running Java service. The `EventLogMemoryStore` used in production is a pure `ConcurrentHashMap` with no durability. The `DurableWorkflowEngine` and `DAGPipelineExecutor` from the platform exist but are not wired into YAPPC services. The `JdbcMemoryItemRepository` and `PersistentMemoryPlane` from `platform/java/agent-memory` exist but are not wired in YAPPC.

There are also two **critical security vulnerabilities** in the running codebase:
1. `DefaultHookExecutor` executes `sh -c <raw_command>` with zero sanitization — command injection (OWASP A03).
2. `ReleaseAutomationManager` runs git operations via `ProcessBuilder` with potentially user-supplied commit messages.

The target architecture requires four transformation tracks: **(1) security remediation**, **(2) runtime/control-plane wiring**, **(3) agent runtime hardening**, and **(4) lifecycle loop closure**.

---

## Current Backend Architecture

### Service Topology

YAPPC currently runs as five independent processes, each with its own I/O, DI module, and HTTP server:

```
┌────────────────────────────────────────────────────────────────────────┐
│                      YAPPC Process Map                                 │
│                                                                        │
│  YappcLifecycleService    :8082   (ActiveJ HTTP, 8-phase router)       │
│  YappcAiService           :8081   (ActiveJ HTTP, AI + Canvas)          │
│  YappcScaffoldService     :8083   (ActiveJ HTTP, plugin registry)      │
│  backend/api              :8080?  (backend API module)                 │
│  backend/websocket        :—      (ConnectionManager, MessageRouter)   │
│                                                                        │
│  YappcLauncher            ——      (CLI shell, reads config, no DI)     │
└────────────────────────────────────────────────────────────────────────┘
```

### Module Inventory

| Module | Package | Status |
|--------|---------|--------|
| `launcher` | `com.ghatana.yappc.launcher` | **Shell only** — reads args/env, blocks main thread, wires no services |
| `services/lifecycle` | `com.ghatana.yappc.services.lifecycle` | **Partially wired** — HTTP server starts, `/advance` returns stub |
| `services/ai` | `com.ghatana.yappc.services.ai` | **Partially wired** — agent list hardcoded `[]`, canvas generation real |
| `services/scaffold` | `com.ghatana.yappc.services.scaffold` | **Partially wired** — plugin discovery real, generate/analyze stubbed |
| `services/domain` | `com.ghatana.yappc.services.domain` | **Declared but thin** — `DomainServiceFacade` wraps IntentSvc + ShapeSvc |
| `services/infrastructure` | `com.ghatana.yappc.services.infrastructure` | **Stub** — `isDatabaseReachable()` returns `Promise.of(true)` |
| `core/lifecycle` | `com.ghatana.yappc.services.*` | **Implemented** — 8 phase services, all backed by real LLM calls via `CompletionService` |
| `core/agents` | `com.ghatana.yappc.agents.*` | **Implemented** — TypedAgent impls, CatalogAgentDispatcher wired |
| `core/framework` | `com.ghatana.yappc.framework.*` | **Implemented** — PluginManager, FeatureFlags, FrameworkBootstrap |
| `core/knowledge-graph` | `com.ghatana.yappc.knowledge.*` | **Partially wired** — DataCloud-backed via `KnowledgeGraphDataCloudPlugin` |
| `core/spi` | `com.ghatana.yappc.client.*` | **Implemented** — `YAPPCClient` SPI + `EmbeddedYAPPCClient` in-process impl |
| `core/scaffold` | `com.ghatana.yappc.core.pack.*` | **Implemented but unsafe** — HookExecutor, ReleaseAutomationManager |
| `backend/persistence` | `com.ghatana.yappc.api.repository.*` | **Dual-path** — InMemory + JDBC implementations, JDBC partially wired |
| `backend/websocket` | `com.ghatana.yappc.api.websocket.*` | **Implemented** — ConnectionManager, MessageRouter, 3 handlers |
| `backend/deployment` | `com.ghatana.yappc.api.deployment.*` | **Declared** — DTO-level only, rollback/monitor/metrics DTOs present |
| `backend/auth` | `com.ghatana.yappc.api.auth.*` | **Partially wired** — PersonaMapping implemented, HTTP filter wiring incomplete |
| `infrastructure/datacloud` | `com.ghatana.yappc.infrastructure.datacloud.*` | **Partially wired** — adapters exist, `DEFAULT_TENANT` hardcoded |
| `infrastructure/persistence` | `com.ghatana.products.yappc.infrastructure.persistence.*` | **JPA-backed** — Widget, Dashboard JPA repositories with tests |
| `libs/java/yappc-domain` | `com.ghatana.products.yappc.domain.*` | **Implemented** — full domain model: User, Project, Deployment, Pipeline, SecurityAlert, etc. |

### Data Stores and Repository Map

| Data | Active Backend | Intended Backend | Notes |
|------|---------------|-----------------|-------|
| Projects, phase states | DataCloud (`projects`, `phase_states` collections) | DataCloud | Wired via `YappcDataCloudRepository`, **DEFAULT_TENANT bug** |
| Dashboards, widgets | DataCloud (`dashboard`, `widget`) | DataCloud | Wired, workspace-scoped correctly |
| Knowledge graph nodes/edges | DataCloud (`kg_node`, `kg_edge`) | DataCloud | Wired via `KnowledgeGraphDataCloudPlugin` |
| Agent registry | `JdbcAgentRegistryRepository` → PostgreSQL `yappc.agent_registry` | JDBC/PostgreSQL | Implemented with ON CONFLICT UPSERT, tenant isolation via column |
| Agent episodes/memory | `EventLogMemoryStore` — `ConcurrentHashMap` | `PersistentMemoryPlane` (JDBC) | **No persistence — lost on restart** |
| Working memory | `BoundedWorkingMemory` — `LinkedHashMap` | Same | Bounded, eviction-based |
| Artifacts | `InMemoryArtifactStore` — `ConcurrentHashMap` | DataCloud / blob store | **No persistence — lost on restart** |
| Events (domain) | `JdbcEventRepository` → PostgreSQL | Same | Implemented |
| Requirements, stories, sprints | `JdbcRequirementRepository` etc. | JDBC/PostgreSQL | Implemented |
| Alerts, incidents, vulnerabilities | `InMemory*Repository` implementations | JDBC (partially available) | Split: InMemory active for most |
| AI suggestions | `JdbcAISuggestionRepository` | JDBC | Implemented |
| Channels | `JdbcChannelRepository` | JDBC | Implemented |
| Code reviews | `InMemoryCodeReviewRepository` | JDBC (available) | InMemory active |
| Compliance | `InMemoryComplianceRepository` | JDBC (available) | InMemory active |
| Metrics, traces, logs | `InMemory*Repository` | JDBC (available) | **InMemory active — signals lost on restart** |
| Refactorer storage | DataCloud (`refactorer_storage`) | DataCloud | Wired |
| EmbeddedYAPPCClient task registry | `ConcurrentHashMap` | Should be JDBC/DataCloud | Per-JVM, no persistence |

---

## Declarative Control Plane Assessment

The control plane lives in `products/yappc/config/**`. It is a first-class backend subsystem.

### Configuration Families

#### Agent Catalog (`config/agents/`)

| Item | Status | Notes |
|------|--------|-------|
| `agent-catalog.yaml` — catalog descriptor | **Declared** | File present with version `3.0.0`, extends `platform` |
| `registry.yaml` — 228 agents across 15 phases | **Declared** | Parsed by a Gradle validation task; not loaded by any running Java service |
| 228 agent YAMLs in `definitions/**` | **Declared** | Present on disk, referenced by registry, not operationally loaded at startup |
| Java type implementations for agents | **Partially wired** | `CatalogAgentDispatcher` dispatches registered Java agents; YAML definitions not deserialized into runtime objects |
| Schema validation | **Partially wired** | Gradle build check validates YAML format but not cross-reference integrity |
| Runtime enforcement | **Not operational** | No running service reads `registry.yaml` and binds YAML-defined agents to `CatalogAgentDispatcher` at startup |

**Gap:** `CatalogAgentDispatcher.registerJavaAgent(agentId, TypedAgent)` works at runtime. The 228 YAML definitions describe agents but there is no `AgentDefinitionLoader` that reads these YAMLs, resolves their `generator.type` (PIPELINE, RULE_BASED, LLM), and creates corresponding `TypedAgent` implementations.

#### Lifecycle Configuration (`config/lifecycle/`)

| Item | Status | Notes |
|------|--------|-------|
| `stages.yaml` — 8 stage definitions with entry/exit criteria | **Declared** | Not loaded at startup in any service |
| `transitions.yaml` — allowed forward/backward transitions with required artifacts | **Declared** | Not validated; `ProjectEntity.advanceStage()` uses a hardcoded inner map, not this YAML |
| Entry/exit criteria enforcement | **Not operational** | No gating code reads entry/exit criteria from YAML |
| Required artifact validation on transitions | **Not operational** | `ProjectEntity.advanceStage()` validates states but not artifact presence |

**Finding:** Config/runtime drift. The YAML is richer than what the Java entity enforces.

#### Workflow Templates (`config/workflows/`)

| Item | Status | Notes |
|------|--------|-------|
| `canonical-workflows.yaml` — 10+ workflow templates | **Declared** | Rich workflow definitions with stage mappings and task references |
| Runtime workflow engine | **Not operational** | No running service materializes these workflow templates into durable workflow executions |

#### Pipeline Configuration (`config/pipelines/`)

| Item | Status | Notes |
|------|--------|-------|
| `lifecycle-management-v1.yaml` — AEP pipeline spec | **Declared** | `apiVersion: ghatana.aep/v1`. Declares topics, operators, error handling, DLQ, monitoring |
| AEP integration | **Not operational** | No YAPPC service connects to AEP to materialize this pipeline |
| Operators referenced | **Declared** | Not implemented as `UnifiedOperator` subclasses in YAPPC code |

#### Policies (`config/policies/`)

| Item | Status | Notes |
|------|--------|-------|
| Policy files | **Missing** | Directory exists but no YAML policy files found |
| `PolicyEngine` in lifecycle service | **Stub** — `return Promise.of(true)` for all calls | All policy checks currently unenforced |

#### Summary: Control Plane Operationality

| Stage | Declared in Config | Validated in Build | Loaded at Startup | Enforced at Runtime | Observable |
|-------|-------------------|-------------------|------------------|--------------------|-----------| 
| Agent catalog | ✅ | ✅ (format) | ❌ | ❌ | ❌ |
| Lifecycle stages | ✅ | ❌ | ❌ | ❌ | ❌ |
| Transitions | ✅ | ❌ | ❌ | ❌ | ❌ |
| Workflows | ✅ | ❌ | ❌ | ❌ | ❌ |
| AEP pipelines | ✅ | ❌ | ❌ | ❌ | ❌ |
| Policies | ❌ | ❌ | ❌ | ❌ | ❌ |
| Domains/Personas | ✅ | ❌ | ❌ | ❌ | ❌ |

---

## Product Lifecycle Capability Assessment

### Lifecycle Stage Coverage

| Stage | Config Present | Service Exists | HTTP Route | Durable State | LLM-Backed | Notes |
|-------|---------------|----------------|------------|--------------|------------|-------|
| **Intent** | ✅ | ✅ `IntentServiceImpl` | ✅ `POST /api/v1/intent/analyze` (core only) | ❌ InMemory | ✅ | Controller not mounted in lifecycle service HTTP |
| **Shape / Context** | ✅ | ✅ `ShapeServiceImpl` | ✅ `POST /api/v1/shape/*` (core only) | ❌ InMemory | ✅ | Controller not mounted |
| **Plan** | ✅ (workflow template) | Implied | ❌ No explicit route | ❌ | — | No PlanService in core/lifecycle |
| **Generate / Execute** | ✅ | ✅ `GenerationServiceImpl` | ✅ `POST /api/v1/generate/*` (core only) | ❌ InMemoryArtifactStore | ✅ | Controller not mounted |
| **Run** | ✅ | ✅ `RunServiceImpl` | — | ❌ | — | Exists but runs in-memory |
| **Verify** | ✅ | ✅ `ValidationServiceImpl` | ✅ (core only) | ❌ | Partial | PolicyEngine is stub |
| **Observe** | ✅ | ✅ `ObserveServiceImpl` | — | ❌ InMemory metrics | Partial | Observation domain model exists |
| **Learn** | ✅ | ✅ `LearningServiceImpl` | — | ❌ | ✅ | Pattern extraction backed by LLM |
| **Evolve / Institutionalize** | ✅ | ✅ `EvolutionServiceImpl` | — | ❌ | ✅ | Evolution plan generation |

**Key finding:** The `YappcLifecycleService` HTTP server has only three routes: `/health`, `/api/v1/lifecycle/phases` (static list), and `/api/v1/lifecycle/advance` (returns stub). The controllers in `core/lifecycle` are built and tested but are not mounted into the run service.

---

## Agent System Architecture

### Agent Taxonomy — Current Reality vs. Target

#### Human Agents
- **Current:** Persona definitions in `personas.yaml` (6 types). RBAC role model: ADMIN/EDITOR/VIEWER. `TenantAwareRepository` enforces workspace isolation.
- **Gap:** No UI for human operator task assignment, escalation, or approval. No approval gate with backend counterpart.
- **Target:** Approval gate API backed by a durable task queue. WebSocket push for approval requests. Operator explainability endpoints.

#### Deterministic Agents (Rule-Based)
- **Current:** Phase transition validation in `ProjectEntity.advanceStage()`. `policy-guard-agent.yaml`, `budget-gate-agent.yaml`, `confidence-threshold-agent.yaml` defined in config.
- **Gap:** None of these gate agents are implemented as `TypedAgent` classes.
- **Target:** Implement `PolicyGuardAgent`, `BudgetGateAgent`, `ConfidenceThresholdAgent` as `TypedAgent<GateInput, GateResult>` wired to the real `PolicyEngine`.

#### Automation Agents (Event-Driven / Scheduled)
- **Current:** `ReleaseAutomationManager` (git ops, ProcessBuilder — unsafe). No scheduler or cron in any service.
- **Gap:** No durable trigger/scheduler. No event-driven agent invocation from lifecycle transitions. `phase-router-agent.yaml` declared but not implemented.
- **Target:** AEP `TriggerListener` + `CheckpointAwareExecutionQueue` for durable, event-driven automation execution.

#### AI Agents (LLM-Backed)
- **Current:** `CatalogAgentDispatcher` dispatches to LLM tier (third priority). Agent YAMLs define LLM steps with GPT-4 config. `AIModelRouter` from `platform/java/ai-integration` is wired. `LangChain4j:0.25.0` declared in `services/ai`.
- **Gap:** The LLM execution tier in `CatalogAgentDispatcher` works only for explicitly registered agents, not the 228 YAML-defined ones.
- **Target:** `AgentDefinitionLoader` reads YAML definitions at startup, creates `AgentDescriptor` objects, and registers them in `CatalogAgentDispatcher` with appropriate tier assignment.

#### Hybrid Agents (Human-in-the-Loop)
- **Current:** `human-in-the-loop-coordinator.yaml` and `human-override-arbitration-agent.yaml` declared in config. No Java implementation exists.
- **Gap:** No approval gate durability. No human-to-agent handoff protocol.
- **Target:** `HumanApprovalGate` as a durable task backed by JDBC. Approval decision triggers agent pipeline resumption via AEP event.

---

## Agent Runtime Design

### Current Implementation

```
CatalogAgentDispatcher
  ├─ Tier-J: JAVA_IMPLEMENTED    → registered TypedAgent<I,O> — synchronous dispatch
  ├─ Tier-S: SERVICE_ORCHESTRATED → ServiceOrchestrationPlan — delegated to AI service
  └─ Tier-L: LLM_EXECUTED       → LlmExecutionPlan — LLM call via AIModelRouter

BaseAgent<TInput, TOutput>
  └─ AgentTurnPipeline
       ├─ PERCEIVE  → context extraction
       ├─ REASON    → outputGenerator.generate()
       ├─ ACT       → concrete action
       ├─ CAPTURE   → MemoryStore.storeEpisode()  [currently: EventLogMemoryStore (in-memory)]
       └─ REFLECT   → fire-and-forget async reflection
```

**Current problems:**
1. `EventLogMemoryStore` is in-memory. Agent episodes are lost on restart.
2. No retry, timeout, or cancellation around `AgentTurnPipeline.execute()`.
3. No execution history persisted — no agent audit trail.
4. Parallel execution of agents has no coordination or result aggregation.

### Target Agent Runtime Model

```
┌─────────────────────────────────────────────────────────────────────┐
│                     YAPPC Agent Runtime                              │
│                                                                      │
│  AgentTurnPipeline (existing)                                        │
│     + TimeoutWrapper (from platform/java/agent-resilience)           │
│     + RetryPolicy (exponential backoff)                              │
│     + DurableExecutionRecord (JDBC via JdbcTaskStateStore)           │
│     + CancellationToken                                              │
│                                                                      │
│  AgentMemoryBus                                                      │
│     ├─ WorkingMemory   → BoundedWorkingMemory (existing, per-turn)   │
│     ├─ EpisodicMemory  → PersistentMemoryPlane/JDBC (platform)       │
│     ├─ SemanticMemory  → DataCloud collections / vector index        │
│     └─ ProceduralMemory → JdbcPolicyStore (from platform)            │
│                                                                      │
│  AgentCoordinator                                                    │
│     ├─ ParallelExecution → Promise.all() with aggregation            │
│     ├─ HumanApprovalGate → JDBC-backed durable wait                 │
│     └─ EscalationChain   → escalates_to field from YAML             │
└─────────────────────────────────────────────────────────────────────┘
```

**Concrete changes required:**
1. Replace `EventLogMemoryStore` with `PersistentMemoryPlane` from `platform/java/agent-memory`
2. Wrap `AgentTurnPipeline.execute()` with timeout/retry from `platform/java/agent-resilience`
3. Add `JdbcTaskStateStore.recordExecution()` calls at CAPTURE phase
4. Implement `ParallelAgentExecutor` using `Promise.all()` for multi-agent steps

---

## Agent Registry Design

### Current State

`JdbcAgentRegistryRepository` is the most complete agent-registry component. Schema:
```sql
CREATE TABLE yappc.agent_registry (
  id TEXT, name TEXT, version TEXT, agent_type TEXT, status TEXT,
  capabilities JSONB, config JSONB, metadata JSONB,
  health_status TEXT, last_heartbeat TIMESTAMP, tenant_id TEXT,
  PRIMARY KEY (id, tenant_id)
);
```

**Gaps:**
- No heartbeat updater — agents do not push health to this table at runtime.
- No registry-to-dispatcher sync — `CatalogAgentDispatcher` has its own `ConcurrentHashMap` and does not read from `JdbcAgentRegistryRepository`.
- No YAML definition loader — `registry.yaml`'s 228 agent definitions are not seeded into this table.
- No versioning enforcement.

### Target Registry Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                    YAPPC Agent Registry                              │
│                                                                      │
│  YAML Control Plane          Java Runtime Registry                   │
│  config/agents/registry.yaml → AgentDefinitionLoader               │
│       │                              │                               │
│       └──────► JdbcAgentRegistryRepository (PostgreSQL)             │
│                       │                                              │
│                       ├─ At startup: seed all YAML-defined agents   │
│                       ├─ At runtime: TypedAgent impls register      │
│                       └─ Periodic: health heartbeats                │
│                              │                                       │
│                    CatalogAgentDispatcher                            │
│                       ├─ Tier-J: Java bean agents                   │
│                       ├─ Tier-S: Service-orchestrated (YAML-defined)│
│                       └─ Tier-L: LLM-executed (YAML-defined)        │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Workflow and Orchestration Architecture

### Current State

| Component | Location | Status |
|-----------|---------|--------|
| `DurableWorkflowEngine` | `platform/java/workflow` | **Implemented** — provides durable, resumable workflow execution |
| `DAGPipelineExecutor` | `platform/java/workflow` | **Implemented** — DAG-based parallel pipeline execution |
| `canonical-workflows.yaml` | `config/workflows/` | **Declared** — not materialized |
| AEP `Orchestrator` with `CheckpointAwareExecutionQueue` + PostgreSQL checkpoint store | `products/aep/platform` | **Implemented** — checkpoint recovery tested |
| AEP `TriggerListener` | `products/aep/platform` | **Implemented** — event-driven trigger subscription |
| `DurableWorkflowEngine` wiring in YAPPC | — | **Missing** |

The `lifecycle-management-v1.yaml` pipeline config correctly declares `apiVersion: ghatana.aep/v1`. AEP's `PipelineMaterializer` can load this YAML and instantiate the pipeline. The operators referenced need to be implemented as `UnifiedOperator` subclasses.

### Target Orchestration Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│              YAPPC Orchestration Layer                               │
│                                                                      │
│  YAPPC-Native (product-specific)                                    │
│  ┌────────────────────────────────┐                                 │
│  │ LifecycleOrchestrationService  │                                 │
│  │  - AdvancePhaseUseCase         │                                 │
│  │  - GateEvaluator               │                                 │
│  │  - TransitionValidator         │                                 │
│  └────────┬───────────────────────┘                                 │
│            │ emits events to:                                        │
│  AEP Integration (reuse, don't rebuild)                             │
│  ┌────────────────────────────────┐                                 │
│  │ AEP Pipeline (aep/platform)    │                                 │
│  │  - TriggerListener             │ ← phase.transition events       │
│  │  - CheckpointAwareQueue        │ ← durable execution tracking    │
│  │  - lifecycle-management-v1.yaml│ ← operator chain materializer  │
│  │  - UnifiedOperators (YAPPC):   │                                 │
│  │     PhaseTransitionValidator   │                                 │
│  │     GateOrchestrator           │                                 │
│  │     LifecycleStatePublisher    │                                 │
│  │     AgentDispatchOperator      │                                 │
│  └────────┬───────────────────────┘                                 │
│            │ state persisted to:                                     │
│  DataCloud (reuse)                                                  │
│  ┌────────────────────────────────┐                                 │
│  │ PhaseStateRepository           │ ← phase_states collection       │
│  │ ProjectRepository              │ ← projects collection           │
│  └────────────────────────────────┘                                 │
└─────────────────────────────────────────────────────────────────────┘
```

**Decision: Use AEP as the orchestration substrate, not rebuild it in YAPPC.** AEP has: durable checkpointing (PostgreSQL), event-driven triggers, operator chain execution with dead-letter handling, observability, and tenant isolation tests.

---

## Workflow Engine Design

### What Exists in `platform/java/workflow`

- `DurableWorkflowEngine` — manages step dependencies, retries, timeouts, resumability
- `UnifiedOperator` — abstract base for all stream/pattern/learning operators
- `DAGPipelineExecutor` — executes operator DAGs with proper dependency ordering
- `PipelineBuilder` — fluent API for constructing pipelines

### YAPPC-Specific Workflow Needs

| Need | Mechanism | Source |
|------|-----------|--------|
| Multi-step artifact generation | `DurableWorkflowEngine` | `platform/java/workflow` |
| Phase-to-phase lifecycle transitions | AEP pipeline + `PhaseTransitionValidator` operator | `products/aep/platform` |
| Code scaffolding workflow | `PluginManager` + `DurableWorkflowEngine` | YAPPC `core/scaffold` + platform |
| Human approval gating | `HumanApprovalGate` durable task | New — YAPPC product code |
| Build/test/deploy automation | Currently unsafe `ProcessBuilder` → must move to sandboxed executor | YAPPC `core/scaffold` |

---

## Tool Ecosystem Architecture

### Current Tool/Plugin Surfaces

| Surface | Status | Notes |
|---------|--------|-------|
| `PluginManager` (core/framework) | **Implemented** | Manages `YappcPlugin` and `BuildGeneratorPlugin` lifecycle |
| `YappcPlugin` SPI (core/framework API) | **Implemented** | Interface: `initialize(PluginContext)`, `getName()`, `getVersion()` |
| `BuildGeneratorPlugin` SPI | **Implemented** | Extends `YappcPlugin` with `generate(ProjectDescriptor)` |
| `PluginRegistry` (platform/java/plugin) | **Implemented** | `UnifiedPluginBootstrap` manages plugin discovery and shutdown |
| `DefaultHookExecutor` (core/scaffold) | **Implemented but unsafe** | `ProcessBuilder("sh", "-c", command)` with no sanitization |
| `ReleaseAutomationManager` (core/scaffold) | **Implemented but unsafe** | Git commands via `ProcessBuilder` |
| Tool definitions in agent YAMLs (e.g., `maven_executor`, `checkstyle_validator`) | **Declared** | Not implemented as callable tool objects |
| OpenRewrite | **Declared** | `org.openrewrite:rewrite-core/java/gradle` in scaffold/core dependencies |
| Handlebars | **Declared** | Template engine in scaffold/core |

### Target Tool Framework

```
┌─────────────────────────────────────────────────────────────────────┐
│              YAPPC Tool Framework                                    │
│                                                                      │
│  Tool Registry (extends platform/java/plugin PluginRegistry)        │
│  ┌───────────────────────────────────────────────────────┐          │
│  │  ToolDescriptor { id, version, permissions, schema }  │          │
│  │  ToolContract { inputSchema, outputSchema }           │          │
│  │  ToolExecutionPolicy { maxDuration, sandbox, audit }  │          │
│  └───────────────────────────────────────────────────────┘          │
│                                                                      │
│  Built-in Tools (implement as SafeToolExecutor wrappers)            │
│  ┌──────────────────────────────────────────────────┐               │
│  │  MavenExecutor     → allowlisted mvn goals only   │               │
│  │  GradleExecutor    → allowlisted tasks only        │               │
│  │  CheckstyleRunner  → no shell, Java API call       │               │
│  │  OpenRewriteTool   → codemods via API, not shell   │               │
│  │  TestRunner        → JUnit platform API            │               │
│  │  KnowledgeTool     → DataCloud query               │               │
│  │  ScaffoldTool      → Handlebars template rendering │               │
│  └──────────────────────────────────────────────────┘               │
│                                                                      │
│  Hook Execution (replaces DefaultHookExecutor)                       │
│  ┌──────────────────────────────────────────────────┐               │
│  │  SafeHookExecutor:                                │               │
│  │   1. Validate command against ALLOWLIST           │               │
│  │   2. Pass args as List<String> (no shell)         │               │
│  │   3. Working dir validation (no path traversal)   │               │
│  │   4. Output/error capture + audit log             │               │
│  │   5. Timeout enforcement                          │               │
│  └──────────────────────────────────────────────────┘               │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Knowledge and Memory Architecture

### Current State

| Layer | Implementation | Status |
|-------|---------------|--------|
| **Working Memory** | `BoundedWorkingMemory` — `LinkedHashMap` with eviction | **Implemented** — in-memory, per-turn |
| **Episodic Memory** | `EventLogMemoryStore.storeEpisode()` — `ConcurrentHashMap` | **Implemented** — no persistence |
| **Semantic Memory** | `storeFact()` in `EventLogMemoryStore` | **Implemented** — no persistence |
| **Procedural Memory** | `storePolicy()` in `EventLogMemoryStore` | **Implemented** — no persistence |
| **Knowledge Graph** | `KnowledgeGraphServiceImpl` → DataCloud `kg_node`/`kg_edge` | **Partially wired** — DataCloud-backed |
| **JDBC-backed memory** | `JdbcMemoryItemRepository`, `PersistentMemoryPlane` | **Implemented in platform** — not wired in YAPPC |
| **Semantic retrieval** | `BM25Retriever`, `HybridRetriever`, `RetrievalPipeline` | **Implemented in platform** — not wired |
| **Memory governance** | `MemoryRedactionFilter`, `MemorySecurityManager` | **Implemented in platform** — not wired |

**Structural gap:** `EventLogMemoryStore.applyGovernance()` logs "Simulated governance applied" and does nothing.

### Target Memory Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                  YAPPC Memory System                                 │
│                                                                      │
│  Layer              Implementation              Store               │
│  ─────────────────────────────────────────────────────────────────  │
│  Working Memory  → BoundedWorkingMemory       → In-process          │
│                    (current, keep)                                   │
│                                                                      │
│  Turn Episodes   → PersistentMemoryPlane      → JDBC                │
│                    (platform/java/agent-memory)  JdbcMemoryItem     │
│                                                  Repository          │
│                                                                      │
│  Task State      → JdbcTaskStateStore         → JDBC                │
│                    (platform/java/agent-memory)  yappc.task_state   │
│                                                                      │
│  Knowledge Facts → DataCloud EntityRepository → DataCloud           │
│  (semantic)        + HybridRetriever            collections         │
│                    (BM25 + dense vector)                             │
│                                                                      │
│  Policies        → JdbcPolicyStore (new)      → JDBC                │
│  (procedural)      backed by PolicyEngine        yappc.policies     │
│                                                                      │
│  Knowledge Graph → KnowledgeGraphDataCloud    → DataCloud           │
│                    Plugin (current, keep)        kg_node/kg_edge    │
│                                                                      │
│  Memory Governance                                                   │
│  → MemoryRedactionFilter (platform) applied before PersistentPlane  │
│  → MemorySecurityManager enforces tenant isolation on all reads     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Human-Agent Interaction Model

### Current Support

| Component | Status |
|-----------|--------|
| `CanvasCollaborationHandler` (WebSocket) | **Implemented** — node/edge operations, multi-user canvas |
| `ChatHandler` (WebSocket) | **Implemented** — message routing |
| `NotificationHandler` (WebSocket) | **Implemented** — notification push |
| `PresenceManager` | **Implemented** — user presence tracking |
| `PersonaMapping` | **Implemented** — 21 personas to 3 RBAC roles |
| Approval request API | **Missing** |
| Explainability endpoints | **Missing** |
| Manual takeover endpoint | **Missing** |
| Audit trail query API | **Missing** |

### Target Interaction Model

```
Human Operator
  │
  ├─ Canvas UI (existing WebSocket)
  │    └─ Real-time collaboration, node/edge ops
  │
  ├─ Approval API (new)
  │    POST /api/v1/approvals/{id}/approve|reject
  │    GET  /api/v1/approvals/pending
  │    WebSocket PUSH → NotificationHandler
  │
  ├─ Agent Visibility API (new)
  │    GET  /api/v1/agents/{id}/status
  │    GET  /api/v1/agents/{id}/history    (from JdbcTaskStateStore)
  │    GET  /api/v1/agents/{id}/rationale  (from episodic memory)
  │
  ├─ Override API (new)
  │    POST /api/v1/lifecycle/{projectId}/override
  │    POST /api/v1/agents/{id}/cancel
  │
  └─ Audit Query API (new)
       GET  /api/v1/audit/events?projectId=&from=&to=
```

---

## Event Architecture

### Current Event Infrastructure

| Component | Status | Notes |
|-----------|--------|-------|
| `DomainEvent` interface | **Implemented** | Base event type in `backend/persistence` |
| `EventPublisher` interface | **Implemented** | Abstraction |
| `JdbcEventRepository` | **Implemented** | PostgreSQL-backed event append |
| `InMemoryEventPublisher` (core/lifecycle) | **Implemented — no durability** | Used in lifecycle service |
| Canvas, AI, Collaboration events | **Implemented** | Defined in persistence module |
| AEP EventCloud | **Implemented** — separate product | Full event cloud platform available |
| `platform/java/event-cloud` | **Implemented** | Event-cloud SPI/client |

**Critical gap:** The lifecycle service uses `InMemoryEventPublisher`. Phase transitions emit no durable events. AEP's `TriggerListener` cannot subscribe to YAPPC lifecycle events because they never reach a durable bus.

### Target Event Architecture

```
YAPPC Services
  │
  │  publish to:
  ▼
DurableEventBus (YAPPC-native, wraps JdbcEventRepository)
  │   topics: phase.transition, artifact.generated, agent.completed,
  │            approval.requested, approval.responded, run.completed,
  │            observation.captured, lesson.learned
  │
  ├─ Local consumers (JDBC polling or PostgreSQL LISTEN/NOTIFY)
  │    └─ WebSocket push → NotificationHandler (real-time UI updates)
  │
  └─ AEP Bridge (new: DataCloudEventCloudClient, already tested in AEP)
       │   forwards to AEP's event cloud
       ▼
  AEP Pipeline (TriggerListener → operator chain → agent dispatch)
       │
       └─ Long-horizon orchestration (approval gating, cross-service coordination)
```

---

## Observability Architecture

### Current State

| Component | Status | Notes |
|-----------|--------|-------|
| `MetricsCollector` (platform) | **Implemented** | Interface with `recordTimer`, `incrementCounter` |
| `NoopMetricsCollector` | **Active** in lifecycle service | Metrics collected but not emitted |
| `AuditLogger.noop()` | **Active** in lifecycle service | Audit calls made but discarded |
| `IntentServiceImpl` — records metrics and audit | **Implemented** | Calls `metrics.recordTimer()` on every intent capture |
| Prometheus scrape config (`prometheus.yappc.yml`) | **Declared** | Config exists but `NoopMetricsCollector` produces nothing |
| Platform `observability` module | **Implemented** | Micrometer + OpenTelemetry wrappers |
| AEP pipeline monitoring config | **Declared** | Counter and histogram metrics in pipeline YAML |

**The observability gap is a single line:** replacing `NoopMetricsCollector` with `MicrometerMetricsCollector` wired to the Prometheus scrape endpoint would activate all existing instrumentation.

### Target Observability Architecture

```
Logs    → Structured JSON → Log4j2 → ELK / Loki
Metrics → MicrometerMetricsCollector → Prometheus → Grafana
Traces  → OpenTelemetry spans → OTLP exporter → Jaeger / Tempo
Audits  → JdbcPersistentAuditService (AEP) → PostgreSQL audit_events
          Query API: GET /api/v1/audit/events
Agent history → JdbcTaskStateStore → GET /api/v1/agents/{id}/history
Pipeline lineage → AEP CheckpointStore → PostgreSQL
```

| Signal Type | Description | Current | Target |
|-------------|-------------|---------|--------|
| **Logs** | Operational runtime info | SLF4J/Log4j2 ✅ | Same, add correlation IDs |
| **Metrics** | Timing/counters | Noop ❌ | MicrometerMetricsCollector |
| **Traces** | Request-spanning correlation | Absent ❌ | OpenTelemetry + OTLP |
| **Audits** | Compliance-grade write-once log | Noop ❌ | JdbcPersistentAuditService (AEP) |
| **Lineage** | Artifact provenance, agent decision chain | Absent ❌ | New — Phase 4 |
| **Execution History** | Per-agent turn records | Absent ❌ | JdbcTaskStateStore (platform) |

---

## Security and Governance Model

### Active Security Mechanisms

| Mechanism | Implementation | Enforced? |
|-----------|---------------|----------|
| API key auth | `ApiKeyAuthFilter` (platform) | ❌ Not wired in YAPPC HTTP services |
| Tenant context | `TenantExtractionFilter` + `TenantContext` (platform) | ❌ Not wired in YAPPC HTTP services |
| RBAC | `RbacPolicy`, `Role`, `TenantIsolationEnforcer` | ⚠️ Partially wired — not called at YAPPC HTTP boundaries |
| Workspace-scoped data | `TenantAwareRepository<T, ID>` | ✅ DashboardDataCloudAdapter correct |
| `YappcDataCloudRepository` tenant | `DEFAULT_TENANT="default"` hardcoded | ❌ Not enforced |
| WebSocket auth | Reads `X-Tenant-Id` header as string | ❌ No principal validation |
| Policy engine | `PolicyEngine.evaluate()` | ❌ Stub — always returns `true` |
| Security scanning | `SecurityServiceAdapter.scanProject()` | ❌ Stub — always returns CLEAN |

### Critical Vulnerabilities

#### Vulnerability 1: Command Injection — CRITICAL (OWASP A03)

**File:** `products/yappc/core/scaffold/core/…/DefaultHookExecutor.java`

```java
// CURRENT — UNSAFE
new ProcessBuilder("sh", "-c", command)  // command = raw string from YAML hook config
```

**Required fix:**
```java
// TARGET — SAFE
private static final Set<String> HOOK_ALLOWLIST = Set.of("mvn", "gradle", "npm");

private void executeHook(String command, List<String> args, Path workingDir) {
    if (!HOOK_ALLOWLIST.contains(command.toLowerCase())) {
        throw new SecurityException("Hook command not allowed: " + command);
    }
    // Validate workingDir is within project root (no path traversal)
    if (!workingDir.toAbsolutePath().startsWith(projectRoot.toAbsolutePath())) {
        throw new SecurityException("Working directory outside project root");
    }
    List<String> cmd = new ArrayList<>();
    cmd.add(command);
    cmd.addAll(args);
    new ProcessBuilder(cmd).directory(workingDir.toFile()).start();
}
```

#### Vulnerability 2: Unvalidated Git Operations — HIGH

**File:** `products/yappc/core/scaffold/core/…/ReleaseAutomationManager.java`

```java
// CURRENT — potentially unsafe
new ProcessBuilder("git", "commit", "-m", commitMessage)
```

`commitMessage` must be validated. Remote URL must be validated against an allowlist of authorized remotes.

### Tenant Isolation Fix

```java
// CURRENT — WRONG
private static final String DEFAULT_TENANT = "default";

// TARGET — CORRECT: resolve from TenantContext per request
private String getTenantId() {
    String tenantId = TenantContext.getTenantId();
    if (tenantId == null || tenantId.isBlank()) {
        throw new SecurityException("No tenant context");
    }
    return tenantId;
}
```

---

## Plugin Architecture

### Current Plugin Model

`PluginManager` (core/framework) manages `YappcPlugin` and `BuildGeneratorPlugin` implementations. `PluginRegistry` (platform/java/plugin) via `UnifiedPluginBootstrap` is used in `YappcScaffoldService`.

**Gaps:**
- No plugin isolation — plugins execute in the same class loader. A buggy plugin can crash the service.
- No plugin permission model.
- No plugin versioning enforcement.
- No plugin audit — plugin invocations not logged to `AuditLogger`.

### Target Plugin Framework

```
PluginSandbox (new)
  ├─ PermissionSet: { network: [allowlist], filesystem: [allowlist], classes: [allowlist] }
  ├─ ResourceBudget: { maxMemoryMB, maxCpuMs, maxWallMs }
  ├─ ClassLoader isolation (child class loader per plugin)
  └─ AuditInterceptor: every plugin invocation recorded to AuditLogger

Plugin Registry (extends platform/java/plugin PluginRegistry)
  ├─ PluginDescriptor { id, version, minPlatformVersion, permissions: PermissionSet }
  ├─ Plugin validation at registration (schema + permission check)
  └─ Hot reload: PluginRegistry.reload(id) without service restart
```

---

## Gap Analysis

### P0 Blockers — Unsafe Execution and Security

| Gap | Module | Risk |
|-----|--------|------|
| `DefaultHookExecutor` executes `sh -c <command>` with no sanitization — command injection | `core/scaffold/core` | 🔴 CRITICAL |
| `ReleaseAutomationManager` git operations with unvalidated inputs | `core/scaffold/core` | 🔴 HIGH |
| `PolicyEngine` stub (`return Promise.of(true)`) — all policy checks bypassed | `services/lifecycle` | 🔴 HIGH |
| `SecurityServiceAdapter` fully stubbed — all scans return CLEAN | `infrastructure/datacloud` | 🔴 HIGH |
| `YappcDataCloudRepository.DEFAULT_TENANT="default"` — tenant data isolation broken | `infrastructure/datacloud` | 🔴 HIGH |
| WebSocket auth not enforced | `backend/websocket` | 🟠 HIGH |
| YAPPC HTTP services have no auth filter | `services/*` | 🟠 HIGH |

### P1 Platform Stability Gaps

| Gap | Module | Risk |
|-----|--------|------|
| `InMemoryEventPublisher` used in lifecycle — all events lost on restart | `core/lifecycle/storage` | 🟠 HIGH |
| `InMemoryArtifactStore` — all artifacts lost on restart | `core/lifecycle/storage` | 🟠 HIGH |
| `EventLogMemoryStore` — agent episodes lost on restart | Multiple | 🟠 HIGH |
| Agent registry YAML (228 agents) not loaded at startup | `config/agents/`, `core/agents` | 🟠 HIGH |
| Lifecycle service `/advance` route returns stub | `services/lifecycle` | 🟠 HIGH |
| API controllers not mounted in lifecycle service HTTP routing | `services/lifecycle` | 🟠 HIGH |
| `NoopMetricsCollector` in production lifecycle service | `services/lifecycle` | 🟡 MEDIUM |
| `AuditLogger.noop()` in production lifecycle service | `services/lifecycle` | 🟡 MEDIUM |
| Config/runtime drift: `stages.yaml` and `transitions.yaml` not enforced | `config/lifecycle/` | 🟡 MEDIUM |
| Most `InMemory*Repository` still active for operational data | `backend/persistence` | 🟡 MEDIUM |
| `EmbeddedYAPPCClient` task registry is `ConcurrentHashMap` | `core/spi` | 🟡 MEDIUM |
| Policy files missing from `config/policies/` | `config/policies/` | 🟡 MEDIUM |

### P2 Evolution Gaps

| Gap | Module | Notes |
|-----|--------|-------|
| 228 agent YAML definitions not wired to `CatalogAgentDispatcher` | `config/agents/`, `core/agents` | `AgentDefinitionLoader` needed |
| AEP lifecycle pipeline not materialized | `config/pipelines/`, AEP | Implement YAPPC `UnifiedOperator` subclasses |
| `canonical-workflows.yaml` not materialized | `config/workflows/` | `WorkflowMaterializer` using `DurableWorkflowEngine` |
| No human approval API | New feature | `HumanApprovalService` backed by JDBC |
| Memory governance stub | `platform/java/agent-memory` | Wire `MemoryRedactionFilter` |
| `HybridRetriever` not wired | `platform/java/agent-memory` | Semantic search unavailable to agents |
| DataCloud NLQ service not exposed to YAPPC agents | `products/data-cloud` | Wire as knowledge retrieval tool |
| No product intelligence aggregation | Multiple | Phase 5 work |
| `agent-learning` module not wired in YAPPC | `platform/java/agent-learning` | Learning pipeline per phase10 agents |
| Two KG implementations in same module | `core/knowledge-graph` | Consolidate `com.ghatana.yappc.knowledge.*` and `com.ghatana.yappc.kg.*` |
| Duplicate `Metric` domain class | Multiple modules | Consolidate into `libs/java/yappc-domain` |

---

## Target System Architecture

```
╔══════════════════════════════════════════════════════════════════════════════════╗
║                     YAPPC AGENTIC PLATFORM — TARGET ARCHITECTURE                ║
╠══════════════════════════════════════════════════════════════════════════════════╣
║                                                                                  ║
║  HUMAN LAYER                                                                     ║
║  ┌─────────────────────────────────────────────────────────────────────────┐     ║
║  │  Canvas UI    Chat/Notifications   Approval Dashboard   Audit Explorer  │     ║
║  │        (existing WebSocket + new REST endpoints)                        │     ║
║  └──────────────────┬──────────────────┬─────────────────────────────────┘     ║
║                     │ WebSocket         │ REST/HTTP                              ║
║  GATEWAY + AUTH LAYER                                                            ║
║  ┌─────────────────────────────────────────────────────────────────────────┐     ║
║  │  ApiKeyAuthFilter ──► TenantExtractionFilter ──► TenantIsolationEnforcer│     ║
║  │  (platform/java/governance — wire into all YAPPC HTTP filter chains)    │     ║
║  └─────────────────────────────────────────────────────────────────────────┘     ║
║                                                                                  ║
║  YAPPC PRODUCT LAYER                    [YAPPC-specific]                         ║
║  ┌─────────────────────────────────────────────────────────────────────────┐     ║
║  │  LifecycleOrchestrationService    YappcAiService      ScaffoldService   │     ║
║  │  (phase advance → gating → event  (AIModelRouter      (SafeHookExecutor │     ║
║  │   emit → agent dispatch trigger)   canvas gen)         PluginRegistry)  │     ║
║  │                                                                          │     ║
║  │  HumanApprovalService  AgentVisibilityService  AuditQueryService        │     ║
║  └─────────────────────────────────────────────────────────────────────────┘     ║
║                │ events                  │ dispatch              │ plugins        ║
║                ▼                         ▼                        ▼               ║
║  AGENT RUNTIME LAYER           [Shared platform — platform/java/]                ║
║  ┌─────────────────────────────────────────────────────────────────────────┐     ║
║  │  CatalogAgentDispatcher                                                  │     ║
║  │     Tier-J  ──► TypedAgent<I,O> implementations (YAPPC core/agents)    │     ║
║  │     Tier-S  ──► ServiceOrchestrationPlan (YAML-defined, AEP-executed)  │     ║
║  │     Tier-L  ──► LlmExecutionPlan (LLMGateway, AIModelRouter)           │     ║
║  │                                                                          │     ║
║  │  AgentTurnPipeline + RetryPolicy + TimeoutWrapper                        │     ║
║  │  PERCEIVE → REASON → ACT → CAPTURE(PersistentMemoryPlane) → REFLECT     │     ║
║  │                                                                          │     ║
║  │  AgentDefinitionLoader                                                   │     ║
║  │     reads config/agents/registry.yaml + definition YAMLs at startup     │     ║
║  │     seeds JdbcAgentRegistryRepository + registers with dispatcher        │     ║
║  └─────────────────────────────────────────────────────────────────────────┘     ║
║                │                                                                  ║
║  ORCHESTRATION LAYER                   [Reuse AEP; don't rebuild]                ║
║  ┌─────────────────────────────────────────────────────────────────────────┐     ║
║  │  AEP Platform (products/aep/platform)                                    │     ║
║  │     TriggerListener ← phase.transition / task.completed events          │     ║
║  │     CheckpointAwareExecutionQueue (PostgreSQL checkpoint store)          │     ║
║  │     DAGPipelineExecutor ← lifecycle-management-v1.yaml                  │     ║
║  │     UnifiedOperators (YAPPC-specific):                                   │     ║
║  │       PhaseTransitionValidator, GateOrchestrator,                        │     ║
║  │       LifecycleStatePublisher, AgentDispatchOperator                    │     ║
║  │     JdbcPersistentAuditService ← all agent + lifecycle events           │     ║
║  └─────────────────────────────────────────────────────────────────────────┘     ║
║                │                                                                  ║
║  KNOWLEDGE & MEMORY LAYER              [Shared platform + DataCloud]             ║
║  ┌─────────────────────────────────────────────────────────────────────────┐     ║
║  │  PersistentMemoryPlane (platform/java/agent-memory/JDBC)                │     ║
║  │     Episodic: JdbcMemoryItemRepository ← agent turns                   │     ║
║  │     Procedural: JdbcPolicyStore ← learned/configured policies           │     ║
║  │  MemoryRedactionFilter + MemorySecurityManager (platform)               │     ║
║  │  HybridRetriever: BM25 + dense vector ← semantic queries               │     ║
║  │                                                                          │     ║
║  │  DataCloud (products/data-cloud)                                         │     ║
║  │     EntityRepository ← projects, phase_states, dashboards, widgets      │     ║
║  │     Graph ← knowledge graph (kg_node, kg_edge)                         │     ║
║  │     NLQService ← natural language knowledge retrieval                   │     ║
║  │     EventCloud ← event replay, audit, lineage                           │     ║
║  └─────────────────────────────────────────────────────────────────────────┘     ║
║                │                                                                  ║
║  PERSISTENCE LAYER                     [JDBC + DataCloud]                        ║
║  ┌─────────────────────────────────────────────────────────────────────────┐     ║
║  │  PostgreSQL (yappc schema)                                               │     ║
║  │     agent_registry    task_state     memory_items    audit_events       │     ║
║  │     requirements      workspaces     ai_suggestions  events             │     ║
║  │     alerts            incidents      code_reviews    compliance         │     ║
║  │     sprints  stories  teams  traces  metrics  logs   policies           │     ║
║  │                                                                          │     ║
║  │  DataCloud Storage                                                       │     ║
║  │     Entity collections, time-series, vector store, lakehouse             │     ║
║  └─────────────────────────────────────────────────────────────────────────┘     ║
║                                                                                  ║
║  OBSERVABILITY                         [platform/java/observability + AEP]       ║
║  ┌─────────────────────────────────────────────────────────────────────────┐     ║
║  │  MicrometerMetricsCollector → Prometheus → Grafana                      │     ║
║  │  OpenTelemetry traces → OTLP → Jaeger / Tempo                          │     ║
║  │  JdbcPersistentAuditService → PostgreSQL audit_events → Audit API       │     ║
║  │  JdbcTaskStateStore → agent execution history → Agent History API       │     ║
║  └─────────────────────────────────────────────────────────────────────────┘     ║
╚══════════════════════════════════════════════════════════════════════════════════╝
```

### Boundary Decisions

| Concern | Stays in YAPPC | Shared Platform | Reuses AEP | Reuses DataCloud |
|---------|---------------|----------------|-----------|-----------------|
| Lifecycle phase semantics | ✅ | | | |
| Agent YAML definitions | ✅ | | | |
| Product domain model | ✅ | | | |
| Human approval API | ✅ | | | |
| TypedAgent contract | | ✅ `platform/java/agent-framework` | | |
| Agent dispatch | | ✅ `platform/java/agent-dispatch` | | |
| Episodic/procedural memory | | ✅ `platform/java/agent-memory` | | |
| Workflow execution | | ✅ `platform/java/workflow` | | |
| Auth/governance filters | | ✅ `platform/java/governance` | | |
| Long-horizon orchestration | | | ✅ AEP Pipeline | |
| Checkpoint durability | | | ✅ AEP Checkpoint Store | |
| Durable audit | | | ✅ AEP JdbcPersistentAuditService | |
| Entity storage | | | | ✅ DataCloud EntityRepository |
| Knowledge graph | | | | ✅ DataCloud Graph |
| Semantic retrieval | | | | ✅ DataCloud NLQ + HybridRetriever |
| `InMemory*Repository` | ❌ Deprecate | | | ✅ Migrate to JDBC/DataCloud |
| `DefaultHookExecutor` (unsafe) | ❌ Remove | Replace with SafeHookExecutor | | |
| `InMemoryEventPublisher` | ❌ Replace with JdbcEventRepository-backed | | | |

---

## Implementation Roadmap

### Phase 1 — Backend Stabilization and Security

**Goals:** Fix all P0 security vulnerabilities. Eliminate production data loss. Wire auth filters.

| Task | Module | Effort |
|------|--------|--------|
| Replace `DefaultHookExecutor` `sh -c` with allowlist-validated `ProcessBuilder(List<String>)` | `core/scaffold/core` | M |
| Validate `commitMessage` in `ReleaseAutomationManager`; add remote URL allowlist | `core/scaffold/core` | S |
| Wire `ApiKeyAuthFilter` + `TenantExtractionFilter` in all three service modules | `services/*` | S |
| Fix `YappcDataCloudRepository.DEFAULT_TENANT` — read from `TenantContext` per request | `infrastructure/datacloud` | S |
| Wire `WebSocketEndpoint` to validate auth token at handshake | `backend/websocket` | M |
| Replace `NoopMetricsCollector` with `MicrometerMetricsCollector` | `services/lifecycle` | S |
| Replace `AuditLogger.noop()` with `JdbcPersistentAuditService` | `services/lifecycle` | M |
| Replace `InMemoryEventPublisher` with `JdbcEventRepository`-backed publisher | `core/lifecycle/storage` | M |
| Replace `InMemoryArtifactStore` with DataCloud artifact adapter | `core/lifecycle/storage` | M |
| Switch active `InMemory*Repository` to JDBC equivalents | `backend/persistence` | M |

**Validation:** All JDBC-backed repos survive service restart. Auth filter returns 401 on missing API key. No `sh -c` invocation in codebase. Security audit log written to PostgreSQL.

---

### Phase 2 — Agent Runtime and Registry Foundations

**Goals:** Wire the 228 YAML agent definitions to the runtime. Harden agent execution. Activate durable memory.

| Task | Module | Effort |
|------|--------|--------|
| Implement `AgentDefinitionLoader` — reads registry + definition YAMLs, upserts to JDBC registry | `core/agents` | L |
| Wire YAML-loaded agents into `CatalogAgentDispatcher` by tier at startup | `core/agents` | M |
| Implement `AgentHeartbeatService` — periodic health updates for active Java agents | `core/agents` | S |
| Replace `EventLogMemoryStore` with `PersistentMemoryPlane` as `MemoryStore` in `BaseAgent` | `core/agents` + `services/*` | M |
| Wire `MemoryRedactionFilter` before all `PersistentMemoryPlane` writes | `core/agents` | S |
| Wrap `AgentTurnPipeline.execute()` with timeout + retry | `core/agents` | M |
| Wire `JdbcTaskStateStore.recordExecution()` at CAPTURE phase | `core/agents` | S |
| Implement `ParallelAgentExecutor` using `Promise.all()` | `core/agents` | M |
| Implement real `PolicyEngine` backed by policy config | `services/lifecycle` + `config/policies/` | L |

**Validation:** Agent registry query returns all 228 agents after startup. Agent episodes survive restart. Agent timeout logs warning + returns error result.

---

### Phase 3 — Lifecycle Execution Hardening

**Goals:** Connect the HTTP layer to service implementations. Make lifecycle transitions durable and policy-enforced.

| Task | Module | Effort |
|------|--------|--------|
| Mount all API controllers in lifecycle service HTTP routing | `services/lifecycle` | M |
| Implement `AdvancePhaseUseCase` — reads `transitions.yaml`, validates artifacts, gates on `PolicyEngine`, persists, emits event | `services/lifecycle` | L |
| Load `stages.yaml` and `transitions.yaml` at startup for runtime enforcement | `services/lifecycle` | M |
| Implement YAPPC `UnifiedOperator` subclasses for `lifecycle-management-v1.yaml` | `core/agents` | L |
| Wire `AepBridge` — subscribe YAPPC lifecycle events to AEP pipeline | `services/lifecycle` + AEP | L |
| Implement `HumanApprovalService` — JDBC-backed approval with WebSocket push | `backend/api` | L |
| Implement approval response API `POST /api/v1/approvals/{id}/approve|reject` | `backend/api` | M |
| Implement `HumanInTheLoopCoordinatorAgent` as `TypedAgent<ApprovalRequest, ApprovalDecision>` | `core/agents` | M |

**Validation:** `POST /api/v1/lifecycle/advance` triggers durable state change visible in DataCloud. Approval requests survive server restart. AEP pipeline processes test events end-to-end.

---

### Phase 4 — Workflow Engine and Knowledge Integration

**Goals:** Materialize `canonical-workflows.yaml`. Activate semantic memory and knowledge retrieval.

| Task | Module | Effort |
|------|--------|--------|
| Implement `WorkflowMaterializer` — loads workflow YAML and creates `DurableWorkflowEngine` instances | `core/agents` + `platform/java/workflow` | L |
| Wire workflow execution triggers to AEP event subscriptions | `services/lifecycle` | M |
| Wire `HybridRetriever` as agent knowledge tool | `core/agents` | M |
| Expose DataCloud `NLQService` as a REST tool for YAPPC agents | `infrastructure/datacloud` | M |
| Implement `SafeSecurityScanner` replacing `SecurityServiceAdapter` stub | `infrastructure/datacloud` | L |
| Add Agent Visibility API and Audit Query API | `backend/api` | M |

---

### Phase 5 — Product Intelligence Layer

**Goals:** Close the feedback loop. Correlate incidents → features → metrics → enhancements.

| Task | Module | Effort |
|------|--------|--------|
| Implement `ProductIntelligenceService` — aggregates incidents, metrics, feedback | `services/ai` or new `services/intelligence` | L |
| Implement `FeedbackAnalysisAgent` (`phase11-enhancement`) as `TypedAgent` | `core/agents` | M |
| Implement `PatternRecognitionAgent` (`phase10-learning`) backed by `agent-learning` module | `core/agents` | L |
| Implement `EnhancementSuggestionPipeline` | `core/lifecycle` | L |
| Wire `ProductIntelligenceService` into AEP learning pipeline | AEP + `services/ai` | L |

---

### Phase 6 — Advanced AI and Multi-Agent Orchestration

**Goals:** Enable multi-agent collaboration, confident autonomy, and organizational learning.

| Task | Module | Effort |
|------|--------|--------|
| Implement `full-lifecycle-orchestrator` from YAML as `TypedAgent` with full routing table | `core/agents` | XL |
| Implement `EscalationChain` — follows `escalates_to` in YAML | `core/agents` | L |
| Implement `AgentCollaborationBus` — multi-agent session with shared context | `core/agents` | XL |
| Implement confidence-based autonomy leveling | `core/agents` | L |
| Wire `LangChain4j:0.25.0` for structured multi-step LLM pipelines | `services/ai` | L |
| Implement organizational knowledge base — cross-project pattern extraction | `core/agents` + DataCloud | XL |

---

## Risk Analysis

### Scalability Risks

| Risk | Severity | Mitigation |
|------|---------|-----------|
| `JdbcAgentRegistryRepository` uses fixed thread pool of 4 — bottleneck at high agent throughput | HIGH | Move to ActiveJ async JDBC wrapper; use `Promise.ofBlocking(executor, ...)` |
| DataCloud queries with field-level tenant filter — full-scan risk at scale | HIGH | Add composite indexes on `(tenantId, collection)` |
| `EventLogMemoryStore` unbounded `ArrayList` in `synchronized` block — memory leak | HIGH | Replaced in Phase 2 by `PersistentMemoryPlane` |
| `BoundedWorkingMemory` eviction policy not configurable per agent type | MEDIUM | Per-agent `WorkingMemoryConfig` in YAML |

### Operational Risks

| Risk | Severity | Mitigation |
|------|---------|-----------|
| Three independent HTTP services with no service registry or health aggregation | MEDIUM | Add health aggregation endpoint in launcher |
| `YappcLauncher` does not actually start any service | HIGH | Implement full DI wiring in launcher |
| No circuit breaker around LLM calls | HIGH | Wrap `LLMGateway` calls with timeout + circuit breaker |
| `run-dev.sh` starts services without database | HIGH | Fail fast if JDBC repositories cannot connect |

### Security Risks

| Risk | Severity | Mitigation |
|------|---------|-----------|
| `DefaultHookExecutor` command injection — **in production codebase** | CRITICAL | Fix in Phase 1, Day 1 |
| Secrets in environment variables without rotation or Vault integration | MEDIUM | Integrate with a real secrets manager |
| `X-Tenant-ID`, `X-Principal`, `X-Roles` headers are trust-on-receipt | MEDIUM | Strip at API gateway before external clients |

### Agent Failure Risks

| Risk | Severity | Mitigation |
|------|---------|-----------|
| No agent state recovery after JVM restart | HIGH | `JdbcTaskStateStore` records turn state; resume on restart |
| LLM output parsing failure propagates as uncaught exception | HIGH | Return `AgentResult.failure()` on parse exceptions |
| `CompletionService` stub activates silently if `OPENAI_API_KEY` not set | MEDIUM | Fail fast with clear error if LLM required and key absent |
| Multi-agent parallel execution has no budget cap | HIGH | Implement `budget-gate-agent` properly |

### Config Drift Risks

| Risk | Severity | Mitigation |
|------|---------|-----------|
| `stages.yaml`/`transitions.yaml` diverge from `ProjectEntity.advanceStage()` hardcoded map | HIGH | Load from YAML at runtime; single source of truth |
| 228 agent YAMLs reference tool IDs not yet implemented in Java | MEDIUM | Build-time validation that every `tools:` entry resolves to a registered tool |
| Pipeline YAML references operators not yet implemented | MEDIUM | Build-time `PipelineValidationTask` checking operator registry |

### Platform Duplication Risks

| Risk | Severity | Mitigation |
|------|---------|-----------|
| YAPPC reimplementing event bus instead of using AEP EventCloud | HIGH | Phase 3 AEP bridge work |
| Two KG implementations in same module (`yappc.knowledge.*` and `yappc.kg.*`) | HIGH | Consolidate into one package |
| Two `Metric` domain classes across modules | MEDIUM | Consolidate to `libs/java/yappc-domain` |
| `PersonaMapping` Java class vs. `personas.yaml` — two sources of truth | MEDIUM | Load from YAML; eliminate Java class |

---

## System Maturity Score

Assessed against 10 dimensions on a 1–10 scale (1=missing, 10=production-ready):

| Dimension | Score | Evidence |
|-----------|-------|---------|
| **Domain Model** | 4/10 | Rich, well-structured Java domain model. Minor: duplicate Metric/KG classes, no aggregate root consistency. |
| **Agent Framework** | 3/10 | `TypedAgent`, `BaseAgent`, `AgentTurnPipeline`, `CatalogAgentDispatcher` implemented. Memory not durable. 228 agent YAMLs not wired. |
| **Lifecycle Management** | 2/10 | Phase services exist and are LLM-backed. HTTP routing to services is broken (stubs). No durable phase state transition. |
| **Security** | 1/10 | Command injection in prod code path. PolicyEngine stub. SecurityServiceAdapter stub. Auth not enforced at service boundaries. Tenant isolation bug. |
| **Persistence Durability** | 2/10 | JDBC repos exist for agents, requirements, workspaces. Metrics/incidents/alerts/artifacts in-memory. Event publisher not durable. |
| **Observability** | 2/10 | Instrumentation calls exist throughout services. Noop collector active. No audit output. |
| **Orchestration** | 2/10 | AEP fully capable. No integration from YAPPC. Workflow YAML declared but not materialized. |
| **Config/Runtime Parity** | 1/10 | 228 agents, stages, transitions, pipelines, workflows in config. None loaded at runtime by any running service. |
| **Knowledge/Memory** | 2/10 | Knowledge graph wired to DataCloud. Episode memory in-memory. Platform has JDBC memory but not wired. |
| **Plugin Architecture** | 3/10 | PluginManager + PluginRegistry working. No sandbox or permission model. |

**Overall Platform Maturity: 2.2 / 10**

The platform has an excellent skeleton — the contracts, abstractions, domain model, and agent framework are well-architected. The primary work is wiring: connecting the control plane to the runtime, replacing stubs with real implementations, and closing the security gaps. This is implementation work, not design work. The design is sound.

---

## Priority Action List

Top 10 by Risk × Impact:

1. **[CRITICAL — Security]** Fix `DefaultHookExecutor`: remove `sh -c`, implement command allowlist. File: `core/scaffold/core/.../DefaultHookExecutor.java`.
2. **[CRITICAL — Security]** Fix `YappcDataCloudRepository.DEFAULT_TENANT` — resolve from `TenantContext` per request.
3. **[HIGH — Security]** Wire `ApiKeyAuthFilter` + `TenantExtractionFilter` into all three YAPPC HTTP servers via `FilterChain`.
4. **[HIGH — Correctness]** Replace `InMemoryEventPublisher` with `JdbcEventRepository`-backed publisher. Phase transitions must be durable.
5. **[HIGH — Correctness]** Implement `AdvancePhaseUseCase` — the lifecycle's `/advance` route is YAPPC's core mutation and must be operational.
6. **[HIGH — Agent]** Implement `AgentDefinitionLoader` — the 228 YAML definitions must be loaded and registered at startup.
7. **[HIGH — Memory]** Replace `EventLogMemoryStore` with `PersistentMemoryPlane` + `JdbcMemoryItemRepository` from `platform/java/agent-memory`.
8. **[HIGH — Observability]** Replace `NoopMetricsCollector` with real Micrometer collector; replace `AuditLogger.noop()` with `JdbcPersistentAuditService`.
9. **[HIGH — Security]** Replace `PolicyEngine` permissive stub with real evaluation backed by `config/policies/` YAML definitions.
10. **[HIGH — Integration]** Wire `DataCloudEventCloudClient` so YAPPC lifecycle events trigger AEP pipelines — this is the orchestration backbone.

# YAPPC Ecosystem Dependency Governance, Ownership Alignment, and Refactor Execution Report

> **Date**: 2026-03-10  
> **Scope**: YAPPC, Shared Platform (`platform/java/*`), AEP (`products/aep`), Data Cloud (`products/data-cloud`)  
> **Source of Truth**: Code, build wiring, runtime wiring, config assets  
> **Version**: 1.0.0

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Build and Module Graph Baseline](#2-build-and-module-graph-baseline)
3. [System Inventory](#3-system-inventory)
4. [Layer Ownership Matrix](#4-layer-ownership-matrix)
5. [Feature Dependency Graph](#5-feature-dependency-graph)
6. [Control Plane and Config Governance Analysis](#6-control-plane-and-config-governance-analysis)
7. [Data Flow Analysis](#7-data-flow-analysis)
8. [Agent, AI, and Runtime Responsibility Alignment](#8-agent-ai-and-runtime-responsibility-alignment)
9. [Data Cloud Responsibility Alignment](#9-data-cloud-responsibility-alignment)
10. [Tool and Plugin Governance Model](#10-tool-and-plugin-governance-model)
11. [Event Architecture Review](#11-event-architecture-review)
12. [Module Responsibility Matrix](#12-module-responsibility-matrix)
13. [Duplication Report](#13-duplication-report)
14. [Dependency Violation Report](#14-dependency-violation-report)
15. [Target Layered Architecture](#15-target-layered-architecture)
16. [Dependency Governance Rules](#16-dependency-governance-rules)
17. [Enforcement Mechanisms](#17-enforcement-mechanisms)
18. [Refactor Execution Plan](#18-refactor-execution-plan)
19. [Long-Term Platform Architecture](#19-long-term-platform-architecture)
20. [Platform Evolution Roadmap](#20-platform-evolution-roadmap)

---

## 1. Executive Summary

### Overall Assessment

The YAPPC ecosystem is in **mid-migration** state. The intended layered architecture is partially enforced but not consistently applied. Core platform libraries under `platform/java/*` provide rich, well-designed abstractions for agents, workflows, plugins, events, memory, learning, security, and AI integration. However, YAPPC has product-local implementations that **duplicate, bypass, or under-utilize** these platform primitives — a consequence of YAPPC's development timeline outpacing platform library availability.

### Key Findings

| Category | Severity | Count | Summary |
|----------|----------|-------|---------|
| **Duplicated implementations** | HIGH | 6 | Agent registry, agent base, workflow step, plugin SPI, AI integration, result types |
| **Misplaced business logic** | HIGH | 30+ | Service classes in `backend/api` that should be in `core/` or `services/` |
| **Missing platform adoption** | MEDIUM | 4 | UnifiedOperator, DataCloudAgentRegistry, PipelineBuilder, Plugin lifecycle |
| **Config/runtime drift** | MEDIUM | 3 | Config loaders not using platform config module; schema validation gaps |
| **Dependency violations** | HIGH | 8 | Product-local AI wiring, direct LLM calls, in-memory registries, sync blocking |
| **Governance gaps** | MEDIUM | 3 | No CODEOWNERS, doc-tag check non-fatal, ArchUnit tests only in platform |
| **Positive findings** | — | 5 | Product isolation enforced via Gradle, YAML validation tasks, Data Cloud integration via adapters, ActiveJ discipline, strict cross-product approval |

### Migration Status

- **Completed**: Data Cloud persistence integration (via `infrastructure/datacloud` adapters), ActiveJ async discipline, cross-product Gradle isolation
- **In Progress**: Agent framework adoption (extends `BaseAgent` but adds product concerns), lifecycle pipeline wiring to AEP
- **Not Started**: UnifiedOperator adoption, DataCloudAgentRegistry migration, platform Plugin SPI adoption, API layer service delegation
- **Regressed**: In-memory agent registry alongside platform registry; deprecated AIIntegrationService still present

### Risk Assessment

Without intervention, the current trajectory will result in:
1. Two parallel agent runtime stacks (YAPPC local + platform)
2. Two parallel plugin systems (YAPPC SPI + platform Plugin)
3. State loss on YAPPC restarts (in-memory registry)
4. Ungoverned AI provider integration (direct LangChain4J in API layer)
5. Continued API layer bloat consuming domain logic

---

## 2. Build and Module Graph Baseline

### Active Build Graph

**Root settings** (`settings.gradle.kts`) includes 27 YAPPC modules:

| Category | Modules | Count |
|----------|---------|-------|
| **Root** | `:products:yappc` | 1 |
| **Platform** | `:products:yappc:platform` | 1 |
| **Backend** | `api`, `persistence`, `auth`, `deployment`, `websocket` | 5 |
| **Services** | root, `lifecycle` | 2 |
| **Core** | `domain`, `scaffold` (+ `api`, `core`, `packs`), `ai`, `agents`, `spi`, `cli-tools`, `knowledge-graph`, `lifecycle`, `framework` (+ `integration-test`), `refactorer:api`, `refactorer:engine` | 15 |
| **Infrastructure** | `datacloud` | 1 |
| **Libs** | `yappc-domain` | 1 |
| **Launcher** | `launcher` | 1 |

**Platform dependencies** (declared in YAPPC standalone settings): 21 platform modules consumed.

**Cross-product dependencies** (approved in `product-isolation.gradle`):
- YAPPC → Data Cloud: **approved** (for persistence)
- YAPPC → AEP: **approved** (for event orchestration)

### Build Integrity Issues

| Issue | Severity | Detail |
|-------|----------|--------|
| **Standalone/monorepo parity** | LOW | Standalone settings uses `includePlatformLib()` helper to dynamically include platform modules — matches monorepo graph |
| **Services umbrella module** | MEDIUM | `products/yappc/services/build.gradle.kts` aggregates all sub-services + backend:api + platform — creates a mega-module with 18+ transitive deps |
| **Backend API dependency fan-out** | HIGH | `backend:api` has 13+ direct dependencies including `core:ai`, `core:framework`, `services:lifecycle` — acts as a God module |
| **Missing modules in root settings** | LOW | `services:domain`, `services:infrastructure`, `services:ai`, `services:scaffold` present in YAPPC standalone but not in root settings — orphaned in monorepo builds |
| **Examples module** | LOW | `examples:sample-build-generator-plugin` only in standalone settings |

### Module Tier Structure (Actual)

```
Tier 0 (Leaf):     libs:java:yappc-domain
Tier 1 (Core):     core:{framework, spi, ai, lifecycle, domain, agents, knowledge-graph, cli-tools, refactorer:*}
Tier 2 (Scaffold): core:scaffold:{core, schemas, adapters, packs, api, cli}
Tier 3 (Backend):  backend:{persistence, auth, deployment, websocket, api}
Tier 4 (Services): services:{domain, ai, lifecycle, infrastructure, scaffold}
Tier 5 (Infra):    infrastructure:datacloud, platform
Tier 6 (Entry):    launcher
```

---

## 3. System Inventory

### YAPPC Product-Specific Modules

| Module | Type | Status | Purpose |
|--------|------|--------|---------|
| `core:domain` | Domain Model | **Implemented** | Agent, task, vector, workflow value objects; CapabilityMatcher, TaskOrchestrator |
| `core:agents` | Agent Integration | **Implemented** | YAPPCAgentBase, YAPPCAgentRegistry, ParallelAgentExecutor, WorkflowContextAdapter |
| `core:ai` | AI Layer | **Implemented** | LLM routing, caching, A/B testing, cost optimization, vector embedding, prompt canvas |
| `core:lifecycle` | Lifecycle Engine | **Implemented** | 8-phase state machine, HTTP endpoints, phase operators |
| `core:framework` | Plugin Framework | **Implemented** | YAPPC-specific framework API and core |
| `core:spi` | Plugin SPI | **Implemented** | AgentPlugin, GeneratorPlugin, ValidatorPlugin, PluginContext |
| `core:scaffold` | Code Generation | **Implemented** | Multi-module scaffold engine (core, schemas, adapters, packs, api) |
| `core:knowledge-graph` | Knowledge Graph | **Implemented** | Graph model, YAPPCGraphService, change impact analysis |
| `core:cli-tools` | CLI | **Implemented** | Command-line tooling for YAPPC operations |
| `core:refactorer` | Code Refactoring | **Implemented** | OpenRewrite-based refactoring engine |
| `backend:api` | HTTP API | **Implemented** | REST/GraphQL endpoints, 30+ service classes, middleware |
| `backend:persistence` | Data Access | **Implemented** | Domain repositories, event store, security DAOs |
| `backend:auth` | Authentication | **Implemented** | Auth filters, security integration |
| `backend:websocket` | Real-time | **Implemented** | WebSocket connection management |
| `backend:deployment` | Deployment | **Implemented** | Deployment coordination |
| `services:lifecycle` | Lifecycle Service | **Implemented** | Phase services, policy engine, gate evaluation, AEP bridge |
| `services:ai` | AI Service | **Implemented** | AiServiceModule, YappcAiService facade |
| `services:domain` | Domain Service | **Implemented** | DomainServiceFacade |
| `services:infrastructure` | Infra Service | **Implemented** | InfrastructureServiceFacade |
| `services:scaffold` | Scaffold Service | **Implemented** | Scaffold orchestration |
| `infrastructure:datacloud` | DC Adapter | **Implemented** | YappcDataCloudRepository, ProjectEntity, KG plugin, widget/dashboard adapters |
| `libs:java:yappc-domain` | Shared DTOs | **Implemented** | Stable contracts/DTOs |
| `platform` (YAPPC) | WebSocket | **Implemented** | StreamPublisher, WebSocketConnection/Manager/Endpoint |
| `launcher` | Entry Point | **Implemented** | Application bootstrap |

### Shared Platform Modules (consumed by YAPPC)

| Module | Status | YAPPC Usage |
|--------|--------|-------------|
| `platform:java:core` | **Implemented** | Universal — used by 25+ YAPPC modules |
| `platform:java:domain` | **Implemented** | Domain primitives |
| `platform:java:database` | **Implemented** | Persistence abstractions |
| `platform:java:http` | **Implemented** | HTTP server primitives |
| `platform:java:observability` | **Implemented** | Metrics, tracing |
| `platform:java:security` | **Implemented** | RBAC, ABAC, JWT, OAuth2 |
| `platform:java:agent-framework` | **Implemented** | BaseAgent, TypedAgent — YAPPC extends but also duplicates |
| `platform:java:agent-dispatch` | **Implemented** | 3-tier dispatch (Java/Service/LLM) — underutilized by YAPPC |
| `platform:java:agent-memory` | **Implemented** | 6-tier memory plane — not directly wired into YAPPC |
| `platform:java:agent-learning` | **Implemented** | Consolidation pipeline — not directly wired into YAPPC |
| `platform:java:agent-registry` | **Implemented** | DataCloud-backed registry — YAPPC uses own in-memory registry |
| `platform:java:workflow` | **Implemented** | UnifiedOperator, PipelineBuilder, DAGPipelineExecutor — YAPPC uses own WorkflowStep |
| `platform:java:plugin` | **Implemented** | Plugin lifecycle, hot-reload, capability model — YAPPC uses own SPI |
| `platform:java:ai-integration` | **Implemented** | LLM providers, prompts, embeddings — YAPPC wraps but also bypasses |
| `platform:java:event-cloud` | **Implemented** | Append-only event log, real-time tailing — used indirectly via AEP |
| `platform:java:governance` | **Partially wired** | PolicyEngine interface only — YAPPC implements own YappcPolicyEngine |
| `platform:java:config` | **Implemented** | Config loading — YAPPC uses own ConfigLoader |
| `platform:java:workflow` | **Implemented** | DurableWorkflowEngine — YAPPC lifecycle does not use it |
| `platform:java:audit` | **Implemented** | Audit trail — used in backend:api |
| `platform:java:connectors` | **Implemented** | External connectors — used by AEP |
| `platform:java:ingestion` | **Implemented** | Data ingestion — not directly used by YAPPC |

### AEP Modules

| Module | Status | YAPPC Relationship |
|--------|--------|-------------------|
| `products:aep:platform` | **Implemented** | Orchestrator, pipeline management; YAPPC publishes events to AEP |
| `products:aep:launcher` | **Implemented** | AEP entry point |

### Data Cloud Modules

| Module | Status | YAPPC Relationship |
|--------|--------|-------------------|
| `products:data-cloud:spi` | **Implemented** | EventLogStore, TenantContext — referenced by YAPPC infrastructure |
| `products:data-cloud:platform` | **Implemented** | EntityRepository, KnowledgeGraphPlugin, MemoryTier — YAPPC uses via adapters |
| `products:data-cloud:launcher` | **Implemented** | Data Cloud entry point |

### Control Plane/Config Assets

| Asset | Location | Status |
|-------|----------|--------|
| Agent Catalog | `config/agents/agent-catalog.yaml` | **Implemented** — validated by Gradle task |
| Agent Definitions | `config/agents/definitions/` (50+ agents) | **Implemented** — per-phase agent definitions |
| Event Routing | `config/agents/event-routing.yaml` | **Implemented** — 50+ event-to-agent mappings |
| Event Schemas | `config/agents/event-schemas/` (5 schemas) | **Implemented** — validated by Gradle task |
| Lifecycle Stages | `config/lifecycle/stages.yaml` | **Implemented** — 8 stages with entry/exit criteria |
| Lifecycle Transitions | `config/lifecycle/transitions.yaml` | **Implemented** — state machine rules |
| Pipelines | `config/pipelines/*.yaml` | **Implemented** — validated by Gradle task |
| Workflows | `config/workflows/canonical-workflows.yaml` | **Implemented** — canonical patterns |
| Task Domains | `config/tasks/domains/` (26 task domains) | **Implemented** — comprehensive task taxonomy |
| Policies | `config/policies/core.rego` | **Implemented** — OPA Rego policy rules |
| Memory Redaction | `config/memory/redaction-rules.yaml` | **Implemented** — event sourcing redaction |
| Personas | `config/personas/personas.yaml` | **Implemented** — user persona catalog |
| Schemas | `config/schemas/domain-definition.schema.json` | **Implemented** — JSON schema validation |
| Validation | `config/validation/pipeline-schema-v1.json` | **Implemented** — pipeline structure schema |

---

## 4. Layer Ownership Matrix

### Validated Layer Model

After analyzing the actual codebase, the candidate layer model is **confirmed with refinements**:

#### Layer 1 — Shared Runtime and Platform Primitives

**Owner**: `platform/java/*`  
**Status**: **Implemented and stable**

| Module | Responsibility | Product-Agnostic? |
|--------|---------------|-------------------|
| `core` | Offset types, base primitives | YES |
| `domain` | Shared domain primitives | YES |
| `database` | Persistence abstractions | YES |
| `http` | HTTP server infrastructure | YES |
| `observability` (+http, +clickhouse) | Metrics, tracing, dashboards | YES |
| `security` | RBAC, ABAC, JWT, OAuth2, encryption | YES |
| `config` | Configuration loading | YES |
| `testing` | Test utilities, EventloopTestBase | YES |
| `runtime` | Runtime lifecycle | YES |
| `audit` | Audit trail infrastructure | YES |
| `agent-framework` | Agent lifecycle, TypedAgent, BaseAgent | YES |
| `agent-dispatch` | 3-tier agent execution routing | YES |
| `agent-memory` | 6-tier memory plane | YES |
| `agent-learning` | Consolidation pipeline, skill promotion | YES |
| `agent-registry` | DataCloud-backed agent registration | YES |
| `agent-resilience` | Agent retry, fallback, circuit breaker | YES |
| `workflow` | UnifiedOperator, Pipeline, DAG executor | YES |
| `plugin` | Plugin lifecycle, hot-reload, capability model | YES |
| `event-cloud` | Append-only event log, real-time tailing | YES |
| `governance` | Policy engine interface | YES (but minimal) |
| `connectors` | External system connectors | YES |
| `ingestion` | Data ingestion primitives | YES |

**Refinement**: `platform/java/ai-integration` belongs in **Layer 1**, not Layer 2. It provides product-agnostic LLM provider integration, prompt engineering, and embedding services. AEP consumes it as a library, not as the owner.

#### Layer 2 — Cross-Product Intelligence and Execution Platform

**Owner**: `products/aep/platform` + `platform/java/ai-integration`

| Component | Current Owner | Correct Owner | Status |
|-----------|--------------|---------------|--------|
| Pipeline orchestration | AEP | AEP | Correct |
| Agent coordination | AEP + YAPPC (duplicated) | AEP | **Overlapping** |
| Event-driven execution | AEP | AEP | Correct |
| LLM provider integration | `platform:java:ai-integration` | Layer 1 | **Refinement: Layer 1** |
| Prompt orchestration | YAPPC `core:ai` | Should delegate to `platform:java:ai-integration` | **Misplaced** |
| Tool invocation coordination | YAPPC `core:agents` | AEP dispatch | **Misplaced** |

#### Layer 3 — Cross-Product Data, Memory, and Knowledge Platform

**Owner**: `products/data-cloud/platform` + `products/data-cloud/spi`

| Capability | Currently Provided? | YAPPC Duplicates? |
|-----------|-------------------|-------------------|
| Entity persistence (EntityRepository) | YES | No — YAPPC uses via adapter |
| Knowledge graph (KnowledgeGraphPlugin) | YES | No — YAPPC uses via adapter |
| Event log (EventLogStore) | YES (SPI) | No — used via AEP |
| Memory tiers (MemoryTier) | YES (HOT→ARCHIVE) | Not wired — YAPPC uses in-memory |
| Graph operations (GraphOperations) | YES | No — YAPPC uses via adapter |
| Vector search | YES (LangChain4J embeddings) | YES — YAPPC `core:ai` has own vector code |
| Execution history storage | Partially | YAPPC stores some in-memory |
| Audit dataset storage | Via platform:audit | Correct |

#### Layer 4 — YAPPC Product Layer

| Component | Should Remain in YAPPC? | Reason |
|-----------|------------------------|--------|
| 8-phase lifecycle state machine | YES | Product-specific orchestration |
| Scaffold engine (code generation) | YES | Product-specific capability |
| Refactorer engine (OpenRewrite) | YES | Product-specific capability |
| Knowledge graph domain logic | YES | Product-specific analysis (change impact, dependency) |
| Task domain taxonomy (26 domains) | YES | Product-specific domain knowledge |
| Agent definitions (50+ agents) | YES | Product-specific agent catalog |
| CLI tools | YES | Product-specific tooling |
| YAPPC-specific domain model | YES | Product-specific entities |
| Agent base class (YAPPCAgentBase) | PARTIALLY | Should compose, not duplicate |
| In-memory agent registry | NO | Should use DataCloudAgentRegistry |
| Product-local AI integration | NO | Should delegate to platform |
| Product-local plugin SPI | NO | Should adopt platform Plugin |
| Product-local workflow step | NO | Should adopt UnifiedOperator |

---

## 5. Feature Dependency Graph

### Core Feature: Lifecycle Management

```
User Request → backend:api (REST endpoint)
  → services:lifecycle (YappcLifecycleService)
    → StageConfigLoader (config/lifecycle/stages.yaml)
    → TransitionConfigLoader (config/lifecycle/transitions.yaml)
    → AdvancePhaseUseCase
      → PhaseTransitionValidatorOperator
      → GateOrchestratorOperator → YappcPolicyEngine → HumanApprovalService
      → AgentDispatchOperator → core:agents (YAPPCAgentBase)
      → LifecycleStatePublisherOperator → AepEventBridge → AEP (HTTP)
    → infrastructure:datacloud (ProjectEntity persistence)
```

**Modules**: `backend:api`, `services:lifecycle`, `core:agents`, `core:ai`, `infrastructure:datacloud`  
**Config**: `lifecycle/stages.yaml`, `lifecycle/transitions.yaml`, `agents/event-routing.yaml`, `pipelines/lifecycle-management-v1.yaml`  
**Events**: `lifecycle.phase.advanced`, `lifecycle.phase.blocked`  
**Persistence**: Data Cloud (projects collection)

### Core Feature: Agent Execution

```
Agent Dispatch Event → core:agents (YAPPCAgentBase)
  → core:ai (AIIntegrationService) → platform:java:ai-integration (LLM providers)
  → core:framework (plugin resolution)
  → AepEventPublisher (HTTP) → AEP
  → platform:java:agent-framework (BaseAgent)
```

**Violation**: YAPPC agents extend `BaseAgent` but embed AEP event publishing, budget tracking, and context conversion instead of composing with platform dispatch.

### Core Feature: Code Scaffolding

```
Scaffold Request → backend:api → services:scaffold
  → core:scaffold:api (gRPC/HTTP)
    → core:scaffold:core (template engine, OpenRewrite)
    → core:scaffold:packs (pack definitions)
    → core:scaffold:adapters (output adapters)
  → infrastructure:datacloud (artifact persistence)
```

**Status**: Self-contained product feature, correctly layered.

### Core Feature: Knowledge Graph

```
Analysis Request → core:knowledge-graph
  → YAPPCGraphService
    → infrastructure:datacloud (KnowledgeGraphDataCloudPlugin)
      → data-cloud:platform (KnowledgeGraphPlugin)
  → Change Impact Analysis (neighbor traversal, depth 5)
```

**Status**: Correctly delegates to Data Cloud for persistence. Product-specific analysis logic stays in YAPPC.

### Core Feature: AI Integration

```
AI Request → backend:api/ai (LLMGatewayAIIntegrationService)
  → core:ai (AIIntegrationService - DEPRECATED wrapper)
    → platform:java:ai-integration (canonical AIIntegrationService)
  → LangChain4J (direct dependency in services:ai and backend:api)
```

**Violation**: `backend:api` has direct LangChain4J dependency. `NoOpLLMGateway` mock present — evidence of incomplete wiring.

---

## 6. Control Plane and Config Governance Analysis

### Config Family Assessment

| Config Family | Location | Schema Validation | Runtime Loader | Consumer | Ownership | Recommendation |
|--------------|----------|-------------------|----------------|----------|-----------|----------------|
| **Agent Catalog** | `config/agents/agent-catalog.yaml` | Gradle `validateAgentCatalog` (5-pass) | `ConfigLoader.loadAgentCapabilities()` | `backend:api`, `core:agents` | YAPPC product | **Correct** — product-specific agent definitions |
| **Agent Definitions** | `config/agents/definitions/` | Gradle (agent ID uniqueness, delegation chains) | Not directly loaded at runtime | Declarative catalog | YAPPC product | **Correct** — but needs runtime loading validation |
| **Event Routing** | `config/agents/event-routing.yaml` | Gradle (validates agent ID refs) | Not clear — may be AEP-consumed | AEP bridge? | **Ambiguous** | Should define clear loader; cross-reference with AEP routing |
| **Event Schemas** | `config/agents/event-schemas/*.json` | Gradle `validateEventSchemas` | Not directly loaded | Declarative contracts | YAPPC + AEP shared | **Review**: Schemas may belong in `platform:contracts` |
| **Lifecycle Stages** | `config/lifecycle/stages.yaml` | None (no Gradle task) | `StageConfigLoader` | `services:lifecycle` | YAPPC product | **Correct** — but add schema validation |
| **Lifecycle Transitions** | `config/lifecycle/transitions.yaml` | None | `TransitionConfigLoader` | `services:lifecycle` | YAPPC product | **Correct** — but add schema validation |
| **Pipelines** | `config/pipelines/*.yaml` | Gradle `validatePipelines` | `PipelineDefinitionLoader` | `backend:api` | YAPPC + AEP shared | **Review**: Pipeline definitions may partially belong in AEP |
| **Workflows** | `config/workflows/canonical-workflows.yaml` | None | `ConfigLoader.loadWorkflows()` | `backend:api` | YAPPC product | **Correct** |
| **Task Domains** | `config/tasks/domains/` (26 files) | None | `ConfigLoader` | `core:domain`, `services:domain` | YAPPC product | **Correct** |
| **Policies** | `config/policies/core.rego` | None | OPA engine external | `services:lifecycle` (via PolicyEngine) | YAPPC product | **Correct** — but bridge to `platform:java:governance` |
| **Memory Redaction** | `config/memory/redaction-rules.yaml` | None | Unknown | Agent memory system | YAPPC product | **Review**: Should activate via `platform:java:agent-memory` governance |
| **Personas** | `config/personas/personas.yaml` | None | `ConfigLoader.loadPersonas()` | `backend:api` | YAPPC product | **Correct** |

### Config Governance Gaps

1. **No schema validation** for lifecycle stages/transitions, workflows, task domains, policies, memory redaction, personas
2. **Config loaders use custom YAML parsing** (`ConfigLoader` in `backend:api`) instead of `platform:java:config` module
3. **Event routing** ownership ambiguous — consumed by YAPPC but routes to AEP; needs clear ownership boundary
4. **Event schemas** may need shared ownership — they define contracts consumed by both YAPPC and AEP
5. **Memory redaction rules** not wired to `platform:java:agent-memory` governance
6. **Pipeline definitions** straddle YAPPC and AEP ownership — pipeline operator definitions are product-specific but pipeline execution is AEP's responsibility

### Recommendations

- **P1**: Add Gradle validation tasks for lifecycle stages/transitions (same pattern as `validateAgentCatalog`)
- **P1**: Migrate `ConfigLoader` to delegate to `platform:java:config` for YAML loading
- **P2**: Move event schemas to `platform:contracts` or `products/yappc/contracts`
- **P2**: Define explicit ownership for event routing (YAPPC defines → AEP consumes)
- **P3**: Wire memory redaction rules to `platform:java:agent-memory` governance SPI

---

## 7. Data Flow Analysis

### Primary Data Flows

#### Flow 1: Lifecycle Phase Transition
```
UI → backend:api (REST) → services:lifecycle → AdvancePhaseUseCase
  ├── Validate: StageConfigLoader + TransitionConfigLoader (YAML files)
  ├── Gate: YappcPolicyEngine (OPA core.rego) → HumanApprovalService (if needed)
  ├── Dispatch: AgentDispatchOperator → StageSpec.agentAssignments
  ├── Publish: AepEventBridge → HttpAepEventPublisher → AEP HTTP API
  └── Persist: infrastructure:datacloud → data-cloud:platform (ProjectEntity)
```

**Data ownership**: Lifecycle state persisted in Data Cloud via `ProjectEntity`. Event published to AEP.

#### Flow 2: Agent Execution
```
Dispatch Event → core:agents (YAPPCAgentBase.execute())
  ├── Context: StepContext → AgentContext (product-local conversion)
  ├── AI Call: core:ai → platform:java:ai-integration → LLM Provider
  ├── Result: StepResult<O> (product type, not OperatorResult)
  └── Event: AepEventPublisher.publish() → AEP (fire-and-forget, failures logged)
```

**Data ownership**: Agent results not persisted to Data Cloud. AEP receives events. In-memory only.

#### Flow 3: Knowledge Graph Operations
```
Analysis Request → core:knowledge-graph → YAPPCGraphService
  ├── Nodes: KnowledgeGraphDataCloudPlugin → data-cloud:platform (kg_node collection)
  ├── Edges: KnowledgeGraphDataCloudPlugin → data-cloud:platform (kg_edge collection)
  └── Analysis: In-memory graph traversal (depth 5, impact scoring)
```

**Data ownership**: Graph data correctly in Data Cloud. Analysis logic correctly in YAPPC.

#### Flow 4: AI Suggestions
```
User Request → backend:api/ai (AISuggestionsController)
  ├── Route: LLMGatewayAIIntegrationService (backend:api local class)
  ├── LLM: Direct LangChain4J call OR NoOpLLMGateway (mock)
  └── Response: In-memory, not persisted
```

**Violation**: AI integration in backend:api bypasses `platform:java:ai-integration`.

### Data Ownership Matrix

| Data Type | Current Owner | Correct Owner | Status |
|-----------|--------------|---------------|--------|
| Project entities | Data Cloud (via adapter) | Data Cloud | **Correct** |
| Lifecycle state | Data Cloud (ProjectEntity.currentStage) | Data Cloud | **Correct** |
| Knowledge graph (nodes/edges) | Data Cloud (kg_node, kg_edge) | Data Cloud | **Correct** |
| Agent execution results | In-memory (YAPPC) | Data Cloud + EventCloud | **Violation** |
| Agent registry entries | In-memory (ConcurrentHashMap) | Data Cloud (agent-registry collection) | **Violation** |
| Agent memory/learning | Not wired | Data Cloud + platform:agent-memory | **Missing** |
| Workflow/pipeline state | Not persisted | DurableWorkflowEngine + Data Cloud | **Missing** |
| Audit trails | platform:java:audit | Correct | **Correct** |
| Event history | AEP (published via HTTP) | EventCloud + Data Cloud | **Partial** |
| AI conversation history | Not persisted | Data Cloud | **Missing** |
| Widget/dashboard config | Data Cloud (via adapter) | Data Cloud | **Correct** |
| Refactorer artifacts | Data Cloud (via adapter) | Data Cloud | **Correct** |

### Critical Data Flow Violations

1. **Agent execution results not persisted**: `StepResult<O>` returned in-memory but not recorded in EventCloud or Data Cloud. No execution history for replay or learning.
2. **Agent registry in-memory**: `YAPPCAgentRegistry` uses `ConcurrentHashMap` — all registered agents lost on restart. Platform `DataCloudAgentRegistry` provides persistence.
3. **No agent memory wiring**: `platform:java:agent-memory` (episodic, semantic, procedural) exists but YAPPC doesn't use it. Learned patterns/procedures not persisted.
4. **Workflow state not durable**: Lifecycle pipeline state exists only in-flight. `DurableWorkflowEngine` in platform supports temporal-style retry/compensation but is not adopted.
5. **AI conversation context lost**: No persistence for AI interactions — prevents learning, audit, and cost attribution.

---

## 8. Agent, AI, and Runtime Responsibility Alignment

### Current State: Agent Runtime

| Concern | Platform Module | YAPPC Module | Status |
|---------|----------------|-------------|--------|
| Agent interface | `agent-framework` → `Agent`, `TypedAgent` | (extends) | **Correct** |
| Agent base class | `agent-framework` → `BaseAgent` | `core:agents` → `YAPPCAgentBase` (extends + adds) | **Overlapping** |
| Agent registration | `agent-registry` → `DataCloudAgentRegistry` | `core:agents` → `YAPPCAgentRegistry` (in-memory) | **Duplicated** |
| Agent dispatch | `agent-dispatch` → `CatalogAgentDispatcher` (3-tier) | YAPPC has custom dispatch via `StepContext` | **Duplicated** |
| Agent lifecycle | `agent-framework` → `BaseAgent.initialize/start/stop` | YAPPC overrides lifecycle in `YAPPCAgentBase` | **Overlapping** |
| Multi-agent coordination | Not in platform | `core:agents` → `ParallelAgentExecutor` | **YAPPC-specific** (candidate for platform) |
| Workflow integration | `workflow` → `UnifiedOperator`, `Pipeline` | `core:agents` → `WorkflowStep` (parallel impl) | **Duplicated** |
| Agent memory | `agent-memory` → `MemoryPlane` (6-tier) | Not wired | **Missing adoption** |
| Agent learning | `agent-learning` → `ConsolidationPipeline` | Not wired | **Missing adoption** |
| Agent resilience | `agent-resilience` | Not wired | **Missing adoption** |

### What YAPPCAgentBase Adds Over Platform BaseAgent

```java
public abstract class YAPPCAgentBase<I, O> extends BaseAgent<StepRequest<I>, StepResult<O>>
    implements WorkflowStep<I, O>
```

**Additions**:
1. `WorkflowStep<I, O>` implementation (contract, validation, execution)
2. `StepContext` → `AgentContext` conversion
3. `AepEventPublisher` integration (embedded publishing)
4. Budget tracking override (reimplements `AgentContext.remainingBudget`)
5. `TraceContext` propagation

**Assessment**: Items 1-3 are product-specific adapter logic. Items 4-5 are platform concerns being overridden. The right pattern is **composition via decorator**, not **inheritance with override**.

### Current State: AI Integration

| Concern | Platform Module | YAPPC Module | Status |
|---------|----------------|-------------|--------|
| LLM provider integration | `ai-integration` → `AIIntegrationService` | `core:ai` → `AIIntegrationService` (DEPRECATED wrapper) | **Deprecated duplicate** |
| LLM routing | Not in platform | `core:ai` → `router/` | **YAPPC-specific** (candidate for platform) |
| Response caching | Not in platform | `core:ai` → `cache/` | **YAPPC-specific** (candidate for platform) |
| A/B testing | Not in platform | `core:ai` → `abtesting/` | **YAPPC-specific** |
| Cost optimization | Not in platform | `core:ai` → `cost/` | **YAPPC-specific** (candidate for platform) |
| Prompt engineering | `ai-integration` → `prompts/` | `core:ai` → `canvas/` | **Partially overlapping** |
| Vector embedding | `ai-integration` → `embedding/` | `core:ai` → `vector/` | **Duplicated** |
| Direct LLM calls | — | `backend:api` → LangChain4J dependency | **Violation** |

### Current State: Workflow/Orchestration

| Concern | Platform Module | YAPPC Module | Status |
|---------|----------------|-------------|--------|
| Operator abstraction | `workflow` → `UnifiedOperator` | `core:agents` → `WorkflowStep` | **Duplicated** |
| Pipeline builder | `workflow` → `PipelineBuilder` | Not used | **Missing adoption** |
| DAG execution | `workflow` → `DAGPipelineExecutor` | Not used | **Missing adoption** |
| Durable workflows | `workflow` → `DurableWorkflowEngine` | Not used | **Missing adoption** |
| Lifecycle pipeline | — | `services:lifecycle` → 4 operators wired | **Product-specific** |
| Operator types | `workflow` → STREAM, PATTERN, LEARNING | Not used | **Missing adoption** |

### Recommendations

1. **P0**: Refactor `YAPPCAgentBase` to use composition over inheritance — delegate AEP event publishing, budget tracking, and trace propagation to decorators
2. **P0**: Migrate `YAPPCAgentRegistry` to `DataCloudAgentRegistry` wrapper with YAPPC-specific discovery extensions
3. **P1**: Remove deprecated `core:ai/AIIntegrationService`; ensure all AI calls route through `platform:java:ai-integration`
4. **P1**: Remove direct LangChain4J dependency from `backend:api`
5. **P1**: Migrate lifecycle operators to `UnifiedOperator` interface
6. **P2**: Wire `platform:java:agent-memory` into YAPPC agent execution
7. **P2**: Wire `platform:java:agent-learning` consolidation pipeline
8. **P3**: Evaluate promoting LLM routing, caching, and cost optimization from YAPPC to `platform:java:ai-integration`

---

## 9. Data Cloud Responsibility Alignment

### What Data Cloud Currently Provides

| Capability | Implementation | YAPPC Usage |
|-----------|---------------|-------------|
| Entity persistence | `EntityRepository` with CRUD + multi-tenancy | YES — via `YappcDataCloudRepository<T>` adapter |
| Knowledge graph | `KnowledgeGraphPlugin` + `GraphOperations` | YES — via `KnowledgeGraphDataCloudPlugin` |
| Memory tiers | `MemoryTier` (HOT→ARCHIVE) | NO — not wired |
| Event log | `EventLogStore` (SPI) | NO — YAPPC uses AEP HTTP bridge instead |
| Graph analytics | Centrality, communities, paths | NO — YAPPC does own traversal |
| Storage tiering | RocksDB, SQLite, S3, Iceberg | NO — not directly consumed |
| Vector search | LangChain4J embeddings | NO — YAPPC has own vector code |

### Where YAPPC Bypasses Data Cloud

| Concern | Current YAPPC Implementation | Should Use |
|---------|------------------------------|-----------|
| Agent registry persistence | `ConcurrentHashMap` (in-memory) | `DataCloudAgentRegistry` (Data Cloud collection) |
| Agent execution history | Not persisted | Data Cloud entity + EventCloud events |
| AI conversation context | Not persisted | Data Cloud entity |
| Workflow state | In-flight only | `DurableWorkflowEngine` + Data Cloud checkpoints |
| Vector embeddings | `core:ai/vector/` (product-local) | Data Cloud vector search |
| Agent memory (episodic) | Not collected | Data Cloud via `platform:java:agent-memory` |

### Where YAPPC Correctly Delegates to Data Cloud

- `ProjectEntity` persistence (projects collection)
- Knowledge graph nodes/edges (kg_node, kg_edge collections)
- Widget/dashboard configuration
- Refactorer artifacts

### Recommendations

1. **P0**: Wire agent registry to Data Cloud (via platform `DataCloudAgentRegistry`)
2. **P1**: Persist agent execution results to Data Cloud
3. **P1**: Adopt Data Cloud vector search, retire `core:ai/vector/`
4. **P2**: Wire agent memory plane (episodic, semantic, procedural) via Data Cloud storage
5. **P2**: Adopt `DurableWorkflowEngine` with Data Cloud state checkpoints
6. **P3**: Leverage Data Cloud graph analytics (centrality, communities) instead of YAPPC-local traversal

---

## 10. Tool and Plugin Governance Model

### Current Tool/Plugin Classification

#### Shared Platform Tools (`platform/java/plugin`)

| Tool/Plugin | Type | Status |
|-------------|------|--------|
| `Plugin` interface | Core SPI | **Implemented** — lifecycle, capabilities, health |
| `PluginRegistry` | Registry | **Implemented** — DI-friendly, lifecycle management |
| `HotReloadPluginManager` | Hot-reload | **Implemented** — FileSystem WatchService |
| `StoragePlugin` | Storage SPI | **Implemented** — write/read/delete |
| `AIModelPlugin` | AI Model SPI | **Implemented** — extends LLMGateway |
| `StreamingPlugin` | Streaming SPI | **Implemented** — pub/sub |
| `GovernancePlugin` | Policy SPI | **Implemented** — validation |
| `PluginInteractionBus` | Inter-plugin comms | **Implemented** |
| 14 `PluginType` categories | Classification | **Implemented** |

#### YAPPC Product Plugin System (`core/spi`)

| Tool/Plugin | Type | Status |
|-------------|------|--------|
| `AgentPlugin` | Agent extension | **Implemented** — extends `YappcPlugin` (NOT platform Plugin) |
| `GeneratorPlugin` | Code generation | **Implemented** |
| `ValidatorPlugin` | Validation | **Implemented** |
| `PluginContext` (YAPPC) | Sandbox | **Implemented** — separate from platform `PluginContext` |
| Bridge/Adapter | Migration | **Implemented** — bridge/migration packages present |

#### YAPPC Product Tools (`tools/`)

| Tool | Type | Status |
|------|------|--------|
| `tools/scripts/` | Automation scripts | **Implemented** — product-specific |
| `tools/vscode-extension/` | VS Code integration | **Implemented** — product-specific |

### Plugin System Duplication

**Two parallel hierarchies exist**:

```
Platform Plugin Hierarchy:
  Plugin (interface)
    ├── StoragePlugin
    ├── AIModelPlugin
    ├── StreamingPlugin
    ├── GovernancePlugin
    └── (PluginRegistry, HotReloadPluginManager)

YAPPC Plugin Hierarchy:
  YappcPlugin (abstract class)
    ├── AgentPlugin
    ├── GeneratorPlugin
    ├── ValidatorPlugin
    └── (YAPPC PluginContext, bridge/adapter packages)
```

**Gap Analysis**:

| Platform Plugin Feature | YAPPC Plugin Has It? |
|------------------------|---------------------|
| `initialize(PluginContext)` | NO |
| `start()` / `stop()` / `shutdown()` | NO |
| `healthCheck()` | NO |
| `PluginCapability` (typed) | NO (string-based) |
| Structured metadata (author, license, tags) | NO |
| Hot-reload | NO |
| Inter-plugin communication bus | NO |
| 14 PluginType categories | NO |

### Recommendations

1. **P1**: Migrate `YappcPlugin` hierarchy to extend `platform:java:plugin:Plugin`
2. **P1**: Add lifecycle methods (initialize, start, stop) to YAPPC plugins
3. **P2**: Register YAPPC agent/generator/validator plugins in platform `PluginRegistry`
4. **P2**: Adopt platform `PluginCapability` instead of string-based capabilities
5. **P3**: Enable hot-reload for YAPPC plugins via `HotReloadPluginManager`
6. **P3**: Retire YAPPC-specific `PluginContext`, use platform's

---

## 11. Event Architecture Review

### Event Production

| Producer | Events | Transport | Destination |
|----------|--------|-----------|-------------|
| `services:lifecycle` → `AepEventBridge` | `lifecycle.phase.advanced`, `lifecycle.phase.blocked` | HTTP POST to AEP | AEP orchestrator |
| `core:agents` → `HttpAepEventPublisher` | Agent dispatch/result events | HTTP POST to AEP | AEP orchestrator |
| `core:lifecycle` → operators | Phase transition, gate evaluation | Internal (in-process) | Pipeline operators |
| Config: `event-routing.yaml` | 50+ event-to-agent mappings | Declarative (YAML) | AEP routing table |

### Event Consumption

| Consumer | Events | Source |
|----------|--------|--------|
| AEP Orchestrator | All YAPPC-published events | HTTP ingestion |
| YAPPC agents (via routing) | `test.failed`, `release.ready`, `agent.output.produced`, etc. | AEP dispatch back |
| Phase gate agents | `phase-transition-v1` events | Lifecycle operators |
| Audit trail agent | `agent.output.produced` | Event routing |
| Cost governor agent | `agent.cost.recorded` | Event routing |

### Event Architecture Issues

| Issue | Severity | Detail |
|-------|----------|--------|
| **HTTP-only transport** | MEDIUM | Events published via HTTP POST to AEP — no EventCloud integration for persistence/replay |
| **Fire-and-forget publishing** | HIGH | `HttpAepEventPublisher` logs failures but never retries. Event loss under AEP downtime. |
| **No event persistence** | HIGH | Events not recorded in EventCloud. No audit trail for event history. |
| **No dead-letter handling** | MEDIUM | Failed events silently dropped (`catch` and log) |
| **Hardcoded AEP endpoint** | LOW | `http://127.0.0.1:7004` with env override — should use service discovery |
| **Event schemas not shared** | MEDIUM | Schemas in `config/agents/event-schemas/` not published to platform contracts |
| **Config-driven routing unverified at runtime** | MEDIUM | `event-routing.yaml` validated at build time but runtime loading unclear |
| **No back-pressure** | MEDIUM | HTTP publishing has 500ms-3000ms timeout but no circuit breaker or back-pressure |

### Event Ownership

| Event Family | Current Owner | Correct Owner |
|-------------|--------------|---------------|
| Lifecycle phase events | YAPPC (product-specific) | YAPPC — correct |
| Agent dispatch events | YAPPC | YAPPC → AEP — correct direction |
| Agent result events | YAPPC | YAPPC → AEP + EventCloud — needs persistence |
| Event schemas | `config/agents/event-schemas/` (YAPPC local) | `platform:contracts` or `products/yappc/contracts` |
| Event routing definitions | `config/agents/event-routing.yaml` (YAPPC local) | YAPPC defines, AEP consumes — needs formal contract |

### Recommendations

1. **P0**: Add EventCloud persistence for all published events (append before HTTP publish)
2. **P0**: Implement retry/dead-letter for `HttpAepEventPublisher` failures
3. **P1**: Move event schemas to shared contracts module
4. **P1**: Add back-pressure or circuit breaker to AEP HTTP client
5. **P2**: Validate event-routing.yaml at runtime, not just build time
6. **P3**: Consider replacing HTTP transport with EventCloud streaming for YAPPC→AEP events

---

## 12. Module Responsibility Matrix

### YAPPC Backend Modules

| Module | Actual Responsibility | Intended Responsibility | Layer | Ownership Status |
|--------|----------------------|------------------------|-------|-----------------|
| `backend:api` | HTTP endpoints + 30 service classes + AI + pipeline + workflow + plugin + memory logic | HTTP endpoints only (thin controller) | Product | **Misplaced** — service logic must move to `services/*` or `core/*` |
| `backend:persistence` | Domain repositories, event store, security DAOs | Data access layer | Product | **Correct** — but event store should use EventCloud |
| `backend:auth` | Auth filters on top of `backend:persistence` + `platform:security` | Auth integration | Product | **Correct** |
| `backend:websocket` | WebSocket infrastructure | Real-time communication | Product | **Correct** |
| `backend:deployment` | Deployment coordination | Deployment | Product | **Correct** |

### YAPPC Core Modules

| Module | Actual Responsibility | Intended Responsibility | Layer | Ownership Status |
|--------|----------------------|------------------------|-------|-----------------|
| `core:domain` | Agent, task, workflow value objects + domain services | Domain model | Product | **Correct** |
| `core:agents` | YAPPCAgentBase, YAPPCAgentRegistry, ParallelAgentExecutor, WorkflowStep | Product agent adapter over platform | Product | **Overlapping** — duplicates registry, workflow step, budget tracking |
| `core:ai` | LLM routing, caching, A/B test, cost, vector, prompts, deprecated AIIntegrationService | Product AI extensions | Product | **Overlapping** — vector/embedding duplicates platform, deprecated wrapper present |
| `core:lifecycle` | 8-phase state machine, HTTP endpoints, phase operators | Product lifecycle engine | Product | **Correct** — but HTTP endpoints should be in `backend:api` |
| `core:framework` | YAPPC framework API and core | Product framework | Product | **Overlapping** — should adopt platform plugin system |
| `core:spi` | AgentPlugin, GeneratorPlugin, ValidatorPlugin | Product plugin SPI | Product | **Overlapping** — parallel to platform plugin SPI |
| `core:scaffold` | Multi-module code generation engine | Product scaffolding | Product | **Correct** — unique product capability |
| `core:knowledge-graph` | Graph model, change impact analysis | Product knowledge analysis | Product | **Correct** — delegating persistence to Data Cloud |
| `core:cli-tools` | CLI tooling | Product CLI | Product | **Correct** |
| `core:refactorer` | OpenRewrite-based refactoring | Product refactoring | Product | **Correct** |

### YAPPC Service Modules

| Module | Actual Responsibility | Intended Responsibility | Layer | Ownership Status |
|--------|----------------------|------------------------|-------|-----------------|
| `services:lifecycle` | Phase services, policy engine, gate evaluation, AEP bridge | Lifecycle orchestration | Product | **Correct** — product-specific orchestration |
| `services:ai` | AiServiceModule, YappcAiService | AI service facade | Product | **Correct** — but should delegate to `platform:java:ai-integration` |
| `services:domain` | DomainServiceFacade | Domain service | Product | **Correct** |
| `services:infrastructure` | InfrastructureServiceFacade | Infra service | Product | **Correct** |
| `services:scaffold` | Scaffold orchestration | Scaffold service | Product | **Correct** |

### YAPPC Infrastructure Modules

| Module | Actual Responsibility | Intended Responsibility | Layer | Ownership Status |
|--------|----------------------|------------------------|-------|-----------------|
| `infrastructure:datacloud` | Repository adapters, entity definitions, KG plugin | Data Cloud integration | Product | **Correct** — clean adapter pattern |
| `platform` (YAPPC) | WebSocket infrastructure | WebSocket support | Product | **Correct** |
| `libs:java:yappc-domain` | Shared DTOs/contracts | Domain library | Product | **Correct** |
| `launcher` | Application bootstrap | Entry point | Product | **Correct** |

### Platform Modules (relevant to YAPPC)

| Module | Status | YAPPC Adoption |
|--------|--------|---------------|
| `platform:java:agent-framework` | **Implemented** | Partial — extends but also duplicates |
| `platform:java:agent-registry` | **Implemented** | **Not adopted** — uses in-memory registry |
| `platform:java:agent-dispatch` | **Implemented** | **Underutilized** — custom dispatch in YAPPC |
| `platform:java:agent-memory` | **Implemented** | **Not adopted** |
| `platform:java:agent-learning` | **Implemented** | **Not adopted** |
| `platform:java:agent-resilience` | **Implemented** | **Not adopted** |
| `platform:java:workflow` | **Implemented** | **Not adopted** — uses own WorkflowStep |
| `platform:java:plugin` | **Implemented** | **Not adopted** — uses own SPI |
| `platform:java:ai-integration` | **Implemented** | Partial — deprecated wrapper still present |
| `platform:java:event-cloud` | **Implemented** | **Not adopted** — uses HTTP to AEP |
| `platform:java:governance` | **Partially wired** | **Not adopted** — uses own PolicyEngine |
| `platform:java:config` | **Implemented** | **Not adopted** — uses own ConfigLoader |

---

## 13. Duplication Report

### Critical Duplications

| # | YAPPC Component | Platform Component | Duplication Type | Severity | LOC Impact |
|---|----------------|-------------------|-----------------|----------|------------|
| D1 | `core:agents/YAPPCAgentRegistry` (in-memory) | `agent-registry/DataCloudAgentRegistry` (persistent) | **Full duplicate** — parallel registry implementations | P0 | ~250 LOC |
| D2 | `core:agents/WorkflowStep<I,O>` | `workflow/UnifiedOperator` | **Narrowed duplicate** — WorkflowStep is subset of UnifiedOperator | P1 | ~80 LOC |
| D3 | `core:agents/StepResult<O>` | `workflow/OperatorResult` | **Type mismatch** — single output vs 0-N events | P1 | ~50 LOC |
| D4 | `core:spi/AgentPlugin` hierarchy | `plugin/Plugin` hierarchy | **Parallel SPI** — two incompatible plugin systems | P1 | ~300 LOC |
| D5 | `core:ai/AIIntegrationService` (deprecated) | `ai-integration/AIIntegrationService` | **Deprecated wrapper** — still present in codebase | P1 | ~19 LOC |
| D6 | `core:ai/vector/*` | `ai-integration/embedding/*` + Data Cloud vector search | **Capability overlap** — vector embedding in 3 places | P2 | ~200 LOC |
| D7 | `backend:api/ai/LLMGatewayAIIntegrationService` | `ai-integration/AIIntegrationService` | **Bypass** — API layer directly wrapping LLM | P1 | ~100 LOC |
| D8 | `backend:api/NoOpLLMGateway` | — | **Mock leak** — production code contains test mock | P2 | ~50 LOC |

### Overlap Zones (Not Full Duplicates)

| # | YAPPC Component | Platform Component | Overlap Type | Severity |
|---|----------------|-------------------|-------------|----------|
| O1 | `core:agents/YAPPCAgentBase` (budget tracking) | `agent-framework/BaseAgent` (AgentContext.remainingBudget) | **Override** — YAPPC reimplements platform feature | P1 |
| O2 | `core:agents/AepEventPublisher` (embedded in agent base) | `event-cloud/EventCloud` (append + stream) | **Bypass** — direct HTTP instead of EventCloud | P1 |
| O3 | `services:lifecycle/YappcPolicyEngine` | `governance/PolicyEngine` | **Product-local implementation** of platform SPI | P2 |
| O4 | `backend:api/ConfigLoader` | `config/*` (platform config module) | **Product-local loader** instead of platform | P2 |
| O5 | `core:ai/prompts/canvas/` | `ai-integration/prompts/` | **Capability overlap** — prompt engineering in two places | P3 |
| O6 | `core:lifecycle/storage/` | Data Cloud persistence | **Product-local storage** — unclear if still used | P3 |

### Consolidation Recommendations

| # | Action | Source → Destination | Priority |
|---|--------|---------------------|----------|
| C1 | Replace `YAPPCAgentRegistry` | `core:agents` → wrapper of `platform:java:agent-registry` | P0 |
| C2 | Migrate `WorkflowStep` to `UnifiedOperator` | `core:agents` → implement `UnifiedOperator` interface | P1 |
| C3 | Delete deprecated `AIIntegrationService` | `core:ai/AIIntegrationService.java` → DELETE | P1 |
| C4 | Migrate YAPPC plugin SPI | `core:spi/AgentPlugin` → extend `platform:java:plugin:Plugin` | P1 |
| C5 | Remove direct LangChain4J from API | `backend:api` build.gradle → remove LangChain4J dep | P1 |
| C6 | Delete `NoOpLLMGateway` | `backend:api` → DELETE | P2 |
| C7 | Consolidate vector embedding | `core:ai/vector/` → delegate to `platform:java:ai-integration` + Data Cloud | P2 |
| C8 | Migrate `ConfigLoader` | `backend:api/ConfigLoader` → delegate to `platform:java:config` | P2 |
| C9 | Bridge `YappcPolicyEngine` | `services:lifecycle` → implement `platform:java:governance:PolicyEngine` | P2 |

---

## 14. Dependency Violation Report

### P0 — Critical (Data Loss or Runtime Failure Risk)

| # | Violating Module | Violated Rule | Evidence | Correct Owner | Recommended Fix |
|---|-----------------|---------------|----------|---------------|----------------|
| V1 | `core:agents/YAPPCAgentRegistry` | **Must use persistent registry** | `ConcurrentHashMap` — all agents lost on restart | `platform:java:agent-registry` (Data Cloud backed) | Wrap `DataCloudAgentRegistry` with YAPPC discovery extensions |
| V2 | `core:agents/HttpAepEventPublisher` | **Events must be durable** | Fire-and-forget HTTP POST; failures logged, events lost | `platform:java:event-cloud` (append log) | Append to EventCloud first, then publish to AEP |

### P1 — High (Architectural Integrity)

| # | Violating Module | Violated Rule | Evidence | Correct Owner | Recommended Fix |
|---|-----------------|---------------|----------|---------------|----------------|
| V3 | `backend:api` | **API layer must be thin controller** | 30+ service classes implementing business logic | `services/*` or `core/*` | Move service classes to appropriate service/core module |
| V4 | `backend:api` | **No direct LLM provider dependency** | LangChain4J in build.gradle.kts | `platform:java:ai-integration` | Remove dependency; delegate via `services:ai` |
| V5 | `core:agents/YAPPCAgentBase` | **Composition over inheritance for product extensions** | Embeds AEP publishing, overrides budget tracking | Decorator/delegate pattern | Refactor to compose with `BaseAgent`, delegate cross-cutting concerns |
| V6 | `core:agents/WorkflowStep` | **Must use platform workflow abstraction** | Parallel interface to `UnifiedOperator` | `platform:java:workflow` | Implement adapter from `WorkflowStep` → `UnifiedOperator` |
| V7 | `core:spi/AgentPlugin` | **Must use platform plugin system** | Parallel hierarchy without lifecycle, health, capabilities | `platform:java:plugin` | Extend `Plugin` interface; add lifecycle methods |
| V8 | `core:ai/AIIntegrationService` | **No deprecated code in active codebase** | `@Deprecated` but still compiled and referenced | DELETE | Remove file and fix any remaining references |

### P2 — Medium (Technical Debt)

| # | Violating Module | Violated Rule | Evidence | Correct Owner | Recommended Fix |
|---|-----------------|---------------|----------|---------------|----------------|
| V9 | `core:ai/vector/` | **No capability duplication across platform and product** | Vector embedding logic exists in YAPPC, platform ai-integration, AND Data Cloud | `platform:java:ai-integration` + Data Cloud | Consolidate in platform; YAPPC delegates |
| V10 | `backend:api/NoOpLLMGateway` | **No test mocks in production source sets** | Mock LLM gateway in main source | DELETE or move to test fixtures | Delete from src/main; use proper test fixture |
| V11 | `backend:api/ConfigLoader` | **Use platform config module** | Custom YAML loader with ConcurrentHashMap cache | `platform:java:config` | Delegate to platform config; preserve product-specific config types |
| V12 | `services:lifecycle/YappcPolicyEngine` | **Implement platform governance SPI** | Product-local policy engine not implementing platform interface | `platform:java:governance` | Implement `PolicyEngine` interface; wire OPA rules via platform |
| V13 | `services:lifecycle/AepEventBridge` | **Use circuit breaker for external calls** | HTTP timeout only (500ms-3000ms); no retry, no back-pressure | Add resilience wrapper | Wrap with `platform:java:agent-resilience` patterns |

### P3 — Low (Future Improvement)

| # | Violating Module | Violated Rule | Evidence | Correct Owner |
|---|-----------------|---------------|----------|---------------|
| V14 | `core:lifecycle` | **HTTP endpoints belong in backend** | Contains `YappcHttpServer`, API controllers | `backend:api` |
| V15 | `core:ai/prompts/` | **Prompt engineering should be unified** | Prompt canvas in YAPPC alongside platform prompts | `platform:java:ai-integration` (evaluate promotion) |
| V16 | `services` (umbrella) | **No mega-modules** | 18+ transitive dependencies | Split into focused aggregation |

---

## 15. Target Layered Architecture

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    LAYER 4: YAPPC PRODUCT                              │
│                                                                         │
│  ┌─────────────┐ ┌─────────────┐ ┌──────────┐ ┌──────────────────────┐ │
│  │ backend:api │ │ services:*  │ │ core:*   │ │ config/**            │ │
│  │ (thin ctrl) │ │ (lifecycle  │ │(scaffold │ │ (agent catalog,      │ │
│  │             │ │  ai, domain │ │ refactor │ │  lifecycle stages,   │ │
│  │             │ │  scaffold)  │ │ kg, cli) │ │  pipelines, etc.)    │ │
│  └──────┬──────┘ └──────┬──────┘ └────┬─────┘ └──────────────────────┘ │
│         │               │             │                                 │
│  ┌──────┴───────────────┴─────────────┴──────┐                         │
│  │ infrastructure:datacloud (adapters)        │                         │
│  │ libs:java:yappc-domain (shared DTOs)       │                         │
│  └──────────────────┬────────────────────────┘                         │
└─────────────────────┼──────────────────────────────────────────────────┘
                      │ ALLOWED: yappc → data-cloud, yappc → aep
┌─────────────────────┼──────────────────────────────────────────────────┐
│                     │  LAYER 3: DATA PLATFORM                         │
│  ┌──────────────────┴───────────────────────────────────────────────┐  │
│  │ products:data-cloud:platform                                     │  │
│  │   EntityRepository, KnowledgeGraphPlugin, MemoryTier,            │  │
│  │   GraphOperations, EventLogStore (SPI)                           │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ products:data-cloud:spi (minimal contracts)                      │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
                      │
┌─────────────────────┼──────────────────────────────────────────────────┐
│                     │  LAYER 2: INTELLIGENCE & EXECUTION              │
│  ┌──────────────────┴───────────────────────────────────────────────┐  │
│  │ products:aep:platform                                            │  │
│  │   Orchestrator, Pipeline lifecycle, Agent dispatch routing       │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
                      │
┌─────────────────────┼──────────────────────────────────────────────────┐
│                     │  LAYER 1: SHARED PLATFORM PRIMITIVES            │
│                     │                                                  │
│  ┌──────────┐ ┌─────┴─────┐ ┌───────────┐ ┌────────────┐            │
│  │agent-    │ │ workflow   │ │ ai-       │ │ event-     │            │
│  │framework │ │ (Operator, │ │integration│ │ cloud      │            │
│  │agent-    │ │  Pipeline, │ │ (LLM,     │ │ (append,   │            │
│  │registry  │ │  DAG exec, │ │  prompts, │ │  tail,     │            │
│  │agent-    │ │  durable)  │ │  embed)   │ │  stream)   │            │
│  │dispatch  │ └───────────┘ └───────────┘ └────────────┘            │
│  │agent-    │                                                        │
│  │memory    │ ┌───────────┐ ┌───────────┐ ┌────────────┐            │
│  │agent-    │ │ plugin    │ │ security  │ │ governance │            │
│  │learning  │ │ (SPI,     │ │ (RBAC,    │ │ (policy    │            │
│  │agent-    │ │  registry,│ │  ABAC,    │ │  engine)   │            │
│  │resilience│ │  hot-rel) │ │  JWT)     │ └────────────┘            │
│  └──────────┘ └───────────┘ └───────────┘                            │
│                                                                        │
│  ┌──────────┐ ┌───────────┐ ┌───────────┐ ┌────────────┐            │
│  │ core     │ │ domain    │ │ database  │ │ http       │            │
│  │ config   │ │ observ*   │ │ audit     │ │ runtime    │            │
│  │ testing  │ │ connectors│ │ ingestion │ │            │            │
│  └──────────┘ └───────────┘ └───────────┘ └────────────┘            │
└────────────────────────────────────────────────────────────────────────┘
```

### Allowed Dependencies

```
Layer 4 (YAPPC) → Layer 3 (Data Cloud)   ✅ via infrastructure:datacloud adapters
Layer 4 (YAPPC) → Layer 2 (AEP)          ✅ via AepEventBridge/Publisher
Layer 4 (YAPPC) → Layer 1 (Platform)     ✅ direct dependency
Layer 3 (Data Cloud) → Layer 1 (Platform) ✅ direct dependency
Layer 2 (AEP) → Layer 3 (Data Cloud SPI) ✅ types only
Layer 2 (AEP) → Layer 1 (Platform)       ✅ direct dependency
Layer 1 (Platform) → Layer 1 (Platform)  ✅ internal dependencies
```

### Forbidden Dependencies

```
Layer 1 (Platform) → Layer 2/3/4         ❌ platform must not depend on products
Layer 2 (AEP) → Layer 4 (YAPPC)          ❌ AEP must not depend on YAPPC
Layer 3 (Data Cloud) → Layer 2/4         ❌ Data Cloud must not depend on AEP/YAPPC
Layer 4 (YAPPC) → Layer 4 (other prods)  ❌ cross-product isolation (except via approved list)
```

### Temporary Migration Bridges (Allowed Until Removed)

| Bridge | From → To | Expiry Condition |
|--------|-----------|-----------------|
| `YAPPCAgentRegistry` (in-memory) | `core:agents` → `platform:java:agent-registry` | Until P0 migration complete |
| `YAPPCAgentBase` (mixed concerns) | `core:agents` → composable `BaseAgent` | Until P1 refactor complete |
| `HttpAepEventPublisher` (fire-and-forget) | `core:agents` → `platform:java:event-cloud` | Until P0 durability fix complete |
| `core:ai/AIIntegrationService` (deprecated) | `core:ai` → DELETE | Until P1 cleanup complete |
| `core:spi/AgentPlugin` | `core:spi` → `platform:java:plugin` | Until P1 migration complete |

---

## 16. Dependency Governance Rules

### Module Dependency Rules

| Rule ID | Rule | Enforcement |
|---------|------|-------------|
| **MDR-01** | `products/*` modules MUST NOT import `com.ghatana.platform.*` internal packages (only public SPI) | ArchUnit |
| **MDR-02** | `platform/java/*` modules MUST NOT depend on any `products/*` module | Gradle `product-isolation.gradle` + ArchUnit |
| **MDR-03** | Cross-product dependencies MUST be pre-approved in `product-isolation.gradle` | Gradle (build failure) |
| **MDR-04** | YAPPC `backend:api` MUST NOT contain business logic service classes | ArchUnit + code review |
| **MDR-05** | YAPPC product modules MUST NOT have direct LLM provider (LangChain4J) dependencies | Gradle dependency constraint |
| **MDR-06** | All agent implementations MUST use `platform:java:agent-framework:BaseAgent` as base | ArchUnit |
| **MDR-07** | All workflow operators MUST implement `platform:java:workflow:UnifiedOperator` | ArchUnit |
| **MDR-08** | All plugin implementations MUST extend `platform:java:plugin:Plugin` | ArchUnit |
| **MDR-09** | All persistent state MUST go through Data Cloud or platform:database | ArchUnit (no `ConcurrentHashMap` for persistent data) |
| **MDR-10** | All events MUST be appended to EventCloud before external publishing | Code review + ArchUnit |

### Package Import Rules

| Rule ID | Rule |
|---------|------|
| **PIR-01** | `com.ghatana.yappc.*` MUST NOT import `org.langchain4j.*` directly (delegate via `platform:java:ai-integration`) |
| **PIR-02** | `com.ghatana.yappc.api.*` MUST NOT import `com.ghatana.yappc.ai.*` (delegate via `services:ai`) |
| **PIR-03** | `com.ghatana.platform.*` MUST NOT import `com.ghatana.yappc.*` or `com.ghatana.aep.*` or `com.ghatana.datacloud.*` |
| **PIR-04** | `com.ghatana.aep.*` MUST NOT import `com.ghatana.yappc.*` |

### Config Ownership Rules

| Rule ID | Rule |
|---------|------|
| **COR-01** | Agent definitions (`config/agents/definitions/`) owned by YAPPC product team |
| **COR-02** | Event schemas (`config/agents/event-schemas/`) MUST be co-owned by YAPPC + AEP teams |
| **COR-03** | Pipeline definitions (`config/pipelines/`) loaded by YAPPC but executed by AEP — dual ownership |
| **COR-04** | All config files MUST have a corresponding JSON schema in `config/schemas/` or `config/validation/` |
| **COR-05** | Config loaders MUST use `platform:java:config` module for YAML parsing |

### Event Ownership Rules

| Rule ID | Rule |
|---------|------|
| **EOR-01** | YAPPC lifecycle events (`lifecycle.phase.*`) owned by YAPPC |
| **EOR-02** | Agent execution events (`agent.dispatch.*`, `agent.result.*`) owned by YAPPC |
| **EOR-03** | Platform events (observability, audit) owned by platform layer |
| **EOR-04** | All events MUST have a schema in `config/agents/event-schemas/` (or similar shared location) |
| **EOR-05** | Event routing configuration consumed by AEP but defined by event producer (YAPPC) |

### Data Ownership Rules

| Rule ID | Rule |
|---------|------|
| **DOR-01** | All persistent entity data MUST be stored via Data Cloud `EntityRepository` |
| **DOR-02** | All knowledge graph data MUST use Data Cloud `KnowledgeGraphPlugin` |
| **DOR-03** | Agent registry data MUST use `DataCloudAgentRegistry` |
| **DOR-04** | Agent memory (episodic, semantic, procedural) MUST use `platform:java:agent-memory` → Data Cloud |
| **DOR-05** | Execution history MUST be appended to `EventCloud` |
| **DOR-06** | In-memory `ConcurrentHashMap` MUST NOT be used for data that outlives a single request |

---

## 17. Enforcement Mechanisms

### Tier 1 — Build-Time (Immediate, Automated)

| Mechanism | Status | Priority | Action |
|-----------|--------|----------|--------|
| `product-isolation.gradle` | **Implemented** | — | Maintain — already throws `GradleException` on violations |
| `duplicate-check.gradle` | **Implemented** | — | Maintain — detects duplicate class names |
| `doc-tag-check.gradle` | **Implemented** (warnings only) | P2 | **Upgrade to fail build** on missing `@doc` tags |
| `validateAgentCatalog` task | **Implemented** | — | Maintain |
| `validateEventSchemas` task | **Implemented** | — | Maintain |
| `validatePipelines` task | **Implemented** | — | Maintain |
| **NEW**: `validateLifecycleConfig` task | **Missing** | P1 | Add validation for `stages.yaml` and `transitions.yaml` |
| **NEW**: Gradle forbidden dependency constraint | **Missing** | P1 | Add `configurations.all { exclude LangChain4J from YAPPC }` |
| **NEW**: Config schema validation for all YAML | **Missing** | P2 | Add JSON Schema validation for all config families |

### Tier 2 — Compile-Time (ArchUnit Tests)

| Test | Status | Priority | Action |
|------|--------|----------|--------|
| Platform-product isolation | **Implemented** (in `platform:java:core`) | — | Maintain |
| Cross-product isolation | **Implemented** | — | Maintain |
| CompletableFuture ban | **Implemented** | — | Maintain |
| EventloopTestBase enforcement | **Implemented** | — | Maintain |
| **NEW**: YAPPC agent must extend BaseAgent | **Missing** | P1 | Add ArchUnit test in YAPPC |
| **NEW**: YAPPC operators must implement UnifiedOperator | **Missing** | P1 | Add ArchUnit test in YAPPC |
| **NEW**: YAPPC plugins must extend Plugin | **Missing** | P1 | Add ArchUnit test in YAPPC |
| **NEW**: No business logic in backend:api | **Missing** | P1 | Add ArchUnit test banning service classes in `api.*` |
| **NEW**: No direct LLM imports in product packages | **Missing** | P1 | Add ArchUnit test banning `org.langchain4j` in `com.ghatana.yappc` |
| **NEW**: No ConcurrentHashMap for registries | **Missing** | P2 | Add ArchUnit test scanning for in-memory registries |

### Tier 3 — Code Review (Policy Enforcement)

| Mechanism | Status | Priority | Action |
|-----------|--------|----------|--------|
| `.github/copilot-instructions.md` | **Implemented** | — | Maintain — already comprehensive |
| CODEOWNERS file | **Missing** | P1 | Add `.github/CODEOWNERS` mapping module paths to team owners |
| Architecture Decision Records | **Implemented** (`docs/adr/`) | — | Maintain |
| **NEW**: Ownership manifest per module | **Missing** | P2 | Add `OWNERS.yaml` to each top-level module with team, layer, dependencies |

### Recommended Enforcement Priority

1. **Week 1**: Add ArchUnit tests for MDR-06, MDR-07, MDR-08 (agent/operator/plugin base classes)
2. **Week 2**: Add CODEOWNERS file; add Gradle forbidden dependency constraint for LangChain4J
3. **Week 3**: Add `validateLifecycleConfig` Gradle task
4. **Week 4**: Upgrade doc-tag check to build-failure; add config schema validation

---

## 18. Refactor Execution Plan

### Phase 1 — P0 Critical Fixes (Data Loss Prevention)

#### RF-01: Migrate Agent Registry to DataCloud Backing

**Source**: `products/yappc/core/agents/src/main/java/com/ghatana/yappc/agent/YAPPCAgentRegistry.java`  
**Destination**: Wrapper around `platform:java:agent-registry:DataCloudAgentRegistry`  
**Reason**: In-memory ConcurrentHashMap loses all agents on restart  
**Steps**:
1. Create `YappcAgentRegistryAdapter` that delegates to `DataCloudAgentRegistry`
2. Add YAPPC-specific discovery methods (by phase, by step name) as additional query methods
3. Migrate `YAPPCAgentRegistry` callers to use adapter
4. Add integration test verifying persistence across simulated restart
5. Delete `YAPPCAgentRegistry` ConcurrentHashMap implementation
6. Add ArchUnit test: no `ConcurrentHashMap` in registry classes

**Dependency impact**: `core:agents`, `services:lifecycle`, `backend:api`  
**Rollback risk**: LOW — adapter pattern allows fallback  
**Bridge required**: YES — `YappcAgentRegistryAdapter` wraps platform registry with product queries

#### RF-02: Add Event Durability to AEP Publisher

**Source**: `products/yappc/core/agents/src/main/java/com/ghatana/yappc/agent/HttpAepEventPublisher.java`  
**Destination**: Durable publisher with EventCloud append-first  
**Reason**: Fire-and-forget HTTP loses events under AEP downtime  
**Steps**:
1. Add `platform:java:event-cloud` dependency to `core:agents`
2. Create `DurableAepEventPublisher` that: (a) appends to EventCloud, (b) publishes to AEP HTTP
3. Add retry logic with exponential backoff for HTTP failures
4. Add dead-letter topic for permanently failed events
5. Swap `HttpAepEventPublisher` usages to `DurableAepEventPublisher`
6. Add integration test with AEP unavailable scenario

**Dependency impact**: `core:agents`, `services:lifecycle`  
**Rollback risk**: LOW — EventCloud append is additive  
**Bridge required**: NO — enhancement, not replacement

### Phase 2 — P1 Architectural Alignment

#### RF-03: Refactor YAPPCAgentBase to Composition Pattern

**Source**: `products/yappc/core/agents/src/main/java/com/ghatana/yappc/agent/YAPPCAgentBase.java`  
**Destination**: Composable agent with delegation  
**Reason**: Mixes platform and product concerns via inheritance  
**Steps**:
1. Extract `AepEventPublisher` integration into `AepPublishingAgentDecorator`
2. Extract budget tracking into `BudgetTrackingAgentDecorator`
3. Extract trace context propagation into `TracingAgentDecorator`
4. Refactor `YAPPCAgentBase` to delegate to decorators instead of overriding BaseAgent methods
5. Keep `WorkflowStep` implementation as TEMPORARY bridge (until RF-04)
6. Update all 50+ agent implementations to use decorated pattern

**Dependency impact**: ALL agent implementations  
**Rollback risk**: MEDIUM — large surface area  
**Bridge required**: YES — maintain `WorkflowStep` interface temporarily

#### RF-04: Migrate WorkflowStep to UnifiedOperator

**Source**: `products/yappc/core/agents/src/main/java/com/ghatana/yappc/agent/WorkflowStep.java`  
**Destination**: Implement `platform:java:workflow:UnifiedOperator`  
**Reason**: WorkflowStep is a narrow subset of UnifiedOperator  
**Steps**:
1. Create `YappcOperatorAdapter` implementing `UnifiedOperator` that delegates to existing `WorkflowStep`
2. Add operator type classification (STREAM/PATTERN/LEARNING) to YAPPC agents
3. Add operator versioning support
4. Migrate lifecycle operators (`PhaseTransitionValidatorOperator`, etc.) to `UnifiedOperator`
5. Adopt `PipelineBuilder` for lifecycle pipeline construction (replace manual wiring in `LifecycleServiceModule`)
6. Deprecate `WorkflowStep` interface
7. Add ArchUnit test enforcing `UnifiedOperator` adoption

**Dependency impact**: `core:agents`, `services:lifecycle`, lifecycle operators  
**Rollback risk**: MEDIUM — operator interface change  
**Bridge required**: YES — `YappcOperatorAdapter` during migration

#### RF-05: Migrate Plugin SPI to Platform

**Source**: `products/yappc/core/spi/src/main/java/com/ghatana/yappc/plugin/`  
**Destination**: Extend `platform:java:plugin:Plugin`  
**Reason**: Parallel plugin hierarchy without lifecycle, health, capabilities  
**Steps**:
1. Make `YappcPlugin` extend `platform:java:plugin:Plugin`
2. Add lifecycle methods (`initialize`, `start`, `stop`, `shutdown`) to `AgentPlugin`, `GeneratorPlugin`, `ValidatorPlugin`
3. Add `healthCheck()` to all YAPPC plugins
4. Replace string-based capabilities with `PluginCapability` instances
5. Register YAPPC plugins in platform `PluginRegistry`
6. Add structured metadata (author, version, tags)
7. Enable hot-reload capability for YAPPC plugins

**Dependency impact**: `core:spi`, `core:framework`, all plugin implementations  
**Rollback risk**: MEDIUM — SPI change affects all plugins  
**Bridge required**: YES — `core:spi/bridge/` and `core:spi/migration/` packages already exist

#### RF-06: Remove Deprecated AI Integration Wrapper

**Source**: `products/yappc/core/ai/src/main/java/com/ghatana/yappc/ai/AIIntegrationService.java`  
**Destination**: DELETE  
**Steps**:
1. Search for all references to `com.ghatana.yappc.ai.AIIntegrationService`
2. Replace with `com.ghatana.ai.AIIntegrationService` (platform canonical)
3. Delete deprecated file
4. Remove LangChain4J from `backend:api/build.gradle.kts`
5. Delete `NoOpLLMGateway.java` from `backend:api`
6. Ensure `services:ai` routes through `platform:java:ai-integration`

**Dependency impact**: `core:ai`, `backend:api`, `services:ai`  
**Rollback risk**: LOW  
**Bridge required**: NO

#### RF-07: Delegate Backend API Service Classes

**Source**: `products/yappc/backend/api/src/main/java/com/ghatana/yappc/api/service/` (30+ classes)  
**Destination**: `services/*` or `core/*` as appropriate  
**Steps**:
1. Categorize each service class by domain:
   - AI services → `services:ai`
   - Lifecycle services → `services:lifecycle`
   - Domain services → `services:domain`
   - Infrastructure services → `services:infrastructure`
   - Config services → platform config delegation
2. Move classes in batches (5-8 per sprint)
3. Replace with thin delegating controllers in `backend:api`
4. Add ArchUnit test: no `*Service` classes in `com.ghatana.yappc.api.service` package

**Dependency impact**: `backend:api`, all `services:*` modules  
**Rollback risk**: LOW — mechanical move  
**Bridge required**: NO

### Phase 3 — P2 Consolidation

#### RF-08: Consolidate Vector Embedding

**Source**: `products/yappc/core/ai/src/main/java/com/ghatana/yappc/ai/vector/`  
**Destination**: Delegate to `platform:java:ai-integration/embedding/` + Data Cloud vector search  
**Steps**:
1. Replace YAPPC vector embedding with platform AI integration embedding service
2. Use Data Cloud vector search for similarity queries
3. Delete `core:ai/vector/` package
4. Update `core:agents` and `core:knowledge-graph` consumers

#### RF-09: Migrate ConfigLoader to Platform Config

**Source**: `products/yappc/backend/api/src/main/java/com/ghatana/yappc/api/service/ConfigLoader.java`  
**Destination**: Delegate to `platform:java:config`  
**Steps**:
1. Define YAPPC-specific config types as records
2. Implement loading via platform config module
3. Preserve ConcurrentHashMap cache (or use platform caching)
4. Keep product-specific config shape (domains, personas, stages)

#### RF-10: Wire Agent Memory and Learning

**Source**: Currently missing  
**Destination**: Wire `platform:java:agent-memory` + `platform:java:agent-learning`  
**Steps**:
1. Add `agent-memory` dependency to `core:agents`
2. Inject `MemoryPlane` into `YAPPCAgentBase` (or its decorator)
3. After each agent execution, append episodic memory entry
4. Configure `ConsolidationPipeline` with YAPPC-specific stages
5. Wire `config/memory/redaction-rules.yaml` to memory governance

#### RF-11: Bridge Governance/Policy Engine

**Source**: `products/yappc/services/lifecycle/src/main/java/com/ghatana/yappc/services/lifecycle/YappcPolicyEngine.java`  
**Destination**: Implement `platform:java:governance:PolicyEngine`  
**Steps**:
1. Make `YappcPolicyEngine` implement `PolicyEngine` interface
2. Bridge OPA Rego evaluation to platform governance SPI
3. Wire lifecycle policies through platform governance

---

## 19. Long-Term Platform Architecture

### Design Principles

1. **Platform as Foundation**: `platform/java/*` provides product-agnostic primitives. Products consume, never modify.
2. **AEP as Execution Coordinator**: AEP orchestrates multi-agent pipelines, tool dispatch, and event routing across products.
3. **Data Cloud as Persistent Brain**: All persistent state, knowledge, memory, events, and analytics flow through Data Cloud.
4. **Products as Domain Experts**: Products like YAPPC provide domain-specific agents, workflows, tools, and config — built on shared infrastructure.
5. **Extension over Inheritance**: Products extend platform via composition, plugins, SPIs — never by subclassing internals.
6. **Event Sourcing as Audit Trail**: All significant actions recorded in EventCloud. No fire-and-forget.

### New Product Onboarding Pattern

A new product joining the ecosystem should:

1. **Depend on** `platform:java:{core, domain, http, database, observability, security}`
2. **Register agents** via `platform:java:agent-registry` (DataCloud-backed)
3. **Define operators** implementing `platform:java:workflow:UnifiedOperator`
4. **Register plugins** via `platform:java:plugin:PluginRegistry`
5. **Persist data** via Data Cloud `EntityRepository`
6. **Publish events** to `platform:java:event-cloud` (EventCloud append-first)
7. **Integrate AI** via `platform:java:ai-integration` (never direct LLM provider deps)
8. **Store agent memory** via `platform:java:agent-memory`
9. **Define config** in product-local `config/` with JSON schemas
10. **Request cross-product dependency** via `product-isolation.gradle` allowlist

### Architecture Invariants

- No product module may implement generic agent runtime, generic workflow engine, generic plugin system, or generic data platform
- Platform modules have zero product dependencies — enforced by ArchUnit + Gradle
- All cross-product dependencies must be pre-approved and reviewed
- All persistent data has exactly one owner (Data Cloud or product-local with clear justification)
- All events have exactly one producer and clear schema ownership
- All agents are discoverable via `DataCloudAgentRegistry`
- All operators are composable via `PipelineBuilder`

---

## 20. Platform Evolution Roadmap

### Phase 1 — Build and Ownership Baseline Alignment (Weeks 1-4)

**Goal**: Fix module graph, visibility, and governance baseline

| Week | Actions |
|------|---------|
| 1 | Add CODEOWNERS file; add ArchUnit tests for MDR-06, MDR-07, MDR-08 |
| 2 | Execute RF-01 (agent registry migration to DataCloud backing) |
| 3 | Execute RF-02 (event durability); add Gradle LangChain4J constraint |
| 4 | Add `validateLifecycleConfig` task; upgrade doc-tag check to fail build |

**Exit Criteria**:
- Agent registry persistent across restarts
- Events durable (EventCloud append-first)
- ArchUnit tests block non-compliant code
- All config families have validation

### Phase 2 — Dependency Cleanup and Boundary Enforcement (Weeks 5-10)

**Goal**: Remove direct violations and add enforcement

| Week | Actions |
|------|---------|
| 5-6 | Execute RF-06 (remove deprecated AI wrapper + direct LangChain4J from API) |
| 7-8 | Execute RF-03 (YAPPCAgentBase composition refactor) |
| 9-10 | Execute RF-07 (move 30+ service classes from backend:api to services/core) — batch 1 |

**Exit Criteria**:
- No deprecated AI wrappers in codebase
- No direct LLM provider dependencies in product modules
- Agent base class uses composition pattern
- API layer reduced to thin controllers (>50% of services moved)

### Phase 3 — Platform Consolidation (Weeks 11-18)

**Goal**: Merge duplicate runtime, AI, data, event, tool, and plugin concerns

| Week | Actions |
|------|---------|
| 11-12 | Execute RF-04 (WorkflowStep → UnifiedOperator migration) |
| 13-14 | Execute RF-05 (plugin SPI migration to platform) |
| 15-16 | Execute RF-08 (vector embedding consolidation) + RF-09 (ConfigLoader migration) |
| 17-18 | Execute RF-07 (service class migration — batch 2, complete) |

**Exit Criteria**:
- All operators implement UnifiedOperator
- All plugins extend platform Plugin
- Vector embedding consolidated
- Config loading delegated to platform
- Backend:api contains only thin controllers

### Phase 4 — Product Isolation and Extension Model Hardening (Weeks 19-24)

**Goal**: YAPPC retains only product-specific behavior

| Week | Actions |
|------|---------|
| 19-20 | Execute RF-10 (wire agent memory and learning) |
| 21-22 | Execute RF-11 (governance/policy engine bridge) |
| 23-24 | Final duplication sweep; add OWNERS.yaml manifests; config schema completion |

**Exit Criteria**:
- Agent memory persisted through Data Cloud
- Learning pipeline configured for YAPPC
- Governance SPI implemented
- Every module has OWNERS.yaml
- Every config family has JSON schema

### Phase 5 — Ecosystem Expansion (Weeks 25+)

**Goal**: Prove the platform supports new products without drift

| Action | Validation |
|--------|-----------|
| New product onboards using documented pattern | Follows 10-step onboarding without platform modification |
| Platform ArchUnit tests pass with new product added | No governance regressions |
| Cross-product dependency review | Only pre-approved dependencies in `product-isolation.gradle` |
| Platform libraries remain product-agnostic | No product imports in platform packages |

**Exit Criteria**:
- New product successfully onboards with zero platform-layer changes
- All governance checks pass continuously in CI
- Architecture drift detection automated and non-bypassable

---

## Appendix A: Module Status Legend

| Status | Meaning |
|--------|---------|
| **Implemented** | Code exists, compiles, has tests, is operational |
| **Partially wired** | Code exists but not fully integrated into runtime paths |
| **Declared but not operational** | Module/interface exists but has no functional implementation |
| **Missing** | Expected capability does not exist in the codebase |

## Appendix B: Ownership Status Legend

| Status | Meaning |
|--------|---------|
| **Correct** | Module is in the right layer with correct dependencies |
| **Misplaced** | Module belongs in a different layer/location |
| **Overlapping** | Module duplicates capabilities from another layer |
| **Temporary bridge** | Known duplicate maintained temporarily during migration |
| **Consolidate** | Module should be merged into canonical implementation |
| **Deprecate** | Module should be removed after migration |

---

*End of Report*

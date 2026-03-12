# YAPPC + AEP Full Integration Plan

## Unified Agentic Framework, Data-Cloud & Pluggable Event Transport

> **Version**: 1.1.0 | **Status**: Approved for Implementation  
> **Scope**: AEP operator hardening first → YAPPC integration on top  
> **Sequence**: Phased (each phase gates the next); parallel steps noted explicitly

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Guiding Principles](#2-guiding-principles)
3. [Architecture Overview](#3-architecture-overview)
4. [Binding Decisions](#4-binding-decisions)
5. [Inventory of Changes](#5-inventory-of-changes)
6. [Phase 1 — AEP Operator Framework Hardening](#phase-1--aep-operator-framework-hardening)
7. [Phase 2 — Schema Registry](#phase-2--schema-registry)
8. [Phase 3 — Parameterized YAML Templates](#phase-3--parameterized-yaml-templates)
9. [Phase 4 — AEP Operator Catalog Loading](#phase-4--aep-operator-catalog-loading)
10. [Phase 5 — AEP ↔ YAPPC Event Routing Bridge](#phase-5--aep--yappc-event-routing-bridge)
11. [Phase 6 — YAPPC Pipeline Operators & Registration](#phase-6--yappc-pipeline-operators--registration)
12. [Phase 7 — YAPPC Lifecycle Service Implementation](#phase-7--yappc-lifecycle-service-implementation)
13. [Phase 8 — YAPPC Agent Catalog, LLM Gateway & Lazy Registry](#phase-8--yappc-agent-catalog-llm-gateway--lazy-registry)
14. [Phase 9 — Durable Memory & Event Sourcing](#phase-9--durable-memory--event-sourcing)
15. [Phase 10 — GAA Lifecycle & AgentTurnPipeline Hardening](#phase-10--gaa-lifecycle--agentturnpipeline-hardening)
16. [Phase 11 — Canonical Workflow Integration](#phase-11--canonical-workflow-integration)
17. [Phase 12 — Testing, Observability & DLQ Hardening](#phase-12--testing-observability--dlq-hardening)
18. [Phase Dependency Graph](#phase-dependency-graph)
19. [End-to-End Verification Checklist](#end-to-end-verification-checklist)
20. [Further Considerations](#further-considerations)

---

## 1. Executive Summary

YAPPC (AI-assisted SDLC platform) and AEP (Agentic Event Processor) currently operate as two disconnected systems. YAPPC has 590 agents across 17 domain catalogs, 10 canonical workflows, and 2 pipeline definitions — none of which are wired to AEP's operator runtime or the GAA (Generic Adaptive Agent) framework. AEP has hardcoded connector config, in-memory state, a fragile HTTP event publisher, and inline schema strings that have already drifted from proto contracts.

This plan delivers a **production-grade, multi-tenant agentic platform** by:

1. **Hardening AEP's foundation** — production persistence, pluggable event transport (gRPC by default), env-driven config, event-sourced orchestrator state.
2. **Establishing a canonical Schema Registry** — driven by proto-generated `bundle.schema.json`; no inline schema strings anywhere.
3. **Introducing a Parameterized YAML Template Engine** — all pipeline, workflow, and agent instance YAMLs support `{{ param }}` substitution; common definitions are reused across instantiations.
4. **Wiring YAPPC onto AEP** — 590 agents registered, 10 workflows executable, 2 pipelines deployed, lifecycle service fully implemented.
5. **Connecting memory and learning** — durable PostgreSQL-backed `PersistentMemoryPlane`; REFLECT→AEP pattern detection→PERCEIVE closed loop.
6. **Zero stubs, zero hardcoded models, zero hardcoded transport** — every external dependency is injected, connector-driven, and fail-fast on missing config.

The result is a world-class agentic SDLC platform where a developer submits a task, YAPPC routes it through 590 registered agents, AEP orchestrates the pipeline, Data-Cloud durably records every episode, and the learning loop continuously refines agent procedures.

---

## 2. Guiding Principles

These principles are **non-negotiable** and apply to every line of code in this plan:

| #   | Principle                             | Enforcement                                                                                                                                                                       |
| --- | ------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | **No stubs**                          | Delete stub branches; throw `IllegalStateException` at startup if required config is absent                                                                                       |
| 2   | **No hardcoded external model names** | All model names come from agent YAML `model` field; Java never contains string literals like `claude-3.5-sonnet`                                                                  |
| 3   | **Pluggable transport connector**     | `EventCloud` is a connector-backed facade; transport (gRPC, HTTP, or others) is declared via `EVENT_CLOUD_TRANSPORT` env var; connectors discovered via `EventCloudConnector` SPI |
| 4   | **EventCloud owns transport**         | YAPPC and AEP never manage connection details, retry, or backpressure — the active `EventCloudConnector` handles them; gRPC is the production default                             |
| 5   | **Schema Registry as truth**          | Every event validated against `SchemaRegistry`; schemas seeded from proto-generated `bundle.schema.json`                                                                          |
| 6   | **Event sourcing for state**          | All mutable orchestrator/lifecycle/workflow state written as events to Data-Cloud EventLogStore                                                                                   |
| 7   | **Multi-tenancy at every layer**      | `tenantId` propagated through all API, service, operator, and EventLogStore calls                                                                                                 |
| 8   | **Fail fast**                         | Missing env var → `IllegalStateException` at module startup, not a silent default or stub                                                                                         |
| 9   | **SPI over mapping tables**           | Operators self-register via `OperatorProvider` SPI; connectors self-register via `EventCloudConnector` SPI; no central enum or mapping class                                      |
| 10  | **ActiveJ only**                      | No `CompletableFuture`, no Spring Reactor; all async via `Promise`; all tests extend `EventloopTestBase`                                                                          |
| 11  | **Parameterized YAML templates**      | All pipeline, workflow, and agent YAML files support `{{ param }}` substitution; no copy-paste variant files; common definitions expressed once                                   |

---

## 3. Architecture Overview

```
┌────────────────────────────────────────────────────────────────────────────────┐
│                              YAPPC Services (ports 8080–8083)                  │
│                                                                                │
│  ┌─ApiApplication─┐  ┌─YappcAiService─┐  ┌─YappcLifecycleService─┐  ┌─Scaffold─┐│
│  │ REST / Auth     │  │ LLM Gateway    │  │ Phase State Mgmt      │  │ Codegen  ││
│  │ JWT extraction  │  │ 590 Agents     │  │ Workflow Execution     │  │          ││
│  │ tenantId inject │  │ AgentRegistry  │  │ EventCloud → Phase    │  │          ││
│  └────────┬────────┘  └───────┬────────┘  └──────────┬────────────┘  └──────────┘│
│           │                   │                       │                           │
│           └───────────────────┴───────────────────────┘                           │
│                                         │ EventCloud.append(tenantId, ...)        │
└─────────────────────────────────────────┼──────────────────────────────────────── ┘
                                          ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│              EventCloud Facade (platform/java/event-cloud)                      │
│         Delegates to active EventCloudConnector (SPI, selected per config)      │
│                                                                                 │
│  ┌───────────────────────┐  ┌────────────────────────┐  ┌─────────────────────┐ │
│  │  GrpcEventCloudConn.  │  │  HttpEventCloudConn.   │  │  (future connectors)│ │
│  │  event_cloud.proto    │  │  REST + SSE / webhook  │  │  Kafka, AMQP, etc.  │ │
│  │  streaming + mTLS     │  │  configurable endpoint │  │                     │ │
│  └───────────┬───────────┘  └────────────┬───────────┘  └──────────┬──────────┘ │
│   selected via EVENT_CLOUD_TRANSPORT env var (default: grpc)        │            │
└──────────────────────────────────────────┬──────────────────────────────────────┘
                                           │
                    ┌──────────────────────▼───────────────────────┐
                    │              AEP Engine (products/aep)        │
                    │                                               │
                    │  OperatorCatalog (SPI-loaded via YamlLoader)  │
                    │  ┌─────────────────────────────────────────┐  │
                    │  │  CatalogAgentDispatcher (event-routing) │  │
                    │  │  PhaseTransitionValidatorOperator        │  │
                    │  │  PhaseStateManagerOperator               │  │
                    │  │  GateOrchestratorOperator                │  │
                    │  │  LifecycleStatePublisherOperator          │  │
                    │  │  AgentDispatchValidatorOperator           │  │
                    │  │  AgentExecutorOperator                   │  │
                    │  │  ResultAggregatorOperator                │  │
                    │  │  MetricsCollectorOperator                │  │
                    │  │  PatternDetectionAgent                   │  │
                    │  └─────────────────────────────────────────┘  │
                    │  Orchestrator → PipelineMaterializer           │
                    │  AIAgentOrchestrationManagerImpl (event-srcd)  │
                    │  PostgresCheckpointStorage                     │
                    └──────────────────────────┬────────────────────┘
                                               │
                    ┌──────────────────────────▼────────────────────┐
                    │        Data-Cloud (products/data-cloud)        │
                    │                                                │
                    │  EventLogStore SPI (PostgreSQL impl)           │
                    │  SchemaRegistry (DataCloudSchemaRegistry)      │
                    │  PersistentMemoryPlane (7-tier MemoryPlane)    │
                    │  WorkflowRunRepository                         │
                    └────────────────────────────────────────────────┘
```

### Component Inventory

| Component                         | Technology                           | Responsibility                                                |
| --------------------------------- | ------------------------------------ | ------------------------------------------------------------- |
| `EventCloudConnector`             | Java SPI interface                   | Pluggable transport contract — append + subscribe             |
| `GrpcEventCloudConnector`         | gRPC stubs from `event_cloud.proto`  | gRPC transport (default); streaming + mTLS                    |
| `HttpEventCloudConnector`         | ActiveJ HTTP client                  | HTTP/SSE transport for simpler deployments                    |
| `EventCloudConnectorRegistry`     | `ServiceLoader<EventCloudConnector>` | Discovers connectors; selects active from env var             |
| `YamlTemplateEngine`              | SnakeYAML + Mustache-style           | Resolves `{{ param }}` in all YAML before parsing             |
| `TemplateContext`                 | Immutable value object               | Carries resolved params (env vars + values.yaml)              |
| `DataCloudSchemaRegistry`         | Data-Cloud EventLogStore             | Schema CRUD, validation, compatibility check                  |
| `YamlOperatorLoader`              | ActiveJ / SnakeYAML                  | YAML → `OperatorCatalog` via SPI; fail-fast                   |
| `CatalogAgentDispatcher`          | ActiveJ `UnifiedOperator`            | Routes event topics to agent IDs (60+ rules)                  |
| `PipelineMaterializer`            | ActiveJ                              | YAML DAG → live operator graph; hard-fail on missing          |
| `PostgresCheckpointStorage`       | JDBC / `libs:database`               | Durable pipeline checkpoint recovery                          |
| `AIAgentOrchestrationManagerImpl` | EventLogStore-backed                 | Event-sourced agent registration & status                     |
| `PersistentMemoryPlane`           | Data-Cloud PostgreSQL                | 7-tier durable agent memory (episode, fact, etc.)             |
| `DefaultLLMGateway`               | `libs:ai-integration`                | Multi-provider routing from YAML `model` field                |
| `AgentRegistryService`            | Platform                             | All 590 YAPPC `AgentDefinition` objects; lazy `AgentInstance` |
| `WorkflowMaterializer`            | ActiveJ                              | 10 canonical workflows → step DAG                             |

---

## 4. Binding Decisions

These decisions are locked and must not be re-opened during implementation:

| Decision                   | Choice                                                           | Rationale                                                                                         |
| -------------------------- | ---------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| **Persistence layer**      | Data-Cloud PostgreSQL via `EventLogStore` SPI                    | Durable, partition/offset resumable, consistent with platform                                     |
| **Event transport**        | Pluggable `EventCloudConnector` SPI; gRPC default                | gRPC for production (streaming, mTLS); HTTP for simple deploys; future connectors via SPI         |
| **Transport selection**    | `EVENT_CLOUD_TRANSPORT=grpc\|http` env var (default: `grpc`)     | No Java code change needed to switch transport                                                    |
| **Schema source of truth** | Proto-generated `bundle.schema.json` → `DataCloudSchemaRegistry` | Single origin; prevents drift between proto and inline strings                                    |
| **YAML definitions**       | Parameterized templates via `YamlTemplateEngine`                 | `{{ param }}` in any YAML; values from env vars or `values.yaml`; one source, many instantiations |
| **Operator discovery**     | `OperatorProvider` SPI (`ServiceLoader`)                         | Self-registering; no central mapping tables                                                       |
| **Agent instantiation**    | Lazy on first dispatch (weak-ref cache)                          | 590 agents is too many to pre-warm; saves cold-path memory                                        |
| **Multi-tenancy**          | Day-one enforcement via `tenantId` in all calls                  | Not retrofittable after data is written                                                           |
| **LLM routing**            | Agent YAML `model` field → `DefaultLLMGateway.addRoute()`        | Zero model names in Java; all controllable via config                                             |
| **Local dev LLM**          | `OllamaCompletionService` via `OLLAMA_HOST` env var              | No stubs; real local inference                                                                    |

---

## 5. Inventory of Changes

### 5.1 New Files to Create

| File (full path from workspace root)                                                                              | Package                                  | Purpose                                                                                                              |
| ----------------------------------------------------------------------------------------------------------------- | ---------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| `platform/java/event-cloud/src/main/java/com/ghatana/core/event/cloud/connector/EventCloudConnector.java`         | `com.ghatana.core.event.cloud.connector` | SPI interface for pluggable event transport                                                                          |
| `platform/java/event-cloud/src/main/java/com/ghatana/core/event/cloud/connector/EventCloudConnectorRegistry.java` | `com.ghatana.core.event.cloud.connector` | Discovers connectors via `ServiceLoader`; selects active from env var                                                |
| `platform/java/event-cloud/src/main/java/com/ghatana/core/event/cloud/grpc/GrpcEventCloudConnector.java`          | `com.ghatana.core.event.cloud.grpc`      | gRPC transport connector using `event_cloud.proto` stubs                                                             |
| `platform/java/event-cloud/src/main/java/com/ghatana/core/event/cloud/http/HttpEventCloudConnector.java`          | `com.ghatana.core.event.cloud.http`      | HTTP/SSE transport connector; configurable endpoint                                                                  |
| `platform/java/yaml-template/src/main/java/com/ghatana/core/yaml/YamlTemplateEngine.java`                         | `com.ghatana.core.yaml`                  | Resolves `{{ param }}` in YAML files before parsing                                                                  |
| `platform/java/yaml-template/src/main/java/com/ghatana/core/yaml/TemplateContext.java`                            | `com.ghatana.core.yaml`                  | Immutable value object carrying resolved template parameters                                                         |
| `platform/java/yaml-template/src/main/java/com/ghatana/core/yaml/TemplateContextBuilder.java`                     | `com.ghatana.core.yaml`                  | Merges env vars + `values.yaml` into a `TemplateContext`                                                             |
| `platform/java/schema-registry/src/main/java/com/ghatana/core/schema/SchemaRegistry.java`                         | `com.ghatana.core.schema`                | Interface: `getSchema`, `validate`, `registerSchema`                                                                 |
| `platform/java/schema-registry/src/main/java/com/ghatana/core/schema/DataCloudSchemaRegistry.java`                | `com.ghatana.core.schema`                | EventLogStore-backed impl; seeds from `bundle.schema.json`                                                           |
| `products/aep/platform/src/main/java/com/ghatana/aep/schema/DataCloudEventTypeRepository.java`                    | `com.ghatana.aep.schema`                 | Replaces `InMemoryEventTypeRepository`; persists to EventLogStore                                                    |
| `products/aep/platform/src/main/java/com/ghatana/aep/operator/YamlOperatorLoader.java`                            | `com.ghatana.aep.operator`               | Loads operator YAMLs → `OperatorCatalog` via SPI; hard-fail                                                          |
| `platform/java/agent-dispatch/src/main/java/com/ghatana/core/dispatch/CatalogAgentDispatcher.java`                | `com.ghatana.core.dispatch`              | **Existing platform class** — Event-topic → agentId dispatch operator. Reference; do NOT recreate in `products/aep`. |
| `products/aep/platform/src/main/java/com/ghatana/aep/di/YappcIntegrationModule.java`                              | `com.ghatana.aep.di`                     | ActiveJ module: loads YAPPC event-routing.yaml; subscribes topics                                                    |
| `products/yappc/core/agents/src/main/java/com/ghatana/yappc/agent/YappcOperatorProvider.java`                     | `com.ghatana.yappc.agent`                | SPI aggregator for all YAPPC pipeline operators                                                                      |
| `products/yappc/core/agents/src/main/java/com/ghatana/yappc/agent/DurableEventCloudPublisher.java`                | `com.ghatana.yappc.agent`                | Renamed from `DurableAepEventPublisher`; calls `EventCloud.append()`                                                 |
| `products/yappc/operators/src/main/java/com/ghatana/yappc/operators/PhaseTransitionValidatorOperator.java`        | `com.ghatana.yappc.operators`            | Validates `phase.transition` via `SchemaRegistry`                                                                    |
| `products/yappc/operators/src/main/java/com/ghatana/yappc/operators/PhaseStateManagerOperator.java`               | `com.ghatana.yappc.operators`            | State machine guard; appends `PHASE_ADVANCED` to EventCloud                                                          |
| `products/yappc/operators/src/main/java/com/ghatana/yappc/operators/GateOrchestratorOperator.java`                | `com.ghatana.yappc.operators`            | Parallel gate approval via `Promise.all()`; 30s timeout                                                              |
| `products/yappc/operators/src/main/java/com/ghatana/yappc/operators/LifecycleStatePublisherOperator.java`         | `com.ghatana.yappc.operators`            | Publishes `lifecycle.state.updated` to EventCloud (gRPC)                                                             |
| `products/yappc/operators/src/main/java/com/ghatana/yappc/operators/AgentDispatchValidatorOperator.java`          | `com.ghatana.yappc.operators`            | Validates `agent.dispatch.requested` via `SchemaRegistry`                                                            |
| `products/yappc/operators/src/main/java/com/ghatana/yappc/operators/AgentExecutorOperator.java`                   | `com.ghatana.yappc.operators`            | Wraps `AgentEventOperator`; circuit breaker; checkpoints every 10 events                                             |
| `products/yappc/operators/src/main/java/com/ghatana/yappc/operators/ResultAggregatorOperator.java`                | `com.ghatana.yappc.operators`            | 5s tumbling window; group by `agent_id + correlation_id`                                                             |
| `products/yappc/operators/src/main/java/com/ghatana/yappc/operators/MetricsCollectorOperator.java`                | `com.ghatana.yappc.operators`            | Micrometer counters/histograms every 10s                                                                             |
| `products/data-cloud/src/main/java/com/ghatana/datacloud/workflow/WorkflowRunRepository.java`                     | `com.ghatana.datacloud.workflow`         | Appends workflow lifecycle events; materializes run state                                                            |

### 5.2 Files to Delete

| File                                                                                                       | Reason                                                                                                                          |
| ---------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| `products/yappc/core/agents/src/main/java/com/ghatana/yappc/agent/HttpAepEventPublisher.java`              | Replaced by `EventCloud` facade + `HttpEventCloudConnector` (transport now owned by the platform connector layer, not by YAPPC) |
| `products/yappc/services/lifecycle/src/main/java/com/ghatana/yappc/services/lifecycle/AepEventBridge.java` | Wrapper around the deleted bespoke HTTP publisher                                                                               |

### 5.3 Files to Modify

| File                                                                                                              | Change Summary                                                                                                                                                                                                                                                                                                                                     |
| ----------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `products/aep/platform/src/main/java/com/ghatana/aep/di/AepCoreModule.java`                                       | Bind `EventCloud` facade (`ConnectorBackedEventCloud`) + `EventCloudConnectorRegistry`; connector selected from `EVENT_CLOUD_TRANSPORT` env var; wire `EventLogStore` SPI from data-cloud; add `SchemaRegistry` and `YamlTemplateEngine` bindings; swap `InMemoryEventTypeRepository` → `DataCloudEventTypeRepository`; add `tenantId` propagation |
| `products/aep/platform/src/main/java/com/ghatana/aep/di/AepOrchestrationModule.java`                              | Replace `InMemoryCheckpointStorage` with `PostgresCheckpointStorage`                                                                                                                                                                                                                                                                               |
| `products/aep/platform/src/main/java/com/ghatana/aep/di/AepConnectorModule.java`                                  | Replace all `localhost:*` hardcodes with env var resolution; fail-fast on missing required vars                                                                                                                                                                                                                                                    |
| `products/aep/platform/src/main/java/com/ghatana/orchestrator/ai/impl/AIAgentOrchestrationManagerImpl.java`       | Replace `ConcurrentHashMap` with EventLogStore-backed event-sourced state                                                                                                                                                                                                                                                                          |
| `products/aep/platform/src/main/java/com/ghatana/aep/config/PipelineMaterializer.java`                            | Call `OperatorCatalog.lookup(operatorId)` for live instances; hard-fail on missing operator                                                                                                                                                                                                                                                        |
| `products/yappc/services/ai/src/main/java/com/ghatana/yappc/services/ai/AiServiceModule.java`                     | Delete entire `else` stub branch; add `OLLAMA_HOST` path for local dev; throw `IllegalStateException` if no provider configured                                                                                                                                                                                                                    |
| `products/yappc/core/agents/src/main/java/com/ghatana/yappc/agent/YappcAgentSystem.java`                          | Register all 590 `AgentDefinition` objects in `AgentRegistryService`; dynamic model routing from YAML `model` field; inject `PersistentMemoryPlane`; constructor-inject `EventCloud`                                                                                                                                                               |
| `products/yappc/core/agents/src/main/java/com/ghatana/yappc/agent/YAPPCAgentBase.java`                            | Constructor-inject `EventCloud`; remove deprecated static `globalAepEventPublisher`; activate PERCEIVE memory retrieval (500ms timeout); connect REFLECT to AEP pattern detection                                                                                                                                                                  |
| `products/yappc/services/lifecycle/src/main/java/com/ghatana/yappc/services/lifecycle/YappcLifecycleService.java` | Implement `POST /lifecycle/phase/:projectId/advance` via `EventCloud.append()`→subscribe; implement `GET /current` and `GET /gates` from EventLogStore                                                                                                                                                                                             |
| `products/yappc/backend/api/aep/EventSchemaValidator.java`                                                        | Remove `ConcurrentHashMap` + 4 inline schema strings; inject `SchemaRegistry`; delegate validation                                                                                                                                                                                                                                                 |

---

## Phase 1 — AEP Operator Framework Hardening

> **Goal**: Make AEP's foundation production-ready before YAPPC sits on top. No stubs. No hardcoded config.  
> **Blocker for**: All subsequent phases.  
> **Steps 1a–1g are independent and run in parallel.**

### Step 1a — Wire EventCloud to Data-Cloud PostgreSQL

**File**: `products/aep/platform/src/main/java/com/ghatana/aep/di/AepCoreModule.java`

In `AepCoreModule`, replace `EventLogStoreBackedEventCloud`'s in-memory backing with the production `EventLogStore` SPI from `products/data-cloud/spi`. Bind `EventLogStore` via `@Provides EventLogStore` pointing to the PostgreSQL impl from `libs:database`.

Add `tenantId` propagation:

```
AepEngine.process(tenantId, event)
  → EventCloud.append(tenantId, eventType, payload)
    → EventLogStore.append(TenantContext(tenantId), ...)
```

All `append()` and `subscribe()` overloads must carry `tenantId` as their first parameter from here forward.

---

### Step 1b — Implement `EventCloudConnector` SPI and Transport Connectors _(parallel with 1a)_

**New module**: `platform/java/event-cloud/` (extend; no new module needed)

#### `EventCloudConnector` — the SPI contract

```java
/**
 * @doc.type interface
 * @doc.purpose Pluggable transport SPI for EventCloud append and subscribe operations.
 * @doc.layer platform
 * @doc.pattern Strategy
 */
public interface EventCloudConnector {
    String transportType();      // "grpc", "http", "kafka", etc. — matched against EVENT_CLOUD_TRANSPORT
    Promise<AppendResult>   append(String tenantId, String eventType, Map<String,Object> payload);
    Promise<Void>           subscribe(String tenantId, String eventType, EventHandler handler);
    Promise<Void>           shutdown();
}
```

Each connector module declares itself in:

```
src/main/resources/META-INF/services/com.ghatana.core.event.cloud.connector.EventCloudConnector
```

#### `EventCloudConnectorRegistry` — selects active connector

```java
// Reads EVENT_CLOUD_TRANSPORT env var (default: "grpc")
// Uses ServiceLoader<EventCloudConnector> to discover all available connectors
// Returns the connector whose transportType() matches; IllegalStateException if none matches
```

#### `GrpcEventCloudConnector` — gRPC transport (production default)

```java
/**
 * @doc.type class
 * @doc.purpose gRPC EventCloudConnector using event_cloud.proto stubs.
 * @doc.layer platform
 * @doc.pattern Strategy
 */
public final class GrpcEventCloudConnector implements EventCloudConnector {
    public String transportType() { return "grpc"; }
    // Reads: EVENT_CLOUD_GRPC_HOST, EVENT_CLOUD_GRPC_PORT — fail-fast if absent
    // Optional: EVENT_CLOUD_GRPC_TLS_CERT / EVENT_CLOUD_GRPC_TLS_KEY for mTLS
    // append() → AppendRequestProto → EventCloudServiceGrpc.newStub(channel).append()
    // subscribe() → SubscribeRequestProto → streaming RPC → handler.handle() per event
}
```

#### `HttpEventCloudConnector` — HTTP/SSE transport

```java
/**
 * @doc.type class
 * @doc.purpose HTTP EventCloudConnector for simpler or firewall-constrained deployments.
 * @doc.layer platform
 * @doc.pattern Strategy
 */
public final class HttpEventCloudConnector implements EventCloudConnector {
    public String transportType() { return "http"; }
    // Reads: EVENT_CLOUD_HTTP_BASE_URL — fail-fast if absent
    // append()    → POST {base}/events/{tenantId}/{eventType}
    // subscribe() → SSE GET {base}/events/{tenantId}/stream?type={eventType}
    //             → fires handler.handle() per Server-Sent Event
}
```

#### `ConnectorBackedEventCloud` — the `EventCloud` facade

`EventCloud` (the existing interface) becomes a thin facade that injects `EventCloudConnectorRegistry` and delegates every `append()` / `subscribe()` call to the active connector. **No caller ever knows which transport is in use.**

**Transport selection env vars**:

| Env var                     | Default                                    | Purpose                   |
| --------------------------- | ------------------------------------------ | ------------------------- |
| `EVENT_CLOUD_TRANSPORT`     | `grpc`                                     | Selects active connector  |
| `EVENT_CLOUD_GRPC_HOST`     | — (fail-fast)                              | gRPC connector: host      |
| `EVENT_CLOUD_GRPC_PORT`     | — (fail-fast)                              | gRPC connector: port      |
| `EVENT_CLOUD_GRPC_TLS_CERT` | — (optional, see §Further Considerations)  | gRPC connector: mTLS cert |
| `EVENT_CLOUD_HTTP_BASE_URL` | — (fail-fast if `http` transport selected) | HTTP connector: base URL  |

---

### Step 1c — Remove HttpAepEventPublisher; wire DurableEventCloudPublisher _(depends on 1b)_

1. **Delete** `products/yappc/core/agents/src/main/java/com/ghatana/yappc/agent/HttpAepEventPublisher.java` — YAPPC no longer owns an HTTP publisher; if HTTP transport is desired, that is handled transparently by `HttpEventCloudConnector` via the `EventCloud` facade.
2. Rename `DurableAepEventPublisher` → `DurableEventCloudPublisher`. Rewrite to call `EventCloud.append(tenantId, eventType, payload)` — no HTTP URL config, no transport details, no manual retry. The active connector handles backpressure and retries internally.
3. Any remaining references in YAPPC source to `HttpAepEventPublisher` or `AepEventBridge` must route through `EventCloud.append()` instead.

---

### Step 1d — Swap CheckpointStorage to PostgreSQL _(parallel with 1a)_

**File**: `products/aep/platform/src/main/java/com/ghatana/aep/di/AepOrchestrationModule.java`

Replace `InMemoryCheckpointStorage` binding with `PostgresCheckpointStorage`. Inject `DataSource` from `libs:database`. Schema (table `aep_checkpoints`) must be created via Flyway migration, not ad-hoc DDL.

---

### Step 1e — Event-Source AIAgentOrchestrationManagerImpl _(parallel with 1a)_

**File**: `products/aep/platform/src/main/java/com/ghatana/orchestrator/ai/impl/AIAgentOrchestrationManagerImpl.java`

Replace all `ConcurrentHashMap` state maps with event-sourced reads from `EventLogStore`:

| Old (ConcurrentHashMap)                      | New (EventLogStore event type)                    |
| -------------------------------------------- | ------------------------------------------------- |
| `agentDefinitions.put(agentId, def)`         | append `AGENT_REGISTERED` event                   |
| `executionStatuses.put(executionId, status)` | append `EXECUTION_STATUS_CHANGED` event           |
| `agentChains.put(chainId, chain)`            | append `AGENT_CHAIN_REGISTERED` event             |
| `getExecutionStatus(id)`                     | materialize last `EXECUTION_STATUS_CHANGED` by id |

All reads materialize state by replaying events from the last checkpoint offset. All writes go to `EventLogStore.append()` (gRPC via `EventCloud`) before any in-memory cache is updated.

---

### Step 1f — Env-Driven Connector Config _(parallel with 1a)_

**File**: `products/aep/platform/src/main/java/com/ghatana/aep/di/AepConnectorModule.java`

Remove all hardcoded values. Replace with env var resolution via `platform:java:config`:

| Hardcoded value  | Env var                           | Behavior if absent                 |
| ---------------- | --------------------------------- | ---------------------------------- |
| `localhost:9092` | `KAFKA_BOOTSTRAP_SERVERS`         | `IllegalStateException` at startup |
| `localhost:5672` | `RABBITMQ_HOST` + `RABBITMQ_PORT` | `IllegalStateException` at startup |
| `us-east-1`      | `AWS_REGION`                      | `IllegalStateException` at startup |

No defaults. No silent fallbacks. If a connector is not configured, AEP boots only the connectors whose env vars are present (opt-in connectors) — but required connectors (Kafka for core pipelines) must fail fast.

---

### Step 1g — tenantId in AgentRegistryService _(parallel with 1a)_

**File**: `platform/java/agent-framework/src/.../AgentRegistryService.java` and all callers.

Add `TenantContext` (or `String tenantId`) as a mandatory first parameter to:

- `registerDefinition(tenantId, AgentDefinition)`
- `lookupDefinition(tenantId, agentId)`
- `listAll(tenantId)`
- `registerInstance(tenantId, AgentInstance)`
- `lookupInstance(tenantId, agentId)`

All JPA/EventLogStore queries must include `WHERE tenant_id = :tenantId`.

---

### Phase 1 Verification

- [ ] All DI modules boot without errors or stub bindings
- [ ] `EventLogStore.append()` is invoked on `AepEngine.process()` — confirm via integration test
- [ ] `aep_checkpoints` table exists in PostgreSQL; checkpoint row written after pipeline step
- [ ] With `EVENT_CLOUD_TRANSPORT=grpc`: gRPC channel connects successfully
- [ ] With `EVENT_CLOUD_TRANSPORT=http`: HTTP connector posts events to `EVENT_CLOUD_HTTP_BASE_URL`
- [ ] `EVENT_CLOUD_TRANSPORT=unknown-value` → `IllegalStateException` listing available connector types
- [ ] `KAFKA_BOOTSTRAP_SERVERS` absent → service refuses to start with clear message
- [ ] No reference to `localhost` in any `AepConnectorModule` method body

---

## Phase 2 — Schema Registry

> **Goal**: Single source of truth for all event and pipeline schemas, backed by Data-Cloud. Eliminate all inline schema strings.  
> **Depends on**: Phase 1 (Data-Cloud EventLogStore wired)  
> **Blocker for**: Phases 3, 4, 5

### Step 2a — Introduce `SchemaRegistry` Interface

**New module**: `platform/java/schema-registry/`

```java
/**
 * @doc.type interface
 * @doc.purpose Canonical schema lookup, validation, and registration service.
 * @doc.layer platform
 * @doc.pattern Repository
 */
public interface SchemaRegistry {
    Promise<JsonSchema>       getSchema(String schemaId, String version);
    Promise<ValidationResult> validate(String schemaId, String version, Map<String, Object> payload);
    Promise<Void>             registerSchema(String schemaId, String version, JsonSchema schema);
    Promise<Void>             registerSchema(String schemaId, String version, JsonSchema schema,
                                             CompatibilityMode mode); // BACKWARD | FORWARD | FULL
}
```

### Step 2b — Implement `DataCloudSchemaRegistry`

**New file**: `platform/java/schema-registry/src/main/java/com/ghatana/core/schema/DataCloudSchemaRegistry.java`

- Stores schema records as events in Data-Cloud `EventLogStore` under event type `schema.registered` (keyed by `schemaId + version`).
- `getSchema()` materializes the latest `schema.registered` event for that key.
- `registerSchema(..., CompatibilityMode)` reads the previous version (if any) and validates BACKWARD/FORWARD/FULL compatibility before appending.
- `validate()` retrieves schema then runs against payload using standard JSON Schema validation.

### Step 2c — Seed from Proto-Generated Bundle at Startup _(depends on 2b)_

`platform/contracts/build.gradle.kts` already generates `build/generated/schemas/bundle.schema.json`. At `AepCoreModule` and `AiServiceModule` startup:

```java
SchemaBootstrapper.seedFromBundle(schemaRegistry, bundleJson);
// Idempotent: no-op if (schemaId, version) already present in EventLogStore.
```

This makes proto-defined contracts (`agent_manifest.proto`, `event_cloud.proto`, `agent_service.proto`) the canonical schemas — no manual JSON duplicates.

### Step 2d — Replace `EventSchemaValidator` Hardcoded Map _(depends on 2b)_

**File**: `products/yappc/backend/api/aep/EventSchemaValidator.java`

- Remove `ConcurrentHashMap<String, String> eventSchemas` and all 4 inline schema strings (`agent_dispatch`, `agent_result`, `phase_transition`, `task_status`).
- Inject `SchemaRegistry`. Implement `validateEvent(eventType, payload)` as:
  ```java
  schemaRegistry.validate(eventType, "latest", payload)
  ```

### Step 2e — Replace `InMemoryEventTypeRepository` _(depends on 2b)_

**New file**: `products/aep/platform/src/.../DataCloudEventTypeRepository.java`

Stores event type records in Data-Cloud EventLogStore (event type `eventtype.registered`). Wire into `AepCoreModule` replacing `InMemoryEventTypeRepository` binding.

### Step 2f — Tests _(parallel with 2d, 2e)_

`SchemaRegistryIntegrationTest extends EventloopTestBase`:

1. Seed 3 schemas from bundle → validate valid payload → assert `ValidationResult.valid()`
2. Validate invalid payload → assert failure with field path included in `ValidationResult`
3. Register schema with `BACKWARD` mode; attempt a breaking change → assert `SchemaCompatibilityException`
4. `DataCloudEventTypeRepository` persists and retrieves event type records

### Phase 2 Verification

- [ ] `EventSchemaValidator` has zero inline JSON strings
- [ ] `InMemoryEventTypeRepository` binding removed from `AepCoreModule`
- [ ] All 4 proto schemas accessible via `schemaRegistry.getSchema("phase_transition", "latest")`
- [ ] Schema compatibility violation throws at registration time, not at validation time

---

## Phase 3 — Parameterized YAML Templates

> **Goal**: Every YAML file (operator, pipeline, workflow, agent instance) supports `{{ param }}` substitution. Common definitions expressed once; multiple instantiations via different values. No copy-paste YAML variants.  
> **Depends on**: Phase 1 (platform infra wired)  
> **Blocker for**: Phases 4, 5, 6 (all YAML loading passes through the template engine)

### Step 3a — Implement `YamlTemplateEngine`

**New module**: `platform/java/yaml-template/`

```java
/**
 * @doc.type class
 * @doc.purpose Resolves {{ param }} placeholders in YAML content before parsing.
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class YamlTemplateEngine {
    // render(String rawYaml, TemplateContext ctx) → String resolvedYaml
    // Replaces {{ varName }} with ctx.get(varName)
    // Unknown variables → IllegalStateException (fail-fast; no silent empty strings)
    // Supports nested access: {{ pipeline.name }}, {{ agent.model }}
}
```

Template syntax deliberately simple — Mustache-style `{{ }}` double-brace. No conditionals or loops in templates (use YAML anchors for structural reuse); `{{ }}` is purely value substitution.

### Step 3b — `TemplateContext` and `TemplateContextBuilder`

```java
/**
 * @doc.type record
 * @doc.purpose Immutable resolved parameter set for YAML template rendering.
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record TemplateContext(Map<String, String> params) {
    public String get(String key) { /* throws if absent */ }
}
```

`TemplateContextBuilder` merges sources in order (later sources override earlier):

1. **Environment variables** — all `ENV_VARS` available by default
2. **Global `values.yaml`** — `platform/agent-catalog/values.yaml` (repo-level defaults)
3. **Local `values.yaml`** — next to the template file (product-level overrides)
4. **Explicit params map** — passed programmatically (test/runtime overrides)

### Step 3c — Template File Conventions

Templateable YAMLs declare themselves with a `template: true` header:

```yaml
# Example: products/yappc/config/pipelines/lifecycle-management-v1.yaml
template: true
params:
  required: [pipeline.name, max_buffer_size]
  defaults:
    dlq.topic: lifecycle.management.dlq
    circuit_breaker.failures: 10

pipeline:
  name: "{{ pipeline.name }}"
  stages:
    - id: validator
      operator: yappc:phase-transition-validator
      config:
        dlq: "{{ dlq.topic }}"
    - id: executor
      operator: yappc:agent-executor
      config:
        maxBuffer: "{{ max_buffer_size }}"
        circuitBreakerFailures: "{{ circuit_breaker.failures }}"
```

A values file instantiates the template:

```yaml
# products/yappc/config/pipelines/values/lifecycle-prod.yaml
pipeline.name: lifecycle-management-v1
max_buffer_size: 4096
```

### Step 3d — Integrate Template Engine into All YAML Loaders _(depends on 3a, 3b)_

Every YAML loading point in the platform must call `YamlTemplateEngine.render(rawYaml, context)` **before** passing content to SnakeYAML or any parser:

| Loader                                        | Where to inject                            |
| --------------------------------------------- | ------------------------------------------ |
| `YamlOperatorLoader`                          | Before parsing each operator YAML          |
| `PipelineMaterializer`                        | Before compiling each pipeline YAML to DAG |
| `WorkflowMaterializer`                        | Before parsing each workflow YAML          |
| `YappcAgentSystem.loadAgentDefinitions()`     | Before parsing each agent YAML file        |
| `YappcIntegrationModule` (event-routing.yaml) | Before parsing routing rules               |

The `TemplateContext` for each loader is built from a `TemplateContextBuilder` initialized at module startup — env vars are always present; values files are scanned from well-known paths relative to the YAML file.

### Step 3e — Agent Instance Templates _(parallel with 3d)_

Agent YAML files can extend a base template via `extends`:

```yaml
# products/yappc/config/agents/sdlc/generate-tests-specialist.yaml
extends: base-agent-template
params:
  agent.id: generate-tests-specialist
  agent.model: llama3
  agent.maxCostPerCall: 0.05
  agent.description: Generates comprehensive test suites
```

```yaml
# platform/agent-catalog/base-agent-template.yaml
template: true
params:
  required: [agent.id, agent.model]
  defaults:
    agent.maxCostPerCall: 0.10
    agent.memoryTier: episodic

id: "{{ agent.id }}"
model: "{{ agent.model }}"
maxCostPerCall: "{{ agent.maxCostPerCall }}"
memoryConfig:
  tier: "{{ agent.memoryTier }}"
```

`YamlTemplateEngine.renderWithInheritance(file, context)` resolves the `extends` chain (max depth 3; `IllegalStateException` on circular extends) before rendering `{{ }}` substitutions.

### Step 3f — Tests

`YamlTemplateEngineTest extends EventloopTestBase`:

- Render simple template with env var context → assert all `{{ }}` resolved
- Unknown variable → assert `IllegalStateException` with variable name in message
- `extends` chain of depth 2 → assert merged parameter resolution
- Circular `extends` → assert `IllegalStateException`
- Load `lifecycle-management-v1.yaml` with `lifecycle-prod.yaml` values → parse to valid pipeline config

### Phase 3 Verification

- [ ] `YamlTemplateEngine.render()` resolves all declared params; throws on missing param
- [ ] All six YAML loaders pass content through template engine before parsing
- [ ] `extends` resolution works with `base-agent-template.yaml` for at least one live agent YAML
- [ ] Old duplicated YAML variant files (if any) collapsed into one template + one values file

---

## Phase 4 — AEP Operator Catalog Loading

> **Goal**: Wire operator YAMLs into `OperatorCatalog` via `OperatorProvider` SPI. No hardcoded mapping tables.  
> **Depends on**: Phases 2, 3  
> **Blocker for**: Phases 5, 6

### Step 4a — Operator Self-Registration via SPI

Each concrete operator class implements `OperatorProvider` and declares its supported `operatorType` (matching the YAML `type` or `id` field). `OperatorProviderRegistry` (already in `core.operator.spi`) discovers all providers via `ServiceLoader<OperatorProvider>`.

Each module that contains operators must have:

```
src/main/resources/META-INF/services/com.ghatana.core.operator.spi.OperatorProvider
```

…listing its provider class(es). No central mapping table anywhere.

### Step 4b — `YamlOperatorLoader` _(depends on 4a)_

**New file**: `products/aep/platform/src/main/java/com/ghatana/aep/operator/YamlOperatorLoader.java`

```java
/**
 * @doc.type class
 * @doc.purpose Loads operator YAML files into OperatorCatalog via OperatorProvider SPI.
 * @doc.layer product
 * @doc.pattern Service
 */
public final class YamlOperatorLoader {
    // 1. For each operator YAML path:
    //    a. YamlTemplateEngine.render(rawYaml, TemplateContextBuilder.fromEnv().build())
    //    b. Parse rendered YAML → reads type + config block
    //    c. OperatorProviderRegistry.lookup(type) → calls provider.create(config)
    //    d. OperatorCatalog.register(id, operator)
    // IllegalStateException if no provider found for declared type — no silent skips
}
```

YAPPC's `YappcOperatorProvider` follows the same SPI pattern. The same `YamlOperatorLoader` handles both AEP and YAPPC operator YAML paths — no duplicate loader.

### Step 4c — Wire `PipelineMaterializer` to `OperatorCatalog` _(depends on 4b)_

**File**: `products/aep/platform/src/main/java/com/ghatana/aep/config/PipelineMaterializer.java`

Before compiling any pipeline YAML: call `YamlTemplateEngine.render(rawYaml, context)`. After operator ID resolution: call `OperatorCatalog.lookup(operatorId)` for live instances. If the operator is not in the catalog: throw `IllegalStateException("Operator not found: " + operatorId)`. Remove all warning-and-skip placeholder logic.

### Step 4d — Integration Test _(parallel with 4b, 4c)_

`OperatorCatalogTest extends EventloopTestBase`:

- Load all 6 AEP operator YAMLs (with template rendering) via `YamlOperatorLoader`
- Verify `OperatorCatalog.lookup("pattern-detection-agent")` returns non-null `PatternDetectionAgent`
- Materialize a two-stage pipeline with a templated buffer size; verify rendered value in operator config
- Attempt to load YAML referencing unknown type; verify `IllegalStateException` thrown

### Phase 4 Verification

- [ ] `Orchestrator.start()` discovers all 6 AEP operators
- [ ] `PipelineMaterializer` passes YAML through `YamlTemplateEngine` before compiling
- [ ] `PipelineMaterializer` throws on unresolvable operator ID (no silent skip)
- [ ] No `switch`, `if/else`, or `Map<String, Class>` mapping operators to classes in any Java file

---

## Phase 5 — AEP ↔ YAPPC Event Routing Bridge

> **Goal**: AEP subscribes to YAPPC's 60+ event topics via the active EventCloud connector. Implement `CatalogAgentDispatcher`.  
> **Depends on**: Phase 4  
> **Blocker for**: Phases 6, 7

### Step 5a — Wire `CatalogAgentDispatcher` from Platform

> **Note**: `CatalogAgentDispatcher` already exists at `platform/java/agent-dispatch/`. Do **NOT** create a duplicate in `products/aep`. Add `libs:agent-dispatch` as a compile dependency in AEP's build file, then bind it in `AepOrchestrationModule`.

`CatalogAgentDispatcher` is an `AbstractOperator` / `UnifiedOperator` that:

1. Looks up `event.type()` in the routing table loaded from `event-routing.yaml` → finds agentId
2. Calls `AIAgentOrchestrationManager.executeChain(agentId, event, context)`
3. Returns `OperatorResult`

This class must **not** be duplicated. If AEP-specific customization is needed, subclass it.

### Step 5b — `YappcIntegrationModule` Wires 60+ Topics _(parallel with 5a)_

**New file**: `products/aep/platform/src/main/java/com/ghatana/aep/di/YappcIntegrationModule.java`

ActiveJ module that:

1. Passes `products/yappc/config/agents/event-routing.yaml` through `YamlTemplateEngine` (topic names can be templated for env-specific routing)
2. Parses rendered YAML into `EventRoutingConfig (Map<topic, agentId>)`
3. Calls `EventCloud.subscribe(tenantId, topic, ...)` for all 60+ topics — transport is transparent
4. Routes each received event to `CatalogAgentDispatcher`
5. Registers `CatalogAgentDispatcher` in `OperatorCatalog` under id `catalog-agent-dispatcher`

### Step 5c — Audit YAPPC Event Publishing _(depends on Phase 1c)_

Audit all YAPPC services against `event-routing.yaml`. Any event type declared in the routing file but not published anywhere in YAPPC source must be added to the appropriate service via `DurableEventCloudPublisher.append()`. All publish calls go through `EventCloud.append()` — no direct HTTP, no bypass.

### Phase 5 Verification

- [ ] Publish `test.failed` event via `EventCloud.append()` → gRPC delivers to AEP subscription
- [ ] `CatalogAgentDispatcher` resolves `test.failed` → `debug-orchestrator` agent
- [ ] `AIAgentOrchestrationManager.executeChain()` invoked with correct agentId
- [ ] All 60+ topics from `event-routing.yaml` have a corresponding `EventCloud.subscribe()` call in `YappcIntegrationModule`

---

## Phase 6 — YAPPC Pipeline Operators & Registration

> **Goal**: Implement the 10 operators from YAPPC's two pipeline YAMLs; register both pipelines with AEP.  
> **Depends on**: Phase 5  
> **Blocker for**: Phase 7

All operators use `SchemaRegistry` for validation and `EventCloud` (gRPC) for publishing. No HTTP. No inline schemas. Each operator self-registers via `OperatorProvider` SPI and is included in `META-INF/services`.

### `lifecycle-management-v1` Operators _(parallel sub-steps)_

| Operator Class                     | SPI Type                           | Behaviour                                                                                                                                        |
| ---------------------------------- | ---------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| `PhaseTransitionValidatorOperator` | `yappc:phase-transition-validator` | Validates `phase.transition` via `SchemaRegistry.validate("phase_transition", "latest", payload)`. Invalid events → `lifecycle.management.dlq`   |
| `PhaseStateManagerOperator`        | `yappc:phase-state-manager`        | Reads current phase from Data-Cloud EventLogStore; validates state machine transitions; appends `PHASE_ADVANCED` event via `EventCloud.append()` |
| `GateOrchestratorOperator`         | `yappc:gate-orchestrator`          | Parallel gate approvals via `Promise.all()`; 30s timeout; emits `gate.passed` or `gate.blocked` via `EventCloud.append()`                        |
| `LifecycleStatePublisherOperator`  | `yappc:lifecycle-state-publisher`  | Publishes `lifecycle.state.updated` to EventCloud (gRPC); consumed by websocket subscription in `ApiApplication`                                 |
| `CatalogAgentDispatcher`           | `catalog-agent-dispatcher`         | Reused from Phase 4                                                                                                                              |

### `agent-orchestration-v1` Operators _(parallel with lifecycle operators)_

| Operator Class                   | SPI Type                         | Behaviour                                                                                                                                |
| -------------------------------- | -------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| `AgentDispatchValidatorOperator` | `yappc:agent-dispatch-validator` | Validates `agent.dispatch.requested` via `SchemaRegistry.validate("agent_dispatch", "latest", payload)`                                  |
| `BackpressureOperator`           | (existing)                       | Reuse existing; configure DROP_OLDEST, buffer=2048, overflow → `agent.orchestration.dlq`                                                 |
| `AgentExecutorOperator`          | `yappc:agent-executor`           | Wraps `AgentEventOperator` + circuit breaker (10 failures / 60s / half-open=5) + checkpoints every 10 events via `CheckpointCoordinator` |
| `ResultAggregatorOperator`       | `yappc:result-aggregator`        | 5s tumbling window; group by `agent_id + correlation_id`                                                                                 |
| `MetricsCollectorOperator`       | `yappc:metrics-collector`        | Micrometer counters/histograms every 10s via `libs:observability`                                                                        |

### Pipeline Registration _(depends on all operators above)_

A `YappcOperatorProvider` aggregates all 9 new operators under namespace `yappc` in `products/yappc/core/`. At `YappcAiService` startup:

1. `YamlOperatorLoader.load("products/yappc/config/pipelines/")` — each YAML rendered through `YamlTemplateEngine` first (buffer sizes, DLQ topic names, circuit breaker thresholds are all templated)
2. `PipelineRegistryClient.register(lifecycle-management-v1)` and `register(agent-orchestration-v1)`
3. `Orchestrator.deployPipeline(lifecycle-management-v1)` and `deployPipeline(agent-orchestration-v1)`

### Phase 6 Verification

- [ ] `Orchestrator.listPipelines()` returns both YAPPC pipelines with all stages resolved
- [ ] Zero references to `HttpAepEventPublisher` anywhere in the codebase
- [ ] Zero inline JSON schema strings in any Java class
- [ ] `MetricsCollectorOperator` exports `yappc_operator_events_total` to Prometheus
- [ ] Change `max_buffer_size` in `values/lifecycle-prod.yaml` → restart → pipeline reflects new value without any Java change

---

## Phase 7 — YAPPC Lifecycle Service Implementation

> **Goal**: Remove `not_implemented` stub; complete advance-phase fully through AEP lifecycle pipeline via EventCloud (connector-agnostic).  
> **Depends on**: Phase 6  
> **Parallel with**: Phase 8

**File**: `products/yappc/services/lifecycle/src/main/java/com/ghatana/yappc/services/lifecycle/YappcLifecycleService.java`

### Endpoints to Implement

**`POST /lifecycle/phase/:projectId/advance`** _(remove stub)_:

1. Extract `tenantId` from JWT (already available in request context)
2. Publish `phase.transition` event: `EventCloud.append(tenantId, "phase.transition", payload)` (gRPC)
3. Subscribe with `correlationId` for `lifecycle.state.updated` event (30s timeout via streaming RPC)
4. Return new phase state JSON to caller; return 408 if timeout

**`GET /lifecycle/phase/:projectId/current`** _(parallel)_:

- Query Data-Cloud EventLogStore for latest `PHASE_ADVANCED` event by `projectId + tenantId`
- Return current phase JSON

**`GET /lifecycle/project/:projectId/gates`** _(parallel)_:

- Query Data-Cloud EventLogStore for gate state events by `projectId + tenantId`
- Return list of gate statuses

### Cleanup

Delete `AepEventBridge` entirely — it was a wrapper around `HttpAepEventPublisher` which is deleted in Phase 1c. All publish calls go directly to injected `EventCloud` instance.

### Phase 7 Verification

- [ ] Full round-trip: `POST /lifecycle/phase/advance` → active EventCloud connector delivers → AEP lifecycle pipeline runs all 5 operators → `lifecycle.state.updated` emitted → caller receives updated phase state JSON
- [ ] `404 Not Implemented` response no longer returned by any lifecycle endpoint
- [ ] No `HttpAepEventPublisher` or `AepEventBridge` in the call chain
- [ ] 30s timeout returns `408` with correlation ID for debugging

---

## Phase 8 — YAPPC Agent Catalog, LLM Gateway & Lazy Registry

> **Goal**: Replace deprecated `YAPPCAgentRegistry`; configure `LLMGateway` fully from YAML (including templated agent definitions); enable lazy `AgentInstance` creation.  
> **Depends on**: Phase 6  
> **Parallel with**: Phase 7

### Step 8a — Remove LLM Stub Fallback; Fail-Fast

**File**: `products/yappc/services/ai/src/main/java/com/ghatana/yappc/services/ai/AiServiceModule.java`

Delete the entire `else` stub branch in `llmGateway()`. Replace with:

```java
if (ollamaHost != null) {
    // Local dev path: OllamaCompletionService (see Further Considerations §1)
    builder.addProvider("ollama", new OllamaCompletionService(ollamaHost));
} else if (openaiKey != null) {
    builder.addProvider("openai", new ToolAwareOpenAICompletionService(openaiKey));
} else if (anthropicKey != null) {
    builder.addProvider("anthropic", new ToolAwareAnthropicCompletionService(anthropicKey));
} else {
    throw new IllegalStateException(
        "No LLM provider configured. Set OLLAMA_HOST, OPENAI_API_KEY, or ANTHROPIC_API_KEY.");
}
```

Apply the same fail-fast pattern to AEP's DI modules that reference LLM providers.

### Step 8b — Dynamic Model Routing from Agent YAML _(depends on 8a)_

**File**: `products/yappc/core/agents/src/main/java/com/ghatana/yappc/agent/YappcAgentSystem.java`

In `loadAgentDefinitions()`, after parsing each `AgentDefinition`, read the YAML `model` field and infer provider:

| Model name prefix                 | Provider    |
| --------------------------------- | ----------- |
| `claude`                          | `anthropic` |
| `gpt`, `o1`, `o3`                 | `openai`    |
| `llama`, `mistral`, `qwen`, `phi` | `ollama`    |

Call `DefaultLLMGateway.addRoute(agentId, providerName)` for each.

Wrap each provider with `CostEnforcingCompletionService` using the tenant's budget from `AgentDefinition.maxCostPerCall` — no agent has unbounded cost access.

**Zero model name string literals in Java code.** All model routing is derived from YAML.

### Step 8c — Register All 590 AgentDefinitions _(parallel with 8b)_

Extend `YappcAgentSystem.loadAgentDefinitions()` to scan all 17 domain catalogs from `_index.yaml` via `FileBasedCatalog`. For each YAML, parse to `AgentDefinition` and call `AgentRegistryService.registerDefinition(tenantId, definition)`.

### Step 8d — Lazy `AgentInstance` Creation in `CatalogAgentDispatcher` _(depends on 8c — aligns with platform/java/agent-dispatch/CatalogAgentDispatcher; do not recreate in products/aep)_

On dispatch:

1. `AgentRegistryService.lookupInstance(tenantId, agentId)` — if absent:
2. Load `AgentDefinition` from registry
3. Build `AgentInstance` with a weak-ref cache entry
4. Proceed with dispatch

No pre-warming of all 590 agents at startup. Instance is created only on first dispatch per tenant.

### Step 8e — AgentRegistryService Migration _(parallel with 8d)_

> **Note**: `AgentRegistryService` additions (`tenantId` parameter) are a platform change (`platform/java/agent-framework/`). Do not duplicate in YAPPC product code.

- `GET /api/v1/ai/agents` → `AgentRegistryService.listAll(tenantId)` → JSON response
- Replace all `YAPPCAgentRegistry` references with `AgentRegistryService` in all YAPPC services
- Add `@Deprecated` shim on `YAPPCAgentRegistry` for one release cycle, then delete

### Phase 8 Verification

- [ ] Service startup throws `IllegalStateException` if no LLM provider env var is set
- [ ] `GET /api/v1/ai/agents` returns all 590 agents
- [ ] `DefaultLLMGateway.getAvailableProviders()` lists only real (non-stub) providers
- [ ] No model name string literal (e.g., `"claude-3.5-sonnet"`) appears in any Java file (`grep -r "claude-3\|gpt-4\|llama3" src/`)
- [ ] Second dispatch to same agent reuses weak-ref cached `AgentInstance`
- [ ] At least one agent YAML uses `extends: base-agent-template` and resolves correctly to a valid `AgentDefinition`

---

## Phase 9 — Durable Memory & Event Sourcing

> **Goal**: Replace `EventLogMemoryStore` (in-memory) with Data-Cloud PostgreSQL for all YAPPC memory operations.  
> **Depends on**: Phase 7 (EventCloud fully wired)  
> **Parallel with**: Phase 8

### Step 9.1 — Wire `PersistentMemoryPlane`

**File**: `products/yappc/core/agents/src/main/java/com/ghatana/yappc/agent/YappcAgentSystem.java`

Inject `DataSource` from `libs:database`. Replace `EventLogMemoryStore` with `PersistentMemoryPlane` backed by Data-Cloud PostgreSQL. Apply Flyway migration from existing `init-db.sql` for `memory_items` table.

### Step 9.2 — Fix `YAPPCAgentBase` Constructor Injection _(parallel)_

**File**: `products/yappc/core/agents/src/main/java/com/ghatana/yappc/agent/YAPPCAgentBase.java`

- Remove deprecated static `globalAepEventPublisher` setter (and all callers of `YAPPCAgentBase.setGlobalAepEventPublisher(...)`)
- Add constructor param `EventCloud eventCloud`
- All episode/pattern events published through `eventCloud.append()` (gRPC)
- Update all 36 agent constructions in `YappcAgentSystem.bootstrapSdlcAgents()` and `loadAgentDefinitions()`

### Step 9.3 — Connect REFLECT to AEP Pattern Detection _(depends on 9.1)_

In `YAPPCAgentBase.reflect()`, after `memory.storePolicy()`:

```java
eventCloud.append(tenantId, "pattern.learning", Map.of(
    "agentId", agentContext.agentId(),
    "episode",  lastEpisode,
    "policy",   newPolicy
));
// PatternDetectionAgent in AEP receives this → updates OperatorRegistry
// → PatternEngine indexes new procedure for future PERCEIVE reflex matches
```

### Step 9.4 — Activate PERCEIVE Memory Retrieval _(depends on 9.1)_

In `YAPPCAgentBase.perceive()`, replace fire-and-forget calls with bounded reads:

```java
// 1. PatternEngine O(1) reflex match first
Optional<Policy> reflex = patternEngine.match(input);
if (reflex.isPresent() && reflex.get().confidence() >= 0.7) {
    return PerceiveResult.withPolicy(reflex.get());
}
// 2. Fall through to full MemoryPlane query (500ms timeout)
return memoryPlane.queryEpisodes(tenantId, agentId, input)
    .withTimeout(Duration.ofMillis(500))
    .thenApply(PerceiveResult::withEpisodes)
    .exceptionally(__ -> PerceiveResult.empty()); // proceed without memory on timeout
```

### Phase 9 Verification

- [ ] 5-episode sequence on `GenerateTestsSpecialistAgent` → all 5 episodes in `memory_items` table
- [ ] 6th episode's PERCEIVE returns past episodes from PostgreSQL
- [ ] `grep -r "globalAepEventPublisher" src/` returns zero results
- [ ] `pattern.learning` event appears in AEP EventLogStore after REFLECT completes

---

## Phase 10 — GAA Lifecycle & AgentTurnPipeline Hardening

> **Goal**: All 36 SDLC agents run through `AgentTurnPipeline` with resilience. Full `AgentContext` propagation.  
> **Depends on**: Phase 9

### Step 10.1 — Wrap SDLC Agents in `AgentTurnPipeline`

For each of the 36 SDLC specialist agents in `YappcAgentSystem.bootstrapSdlcAgents()`:

```java
agentTurnPipeline.executeWithPolicy(agentContext, input,
    ResiliencePolicy.builder()
        .circuitBreaker(failures: 10, openDuration: 60s, halfOpenPermits: 5)
        .retry(maxAttempts: 5, backoff: EXPONENTIAL, maxWait: 30s)
        .build()
);
```

Resilience4j is already in the AEP build — reuse without adding a new dependency.

### Step 10.2 — Propagate `AgentContext` _(parallel with 10.1)_

In all YAPPC HTTP handlers (`ApiApplication`, `YappcAiService`, `YappcLifecycleService`):

1. Extract `tenantId` from verified JWT claims
2. Extract `traceId` from `X-Trace-Id` header (generate if absent)
3. Build `AgentContext` with both values
4. Pass `AgentContext` through all `executeTurn()` calls — do not re-extract per call

### Phase 10 Verification

- [ ] 11 consecutive failures open circuit breaker on `QualityGuardAgent`; subsequent call returns fast with circuit-open error
- [ ] `tenantId` appears in all Data-Cloud EventLogStore entries (assert via SQL query in integration test)
- [ ] `traceId` propagated through connector-specific metadata (gRPC header / HTTP header) in all calls

---

## Phase 11 — Canonical Workflow Integration

> **Goal**: Wire all 10 workflows from `canonical-workflows.yaml` (a parameterized template) to an execution engine backed by Data-Cloud.  
> **Depends on**: Phase 10

### Step 11.1 — Extend `WorkflowMaterializer`

Pass `products/yappc/config/workflows/canonical-workflows.yaml` through `YamlTemplateEngine` first (agent IDs, retry counts, timeout values are all templated). For each rendered workflow step:

- `agent-dispatch` type → route through `CatalogAgentDispatcher`
- `human-approval` type → route through `ApprovalController`

Register all 10 workflows at YAPPC startup.

### Step 11.2 — Implement `WorkflowRunRepository` _(parallel with 11.1)_

**New file**: `products/data-cloud/src/main/java/com/ghatana/datacloud/workflow/WorkflowRunRepository.java`

Data-Cloud EventLogStore backed:

- `startRun(tenantId, templateId, input)` → appends `WORKFLOW_STARTED` event → returns `runId`
- `completeStep(tenantId, runId, stepId)` → appends `STEP_COMPLETED` event
- `finishRun(tenantId, runId)` → appends `WORKFLOW_FINISHED` event
- `getRunStatus(tenantId, runId)` → materializes state from events

### Step 11.3 — Wire `POST /workflows/:templateId/start` _(depends on 11.1, 11.2)_

1. Load workflow definition from registry
2. Call `WorkflowRunRepository.startRun(tenantId, templateId, input)` → get `runId`
3. Begin first step via `AgentTurnPipeline`
4. Return `{ "runId": "...", "status": "STARTED" }` immediately (async execution continues)

### Step 11.4 — Wire Approval Steps _(depends on 11.3)_

- `human-approval` type publishes `approval.requested` via `EventCloud.append()` (transport-agnostic)
- `PUT /approvals/:id/decide` publishes `approval.decided`
- AEP `YappcIntegrationModule` subscription to `approval.decided` resumes workflow at the next step

### Phase 11 Verification

- [ ] Start `new-feature` workflow → all 5 steps complete including approval gate (`approval.decided` received within 10s in test)
- [ ] `GET /workflows/runs/:runId/status` returns correct materialized state at each step
- [ ] All 10 workflow templates resolvable at startup via `WorkflowMaterializer`
- [ ] Changing a timeout value in `values/workflows-prod.yaml` and restarting reflects the new value without any Java change

---

## Phase 12 — Testing, Observability & DLQ Hardening

> **Parallel with**: Phase 11

### Integration Tests (all extend `EventloopTestBase`)

| Test Class                           | What It Validates                                                                                                                                     |
| ------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| `EventCloudConnectorIntegrationTest` | Both `GrpcEventCloudConnector` and `HttpEventCloudConnector`: `append()` → `AppendResult` with non-zero offset; switching via `EVENT_CLOUD_TRANSPORT` |
| `YamlTemplateEngineTest`             | Template rendering; unknown param → error; `extends` chain; circular extends → error                                                                  |
| `SchemaRegistryIntegrationTest`      | Seed from bundle; validate valid/invalid payloads; BACKWARD compatibility enforcement                                                                 |
| `YappcAepIntegrationTest`            | Publish `phase.transition` → assert `PHASE_ADVANCED` in EventLogStore within 5s                                                                       |
| `AgentDispatchIntegrationTest`       | Send `test.failed` → lazy-creates `debug-orchestrator` → episode written to Data-Cloud                                                                |
| `WorkflowRunIntegrationTest`         | Start `bug-fix` workflow (from parameterized YAML) → all 3 steps complete; run status correct                                                         |
| `MemoryPlaneIntegrationTest`         | 5-episode sequence → 6th PERCEIVE returns past episodes with < 500ms latency                                                                          |

All tests use `TestDataBuilders`. gRPC tests use in-process transport (`InProcessChannelBuilder`). HTTP tests use `MockWebServer`.

### DLQ Monitoring

- Wire `lifecycle.management.dlq` and `agent.orchestration.dlq` to `AlertManager`
- Add alert rules to [alert-rules.yml](../../alert-rules.yml):
  - `LifecycleDlqDepthHigh` — fires if DLQ depth > 100 for 5m
  - `AgentOrchestrationDlqHigh` — fires if DLQ depth > 50 for 5m
  - Include full error context (schema validation failure reason, operator ID, tenantId) in DLQ event payload

### Micrometer / OpenTelemetry

- Confirm `AepObservabilityModule` exports to Prometheus endpoint at `/metrics`
- Add YAPPC-specific metrics:

| Metric                              | Type      | Labels                                       |
| ----------------------------------- | --------- | -------------------------------------------- |
| `lifecycle_phase_transitions_total` | Counter   | `tenantId`, `fromPhase`, `toPhase`, `status` |
| `workflow_runs_active`              | Gauge     | `tenantId`, `templateId`                     |
| `agent_dispatch_failures_total`     | Counter   | `tenantId`, `agentId`, `reason`              |
| `schema_validation_failures_total`  | Counter   | `schemaId`, `version`, `eventType`           |
| `llm_request_duration_seconds`      | Histogram | `tenantId`, `provider`, `agentId`            |

### JavaDoc + @doc Tags

Every new public class must have all 4 required tags:

```java
/**
 * @doc.type class
 * @doc.purpose [One-line description]
 * @doc.layer [core|product|platform]
 * @doc.pattern [Service|Repository|ValueObject|EventSourced|etc]
 * @doc.gaa.memory [episodic|semantic|procedural|preference]  ← if memory-related
 * @doc.gaa.lifecycle [perceive|reason|act|capture|reflect]   ← if lifecycle-related
 */
```

---

## Phase Dependency Graph

```
Phase 1 (AEP Hardening + EventCloudConnector SPI)
    │
    ├── Phase 2 (Schema Registry)
    │       │
    │       └──── Phase 3 (Parameterized YAML Templates)
    │                   │
    │                   └── Phase 4 (Operator Catalog)
    │                           │
    │                           └── Phase 5 (Event Routing Bridge)
    │                                   │
    │                                   └── Phase 6 (YAPPC Pipeline Operators)
    │                                           │
    │                               ┌───────────┴───────────┐
    │                               │                       │
    │                           Phase 7                 Phase 8
    │                       (Lifecycle Svc)       (Agent Catalog / LLM)
    │                               │                       │
    │                               └───────────┬───────────┘
    │                                           │
    │                                       Phase 9
    │                                  (Durable Memory)
    │                                           │
    │                                       Phase 10
    │                                   (GAA Hardening)
    │                                           │
    │                           ┌───────────────┴──────────────┐
    │                           │                              │
    │                       Phase 11                      Phase 12
    │                     (Workflows)               (Testing / Obs)
    │
    └── (Phases 11 and 12 run in parallel)
```

**Critical path**: Phase 1 → 2 → 3 → 4 → 5 → 6 → 9 → 10 → 11

---

## End-to-End Verification Checklist

### Static Checks

- [ ] `./gradlew checkstyleMain pmdMain` — zero warnings across all changed modules
- [ ] `./gradlew spotlessApply && git diff --exit-code` — no formatting diffs
- [ ] `grep -r "localhost" products/aep/platform/src/main/java/` — zero results in `AepConnectorModule`
- [ ] `grep -r "HttpAepEventPublisher\|AepEventBridge" src/` — zero results everywhere
- [ ] `grep -r "claude-3\|gpt-4\|llama3\|mistral" src/` — zero results in Java files
- [ ] `grep -r "ConcurrentHashMap" products/aep/platform/src/main/java/com/ghatana/orchestrator/` — zero results
- [ ] `grep -r "not_implemented" products/yappc/` — zero results
- [ ] `grep -rn "{{ " platform/java/yaml-template/src/test/` — template test coverage present

### Automated Tests

- [ ] `./gradlew test` — all new `EventloopTestBase` tests pass (including gRPC in-process transport)
- [ ] All 6 integration test classes run green (see Phase 11)

### Startup Smoke Tests

- [ ] All 4 YAPPC services + AEP start successfully with real env vars
- [ ] `OPENAI_API_KEY` unset and `OLLAMA_HOST` unset → service exits with clear `IllegalStateException` message
- [ ] `EVENT_CLOUD_TRANSPORT=grpc` with `EVENT_CLOUD_GRPC_HOST` unset → throws at module boot
- [ ] `EVENT_CLOUD_TRANSPORT=http` with `EVENT_CLOUD_HTTP_BASE_URL` unset → throws at module boot
- [ ] `EVENT_CLOUD_TRANSPORT=unknown` → throws listing known connector types

### Functional Round-Trip

- [ ] `POST /lifecycle/phase/advance` → observe AEP pipeline execution in Prometheus (`lifecycle_phase_transitions_total` increments)
- [ ] Verify `PHASE_ADVANCED` event in PostgreSQL `event_log` table with correct `tenantId`
- [ ] `GET /api/v1/ai/agents` returns exactly 590 entries
- [ ] Start `new-feature` canonical workflow → all steps complete → `WORKFLOW_FINISHED` in EventLogStore

### Resilience / Chaos

- [ ] Kill Data-Cloud mid-pipeline → checkpoint recovery restores AEP pipeline state in < 5s
- [ ] Inject invalid schema event → event lands in `lifecycle.management.dlq` with error context → `LifecycleDlqDepthHigh` alert fires in AlertManager
- [ ] Inject 11 consecutive `QualityGuardAgent` failures → circuit breaker opens → 12th call returns fast with circuit-open error

### Multi-Tenancy Isolation

- [ ] Tenant A event never appears in Tenant B's EventLogStore subscription
- [ ] Agent dispatch for Tenant A never creates `AgentInstance` in Tenant B's weak-ref cache

---

## Further Considerations

### 1. Ollama Local Development Path

`AiServiceModule` will fail fast if no cloud provider API key is present. To support local development without cloud credentials, the `OLLAMA_HOST` env var enables the Ollama path. When `OLLAMA_HOST` is set, `OllamaCompletionService` is registered as the sole provider without error. This provides a viable local development mode — real inference, not stubs, no cloud cost.

Order of precedence for LLM provider resolution:

1. `OLLAMA_HOST` set → use Ollama (local/dev)
2. `OPENAI_API_KEY` set → use OpenAI
3. `ANTHROPIC_API_KEY` set → use Anthropic
4. None set → `IllegalStateException`

Multiple providers can be combined; `DefaultLLMGateway.addRoute()` maps per-agent model → provider.

### 2. Connector-Specific Security

Each `EventCloudConnector` implementation enforces its own transport security:

**gRPC connector** (`GrpcEventCloudConnector`):

- `EVENT_CLOUD_GRPC_TLS_CERT` + `EVENT_CLOUD_GRPC_TLS_KEY` set → `SslContextBuilder` with mTLS
- Neither set and `APP_ENV != development` → `IllegalStateException` at boot
- Dev/test: `InProcessChannelBuilder` bypasses TLS entirely

**HTTP connector** (`HttpEventCloudConnector`):

- `EVENT_CLOUD_HTTP_BASE_URL` must start with `https://` in non-development environments
- If plain `http://` and `APP_ENV != development` → `IllegalStateException` at boot
- Supports bearer token auth via `EVENT_CLOUD_HTTP_AUTH_TOKEN`

This contract prevents accidental plain-text transport in production regardless of which connector is active.

### 3. Schema Version Lifecycle & Compatibility Modes

`DataCloudSchemaRegistry` materializes the latest `schema.registered` event per `schemaId`. When a proto contract in `platform/contracts` bumps a schema version, in-flight events from the old version must still be valid. The `registerSchema(..., CompatibilityMode)` overload enforces:

| Mode       | Rule                                                                      |
| ---------- | ------------------------------------------------------------------------- |
| `BACKWARD` | New schema can read data written by old schema (adding optional fields)   |
| `FORWARD`  | Old schema can read data written by new schema (removing optional fields) |
| `FULL`     | Both BACKWARD and FORWARD — safest for long-running subscriptions         |

Default: `BACKWARD` for all AEP operator schemas. Pipelines declare their required compatibility mode in the operator YAML. `SchemaBootstrapper.seedFromBundle()` uses `BACKWARD` for all proto-seeded schemas unless overridden.

Breaking changes are blocked at registration time with a clear `SchemaCompatibilityException`, not discovered at event validation time when recovery is harder.

---

_End of Plan — YAPPC + AEP Full Integration v1.1.0_

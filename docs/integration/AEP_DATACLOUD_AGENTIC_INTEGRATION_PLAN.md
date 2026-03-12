# AEP + Data-Cloud + Agentic Framework — Full Integration & UI/UX Plan

> **Version**: 2.1.0
> **Status**: Approved for Implementation
> **Date**: 2026-03-12
> **Replaces**: v2.0.0 (rescoped: YAPPC internal concerns removed; platform boundary clarified)
> **Scope**: Platform integration of AEP, Data-Cloud, and Agent Framework. YAPPC and other
> consumer products are explicitly **out of scope** except when promoting generic capabilities
> to the platform.

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Boundary Definition](#2-boundary-definition)
3. [Guiding Principles (Non-Negotiable)](#3-guiding-principles-non-negotiable)
4. [Architecture Overview](#4-architecture-overview)
5. [Current State & Gap Analysis](#5-current-state--gap-analysis)
6. [Canvas Strategy — Shared `@ghatana/flow-canvas`](#6-canvas-strategy--shared-ghatanaflow-canvas)
7. [Phase A — Backend Infrastructure Hardening](#phase-a--backend-infrastructure-hardening)
8. [Phase B — Generic Capabilities Promoted to Platform](#phase-b--generic-capabilities-promoted-to-platform)
9. [Phase C — AEP UI/UX Total Overhaul](#phase-c--aep-uiux-total-overhaul)
10. [Phase D — Data-Cloud UI/UX Expansion](#phase-d--data-cloud-uiux-expansion)
11. [Phase E — Unified Platform Shell & Cross-Cutting](#phase-e--unified-platform-shell--cross-cutting)
12. [Phase Dependency Graph](#12-phase-dependency-graph)
13. [New Files to Create](#13-new-files-to-create)
14. [Files to Modify](#14-files-to-modify)
15. [End-to-End Verification Checklist](#15-end-to-end-verification-checklist)
16. [Architectural Decisions Reference](#16-architectural-decisions-reference)

---

## 1. Executive Summary

AEP (Agentic Event Processor), Data-Cloud, and the Agent Framework currently operate as
disconnected silos. AEP has a functional pipeline engine with hardcoded connector config,
in-memory orchestrator state, and a single-page UI. Data-Cloud provides a robust
event-sourcing and storage platform but exposes minimal UI. The GAA (Generic Adaptive Agent)
framework exists in `platform/java/agent-framework` but its lifecycle loop is not fully wired
through AEP in production.

This plan delivers **platform-level** integration across these three systems:

1. **Backend hardening** — pluggable event transport (gRPC by default), durable event-sourced
   orchestrator state, env-driven config, Schema Registry, YAML template engine.
2. **Generic capabilities promoted to platform** — `AgentDefinitionLoader` as a platform class
   in `platform/java/event-cloud`; `PersistentMemoryPlane` backed by `JdbcMemoryItemRepository` (pgvector);
   `@ghatana/flow-canvas` extracted from YAPPC canvas internals.
3. **AEP UI overhaul** — seven new pages (Agent Registry, Pattern Studio, HITL Review Queue,
   Monitoring Dashboard, Memory Explorer) plus a redesigned Pipeline Builder; all backed by
   live data, real-time SSE, and drag-and-drop DAG canvas.
4. **Data-Cloud UI expansion** — five feature areas (Event Explorer, Memory Plane Viewer,
   redesigned Entity Browser, Data Fabric View, Workflow Canvas) replacing 4 partial stubs.
5. **Shared canvas** — extract reusable DAG/flow components from `@ghatana/yappc-canvas` into
   `platform/typescript/flow-canvas/` as `@ghatana/flow-canvas`; AEP UI and Data-Cloud UI
   both consume it.
6. **Unified shell** — single platform navigation shell, tenant selector, and observability
   bridge shared across AEP and Data-Cloud.

**YAPPC and other consumer products** will benefit from all of the above automatically, as
platform consumers. Their internal concerns (security fixes, lifecycle stubs, config loading
for 590 agents across 17 domain catalogs) are not tracked in this plan.

---

## 2. Boundary Definition

This section is the **authoritative scope boundary** for this plan.

| Category                                              | In Scope                                    | Out of Scope                                                                                                                       |
| ----------------------------------------------------- | ------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| **AEP** (`products/aep/`)                             | All backend + UI work                       | —                                                                                                                                  |
| **Data-Cloud** (`products/data-cloud/`)               | All backend + UI work                       | —                                                                                                                                  |
| **Agent Framework** (`platform/java/agent-framework`) | All platform-level classes                  | —                                                                                                                                  |
| **YAPPC** (`products/yappc/`)                         | Extracting generic capabilities TO platform | Internal security fixes, lifecycle stub, config loading for YAPPC's 590 agents across 17 domain catalogs, YAPPC-specific operators |
| **Other consumer products**                           | None                                        | All internal concerns                                                                                                              |

### Decision Rule

> If a change lives **entirely within** `products/yappc/` and does not produce a reusable
> artifact consumed by other products or platform modules, it belongs in a **YAPPC-specific
> plan**, not here.

> If a concept **originates in YAPPC** but is being generalised and moved to `platform/` or
> `libs/`, it belongs in **Phase B** of this plan.

---

## 3. Guiding Principles (Non-Negotiable)

1. **Platform-first** — all new abstractions go in `platform/` or `libs/`; products only
   contain product-specific wiring.
2. **Event-sourced state** — all orchestrator/agent state mutations are events first;
   projections are secondary.
3. **Pluggable transports** — no hardcoded `localhost:*`; all external connections via SPI +
   env config.
4. **ActiveJ only** — no Spring Reactor, no CompletableFuture in concurrent paths.
5. **Type safety** — no `any` in TypeScript; no raw generic types in Java.
6. **Shared canvas** — all DAG/flow UI components go in `@ghatana/flow-canvas`; products wrap
   them, not re-implement them.

---

## 4. Architecture Overview

```
┌────────────────────────────────────────────────────────────────────┐
│                         Consumer Products                           │
│  ┌──────────┐  ┌────────────┐  ┌───────────┐  ┌──────────────┐   │
│  │  YAPPC   │  │ TutorPutor │  │  Flashit  │  │   Others…    │   │
│  └────┬─────┘  └─────┬──────┘  └─────┬─────┘  └──────┬───────┘   │
└───────┼──────────────┼───────────────┼────────────────┼───────────┘
        │              │               │                │
        ▼              ▼               ▼                ▼
┌────────────────────────────────────────────────────────────────────┐
│                          Platform Layer                             │
│                                                                     │
│  ┌───────────────────────────┐   ┌──────────────────────────────┐  │
│  │           AEP             │   │          Data-Cloud           │  │
│  │  PipelineExecutionEngine  │◄──┤  EventLogStore SPI            │  │
│  │  UnifiedOperator base     │   │  EntityStore                  │  │
│  │  AIAgentOrchestrator      │   │  Tier: Redis→PG→Iceberg→S3   │  │
│  └──────────────┬────────────┘   └────────────────┬─────────────┘  │
│                 │                                  │               │
│                 ▼                                  ▼               │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │             Agent Framework  (platform/java)                 │   │
│  │  BaseAgent · AgentTurnPipeline · AgentDefinition             │   │
│  │  MemoryPlane · AgentDefinitionLoader · AgentRegistryService  │   │
│  └─────────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────────┘
        ▲
        │  shared UI components
┌────────────────────────────────────────────────────────────────────┐
│              platform/typescript/flow-canvas                        │
│   DAGCanvas · PipelineNode · EdgeRenderer · LayoutEngine           │
└────────────────────────────────────────────────────────────────────┘
```

---

## 5. Current State & Gap Analysis

### 5.1 AEP Gaps

| Component                         | Current State                                   | Gap                                                       |
| --------------------------------- | ----------------------------------------------- | --------------------------------------------------------- |
| `EventLogStoreBackedEventCloud`   | Transport hardcoded to `localhost:6379` (Redis) | Needs SPI so gRPC, Kafka, Redis swap without code changes |
| `AIAgentOrchestrationManagerImpl` | Uses `ConcurrentHashMap` for run state          | Not event-sourced; state lost on restart                  |
| `AepConnectorModule`              | All connector URLs hardcoded                    | Must be env-driven                                        |
| `InMemoryCheckpointStorage`       | In-memory only                                  | Needs `PostgresCheckpointStorage`                         |
| `AepSchemaRegistry`               | Stub / not wired                                | Needs real enforcement                                    |
| AEP UI                            | Single `PipelineBuilderPage`                    | 5 additional pages needed                                 |

### 5.2 Data-Cloud Gaps

| Component           | Current State                    | Gap                                                              |
| ------------------- | -------------------------------- | ---------------------------------------------------------------- |
| `EventLogStore` SPI | Defined; Redis (HOT) impl exists | Needs PostgreSQL WARM tier impl                                  |
| `EntityStore`       | Functional                       | UI is 4 partial stubs                                            |
| `DataCloudClient`   | Functional                       | No schema validation on ingest                                   |
| Data-Cloud UI       | 4 partial stubs                  | Need Event Explorer, Memory Viewer, Fabric view, Workflow Canvas |

### 5.3 Agent Framework Gaps

| Component               | Current State                  | Gap                                                                                   |
| ----------------------- | ------------------------------ | ------------------------------------------------------------------------------------- |
| `AgentRegistryService`  | Lacks `tenantId` parameter     | Multi-tenancy not enforced                                                            |
| `EventLogMemoryStore`   | In-memory                      | Needs durable `PersistentMemoryPlane` backed by `JdbcMemoryItemRepository` (pgvector) |
| `AgentDefinitionLoader` | Does not exist                 | Platform class needed to load YAML/JSON definitions                                   |
| `MemoryPlane`           | 7 tiers defined, not all wired | Full wiring needed                                                                    |

---

## 6. Canvas Strategy — Shared `@ghatana/flow-canvas`

YAPPC's `products/yappc/frontend/libs/canvas/` contains a fully implemented DAG canvas using
`@xyflow/react` v12, dagre layout, yjs CRDT collaboration, and jotai state. These capabilities
are useful to AEP UI and Data-Cloud UI, but they are embedded in YAPPC.

**Target**: extract the generic DAG parts into `platform/typescript/flow-canvas/` as
`@ghatana/flow-canvas`. YAPPC's `@ghatana/yappc-canvas` becomes a thin wrapper around
`@ghatana/flow-canvas` that adds YAPPC-specific node types and styling.

| Component                      | Source                     | Target                          |
| ------------------------------ | -------------------------- | ------------------------------- |
| `DAGCanvas` core               | `yappc-canvas/src/canvas/` | `flow-canvas/src/canvas/`       |
| `PipelineNode`, `EdgeRenderer` | `yappc-canvas/src/nodes/`  | `flow-canvas/src/nodes/`        |
| dagre layout engine            | `yappc-canvas/src/layout/` | `flow-canvas/src/layout/`       |
| yjs collaboration layer        | `yappc-canvas/src/collab/` | `flow-canvas/src/collab/`       |
| YAPPC-specific node types      | stay in `yappc-canvas/`    | `yappc-canvas/src/yappc-nodes/` |

---

## Phase A — Backend Infrastructure Hardening

**Objective**: Remove all hardcoded configuration, make event transport pluggable, make
orchestrator state durable, enforce schema validation at ingest.

### A.1 EventCloud SPI (AEP)

**Problem**: `EventLogStoreBackedEventCloud` has Redis hardcoded at `localhost:6379`.

**Solution**: Introduce `EventCloudConnector` SPI; move Redis impl behind it; provide gRPC
impl as default.

```java
// platform/java/event-cloud/src/main/java/com/ghatana/core/event/cloud/connector/EventCloudConnector.java
/**
 * @doc.type interface
 * @doc.purpose SPI for event cloud transport — gRPC or HTTP. Selected by EVENT_CLOUD_TRANSPORT env var.
 * @doc.layer platform
 * @doc.pattern SPI
 */
public interface EventCloudConnector {
    Promise<Void> publish(String topic, byte[] payload);
    Promise<EventStream> subscribe(String topic, String consumerGroup);
}
```

`AepConnectorModule` binds the active implementation from env:
`EVENT_CLOUD_TRANSPORT=grpc|http` (default: `grpc`).

### A.2 Event-Sourced Orchestrator State (AEP)

**Problem**: `AIAgentOrchestrationManagerImpl` uses `ConcurrentHashMap<String, AgentRunState>`
— state lost on crash.

**Solution**: Replace map with event-sourced writes to `EventLogStore`; restore from log on
startup.

```java
// Key change in AIAgentOrchestrationManagerImpl:
// BEFORE: state.put(agentId, newState);
// AFTER:
private Promise<Void> persistState(String agentId, AgentRunState newState) {
    AgentStateChangedEvent event = AgentStateChangedEvent.of(agentId, newState, Instant.now());
    return Promise.ofBlocking(executor,
        () -> eventLogStore.append(AGENT_STATE_STREAM, event));
}
```

### A.3 PostgreSQL Checkpoint Storage (AEP)

Replace `InMemoryCheckpointStorage` with `PostgresCheckpointStorage` using the `libs:database`
module (JDBC, no raw SQL strings to prevent injection).

### A.4 Environment-Driven Config (AEP)

All `AepConnectorModule` bindings read from environment variables with sensible defaults:

| Config Key                | Default                           | Description               |
| ------------------------- | --------------------------------- | ------------------------- |
| `AEP_REDIS_URI`           | `redis://localhost:6379`          | Redis for hot event store |
| `AEP_GRPC_ENDPOINT`       | `localhost:50051`                 | gRPC event transport      |
| `AEP_PG_URL`              | `jdbc:postgresql://localhost/aep` | Checkpoint + state store  |
| `AEP_SCHEMA_REGISTRY_URL` | `http://localhost:8081`           | Schema Registry           |

### A.5 Schema Registry (AEP)

Wire `AepSchemaRegistry` to a real Confluent-compatible registry. Events without registered
schemas are rejected at ingest with a structured error response (no raw exception propagation).

### A.6 YAML Template Engine (AEP)

Integrate `YamlTemplateEngine` into the pipeline builder. Pipelines are describable as YAML
and can reference operator templates from the operator catalog.

**Deliverables**: 6 Java class changes, 3 new classes; all covered by `EventloopTestBase`
tests.

---

## Phase B — Generic Capabilities Promoted to Platform

**Objective**: Identify capabilities that originated in YAPPC (or exist as stubs elsewhere)
that are valuable across all products, and promote them to `platform/`.

### B.1 `AgentDefinitionLoader` as Platform Class

**Origin**: The concept is needed by YAPPC (590 agents across 17 domain catalogs) but is product-agnostic. Any
product that declares agent definitions in YAML should be able to load them the same way.

**Target**: `platform/java/agent-framework/.../loader/AgentDefinitionLoader.java`

```java
/**
 * @doc.type class
 * @doc.purpose Loads AgentDefinition objects from YAML/JSON classpath or filesystem resources.
 * @doc.layer platform
 * @doc.pattern Service
 */
public class AgentDefinitionLoader {

    /** Load all definitions under the given classpath prefix. */
    public Promise<List<AgentDefinition>> loadFromClasspath(String prefix) { ... }

    /** Load all definitions from a filesystem directory. */
    public Promise<List<AgentDefinition>> loadFromDirectory(Path dir) { ... }
}
```

**Impact**: Any product (YAPPC, AEP itself, TutorPutor) can load agent definitions using this
platform class without re-implementing YAML parsing. Products do not import each other.

### B.2 `PersistentMemoryPlane` as Platform Class

**Origin**: `EventLogMemoryStore` is in-memory; durable, event-sourced memory is needed
platform-wide for GAA agents.

**Target**: `platform/java/agent-framework/.../memory/PersistentMemoryPlane.java`

```java
/**
 * @doc.type class
 * @doc.purpose Durable MemoryPlane backed by JdbcMemoryItemRepository with pgvector semantic search.
 * @doc.layer platform
 * @doc.pattern EventSourced
 * @doc.gaa.memory episodic
 * @doc.gaa.lifecycle capture
 */
public class PersistentMemoryPlane implements MemoryPlane {
    private final JdbcMemoryItemRepository repository; // pgvector(1536) column for semantic search
    private final Executor blockingExecutor;

    @Override
    public Promise<Void> store(MemoryEntry entry) {
        return Promise.ofBlocking(blockingExecutor,
            () -> repository.save(entry.agentId(), entry));
    }

    @Override
    public Promise<List<MemoryEntry>> recall(String agentId, MemoryQuery query) {
        return Promise.ofBlocking(blockingExecutor, () ->
            query.isSemanticSearch()
                ? repository.semanticSearch(agentId, query.embedding(), query.limit())
                : repository.findByAgentId(agentId, query));
    }
}
```

> **Note**: backing store is `JdbcMemoryItemRepository` (JDBC + pgvector `vector(1536)` column),
> NOT `EventLogStore`. This allows O(log n) semantic similarity search via `<=>` operator.
> Schema defined in `platform/java/agent-memory/src/main/resources/db/migration/V1__memory_plane.sql`.

### B.3 `@ghatana/flow-canvas` Extraction

See [Section 6](#6-canvas-strategy--shared-ghatanaflow-canvas) for full extraction mapping.

**Source**: `products/yappc/frontend/libs/canvas/`
**Target**: `platform/typescript/flow-canvas/`
**Package**: `@ghatana/flow-canvas`

Steps:

1. Create `platform/typescript/flow-canvas/` with `package.json`, `tsconfig.json`,
   `vite.config.ts`.
2. Move generic components (DAGCanvas, PipelineNode, EdgeRenderer, layout engine, yjs collab)
   stripping YAPPC-specific node type assumptions.
3. YAPPC's `@ghatana/yappc-canvas` re-exports + extends `@ghatana/flow-canvas` with
   YAPPC-specific node types — **zero behaviour regression** for YAPPC.
4. AEP UI and Data-Cloud UI import `@ghatana/flow-canvas` directly.

**Deliverables**: 1 new platform TypeScript package, YAPPC canvas converted to a thin
wrapper, all existing YAPPC canvas tests continue to pass.

---

## Phase C — AEP UI/UX Total Overhaul

**Objective**: Expand AEP frontend from one page to a full six-page application. All pages use
real-time SSE and the shared `@ghatana/flow-canvas`.

### C.1 Pipeline Builder (Redesign)

The existing `PipelineBuilderPage` is rebuilt on top of `@ghatana/flow-canvas`.

- **DAG canvas**: drag-and-drop nodes from an operator catalog sidebar; auto-layout via dagre.
- **Operator config panel**: right-side drawer with schema-validated YAML/JSON form per
  operator type.
- **Real-time validation**: SSE stream from AEP backend reports validation errors inline.
- **Save as YAML**: pipeline serialised to YAML template via `YamlTemplateEngine`.

### C.2 Agent Registry Page (`/aep/agents`)

Lists all agents registered in `AgentRegistryService` for the current tenant.

| Column       | Source                                    |
| ------------ | ----------------------------------------- |
| Agent ID     | `AgentDefinition.id()`                    |
| Display Name | `AgentDefinition.displayName()`           |
| Status       | `AgentInstance` runtime status            |
| Last Active  | Event log timestamp                       |
| Actions      | View config · Force restart · View memory |

- **Filter bar**: lifecycle status, capability tag, last-active window.
- **Agent detail drawer**: `AgentDefinition` YAML, current `AgentInstance` overrides, last
  20 episodic memory entries.

### C.3 Pattern Studio Page (`/aep/patterns`)

Visual editor for `PolicyProcedure` / pattern definitions in the learning system.

- **Monaco editor** (YAML mode) with JSON Schema validation against `AgentPattern` schema.
- **Live preview pane**: renders the pattern as a decision-tree diagram.
- **Confidence badges**: colour-coded (green ≥ 0.9, yellow ≥ 0.7, red < 0.7 — flagged for
  human review per GAA spec).
- **Publish action**: submits the pattern to `AgentLearningService` via REST.

### C.4 Workflow Catalog Page (`/aep/workflows`)

Browsable catalog of all pipeline/workflow templates managed by `YamlTemplateEngine`.

- **Card grid**: template name, operator count, last modified date.
- **Instantiate**: opens Pipeline Builder with template pre-loaded.
- **Version history**: timeline of template versions with diff view.

### C.5 Monitoring Dashboard (`/aep/monitoring`)

Real-time observability for running pipelines and agents.

- **Pipeline run table**: status, operator count, throughput, p99 latency — via SSE.
- **Agent health grid**: one tile per running agent, coloured by health score.
- **Event rate sparklines**: per-topic event rate over the last 5 min (from Data-Cloud).
- **Error log stream**: live tail of error events with operator context.

### C.6 Memory Explorer Page (`/aep/memory`)

Browse and search agent memory backed by `PersistentMemoryPlane`.

- **Timeline view**: episodic memories in chronological order per agent.
- **Semantic search bar**: full-text search across semantic memory tier.
- **Procedural policy list**: all policies with confidence scores and approval status.

---

## Phase D — Data-Cloud UI/UX Expansion

**Objective**: Replace 4 partial stubs with a full-featured data platform UI across 5 areas.

### D.1 Event Explorer (`/data-cloud/events`)

Browse, filter, and replay events from `EventLogStore`.

- **Topic selector**: tree view of all topics/streams.
- **Event table**: infinite scroll; JSON payload preview on row click.
- **Filter toolbar**: time range, event type, producer ID, schema version.
- **Replay**: re-emit selected events to a target topic.
- **Schema badge**: each row shows schema version; click to view in Schema Registry.

### D.2 Memory Plane Viewer (`/data-cloud/memory`)

View the 7-tier `MemoryPlane` contents for any agent/tenant.

- **Tier selector tabs**: Episodic · Semantic · Procedural · Preference · Working · Sensory ·
  Archive.
- **Entry table**: timestamp, agent ID, content preview, confidence score.
- **Diff view**: compare two memory state snapshots side-by-side.

### D.3 Entity Browser (Redesign)

The existing 4 stub pages are merged into a unified entity browser.

- **Left rail**: entity type tree (Event, Agent, Pipeline, Checkpoint, Schema).
- **Main panel**: inline table with column customisation.
- **Detail panel**: right drawer with full entity JSON + related entities.
- **Bulk operations**: export CSV, delete selected, re-index.

### D.4 Data Fabric View (`/data-cloud/fabric`)

Visual map of the storage tier topology, built on `@ghatana/flow-canvas`.

- **Tier flow diagram**: HOT Redis → WARM PostgreSQL → COOL Iceberg → COLD S3.
- **Live metrics overlay**: bytes/s, events/s, tier fill % per node.
- **Migration triggers**: manual tier migration for specific streams.

### D.5 Workflow Canvas (`/data-cloud/workflows`)

Visual editor for Data-Cloud ingestion/processing pipelines, built on `@ghatana/flow-canvas`.

- **Node types**: Source · Transform · Enrich · Sink.
- **Node config**: schema, filter expression, transformation script per node.

---

## Phase E — Unified Platform Shell & Cross-Cutting

**Objective**: Single navigation shell and shared infrastructure for AEP and Data-Cloud
frontends.

### E.1 Unified Navigation Shell

```
┌──────────────────────────────────────────────────────────────┐
│  [Ghatana Logo]  AEP ▾  Data-Cloud ▾  [Tenant Selector]     │
│                              [User]  [Settings] [Notifs]     │
└──────────────────────────────────────────────────────────────┘
```

- Shell lives in `platform/typescript/platform-shell/`.
- Both AEP and Data-Cloud frontend apps mount inside the shell via Module Federation.
- Tenant selector propagates `tenantId` via Jotai context to all child micro-frontends.

### E.2 Shared Auth Context

- `useAuth()` hook from `@ghatana/platform-shell` provides `accessToken`, `tenantId`, `userId`.
- All API calls from AEP UI and Data-Cloud UI attach the token automatically via a fetch
  interceptor.

### E.3 Observability Bridge

- `MetricsDashboard` component embeds a Grafana iframe scoped to the current tenant.
- OpenTelemetry trace IDs are surfaced in the browser console for debugging.

### E.4 Notification Centre

- SSE stream from `/platform/notifications`.
- Agent lifecycle events, pipeline failures, schema conflicts surface as toast notifications.

---

## 12. Phase Dependency Graph

```
Phase A  (Backend Hardening)
    │
    ├──► Phase B  (Generic → Platform)
    │         │
    │         └──► @ghatana/flow-canvas ready
    │
    ├──► Phase C  (AEP UI) ───────────► needs flow-canvas (B.3) + Phase A APIs
    │
    ├──► Phase D  (Data-Cloud UI) ────► needs flow-canvas (B.3) + Phase A APIs
    │
    └──► Phase E  (Unified Shell) ────► needs Phase C + D complete
```

---

## 13. New Files to Create

### Java — Platform

| File                             | Package                             | Purpose                     |
| -------------------------------- | ----------------------------------- | --------------------------- |
| `EventCloudConnector.java`       | `com.ghatana.aep.event.spi`         | SPI for event transport     |
| `GrpcEventCloudConnector.java`   | `com.ghatana.aep.event.grpc`        | gRPC implementation         |
| `PostgresCheckpointStorage.java` | `com.ghatana.aep.checkpoint`        | Durable checkpoint store    |
| `AgentDefinitionLoader.java`     | `com.ghatana.agentframework.loader` | Platform YAML/JSON loader   |
| `PersistentMemoryPlane.java`     | `com.ghatana.agentframework.memory` | EventLogStore-backed memory |

### TypeScript — Platform

| Package                   | Path                                  | Purpose                         |
| ------------------------- | ------------------------------------- | ------------------------------- |
| `@ghatana/flow-canvas`    | `platform/typescript/flow-canvas/`    | Extracted DAG canvas components |
| `@ghatana/platform-shell` | `platform/typescript/platform-shell/` | Unified navigation shell        |

### AEP Frontend Pages

| File                          | Route             | Purpose                   |
| ----------------------------- | ----------------- | ------------------------- |
| `AgentRegistryPage.tsx`       | `/aep/agents`     | Agent registry browser    |
| `PatternEditorPage.tsx`       | `/aep/patterns`   | Pattern/policy editor     |
| `WorkflowCatalogPage.tsx`     | `/aep/workflows`  | Workflow template catalog |
| `MonitoringDashboardPage.tsx` | `/aep/monitoring` | Real-time monitoring      |
| `MemoryExplorerPage.tsx`      | `/aep/memory`     | Agent memory browser      |

### Data-Cloud Frontend Pages

| File                        | Route                   | Purpose                |
| --------------------------- | ----------------------- | ---------------------- |
| `EventExplorerPage.tsx`     | `/data-cloud/events`    | Event log browser      |
| `MemoryPlaneViewerPage.tsx` | `/data-cloud/memory`    | Memory tier viewer     |
| `EntityBrowserPage.tsx`     | `/data-cloud/entities`  | Unified entity browser |
| `DataFabricPage.tsx`        | `/data-cloud/fabric`    | Storage tier topology  |
| `WorkflowCanvasPage.tsx`    | `/data-cloud/workflows` | Data pipeline canvas   |

---

## 14. Files to Modify

### Java

| File                                   | Change                                                 |
| -------------------------------------- | ------------------------------------------------------ |
| `EventLogStoreBackedEventCloud.java`   | Replace hardcoded Redis with `EventCloudConnector` SPI |
| `AIAgentOrchestrationManagerImpl.java` | Replace `ConcurrentHashMap` with event-sourced state   |
| `AepConnectorModule.java`              | Replace all hardcoded URLs with env-driven config      |
| `AepSchemaRegistry.java`               | Wire to real schema registry                           |
| `AgentRegistryService.java`            | Add `tenantId` parameter to all public methods         |
| `EventLogMemoryStore.java`             | Deprecate; replace usages with `PersistentMemoryPlane` |

### TypeScript

| File                                   | Change                                              |
| -------------------------------------- | --------------------------------------------------- |
| `PipelineBuilderPage.tsx`              | Rebuild on `@ghatana/flow-canvas`                   |
| `products/yappc/frontend/libs/canvas/` | Convert to thin wrapper over `@ghatana/flow-canvas` |
| AEP app router                         | Add 5 new page routes (C.2 – C.6)                   |
| Data-Cloud app router                  | Add 5 new page routes (D.1 – D.5)                   |

---

## 15. End-to-End Verification Checklist

### Backend

- [ ] `EventCloudConnector` swaps between gRPC and Redis via env var — tested with `EventloopTestBase`
- [ ] `AIAgentOrchestrationManagerImpl` restores state from `EventLogStore` after simulated restart
- [ ] `PostgresCheckpointStorage` persists and restores checkpoints across process restarts
- [ ] `AgentDefinitionLoader` loads a 5-definition YAML fixture in < 100 ms
- [ ] `PersistentMemoryPlane` round-trips an episodic memory entry through `EventLogStore`
- [ ] `AgentRegistryService` enforces tenant isolation — agent from tenant A not visible to tenant B

### Frontend

- [ ] `@ghatana/flow-canvas` renders a 50-node DAG with dagre layout in < 500 ms
- [ ] YAPPC canvas (thin wrapper) passes all existing visual regression tests
- [ ] AEP Pipeline Builder renders an existing pipeline from YAML
- [ ] Agent Registry page loads 100 agents in < 2 s with virtual scrolling
- [ ] Data-Cloud Event Explorer paginates 10 000 events without memory leak
- [ ] Unified shell tenant switch re-fetches all data with new `tenantId`

---

## 16. Architectural Decisions Reference

| ADR     | Decision                                                                        |
| ------- | ------------------------------------------------------------------------------- |
| ADR-001 | Typed Agent Framework — `AgentDefinition` schema                                |
| ADR-002 | Event-Sourced Memory — append-only `EventLogStore`                              |
| ADR-003 | ActiveJ-only concurrency — no Spring Reactor                                    |
| ADR-004 | Multi-tenancy at all layers                                                     |
| ADR-005 | Pluggable event transport SPI                                                   |
| ADR-011 | YAML-driven agent definition loading _(new — platform `AgentDefinitionLoader`)_ |
| ADR-012 | `@ghatana/flow-canvas` as shared DAG component library _(new)_                  |

# Ghatana Platform — Unified Master Implementation Plan

> **Version**: 2.0.0 | **Status**: Authoritative  
> **Synthesises**: `AEP_DATACLOUD_AGENTIC_INTEGRATION_PLAN.md`, `AEP_COMPLETE_IMPLEMENTATION_PLAN.md`, `DATACLOUD_COMPLETE_IMPLEMENTATION_PLAN.md`, `YAPPC_AEP_INTEGRATION_PLAN.md`  
> **v2.0.0 changes**: Added Section 2.5 (existing deployment architecture); corrected Track 0A (EventCloudConnector → EventLogStore SPI impls); added YAPPC-Ph0.5 (DataCloud client) and YAPPC-Ph0.6 (Outbox relay); fixed YAPPC-Ph1 step 1b and 1h; corrected Zero-Duplication Table; updated all file checklists; removed all `EVENT_CLOUD_TRANSPORT` / `ConnectorBackedEventCloud` references.  
> **Goal**: Implement every planned item — once — in the order that maximises reuse and minimises rework.

---

## 1. Ordering Principles

1. **Platform first.** Shared modules in `platform/java/*` and `platform/typescript/*` are built exactly once and imported by AEP, YAPPC, and Data-Cloud. No product re-implements a platform abstraction.
2. **Data-Cloud before products.** Data-Cloud provides the WARM-tier PostgreSQL store and gRPC server that AEP and YAPPC depend on for event persistence and transport.
3. **AEP bugs before new AEP features.** Critical concurrency and multi-tenancy bugs in AEP must be fixed before any new phase is layered on.
4. **YAPPC last on the critical path.** YAPPC wires on top of a hardened AEP + Data-Cloud stack.
5. **UI unlocked by shared canvas.** Extract `@ghatana/flow-canvas` first; all product UI pages run in parallel after that.
6. **Fail-fast env config everywhere.** Env-config phases are delivered first within each product so that later phases never contain hardcoded strings.
7. **SPI over mapping tables.** `ServiceLoader`-based SPI (EventLogStore, OperatorProvider) is wired in the platform track; products add `META-INF/services` entries, never mapping tables.
8. **Reuse existing deployment infrastructure.** `DeploymentMode` enum, `DataCloudClientFactory`, `AepEventCloudFactory`, `Aep.embedded()`, and YAPPC's `AepClientFactory` already exist. New work builds **on top of** these — never duplicates them.

---

## 2. Dependency Graph

```
┌────────────────────────────────────────────────────────────────────────────┐
│ TRACK 0 — Platform Java Foundations (all parallel with each other)         │
│  0A event-cloud SPI ─────────────────────────────────────────────────────┐ │
│  0B yaml-template engine                                                 │ │
│  0C schema-registry ─────────────────────────────────────── (uses 0A)   │ │
│  0D agent-memory (PersistentMemoryPlane + JdbcMemoryItemRepository)      │ │
│  0E agent-framework loader (AgentDefinitionLoader)                       │ │
└──────────────────────────────────────────────────────────────────────────┼─┘
                    ║                                                       │
         ┌──────────╨──────────┐               ┌──────────────┐            │
         ▼                     ▼               ▼              ▼            │
┌─────────────────┐  ┌──────────────────┐  ┌─────────┐  ┌──────────────┐  │
│ TRACK 1         │  │ TRACK 2          │  │ TRACK 3 │  │ TRACK 2b     │  │
│ Data-Cloud      │  │ AEP Backend      │  │ Platform│  │ YAPPC Backend│  │
│ DC-0 (BLOCKING) │  │ Bugs (critical)  │  │ TypeSc. │  │ (deps Track 2│  │
│ DC-1 WARM tier  │  │ AEP Phases 1–8  │  │ flow-   │  │ + Track 1)   │  │
│ DC-2 gRPC srv   │  │                  │  │ canvas  │  │ Phases 1–12  │  │
│ DC-3–9 HTTP/SSE │  │                  │  │ shell   │  │              │  │
└────────┬────────┘  └────────┬─────────┘  └────┬────┘  └──────┬───────┘  │
         └──────────────┬─────┘                 │              │           │
                        ▼                       ▼              │           │
              ┌──────────────────────────────────────────────────────────┐ │
              │ TRACK 4 — All UI (AEP 7 pages  ∥  Data-Cloud 7 areas)   │◄┘
              └──────────────────────────────────────────────────────────┘
```

**Critical path (minimum unblocking thread)**:  
`DC-0` → `DC-1` → `DC-2` → _(AEP bugs)_ → `AEP P1–P2` → `YAPPC-Ph0.5` ∥ `YAPPC-Ph0.6` → `YAPPC-Ph1` → `YAPPC-Ph2–4` → `YAPPC-Ph5` → `YAPPC-Ph6` → `YAPPC-Ph7/8 ∥` → `YAPPC-Ph9` → `YAPPC-Ph10` → `YAPPC-Ph11 ∥ Ph12`

All Track 0 modules feed the critical path and should start on Day 1.

---

## 2.5 Existing Multi-Mode Deployment Architecture (DO NOT RE-IMPLEMENT)

> **CRITICAL**: This section documents infrastructure that **ALREADY EXISTS** in the codebase. Plans must build upon it — never replace or duplicate it. Review this section before starting any Track.

### Data-Cloud Deployment Modes (ALREADY IMPLEMENTED)

`products/data-cloud/platform/…/deployment/DeploymentMode.java` — enum `EMBEDDED | STANDALONE | DISTRIBUTED`

| Mode          | Factory Method                                    | When to Use                             |
| :------------ | :------------------------------------------------ | :-------------------------------------- |
| `EMBEDDED`    | `DataCloudClientFactory.embedded(ServerConfig)`   | AEP or YAPPC in same JVM, edge, testing |
| `STANDALONE`  | `DataCloudClientFactory.standalone(serverUrl)`    | Development, small production           |
| `DISTRIBUTED` | `DataCloudClientFactory.distributed(clusterUrls)` | HA, scale-out production                |

Existing classes (do not duplicate):

- `DataCloudClient.java` — unified interface across all modes
- `EmbeddedDataCloudClient.java` — "Mini Distributed": in-process, same plugin architecture as distributed
- `HttpDataCloudClient.java` — HTTP client for STANDALONE
- `DistributedHttpDataCloudClient.java` — load-balanced client for DISTRIBUTED
- `EmbeddableDataCloud.java` + `DefaultEmbeddableDataCloud.java` — in-process embedding API

**Missing piece (add in DC-0)**: `DataCloudClientFactory.fromEnvironment()` — env-var-driven mode selection:

- `DATACLOUD_MODE=embedded|standalone|distributed` (default: `embedded` for dev, `standalone` otherwise)
- `DATACLOUD_SERVICE_URL` (STANDALONE), `DATACLOUD_CLUSTER_URLS` (DISTRIBUTED)

### AEP Deployment Modes (ALREADY IMPLEMENTED)

`products/aep/platform/…/Aep.java` — factory class with three modes:

| Mode              | Entry Point                                              | EventCloud Backend                                                           |
| :---------------- | :------------------------------------------------------- | :--------------------------------------------------------------------------- |
| Library (dev)     | `Aep.embedded()`                                         | ServiceLoader → `InMemoryEventCloud` if `AEP_DEV_MODE=true`, else fail-fast  |
| Library (prod)    | `Aep.create(AepConfig)`                                  | ServiceLoader finds production `EventLogStore` provider; fail-fast if absent |
| Testing           | `Aep.forTesting()`                                       | `InMemoryEventCloud` always                                                  |
| Standalone server | `AepLauncher` + `AEP_HTTP_ENABLED=true` or `--http` flag | Same ServiceLoader-backed                                                    |

`AepEventCloudFactory.java` already implements ServiceLoader discovery + fail-fast + `AEP_DEV_MODE` fallback.  
`AepConfig` record: `instanceId`, `workerThreads`, `maxPipelinesPerTenant`, `enableMetrics`, `enableTracing`.  
Env vars that EXIST in code: `AEP_DEV_MODE`, `AEP_HTTP_ENABLED`, `AEP_INSTANCE_ID`, `AEP_WORKERS`.

> **Do NOT build a separate `EventCloudConnector` transport layer** — AEP uses `EventLogStore` SPI via ServiceLoader. See Track 0A correction below.

### YAPPC → AEP Consumption (PARTIALLY IMPLEMENTED)

`products/yappc/backend/api/…/aep/`: `AepClientFactory`, `AepConfig`, `AepMode`, `AepLibraryClient`, `AepServiceClient`

| Mode    | `AEP_MODE` value | Default for       | Status                                                                                                                                                                                  |
| :------ | :--------------- | :---------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| LIBRARY | `library`        | `dev`, `local`    | Exists — **needs fix**: `AepLibraryClient` uses `URLClassLoader` + reflection; should call `Aep.create(config)` via direct typed API (add `products:aep:platform` compile dep to YAPPC) |
| SERVICE | `service`        | `prod`, `staging` | Exists — `AepServiceClient` → `AEP_SERVICE_HOST:AEP_SERVICE_PORT` (default `7004`)                                                                                                      |

### YAPPC → Data-Cloud Consumption (ENTIRELY MISSING — Must Add in Ph0.5)

YAPPC has **zero** DataCloud client code. User requirement: "yappc consumes both data-cloud and aep".

Required additions (defined fully in **YAPPC-Ph0.5** below):

- `DataCloudClientConfig.java` — `fromEnvironment()` reading `DATACLOUD_MODE`, `DATACLOUD_SERVICE_URL`, `DATACLOUD_GRPC_HOST`, `DATACLOUD_GRPC_PORT`
- Wire `DataCloudClientFactory` into YAPPC's DI modules so services can inject `DataCloudClient`

### YAPPC EventPublisher Outbox (GAP — Must Fix Before Ph5)

`products/yappc/backend/persistence/…/events/EventPublisher.java` writes domain events to `yappc.domain_events` + `yappc.event_outbox` tables via JDBC. This outbox is **completely disconnected** from AEP EventCloud — no relay exists.

Required addition (defined fully in **YAPPC-Ph0.6** below):

- `OutboxRelayService.java` — polls `yappc.event_outbox`, calls `EventCloud.append()` or `aepClient.publishEvent()`, marks entries processed

---

## 3. Zero-Duplication Table

Build the following **once** in the platform. Every product **imports and binds** — never re-implements.

| Shared Component                                                                                                                        | Lives in                                                                 | Consumed by                                                                        |
| --------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------ | ---------------------------------------------------------------------------------- |
| `EventCloud` SPI **(ALREADY EXISTS)** + `GrpcEventLogStore` + `HttpEventLogStore` (new `EventLogStore` SPI impls for remote Data-Cloud) | `platform/java/event-cloud/` (SPI) + `platform/java/connectors/` (impls) | AEP (ServiceLoader picks impl), YAPPC (via DataCloud client)                       |
| `YamlTemplateEngine` + `TemplateContext`                                                                                                | `platform/java/yaml-template/`                                           | AEP catalog loader (Phase 2), YAPPC all YAML loaders (Phases 3–6, 11)              |
| `SchemaRegistry` + `DataCloudSchemaRegistry`                                                                                            | `platform/java/schema-registry/`                                         | AEP `EventSchemaValidator`, YAPPC `EventSchemaValidator`, both `AepCoreModule`s    |
| `PersistentMemoryPlane` + `JdbcMemoryItemRepository`                                                                                    | `platform/java/agent-framework/memory/`                                  | AEP (Phase 3), YAPPC (Phase 9), Data-Cloud `/memory` HTTP routes (DC-4)            |
| `AgentDefinitionLoader`                                                                                                                 | `platform/java/agent-framework/loader/`                                  | AEP `AepOperatorCatalogLoader` (Phase 2), YAPPC `YappcAgentSystem` (Phase 8)       |
| `CatalogAgentDispatcher`                                                                                                                | `platform/java/agent-dispatch/` (**ALREADY EXISTS**)                     | AEP (Phase 2 — bind only), YAPPC (Phase 5 — bind only). **No new code.**           |
| `EnvConfig` utility                                                                                                                     | `platform/java/common-utils/` or `products/aep/platform/`                | AEP all modules (Phase 6), YAPPC connector modules (Phase 1f)                      |
| `PostgresCheckpointStorage`                                                                                                             | `products/aep/platform/`                                                 | AEP Phase 1d, YAPPC Phase 1d — bind from same class                                |
| `DurableEventCloudPublisher`                                                                                                            | Renamed from `DurableAepEventPublisher` in AEP                           | YAPPC replaces `HttpAepEventPublisher` with same class                             |
| `YamlOperatorLoader`                                                                                                                    | `products/aep/platform/`                                                 | AEP Phase 4, YAPPC Phase 6 — single loader; YAPPC adds its own `META-INF/services` |
| `@ghatana/flow-canvas`                                                                                                                  | `platform/typescript/flow-canvas/`                                       | AEP UI `WorkflowDesigner`, Data-Cloud UI `DataFabricPage` + `WorkflowCanvasPage`   |
| `@ghatana/realtime` (`useEventStream`)                                                                                                  | `platform/typescript/realtime/`                                          | All SSE-consuming pages in AEP UI and Data-Cloud UI                                |
| `@ghatana/ui`                                                                                                                           | `platform/typescript/ui/`                                                | Component library for all product UI pages                                         |

---

## 4. Track 0 — Platform Java Foundations

> Start immediately. All sub-tracks are independent and fully parallel.  
> Output: stable library JARs that products add to `dependencies {}`.

### 0A — `platform/java/event-cloud/` + `platform/java/connectors/` (EventCloud SPI + Remote Impls)

> **CORRECTION**: `EventCloud` SPI already exists in `platform/java/event-cloud/` (`EventCloud.java`, `InMemoryEventCloud.java`, `EventRecord.java`, `AppendResult.java`, `EventStream.java`). `EventLogStore` SPI already exists in `products/data-cloud/spi/`. AEP's `AepEventCloudFactory` already implements ServiceLoader discovery with fail-fast. **Do NOT build a conflicting `EventCloudConnector` layer.**
>
> **Revised 0A scope**: Build `EventLogStore` SPI implementations that connect to a remote Data-Cloud server via gRPC or HTTP. These are what AEP (in non-embedded mode) and other consumers use to talk to a standalone/distributed Data-Cloud instance through the existing SPI seam.

**Existing files (keep, do not modify)**: `EventCloud.java`, `InMemoryEventCloud.java`, `EventRecord.java`, `AppendResult.java`, `EventStream.java`, `EventTypeRef.java`, `Version.java` — all in `platform/java/event-cloud/`

**Existing files (keep, do not modify)**: `EventLogStore.java`, `TenantContext.java`, `EventView.java`, `StorageTier.java` — all in `products/data-cloud/spi/`

**Existing files (keep, do not modify)**: `AepEventCloudFactory.java`, `EventLogStoreBackedEventCloud.java`, `InMemoryEventCloud.java` — in AEP platform module

**New files**:

| File                               | Purpose                                                                                                                                                 |
| ---------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `GrpcEventLogStore.java`           | `EventLogStore` SPI impl — connects to Data-Cloud gRPC server. Reads `DATACLOUD_GRPC_HOST`, `DATACLOUD_GRPC_PORT`; mTLS when `APP_ENV != development`   |
| `HttpEventLogStore.java`           | `EventLogStore` SPI impl — HTTP fallback. Reads `DATACLOUD_HTTP_BASE_URL` (must be `https://` outside dev). Bearer auth via `DATACLOUD_HTTP_AUTH_TOKEN` |
| `META-INF/services/…EventLogStore` | Registers both implementations for ServiceLoader discovery                                                                                              |

**Tests**: `RemoteEventLogStoreIntegrationTest extends EventloopTestBase`  
→ `GrpcEventLogStore.append()` returns non-zero offset; reading back returns correct event; missing `DATACLOUD_GRPC_HOST` → `IllegalStateException` with env var name in message.

**Env vars used** (align with existing `DeploymentMode`/`DataCloudClientFactory`):  
`DATACLOUD_GRPC_HOST`, `DATACLOUD_GRPC_PORT` (default `9090`), `DATACLOUD_HTTP_BASE_URL`, `DATACLOUD_HTTP_AUTH_TOKEN`

> Remove all references to `EventCloudConnector`, `EventCloudConnectorRegistry`, `ConnectorBackedEventCloud`, `GrpcEventCloudConnector`, `HttpEventCloudConnector`, `EVENT_CLOUD_TRANSPORT` in any plan or code. Use `EventLogStore` SPI and `EventCloud` interface instead.

---

### 0B — `platform/java/yaml-template/` (YAML Template Engine)

**New files**:

| File                          | Purpose                                                                                                                                                                                                                 |
| ----------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `YamlTemplateEngine.java`     | `render(rawYaml, TemplateContext)` → resolved YAML string. Unknown `{{ var }}` → `IllegalStateException`. `renderWithInheritance(file, ctx)` → resolves `extends` chain (max depth 3, cycles → `IllegalStateException`) |
| `TemplateContext.java`        | Immutable `record`; `get(key)` throws if absent                                                                                                                                                                         |
| `TemplateContextBuilder.java` | Merges: env vars < `platform/agent-catalog/values.yaml` < local `values.yaml` < explicit params map                                                                                                                     |

**Tests**: `YamlTemplateEngineTest extends EventloopTestBase`  
→ Simple render; unknown var → error; `extends` depth 2; circular extends → error; full pipeline YAML render.

---

### 0C — `platform/java/schema-registry/` (Schema Registry)

> Depends on: Track 0A (uses `EventLogStore` for persistence via Data-Cloud).

**New files**:

| File                           | Purpose                                                                                                                                                                                            |
| ------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `SchemaRegistry.java`          | Interface: `getSchema()`, `validate()`, `registerSchema()`, `registerSchema(…, CompatibilityMode)` — `BACKWARD \| FORWARD \| FULL`                                                                 |
| `DataCloudSchemaRegistry.java` | Stores schemas as `schema.registered` events in `EventLogStore`. Enforces compatibility at registration time; throws `SchemaCompatibilityException` on breaking change                             |
| `SchemaBootstrapper.java`      | `seedFromBundle(schemaRegistry, bundleJson)` — seeds from `platform/contracts/build/generated/schemas/bundle.schema.json`. Idempotent (no-op if version already present). Default: `BACKWARD` mode |

**Tests**: `SchemaRegistryIntegrationTest extends EventloopTestBase`  
→ Seed from bundle; valid payload → `ValidationResult.valid()`; invalid → failure with field path; BACKWARD violation blocked at registration.

---

### 0D — `platform/java/agent-framework/memory/` (Persistent Memory Plane)

**New files**:

| File                            | Purpose                                                                                                                              |
| ------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| `PersistentMemoryPlane.java`    | Implements `MemoryPlane` SPI; delegates reads/writes to `JdbcMemoryItemRepository`; async via `Promise.ofBlocking(executor, …)`      |
| `JdbcMemoryItemRepository.java` | JDBC-based; `memory_items` table with `embedding vector(1536)` + ivfflat index; pgvector semantic search                             |
| `V001__memory_items.sql`        | Flyway migration: `memory_items (id, tenant_id, agent_id, memory_type, content, embedding vector(1536), created_at)` + ivfflat index |

**Tests**: `MemoryPlaneIntegrationTest extends EventloopTestBase`  
→ 5-episode write; 6th PERCEIVE returns episodes from PostgreSQL; semantic search returns top-3 by cosine similarity < 500ms.

---

### 0E — `platform/java/agent-framework/loader/` (Agent Definition Loader)

**New files**:

| File                         | Purpose                                                                                                                                                                                                 |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `AgentDefinitionLoader.java` | Loads `AgentDefinition` objects from YAML/JSON on classpath or filesystem. Calls `YamlTemplateEngine.renderWithInheritance()` before parsing. Throws `IllegalStateException` on missing required fields |

**Tests**: `AgentDefinitionLoaderTest extends EventloopTestBase`  
→ Load YAML with `extends: base-agent-template`; resolve correctly; missing required field → error.

---

## 5. Track 1 — Data-Cloud Backend (Phases 0–9)

> Start in parallel with Track 0.  
> **Phase DC-0 is BLOCKING** — nothing else in Data-Cloud can start until it lands.

### DC-0 (BLOCKING): Env Config + Startup Validation

**New files**:

| File                             | Purpose                                                                                                                                                                                                                             |
| -------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `DataCloudEnvConfig.java`        | Record holding all `DATACLOUD_*` env vars. `getRequired(key)` throws if absent. vars: `DATACLOUD_REDIS_HOST`, `DATACLOUD_PG_URL`, `DATACLOUD_PG_USER`, `DATACLOUD_PG_PASSWORD`, `DATACLOUD_S3_BUCKET` + optional tier-specific vars |
| `DataCloudStartupValidator.java` | Pings each tier (Redis PING, JDBC connection test, S3 HeadBucket) at startup. Fail-fast with clear message on first failure                                                                                                         |

**Modify**: `DataCloudStorageModule` — inject `DataCloudEnvConfig`; remove all hardcoded strings.

**Modify**: `DataCloudClientFactory` — add `fromEnvironment(Map<String,String> env)` static factory that reads `DATACLOUD_MODE` and delegates to `embedded(ServerConfig)` / `standalone(serverUrl)` / `distributed(clusterUrls)`. **`DeploymentMode` enum and the three factory methods already exist** — this adds only the env-var dispatch method.

---

### DC-1: WARM Tier (PostgreSQL Event Log)

> Depends on: DC-0

**New files**:

| File                         | Purpose                                                                                                                                                                                                                     |
| ---------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `WarmTierEventLogStore.java` | Full `EventLogStore` SPI impl: `append()`, `read()`, `readByTimeRange()`, `readByType()`, `tail()` (uses `readBlocking()` helper — not inline blocking), `appendBatch()`. All queries include `WHERE tenant_id = :tenantId` |
| `V001__create_event_log.sql` | `event_log (offset_value BIGINT GENERATED ALWAYS AS IDENTITY, tenant_id, event_type, collection_id, payload JSONB, created_at)`                                                                                             |

**Modify**: `DataCloudStorageModule` — register `WarmTierEventLogStore`; `ConfigDrivenStorageRouter` — add WARM tier route.

---

### DC-2: gRPC Server

> Depends on: DC-1

**New files**:

| File                           | Purpose                                                                  |
| ------------------------------ | ------------------------------------------------------------------------ |
| `EventLogGrpcService.java`     | Implements `EventLogService` proto; delegates to `WarmTierEventLogStore` |
| `EventQueryGrpcService.java`   | Implements `EventQueryService` proto; stream and point queries           |
| `EventServiceGrpcService.java` | Bidirectional streaming `ingestStream`                                   |
| `ProtobufMapper.java`          | proto ↔ domain model conversions                                         |

**Modify**: `DataCloudLauncher` — start gRPC server on `DATACLOUD_GRPC_PORT` (default 9090); register shutdown hook.

---

### DC-3: Agent Plugin HTTP Endpoints

> Depends on: DC-2 | Can parallel with DC-4–9 after DC-2

**New files**: `AgentRegistryRoutes.java` (7 REST routes for agent CRUD + SSE stream), `CheckpointRoutes.java` (4 routes: POST/GET/GET/DELETE checkpoints), `SseManager.java` (reusable ActiveJ `text/event-stream` infrastructure)

---

### DC-4: Memory Plane HTTP

> Depends on: DC-2, Track 0D (PersistentMemoryPlane already built)

**New file**: `MemoryPlaneRoutes.java` — 9 routes: summary, per-tier pages (episodic/semantic/procedural/preference), semantic search (pgvector), delete, retain. Backed by `PersistentMemoryPlane`.

---

### DC-5: Brain REST + SSE

> Depends on: DC-2 | Parallel with DC-3, DC-4, DC-6–9

**New file**: `BrainRoutes.java` — 7 routes: workspace, workspace/stream (SSE broadcast when salience ≥ 0.95), attention/elevate, attention/thresholds, patterns, patterns/match, salience/{itemId}.

---

### DC-6: Learning Loop

> Depends on: DC-2 | Parallel with DC-3–5, DC-7–9

**New files**: `LearningBridge.java` (wires `FeedbackCollector` → `ConsolidationPipeline`; fires every 5 min via `ScheduledExecutorService`), `LearningRoutes.java` (5 routes: trigger, status, review-queue, approve, reject).  
**Modify**: `DataCloudCoreModule` + `DataCloudLauncher` — start `LearningBridge`.

---

### DC-7: LLM Integration

> Depends on: DC-2 | Parallel with DC-3–6, DC-8–9

**Modify**: `DataCloudBrainModule` — wire `ContextGateway` → `AIIntegrationService` → `DefaultLLMGateway`. Wire `LLMFactExtractor` to real `DefaultLLMGateway` (remove stub).

---

### DC-8: Analytics REST

> Depends on: DC-2 | Parallel with DC-3–7, DC-9

**New file**: `AnalyticsRoutes.java` — 4 routes: query, query/stream (SSE), aggregate, query-plan. Delegates to `AnalyticsQueryEngine`.

---

### DC-9: SSE Streaming

> Depends on: DC-1 (HOT tier read) | Can parallel with DC-3–8

**New file**: `EventStreamRoutes.java` — tail events from HOT tier; filtered by type/collection. Uses `SseManager` from DC-3.

---

### Data-Cloud Module Wiring Summary

**Modify**:

- `DataCloudCoreModule` — add `LearningBridge`, `ConsolidationPipeline`, `SseManager`, all route modules
- `DataCloudHttpServer` — register all new route modules
- `DataCloudLauncher` — call `DataCloudStartupValidator.validate()` → start gRPC → start `LearningBridge` → start HTTP; use `DATACLOUD_HTTP_PORT` env var

---

## 6. Track 2 — AEP Backend

> Critical bugs MUST land before any new AEP phase. Env config lands early (low risk, big impact).

### AEP-P0: Critical Bug Fixes (Do First)

| Bug                                                                                                            | Fix                                                                                                                               |
| -------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| **Bug 1**: `.getResult()` in `AIAgentOrchestrationManagerImpl.executeChainInternal()` deadlocks the event loop | Rewrite using `Promises.all()` + `Promises.reduceEx()` to chain Promises without blocking                                         |
| **Bug 2**: `AgentRegistryService` missing `tenantId` on all public methods                                     | Add `TenantId tenantId` as first parameter to every method                                                                        |
| **Bug 3**: `JedisPool` created without connection validation                                                   | Add `cfg.setTestOnBorrow(true)` in `AepIngressModule`; add startup health check that fails application start on Redis unreachable |

---

### AEP-P6: Env Config (Deliver Early — Low Risk)

> Can parallel with AEP-P0 or immediately after.

**New file**: `EnvConfig.java` — `env(key, default)`, `intEnv(key, default)` (logged defaults). Shared pattern used by YAPPC connector modules too (Phase 1f).

**Modify**: `AepConnectorModule`, `AepIngressModule` — replace every hardcoded string/port/URL with `EnvConfig.env(…)`. Required vars: `AEP_REDIS_HOST`, `AEP_REDIS_PORT`, `AEP_PG_URL`, `AEP_PG_USER`, `AEP_PG_PASS`, `AEP_HTTP_PORT`, `AEP_KAFKA_BROKERS`, `AEP_KAFKA_GROUP`, `AEP_AWS_REGION`, `AEP_S3_BUCKET`, `AEP_SQS_URL`.  
Zero references to `localhost` remaining in any `AepConnectorModule` method body.

---

### AEP-P1: Framework Wiring (AgentTurnPipeline)

> Depends on: AEP-P0 (bugs fixed), Track 0A (event-cloud SPI), Track 0D (memory)

**New files**:

| File                             | Purpose                                                                                                                                                                                                                                                              |
| -------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `AepAgentAdapter.java`           | Bridges `AgentDefinition` → `BaseAgent`. Implements PERCEIVE (query 5 recent episodes via `PersistentMemoryPlane`), ACT (stepRunner.execute), CAPTURE (store episode via `Promise.ofBlocking`), REFLECT (fire-and-forget `EventCloud.append("pattern.learning", …)`) |
| `AepContextBridge.java`          | Converts `AgentExecutionContext` → `AgentContext` (includes `tenantId`, `traceId`)                                                                                                                                                                                   |
| `PostgresCheckpointStorage.java` | Replaces `InMemoryCheckpointStorage`; Flyway-managed `aep_checkpoints` table; `Promise.ofBlocking()` wrapping                                                                                                                                                        |

**Modify**: `AIAgentOrchestrationManagerImpl` — wire `AgentTurnPipeline`; chain via `Promises.reduceEx()` (also fully resolves Bug 1).

---

### AEP-P2: Agent Dispatch + Catalog Loader

> Depends on: AEP-P1, Track 0B (yaml-template), Track 0E (AgentDefinitionLoader)

**New file**: `AepOperatorCatalogLoader.java` — scans `resources/operators/` YAMLs; calls `YamlTemplateEngine.render()` before each parse; calls `OperatorCatalog.register(id, operator)` for each. Throws `IllegalStateException` if `OperatorProvider` not found for declared type (no silent skip).

**Modify**:

- `AepOrchestrationModule` — bind `CatalogAgentDispatcher` (from `libs:agent-dispatch` — **no new class**), bind `AgentDispatcher`
- `AepLauncher` — call `AepOperatorCatalogLoader.loadFromClasspath()` **before** HTTP server starts
- `PipelineMaterializer` — pass YAML through `YamlTemplateEngine.render()` before compiling; throw `IllegalStateException` on unresolvable operator ID (remove warning-and-skip)

---

### AEP-P3: Memory Plane Binding

> Depends on: AEP-P2, Track 0D (PersistentMemoryPlane)

**New file**: `V5__memory_plane.sql` — `aep_memory_items (id, tenant_id, agent_id, memory_type, content, embedding vector(1536), created_at)` + ivfflat index; `aep_task_states (id, tenant_id, agent_id, task_id, state JSONB)`

**Modify**: `AepOrchestrationModule` — bind `PersistentMemoryPlane` (from `platform/java/agent-framework/`) + `JdbcMemoryItemRepository` + `JdbcTaskStateRepository`.

---

### AEP-P4: Learning Loop

> Depends on: AEP-P3

**New files**:

| File                     | Purpose                                                                                                      |
| ------------------------ | ------------------------------------------------------------------------------------------------------------ |
| `LearningScheduler.java` | Fires `ConsolidationPipeline` every 5 min per active agent                                                   |
| `HitlQueue.java`         | Backed by `EventLogStore`; `enqueue()`, `listPending(tenantId)`, `approve(id, reason)`, `reject(id, reason)` |
| `HitlReviewItem.java`    | `record` class                                                                                               |

---

### AEP-P5: Agent Registry + Multi-Tenancy

> Depends on: AEP-P3, Data-Cloud DC-3 (AgentRegistryRoutes live)

**Modify**:

- `AepOrchestrationModule` — bind `DataCloudAgentRegistry` replacing the stub `AgentRegistryService` binding
- `AgentRegistryService` — confirm `tenantId` first parameter on all methods (already done in AEP-P0 Bug 2; verify binding propagated)
- `AepLauncher` — call `AgentRegistryService.register(tenantId, def)` for all loaded catalog agents right after catalog loader finishes

---

### AEP-P7: REST + SSE Endpoints

> Depends on: AEP-P4, AEP-P5 | Parallel with AEP-P8

**New files**:

| File                   | Purpose                                                                                                                                                                                                               |
| ---------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `AgentController.java` | `GET /api/v1/agents` (tenantId filter), `GET /agents/:id`, `POST /agents/:id/execute`, `GET /agents/:id/memory`, `GET /agents/:id/memory/episodes`, `GET /agents/:id/policies`, `GET /agents/:id/status/:executionId` |
| `HitlController.java`  | `GET /api/v1/hitl/queue`, `GET /hitl/queue/:id`, `POST /hitl/queue/:id/approve`, `POST /hitl/queue/:id/reject`                                                                                                        |
| SSE routes (inline)    | `/events/pipeline-runs`, `/events/agent-outputs`, `/events/hitl-queue` — ActiveJ native SSE: `ChannelSupplier.ofIterable(eventBus.subscribe(…)).map(encodeSSE)`                                                       |

**Modify**: `AepHttpServer` — register `AgentController`, `HitlController`; add SSE routes.

---

### AEP-P8: gRPC Hardening

> Depends on: AEP-P5 | Parallel with AEP-P7

**Modify**: `AgentGrpcService` — wire to `DataCloudAgentRegistry` + `AgentDispatcher`; all async ops via `.whenComplete()` — zero `.getResult()` calls.

---

### AEP — Additional Wiring (Schema Registry + Event Types)

> Depends on: Track 0C, can parallel with AEP-P3

**New file**: `DataCloudEventTypeRepository.java` — stores event type records as events in Data-Cloud `EventLogStore` (event type `eventtype.registered`). Replaces `InMemoryEventTypeRepository`.

**Modify**:

- `EventSchemaValidator` — inject `SchemaRegistry`; remove `ConcurrentHashMap<String, String> eventSchemas` and all 4 inline JSON schema strings; implement as `schemaRegistry.validate(eventType, "latest", payload)`
- `AepCoreModule` — swap `InMemoryEventTypeRepository` binding → `DataCloudEventTypeRepository`; bind `AgentDefinitionLoader` (from Track 0E)

Call `SchemaBootstrapper.seedFromBundle(schemaRegistry, bundleJson)` at `AepCoreModule` startup (idempotent).

---

## 7. Track 2b — YAPPC Backend (Phases 1–12)

> YAPPC phases depend on hardened AEP + Data-Cloud. Where a phase maps directly to a Track 0 platform module, that module is the deliverable — no YAPPC-specific re-implementation.

### YAPPC-Ph0.5: Data-Cloud Client Wiring (BLOCKING for Ph5–Ph9)

> **NEW PHASE — addresses the completely missing YAPPC → Data-Cloud consumption path.**  
> Depends on: DC-0 (DataCloudEnvConfig), DC-1 (WarmTierEventLogStore) | Can parallel with YAPPC-Ph1

YAPPC currently has zero DataCloud client code. This phase wires `DataCloudClientFactory` into YAPPC so domain services can read and write events/memory/state directly against Data-Cloud without routing everything through AEP.

**New files**:

| File                         | Purpose                                                                                                                                         |
| ---------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- | ---------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `DataCloudClientConfig.java` | Record; `fromEnvironment(Map<String,String> env)` — reads `DATACLOUD_MODE` (`embedded                                                           | standalone | distributed`, default `standalone`), `DATACLOUD_SERVICE_URL`, `DATACLOUD_CLUSTER_URLS`, `DATACLOUD_GRPC_HOST`, `DATACLOUD_GRPC_PORT`. Fail-fast if required vars absent in non-embedded mode. |
| `DataCloudModule.java`       | ActiveJ DI module providing `DataCloudClient` singleton; delegates to `DataCloudClientFactory.fromEnvironment()` using `DataCloudClientConfig`. |

**Modify**:

- `YappcAiModule.java` (or equivalent top-level DI module) — import `DataCloudModule`; expose `DataCloudClient` for injection
- `YappcLifecycleService.java` — inject `DataCloudClient` alongside `AepClient` — phase state reads/writes go direct to Data-Cloud, event submission goes via AEP

**Tests**: `DataCloudModuleIntegrationTest extends EventloopTestBase`  
→ `DATACLOUD_MODE=embedded` → `EmbeddedDataCloudClient` created; missing `DATACLOUD_SERVICE_URL` in standalone mode → `IllegalStateException`.

---

### YAPPC-Ph0.6: Outbox Relay Service (BLOCKING for Ph5)

> **NEW PHASE — fixes the disconnection between YAPPC domain events and AEP EventCloud.**  
> Depends on: YAPPC-Ph0.5, YAPPC-Ph1 (AepClient available) | Can parallel with other YAPPC phases as long as deps are met

`EventPublisher.java` currently writes domain events to `yappc.domain_events` + `yappc.event_outbox` tables (outbox pattern). No relay exists to forward these to AEP EventCloud.

**New files**:

| File                            | Purpose                                                                                                                                                                                                                                                                                                                                                                                            |
| ------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `OutboxRelayService.java`       | Scheduled service; polls `yappc.event_outbox` for unprocessed entries (status = `PENDING`); calls `aepClient.publishEvent(eventType, payload)` or `EventCloud.append(tenantId, eventType, payload)` for each; marks entry `PROCESSED` on success, `FAILED` on error with retry count; fires every 500ms via `ScheduledExecutorService`. Uses `Promise.ofBlocking(executor, …)` for all JDBC calls. |
| `V010__outbox_relay_status.sql` | Flyway migration: adds `status VARCHAR(16)` + `retry_count INT` + `processed_at TIMESTAMP` columns to `yappc.event_outbox` if not already present                                                                                                                                                                                                                                                  |

**Modify**:

- `YappcPersistenceModule.java` — bind `OutboxRelayService`; schedule via `ScheduledExecutorService`
- `EventPublisher.java` — ensure `status='PENDING'` is written on insert (may already be the case)

**Tests**: `OutboxRelayServiceTest extends EventloopTestBase`  
→ Insert 3 PENDING outbox rows; relay fires; `AepClient.publishEvent()` called 3 times; rows marked PROCESSED.  
→ `AepClient.publishEvent()` throws → row marked FAILED; retry_count incremented.

---

### YAPPC-Ph1: AEP Hardening + EventCloud Wiring

> Depends on: Track 0A, DC-1, AEP-P0

| Step | Deliverable                                                                                                                                                                                                                                                                                                                                                                     | Notes                                                                                   |
| ---- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| 1a   | Wire `EventLogStore` to Data-Cloud PostgreSQL; add `tenantId` to all `append()`/`subscribe()` calls in AEP                                                                                                                                                                                                                                                                      | Same as AEP-P0 Bug 2 context — verify propagated                                        |
| 1b   | Verify `AepEventCloudFactory.createDefault()` resolves the production `EventLogStore` SPI impl (from Track 0A's `GrpcEventLogStore` or `WarmTierEventLogStore`). **Do NOT introduce** a new `EventCloudConnector` layer — the existing `EventLogStoreBackedEventCloud` is the correct facade. Ensure `AEP_DEV_MODE=false` in production so fail-fast on missing impl is active. | No new connector classes — `AepEventCloudFactory` already handles this                  |
| 1c   | Delete `HttpAepEventPublisher.java`; rename `DurableAepEventPublisher` → `DurableEventCloudPublisher`                                                                                                                                                                                                                                                                           | All callers updated in one pass. Outbox relay (Ph0.6) uses `DurableEventCloudPublisher` |
| 1d   | Swap `InMemoryCheckpointStorage` → `PostgresCheckpointStorage` (built in AEP-P1)                                                                                                                                                                                                                                                                                                | Flyway creates `aep_checkpoints` table                                                  |
| 1e   | Event-source `AIAgentOrchestrationManagerImpl`: replace `ConcurrentHashMap` with `EventLogStore` events (`AGENT_REGISTERED`, `EXECUTION_STATUS_CHANGED`, `AGENT_CHAIN_REGISTERED`)                                                                                                                                                                                              | Same as AEP Bug 1 fix + AEP-P1 — one implementation                                     |
| 1f   | Env-drive `AepConnectorModule`: `KAFKA_BOOTSTRAP_SERVERS`, `RABBITMQ_HOST`, `AWS_REGION` — use `EnvConfig` from AEP-P6; fail-fast                                                                                                                                                                                                                                               | Same `EnvConfig` utility                                                                |
| 1g   | `AgentRegistryService` `tenantId` param                                                                                                                                                                                                                                                                                                                                         | Same as AEP-P0 Bug 2 — confirm already done                                             |
| 1h   | **Fix `AepLibraryClient` reflection** — add `products:aep:platform` as `compileOnly` dep in YAPPC API module; replace `URLClassLoader` + reflection with direct typed call: `Aep.create(aepConfig)` or `Aep.embedded()`. Remove `AEP_LIBRARY_PATH` env var (no longer needed).                                                                                                  | Eliminates cross-classloader fragility; maintains LIBRARY/SERVICE split via `AEP_MODE`  |

**Files to delete**: `HttpAepEventPublisher.java`, `AepEventBridge.java`

---

### YAPPC-Ph2: Schema Registry

> Depends on: YAPPC-Ph1, Track 0C | Same platform module, product-level wiring only

- Call `SchemaBootstrapper.seedFromBundle(schemaRegistry, bundleJson)` at `AiServiceModule` startup (idempotent)
- Inject `SchemaRegistry` into YAPPC's `EventSchemaValidator`; remove `ConcurrentHashMap<String, String> eventSchemas` (4 inline JSON strings removed)
- Wire `DataCloudEventTypeRepository` in place of `InMemoryEventTypeRepository` in `AepCoreModule` (also done in AEP track above — confirm propagated to YAPPC)

---

### YAPPC-Ph3: YAML Template Engine Wiring

> Depends on: YAPPC-Ph1 | Track 0B module already built — wiring only

Inject `YamlTemplateEngine` into every YAML loading site in YAPPC:

| Loader                                        | Injection point                                           |
| --------------------------------------------- | --------------------------------------------------------- |
| `YamlOperatorLoader`                          | Before parsing each operator YAML                         |
| `PipelineMaterializer`                        | Before compiling each pipeline YAML (also done in AEP-P2) |
| `WorkflowMaterializer`                        | Before parsing each workflow YAML                         |
| `YappcAgentSystem.loadAgentDefinitions()`     | Before parsing each agent YAML                            |
| `YappcIntegrationModule` (event-routing.yaml) | Before parsing routing rules                              |

`TemplateContextBuilder` initialised at module startup: env vars always present; `values.yaml` scanned from well-known paths.

---

### YAPPC-Ph4: Operator Catalog Loading

> Depends on: YAPPC-Ph2, YAPPC-Ph3

- Create `META-INF/services/com.ghatana.core.operator.spi.OperatorProvider` in YAPPC operator modules
- Use **same** `YamlOperatorLoader` (from AEP-P2 — single class, not duplicated); YAPPC passes its operator YAML paths
- `PipelineMaterializer` already uses template engine (AEP-P2) — confirm YAPPC pipeline YAMLs flow through the same path
- Verify: no `switch`, `if/else`, or `Map<String, Class>` mapping operators to classes anywhere

---

### YAPPC-Ph5: AEP ↔ YAPPC Event Routing Bridge

> Depends on: YAPPC-Ph4

**New file**: `YappcIntegrationModule.java` — ActiveJ module that:

1. Renders `products/yappc/config/agents/event-routing.yaml` through `YamlTemplateEngine` first
2. Parses rendered YAML → `EventRoutingConfig (Map<topic, agentId>)`
3. Calls `EventCloud.subscribe(tenantId, topic, …)` for all 60+ topics
4. Routes each event to `CatalogAgentDispatcher` (bound from `libs:agent-dispatch` — **no new class**)
5. Registers `CatalogAgentDispatcher` in `OperatorCatalog` under id `catalog-agent-dispatcher`

Audit YAPPC services vs. `event-routing.yaml` — every topic declared must have a corresponding `EventCloud.append()` call somewhere in YAPPC source. Add missing publishes via `DurableEventCloudPublisher.append()`.

---

### YAPPC-Ph6: Pipeline Operators + Registration

> Depends on: YAPPC-Ph5

Implement 9 new operators (all self-register via `OperatorProvider` SPI; all use `SchemaRegistry` for validation; all publish via `EventCloud.append()` — no HTTP):

**`lifecycle-management-v1` pipeline operators** (parallel sub-steps):

| Class                              | SPI Type                           | Key behaviour                                                                                                       |
| ---------------------------------- | ---------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| `PhaseTransitionValidatorOperator` | `yappc:phase-transition-validator` | `schemaRegistry.validate("phase_transition", "latest", payload)`; failed events → `lifecycle.management.dlq`        |
| `PhaseStateManagerOperator`        | `yappc:phase-state-manager`        | Read state from Data-Cloud EventLogStore; validate state machine; append `PHASE_ADVANCED` via `EventCloud.append()` |
| `GateOrchestratorOperator`         | `yappc:gate-orchestrator`          | Parallel gate approvals via `Promise.all()`; 30s timeout; emit `gate.passed` or `gate.blocked`                      |
| `LifecycleStatePublisherOperator`  | `yappc:lifecycle-state-publisher`  | Publish `lifecycle.state.updated` to EventCloud (gRPC)                                                              |

**`agent-orchestration-v1` pipeline operators** (parallel with lifecycle operators):

| Class                            | SPI Type                         | Key behaviour                                                                                                |
| -------------------------------- | -------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| `AgentDispatchValidatorOperator` | `yappc:agent-dispatch-validator` | `schemaRegistry.validate("agent_dispatch", "latest", payload)`                                               |
| `BackpressureOperator`           | existing                         | Configure DROP_OLDEST, buffer=2048, overflow → `agent.orchestration.dlq`                                     |
| `AgentExecutorOperator`          | `yappc:agent-executor`           | Wraps `AgentEventOperator` + circuit breaker (10 failures / 60s / half-open=5) + checkpoints every 10 events |
| `ResultAggregatorOperator`       | `yappc:result-aggregator`        | 5s tumbling window; group by `agent_id + correlation_id`                                                     |
| `MetricsCollectorOperator`       | `yappc:metrics-collector`        | Micrometer counters/histograms every 10s via `libs:observability`                                            |

**Create**: `YappcOperatorProvider.java` — aggregates all 9 YAPPC operators under namespace `yappc`.

At `YappcAiService` startup:

1. `YamlOperatorLoader.load("products/yappc/config/pipelines/")` (template rendering included)
2. `PipelineRegistryClient.register(lifecycle-management-v1)` + `register(agent-orchestration-v1)`
3. `Orchestrator.deployPipeline(lifecycle-management-v1)` + `deployPipeline(agent-orchestration-v1)`

---

### YAPPC-Ph7: Lifecycle Service Implementation (Parallel with Ph8)

> Depends on: YAPPC-Ph6

**Modify** `YappcLifecycleService.java`:

| Endpoint                                   | Implementation                                                                                                                                                                                            |
| ------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `POST /lifecycle/phase/:projectId/advance` | Extract `tenantId` from JWT → `EventCloud.append(tenantId, "phase.transition", payload)` → subscribe with `correlationId` for `lifecycle.state.updated` (30s timeout) → return updated phase state or 408 |
| `GET /lifecycle/phase/:projectId/current`  | Query Data-Cloud EventLogStore for latest `PHASE_ADVANCED` event by `projectId + tenantId`                                                                                                                |
| `GET /lifecycle/project/:projectId/gates`  | Query Data-Cloud EventLogStore for gate state events                                                                                                                                                      |

Delete `AepEventBridge` — no longer needed.

---

### YAPPC-Ph8: Agent Catalog, LLM Gateway & Lazy Registry (Parallel with Ph7)

> Depends on: YAPPC-Ph6

**Step 8a — Fail-fast LLM provider** (`AiServiceModule`):

Delete the `else` stub branch in `llmGateway()`. Order of precedence:

1. `OLLAMA_HOST` set → `OllamaCompletionService` (local dev)
2. `OPENAI_API_KEY` → `ToolAwareOpenAICompletionService`
3. `ANTHROPIC_API_KEY` → `ToolAwareAnthropicCompletionService`
4. None set → `IllegalStateException` with message listing required env vars

Wrap every provider with `CostEnforcingCompletionService` using `AgentDefinition.maxCostPerCall`.

**Step 8b — Dynamic model routing** (`YappcAgentSystem.loadAgentDefinitions()`):

- Read YAML `model` field; infer provider from prefix (`claude` → anthropic, `gpt`/`o1` → openai, `llama`/`mistral`/`qwen`/`phi` → ollama)
- Call `DefaultLLMGateway.addRoute(agentId, providerName)` for each agent
- **Zero model name string literals in Java** — all routing derived from YAML

**Step 8c — Load all 590 AgentDefinitions**:  
`YappcAgentSystem.loadAgentDefinitions()` scans all 17 domain catalogs from `_index.yaml` via `FileBasedCatalog`. Uses `AgentDefinitionLoader` from Track 0E. Calls `AgentRegistryService.registerDefinition(tenantId, definition)` for each.

**Step 8d — Lazy AgentInstance creation**:  
`CatalogAgentDispatcher` (platform class — no new code): on dispatch, `AgentRegistryService.lookupInstance(tenantId, agentId)` → if absent, load `AgentDefinition` → build `AgentInstance` → weak-ref cache entry. No pre-warming of all 590 agents.

**Step 8e — Replace YAPPCAgentRegistry**:  
Wire `AgentRegistryService.listAll(tenantId)` for `GET /api/v1/ai/agents`. Add `@Deprecated` shim on `YAPPCAgentRegistry`; delete after one release cycle.

---

### YAPPC-Ph9: Durable Memory + Event Sourcing (After Ph7)

> Depends on: YAPPC-Ph7, Track 0D

**Step 9.1**: `YappcAgentSystem` — inject `DataSource`; replace `EventLogMemoryStore` with `PersistentMemoryPlane` (from Track 0D). Apply Flyway migration from existing `init-db.sql`.

**Step 9.2** — `YAPPCAgentBase.java`:

- Remove static `globalAepEventPublisher` setter and all callers
- Add constructor param `EventCloud eventCloud`
- All episode/pattern events via `eventCloud.append()` (gRPC)
- Update all 36 agent constructions in `YappcAgentSystem.bootstrapSdlcAgents()` + `loadAgentDefinitions()`

**Step 9.3** — REFLECT → AEP Pattern Detection:

```java
// In YAPPCAgentBase.reflect(), after memory.storePolicy():
eventCloud.append(tenantId, "pattern.learning", Map.of(
    "agentId", agentContext.agentId(),
    "episode", lastEpisode,
    "policy",  newPolicy
));
```

**Step 9.4** — PERCEIVE with PatternEngine reflex:

1. `patternEngine.match(input)` O(1) reflex match first — if confidence ≥ 0.7, return immediately
2. Fall through to `memoryPlane.queryEpisodes(tenantId, agentId, input).withTimeout(500ms)`
3. On timeout: `PerceiveResult.empty()` — proceed without memory, never block

---

### YAPPC-Ph10: GAA Lifecycle Hardening

> Depends on: YAPPC-Ph9

**Step 10.1**: Wrap all 36 SDLC agents in `AgentTurnPipeline` with `ResiliencePolicy` (circuit breaker: 10 failures / 60s / half-open=5; retry: max 5, exponential backoff, max 30s). Use Resilience4j (already in AEP build — no new dependency).

**Step 10.2**: All YAPPC HTTP handlers extract `tenantId` from JWT + `traceId` from `X-Trace-Id` header (generate UUID if absent) → build `AgentContext` once per request → pass through all `executeTurn()` calls.

---

### YAPPC-Ph11: Canonical Workflow Integration

> Depends on: YAPPC-Ph10

**Modify** `WorkflowMaterializer`:

- Render `canonical-workflows.yaml` through `YamlTemplateEngine` first
- `agent-dispatch` type → route through `CatalogAgentDispatcher`
- `human-approval` type → route through `ApprovalController`
- Register all 10 workflows at YAPPC startup

**New file in Data-Cloud**: `WorkflowRunRepository.java` — `EventLogStore`-backed:

- `startRun(tenantId, templateId, input)` → appends `WORKFLOW_STARTED` → returns `runId`
- `completeStep(tenantId, runId, stepId)` → appends `STEP_COMPLETED`
- `finishRun(tenantId, runId)` → appends `WORKFLOW_FINISHED`
- `getRunStatus(tenantId, runId)` → materialises state from events

**Wire**: `POST /workflows/:templateId/start` → `startRun()` + first step via `AgentTurnPipeline` → return `{ runId, status: "STARTED" }` (async).  
**Wire**: `PUT /approvals/:id/decide` → `EventCloud.append("approval.decided")` → `YappcIntegrationModule` subscription resumes workflow.

---

### YAPPC-Ph12: Testing, Observability + DLQ (Parallel with Ph11)

**Integration tests** (all extend `EventloopTestBase`):

| Test class                           | What it validates                                                                           |
| ------------------------------------ | ------------------------------------------------------------------------------------------- |
| `RemoteEventLogStoreIntegrationTest` | `GrpcEventLogStore` + `HttpEventLogStore`; missing env var → fail-fast error                |
| `DataCloudClientIntegrationTest`     | `DATACLOUD_MODE=embedded` → `EmbeddedDataCloudClient`; `standalone` → `HttpDataCloudClient` |
| `OutboxRelayServiceTest`             | 3 PENDING rows relayed to AepClient; FAILED rows increment retry_count                      |
| `YamlTemplateEngineTest`             | Render; unknown param; `extends` chain; circular → error                                    |
| `SchemaRegistryIntegrationTest`      | Seed from bundle; valid/invalid; BACKWARD compatibility                                     |
| `YappcAepIntegrationTest`            | Publish `phase.transition` → `PHASE_ADVANCED` in EventLogStore within 5s                    |
| `AgentDispatchIntegrationTest`       | `test.failed` → lazy-create `debug-orchestrator` → episode in Data-Cloud                    |
| `WorkflowRunIntegrationTest`         | Start `bug-fix` workflow → all steps complete; status correct                               |
| `MemoryPlaneIntegrationTest`         | 5-episode sequence → 6th PERCEIVE returns from PostgreSQL < 500ms                           |

**DLQ alerts** (add to `alert-rules.yml`):

- `LifecycleDlqDepthHigh` — DLQ depth > 100 for 5m
- `AgentOrchestrationDlqHigh` — DLQ depth > 50 for 5m

**Micrometer metrics** (YAPPC-specific):

| Metric                              | Type      | Labels                                       |
| ----------------------------------- | --------- | -------------------------------------------- |
| `lifecycle_phase_transitions_total` | Counter   | `tenantId`, `fromPhase`, `toPhase`, `status` |
| `workflow_runs_active`              | Gauge     | `tenantId`, `templateId`                     |
| `agent_dispatch_failures_total`     | Counter   | `tenantId`, `agentId`, `reason`              |
| `schema_validation_failures_total`  | Counter   | `schemaId`, `version`, `eventType`           |
| `llm_request_duration_seconds`      | Histogram | `tenantId`, `provider`, `agentId`            |

---

## 8. Track 3 — Platform TypeScript

> Start on Day 1. Fully independent of all Java backend tracks. Unblocks all UI work.

### 3A — `@ghatana/flow-canvas` (Extract + Publish)

Extract from `products/yappc/frontend/libs/canvas/` → `platform/typescript/flow-canvas/`.  
Publish as internal package `@ghatana/flow-canvas`.  
Exposes: `FlowCanvas` component, 4-tier topology nodes, edge types, zoom/pan controls.

### 3B — `@ghatana/platform-shell` (Module Federation Shell)

New package at `platform/typescript/platform-shell/`.  
Module Federation shell; tenant selector via Jotai; shared auth tokens; notification centre; observability bridge.  
Routes: `/ → product picker`, `/aep/* → AEP shell`, `/data-cloud/* → Data-Cloud shell`.

---

## 9. Track 4 — All UI Work

> Start after Track 3A (flow-canvas available) and respective backend tracks.  
> All pages use: `@ghatana/ui` components, `@ghatana/realtime` `useEventStream`, Jotai (local state), TanStack Query (server state), `react-hook-form` + Zod (forms), Tailwind CSS only.  
> No raw `@xyflow/react`, no `any` types, strict TypeScript.

### AEP UI (7 pages) — can run fully parallel after Track 3A + AEP-P7

**New deps**: `@tanstack/react-query ^5.67.0`, `recharts ^2.14.1`, `@monaco-editor/react ^4.7.0`

| Page / Phase                                  | Path                                                                   | Key components                                                                                                               |
| --------------------------------------------- | ---------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| **UI-A**: Router + NavBar + PipelineListPage  | Modify `main.tsx` → `<RouterProvider router={createBrowserRouter(…)}>` | `PipelineListPage`, `NavBar`                                                                                                 |
| **UI-B**: PipelineBuilderPage redesign        | `/aep/pipelines/:pipelineId`                                           | URL param; `useQuery` save/load; live validation (POST debounced 500ms); Run Now drawer with SSE output; collapsible palette |
| **UI-C**: AgentRegistryPage + AgentDetailPage | `/aep/agents`, `/aep/agents/:agentId`                                  | Agent cards, tabbed detail (Overview, Memory[episodes/facts/procedures/working], Policies, Executions, Config)               |
| **UI-D**: MonitoringDashboardPage             | `/aep/monitoring`                                                      | SSE pipeline runs; agent health grid; recharts event rate; HITL queue count                                                  |
| **UI-E**: PatternStudioPage                   | `/aep/patterns`                                                        | Monaco YAML editor; live validation; publish flow                                                                            |
| **UI-F**: HitlReviewPage                      | `/aep/hitl`                                                            | SSE queue; approve/reject with required reason                                                                               |
| **UI-G**: LearningPage                        | `/aep/learning`                                                        | Consolidation history; skill promotion queue; bar charts                                                                     |

**New hooks/services**: `useAgents`, `useAgentMemory`, `usePipelineRuns`, `useHitlQueue`, `agent.service.ts`, `memory.service.ts`, `hitl.service.ts`

---

### Data-Cloud UI (7 areas) — can run parallel after Track 3A + DC-5/DC-9

| Area                                   | File                         | Key components                                                                                                          |
| -------------------------------------- | ---------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| **DC-UI-1**: EventExplorerPage         | `EventExplorerPage.tsx`      | `useEventStream` hook, Jotai `eventFilterAtom` + `eventLogAtom`, `event.service.ts`                                     |
| **DC-UI-2**: MemoryPlaneViewerPage     | `MemoryPlaneViewerPage.tsx`  | 7-tier tabs, TanStack Query, semantic search input, `memory.service.ts`                                                 |
| **DC-UI-3**: EntityBrowserPage         | `EntityBrowserPage.tsx`      | Unified collection browser; table/JSON/schema view modes; `entity.service.ts`                                           |
| **DC-UI-4**: DataFabricPage            | `DataFabricPage.tsx`         | `@ghatana/flow-canvas` `FlowCanvas` with 4-tier topology nodes; live metrics from SSE; `fabric-topology.ts` static data |
| **DC-UI-5**: AgentPluginManagerPage    | `AgentPluginManagerPage.tsx` | Agent cards; SSE registry events feed; `agent-registry.service.ts`                                                      |
| **DC-UI-6**: BrainDashboardPage        | `BrainDashboardPage.tsx`     | Rewrite existing stub; GlobalWorkspace spotlight SSE; pattern list; attention thresholds; `brain.service.ts`            |
| **DC-UI-7**: WorkflowDesigner redesign | `WorkflowDesigner.tsx`       | Replace `@xyflow/react` → `@ghatana/flow-canvas`; wire to backend workflow endpoints + SSE execution progress           |

Also wire to real endpoints (remove stubs): `DashboardPage`, `SqlWorkspacePage`, `GovernancePage`, `AlertsPage`.

---

## 10. Parallel Work Opportunities

```
Week 0-N (continuous):
  Track 0A ─────────────────────────────────────────────┐
  Track 0B ─────────────────────────────────────────────┤
  Track 0C ─────────────────────────────────────────────┤ All parallel
  Track 0D ─────────────────────────────────────────────┤
  Track 0E ─────────────────────────────────────────────┘
  Track 3A (flow-canvas) ───────────────────────────────┘ parallel with all above

After DC-0 complete:
  DC-1 ──────────────────────────
  After DC-1: DC-2 →─────────────

After DC-2:
  DC-3 ∥ DC-4 ∥ DC-5 ∥ DC-6 ∥ DC-7 ∥ DC-8 ∥ DC-9

After AEP-P0 (bugs fixed):
  AEP-P6 ─────────────────── (can start immediately)
  AEP-P1 → AEP-P2
             After AEP-P2: AEP-P3 ∥ schema registry wiring
               After AEP-P3: AEP-P4 → AEP-P5
                 After AEP-P5: AEP-P7 ∥ AEP-P8

After DC-0 + AEP-P0 (YAPPC pre-phases — fully parallel with each other):
  YAPPC-Ph0.5 (DataCloud client wiring) ─────────────────┐
  YAPPC-Ph0.6 (Outbox relay) ─────────────────────────────┘ both parallel
  After Ph0.5 + Ph0.6: YAPPC-Ph1

After YAPPC-Ph6:
  YAPPC-Ph7 ∥ YAPPC-Ph8

After YAPPC-Ph7 + Ph8:
  YAPPC-Ph9 → YAPPC-Ph10
    After Ph10: YAPPC-Ph11 ∥ YAPPC-Ph12

UI (after Track 3A + backend):
  AEP UI pages 1–7: all parallel
  Data-Cloud UI areas 1–7: all parallel
  AEP UI ∥ Data-Cloud UI ∥ Track 3B (platform-shell)
```

---

## 11. Master New Files Checklist (All Tracks, Deduplicated)

### Track 0 — Platform Java

- [x] ~~`platform/java/event-cloud/…/EventCloudConnector.java`~~ **REMOVED** — use `EventLogStore` SPI instead
- [x] ~~`platform/java/event-cloud/…/EventCloudConnectorRegistry.java`~~ **REMOVED** — `AepEventCloudFactory` already handles this
- [x] ~~`platform/java/event-cloud/…/ConnectorBackedEventCloud.java`~~ **REMOVED** — already exists as `EventLogStoreBackedEventCloud`
- [x] ~~`platform/java/event-cloud/…/grpc/GrpcEventCloudConnector.java`~~ **RENAMED** → `GrpcEventLogStore.java` below
- [x] ~~`platform/java/event-cloud/…/http/HttpEventCloudConnector.java`~~ **RENAMED** → `HttpEventLogStore.java` below
- [ ] `platform/java/connectors/…/GrpcEventLogStore.java` (**replaces** `GrpcEventCloudConnector`)
- [ ] `platform/java/connectors/…/HttpEventLogStore.java` (**replaces** `HttpEventCloudConnector`)
- [ ] `platform/java/connectors/META-INF/services/…EventLogStore` (registers both impls)
- [ ] `platform/java/yaml-template/…/YamlTemplateEngine.java`
- [ ] `platform/java/yaml-template/…/TemplateContext.java`
- [ ] `platform/java/yaml-template/…/TemplateContextBuilder.java`
- [ ] `platform/java/schema-registry/…/SchemaRegistry.java`
- [ ] `platform/java/schema-registry/…/DataCloudSchemaRegistry.java`
- [ ] `platform/java/schema-registry/…/SchemaBootstrapper.java`
- [ ] `platform/java/agent-framework/…/memory/PersistentMemoryPlane.java`
- [ ] `platform/java/agent-framework/…/memory/JdbcMemoryItemRepository.java`
- [ ] `platform/java/agent-framework/…/memory/V001__memory_items.sql`
- [ ] `platform/java/agent-framework/…/loader/AgentDefinitionLoader.java`

### Track 1 — Data-Cloud

- [ ] `products/data-cloud/…/DataCloudEnvConfig.java`
- [ ] `products/data-cloud/…/DataCloudStartupValidator.java`
- [ ] `products/data-cloud/…/plugins/warm/WarmTierEventLogStore.java`
- [ ] `products/data-cloud/…/db/V001__create_event_log.sql`
- [ ] `products/data-cloud/…/grpc/EventLogGrpcService.java`
- [ ] `products/data-cloud/…/grpc/EventQueryGrpcService.java`
- [ ] `products/data-cloud/…/grpc/EventServiceGrpcService.java`
- [ ] `products/data-cloud/…/grpc/ProtobufMapper.java`
- [ ] `products/data-cloud/…/http/AgentRegistryRoutes.java`
- [ ] `products/data-cloud/…/http/CheckpointRoutes.java`
- [ ] `products/data-cloud/…/http/SseManager.java`
- [ ] `products/data-cloud/…/http/MemoryPlaneRoutes.java`
- [ ] `products/data-cloud/…/http/BrainRoutes.java`
- [ ] `products/data-cloud/…/http/LearningRoutes.java`
- [ ] `products/data-cloud/…/learning/LearningBridge.java`
- [ ] `products/data-cloud/…/http/AnalyticsRoutes.java`
- [ ] `products/data-cloud/…/http/EventStreamRoutes.java`
- [ ] `products/data-cloud/…/workflow/WorkflowRunRepository.java`

### Track 2 — AEP

- [ ] `products/aep/…/EnvConfig.java`
- [ ] `products/aep/…/AepAgentAdapter.java`
- [ ] `products/aep/…/AepContextBridge.java`
- [ ] `products/aep/…/PostgresCheckpointStorage.java`
- [ ] `products/aep/…/operator/AepOperatorCatalogLoader.java`
- [ ] `products/aep/…/DataCloudEventTypeRepository.java`
- [ ] `products/aep/…/db/V5__memory_plane.sql`
- [ ] `products/aep/…/learning/LearningScheduler.java`
- [ ] `products/aep/…/hitl/HitlQueue.java`
- [ ] `products/aep/…/hitl/HitlReviewItem.java`
- [ ] `products/aep/…/http/AgentController.java`
- [ ] `products/aep/…/http/HitlController.java`

### Track 2b — YAPPC

- [ ] `products/yappc/…/aep/DataCloudClientConfig.java` **(NEW — Ph0.5)**
- [ ] `products/yappc/…/di/DataCloudModule.java` **(NEW — Ph0.5)**
- [ ] `products/yappc/…/events/OutboxRelayService.java` **(NEW — Ph0.6)**
- [ ] `products/yappc/…/db/V010__outbox_relay_status.sql` **(NEW — Ph0.6)**
- [ ] `products/yappc/…/YappcIntegrationModule.java`
- [ ] `products/yappc/…/operators/PhaseTransitionValidatorOperator.java`
- [ ] `products/yappc/…/operators/PhaseStateManagerOperator.java`
- [ ] `products/yappc/…/operators/GateOrchestratorOperator.java`
- [ ] `products/yappc/…/operators/LifecycleStatePublisherOperator.java`
- [ ] `products/yappc/…/operators/AgentDispatchValidatorOperator.java`
- [ ] `products/yappc/…/operators/AgentExecutorOperator.java`
- [ ] `products/yappc/…/operators/ResultAggregatorOperator.java`
- [ ] `products/yappc/…/operators/MetricsCollectorOperator.java`
- [ ] `products/yappc/…/YappcOperatorProvider.java`
- [ ] `products/yappc/.../META-INF/services/…OperatorProvider`

### Track 3 — Platform TypeScript

- [ ] `platform/typescript/flow-canvas/` (extract from `products/yappc/frontend/libs/canvas/`)
- [ ] `platform/typescript/platform-shell/` (new)

### Track 4 — UI

**AEP UI new files**:

- [ ] `products/aep/ui/src/router.tsx`
- [ ] `products/aep/ui/src/pages/PipelineBuilderPage.tsx` (redesign)
- [ ] `products/aep/ui/src/pages/AgentRegistryPage.tsx`
- [ ] `products/aep/ui/src/pages/AgentDetailPage.tsx`
- [ ] `products/aep/ui/src/pages/MonitoringDashboardPage.tsx`
- [ ] `products/aep/ui/src/pages/PatternStudioPage.tsx`
- [ ] `products/aep/ui/src/pages/HitlReviewPage.tsx`
- [ ] `products/aep/ui/src/pages/LearningPage.tsx`
- [ ] `products/aep/ui/src/hooks/useAgents.ts`
- [ ] `products/aep/ui/src/hooks/useAgentMemory.ts`
- [ ] `products/aep/ui/src/hooks/usePipelineRuns.ts`
- [ ] `products/aep/ui/src/hooks/useHitlQueue.ts`
- [ ] `products/aep/ui/src/services/agent.service.ts`
- [ ] `products/aep/ui/src/services/memory.service.ts`
- [ ] `products/aep/ui/src/services/hitl.service.ts`
- [ ] `products/aep/ui/src/types/agent.types.ts`
- [ ] `products/aep/ui/src/types/memory.types.ts`
- [ ] `products/aep/ui/src/types/hitl.types.ts`

**Data-Cloud UI new files**:

- [ ] `products/data-cloud/ui/src/pages/EventExplorerPage.tsx`
- [ ] `products/data-cloud/ui/src/pages/MemoryPlaneViewerPage.tsx`
- [ ] `products/data-cloud/ui/src/pages/EntityBrowserPage.tsx`
- [ ] `products/data-cloud/ui/src/pages/DataFabricPage.tsx`
- [ ] `products/data-cloud/ui/src/pages/AgentPluginManagerPage.tsx`
- [ ] `products/data-cloud/ui/src/pages/BrainDashboardPage.tsx`
- [ ] `products/data-cloud/ui/src/services/event.service.ts`
- [ ] `products/data-cloud/ui/src/services/memory.service.ts`
- [ ] `products/data-cloud/ui/src/services/entity.service.ts`
- [ ] `products/data-cloud/ui/src/services/agent-registry.service.ts`
- [ ] `products/data-cloud/ui/src/services/brain.service.ts`
- [ ] `products/data-cloud/ui/src/data/fabric-topology.ts`

---

## 12. Master Modifications Checklist (All Tracks, Deduplicated)

### Java — Platform/AEP

- [ ] `AIAgentOrchestrationManagerImpl.java` — fix `.getResult()` (Bug 1) + wire `AgentTurnPipeline` + `Promises.reduceEx()` + event-source state
- [ ] `AgentRegistryService.java` — add `TenantId tenantId` first param to all methods (Bug 2)
- [ ] `AepConnectorModule.java` — all hardcoded strings → `EnvConfig.env()` (AEP-P6)
- [ ] `AepIngressModule.java` — Redis: `setTestOnBorrow(true)` + startup health check (Bug 3); env-driven
- [ ] `AepOrchestrationModule.java` — bind `CatalogAgentDispatcher` (from `libs:agent-dispatch`), `PersistentMemoryPlane`, `JdbcMemoryItemRepository`, `JdbcTaskStateRepository`, `LearningScheduler`, `ConsolidationPipeline`
- [ ] `AepCoreModule.java` — bind `AgentDefinitionLoader`, `DataCloudEventTypeRepository` (replaces `InMemoryEventTypeRepository`); call `SchemaBootstrapper.seedFromBundle()` at startup
- [ ] `AepHttpServer.java` — register `AgentController`, `HitlController`; add SSE routes
- [ ] `AepLauncher.java` — call `AepOperatorCatalogLoader.loadFromClasspath()` before HTTP start; register all catalog agents in `AgentRegistryService`
- [ ] `PipelineMaterializer.java` — inject `YamlTemplateEngine`; render before compile; throw on unresolvable operator (no skip)
- [ ] `EventSchemaValidator.java` — inject `SchemaRegistry`; remove `ConcurrentHashMap<String, String>` + inline JSON strings

### Java — Data-Cloud

- [ ] `DataCloudClientFactory.java` — add `fromEnvironment(Map<String,String> env)` static method — reads `DATACLOUD_MODE`, dispatches to existing `embedded()`/`standalone()`/`distributed()` **(DC-0)**
- [ ] `DataCloudStorageModule.java` — inject `DataCloudEnvConfig`; register `WarmTierEventLogStore`; `ConfigDrivenStorageRouter` WARM tier
- [ ] `DataCloudBrainModule.java` — wire `ContextGateway` → `AIIntegrationService` → `DefaultLLMGateway`; `LLMFactExtractor` to real gateway
- [ ] `DataCloudCoreModule.java` — add `LearningBridge`, `ConsolidationPipeline`, `SseManager`, all route modules
- [ ] `DataCloudHttpServer.java` — register all new route modules
- [ ] `DataCloudLauncher.java` — call `DataCloudStartupValidator.validate()` → start gRPC → start `LearningBridge` → start HTTP; use `DATACLOUD_HTTP_PORT` + `DATACLOUD_GRPC_PORT` env vars

### Java — YAPPC

- [ ] `AepLibraryClient.java` — replace `URLClassLoader` + reflection with direct typed `Aep.create(config)` call; add `products:aep:platform` as `compileOnly` dep; remove `AEP_LIBRARY_PATH` **(YAPPC-Ph1 step 1h)**
- [ ] `YappcAiModule.java` (or top-level DI module) — import `DataCloudModule`; expose `DataCloudClient` **(YAPPC-Ph0.5)**
- [ ] `YappcLifecycleService.java` — inject `DataCloudClient` for state reads; inject `AepClient` for event submission **(YAPPC-Ph0.5 + YAPPC-Ph7)**
- [ ] `YappcPersistenceModule.java` — bind + schedule `OutboxRelayService` **(YAPPC-Ph0.6)**
- [ ] `AiServiceModule.java` — delete stub `else` branch in `llmGateway()`; fail-fast if no provider; `CostEnforcingCompletionService` wrapper
- [ ] `AepCoreModule.java` (YAPPC's) — wire `DataCloudSchemaRegistry`, call `SchemaBootstrapper.seedFromBundle()` at module startup
- [ ] `YappcAgentSystem.java` — inject `DataSource`; replace `EventLogMemoryStore` → `PersistentMemoryPlane`; load all 590 agents; `addRoute()` per agent model; lazy dispatch via `CatalogAgentDispatcher`
- [ ] `YAPPCAgentBase.java` — remove static `globalAepEventPublisher`; add `EventCloud eventCloud` constructor param; all publishes via `eventCloud.append()`; update all 36 agent constructions
- [ ] `WorkflowMaterializer.java` — inject `YamlTemplateEngine`; render `canonical-workflows.yaml` before parse; route `agent-dispatch` → `CatalogAgentDispatcher`; route `human-approval` → `ApprovalController`
- [ ] `AepConnectorModule.java` (YAPPC's) — env-drive all connector config (same `EnvConfig` pattern)

### Java — Files to Delete

- [ ] `products/yappc/…/HttpAepEventPublisher.java`
- [ ] `products/yappc/…/AepEventBridge.java`

> **Note**: `AepLibraryClient.java` is modified (not deleted) — reflection replaced with typed API. `AEP_LIBRARY_PATH` env var is retired.

### TypeScript

- [ ] `products/aep/ui/src/main.tsx` — replace direct `<PipelineBuilderPage>` with `<RouterProvider router={router}>`
- [ ] `products/aep/ui/src/pages/PipelineBuilderPage.tsx` — URL param for pipeline ID; `useQuery` save/load; Run Now button
- [ ] `products/aep/ui/src/api/pipeline.api.ts` — add `runPipeline()`, `listPipelines()`
- [ ] `products/aep/ui/src/store/pipeline.store.ts` — add `currentRunAtom`
- [ ] `products/aep/ui/package.json` — add `@tanstack/react-query ^5.67.0`, `recharts ^2.14.1`, `@monaco-editor/react ^4.7.0`
- [ ] `products/data-cloud/ui/src/pages/WorkflowDesigner.tsx` — replace `@xyflow/react` → `@ghatana/flow-canvas`

---

## 13. Definition of Done (Union of All 4 Plans)

A phase is **complete** only when **all** of the following pass:

### Java

- [ ] All tests pass using `EventloopTestBase`; zero `.getResult()` calls
- [ ] `./gradlew spotlessApply` — zero warnings
- [ ] `./gradlew checkstyleMain pmdMain` — zero warnings
- [ ] All public classes have JavaDoc with all 4 required tags: `@doc.type`, `@doc.purpose`, `@doc.layer`, `@doc.pattern` (+ `@doc.gaa.memory` / `@doc.gaa.lifecycle` where applicable)
- [ ] All async ops chain via `.then()` / `.whenComplete()` — never `.getResult()`
- [ ] All connection strings from env via `EnvConfig` or `DataCloudEnvConfig`
- [ ] All GAA memory ops (`append()`, `JdbcMemoryItemRepository.save()`) wrapped in `Promise.ofBlocking(executor, …)`
- [ ] `tenantId` passed on every agent registry, memory, HITL, and EventLogStore call

### TypeScript / Frontend

- [ ] Zero `any` types; strict TypeScript compilation
- [ ] Every new page has minimum render test (React Testing Library)
- [ ] SSE connections use `@ghatana/realtime` `useEventStream` hook; auto-reconnect on error
- [ ] No raw `@xyflow/react` usage (replaced by `@ghatana/flow-canvas`)
- [ ] Canvas/UI components from `@ghatana/ui` only

### Static checks

- [ ] `grep -r "localhost" products/aep/platform/src/main/java/` → zero results in `AepConnectorModule`
- [ ] `grep -r "HttpAepEventPublisher\|AepEventBridge" src/` → zero results everywhere
- [ ] `grep -r "claude-3\|gpt-4\|llama3\|mistral" src/` → zero results in Java files
- [ ] `grep -r "ConcurrentHashMap" products/aep/platform/src/main/java/com/ghatana/orchestrator/` → zero results
- [ ] `grep -r "not_implemented" products/yappc/` → zero results
- [ ] `grep -r "globalAepEventPublisher" src/` → zero results

### Functional round-trips

- [ ] `POST /lifecycle/phase/advance` → observe pipeline execution in Prometheus (`lifecycle_phase_transitions_total` increments)
- [ ] `GET /api/v1/ai/agents` returns exactly 590 entries
- [ ] Start `new-feature` canonical workflow → all steps complete → `WORKFLOW_FINISHED` in EventLogStore
- [ ] `EVENT_CLOUD_TRANSPORT=unknown` → `IllegalStateException` listing known types
- [ ] No LLM provider env var set → service exits with clear `IllegalStateException` message

---

_End of Unified Master Plan — v1.0.0_

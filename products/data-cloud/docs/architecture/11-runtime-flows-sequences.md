# Runtime Flows and Sequences

See [`diagrams/sequence-http-entity-crud.mmd`](diagrams/sequence-http-entity-crud.mmd), [`diagrams/sequence-workflow-pipeline.mmd`](diagrams/sequence-workflow-pipeline.mmd), [`diagrams/sequence-feature-ingest.mmd`](diagrams/sequence-feature-ingest.mmd), and [`diagrams/runtime-topology.mmd`](diagrams/runtime-topology.mmd).

## Flow Inventory

| Flow | Trigger | Participants | Key Artifacts |
|---|---|---|---|
| Standalone bootstrap | process start | `DataCloudLauncher`, validator, `DataCloud.create`, HTTP bootstrap, gRPC bootstrap | env vars, `DataCloudLauncherSettings`, `DataCloudHttpLauncherBootstrap`, `DataCloudGrpcLauncherBootstrap` |
| HTTP entity CRUD | browser or service request | UI client, HTTP server, handlers, repositories, store adapters | `/api/v1/entities/**`, `EntityCrudHandler`, `CollectionRepository`, `EntityStore` |
| Pipeline/workflow save | UI workflow action | browser, workflow client, HTTP route, pipeline/checkpoint handler, persistence | `/api/v1/pipelines/**`, `/api/v1/checkpoints/**` |
| Event-to-feature ingest | event append or poll tick | event log, ingest worker, feature store, metrics, DLQ | `FeatureStoreIngestLauncher`, `EventLogStore`, `FeatureStoreService` |
| Agent registration | AEP/provider call | `DataCloudAgentRegistry`, `DataCloudClient`, event publisher | `agent-registry` collection, registry events |

## Standalone Bootstrap

### Implemented

1. `DataCloudLauncher.main()` validates environment via `DataCloudConfigValidator.fromEnvironment().validate()`.
2. Launcher resolves config and transport flags from `DataCloudLauncherSettings`.
3. `DataCloud.create(config)` creates the core client/runtime facade from `platform-launcher`.
4. HTTP bootstrap optionally creates brain, analytics, database, AI model, and feature store services.
5. `DataCloudHttpServer` starts and optionally exposes DB health probes.
6. gRPC bootstrap starts separately if enabled.

### Inferred

- Startup is intentionally modular by capability flag, but that also means two deployments using the same image can expose materially different route sets.

### Missing

- There is no single generated capability matrix that tells operators which env flags activate which endpoint families.

## HTTP Entity CRUD Flow

### Implemented

1. UI route loads `DataExplorer` or a related page from `ui/src/routes.tsx`.
2. Hook or page calls `ui/src/lib/api/collections.ts` or `ui/src/lib/api/client.ts`.
3. Browser request hits `DataCloudHttpServer`.
4. Request passes CORS, rate-limit, payload-size, and optional security middleware.
5. `EntityCrudHandler` delegates into Data Cloud client and repository/store layers.
6. Persistence lands in entity or metadata tables via `platform-entity` and `platform-launcher` infrastructure.
7. Response returns JSON to the UI.

### Errors and Failures

- Payload oversize returns HTTP 413.
- Missing optional backend integrations can cause 501/503 behavior for certain specialized routes.
- Schema validation routes explicitly surface validation failures.

## Pipeline / Workflow Flow

### Implemented

1. User enters `/pipelines` UI.
2. UI calls workflow adapters in `ui/src/lib/api/workflows.ts`.
3. Runtime routes in `DataCloudHttpServer` dispatch to `PipelineCheckpointHandler`.
4. Handler persists or queries pipeline/checkpoint state through runtime/domain services in `platform-launcher`.

### Inferred

- The workflow builder UI is ahead of the backend naming cleanup. “Workflow” remains a user-facing label while “pipeline” is the wire contract.

## Event to Feature Store Flow

### Implemented

1. Event is appended to `EventLogStore`.
2. `FeatureStoreIngestLauncher` polling loop reads a batch for each tenant.
3. Each event is transformed into one or more ML features.
4. Circuit breaker guards writes to `FeatureStoreService`.
5. On permanent failure, entry is written to DLQ.
6. Per-tenant offset is advanced after successful processing.

### Observability Hooks

- Metrics are emitted through `MetricsCollector`.
- Worker logs startup, retries, and DLQ/circuit-breaker behavior with SLF4J.

### Retry / Timeout

- Retry delay and poll delay are env-configurable.
- Circuit breaker resets after configured timeout.

## Agent Registration Flow

### Implemented

1. Upstream AEP/provider code calls `DataCloudAgentRegistry.register()`.
2. Registry serializes descriptor/config into a map.
3. `DataCloudClient.createEntity()` persists an entity into the `agent-registry` collection.
4. In-memory cache updates after successful persist.
5. `RegistryEventPublisher` emits a lifecycle event asynchronously.

### Inferred

- Registry events are audit and discovery signals, not the source of truth for live in-process agent instances.

## Config Loading Flow

### Implemented

1. `ConfigLoader` resolves `config/collections/<tenant>/<collection>.yaml` or classpath defaults.
2. YAML is deserialized into raw config models.
3. Similar lookup behavior exists for plugins under `config/plugins/**`.

### Missing

- Interpolation support is partially scaffolded in `ConfigLoader`, but the current `interpolateEnvVars` implementation returns the raw object unchanged.

## Runtime Flow Findings

| Finding | Evidence | Impact |
|---|---|---|
| Capability flags materially change runtime topology | `DataCloudHttpLauncherBootstrap.java` | harder environment parity |
| Bootstrap is concrete, but capability documentation is diffuse | launcher plus infra plus docs | operator confusion |
| Async worker flow is more explicit than some HTTP flows | feature-store-ingest has clear circuit breaker and DLQ | uneven implementation maturity |

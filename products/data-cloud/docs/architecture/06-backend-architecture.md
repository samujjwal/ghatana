# Backend Architecture

See [`diagrams/runtime-topology.mmd`](diagrams/runtime-topology.mmd), [`diagrams/backend-http-runtime-components.mmd`](diagrams/backend-http-runtime-components.mmd), [`diagrams/backend-agent-registry-components.mmd`](diagrams/backend-agent-registry-components.mmd), and [`diagrams/backend-feature-store-ingest-components.mmd`](diagrams/backend-feature-store-ingest-components.mmd).

## Backend Service Inventory

| Backend Unit | Purpose | Inbound Paths | Outbound Paths |
|---|---|---|---|
| `launcher` | Standalone deployable HTTP/gRPC app | `main()`, HTTP, gRPC | `platform-launcher`, DB/brokers/storage |
| `platform-launcher` | Runtime/domain/infra kernel | consumed by launcher and other modules | plugins, storage connectors, DI, gRPC, metrics |
| `platform-api` | Controller/app-service layer | library use and tests | shared domain/config/analytics modules |
| `agent-registry` | Durable agent metadata backend | provider calls from AEP/runtime | Data Cloud entity/event APIs |
| `feature-store-ingest` | Event-log tailing worker | `main()`, scheduler | EventLogStore, FeatureStoreService, DLQ |

## Standalone Launcher

### Implemented

- `DataCloudLauncher.java` is the main entry point.
- It validates environment config, creates a `DataCloudClient`, starts HTTP and/or gRPC transports, and blocks the main thread.
- `DataCloudHttpLauncherBootstrap` wires optional brain, analytics, database-backed AI/model services, Prometheus registry, and HTTP server startup.
- `DataCloudGrpcLauncherBootstrap` starts a gRPC server backed by the event log store.

### Inferred

- Standalone mode is the repo’s most concrete runtime path; `platform-api` is not yet the sole transport assembly point.

## HTTP Runtime

### Implemented

- `launcher/http/DataCloudHttpServer.java` is the concrete route registry for health, entities, events, pipelines, checkpoints, memory, brain, learning, analytics, reports, models, features, AI assist, voice, governance, SSE, and WebSocket.
- Routes delegate to specialized handlers in `launcher/http/handlers/*`.
- Optional features are activated by builder methods such as `withReportService`, `withAiModelManager`, `withFeatureStoreService`, `withOpenSearchConnector`, and `withSchemaValidator`.
- Middleware chain applies CORS, rate limiting, payload-size filtering, content-type filtering, and optionally the `DataCloudSecurityFilter`.

### Missing

- `DataCloudHttpLauncherBootstrap` does not enable `withApiKeyResolver`, `withPolicyEngine`, or `withAuditService`, so standalone security middleware is inactive unless another caller wires it.

## gRPC Runtime

### Implemented

- `launcher/grpc/DataCloudGrpcServer.java` exposes `EventLogGrpcService`, `EventQueryGrpcService`, and `EventServiceGrpcService`.
- Tenant propagation relies on `TenantGrpcInterceptor`.
- gRPC is focused on event ingest/query rather than full parity with HTTP surfaces.

## Platform API Module

### Implemented

- `platform-api` contains controllers, DTOs, and app services for collections, reports, memory, patterns, webhook delivery, autonomy, and GraphQL mutation support.
- Build comments explicitly say it is a strict subset extracted from `platform-launcher`.

### Missing

- A repo-wide usage search shows controller construction primarily in tests; live launcher routing is still assembled in `launcher/http/DataCloudHttpServer.java`.
- There is no checked-in `GraphQLQueries` implementation, only `GraphQLMutations.java` and GraphQL tests.

## Platform Launcher Module

### Implemented

- `platform-launcher` holds:
  - `DataCloud.java` public factory/embedded client
  - DI modules (`di/*`)
  - infrastructure adapters (JPA, Redis cache, object storage, search, time-series, query telemetry)
  - embedded/distributed/edge deployment abstractions
  - gRPC service implementations
  - backpressure, resilience, migration, security, observability, workflow support

### Weakness

- This module currently mixes runtime composition, client APIs, storage connectors, distributed coordination, migration logic, embedded modes, and observability concerns.

## Agent Registry

### Implemented

- `DataCloudAgentRegistry` persists agent descriptors/configs through `DataCloudClient`, maintains an in-memory write-through cache, and publishes lifecycle events.
- Additional repositories cover releases, rollouts, evaluation results, and memory namespaces.

### Inferred

- Agent registry is intended as a Data Cloud persistence provider behind AEP-owned registry APIs, not a user-facing Data Cloud control plane.

## Feature Store Ingest

### Implemented

- `FeatureStoreIngestLauncher` tails `EventLogStore`, extracts features, writes to `FeatureStoreService`, tracks offsets per tenant, applies a circuit breaker, and uses a DLQ for failures.
- Build comments already acknowledge an undesirable dependency on `platform-launcher` just to obtain `WarmTierEventLogStore`.

## Persistence And Async Behavior

| Concern | Implemented Evidence |
|---|---|
| Promise-based async interfaces | `spi`, repository ports, handlers, config loader |
| Blocking isolation | `Promise.ofBlocking`, dedicated executors, virtual-thread executors in runtime code |
| DB persistence | JPA repository impls, Flyway migrations, HikariCP |
| Event persistence | `EventLogStore`, Kafka plugins, warm-tier/event durability services |
| Search/analytics | OpenSearch, ClickHouse, `AnalyticsQueryEngine` |

## Error Handling / Retry / Idempotency

**Implemented**
- HTTP filters enforce payload and rate-limit protections.
- Feature ingest includes circuit breaker and DLQ behavior.
- Event APIs expose idempotency key support in `EventLogStore.EventEntry`.
- gRPC services translate validation/processing failures into status codes.

**Missing**
- There is no single canonical resilience policy document or runtime policy registry tying retries/timeouts/idempotency together.

## Backend Findings

| Finding | Evidence | Impact |
|---|---|---|
| Transport split is incomplete | `platform-api` extraction comments + launcher-owned routing | duplicate API architecture |
| Runtime security is optional | bootstrap does not wire security filter | deployment-dependent protection |
| `platform-launcher` is too broad | 237 main files across many concerns | hard-to-reason dependency surface |
| Some architecture tests are placeholders | `platform-api/src/test/.../ModuleBoundaryTest.java` uses trivial assertions | low-confidence guardrails |

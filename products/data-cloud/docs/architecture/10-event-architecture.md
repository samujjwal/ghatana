# Event Architecture

See [09-api-contract-architecture.md](09-api-contract-architecture.md), [11-runtime-flows-sequences.md](11-runtime-flows-sequences.md), [`diagrams/event-topology.mmd`](diagrams/event-topology.mmd), and [`diagrams/producer-consumer-map.mmd`](diagrams/producer-consumer-map.mmd).

## Event Subsystems

| Subsystem | Producers | Consumers | Storage / Broker | Evidence |
|---|---|---|---|---|
| Core event log | HTTP event routes, gRPC event services, internal appenders | HTTP reads, gRPC reads, feature-store ingest, replay/query services | `spi/EventLogStore.java`, warm tier stores, Kafka plugin paths | `launcher/http/EventHandler.java`, `launcher/grpc/DataCloudGrpcServer.java`, `platform-launcher/**`, `platform-plugins/**` |
| Registry lifecycle events | `RegistryEventPublisher` | AEP and diagnostics readers | Data Cloud collection/event stream | `agent-registry/event/RegistryEventPublisher.java` |
| Learning/brain stream | learning bridge and learning handlers | UI stream pages and internal learning consumers | SSE/WebSocket + product runtime memory/brain services | `DataCloudLearningBridge`, `LearningHandler`, `/api/v1/learning/stream` |
| Feature ingestion | core event log entries | `FeatureStoreIngestLauncher` | `EventLogStore` plus DLQ | `feature-store-ingest/**` |
| Plugin-backed streaming | storage and streaming plugins | runtime readers and downstream consumers | Kafka, Redis, possibly OpenSearch-backed paths | `platform-plugins/**`, build manifests |

## Implemented

- `spi/src/main/java/com/ghatana/datacloud/spi/EventLogStore.java` is the central port for event append/read semantics.
- HTTP event endpoints live in `DataCloudHttpServer.java`.
- gRPC event endpoints live in `DataCloudGrpcServer.java` and `platform-launcher/src/main/java/com/ghatana/datacloud/grpc/**`.
- `feature-store-ingest` polls `EventLogStore`, tracks offsets per tenant, extracts features, and writes to `FeatureStoreService`.
- `agent-registry` publishes lifecycle events through `RegistryEventPublisher` after register/deregister operations.

## Inferred

- Event storage abstraction is meant to support multiple backends and runtime modes. The combination of warm-tier store code, Kafka plugins, and gRPC/HTTP surfaces suggests Data Cloud wants one logical event log with several transport and persistence implementations.

## Missing

- No single product-local event catalog enumerates event types, retention guarantees, ordering rules, and ownership.
- DLQ behavior is explicit for feature ingestion, but generalized retry/DLQ policy is not centrally documented for the rest of the platform.

## Recommended

- Treat the event log as a first-class subsystem with a maintained event catalog, especially because it already underpins streaming, gRPC, registry, and feature pipelines.

## Producer / Consumer Map

| Producer | Event / Stream | Consumer | Notes |
|---|---|---|---|
| `EventHandler` | entity/domain events appended through event APIs | `FeatureStoreIngestLauncher`, HTTP readers, gRPC readers | multi-tenant offsets |
| `RegistryEventPublisher` | `agent.registered`, `agent.deregistered` | AEP-side services, audit consumers | fire-and-forget side effect |
| `DataCloudLearningBridge` | learning signal stream | `/api/v1/learning/stream`, brain services | optional runtime feature |
| Runtime SSE broadcaster | `/events/stream` and entity query streams | browser subscribers | non-durable client-facing stream |
| WebSocket endpoint | `/ws` | browser/dashboard subscribers | transport channel, not canonical schema owner |

## Routing and Wiring

### Implemented

- HTTP route registration is explicit in `DataCloudHttpServer.java`.
- gRPC service registration is explicit in `DataCloudGrpcServer.java`.
- Feature ingestion worker starts from `FeatureStoreIngestLauncher.main` and chooses `inmemory` or `postgres` ingest mode from env.
- Agent registry events are emitted from persistence callbacks and do not block the caller.

### Inferred

- Plugin routing for Kafka/Redis/search backends is assembled in `platform-launcher` and `platform-plugins`, not from a single top-level event runtime configuration file.

## Retry, Ordering, Idempotency

| Concern | Evidence | Assessment |
|---|---|---|
| Feature ingestion retries | `FeatureStoreIngestLauncher` reschedule logic and retry delay env | implemented |
| Feature ingestion DLQ | `DeadLetterQueue` in `FeatureStoreIngestLauncher` | implemented |
| Write protection | `CircuitBreaker` around feature-store writes | implemented |
| Event idempotency | `EventLogStore.EventEntry` includes idempotency-oriented fields | implemented / port-level |
| Ordering | offset-tracking per tenant in ingest worker | inferred ordering by tenant stream |
| Global failure handling | no single policy registry found | missing |

## Eventual Consistency Implications

**Implemented**
- Feature ingestion is asynchronous relative to event appends, so feature-store state lags the source event log.
- Registry event publishing is fire-and-forget after persistence, so event emission failure does not roll back registration.

**Inferred**
- UI streaming endpoints likely expose near-real-time state rather than fully serialized, transactionally consistent views across all backends.

## Event Findings

| Finding | Evidence | Impact |
|---|---|---|
| Event log is a core platform primitive, but documentation is fragmented | `spi`, `launcher`, `platform-launcher`, `feature-store-ingest`, `agent-registry` | hard to reason about guarantees |
| Async safety is implemented feature-by-feature, not centrally | circuit breaker and DLQ only obvious in feature ingest | inconsistent reliability posture |
| HTTP and gRPC event contracts are healthier than GraphQL or alternate OpenAPI surfaces | concrete launcher registration | likely canonical integration path |

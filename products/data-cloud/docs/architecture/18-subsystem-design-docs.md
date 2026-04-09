# Subsystem Design Docs

See [06-backend-architecture.md](06-backend-architecture.md), [07-shared-library-architecture.md](07-shared-library-architecture.md), and [17-deployment-environments.md](17-deployment-environments.md).

## Standalone Runtime

### Overview

The standalone runtime is the deployable Data Cloud process built from `launcher` on top of `platform-launcher` and `platform-api`.

### Responsibilities

- validate config
- start HTTP and gRPC transports
- assemble optional brain, analytics, AI, and DB-backed services
- expose health/metrics endpoints

### Boundaries

- owns transport startup
- does not fully own all runtime/domain logic; much of that remains in `platform-launcher`

### Internal Components

- `DataCloudLauncher`
- `DataCloudHttpLauncherBootstrap`
- `DataCloudGrpcLauncherBootstrap`
- `DataCloudHttpServer`
- `DataCloudGrpcServer`

### Interfaces / Contracts

- HTTP `/api/v1/**`
- gRPC event services
- env-based startup flags

### Data Ownership

- no primary ownership; delegates to runtime stores and domain modules

### Runtime Behavior

- capability-gated startup by environment
- separate HTTP and gRPC transport toggles

### Event Interactions

- serves event append/query via HTTP and gRPC
- exposes SSE/WebSocket streams

### Failure Modes

- invalid config stops startup
- optional subsystems can fail closed or degrade depending on bootstrap path

### Scaling / Performance Considerations

- ActiveJ runtime plus virtual-thread executor for blocking work
- HPA support in Helm

### Security Considerations

- security filter exists but is not always wired

### Observability Considerations

- metrics and health endpoints are first-class

### Known Gaps / Issues

- transport/API split incomplete
- packaging ambiguity around Docker jar

### Recommended Improvements

- complete module split or simplify back to fewer explicit modules

## Frontend UI

### Overview

React 19 + Vite SPA under `ui`, using a new simplified route map with legacy aliases retained.

### Responsibilities

- data exploration
- workflow management
- trust/insights pages
- plugin/agent views

### Boundaries

- browser-only shell
- depends on Data Cloud HTTP APIs and shared UI packages

### Internal Components

- `routes.tsx`
- `layouts/DefaultLayout.tsx`
- `pages/*`
- `lib/api/*`
- hooks and state stores

### Interfaces / Contracts

- HTTP JSON endpoints
- SSE/WebSocket clients

### Data Ownership

- no source-of-truth ownership; client/cache only

### Runtime Behavior

- lazy-loaded route tree
- TanStack Query + Jotai state

### Event Interactions

- event explorer and realtime clients

### Failure Modes

- duplicate API layers can route identical user intent through different contracts

### Scaling / Performance Considerations

- lazy chunks and loading/error boundaries

### Security Considerations

- token storage is transitional and header-based

### Observability Considerations

- frontend-specific telemetry was not prominent in inspected product-local code

### Known Gaps / Issues

- stale E2E routes
- unused `AppShell`
- legacy client layers

### Recommended Improvements

- standardize on `lib/api` and align E2E with `/data`

## Shared Domain / Config Layer

### Overview

`spi`, `platform-entity`, `platform-event`, `platform-config`, and `platform-analytics` form the reusable product foundation.

### Responsibilities

- define domain ports and models
- define config loading/validation
- define analytics/reporting primitives

### Boundaries

- should be reusable without pulling all runtime adapters

### Internal Components

- repository ports
- JPA models
- event models
- config loaders
- analytics engines

### Interfaces / Contracts

- Promise-based repository APIs
- YAML config contracts

### Data Ownership

- collection metadata and entity/event modeling

### Runtime Behavior

- consumed by runtime and workers

### Event Interactions

- event schemas and ports

### Failure Modes

- duplicated model layers create contract drift

### Scaling / Performance Considerations

- Promise-based interfaces and storage profile concepts

### Security Considerations

- tenant scoping and validation are embedded in models and repos

### Observability Considerations

- config and analytics layers rely on caller-provided metrics/logging

### Known Gaps / Issues

- placeholder extraction modules complicate the shared-library story

### Recommended Improvements

- narrow what “shared” means and keep only modules with real consumers and stable APIs

## Agent Registry Provider

### Overview

Durable persistence backend for AEP registry metadata and lifecycle events.

### Responsibilities

- persist descriptors/configs
- cache live references
- publish registry events

### Boundaries

- not a product-local user-facing control plane

### Internal Components

- `DataCloudAgentRegistry`
- `RegistryEventPublisher`
- release / rollout / evaluation / memory repositories

### Interfaces / Contracts

- `AgentRegistry` provider contract
- Data Cloud entity/event APIs

### Data Ownership

- durable metadata only, not live agent reconstruction

### Runtime Behavior

- write-through cache plus async event emission

### Event Interactions

- `agent.registered`, `agent.deregistered`

### Failure Modes

- event publish failures are logged but do not block registration

### Scaling / Performance Considerations

- TTL/LRU in-memory cache

### Security Considerations

- depends on upstream AEP ownership and Data Cloud tenant isolation

### Observability Considerations

- good log coverage, limited dedicated metrics visible in inspected code

### Known Gaps / Issues

- depends on heavy `platform-launcher`

### Recommended Improvements

- extract a narrower persistence client/store layer

## Feature Store Ingest

### Overview

Background worker that tails the event log and writes derived features.

### Responsibilities

- poll per-tenant event streams
- extract features
- write to feature store
- manage offsets, retries, DLQ

### Boundaries

- background projection worker, not a user-facing API

### Internal Components

- `FeatureStoreIngestLauncher`
- config/env parsing
- circuit breaker
- DLQ

### Interfaces / Contracts

- `EventLogStore`
- `FeatureStoreService`

### Data Ownership

- owns derived feature projection flow, not source events

### Runtime Behavior

- scheduled polling loops on ActiveJ eventloop plus scheduler

### Event Interactions

- consumes core event stream

### Failure Modes

- write failures, extraction failures, downstream outages

### Scaling / Performance Considerations

- batch size, retry delay, poll delay are configurable

### Security Considerations

- inherits event/store credentials and tenant context

### Observability Considerations

- metrics, logging, DLQ visibility

### Known Gaps / Issues

- depends on heavyweight `platform-launcher` just for warm-tier event store access

### Recommended Improvements

- extract warm-tier event store into a smaller shared module

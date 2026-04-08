# Integration Platform Architecture

> **Owner:** Platform Team
> **Status:** Active
> **Scope:** Shared integration architecture for Java platform modules, shared services, and product composition roots
> **Audience:** Platform engineers, product backend engineers, architecture reviewers
> **Authority Level:** Binding for new shared integration work; migration guidance for existing modules
> **Supersedes:** None
> **Last Reviewed:** 2026-04-04

## Decision

Ghatana adopts a three-tier integration architecture:

1. Business ports for default product-facing integration needs
2. Capability ports for shared infrastructure libraries that need stronger semantics
3. Native escape hatches for explicitly approved hot paths

We will implement that architecture in a repo-native way:

- Reuse existing `platform/java/*`, `platform/contracts`, `platform/java/testing`, `shared-services/*`, and approved SPI modules
- Refactor broad modules into clearer port/adapter packages before creating new Gradle modules
- Add new top-level modules only when the [module admission checklist](./MODULE_ADMISSION_CHECKLIST.md) is satisfied and at least one concrete consumer exists

This preserves the stricter production-grade plan without fighting the current Ghatana repository shape or increasing module sprawl prematurely.

## Repo-Native Mapping

The earlier greenfield target structure maps to the current repo as follows.

| Proposed area | Ghatana-native location | Direction |
|---|---|---|
| `foundation/config` | `platform/java/config` | Reuse directly |
| `foundation/errors` | `platform/java/core` first, extract only if needed | Reuse then tighten taxonomy |
| `foundation/telemetry` | `platform/java/observability` | Reuse directly |
| `foundation/serialization` | `platform/contracts` plus shared codecs in `platform/java/core` or owning module packages | Consolidate, do not pre-create a new module yet |
| `foundation/lifecycle` | `platform/java/runtime`, `platform/java/kernel`, `platform/java/connectors` | Reuse and standardize |
| `integrations/sql-port` | `platform/java/database` | Refactor in place |
| `integrations/cache-port` | `platform/java/distributed-cache` | Refactor in place |
| `integrations/coordination-port` | Start in `platform/java/distributed-cache` or `platform/java/kernel-persistence` based on first consumer | Add in place |
| `integrations/messaging-port` | `platform/java/connectors` with narrow use of `products:data-cloud:spi` for cross-cutting event types | Refactor in place |
| `integrations/blob-port` | Do not create until a real shared consumer exists | Defer |
| `capability-ports/*` | Package-level capability ports inside owning platform module first | Add in place |
| `adapters/*` | Adapter packages inside owning platform module | Reuse and thin down |
| `native-extensions/*` | Package-level native subpackages inside owning platform module first | Add in place, rare by policy |
| `contracts/*` | Shared contract suites in owning module tests plus reusable harnesses in `platform/java/testing` | Reuse existing test module |
| `fixtures/*` | `platform/java/testing` and `platform/java/testing/testcontainers` | Consolidate here |

## Current Assets To Reuse

These modules already cover a meaningful part of the target architecture and should be evolved rather than replaced.

### Foundation

- `platform/java/config`: typed config sources, precedence, validation, reload support
- `platform/java/observability`: Micrometer, OTel, health checks, queue/lag/latency metrics
- `platform/java/runtime`: shared lifecycle and runtime support
- `platform/java/kernel`: lifecycle-aware service contracts already used by kernel consumers
- `platform/contracts`: OpenAPI, Protobuf, and schema artifacts for shared serialization contracts
- `platform/java/testing`: common test utilities, ActiveJ test bases, and a natural home for shared real-infra fixtures

### Existing Ports and Adapters

- `platform/java/distributed-cache`: `DistributedCachePort`, in-memory adapter, Redis adapter, write-through cache
- `platform/java/connectors`: `Connector`, `EventSource`, `EventSink`, Kafka source/sink/connector implementations
- `platform/java/database`: connection factories, pooling, Redis/JDBC helpers, Flyway support, transaction utilities
- `platform/java/kernel-persistence`: concrete durable adapters such as `PostgresAuditTrailPersistence` and `RedisKernelConfigResolver`
- `products:data-cloud:spi`: approved cross-cutting SPI for storage/event abstractions used by platform modules

### Existing Real-Infra Testing

- `shared-services/auth-gateway` already runs PostgreSQL-backed Testcontainers integration tests for `JdbcCredentialStore`
- `shared-services/user-profile-service` already uses PostgreSQL-backed Testcontainers integration tests
- `platform/java/testing/testcontainers` already has a TypeScript-side PostgreSQL helper that should be folded into the shared fixture strategy instead of duplicated elsewhere

## Target Responsibilities By Existing Module

### `platform/java/config`

Keep as the canonical home for:

- typed config loading
- source precedence
- validation
- profile/env composition

Required upgrades:

- publish canonical adapter config models for shared integration modules
- block raw environment lookups in adapters except inside composition roots and config loaders

### `platform/java/observability`

Keep as the canonical home for:

- tracing
- latency/error metrics
- queue depth and lag metrics
- health/readiness probes

Required upgrades:

- standard metric and span names for adapters
- adapter readiness checklist that fails if standard telemetry is missing
- batch-size and payload-size metrics for high-volume integrations

### `platform/java/database`

This module is the right home for SQL-oriented business and capability ports, but its current surface is too broad and partially too generic.

Direction:

- freeze new use of generic interfaces such as `DatabaseClient` for shared product work
- introduce narrow business ports inside this module first:
  - `QueryExecutor`
  - `TransactionalExecutor`
  - `TransactionHandle`
- introduce capability ports only when justified:
  - `BulkUpsertStore`
  - `OutboxStore`
  - `CheckpointStore` if it proves cross-product
- keep vendor adapters thin:
  - Postgres/JDBC adapter packages
  - connection pool and migration helpers

### `platform/java/distributed-cache`

This is the correct starting point for cache and coordination work, but the existing port is too narrow for the production plan.

Direction:

- evolve `DistributedCachePort` toward business ports:
  - `KeyValueCache`
  - `LeaseCoordinator`
- add batched and pipelined operations as first-class capabilities:
  - `getMany`
  - `setMany`
  - `deleteMany`
  - optional `PipelinedKeyValueOperations`
- keep in-memory and Redis adapters, but separate cache semantics from lease/coordination semantics

### `platform/java/connectors`

This is the natural home for messaging ports and Kafka adapters, but its current `EventSource` and `EventSink` contracts are adapter-level and not sufficient as the long-term product-facing API.

Direction:

- keep `Connector`, `EventSource`, and `EventSink` as low-level lifecycle or adapter abstractions
- add business ports in this module for product and platform use:
  - `BatchEventPublisher`
  - `ConsumerLoop`
- add capability ports only when needed:
  - `OrderedPartitionPublisher`
  - `CheckpointStore` if messaging-owned
- retain Kafka-specific adapters behind those ports
- continue allowing `products:data-cloud:spi` only for pure shared event/storage abstractions already covered by the existing allowlist

### `platform/java/kernel-persistence`

Keep this module as the home for durable adapters used by kernel-oriented cross-cutting services.

Direction:

- reuse existing Postgres and Redis persistence adapters
- promote only genuinely cross-product persistence capabilities into shared ports
- keep product-specific persistence logic in product modules until a second real consumer appears

### `platform/java/testing`

This becomes the canonical home for shared integration fixtures and contract harnesses.

Direction:

- add reusable JUnit/Testcontainers fixture support here
- keep owning-module contract tests close to the code they verify
- place shared fixture lifecycle, random naming, diagnostics, and cleanup helpers here
- converge Java and TypeScript real-infra helpers under one documented fixture strategy

## Business Ports

Business ports are the default dependency surface for product code.

Rules:

- products may depend on business ports
- products may not import Redis, Kafka, JDBC, Hikari, Jedis, Kafka client, or similar vendor SDKs directly unless explicitly approved
- composition roots bind ports to adapters
- batched operations are mandatory when the backend benefits materially from batching

Initial business ports by module:

| Module | Business ports |
|---|---|
| `platform/java/database` | `QueryExecutor`, `TransactionalExecutor` |
| `platform/java/distributed-cache` | `KeyValueCache`, `LeaseCoordinator` |
| `platform/java/connectors` | `BatchEventPublisher`, `ConsumerLoop` |
| `TBD only after demand` | `BlobStore` |

## Capability Ports

Capability ports are for shared platform or infrastructure-heavy libraries that need stronger semantics than business ports expose.

Rules:

- default location is inside an existing owning module, not a new module
- every capability port must document why the business port is insufficient
- every capability port must have a real production use case before creation
- no capability port may simply mirror a vendor SDK

Likely initial candidates:

- `BulkUpsertStore` in `platform/java/database`
- `PipelinedKeyValueOperations` in `platform/java/distributed-cache`
- `OrderedPartitionPublisher` in `platform/java/connectors`
- `CheckpointStore` in the module where the first two real consumers emerge
- `OutboxStore` in `platform/java/database` or `platform/java/kernel-persistence` if it becomes cross-product

## Native Escape Hatches

Native escape hatches remain allowed but rare.

Repo-native rule:

- implement them as package-level native extensions inside the owning module first
- do not create dedicated Gradle modules for native escape hatches until there are multiple approved uses

Approval requirements:

- architecture review
- benchmark evidence
- named owner
- documented fallback or operational reason

Examples:

- Kafka native producer access inside `platform/java/connectors`
- Redis native pipeline access inside `platform/java/distributed-cache`
- JDBC/Postgres native access inside `platform/java/database`

## Thin Adapter Standard

Every adapter in the shared platform must remain thin.

Allowed responsibilities:

- port-to-vendor translation
- lifecycle management
- typed config application
- telemetry emission
- standardized serialization
- deterministic error mapping
- adapter-level invariants

Forbidden responsibilities:

- product business logic
- multi-resource orchestration across unrelated systems
- leaking vendor exception types across business-port boundaries
- silently dropping batching, ordering, or durability semantics

## Performance Is Part Of Correctness

Functional correctness is necessary but insufficient.

Every shared adapter must define:

- latency expectations for core operations
- batch-sensitivity expectations
- concurrency and backpressure expectations
- ordering guarantees where relevant

These performance checks should be implemented as smoke/regression guards in CI, not as heavyweight benchmarks on every PR.

## Contract Testing And Fixture Rules

Ghatana will standardize around real-infrastructure contract tests using Testcontainers-backed fixtures for shared adapters and narrow integration tests.

### Functional contract scope

- cache: get, put, batch ops, TTL, overwrite, serialization, namespace isolation
- coordination: acquire, renew, release, expiration, owner validation
- SQL: transaction boundaries, rollback, conflict mapping, batch execution
- messaging: batch publish, ordering scope, headers, checkpoint behavior, retry mapping

### Performance smoke scope

- p95 latency for representative operations
- minimum throughput floor for batch-sensitive adapters
- moderate concurrency behavior
- backpressure behavior

### Shared fixture rules

Implement in `platform/java/testing` and its documented subpackages:

- random container and network naming where applicable
- random host ports only, never fixed localhost ports
- unique topic/schema/table/key prefixes
- unique consumer group ids
- readiness checks before test execution
- deterministic cleanup
- automatic failure log capture

### Test placement rule

- shared reusable fixture code goes in `platform/java/testing`
- contract tests stay with the owning shared module by default
- product narrow integration tests use production adapters plus fixture endpoints

## Product Wiring Rules

Composition roots remain the only binding point.

Allowed:

- product code depends on shared port packages or approved SPI
- composition roots build adapter instances from typed config

Not allowed:

- direct vendor SDK instantiation in product business code
- reaching through a port into adapter internals
- ad hoc product-local vendor configuration when a shared adapter/config model exists

Immediate migration targets:

- move shared-services and products away from direct `HikariConfig`, `HikariDataSource`, raw Jedis/Kafka construction where a shared adapter or platform factory can own that behavior
- keep product-local direct integrations only until a shared port/adapter is available and justified

## CI And Governance

This plan extends existing governance rather than replacing it.

Existing hooks to reuse:

- `scripts/check-architecture-compliance.js`
- `eslint-rules/ghatana-architecture-rules.js`
- `gradle/platform-boundary-check.gradle`
- [Root module registry in settings.gradle.kts](../settings.gradle.kts)
- [Module Admission Checklist](./MODULE_ADMISSION_CHECKLIST.md)

New enforcement to add incrementally:

1. no new vendor SDK imports outside approved adapter, fixture, or native-extension packages
2. no new shared adapter without a functional contract suite
3. no new shared adapter without telemetry
4. no new capability port without documented justification
5. no new native escape hatch without benchmark evidence

## Rollout Plan

### Wave 1: SQL inside existing modules

Deliver in `platform/java/database` and `platform/java/testing`:

- `QueryExecutor`
- `TransactionalExecutor`
- Postgres adapter package
- SQL contract tests
- shared PostgreSQL fixture helpers

Exit criteria:

- at least one existing direct JDBC/Hikari consumer migrated
- transaction semantics documented and tested
- latency smoke baselines captured

### Wave 2: Cache and coordination

Deliver in `platform/java/distributed-cache` and `platform/java/testing`:

- `KeyValueCache`
- `LeaseCoordinator`
- Redis cache adapter package
- Redis coordination adapter package
- batch and pipeline-capable operations
- Redis contract tests and fixture helpers

Exit criteria:

- cache vs lease semantics split cleanly
- batch operations available
- at least one consumer migrated from direct Redis-style logic

### Wave 3: Messaging

Deliver in `platform/java/connectors` and `platform/java/testing`:

- `BatchEventPublisher`
- `ConsumerLoop`
- Kafka publisher and consumer adapter packages
- messaging contract tests
- Kafka fixture helpers

Exit criteria:

- batch publish path established
- ordering and checkpoint semantics documented
- at least one product or shared service wired through the new ports

### Wave 4: Capability ports

Deliver only for proven cross-product cases:

- `BulkUpsertStore`
- `PipelinedKeyValueOperations`
- `OrderedPartitionPublisher`
- `OutboxStore`
- `CheckpointStore`

Exit criteria:

- each capability port has at least one real production use case
- no duplicated capability shape exists in a sibling module

### Wave 5: Native escape hatches and stricter perf gates

Deliver:

- approved native extension entry points
- benchmark harnesses
- stricter regression thresholds

Exit criteria:

- escape hatches remain rare and auditable
- every use has owner, evidence, and operational rationale

## Definition Of Done For A Shared Adapter

A shared adapter is not production-ready until all of the following are true:

- its business or capability port contract is implemented
- functional contract tests pass against real infrastructure
- performance smoke checks pass
- telemetry is emitted using standard names
- errors map into the shared taxonomy
- startup, readiness, and shutdown behavior are tested
- typed config is validated at startup
- docs describe semantics, limitations, tuning knobs, and failure behavior
- at least one product wiring example exists

## Immediate Repo Decisions

To keep this plan consistent with the current monorepo, the following decisions apply now.

1. Do not create a new top-level `libs/integrations/*` tree yet.
2. Do not add new Gradle modules for every port, adapter, contract, and fixture package by default.
3. Refactor inside `platform/java/database`, `platform/java/distributed-cache`, `platform/java/connectors`, `platform/java/kernel-persistence`, and `platform/java/testing` first.
4. Create new modules only when consumer evidence and governance checks justify them.
5. Treat `platform/java/database:DatabaseClient` and similarly broad abstractions as legacy surfaces for shared integration work; new work should use narrower ports.
6. Extend existing Testcontainers usage into shared fixture and contract harnesses instead of scattering bespoke container setup across products and services.

## Why This Is Stronger Than The Earlier Plan

This version keeps the production-grade intent of the earlier architecture while aligning to the actual Ghatana repo:

- it honors the business-port vs capability-port split
- it treats performance as contractual
- it requires batching where the backend benefits
- it standardizes real-infra contract testing
- it governs native escape hatches
- it avoids speculative new modules and reuses existing code first

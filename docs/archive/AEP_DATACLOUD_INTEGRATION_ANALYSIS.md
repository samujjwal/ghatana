# AEP ↔ Data-Cloud Integration: Comprehensive Analysis Report

> **Date**: 2026-02-04  
> **Scope**: Full source-level analysis across both `ghatana/` (original) and `ghatana-new/` (refactored) codebases  
> **Directories analyzed**: `core/operator/eventcloud/`, `aep/integration/events/`, `pipeline/registry/`, `eventprocessing/`, `eventcore/`, `ingress/`, `orchestrator/models|queue|store/`, `statestore/`, `aep/preprocessing/`, `aep/analytics/`, `data-cloud/spi/`, `data-cloud/plugins/agentic/`

---

## Table of Contents

1. [EventCloud Operator Model](#1-eventcloud-operator-model)
2. [Ingress Model](#2-ingress-model)
3. [Orchestrator Execution Model](#3-orchestrator-execution-model)
4. [Pipeline Registry Model](#4-pipeline-registry-model)
5. [Event Processing Registry Model](#5-event-processing-registry-model)
6. [Integration Points Between AEP and Data-Cloud](#6-integration-points-between-aep-and-data-cloud)
7. [Preprocessing and Analytics Capabilities](#7-preprocessing-and-analytics-capabilities)
8. [Gaps in the Integration](#8-gaps-in-the-integration)

---

## 1. EventCloud Operator Model

### Overview

The EventCloud operator model defines how AEP consumes, partitions, tracks, and reconnects to event streams. It is the foundational abstraction through which AEP reads from (and writes to) data-cloud's event storage.

### Key Components

#### Event Conversion (`EventCloudEventConverter.java`)
- Converts `EventCloudRecord` instances into domain `GEvent` objects.
- Inner interface `EventCloudRecord` defines the canonical event shape: `getId()`, `getType()`, `getTimestamp()`, `getPayload()` (Map), `getMetadata()` (Map), and `offset()`.
- The converter maps raw cloud records into AEP's domain model for downstream pattern detection and analytics.

#### Subscription Configuration (`EventCloudSubscriptionConfig.java`)
- Builder-pattern configuration with:
  - `tenantId` — multi-tenant isolation
  - `eventTypePattern` — glob/wildcard event type matching
  - `startAt` — EARLIEST / LATEST / specific offset
  - `maxBatchSize`, `pollTimeout` — throughput tuning
  - `autoCommit` — offset commit strategy
  - `prefetchCount` — prefetch for throughput optimization
  - `reconnectionStrategy` — pluggable reconnection
  - `consumerGroup` — shared subscription semantics

#### Partition Assignment
Three strategies for distributing partitions across consumers:

| Strategy | Class | Algorithm |
|----------|-------|-----------|
| **Consistent Hashing** | `ConsistentHashStrategy` | 150 virtual nodes per consumer on a hash ring; minimizes rebalance churn |
| **Round Robin** | `RoundRobinStrategy` | Simple `partitionId % consumerCount` modulo assignment |
| **Interface** | `PartitionAssignmentStrategy` | `shouldAssignPartition()`, `getAssignedPartitions()`, `rebalance()` |

Supporting types:
- `PartitionAssignment` — tracks `Map<String, Set<Integer>>` consumer→partition mapping with an epoch version for rebalance fencing
- `RebalanceResult` — captures new assignment plus added/revoked partition sets

#### Offset Tracking (`OffsetTracker.java`)
- Per-partition tracking of consumed and committed offsets using `ConcurrentHashMap<Integer, AtomicLong>`.
- Exposes `lag()` per partition (consumed - committed).
- Thread-safe; designed for shared-nothing per-subscription instances.

#### Reconnection
Two layers of reconnection:

1. **`ReconnectionStrategy`** (config level) — exponential backoff with jitter: initialDelay=1s, maxDelay=60s, multiplier=2.0, maxAttempts=10
2. **`ReconnectionPolicy`** (runtime level) — interface with `shouldRetry()`, `getBackoffDuration()`, `reset()`, `getMaxRetries()`
   - **`ExponentialBackoffPolicy`** implementation: 100ms initial, 30s max, 2.0x multiplier, 10 retries
3. **`ConnectionHealthMonitor`** — thread-safe state machine:
   ```
   DISCONNECTED → CONNECTING → CONNECTED → RECONNECTING → FAILED
   ```
   Tracks consecutive failures, uptime, and exposes `HealthSnapshot` records.

### How AEP Actually Connects to Data-Cloud's Event Store

The **`DataCloudEventCloudClient`** (in `ghatana/` codebase) is the actual adapter:
- Implements AEP's `EventCloudClient` interface
- Wraps data-cloud's **`StoragePlugin`** (for `append()` / `appendBatch()`) and **`StreamingPlugin`** (for `subscribe()`)
- The subscription model:
  1. Resolves `startOffset` from subscription config (EARLIEST/LATEST/specific)
  2. Calls `streamingPlugin.subscribe(tenantId, eventTypePattern, partitionId, offset, listener)`
  3. Wraps the returned `Subscription` in a `DataCloudEventCloudSubscription`
  4. The subscription buffers events in a `ConcurrentLinkedQueue` with backpressure (high water mark = 800, low water mark = 400, max = 1000)
  5. `poll(maxRecords)` drains the buffer; tracks last-polled offsets per partition
  6. `commit()` commits those offsets to the streaming plugin
- Metrics: `aep.eventcloud.client.subscribe.success/failed`, `aep.eventcloud.client.append.success/failed`, `aep.eventcloud.subscription.poll.events`, `aep.eventcloud.subscription.commit.*`, `aep.eventcloud.subscription.pause/resume`

---

## 2. Ingress Model

### Overview

The ingress layer is how external events enter the AEP system. It handles HTTP health checks, rate limiting, idempotency, and routing to connectors.

### Components

#### Health Endpoints (`HealthController.java`)
- ActiveJ `RoutingServlet` serving `/health/live` and `/health/ready`
- Standard Kubernetes-style health probes

#### Event Receipt (`EventReceipt.java`)
- Simple receipt with `eventId` and `duplicate` flag
- Returned to callers after successful ingestion

#### Rate Limiting (`RedisRateLimitStorage.java`)
- **Algorithm**: Redis sorted-set sliding window
- Interface `RateLimitStorage` with `tryConsume()` and `headers()` methods
- Enforces per-tenant or per-key rate limits with configurable windows
- Returns HTTP rate-limit headers

#### Idempotency Service (`IdempotencyService.java`)
- Redis-backed with TTL-based expiry
- Stores `IngestResponseProto` (protobuf) responses keyed by idempotency token
- Pattern: `seen(key) → Optional<Response>` then `remember(key, response, ttl)`
- Ensures exactly-once semantics for event ingestion

#### Tenant Context Propagation (`TenantContextPropagator.java`)
- Lightweight propagator (currently near no-op for single-eventloop deployments)
- Designed to carry tenant context through the processing chain

#### Error Handling (`GlobalExceptionHandler.java`)
- `ValidationException` → HTTP 422
- All other exceptions → HTTP 500
- Uses `ApiException` with `ErrorCode` enum for structured error responses

#### Connector Routing (`IngressConnectorRouter.java`)
- Routes incoming HTTP or queue messages to registered `ConnectorOperator` instances
- Maps by path or topic to the appropriate connector
- Part of the pipeline registry's connector subsystem

### Event Flow Through Ingress
```
External Request
    → Rate Limit Check (Redis sliding window)
    → Idempotency Check (Redis)
    → Tenant Context Propagation
    → ConnectorOperator processing
    → Event appended to EventCloud (via DataCloudEventCloudClient → StoragePlugin)
    → EventReceipt returned
```

---

## 3. Orchestrator Execution Model

### Overview

The orchestrator manages execution of multi-step agent pipelines with exactly-once semantics, distributed work claiming, and full checkpoint/resume capabilities.

### Execution Queue

#### Interface (`ExecutionQueue.java`)
- `enqueue(ExecutionJob)` — add work to the queue
- `poll(visibilityTimeout)` — claim a job with lease-based visibility
- `complete(jobId)` / `fail(jobId)` — finalize job state

#### Job Model
- **`ExecutionJob`** — value object: tenantId, pipelineId, triggerData, idempotencyKey, jobId, instanceId
- **`ExecutionQueueJob`** — full lifecycle record with:
  - Status: `PENDING → IN_PROGRESS → COMPLETED / FAILED / CANCELLED / TIMED_OUT`
  - `attemptCount` — retry tracking
  - Lease tracking (lease start, lease expires)

#### Implementations

| Implementation | Backend | Key Feature |
|---------------|---------|-------------|
| `InMemoryExecutionQueue` | ConcurrentLinkedQueue + ConcurrentHashMap | Idempotency via in-memory map; for testing |
| `PostgresExecutionQueue` | PostgreSQL | **SKIP LOCKED** for distributed claiming; `poll_execution_queue` stored procedure; expired lease detection |
| `CheckpointAwareExecutionQueue` | Wraps CheckpointStore | Validates checkpoint status before execution; prevents duplicate processing |

The PostgreSQL implementation is the production path:
- `enqueue()` persists via `ExecutionQueueJobRepository` with unique idempotency constraint
- `poll()` uses `SELECT ... FOR UPDATE SKIP LOCKED` native query to atomically claim jobs
- Expired leases are detected and reclaimable
- Stats queries aggregate by status

### Checkpoint System

#### Core Interface (`CheckpointStore.java`)
- `createExecution()` / `completeExecution()`
- `updateCheckpoint()` / `recordStepCheckpoint()`
- `findByInstanceId()` / `findByIdempotencyKey()` — deduplication
- `findActive()` / `findStale()` — monitoring
- `isDuplicate()` / `isExecutionAllowed()` — guard clauses
- `getLastSuccessfulStep()` — resume from failure

#### Pipeline Checkpoint Model
- **`PipelineCheckpoint`** — full domain object:
  - `instanceId`, `tenantId`, `pipelineId`, `idempotencyKey`
  - `status` enum: `CREATED → RUNNING → STEP_SUCCESS → STEP_FAILED → COMPLETED → FAILED → CANCELLED`
  - `state` (Map) — accumulated state across steps
  - `result` (Map) — final result
  - Step tracking for ordered execution

- **`StepCheckpoint`** — per-step tracking:
  - `stepId`, `stepName`, `status`
  - Input/output data
  - `startedAt/completedAt` timestamps
  - `retryCount`

#### Persistence
- **`PipelineCheckpointEntity`** — JPA entity with `jsonb` columns for state/result, GIN index on idempotency_key, multi-tenant composite indexes
- **`StepCheckpointEntity`** — JPA entity indexed on (instance_id, step_id)
- **`PostgresqlCheckpointStore`** — full implementation using manual `EntityManager` with proper transaction boundaries, throws `DuplicateExecutionException`, execution statistics

#### Consumer Offset Tracking
- **`ConsumerOffsetEntity`** — JPA entity for (tenantId, consumerGroup, partitionId) → offset
- Tracks committed offsets for event stream consumption state

### Execution Flow
```
Trigger Event
    → ExecutionQueue.enqueue(job)
    → Worker.poll() (SKIP LOCKED)
    → CheckpointStore.createExecution()
    → For each step:
        → CheckpointStore.getLastSuccessfulStep() (resume support)
        → AgentStepRunner.execute(step)
        → CheckpointStore.recordStepCheckpoint()
    → CheckpointStore.completeExecution()
    → ExecutionQueue.complete(jobId)
```

---

## 4. Pipeline Registry Model

### Overview

The pipeline registry manages the lifecycle of detection patterns and processing pipelines — from draft registration through activation and deployment.

### Pattern Model (`Pattern.java`)
- Domain object with lifecycle state machine:
  ```
  DRAFT → COMPILED → ACTIVE ↔ INACTIVE
  ```
- Fields: patternId, tenantId, name, specification, schema version
- `detectionPlan` — compiled execution plan for the pattern
- `agentHints` — hints for which agents can process this pattern

### Pipeline Model (`Pipeline.java`)
- Supports both structured and legacy config formats
- Fields: pipelineId, name, stages (ordered list), metadata, version
- Status: `DRAFT → ACTIVE ↔ INACTIVE → DEPRECATED`
- Versioned for safe updates

### Event Publishing

#### `RegistryEventPublisher` Interface
Events published on pattern/pipeline lifecycle transitions:
- `pattern.registered`, `pattern.activated`, `pattern.deactivated`
- `pipeline.registered`, `pipeline.activated`, `pipeline.deactivated`

#### `EventCloudRegistryEventPublisher` Implementation
- **This is a key integration point**: publishes registry lifecycle events directly to EventCloud
- Uses the `EventCloudClient` (backed by `DataCloudEventCloudClient`) to append events
- Full observability: MDC context for tracing, Micrometer metrics for publish success/failure
- Events are stored in data-cloud's event log, making them available to any downstream consumer

### Pattern Registry Service (`PatternRegistryService.java`)
- Full CRUD with validation pipeline
- Publishes events on registration/activation/deactivation
- Observability: metrics for operation counts, latencies
- Multi-tenant with tenant isolation

### Connector System
- **`ConnectorOperator`** interface — lifecycle: `initialize() → connect() → process() → disconnect() → close()`
- **`IngressConnectorRouter`** — routes HTTP/queue messages to registered connectors
- Connectors are the entry points for specific data sources into pipelines

---

## 5. Event Processing Registry Model

### Overview

The event processing registry manages pattern registrations and their mapping to EventCloud event payloads.

### Pattern Registration (`PatternRegistration.java`)
- Immutable value object:
  - `patternId`, `tenantId`, `specification`
  - `schemaVersion` — for schema evolution
  - `agentHint` — preferred agent for processing
  - `consumerHint` — preferred consumer group
  - `tags` — arbitrary labels for discovery

### Registration Mappers (`RegistrationMappers.java`)
- Bidirectional mapping between domain objects and EventCloud event payloads:
  - `PatternRegistration ↔ EventCloud event payload`
  - `EventTypeRegistration ↔ EventCloud event payload`
- Enables patterns to be persisted as events in EventCloud (event sourcing for the registry itself)

### Observability (`RealEventCloudClientObservability.java`)
- Micrometer metrics decorating the EventCloud client:
  - `aep.eventcloud.append.*` — append latency and counts
  - `aep.eventcloud.batch.*` — batch append metrics
  - `aep.eventcloud.subscription.*` — subscription lifecycle
  - `aep.eventcloud.reconnect.*` — reconnection attempts and outcomes
- Provides full operational visibility into EventCloud interactions

---

## 6. Integration Points Between AEP and Data-Cloud

This is the most critical section. There are **four distinct integration points** between the two products.

### Integration Point 1: DataCloudEventCloudClient (AEP → Data-Cloud)

**Location**: `ghatana/products/aep/modules/domains/detection-engine/detection-operators/.../client/DataCloudEventCloudClient.java`

This is the **primary integration adapter**. It implements AEP's `EventCloudClient` interface by delegating to data-cloud's SPIs:

| AEP Operation | Data-Cloud SPI | Method |
|---------------|---------------|--------|
| Subscribe to events | `StreamingPlugin` | `subscribe(tenantId, pattern, partition, offset, listener)` |
| Append single event | `StoragePlugin` | `append(event)` → `Promise<Offset>` |
| Append batch | `StoragePlugin` | `appendBatch(events)` → `Promise<List<Offset>>` |
| Commit offsets | `StreamingPlugin.Subscription` | `commitOffsets(Map<PartitionId, Offset>)` |
| Pause/Resume | `StreamingPlugin.Subscription` | `pause()` / `resume()` |

**Architecture Pattern**: Adapter / Anti-Corruption Layer
- AEP types (EventCloudRecord, EventCloudSubscription) are never exposed to data-cloud
- Data-cloud types (StoragePlugin, StreamingPlugin, Event) are never exposed to AEP domain
- The adapter handles conversion in both directions

### Integration Point 2: EventLogStore SPI (Data-Cloud → AEP)

**Location**: `ghatana/products/data-cloud/spi/src/main/java/com/ghatana/datacloud/spi/EventLogStore.java`

Data-cloud declares an SPI specifically for AEP's EventCloud storage needs:

```java
/**
 * Event Log Store SPI - Append-only event log storage.
 * This is the storage abstraction owned by Data-Cloud that provides
 * storage for AEP's EventCloud.
 */
```

Operations provided:
- **Write**: `append(TenantContext, EventEntry)`, `appendBatch(TenantContext, List<EventEntry>)`
- **Read**: `read(TenantContext, Offset, limit)`, `readByTimeRange(...)`, `readByType(...)`
- **Offset Management**: `getLatestOffset()`, `getEarliestOffset()`
- **Streaming**: `tail(TenantContext, Offset, Consumer<EventEntry>)` — live tailing

The `EventEntry` record contains: eventId, eventType, eventVersion, timestamp, payload (ByteBuffer), contentType, headers, idempotencyKey.

Data-cloud provides multiple implementations:
- PostgreSQL (`PostgresStoragePluginProvider`)
- S3 cold-tier archive (`ColdTierArchivePluginProvider`)
- Delta Lake analytics (`DeltaLakeArchivePlugin`)

### Integration Point 3: AgenticDataProcessor (Data-Cloud → AEP, optional)

**Location**: `ghatana/products/data-cloud/plugins/agentic/src/main/java/.../AgenticDataProcessor.java`

Data-cloud can **optionally** use AEP for intelligent data validation and pattern detection:

```java
Class<?> aepEngineClass = Class.forName("com.ghatana.aep.core.AepEngine");
ServiceLoader<?> loader = ServiceLoader.load(aepEngineClass);
```

- Uses Java's `ServiceLoader` to discover AEP at runtime
- If AEP is on the classpath: uses `AepValidationStrategy` for validation and pattern detection
- If not: falls back to `BasicValidationStrategy` (null checks, rule-based validation)
- Exposes `isAepAvailable()` for runtime feature detection

This is a **soft dependency** — data-cloud functions fully without AEP, but gains intelligent pattern detection when AEP is available.

### Integration Point 4: AnalyticsEngine Direct Import

**Location**: `ghatana-new/products/aep/platform/.../aep/analytics/AnalyticsEngine.java`

The AnalyticsEngine directly imports:
- `com.ghatana.datacloud.event.model.Event` — uses data-cloud's event model
- `com.ghatana.platform.event.EventCloud` — uses the shared platform EventCloud abstraction

This represents a **compile-time dependency** from AEP analytics to data-cloud's event model.

### Integration Point 5: Registry Event Publishing

The `EventCloudRegistryEventPublisher` writes pattern/pipeline lifecycle events to EventCloud, which is backed by data-cloud storage. This means:
- Pattern registrations are durable in data-cloud's event log
- Pipeline lifecycle transitions are auditable via data-cloud
- Any data-cloud consumer can react to AEP registry changes

### Dependency Direction Summary

```
┌─────────────────────────────────────────────────────────┐
│                      AEP Product                         │
│                                                          │
│  ┌──────────────┐    ┌──────────────┐   ┌────────────┐  │
│  │ EventCloud   │    │ Analytics    │   │ Pipeline   │  │
│  │ Operator     │    │ Engine       │   │ Registry   │  │
│  │              │    │              │   │            │  │
│  └──────┬───────┘    └──────┬───────┘   └─────┬──────┘  │
│         │                   │                  │         │
└─────────┼───────────────────┼──────────────────┼─────────┘
          │                   │                  │
          ▼                   ▼                  ▼
┌─────────────────────────────────────────────────────────┐
│              DataCloudEventCloudClient                    │
│              (Adapter / Anti-Corruption Layer)            │
└─────────────────────┬───────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────┐
│                   Data-Cloud Product                      │
│                                                          │
│  ┌──────────────┐    ┌──────────────┐   ┌────────────┐  │
│  │ StoragePlugin│    │ StreamingPlug│   │ EventLog   │  │
│  │ SPI          │    │ SPI          │   │ Store SPI  │  │
│  └──────────────┘    └──────────────┘   └────────────┘  │
│                                                          │
│  ┌──────────────────────────────────────────────────┐    │
│  │ AgenticDataProcessor (optional AEP integration)  │    │
│  │ Uses ServiceLoader to discover AepEngine          │    │
│  └──────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
```

**Key insight**: The dependency is primarily **AEP → Data-Cloud** (AEP depends on data-cloud's SPIs for storage and streaming). Data-cloud's dependency on AEP is **optional and runtime-only** (ServiceLoader).

---

## 7. Preprocessing and Analytics Capabilities

### Preprocessing Pipeline

#### Eventization Service (`EventizationService.java`)
- Transforms raw signals into semantic events
- **10:1 compression ratio** — aggregates 10 raw signals into 1 semantic event
- Capabilities:
  - Noise filtering (removes irrelevant signals)
  - Temporal aggregation (groups signals by time windows)
  - Semantic extraction (extracts meaning from raw payloads)
- Input: `RawSignal` → Output: `SemanticEvent`

#### Normalization Service (`NormalizationService.java`)
- Transforms semantic events into canonical events
- Uses pluggable `SourceAdapter` pattern for different event source formats
- Schema translation across heterogeneous sources
- Input: `SemanticEvent` → Output: `CanonicalEvent`

#### Statistics Service (`EventStatisticsService.java`)
- Statistical profiling for the learning system
- Tracks:
  - Event frequencies (per type, per tenant)
  - Co-occurrence patterns (which events tend to appear together)
  - Inter-arrival time distributions
- Used to inform pattern detection thresholds and anomaly baselines

### Preprocessing Flow
```
Raw Signals → Eventization (10:1) → Semantic Events → Normalization → Canonical Events
                                                                          ↓
                                                                   EventCloud Storage
                                                                   (via Data-Cloud)
```

### Analytics Engine (`AnalyticsEngine.java`)

A comprehensive analytics facade providing:

| Capability | Description |
|-----------|-------------|
| **Anomaly Detection** | Statistical outlier detection on event streams |
| **Predictive Analytics** | Forward-looking predictions based on event patterns |
| **Time Series Forecasting** | Trend analysis and forecasting on event time series |
| **KPI Aggregation** | Roll-up of key performance indicators from events |
| **Pattern Performance Analysis** | Analysis of how detection patterns are performing |
| **BI Integration** | Business intelligence data export |
| **Alerting** | Threshold-based alerting on analytics results |

**Integration with Data-Cloud**: The AnalyticsEngine directly imports `com.ghatana.datacloud.event.model.Event`, meaning it operates on data-cloud's event model natively. Events flow from data-cloud's storage through the analytics engine for processing.

---

## 8. Gaps in the Integration

### 8.1 Empty Directories in `ghatana-new` (Missing Migration)

The following directories exist in `ghatana-new` but are **empty**, suggesting incomplete migration from `ghatana/`:

| Empty Directory | Expected Content |
|----------------|-----------------|
| `core/operator/eventcloud/client/` | Should contain `DataCloudEventCloudClient` — the critical bridge adapter |
| `core/operator/eventcloud/offset/` | Should contain offset management utilities |

**Impact**: The `ghatana-new` codebase lacks the actual adapter that connects AEP to data-cloud. The `DataCloudEventCloudClient` only exists in the older `ghatana/` codebase. **This is the single most critical gap** — without this adapter, the new codebase has no way to connect to data-cloud's storage.

### 8.2 Placeholder Implementations in Orchestrator

**`AgentEventEmitter.java`** (lines 55, 80):
```java
// TODO: Day 40 - Temporarily disabled for testing
// TODO: Day 40 - Temporarily simplified for testing without proto dependencies
// Placeholder for testing - return a simple map with event data
```

**`DefaultEventLogClient.java`** (line 39):
```java
// TODO: Day 40 - Replace with actual EventLog service client
// For now, this is a placeholder implementation that logs events
```

**Impact**: The orchestrator can execute pipelines, but cannot emit real events or write to the actual event log. These placeholders must be replaced with real integrations to data-cloud's EventLogStore.

### 8.3 AepValidationStrategy is a Stub

In `AgenticDataProcessor.AepValidationStrategy`:
```java
@Override
public Promise<ValidationResult> validate(TenantContext tenant, Entity entity) {
    // Would call AEP engine for validation
    // For now, delegate to basic validation
    return new BasicValidationStrategy().validate(tenant, entity);
}
```

The AEP-based validation strategy is discovered via ServiceLoader but currently **delegates back to basic validation**. Pattern detection also returns empty lists. The reverse integration (data-cloud using AEP) is architecturally prepared but not functionally implemented.

### 8.4 Missing Consumer Group Coordination

While `ConsistentHashStrategy` and `RoundRobinStrategy` exist for partition assignment, there is **no visible group coordinator** — no implementation of:
- Consumer group membership protocol
- Heartbeat mechanism for failure detection
- Automatic rebalance triggering

The partition assignment strategies exist in isolation without the coordination layer that would invoke them.

### 8.5 StateStore ↔ EventCloud Integration Gap

The `HybridStateStore` (local + central) and `CheckpointCoordinator` exist as sophisticated state management tools, but there is **no explicit integration** between:
- StateStore checkpoints and EventCloud offset commits
- The checkpoint barrier protocol and event processing acknowledgments

These are parallel systems that would need coordination for exactly-once processing guarantees across both state and events.

### 8.6 Event Schema Evolution

While `PatternRegistration` has a `schemaVersion` field and `EventLogStore.EventEntry` has an `eventVersion` field, there is **no schema registry** or **schema compatibility checker**. Events with different schema versions could cause deserialization failures without:
- A schema registry service
- Forward/backward compatibility validation
- Schema migration tooling

### 8.7 Pipeline ↔ Ingress Routing Gap

`IngressConnectorRouter` routes to connectors, and `Pipeline` defines stages, but there is no visible **binding** between:
- A pipeline's configured ingress source and an actual connector registration
- Pipeline activation triggering automatic connector setup

### 8.8 Missing ghatana-new Data-Cloud Product

The `ghatana-new/` workspace appears to only contain the AEP product. There is no `ghatana-new/products/data-cloud/` directory visible. This means the refactored codebase may be developing AEP **without the data-cloud counterpart**, relying on the original `ghatana/` data-cloud code.

### 8.9 Metrics Namespace Fragmentation

Metrics are spread across multiple namespaces:
- `aep.eventcloud.client.*` (DataCloudEventCloudClient)
- `aep.eventcloud.subscription.*` (DataCloudEventCloudSubscription)
- `aep.eventcloud.append.*` (RealEventCloudClientObservability)
- `aep.pattern.*` (PatternRegistryService)
- `aep.ingress.*` (ingress layer)

There's no unified metrics taxonomy document, and some metric names overlap or conflict between the observability wrapper and the direct client instrumentation.

---

## Summary of Integration Architecture

```
                    ┌─────────────────────────────────────┐
                    │          External Sources            │
                    └──────────────┬──────────────────────┘
                                   │
                    ┌──────────────▼──────────────────────┐
                    │      Ingress Layer                   │
                    │  Rate Limit → Idempotency → Router  │
                    └──────────────┬──────────────────────┘
                                   │
                    ┌──────────────▼──────────────────────┐
                    │      Preprocessing                   │
                    │  Eventization → Normalization        │
                    │  (10:1 compression)                  │
                    └──────────────┬──────────────────────┘
                                   │
          ┌────────────────────────▼────────────────────────┐
          │              EventCloud                          │
          │   ┌─────────────────────────────────────────┐   │
          │   │  DataCloudEventCloudClient (ADAPTER)    │   │
          │   │  AEP types ←→ Data-Cloud SPIs           │   │
          │   └───────────┬─────────────┬───────────────┘   │
          │               │             │                    │
          │    StoragePlugin    StreamingPlugin               │
          │    (append/read)   (subscribe/commit)            │
          └────────────────────────┬────────────────────────┘
                                   │
                    ┌──────────────▼──────────────────────┐
                    │    Data-Cloud Event Log Store        │
                    │  (PostgreSQL / Kafka / S3 / Delta)   │
                    └──────────────┬──────────────────────┘
                                   │
               ┌───────────────────┼───────────────────────┐
               │                   │                       │
    ┌──────────▼───────┐  ┌───────▼────────┐   ┌─────────▼──────────┐
    │ Pattern Detection │  │ Orchestrator    │   │ Analytics Engine   │
    │ (EventProcessing) │  │ (Queue+Chkpt)  │   │ (Anomaly/Predict)  │
    └──────────────────┘  └────────────────┘   └────────────────────┘
```

### Key Architectural Decisions

1. **Adapter Pattern**: AEP never directly calls data-cloud; always goes through `DataCloudEventCloudClient`
2. **SPI-based**: Data-cloud provides SPIs (`StoragePlugin`, `StreamingPlugin`, `EventLogStore`); AEP consumes them
3. **Optional reverse dependency**: Data-cloud can optionally use AEP via ServiceLoader (soft coupling)
4. **Multi-tenant throughout**: Every operation carries `tenantId` or `TenantContext`
5. **Promise-based async**: ActiveJ `Promise` throughout both products for non-blocking I/O
6. **Event sourcing for metadata**: Pipeline/pattern registry events are stored in EventCloud itself

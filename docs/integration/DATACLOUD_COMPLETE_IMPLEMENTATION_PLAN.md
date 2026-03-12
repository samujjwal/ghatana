# Data-Cloud: Complete Implementation Plan

> **Document ID:** `DATACLOUD_COMPLETE_IMPLEMENTATION_PLAN`
> **Version:** 1.0.0
> **Status:** ACTIVE
> **Scope:** `products/data-cloud/**` — all backend phases, UI/UX redesign, agentic plugin integration
> **Related Docs:** `AEP_COMPLETE_IMPLEMENTATION_PLAN.md`, `AEP_DATACLOUD_AGENTIC_INTEGRATION_PLAN.md` (v2.1.0), ADR-001, ADR-003, ADR-004, ADR-005, ADR-008

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Architectural Foundation](#2-architectural-foundation)
3. [Current State Audit](#3-current-state-audit)
4. [Backend: Phase-by-Phase Implementation](#4-backend-phase-by-phase-implementation)
   - Phase 0: Critical Fixes & Config Hardening
   - Phase 1: PostgreSQL WARM Tier EventLogStore
   - Phase 2: gRPC Server Activation
   - Phase 3: Agent Plugin HTTP Endpoints
   - Phase 4: Memory Plane HTTP Endpoints
   - Phase 5: Brain & Attention REST + SSE
   - Phase 6: Learning Loop → Consolidation Pipeline Wiring
   - Phase 7: ContextGateway → LLM Integration
   - Phase 8: Analytics REST Expansion
   - Phase 9: Streaming SSE Endpoints
5. [UI/UX Plan](#5-uiux-plan)
   - Event Explorer
   - Memory Plane Viewer
   - Entity Browser (Redesign)
   - Data Fabric View
   - Agent Plugin Manager
   - Brain Dashboard (Redesign)
   - Workflow Canvas (Redesign)
6. [Configuration Reference](#6-configuration-reference)
7. [New Files List](#7-new-files-list)
8. [Modifications List](#8-modifications-list)
9. [Test Plan](#9-test-plan)
10. [Definition of Done](#10-definition-of-done)

---

## 1. Executive Summary

Data-Cloud is the persistent, intelligent substrate for the Ghatana platform. It stores, routes, tiers, and streams all events, entities, and agent memory. The current implementation has correct SPI contracts and DI wiring, but suffers from:

- **Hardcoded infrastructure configs** (Redis `localhost:6379`, Iceberg local warehouse, S3 `us-east-1`) that prevent environment-specific deployments
- **Missing WARM tier** — PostgreSQL `EventLogStore` is not implemented, leaving a gap in the four-tier strategy
- **Silent cognitive components** — `GlobalWorkspace`, `AttentionManager`, `ReflexEngine`, `PatternCatalog` exist but have no HTTP/SSE exposure
- **Disconnected learning pipeline** — `LearningLoop` and `FeedbackCollector` are not wired to `ConsolidationPipeline`
- **Un-served gRPC** — full proto contracts exist (`EventLogService`, `EventService`, `EventQueryService`) but no gRPC server is started in `DataCloudLauncher`
- **No agent plugin endpoints** — `DataCloudAgentRegistry`, `DataCloudCheckpointStore`, `RegistryEventPublisher` are invisible to external callers
- **UI with stubs** — 27 pages exist but many are disconnected from the backend or are placeholder shells

This plan resolves all gaps in sequence, starting with blocking infrastructure issues (Phase 0) through full cognitive and streaming capability (Phase 9), followed by a complete UI/UX redesign.

---

## 2. Architectural Foundation

### ADR Constraints (Binding)

| ADR     | Rule                                                                                                                       | Impact                                                                      |
| ------- | -------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| ADR-001 | Agent taxonomy: DETERMINISTIC / PROBABILISTIC / HYBRID / ADAPTIVE / COMPOSITE / REACTIVE                                   | All Data-Cloud agent plugin endpoints must accept and return this taxonomy  |
| ADR-003 | Four-tier event storage: HOT (Redis <1ms) → WARM (PostgreSQL <10ms) → COOL (Iceberg <100ms) → COLD (S3, seconds)           | WARM tier implementation is non-negotiable                                  |
| ADR-004 | ActiveJ `Promise<T>` only — never `CompletableFuture`, never Spring Reactor/WebFlux                                        | All new async code must use `Promise.ofBlocking()` for IO                   |
| ADR-005 | Multi-tenancy: thread-local `TenantContext` + explicit `tenantId` on ALL data access                                       | Every new endpoint must extract `tenantId` from request header              |
| ADR-008 | SPI pattern: `EventLogStore`, `EntityStore`, `StorageConnector`, `EncryptionService`, `AuditLogger` via Java ServiceLoader | New tier implementations must implement the SPI interfaces, not bypass them |

### Module Boundaries

```
products/data-cloud/
├── spi/              ← SPI interfaces: EventLogStore, EntityStore, StorageTier, TenantContext, EventEntry
├── platform/         ← DI modules, implementations, HTTP server, Launcher
│   ├── brain/        ← GlobalWorkspace, AttentionManager, ReflexEngine, PatternCatalog, SalienceScorer
│   ├── config/       ← ConfigLoader, ConfigValidator, RoutingConfigCompiler
│   ├── feedback/     ← FeedbackCollector, LearningLoop, LearningSignal
│   ├── plugins/      ← RedisHotTierPlugin, CoolTierStoragePlugin (Iceberg), ColdTierArchivePlugin (S3)
│   └── routing/      ← ConfigDrivenStorageRouter, MemoryTierRouter
└── ui/               ← React 19 + Jotai + TanStack Query (port 5173)

platform/java/
├── agent-memory/     ← PersistentMemoryPlane, JdbcMemoryItemRepository, SemanticMemoryManager
├── agent-learning/   ← ConsolidationPipeline, EpisodicToSemanticConsolidator, SkillPromotionWorkflow
└── agent-framework/  ← BaseAgent, AgentTurnPipeline, AgentDefinition, AgentInstance

platform/contracts/event/v1/  ← Proto: EventLogService, EventService, EventQueryService
```

### Dependency Injection Topology

```
DataCloudLauncher
  └── DataCloudCoreModule     (DataCloudConfig, DataCloudClient, StoragePluginRegistry)
      ├── DataCloudStorageModule  (RedisHot, Iceberg Cool, S3 Cold)
      ├── DataCloudBrainModule    (GlobalWorkspace, AttentionManager, SalienceScorer, ReflexEngine, PatternCatalog)
      ├── DataCloudStreamingModule (KafkaStreamingPlugin, EventSerializer, RedisStateAdapter)
      └── DataCloudHttpServer     (HTTP routes — currently incomplete)
```

---

## 3. Current State Audit

### Backend Gaps

| Component                         | File Location        | Status         | Gap                                                         |
| --------------------------------- | -------------------- | -------------- | ----------------------------------------------------------- |
| `RedisHotTierPlugin`              | `platform/plugins/`  | ✅ Exists      | Config hardcoded to `localhost:6379`                        |
| `CoolTierStoragePlugin` (Iceberg) | `platform/plugins/`  | ✅ Exists      | Warehouse path hardcoded                                    |
| `ColdTierArchivePlugin` (S3)      | `platform/plugins/`  | ✅ Exists      | Region hardcoded `us-east-1`, bucket hardcoded              |
| PostgreSQL WARM tier              | —                    | ❌ Missing     | No `EventLogStore` impl for PostgreSQL                      |
| `GlobalWorkspace`                 | `platform/brain/`    | ✅ Exists      | Not exposed via HTTP/SSE                                    |
| `AttentionManager`                | `platform/brain/`    | ✅ Exists      | Not exposed via HTTP/SSE                                    |
| `ReflexEngine`                    | `platform/brain/`    | ✅ Exists      | Not wired to HTTP routing                                   |
| `FeedbackCollector`               | `platform/feedback/` | ✅ Exists      | Not connected to `ConsolidationPipeline`                    |
| `LearningLoop`                    | `platform/feedback/` | ✅ Exists      | Not scheduled, not triggered                                |
| `DefaultContextGateway`           | `platform/`          | ✅ Exists      | Not wired to `AIIntegrationService`                         |
| `DataCloudAgentRegistry`          | `platform/`          | ✅ Exists      | No HTTP endpoint                                            |
| `RegistryEventPublisher`          | `platform/`          | ✅ Exists      | Stream `agent-registry-events` never consumed               |
| `DataCloudCheckpointStore`        | `platform/`          | ✅ Exists      | No HTTP endpoint                                            |
| `DataCloudHttpServer` routes      | `platform/`          | ⚠️ Partial     | Only: health, entity CRUD, basic event ingestion            |
| gRPC server                       | `platform/`          | ❌ Not started | Protos fully defined; `DataCloudLauncher` never starts gRPC |
| `AnalyticsQueryEngine`            | `platform/`          | ✅ Exists      | Not fully exposed — only basic entity query via HTTP        |

### UI Gaps

| Page                 | Route                    | Status     | Gap                                                        |
| -------------------- | ------------------------ | ---------- | ---------------------------------------------------------- |
| EventLog Explorer    | `/data-cloud/events`     | ❌ Missing | No page; `EventLogStore.tail()` never SSE-fed              |
| Memory Plane Viewer  | `/data-cloud/memory`     | ❌ Missing | No page for 7-tier agent memory browsing                   |
| Entity Browser       | `/data-cloud/entities`   | ⚠️ Stub    | 4 partial stubs exist; need unified `EntityBrowserPage`    |
| Data Fabric View     | `/data-cloud/fabric`     | ❌ Missing | No topology view; `@ghatana/flow-canvas` not used          |
| Agent Plugin Manager | `/data-cloud/agents`     | ❌ Missing | `PluginsPage` is generic; no agent-specific plugin manager |
| Brain Dashboard      | `/data-cloud/brain`      | ⚠️ Stub    | `BrainDashboardPage` exists but not wired to real SSE      |
| Workflow Canvas      | `/data-cloud/workflows`  | ⚠️ Partial | Custom nodes; not using `@ghatana/flow-canvas`             |
| DashboardPage        | `/data-cloud`            | ⚠️ Mock    | Charts exist; data mostly mocked                           |
| SqlWorkspacePage     | `/data-cloud/sql`        | ⚠️ Stub    | Editor present; no backend query dispatch                  |
| GovernancePage       | `/data-cloud/governance` | ⚠️ Stub    | Present; lineage data mocked                               |
| AlertsPage           | `/data-cloud/alerts`     | ⚠️ Stub    | Present; rules not persisted to backend                    |
| CostOptimizationPage | `/data-cloud/cost`       | ⚠️ Stub    | Charts mocked                                              |

---

## 4. Backend: Phase-by-Phase Implementation

---

### Phase 0: Critical Fixes & Config Hardening

**Priority: BLOCKING — Nothing else can happen until configs are environment-driven.**

#### 0.1 — Introduce `DataCloudEnvConfig`

Create a centralized env-var reader that all plugins use. No plugin should have a hardcoded host/port/bucket.

**File:** `products/data-cloud/platform/src/main/java/com/ghatana/datacloud/config/DataCloudEnvConfig.java`

```java
/**
 * @doc.type class
 * @doc.purpose Reads all Data-Cloud infrastructure configuration from environment variables
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DataCloudEnvConfig(
    // HOT tier — Redis
    String redisHost,
    int redisPort,
    String redisPassword,           // nullable
    int redisMaxConnections,
    boolean redisTestOnBorrow,

    // WARM tier — PostgreSQL
    String pgUrl,                   // full JDBC URL
    String pgUser,
    String pgPassword,
    int pgMaxPoolSize,

    // COOL tier — Iceberg
    String icebergWarehouse,        // "s3://bucket/path" or "file:///local"
    String icebergCatalogType,      // "rest" | "hive" | "jdbc"
    String icebergCatalogUri,       // for REST catalog

    // COLD tier — S3
    String s3Bucket,
    String s3Region,
    String s3Endpoint,              // nullable — for MinIO/localstack
    String s3AccessKey,             // nullable — for non-IAM auth
    String s3SecretKey,             // nullable — for non-IAM auth

    // Kafka
    String kafkaBootstrapServers,
    String kafkaGroupId,

    // gRPC server
    int grpcPort,

    // HTTP
    int httpPort
) {
    public static DataCloudEnvConfig fromEnvironment() {
        return new DataCloudEnvConfig(
            getRequired("DATACLOUD_REDIS_HOST"),
            Integer.parseInt(getOrDefault("DATACLOUD_REDIS_PORT", "6379")),
            System.getenv("DATACLOUD_REDIS_PASSWORD"),
            Integer.parseInt(getOrDefault("DATACLOUD_REDIS_MAX_CONNECTIONS", "20")),
            Boolean.parseBoolean(getOrDefault("DATACLOUD_REDIS_TEST_ON_BORROW", "true")),

            getRequired("DATACLOUD_PG_URL"),
            getRequired("DATACLOUD_PG_USER"),
            getRequired("DATACLOUD_PG_PASSWORD"),
            Integer.parseInt(getOrDefault("DATACLOUD_PG_MAX_POOL_SIZE", "20")),

            getOrDefault("DATACLOUD_ICEBERG_WAREHOUSE", "file:///tmp/iceberg-warehouse"),
            getOrDefault("DATACLOUD_ICEBERG_CATALOG_TYPE", "jdbc"),
            System.getenv("DATACLOUD_ICEBERG_CATALOG_URI"),

            getRequired("DATACLOUD_S3_BUCKET"),
            getOrDefault("DATACLOUD_S3_REGION", "us-east-1"),
            System.getenv("DATACLOUD_S3_ENDPOINT"),
            System.getenv("DATACLOUD_S3_ACCESS_KEY"),
            System.getenv("DATACLOUD_S3_SECRET_KEY"),

            getOrDefault("DATACLOUD_KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"),
            getOrDefault("DATACLOUD_KAFKA_GROUP_ID", "data-cloud"),

            Integer.parseInt(getOrDefault("DATACLOUD_GRPC_PORT", "9090")),
            Integer.parseInt(getOrDefault("DATACLOUD_HTTP_PORT", "8080"))
        );
    }

    private static String getRequired(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required env var not set: " + key);
        }
        return value;
    }

    private static String getOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }
}
```

#### 0.2 — Update All Plugins to Use `DataCloudEnvConfig`

Modify `DataCloudStorageModule` to inject `DataCloudEnvConfig` into each plugin:

```java
// DataCloudStorageModule.java — configure method
DataCloudEnvConfig env = DataCloudEnvConfig.fromEnvironment();

bind(RedisHotTierPlugin.class).toInstance(
    new RedisHotTierPlugin(
        env.redisHost(), env.redisPort(),
        env.redisPassword(), env.redisMaxConnections(), env.redisTestOnBorrow()
    )
);

bind(CoolTierStoragePlugin.class).toInstance(
    new CoolTierStoragePlugin(
        env.icebergWarehouse(), env.icebergCatalogType(), env.icebergCatalogUri()
    )
);

bind(ColdTierArchivePlugin.class).toInstance(
    new ColdTierArchivePlugin(
        env.s3Bucket(), env.s3Region(), env.s3Endpoint(),
        env.s3AccessKey(), env.s3SecretKey()
    )
);

bind(KafkaStreamingPlugin.class).toInstance(
    new KafkaStreamingPlugin(env.kafkaBootstrapServers(), env.kafkaGroupId())
);
```

#### 0.3 — Startup Validation

Add a `DataCloudStartupValidator` that pings each tier on startup and fails fast with a clear error message (never silently continue with an unusable tier):

```java
/**
 * @doc.type class
 * @doc.purpose Validates all storage tier connections on application startup
 * @doc.layer product
 * @doc.pattern Service
 */
public class DataCloudStartupValidator {

    public Promise<Void> validate(
        RedisHotTierPlugin redis,
        WarmTierEventLogStore postgres,     // Phase 1
        String icebergWarehouse,
        String s3Bucket
    ) {
        return Promise.ofCallback(callback -> {
            List<String> failures = new ArrayList<>();
            // validate each tier synchronously in ofBlocking
            Promise.ofBlocking(executor, () -> {
                validateRedis(redis, failures);
                validatePostgres(postgres, failures);
                validateIceberg(icebergWarehouse, failures);
                validateS3(s3Bucket, failures);
                return failures;
            }).whenResult(errs -> {
                if (!errs.isEmpty()) {
                    callback.setException(new IllegalStateException(
                        "Data-Cloud startup validation failed: " + String.join("; ", errs)));
                } else {
                    callback.set(null);
                }
            }).whenException(callback::setException);
        });
    }
}
```

---

### Phase 1: PostgreSQL WARM Tier EventLogStore

**Priority: HIGH — Completes the four-tier architecture.**

The WARM tier provides durable, queryable storage between Redis (volatile) and Iceberg (analytics). It must implement `EventLogStore` SPI and use JDBC via `Promise.ofBlocking()` per ADR-004.

#### 1.1 — Schema

```sql
-- migrations/V001__create_event_log.sql
CREATE TABLE event_log (
    event_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      VARCHAR(128) NOT NULL,
    workspace_id   VARCHAR(128),
    event_type     VARCHAR(256) NOT NULL,
    event_version  VARCHAR(32)  NOT NULL DEFAULT '1.0',
    payload        BYTEA        NOT NULL,
    content_type   VARCHAR(128) NOT NULL DEFAULT 'application/json',
    headers        JSONB,
    idempotency_key VARCHAR(256),
    offset_value   BIGINT       NOT NULL GENERATED ALWAYS AS IDENTITY,
    timestamp      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_idempotency UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX idx_event_log_tenant_type      ON event_log (tenant_id, event_type, timestamp DESC);
CREATE INDEX idx_event_log_tenant_timestamp ON event_log (tenant_id, timestamp DESC);
CREATE INDEX idx_event_log_offset           ON event_log (tenant_id, offset_value);
```

#### 1.2 — Implementation

**File:** `products/data-cloud/platform/src/main/java/com/ghatana/datacloud/plugins/warm/WarmTierEventLogStore.java`

```java
/**
 * @doc.type class
 * @doc.purpose PostgreSQL-backed EventLogStore for the WARM storage tier (ADR-003)
 * @doc.layer product
 * @doc.pattern Repository
 */
public class WarmTierEventLogStore implements EventLogStore {

    private final DataSource dataSource;
    private final Executor blockingExecutor;

    // EventEntry → JDBC insert row
    @Override
    public Promise<Long> append(TenantContext ctx, EventEntry entry) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO event_log (tenant_id, workspace_id, event_type, event_version, " +
                     "payload, content_type, headers, idempotency_key) VALUES (?,?,?,?,?,?,?::jsonb,?) " +
                     "ON CONFLICT (tenant_id, idempotency_key) DO NOTHING RETURNING offset_value",
                     Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, ctx.tenantId());
                ps.setString(2, ctx.workspaceId().orElse(null));
                ps.setString(3, entry.eventType());
                ps.setString(4, entry.eventVersion());
                ps.setBytes(5, entry.payload().array());
                ps.setString(6, entry.contentType());
                ps.setObject(7, headersToJson(entry.headers()));
                ps.setString(8, entry.idempotencyKey().orElse(null));
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) return keys.getLong(1);
                    return -1L; // idempotent duplicate
                }
            }
        });
    }

    @Override
    public Promise<List<EventEntry>> read(TenantContext ctx, long fromOffset, int limit) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            List<EventEntry> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM event_log WHERE tenant_id=? AND offset_value>=? " +
                     "ORDER BY offset_value ASC LIMIT ?")) {
                ps.setString(1, ctx.tenantId());
                ps.setLong(2, fromOffset);
                ps.setInt(3, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) results.add(mapRow(rs));
                }
            }
            return results;
        });
    }

    @Override
    public Promise<List<EventEntry>> readByTimeRange(
        TenantContext ctx, Instant from, Instant to, int limit) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            // SELECT ... WHERE tenant_id=? AND timestamp BETWEEN ? AND ? ORDER BY timestamp ASC LIMIT ?
            // ... implementation similar to read()
        });
    }

    @Override
    public Promise<List<EventEntry>> readByType(
        TenantContext ctx, String eventType, long fromOffset, int limit) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            // SELECT ... WHERE tenant_id=? AND event_type=? AND offset_value>=? ...
        });
    }

    @Override
    public Promise<Long> getLatestOffset(TenantContext ctx) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT MAX(offset_value) FROM event_log WHERE tenant_id=?")) {
                ps.setString(1, ctx.tenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        });
    }

    @Override
    public Promise<Long> getEarliestOffset(TenantContext ctx) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            // SELECT MIN(offset_value) ... same pattern
        });
    }

    /**
     * Tail is a poll-based push for WARM tier — use Kafka or SSE adapter in Phase 9.
     * This implementation provides a blocking poll fallback.
     *
     * ⚠️ IMPORTANT: never call Promise methods (e.g. .getResult()) inside ofBlocking.
     * Use raw JDBC inside the blocking thread; never nest Promise.ofBlocking calls.
     */
    @Override
    public Promise<Void> tail(TenantContext ctx, long fromOffset, Consumer<EventEntry> handler) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            long currentOffset = fromOffset;
            while (!Thread.currentThread().isInterrupted()) {
                // ✅ Direct JDBC read inside the blocking thread — no .getResult() needed
                List<EventEntry> batch = readBlocking(ctx, currentOffset, 100);
                if (!batch.isEmpty()) {
                    batch.forEach(handler);
                    currentOffset = batch.get(batch.size() - 1).offset() + 1;
                } else {
                    Thread.sleep(500); // poll interval
                }
            }
            return null;
        });
    }

    /** Blocking JDBC read used internally within ofBlocking threads only. */
    private List<EventEntry> readBlocking(TenantContext ctx, long fromOffset, int limit) {
        List<EventEntry> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM event_log WHERE tenant_id=? AND offset_value>=? " +
                 "ORDER BY offset_value ASC LIMIT ?")) {
            ps.setString(1, ctx.tenantId());
            ps.setLong(2, fromOffset);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("readBlocking failed", e);
        }
        return results;
    }

    @Override
    public Promise<Void> appendBatch(TenantContext ctx, List<EventEntry> entries) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            // batch INSERT with try-with-resources + executeBatch()
        });
    }

    private EventEntry mapRow(ResultSet rs) throws SQLException {
        // Map ResultSet columns → EventEntry record fields
    }
}
```

#### 1.3 — Register WARM Tier in Storage Module

```java
// DataCloudStorageModule.java — add:
bind(WarmTierEventLogStore.class).toInstance(
    new WarmTierEventLogStore(createHikariPool(env), blockingExecutor)
);

// ConfigDrivenStorageRouter already handles tier selection;
// update router to include WarmTierEventLogStore as the WARM tier option.
```

---

### Phase 2: gRPC Server Activation

**Priority: HIGH — external AEP and other products depend on gRPC for high-throughput event ingestion.**

#### 2.1 — Identify Proto-Generated Classes

Proto contracts reside in `platform/contracts/event/v1/`. Generated Java stubs are in `platform/contracts/build/generated/`. The three services to serve:

- `EventLogServiceGrpc` — `Append`, `ReadByType`
- `EventServiceGrpc` — `Ingest`, `IngestBatch`, `IngestStream` (bidirectional), `Query` (server-streaming), `GetEvent`
- `EventQueryServiceGrpc` — `ExecuteQuery` (server-streaming), `GetQueryPlan`

#### 2.2 — Service Implementations

**File:** `products/data-cloud/platform/src/main/java/com/ghatana/datacloud/grpc/EventLogGrpcService.java`

```java
/**
 * @doc.type class
 * @doc.purpose gRPC service implementation bridging EventLogServiceGrpc to EventLogStore SPI
 * @doc.layer product
 * @doc.pattern Service
 */
public class EventLogGrpcService extends EventLogServiceGrpc.EventLogServiceImplBase {

    private final ConfigDrivenStorageRouter router;
    private final ProtobufMapper mapper;

    @Override
    public void append(AppendRequest request, StreamObserver<AppendResponse> responseObserver) {
        TenantContext ctx = TenantContext.fromGrpcMetadata(request);
        EventEntry entry = mapper.toEventEntry(request.getEvent());

        // Route to correct tier and append
        router.routeWrite(ctx, entry)
            .whenComplete((result, err) -> {
                if (err != null) {
                    responseObserver.onError(Status.INTERNAL.withCause(err).asException());
                } else {
                    responseObserver.onNext(AppendResponse.newBuilder()
                        .setOffset(result.offset())
                        .setEventId(entry.eventId().toString())
                        .build());
                    responseObserver.onCompleted();
                }
            });
    }

    @Override
    public StreamObserver<IngestRequest> ingestStream(StreamObserver<IngestResponse> responseObserver) {
        // Bidirectional streaming: receive events, append to HOT tier,
        // respond with offset for each event
        return new StreamObserver<>() {
            @Override
            public void onNext(IngestRequest request) {
                // append to HOT tier, send response
            }
            @Override public void onError(Throwable t) { responseObserver.onError(t); }
            @Override public void onCompleted() { responseObserver.onCompleted(); }
        };
    }
}
```

**File:** `products/data-cloud/platform/src/main/java/com/ghatana/datacloud/grpc/EventQueryGrpcService.java`

```java
/**
 * @doc.type class
 * @doc.purpose gRPC service for analytical event queries, server-streaming results
 * @doc.layer product
 * @doc.pattern Service
 */
public class EventQueryGrpcService extends EventQueryServiceGrpc.EventQueryServiceImplBase {

    private final AnalyticsQueryEngine analyticsEngine;
    private final ProtobufMapper mapper;

    @Override
    public void executeQuery(QueryRequest request, StreamObserver<QueryResult> responseObserver) {
        TenantContext ctx = TenantContext.fromGrpcMetadata(request);
        RecordQuery query = mapper.toRecordQuery(request);

        analyticsEngine.execute(ctx, query)
            .whenResult(results -> {
                results.forEach(row ->
                    responseObserver.onNext(mapper.toQueryResult(row)));
                responseObserver.onCompleted();
            })
            .whenException(e -> responseObserver.onError(
                Status.INTERNAL.withCause(e).asException()));
    }
}
```

#### 2.3 — Wire gRPC Server in Launcher

```java
// DataCloudLauncher.java — add gRPC server startup alongside HTTP:

Server grpcServer = ServerBuilder.forPort(env.grpcPort())
    .addService(injector.getInstance(EventLogGrpcService.class))
    .addService(injector.getInstance(EventQueryGrpcService.class))
    .addService(injector.getInstance(EventServiceGrpcService.class))
    .build();

grpcServer.start();
logger.info("gRPC server started on port {}", env.grpcPort());
Runtime.getRuntime().addShutdownHook(new Thread(grpcServer::shutdown));
```

---

### Phase 3: Agent Plugin HTTP Endpoints

**Priority: HIGH — AEP and other agentic products cannot register or query agents without these.**

All three of these components exist but have no HTTP surface:

- `DataCloudAgentRegistry` (collections: `agent-registry`, `agent-capabilities`, `agent-executions`)
- `RegistryEventPublisher` (stream: `agent-registry-events`)
- `DataCloudCheckpointStore`

#### 3.1 — New Route Group: `/api/v1/agents`

```
POST   /api/v1/agents/register              ← register agent definition
GET    /api/v1/agents                       ← list all agents (paginated)
GET    /api/v1/agents/{agentId}             ← get agent details + capabilities
DELETE /api/v1/agents/{agentId}             ← deregister agent
PUT    /api/v1/agents/{agentId}/capabilities ← update capabilities
GET    /api/v1/agents/{agentId}/executions  ← paginated execution history
POST   /api/v1/agents/{agentId}/executions  ← record new execution event
GET    /api/v1/agents/events/stream         ← SSE: live agent-registry-events stream
```

**File:** `products/data-cloud/platform/src/main/java/com/ghatana/datacloud/http/AgentRegistryRoutes.java`

```java
/**
 * @doc.type class
 * @doc.purpose HTTP route handlers for the Data-Cloud Agent Registry
 * @doc.layer product
 * @doc.pattern Service
 */
public class AgentRegistryRoutes implements RoutesModule {

    private final DataCloudAgentRegistry registry;
    private final RegistryEventPublisher eventPublisher;
    private final SseManager sseManager;

    @Override
    public void register(RoutingServlet router) {
        router.add(POST, "/api/v1/agents/register", this::registerAgent);
        router.add(GET,  "/api/v1/agents",           this::listAgents);
        router.add(GET,  "/api/v1/agents/:agentId",  this::getAgent);
        router.add(DELETE,"/api/v1/agents/:agentId", this::deregisterAgent);
        router.add(GET,  "/api/v1/agents/events/stream", this::streamRegistryEvents);
    }

    private Promise<HttpResponse> registerAgent(HttpRequest request) {
        String tenantId = extractTenantId(request);  // ADR-005
        TenantContext ctx = TenantContext.of(tenantId);
        AgentRegistrationRequest body = parseBody(request, AgentRegistrationRequest.class);

        return registry.register(ctx, body)
            .map(agentRecord -> HttpResponse.ofCode(201)
                .withJson(agentRecord));
    }

    private Promise<HttpResponse> streamRegistryEvents(HttpRequest request) {
        String tenantId = extractTenantId(request);
        // SSE: subscribe to RegistryEventPublisher for this tenant
        // Return HttpResponse with chunked SSE headers
        return sseManager.createStream(tenantId, "agent-registry-events");
    }
}
```

#### 3.2 — New Route Group: `/api/v1/checkpoints`

```
POST   /api/v1/checkpoints                     ← save checkpoint
GET    /api/v1/checkpoints/{agentId}            ← list checkpoints for agent
GET    /api/v1/checkpoints/{agentId}/{checkpointId} ← get specific checkpoint
DELETE /api/v1/checkpoints/{agentId}/{checkpointId} ← delete checkpoint
```

**File:** `products/data-cloud/platform/src/main/java/com/ghatana/datacloud/http/CheckpointRoutes.java`

Same pattern as `AgentRegistryRoutes` — delegate to `DataCloudCheckpointStore`, extract `tenantId` per ADR-005.

---

### Phase 4: Memory Plane HTTP Endpoints

**Priority: HIGH — enables agent memory inspection and debugging.**

`PersistentMemoryPlane` in `platform/java/agent-memory/` is backed by `JdbcMemoryItemRepository` with PostgreSQL + pgvector (`vector(1536)`). It supports 7 memory tiers: Episodic, Semantic, Procedural, Task-State, Working, Preference, Typed Artifacts.

#### 4.1 — New Route Group: `/api/v1/memory`

```
GET    /api/v1/memory/{agentId}                       ← summary: count per tier
GET    /api/v1/memory/{agentId}/episodic               ← paginated episodic memories
GET    /api/v1/memory/{agentId}/semantic               ← paginated semantic memories
GET    /api/v1/memory/{agentId}/procedural             ← paginated procedural memories
GET    /api/v1/memory/{agentId}/preference             ← preference memories
GET    /api/v1/memory/{agentId}/working                ← current working memory
GET    /api/v1/memory/{agentId}/artifacts              ← typed artifacts
POST   /api/v1/memory/{agentId}/search                 ← semantic vector search (pgvector)
DELETE /api/v1/memory/{agentId}/{memoryId}             ← delete specific memory item
PUT    /api/v1/memory/{agentId}/{memoryId}/retain      ← mark as retained (bypass decay)
```

**File:** `products/data-cloud/platform/src/main/java/com/ghatana/datacloud/http/MemoryPlaneRoutes.java`

```java
/**
 * @doc.type class
 * @doc.purpose HTTP route handlers exposing the 7-tier PersistentMemoryPlane via REST
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.memory episodic|semantic|procedural|preference
 */
public class MemoryPlaneRoutes implements RoutesModule {

    private final PersistentMemoryPlane memoryPlane;

    @Override
    public void register(RoutingServlet router) {
        router.add(GET,  "/api/v1/memory/:agentId",           this::getMemorySummary);
        router.add(GET,  "/api/v1/memory/:agentId/:tier",     this::getTierMemories);
        router.add(POST, "/api/v1/memory/:agentId/search",    this::semanticSearch);
        router.add(DELETE,"/api/v1/memory/:agentId/:memoryId",this::deleteMemory);
        router.add(PUT,  "/api/v1/memory/:agentId/:memoryId/retain", this::retainMemory);
    }

    private Promise<HttpResponse> getMemorySummary(HttpRequest request) {
        String agentId = request.getPathParameter("agentId");
        String tenantId = extractTenantId(request);
        TenantContext ctx = TenantContext.of(tenantId);

        return memoryPlane.getSummary(ctx, agentId)
            .map(summary -> HttpResponse.ok200().withJson(summary));
    }

    private Promise<HttpResponse> semanticSearch(HttpRequest request) {
        // Parse query embedding from body; use JdbcMemoryItemRepository's
        // pgvector similarity search (<=> operator)
        String agentId = request.getPathParameter("agentId");
        SemanticSearchRequest body = parseBody(request, SemanticSearchRequest.class);
        TenantContext ctx = TenantContext.of(extractTenantId(request));

        return memoryPlane.semanticSearch(ctx, agentId, body.queryEmbedding(), body.limit())
            .map(items -> HttpResponse.ok200().withJson(items));
    }
}
```

---

### Phase 5: Brain & Attention REST + SSE

**Priority: MEDIUM-HIGH — enables UI to display real-time cognitive state.**

#### 5.1 — New Route Group: `/api/v1/brain`

```
GET  /api/v1/brain/workspace                     ← current GlobalWorkspace spotlight entries
GET  /api/v1/brain/workspace/stream              ← SSE: live spotlight updates
POST /api/v1/brain/attention/elevate             ← manually elevate salience of an item
GET  /api/v1/brain/attention/thresholds          ← current elevation (0.7) + emergency (0.95) thresholds
PUT  /api/v1/brain/attention/thresholds          ← update thresholds (for tuning)
GET  /api/v1/brain/patterns                      ← PatternCatalog: list all patterns
POST /api/v1/brain/patterns/match                ← ReflexEngine: match input against patterns
GET  /api/v1/brain/salience/{itemId}             ← SalienceScorer: get current score for item
```

**File:** `products/data-cloud/platform/src/main/java/com/ghatana/datacloud/http/BrainRoutes.java`

```java
/**
 * @doc.type class
 * @doc.purpose HTTP + SSE routes exposing GlobalWorkspace, AttentionManager, ReflexEngine
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle perceive|reason
 */
public class BrainRoutes implements RoutesModule {

    private final GlobalWorkspace globalWorkspace;
    private final AttentionManager attentionManager;
    private final ReflexEngine reflexEngine;
    private final PatternCatalog patternCatalog;
    private final SalienceScorer salienceScorer;
    private final SseManager sseManager;

    @Override
    public void register(RoutingServlet router) {
        router.add(GET,  "/api/v1/brain/workspace",        this::getWorkspace);
        router.add(GET,  "/api/v1/brain/workspace/stream", this::streamWorkspace);
        router.add(POST, "/api/v1/brain/attention/elevate",this::elevateAttention);
        router.add(GET,  "/api/v1/brain/patterns",         this::listPatterns);
        router.add(POST, "/api/v1/brain/patterns/match",   this::matchPatterns);
    }

    private Promise<HttpResponse> streamWorkspace(HttpRequest request) {
        String tenantId = extractTenantId(request);
        // GlobalWorkspace.subscribe(subscriber) → push spotlight updates to SSE
        return sseManager.createStream(tenantId, "brain-workspace", subscriber ->
            globalWorkspace.subscribe(spotlight ->
                subscriber.send(SseEvent.of(JSON.toJson(spotlight)))));
    }
}
```

#### 5.2 — GlobalWorkspace SSE Architecture

`GlobalWorkspace` already maintains `CopyOnWriteArrayList<Subscriber> subscribers`. The `SseManager` wraps ActiveJ HTTP's chunked response. On each salience-driven broadcast (≥0.95 emergency threshold), all SSE subscribers for that tenant receive a push.

---

### Phase 6: Learning Loop → Consolidation Pipeline Wiring

**Priority: MEDIUM — closes the learning cycle.**

**Currently broken:** `LearningLoop` and `FeedbackCollector` in `products/data-cloud/platform/feedback/` exist but are disconnected from `ConsolidationPipeline` in `platform/java/agent-learning/`.

#### 6.1 — Wire `FeedbackCollector` → `LearningSignal` → `ConsolidationPipeline`

```java
/**
 * @doc.type class
 * @doc.purpose Bridges Data-Cloud FeedbackCollector to agent-learning ConsolidationPipeline
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle reflect
 */
public class LearningBridge {

    private final FeedbackCollector feedbackCollector;
    private final ConsolidationPipeline consolidationPipeline;
    private final ScheduledExecutorService scheduler;

    public void start() {
        // Fire-and-forget every 5 minutes — NEVER block agent response
        scheduler.scheduleAtFixedRate(this::triggerConsolidation, 5, 5, TimeUnit.MINUTES);
    }

    private void triggerConsolidation() {
        feedbackCollector.drainPendingSignals()
            .whenResult(signals -> {
                if (!signals.isEmpty()) {
                    consolidationPipeline.run(signals)
                        .whenException(e -> logger.error("Consolidation pipeline error", e));
                }
            })
            .whenException(e -> logger.error("Failed to drain feedback signals", e));
    }
}
```

#### 6.2 — Add `LearningBridge` to DI Module

```java
// DataCloudCoreModule.java — add:
bind(LearningBridge.class).in(Singleton.class);
bind(ConsolidationPipeline.class).in(Singleton.class);

// DataCloudLauncher.java — on startup:
injector.getInstance(LearningBridge.class).start();
```

#### 6.3 — Add HTTP Endpoint for Manual Trigger

```
POST /api/v1/learning/trigger          ← manually trigger consolidation
GET  /api/v1/learning/status           ← last run time, signals processed, errors
GET  /api/v1/learning/review-queue     ← HumanReviewQueue items (confidence < 0.7)
POST /api/v1/learning/review-queue/{id}/approve    ← approve pending policy
POST /api/v1/learning/review-queue/{id}/reject     ← reject pending policy
```

`HumanReviewQueue` already enqueues items with confidence < 0.7. These endpoints make the queue accessible for the UI's HITL (Human-in-the-Loop) Review page.

---

### Phase 7: ContextGateway → LLM Integration

**Priority: MEDIUM — enables Data-Cloud's brain to make LLM-backed decisions.**

`DefaultContextGateway` exists in `platform/` but is not wired to `AIIntegrationService` or `DefaultLLMGateway`.

#### 7.1 — Wire ContextGateway in DI

```java
// DataCloudBrainModule.java — add:
bind(ContextGateway.class).to(DefaultContextGateway.class).in(Singleton.class);
bind(AIIntegrationService.class).in(Singleton.class);  // from libs:ai-integration
bind(LLMGateway.class).to(DefaultLLMGateway.class).in(Singleton.class);

// DefaultContextGateway constructor injection:
// DefaultContextGateway(LLMGateway llmGateway, EmbeddingService embeddingService)
```

#### 7.2 — Connect `EpisodicToSemanticConsolidator` to Real LLM

`EpisodicToSemanticConsolidator` uses `LLMFactExtractor` which currently stubs out the LLM call. Wire it to `DefaultLLMGateway`:

```java
// agent-learning module — LLMFactExtractor:
@Override
public Promise<List<ExtractedFact>> extractFacts(List<EpisodicMemory> episodes) {
    String prompt = PromptBuilder.factExtractionPrompt(episodes);
    return llmGateway.complete(LLMRequest.of(prompt))
        .map(response -> FactParser.parse(response.text()));
}
```

---

### Phase 8: Analytics REST Expansion

**Priority: MEDIUM — exposes Data-Cloud's full query power.**

`AnalyticsQueryEngine` supports filtering, sorting, pagination, time-bucket, full-text search, and aggregations via the `RecordQuery` fluent builder. Currently only basic entity CRUD is exposed.

#### 8.1 — New Route Group: `/api/v1/analytics`

```
POST   /api/v1/analytics/query              ← execute RecordQuery; returns paginated results
POST   /api/v1/analytics/query/stream       ← SSE-stream large result sets
GET    /api/v1/analytics/schema/{collection} ← infer schema from stored records
POST   /api/v1/analytics/aggregate          ← run aggregation pipeline
GET    /api/v1/analytics/query-plan         ← explain query execution plan
```

**File:** `products/data-cloud/platform/src/main/java/com/ghatana/datacloud/http/AnalyticsRoutes.java`

```java
/**
 * @doc.type class
 * @doc.purpose REST routes exposing AnalyticsQueryEngine for ad-hoc data exploration
 * @doc.layer product
 * @doc.pattern Service
 */
public class AnalyticsRoutes implements RoutesModule {

    private final AnalyticsQueryEngine queryEngine;

    @Override
    public void register(RoutingServlet router) {
        router.add(POST, "/api/v1/analytics/query",        this::executeQuery);
        router.add(POST, "/api/v1/analytics/query/stream", this::streamQuery);
        router.add(POST, "/api/v1/analytics/aggregate",    this::aggregate);
        router.add(GET,  "/api/v1/analytics/query-plan",   this::explainQuery);
    }

    private Promise<HttpResponse> executeQuery(HttpRequest request) {
        String tenantId = extractTenantId(request);
        QueryRequest body = parseBody(request, QueryRequest.class);
        RecordQuery query = RecordQuery.builder()
            .collection(body.collection())
            .filters(body.filters())
            .sorts(body.sorts())
            .pagination(body.page(), body.pageSize())
            .timeBucket(body.timeBucket())
            .build();

        return queryEngine.execute(TenantContext.of(tenantId), query)
            .map(results -> HttpResponse.ok200().withJson(results));
    }
}
```

---

### Phase 9: Streaming SSE Endpoints

**Priority: MEDIUM — real-time UI requires live data feeds.**

#### 9.1 — `SseManager`: Reusable SSE Infrastructure

**File:** `products/data-cloud/platform/src/main/java/com/ghatana/datacloud/http/SseManager.java`

```java
/**
 * @doc.type class
 * @doc.purpose Manages Server-Sent Events streams for Data-Cloud HTTP server
 * @doc.layer product
 * @doc.pattern Service
 */
public class SseManager {

    // Creates an ActiveJ HTTP response with chunked SSE encoding
    public Promise<HttpResponse> createStream(
        String tenantId,
        String topic,
        Consumer<SseSubscriber> subscribeCallback
    ) {
        // Use ActiveJ's response body writer
        // Content-Type: text/event-stream
        // Connection: keep-alive
        // Cache-Control: no-cache
    }
}
```

#### 9.2 — New SSE Endpoints

```
GET /api/v1/events/stream                    ← tail all events (HOT tier) for tenant via SSE
GET /api/v1/events/stream?type={eventType}   ← tail filtered by event type
GET /api/v1/events/stream?collection={col}   ← tail filtered by collection/source
GET /api/v1/brain/workspace/stream           ← (Phase 5) GlobalWorkspace spotlight SSE
GET /api/v1/agents/events/stream             ← (Phase 3) agent-registry-events SSE
GET /api/v1/learning/stream                  ← consolidation pipeline progress SSE
```

**EventLogStore tail → SSE:**

```java
// EventStreamRoutes.java
private Promise<HttpResponse> tailEvents(HttpRequest request) {
    String tenantId = extractTenantId(request);
    String eventType = request.getQueryParameter("type");
    TenantContext ctx = TenantContext.of(tenantId);

    return sseManager.createStream(tenantId, "events", subscriber -> {
        // Use HOT tier (Redis) for real-time tail
        hotTierStore.tail(ctx, 0L, entry -> {
            if (eventType == null || entry.eventType().equals(eventType)) {
                subscriber.send(SseEvent.of("event", JSON.toJson(entry)));
            }
        });
    });
}
```

---

## 5. UI/UX Plan

### Design System Constraints

- **Component library:** `@ghatana/ui` (import all primitives from here; never reinvent)
- **Canvas:** `@ghatana/flow-canvas` (replace any custom `@xyflow/react` usage)
- **Real-time:** `@ghatana/realtime` (SSE abstraction; use `useEventStream` hook)
- **State:** Jotai for local/app state; TanStack Query for server state with proper `staleTime`
- **Forms:** `react-hook-form` with Zod validation
- **Routing:** Existing React Router setup (no changes needed)
- **Styling:** Tailwind CSS only — no inline styles, no CSS modules

---

### UI Area 1: Event Explorer (`/data-cloud/events`)

**Purpose:** Browse, filter, search, and tail live events from the `EventLogStore` across all storage tiers.

#### Layout

```
┌──────────────────────────────────────────────────────────────────┐
│ Event Explorer                              [Tier: HOT ▼] [Tail ●]│
├─────────────┬────────────────────────────────────────────────────┤
│ Filters     │ Event Log                                           │
│ ─────────── │ ┌──────────────────────────────────────────────┐   │
│ Type        │ │ Timestamp  Type              Offset  Tenant   │   │
│ [input]     │ │ 10:42:31   agent.registered  1024    tenant-1 │   │
│             │ │ 10:42:30   memory.stored     1023    tenant-1 │   │
│ Time Range  │ │ ...                                           │   │
│ [date-from] │ └──────────────────────────────────────────────┘   │
│ [date-to]   │                                                      │
│             │ ┌─ Event Detail ──────────────────────────────────┐ │
│ Collection  │ │ {                                               │ │
│ [select]    │ │   "eventId": "...",                             │ │
│             │ │   "payload": { ... }                            │ │
│ [Search]    │ │ }                                               │ │
│             │ └─────────────────────────────────────────────────┘ │
└─────────────┴────────────────────────────────────────────────────┘
```

#### Key Components

**File:** `products/data-cloud/ui/src/pages/EventExplorerPage.tsx`

```tsx
// Uses @ghatana/realtime's useEventStream for SSE tail
import { useEventStream } from "@ghatana/realtime";
import { useAtom } from "jotai";
import { eventFilterAtom, eventLogAtom } from "../atoms/event-explorer.atoms";

export function EventExplorerPage() {
  const [filters] = useAtom(eventFilterAtom);
  const [events, setEvents] = useAtom(eventLogAtom);

  // SSE tail — auto-reconnect handled by @ghatana/realtime
  const { status } = useEventStream(
    `/api/v1/events/stream?type=${filters.type}&collection=${filters.collection}`,
    {
      onMessage: (event) =>
        setEvents((prev) => [JSON.parse(event.data), ...prev].slice(0, 500)),
      enabled: filters.liveTail,
    },
  );

  return (
    <div className="flex h-full">
      <EventFilterSidebar />
      <div className="flex-1 flex flex-col">
        <EventLogTable events={events} />
        <EventDetailPanel />
      </div>
    </div>
  );
}
```

**New atoms:** `products/data-cloud/ui/src/atoms/event-explorer.atoms.ts`

```ts
export const eventFilterAtom = atom<EventFilter>({
  type: "",
  collection: "",
  fromDate: null,
  toDate: null,
  liveTail: false,
  tier: "HOT",
});

export const eventLogAtom = atom<EventEntry[]>([]);
export const selectedEventAtom = atom<EventEntry | null>(null);
```

**New service:** `products/data-cloud/ui/src/services/event.service.ts`

```ts
export const eventService = {
  query: (filter: EventFilter, page: number) =>
    axios.post("/api/v1/analytics/query", {
      collection: filter.collection,
      filters: buildFilters(filter),
      page,
      pageSize: 50,
    }),

  replayFrom: (offset: number) =>
    axios.get(`/api/v1/events/stream?fromOffset=${offset}`),
};
```

---

### UI Area 2: Memory Plane Viewer (`/data-cloud/memory`)

**Purpose:** Browse all 7 tiers of agent memory. Useful for debugging agent behavior, auditing memory retention, and inspecting semantic similarity.

#### Layout

```
┌──────────────────────────────────────────────────────────────────┐
│ Memory Plane Viewer          Agent: [agent-007 ▼]                 │
├──────────────────────────────────────────────────────────────────┤
│ [Episodic] [Semantic] [Procedural] [Task-State] [Working] [Pref] [Artifacts]│
├──────────────────────────────────────────────────────────────────┤
│ Search memories...  [🔍 Semantic Search]  [Decay Score ↑]         │
│ ─────────────────────────────────────────────────────────────── │
│ Memory Item Cards (3-column grid)                                  │
│ ┌────────────────┐ ┌────────────────┐ ┌────────────────┐         │
│ │ Subject        │ │ Subject        │ │ Subject        │         │
│ │ Predicate      │ │ Predicate      │ │ Predicate      │         │
│ │ Object         │ │ Object         │ │ Object         │         │
│ │ Conf: 0.87     │ │ Conf: 0.72     │ │ Conf: 0.65     │         │
│ │ [Retain] [Del] │ │ [Retain] [Del] │ │ [Retain] [Del] │         │
│ └────────────────┘ └────────────────┘ └────────────────┘         │
└──────────────────────────────────────────────────────────────────┘
```

#### Key Components

**File:** `products/data-cloud/ui/src/pages/MemoryPlaneViewerPage.tsx`

```tsx
import { useQuery, useMutation } from "@tanstack/react-query";
import { Tabs } from "@ghatana/ui";
import { memoryService } from "../services/memory.service";

const MEMORY_TIERS = [
  "episodic",
  "semantic",
  "procedural",
  "task-state",
  "working",
  "preference",
  "artifacts",
];

export function MemoryPlaneViewerPage() {
  const [selectedAgent] = useAtom(selectedAgentAtom);
  const [activeTier, setActiveTier] = useAtom(activeTierAtom);
  const [searchQuery, setSearchQuery] = useState("");

  const { data: summary } = useQuery({
    queryKey: ["memory-summary", selectedAgent],
    queryFn: () => memoryService.getSummary(selectedAgent),
    staleTime: 30_000,
  });

  const { data: memories, isLoading } = useQuery({
    queryKey: ["memories", selectedAgent, activeTier, searchQuery],
    queryFn: () =>
      searchQuery
        ? memoryService.semanticSearch(selectedAgent, searchQuery)
        : memoryService.getTierMemories(selectedAgent, activeTier),
    enabled: !!selectedAgent,
  });

  const retainMutation = useMutation({
    mutationFn: ({ memoryId }: { memoryId: string }) =>
      memoryService.retainMemory(selectedAgent, memoryId),
  });

  return (
    <div className="flex flex-col h-full gap-4 p-4">
      <AgentSelector />
      <MemorySummaryCards summary={summary} />
      <Tabs value={activeTier} onValueChange={setActiveTier}>
        {MEMORY_TIERS.map((tier) => (
          <Tabs.Tab
            key={tier}
            value={tier}
            label={`${tier} (${summary?.[tier] ?? 0})`}
          />
        ))}
      </Tabs>
      <SemanticSearchBar value={searchQuery} onChange={setSearchQuery} />
      <MemoryGrid
        memories={memories}
        onRetain={retainMutation.mutate}
        isLoading={isLoading}
      />
    </div>
  );
}
```

**New service:** `products/data-cloud/ui/src/services/memory.service.ts`

```ts
export const memoryService = {
  getSummary: (agentId: string) =>
    axios.get(`/api/v1/memory/${agentId}`).then((r) => r.data),

  getTierMemories: (agentId: string, tier: string, page = 0) =>
    axios
      .get(`/api/v1/memory/${agentId}/${tier}?page=${page}`)
      .then((r) => r.data),

  semanticSearch: (agentId: string, query: string, limit = 20) =>
    axios
      .post(`/api/v1/memory/${agentId}/search`, { query, limit })
      .then((r) => r.data),

  retainMemory: (agentId: string, memoryId: string) =>
    axios.put(`/api/v1/memory/${agentId}/${memoryId}/retain`),

  deleteMemory: (agentId: string, memoryId: string) =>
    axios.delete(`/api/v1/memory/${agentId}/${memoryId}`),
};
```

---

### UI Area 3: Entity Browser Redesign (`/data-cloud/entities`)

**Purpose:** Replace 4 partial stub pages with a single unified entity CRUD page that auto-discovers collections.

#### Layout

```
┌────────────────────────────────────────────────────────────────────┐
│ Entity Browser              [Collection: agent-registry ▼] [+ New]  │
├──────────────────────────────────────────────────────────────────────┤
│ Search entities...  [Filters ▼]                    Showing 1-20/154  │
│ ──────────────────────────────────────────────────────────────────── │
│ [Table View] [JSON View] [Schema View]                               │
│                                                                      │
│ ID           Type           Created        Updated       Actions     │
│ ──────────── ──────────── ────────────── ────────────── ─────────── │
│ agent-007    ADAPTIVE      2 hours ago   1 min ago      [Edit][Del]  │
│ agent-042    DETERMINISTIC 1 day ago     5 hours ago    [Edit][Del]  │
│                                                                      │
│ [← 1 2 3 ... 8 →]                                                   │
└──────────────────────────────────────────────────────────────────────┘
```

#### Key Components

**File:** `products/data-cloud/ui/src/pages/EntityBrowserPage.tsx`

```tsx
import { useQuery } from "@tanstack/react-query";
import { DataTable, Button, Select } from "@ghatana/ui";
import { entityService } from "../services/entity.service";

export function EntityBrowserPage() {
  const [collection, setCollection] = useAtom(selectedCollectionAtom);
  const [pagination, setPagination] = useAtom(paginationAtom);
  const [viewMode, setViewMode] = useState<"table" | "json" | "schema">(
    "table",
  );

  const { data: collections } = useQuery({
    queryKey: ["collections"],
    queryFn: entityService.listCollections,
    staleTime: 60_000,
  });

  const { data, isLoading } = useQuery({
    queryKey: ["entities", collection, pagination],
    queryFn: () => entityService.list(collection, pagination),
    enabled: !!collection,
  });

  return (
    <div className="flex flex-col h-full p-4 gap-4">
      <EntityBrowserHeader
        collections={collections}
        selectedCollection={collection}
        onCollectionChange={setCollection}
      />
      <ViewModeTabs value={viewMode} onChange={setViewMode} />
      {viewMode === "table" && (
        <EntityDataTable data={data} isLoading={isLoading} />
      )}
      {viewMode === "json" && <EntityJsonView data={data} />}
      {viewMode === "schema" && <EntitySchemaView collection={collection} />}
      <Pagination
        {...pagination}
        onChange={setPagination}
        total={data?.total}
      />
    </div>
  );
}
```

---

### UI Area 4: Data Fabric View (`/data-cloud/fabric`)

**Purpose:** Visualize the four-tier storage topology (HOT→WARM→COOL→COLD) as a living canvas with real-time throughput metrics on each tier.

**Technology:** `@ghatana/flow-canvas` — NOT raw `@xyflow/react`.

#### Layout

```
┌──────────────────────────────────────────────────────────────────────┐
│ Data Fabric                    [Auto-Layout] [Export] [Live Metrics ●]│
├──────────────────────────────────────────────────────────────────────┤
│                                                                        │
│   ┌─────────────┐                                                      │
│   │  🔴 HOT     │ ──events/sec──▶ ┌─────────────┐                    │
│   │  Redis      │                  │  🟡 WARM    │                    │
│   │  <1ms       │                  │  PostgreSQL │                    │
│   │  12k ev/s   │ ◀──backpressure─ │  <10ms      │                    │
│   └─────────────┘                  └─────────────┘                    │
│          │                                │                            │
│      (tiering)                        (archival)                      │
│          ↓                                ↓                            │
│   ┌─────────────┐                  ┌─────────────┐                    │
│   │  🔵 COOL    │ ──compaction──▶  │  ⚫ COLD    │                    │
│   │  Iceberg    │                  │  S3/GCS     │                    │
│   │  <100ms     │                  │  seconds    │                    │
│   └─────────────┘                  └─────────────┘                    │
│                                                                        │
│  Right Panel: Selected Tier Details                                    │
│  ┌──────────────────────────────────────────┐                         │
│  │ HOT — Redis                              │                         │
│  │ Host: redis:6379                         │                         │
│  │ Events/sec: 12,304                       │                         │
│  │ Memory: 2.3 GB / 8 GB                    │                         │
│  │ Latency p99: 0.8ms                       │                         │
│  └──────────────────────────────────────────┘                         │
└──────────────────────────────────────────────────────────────────────┘
```

#### Key Components

**File:** `products/data-cloud/ui/src/pages/DataFabricPage.tsx`

```tsx
import { FlowCanvas, FlowNode, FlowEdge } from "@ghatana/flow-canvas";
import { useEventStream } from "@ghatana/realtime";
import { storageTierNodes, storageTierEdges } from "../data/fabric-topology";

export function DataFabricPage() {
  const [metrics, setMetrics] = useAtom(fabricMetricsAtom);
  const [selectedTier, setSelectedTier] = useState<string | null>(null);

  // Live metrics via SSE
  useEventStream("/api/v1/brain/workspace/stream", {
    onMessage: (event) => {
      const spotlight = JSON.parse(event.data);
      setMetrics((prev) => mergeTierMetrics(prev, spotlight));
    },
  });

  // Enrich static topology nodes with live metric data
  const enrichedNodes: FlowNode[] = storageTierNodes.map((node) => ({
    ...node,
    data: { ...node.data, metrics: metrics[node.id] },
  }));

  return (
    <div className="flex h-full">
      <FlowCanvas
        nodes={enrichedNodes}
        edges={storageTierEdges}
        onNodeClick={(node) => setSelectedTier(node.id)}
        nodeTypes={{ storageTier: StorageTierNode }}
        fitView
      />
      {selectedTier && (
        <TierDetailPanel
          tierId={selectedTier}
          metrics={metrics[selectedTier]}
        />
      )}
    </div>
  );
}
```

**File:** `products/data-cloud/ui/src/data/fabric-topology.ts`

```ts
export const storageTierNodes: FlowNode[] = [
  {
    id: "hot",
    type: "storageTier",
    position: { x: 100, y: 200 },
    data: { label: "HOT — Redis", tier: "HOT", sla: "<1ms" },
  },
  {
    id: "warm",
    type: "storageTier",
    position: { x: 400, y: 200 },
    data: { label: "WARM — PostgreSQL", tier: "WARM", sla: "<10ms" },
  },
  {
    id: "cool",
    type: "storageTier",
    position: { x: 100, y: 450 },
    data: { label: "COOL — Iceberg", tier: "COOL", sla: "<100ms" },
  },
  {
    id: "cold",
    type: "storageTier",
    position: { x: 400, y: 450 },
    data: { label: "COLD — S3", tier: "COLD", sla: "seconds" },
  },
];

export const storageTierEdges: FlowEdge[] = [
  {
    id: "hot-warm",
    source: "hot",
    target: "warm",
    label: "tiering",
    animated: true,
  },
  {
    id: "warm-cool",
    source: "warm",
    target: "cool",
    label: "archival",
    animated: true,
  },
  {
    id: "cool-cold",
    source: "cool",
    target: "cold",
    label: "compaction",
    animated: true,
  },
];
```

---

### UI Area 5: Agent Plugin Manager (`/data-cloud/agents`)

**Purpose:** Register, configure, monitor, and deregister agentic plugins that use Data-Cloud as their storage substrate.

#### Layout

```
┌────────────────────────────────────────────────────────────────────┐
│ Agent Plugin Manager          [+ Register Agent] [Filter: All ▼]   │
├────────────────────────────────────────────────────────────────────┤
│ Agent Cards (2-column)                                              │
│ ┌────────────────────────┐  ┌────────────────────────┐            │
│ │ 🤖 agent-007           │  │ 🤖 agent-042           │            │
│ │ Type: ADAPTIVE         │  │ Type: DETERMINISTIC    │            │
│ │ Status: ● Active       │  │ Status: ○ Idle         │            │
│ │ Memory: 2,304 items    │  │ Memory: 892 items      │            │
│ │ Executions: 1,204      │  │ Executions: 342        │            │
│ │ Last seen: 2 min ago   │  │ Last seen: 1 hour ago  │            │
│ │ [View] [Config] [Stop] │  │ [View] [Config] [Stop] │            │
│ └────────────────────────┘  └────────────────────────┘            │
│                                                                     │
│ [← 1 2 3 →]                                                        │
├────────────────────────────────────────────────────────────────────┤
│ Recent Registry Events (SSE-live)                                   │
│ 10:44:01 agent.registered   agent-007  tenant-1                     │
│ 10:43:55 agent.deregistered agent-001  tenant-2                     │
└────────────────────────────────────────────────────────────────────┘
```

#### Key Components

**File:** `products/data-cloud/ui/src/pages/AgentPluginManagerPage.tsx`

```tsx
import { useEventStream } from "@ghatana/realtime";
import { useQuery, useMutation } from "@tanstack/react-query";
import { agentRegistryService } from "../services/agent-registry.service";

export function AgentPluginManagerPage() {
  const [registryEvents, setRegistryEvents] = useState<RegistryEvent[]>([]);

  const { data: agents, refetch } = useQuery({
    queryKey: ["agents"],
    queryFn: agentRegistryService.listAgents,
    staleTime: 15_000,
  });

  // Live registry events
  useEventStream("/api/v1/agents/events/stream", {
    onMessage: (event) => {
      const registryEvent = JSON.parse(event.data);
      setRegistryEvents((prev) => [registryEvent, ...prev].slice(0, 100));
      refetch(); // refresh agent list on new event
    },
  });

  const deregisterMutation = useMutation({
    mutationFn: agentRegistryService.deregisterAgent,
    onSuccess: () => refetch(),
  });

  return (
    <div className="flex flex-col h-full p-4 gap-6">
      <AgentManagerHeader />
      <AgentCardGrid agents={agents} onDeregister={deregisterMutation.mutate} />
      <RegistryEventsFeed events={registryEvents} />
    </div>
  );
}
```

**New service:** `products/data-cloud/ui/src/services/agent-registry.service.ts`

```ts
export const agentRegistryService = {
  listAgents: (page = 0) =>
    axios.get(`/api/v1/agents?page=${page}`).then((r) => r.data),

  getAgent: (agentId: string) =>
    axios.get(`/api/v1/agents/${agentId}`).then((r) => r.data),

  registerAgent: (registration: AgentRegistrationRequest) =>
    axios.post("/api/v1/agents/register", registration).then((r) => r.data),

  deregisterAgent: (agentId: string) =>
    axios.delete(`/api/v1/agents/${agentId}`),

  getExecutions: (agentId: string, page = 0) =>
    axios
      .get(`/api/v1/agents/${agentId}/executions?page=${page}`)
      .then((r) => r.data),
};
```

---

### UI Area 6: Brain Dashboard Redesign (`/data-cloud/brain`)

**Purpose:** Replace stub `BrainDashboardPage` with a real-time view of the cognitive layer — GlobalWorkspace spotlight, AttentionManager state, ReflexEngine pattern matches, and SalienceScorer scores.

#### Layout

```
┌────────────────────────────────────────────────────────────────────┐
│ Brain Dashboard                          [Pause ∥] [Live ●]         │
├───────────────────┬────────────────────────────────────────────────┤
│ Attention         │ GlobalWorkspace Spotlight                        │
│ ───────────────── │ ──────────────────────────────────────────────  │
│ Elevation:  0.70  │ Item ID          Salience  Type       Age       │
│ Emergency:  0.95  │ plan-2024-001    0.97 🔴  TASK       2s        │
│                   │ memory-a7b2      0.83 🟡  SEMANTIC   15s       │
│ Active Items: 12  │ signal-x912      0.71 🟢  EVENT      30s       │
│ Emergency:     2  │                                                  │
│ ───────────────── │ [Evict selected]                                 │
│ Patterns          ├────────────────────────────────────────────────┤
│ ───────────────── │ Recent Pattern Matches (ReflexEngine)           │
│ Loaded:    48     │ ────────────────────────────────────────────    │
│ Matched:  312/hr  │ 10:44:02  user.query  → pattern-financial-q     │
│                   │ 10:43:58  memory.req  → pattern-recall-recent   │
│ [View All]        │                                                  │
└───────────────────┴────────────────────────────────────────────────┘
```

#### Key Components

**File:** `products/data-cloud/ui/src/pages/BrainDashboardPage.tsx` (rewrite existing stub)

```tsx
import { useEventStream } from "@ghatana/realtime";
import { useQuery } from "@tanstack/react-query";
import { brainService } from "../services/brain.service";

export function BrainDashboardPage() {
  const [spotlight, setSpotlight] = useAtom(spotlightAtom);
  const [patternMatches, setPatternMatches] = useAtom(patternMatchesAtom);
  const [isPaused, setIsPaused] = useState(false);

  const { data: thresholds } = useQuery({
    queryKey: ["brain-thresholds"],
    queryFn: brainService.getThresholds,
    staleTime: 60_000,
  });

  // Live GlobalWorkspace updates
  useEventStream("/api/v1/brain/workspace/stream", {
    onMessage: (event) => {
      if (!isPaused) setSpotlight(JSON.parse(event.data));
    },
    enabled: !isPaused,
  });

  const { data: patterns } = useQuery({
    queryKey: ["brain-patterns"],
    queryFn: brainService.listPatterns,
    staleTime: 30_000,
  });

  return (
    <div className="flex h-full">
      <div className="w-64 flex flex-col p-4 gap-6 border-r">
        <AttentionPanel thresholds={thresholds} spotlight={spotlight} />
        <PatternSummaryPanel patterns={patterns} />
      </div>
      <div className="flex-1 flex flex-col">
        <SpotlightTable items={spotlight?.items ?? []} />
        <PatternMatchFeed matches={patternMatches} />
      </div>
    </div>
  );
}
```

**New service:** `products/data-cloud/ui/src/services/brain.service.ts` (extend existing file)

```ts
// Add to existing brain.service.ts:
export const brainService = {
  ...existingBrainService,

  getWorkspace: () => axios.get("/api/v1/brain/workspace").then((r) => r.data),

  getThresholds: () =>
    axios.get("/api/v1/brain/attention/thresholds").then((r) => r.data),

  listPatterns: () => axios.get("/api/v1/brain/patterns").then((r) => r.data),

  matchPatterns: (input: PatternMatchRequest) =>
    axios.post("/api/v1/brain/patterns/match", input).then((r) => r.data),

  elevateAttention: (itemId: string, salience: number) =>
    axios.post("/api/v1/brain/attention/elevate", { itemId, salience }),
};
```

---

### UI Area 7: Workflow Canvas Redesign (`/data-cloud/workflows`)

**Purpose:** Replace the existing custom `@xyflow/react` node implementation with `@ghatana/flow-canvas`, and wire workflow execution to real backend endpoints.

#### Migration Strategy

1. Replace all `import { ReactFlow, ... } from '@xyflow/react'` with `import { FlowCanvas, ... } from '@ghatana/flow-canvas'`
2. Replace custom node type implementations with `@ghatana/flow-canvas` node types
3. Retain existing Jotai atoms: `workflowAtom`, `nodesAtom`, `edgesAtom`, `selectedNodeAtom`, `executionAtom`
4. Wire workflow save/load to `/api/v1/workflows` endpoints
5. Wire workflow execution to `/api/v1/workflows/{id}/execute` + SSE for progress

#### Key Changes

**File:** `products/data-cloud/ui/src/pages/WorkflowDesigner.tsx`

```tsx
// BEFORE (remove):
import { ReactFlow, Background, Controls } from "@xyflow/react";

// AFTER:
import { FlowCanvas } from "@ghatana/flow-canvas";

// Replace <ReactFlow> usage:
return (
  <FlowCanvas
    nodes={nodes}
    edges={edges}
    onNodesChange={onNodesChange}
    onEdgesChange={onEdgesChange}
    onConnect={onConnect}
    nodeTypes={workflowNodeTypes} // Migrate custom types to @ghatana/flow-canvas API
    fitView
  >
    <WorkflowToolbar />
    <ExecutionStatusPanel />
  </FlowCanvas>
);
```

**Workflow execution SSE:**

```tsx
// Wire execution progress to SSE:
const { data: execution } = useEventStream(
  `/api/v1/workflows/${workflowId}/execute/stream`,
  {
    onMessage: (event) => setExecutionStatus(JSON.parse(event.data)),
    enabled: isExecuting,
  },
);
```

---

### Additional Page Wiring (Existing Pages → Real Backend)

#### Dashboard Page

Replace all `Math.random()` / mock data in `DashboardPage.tsx` with:

- TanStack Query calls to `/api/v1/analytics/query` for event counts
- Aggregation calls to `/api/v1/analytics/aggregate` for time-series charts
- `staleTime: 30_000` to avoid over-fetching

#### SQL Workspace

Wire `SqlWorkspacePage.tsx` to:

- `POST /api/v1/analytics/query` (translate SQL WHERE clause to `RecordQuery` filters)
- `GET /api/v1/analytics/schema/{collection}` for autocomplete

#### Governance & Lineage

Wire `GovernancePage.tsx` and `LineageExplorerPage.tsx` to:

- `GET /api/v1/entities` with `collection=lineage-records`
- Build lineage graph from entity relationships using `@ghatana/flow-canvas`

#### Alerts

Wire `AlertsPage.tsx` to:

- `POST /api/v1/entities` with `collection=alert-rules`
- SSE: subscribe to alert-trigger events via `/api/v1/events/stream?type=alert.triggered`

---

## 6. Configuration Reference

All Data-Cloud environment variables, their defaults, and descriptions:

| Variable                                   | Default                         | Required          | Description                                       |
| ------------------------------------------ | ------------------------------- | ----------------- | ------------------------------------------------- |
| `DATACLOUD_HTTP_PORT`                      | `8080`                          | No                | HTTP server port                                  |
| `DATACLOUD_GRPC_PORT`                      | `9090`                          | No                | gRPC server port                                  |
| `DATACLOUD_REDIS_HOST`                     | —                               | **Yes**           | Redis hostname                                    |
| `DATACLOUD_REDIS_PORT`                     | `6379`                          | No                | Redis port                                        |
| `DATACLOUD_REDIS_PASSWORD`                 | —                               | No                | Redis password (blank = no auth)                  |
| `DATACLOUD_REDIS_MAX_CONNECTIONS`          | `20`                            | No                | Redis connection pool size                        |
| `DATACLOUD_REDIS_TEST_ON_BORROW`           | `true`                          | No                | Validate connection before use                    |
| `DATACLOUD_PG_URL`                         | —                               | **Yes**           | JDBC URL: `jdbc:postgresql://host:5432/datacloud` |
| `DATACLOUD_PG_USER`                        | —                               | **Yes**           | PostgreSQL username                               |
| `DATACLOUD_PG_PASSWORD`                    | —                               | **Yes**           | PostgreSQL password                               |
| `DATACLOUD_PG_MAX_POOL_SIZE`               | `20`                            | No                | HikariCP max pool size                            |
| `DATACLOUD_ICEBERG_WAREHOUSE`              | `file:///tmp/iceberg-warehouse` | No                | Iceberg catalog warehouse location                |
| `DATACLOUD_ICEBERG_CATALOG_TYPE`           | `jdbc`                          | No                | `rest`, `hive`, or `jdbc`                         |
| `DATACLOUD_ICEBERG_CATALOG_URI`            | —                               | No                | Catalog URI (for REST/Hive catalogs)              |
| `DATACLOUD_S3_BUCKET`                      | —                               | **Yes**           | S3 bucket name for COLD tier                      |
| `DATACLOUD_S3_REGION`                      | `us-east-1`                     | No                | AWS region                                        |
| `DATACLOUD_S3_ENDPOINT`                    | —                               | No                | Custom endpoint for MinIO/LocalStack              |
| `DATACLOUD_S3_ACCESS_KEY`                  | —                               | No                | AWS access key (blank = IAM role)                 |
| `DATACLOUD_S3_SECRET_KEY`                  | —                               | No                | AWS secret key (blank = IAM role)                 |
| `DATACLOUD_KAFKA_BOOTSTRAP_SERVERS`        | `localhost:9092`                | No                | Kafka bootstrap servers                           |
| `DATACLOUD_KAFKA_GROUP_ID`                 | `data-cloud`                    | No                | Kafka consumer group ID                           |
| `DATACLOUD_LLM_API_URL`                    | —                               | **Yes** (Phase 7) | LLM API endpoint for ContextGateway               |
| `DATACLOUD_LLM_API_KEY`                    | —                               | **Yes** (Phase 7) | LLM API key                                       |
| `DATACLOUD_CONSOLIDATION_INTERVAL_MINUTES` | `5`                             | No                | Learning loop trigger interval                    |

---

## 7. New Files List

### Java Backend

| File                                                                           | Phase | Purpose                                           |
| ------------------------------------------------------------------------------ | ----- | ------------------------------------------------- |
| `products/data-cloud/platform/src/.../config/DataCloudEnvConfig.java`          | 0     | Env-driven config record                          |
| `products/data-cloud/platform/src/.../config/DataCloudStartupValidator.java`   | 0     | Startup connectivity validation                   |
| `products/data-cloud/platform/src/.../plugins/warm/WarmTierEventLogStore.java` | 1     | PostgreSQL WARM tier SPI impl                     |
| `products/data-cloud/platform/src/.../grpc/EventLogGrpcService.java`           | 2     | gRPC EventLogService handler                      |
| `products/data-cloud/platform/src/.../grpc/EventQueryGrpcService.java`         | 2     | gRPC EventQueryService handler                    |
| `products/data-cloud/platform/src/.../grpc/EventServiceGrpcService.java`       | 2     | gRPC EventService (ingest/stream) handler         |
| `products/data-cloud/platform/src/.../grpc/ProtobufMapper.java`                | 2     | Proto ↔ domain object mapping                     |
| `products/data-cloud/platform/src/.../http/AgentRegistryRoutes.java`           | 3     | HTTP routes for agent registry                    |
| `products/data-cloud/platform/src/.../http/CheckpointRoutes.java`              | 3     | HTTP routes for checkpoint store                  |
| `products/data-cloud/platform/src/.../http/MemoryPlaneRoutes.java`             | 4     | HTTP routes for memory plane                      |
| `products/data-cloud/platform/src/.../http/BrainRoutes.java`                   | 5     | HTTP + SSE routes for brain/attention             |
| `products/data-cloud/platform/src/.../http/SseManager.java`                    | 9     | Reusable SSE stream manager                       |
| `products/data-cloud/platform/src/.../http/EventStreamRoutes.java`             | 9     | SSE tail endpoint routes                          |
| `products/data-cloud/platform/src/.../learning/LearningBridge.java`            | 6     | Bridges FeedbackCollector → ConsolidationPipeline |
| `products/data-cloud/platform/src/.../http/AnalyticsRoutes.java`               | 8     | Analytics query REST routes                       |
| `products/data-cloud/platform/src/.../http/LearningRoutes.java`                | 6     | HITL review queue REST routes                     |
| `products/data-cloud/platform/db/migrations/V001__create_event_log.sql`        | 1     | PostgreSQL event_log table                        |

### TypeScript UI

| File                                                                      | UI Area | Purpose                          |
| ------------------------------------------------------------------------- | ------- | -------------------------------- |
| `products/data-cloud/ui/src/pages/EventExplorerPage.tsx`                  | 1       | Event log browser with SSE tail  |
| `products/data-cloud/ui/src/atoms/event-explorer.atoms.ts`                | 1       | Jotai atoms for event explorer   |
| `products/data-cloud/ui/src/services/event.service.ts`                    | 1       | API calls for event log          |
| `products/data-cloud/ui/src/components/events/EventFilterSidebar.tsx`     | 1       | Filter controls                  |
| `products/data-cloud/ui/src/components/events/EventLogTable.tsx`          | 1       | Virtualized event log table      |
| `products/data-cloud/ui/src/components/events/EventDetailPanel.tsx`       | 1       | JSON payload viewer              |
| `products/data-cloud/ui/src/pages/MemoryPlaneViewerPage.tsx`              | 2       | 7-tier agent memory browser      |
| `products/data-cloud/ui/src/atoms/memory-plane.atoms.ts`                  | 2       | Jotai atoms for memory plane     |
| `products/data-cloud/ui/src/services/memory.service.ts`                   | 2       | API calls for memory plane       |
| `products/data-cloud/ui/src/components/memory/MemoryGrid.tsx`             | 2       | Memory item card grid            |
| `products/data-cloud/ui/src/components/memory/SemanticSearchBar.tsx`      | 2       | Semantic search UI               |
| `products/data-cloud/ui/src/components/memory/MemorySummaryCards.tsx`     | 2       | Per-tier count summary           |
| `products/data-cloud/ui/src/pages/EntityBrowserPage.tsx`                  | 3       | Unified entity CRUD page         |
| `products/data-cloud/ui/src/atoms/entity-browser.atoms.ts`                | 3       | Jotai atoms for entity browser   |
| `products/data-cloud/ui/src/services/entity.service.ts`                   | 3       | API calls for entity CRUD        |
| `products/data-cloud/ui/src/components/entities/EntityDataTable.tsx`      | 3       | Entity table component           |
| `products/data-cloud/ui/src/components/entities/EntityJsonView.tsx`       | 3       | Raw JSON view for entities       |
| `products/data-cloud/ui/src/pages/DataFabricPage.tsx`                     | 4       | Storage tier topology canvas     |
| `products/data-cloud/ui/src/atoms/data-fabric.atoms.ts`                   | 4       | Jotai atoms for fabric metrics   |
| `products/data-cloud/ui/src/data/fabric-topology.ts`                      | 4       | Static topology nodes/edges      |
| `products/data-cloud/ui/src/components/fabric/StorageTierNode.tsx`        | 4       | Custom FlowCanvas node for tiers |
| `products/data-cloud/ui/src/components/fabric/TierDetailPanel.tsx`        | 4       | Right-panel tier details         |
| `products/data-cloud/ui/src/pages/AgentPluginManagerPage.tsx`             | 5       | Agent registration + monitoring  |
| `products/data-cloud/ui/src/atoms/agent-registry.atoms.ts`                | 5       | Jotai atoms for agent registry   |
| `products/data-cloud/ui/src/services/agent-registry.service.ts`           | 5       | API calls for agent registry     |
| `products/data-cloud/ui/src/components/agents/AgentCardGrid.tsx`          | 5       | Agent cards grid                 |
| `products/data-cloud/ui/src/components/agents/AgentRegistrationModal.tsx` | 5       | Register new agent form          |
| `products/data-cloud/ui/src/components/agents/RegistryEventsFeed.tsx`     | 5       | Live registry events SSE feed    |
| `products/data-cloud/ui/src/atoms/brain.atoms.ts`                         | 6       | Jotai atoms for brain dashboard  |

---

## 8. Modifications List

### Java Backend

| File                                                               | Change                                                                                                                                            |
| ------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| `products/data-cloud/platform/src/.../DataCloudStorageModule.java` | Inject `DataCloudEnvConfig`; wire WARM tier; env-driven plugin construction                                                                       |
| `products/data-cloud/platform/src/.../DataCloudBrainModule.java`   | Add `ContextGateway`, `AIIntegrationService`, `LLMGateway` bindings (Phase 7)                                                                     |
| `products/data-cloud/platform/src/.../DataCloudCoreModule.java`    | Add `LearningBridge`, `ConsolidationPipeline`, `SseManager`, all new route modules                                                                |
| `products/data-cloud/platform/src/.../DataCloudHttpServer.java`    | Register: `AgentRegistryRoutes`, `CheckpointRoutes`, `MemoryPlaneRoutes`, `BrainRoutes`, `AnalyticsRoutes`, `EventStreamRoutes`, `LearningRoutes` |
| `products/data-cloud/platform/src/.../DataCloudLauncher.java`      | Start gRPC server; call `DataCloudStartupValidator`; start `LearningBridge`; use `DataCloudEnvConfig` for HTTP port                               |
| `platform/java/agent-learning/src/.../LLMFactExtractor.java`       | Wire to `DefaultLLMGateway` instead of stub (Phase 7)                                                                                             |

### TypeScript UI

| File                                                       | Change                                                              |
| ---------------------------------------------------------- | ------------------------------------------------------------------- |
| `products/data-cloud/ui/src/pages/BrainDashboardPage.tsx`  | Full rewrite — replace mock with real SSE + TanStack Query          |
| `products/data-cloud/ui/src/pages/WorkflowDesigner.tsx`    | Replace `@xyflow/react` imports with `@ghatana/flow-canvas`         |
| `products/data-cloud/ui/src/pages/DashboardPage.tsx`       | Replace mock data with TanStack Query → analytics endpoints         |
| `products/data-cloud/ui/src/pages/SqlWorkspacePage.tsx`    | Wire query execution to `/api/v1/analytics/query`                   |
| `products/data-cloud/ui/src/services/brain.service.ts`     | Add workspace, thresholds, patterns, match, elevate methods         |
| `products/data-cloud/ui/src/App.tsx` (or router file)      | Add routes: `/events`, `/memory`, `/entities`, `/fabric`, `/agents` |
| `products/data-cloud/ui/src/components/layout/Sidebar.tsx` | Add nav links for all new pages                                     |

---

## 9. Test Plan

### Phase 0 — Config Tests

```java
class DataCloudEnvConfigTest {
    @Test
    void shouldThrowWhenRequiredVarMissing() {
        // Given: DATACLOUD_REDIS_HOST not set
        // When: DataCloudEnvConfig.fromEnvironment()
        // Then: IllegalStateException with var name in message
    }

    @Test
    void shouldUseDefaultsForOptionalVars() {
        // Given: only required vars set
        // Then: defaults applied correctly
    }
}
```

### Phase 1 — WARM Tier Tests

```java
class WarmTierEventLogStoreTest extends EventloopTestBase {

    WarmTierEventLogStore store; // Wired with testcontainers PostgreSQL

    @Test
    void shouldAppendAndRead() {
        TenantContext ctx = TenantContext.of("tenant-test");
        EventEntry entry = TestDataBuilders.eventEntry().type("test.event").build();

        Long offset = runPromise(() -> store.append(ctx, entry));
        List<EventEntry> results = runPromise(() -> store.read(ctx, offset, 10));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).eventType()).isEqualTo("test.event");
    }

    @Test
    void shouldEnforceIdempotency() {
        // Two appends with same idempotency key → same offset returned
    }

    @Test
    void shouldFilterByTimeRange() {
        // Append 3 events at t1, t2, t3 → readByTimeRange(t1, t2) returns 2
    }

    @Test
    void shouldEnforceTenantIsolation() {
        // Events for tenant-A must not appear in tenant-B reads (ADR-005)
    }
}
```

### Phase 2 — gRPC Integration Tests

```java
class EventLogGrpcServiceTest extends EventloopTestBase {

    @Test
    void shouldAppendViaGrpc() {
        // Use in-process gRPC channel
        // IngestRequest → verify EventLogStore receives event
    }

    @Test
    void shouldStreamQueryResults() {
        // Append 100 events → ExecuteQuery → collect server-streaming responses
        // assertThat(received).hasSize(100)
    }
}
```

### Phase 3 — Agent Registry Tests

```java
class AgentRegistryRoutesTest extends EventloopTestBase {

    @Test
    void shouldRegisterAgent() {
        // POST /api/v1/agents/register → 201
        // GET /api/v1/agents/{agentId} → agent details
    }

    @Test
    void shouldIsolateAgentsByTenant() {
        // Register in tenant-A; verify not visible in tenant-B GET /api/v1/agents
    }
}
```

### Phase 4 — Memory Plane Tests

```java
class MemoryPlaneRoutesTest extends EventloopTestBase {

    @Test
    void shouldReturnMemorySummaryByTier() {
        // Populate episodic + semantic memories
        // GET /api/v1/memory/{agentId} → counts per tier match
    }

    @Test
    void shouldSemanticSearch() {
        // Store memory with known embedding
        // POST /api/v1/memory/{agentId}/search → similar memory returned
    }
}
```

### Phase 5 — Brain SSE Tests

```java
class BrainRoutesTest extends EventloopTestBase {

    @Test
    void shouldStreamSpotlightUpdates() {
        // Connect SSE to /api/v1/brain/workspace/stream
        // Elevate an item via GlobalWorkspace
        // assertThat(received SSE event).containsItem(elevated item)
    }
}
```

### Phase 6 — Learning Loop Tests

```java
class LearningBridgeTest extends EventloopTestBase {

    @Test
    void shouldDrainFeedbackAndTriggerConsolidation() {
        // Add 3 signals to FeedbackCollector
        // Trigger LearningBridge.triggerConsolidation()
        // Verify ConsolidationPipeline.run() was called with those signals
    }
}
```

### UI Tests

All new pages require:

1. **Unit tests** (Jest + React Testing Library): render with mocked API responses; verify key elements present
2. **SSE tests**: mock `@ghatana/realtime`'s `useEventStream`; send synthetic events; verify UI updates
3. **Integration snapshot tests**: stable snapshots for key page layouts

---

## 10. Definition of Done

A phase is complete when ALL of the following are true:

- [ ] All new Java classes have JavaDoc with `@doc.type`, `@doc.purpose`, `@doc.layer`, `@doc.pattern` tags
- [ ] All async Java code uses `ActiveJ Promise<T>` — never `CompletableFuture` or Reactor
- [ ] All blocking I/O is wrapped with `Promise.ofBlocking(executor, ...)`
- [ ] All data access has `tenantId` extracted from HTTP header and passed into `TenantContext` (ADR-005)
- [ ] All new async tests extend `EventloopTestBase` — never call `.getResult()` on a Promise
- [ ] `./gradlew spotlessApply` passes (no formatting violations)
- [ ] `./gradlew checkstyleMain pmdMain` passes (zero warnings)
- [ ] `./gradlew test` passes (no failures)
- [ ] No hardcoded hostnames, ports, buckets, regions, or credentials in any production code
- [ ] New TypeScript components: no `any` types; strict null checks; all props typed
- [ ] New UI pages: TanStack Query used for server state; Jotai for local state; no direct `fetch` calls
- [ ] New UI pages: `@ghatana/ui` primitives used (no custom reinventions of Button, Select, Table, etc.)
- [ ] SSE connections handled via `@ghatana/realtime` hooks (not raw `EventSource` instantiation)
- [ ] All new routes registered in `DataCloudHttpServer` and router file for UI
- [ ] Sidebar nav links added for all new pages

---

_Document generated: 2025-01-19 | Maintainers: Platform Engineering_
_Next review: After Phase 3 completion_

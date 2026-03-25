# Data-Cloud Platform Comprehensive Audit Report

**Date:** 2026-03-24  
**Scope:** data-cloud — all modules (platform, `launcher`, `spi`, `sdk`, `feature-store-ingest`, `agent-catalog`, `agent-registry`)  
**Total Java source files audited:** ~788 (excl. SDK build-generated)

---

## Part 1: Module Architecture Overview

```
contracts/               (OpenAPI → SDK, Protobuf → gRPC stubs)
products/data-cloud/
  spi/                   DataCloudClient, EventLogStore, EntityStore, TenantContext
  platform/              Domain models, infrastructure connectors, DI modules, gRPC service
  launcher/              HTTP server, security filter, handlers, config validator
  feature-store-ingest/  Stand-alone polling pipeline → FeatureStore
  agent-catalog/         Agent definitions
  agent-registry/        Agent registry
  sdk/                   Generated Java SDK (OpenAPI)
```

**Dependency flow:** `launcher → platform → spi → libs:core/domain/observability`

---

## Part 2: Priority Findings

---

### FINDING-1 — **CRITICAL** | Event Loop Blocking in `WarmTierEventLogStore`

**File:** WarmTierEventLogStore.java  
**Module:** `data-cloud:platform`

**Issue:** Every SPI method (`append`, `appendBatch`, `read`, `readByTimeRange`, `readByType`, `getLatestOffset`, `getEarliestOffset`) executes blocking JDBC directly on the calling thread, wrapping results in `Promise.of(syncResult)`. None use `Promise.ofBlocking(executor, …)`.

**Evidence:**
```java
// append() — line ~133
@Override
public Promise<Offset> append(TenantContext tenant, EventEntry entry) {
    try {
        return Promise.of(doAppend(tenant.tenantId(), entry));  // ← JDBC on calling thread
    } catch (Exception e) {
        return Promise.ofException(e);
    }
}
// appendBatch() — line ~144: acquires connection and commits inline
// read() — line ~175: PreparedStatement executed inline
```

**Contradiction:** The class JavaDoc explicitly claims:
> *"All JDBC calls are wrapped in `Promise.ofBlocking(executor, …)` so the ActiveJ event loop is never blocked."*

This documentation is false. Only `tail()` correctly delegates to the blocking executor via `PollingSubscription.start(dataSource, executor)`.

**Why it matters:** The ActiveJ event loop is single-threaded. Any blocking JDBC call (even a 5 ms query) monopolizes the loop, stalling all other concurrent requests processed by the server. Under production load this causes cascading timeout failures across all endpoints.

**Exception reference:** The `PollingSubscription` inner class contains a comment that explicitly documents the anti-pattern it chose to keep:
> *"Use direct synchronous JDBC — avoids the race condition where `Promise.ofBlocking(...).getResult()` returns null if the task has not yet completed"*

The developer misused `.getResult()` (which the architecture standards explicitly forbid) and then worked around the resulting NPE by removing the async wrapping entirely, making the problem structurally worse.

**Downstream impact:** Affects every caller of this store: `EventBuffer.spillExcess()`, `feature-store-ingest` polling, AEP `EventSourcedEpisodicStore`, gRPC event service.

**Fix:**
```java
@Override
public Promise<Offset> append(TenantContext tenant, EventEntry entry) {
    return Promise.ofBlocking(executor, () -> doAppend(tenant.tenantId(), entry));
}

@Override
public Promise<List<EventEntry>> read(TenantContext tenant, Offset from, int limit) {
    long fromVal = parseLong(from);
    return Promise.ofBlocking(executor, () -> {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(READ_SQL)) {
            ps.setString(1, tenant.tenantId());
            ps.setLong(2, fromVal);
            ps.setInt(3, limit);
            return mapResultSet(ps.executeQuery());
        }
    });
}
// Apply same pattern to all remaining blocking methods.
```

**Tests missing:** No test verifies that `append()` resolves asynchronously (off-loop). `WarmTierEventLogStoreTest` uses `Testcontainers` but does not assert event-loop non-blocking.

---

### FINDING-2 — **CRITICAL** | Same Event Loop Blocking in `KafkaEventLogStore`

**File:** KafkaEventLogStore.java  
**Module:** `data-cloud:platform`

**Issue:** Identical pattern to Finding-1. `append()`, `appendBatch()`, `read()`, `readByTimeRange()`, `readByType()`, `getLatestOffset()`, `getEarliestOffset()` all execute blocking Kafka client operations (`producer.send().get()`, consumer `poll()`, `AdminClient` describe) synchronously, then wrap the result in `Promise.of(...)`.

**Evidence (append path):**
```java
@Override
public Promise<Offset> append(TenantContext tenant, EventEntry entry) {
    try {
        return Promise.of(doAppend(tenant, entry));  // ← producer.send().get() inside
    } catch (Exception e) {
        return Promise.ofException(e);
    }
}
// appendBatch: blocks on producer.commitTransaction() + futures.get()
// getLatestOffset: creates a KafkaConsumer inline, calls endOffsets()
```

The class declares `private final Executor blockingExecutor` but **never uses it** for the SPI methods. Only the `tailExecutor` (used for polling threads) is used correctly.

**Fix:** Wrap all blocking Kafka calls in `Promise.ofBlocking(blockingExecutor, () -> ...)`.

**Tests missing:** `KafkaEventLogStoreTest` uses `@Testcontainers(Kafka)` but does not verify async dispatch.

---

### FINDING-3 — **CRITICAL** | Thread Pool Leak in `FeatureStoreIngestLauncher.reschedule()`

**File:** FeatureStoreIngestLauncher.java

**Issue:** `reschedule()` creates a brand-new `Executors.newSingleThreadExecutor()` for every single delay invocation and never shuts it down:

```java
private void reschedule(Eventloop eventloop, TenantId tenant, long delayMs) {
    if (!running.get()) return;
    if (delayMs > 0) {
        Promise.ofBlocking(
            Executors.newSingleThreadExecutor(),   // ← NEW POOL, NO SHUTDOWN
            () -> { Thread.sleep(delayMs); return null; })
        .whenResult(...);
    } else { ... }
}
```

With `DEFAULT_POLL_DELAY_MS = 1000ms`, 3 tenants, running for 1 hour: **10,800 leaked thread pool objects** and their associated threads that will never be GC'd until the JVM exits.

**Fix:**
```java
private final ScheduledExecutorService scheduler =
    Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "feature-ingest-scheduler");
        t.setDaemon(true);
        return t;
    });

private void reschedule(Eventloop eventloop, TenantId tenant, long delayMs) {
    if (!running.get()) return;
    if (delayMs > 0) {
        scheduler.schedule(
            () -> { if (running.get()) eventloop.execute(() -> pollNext(eventloop, tenant, TenantContext.of(tenant.value()))); },
            delayMs, TimeUnit.MILLISECONDS);
    } else {
        pollNext(eventloop, tenant, TenantContext.of(tenant.value()));
    }
}
// Shutdown scheduler in stop()
```

**Tests missing:** No test exercises the reschedule path with delay.

---

### FINDING-4 — **CRITICAL** | Offset Tracking Bug Produces Wrong Reads in `FeatureStoreIngestLauncher`

**File:** Same as Finding-3, FeatureStoreIngestLauncher.java

**Issue:** After each poll, the next offset is calculated as:
```java
tenantOffsets.put(tenant.value(), currentOffset + entries.size());
```

`EventLogStore` offsets are assigned by PostgreSQL `GENERATED ALWAYS AS IDENTITY`. This column is monotonically increasing but **not necessarily gapless** — gaps occur after rolled-back transactions, replication failover, etc. If the last returned batch had offsets [100, 105, 110] (gaps from aborted inserts), the code stores `currentOffset + 3` (e.g., 103) instead of 111. The next read starts from 103, re-delivering events 105 and 110 or skipping 111 onwards.

**Fix:** Track the actual maximum returned offset:
```java
if (!entries.isEmpty()) {
    // ... process entries ...
    long maxOffset = entries.stream()
        .mapToLong(e -> parseOffset(e.offset()))  // requires offset in EventEntry
        .max().getAsLong();
    tenantOffsets.put(tenant.value(), maxOffset + 1);
}
```

Note: `EventEntry` does not currently expose the assigned offset. The SPI needs to be extended, or `read()` should return `(entry, offset)` pairs.

---

### FINDING-5 — **HIGH** | `PostgresJsonbConnector.query()` Silently Ignores All Filter Criteria

**File:** PostgresJsonbConnector.java

**Issue:** The `query()` method accepts a `QuerySpec` but passes `Collections.emptyMap()` as the filter to `entityRepository.findAll()`:

```java
@Override
public Promise<QueryResult> query(UUID collectionId, String tenantId, QuerySpec spec) {
    String collectionName = collectionId.toString();
    int limit = spec.getLimit();
    int offset = spec.getOffset();
    return entityRepository.findAll(tenantId, collectionName,
        Collections.emptyMap(),   // ← filter ALWAYS empty — QuerySpec predicates ignored
        formatSort(spec), offset, limit)
        .then(entities -> entityRepository.countByFilter(tenantId, collectionName,
            Collections.emptyMap())   // ← same
            .map(totalCount -> new QueryResult(entities, totalCount, limit, offset, duration)));
}
```

**Effect:** Every query returns ALL entities in the collection up to `limit`, regardless of any WHERE conditions in the query spec. This is a functional correctness bug and a data exposure risk — callers believe they're receiving filtered results.

**Similarly**: `scan()` accepts a `filterExpression` parameter and never applies it.

**Fix:** Translate `QuerySpec.getFilters()` / `spec.getFilter()` to the map expected by `EntityRepository.findAll()`, or use `entityRepository.findByQuery(tenantId, collectionName, spec)` if that overload exists.

**Tests missing:** `PostgresJsonbConnectorTest` does not test that filter predicates actually reduce the result set.

---

### FINDING-6 — **HIGH** | `PostgresJsonbConnector.read()` Uses UUID String as Collection Name

**File:** Same as Finding-5

**Issue:**
```java
@Override
public Promise<Optional<Entity>> read(UUID collectionId, String tenantId, UUID entityId) {
    String collectionName = collectionId.toString();  // ← UUID string e.g. "3f4a6b2e-..."
    return entityRepository.findById(tenantId, collectionName, entityId);
}
```

Collection names in the `entities` table are human-readable strings (e.g., `"products"`). A UUID string will never match any real collection name. Every `read()` call through this connector returns `Optional.empty()` unconditionally — a silent data availability failure, not an error.

Same bug affects `delete()`, `query()`, `scan()`, `count()`, and `bulkCreate()`.

**Fix:** The connector must be constructed with a `collectionName: String` or accept collection name rather than UUID, or the `StorageConnector` interface must be reconsidered. The `create()` method is the only one that correctly uses `entity.getCollectionName()`.

---

### FINDING-7 — **HIGH** | `FeatureStoreIngestLauncher.main()` Production Mode Is Broken

**File:** FeatureStoreIngestLauncher.java

**Issue:** When `FEATURE_INGEST_MODE=postgres`, `main()` unconditionally throws `IllegalStateException`:
```java
if ("postgres".equalsIgnoreCase(mode)) {
    throw new IllegalStateException(
        "FEATURE_INGEST_MODE=postgres requires a PostgreSQL EventLogStore implementation. "
        + "Set FEATURE_INGEST_MODE=inmemory for local development.");
}
```

There is no production wiring. Any operator who sets `FEATURE_INGEST_MODE=postgres` to connect to the actual warm-tier store gets an instant crash with a confusing error. Kubernetes pods will restart loop.

**Fix:** Wire `WarmTierEventLogStore` with a `DataSource` when `FEATURE_INGEST_MODE=postgres`:
```java
if ("postgres".equalsIgnoreCase(mode)) {
    HikariConfig cfg = new HikariConfig();
    cfg.setJdbcUrl(require(ENV_DB_URL));
    // ...
    eventLogStore = new WarmTierEventLogStore(new HikariDataSource(cfg));
}
```

---

### FINDING-8 — **HIGH** | `AnalyticsQueryEngine` Uses Unbounded `CachedThreadPool`

**File:** AnalyticsQueryEngine.java

**Issue:**
```java
this.blockingExecutor = Executors.newCachedThreadPool(r -> {
    Thread t = new Thread(r, "analytics-query-worker");
    t.setDaemon(true);
    return t;
});
```

`newCachedThreadPool` creates unlimited threads under concurrent load. A sudden burst of 500 concurrent analytics queries produces 500 threads — JVM heap and stack exhaustion.

**Additionally:** `resultCache` is a `LinkedHashMap` locked with `Collections.synchronizedMap` — high contention under concurrent access, not a thread-safe LRU. The LRU eviction (`removeEldestEntry`) is not safe here because it executes inside `put()` while holding the map monitor, risking deadlock in multi-threaded access.

**Fix:** Use `Executors.newFixedThreadPool(n)` with a bounded queue, or a virtual-thread executor with a semaphore. For the cache, use Caffeine.

---

### FINDING-9 — **HIGH** | Missing GIN Index on `entities.data` JSONB Column

**File:** V002__create_entities_table.sql

**Issue:** The `entities` table stores all entity payloads in a `data JSONB` column. No GIN index is created on it. Any query that filters or searches within JSONB attributes (e.g., `data @> '{"status":"active"}'`) will perform a sequential scan on the full table, costing O(N) per tenant per query.

All analytics, search, and filtering operations pass through this column.

**Fix:** Add a new migration:
```sql
-- V008__add_entities_data_gin_index.sql
CREATE INDEX CONCURRENTLY idx_entities_data_gin
    ON entities USING GIN (data jsonb_path_ops);
```

Use `CONCURRENTLY` to avoid locking the table in production.

---

### FINDING-10 — **HIGH** | `AnalyticsQueryEngine.extractDataSources()` Uppercases Query Before Collection Name Extraction

**File:** AnalyticsQueryEngine.java

**Issue:**
```java
private QueryPlan generateQueryPlan(AnalyticsQuery query) {
    String queryText = query.getQueryText().toUpperCase();
    // ...
    List<String> dataSources = extractDataSources(queryText);  // extracts FROM clause names
```

The collection name in `FROM products` becomes PRODUCTS after `.toUpperCase()`, which won't match any stored collection. All query plans have wrong data source names. Since this feeds into routing decisions, every SELECT/AGGREGATE query is mis-routed.

**Fix:** Parse query text case-preservingly using the already-present JSqlParser dependency:
```java
private List<String> extractDataSources(String queryText) {
    PlainSelect select = parsePlainSelect(queryText);  // uses CCJSqlParserUtil correctly
    // ... extract Table names from FromItem/Joins without case mutation
}
```

---

## Part 3: Data Integrity Risks

| # | Risk | Severity | File |
|---|------|----------|------|
| 1 | Offset gaps in `event_log` cause `FeatureStoreIngestLauncher` to re-deliver or skip events | Critical | feature-store-ingest |
| 2 | `PostgresJsonbConnector.query()` ignores filters — all queries return full collection data | Critical | platform/infrastructure/storage |
| 3 | `Entity.equals()` uses only `id`, not `(tenantId, id)` — cross-tenant entity collision in Java collections | High | platform/entity/Entity.java |
| 4 | `event_log.created_at` is server-side only (`DEFAULT NOW()`) — event ordering reflects ingestion time, not source occurrence time; replay after delay produces incorrect ordering | Medium | V005 migration |
| 5 | `entities` table has no generated checksum or hash on `data` — no integrity verification after write | Medium | V002 migration |
| 6 | `WarmTierEventLogStore.appendBatch()` rolls back only `conn.rollback()` — if the connection was already in a failed state before the batch, the rollback may itself throw, leaving partial state | Medium | WarmTierEventLogStore |
| 7 | `NLQService.parseFilters()` silently drops unrecognized predicates — a malformed NLQ returns fewer filters than the user intended, producing unexpectedly large result sets | Medium | NLQService |

---

## Part 4: Uncovered Edge Cases

1. **`WarmTierEventLogStore.appendBatch()`**: What happens when the batch is empty? `appendBatch(tenant, List.of())` acquires a connection, sets `autoCommit=false`, commits with an empty loop, then restores `autoCommit`. Correct but wasteful — no fast-path guard.

2. **`EventBuffer.spillExcess()`** race: `size.get()` is read, then `drain(spillCount)` is called. Between those two calls, other threads may drain the buffer, making `drain()` return fewer than `spillCount` events. The spill count in the returned Promise will be smaller than expected — not a bug but callers are not informed.

3. **`EventBuffer` capacity enforcement**: `offer()` checks `size.get() >= capacity` but `size` is decremented in `drain()` on a different thread. Under concurrent `offer()/drain()` pairs, atomicity is maintained by `AtomicInteger`, but the check-then-act between `size.get()` and `buffer.add()` is not atomic — two threads can both see `size < capacity` and both add, temporarily exceeding capacity by the number of concurrent producers.

4. **`DataCloudSecurityFilter` CRITICAL route policy check** falls back to `"default"` tenant when `TenantContext.getCurrentTenantId()` returns null:
   ```java
   return (tenantId != null) ? tenantId : "default";
   ```
   If the tenant context was not established (auth failure handled before the context was set), the policy evaluation runs with `tenantId = "default"`, potentially matching a real tenant's exclusion list.

5. **`KafkaEventLogStore.getLatestOffset()`** creates a new `KafkaConsumer` inline and calls `endOffsets()` which blocks until metadata is fetched. If the Kafka broker is down, this blocks indefinitely with no timeout configured on the consumer.

6. **`FeatureStoreIngestLauncher.processEntry()`**: `entry.payload().duplicate()` is called to read the bytes. If `payload` is a read-only or direct `ByteBuffer` with zero remaining bytes (valid for empty payloads), the `MAPPER.readValue(raw, Map.class)` will fail with `MismatchedInputException`. Empty events are not handled.

7. **`NLQService.parseFilters()`**: The `CONTAINS_PATTERN` regex matches `(\w+) contains (\w+)` — value is `\w+` (word chars only). A search query like `name contains "John Doe"` (with space) will not match and be silently dropped.

---

## Part 5: Missing Test Cases

| # | Test Gap | Severity | Where Needed |
|---|----------|----------|-------------|
| 1 | `WarmTierEventLogStore` — verify `append()` does NOT block the event loop thread | Critical | `WarmTierEventLogStoreTest` |
| 2 | `WarmTierEventLogStore` — idempotency key deduplication (second append with same key returns error, not silent drop) | High | `WarmTierEventLogStoreTest` |
| 3 | `PostgresJsonbConnector.query()` — assert returned entities match filter criteria, not full collection | Critical | `PostgresJsonbConnectorTest` |
| 4 | `PostgresJsonbConnector.read()` — assert lookup with actual collection name string vs UUID string | High | `PostgresJsonbConnectorTest` |
| 5 | `FeatureStoreIngestLauncher` — offset advancement with IDENTITY gaps (offsets 1,3,5 should advance to 6, not 4) | Critical | new test class |
| 6 | `FeatureStoreIngestLauncher` — `reschedule()` does not leak thread pools | High | new test class |
| 7 | `FeatureStoreIngestLauncher` — empty payload handling in `processEntry()` | Medium | new test class |
| 8 | `AnalyticsQueryEngine` — `submitQuery()` result does not reference other tenants' data in shared cache | High | `AnalyticsQueryEngineTest` |
| 9 | `KafkaEventLogStore` — event loop non-blocking assertion | Critical | `KafkaEventLogStoreTest` |
| 10 | `DataCloudSecurityFilter` — CRITICAL route policy check with null tenant context falls back correctly | Medium | `DataCloudSecurityFilterTest` |
| 11 | `EventBuffer` — concurrent `offer()/drain()` does not exceed capacity | Medium | `EventBufferTest` |
| 12 | `NLQService` — multi-word `contains` value is rejected or handled gracefully | Medium | `NLQServiceTest` |
| 13 | Schema migration — `event_log` idempotency constraint prevents duplicate insert for same tenant+key | High | `WarmTierEventLogStoreTest` integration |
| 14 | `CachingEntityRepository` — verify invalidation on `save()` and `delete()` | Medium | `CachingEntityRepositoryTest` |

---

## Part 6: Integration and Dependency Risks

1. **`feature-store-ingest` has no production EventLogStore wiring** (Finding-7). Module is production-dead for the postgres mode.

2. **`WarmTierEventLogStore` executor is created internally** (`Executors.newVirtualThreadPerTaskExecutor()`) in the default constructor. There is no lifecycle management — no `shutdown()` method. Virtual thread executors are JVM-managed and do not need explicit shutdown, but if this store is wrapped in a Service that IS shut down, the orphaned tasks will continue running until the JVM exits.

3. **`AnalyticsQueryEngine.blockingExecutor`** has no `close()` wiring. `AnalyticsQueryEngine implements AutoCloseable`, and `close()` exists, but whether it shuts down `blockingExecutor` needs verification.

4. **SDK contract drift**: The generated SDK (`sdk/build/generated/`) references model types `ApiV1EntitiesCollectionIdDelete200Response` and `ApiV1EventsPost200Response`. These are generated from OpenAPI, but the `OpenAPI spec source` is not visible in source control (only the generated output is). If the spec and implementation diverge, the SDK becomes incorrect.

5. **`DataCloudClient` interface (SPI)** declares `entityStore()` and implicitly expects `EventLogStore` access, but `TailRequest` and `EventQuery` nested types are referenced without being imported in the SPI interface file (relies on platform types being present at the call site — fragile).

---

## Part 7: Schema and Contract Risks

1. **`event_log.payload` is `BYTEA`**: No schema version enforcement at the DB layer. Payloads can contain schema V1 and V2 bytes in the same table without any discriminator beyond `event_version`. Consumers must handle both, but no migration playbook exists for payload format evolution.

2. **`entities.data JSONB`**: Fields are completely dynamic — no schema enforcement at storage layer. A malformed or unexpected field silently persists. `EntitySchemaValidator` exists but is optional (nullable field in `DataCloudHttpServer`).

3. **`uk_event_log_idem UNIQUE (tenant_id, idempotency_key)`**: PostgreSQL allows multiple NULLs in a unique index (NULLs are not equal). This is correct behaviour, but it means idempotency is only enforced when `idempotency_key IS NOT NULL`. Callers who rely on idempotency protection must always supply a key.

4. **`CollectionStorageProfile` is not versioned**: The cache TTL for routing decisions is 5 minutes (`StorageRouterService`). A profile change (e.g., migrating a collection to a different backend) will silently serve stale routing for up to 5 minutes after the change.

5. **No `IF NOT EXISTS` guards on indexes in V002**: `CREATE INDEX idx_entities_tenant` will fail on second run on a database that already has the index (e.g., re-running migration on a partially migrated schema in a multi-instance deployment). V005 uses `CREATE TABLE IF NOT EXISTS` (good) but lacks `IF NOT EXISTS` on index creation.

---

## Part 8: Performance and Scalability Concerns

1. **Event loop blocking** (Findings 1 & 2) — affects entire throughput of the server. Under load, a single 50ms JDBC call blocks all concurrent ActiveJ requests.

2. **No GIN index on `entities.data`** (Finding-9) — all JSONB attribute queries scan the full table per tenant.

3. **`StorageRouterService` routing cache is a `ConcurrentHashMap` with manual TTL** — expiry is only enforced lazily on next access. Stale entries accumulate unbounded until a tenant issues a new request.

4. **`CachingEntityRepository` L1 cache** is keyed on `(tenantId, collectionName, entityId)`. With many short-lived entities (e.g., events surfaced as entities), the 10,000-entry limit provides poor hit rates for multi-tenant workloads. No per-tenant size cap.

5. **`AnalyticsQueryEngine.executeAggregate()`** fetches up to `MAX_QUERY_LIMIT = 10,000` entities into memory for in-memory group-by. For large collections this is a memory and latency concern. Native DB aggregation should be used.

6. **`AnalyticsQueryEngine.executeJoin()`** loads entire left and right collections into memory (up to 10K each) for in-memory hash join. 200MB+ heap usage for large joins.

7. **`WarmTierEventLogStore.appendBatch()`** uses a single connection for the entire batch (`setAutoCommit(false)` / manual commit). Long batches hold the connection for the full duration, reducing pool availability for concurrent requests.

---

## Part 9: Resilience and Operational Concerns

1. **No circuit breaker** on any storage connector. A flaky PostgreSQL or OpenSearch connection will cause all requests to fail immediately rather than opening a circuit and serving from cache or returning graceful degradation.

2. **`FeatureStoreIngestLauncher`** retry on error (`reschedule(eventloop, tenant, retryDelayMs)`) has a fixed delay with no exponential back-off or jitter. Under sustained outage, all tenant pollers hammer the store simultaneously at exactly the same interval.

3. **`WarmTierEventLogStore.tail()` polling subscription**: On error, the subscription logs a warning and sleeps for `POLL_DELAY_MS * 4 = 1000ms` before retrying. There is no maximum retry count or dead-letter mechanism. A tenant-specific error (e.g., malformed row) will cause infinite retry loop.

4. **`DataCloudSecurityFilter`** audit emission is fire-and-forget (`whenException` only logs). Audit failures are invisible to operators unless they actively monitor logs. No metric is emitted for audit write failures.

5. **No backpressure between HTTP layer and storage layer**: `DataCloudHttpServer` rate-limits per IP (200 req/60s) but there is no queue depth or connection pool saturation signal to shed load gracefully before the event loop blocks.

---

## Part 10: Security and Access-Control Concerns

1. **`DataCloudSecurityFilter.resolveTenantFromContext()` default fallback**:
   ```java
   return (tenantId != null) ? tenantId : "default";
   ```
   If tenant context is missing (misconfigured upstream middleware), policy evaluation runs as `tenantId = "default"`. If `"default"` is in `policyExcludedTenants`, policy is skipped entirely for unauthenticated requests.

2. **`SimpleEncryptionService.main()`** is a runnable key-generation utility embedded in production code. It prints the generated key to `stdout`. If this is accidentally invoked in CI or prod environments, the key appears in logs. Move to a separate CLI tool or test utility.

3. **`DataCloudEnvConfig.authToken()`** returns `""` (empty string) silently when not configured. The HTTP client (`HttpDataCloudClient`) may send an `Authorization: Bearer ` header with an empty token to upstream services. Some services accept empty bearer tokens, effectively skipping auth. Add a startup assertion.

4. **`ApiInputValidator`** correctly validates identifiers against `SAFE_IDENTIFIER = [a-zA-Z0-9._-:]`. However, the validator is not consistently applied in all handlers — verify that `EntityCrudHandler`, `EventHandler`, and `AgentRegistryHandler` all call `ApiInputValidator.validateTenantId()` and `validateCollection()` before delegating to service layer.

5. **Tenant context in `DataCloudSecurityFilter.handlePostAuth()`**: Policy evaluation is invoked via `authedServlet.serve(request)` followed by `.then(handlePostAuth)`. The policy check in `handlePostAuth` calls `resolveTenantFromContext()` to get the tenant ID. However, this read from thread-local context happens inside an ActiveJ `.then()` callback which may execute on a different virtual thread. Thread-local propagation with `TenantIsolationHttpFilter` must be verified for ActiveJ's async execution model.

---

## Part 11: Naming and Documentation Issues

1. **`WarmTierEventLogStore` JavaDoc**: Claims "All JDBC calls are wrapped in `Promise.ofBlocking`" — this is factually incorrect and actively misleading. Must be corrected alongside the fix.

2. **`PollingSubscription.poll()` comment** documents a workaround for misused `.getResult()` as a permanent decision. This comment must be removed once `Promise.ofBlocking` is used correctly.

3. **`AnalyticsQueryEngine` `@doc.layer` tag is `core`** — this is a product-layer class, not a core platform class. Should be `product`.

4. **`entity.governance.port.PermissionService`** and **`entity.governance.service.PermissionService`** — two classes with the same name in adjacent packages. Only one should exist; the port should be an interface; the service should be the implementation.

5. **`DataCloudClient` interface** JavaDoc says "11 methods" but only 8 are enumerated in the comment. The count is wrong and the list is incomplete.

6. **`NLQService` `entityRepository` parameter is documented**: "can be null for parse-only usage" — nullable constructor argument that silently enables parse-only mode without a factory method or builder pattern. Makes the null check on `executeQuery()` invisible to callers.

---

## Part 12: Dead Code, Stale Configs, and Unnecessary Abstractions

1. **`FeatureStoreIngestLauncher` postgres mode block** — the `throw new IllegalStateException(...)` branch under `FEATURE_INGEST_MODE=postgres` prevents any code after it from executing. This is effectively dead code-by-design, but misleads operators into believing the mode exists.

2. **`AnalyticsQueryEngine` no-arg constructor** (`AnalyticsQueryEngine()`) calls `this(null)`. A null `storageConnector` yields silent empty results for every query. This "legacy/testing mode" is not documented in the constructor JavaDoc and has no test that explicitly tests this mode. It should be removed or replaced with `AnalyticsQueryEngine.noOp()` factory.

3. **`entities` table `record_type` CHECK constraint**: `CHECK (record_type IN ('ENTITY', 'EVENT', 'TIMESERIES', 'DOCUMENT', 'GRAPH'))`. However, the `RecordType` Java enum also has `GRAPH`. If new record types are added to the enum, the DB constraint must be updated manually — fragile coupling.

4. **`V007__entities_display_name_index_README.sql`**: A migration file named `README.sql` suggests it is documentation embedded in a migration — migration files should not serve as README proxies.

---

## Part 13: Quick Wins

| | Fix | Effort |
|---|-----|--------|
| 1 | Wrap `WarmTierEventLogStore` `append()`/`read()` in `Promise.ofBlocking` | 30 min |
| 2 | Fix `FeatureStoreIngestLauncher.reschedule()` thread pool leak | 15 min |
| 3 | Add GIN index migration `V008__add_entities_data_gin_index.sql` | 10 min |
| 4 | Fix `AnalyticsQueryEngine` uppercase query bug in `generateQueryPlan()` | 5 min |
| 5 | Fix `WarmTierEventLogStore` JavaDoc to match implementation | 5 min |
| 6 | Remove `SimpleEncryptionService.main()` from production code | 5 min |
| 7 | Add startup assertion in `DataCloudConfigValidator` for non-empty `authToken` in non-dev environments | 10 min |
| 8 | Fix `V002` migration index statements to use `CREATE INDEX IF NOT EXISTS` | 5 min |
| 9 | Fix `KafkaEventLogStore` to use `blockingExecutor` it already declares | 30 min |
| 10 | Fix `FeatureStoreIngestLauncher` postgres mode to wire `WarmTierEventLogStore` | 30 min |

---

## Part 14: Larger Refactor Opportunities

1. **EventLogStore SPI needs to include offset in `EventEntry`**: The current `EventEntry` record does not contain the assigned offset (storage-assigned value). Consumers like `FeatureStoreIngestLauncher` have no way to reliably track progress without it. Add `Optional<Offset> assignedOffset()` to `EventEntry`.

2. **`StorageConnector` interface API inconsistency**: `create(Entity)` uses `Entity.getCollectionName()`, but `read(UUID collectionId, String tenantId, UUID entityId)` uses `UUID collectionId`. This mixed API is the root cause of Finding-6. Standardize to either UUID or String collection identifiers throughout.

3. **`AnalyticsQueryEngine` in-memory JOIN/AGGREGATE**: Replace with pushdown to PostgreSQL for the `PostgresJsonbConnector` path using SQL aggregation. The in-memory approach with 10K row limits is not suitable for production analytics workloads.

4. **`NLQService` SQL generation**: The current approach builds SQL strings with `StringBuilder` and named parameters. This bypasses ORM-level protection. Migrate to JPA `CriteriaBuilder` or QuerySpec predicate objects to make the intent type-safe and remove all string-concatenation from query generation.

5. **Schema evolution strategy**: The `event_log.payload` column is `BYTEA` with `event_version VARCHAR(64)`. There is no schema registry, no Avro/Protobuf format, and no backward/forward compatibility guarantee. Introduce a schema registry (the existing `EventSchemaRegistry` class in platform) as a mandatory validation step on ingest.

---

## Part 15: Final Summary

### Top 10 Most Important Fixes

| Priority | Fix |
|---|---|
| **1** | Wrap all blocking methods in `WarmTierEventLogStore` with `Promise.ofBlocking` |
| **2** | Wrap all blocking methods in `KafkaEventLogStore` with `Promise.ofBlocking` |
| **3** | Fix `FeatureStoreIngestLauncher.reschedule()` thread pool leak |
| **4** | Fix `FeatureStoreIngestLauncher` postgres production mode (wire `WarmTierEventLogStore`) |
| **5** | Fix `PostgresJsonbConnector.query()` to apply filter predicates from `QuerySpec` |
| **6** | Fix `PostgresJsonbConnector.read/delete/scan/count` to use actual collection name, not UUID string |
| **7** | Fix `FeatureStoreIngestLauncher` offset tracking to use `max(returned_offset)` not `currentOffset + size` |
| **8** | Fix `AnalyticsQueryEngine.generateQueryPlan()` — remove `.toUpperCase()` before collection name extraction |
| **9** | Add GIN index on `entities.data JSONB` in V008 migration |
| **10** | Replace `AnalyticsQueryEngine.blockingExecutor = newCachedThreadPool` with bounded executor |

### Top 10 Missing Tests

1. `WarmTierEventLogStore` — event loop non-blocking verification  
2. `KafkaEventLogStore` — event loop non-blocking verification  
3. `PostgresJsonbConnector.query()` — filter predicate actually filters  
4. `FeatureStoreIngestLauncher` — offset gap handling  
5. `FeatureStoreIngestLauncher` — no thread pool leak in reschedule  
6. `AnalyticsQueryEngine` — query result not accessible cross-tenant  
7. `PostgresJsonbConnector.read()` — collection name string vs UUID  
8. `DataCloudSecurityFilter` — null tenant fallback behavior  
9. `EventBuffer` — concurrent offer does not exceed capacity  
10. Schema — `event_log` idempotency key deduplication enforced  

### Top 10 Data Integrity Risks

1. Incorrect offset tracking causes re-delivery/skip in feature ingestion  
2. `query()` filter bypass returns entire collection to all callers  
3. Blocking event loop causes JDBC timeout under load → partial writes  
4. No GIN index → slow queries lead to query timeouts → inconsistent paging results  
5. `Entity.equals()` cross-tenant collision in Java collections  
6. No payload schema registry → `event_log` accumulates incompatible schema versions  
7. `created_at` server-side only → events from delayed sources are missequenced  
8. `CollectionStorageProfile` routing cache stale for up to 5 minutes after migration  
9. `appendBatch()` long-held connection reduces pool availability, causing write failures under concurrency  
10. Missing `IF NOT EXISTS` on V002 indexes — migration failure on re-run leaves indexes absent without error  

### Top Documentation/Comment Improvements

1. **`WarmTierEventLogStore`** — correct the JavaDoc claim about `Promise.ofBlocking` wrapping  
2. **`PollingSubscription.poll()`** — remove comment that documents wrong pattern as intentional design  
3. **`DataCloudClient` interface** — fix method count claim (says 11, lists 8) and add full method list  
4. **`NLQService`** — document that `entityRepository = null` disables execution, and why  
5. **`AnalyticsQueryEngine` `@doc.layer`** — fix from `core` to `product`  
6. **`PostgresJsonbConnector.read()`** — document the collection-name-from-UUID issue until it is fixed  
7. **`FeatureStoreIngestLauncher`** — document that `FEATURE_INGEST_MODE=postgres` is not yet wired  

### Overall Health Assessment by Module

| Module | Health | Key Issues |
|---|---|---|
| `spi` | ✅ Good | Well-designed, immutable records, clear contracts |
| `platform/entity` | ✅ Good | Clean domain model, proper JPA annotations |
| `platform/storage` — `WarmTierEventLogStore` | 🔴 Critical | Event loop blocking throughout |
| `platform/plugins/kafka` — `KafkaEventLogStore` | 🔴 Critical | Event loop blocking throughout |
| `platform/infrastructure` — `PostgresJsonbConnector` | 🔴 Critical | Filter/collection-name bugs make most operations no-ops |
| `platform/analytics` — `AnalyticsQueryEngine` | 🟡 Medium | Thread pool, cache, and collection-name bugs |
| `platform/application/nlq` — `NLQService` | 🟡 Medium | Functional but incomplete pattern support |
| `platform/infrastructure/cache` — `CachingEntityRepository` | ✅ Good | Correct Caffeine usage, good observability |
| `launcher/security` — `DataCloudSecurityFilter` | ✅ Good | Well-structured, fail-closed |
| `launcher/validation` — `ApiInputValidator` | ✅ Good | Thorough, stateless, correctly OWASP-aware |
| `feature-store-ingest` | 🔴 Critical | Thread leak, offset bug, mode broken |
| `platform/config` — `DataCloudEnvConfig` | ✅ Good | Centralized, testable |
| `platform/di` | ✅ Good | Clean DI modules |
| Schema migrations (V001–V007) | 🟡 Medium | Missing GIN index, missing `IF NOT EXISTS` on indexes |

### Recommended Execution Plan

**Phase 1 — Stop the bleeding (1–2 days):**
- Fix `WarmTierEventLogStore` and `KafkaEventLogStore` event loop blocking (Findings 1 & 2)  
- Fix `FeatureStoreIngestLauncher` thread pool leak (Finding 3)  
- All three are independent and safe to ship in a single PR  

**Phase 2 — Correctness (2–3 days):**
- Fix `PostgresJsonbConnector` filter bypass and collection-name bugs (Findings 5 & 6)  
- Fix `FeatureStoreIngestLauncher` offset tracking (Finding 4)  
- Fix `AnalyticsQueryEngine` uppercase bug (Finding 10)  
- Add `V008__add_entities_data_gin_index.sql`  

**Phase 3 — Production readiness (1 week):**
- Wire `FEATURE_INGEST_MODE=postgres` properly (Finding 7)  
- Replace `AnalyticsQueryEngine` unbounded thread pool (Finding 8)  
- Add all missing tests from Part 5  
- Add `Promise.ofBlocking` compliance test to `EventloopTestBase`-based tests  

**Phase 4 — Architecture improvements (ongoing):**
- Add `assignedOffset` to `EventEntry`  
- Standardize `StorageConnector` API to use consistent collection identifier type  
- Migrate `NLQService` SQL generation to `CriteriaBuilder`  
- Introduce schema registry enforcement on event ingest  
- Replace `AnalyticsQueryEngine` in-memory aggregation with storage-pushdown   

Completed: *Compile and prioritize findings* (8/8)

The audit is complete. Here is a summary of the most actionable findings:

**4 Critical issues requiring immediate action:**

1. **`WarmTierEventLogStore`** — every SPI method (`append`, `read`, `getLatestOffset`, etc.) blocks the ActiveJ event loop via direct JDBC, despite the JavaDoc claiming otherwise. Wrap all in `Promise.ofBlocking(executor, …)`.

2. **`KafkaEventLogStore`** — identical pattern: all methods block the event loop. The class declares a `blockingExecutor` field but never uses it for these methods.

3. **`FeatureStoreIngestLauncher.reschedule()`** — creates a new, never-shutdown `Executors.newSingleThreadExecutor()` on every poll delay, leaking thousands of thread pools over hours of operation.

4. **`PostgresJsonbConnector.query()/scan()/read()`** — passes `Collections.emptyMap()` as the filter to the repository (bypassing all query predicates), and uses `UUID.toString()` as the collection name for read/delete/count operations — meaning those methods always return empty results silently.

The platform/storage/WarmTierEventLogStore.java, platform/plugins/kafka/KafkaEventLogStore.java, feature-store-ingest launcher, and platform/infrastructure/storage/PostgresJsonbConnector.java are the four files requiring the most urgent fixes.
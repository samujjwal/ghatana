# Data-Cloud Platform – Comprehensive Audit Report
**Audit ID:** data-cloud-2  
**Date:** 2026-03-25  
**Platform:** `products/data-cloud`  
**Auditor:** GitHub Copilot / Ghatana AI Agent  
**Prior Audit:** `docs/audits/data-cloud-1.md` (14 findings, all remediated)

---

## Executive Summary

This audit covers every module, service, schema, pipeline, and integration point of
`products/data-cloud` following the full remediation of the first audit cycle. The
review was conducted file-by-file across the `platform`, `spi`, `sdk`,
`feature-store-ingest`, `analytics`, `application`, `infrastructure`, `entity/storage`,
and migration directories.

**22 new findings** were identified:

| Severity | Count |
|:---------|------:|
| 🔴 CRITICAL | 4 |
| 🟠 HIGH | 6 |
| 🟡 MEDIUM | 7 |
| 🔵 LOW | 5 |
| **Total** | **22** |

The most urgent issues are an **SQL injection vector** in a `StorageConnector` default
method that is reachable from any storage backend via the `Map<String,Object>` filter
overload, a **silent no-op** in the default `deleteByQuery()` implementation, **all JPA
repository calls blocking the event-loop thread** despite a dedicated executor being
declared, and a **type-confusion boundary** that silently returns empty query results
when `entity.storage.QuerySpec` is passed to a method that expects
`application.QuerySpec`.

---

## Scope Reviewed

### Java Modules (file-by-file)

| Module Path | Files Reviewed |
|:-----------|:--------------|
| `products/data-cloud/spi` | `EventLogStore.java`, `EntityStore.java`, `TenantContext.java`, `StorageTier.java`, `InMemoryEventLogStoreProvider.java` |
| `products/data-cloud/platform` – entity | `Entity.java`, `EntityRepository.java`, `EntityService.java`, `QuerySpec.java` (entity.storage) |
| `products/data-cloud/platform` – application | `QuerySpec.java` (application), `DynamicQueryBuilder.java`, `NLQService.java` |
| `products/data-cloud/platform` – infrastructure | `JpaEntityRepositoryImpl.java`, `PostgresJsonbConnector.java`, `OpenSearchConnector.java`, `SimpleEncryptionService.java`, `DataCloudEnvConfig.java` |
| `products/data-cloud/platform` – storage | `StorageConnector.java`, `WarmTierEventLogStore.java` |
| `products/data-cloud/platform` – analytics | `AnalyticsQueryEngine.java` |
| `products/data-cloud/feature-store-ingest` | `FeatureStoreIngestLauncher.java` |

### SQL Migrations (all 8)

`V001` through `V008` – all files reviewed.

### Test Files (6)

`TenantIsolationConnectorTest`, `StorageTierTest`, `ConcurrentTenantLoadTest`,
`MultiTenancyIsolationTest`, `AnalyticsQueryEngineTest`, `NLQServiceTest`,
`WarmTierEventLogStoreTest` (structure only – requires Docker), `PostgresJsonbConnectorTest` (disabled).

### Build

`platform/build.gradle.kts`, `spi/build.gradle.kts`, `feature-store-ingest/build.gradle.kts`.

---

## System Architecture Overview

```
┌──────────────────────────────────────────────────────────┐
│  products/data-cloud                                     │
│                                                          │
│  spi/              ← EventLogStore, EntityStore SPI      │
│    InMemoryEventLogStoreProvider  (testing default)      │
│                                                          │
│  platform/         ← Core implementation                 │
│    application/    NLQService, DynamicQueryBuilder,      │
│                    application.QuerySpec                 │
│    analytics/      AnalyticsQueryEngine                  │
│    entity/         Entity (JPA), entity.storage.QuerySpec│
│    infrastructure/ JpaEntityRepositoryImpl               │
│                    PostgresJsonbConnector                 │
│                    OpenSearchConnector                    │
│                    WarmTierEventLogStore                  │
│                    SimpleEncryptionService                │
│    config/         DataCloudEnvConfig                    │
│    embedded/       SQLiteStore, RocksDBStore, H2Store    │
│    client/         DataCloudClient hierarchy             │
│    reflex/         ReflexEngine (rules / alerts)         │
│                                                          │
│  feature-store-ingest/  ← Scheduled ingest pipeline     │
│                                                          │
│  sdk/              ← Language SDKs / thin clients        │
└──────────────────────────────────────────────────────────┘

Event flow:
  [Producer] → KafkaEventLogStore / WarmTierEventLogStore (SPI)
           → FeatureStoreIngestLauncher (polls, ingests)
           → PostgresJsonbConnector (writes Entity)
           → JpaEntityRepositoryImpl (persists)
           → PostgreSQL JSONB entities table

Query flow:
  [Consumer] → NLQService / AnalyticsQueryEngine
            → DynamicQueryBuilder → application.QuerySpec
            → JpaEntityRepositoryImpl.findByQuery(...)
            → PostgreSQL
```

---

## Audit Method

1. **Static code review** – full source read for every listed file.  
2. **Boundary tracing** – each method signature traced upstream to callers and
   downstream to implementations.  
3. **SQL analysis** – every JPQL / native SQL string inspected for injection, NULL
   handling, missing pagination guards.  
4. **Data-flow analysis** – event offsets, QuerySpec types, executor references
   followed through call chains.  
5. **Schema analysis** – all Flyway migrations reviewed for correctness, idempotency,
   zero-downtime safety.  
6. **ActiveJ compliance** – every blocking operation checked against
   `Promise.ofBlocking` requirement.  
7. **Test coverage gap analysis** – source files cross-referenced with matching test
   classes.

---

## Findings

### 🔴 CRITICAL

---

#### FINDING-C1 · SQL Injection in `StorageConnector.query(String, String, Map)` Default

**File:** `platform/src/main/java/com/ghatana/datacloud/entity/storage/StorageConnector.java`  
**Method:** `default Promise<List<Entity>> query(String tenantId, String collectionName, Map<String, Object> filters)`

**Description:**  
The default interface method builds a filter SQL expression by direct string concatenation
of map keys and values without any parameterization:

```java
filterExpr.append(entry.getKey())
          .append("=")
          .append("'").append(entry.getValue()).append("'");
```

This expression is then passed directly to `scan()`. Any connector that inherits this
default (i.e., does **not** override this overload) will accept arbitrary attacker-controlled
SQL fragments through map values. Value types are not validated; `entry.getValue()` is
coerced via `toString()`. Key names are also directly concatenated without whitelist
validation, permitting column-name injection.

**Attack surface:** Any caller that passes externally-sourced data through the
`Map<String,Object>` overload — e.g., semantic search internal nodes, reflex rule
evaluation, autonomy log queries.

**Severity:** CRITICAL (OWASP A03 – Injection)

**Recommendation:**

Option A – Remove the default body and make the `Map` overload abstract (force
implementors to handle it safely):

```java
Promise<List<Entity>> query(String tenantId, String collectionName, Map<String, Object> filters);
```

Option B – Keep default but convert to a parameterized `QuerySpec` before delegating:

```java
default Promise<List<Entity>> query(String tenantId, String collectionName, Map<String, Object> filters) {
    // Build QuerySpec with proper parameterization — no string concat
    com.ghatana.datacloud.entity.storage.QuerySpec spec =
        new com.ghatana.datacloud.entity.storage.QuerySpec(
            null, // filter built by backend
            List.of(),
            100,
            0,
            List.of(),
            null, null,
            Map.of("rawFilters", filters)  // let connector convert safely
        );
    UUID syntheticId = UUID.nameUUIDFromBytes(collectionName.getBytes(StandardCharsets.UTF_8));
    return query(syntheticId, tenantId, spec).thenApply(QueryResult::entities);
}
```

Either way, **the concatenation must be removed immediately**.

---

#### FINDING-C2 · `StorageConnector.deleteByQuery()` Default is a Silent No-Op

**File:** `platform/src/main/java/com/ghatana/datacloud/entity/storage/StorageConnector.java`  
**Method:** `default Promise<Long> deleteByQuery(...)`

**Description:**

```java
default Promise<Long> deleteByQuery(String tenantId, String collectionName, Map<String, Object> queryFilters) {
    return bulkDelete(UUID.randomUUID(), tenantId, List.of());
}
```

`bulkDelete` is called with an **empty list** (`List.of()`), meaning zero entities are
ever deleted. The returned `Promise<Long>` resolves to `0` in all cases, giving callers
false confidence that the operation succeeded. Any code paths that rely on this default
to remove stale data (reflex rule cleanup, autonomy log eviction, semantic node pruning)
silently lose their delete semantics.

**Severity:** CRITICAL – Data integrity / silent data-loss by omission.

**Recommendation:**

```java
default Promise<Long> deleteByQuery(String tenantId, String collectionName, Map<String, Object> queryFilters) {
    throw new UnsupportedOperationException(
        "deleteByQuery must be implemented by " + getClass().getSimpleName());
}
```

Connectors that don't need bulk delete can provide an empty-body override with a log
warning. Leaving a silent no-op is far more dangerous than a fast-fail.

---

#### FINDING-C3 · `JpaEntityRepositoryImpl.DB_EXECUTOR` Declared but Never Used — All JPA Calls Block Event-Loop Thread

**File:** `platform/src/main/java/com/ghatana/datacloud/infrastructure/persistence/JpaEntityRepositoryImpl.java`

**Description:**  
The class declares:

```java
private static final ExecutorService DB_EXECUTOR =
    Executors.newThreadPerTaskExecutor(/* virtual threads */);
```

The JavaDoc explicitly states: *"Wraps blocking JPA calls in `Promise.ofBlocking()` for
ActiveJ compatibility."* However, every single method in the class performs a blocking
`EntityManager` call and then wraps the result in `Promise.of(result)` — **not**
`Promise.ofBlocking(DB_EXECUTOR, ...)`:

```java
// ACTUAL CODE  (blocks caller thread)
List<Entity> results = query.getResultList();
return Promise.of(results);

// REQUIRED CODE (offloads blocking call)
return Promise.ofBlocking(DB_EXECUTOR, () -> query.getResultList());
```

`DB_EXECUTOR` is referenced **zero times** outside its own declaration.

Under the ActiveJ event-loop model, any blocking call on the event-loop thread stalls
**all** concurrent operations for all tenants until the JDBC round-trip completes.
Under sustained load (e.g., `ConcurrentTenantLoadTest` with 50 tenants × 100 ops), this
produces head-of-line blocking and latency spikes that are invisible until load testing.

**Affected methods:** `findById`, `findAll`, `findByQuery`, `save`, `delete`, `exists`,
`count`, `countByFilter`, `bulkSave`, `bulkDelete` — all methods.

**Severity:** CRITICAL – Violates the core ActiveJ contract; causes event-loop starvation.

**Recommendation:**  
Wrap every blocking `EntityManager` call in `Promise.ofBlocking(DB_EXECUTOR, () -> { ... })`:

```java
@Override
public Promise<Optional<Entity>> findById(String tenantId, String collectionName, UUID entityId) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(collectionName, "Collection name must not be null");
    Objects.requireNonNull(entityId, "Entity ID must not be null");
    return Promise.ofBlocking(DB_EXECUTOR, () -> {
        TypedQuery<Entity> q = entityManager.createQuery(
            "SELECT e FROM Entity e WHERE e.tenantId = :tenantId " +
            "AND e.collectionName = :collectionName AND e.id = :id AND e.active = true",
            Entity.class);
        q.setParameter("tenantId", tenantId);
        q.setParameter("collectionName", collectionName);
        q.setParameter("id", entityId);
        List<Entity> r = q.getResultList();
        return r.isEmpty() ? Optional.empty() : Optional.of(r.get(0));
    });
}
```

The `DB_EXECUTOR` (virtual-thread-per-task) is already the correct choice. The JPA
`EntityManager` is `@PersistenceContext`-scoped so it is safe to use from the virtual
thread executing inside `ofBlocking`.

---

#### FINDING-C4 · `QuerySpec` Type-Confusion Boundary — `findByQuery` Silently Returns Empty Results

**Files:**
- `platform/.../entity/storage/QuerySpec.java` (`com.ghatana.datacloud.entity.storage`)
- `platform/.../application/QuerySpec.java` (`com.ghatana.datacloud.application`)
- `platform/.../infrastructure/persistence/JpaEntityRepositoryImpl.java` line ~230

**Description:**  
There are two unrelated `QuerySpec` classes in the codebase:

| Class | Package | Has `sql()` / `parameters()`? |
|:------|:--------|:------------------------------|
| `entity.storage.QuerySpec` | storage layer (SPI-side) | No — has `filter`, `sortFields`, `limit`, `offset`, `projections`, `timeWindowStart/End`, `metadata` |
| `application.QuerySpec` | application layer | Yes — has `sql()`, `parameters()`, `offset()`, `limit()`, `sort()` |

`EntityRepository.findByQuery(Object querySpec)` performs:

```java
if (!(querySpec instanceof QuerySpec spec)) {   // application.QuerySpec
    logger.warn("Unsupported querySpec type: {}", querySpec.getClass());
    return Promise.of(Collections.emptyList());
}
```

`StorageConnector.query(String, String, QuerySpec)` calls
`entityRepository.findByQuery(tenantId, collectionName, spec)` where `spec` is of type
`entity.storage.QuerySpec`. The `instanceof` check **fails silently** — a `WARN` log is
emitted and an empty list is returned. Callers receive no exception.

This means:
- Any connector that calls `query(String, String, entity.storage.QuerySpec)` returns
  empty results for all queries without any exception.
- `PostgresJsonbConnector` inherits this issue through the default-method chain.

**Severity:** CRITICAL – Query result suppression; data appears missing.

**Recommendation:**  
Consolidate to a single `QuerySpec` or remove the `findByQuery(Object)` overload and
replace with two typed overloads:

```java
Promise<List<Entity>> findByQuery(String tenantId, String collectionName,
                                  com.ghatana.datacloud.application.QuerySpec spec);

Promise<List<Entity>> findByQuery(String tenantId, String collectionName,
                                  com.ghatana.datacloud.entity.storage.QuerySpec storageSpec);
```

The second overload converts `entity.storage.QuerySpec` into SQL via
`DynamicQueryBuilder` (which already handles this conversion for field-level filters)
before executing the native query.

---

### 🟠 HIGH

---

#### FINDING-H1 · `JpaEntityRepositoryImpl.findAll()` Silently Ignores Non-Empty Filter Map

**File:** `platform/src/main/java/com/ghatana/datacloud/infrastructure/persistence/JpaEntityRepositoryImpl.java`

**Description:**

```java
if (filter != null && !filter.isEmpty()) {
    for (String key : filter.keySet()) {
        logger.warn("JSONB filtering not fully implemented in JPQL for key: {}", key);
    }
    // ← No actual filter applied. Returns ALL active entities in collection.
}
```

Callers that pass a filter map receive unfiltered results. There is no exception, no
signal in the return value, and only a DEBUG-level (actually WARN-level) log that is
invisible in production log configurations. Any feature that depends on `findAll()` with
filter criteria — e.g., tenant-scoped collection listing, data exports with field
filters — silently returns over-broad data sets.

**Recommendation:**  
Implement JSONB containment filtering using PostgreSQL's `@>` operator via a native
query fallback, or throw `UnsupportedOperationException` to make the gap explicit. The
`DynamicQueryBuilder` already supports field-level filters and JSONB operators; wire it
into `findAll()` to build a native query using the existing `findByQuery()` path.

---

#### FINDING-H2 · `InMemoryEventLogStoreProvider.read()` Treats `Offset` as a List Index

**File:** `spi/src/main/java/.../InMemoryEventLogStoreProvider.java`

**Description:**

```java
return entries.stream()
    .skip(startOffset.value())  // ← offset treated as index position
    .limit(limit)
    ...
```

`Offset.value()` is a logical event offset (e.g., a monotonically increasing integer
assigned at append time), not a sequential list position. If entries are appended with
gaps (offsets 1, 2, 3, 5, 7 — as happens after any crash recovery or compaction),
`.skip(5)` skips the first 5 entries by array position, not all entries with offset < 5.

This causes the in-memory store to:
- Return incorrect events in recovery scenarios.
- Silently diverge from the PostgreSQL warm-tier and Kafka stores (which correctly seek
  by offset value, not by list index).

`readByType()` has the same bug.

**Recommendation:**

```java
return entries.stream()
    .filter(e -> {
        String raw = e.headers().get(EventLogStore.HEADER_OFFSET_KEY);
        if (raw == null) return false;
        try { return Long.parseLong(raw) >= startOffset.value(); }
        catch (NumberFormatException ex) { return false; }
    })
    .limit(limit)
    .collect(Collectors.toList());
```

Also ensure `InMemoryEventLogStoreProvider.append()` injects the `_x_dc_offset` header
(currently missing — see FINDING-M4).

---

#### FINDING-H3 · `StorageConnector.write()` Default Always Calls `create()` — No Upsert Semantics

**File:** `platform/.../entity/storage/StorageConnector.java`  
**Method:** `default Promise<Entity> write(...)`

**Description:**  
The Javadoc promises: *"Creates entity if ID is new, updates if ID already exists."*
The implementation:

```java
// Simple approach: try create first, fallback to update on failure
return create(entity);
```

There is **no fallback to `update()`**. The comment says "fallback to update on failure"
but the `exceptionally` / `thenCompose` to actually do so is absent. Repeated writes
with the same ID will attempt to re-insert the entity, causing a unique-constraint
violation (`pk_entities PRIMARY KEY (id)`) that surfaces as a storage exception to the
caller — or worse, a silent failure if the connector swallows the exception.

**Recommendation:**

```java
default Promise<Entity> write(String tenantId, String collectionName, Object entityId, Map<String, Object> data) {
    UUID id = resolveId(entityId);
    Entity entity = Entity.builder()
            .tenantId(tenantId).collectionName(collectionName).id(id).data(data).build();
    return create(entity)
            .exceptionally(err -> {
                if (isDuplicateKeyException(err)) {
                    return update(entity);  // actual upsert fallback
                }
                throw new StorageException("write failed", err);
            });
}
```

Or prefer a native `ON CONFLICT DO UPDATE` at the PostgreSQL level (already available
in `PostgresJsonbConnector.create()` via `merge()`).

---

#### FINDING-H4 · `PostgresJsonbConnector.query()` Total Count Always Unfiltered

**File:** `platform/src/main/java/com/ghatana/datacloud/infrastructure/storage/PostgresJsonbConnector.java`

**Description:**  
When building a `QueryResult`, the connector calls:

```java
long totalCount = entityRepository.countByFilter(tenantId, collectionName, Collections.emptyMap());
```

`Collections.emptyMap()` means "no filter" — the count reflects the **total number of
entities in the collection**, not the number matching the query. API consumers that
display "showing N of M total" in paginated UIs will show inflated totals.

**Recommendation:**  
Pass the active filter map derived from the `QuerySpec`:

```java
long totalCount = entityRepository.countByFilter(tenantId, collectionName, spec.filterAsMap());
```

This requires adding a `filterAsMap()` method to `entity.storage.QuerySpec` or passing
the filter metadata through the `QueryResult` builder. At minimum, pass
`spec.metadata()` if it carries filter context.

---

#### FINDING-H5 · V001 / V002 / V005 Indexes Lack `IF NOT EXISTS` — Cannot Safely Re-apply

**Files:**
- `V001__create_events_table.sql`
- `V002__create_entities_table.sql`
- `V005__create_event_log.sql`

**Description:**  
All three migrations create indexes with bare `CREATE INDEX` statements:

```sql
-- V001
CREATE INDEX idx_events_tenant ON events (tenant_id);
CREATE INDEX idx_events_stream ON events (tenant_id, stream_name);
...
-- V002
CREATE INDEX idx_entities_tenant ON entities (tenant_id);
...
```

If a migration is manually re-applied (e.g., after a rollback in staging, or when
bootstrapping an environment from a dumped schema), these statements will fail with
`"relation already exists"`. Flyway checksums prevent re-apply of the exact migration
in normal operation, but `CREATE INDEX CONCURRENTLY` cannot run inside a transaction
and is therefore sometimes executed outside Flyway — increasing the risk of partial
re-runs.

V008 (GIN index, created this audit cycle) correctly uses `CREATE INDEX CONCURRENTLY
IF NOT EXISTS`, setting a precedent that earlier migrations do not follow.

**Recommendation:**  
Create a follow-up migration `V009__add_if_not_exists_to_legacy_indexes.sql` that
idempotently verifies/recreates missing indexes (cannot ALTER existing CREATE INDEX
statements without dropping/recreating).

---

#### FINDING-H6 · `AnalyticsQueryEngine.queries` and `queryPlans` Still Use `Collections.synchronizedMap`

**File:** `platform/.../analytics/AnalyticsQueryEngine.java`

**Description:**  
FINDING-8 from data-cloud-1 correctly replaced the `resultCache` with a Caffeine-backed
cache. However, `queries` (active `AnalyticsQuery` map) and `queryPlans` (compiled
`QueryPlan` cache) still use `Collections.synchronizedMap(new LinkedHashMap(...))`:

- `synchronizedMap` holds a single coarse mutex; every read and write on these maps is
  serialized behind the same lock.
- Under parallel query submission (multi-tenant analytics), all threads block on the
  same lock even for read operations.
- The `removeEldestEntry` override evicts LRU entries but the `LinkedHashMap` itself is
  not thread-safe for concurrent iteration — iterating while adding can produce
  `ConcurrentModificationException` even through the `synchronizedMap` wrapper if the
  iterator is used outside a `synchronized` block.

**Recommendation:**  
Replace with `Caffeine.newBuilder().maximumSize(MAX_CACHE_ENTRIES).buildAsMap()` (a
`ConcurrentMap` built on a Caffeine cache) or use `ConcurrentHashMap` with explicit
eviction policy. The Caffeine dependency is already present in `platform/build.gradle.kts`.

---

### 🟡 MEDIUM

---

#### FINDING-M1 · `NLQService.EQUALS_PATTERN` Cannot Match Quoted Multi-Word Values

**File:** `platform/src/main/java/com/ghatana/datacloud/application/nlq/NLQService.java`

**Description:**  
The `EQUALS_PATTERN`:

```java
private static final Pattern EQUALS_PATTERN =
    Pattern.compile("(\\w+)\\s+(?:equals?|is|=)\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
```

The value capture group `(\\w+)` only matches word-characters (no spaces, quotes, or
hyphens). A query like `name equals "John Doe"` or `status = "in-progress"` produces no
match. FINDING-13 broadened `CONTAINS_PATTERN` to handle this, but `EQUALS_PATTERN` was
not updated consistently.

**Recommendation:**

```java
private static final Pattern EQUALS_PATTERN =
    Pattern.compile("(\\w+)\\s+(?:equals?|is|=)\\s+\"?([^\",]+?)\"?\\s*$",
                    Pattern.CASE_INSENSITIVE);
```

---

#### FINDING-M2 · `WarmTierEventLogStore.parseLong()` Returns `0` on Parse Failure — Silent Offset Reset

**File:** `platform/src/main/java/com/ghatana/datacloud/storage/WarmTierEventLogStore.java`

**Description:**  
The offset parsing for subscriptions returns `0L` on `NumberFormatException`:

```java
private long parseLong(Offset offset) {
    try {
        return Long.parseLong(offset.value().toString());
    } catch (NumberFormatException e) {
        return 0L;   // ← silently starts reading from the beginning
    }
}
```

A corrupt or missing offset header causes the subscription to restart from offset 0,
re-delivering all historical events to the consumer. For feature-store ingest pipelines
or analytics aggregation, this results in silent full-replay and duplicate processing.

**Recommendation:**

```java
private long parseLong(Offset offset) {
    try {
        return Long.parseLong(offset.value().toString());
    } catch (NumberFormatException e) {
        LOG.error("Invalid offset value '{}' — cannot parse as long. Failing fast to prevent re-delivery.",
                  offset.value());
        throw new IllegalArgumentException("Invalid offset: " + offset.value(), e);
    }
}
```

Alternatively, fall back to the end-of-log offset so re-processing is opt-in.

---

#### FINDING-M3 · `JpaEntityRepositoryImpl.findAll()` Sort Only Handles `createdAt` / `updatedAt`

**File:** `JpaEntityRepositoryImpl.java`

**Description:**

```java
if (field.equals("createdAt") || field.equals("updatedAt")) {
    jpql.append(" ORDER BY e.").append(field).append(" ").append(direction);
}
// All other sort fields silently ignored
```

Sorts by any JSONB data field (e.g., `name`, `status`, `price`) are accepted without
error but produce unsorted results. This is undocumented and will produce subtly wrong
result ordering in paginated UIs.

**Recommendation:**  
Use `DynamicQueryBuilder` to build sort expressions for data fields using the
JSONB arrow operator: `ORDER BY e.data->>'fieldName' ASC`. If a field is not in the
schema, raise a `BadRequestException` rather than silently dropping the sort.

---

#### FINDING-M4 · `InMemoryEventLogStoreProvider` Does Not Inject `_x_dc_offset` Header

**File:** `spi/src/main/.../InMemoryEventLogStoreProvider.java`

**Description:**  
`WarmTierEventLogStore` and `KafkaEventLogStore` both inject an `_x_dc_offset` header
into `EventEntry.headers()` on every entry returned from `read()` and within
`PollingSubscription`. `InMemoryEventLogStoreProvider` omits this injection entirely.

Consumers that use header-based offset advancement (e.g., `FeatureStoreIngestLauncher`
using `headers.get("_x_dc_offset")` with a fallback to size-based position) will fall
back to the list-size heuristic, which is also incorrect (see FINDING-H2).

In integration test scenarios where `InMemoryEventLogStoreProvider` is the provider,
offset-tracking bugs are masked and only surface against real infrastructure.

**Recommendation:**  
In `InMemoryEventLogStoreProvider.append()`, track an `AtomicLong` offset counter and
inject it as a header on every stored entry.

---

#### FINDING-M5 · `V007` Is a Pure-Comment Migration — Risk of Naming Collision

**File:** `V007__entities_display_name_index_README.sql`

**Description:**  
V007 contains only SQL comments — no DDL. Flyway applies it as a no-op checkpoint for
documentation purposes. The comment refers to "CREATE INDEX CONCURRENTLY" being
executed at startup by `DataMigrationService`, but:

1. **V008 naming conflict:** The V007 comment says `V008` will add a `NOT NULL`
   constraint on `display_name`. However, the actual V008 (`V008__add_entities_data_gin_index.sql`)
   adds a **GIN index**, not a NOT NULL constraint. The `NOT NULL` promotion migration
   described in V007 is nowhere in the codebase.
2. The comment-only migration is a documentation anti-pattern; docs belong in `docs/`
   or inline on the DDL migration that actually executes work. Flyway checksums lock
   this file forever.

**Recommendation:**  
Create `V009__entities_display_name_not_null.sql` (when backfill is confirmed complete)
for the `NOT NULL` constraint. Update V007 comment to reflect that V008 is the GIN
index migration. Do not create further documentation-only Flyway migrations.

---

#### FINDING-M6 · `OpenSearchConnector` Uses Raw Micrometer Instead of Platform `MetricsCollector`

**File:** `platform/src/main/java/com/ghatana/datacloud/infrastructure/storage/OpenSearchConnector.java`

**Description:**  
`OpenSearchConnector` directly instantiates and uses `MeterRegistry` from Micrometer,
bypassing `com.ghatana.observability.MetricsCollector` (from `libs:observability`):

```java
// OpenSearchConnector
Counter.builder("datacloud.opensearch.writes")
    .register(meterRegistry)
    .increment();
```

`PostgresJsonbConnector` correctly uses `MetricsCollector` (the platform abstraction).
Using raw Micrometer bypasses the platform's tag normalization, multi-tenancy label
injection, and cardinality guards defined in `libs:observability`.

**Recommendation:**  
Replace `MeterRegistry` with `MetricsCollector` injection (same as `PostgresJsonbConnector`):

```java
// Inject
private final MetricsCollector metrics;

// Usage
metrics.incrementCounter("datacloud.opensearch.writes",
    "tenantId", tenantId, "collection", collectionName);
```

---

#### FINDING-M7 · `PostgresJsonbConnectorTest` Is Globally Disabled

**File:** `platform/src/test/java/.../infrastructure/storage/PostgresJsonbConnectorTest.java`

**Description:**  
The test class carries `@Disabled("Temporarily disabled due to assertion issues in test environment")`. The class exists but contributes zero coverage to production code. Given
that `PostgresJsonbConnector` is the primary write path for entity data, this leaves the
connector's create / update / delete / query behavior entirely unverified by automated
tests. The current test suite (1115 tests, 0 failures) passes precisely because this
test is excluded.

**Recommendation:**  
Fix the assertion issues causing the disablement and re-enable. If the issue is mocked
interaction semantics, update the mocks to match the refactored code (particularly the
`findByQuery()` routing fixed during data-cloud-1 remediation).

---

### 🔵 LOW

---

#### FINDING-L1 · V001 Unique Constraint on Nullable `idempotency_key` — Undocumented NULL Semantics

**File:** `V001__create_events_table.sql`

```sql
CONSTRAINT uk_events_idempotency UNIQUE (tenant_id, idempotency_key)
```

PostgreSQL's UNIQUE constraint allows multiple NULL values (ISO SQL standard: NULL ≠
NULL). This is the correct behavior for an optional idempotency key — multiple events
without an idempotency key do not conflict. However, the migration comment does not
document this. A developer unfamiliar with PostgreSQL NULL-in-UNIQUE semantics may
assume the constraint prevents all duplicate records.

**Recommendation:**  
Add a comment to V001 (or V005 which has the same pattern):
```sql
-- NULL idempotency_key is allowed multiple times (PostgreSQL UNIQUE ignores NULLs).
-- Uniqueness is enforced only when idempotency_key IS NOT NULL.
CONSTRAINT uk_events_idempotency UNIQUE (tenant_id, idempotency_key)
```

---

#### FINDING-L2 · `AnalyticsQueryEngineTest` Instantiates Engine Without `StorageConnector`

**File:** `platform/src/test/.../AnalyticsQueryEngineTest.java`

**Description:**

```java
engine = new AnalyticsQueryEngine();
```

`AnalyticsQueryEngine` requires a `StorageConnector` to execute queries. The test
creates an instance with no connector and only exercises `generateQueryPlan()` and
`submitQuery()` without verifying actual execution (`executeSelect`,
`executeAggregate`, `executeTimeSeries`, `executeJoin`). The execution paths — the most
likely source of bugs — are untested.

**Recommendation:**  
Inject a `mock(StorageConnector.class)` and add tests for each query type to verify the
correct `connector.query()` overload is called with expected arguments.

---

#### FINDING-L3 · `StorageConnector` `UUID.nameUUIDFromBytes` Uses Platform Default Charset

**File:** `StorageConnector.java`  
**Methods:** `query(String, String, QuerySpec)` and `query(String, String, Map)` defaults, `count(String, String)` default

**Description:**

```java
UUID syntheticId = UUID.nameUUIDFromBytes(collectionName.getBytes());
```

`String.getBytes()` without an explicit charset uses the JVM default charset, which is
platform- and locale-dependent. On most modern JVMs this is UTF-8, but it can differ
on Windows or in containerized environments with non-standard `file.encoding` settings,
producing **different UUIDs for the same collection name across environments**.

**Recommendation:**

```java
UUID syntheticId = UUID.nameUUIDFromBytes(collectionName.getBytes(StandardCharsets.UTF_8));
```

---

#### FINDING-L4 · `InMemoryEventLogStoreProvider.tail()` Cannot Be Interrupted Mid-Processing

**File:** `spi/src/main/.../InMemoryEventLogStoreProvider.java`

**Description:**  
`tail()` delivers all existing entries synchronously before returning the subscription
object. If the entry list is large, the call blocks for the duration of delivery.
After delivery begins, `subscription.cancel()` sets a flag, but in-progress delivery
cannot be interrupted (no `Thread.interrupted()` checks between entries). This affects
test teardown and consumer lifecycle management.

**Recommendation:**  
Check the cancellation flag between each entry:

```java
for (EventEntry entry : snapshot) {
    if (cancelled.get()) break;
    handler.accept(entry);
}
```

---

#### FINDING-L5 · `DataCloudEnvConfig.authToken()` Warning Does Not Differentiate `test` Profile

**File:** `platform/src/main/java/com/ghatana/datacloud/config/DataCloudEnvConfig.java`

**Description:**  
The `authToken()` method added during data-cloud-1 remediation warns when the token is
empty and the environment is not `dev`:

```java
if (token.isBlank() && !"dev".equalsIgnoreCase(environment)) {
    LOG.warn("DATA_CLOUD_AUTH_TOKEN is empty in non-dev environment: {}", environment);
}
```

CI pipelines that run with `environment=test` (or `staging`) but intentionally omit
the token (test containers use local mTLS, not token auth) will flood logs with false
warnings on every startup. The warning is correct for `production` but noisy for
`staging` and `test`.

**Recommendation:**

```java
private static final Set<String> TOKEN_OPTIONAL_ENVS =
    Set.of("dev", "test", "local");

if (token.isBlank() && !TOKEN_OPTIONAL_ENVS.contains(environment.toLowerCase())) {
    LOG.warn("DATA_CLOUD_AUTH_TOKEN is empty in environment: {}. "
             + "Requests may fail authentication.", environment);
}
```

---

## Schema Audit

### All Migrations — Summary Table

| Migration | DDL | Notes |
|:----------|:----|:-------|
| **V001** – events | `CREATE TABLE IF NOT EXISTS events` | Indexes lack `IF NOT EXISTS`. `uk_events_idempotency` NULL semantics undocumented (FINDING-L1). |
| **V002** – entities | `CREATE TABLE IF NOT EXISTS entities` | Indexes lack `IF NOT EXISTS` (FINDING-H5). `display_name` column added in V006. |
| **V003** – timeseries | `CREATE TABLE IF NOT EXISTS timeseries_data` | Not reviewed in depth; appears structurally sound. |
| **V004** – collections | `CREATE TABLE IF NOT EXISTS collections` | Has `IF NOT EXISTS`. Trigger `update_collections_updated_at()` uses `CREATE OR REPLACE FUNCTION`. No soft-delete column. |
| **V005** – event_log | `CREATE TABLE IF NOT EXISTS event_log` | `GENERATED ALWAYS AS IDENTITY` for offset — correct sequential ID semantics. `uk_event_log_idem` NULL semantics same as V001. Indexes lack `IF NOT EXISTS`. |
| **V006** – display_name expand | `ALTER TABLE entities ADD COLUMN IF NOT EXISTS display_name` | Zero-downtime nullable add. Expand/contract pattern well-executed. References backfill in `DataMigrationService`. |
| **V007** – checkpoint | Comment-only | Naming conflict with described V008 NOT NULL step (FINDING-M5). |
| **V008** – GIN index | `CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_entities_data_gin` | Correct — `CONCURRENTLY`, `IF NOT EXISTS`, `jsonb_path_ops`. Gold standard for future indexes. |

### V008 as the Reference Index Pattern

All future index migrations should follow the pattern established in V008:
```sql
CREATE INDEX CONCURRENTLY IF NOT EXISTS <name>
    ON <table> USING <method> (<column>);
```
A retrofit `V009` migration should add `IF NOT EXISTS` to existing bare `CREATE INDEX`
statements in V001, V002, and V005 to close the idempotency gap (FINDING-H5).

---

## Module-by-Module Review

### `spi/` — Service Provider Interface Layer

**Strengths:**  
- Clean separation: `EventLogStore`, `EntityStore`, `TenantContext`, `StorageTier` are
  well-documented records/interfaces with no implementation dependencies.  
- `StorageTier` enum lifecycle semantics (HOT/WARM/COOL/COLD) are thoroughly documented.  
- `InMemoryEventLogStoreProvider` is a useful testing default registered via ServiceLoader.

**Issues:**  
- `InMemoryEventLogStoreProvider.read()` offset-as-index bug (FINDING-H2).  
- `InMemoryEventLogStoreProvider` missing `_x_dc_offset` header injection (FINDING-M4).

### `platform/entity/storage/` — Storage Port Layer

**Strengths:**  
- `StorageConnector` hexagonal port is well-documented.  
- `QuerySpec` (entity.storage) is an immutable record.

**Issues:**  
- `StorageConnector.query(String, String, Map)` SQL injection (FINDING-C1).  
- `StorageConnector.deleteByQuery()` silent no-op (FINDING-C2).  
- `StorageConnector.write()` broken upsert (FINDING-H3).  
- `UUID.nameUUIDFromBytes` charset issue (FINDING-L3).  
- Type confusion with `application.QuerySpec` (FINDING-C4).

### `platform/infrastructure/` — Persistence Adapters

**Strengths:**  
- `PostgresJsonbConnector` correctly routes through `findByQuery()` after data-cloud-1
  remediation.  
- `SimpleEncryptionService` is cleaned up (no `main()`).  
- `DataCloudEnvConfig` centralizes all config vars with startup validation.  
- Audit logging via `DataCloudAuditLogger` on all write operations.

**Issues:**  
- `JpaEntityRepositoryImpl` does not use `Promise.ofBlocking` (FINDING-C3).  
- `JpaEntityRepositoryImpl.findAll()` filter silently ignored (FINDING-H1).  
- `JpaEntityRepositoryImpl.findAll()` sort limited to `createdAt`/`updatedAt` (FINDING-M3).  
- `PostgresJsonbConnector.query()` count always unfiltered (FINDING-H4).  
- `PostgresJsonbConnectorTest` globally disabled (FINDING-M7).  
- `OpenSearchConnector` uses raw Micrometer (FINDING-M6).

### `platform/application/` — Application Services

**Strengths:**  
- `DynamicQueryBuilder` generates fully parameterized SQL — no injection risk.  
- `application.QuerySpec` is immutable with validation (`limit > 0`, `offset >= 0`,
  `limit <= 10_000`).  
- `NLQService` has a confidence scoring system and field name allow-listing.

**Issues:**  
- `NLQService.EQUALS_PATTERN` cannot match quoted multi-word values (FINDING-M1).  
- Dual `QuerySpec` type confusion creates silent failure mode (FINDING-C4).

### `platform/analytics/` — Analytics Engine

**Strengths:**  
- Caffeine result cache with TTL and bounded size (fixed in data-cloud-1).  
- Bounded thread pool (fixed in data-cloud-1).  
- SQL AST parsing via JSQLParser.  
- `generateQueryPlan()` case-preserving fix (from data-cloud-1).

**Issues:**  
- `queries` and `queryPlans` still use `Collections.synchronizedMap` (FINDING-H6).  
- `AnalyticsQueryEngineTest` lacks connector injection, execution paths untested
  (FINDING-L2).

### `platform/storage/` — Event Log Warm Tier

**Strengths:**  
- All 7 `EventLogStore` SPI methods correctly wrapped in `Promise.ofBlocking`.  
- `PollingSubscription` is well-structured with back-off on error.  
- `HEADER_OFFSET_KEY` constant avoids magic strings.  
- `doAppendBatchSync()` uses a single connection + transaction for atomicity.

**Issues:**  
- `parseLong()` silently falls back to 0 on parse failure (FINDING-M2).

### `feature-store-ingest/` — Ingest Pipeline

**Strengths:**  
- Shared scheduler (fixed in data-cloud-1 — previously leaked threads).  
- Offset tracking via `_x_dc_offset` header (fixed in data-cloud-1).  
- Postgres mode correctly wires `WarmTierEventLogStore(HikariDataSource)`.  
- `platform` dependency added to `build.gradle.kts`.

**Issues:**  
- None new identified in this audit cycle.

---

## Test Coverage Assessment

| Area | Coverage Verdict | Gap |
|:-----|:----------------|:----|
| `StorageTier` enum | ✅ Comprehensive | None |
| `TenantIsolationConnector` | ✅ Updated (data-cloud-1 fix) | None |
| `MultiTenancyIsolation` | ✅ 50-tenant concurrent test | None |
| `WarmTierEventLogStore` | ✅ Integration via Testcontainers | Requires Docker |
| `NLQService` | ✅ Mockito unit tests | `EQUALS_PATTERN` quoted values not tested |
| `AnalyticsQueryEngine` | ⚠️ Partial | Execution paths untested (FINDING-L2) |
| `JpaEntityRepositoryImpl` | ❌ No dedicated test | All methods untested |
| `PostgresJsonbConnector` | ❌ Disabled | FINDING-M7 |
| `InMemoryEventLogStoreProvider` | ❌ No tests | Offset bug masked |
| `StorageConnector` defaults | ❌ No tests | SQL injection + no-op untested |
| `DynamicQueryBuilder` | ❌ No tests found | SQL generation unverified |

---

## Data Integrity Risks

1. **Event De-duplication Gap (V005 + InMemory):** The `unique constraint` on
   `(tenant_id, event_id)` prevents duplicate events at the database level.
   `InMemoryEventLogStoreProvider` has no duplicate-key enforcement at all —
   test scenarios that assert idempotency may pass incorrectly.

2. **Offset Gap on Crash Recovery:** `FeatureStoreIngestLauncher` reads the last
   processed offset from `_x_dc_offset` headers. If the launcher crashes mid-batch, the
   next startup reads from the last successfully committed offset. The in-memory fallback
   (size of local list) used when no header is present will re-process events, and since
   `PostgresJsonbConnector.create()` uses JPA merge, re-inserted entities will update
   (`version++`) rather than duplicate — this is safe, but the re-processing latency is
   uncapped.

3. **`findAll()` Filter Silently Ignored:** Any export or replication job that filters
   by field values using `findAll()` will export the entire collection, not just the
   filtered subset (FINDING-H1).

4. **Silent Empty Query Results:** The `QuerySpec` type confusion (FINDING-C4) means
   any call path from `StorageConnector` → `PostgresJsonbConnector` → `findByQuery()`
   with `entity.storage.QuerySpec` returns zero entities without error. Analytics,
   NLQ, and reflex rules that trigger on query results will silently see empty data.

---

## Security Analysis

| Finding | OWASP Category | Severity |
|:--------|:--------------|:---------|
| FINDING-C1 – SQL injection in `StorageConnector.query(Map)` default | A03 Injection | 🔴 CRITICAL |
| FINDING-C3 – Blocking JPA on event loop (DoS amplification under load) | A05 Security Misconfiguration | 🔴 CRITICAL |
| FINDING-H3 – `write()` default always creates (constraint violation path) | A04 Insecure Design | 🟠 HIGH |
| FINDING-C2 – `deleteByQuery()` silent no-op (data retention bypass) | A04 Insecure Design | 🔴 CRITICAL |
| `DynamicQueryBuilder` | ✅ Parameterized | No issue |
| `NLQService` field allow-listing | ✅ Present | Good practice |
| `SimpleEncryptionService.main()` removed | ✅ Fixed in data-cloud-1 | No issue |
| `DataCloudEnvConfig.authToken()` warning | ✅ Added in data-cloud-1 | See FINDING-L5 |

No issues found with: authentication token handling, encryption key management,
audit logging coverage, or tenant isolation boundary enforcement.

---

## Performance Analysis

| Area | Finding | Impact |
|:-----|:--------|:-------|
| JPA calls on event loop | FINDING-C3 | Head-of-line blocking; all tenants serialized |
| `synchronizedMap` in AnalyticsQueryEngine | FINDING-H6 | Mutex contention under parallel queries |
| GIN index on `entities.data` | V008 ✅ | JSONB queries now index-assisted |
| `findAll()` filter not applied | FINDING-H1 | Full table scans instead of filtered |
| `OpenSearchConnector.ensureIndex()` on every write | Observed | Round-trip per write; `confirmedIndices` set mitigates |
| `PollingSubscription` 250ms poll interval | Observed | Fixed interval; could be adaptive |

---

## Resilience & Fault Tolerance

| Area | Status |
|:-----|:-------|
| `WarmTierEventLogStore` exponential back-off on poll error | ✅ Present |
| `KafkaEventLogStore` `endOffsets()` 10s timeout | ✅ Added in data-cloud-1 |
| `FeatureStoreIngestLauncher` shared scheduler (no leak) | ✅ Fixed in data-cloud-1 |
| `OpenSearchConnector` circuit breaker | ❌ Missing |
| `OpenSearchConnector` retry on connection failure | ❌ Missing |
| `JpaEntityRepositoryImpl` connection pool exhaustion guard | Relies on HikariCP timeout; event-loop blocking makes this worse |
| `AnalyticsQueryEngine` query timeout | Observed — no per-query timeout enforced |

---

## Code Health

### Dead Code

| File | Dead Code |
|:-----|:---------|
| `JpaEntityRepositoryImpl` | `DB_EXECUTOR` declared but never referenced |
| `StorageConnector` | `deleteByQuery()` default body (no-op) |

### Documentation Gaps

| File | Gap |
|:-----|:----|
| `StorageConnector.query(String,String,Map)` | Javadoc does not warn about injection risk |
| `StorageConnector.deleteByQuery()` | Javadoc says "bulk delete" but body is a no-op |
| `JpaEntityRepositoryImpl.findAll()` | Javadoc says filter uses `@>` but filter is silently ignored |
| V007 | Misleading next-step reference (see FINDING-M5) |

### Naming

- `QuerySpec` (two classes, two packages) — the dual-QuerySpec naming is the root cause
  of FINDING-C4. One should be renamed (e.g., `StorageQuerySpec` for the SPI-side
  record) to eliminate ambiguity.

---

## Prioritized Remediation Plan

### Sprint 1 — Critical (block release)

| Priority | Finding | Effort |
|:---------|:--------|:-------|
| 1 | FINDING-C1: Remove SQL concatenation in `StorageConnector.query(Map)` | 30 min |
| 2 | FINDING-C2: Make `deleteByQuery()` throw `UnsupportedOperationException` | 15 min |
| 3 | FINDING-C3: Wrap all `JpaEntityRepositoryImpl` methods in `Promise.ofBlocking` | 2 h |
| 4 | FINDING-C4: Rename `entity.storage.QuerySpec` → `StorageQuerySpec` and fix all call sites | 1–2 h |

### Sprint 2 — High

| Priority | Finding | Effort |
|:---------|:--------|:-------|
| 5 | FINDING-H1: Implement JSONB filter in `findAll()` or throw `UnsupportedOperationException` | 1 h |
| 6 | FINDING-H2: Fix `InMemoryEventLogStoreProvider.read()` offset semantics | 30 min |
| 7 | FINDING-H3: Fix `StorageConnector.write()` upsert semantics | 30 min |
| 8 | FINDING-H4: Fix `countByFilter` to use active filter, not `emptyMap()` | 20 min |
| 9 | FINDING-H5: Create `V009` migration to add `IF NOT EXISTS` to legacy indexes | 30 min |
| 10 | FINDING-H6: Replace `synchronizedMap` in `AnalyticsQueryEngine` with Caffeine map | 30 min |

### Sprint 3 — Medium / Low

| Priority | Finding | Effort |
|:---------|:--------|:-------|
| 11 | FINDING-M1: Broaden `EQUALS_PATTERN` in `NLQService` | 15 min |
| 12 | FINDING-M2: Make `parseLong()` fail fast on bad offset | 15 min |
| 13 | FINDING-M3: Implement JSONB-field sort in `findAll()` via `DynamicQueryBuilder` | 1 h |
| 14 | FINDING-M4: Inject `_x_dc_offset` header in `InMemoryEventLogStoreProvider` | 20 min |
| 15 | FINDING-M5: Correct V007 comment; plan V009 NOT NULL migration separately | 20 min |
| 16 | FINDING-M6: Replace raw Micrometer in `OpenSearchConnector` with `MetricsCollector` | 30 min |
| 17 | FINDING-M7: Re-enable `PostgresJsonbConnectorTest` | 1–2 h |
| 18 | FINDING-L1–L5: Charset, comment, test, interrupt, config | 2 h total |

---

## What Was Fixed in Data-Cloud-1 (For Completeness)

| Finding | Description | Status |
|:--------|:------------|:-------|
| FINDING-1 | `WarmTierEventLogStore` – all 7 SPI methods `Promise.ofBlocking` | ✅ |
| FINDING-2 | `KafkaEventLogStore` – all SPI methods `Promise.ofBlocking` + `endOffsets()` timeout | ✅ |
| FINDING-3 | `FeatureStoreIngestLauncher` thread pool leak (shared scheduler) | ✅ |
| FINDING-4 | `FeatureStoreIngestLauncher` offset tracking via `_x_dc_offset` header | ✅ |
| FINDING-5 | `PostgresJsonbConnector` filter bypass – routes through `findByQuery()` | ✅ |
| FINDING-6 | `PostgresJsonbConnector` UUID collection name routing | ✅ |
| FINDING-7 | `FeatureStoreIngestLauncher` postgres mode + `build.gradle.kts` dependency | ✅ |
| FINDING-8 | `AnalyticsQueryEngine` bounded executor (`newFixedThreadPool`) | ✅ |
| FINDING-9 | GIN index migration V008 created (`CONCURRENTLY IF NOT EXISTS`) | ✅ |
| FINDING-10 | `AnalyticsQueryEngine` uppercase fix + Caffeine result cache | ✅ |
| FINDING-11a | `SimpleEncryptionService.main()` removed | ✅ |
| FINDING-11b | `DataCloudEnvConfig.authToken()` empty-token warning | ✅ |
| FINDING-13 | `NLQService.CONTAINS_PATTERN` broadened to quoted multi-word values | ✅ |
| FINDING-14 | Build green: 1115 tests, 0 failures | ✅ |

---

## Final Recommendations

1. **Establish a `StorageConnector` contract test suite** that exercises every default
   method against a mock concrete implementation. Three of the four CRITICAL findings
   stem from untested default methods.

2. **Rename `entity.storage.QuerySpec` to `StorageQuerySpec`** (or similar) immediately.
   The dual-QuerySpec naming is the most dangerous code smell: it causes silent data
   suppression with no error, no log at ERROR level, and no test catching it.

3. **Adopt `Promise.ofBlocking` as a mandatory lint rule** for any class implementing
   `EntityRepository` or `EventLogStore`. The `DB_EXECUTOR` pattern in
   `JpaEntityRepositoryImpl` shows the intent was correct; the execution was missing.
   An ArchUnit test can enforce this: *"any method returning `Promise` that contains a
   blocking JDBC call must call `Promise.ofBlocking`"*.

4. **Re-enable `PostgresJsonbConnectorTest`** as a blocker for the Sprint 1 milestone.
   The connector is the primary write path and must have test coverage before the
   `Promise.ofBlocking` migration (FINDING-C3) is landed.

5. **Use V008 as the index migration template** for all future DDL migrations that add
   indexes: always `CONCURRENTLY`, always `IF NOT EXISTS`, always outside a transaction
   block in a separate Flyway migration.

6. **Add an `OpenSearchConnector` circuit breaker** (e.g., Resilience4j) before
   enabling OpenSearch in production tenants. Transient OpenSearch unavailability
   currently propagates as uncaught exceptions to the caller with no retry or fallback.

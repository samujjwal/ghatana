# ADR-008: Data-Cloud SPI with ServiceLoader Discovery

**Status:** Accepted  
**Date:** 2026-01-18  
**Decision Makers:** Platform Team, Data-Cloud Team  
**Phase:** 1 — Foundation  

## Context

Data-Cloud needs to support pluggable storage backends (in-memory, PostgreSQL, Iceberg, S3) without compile-time coupling to any specific backend. The same `DataCloudClient` API should work against any storage implementation, selected at runtime.

## Decision

Define a **Service Provider Interface (SPI)** with **Java `ServiceLoader` discovery**:

**Core SPI interfaces (13 files):**

| Interface | Purpose |
|-----------|---------|
| `EntityStore` | CRUD for entities (save, findById, query, delete) |
| `EventLogStore` | Append-only event log (append, query, tail) |
| `StorageConnector` | Bridge to backend storage technology |
| `StoragePlugin` | Plugin lifecycle (initialize, healthCheck, shutdown) |
| `EncryptionService` | At-rest encryption for stored data |
| `AuditLogger` | Storage-level audit trail |
| `TenantContext` | Tenant isolation context (record type) |

**Capability interfaces (optional, marker-based):**

| Interface | Purpose |
|-----------|---------|
| `AggregationCapability` | Backend supports server-side aggregation |
| `SimilaritySearchCapability` | Backend supports vector/similarity search |
| `StreamingCapability` | Backend supports change streams / CDC |
| `TransactionCapability` | Backend supports ACID transactions |

**Discovery pattern:**
```java
// In DataCloud.create()
ServiceLoader<EntityStore> loader = ServiceLoader.load(EntityStore.class);
EntityStore store = loader.findFirst().orElse(new InMemoryEntityStore());
```

**Built-in implementations:**
- `InMemoryEntityStore` — ConcurrentHashMap, always available as fallback
- `InMemoryEventLogStore` — CopyOnWriteArrayList, always available as fallback

**Testing entry point:**
```java
DataCloudClient client = DataCloud.forTesting(); // In-memory stores, no external deps
```

## Rationale

- **ServiceLoader** is standard Java — no framework dependency for plugin discovery
- **Capability interfaces** allow feature detection: `if (store instanceof TransactionCapability tx) { tx.beginTransaction(); }`
- **In-memory fallbacks** ensure the system always starts, even without external storage
- **`DataCloud.forTesting()`** provides a zero-config test double
- **TenantContext as a record** (`TenantContext.of("tenant1")`) — immutable, lightweight context

## Consequences

- Plugin JARs must include `META-INF/services/` files for ServiceLoader discovery
- Only one implementation per SPI is loaded (first found wins) — no multi-backend routing yet
- In-memory stores are not durable — production must provide JDBC or equivalent implementations
- The SPI is data-cloud specific (`com.ghatana.datacloud.spi`) — not shared with AEP's pipeline SPI

## Alternatives Considered

1. **ActiveJ DI modules for storage wiring** — rejected for SPI; DI is used at the application level, SPI at the library level
2. **OSGi for plugin isolation** — rejected; too heavyweight for this use case
3. **Abstract factory pattern** — rejected; ServiceLoader provides cleaner decoupling

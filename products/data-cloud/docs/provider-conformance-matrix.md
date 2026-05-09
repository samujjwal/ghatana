# Provider Conformance Matrix

**DC-36:** This document tracks the conformance of all EntityStore and EventLogStore provider implementations against the SPI contract requirements.

## EntityStore Implementations

### H2SovereignEntityStore

| Feature | Status | Notes |
|---------|--------|-------|
| save() | ✅ Implemented | Single entity save with tenant scoping |
| saveBatch() | ✅ Implemented | Batch save with partial success semantics |
| findById() | ✅ Implemented | Entity lookup by ID |
| findByRef() | ✅ Implemented | Collection-scoped entity lookup |
| query() | ✅ Implemented | Query with filters and pagination |
| delete() | ✅ Implemented | Single entity delete |
| deleteBatch() | ⚠️ Deprecated | Uses deleteByRefs() instead (DC-P0-001) |
| deleteByRef() | ✅ Implemented | Collection-scoped delete |
| deleteByRefs() | ✅ Implemented | Batch collection-scoped delete |
| Transactional Semantics | ⚠️ Partial | Batch operations allow partial success (DC-19) |
| Tenant Isolation | ✅ Implemented | Full tenant scoping via TenantContext |
| Workspace Scoping | ✅ Implemented | Via TenantContext.withWorkspace() (DC-17) |

### PostgresEntityStore

| Feature | Status | Notes |
|---------|--------|-------|
| save() | ✅ Implemented | Single entity save with tenant scoping |
| saveBatch() | ✅ Implemented | Batch save with partial success semantics |
| findById() | ✅ Implemented | Entity lookup by ID |
| findByRef() | ✅ Implemented | Collection-scoped entity lookup |
| query() | ✅ Implemented | Query with filters and pagination |
| delete() | ✅ Implemented | Single entity delete |
| deleteBatch() | ⚠️ Deprecated | Uses deleteByRefs() instead (DC-P0-001) |
| deleteByRef() | ✅ Implemented | Collection-scoped delete |
| deleteByRefs() | ✅ Implemented | Batch collection-scoped delete |
| Transactional Semantics | ⚠️ Partial | Batch operations allow partial success (DC-19) |
| Tenant Isolation | ✅ Implemented | Full tenant scoping via TenantContext |
| Workspace Scoping | ✅ Implemented | Via TenantContext.withWorkspace() (DC-17) |

## EventLogStore Implementations

### H2SovereignEventLogStore

| Feature | Status | Notes |
|---------|--------|-------|
| append() | ✅ Implemented | Single event append |
| appendBatch() | ✅ Implemented | Atomic batch append |
| read() | ✅ Implemented | Read from offset with limit |
| readByTimeRange() | ✅ Implemented | Time-range based queries |
| readByType() | ✅ Implemented | Type-based queries |
| getLatestOffset() | ✅ Implemented | Latest offset tracking |
| getEarliestOffset() | ✅ Implemented | Earliest offset tracking |
| tail() | ✅ Implemented | Streaming subscription |
| EventEntry Fields | ✅ Implemented | Full DC-20 fields (correlationId, causationId, source, userId) |
| Tenant Isolation | ✅ Implemented | Full tenant scoping via TenantContext |
| Workspace Scoping | ✅ Implemented | Via TenantContext.withWorkspace() (DC-17) |
| Transactional Semantics | ✅ Implemented | appendBatch is atomic |

### KafkaEventLogStore

| Feature | Status | Notes |
|---------|--------|-------|
| append() | ✅ Implemented | Single event append |
| appendBatch() | ✅ Implemented | Atomic batch append via producer |
| read() | ✅ Implemented | Consumer-based read from offset |
| readByTimeRange() | ⚠️ Limited | Requires timestamp-indexed topics |
| readByType() | ❌ Not Implemented | Would require topic per type or filtering |
| getLatestOffset() | ✅ Implemented | Via consumer offset tracking |
| getEarliestOffset() | ✅ Implemented | Via consumer offset tracking |
| tail() | ✅ Implemented | Native Kafka consumer subscription |
| EventEntry Fields | ⚠️ Partial | Depends on message format configuration |
| Tenant Isolation | ✅ Implemented | Via topic partitioning or separate topics |
| Workspace Scoping | ✅ Implemented | Via topic partitioning (DC-17) |
| Transactional Semantics | ✅ Implemented | appendBatch uses Kafka transactions |
| Durability | ✅ Implemented | Kafka log-based durability |

### InMemoryEventLogStore

| Feature | Status | Notes |
|---------|--------|-------|
| append() | ✅ Implemented | Single event append |
| appendBatch() | ✅ Implemented | Atomic batch append |
| read() | ✅ Implemented | In-memory list iteration |
| readByTimeRange() | ✅ Implemented | Filter by timestamp |
| readByType() | ✅ Implemented | Filter by event type |
| getLatestOffset() | ✅ Implemented | Counter-based offset |
| getEarliestOffset() | ✅ Implemented | Always returns 0 |
| tail() | ✅ Implemented | In-memory subscriber notification |
| EventEntry Fields | ✅ Implemented | Full DC-20 fields |
| Tenant Isolation | ✅ Implemented | In-memory tenant filtering |
| Workspace Scoping | ✅ Implemented | In-memory workspace filtering (DC-17) |
| Transactional Semantics | ✅ Implemented | In-memory atomic operations |
| Durability | ❌ Not Durable | Volatile, for testing only |

### WarmTierEventLogStore

| Feature | Status | Notes |
|---------|--------|-------|
| append() | ✅ Implemented | Delegates to hot tier |
| appendBatch() | ✅ Implemented | Delegates to hot tier |
| read() | ✅ Implemented | Hot tier + warm tier fallback |
| readByTimeRange() | ✅ Implemented | Tier-aware time range query |
| readByType() | ✅ Implemented | Tier-aware type query |
| getLatestOffset() | ✅ Implemented | Hot tier offset |
| getEarliestOffset() | ✅ Implemented | Warm tier offset |
| tail() | ✅ Implemented | Hot tier streaming |
| EventEntry Fields | ✅ Implemented | Depends on tier implementation |
| Tenant Isolation | ✅ Implemented | Delegated to tiers |
| Workspace Scoping | ✅ Implemented | Delegated to tiers (DC-17) |
| Transactional Semantics | ✅ Implemented | Delegated to hot tier |
| Durability | ✅ Implemented | Multi-tier persistence |

### ResilientEventLogStore

| Feature | Status | Notes |
|---------|--------|-------|
| append() | ✅ Implemented | With retry and circuit breaker |
| appendBatch() | ✅ Implemented | With retry and circuit breaker |
| read() | ✅ Implemented | With fallback to secondary |
| readByTimeRange() | ✅ Implemented | With fallback to secondary |
| readByType() | ✅ Implemented | With fallback to secondary |
| getLatestOffset() | ✅ Implemented | Delegated to primary |
| getEarliestOffset() | ✅ Implemented | Delegated to primary |
| tail() | ✅ Implemented | With reconnection handling |
| EventEntry Fields | ✅ Implemented | Depends on delegate |
| Tenant Isolation | ✅ Implemented | Delegated to primary |
| Workspace Scoping | ✅ Implemented | Delegated to primary (DC-17) |
| Transactional Semantics | ✅ Implemented | Delegated to primary |
| Resilience | ✅ Implemented | Circuit breaker, retry, fallback |

## Conformance Summary

### Fully Conformant Providers
- **H2SovereignEntityStore**: Full EntityStore conformance with DC-19 partial semantics
- **H2SovereignEventLogStore**: Full EventLogStore conformance with DC-20 fields
- **PostgresEntityStore**: Full EntityStore conformance with DC-19 partial semantics

### Partially Conformant Providers
- **KafkaEventLogStore**: Missing readByType implementation, limited time-range queries
- **WarmTierEventLogStore**: Conformance depends on delegate tiers
- **ResilientEventLogStore**: Conformance depends on delegate providers

### Testing-Only Providers
- **InMemoryEventLogStore**: Full feature conformance but not durable (testing only)

## Key DC Compliance Notes

- **DC-17**: All providers support workspace scoping via `TenantContext.withWorkspace()`
- **DC-19**: EntityStore batch operations use partial success semantics by default
- **DC-20**: EventLogStore implementations support first-class envelope fields (correlationId, causationId, source, userId)
- **DC-P0-001**: All providers prefer collection-scoped operations (findByRef, deleteByRef) over deprecated ID-based operations

## Future Work

1. **KafkaEventLogStore**: Implement readByType via topic-per-type pattern or message filtering
2. **Transactional EntityStore**: Consider adding optional transactional batch mode for atomic all-or-nothing semantics
3. **Provider Tests**: Expand conformance tests to verify matrix compliance

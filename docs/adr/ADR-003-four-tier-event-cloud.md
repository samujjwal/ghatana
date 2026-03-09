# ADR-003: Four-Tier Event-Cloud Storage with Automatic Lifecycle

**Status:** Accepted  
**Date:** 2026-01-18  
**Decision Makers:** Platform Team, Data-Cloud Team  
**Phase:** 1 — Foundation  

## Context

Event data has varying access patterns over time — recent events are queried frequently and need sub-millisecond latency, while historical events are rarely accessed and can tolerate higher latency. A single storage backend cannot optimize for both cost and performance across all access patterns.

## Decision

Implement a **four-tier storage model** with automatic data lifecycle management:

| Tier | Latency Target | Backend | Retention | Cost |
|------|---------------|---------|-----------|------|
| **HOT** | < 1ms | Redis / Dragonfly (in-memory) | Minutes–Hours | $$$ |
| **WARM** | < 10ms | PostgreSQL (JDBC) | Days–Weeks | $$ |
| **COOL** | < 100ms | Apache Iceberg (data lake) | Months | $ |
| **COLD** | Seconds | S3 / Parquet (archive) | Years | ¢ |

Data flows through tiers automatically: `HOT → WARM → COOL → COLD`. The `StorageTier` enum is used throughout the stack — from Flyway migration DDL constraints to runtime tiering decisions.

The `DataCloudClient` facade presents a unified API — callers specify `tenantId` and `collection`, while the backend transparently queries the appropriate tier(s) based on configured policies.

## Rationale

- **Four tiers** map cleanly to real infrastructure and cost models (in-memory → RDBMS → data lake → object store)
- **Automatic lifecycle** reduces operational burden — no manual data movement
- **SPI-based backends** (`EntityStore`, `EventLogStore`) allow each tier to use optimal storage technology
- The `InMemoryEntityStore` and `InMemoryEventLogStore` serve as both HOT tier and test doubles
- Flyway migrations enforce `CHECK (tier IN ('HOT', 'WARM', 'COOL', 'COLD'))` at the database level

## Consequences

- Cross-tier queries are not supported in v1 — callers get data from the first tier that contains it
- Tier boundaries are time-based — no content-based tiering yet
- The cool/cold tier backends (Iceberg, S3) are stubbed; in-memory fallbacks used until Phase 5.6
- Each product (AEP, data-cloud) manages its own tier policies independently

## Alternatives Considered

1. **Two tiers (hot/cold)** — rejected; too coarse for enterprise event volumes
2. **Five tiers (add FROZEN)** — rejected; diminishing returns, four covers real use cases
3. **Manual tiering** — rejected; error-prone and operationally expensive

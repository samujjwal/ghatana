# Data Cloud Documentation

This file is the canonical Data Cloud documentation index.

## Canonical Documents

| Topic | Document |
| --- | --- |
| Vision and market positioning | `product/01_data_cloud_unified_vision_market_positioning.md` |
| Detailed architecture | `product/02_data_cloud_unified_detailed_architecture.md` |
| High-level design | `product/03_data_cloud_unified_high_level_design.md` |
| Plane architecture | `architecture/PLANE_ARCHITECTURE.md` |
| Operations runbook | `operations/RUNBOOK.md` |
| Schema reference | `SCHEMA_ERD.md` |
| OpenAPI contract | `../contracts/openapi/data-cloud.yaml` |

`CANONICAL_DOCS_INDEX.md` is deprecated and retained only as a compatibility pointer.

## Runtime Implementation Docs

`docs/aep` contains historical integration documents for Action Plane (formerly AEP) persistence and compatibility boundaries. Action Plane is Data Cloud's governed automation runtime; these documents describe integration patterns and boundary contracts.

## Deprecated Documentation

Generated reverse-engineering reports, stale product narratives, and old capability-area documents are not authoritative. If a document repeats or contradicts `architecture/PLANE_ARCHITECTURE.md`, either align it or remove it.

## Storage Profile Behavior

Data Cloud operates in two storage modes controlled by the active Spring/ActiveJ profile:

### Local / In-Memory Profile (`local`, `test`)

- **Entity store**: `InMemoryEntityStore` — data is NOT persisted across restarts.
- **Event log**: `InMemoryEventLogStore` — events are NOT durable. They exist only for the lifetime of the process.
- **Vector index**: disabled or stub.
- **Cache**: Caffeine in-process cache. No Redis. Cache evicts on restart.
- **Use**: developer iteration, integration tests, CI builds.
- **Warning**: the launcher logs a `WARN` banner at startup when running in local/in-memory mode.

### Durable / Sovereign Profile (`sovereign`, `production`)

- **Entity store**: PostgreSQL via HikariCP connection pool, Flyway-managed schema.
- **Event log**: `WarmTierEventLogStore` (RocksDB local) + cold-tier fallback (S3/ClickHouse).
- **Vector index**: OpenSearch or pgvector (configured per deployment).
- **Cache**: Caffeine + Redis (Jedis). Redis is required for multi-instance deployments.
- **Use**: production deployments, sovereign on-premise installations.

| Capability | Local | Sovereign |
| --- | --- | --- |
| Data survives restart | ✗ | ✓ |
| Multi-instance safe | ✗ | ✓ |
| Redis required | ✗ | ✓ |
| PostgreSQL required | ✗ | ✓ |
| AI/embedding | preview stub | real provider |
| Audit log | in-memory | durable DB |

Profile is selected via `SPRING_PROFILES_ACTIVE` or the `--profile` flag on the launcher.

## Vocabulary

Use:

```text
plane
surface
Runtime Truth Registry
plane state
surface state
Action Plane
Action Plane integration boundary
```

Avoid in product/architecture docs:

```text
capability area
capability model
capability registry
Data-Cloud as CEP/EventCloud
AEP as separate product (Action Plane is integrated within Data Cloud)
Data Cloud + AEP as one blurred product
```

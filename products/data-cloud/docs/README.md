# Data Cloud Documentation

This folder contains the required Data Cloud product and architecture documentation. The canonical structure is plane-based.

## Required Canonical Docs

| Document | Purpose |
| --- | --- |
| `architecture/PLANE_ARCHITECTURE.md` | Canonical plane model, target module layout, dependency rules, and migration sequence |
| `product/01_data_cloud_unified_vision_market_positioning.md` | Product vision, market positioning, and plane-based narrative |
| `product/02_data_cloud_unified_detailed_architecture.md` | Detailed plane architecture, contracts, runtime, and enforcement model |
| `product/03_data_cloud_unified_high_level_design.md` | Outcome-first navigation, UX hierarchy, API design, and migration design |
| `audits/end-to-end-product-correctness-audit.md` | Current Data Cloud product correctness audit |
| `migration/AEP_FIRST_PASS_FILE_BY_FILE_MOVE_AND_REFERENCE_PLAN.md` | Historical first-pass migration plan; keep only until the plane migration plan replaces it |
| `SCHEMA_ERD.md` | Database/schema reference |

## Runtime Implementation Docs

`docs/aep` contains Data-Cloud integration documents for AEP persistence and compatibility boundaries. AEP is a separate adaptive event intelligence platform; these documents must not define AEP-owned EventCloud, PatternSpec/EPL, operator-runtime, pattern learning/adaptation, or agent orchestration semantics.

## Deprecated Documentation

Generated reverse-engineering reports, stale product narratives, and old capability-area documents should not be retained as authoritative docs. If a document repeats or contradicts the plane architecture, either update it to defer to `architecture/PLANE_ARCHITECTURE.md` or remove it.

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
AEP integration boundary
AEP as separate adaptive event intelligence platform
```

Avoid in product/architecture docs:

```text
capability area
capability model
capability registry
Data-Cloud as CEP/EventCloud
AEP as Data-Cloud internals
Data Cloud + AEP as one blurred product
```

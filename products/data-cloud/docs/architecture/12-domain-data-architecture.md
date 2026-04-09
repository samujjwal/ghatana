# Domain and Data Architecture

See [07-shared-library-architecture.md](07-shared-library-architecture.md), [`diagrams/domain-relationships.mmd`](diagrams/domain-relationships.mmd), and [`diagrams/data-flow.mmd`](diagrams/data-flow.mmd).

## Core Domain Objects

| Domain Area | Canonical Types | Persistence Evidence | Notes |
|---|---|---|---|
| Collection metadata | `MetaCollection`, `MetaField`, `CollectionRepository` | `V004__create_collections_metadata.sql`, JPA annotations in `MetaCollection.java` | dynamic schema, RBAC, storage profile |
| Generic entities | `platform-entity` entity models plus `spi.EntityStore` records | `V002__create_entities_table.sql`, `V006` and `V008` follow-up migrations | logical entity data and metadata |
| Events | `platform-event/**`, `spi.EventLogStore.EventEntry` | `V001__create_events_table.sql`, `V005__create_event_log.sql` | append-only/event-log centric |
| Time-series / analytics | analytics services and storage adapters | `V003__create_timeseries_table.sql`, ClickHouse integrations | analytical views |
| Relationships / graph edges | entity relation support | `V010__create_entity_relations.sql` | lineage / dependency modeling |
| Agent registry state | agent descriptor/config maps, release/rollout/evaluation repositories | `V013` through `V017` migrations | AEP persistence backend |
| Media / artifact state | media artifact models | `V018__create_media_artifacts.sql` | product media evidence / assets |

## Implemented

- `platform-entity/src/main/java/com/ghatana/datacloud/entity/MetaCollection.java` is a rich JPA entity with tenant scoping, JSONB permission maps, application-specific views, validation schema, storage profile, physical mappings, schema versioning, lifecycle timestamps, and child fields.
- `CollectionRepository.java` exposes Promise-based repository operations and forces tenant-scoped access patterns.
- Flyway migrations under `platform-launcher/src/main/resources/db/migration` show the persisted model history:
  - `V001` events
  - `V002` entities
  - `V003` timeseries
  - `V004` collection metadata
  - `V010` entity relations
  - `V011` database-level tenant isolation
  - `V013` to `V018` agent, evaluation, memory, promotion, and media tables

## Inferred

- The intended domain model is “logical collection + generic entity + append-only events + optional analytical projections,” with plugins selecting physical storage placement.
- Collection metadata is doing more than schema description. It also captures permissions, app views, storage routing, and lifecycle concerns, which makes it close to the system’s aggregate root for multi-tenant datasets.

## Missing

- There is no single canonical ERD or ownership map checked into the product prior to this documentation set.
- Transaction boundary documentation across entity, event, analytics, and registry writes is not explicit.

## Recommended

- Keep `MetaCollection` as the canonical metadata owner, but separate permission, storage policy, and UI-view concerns if the model keeps growing.

## Data Ownership

| Data Class | Owner | Access Paths | Risks |
|---|---|---|---|
| Collection metadata | `platform-entity` + runtime repos | HTTP entity/collection operations, config loading | overgrown aggregate |
| Entity payloads | `spi.EntityStore` and runtime stores | generic entity APIs, search/export/validation | duplicated DTO views |
| Event log | `spi.EventLogStore` | HTTP, gRPC, workers | multiple representations |
| Feature vectors | AI integration / feature store service | `/api/v1/features/**`, ingest worker | async lag from source events |
| Agent registry records | `agent-registry` provider | AEP/provider calls | upstream/downstream boundary confusion |

## Duplicate Models Across Layers

| Concern | Evidence | Impact |
|---|---|---|
| Collection/entity models | `platform-entity`, `spi`, UI types in `ui/src/lib/api/collections.ts` | repeated transformations |
| Event models | `platform-event`, `spi.EventEntry`, gRPC service DTOs | contract drift risk |
| Client-facing collection contracts | `ui/src/api/client.ts` and `ui/src/services/collections-impl.ts` | stale browser expectations |

## Consistency and Lifecycle Rules

### Implemented

- Tenant ID is a first-class constraint in repositories and security filters.
- `MetaCollection` uses soft-active semantics (`active` flag) and timestamps.
- Feature store state is asynchronously derived from events.
- Agent registry cache and durable persistence intentionally have different roles.

### Inferred

- Full strong consistency across all storage engines is not the design goal. The architecture is closer to “primary store plus derived stores and async projections.”

### Missing

- Storage profile semantics are embedded in code/comments rather than documented as a maintained contract for operators or developers.

## Domain Findings

| Finding | Evidence | Impact |
|---|---|---|
| Domain ownership is fairly rich but not centrally narrated | `MetaCollection.java`, migrations, repos | onboarding cost |
| Data model scope has expanded from generic entities into agent/runtime concerns | `V013`-`V018` migrations | product boundary complexity |
| Duplicate model layers increase mapping cost | SPI vs JPA vs transport vs UI | more drift surfaces |

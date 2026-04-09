# Shared Library Architecture

See [`diagrams/shared-library-consumers.mmd`](diagrams/shared-library-consumers.mmd) and [`diagrams/module-dependencies.mmd`](diagrams/module-dependencies.mmd).

## Shared Library Inventory

| Library | Purpose | Public API Surface | Main Consumers | Reuse Level |
|---|---|---|---|---|
| `spi` | External/public contract layer | `EntityStore`, `EventLogStore`, tenant context, plugin/storage capabilities | AEP, YAPPC, runtime modules | high |
| `platform-entity` | Data Cloud domain and persistence model library | entity, collection, workflow, schema models and repo ports | SPI, runtime, API, analytics | high |
| `platform-event` | Event-domain library | event models, replay/durability, event plugin SPIs | runtime, plugins, analytics | medium-high |
| `platform-config` | Config pipeline library | YAML loading, compilation, registry, reload, health adapters | runtime, startup validation | medium-high |
| `platform-analytics` | Query/report/anomaly library | query engine, reports, export, anomaly detector | runtime/HTTP analytics flows | medium |
| `platform-api` | Semi-shared API/app-service layer | DTOs, controllers, GraphQL mutation class | launcher build and tests | medium but partial |
| `platform-launcher` | Runtime mega-module | DataCloud factory, infra adapters, DI, gRPC, storage, distributed/embedded code | launcher, agent-registry, feature-store-ingest | high but overloaded |
| `platform-plugins` | Shared plugin implementations | Kafka/Redis/S3/Iceberg/vector/etc. plugins | runtime | medium |
| `platform-client` | Planned shared client layer | build metadata only right now | intended external consumers | low |

## Consumer-To-Library Mapping

| Consumer | Libraries Used |
|---|---|
| `launcher` | `platform-launcher`, `platform-api`, platform java libs |
| `feature-store-ingest` | `spi`, `platform-launcher`, platform java libs |
| `agent-registry` | `platform-launcher`, `agent-core` |
| `ui` | shared frontend packages plus product-local API layers |
| Other products | mostly `spi`, some `platform-launcher` or `agent-registry` references via root build graph |

## Overlap Findings Matrix

| Overlap | Evidence | Assessment |
|---|---|---|
| `platform-api` vs `launcher/http` | both represent HTTP/API concerns | partial migration, duplicate transport layer |
| `platform-client` vs `sdk` vs `DataCloud.java` | three separate client-facing stories | unclear canonical client strategy |
| `platform-event` event models vs `spi.EventLogStore.EventEntry` vs `EventRecord` | multiple event representations | model duplication |
| `platform-entity.Entity` vs `spi.EntityStore.Entity` vs UI entity payload models | multiple entity representations | transformation overhead and drift risk |
| UI `src/api/*` vs `src/lib/api/*` vs `src/services/*` | several client abstractions | frontend shared-layer misuse |

## Duplicated Utility And Infra Dependencies

| Category | Evidence | Observation |
|---|---|---|
| Redis clients | `platform-launcher` and `platform-plugins` include `jedis` and `lettuce` | duplicate client stacks for similar concern |
| Search clients | OpenSearch Java + REST client both present | may be required by SDK/runtime, but increases surface |
| Storage engines | H2, SQLite, RocksDB, PostgreSQL, Redis, ClickHouse, Kafka, S3/Ceph | broad runtime matrix concentrated in one product |
| HTTP/API layers | ActiveJ HTTP in launcher plus controller classes in `platform-api` | partial split |

## Truly Shared Vs Feature-Specific

**Implemented**
- `spi`, `platform-entity`, `platform-event`, and `platform-config` are legitimate shared libraries.

**Inferred**
- `platform-api` and `platform-client` are still extraction seams, not stable shared libraries.

**Recommended**
- Keep only modules with real source ownership and actual consumers in the “shared library” category.
- Mark placeholder extraction targets explicitly in readmes/build descriptions until they contain sources.

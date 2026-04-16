# Data Cloud

Data Cloud is the data foundation product for Ghatana. It owns entity storage, event persistence, analytics, governance, lineage, agent memory persistence, plugin-backed pipeline execution, and the HTTP surface other products consume. It does not own broader agentic orchestration; AEP integrates through Data Cloud contracts and events for cross-product agent workflows.

## Current State

| Capability | Status | Notes |
| --- | --- | --- |
| Entity CRUD, batch, history, export, validation | Ready | Active in the launcher HTTP surface |
| Event append/query | Ready | Includes offset lookup and streaming support |
| Pipelines, checkpoints, and execution state | Ready | CRUD plus plugin-backed execution and execution logs are active |
| Agent memory persistence | Ready | TTL-aware memory APIs are live |
| Context layer | Ready | Includes snapshot support and RAG endpoint |
| Lineage API | Ready | Collection lineage and impact endpoints are live |
| Data products API | Ready | Publish, discover, and subscribe shipped in the launcher |
| Semantic similarity and RAG | Ready | Auto-index on entity writes, `/similar`, and `/rag` shipped |
| SDK generation | Ready | Java, TypeScript, and Python SDKs generated from OpenAPI |
| Analytics, reports, AI models, feature store | Ready with optional dependencies | Some routes degrade when backing services are absent |
| AI assist, voice, learning, plugin lifecycle | Beta | Available, with runtime plugin hot-swap and quality dependent on optional services |
| Sovereign durable profile | Ready | `DATACLOUD_PROFILE=sovereign` runs file-backed H2 entity and event storage with restart persistence |
| Auto-compaction for embedded durable storage | Ready | Tombstone-based compaction runs on a configurable schedule and can be gated via autonomy |

## Architecture

```text
Clients and products
    -> Data Cloud HTTP API and plugin runtime
    -> entities, events, analytics, governance, lineage, memory, workflow execution
    -> event-backed integration with AEP for broader agent orchestration
```

Operational guidance for validated deployment paths lives in `RUNBOOK.md`.

## Key Modules

| Module | Purpose |
| --- | --- |
| `spi/` | Stable client and plugin contracts |
| `platform-entity/` | Entity domain types and storage contracts |
| `platform-event/` | Event-log primitives |
| `platform-analytics/` | Query and reporting services |
| `platform-plugins/` | Plugin implementations including lineage and vector search |
| `platform-launcher/` | Runtime composition and embedded services |
| `launcher/` | ActiveJ HTTP server and transport handlers |
| `sdk/` | Generated Java, TypeScript, and Python clients |
| `ui/` | Product UI |

## Quick Start

Local profile is the honest zero-dependency entry point today. It is suitable for development and manual API validation.

Prerequisites:

- Java 21
- `pnpm` if you want to validate generated TypeScript SDK output

Run the server:

```bash
DATACLOUD_PROFILE=local \
DATACLOUD_HTTP_ENABLED=true \
./gradlew :products:data-cloud:launcher:run
```

The default HTTP port is `8082`.

Create an entity:

```bash
curl -sS -X POST http://localhost:8082/api/v1/entities/tickets \
    -H 'Content-Type: application/json' \
    -H 'X-Tenant-ID: local-dev' \
    -d '{"title":"login failure","summary":"password reset token expired"}'
```

Query similar entities after a few writes:

```bash
curl -sS 'http://localhost:8082/api/v1/entities/tickets/similar?id=<entity-id>&k=5' \
    -H 'X-Tenant-ID: local-dev'
```

Publish a collection as a data product:

```bash
curl -sS -X POST http://localhost:8082/api/v1/data-products \
    -H 'Content-Type: application/json' \
    -H 'X-Tenant-ID: local-dev' \
    -d '{
        "name":"Support Tickets",
        "collection":"tickets",
        "description":"Ticket search and support analytics",
        "sla":{"freshnessSeconds":600,"completenessTarget":0.95}
    }'
```

## Deployment Modes

| Mode | Status | Notes |
| --- | --- | --- |
| `local` | Ready | In-process development profile, no external services required |
| Standard standalone | Ready | HTTP server plus optional external services and plugins |
| Kubernetes and Helm | Available | Deployment assets exist in `k8s/` and `helm/` |
| `sovereign` | Ready | File-backed single-binary air-gapped profile with embedded H2 entity and event storage |

Important local-profile caveats:

- Data is not durable across restarts in `local`; use `sovereign` for file-backed embedded durability.
- Federated query and tier migration degrade when external services are absent.
- Semantic search currently uses the in-process vector plugin initialized by the launcher.

## Configuration Highlights

| Variable | Purpose |
| --- | --- |
| `DATACLOUD_PROFILE` | Runtime profile, currently `local` is the supported zero-dependency mode |
| `DATACLOUD_SOVEREIGN_DATA_DIR` | Base directory for sovereign embedded H2 data files |
| `DATACLOUD_COMPACTION_INTERVAL_SECONDS` | Sovereign storage compaction interval |
| `DATACLOUD_COMPACTION_TOMBSTONE_THRESHOLD` | Tombstone count that triggers sovereign compaction |
| `DATACLOUD_HTTP_ENABLED` | Enables the HTTP server |
| `DATACLOUD_CORS_ALLOWED_ORIGINS` | Overrides default CORS origin |
| `DATACLOUD_RATE_LIMIT_REQUESTS` | Per-window request cap |
| `DATACLOUD_RATE_LIMIT_WINDOW_SECONDS` | Rate-limit window size |
| `OPENAI_API_KEY` or `OLLAMA_HOST` | Optional AI assist backing services |
| `TRINO_URL` | Optional federated query endpoint |

## Contracts And SDKs

- Canonical REST contract: `products/data-cloud/api/openapi.yaml`
- Human-readable API companion: `products/data-cloud/REST_API_DOCUMENTATION.md`
- Generate SDKs: `./gradlew :products:data-cloud:sdk:build`
- Generated outputs land under `products/data-cloud/sdk/build/generated/sdk`

## Validation

Useful commands during development:

```bash
./gradlew :products:data-cloud:launcher:test
./gradlew :products:data-cloud:sdk:build
./gradlew :products:data-cloud:build
```

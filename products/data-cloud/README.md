# Data Cloud

Data Cloud is the data foundation product for Ghatana. It owns entity storage, event persistence, analytics, governance, lineage, agent memory persistence, plugin-backed pipeline execution, and the HTTP surface other products consume. It does not own broader agentic orchestration; AEP integrates through Data Cloud contracts and events for cross-product agent workflows.

## Current State

| Capability | Implementation state | Evidence level | Notes |
| --- | --- | --- | --- |
| Entity CRUD, batch, history, export, validation | Implemented | Deployment-validated | Active in the launcher HTTP surface and covered by launcher tests plus local runbooks |
| Event append/query | Implemented | Deployment-validated | Includes offset lookup and streaming support in the launcher runtime |
| Pipeline metadata and checkpoints | Implemented | Verified in integration | CRUD and checkpoint storage are active in the launcher HTTP surface |
| Workflow execution and logs | Implemented | Verified in integration | Execution snapshots and logs persist through Data Cloud storage with real provider integration tests |
| Agent memory persistence | Implemented | Verified locally | TTL-aware memory APIs are live |
| Context layer | Implemented | Verified in integration | Includes snapshot support and RAG endpoint |
| Lineage API | Implemented | Verified in integration | Collection lineage and impact endpoints are live |
| Data products API | Implemented | Verified in integration | Publish, discover, and subscribe ship in the launcher |
| Semantic similarity and RAG | Implemented | Verified in integration | Auto-index on entity writes plus `/similar` and `/rag` routes |
| SDK generation | Implemented | Verified locally | Java, TypeScript, and Python SDKs generate from OpenAPI during the SDK build |
| Analytics, reports, AI models, feature store | Implemented | Verified locally | Some routes degrade when backing services are absent; inspect `/api/v1/capabilities` for runtime truth |
| AI assist, voice, learning, plugin lifecycle | Implemented | Verified locally | Optional-service quality and availability depend on runtime capability registration |
| Data fabric topology visualization | Preview | UI demo only | Uses hardcoded demo metrics; real fabric metrics API in development |
| Sovereign durable profile | Implemented | Verified in integration | `DATACLOUD_PROFILE=sovereign` runs file-backed H2 entity and event storage with restart persistence |
| Auto-compaction for embedded durable storage | Implemented | Verified in integration | Tombstone-based compaction runs on a configurable schedule and can be gated via autonomy |

Evidence references:

- Integration-backed workflow durability and runtime execution: `products/data-cloud/integration-tests` and `products/data-cloud/launcher` tests
- Capability registry truth surface: `GET /api/v1/capabilities` with operator UI reflection in `products/data-cloud/ui`
- Durable governance, purge, redaction, and audit evidence: `products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/DataCloudHttpServerGovernanceTest.java`
- Tenant-isolation provider evidence: `products/data-cloud/IMPLEMENTATION_PLAN_2026-04-13.md` entries for `P2.10.2` and related provider tests

## Architecture

```text
Clients and products
    -> Data Cloud HTTP API and plugin runtime
    -> entities, events, analytics, governance, lineage, memory, workflow execution
    -> event-backed integration with AEP for broader agent orchestration
```

Operational guidance for validated deployment paths lives in `RUNBOOK.md`.

## Workflow Capability Tiers

| Tier | Implementation state | Evidence level | What it means today |
| --- | --- | --- | --- |
| Pipeline metadata CRUD | Implemented | Verified locally | Pipeline definitions and checkpoints are stored and served over the launcher HTTP API |
| Runtime execution | Implemented | Verified in integration | `POST /api/v1/pipelines/:id/execute` persists execution snapshots and logs so detail and history survive process restarts when the selected Data Cloud storage is durable |
| Durable orchestration | Limited | Not deployment-validated | The launcher currently runs a single-process workflow plugin; it is not advertised as a distributed scheduler or multi-worker orchestration plane |

## UI Surface Truth

The Data Cloud UI is intentionally narrower than the full backend contract set. Treat the launcher-backed routes below as the source of truth when evaluating product readiness.

| Surface | Runtime truth | Notes |
| --- | --- | --- |
| Intelligent Hub | Live primary launcher | Outcome-first entry point for query, workflow, trust, and operator journeys |
| SQL Workspace | Live primary launcher | Uses `/api/v1/analytics/suggest`, `/analytics/query`, `/analytics/explain`, and optional federated execution when capability registry marks it active |
| Smart Workflow Builder | Live with governed review | Intent-to-draft generation, confidence/fallback metadata, review messaging, and persistence into the canonical pipelines surface are launcher-backed |
| Workflows | Live for CRUD and execution visibility | Pipeline CRUD, execution history, and optimisation hints are launcher-backed; distributed orchestration is still out of scope |
| Data Fabric | Preview/demo only | Uses hardcoded demo metrics; real fabric metrics API in development |
| Trust Center | Partial but action-backed | Retention classification, purge preview, redaction, compliance refresh, audit visibility, derived operator recommendations, and explicit lifecycle-truth cards are wired; broader policy CRUD lifecycle remains incomplete |
| Alerts | Live operator triage surface | Alert list, acknowledge, resolve, grouping, suggestions, rules, and SSE stream are launcher-backed; the route remains operator-facing rather than primary-user-facing |

## Role-Aware Shell Disclosure

The UI now supports progressive disclosure through an explicit shell-role mode in the header.

| Shell role | Default emphasis | Revealed surfaces |
| --- | --- | --- |
| Primary user | Query, data exploration, and workflow launchers | Home, Data, Pipelines, Query |
| Operator | Runtime investigation and trust workflows | Adds Insights, Trust, and Events |
| Admin | Full shell for operational and configuration work | Adds Settings on top of operator surfaces |

This mode controls shell density and launcher emphasis only. It does not replace backend authorization.

The broad `ui/src/api/client.ts` surface and deprecated-route mocks are compatibility boundaries only. New page and test work should bind to canonical launcher-backed adapters first.

## Validated Product Journeys

Use this section as the current reality map for what works today.

### Working journeys

- Create and manage collections through the launcher-backed entity APIs.
- Run direct analytics queries from the SQL Workspace and receive live NLQ suggestion templates.
- Execute federated queries when the capability registry reports Trino-backed execution as active.
- Create, inspect, and execute pipelines with persisted execution snapshots and launcher-backed optimisation hints.
- Review retention classification, purge previews, PII redaction, compliance posture, lifecycle truth, and audit activity in Trust Center.

### Partial journeys

- Smart Workflow Builder now generates launcher-backed pipeline drafts with confidence and fallback metadata, then persists accepted drafts through the canonical pipelines API.
- SQL natural-language assistance now infers likely scope, recommends an execution path, and exposes explain-plan guardrails before execution, but clarification prompts for low-confidence intents are still limited.
- The home launcher routes intent into query, workflow, trust, and operator flows, and progressive disclosure is now enforced across the sidebar, header mode switcher, and global search.

### Operator-only journeys

- Capability truth, runtime diagnostics, degraded optional dependencies, and launcher-process AI fallback/confidence telemetry are surfaced in Insights for operators.
- Operator-focused surfaces such as Alerts remain intentionally disclosed outside the default primary-user navigation even though the alert lifecycle is now live.

### Roadmap journeys

- Real fabric metrics API for Data Fabric (currently uses demo data)
- Broader build-graph and browser-level evidence hardening for the now-live workflow-draft and alerts journeys.
- Broader policy CRUD lifecycle beyond the currently wired governance actions.

## Manuals

Use the manual set below depending on your goal:

| Document | Audience | Purpose |
| --- | --- | --- |
| `DEVELOPER_MANUAL.md` | developers and integrators | local setup, module map, API-first development, SDK and UI workflows |
| `TEST_MANUAL.md` | engineers and QA | test layers, commands, durable load verification, contract checks |
| `USER_MANUAL.md` | operators, analysts, and consumers | UI navigation, common workflows, API usage recipes |
| `RUNBOOK.md` | operators and SREs | deployment, durability, recovery, and operational checks |
| `REST_API_DOCUMENTATION.md` | API consumers | human-readable route inventory |

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
./gradlew :products:data-cloud:launcher:runLauncher
```

Run backend and UI together with one command:

```bash
bash products/data-cloud/scripts/run-local-stack.sh
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

| Mode | Implementation state | Evidence level | Notes |
| --- | --- | --- | --- |
| `local` | Implemented | Verified locally | In-process development profile, no external services required |
| Standard standalone | Implemented | Verified locally | HTTP server plus optional external services and plugins |
| Kubernetes and Helm | Documented | Documentation-backed | Deployment assets exist in `k8s/` and `helm/`; treat them as deployment artifacts until validated in your environment |
| `sovereign` | Implemented | Verified in integration | File-backed single-binary air-gapped profile with embedded H2 entity and event storage |

Important local-profile caveats:

- Data is not durable across restarts in `local`; use `sovereign` for file-backed embedded durability.
- Workflow execution history is only restart-safe when the selected Data Cloud storage is itself durable.
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

For richer day-to-day guidance, use `DEVELOPER_MANUAL.md` and `TEST_MANUAL.md` instead of treating this README as the only source of setup and verification steps.

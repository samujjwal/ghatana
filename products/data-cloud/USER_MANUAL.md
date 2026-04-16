# Data Cloud User Manual

This manual explains how operators, analysts, and product teams use Data Cloud today through the UI and API.

It is intentionally practical. For low-level route details, use `REST_API_DOCUMENTATION.md` and `api/openapi.yaml`. For deployment and recovery, use `RUNBOOK.md`.

## 1. Who This Manual Is For

Use this guide if you are:

- exploring datasets and entities
- operating ingestion and workflow pipelines
- using SQL, lineage, governance, or insights features
- publishing and subscribing to data products
- using Data Cloud as the persistence and context layer behind other products

## 2. Core Concepts

### Tenant

Data Cloud is tenant-scoped. Most API calls use the `X-Tenant-ID` header, and UI behavior should be understood in the context of the current tenant.

### Collection

A collection is the primary logical container for entity records.

Examples:

- `tickets`
- `customers`
- `orders`

### Entity

An entity is a single record in a collection. Entities can be created, queried, validated, exported, versioned, and searched semantically.

### Event

Events capture append-only changes or domain activity. They support audit, replay, streaming, and downstream processing.

### Pipeline

A pipeline defines an executable workflow. Pipelines can be listed, created, updated, executed, monitored, and cancelled.

### Data Product

A data product is a published dataset descriptor that other teams can discover and subscribe to.

### Memory And Context

Data Cloud stores agent memory, runtime context, and grounded retrieval data for AI-assisted workflows.

## 3. Starting Data Cloud Locally

If you want to use Data Cloud locally without external infrastructure:

```bash
DATACLOUD_PROFILE=local \
DATACLOUD_HTTP_ENABLED=true \
./gradlew :products:data-cloud:launcher:runLauncher
```

Default API endpoint:

- `http://localhost:8082`

If you also want the UI:

```bash
pnpm --dir products/data-cloud/ui install
pnpm --dir products/data-cloud/ui dev
```

## 4. Main UI Areas

The current primary route model is defined in `ui/src/routes.tsx`.

### Intelligent Hub

Routes:

- `/`
- `/dashboard`
- `/hub`

Use this as the home screen for a high-level operational overview.

### Data Explorer

Routes:

- `/data`
- `/data/:id`
- `/data/:id/:view`
- legacy: `/collections`, `/datasets`, `/lineage`, `/quality`

Use this area to browse datasets or collections and move into more detailed views.

### Pipelines

Routes:

- `/pipelines`
- `/pipelines/new`
- `/pipelines/:id`
- `/pipelines/:id/edit`
- legacy: `/workflows`, `/workflows/new`, `/workflows/:id`

Use this area to create, edit, and monitor workflow pipelines.

### SQL Workspace

Routes:

- `/query`
- legacy: `/sql`

Use this area to author and run query workflows.

### Trust Center

Routes:

- `/trust`
- legacy: `/governance`

Use this for governance, compliance, and privacy-oriented views.

### Insights

Routes:

- `/insights`
- legacy: `/brain`, `/dashboards`, `/cost`

Use this for analytics and higher-level operational insights.

### Additional operational pages

Routes:

- `/events` for event exploration
- `/entities` for entity browsing
- `/memory` for memory-plane inspection
- `/fabric` for data-fabric views
- `/agents` for agent and plugin management surfaces
- `/plugins` and `/plugins/:id` for plugin lifecycle pages
- `/alerts` and `/settings`

## 5. Common User Workflows

### Workflow A: Create and inspect a collection through the API

1. Start the backend locally.
2. Create entities in a collection.
3. Query the collection.
4. Inspect history, search, or export when needed.

Example create request:

```bash
curl -sS -X POST http://localhost:8082/api/v1/entities/tickets \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-ID: local-dev' \
  -d '{"title":"login failure investigation","summary":"password reset token expired"}'
```

Example read request:

```bash
curl -sS 'http://localhost:8082/api/v1/entities/tickets' \
  -H 'X-Tenant-ID: local-dev'
```

Use this flow when onboarding a new operational domain onto Data Cloud.

### Workflow B: Explore similar records

After creating a few records, use semantic similarity search:

```bash
curl -sS 'http://localhost:8082/api/v1/entities/tickets/similar?id=<entity-id>&k=5' \
  -H 'X-Tenant-ID: local-dev'
```

Use this when searching for related tickets, similar incidents, or semantically close records.

### Workflow C: Append and inspect events

Append an event:

```bash
curl -sS -X POST http://localhost:8082/api/v1/events \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-ID: local-dev' \
  -d '{"type":"ticket.created","payload":{"ticketId":"123"}}'
```

Query events:

```bash
curl -sS 'http://localhost:8082/api/v1/events' \
  -H 'X-Tenant-ID: local-dev'
```

Use `/events` in the UI when you want a browsing-first view instead of raw API responses.

### Workflow D: Publish a data product

Use this when a collection is ready to be shared with other teams or downstream tools.

```bash
curl -sS -X POST http://localhost:8082/api/v1/data-products \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-ID: local-dev' \
  -d '{
    "name":"Support Tickets",
    "collection":"tickets",
    "description":"Support search and analytics",
    "sla":{"freshnessSeconds":600,"completenessTarget":0.95}
  }'
```

Then list published data products:

```bash
curl -sS 'http://localhost:8082/api/v1/data-products' \
  -H 'X-Tenant-ID: local-dev'
```

### Workflow E: Grounded retrieval for AI help

Use the context-layer RAG endpoint when you want an answer grounded in collection content.

```bash
curl -sS -X POST http://localhost:8082/api/v1/context/tickets/rag \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-ID: local-dev' \
  -d '{
    "question":"How should I troubleshoot an expired password reset token?",
    "k":3
  }'
```

Use this when Data Cloud is serving as the context layer for AI-assisted operations.

### Workflow F: Manage pipelines

Use the `/pipelines` UI area to:

- review current workflows
- create a new workflow
- edit a workflow design
- inspect execution history
- cancel a running execution when needed

API surfaces behind this include:

- `GET /api/v1/pipelines`
- `POST /api/v1/pipelines`
- `POST /api/v1/pipelines/:pipelineId/execute`
- `GET /api/v1/pipelines/:pipelineId/executions`

## 6. What Works Today Versus What Is Still Emerging

These capabilities are active and usable now:

- entity CRUD, batch operations, validation, history, export
- event append and query
- pipelines, checkpoints, execution tracking
- memory persistence
- context and RAG endpoints
- lineage APIs
- data product publishing and subscribe endpoints
- semantic similarity search

Some features exist but depend on optional or stronger runtime backing services:

- advanced analytics and reports
- AI model routes
- feature-store-backed workflows
- AI assist quality and plugin hot-swap behavior

Some UI page specs in `ui/docs/web-page-specs/` are design targets rather than fully wired pages. When in doubt, trust the actual routes in `ui/src/routes.tsx` and the live API contract.

## 7. Best Practices For Users

- always use the correct tenant context
- start with one narrow collection or workflow instead of a broad migration
- publish data products only after the underlying collection and SLA are stable
- use semantic search and RAG to accelerate exploration, but validate the returned context before taking action
- use the Trust Center and Insights areas for governance and operational visibility, not as substitutes for core CRUD and query workflows

## 8. Troubleshooting

### I can start the backend, but data disappears after restart

You are probably using `DATACLOUD_PROFILE=local`. Use `sovereign` if you need file-backed restart persistence.

### Similarity search or RAG is not useful

Possible causes:

- too little source data has been written
- optional backing services are not configured
- the current profile is using the minimal local setup

### I cannot find a page described in a spec document

Check `ui/src/routes.tsx`. Some page specs under `ui/docs/web-page-specs/` are intentionally forward-looking.

### My API request returns no data

Check:

- the `X-Tenant-ID` header
- the collection name
- whether the item was created in the same profile and environment you are querying

## 9. Where To Go Next

- `DEVELOPER_MANUAL.md` if you are implementing or integrating Data Cloud
- `TEST_MANUAL.md` if you are validating a change or deployment
- `REST_API_DOCUMENTATION.md` for the route inventory
- `api/openapi.yaml` for the contract and SDK generation source
- `RUNBOOK.md` for operational deployment, recovery, and durable-mode guidance
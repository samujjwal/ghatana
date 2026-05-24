# Data Cloud Developer Manual

This manual is the practical developer guide for working on and integrating with Data Cloud in this repository. It complements, but does not replace, the contract and operations docs:

- `README.md` for the product overview
- `docs/api/REST_API_DOCUMENTATION.md` for the human-readable API inventory
- `contracts/openapi/data-cloud.yaml` for the canonical REST contract
- `docs/operations/RUNBOOK.md` for validated deployment and recovery paths

## 1. What Data Cloud Owns

Data Cloud is Ghatana's data foundation product. It owns:

- entity storage and query
- storage-plane event append, replay, and audit records
- execution metadata and checkpoint persistence when called through public contracts or stable SPI
- lineage, governance, and context APIs
- agent memory persistence and plugin-backed storage extensions
- generated SDKs for downstream consumers
- a React UI for operators and data users

Data Cloud does not own higher-level agent orchestration, complex event processing, EventCloud semantics, PatternSpec/EPL, pattern learning/adaptation, or predictive/recommended pattern lifecycle. AEP integrates with Data Cloud through contracts, storage-plane events, persistence APIs, and stable storage SPI.

> **Canonical boundary (2026):** Data-Cloud is the governed data/storage substrate. AEP is the adaptive event intelligence platform. EventCloud and adaptive event semantics belong to AEP. Data-Cloud may provide EventCloud persistence plugins, but those plugins must not expose CEP, PatternSpec, operator-runtime, or learning semantics.

## 2. Repository Map

Use these modules as the main starting points:

| Path | Purpose |
| --- | --- |
| `delivery/launcher/` | ActiveJ HTTP server, route wiring, transport handlers |
| `delivery/runtime-composition/` | runtime composition, profiles, embedded services |
| `planes/data/entity/` | entity contracts, schema, and storage-facing types |
| `planes/event/core/` | storage-plane event-log abstractions and persistence contracts |
| `planes/intelligence/analytics/` | analytics and reporting services |
| `extensions/plugins/` | pluggable providers, lineage, vector search, durable stores, and optional AEP storage adapters |
| `planes/shared-spi/` | stable SPI and client-facing contracts |
| `delivery/sdk/` | generated Java, TypeScript, and Python SDKs |
| `delivery/ui/` | React application |
| `planes/intelligence/feature-ingest/` | real-time feature ingestion service |
| `scripts/` | smoke, load, verification, and recovery helpers |
| `deploy/terraform/`, `deploy/helm/`, `deploy/k8s/` | deployment assets |

## 3. Prerequisites

For normal development:

- Java 21
- `pnpm`
- Docker Desktop or compatible container runtime for Testcontainers-backed tests
- `node` for frontend and script validation

For infrastructure and deployment work:

- Terraform 1.7+
- Kubernetes tooling such as `helm` and `kubectl`
- AWS CLI if you are using the Terraform deployment path

## 4. Local Development Profiles

### `DATACLOUD_PROFILE=local`

Use this for the fastest inner loop.

- in-memory entity and event stores
- suitable for manual API validation and UI integration work
- not durable across restart

Run the server:

```bash
DATACLOUD_PROFILE=local \
DATACLOUD_HTTP_ENABLED=true \
./gradlew :products:data-cloud:delivery:launcher:runLauncher
```

Default HTTP port: `8082`

### `DATACLOUD_PROFILE=sovereign`

Use this when you need embedded durability without external services.

```bash
DATACLOUD_PROFILE=sovereign \
DATACLOUD_HTTP_ENABLED=true \
DATACLOUD_SOVEREIGN_DATA_DIR=$PWD/.data/datacloud \
./gradlew :products:data-cloud:delivery:launcher:run
```

Use sovereign mode when you want restart persistence in a single-machine or air-gapped setup.

### Staging or production-like profiles

Use these only when the required backing services are actually configured.

Typical variables:

- `DATACLOUD_DB_URL`
- `DATACLOUD_DB_USER`
- `DATACLOUD_DB_PASSWORD`
- `DATACLOUD_KAFKA_BOOTSTRAP_SERVERS`
- `OPENAI_API_KEY` or `OLLAMA_HOST`
- `TRINO_URL`

See `docs/operations/RUNBOOK.md` for the durable provider expectations and failure signatures.

## 5. Running the Main Surfaces

### Backend server

```bash
DATACLOUD_PROFILE=local \
DATACLOUD_HTTP_ENABLED=true \
./gradlew :products:data-cloud:delivery:launcher:run
```

### Backend and UI together

```bash
bash products/data-cloud/scripts/run-local-stack.sh
```

This launcher starts the backend on `http://localhost:8082`, starts the Vite UI on `http://localhost:5173`, points the UI at the live backend, disables browser MSW for that session, and stops both processes on `Ctrl+C`.

### Frontend UI

```bash
pnpm --dir products/data-cloud/delivery/ui install
pnpm --dir products/data-cloud/delivery/ui dev
```

Use the UI against a local backend by pointing it at the running Data Cloud API endpoint configured for your local frontend environment.

### SDK generation

```bash
./gradlew :products:data-cloud:delivery:sdk:build
```

Generated outputs are written under:

- `products/data-cloud/delivery/sdk/build/generated/sdk/java`
- `products/data-cloud/delivery/sdk/build/generated/sdk/typescript`
- `products/data-cloud/delivery/sdk/build/generated/sdk/python`

## 6. Core Developer Workflows

### Add or update an HTTP endpoint

1. Update the route and handler wiring in `delivery/launcher/`.
2. Update `products/data-cloud/contracts/openapi/data-cloud.yaml` in the same change.
3. Update `docs/api/REST_API_DOCUMENTATION.md` if the endpoint should be visible in the human-readable inventory.
4. Run drift and contract checks before considering the work done.

Useful command:

```bash
products/data-cloud/scripts/check-openapi-drift.sh
```

### Work on entity storage or event persistence

Start in:

- `planes/data/entity/` for entity behavior
- `planes/event/core/` for event primitives
- `extensions/plugins/` for durable provider implementations

Validate both the unit path and the durable integration path. If a provider uses Postgres or Kafka, assume Testcontainers-backed verification is required.

### Work on UI features

Relevant sources:

- `ui/src/routes.tsx` for navigation and route names
- `ui/src/pages/` for major page entrypoints
- `ui/src/features/` for feature-local state, services, and components
- `ui/docs/web-page-specs/` for intended UX and IA

Current primary and role-aware routes include:

- Primary-user disclosure: `/`, `/data`, `/data/new`, `/data/:id`, `/data/:id/edit`, `/pipelines`, `/pipelines/new`, `/query`
- Operator disclosure: adds `/insights`, `/trust`, `/events`, `/entities`, `/memory`, `/context`, `/agents`, and `/plugins`
- Admin disclosure: adds `/settings`
- Operator-only direct links: `/alerts` is now a launcher-backed triage surface with list, acknowledge, resolve, grouping, suggestion, rule, and stream contracts; `/fabric` remains preview-only

### Work on plugins

If you are adding or updating plugin-backed functionality:

1. keep the core contract in `planes/shared-spi/` or the appropriate platform module
2. implement the provider in `extensions/plugins/`
3. expose only product-relevant transport behavior in `delivery/launcher/`
4. verify the plugin lifecycle and API surfaces together

### Work on Data Fabric admin UI

The Data Fabric admin feature already has focused docs under:

- `ui/src/features/data-fabric/README.md`
- `ui/src/features/data-fabric/INTEGRATION_GUIDE.md`
- `ui/src/features/data-fabric/TESTING_GUIDE.md`

Use those as the feature-local source of truth. Keep the product-level manuals high signal and cross-feature.

For normal UI page work, prefer the canonical launcher-backed adapters in `ui/src/lib/api/collections.ts`, `ui/src/lib/api/workflows.ts`, and `ui/src/api/*`. Preserve deprecated-route behavior in the mock/helpers layer only when you need explicit compatibility coverage.

## 7. Local API Usage Recipes

### Create an entity

```bash
curl -sS -X POST http://localhost:8082/api/v1/entities/tickets \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-ID: local-dev' \
  -d '{"title":"login failure","summary":"password reset token expired"}'
```

### Query entities

```bash
curl -sS 'http://localhost:8082/api/v1/entities/tickets' \
  -H 'X-Tenant-ID: local-dev'
```

### Append an event

```bash
curl -sS -X POST http://localhost:8082/api/v1/events \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-ID: local-dev' \
  -d '{"type":"ticket.created","payload":{"ticketId":"123"}}'
```

### Run a grounded retrieval request

```bash
curl -sS -X POST http://localhost:8082/api/v1/context/tickets/rag \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-ID: local-dev' \
  -d '{"question":"How do I fix an expired password reset token?","k":3}'
```

### Publish a data product

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

## 8. Daily Validation Commands

Use these as your default safety net:

```bash
./gradlew :products:data-cloud:delivery:launcher:test
./gradlew :products:data-cloud:build
./gradlew :products:data-cloud:delivery:sdk:build
pnpm --dir products/data-cloud/delivery/ui type-check
pnpm --dir products/data-cloud/delivery/ui lint
pnpm --dir products/data-cloud/delivery/ui test
```

Additional helpers:

```bash
products/data-cloud/scripts/check-openapi-drift.sh
products/data-cloud/scripts/verify-coverage.sh
products/data-cloud/scripts/verify-flakiness.sh
products/data-cloud/scripts/run-smoke-e2e.sh
```

## 9. Common Failure Modes

### Durable provider not discovered

Symptoms:

- `No durable EntityStore provider found`
- `No durable EventLogStore provider found`

Meaning:

- you started a non-local profile without the durable provider actually available or configured

### Postgres provider discovered but not configured

Symptom:

- `PostgresEntityStore discovered on the classpath, but no database configuration was provided`

Meaning:

- the plugin is present but `DATACLOUD_DB_*` or compatible aliases are missing

### Frontend route or contract drift

Symptoms:

- UI expects a route or schema that the launcher does not expose
- contract tests or manual API usage disagree with UI behavior

Action:

- verify `ui/src/routes.tsx`
- verify `contracts/openapi/data-cloud.yaml`
- rerun `products/data-cloud/scripts/check-openapi-drift.sh`

## 10. Documentation Map

Use this doc set in the following order:

1. `README.md` for product overview and quick start
2. `DEVELOPER_MANUAL.md` for day-to-day development
3. `TEST_MANUAL.md` for verification and CI-facing workflows
4. `USER_MANUAL.md` for operator and consumer workflows
5. `docs/operations/RUNBOOK.md` for validated deployment and recovery procedures
6. `docs/api/REST_API_DOCUMENTATION.md` and `contracts/openapi/data-cloud.yaml` for API work

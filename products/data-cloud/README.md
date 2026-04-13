# Data Cloud

**Product Owner:** @ghatana/data-team  
**Status:** Active  
**Stack:** Java 21 + ActiveJ 6.0

## Purpose

**Data Cloud** is an independent, deployable, AI/ML-native data product for the Ghatana ecosystem. It provides:

- **Data management** — entity storage, retrieval, schema governance, and lifecycle controls
- **Event-cloud backbone** — append-only event storage plus streaming for platform and product integrations
- **Analytics and reporting** — query, aggregation, reporting, and observability surfaces
- **AI/ML-native capabilities** — feature ingestion, model metadata, anomaly detection, ranking, recommendations, and assistive flows embedded across normal product workflows
- **Plugin-driven extensibility** — feature expansion through a product plugin system rather than hard-coded product silos
- **Execution persistence surfaces** — pipeline/checkpoint metadata, agent memory, and agent definition persistence for external runtimes

## Boundary And Roles

Data Cloud owns the data foundation and feature platform:

- data storage, event streaming, search, analytics, reporting, governance, and observability
- AI/ML-native product behavior embedded into data and analytics workflows
- plugin lifecycle, feature store, model metadata, and execution metadata persistence

AEP owns agentic processing:

- planning, orchestration, tool-using workflows, and long-running multi-step execution
- runtime discovery and execution of agents/operator pipelines

The integration rule is one-way:

- AEP may depend on Data Cloud public contracts, event schemas, and APIs
- Data Cloud must not depend on AEP code or import AEP modules
- Data Cloud requests agentic work through event-cloud and public contracts; AEP executes and writes results, telemetry, and checkpoints back through Data Cloud-owned APIs/events

## Architecture

```
Products / Clients  →  Data Cloud APIs + Plugin Runtime  →  Entities / Events / Features / Analytics
                                  │
                                  ├── event-cloud ──▶ AEP agentic runtime
                                  │                     │
                                  │                     └── emits results / telemetry / checkpoints
                                  │
                                  └── standalone and multi-environment deployment modes
```

## Deployment Modes

- **Standalone product deployment** for independent Data Cloud operation
- **Integrated platform deployment** as the shared data foundation for other Ghatana products
- **Embedded or environment-specific deployment** through launcher, Helm, Kubernetes, and Terraform assets

### Key Modules

| Module | Purpose |
|--------|---------|
| `spi/` | Stable public contracts for storage, event-cloud integration, plugins, and external product consumption |
| `platform-entity/` | Entity storage and schema-oriented domain contracts |
| `platform-event/` | Event-cloud and event-log primitives |
| `platform-analytics/` | Analytics, reporting, and query capabilities |
| `platform-config/` | Configuration and deployment-time product wiring |
| `platform-plugins/` | Plugin lifecycle and extensibility support |
| `platform-launcher/` | Core runtime/domain services, transport adapters, and DI wiring |
| `agent-registry/` | Durable persistence for agent definitions and metadata used by external runtimes such as AEP |
| `agent-catalog/` | Data Cloud capability catalog and integration metadata |
| `feature-store-ingest/` | Real-time feature ingestion pipeline from event-cloud into the feature store for ML workflows |
| `sdk/` | Generated client libraries for the Data-Cloud REST API (Java, TypeScript, Python) — run `./gradlew :products:data-cloud:sdk:generateAllSdks` |
| `launcher/` | ActiveJ bootstrap; hosts the HTTP server with all API routes |
| `ui/` | React 19 frontend for the Data-Cloud product |
| `k8s/` | Raw Kubernetes manifests (ConfigMap, Deployment, Service, Ingress) |
| `helm/` | Helm charts for production deployment |
| `terraform/` | AWS infrastructure provisioning (private subnets, no public exposure) |

## Prerequisites

- Java 21
- Apache Kafka (or Redpanda for local dev)
- PostgreSQL (for agent registry persistence)

## Local Development

```bash
# Start infrastructure
docker-compose -f shared-services/infrastructure/docker-compose.yml up -d kafka postgres

# Build
./gradlew :products:data-cloud:build

# Run tests
./gradlew :products:data-cloud:test
```

### Embedded Developer Mode (zero external infrastructure) — B2

Set `DATACLOUD_PROFILE=local` to start a fully self-contained single-process server. In this
mode the launcher uses an H2 in-process store for entities and an in-memory event log. No
PostgreSQL, Redis, ClickHouse, Iceberg, or Kafka credentials are required.

```bash
DATACLOUD_PROFILE=local \
DATACLOUD_HTTP_ENABLED=true \
./gradlew :products:data-cloud:launcher:run
```

The server starts on the default port (`8082`) and is fully functional for:
- Entity CRUD, batch, search, validation, and anomaly detection
- SQL workspace queries (in-memory analytics engine fallback)
- Alert rules, governance, and lifecycle endpoints
- AI assist endpoints (if `OPENAI_API_KEY` or `OLLAMA_HOST` are also set)

**Caveats in local-dev mode:**
- Data does not persist across restarts (in-memory H2)
- Tier migration, ClickHouse tracing, and pgvector search are no-ops
- Federated Trino queries fall back to the local analytics engine

To use the CLI flag instead of environment variable:
```bash
DATACLOUD_HTTP_ENABLED=true ./gradlew :products:data-cloud:launcher:run --args="--http --profile=local"
```



- **Event sourcing** — all state changes produce events; consumers build projections
- **Tenant isolation** — topics and registry entries are namespaced by `tenantId`
- **AI/ML is implicit** — intelligence is embedded into product workflows rather than isolated behind a separate mode
- **Agentic processing stays external** — agentic execution runs in AEP, while Data Cloud remains the system of record for the relevant data, features, memory, and execution metadata
- **No circular product dependency** — Data Cloud does not import AEP; AEP consumes Data Cloud through public contracts and event-cloud integration

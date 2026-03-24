# Data Cloud

**Product Owner:** @ghatana/data-team  
**Status:** Active  
**Stack:** Java 21 + ActiveJ 6.0

## Purpose

**Data Cloud** is the platform's persistent event store and streaming infrastructure. It provides:

- **Event log** — append-only, tenant-scoped event storage (Apache Kafka-backed)
- **Event tailing** — real-time push-based subscriptions via SSE/WebSocket
- **Agent registry** — persistent cross-product agent metadata store
- **Platform abstraction** — SPI for event publishing/consuming consumed by AEP and other products

## Architecture

```
Producers (AEP, Products)  →  data-cloud/event  →  EventLog (Kafka)
                                                          │
                                              EventTailing (SSE push)
                                                          │
                                              Consumers (AEP, Products)
```

### Key Modules

| Module | Purpose |
|--------|---------|
| `platform/` | Core SPI: `EventPublisher`, `EventConsumer`, `EventLog` interfaces; includes `com.ghatana.datacloud.event` package for canonical event streaming |
| `spi/` | Shared interfaces and types for cross-product integration (`EventLogStore`, `AgentRegistry` SPI) |
| `agent-registry/` | `DataCloudAgentRegistry` — implements platform `AgentRegistry` SPI |
| `agent-catalog/` | YAML definitions for built-in agent capabilities and operator catalogue |
| `feature-store-ingest/` | Real-time feature ingestion pipeline from EventCloud → Feature Store (ML pipelines); migrated from `shared-services` per ADR-013 |
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

## Key Design Decisions

- **Event sourcing** — all state changes produce events; consumers build projections
- **Tenant isolation** — topics and registry entries are namespaced by `tenantId`
- **No cross-product platform deps** — only depends on `platform/java/*` libs

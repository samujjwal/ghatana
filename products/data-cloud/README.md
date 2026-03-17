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
| `platform/` | Core SPI: `EventPublisher`, `EventConsumer`, `EventLog` interfaces |
| `event/` | Package `com.ghatana.datacloud.event` — canonical event streaming |
| `agent-registry/` | `DataCloudAgentRegistry` — implements platform `AgentRegistry` SPI |
| `launcher/` | ActiveJ bootstrap |
| `k8s/` | Kubernetes manifests |
| `helm/` | Helm charts |

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

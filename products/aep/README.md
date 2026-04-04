# AEP — Agentic Event Processor

**Product Owner:** @ghatana/aep-team  
**Status:** Active  
**Stack:** Java 21 + ActiveJ 6.0 / Kotlin

## Purpose

The **Agentic Event Processor** (AEP) is the central event-driven operator pipeline for the Ghatana platform. It provides:

- **Operator catalog** — registry of all domain operators (`UnifiedOperator` implementations)
- **Pipeline execution** — composable, YAML-configurable operator chains
- **Event routing** — subscribe to Data Cloud event-cloud streams and dispatch to the correct operator pipeline
- **Multi-tenant isolation** — all pipelines are tenant-scoped

AEP is the canonical home for the `Operator`, `OperatorCatalog`, and `Pipeline` interfaces used across the platform.

## Boundary With Data Cloud

Data Cloud and AEP are tightly integrated but intentionally asymmetric:

- **Data Cloud owns** data management, events, analytics, reporting, feature storage, model metadata, plugin-driven data capabilities, memory persistence, and execution metadata persistence
- **AEP owns** agentic processing, planning, orchestration, tool use, and long-running multi-step execution
- **Dependency direction** is one-way at compile time: AEP may depend on Data Cloud public contracts and APIs; Data Cloud must not import AEP modules
- **Runtime integration** happens through event-cloud and Data Cloud-owned persistence surfaces: AEP consumes requests and data from Data Cloud, then writes results, telemetry, checkpoints, and memory updates back

## Architecture

```
Data Cloud events / intents  →  AEP Server  →  PipelineExecutionEngine  →  OperatorCatalog
                                           │
                                  ┌────────┴────────┐
                            OperatorChain     UnifiedOperator impls
                                           │
                                           └── results / checkpoints / telemetry → Data Cloud
```

### Key Modules

| Module | Purpose |
|--------|---------|
| `platform/` | Core interfaces: `Operator`, `Pipeline`, `OperatorCatalog`, `AgentRegistryService` |
| `api/` | REST API for pipeline management and operator catalog queries |
| `server/` | ActiveJ HTTP server, bootstrap and event-loop wiring |
| `agent-catalog/` | YAML-based catalog of built-in operators |
| `k8s/` | Kubernetes manifests for production deployment |
| `helm/` | Helm charts |

## Prerequisites

- Java 21
- Docker (for local infrastructure)
- Access to `data-cloud` event stream

## Local Development

```bash
# Build the platform module
./gradlew :products:aep:platform:build

# Build everything
./gradlew :products:aep:build

# Run tests
./gradlew :products:aep:test

# Run locally (requires Kafka/Redis)
./gradlew :products:aep:server:run
```

## Key Design Decisions

- **ActiveJ only** — no Spring Reactor/WebFlux. All async via `Promise`.
- **Operator SPI** — operators are discovered via `ServiceLoader`. Add new operators by implementing `UnifiedOperator`.
- **Agentic boundary** — AEP handles agentic execution; Data Cloud remains the AI/ML-native system of record and data foundation.
- **Cross-product rule** — consumer products may depend on `products/aep/platform` only where AEP contracts are intentionally exposed; Data Cloud integration remains one-way from AEP to Data Cloud public contracts. See [remediation plan](../../docs/PRODUCTION_REMEDIATION_PLAN.md).

## Related ADRs

- `docs/adr/` — architectural decision records for operator design, event routing, multi-tenancy

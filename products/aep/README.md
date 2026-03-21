# AEP — Agentic Event Processor

**Product Owner:** @ghatana/aep-team  
**Status:** Active  
**Stack:** Java 21 + ActiveJ 6.0 / Kotlin

## Purpose

The **Agentic Event Processor** (AEP) is the central event-driven operator pipeline for the Ghatana platform. It provides:

- **Operator catalog** — registry of all domain operators (`UnifiedOperator` implementations)
- **Pipeline execution** — composable, YAML-configurable operator chains
- **Event routing** — subscribe to `data-cloud/event` and dispatch to the correct operator pipeline
- **Multi-tenant isolation** — all pipelines are tenant-scoped

AEP is the canonical home for the `Operator`, `OperatorCatalog`, and `Pipeline` interfaces used across the platform.

## Architecture

```
data-cloud/event  →  AEP Server  →  PipelineExecutionEngine  →  OperatorCatalog
                                             │
                                    ┌────────┴────────┐
                              OperatorChain     UnifiedOperator impls
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
- **Cross-product rule** — other products depend on `products/aep/platform` until `platform/java/pipeline-api` is extracted. See [remediation plan](../../docs/PRODUCTION_REMEDIATION_PLAN.md).

## Related ADRs

- `docs/adr/` — architectural decision records for operator design, event routing, multi-tenancy

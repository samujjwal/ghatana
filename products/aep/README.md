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

- **Data Cloud owns** data management, events, analytics, reporting, feature storage, model metadata, and plugin-driven data capabilities
- **AEP owns** agentic processing, planning, orchestration, tool use, long-running multi-step execution, and the AEP-owned durable execution-history and agent-memory/task-state persistence paths when DB-backed runtime services are configured
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

## Production Notes

- Set `AEP_PROFILE=production` for production deployments.
- In production, `AEP_DB_URL` and `AEP_JWT_SECRET` are mandatory and startup now fails fast when either is missing.
- Durable governance, execution history, and memory persistence require the database-backed path; without `AEP_DB_URL`, AEP remains suitable only for non-production or reduced-scope deployments.
- `GET /metrics` serves Prometheus text format when the launcher wires a `PrometheusMeterRegistry`; otherwise embedded and fixture-backed modes return a JSON fallback payload.
- `GET /health` is the shallow liveness-style aggregate, while `GET /health/deep` exposes deeper dependency state for the injected database, Redis, Data Cloud backing stores, pipeline durability, memory storage, and execution-history persistence.
- `POST /api/v1/session` is now wired into the HTTP server chain through `SessionFilter`; in authenticated environments it sits behind JWT auth and issues short-lived `X-AEP-Session` tokens for repeated requests.

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

## Integration Tests

Phase-1 integration coverage is grouped under `IntegrationTestSuite` in the server module. Local execution requires Docker because the suite uses Testcontainers for PostgreSQL, Redis, and Kafka.

Measured repository inventory on 2026-04-15: `products/aep` currently contains 229 `*Test.java` files and 2,613 `@Test` methods. Coverage percentage still requires a fresh CI/build report.

```bash
./gradlew :products:aep:server:test --tests com.ghatana.aep.server.integration.IntegrationTestSuite
```

## Key Design Decisions

- **ActiveJ only** — no Spring Reactor/WebFlux. All async via `Promise`.
- **Operator SPI** — operators are discovered via `ServiceLoader`. Add new operators by implementing `UnifiedOperator`.
- **Agentic boundary** — AEP handles agentic execution; Data Cloud remains the AI/ML-native system of record and data foundation.
- **Cross-product rule** — consumer products may depend on `products/aep/platform` only where AEP contracts are intentionally exposed; Data Cloud integration remains one-way from AEP to Data Cloud public contracts. See [remediation plan](../../docs/PRODUCTION_REMEDIATION_PLAN.md).

## Related ADRs

- `docs/adr/` — architectural decision records for operator design, event routing, multi-tenancy

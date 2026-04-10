# Data Cloud Architecture Index

Last reviewed: 2026-04-09  
Primary source of truth: `products/data-cloud/**`, especially `README.md`, `launcher/src/main/**`, `platform-*/src/main/**`, `ui/src/**`, `docs/openapi.yaml`, `helm/**`, `k8s/**`, `terraform/**`, and product-local test suites.

## Purpose

This package documents the implemented architecture of `products/data-cloud` for onboarding, architecture review, refactoring, risk management, and roadmap planning.

## Scope

**Implemented**
- Java product modules under `products/data-cloud`
- React/Vite UI under `products/data-cloud/ui`
- OpenAPI, gRPC, GraphQL-adjacent contracts, scripts, Helm, Kubernetes, Terraform, and product-local workflows

**Inferred**
- Runtime ownership boundaries where wiring is split across comments, tests, and build files
- Intended module extraction plan where build files explicitly describe Phase 1/Phase 2 splits

**Missing**
- Non-repo operational runbooks outside this product folder
- Production environment values and secrets

**Recommended**
- Treat this package as the maintained architecture home for Data Cloud going forward

## Labels Used

- **Implemented**: directly evidenced in code, config, scripts, or tests
- **Inferred**: strongly supported by multiple artifacts but not fully wired in one place
- **Missing**: expected architectural element is absent or only partially present
- **Recommended**: pragmatic next step based on implemented reality

## How To Navigate

1. Start with [01-executive-summary.md](01-executive-summary.md).
2. Read [02-repository-inventory.md](02-repository-inventory.md) and [04-workspace-module-architecture.md](04-workspace-module-architecture.md).
3. Use the frontend, backend, shared-library, and feature-mapping documents for subsystem work.
4. Use [20-risks-gaps-recommendations.md](20-risks-gaps-recommendations.md) for prioritization.

## Document Map

| File | Focus |
|---|---|
| `00-index.md` | Navigation, conventions, scope |
| `01-executive-summary.md` | Product summary, strengths, weaknesses, priorities |
| `02-repository-inventory.md` | Concrete inventory of modules, configs, tests, docs |
| `03-system-context.md` | Actors, systems, trust boundaries, system ownership |
| `04-workspace-module-architecture.md` | Monorepo and module graph |
| `05-frontend-architecture.md` | UI routing, state, pages, client layers |
| `06-backend-architecture.md` | Runtime services, APIs, storage, async flows |
| `07-shared-library-architecture.md` | Shared modules and reuse quality |
| `08-feature-mapping.md` | Feature-to-module and end-to-end mapping |
| `09-api-contract-architecture.md` | REST, gRPC, GraphQL, config contracts |
| `10-event-architecture.md` | Event log, Kafka plugin, SSE/WebSocket, async consumers |
| `11-runtime-flows-sequences.md` | Startup and core workflow sequences |
| `12-domain-data-architecture.md` | Entities, persistence, schema ownership |
| `13-dependency-architecture.md` | Library overlap, sprawl, runtime implications |
| `14-build-tooling-cicd-architecture.md` | Build, test, codegen, packaging, delivery |
| `15-security-architecture.md` | Auth, authz, secrets, data protection |
| `16-observability-operations.md` | Logging, metrics, health, runtime diagnostics |
| `17-deployment-environments.md` | Local, CI, staging, production deployment topology |
| `18-subsystem-design-docs.md` | Mini design docs for major subsystems |
| `19-architecture-decisions.md` | Reconstructed architectural decisions |
| `20-risks-gaps-recommendations.md` | Evidence-based issues and roadmap |

## Diagram Map

| Diagram | Purpose |
|---|---|
| [`diagrams/system-context.mmd`](diagrams/system-context.mmd) | Actors and external systems |
| [`diagrams/workspace-structure.mmd`](diagrams/workspace-structure.mmd) | Product folder structure |
| [`diagrams/module-dependencies.mmd`](diagrams/module-dependencies.mmd) | Core module dependency flow |
| [`diagrams/runtime-topology.mmd`](diagrams/runtime-topology.mmd) | Runtime components and transports |
| [`diagrams/deployment.mmd`](diagrams/deployment.mmd) | K8s/AWS deployment view |
| [`diagrams/frontend-routes.mmd`](diagrams/frontend-routes.mmd) | UI route hierarchy |
| [`diagrams/frontend-components.mmd`](diagrams/frontend-components.mmd) | Frontend component layering |
| [`diagrams/frontend-state-api-flow.mmd`](diagrams/frontend-state-api-flow.mmd) | UI state and API flow |
| [`diagrams/backend-http-runtime-components.mmd`](diagrams/backend-http-runtime-components.mmd) | Standalone HTTP runtime |
| [`diagrams/backend-agent-registry-components.mmd`](diagrams/backend-agent-registry-components.mmd) | Agent registry subsystem |
| [`diagrams/backend-feature-store-ingest-components.mmd`](diagrams/backend-feature-store-ingest-components.mmd) | Feature ingestion pipeline |
| [`diagrams/shared-library-consumers.mmd`](diagrams/shared-library-consumers.mmd) | Shared library usage map |
| [`diagrams/api-architecture.mmd`](diagrams/api-architecture.mmd) | Contract surface overview |
| [`diagrams/contract-ownership.mmd`](diagrams/contract-ownership.mmd) | Canonical contract owners |
| [`diagrams/event-topology.mmd`](diagrams/event-topology.mmd) | Event producers and brokers |
| [`diagrams/producer-consumer-map.mmd`](diagrams/producer-consumer-map.mmd) | Producer/consumer relationships |
| [`diagrams/sequence-http-entity-crud.mmd`](diagrams/sequence-http-entity-crud.mmd) | HTTP entity lifecycle |
| [`diagrams/sequence-workflow-pipeline.mmd`](diagrams/sequence-workflow-pipeline.mmd) | Pipeline/workflow flow |
| [`diagrams/sequence-feature-ingest.mmd`](diagrams/sequence-feature-ingest.mmd) | Feature-store ingestion flow |
| [`diagrams/domain-relationships.mmd`](diagrams/domain-relationships.mmd) | Core entity relationships |
| [`diagrams/data-flow.mmd`](diagrams/data-flow.mmd) | Storage/data movement |
| [`diagrams/trust-boundaries.mmd`](diagrams/trust-boundaries.mmd) | Security boundaries |
| [`diagrams/observability-flow.mmd`](diagrams/observability-flow.mmd) | Metrics/logs/health signal flow |
| [`diagrams/build-cicd.mmd`](diagrams/build-cicd.mmd) | Build and delivery toolchain |

## Authoring Conventions

**Implemented**
- Prefer exact file paths in prose, tables, and evidence callouts.

**Inferred**
- Call out inferred relationships explicitly instead of silently upgrading them to facts.

**Missing**
- When the repo lacks a clear owner, contract, or runtime path, say so directly.

**Recommended**
- Recommendations should preserve the implemented AEP/Data Cloud product boundary.

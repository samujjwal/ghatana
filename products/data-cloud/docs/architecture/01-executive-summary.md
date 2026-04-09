# Data Cloud Executive Summary

## Product Summary

**Implemented**
- Data Cloud is a multi-module, deployable data platform under `products/data-cloud` with Java 21 + ActiveJ runtime code and a separate React 19 UI (`README.md`, `launcher/src/main/java/com/ghatana/datacloud/launcher/DataCloudLauncher.java`, `ui/package.json`).
- It owns generic entity storage, append-only event storage, analytics/reporting, memory-plane persistence, plugin-backed storage/streaming/search, and AI/ML-adjacent capabilities such as feature ingestion and model-registry endpoints (`launcher/src/main/java/.../DataCloudHttpServer.java`, `platform-*/src/main/**`, `feature-store-ingest/**`).
- It explicitly positions AEP as an external agentic-processing consumer rather than an internal dependency (`README.md`, `OWNER.md`, `docs/ADR-DC-001-MODULE-OWNERSHIP.md`).

**Inferred**
- Data Cloud is trying to evolve from a monolithic `platform-launcher`/`launcher` runtime toward clearer module separation (`platform-api/build.gradle.kts`, `platform-client/build.gradle.kts`, comments tagged `FINDING-DC-H2`).

## Major Capabilities

- Generic entity CRUD, batch operations, validation, search, export, anomaly detection
- Event append/read/tail via HTTP, SSE, WebSocket, and gRPC
- Pipeline/checkpoint persistence for external runtimes
- Agent memory plane and brain/learning APIs
- Analytics query, aggregation, reports, AI suggestions
- Governance and privacy operations
- Plugin registry, storage plugins, and feature-store ingestion

## Major Runtime Boundaries

| Boundary | Implemented Reality |
|---|---|
| Standalone transport | `launcher` hosts HTTP and gRPC bootstrap |
| Runtime/domain/infrastructure | `platform-launcher` carries DI, infra adapters, storage, gRPC services, distributed/embedded modes |
| API/library layer | `platform-api` holds controllers, DTOs, and app services, but much HTTP routing still lives in `launcher/http` |
| Shared contracts | `spi`, `platform-entity`, `platform-event`, `platform-config`, `platform-analytics` |
| UI | `ui` is a standalone Vite/React app with TanStack Query + Jotai |

## Architectural Style

**Implemented**
- ActiveJ async runtime with Promise-based APIs
- Hexagonal/repository-port language in domain modules
- Plugin-driven storage/streaming/search extensions
- Monorepo modularization with Gradle + Vite split

**Inferred**
- The product is partially through a transport/API/runtime separation refactor, but not fully landed.

## Strengths

- Strong product boundary against AEP imports
- Rich deployment assets: Docker, Helm, raw K8s, Terraform, Argo CD
- Broad test surface across modules and UI
- Clear event/storage/plugin ambitions with concrete code in `platform-plugins`, `feature-store-ingest`, and `launcher/http`

## Key Weaknesses And Risks

- `platform-launcher` still acts as a gravity well for infra, client, embedded, gRPC, storage, observability, and migration concerns
- `platform-api` extraction is only partial; several controllers appear test-only and not obviously wired into the live launcher path
- UI contains duplicated and partially stale client layers (`src/api/*`, `src/lib/api/*`, `src/services/*`)
- OpenAPI already drifts from live routes (`check-openapi-drift.sh --warn-only`)
- Several modules are placeholders or empty (`platform-client`, `sdk`, `platform-governance`, `platform`)
- Security filter is optional and not enabled by default in the standalone HTTP bootstrap

## Maturity Assessment

**Implemented**
- Operational maturity is medium-high for packaging and infrastructure assets.
- Architectural maturity is medium because the repo shows partial migrations, duplicate contract layers, and placeholder tests in some structural areas.

## Top Priority Improvements

1. Finish or pause the `platform-launcher` split; stop leaving empty extraction targets in place.
2. Collapse UI API/client duplication onto one canonical data-access layer.
3. Make one canonical HTTP contract source and enforce launcher-to-spec parity in CI.
4. Wire security enforcement explicitly for standalone mode or document that standalone is gateway-only.
5. Separate real architecture tests from placeholder “assert true” structural tests.

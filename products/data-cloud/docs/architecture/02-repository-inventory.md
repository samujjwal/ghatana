# Data Cloud Repository Inventory

See also [04-workspace-module-architecture.md](04-workspace-module-architecture.md) and [`diagrams/workspace-structure.mmd`](diagrams/workspace-structure.mmd).

## Applications, Services, And Libraries

| Name | Type | Path | Responsibility | Important Dependencies | Important Dependents | Status |
|---|---|---|---|---|---|---|
| `launcher` | Deployable app | `products/data-cloud/launcher` | Standalone HTTP/gRPC bootstrap and transport handlers | `platform-launcher`, `platform-api`, ActiveJ, HikariCP | Dockerfile, K8s deployment | active |
| `ui` | Frontend app | `products/data-cloud/ui` | React/Vite SPA for data, workflows, insights, trust, events, memory, plugins | React Router, TanStack Query, Jotai, product API clients | Human operators, E2E tests | active |
| `feature-store-ingest` | Background service | `products/data-cloud/feature-store-ingest` | Poll EventLogStore and write ML features | `spi`, `platform-launcher`, ai-integration | ML/feature workflows | active |
| `agent-registry` | Backend library/service module | `products/data-cloud/agent-registry` | Persist agent metadata, releases, rollouts, evaluation results, memory namespaces | `platform-launcher`, agent-core | AEP and Data Cloud runtime integrations | active |
| `spi` | Shared contract library | `products/data-cloud/spi` | Public storage/event/plugin abstractions | `platform-entity`, kernel-plugin | AEP, YAPPC, platform modules | active |
| `platform-entity` | Shared domain/persistence library | `products/data-cloud/platform-entity` | Entity, collection, workflow, schema models and repo ports | platform java core/domain/database | SPI, platform-api, runtime | active |
| `platform-event` | Shared event library | `products/data-cloud/platform-event` | Event models, durability, replay, plugin SPIs | `spi`, `platform-entity` | runtime, analytics, plugins | active |
| `platform-config` | Shared config library | `products/data-cloud/platform-config` | YAML config loading, validation, compilation, reload, config-driven health | platform config libs, ActiveJ | runtime, plugins, startup validation | active |
| `platform-analytics` | Shared analytics library | `products/data-cloud/platform-analytics` | Query engine, report generation, anomaly detection, export | `platform-event`, `platform-entity`, JSqlParser | runtime and HTTP analytics routes | active |
| `platform-api` | API/library module | `products/data-cloud/platform-api` | Controllers, DTOs, GraphQL mutation class, app services | `spi`, `platform-config`, `platform-analytics` | `platform-launcher`, tests | partial |
| `platform-launcher` | Runtime/domain/infra library | `products/data-cloud/platform-launcher` | DataCloud entrypoint, DI, infra adapters, gRPC, caching, storage, migration, distributed/embedded modes | almost every shared module + infra libs | `launcher`, `agent-registry`, `feature-store-ingest` | active |
| `platform-plugins` | Shared plugin implementations | `products/data-cloud/platform-plugins` | Kafka, Redis, S3 archive, Iceberg, Trino, vector, knowledge graph, enterprise plugins | Kafka, Redis, AWS S3, OpenSearch, RocksDB | runtime plugin wiring | active |
| `platform-client` | Extraction target | `products/data-cloud/platform-client` | Intended lean client SDK module | `spi`, `platform-entity`, `platform-config` | intended external consumers | partial |
| `sdk` | Codegen module | `products/data-cloud/sdk` | Generate Java/TS/Python SDKs from OpenAPI | OpenAPI Generator, `docs/openapi.yaml` | external consumers | partial |
| `api` | Spec-only module | `products/data-cloud/api` | Holds `data-cloud-api.openapi.yaml` and API-focused tests | OpenAPI spec/test files | docs and tests | partial |
| `data-cloud-cache` | Small support module | `products/data-cloud/data-cloud-cache` | Cache-specific code with very small surface | Java platform libs | unclear | unclear |
| `platform-governance` | Test-only placeholder | `products/data-cloud/platform-governance` | Governance-targeted tests without corresponding main sources | test frameworks | unclear | partial |
| `platform` | Empty/legacy folder | `products/data-cloud/platform` | No evident checked-in main sources | n/a | historical docs/build assumptions | legacy |

## Config, Infra, And Tooling Inventory

| Item | Type | Path | Responsibility | Status |
|---|---|---|---|---|
| Product README | Doc | `products/data-cloud/README.md` | Product boundary and module overview | active |
| OpenAPI spec | Contract | `products/data-cloud/docs/openapi.yaml` | Canonical REST spec referenced by SDK generation | active |
| REST companion doc | Doc | `products/data-cloud/REST_API_DOCUMENTATION.md` | Human-readable REST guide | active |
| Helm chart | Deployment | `products/data-cloud/helm/data-cloud` | Parametrized K8s packaging | active |
| Raw K8s manifests | Deployment | `products/data-cloud/k8s` | Namespace, service, deployment, ingress, HPA, secrets, Argo CD app | active |
| Terraform | Infra | `products/data-cloud/terraform` | AWS infra modules and env overlays | active |
| Product scripts | Tooling | `products/data-cloud/scripts` | Drift checks, smoke tests, backup/DR, verification | active |
| Product-local GitHub workflow | CI/security | `products/data-cloud/.github/workflows/security-scanning.yml` | Dependency/SAST/secret/container scanning | active |
| Existing generated docs | Historical docs | `products/data-cloud/docs-generated` | Previous generated analysis package | legacy |

## Test Inventory Summary

| Surface | Evidence | Notes |
|---|---|---|
| Launcher HTTP tests | `launcher/src/test/java/com/ghatana/datacloud/launcher/http/**` | Extensive endpoint-focused coverage |
| Platform API tests | `platform-api/src/test/java/**` | Includes contract, security, structural, controller tests |
| Runtime/module tests | `platform-launcher/src/test/java/**` | DI, storage, gRPC, recovery, backpressure, versioning |
| Feature ingest tests | `feature-store-ingest/src/test/java/**` | Includes DLQ and circuit-breaker behavior |
| UI unit/integration | `ui/src/__tests__/**` | Pages, services, hooks, accessibility |
| UI E2E | `ui/e2e/**` | Playwright specs by feature |
| Coverage matrices/docs | `docs/testing/**` | Extensive test planning and coverage mapping |

## External Integrations, Stores, And Protocols

| Category | Evidence |
|---|---|
| PostgreSQL / JSONB | `platform-launcher/src/main/java/.../PostgresJsonbConnector.java`, Flyway migrations |
| Kafka / event streaming | `platform-plugins/.../KafkaStreamingPlugin.java`, `KafkaEventLogStore.java` |
| Redis / hot cache | `platform-launcher` and `platform-plugins` include Jedis and Lettuce |
| OpenSearch | `platform-launcher/.../OpenSearchConnector.java`, Helm values, Terraform module |
| ClickHouse | `platform-launcher/.../ClickHouseTimeSeriesConnector.java`, Helm values, Terraform module |
| S3 / Ceph object storage | `platform-launcher/.../BlobStorageConnector.java`, `CephObjectStorageConnector.java` |
| gRPC | `launcher/grpc/DataCloudGrpcServer.java`, `platform-launcher/grpc/*.java` |
| SSE/WebSocket | `launcher/http/DataCloudHttpServer.java`, `ui/src/lib/websocket/**` |

## Duplicate, Suspicious, Or Unclear Areas

| Finding | Evidence | Assessment |
|---|---|---|
| UI has duplicated client layers | `ui/src/api/*`, `ui/src/lib/api/*`, `ui/src/services/*` | active duplication and migration drift |
| `platform-api` controllers are not obviously part of live route assembly | usage search shows controller construction primarily in tests | partial migration |
| `platform-client` build exists but no checked-in sources | `platform-client/build.gradle.kts`, zero main files | extraction target, not yet real module |
| `sdk` generates outputs but stores no checked-in SDK code | `sdk/build.gradle.kts`, empty `sdk/src` | codegen task, not a maintained source module |
| `platform-governance` has tests but no main sources | source count and folder scan | unclear ownership |
| `api` contains spec/test artifacts only | `api/data-cloud-api.openapi.yaml` | support module, not runtime service |

## Ownership Notes

**Implemented**
- Product ownership is declared in `products/data-cloud/OWNER.md`.
- Module ownership intent is documented in `docs/ADR-DC-001-MODULE-OWNERSHIP.md`.

**Missing**
- Several submodules do not contain clearly consumable runtime entrypoints or checked-in owners beyond top-level product ownership.

**Recommended**
- Add or refresh `OWNER.md` files for partial modules and explicitly mark placeholder modules as deprecated or planned.

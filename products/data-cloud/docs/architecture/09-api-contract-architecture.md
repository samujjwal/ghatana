# API Contract Architecture

See [06-backend-architecture.md](06-backend-architecture.md), [13-dependency-architecture.md](13-dependency-architecture.md), [`diagrams/api-architecture.mmd`](diagrams/api-architecture.mmd), and [`diagrams/contract-ownership.mmd`](diagrams/contract-ownership.mmd).

## Contract Inventory

| Contract Surface | Definition Location | Implemented Location | Consumers | Ownership Quality |
|---|---|---|---|---|
| Standalone HTTP REST | `products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudHttpServer.java` | same | UI, tests, direct service consumers | strong runtime owner, weak external spec parity |
| Product OpenAPI spec | `products/data-cloud/docs/openapi.yaml` | drift checker targets this file | docs, CI scripts | partial |
| Alternate OpenAPI spec | `products/data-cloud/api/data-cloud-api.openapi.yaml` | no direct runtime binding found | human readers, possible generated tooling | unclear / likely stale |
| gRPC event services | generated from product proto outputs consumed by `EventLogGrpcService`, `EventQueryGrpcService`, `EventServiceGrpcService` | `launcher/grpc/DataCloudGrpcServer.java` and `platform-launcher/grpc/**` | service-to-service clients | strong |
| GraphQL | `platform-api/src/main/java/com/ghatana/datacloud/api/graphql/GraphQLMutations.java` | partial library only | tests, future callers | weak / partial |
| DTOs and controller contracts | `platform-api/src/main/java/com/ghatana/datacloud/api/**` | mostly library/test usage | tests, future launcher split | partial |
| Config contracts | `platform-config/src/main/java/com/ghatana/datacloud/config/**`, YAML files under `config/**` | runtime loaders and validators | launcher, plugins, env-specific config | strong |
| Frontend client contracts | `ui/src/lib/api/*`, `ui/src/api/client.ts`, `ui/src/services/*` | browser runtime | UI pages and hooks | duplicated |

## REST Contract Families

### Implemented

- Health and ops: `/health`, `/health/detail`, `/ready`, `/live`, `/info`, `/metrics`
- Entity APIs: `/api/v1/entities/:collection`, `/api/v1/entities/:collection/:id`, search, export, validation, anomalies, batching, streaming
- Event APIs: `/api/v1/events`, `/api/v1/events/:offset`, `/events/stream`, `/ws`
- Pipeline/checkpoint APIs: `/api/v1/pipelines/**`, `/api/v1/checkpoints/**`
- Memory, brain, learning, analytics, reports, model registry, feature store, AI assist, voice, and governance endpoints in `DataCloudHttpServer.java`

### Inferred

- `launcher/http` is the canonical contract owner today because all live routes are assembled there, even when library DTO/controller abstractions exist elsewhere.

### Missing

- There is no single checked-in REST artifact that fully matches the launcher route table.
- The presence of both `docs/openapi.yaml` and `api/data-cloud-api.openapi.yaml` creates contract ambiguity.

### Recommended

- Choose one machine-readable spec location and make it the required update target for all route changes.

## gRPC Contracts

| Service | Runtime Class | Backing Store | Notes |
|---|---|---|---|
| `EventLogService` | `EventLogGrpcService` | `EventLogStore` | append/read-by-type focused |
| `EventQueryService` | `EventQueryGrpcService` | `EventLogStore` | query and explain paths |
| `EventService` | `EventServiceGrpcService` | `EventLogStore` | ingest, query, get-event flow |

**Implemented**
- gRPC runs on port resolved by `DATACLOUD_GRPC_PORT` in `DataCloudGrpcServer.java`.
- Tenant context is applied through `TenantGrpcInterceptor.lenient()`.

**Missing**
- There is no equivalent broad gRPC surface for entities, analytics, or governance.

## GraphQL Contracts

**Implemented**
- `platform-api` contains `GraphQLMutations.java`.

**Inferred**
- GraphQL was either an exploratory or partially extracted contract surface during the `platform-launcher` split.

**Missing**
- No checked-in `GraphQLQueries` implementation was found even though tests reference GraphQL query behavior.
- No runtime wiring in `launcher` or `platform-launcher` was found that exposes a GraphQL transport endpoint.

## Contract Ownership Matrix

| Concern | Canonical Owner Today | Overlaps |
|---|---|---|
| HTTP route registration | `launcher/http/DataCloudHttpServer.java` | `platform-api/controller/**`, OpenAPI specs |
| Public-ish domain ports | `spi/**` | `platform-entity/**`, runtime DTOs |
| Persistence/domain DTOs | `platform-entity/**` | `spi` nested records, UI types |
| Event model | `spi/EventLogStore.java` plus `platform-event/**` | gRPC DTOs, runtime event records |
| Frontend transport adapter | `ui/src/lib/api/*` appears current | `ui/src/api/client.ts`, `ui/src/services/*` |

## Request/Response Lifecycle

### Implemented

1. Browser or service calls HTTP or gRPC transport.
2. Transport layer applies coarse filtering and optional auth/policy middleware.
3. Request delegates into launcher handlers or gRPC services.
4. Handlers use `DataCloudClient`, repositories, analytics services, or plugin-backed storage connectors.
5. Response is serialized by ActiveJ HTTP or gRPC runtime.

### Inferred

- DTO/controller classes in `platform-api` were intended to become the main request orchestration layer, but the live launcher path still bypasses that idealized split for many flows.

## Validation Quality

| Validation Layer | Evidence | Assessment |
|---|---|---|
| HTTP payload size/content checks | `DataCloudHttpServer.java` | implemented |
| Entity schema validation | `EntitySchemaValidator`, `/validate` routes | implemented |
| Config validation | `platform-config/**`, `ConfigLoader.java`, product validators | implemented |
| OpenAPI conformance | `scripts/check-openapi-drift.sh` | implemented but only partial and warn-only in observed run |
| Frontend runtime validation | `ui/src/api/client.ts` uses `zod` for some older APIs | partial and not canonical |
| Contract test enforcement | module and route tests exist | uneven |

## Contract Findings

| Finding | Evidence | Impact |
|---|---|---|
| Two OpenAPI files imply two stories | `docs/openapi.yaml` and `api/data-cloud-api.openapi.yaml` | unclear canonical contract |
| HTTP runtime and spec already diverge | drift script output for `/api/v1/events/{offset}` | docs cannot be trusted blindly |
| UI contract adapters disagree | `ui/src/lib/api/collections.ts` vs `ui/src/api/client.ts` vs `ui/src/services/collections-impl.ts` | browser behavior depends on import path |
| GraphQL is partial | only mutations implementation found | dead or incomplete integration surface |

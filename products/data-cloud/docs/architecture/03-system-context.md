# Data Cloud System Context

See [`diagrams/system-context.mmd`](diagrams/system-context.mmd) and [`diagrams/trust-boundaries.mmd`](diagrams/trust-boundaries.mmd).

## Actors And Systems

| Actor/System | Role | Evidence |
|---|---|---|
| Platform/data operators | Use the React UI to manage data, workflows, governance, plugins, and observability | `ui/src/routes.tsx`, `ui/src/pages/**` |
| Product/service callers | Use Data Cloud HTTP/gRPC contracts and SDKs | `docs/openapi.yaml`, `sdk/build.gradle.kts`, `launcher/grpc/DataCloudGrpcServer.java` |
| AEP | Consumes Data Cloud contracts/events and writes results back | `README.md`, `OWNER.md`, `docs/ADR-DC-001-MODULE-OWNERSHIP.md` |
| Storage systems | PostgreSQL, Kafka, Redis, OpenSearch, ClickHouse, S3/Ceph back Data Cloud runtime | `platform-launcher/**`, `platform-plugins/**`, Helm/Terraform |
| Monitoring/GitOps systems | Prometheus scraping, Argo CD delivery, External Secrets, Terraform-managed AWS | `helm/**`, `k8s/**`, `terraform/**` |

## High-Level Responsibility Boundary

**Implemented**
- Data Cloud owns data persistence, event storage/streaming, analytics/reporting, memory persistence, plugin-backed extensibility, governance operations, and model/feature metadata.
- AEP owns agentic orchestration and execution, not Data Cloud runtime control flow.

**Inferred**
- The repo is designed so Data Cloud is the system of record for data and execution metadata even when higher-level agentic systems act on it.

**Missing**
- A single machine-readable context map that ties UI, gateway, runtime, brokers, and AWS resources together in one maintained artifact.

## Trust Boundaries

| Boundary | Implemented Evidence | Notes |
|---|---|---|
| Browser to Data Cloud/API gateway | UI stores bearer tokens client-side and uses `/api/v1` + `/ws` | `ui/src/lib/auth/tokenStorage.ts`, `ui/src/lib/api/client.ts`, `ui/src/lib/websocket/client.ts` |
| Gateway/proxy to standalone launcher | OpenAPI notes bearer token via security gateway proxy; standalone launcher can also run directly | `docs/openapi.yaml`, `launcher/DataCloudLauncher.java` |
| HTTP transport to internal handlers | `DataCloudSecurityFilter` provides API-key auth, tenant isolation, policy, and audit middleware when configured | `launcher/http/DataCloudSecurityFilter.java` |
| Runtime to storage/brokers | Runtime connects to DB, Kafka, OpenSearch, ClickHouse, S3/Ceph, Redis | runtime code + Helm/Terraform |
| Cluster to secrets backend | ExternalSecret and SecretStore abstractions | `helm/.../externalsecret.yaml`, `k8s/external-secret.yaml`, `k8s/secret-store.yaml` |

## What Data Cloud Owns Vs Does Not Own

### Owns

- Multi-tenant entity and collection metadata
- Event log persistence and streaming interfaces
- Analytics query/report execution
- Pipeline/checkpoint/memory persistence surfaces
- Feature ingestion and model metadata endpoints
- Governance, retention, redaction, and compliance summaries

### Does Not Own

- Agentic planning/orchestration runtime
- External user identity provider logic beyond consumed gateway/security abstractions
- Consumer-product business workflows outside Data Cloud’s persistence and query scope

## Major Data / Control / Event Flow

**Implemented**
1. UI or product callers hit HTTP or gRPC endpoints exposed by `launcher`.
2. `launcher/http/DataCloudHttpServer.java` delegates to feature handlers.
3. Handlers call `DataCloudClient`, analytics engines, brain/learning bridges, or feature/model services.
4. Runtime and plugins persist or read from PostgreSQL, Kafka, OpenSearch, ClickHouse, Redis, object storage, and embedded stores.
5. Real-time updates fan back out via SSE and WebSocket routes.

**Inferred**
- In integrated deployments, an upstream security gateway likely enforces bearer auth while Data Cloud still enforces tenant and route-level controls when configured.

## Context Commentary

**Implemented**
- The most important repo-specific boundary is one-way Data Cloud consumption by AEP, not reciprocal imports.

**Recommended**
- Keep future architecture decisions aligned with that product boundary and avoid importing AEP runtime code into Data Cloud modules.

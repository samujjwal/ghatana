# Feature Mapping

See [05-frontend-architecture.md](05-frontend-architecture.md), [06-backend-architecture.md](06-backend-architecture.md), [09-api-contract-architecture.md](09-api-contract-architecture.md), [10-event-architecture.md](10-event-architecture.md), and [`diagrams/sequence-http-entity-crud.mmd`](diagrams/sequence-http-entity-crud.mmd).

## Feature Inventory

| Feature | User Entry Point | Frontend Modules | Backend Modules | Data / Events / Tests | Status |
|---|---|---|---|---|---|
| Data explorer and collection management | `/data`, legacy `/collections` aliases in `ui/src/routes.tsx` | `ui/src/pages/DataExplorer.tsx`, `ui/src/hooks/useCollections.ts`, `ui/src/lib/api/collections.ts` | `launcher/http/DataCloudHttpServer.java`, `launcher/http/handlers/EntityCrudHandler.java`, `platform-entity/**`, `spi/EntityStore.java` | `meta_collections`, entity tables, `ui/e2e/collections.spec.ts`, launcher HTTP tests | active with drift |
| Generic entity CRUD and validation | UI data pages and direct API clients | `ui/src/lib/api/client.ts`, `ui/src/lib/api/collections.ts`, `ui/src/api/client.ts` | `EntityCrudHandler`, `EntityValidationHandler`, `EntitySchemaValidator`, `CollectionRepository`, `EntityRepository` | `/api/v1/entities/**`, `V002__create_entities_table.sql`, `V004__create_collections_metadata.sql` | active |
| Event ingestion and replay | `/events`, SSE `/events/stream`, gRPC clients | `ui/src/pages/EventExplorerPage.tsx`, `ui/src/lib/websocket/client.ts` | `EventHandler`, `SseStreamingHandler`, `DataCloudGrpcServer`, `EventLogGrpcService`, `EventQueryGrpcService`, `spi/EventLogStore.java` | `/api/v1/events`, `/api/v1/events/:offset`, Kafka/OpenSearch plugins, gRPC tests | active with spec drift |
| Pipelines and checkpoints | `/pipelines` | `ui/src/pages/WorkflowsPage.tsx`, `ui/src/pages/WorkflowDesigner.tsx`, `ui/src/lib/api/workflows.ts` | `PipelineCheckpointHandler`, pipeline/checkpoint services in `platform-launcher` and DTOs in `platform-api` | `/api/v1/pipelines/**`, `/api/v1/checkpoints/**`, workflow persistence types in `platform-entity` | active with naming drift |
| Memory plane and brain/learning APIs | `/memory`, implicit AI interactions | `ui/src/pages/MemoryPlaneViewerPage.tsx`, `ui/src/pages/IntelligentHub.tsx` | `MemoryPlaneHandler`, `BrainHandler`, `LearningHandler`, `DataCloudBrainModule`, `DataCloudLearningBridge` | `/api/v1/memory/**`, `/api/v1/brain/**`, `/api/v1/learning/**`, `V016__create_memory_namespaces.sql` | active, optional by env |
| Analytics, reports, and insights | `/insights`, `/query` | `ui/src/pages/InsightsPage.tsx`, `ui/src/pages/SqlWorkspacePage.tsx` | `AnalyticsHandler`, `AnalyticsQueryEngine`, `ReportService`, `platform-analytics/**` | `/api/v1/analytics/**`, `/api/v1/reports/**`, ClickHouse/OpenSearch integrations | active |
| Governance, retention, privacy, trust center | `/trust` | `ui/src/pages/TrustCenter.tsx` | `DataLifecycleHandler`, policy/security helpers, privacy/compliance endpoints | `/api/v1/governance/**`, audit hooks, retention classification endpoints | active but uneven |
| AI assist, model registry, feature store | `/insights`, `/query`, `/pipelines` hints | UI pages call analytics/query APIs and suggestions indirectly | `AiAssistHandler`, `AiModelHandler`, `AIModelManager`, `FeatureStoreService`, `FeatureStoreIngestLauncher` | `/api/v1/features/**`, `/api/v1/models/**`, `feature-store-ingest/**` tests | active |
| Plugin management | `/plugins`, `/agents` | `ui/src/pages/PluginsPage.tsx`, `ui/src/pages/AgentPluginManagerPage.tsx` | `platform-api/plugin/*`, `platform-plugins/**`, runtime plugin loaders in `platform-launcher` | plugin configs under `config/plugins/**`, plugin registry tests | partial |
| Agent registry persistence | AEP-owned agent APIs, not product-local UI | no direct Data Cloud UI entry point | `agent-registry/**`, `DataCloudAgentRegistry.java`, `RegistryEventPublisher.java` | `agent-registry`, `agent-registry-events`, release/rollout/evaluation/memory tables, module tests | active, provider-only |

## End-To-End Feature Mapping

### Data Explorer and Collections

**Implemented**
- Route ownership is in `ui/src/routes.tsx` under `/data`, while older collection-focused pages remain reachable through legacy aliases.
- The current canonical collection adapter is `ui/src/lib/api/collections.ts`, which maps collections onto the generic entity endpoint `/api/v1/entities/dc_collections`.
- The live backend path is assembled in `launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudHttpServer.java` and delegated to `EntityCrudHandler`.
- Domain ownership is split between `platform-entity/src/main/java/com/ghatana/datacloud/entity/MetaCollection.java`, `CollectionRepository.java`, and migration `platform-launcher/src/main/resources/db/migration/V004__create_collections_metadata.sql`.

**Inferred**
- The product is moving from bespoke collection endpoints toward “collection metadata as entities in a reserved collection” because the new UI adapter already uses `/entities/dc_collections` while older clients still expect `/collections`.

**Missing**
- `ui/e2e/collections.spec.ts` still mocks `**/api/v1/collections*` and drives legacy `/collections` pages, so the current `/data` flow is not the dominant E2E path.

**Recommended**
- Treat `ui/src/lib/api/collections.ts` plus `launcher/http` as the canonical flow, then either delete or redirect the older `src/api` and `src/services` variants.

### Events and Streaming

**Implemented**
- HTTP routes `/api/v1/events`, `/api/v1/events/:offset`, `/events/stream`, and `/ws` live in `DataCloudHttpServer.java`.
- gRPC transport is implemented in `launcher/src/main/java/com/ghatana/datacloud/launcher/grpc/DataCloudGrpcServer.java`.
- Storage contracts live in `spi/src/main/java/com/ghatana/datacloud/spi/EventLogStore.java`.
- Streaming backends are implemented through `platform-plugins` and event durability/runtime code in `platform-launcher`.

**Inferred**
- HTTP and gRPC event surfaces target different client classes: browser/admin tooling for HTTP and higher-throughput service integration for gRPC.

**Missing**
- `products/data-cloud/scripts/check-openapi-drift.sh --warn-only` reports `/api/v1/events/{offset}` as live in code but absent from `products/data-cloud/docs/openapi.yaml`.

**Recommended**
- Make the event log contract one of the first contract-parity targets because it spans HTTP, gRPC, SSE, and worker consumption.

### Pipelines and Checkpoints

**Implemented**
- UI routes under `/pipelines` are defined in `ui/src/routes.tsx`.
- Workflow client calls are in `ui/src/lib/api/workflows.ts`.
- Runtime ownership is in `PipelineCheckpointHandler` and storage types in `platform-entity`.

**Inferred**
- “Workflow” in UI terminology maps onto “pipeline” in transport terminology. This is why the route is `/pipelines` while the old mental model and page names still use workflow language.

**Missing**
- There is no single canonical document or module that states whether “workflow” and “pipeline” are aliases or separate concepts.

**Recommended**
- Standardize naming across UI, DTOs, and storage objects so architectural ownership is obvious.

### Memory Plane, Brain, and Learning

**Implemented**
- Optional brain and learning startup is controlled in `DataCloudHttpLauncherBootstrap.java`.
- Runtime handlers are `MemoryPlaneHandler`, `BrainHandler`, and `LearningHandler`.
- Memory persistence is represented by migration `V016__create_memory_namespaces.sql`.

**Inferred**
- These capabilities are designed as Data Cloud persistence and retrieval surfaces for agentic systems, not as a full agent runtime, matching the product boundary in `README.md` and `OWNER.md`.

**Missing**
- The UI exposes memory browsing, but there is no strong product-local ownership narrative tying memory plane semantics to AEP responsibilities.

**Recommended**
- Document memory retention, TTL, and consistency expectations as part of the product contract, not only as route-level behavior.

### Analytics, Reporting, and Query

**Implemented**
- `/query` and `/insights` are UI entry points in `ui/src/routes.tsx`.
- Runtime handlers are `AnalyticsHandler` and `ReportService`.
- Core engine ownership is `platform-analytics/src/main/java/com/ghatana/datacloud/analytics/AnalyticsQueryEngine.java`.

**Inferred**
- Query and report workloads are intended to run against different backends depending on storage profile, with ClickHouse and OpenSearch forming the analytical side of the runtime matrix.

**Missing**
- The checked-in OpenAPI file under `api/data-cloud-api.openapi.yaml` describes dataset/query endpoints that do not align cleanly with the launcher’s current runtime shape.

**Recommended**
- Collapse the split between `api/data-cloud-api.openapi.yaml` and `docs/openapi.yaml`, then align UI query clients to the surviving contract.

### Governance and Trust Center

**Implemented**
- UI route `/trust` maps to `ui/src/pages/TrustCenter.tsx`.
- Runtime endpoints exist in `DataLifecycleHandler` for retention, privacy, and compliance summaries.
- Security/policy support is present in `DataCloudSecurityFilter.java`.

**Inferred**
- Governance is partly implemented as product-level routes and partly expected to be enforced by upstream gateway or shared platform components.

**Missing**
- `platform-governance` has tests but no checked-in production source, so the shared-library boundary for governance is incomplete.

**Recommended**
- Decide whether governance logic belongs in shared platform packages or in product-local runtime handlers, then finish the extraction.

### Agent Registry and Provider Features

**Implemented**
- `agent-registry/src/main/java/com/ghatana/datacloud/agent/registry/DataCloudAgentRegistry.java` stores agent descriptors in Data Cloud collections and emits registry lifecycle events.
- Supporting repositories exist for releases, rollouts, evaluation results, and memory namespaces.

**Inferred**
- Data Cloud is the durable backend for AEP-owned agent registry operations, not the control-plane owner.

**Missing**
- There is no product-local UI or API surface exposing these registry functions directly.

**Recommended**
- Keep agent-registry documentation explicit that this is a provider subsystem with upstream ownership outside this product.

## Responsibility Split Findings

| Finding | Evidence | Impact |
|---|---|---|
| Feature ownership is split across old and new UI access layers | `ui/src/api/*`, `ui/src/lib/api/*`, `ui/src/services/*` | harder onboarding, stale tests, contract drift |
| HTTP transport ownership is split across launcher and `platform-api` | `launcher/http/**` vs `platform-api/controller/**` | duplicate abstraction layers |
| Several features are env-gated optional runtime paths | `DataCloudHttpLauncherBootstrap.java` for brain, analytics, AI, DB | runtime behavior differs by deployment mode |
| Feature names do not always match contract names | workflows vs pipelines, collections vs generic entities | weak architectural vocabulary |

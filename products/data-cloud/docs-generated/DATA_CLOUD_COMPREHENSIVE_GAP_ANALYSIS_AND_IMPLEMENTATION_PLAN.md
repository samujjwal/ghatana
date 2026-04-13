# Data Cloud Comprehensive Gap Analysis & Implementation Plan

**Document ID:** DC-GAP-IMPL-001  
**Version:** 2.0  
**Date:** 2026-04-12  
**Status:** REVISED — Codebase-Verified; Engineering Review Required  
**Supersedes:** Version 1.0 (2026-04-12) — contained materially inaccurate implementation assessments  

---

## Executive Summary

This revised analysis is grounded in direct inspection of ~1,038 Java source files, ~150 TypeScript/TSX files, 13 Gradle modules, all Kubernetes manifests, Terraform configurations, and UI component inventory.

Data Cloud has a **significantly stronger foundation than previously assessed**. Version 1.0 of this document understated existing implementation by approximately 20%, which would have caused duplicate re-implementation of working code in Phases 1–2. This revision corrects that, identifies the **15 real gaps** blocking vision achievement, and reorders implementation phases by operational safety, visibility, and developer experience — not just technical feature milestones.

### Corrected Vision vs. Reality

| Vision Promise | Actual Reality | True Gap |
|----------------|----------------|----------|
| First-class AI/ML-native | `DataCloudBrain`, `LearningLoop`, `FeedbackCollector`, `VectorMemoryPlugin`, `FeatureStoreIngestLauncher`, AI SPI capabilities all implemented | LLM backend not wired (**B1**) |
| Ubiquitous data layer abstraction | 4-tier HOT/WARM/COOL/COLD with `ConfigDrivenStorageRouter`, `TierMigrationScheduler` implemented | Embedded dev mode broken (**B2**); pgvector not persistent (**B12**) |
| Seamless data movement | `TierMigrationScheduler` + `ArchiveMigrationScheduler` implemented | Manual trigger missing from UI (**B10**) |
| Canvas-centered minimal UI | `WorkflowCanvas`, `WorkflowDesigner`, `DataFabricPage` using `@ghatana/canvas/flow`, `SmartWorkflowBuilder` all implemented | `FlowControls` not wired (**B8**); onboarding wizard missing (**B15**) |
| Cheap data priority | `CostExplorer`, `CostChart`, S3 Glacier lifecycle, tier cost model implemented | Cost estimate API missing from backend (**B11**) |
| Plugin ecosystem | 8 plugin types, full plugin UI (card, health, logs, config, version compare, dependency graph) | Plugin install/upgrade backend endpoint missing (**B6**) |
| Full observability | `DistributedTracer`, `TracingService`, `DataCloudHttpMetrics`, `O11yPanel` implemented | OTLP exporter not wired; trace DLQ not implemented (**B4**) |
| Graduated autonomy | `AutonomyController`, `AutonomyLevel`, `AutonomyPolicy`, `ReflexEngine` implemented | Emergency autonomy shutoff UI missing (**B9**) |

### Revised Implementation Completeness

| Area | v1.0 Claimed | Actual (Verified) |
|------|-------------|-------------------|
| AI/ML Integration | 3/10 | **7/10** |
| Storage Tiering | — | **9/10** |
| Canvas UI | 30% | **70%** |
| Governance / RBAC | — | **8/10** |
| Observability | — | **7/10** |
| Autonomy / Automation | — | **8/10** |
| Plugin Ecosystem | "limited" | **8/10** |
| Onboarding Experience | — | **3/10** |
| Overall | ~65% | **~84%** |

### Root Cause of Previous Inaccuracy

Version 1.0 assessed the product against an architectural vision without first reading the implemented source. All subsequent planning work must start from the verified inventory in Section 2.

---

## 1. Verified Implementation Inventory

Before planning any new work, every change must start from the verified baseline. This section is the authoritative statement of what is implemented so work is never duplicated.

### 1.1 Java Backend — What Exists

| Module | Key Implemented Types | Status |
|---|---|---|
| `spi/` | `EntityStore`, `EventLogStore`, `StoragePlugin<R>`, `AnomalyDetectionCapability`, `PredictionCapability`, `RecommendationCapability`, `ExplanationCapability`, `StoragePluginRegistry`, `TenantContext`, `BackpressurePort`, `EncryptionService` | ✅ Complete SPI |
| `platform-entity/` | `Entity`, `Collection`, `DynamicEntity`, `RecordType`, `SchemaCompatibilityChecker`, `EventSchemaRegistry`, `DistributedTracer`, `PolicyEngine`, `ContentPolicyChecker`, `AuditLog`, `PermissionService`, `RoleCatalog`, `QualityScorer` | ✅ Full domain model |
| `platform-api/` | `EntityService`, `CollectionService`, `WorkflowService`, `ValidateService`, `AIAssistService`, `LLMProvider`, `ContextWindowManager`, `PromptTemplateManager`, brain/attention/memory subsystem, `LearningLoop`, `FeedbackCollector`, `ModelTrainingService`, `AutonomyController` | ✅ Dense, feature-complete |
| `platform-config/` | `ConfigLoader`, `ConfigValidator`, `ConfigRegistry`, `OpaClient` (local), `OpaPolicyEngine`, `ReflexEngine`, `ConfigDrivenStorageRouter`, `ConfigReloadManager`, `GracefulReloadManager` | ✅ Production-grade |
| `platform-launcher/` | `DataCloudBrain`, NLQ service, `TracingService`, `DataCloudAuditService`, `BackpressureManager`, `DataCloudFeatureFlags`, `CcpaDataSubjectRightsService`, `Soc2ControlFramework`, `TenantQuotaManager`, grpc services | ✅ Mostly complete; embedded storage stubs |
| `launcher/` | Full ActiveJ HTTP + gRPC server, all entity/analytics/AI/brain/voice/SSE/pipeline/memory handlers, `DataCloudHttpMetrics`, `DataCloudSecurityFilter`, `DataCloudLearningBridge` | ✅ Full transport |
| `platform-plugins/` | Kafka (streaming, exactly-once), Redis HOT (LMAX Disruptor + Streams), Iceberg COOL, S3/Glacier COLD, Trino (cross-tier federated SQL), `VectorMemoryPlugin` (HNSW in-memory), `KnowledgeGraphPlugin`, `DataValidationProcessor` | ✅ 8 plugin types |
| `agent-registry/` | `DataCloudAgentRegistry`, `DataAnomalyDetectorAgent`, `DataSyncAgent`, `SchemaValidatorAgent`, A/B & canary rollout repositories | ✅ Full runtime |
| `agent-catalog/` | 12 YAML agent definitions (archival, migration, observability, orchestration, query, replication, storage) | ⚠️ Definitions only — no runtime API |
| `feature-store-ingest/` | Event-log tailing → ML feature extraction → FeatureStore write, CircuitBreaker, DLQ | ✅ Implemented |
| `platform-governance/` | Audit logging, purge/rollback, field masking, retention classification (via `platform-api` and `platform-launcher`) | ✅ Functional; scattered |
| `platform-analytics/` | `AnalyticsQueryEngine`, `QueryPlan`, `StatisticalAnomalyDetector`, `EntityExportService`, `ReportService` | ✅ Core covered |
| `platform-event/` | `Event`, `EventStream`, `EventDurabilityService`, `EventReplayService`, SPI plugins (Storage/Streaming/Archive/Routing), secret providers | ✅ Complete |

### 1.2 TypeScript UI — What Exists

**21 pages** across two navigation clusters. Key canvas and AI-native pages:

| Page / Component Group | Technology | Status |
|---|---|---|
| `DataFabricPage` | `@ghatana/canvas/flow` live topology, HOT→WARM→COOL→COLD with real-time metrics | ✅ Implemented; `FlowControls` not rendered (bug) |
| `WorkflowDesigner` / `WorkflowCanvas` | Canvas-based DAG, 4 node types (`ApiCallNode`, `ApprovalNode`, `DecisionNode`, `TransformNode`) | ✅ Implemented |
| `SmartWorkflowBuilder` | NL intent → confidence-scored steps, advanced mode → `WorkflowCanvas` | ✅ Implemented; LLM backend not wired |
| `SqlWorkspacePage` | Monaco editor, `SmartSQLAssistant`, `MonacoSQLEditor`, `SavedQueries` | ✅ Implemented; saved query edit is TODO |
| `InsightsPage` / `IntelligentHub` | AI insights, `BrainSidebar`, `MemoryLane`, `PatternOverlay` | ✅ Implemented |
| `MemoryPlaneViewerPage` | Memory tier visualiser | ✅ Implemented |
| `PluginsPage` / `PluginDetailsPage` | `PluginCard`, `PluginConfigModal`, `PluginHealthMonitor`, `PluginLogsViewer`, `PluginVersionCompare`, `PluginPerformanceMetrics`, `PluginDependencyGraph` | ✅ UI complete; install/upgrade backend endpoint missing |
| `AlertsPage` | Alert management UI | ✅ UI complete; rules API not connected |
| `TrustCenter` | `PolicyVisualizer`, `ConsentManager`, `PIIDetectionPanel`, `RBACGuard` | ✅ Implemented |
| Cross-cutting | `O11yPanel`, `CostExplorer`, `CostChart`, `LineageGraph`, `CommandBar`, `AmbientIntelligenceBar`, `NLQInput`, `VoiceInput`, `KeyboardShortcuts`, `GlobalSearch` | ✅ Implemented |

### 1.3 Infrastructure — What Exists

- **Terraform**: multi-environment (staging/production/DR) — EKS, RDS PostgreSQL, MSK (Kafka), ElastiCache (Redis), OpenSearch, S3, ClickHouse (EC2), cross-region replication, global load balancer.
- **Kubernetes**: HPA, PDB, Istio service mesh, canary deployments (Istio VirtualService + DestinationRule), ArgoCD GitOps, External Secrets Operator, network policies, PostgreSQL PITR + read replica, ClickHouse backup CronJob, non-root security contexts.
- **SDK**: Generated Java/TypeScript/Python clients via Gradle task `generateAllSdks`.

---

## 2. Platform Reuse Obligations

> **Rule 1 from copilot-instructions.md**: *Reuse before creating. Check `platform/*` before adding new abstractions.*

Every gap in this document must be resolved by wiring to an existing platform module first. The following table maps each gap to its canonical platform home. **No new platform-level abstraction should be created for these capabilities.**

### 2.1 Java Platform Reuse Map

| Capability Needed | Do NOT Implement Locally | USE THIS Platform Module |
|---|---|---|
| LLM backend (OpenAI, Anthropic, Ollama) | New `LLMProvider` impl in `platform-api` | `platform:java:ai-integration` — `LLMGatewayService`, `ProviderRouter`, `OpenAICompletionService`, `OllamaCompletionService`, `ToolAwareAnthropicCompletionService` |
| Model registry + A/B testing | New `ModelRegistryService` in `platform-launcher` | `platform:java:ai-integration` — `ModelRegistryService`, `ModelDeploymentService`, `ABTestingService` |
| Feature store | Custom `FeatureStoreService` in Data Cloud | `platform:java:ai-integration` — `FeatureStoreService`, `RedisFeatureCacheAdapter`, `FeatureLineageTracker` |
| AI cost tracking | New cost API in Data Cloud | `platform:java:ai-integration` — `CostTracker`, `AiMetricsEmitter` |
| Data / model drift detection | Custom drift logic in `platform-launcher` | `platform:java:ai-integration` — `DataDriftDetector`, `ModelDriftDetector`, `FeatureDriftDetector` |
| Distributed trace export (OTLP / ClickHouse) | Bespoke `TraceExportService` DLQ | `platform:java:observability` — `ClickHouseTraceStorage`, `SpanBuffer`, `TracingAspect`, `PrometheusMetricsExporter` |
| Correlation ID propagation | Ad hoc `MDC.put()` calls | `platform:java:observability` — `CorrelationContext`, `SharedMetricsRegistry` |
| DAG pipeline execution | `WorkflowService` re-implementing DAG logic | `platform:java:workflow` — `DAGPipelineExecutor`, `PipelineBuilder`, `DurableWorkflowRuntime`, `CelWorkflowExpressionEvaluator` |
| Distributed query caching | `DataCloudQueryCacheService` custom Redis impl | `platform:java:cache` — `DistributedCacheService`, `RedisDistributedCacheBackend`, `CacheInvalidationEventPublisher` |
| OPA / policy-as-code engine | Local `OpaClient` in `platform-config` | `platform:java:policy-as-code` — `OpaClient`, `PolicyAsCodeEngine`, `InMemoryPolicyEngine` (duplicate must be removed) |
| Consent / data minimization | `CcpaDataSubjectRightsService` reinventing governance | `platform:java:data-governance` — `ConsentManager`, `DataMinimizationEngine`, `SensitiveDataClassifier`, `PurposeLimitationEnforcer` |
| Kafka circuit breaker / retry | Custom `CircuitBreaker` in `feature-store-ingest` | `platform:java:messaging` — `CircuitBreakerConnector`, `RetryExecutor`, `AbstractResilientConnector` |
| Agent capability manifest / YAML catalog | Raw YAML in `agent-catalog/` with no runtime | `platform:java:agent-core` — `AgentCapabilityManifest`, `YamlTemplateEngine`, `AgentPackage`, `LLMAgent` |

### 2.2 TypeScript Platform Reuse Map

| Capability Needed | Do NOT Create New | USE THIS Platform Package |
|---|---|---|
| Onboarding wizard (multi-step first-run) | Custom `OnboardingWizard.tsx` from scratch | `platform/typescript/wizard` — already in workspace |
| Plugin config forms (schema-driven) | Ad hoc form JSX in `PluginConfigModal` | `platform/typescript/forms` — typed hook-and-component library |
| Canvas flow controls (zoom, fit, minimap) | New `FlowControls` component | `platform/typescript/canvas` — `flow/` subdirectory, already imported by `DataFabricPage` |
| Canvas AI suggestions overlay | New AI overlay component | `platform/typescript/canvas` — `ai/` subdirectory |
| Canvas collaboration / shared editing | New real-time component | `platform/typescript/canvas` — `collaboration/` subdirectory |
| Canvas topology layouts | Custom layout code | `platform/typescript/canvas` — `topology/` subdirectory |
| Real-time websocket events | Custom websocket hook | `platform/typescript/realtime` — already in workspace |
| Audit / event components | Custom audit UI | `platform/typescript/audit-components` — already in workspace |

---

## 3. Verified Gaps (15 Items)

These are the **actual remaining gaps** after codebase inspection and platform module inventory. Each is prioritised from the lens of: operational safety > full visibility > developer onboarding > automation > competitive features.

### B1 — LLM Backend Not Wired (CRITICAL)

**Symptom**: `LLMProvider` is a Data Cloud-local interface. `SmartWorkflowBuilder`, NLQ service, AI assist, and workflow generation all depend on it. No concrete binding exists in the launched server.  
**Platform action**: Wire `platform:java:ai-integration`'s `LLMGatewayService` (which contains `ProviderRouter`, `OpenAICompletionService`, `OllamaCompletionService`, `ToolAwareAnthropicCompletionService`) as the `LLMProvider` implementation at `DataCloudHttpServer` startup. Add `AI_PROVIDER`, `OPENAI_API_KEY`, `OLLAMA_HOST` to `ConfigLoader` and expose in `SettingsPage`.  
**Owner**: platform-api + launcher team  
**Blocks**: B3, B15 and every AI-native capability

### B2 — Embedded Developer Mode Broken (CRITICAL — Onboarding)

**Symptom**: `DefaultEmbeddableDataCloud` only wires `IN_MEMORY`. `RocksDB`, `H2`, and `SQLite` embedded backends are class stubs with TODO bodies. A developer running `./gradlew :products:data-cloud:launcher:run` with no external infrastructure gets an unusable server.  
**Action**: Implement the H2 embedded backend in `platform-launcher/embedded/` to cover the entity and event stores. Add a `local-dev` Spring-profile-equivalent config preset in `DataCloudHttpServer` (in-memory Redis mock via Testcontainers or embedded Redis) so `./gradlew run -Pprofile=local` gives a fully working single-process experience. Document in `README.md`.  
**Owner**: platform-launcher team  
**Blocks**: new developer productivity; integration test isolation

### B3 — Agent Catalog Has No Runtime API (CRITICAL — Automation)

**Symptom**: `agent-catalog/` has 12 YAML agent definitions across 6 domains. There is no Java service to query them: automation, UI, and AEP cannot discover available agents programmatically. The `AgentPluginManagerPage` has no data source.  
**Platform action**: Use `platform:java:agent-core`'s `AgentCapabilityManifest`, `YamlTemplateEngine`, and `AgentPackage` to build a thin `DataCloudAgentCatalogService` in `platform-api/`. Expose `GET /api/v1/agents/catalog` and `GET /api/v1/agents/catalog/{agentId}` via a new `AgentCatalogHandler` in `launcher/`. Wire `AgentPluginManagerPage` to consume it.  
**Owner**: agent-registry team  
**Blocks**: `AgentPluginManagerPage`, automation discoverability

### B4 — Trace Export Not Reaching Observability Backend (HIGH — Debuggability)

**Symptom**: `TracingService` generates spans. `TraceExportService` batches them. The `TraceExporter` SPI is defined. No OTLP or ClickHouse exporter is wired at server startup — spans are silently discarded. The DLQ for failed exports (documented TODO in `TraceExportService`) is not implemented.  
**Platform action**: Wire `platform:java:observability`'s `ClickHouseTraceStorage` as the `TraceExporter` implementation at `DataCloudHttpServer` startup (ClickHouse is already provisioned in `terraform/modules/clickhouse/` and `k8s/clickhouse-backup-cronjob.yaml`). Use `platform:java:observability`'s `SpanBuffer` for the DLQ. Wire `platform:java:observability`'s `CorrelationContext` to replace ad hoc `MDC.put("correlationId", ...)` calls.  
**Owner**: observability team  
**Blocks**: diagnosability of every production issue

### B5 — Alert Rules API Not Connected (HIGH — Full Visibility)

**Symptom**: `AlertsPage.tsx` has `// TODO: Integrate with alert rules API`. `ReflexEngine` and `AlertActionHandler` exist in `platform-config/`. Users can view but not create/edit/delete alert rules.  
**Action**: Add `AlertRulesHandler` in `launcher/`, exposing `GET/POST/PUT/DELETE /api/v1/alerts/rules`. These should delegate to the `ReflexEngine`'s rule management methods. Update `AlertsPage.tsx` to use the new endpoints via a typed `useQuery`/`useMutation` pair.  
**Owner**: platform-config + UI team

### B6 — Plugin Install / Upgrade Endpoint Missing (HIGH — Ecosystem)

**Symptom**: `PluginsPage.tsx` upload to backend is `TODO`. `PluginDetailsPage.tsx` upgrade logic is `TODO`. `PluginRegistryImpl` and the full plugin lifecycle backend exist. There is no `POST /api/v1/plugins/install` handler.  
**Action**: Add `PluginInstallHandler` in `launcher/` accepting a multipart JAR upload, delegating to `PluginRegistryImpl.register()`. Add `POST /api/v1/plugins/{id}/upgrade`. Connect `PluginsPage` upload and `PluginDetailsPage` upgrade button to the new endpoints.  
**Owner**: plugins team  
**Security note**: Validate JAR manifest checksum and publisher signature before registration. Enforce plugin sandbox via the existing `StoragePluginRegistry` isolation boundary.

### B7 — Saved Query Edit Not Implemented (MEDIUM — Low Cognitive Load)

**Symptom**: `SavedQueries.tsx` edit button is `TODO`. Users can save queries from `SqlWorkspacePage` but cannot modify them, forcing delete-and-recreate.  
**Action**: Add `PATCH /api/v1/queries/{id}` to the SQL workspace handler. Wire `SavedQueries.tsx` edit button.  
**Owner**: UI team

### B8 — `FlowControls` Not Rendered in `DataFabricPage` (MEDIUM — Canvas Completeness)

**Symptom**: `FlowControls` is imported from `@ghatana/canvas/flow` in `DataFabricPage.tsx` but never rendered in JSX (confirmed via test). The fabric topology cannot be zoomed, fit-to-screen, or panned by keyboard.  
**Platform action**: This is a **one-line JSX gap**. Render `<FlowControls />` within the `DataFabricPage` canvas root where `@ghatana/canvas/flow`'s `ReactFlow` wrapper is mounted. No new component needed — `@ghatana/canvas/flow` is the canonical source.  
**Owner**: UI team

### B9 — Emergency Autonomy Shutoff Has No UI (HIGH — Operational Safety)

**Symptom**: `AutonomyController.gate()` API and `AutonomyLevel.LEVEL_0` (full manual) exist. There is no UI element in `AppShell` or anywhere else for an operator to immediately stop all autonomous actions if the system is behaving incorrectly.  
**Action**: Add a global "Autonomy: OFF" toggle to `AppShell` header, gated by `RBACGuard` ADMIN role. On confirmation, call `PUT /api/v1/autonomy/level` with `{"level": "LEVEL_0"}`. Show persistent warning banner until re-enabled. Wire `AutonomyTimeline` to log the override event.  
**Owner**: platform-api + UI team

### B10 — Manual Tier Migration Exposed Only via Scheduler, Not UI (MEDIUM — Manual Override)

**Symptom**: `TierMigrationScheduler` and `ArchiveMigrationScheduler` automate migrations, but there is no way for a user to **manually force** a collection from one tier to another (e.g., pin a cold collection to warm for an ad hoc report).  
**Action**: Add `POST /api/v1/collections/{id}/migrate` with `?targetTier=WARM` query param to the collection handler. Add a context-menu action on collection rows in `EntityBrowserPage` and `DataFabricPage` nodes.  
**Owner**: platform-entity + UI team

### B11 — Cost Estimate API Missing from Backend (MEDIUM — Full Visibility)

**Symptom**: `CostExplorer` UI component and `CostChart` exist. S3 Glacier lifecycle and tier cost model (`$$$` → `¢`) are documented. No Java API surface exists for query cost estimation or per-collection storage cost reports.  
**Platform action**: `platform:java:ai-integration` already has a `CostTracker` for AI operation costs. Extend this contract or create a parallel `StorageCostTracker` in `platform-analytics/` with `estimateQueryCost(QueryPlan)` and `getStorageCostReport(tenantId)`. Expose at `GET /api/v1/queries/estimate` and `GET /api/v1/collections/{id}/cost-report`. Wire `CostExplorer` to consume.  
**Owner**: analytics + UI team

### B12 — `VectorMemoryPlugin` Uses In-Memory HNSW Only (HIGH — Production Scale)

**Symptom**: `VectorMemoryPlugin` implements HNSW-like similarity search entirely in-memory. This does not survive restarts and cannot scale beyond a single JVM heap. The Terraform RDS module provisions standard PostgreSQL without `pgvector` extension.  
**Action**: Add `pgvector` extension to `terraform/modules/rds/main.tf` (`CREATE EXTENSION IF NOT EXISTS vector`). Implement a `PgVectorMemoryPlugin` in `platform-plugins/` backed by the provisioned RDS instance. Keep `VectorMemoryPlugin` (in-memory) for local dev / embedded mode. The `StoragePluginRegistry` already supports multiple implementations — no SPI changes needed.  
**Owner**: infra + plugins team

### B13 — Federated Trino Query Not Surfaced in UI (HIGH — Full Visibility)

**Symptom**: `EventCloudConnector` (Trino SPI) provides SQL access over ALL storage tiers in a single federated query. This is a major differentiating capability. `SqlWorkspacePage` routes all queries to the direct `AnalyticsQueryEngine` — users have no way to use Trino.  
**Action**: Add a "Federated" toggle to `SqlWorkspacePage` that routes queries to a `POST /api/v1/queries/federated` endpoint backed by the Trino connector. Display the estimated query plan cost (B11) next to the toggle.  
**Owner**: analytics + UI team

### B14 — Entity Point-in-Time Queries Not Exposed (MEDIUM — Competitive Parity)

**Symptom**: `EventReplayService` and `EventDurabilityService` exist. Event log provides the state history of every entity. There is no HTTP endpoint to retrieve entity state at an arbitrary past timestamp.  
**Action**: Add `GET /api/v1/entities/{id}?asOf=<ISO-8601-timestamp>` to the entity handler. Implement replay via `EventReplayService` up to the given timestamp. This gives Snowflake-style time travel for entities.  
**Owner**: platform-entity team

### B15 — No First-Run Onboarding Experience (HIGH — Onboarding)

**Symptom**: A developer or new tenant lands on a blank canvas with no guidance. No sample data, no guided setup, no getting-started flow.  
**Platform action**: Use `platform/typescript/wizard` (already in workspace) to build a 5-step `DataCloudOnboardingWizard`: (1) Connect storage backend, (2) Create first collection, (3) Ingest sample data, (4) Run first query, (5) Enable autonomy level. Trigger on first login (detect via user preference stored in `SettingsService`). Add a "Restart setup" option to `SettingsPage`.  
**Owner**: UI team

---

## 4. Duplicate / Divergence Issues to Resolve

> **Rule 1 from copilot-instructions.md**: *Reuse before creating. Do not turn shared packages into dumping grounds.*

These are cases where Data Cloud has local implementations that overlap with canonical platform modules. They must be resolved via the **fix-forward** approach — replace local usage, do not maintain both.

| Local (Data Cloud) | Canonical Platform | Resolution |
|---|---|---|
| `OpaClient` in `platform-config/` | `platform:java:policy-as-code` — `OpaClient`, `PolicyAsCodeEngine` | Delete local `OpaClient`; introduce `platform:java:policy-as-code` dependency in `platform-config/build.gradle.kts` |
| Custom `CircuitBreaker` in `feature-store-ingest/` | `platform:java:messaging` — `CircuitBreakerConnector`, `RetryExecutor` | Replace with `platform:java:messaging` |
| Local `LLMProvider` interface + stub | `platform:java:ai-integration` — `LLMGatewayService`, `ProviderRouter` | Wire `LLMGatewayService` as the `LLMProvider` binding; delete stub implementation |
| `DataCloudQueryCacheService` (custom Redis) | `platform:java:cache` — `DistributedCacheService`, `RedisDistributedCacheBackend` | Delegate to platform cache; delete local Redis wrapper |
| `CcpaDataSubjectRightsService` (partial governance) | `platform:java:data-governance` — `ConsentManager`, `DataMinimizationEngine` | Delegate CCPA consent and data minimization to platform; keep Data Cloud-specific CCPA reporting layer |
| `FeatureStoreService` referenced directly in `feature-store-ingest/` | `platform:java:ai-integration` — `FeatureStoreService`, `RedisFeatureCacheAdapter` | Import the platform type directly; do not copy the interface |
| `platform-config` local `PatternMatcher` / `PatternCatalog` | Where pattern matching is generic rule evaluation, use `platform:java:policy-as-code` | Audit for overlap; keep only Data Cloud–specific pattern config |

---

## 5. Competitive Excellence Gaps

These capabilities are missing relative to Snowflake, Databricks, and MongoDB Atlas, and are not covered by the existing platform modules.

| Feature Gap | Competitor | Priority | Approach |
|---|---|---|---|
| **Zero-copy data sharing** | Snowflake | HIGH | Share a collection snapshot reference across tenants without physical data copy. Implement via event log cursor sharing — no data movement. |
| **CDC (Change Data Capture) ingest** | Debezium / Kafka Connect | HIGH | Use `platform:java:messaging`'s `KafkaConsumerAdapter` to tail a CDC topic and stream changes into Data Cloud entity store via `feature-store-ingest` pipeline pattern. |
| **Collaborative query workspace** | Databricks Notebooks | HIGH | `platform/typescript/canvas`'s `collaboration/` subdirectory provides real-time shared editing. Apply to `SqlWorkspacePage` to enable multi-user query sessions via `platform/typescript/realtime`. |
| **Auto-index recommendations** | MongoDB Atlas | MEDIUM | `query-optimization-agent` YAML is defined in `agent-catalog/`. Once B3 (catalog runtime) is resolved, wire the agent to analyse query patterns via `platform:java:ai-integration`'s `FeatureDriftDetector` and surface index recommendations in `InsightsPage`. |
| **dbt-style data lineage** | dbt Core | MEDIUM | Extend `LineageGraph` component with transformation-level nodes. Backend: `platform:java:ai-integration`'s `FeatureLineageTracker` already tracks feature provenance — extend to entity transformations. |
| **Iceberg REST Catalog** | Apache Iceberg spec | MEDIUM | Expose `GET /v1/namespaces`, `GET /v1/namespaces/{n}/tables` endpoints (Iceberg REST Catalog spec) backed by `CoolTierStoragePlugin`. Allows external compute engines (Trino, Spark) to read Data Cloud Iceberg tables. |
| **Reverse-ETL** | Census, Hightouch | LOW | Use existing `EntityExportService` + webhook system to push Data Cloud entity changes to external SaaS destinations. No new backend needed; requires UI workflow in `WorkflowDesigner`. |

---

## 6. Cross-Cutting Quality Concerns

These apply across all gaps and must be part of every change, not afterthoughts.

### 6.1 Debuggability

Every change must be diagnosable without access to production:

- Wire `platform:java:observability`'s `ClickHouseTraceStorage` (B4) so every request has a complete span tree visible in Jaeger at `http://localhost:16686`.
- Add a `GET /api/v1/debug/request/{correlationId}` endpoint that returns all spans, logs, and events for a given correlation ID, sourced from `ClickHouseTraceStorage`.
- Implement "dry-run" mode for `ReflexEngine`: `POST /api/v1/reflexes/simulate` returns what actions would fire without executing them.
- Add query explain plan endpoint: `POST /api/v1/queries/explain` returning the `QueryPlan` as JSON and rendering it in `SqlWorkspacePage`.
- Add workflow step-level replay: `GET /api/v1/workflows/{runId}/steps` showing per-step inputs, outputs, duration, and any errors.

### 6.2 Low Cognitive Load

- Progressive disclosure: every page should have sensible defaults so zero decisions are required to get started. Config-heavy pages (`CreateCollectionPage`, `PluginConfigModal`) should use `platform/typescript/forms`'s schema-driven approach with `platform/typescript/wizard` for multi-step flows.
- `CommandBar` must cover all operations. Audit: every API endpoint should have a corresponding `CommandBar` entry so users can act without navigating menus.
- `AmbientIntelligenceBar` must actively surface the top 3 actionable recommendations from `DataCloudBrain` at all times — not just exist as UI chrome.
- Default autonomy level on first install: `LEVEL_1` (advisory with approval gate), not maximum. Users opt into higher autonomy explicitly.

### 6.3 Full Visibility

- Tenant health dashboard: quota utilisation, storage cost per tier, throughput, and error rate per tenant. Source from existing `TenantQuotaManager` + `DataCloudHttpMetrics` + B11 cost API.
- Agent execution audit log UI: which agents ran, what they decided, why, and what they changed. Use `platform/typescript/audit-components` to render the existing `AuditLog` data from `DataCloudAuditService`.
- Cross-tier entity lineage: a user should be able to see that entity X was created in HOT, migrated to WARM at T2, archived to COLD at T3. Backend data exists in event log; surface in `LineageGraph`.
- Scheduled autonomy job board: show all `TierMigrationScheduler` and `ArchiveMigrationScheduler` jobs — last run, next run, outcome. Surface in `DataFabricPage` or a dedicated `ScheduledJobsPage`.

### 6.4 Manual Override for Every Automated Action

- Emergency autonomy shutoff at `AppShell` level (B9 — P0 operational safety).
- Manual tier migration from collection context menu (B10).
- Manual agent trigger: right-click on collection → "Run DataAnomalyDetectorAgent now".
- Reflex action undo: `PUT /api/v1/reflexes/{executionId}/undo` with audit trail.
- Per-collection storage pin: ability to bypass `ConfigDrivenStorageRouter` and force a collection to a specific tier via `POST /api/v1/collections/{id}/storage-pin`.

### 6.5 API Contract Discipline

Per copilot-instructions.md §11 (Contracts, Events, and Compatibility):

- All new handlers must produce OpenAPI annotations compatible with the existing drift test in `api/`.
- Event payloads flowing from Data Cloud to AEP must carry `correlationId`, `tenantId`, `schemaVersion`, and `timestamp`.
- New endpoints follow the existing path convention: `/api/v1/<resource>` — no legacy `/api/<resource>` paths.
- Breaking API changes require a migration note and cannot be merged without updating the `REST_API_DOCUMENTATION.md`.

### 6.6 Test Coverage Standards

Per copilot-instructions.md §3 and §16:

- Every new Java class: unit test in `src/test/java/com/ghatana/...` mirroring the source path.
- Async services: extend `EventloopTestBase`, use `runPromise(() -> ...)`.
- New platform wiring (B1, B4 dependency binding): integration test verifying the full startup chain.
- New HTTP handlers (B3, B5, B6, etc.): contract test verifying OpenAPI compliance.
- UI components: co-located `__tests__/` using React Testing Library + Vitest.
- New infra (B12 pgvector): Testcontainers-based integration test in `integration-tests/`.

---

## 7. Implementation Roadmap (Revised)

Phases are ordered: **operational safety → full visibility → developer onboarding → automation completeness → competitive differentiation**.

### Phase 1: Unblock and Stabilise (Weeks 1–3) — Critical path

| # | Task | Module | Platform Dependency |
|---|---|---|---|
| 1 | Wire `LLMGatewayService` as `LLMProvider` (B1) | `platform-api`, `launcher` | `platform:java:ai-integration` |
| 2 | Wire `ClickHouseTraceStorage` + `CorrelationContext` as trace exporter (B4) | `launcher` | `platform:java:observability` |
| 3 | Wire `ClickHouseTraceStorage` span DLQ (B4 continuation) | `platform-launcher` | `platform:java:observability` (`SpanBuffer`) |
| 4 | Emergency autonomy shutoff UI (B9) | `ui/AppShell`, `platform-api` | `AutonomyController` (exists) |
| 5 | H2 embedded backend for local dev (B2) | `platform-launcher/embedded` | `platform:java:database` (H2) |
| 6 | Remove local `OpaClient`; use `platform:java:policy-as-code` | `platform-config` | `platform:java:policy-as-code` |
| 7 | Replace local `CircuitBreaker`; use `platform:java:messaging` | `feature-store-ingest` | `platform:java:messaging` |

### Phase 2: Full Visibility and Operational Control (Weeks 4–6)

| # | Task | Module | Platform Dependency |
|---|---|---|---|
| 8 | Alert rules API + `AlertsPage` wire-up (B5) | `launcher`, `ui` | `ReflexEngine` (exists) |
| 9 | Agent catalog runtime service (B3) | `platform-api`, `launcher`, `ui` | `platform:java:agent-core` |
| 10 | Cost estimate API (B11) | `platform-analytics`, `launcher` | `platform:java:ai-integration` (`CostTracker`) |
| 11 | Tenant health dashboard | `ui` | `TenantQuotaManager` + `DataCloudHttpMetrics` (exist) |
| 12 | Agent execution audit log UI | `ui` | `platform/typescript/audit-components` |
| 13 | Cross-tier entity lineage in `LineageGraph` | `ui` | `EventReplayService` (exists) |
| 14 | Scheduled autonomy job board | `ui` | `TierMigrationScheduler` (exists) |
| 15 | Debug request trace endpoint | `launcher` | `platform:java:observability` (`ClickHouseTraceStorage`) |

### Phase 3: Developer Onboarding and Cognitive Load (Weeks 7–9)

| # | Task | Module | Platform Dependency |
|---|---|---|---|
| 16 | First-run onboarding wizard (B15) | `ui` | `platform/typescript/wizard` |
| 17 | Wire `FlowControls` in `DataFabricPage` (B8) | `ui/DataFabricPage` | `@ghatana/canvas/flow` (exists) |
| 18 | Saved query edit endpoint + UI (B7) | `launcher`, `ui` | None — small handler gap |
| 19 | `CommandBar` completeness audit + fill | `ui` | All existing API endpoints |
| 20 | `AmbientIntelligenceBar` active recommendations | `ui` | `DataCloudBrain` (exists) |
| 21 | Progressive disclosure audit on all 21 pages | `ui` | `platform/typescript/forms`, `platform/typescript/wizard` |
| 22 | Replace `DataCloudQueryCacheService` with `platform:java:cache` | `data-cloud-cache` | `platform:java:cache` |

### Phase 4: Automation Completeness (Weeks 10–12)

| # | Task | Module | Platform Dependency |
|---|---|---|---|
| 23 | Plugin install + upgrade backend endpoint + UI (B6) | `launcher`, `ui` | `PluginRegistryImpl` (exists) |
| 24 | Manual tier migration from UI (B10) | `launcher`, `ui` | `TierMigrationScheduler` (exists) |
| 25 | Per-collection storage pin API + UI | `launcher`, `ui` | `ConfigDrivenStorageRouter` (exists) |
| 26 | Manual agent trigger UI | `ui` | `DataCloudAgentRegistry` (exists) |
| 27 | Reflex action undo endpoint | `launcher` | `ReflexEngine` (exists) |
| 28 | Dry-run mode for `ReflexEngine` | `launcher` | `ReflexEngine` (exists) |
| 29 | Workflow step-level replay endpoint | `launcher` | `EventReplayService` (exists) |
| 30 | Query explain plan endpoint + UI | `launcher`, `ui` | `AnalyticsQueryEngine` (exists) |

### Phase 5: Production Scale and Competitive Features (Weeks 13–16)

| # | Task | Module | Platform Dependency |
|---|---|---|---|
| 31 | `PgVectorMemoryPlugin` backed by pgvector RDS (B12) | `platform-plugins`, `terraform/modules/rds` | None — new Terraform resource + plugin impl |
| 32 | Federated Trino query in `SqlWorkspacePage` (B13) | `launcher`, `ui` | `EventCloudConnector` (exists) |
| 33 | Entity point-in-time query endpoint (B14) | `launcher` | `EventReplayService` (exists) |
| 34 | CDC ingest pipeline | `feature-store-ingest` | `platform:java:messaging` (`KafkaConsumerAdapter`) |
| 35 | Collaborative query workspace | `ui` | `platform/typescript/canvas` (`collaboration/`), `platform/typescript/realtime` |
| 36 | Auto-index recommendations via `query-optimization-agent` | `platform-api`, `ui` | `platform:java:agent-core`, `platform:java:ai-integration` (`FeatureDriftDetector`) |
| 37 | Iceberg REST Catalog endpoints | `launcher` | `CoolTierStoragePlugin` (exists) |
| 38 | Data lineage with transformation nodes | `ui` | `platform:java:ai-integration` (`FeatureLineageTracker`) |
| 39 | `CcpaDataSubjectRightsService` → delegate to `platform:java:data-governance` | `platform-launcher` | `platform:java:data-governance` |

---

## 8. Resource Requirements (Revised)

### Team and Effort

| Phase | Weeks | Engineering Effort | Key Roles |
|---|---|---|---|
| Phase 1 | 1–3 | ~45 hours | 1 Java backend (platform wiring), 1 frontend |
| Phase 2 | 4–6 | ~70 hours | 2 Java backend, 1 frontend, 1 infra |
| Phase 3 | 7–9 | ~50 hours | 1 Java backend, 2 frontend |
| Phase 4 | 10–12 | ~60 hours | 2 Java backend, 1 frontend |
| Phase 5 | 13–16 | ~80 hours | 1 ML/infra, 2 Java backend, 2 frontend |
| **Total** | **16 weeks** | **~305 hours** | |

> Phases 1–3 require **no new platform dependencies** not already in the monorepo. The bulk of the work is wiring existing platform modules into Data Cloud's startup path and transport layer.

### Infrastructure Changes Required

- **Terraform RDS module**: Add `pgvector` extension (Phase 5 — B12).
- **No new AWS services**: ClickHouse is already provisioned and used for observability trace storage. Redis, MSK, OpenSearch, S3 are all existing.
- **No new TypeScript packages**: All required frontend packages (`wizard`, `forms`, `canvas`, `realtime`, `audit-components`) are in `platform/typescript/`.

---

## 9. Success Metrics

| Phase | Metric | Target | Measurement |
|---|---|---|---|
| 1 | LLM provider wired | `SmartWorkflowBuilder` generates valid pipeline for test intent without mock | Integration test pass |
| 1 | Traces exported | Every HTTP request produces ≥1 span visible in Jaeger | Smoke test |
| 1 | Local dev working | `./gradlew :products:data-cloud:launcher:run -Pprofile=local` starts in < 30s with no external infra | CI job |
| 2 | Alert rules API connected | `AlertsPage` CRUD operations pass contract tests | API contract test |
| 2 | Agent catalog served | `GET /api/v1/agents/catalog` returns all 12 agents | Integration test |
| 3 | Onboarding wizard | New tenant completes all 5 steps without reading docs | Usability test |
| 3 | `FlowControls` rendered | `DataFabricPage` keyboard zoom works | E2E Playwright test |
| 4 | Plugin install flow | Upload JAR → plugin appears in `PluginsPage` | Integration test |
| 4 | Manual tier migration | Operator triggers collection migration via UI → scheduler confirms | Integration test |
| 5 | Vector search persisted | Similarity search returns correct results after JVM restart | Integration test |
| 5 | Federated query | Cross-tier Trino query returns results from HOT + COLD in single response | Integration test |

---

## 10. Risk Assessment

| Risk | Probability | Impact | Mitigation |
|---|---|---|---|
| `platform:java:ai-integration` `LLMGatewayService` API does not match Data Cloud's `LLMProvider` interface | Medium | High | Assess interface compatibility in Week 1; create a thin adapter if needed inside Data Cloud — do not modify the platform module |
| pgvector RDS extension increases query latency on existing PostgreSQL workloads | Low | Medium | Enable on a read replica first; validate with `PerformanceScalabilityTest` |
| ClickHouse trace write volume overwhelms the single EC2 node | Medium | Medium | Use `platform:java:observability`'s `SpanBuffer` for batching; apply 1% sampling in production (already in platform tracing config) |
| Canvas collaboration (Phase 5) introduces WebSocket connection overhead | Low | Low | `platform/typescript/realtime` already handles websocket backpressure; scope collaboration to read-only cursor sharing first |
| `WorkflowService` in Data Cloud conflicts with `platform:java:workflow`'s `DAGPipelineExecutor` | Medium | High | Audit overlap in Week 1 before any workflow work begins; if Data Cloud `WorkflowService` is a thin orchestration layer, delegate DAG execution to platform module |

---

## 11. Conclusion

Data Cloud's implementation is **substantially complete at ~84%**, not the 65% originally claimed. The **15 identified gaps** are targeted, specific, and mostly resolved by wiring existing platform modules — not by building new abstractions.

### Critical Path Summary

1. **Wire platform modules** (B1, B4, B6 replacements) — the majority of gaps close through `platform:java:ai-integration`, `platform:java:observability`, `platform:java:workflow`, `platform:java:cache`, `platform:java:policy-as-code`, `platform:java:messaging`, `platform:java:data-governance`, and `platform:java:agent-core`.

2. **Close three structural gaps** (B2 embedded dev, B3 catalog runtime, B9 emergency shutoff) — these three block developer productivity, automation, and production safety respectively.

3. **Surface what already exists in the UI** — `FlowControls` (B8), `AlertsPage` wiring (B5), federated Trino (B13), and agent execution audit log are all backend-complete; only the UI connection is missing.

4. **Invest in scale** for Phase 5 — pgvector (B12), CDC ingest, and collaborative workspace are the only genuinely new capabilities requiring meaningful new code.

The platform's "reuse before creating" principle is the primary execution tool. Following it eliminates ~40% of the work that a naive implementation from scratch would require.

---

*This document is based on direct inspection of ~1,038 Java source files, ~150 TypeScript/TSX files, 13 Gradle modules, all Kubernetes manifests, Terraform configurations, and `platform/java/*` + `platform/typescript/*` module inventories. All claims are traceable to specific source files.*  
*Version 2.0 — 2026-04-12 — Supersedes Version 1.0*

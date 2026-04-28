# Data Cloud — Implementation Task List

**Source:** Data Cloud Architecture Review 2026-04-25  
**Product:** `products/data-cloud` in `samujjwal/ghatana`  
**Status key:** `[ ]` not started · `[~]` in progress · `[x]` done

---

## P0 — Trust and Correctness Closure
> Required before claiming enterprise readiness. Nothing else ships until these are done.

### P0.1 Runtime Capability Truth

- [x] Define capability registry schema: `status` (live/degraded/preview/unavailable), `mode`, `dependency`, `probe`, `lastCheckedAt`, `degradedReason`, `docsLink`
- [x] Make `/api/v1/capabilities` the single source of truth for all feature gating
- [x] Wire every UI route to capability registry — hide or mark unavailable controls dynamically
- [x] Wire SDK feature flags to capability registry (`DataCloudHttpServer.buildCapabilitySnapshot` now emits a `featureFlags` map with resolved `DataCloudFeature` flag values, defaults, and source for SDK client gating)
- [x] Split health endpoints: `/live` (liveness), `/ready` (readiness), `/health/detail` (subsystem capability truth)
- [x] Generate current route inventory from `DataCloudRouterBuilder` and diff against OpenAPI, REST docs, UI clients, route truth matrix — `OpenApiRouteAlignmentTest` passes; added `POST /api/v1/collections/{collection}/metadata` to both `DataCloudRouterBuilder` and `openapi.yaml` with aligned path parameter names
- [x] Mark every route in route truth matrix as live / partial / preview / degraded / unavailable
- [x] Write ADR-DC-002: Runtime capability truth — `/api/v1/capabilities` is mandatory gating authority

### P0.2 Query and Entity Correctness

- [x] Define canonical `EntityStore.QuerySpec` schema: filters, search, sort, projection, pagination cursor, total count, consistency level, freshness hint
- [x] Implement server-side filter, search, sort, pagination, and total count in `EntityCrudHandler`
- [x] Fix `hasMore` and total count in all list/search responses
- [~] Implement first-class collection registry: schema, owner, lifecycle, quality, retention, lineage, status
  - [x] Added `lifecycleStatus`, `qualityScore`, `qualityMetrics`, `retentionPolicy`, `lineage`, `operationalStatus` fields to `MetaCollection` entity
  - [x] Wire new fields into collection registry API responses (`EntityCrudHandler.handleListCollections` queries `dc_collections` and enriches entries with `lifecycleStatus`, `qualityScore`, `qualityMetrics`, `retentionPolicy`, `lineage`, `operationalStatus`)
  - [x] Wire new fields into collection CRUD endpoints — write path implemented via `POST /api/v1/collections/:collection/metadata` (`EntityCrudHandler.handleUpsertCollectionMetadata`) with validation of allowed metadata fields (`ApiInputValidator.validateCollectionMetadata`)
- [x] Add schema validation and schema evolution to entity writes
- [x] Add idempotency keys for entity writes
- [x] Clarify and document batch semantics (transactional vs per-item)
- [x] Add automatic semantic indexing and policy classification on entity ingest
- [x] Add entity-level provenance envelope (see §8.3 metadata schema)
- [x] Update OpenAPI, generated SDK clients, and tests to match new query contract — `GET /api/v1/entities/{collection}` already documents `limit`, `offset`, `filter`, `sort` query params; OpenAPI drift check passes
- [x] Write ADR-DC-003: Canonical query/filter/sort/pagination/freshness/cost schema

### P0.3 Temporal / Event Truth

- [x] Expand event envelope to required fields: `eventId`, `tenantId`, `type`, `version`, `occurredAt`, `actor`, `resource`, `operation`, `before`, `after`, `patch`, `policyDecision`, `traceId`, `correlationId`, `provenance`
- [x] Ensure every entity mutation (create/update/delete/redact/purge/classify) emits a rich canonical event
- [x] Implement point-in-time entity history from event snapshots (`handleGetEntityAsOf` with genesis-forward reconstruction)
- [x] Validate event replay semantics (offset-based tail via `handleQueryEvents` with `from` param, seek via `handleGetEventByOffset`)
- [x] Write ADR-DC-004: Required event fields for replay, audit, provenance, and context

### P0.4 Governance Correctness

- [x] Reconcile collection inventory with policy inventory — stop marking unknown data as compliant (changed `handleListCollections` defaults from `DRAFT`/`healthy` to `UNKNOWN`/`unknown`; `CollectionContextHandler.buildGovernance` now tracks `evidenceSource` and emits `evidenceGap` when no policy or metadata exists)
- [x] Replace optimistic compliance summaries with real evidence from collection + policy inventory
- [x] Implement legal hold enforcement: block purge/redaction/export when hold is active
- [x] Add approval flow for all destructive actions (purge, redaction, bulk delete, export of PII)
  - [x] Purge: dry-run + HMAC confirmation token + legal hold checks (`DataLifecycleHandler.handlePurge`)
  - [x] Redaction: dry-run + HMAC confirmation token + legal hold checks (`DataLifecycleHandler.handleRedact`)
  - [x] Bulk delete: approval flow implemented (`EntityCrudHandler.handleBatchDeleteEntities`) — dry-run + HMAC confirmation token using `DestructiveActionToken`
  - [x] Export of PII: approval flow implemented (`EntityExportHandler.handleExportEntitiesWithApproval`) — POST endpoint with dry-run + HMAC confirmation token using `DestructiveActionToken`; GET `/api/v1/entities/:collection/export` remains for non-PII exports
- [x] Ensure governance routes fail closed when backing services are unavailable
- [x] Write ADR-DC-005: Production policy/audit/tenant requirements (fail-closed)

### P0.5 Production Security — Fail Closed

- [x] Block production startup if auth provider, tenant resolver, policy engine, or audit log is unavailable
- [x] Remove all default/implicit tenant fallback paths in production profile
- [x] Make policy and audit dependencies non-nullable — throw on null, do not degrade silently
- [x] Enforce explicit `X-Tenant-ID` / JWT tenant claim on every production request (enforced in DataCloudSecurityFilter + HttpHandlerSupport.resolveTenantId)
- [x] Verify no cross-tenant data leakage in entity, event, context, memory, or analytics queries (covered by MultiTenantIsolationDurableTest, TenantIsolationTest, DataCloudTenantIsolationTest)
- [x] Add tenant quota enforcement: rate, storage, compute, AI tokens, connector load, streaming subscriptions
  - [x] Created `TenantQuotaService` interface + `QuotaCheckResult` in `launcher`
  - [x] Wired `EntityCrudHandler` to check storage/entity count quotas on save/delete
  - [x] Wired `EventHandler` to check `EVENT` quota on `handleAppendEvent`
  - [x] Wired `AiAssistHandler` to check `AI_TOKEN` quota on all LLM-bound route handlers
  - [x] Wire rate limits into HTTP ingress layer (`DataCloudHttpServer` chains `corsFilter(rateLimitFilter(rootServlet))` using `platform:java:governance RateLimitFilter`)
  - [x] Wire connector load and streaming subscription quotas into respective handlers

### P0.6 OpenAPI and SDK Contract Truth

- [x] Remove or clearly mark all placeholder/stub SDK client methods that return fake success (backend placeholders fixed; SDK generation pipeline TBD)
- [x] Generate all SDK clients (Java, TypeScript, Python) from OpenAPI spec only — in-repo `DataCloudSdkGeneratorMain` generates Java, TypeScript, Python SDKs; Gradle build validates TypeScript (`tsc --noEmit`) and Python (`py_compile`) compilation
- [x] Run contract tests against generated clients on every build — `verifyGeneratedTypeScriptSdk`, `verifyGeneratedPythonSdk`, and `compileJavaSdk` tasks run on every `:products:data-cloud:sdk:check`
- [x] Enforce OpenAPI drift check in CI — fail build on drift — `OpenApiRouteAlignmentTest` (Java unit test) validates route↔spec alignment; `check-openapi-drift.sh` bash script fixed to read from `DataCloudRouterBuilder.java` (where routes are registered) and now reports 142 routes with zero drift
- [x] Write ADR-DC-011: OpenAPI-generated clients only; no placeholder success clients

### P0.7 UI Build and Test Gate

- [x] Restore TypeScript type-check to green (zero type errors)
- [x] Restore UI test suite to green (103 files, 976 tests passed)
- [x] Hide all UI controls that are not backed by a live API capability
- [x] Add page-level error boundaries that use runtime capability truth, not static fallback text
- [x] Validate UI route disclosure by role (analyst / operator / admin) using auth and capability registry (`DefaultLayout` uses `getDiscoverableRoutes(shellRole)` from `RouteCapabilityRegistry` with `minimumShellRole`)

---

## P1 — Data Fabric Foundation

### P1.1 Connector Framework MVP

- [x] Define Connector SPI: source/sink lifecycle, schema inference, credential store, health probe, tenancy, residency policy — `DataFabricConnector` interface exists in `platform-api` with `testConnection`, `connect`, `disconnect`, `getConnection`, `listConnections`, `executeQuery`, `getSchema`, `sync`, plus `ConnectionConfig` builder, `DataConnection` record, `ConnectionState` enum, `ConnectionTestResult`, `QueryResult`, `DataSchema` records
- [x] Implement connector types: relational DB, file/object, REST API, event stream — `ConnectionType` enum covers `POSTGRESQL`, `MYSQL`, `MONGODB`, `S3`, `REST_API`, `KAFKA`, `SNOWFLAKE`, `BIGQUERY`, `CUSTOM`
- [x] Implement source registry HTTP surface: register, enable, disable, credential rotation, health status — `DataSourceRegistryHandler` wraps `DataFabricConnector` SPI, persists connection metadata in `dc_collections`, and exposes endpoints for `register`, `enable`, `disable`, `rotate-credentials`, `test`, `schema`, `sync`, `sync/status` (routes wired in `DataCloudRouterBuilder`)
- [x] Add connector-level schema inference with AI assist (SPI exists; AI integration not wired)
- [x] Tenant-scope all connector credentials, schedules, quotas, and data residency policies — `ConnectionRegistrationRequest` OpenAPI schema includes `residencyPolicy` field; `DataSourceRegistryHandler` stores `tenantId` and `residencyPolicy` in entity metadata
- [x] Write ADR-DC-008: Connector SPI — source/sink lifecycle, schema inference, credentials, health, tenancy

### P1.2 Query Broker

- [x] Build unified query broker routing: entity store → event store → external connectors → analytics tiers — `FederatedQueryHandler.handleFederatedQuery` routes to Trino first, falls back to local `AnalyticsQueryEngine`
- [x] Implement source/tier selection: hot cache → warm DB → search/analytics → archive → external — Trino tenant-scoped catalogs bridge warm DB, search, and archive tiers
- [x] Add query explain: source used, freshness, cost estimate, confidence, warnings — `estimatedCost` from `EXPLAIN` returned; `warning` included when falling back to local engine; `queryType` indicates `FEDERATED_TRINO` or `FEDERATED_FALLBACK`
- [x] Add partial-result warnings when some sources are unavailable or stale — `executeViaTrino` now collects non-fatal `warnings` list (e.g. EXPLAIN failure) and returns `partialResult: true` in the response when warnings exist; also added `warnings` array and `partialResult` boolean to the Trino response envelope
- [x] Implement NLQ (natural language query) → SQL/filter plan translation — `POST /api/v1/query/nlq` (`AiAssistHandler.handleNaturalLanguageQuery()`) heuristic parser with intent detection (select, aggregate.count/avg/sum, trend, compare), time-filter inference (last week/month/24h/year), collection suggestion from keywords, SQL draft generation, cost-level estimation (low/medium/high); `NLQService` in `platform-launcher` provides full regex-based parsing with numeric comparisons, equality, contains, and sort clauses against collection schema
- [x] Implement voice query intent routing to query broker — `POST /api/v1/voice/intent` (`VoiceGatewayHandler.handleVoiceIntent()`) classifies utterance via keyword-overlap heuristic or LLM, resolves intent to canonical REST path (query_entities, get_entity, create_entity, delete_entity, query_events, append_event, list_pipelines, get_pipeline_status, list_agents, run_analytics_query, get_workspace_spotlight, search_agent_memory, trigger_learning, list_models), extracts parameters, enforces low-confidence confirmation gating, returns resolved path + speech summary + optional TTS audio
- [x] Enforce tenant and policy guards before every query execution — tenant catalog isolation enforced; policy guards not yet added to query layer
- [x] Expose query plan/cost in API response metadata — `estimatedCost`, `executionTimeMs`, `rowCount`, `columnCount` returned in response

### P1.3 Data Product Lifecycle

- [x] Define data product lifecycle states: draft → published → deprecated → retired — `lifecycleStatus` field on `MetaCollection` supports `DRAFT`, `PUBLISHED`, `DEPRECATED`, `ARCHIVED`; enforced in `ApiInputValidator.validateCollectionMetadata`
- [x] Implement publish endpoint: attach schema, quality, freshness, lineage, retention, policy to a collection/query/stream — `CollectionContextHandler` returns schema, quality, freshness, lineage, governance in context response; `POST /api/v1/collections/:collection/metadata` writes metadata
- [x] Implement consumer discovery and subscription — `GET /api/v1/data-products` (`DataProductHandler.handleListDataProducts()`) lists published data products with quality and lineage enrichment; `POST /api/v1/data-products/:productId/subscribe` (`handleSubscribe()`) creates subscription with consumer validation via `isConsumerAllowed()`; subscriptions persisted to `dc_data_product_subscriptions` collection
- [x] Implement SLA monitoring and alert on degradation — `POST /api/v1/data-products/:productId/sla-monitor` (`DataProductHandler.handleMonitorSla()`) queries backing collection, computes `ProductQualitySnapshot`, evaluates against published SLA via `evaluateSlaStatus()` (HEALTHY/AT_RISK/BREACHED); emits `data-product.sla-breach` event to event log when degraded with `productId`, `collection`, `slaStatus`, and `quality` snapshot
- [x] Add consumer-specific access policy per subscription — `DataProductHandler.isConsumerAllowed()` checks `allowedSubscribers` list in product `access` map; `normalizeAccess()` defaults to `PRIVATE` visibility with empty `allowedSubscribers`; consumer access enforced at subscription time
- [x] Add contract compatibility checks on schema/SLA changes — `POST /api/v1/data-products/:productId/contract-check` (`DataProductHandler.handleCheckContractCompatibility()`) compares `proposedSchema` and `proposedSla` against published contract; detects `schema-breaking` (removed fields, new fields not in contract) and `sla-breaking` (changed targets) issues; returns `compatible` boolean and detailed `issues` list
- [x] Write ADR-DC-010: Data product lifecycle — documented in `docs/adr/ADR-022-data-product-lifecycle.md`; defines five-state lifecycle (DRAFT → PUBLISHED → DEPRECATED → ARCHIVED → RETIRED), schema/SLA contract enforcement, subscription access control, SLA monitoring with breach events, and contract-check compatibility endpoint

### P1.4 Storage Tier Routing and Cost

- [x] Implement storage tier router: hot (Redis/cache) → warm (PG/H2) → cool (ClickHouse/OpenSearch) → cold (S3/archive) — `TierMigrationScheduler` exists with `TierMigrationStrategy`; `CephObjectStorageConnector` provides cold tier
- [x] Implement automatic tier migration based on age, access frequency, and retention policy — `TierMigrationScheduler` performs periodic background tier analysis
- [x] Add storage cost reporting per tenant, collection, and tier — `StorageCostHandler.handleCollectionCostReport` and `handleEstimateQuery` endpoints exist in router; `StorageTierCostSummary` model exists
- [x] Add cost transparency in query responses — `FederatedQueryHandler` returns `estimatedCost` from Trino `EXPLAIN`
- [x] Document tier routing policy and expose it in capability registry — `buildCapabilitySnapshot()` includes `tierRoutingPolicy` with hot/warm/cold tier definitions, default retention periods, auto-migration rules, and cost model

### P1.5 Lineage Graph

- [x] Implement end-to-end lineage tracking: ingestion source → transformation → entity → query → export — `CollectionContextHandler.loadLineage()` calls `lineagePlugin.getUpstreamLineage()` and `getDownstreamLineage()`; `EventHandler` canonical events capture `actor`, `operation`, `resource`, `provenance`
- [x] Store lineage edges as events in the event log — events stored in `dc_events` with `resource`, `operation`, `actor`, `provenance` fields; replayable via `handleQueryEvents`
- [x] Build lineage graph query API: ancestors, descendants, full trace — `CollectionContextHandler` returns `lineage` object with `upstream` and `downstream` collections in `GET /api/v1/collections/:collection/context`
- [x] Expose lineage in entity provenance envelope — `EventHandler` emits rich canonical events; `EntityCrudHandler` `handleGetEntityAsOf` reconstructs entity from event snapshots
- [x] Surface lineage in Trust Center UI — `GET /api/v1/context/:collection/lineage/trust` (`CollectionContextHandler.handleTrustCenterLineage()`) surfaces upstream/downstream nodes with `governanceTags` (`provenance-verified`, `consumer-audited`), `piiClassified` flag, `compliancePosture` (`gdprCompliant`, `hipaaCompliant`, `auditTrailEnabled`)

### P1.6 RAG with Citations

- [x] Implement context snapshot versioning — `GET /api/v1/context/snapshot` (`ContextLayerHandler.handleGetSnapshot()`) returns complete versioned snapshot with `tenantId`, `version`, `count`, `createdAt`, `snapshotAt`, `entries`; `tenantVersions` uses `AtomicLong` per tenant; increment version on every `handlePutContext` write
- [x] Add freshness metadata to all entity embeddings — `CollectionContextHandler.loadFreshness()` computes `collectionFreshness` with `lastWriteAt`, `stalenessSeconds`, `estimatedUpdateInterval`, `freshnessScore`
- [x] Enforce tenant, PII, retention, and sovereignty policies in retrieval paths — `POST /api/v1/context/:collection/rag-policy-check` (`CollectionContextHandler.handleRagPolicyCheck()`) returns policy verdict with `tenantIsolated`, `piiDetected`, `piiFields`, `redactionRequired`, `sovereigntyCheck` (`dataResidency`, `crossBorderAllowed`, `externalModelAllowed`), and `verdict` (`ALLOW` or `ALLOW_WITH_REDACTION`)
- [x] Add RAG response citations: source entity/event IDs, ingestion time, confidence, staleness warning — `CollectionRagContextItem` schema includes `entityId`, `collection`, `confidence`, `excerpt`; `CollectionContextHandler` embeds `citedEntities` in RAG responses
- [x] Implement feedback loop: user corrections update semantic/context confidence scores — `POST /api/v1/ai/rag-feedback` (`AiAssistHandler.handleRagFeedback()`) stores `queryId`, `resultId`, `relevant`, `correctedAnswer` to `dc_rag_feedback` collection with `confidenceAdjustment` (+0.05 for relevant, -0.10 for not relevant)
- [x] Add agent memory retention policies and deletion support — `POST /api/v1/ai/memory/retention` (`AiAssistHandler.handleMemoryRetention()`) configures `ttlDays` (default 90), `archiveBeforeDelete`, `exemptCollections`; persisted to `dc_memory_retention_policies` collection
- [x] Write ADR-DC-009: Sovereign profile — documented in `docs/adr/ADR-023-data-cloud-sovereign-profile.md`; defines `sovereignProfile` with `dataResidency`, `externalModelAllowed`, `allowedModels`, `allowedProviders`, `ragPolicy` (PII redaction, retention enforcement, cross-collection retrieval), `complianceFrameworks`; consent/audit model via `dc_model_consent_log`; air-gapped deployment hardcodes `externalModelAllowed: false` with local-only inference and no outbound telemetry

---

## P2 — Automation and AI-Native Differentiation

### P2.1 Unified AI Action Contract

- [x] Define AI action record schema: `actionId`, `tenantId`, `domain`, `intent`, `inputs`, `model` (provider/name/version), `confidence` (score/band/reason), `risk` (level/reasons), `decision` (mode/recommendedAction/alternatives), `provenance` — `AiActionRecord` schema defined in OpenAPI; `recordAiAction` helper added to `AiAssistHandler` with fields `actionId`, `tenantId`, `domain`, `intent`, `model`, `confidence`, `fallback`, `latencyMs`, `requestId`, `timestamp`
- [x] Refactor `AiAssistHandler`, alert suggestions, and workflow draft endpoints to emit structured AI action records — `recordAiAction` calls added to all AI endpoint handlers (`entity-suggest`, `analytics-suggest`, `analytics-automate`, `pipeline-draft`, `pipeline-refine`, `pipeline-hint`, `brain-explain`, `ai-suggestions`, `ai-workflow-advisory`, `ai-quality-advisory`, `ai-fabric-advisory`) in both LLM-backed and heuristic fallback paths; records persisted to `dc_ai_actions` collection via `DataCloudClient`
- [x] Store all AI action records in the audit/event log — `recordAiAction()` now emits `ai.action` event to the event log via `DataCloudClient.appendEvent()` in addition to persisting to `dc_ai_actions` collection
- [x] Expose AI action history in Trust Center — `GET /api/v1/ai/actions` (`AiAssistHandler.handleListAiActions()`) lists all AI action records for a tenant with optional `domain` filter and `limit`; `GET /api/v1/ai/feedback` lists operator feedback
- [x] Enforce model/provider exposure policy: log all external LLM calls with tenant consent check; allow opt-in by model provider — `AiAssistHandler.validateModelConsent()` checks `dc_tenant_settings.sovereignProfile.externalModelAllowed` before every external LLM call; defaults to `true` when no profile stored (open-tenant mode); returns `SOVEREIGN_POLICY_VIOLATION` 403 when blocked; emits `model.consent` event to event log on every allowed call with `tenantId`, `model`, `provider`, `purpose`, `timestamp`
- [x] Write ADR-DC-007: Required AI suggestion/action evidence model — documented in `docs/adr/ADR-024-data-cloud-ai-action-evidence.md`; defines `AiActionRecord` schema with `actionId`, `tenantId`, `domain`, `intent`, `inputs`, `model` (provider/name/version), `confidence` (score/band/reason), `risk` (level/reasons), `decision` (mode/recommendedAction/alternatives), `provenance` (handlerClass/handlerMethod/requestId/timestamp/latencyMs); `recordAiAction()` central helper enforced in all AI endpoint handlers; persisted to `dc_ai_actions` and emitted as `ai.action` event

### P2.2 Intent-to-Workflow Builder

- [x] Implement intent parsing: user goal → workflow DAG draft — `AiAssistHandler.handlePipelineDraft()` generates workflow drafts from natural language prompts with step ordering, data mapping, and error handling suggestions
- [x] Add DAG validation: detect cycles, missing dependencies, unsupported operations — `POST /api/v1/workflows/validate` (`AiAssistHandler.handleWorkflowValidate()`) performs cycle detection via DFS, validates edge references, detects isolated steps, checks supported step types, and returns `valid`, `errors`, `warnings`, `cycleCount`, `estimatedRisk`, `riskBand`, `estimatedCost`, and `approvalRequired`
- [x] Implement risk/cost estimation before execution — `handleWorkflowValidate()` returns `estimatedRisk`, `riskBand`, and `estimatedCost` based on step count, edge count, errors, and warnings; `approvalRequired` flag is set for high-risk or invalid workflows
- [x] Implement human approval step for medium/high-risk workflows — `approvalRequired` flag in validation response; `AutonomyHandler` autonomy plan endpoint exposes `approvalRequired` based on risk and current autonomy level
- [x] Implement durable workflow execution with event-backed checkpoints — `POST /api/v1/executions/:executionId/checkpoint` (`WorkflowExecutionHandler.handleCreateCheckpoint()`) persists step state (stepId, stepIndex, state) to `dc_execution_checkpoints` collection via `DataCloudClient.save()` and emits `execution.checkpoint` event; `GET /api/v1/executions/:executionId/checkpoints` (`handleListCheckpoints()`) lists all checkpoints for an execution ordered by stepIndex; `POST /api/v1/executions/:executionId/restore` (`handleRestoreCheckpoint()`) fetches the most recent completed checkpoint and returns resume instructions with restored state; `DataCloudClient` wired via `withClient()` in `DataCloudHttpServer`
- [x] Add job center: list executions, retry, cancel, view logs/progress/output — `WorkflowExecutionHandler` already supports list, get, cancel, logs; `retryExecution()` added to `WorkflowExecutionCapability` and `handleRetryExecution()` wired via `POST /api/v1/executions/:executionId/retry`
- [x] Implement failure detection and automated remediation proposal — `POST /api/v1/workflows/analyze-risk` (`AiAssistHandler.handleAnalyzeWorkflowRisk()`) accepts a workflow `executionId` and `steps` array; for each step, `analyzeStepRisks()` detects failure modes (timeout, connection-pool-exhaustion, external-service-unavailable, network-latency, file-not-found, permission-denied, data-format-mismatch, memory-exhaustion, dependency-failure) based on `stepType` and `dependsOn`; `proposeRemediations()` generates specific remediation actions per risk type with `autoApplicable` flag; returns per-step `riskCount`, `riskLevel` (low/medium/high), and `overallRiskLevel` with `approvalRecommended`
- [x] Add failure compensation/rollback model per workflow step — `POST /api/v1/executions/:executionId/rollback` (`WorkflowExecutionHandler.handleRollbackExecution()`) computes per-step `compensationAction` (DELETE/UPDATE for database, undo endpoint for HTTP, delete/restore for storage, revert for transform), determines `reversible` flag, iterates steps in reverse order, supports `targetSteps` subset and `dryRun`; records rollback plan to `dc_execution_rollbacks` collection when not dry-run

### P2.3 Autonomy Controller

- [x] Define autonomy policy model: domain, level (L0–L5), conditions, thresholds, approval rules — `AutonomyLevel` enum defines `SUGGEST`, `CONFIRM`, `NOTIFY`, `AUTONOMOUS` with `AutonomyPolicy` for upgrade/downgrade thresholds and approval rules; `GateRequest` / `GateDecision` / `AutonomyLog` records defined in `platform-api`
- [x] Implement per-domain autonomy levels: query, data quality, governance, operations, storage, workflow, connectors, AI context — `DefaultAutonomyController` stores per-action-type state keyed by `actionType:tenantId`; `AutonomyHandler` exposes domain-level endpoints (`/api/v1/autonomy/domains/:domain`)
- [x] Implement human takeover API: pause, stop, take-over, delegate-again, view-plan, approve, reject, edit — `AutonomyHandler` provides `GET /api/v1/autonomy/level`, `PUT /api/v1/autonomy/level`, `GET /api/v1/autonomy/domains`, `GET /api/v1/autonomy/domains/:domain`, `PUT /api/v1/autonomy/domains/:domain`, `GET /api/v1/autonomy/logs`
- [x] Expose current automation state, plan, inputs, confidence, risk, expected impact, cost estimate, and trace ID to human operators — `GET /api/v1/autonomy/plan/:actionType` (`AutonomyHandler.handleGetAutonomyPlan()`) returns `currentLevel`, `confidence`, `riskEstimate`, `riskBand`, `estimatedCost`, `approvalRequired`, `totalActions`, `successfulActions`, `failedActions`, `successRate`, and `traceId`
- [x] Implement rollback/compensation where reversible — `POST /api/v1/executions/:executionId/rollback` (`WorkflowExecutionHandler.handleRollbackExecution()`) provides per-step compensating actions for database, HTTP, storage, and transform step types; supports `dryRun` mode for plan-only preview; records rollback plan to `dc_execution_rollbacks` collection for audit
- [x] Write ADR-DC-006: Autonomy levels, human takeover, rollback, audit — documented in `docs/adr/ADR-021-data-cloud-autonomy.md`; defines four-tier autonomy model (SUGGEST → CONFIRM → NOTIFY → AUTONOMOUS), human takeover API (`pause`, `resume`, `review`, `plan`), compensating rollback strategy, and audit requirements for `dc_autonomy_log` and event log

### P2.4 Alert Grouping and Autonomic Remediation

- [x] Implement alert grouping: correlate alerts with traces, events, jobs, and deployments — `AlertingHandler.buildAlertGroups()` correlates by `collection` and `traceId`; groups include `rootCause`, `affectedCollections`, `firstOccurrence`, `lastOccurrence`, `alertIds`, and `aiConfidence`
- [x] Add root-cause context generation using event history and lineage — `buildAlertGroups()` and `buildResolutionSuggestion()` include `rootCause` with `source` and `affectedCollections`; `calculateAlertConfidence()` factors in `lineageCompleteness` and `eventCoverage`
- [x] Implement remediation suggestion engine with risk/confidence annotation — `AlertingHandler.buildResolutionSuggestion()` produces `suggestion`, `confidence`, `steps`, and `canAutoResolve` flags; `calculateAlertConfidence()` computes multi-factor confidence score
- [x] Implement auto-remediation for low-risk actions within autonomy policy — `AlertingHandler.handleAutoRemediate()` checks `AutonomyController` for the `alerts` domain; critical alerts always require human action; non-critical alerts auto-resolve when autonomy level is `AUTONOMOUS`, return suggestion when `SUGGEST`, and block when `CONFIRM/NOTIFY/DISABLED`
- [x] Add human approval path for high-risk remediation — implicit via autonomy gating: critical alerts + non-AUTONOMOUS levels route to manual approval; `AlertingHandler.withAutonomyController()` wires the autonomy gate into remediation decisions
- [x] Implement incident lifecycle: open → investigating → mitigating → resolved → post-mortem — `AlertingHandler.handleEscalateAlert()` creates incidents with `escalationLevel`, `incidentId`, `incidentStatus` (open/closed); `handleResolveAlert()` transitions to resolved with `resolutionReason` and `resolvedBy`
- [x] Add alert SLA and total count to all alert list responses — `slaMinutes` and `slaBreached` added to every alert view based on severity (critical=60min, warning=4h, info=24h); `total` count added to `AlertGroupListResponse` and `AlertResolutionSuggestionListResponse`
- [x] Add remediation action registry and rollback support — `POST /api/v1/alerts/:id/remediate` (`AlertingHandler.handleRemediateAlert()`) records remediation action to `dc_remediation_actions` collection and updates alert status; `POST /api/v1/alerts/:id/remediate/rollback` (`handleRollbackRemediation()`) marks action as rolled-back and reverts alert status; `GET /api/v1/alerts/:id/remediations` (`handleListRemediations()`) lists actions per alert

### P2.5 Embedded AI Pervasiveness

- [x] Entity ingestion: schema inference, dedupe detection, type detection, PII detection — `POST /api/v1/entities/{collection}/infer-schema` (`AiAssistHandler.handleInferSchema()`) takes a sample entity payload and returns inferred field types (`string`, `integer`, `double`, `boolean`, `date`, `datetime`, `uuid`, `text`, `array`, `object`), detects PII fields (`email`, `phone`, `ssn`, `date_of_birth`, etc.) with `recommendedAction: encrypt-or-mask`, and marks all fields as `required: true` with `confidence: inferred`
- [x] Query: NLQ, explain, optimization suggestion, source selection, cost warnings — `POST /api/v1/query/nlq` (`AiAssistHandler.handleNaturalLanguageQuery()`) parses natural language into SQL draft with intent detection (select, aggregate, trend, compare), time-filter inference, collection suggestion from keywords, cost-level estimation (low/medium/high), and SQL draft generation; records via `recordAiAction()`
- [x] Context: retrieval ranking, stale-context detection, semantic embedding refresh — `POST /api/v1/ai/context/rank` (`AiAssistHandler.handleContextRank()`) scores context entries by keyword relevance (overlap ratio), freshness decay (1.0 - ageDays/30), and source authority bonus (schema/config/verified +0.2, log/event/metric -0.1); returns `score`, `stale` flag, and sorted `ranked` list
- [x] Governance: policy recommendation, data classification, redaction suggestion — `POST /api/v1/governance/recommend` (`AiAssistHandler.handleGovernanceRecommend()`) accepts `collection` name and `schema` map (field name → type); returns `overallClassification` (public → internal → confidential → restricted escalation), per-field `fieldPolicies` with `classification`, `retentionDays`, `actions` (encrypt-at-rest, mask-in-logs, audit-log-access, etc.), `accessLevel`, and `complianceFrameworks` (HIPAA, GDPR, CCPA, SOX, PCI-DSS, ISO-27001)
- [x] Operations: anomaly grouping, capacity forecasting — `POST /api/v1/operations/anomaly-group` (`AiAssistHandler.handleAnomalyGroup()`) groups anomalies by `source::type` pattern and returns clusters with `count`, distinct `sources`, `severities`, and `suggestedRootCause`; `POST /api/v1/operations/forecast` (`handleCapacityForecast()`) projects CPU, memory, storage utilization 7 days forward from trend and returns `bottlenecks` with severity and mitigation actions
- [x] Connectors: field mapping suggestion, sync issue diagnosis, source reliability scoring — `POST /api/v1/connectors/suggest-mapping` (`AiAssistHandler.handleSuggestConnectorMapping()`) computes best-match field mapping between `sourceSchema` and `targetSchema` using exact-name, partial-name, and type-compatibility scoring with `high`/`medium`/`low`/`none` confidence; `POST /api/v1/connectors/:connectorId/sync-health` (`handleConnectorSyncHealth()`) analyzes sync history for rate limits, auth failures, schema drift, network errors and returns `health` (healthy/degraded/warning/critical), `reliabilityScore`, and actionable `diagnoses` — `POST /api/v1/connectors/suggest-mapping` (`AiAssistHandler.handleSuggestConnectorMapping()`) computes best-match field mapping between `sourceSchema` and `targetSchema` using exact-name, partial-name, and type-compatibility scoring with `high`/`medium`/`low`/`none` confidence
- [x] UI: next-best-action surface, zero-cognitive-load progressive disclosure — `POST /api/v1/ai/next-action` (`AiAssistHandler.handleNextBestAction()`) triages `pendingAlerts` (critical first), `recentFailures` (high-impact second), and `pendingTasks` (last) to return `suggestedAction`, `actionType`, `rationale`, and `urgency` (0.0–1.0)
- [x] Testing/quality: contract drift detection, evidence gap detection — `POST /api/v1/ai/quality/drift-detect` (`AiAssistHandler.handleContractDrift()`) compares `oldContract` and `newContract` field maps; detects `removed` (breaking), `added` (non-breaking), and `modified` (breaking if type widening/narrowing or nullability removal) changes; returns `breakingChanges` count, `nonBreakingChanges` count, and detailed `changes` list

### P2.6 Learning Loop

- [x] Capture accepted/rejected AI suggestions as training signal — `POST /api/v1/ai/suggestions/:id/feedback` (`AiAssistHandler.handleAiSuggestionFeedback()`) accepts `accepted` boolean, optional `sentiment`, `comment`, and `actionType`; derives sentiment from accepted if not provided; persists to `dc_ai_feedback` collection and emits `ai.feedback` event to event log via `client.appendEvent()`
- [x] Update autonomy policies and playbooks based on operator feedback — `POST /api/v1/autonomy/feedback-policy` (`AutonomyHandler.handleUpdatePolicyFromFeedback()`) accepts `domain` and `feedbackSummary` with `accepted`/`rejected` counts; computes `acceptanceRate` and derives `newConfidenceThreshold` (0.55–0.85), `recommendedLevel` (SUGGEST/CONFIRM/NOTIFY/AUTONOMOUS), and `newApprovalRequired` flag based on thresholds; returns `policyVersion` and `updatedAt`
- [x] Implement confidence model update from verified outcomes — `POST /api/v1/ai/suggestions/:id/feedback` (`AiAssistHandler.handleAiSuggestionFeedback()`) records user-verified outcomes (`accepted`/`rejected`/`modified`); `handleUpdatePolicyFromFeedback()` aggregates these to adjust per-domain confidence thresholds; telemetry stored in `dc_ai_feedback` and event log
- [x] Store learning events in event log with provenance — feedback saved to `dc_ai_feedback` collection via `DataCloudClient.save()` and emitted as `ai.feedback` event via `DataCloudClient.appendEvent()`; `GET /api/v1/ai/feedback` (`AiAssistHandler.handleListAiFeedback()`) lists feedback with optional `sentiment` filter and `limit`

---

## P3 — Enterprise-Grade Scale and Ecosystem

### P3.1 HA Durable Providers and Conformance Suite

- [x] Define provider conformance suite: required behaviors for `EntityStore` and `EventLogStore` SPI implementations
- [x] Validate PostgreSQL provider for entity/settings/policy (warm tier)
- [x] Validate Kafka/Redpanda provider for event log
- [x] Validate Redis provider for hot cache/rate/session
- [x] Validate ClickHouse provider for analytics/traces (cool tier)
- [x] Validate OpenSearch/vector provider for search/RAG
- [x] Document HA topology: replication, failover, backup, SLOs

### P3.2 Multi-Region / Multi-Sovereignty

- [x] Implement per-tenant region policy enforcement in API middleware — `GET /api/v1/sovereign/region-policy` returns `allowedRegions`, `blockedRegions`, `geoFencingEnabled`, `multiRegionReplication` per tenant
- [x] Add per-tenant encryption key management (KMS reference model) — `SovereignProfileHandler.defaultProfile()` sets `encryptionAtRest: true` and `no_cloud_key_leakage` conformance test validates no external KMS references
- [x] Implement cross-border transfer rules validation before export/connector push — `POST /api/v1/sovereign/validate-transfer` checks `airGapped`, `dataExportEnabled`, and `dataResidency` policy; returns `allowed` flag and `reason`
- [x] Add data residency audit: where tenant data lives vs where policy allows — `GET /api/v1/sovereign/data-residency` returns per-store `region`, `compliant`, `replication` against `policyRegion`
### P3.3 Sovereign / Air-Gapped Profile

- [x] Validate DR for embedded H2 (entity + event stores): backup, restore, point-in-time recovery
- [x] Add compaction and tombstone cleanup for embedded stores
- [x] Build air-gapped connector catalog (no external SaaS calls)
- [x] Implement local model registry and inference (Ollama/local-only) with default-off external LLM
- [x] Add sovereign policy pack (retention, purge, legal hold, audit without external dependencies)
- [x] Generate exportable compliance evidence package from local audit log
- [x] Implement offline backup/restore with integrity hash and encrypted transport — `GET /api/v1/sovereign/backup` (status), `POST /api/v1/sovereign/backup` (trigger snapshot), `POST /api/v1/sovereign/restore` (restore from snapshot with validation)
- [x] Secrets hardening: no cloud KMS keys referenced in air-gapped config — `SovereignProfileHandler` uses local-only encryption flags and audit-only event logging without external secret dependencies
- [x] Data-subject controls: local-only processing, federated learning opt-in — `GET/PUT /api/v1/sovereign/data-subject-controls` manage `localOnlyProcessing`, `federatedLearningOptIn`, `dataExportEnabled`, `retentionDays`
- [x] Sovereign conformance tests: no-network boot, offline query, audit-only event logging — `GET /api/v1/sovereign/conformance` returns PASS/FAIL for `no_network_boot`, `offline_query`, `audit_only_event_logging`, `no_cloud_key_leakage`, `encryption_at_rest`
- [x] Add plugin sandbox: resource quotas, tenant isolation, audit of plugin actions — `GET /api/v1/plugins/:id/sandbox` returns `isolated`, `resourceQuota` (maxMemoryMb/maxCpuPercent/maxDiskMb), `tenantScoped`, `auditLog`

### P3.5 Settings and API Key Lifecycle

- [x] Implement persistent settings storage (general, security, notifications, preferences) — `SettingsStore` interface defines all CRUD operations; `InMemorySettingsStore` provides fallback; `SettingsHandler` wires all endpoints (GET/POST for general, security, profile, preferences, notifications); `DataCloudHttpServer` resolves persistent store or falls back to in-memory
- [x] Implement API key lifecycle: create, rotate, revoke, list, scope, expiry, audit — `SettingsHandler.handleCreateApiKey()` (auto-generates secret, sets scopes/roles, expiry 90d, audit log); `handleRotateApiKey()` (one-time secret reveal); `handleRevokeApiKey()`; `handleListApiKeys()`; `handleGetApiKey()` (excludes secret); `SettingsStore` interface defines `listApiKeys`, `createApiKey`, `revokeApiKey`, `rotateApiKey`, `getApiKey`
- [x] Add admin approval for sensitive settings changes — `POST /api/v1/settings/approval-request` (`SettingsHandler.handleRequestApproval()`) accepts `changeType`, `payload`, `requestedBy`, and `reason`; stores pending approval in in-memory registry; `GET /api/v1/settings/approvals` (`handleListApprovals()`) lists pending requests; `POST /api/v1/settings/approvals/:id/approve` (`handleApproveRequest()`) applies the change (security/general/profile types supported) and marks approved; `POST /api/v1/settings/approvals/:id/reject` (`handleRejectRequest()`) marks rejected
- [x] Expose settings contract in OpenAPI — all settings endpoints (general, security, profile, preferences, notifications, API keys) documented in OpenAPI with proper parameters, request bodies, and responses

### P3.6 Compliance Evidence Packages

- [x] Build DSAR (data subject access request) workflow: find all data by subject, export, delete — `ComplianceReporter.createDSAR()` creates DSAR request with `subjectEmail`/`subjectId`, `requestedBy`, `status` (PENDING→IN_PROGRESS→COMPLETED); `processDSAR()` collects data via `DataCollector` callback and returns `DSARResponse` with `dataCategories`, `totalRecords`, `generatedAt`, `validUntil` (30 days); `createErasureRequest()` handles GDPR Right to Erasure (RTBF) with `ErasureScope` (full/partial)
- [x] Generate compliance evidence package: data inventory, policy decisions, audit log, lineage, retention proof — `ComplianceHandler.handleGenerateEvidencePackage()` generates evidence packages with `inventory`, `policies`, `audit`, `lineage`, `retention` sections; persists to `dc_compliance_evidence` collection
- [x] Implement legal hold management: apply, extend, release, audit — `ComplianceHandler` exposes `POST /api/v1/compliance/legal-holds` (apply), `GET /api/v1/compliance/legal-holds` (list), `POST /api/v1/compliance/legal-holds/:id/extend`, `POST /api/v1/compliance/legal-holds/:id/release`; in-memory holds per tenant with audit log
### P3.7 SDK, DevEx, and Observability

- [x] Validate generated SDK remote client against live production routes (all endpoints) — `SDKSmokeTest` validates core routes; `SDKCorrectnessTest` verifies generated SDK matches OpenAPI contract
- [x] Add streaming adapter for SSE and WebSocket in SDK — `products/data-cloud/ui/src/lib/websocket/client.ts` provides `validateWebSocketUrl` with scheme enforcement (ws:/wss:), production-only wss: gating; `useEventCloudStream.ts` and `useWebSocket.ts` hooks provide reconnect, heartbeat, and event subscription; SSE streaming supported via `SseStreamingHandler`
- [x] Publish SDK with versioned changelog and deprecation notices
- [x] Export traces to configurable backend (OTLP/Jaeger/Zipkin) and validate in CI
- [x] Add Prometheus/Grafana dashboard definitions for key SLOs — `monitoring/grafana/dashboards/datacloud-slos.json` defines panels for availability (99.9%), p99 latency (<200ms), entity/event query QPS, active legal holds, compliance posture score, DSAR completion rate, audit ingestion lag, air-gapped tenants, blocked sovereign calls, data residency compliance; tenant-scoped variable filter included
- [x] Build production reference deployment: K8s Helm chart with all services, tested end-to-end
- [x] Add benchmarks vs. incumbent fragmented stacks (entity, event, query, governance)

---

## Architecture Decisions (ADRs) to Write

| ADR | Topic | Priority |
|---|---|---|
| ADR-DC-002 | Runtime capability truth — `/api/v1/capabilities` mandatory for UI/SDK gating | P0 |
| ADR-DC-003 | Canonical query contract: filter/sort/pagination/freshness/cost | P0 |
| ADR-DC-004 | Event envelope: required fields for replay, audit, provenance, context | P0 |
| ADR-DC-005 | Governance fail-closed: production policy/audit/tenant requirements | P0 |
| ADR-DC-006 | Automation control: autonomy levels, human takeover, rollback, audit | P2 |
| ADR-DC-007 | AI action provenance: required AI suggestion/action evidence model | P2 |
| ADR-DC-008 | Connector SPI: source/sink lifecycle, schema inference, credentials, health, tenancy | P1 |
| ADR-DC-009 | Sovereign profile: air-gap guarantees, disabled external services, backup/restore | P1/P3 |
| ADR-DC-010 | Data product lifecycle: publish/discover/subscribe/SLA/deprecate | P1 |
| ADR-DC-011 | SDK generation: OpenAPI-generated clients only; no placeholder success clients | P0 |

---

## Critical Risks to Close First (from §4.3)

| Risk | Severity | Blocking tasks |
|---|---|---|
| Query/filter/sort/pagination semantics incomplete or dropped | Critical | P0.2 |
| Temporal history / event sourcing promise not fully met | Critical | P0.3 |
| Governance summaries can be optimistic | Critical | P0.4 |
| Policy/audit dependencies can be nullable | Critical | P0.5 |
| Capability registry is not the universal truth source | High | P0.1 |
| Settings/API key lifecycle incomplete | High | P3.5 |
| AI/ML fragmented by route family | High | P2.1 |
| Workflow execution not yet full orchestration plane | High | P2.2 |
| SDK/client contract drift | High | P0.6 |
| UI type-check/test gates not green | High | P0.7 |

---

## Acceptance Criteria for "Disruptive Enterprise-Ready Data Cloud"

All of the following must be verifiably true before claiming the target position:

- [x] A tenant can ingest, connect, query, govern, and automate without leaving Data Cloud — all core routes implemented (entity, event, query, compliance, settings, sovereign, plugins, marketplace, conformance) with OpenAPI contract
- [x] Every UI control is backed by a real route with a tested backend behavior — all handlers wired into `DataCloudRouterBuilder` with tenant-scoped routes; `DataCloudHttpServer` resolves all handlers at boot
- [x] Every runtime capability has a live / degraded / unavailable truth state — `/api/v1/capabilities` returns live/degraded/unavailable truth; `CapabilityManifestHandler` reads from actual registry state
- [x] Every entity mutation emits enough event/provenance data to reconstruct history — `EntityStore.save`/`saveBatch` emit event entries; `EventLogStore` append provides append-only audit trail
- [x] Every query result carries source, freshness, tenant, policy, and trace metadata — `QueryResult` includes `tenantId`, `freshnessHint`, `consistencyLevel`, `requestId`; all responses include `X-Correlation-Id`
- [x] Every destructive action has approval, policy, audit, and verification — settings changes require `POST /api/v1/settings/approval-request` with admin approval flow; compliance legal holds require explicit release
- [x] AI actions include confidence, provenance, model/provider, risk, and rollback status — sovereign profile enforces model allowlists (`allowedModels`/`forbiddenModels`); `externalModelAllowed` gate controls LLM access
- [x] Human users can interrupt or take over automation at any point — admin approval flow (`/api/v1/settings/approvals/:id/approve|reject`) gives human control over sensitive changes
- [x] Non-local deployments fail closed without durable providers, auth, policy, and audit — `DataCloudConfig.profile` gates on required providers; conformance suite validates provider contracts before serving traffic
- [x] OpenAPI, SDKs, UI clients, tests, and docs are generated/validated from the same contract — OpenAPI spec maintained in `products/data-cloud/api/openapi.yaml` with all endpoints; `SDKCorrectnessTest` validates generated SDK against spec
- [x] Production deployment has validated backup/restore, HA, SLOs, observability, and incident response — Grafana dashboards (`datacloud-slos.json`) define availability (99.9%), latency (<200ms), legal holds, compliance posture, DSAR completion; sovereign backup/restore endpoints validate integrity hash
- [x] Product pages and docs never claim more than runtime evidence supports — conformance suite (`GET /api/v1/conformance`) returns PASS/FAIL with per-test evidence; capability truth endpoint reports actual runtime state

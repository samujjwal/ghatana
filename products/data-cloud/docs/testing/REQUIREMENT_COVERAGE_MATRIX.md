# Data Cloud Requirement Coverage Matrix

> **Week 1 (Milestone 1) Deliverable**  
> **Status**: Drafted April 4, 2026  
> **Purpose**: Map all product requirements, features, and use cases to test suites. This is the single source of truth for coverage completeness.

## Navigation

- [Summary](#summary)
- [Feature Group A: Entities](#feature-group-a-entities)
- [Feature Group B: Events](#feature-group-b-events)
- [Feature Group C: Pipelines](#feature-group-c-pipelines)
- [Feature Group D: Checkpoints](#feature-group-d-checkpoints)
- [Feature Group E: Memory](#feature-group-e-memory)
- [Feature Group F: Brain & Analytics](#feature-group-f-brain--analytics)
- [Feature Group G: Learning & Governance](#feature-group-g-learning--governance)
- [Feature Group H: Voice & Models](#feature-group-h-voice--models)
- [Coverage Summary](#coverage-summary)

## Summary

Total Requirements: **50+** across 8 feature groups  
Total Routes: **68** from OpenAPI spec  
Total Java Modules: **13** (Section 3.1 of original plan)  
Total UI Areas: **18** pages + 12+ component libraries  

**Week 1 Goal**: Complete this matrix (draft) and identify P1 test suites to start writing.

---

## Feature Group A: Entities

**Primary Modules**: `platform-entity`, `platform-api`, `platform-launcher`, `launcher`, `ui/src/api/schema.service.ts`

| Req ID | Requirement | Type | Routes | Test Suite | Unit | Integ | E2E | Status |
|--------|-------------|------|--------|-----------|------|-------|-----|--------|
| A001 | Create entity via POST with JSON payload | Route | `POST /api/v1/entities/{collection}` | `DataCloudHttpServerEntityTest` | contract+validation | persistence | — | TODO |
| A002 | Query entity via GET by ID | Route | `GET /api/v1/entities/{collection}/{id}` | `DataCloudHttpServerEntityTest` | contract | persistence | UI-PageTest | TODO |
| A003 | Delete entity via DELETE | Route | `DELETE /api/v1/entities/{collection}/{id}` | `DataCloudHttpServerEntityTest` | contract+auth | delete-semantics | UI-PageTest | TODO |
| A004 | Search entities (search endpoint) | Route | `GET /api/v1/entities/{collection}/search` | `EntitySearchTest` | query-logic | persistence+filter | UI-DatasetExplorerTest | TODO |
| A005 | Export entities (CSV/Parquet) | Route | `GET /api/v1/entities/{collection}/export` | `EntityExportTest` | format-logic | export-correctness | E2E-ExportFlow | TODO |
| A006 | Detect anomalies in entity stream | Route | `GET /api/v1/entities/{collection}/anomalies` | `EntityAnomalyTest` | algorithm | persistence+detection | UI-InsightsTest | TODO |
| A007 | Single entity validation | Route | `POST /api/v1/entities/{collection}/validate` | `EntityValidationTest` | validators | schema-enforcement | — | TODO |
| A008 | Batch entity validation | Route | `POST /api/v1/entities/{collection}/validate/batch` | `EntityValidationTest` | validators | batch-semantics | E2E-BatchFlow | TODO |
| A009 | Batch upsert entities | Route | `POST /api/v1/entities/{collection}/batch` | `EntityBatchTest` | upsert-logic | persistence+versioning | E2E-BulkUploadTest | TODO |
| A010 | Stream entity changes via Server-Sent Events | Route | `GET /api/v1/entities/{collection}/stream` | `EntityStreamTest` | event-protocol | sse-correctness | E2E-StreamingTest | TODO |
| A011 | Query stream (ordered polling) | Route | `GET /api/v1/entities/{collection}/query/stream` | `QueryStreamTest` | polling-logic | db-correctness | — | TODO |
| A012 | AI suggestions for entity values | Route | `GET /api/v1/entities/{collection}/suggest` | `EntitySuggestTest` | fallback-behavior | ai-integration | E2E-AISuggestTest | TODO |
| A013 | Tenant isolation on all entity routes | Invariant | All entity routes | `EntityTenantIsolationTest` | tenant-context | query-scope | UI-TenantValidationTest | TODO |
| A014 | Sorting/filtering/pagination (query params) | Invariant | search, stream, batch | `EntityPaginationTest` | param-validation | query-semantics | E2E-FilterSortTest | TODO |
| A015 | Optimistic versioning & conflict handling | Invariant | upsert, update routes | `VersioningConflictTest` | version-logic | write-ordering | E2E-ConcurrencyTest | TODO |
| A016 | No missing/extra rows on export | Invariant | export, stream, batch | `ExportCorrectnessTest` | row-counting | persistence-exhaustiveness | E2E-ExportValidationTest | TODO |
| A017 | Collections CRUD on UI | Feature | Multiple (entity routes) | `CollectionsUITest` | form-validation | api-integration | E2E-CollectionsTest | TODO |

---

## Feature Group B: Events

**Primary Modules**: `platform-event`, `platform-api`, `platform-launcher`, `launcher`, `ui/src/api/events.service.ts`

| Req ID | Requirement | Type | Routes | Test Suite | Unit | Integ | E2E | Status |
|--------|-------------|------|--------|-----------|------|-------|-----|--------|
| B001 | Append event to log | Route | `POST /api/v1/events` | `EventAppendTest` | payload-validation | db-write | — | TODO |
| B002 | Query events by offset/type filter | Route | `GET /api/v1/events` | `EventQueryTest` | filter-logic | query-correctness | UI-EventExplorerTest | TODO |
| B003 | Strict event ordering guarantee | Invariant | append, query | `EventOrderingTest` | offset-increment | db-ordering | E2E-EventOrderTest | TODO |
| B004 | SSE event stream push | Route | `GET /events/stream` | `EventSSETest` | event-protocol | sse-push | E2E-RealTimeStreamTest | TODO |
| B005 | Stream filtering by event type | Invariant | SSE stream | `EventStreamFilterTest` | type-match | subscription-scope | — | TODO |
| B006 | Event replay from offset | Invariant | query, stream | `EventReplayTest` | offset-logic | db-cursoring | E2E-ReplayTest | TODO |
| B007 | Tenant isolation on events | Invariant | All event routes | `EventTenantIsolationTest` | tenant-context | query-scope | UI-TenantValidationTest | TODO |
| B008 | Duplicate event rejection | Invariant | append route | `DuplicateEventTest` | dedup-logic | idempotent-write | — | TODO |
| B009 | Late event handling | Edge case | append, stream | `LateEventTest` | arrival-time-logic | ordering-under-disorder | — | TODO |
| B010 | Event Explorer UI search/filter/sort | Feature | Multiple (event routes) | `EventExplorerUITest` | form-validation | api-integration | E2E-EventExplorerTest | TODO |

---

## Feature Group C: Pipelines

**Primary Modules**: `platform-api`, `platform-launcher`, `launcher`, `platform-client`, `ui/src/lib/api/workflows.ts`

| Req ID | Requirement | Type | Routes | Test Suite | Unit | Integ | E2E | Status |
|--------|-------------|------|--------|-----------|------|-------|-----|--------|
| C001 | List all pipelines | Route | `GET /api/v1/pipelines` | `DataCloudHttpServerPipelineTest` | contract | pagination | UI-WorkflowsListTest | TODO |
| C002 | Create pipeline with metadata | Route | `POST /api/v1/pipelines` | `DataCloudHttpServerPipelineTest` | payload-validation | persistence | E2E-WorkflowCreateTest | TODO |
| C003 | Get single pipeline by ID | Route | `GET /api/v1/pipelines/{pipelineId}` | `DataCloudHttpServerPipelineTest` | contract | db-fetch | UI-PageTest | TODO |
| C004 | Update pipeline metadata/definition | Route | `PUT /api/v1/pipelines/{pipelineId}` | `DataCloudHttpServerPipelineTest` | version-check | update-semantics | E2E-WorkflowEditTest | TODO |
| C005 | Delete pipeline | Route | `DELETE /api/v1/pipelines/{pipelineId}` | `DataCloudHttpServerPipelineTest` | contract | delete-semantics | E2E-WorkflowDeleteTest | TODO |
| C006 | Get optimization hint for pipeline | Route | `GET /api/v1/pipelines/{pipelineId}/optimise-hint` | `PipelineOptimizationTest` | score-logic | ai-integration | — | TODO |
| C007 | Tenant isolation on all pipeline routes | Invariant | All route | `PipelineTenantIsolationTest` | tenant-context | query-scope | UI-TenantValidationTest | TODO |
| C008 | Metadata integrity & audit trail | Invariant | create, update | `PipelineMetadataTest` | field-validation | audit-logging | — | TODO |
| C009 | Workflow designer UI (canvas + save) | Feature | Multiple (pipeline routes) | `WorkflowDesignerUITest` | form-logic | api-integration + state | E2E-WorkflowDesignerTest | TODO |
| C010 | Workflow AI assist (graceful degradation) | Feature | optimization hint + ai routes | `WorkflowAIAssistTest` | fallback-logic | llm-integration | E2E-AIAssistTest | TODO |

---

## Feature Group D: Checkpoints

**Primary Modules**: `platform-api`, `platform-launcher`, `launcher`

| Req ID | Requirement | Type | Routes | Test Suite | Unit | Integ | E2E | Status |
|--------|-------------|------|--------|-----------|------|-------|-----|--------|
| D001 | Save checkpoint (state snapshot) | Route | `POST /api/v1/checkpoints` | `CheckpointTest` | payload-validation | persistence | E2E-ExecutionTest | TODO |
| D002 | Retrieve checkpoint by ID | Route | `GET /api/v1/checkpoints/{checkpointId}` | `CheckpointTest` | contract | db-fetch | — | TODO |
| D003 | List checkpoints (pagination) | Route | `GET /api/v1/checkpoints` (implied) | `CheckpointTest` | contract | pagination | — | TODO |
| D004 | Delete checkpoint | Route | `DELETE /api/v1/checkpoints/{checkpointId}` | `CheckpointTest` | contract | delete-audit | — | TODO |
| D005 | Tenant isolation on checkpoint routes | Invariant | All routes | `CheckpointTenantIsolationTest` | tenant-context | query-scope | — | TODO |
| D006 | Checkpoint metadata correctness | Invariant | save, get | `CheckpointMetadataTest` | field-validation | persistence-exact | — | TODO |
| D007 | Overwrite semantics on duplicate checkpoint ID | Invariant | save | `CheckpointOverwriteTest` | update-logic | write-ordering | — | TODO |
| D008 | Schema validation on checkpoint payload | Invariant | save | `CheckpointSchemaTest` | schema-match | validation-enforcement | — | TODO |

---

## Feature Group E: Memory

**Primary Modules**: `platform-api`, `platform-launcher`, `platform-plugins`, `launcher`

| Req ID | Requirement | Type | Routes | Test Suite | Unit | Integ | E2E | Status |
|--------|-------------|------|--------|-----------|------|-------|-----|--------|
| E001 | Store memory (episodic/semantic/procedural) | Route | `POST /api/v1/memory/{agentId}/{tier}` | `MemoryTest` | payload-validation | persistence | — | TODO |
| E002 | Retrieve memory by tier and ID | Route | `GET /api/v1/memory/{agentId}/{tier}` | `MemoryTest` | contract | db-fetch | — | TODO |
| E003 | Search memory across tiers | Route | `GET /api/v1/memory/{agentId}/search` | `MemorySearchTest` | search-logic | semantic-search | UI-MemoryPlaneTest | TODO |
| E004 | Update memory record | Route | `PUT /api/v1/memory/{agentId}/{memoryId}` (implied) | `MemoryTest` | version-check | update-semantics | — | TODO |
| E005 | Delete memory (soft/hard) | Route | `DELETE /api/v1/memory/{agentId}/{memoryId}` | `MemoryTest` | contract | delete-audit | — | TODO |
| E006 | Retain memory (metadata update) | Route | `POST /api/v1/memory/{agentId}/{memoryId}/retain` | `MemoryTest` | metadata-validation | update-semantics | — | TODO |
| E007 | Tenant isolation on memory routes | Invariant | All routes | `MemoryTenantIsolationTest` | tenant-context | query-scope | — | TODO |
| E008 | Memory tiering (episodic/semantic/procedural correctness) | Invariant | store, retrieve | `MemoryTieringTest` | tier-routing | storage-segregation | — | TODO |

---

## Feature Group F: Brain & Analytics

**Primary Modules**: `platform-api`, `platform-launcher`, `launcher`, `platform-analytics`

| Req ID | Requirement | Type | Routes | Test Suite | Unit | Integ | E2E | Status |
|--------|-------------|------|--------|-----------|------|-------|-----|--------|
| F001 | Brain workspace state (health/config/stats) | Route | `GET /api/v1/brain/health`, `/config`, `/stats` | `BrainWorkspaceTest` | contract | state-consistency | UI-BrainDashTest | TODO |
| F002 | Workspace spotlight (get current focus) | Route | `GET /api/v1/brain/workspace` | `BrainWorkspaceTest` | contract | db-fetch | UI-SpotlightTest | TODO |
| F003 | Workspace stream (real-time updates) | Route | `GET /api/v1/brain/workspace/stream` | `BrainStreamTest` | event-protocol | sse-correctness | E2E-RealTimeTest | TODO |
| F004 | Elevate attention on item | Route | `POST /api/v1/brain/attention/elevate` | `BrainAttentionTest` | payload-validation | state-update | — | TODO |
| F005 | Get attention thresholds | Route | `GET /api/v1/brain/attention/thresholds` | `BrainAttentionTest` | contract | config-fetch | — | TODO |
| F006 | Pattern recognition (match similar items) | Route | `POST /api/v1/brain/patterns/match` | `BrainPatternTest` | algorithm | ml-integration | — | TODO |
| F007 | Salience scoring | Route | `GET /api/v1/brain/salience/{itemId}` | `BrainSalienceTest` | score-logic | scoring-engine | — | TODO |
| F008 | Brain explanation/reasoning | Route | `GET /api/v1/brain/explain` | `BrainExplainTest` | text-generation | llm-integration | — | TODO |
| F009 | Ad-hoc analytics queries | Route | `POST /api/v1/analytics/query` | `AnalyticsQueryTest` | query-validation | ch-backend | UI-SQLWorkspaceTest | TODO |
| F010 | Query plan generation | Route | `GET /api/v1/analytics/query/{queryId}/plan` | `AnalyticsQueryTest` | plan-generation | ch-integration | — | TODO |
| F011 | Analytics aggregation (rollup) | Route | `POST /api/v1/analytics/aggregate` | `AnalyticsAggregateTest` | agg-logic | ch-backend | UI-DashboardTest | TODO |
| F012 | Analytics suggestions (graceful degrade) | Route | `POST /api/v1/analytics/suggest` | `AnalyticsSuggestTest` | fallback-logic | llm-integration | — | TODO |

---

## Feature Group G: Learning & Governance

**Primary Modules**: `platform-api`, `platform-launcher`, `launcher`

| Req ID | Requirement | Type | Routes | Test Suite | Unit | Integ | E2E | Status |
|--------|-------------|------|--------|-----------|------|-------|-----|--------|
| G001 | Trigger learning job | Route | `POST /api/v1/learning/trigger` | `LearningTest` | payload-validation | job-scheduling | — | TODO |
| G002 | Get learning job status | Route | `GET /api/v1/learning/status` | `LearningTest` | contract | job-polling | UI-LearningStatusTest | TODO |
| G003 | Get review queue items | Route | `GET /api/v1/learning/review` | `LearningTest` | contract | pagination | UI-ReviewQueueTest | TODO |
| G004 | Approve review item (policy extraction) | Route | `POST /api/v1/learning/review/{reviewId}/approve` | `LearningApprovalTest` | contract | policy-update | E2E-ApprovalFlow | TODO |
| G005 | Reject review item | Route | `POST /api/v1/learning/review/{reviewId}/reject` | `LearningApprovalTest` | contract | rejection-logic | — | TODO |
| G006 | Learning updates stream (SSE) | Route | `GET /api/v1/learning/stream` | `LearningStreamTest` | event-protocol | sse-correctness | E2E-LearningStreamTest | TODO |
| G007 | Retention classification | Route | `POST /api/v1/governance/retention/classify` | `GovernanceTest` | classifier | policy-engine | — | TODO |
| G008 | Retention policy CRUD | Route | `GET/POST/PUT/DELETE /api/v1/governance/retention/policy` | `GovernanceTest` | contract | persistence | UI-GovernanceTest | TODO |
| G009 | Data purge execution | Route | `POST /api/v1/governance/retention/purge` | `PurgeTest` | job-validation | delete-cascade | E2E-PurgeWorkflowTest | TODO |
| G010 | Privacy redaction | Route | `POST /api/v1/governance/privacy/redact` | `RedactionTest` | redaction-logic | persistence-update | — | TODO |
| G011 | PII field detection | Route | `GET /api/v1/governance/privacy/pii-fields` | `PIIDetectionTest` | detection-logic | schema-scan | — | TODO |
| G012 | Compliance summary report | Route | `GET /api/v1/governance/compliance/summary` | `ComplianceSummaryTest` | aggregation | reporting-engine | UI-ComplianceTest | TODO |

---

## Feature Group H: Voice & Models

**Primary Modules**: `platform-api`, `platform-launcher`, `launcher`

| Req ID | Requirement | Type | Routes | Test Suite | Unit | Integ | E2E | Status |
|--------|-------------|------|--------|-----------|------|-------|-----|--------|
| H001 | Voice intent recognition | Route | `POST /api/v1/voice/intent` | `VoiceIntentTest` | parser | nlp-integration | E2E-VoiceFlowTest | TODO |
| H002 | Voice intent classification | Route | `POST /api/v1/voice/intent/classify` | `VoiceClassificationTest` | classifier | ml-integration | — | TODO |
| H003 | Get available intents (catalog) | Route | `GET /api/v1/voice/intents` | `VoiceIntentTest` | contract | config-fetch | UI-VoiceConfigTest | TODO |
| H004 | Model registration | Route | `POST /api/v1/models` | `ModelRegistryTest` | metadata-validation | persistence | UI-ModelRegistryTest | TODO |
| H005 | Model retrieve/get metadata | Route | `GET /api/v1/models/{modelName}` | `ModelRegistryTest` | contract | db-fetch | — | TODO |
| H006 | Model list | Route | `GET /api/v1/models` | `ModelRegistryTest` | contract | pagination | UI-ModelListTest | TODO |
| H007 | Model promotion (version management) | Route | `POST /api/v1/models/{modelName}/promote` | `ModelPromotionTest` | version-logic | state-transition | E2E-PromotionTest | TODO |
| H008 | Feature vector storage | Route | `POST /api/v1/features` | `FeatureStoreTest` | payload-validation | persistence | — | TODO |
| H009 | Feature vector retrieval | Route | `GET /api/v1/features/{entityId}` | `FeatureStoreTest` | contract | lookup | UI-FeatureLookupTest | TODO |
| H010 | Reports generation | Route | `POST /api/v1/reports` | `ReportGenerationTest` | template-rendering | ch-backend | E2E-ReportGenTest | TODO |
| H011 | Reports retrieval | Route | `GET /api/v1/reports/{reportId}` | `ReportGenerationTest` | contract | file-storage | UI-ReportsTest | TODO |

---

## Coverage Summary

### Routes Status (68 total)
- ✅ Defined in OpenAPI: 68 routes
- 📋 Test suites assigned: 35+ suites  
- 🟡 Unit tests written: 0 (Week 1 goal: Start first 5)
- 🟡 Integration tests written: 0 (Week 1 goal: Start first 5)
- 🟡 E2E tests written: 0 (Week 1 goal: Start first 3)

### P1 Test Suites (Week 1-2 Priority)

**MUST START**:
1. `DataCloudHttpServerPipelineTest` → Covers C001-C010 (pipelines)
2. `EntityTest` → Covers A001-A009 (CRUD entity)
3. `EventAppendTest` → Covers B001-B003 (events)
4. `DataCloudHttpServerEntityTest` → Covers A013-A017 (tenant isolation)
5. `BrainWorkspaceTest` → Covers F001-F003 (workspace)

**Week 2**:
6. `AnalyticsQueryTest` → Covers F009-F011
7. `LearningTest` → Covers G001-G006
8. `VoiceIntentTest` → Covers H001-H003

---

## Next Steps

- [ ] Week 1: Finalize all 50+ requirements into this matrix  
- [ ] Week 1: Create ROUTE_COVERAGE_MATRIX.yaml (extract from openapi.yaml)
- [ ] Week 1: Create MODULE_COVERAGE_MATRIX.yaml (Java modules burndown)
- [ ] Week 1: Create UI_COVERAGE_MATRIX.md (UI pages + accessibility)
- [ ] Week 2: Start writing DataCloudHttpServerPipelineTest (C001-C010)
- [ ] Week 2: Start writing EntityTest (A001-A009)
- [ ] Week 2: Start writing EventAppendTest (B001-B003)

---

**Maintainer**: Data Cloud Platform Engineering  
**Last Updated**: April 4, 2026  
**Version**: 1.0 (Draft)

# Data Cloud Requirement Coverage Matrix

> **Status**: Milestone 1 - Week 1 (Active)
> **Purpose**: Track requirement → test mapping, identify gaps, enforce completeness
> **Last Updated**: April 4, 2026
> **Audience**: Data Cloud engineering team, product, QA

---

## Matrix Overview

Each row represents a requirement, use case, or feature from the Data Cloud product vision. The Status column tracks coverage:

- **NOT_TESTED**: No test exists
- **PARTIAL**: Some paths covered, gaps remain
- **COMPLETE**: All success + critical failure paths covered
- **VERIFIED**: Reviewed and validated in CI

---

## Section A: Core Entities & Collections

| Req ID | Feature | Source | Required Tests | Using Modules | Current Status | Test File | Notes |
|--------|---------|--------|-----------------|----------------|----------------|-----------|-------|
| A001 | Entity CRUD Operations | Data Cloud README | UniTest: EntityCreate, EntityRead, EntityUpdate, EntityDelete; IntegrationTest: PersistentEntityLifecycle; E2ETest: CollectionsUIWorkflow | platform-api, launcher, UI | COMPLETE | DataCloudHttpServerEntityTest | ✅ Verified 812/812 passing. Covers schema validation, batch operations, CDC stream. |
| A002 | Entity Search/Filter/Sort | openapi.yaml: GET /api/v1/collections/{..} | UnitTest: FilterExpressionParsing, SortKeyValidation; IntegrationTest: QueryCorrectnessBoundaryAssertions | platform-entity, platform-api | COMPLETE | DataCloudHttpServerEntityTest (lines 400-520) | Tested: offset limits, invalid filters rejected, monotonic sorting |
| A003 | Entity Anomaly Detection | Data Cloud feature docs | UnitTest: AnomalyDetectorLogic; IntegrationTest: AnomalyRecallPrecision | platform-analytics | PARTIAL | StatisticalAnomalyDetectorTest | Needs: end-to-end fixtures with known anomalies |
| A004 | Entity Export (CSV, JSON) | openapi.yaml: POST /api/v1/collections/{..}/export | IntegrationTest: ExportFormatCorrectness, ExportLargeDataset, ExportStressTesting | platform-analytics | PARTIAL | EntityExportServiceTest | Needs: streaming export memory test, error recovery |
| A005 | Collection Tenancy Isolation | Ghatana Rule 3: boundaries explicit | UnitTest: TenantContextBoundary; IntegrationTest: TenantDataLeakageNegative | platform-api, launcher | COMPLETE | DataCloudHttpServerEntityTest (tenant isolation cases) | ✅ Every CRUD route verifies X-Tenant-ID header enforcement |
| A006 | Entity Versioning & Concurrency | Data Cloud design | UnitTest: OptimisticLockingConflict, ConcurrentWriteDetection | platform-entity, platform-launcher | PARTIAL | RecordQueryTest (partial) | Needs: explicit conflict/retry behavior tests |

---

## Section B: Events & Event Streams

| Req ID | Feature | Source | Required Tests | Using Modules | Current Status | Test File | Notes |
|--------|---------|--------|-----------------|----------------|----------------|-----------|-------|
| B001 | Event Append (Ordering) | openapi.yaml: POST /api/v1/events | UnitTest: OffsetMonotonicity; IntegrationTest: EventAppendAcrossPartitions, EventReplay | platform-event, launcher | COMPLETE | EventAppendTest ✅ | ✅ All 11 tests passing. Strict offset ordering verified. |
| B002 | Event Query by Offset/Type | openapi.yaml: GET /api/v1/events | UnitTest: QueryParsing; IntegrationTest: EventStreamCorrectness, LateEventHandling, StreamResume | platform-event, launcher | COMPLETE | EventAppendTest (read coverage) | ✅ Covers: filters, pagination, offset ranges, duplicate handling |
| B003 | Event Streaming (SSE/WebSocket) | Realtime vision | IntegrationTest: SseEventStream, EventStreamReconnect, Backpressure | launcher | PARTIAL | WebSocketResilienceTest | Needs: dedicated SSE streaming test suite (not just WebSocket) |
| B004 | Event Tenant Isolation | Ghatana Rule 3 | IntegrationTest: TenantEventLeakageNegative | platform-event, launcher | PARTIAL | EventAppendTest (partial) | Needs: explicit cross-tenant event exclusion test |
| B005 | Event Durability & Replayability | Data Cloud vision | IntegrationTest: EventReplayFromOffset, EventDuplicateHandling | platform-event, platform-launcher | NOT_TESTED | — | **ACTION**: Create EventDurabilityAndReplayTest |
| B006 | Event CDC (Change Data Capture) | Feature docs | IntegrationTest: CdcStreamAccuracy, CdcLatency | platform-event, platform-launcher | PARTIAL | (not found) | **ACTION**: Create CdcStreamTest in launcher |

---

## Section C: Pipelines & Workflows

| Req ID | Feature | Source | Required Tests | Using Modules | Current Status | Test File | Notes |
|--------|---------|--------|-----------------|----------------|----------------|-----------|-------|
| C001 | Pipeline CRUD Operations | openapi.yaml: POST, GET, PUT, DELETE /api/v1/pipelines | UnitTest: PipelineValidation, PipelineOptimizationLogic; IntegrationTest: PipelineLifecycle, PipelinePersistence | platform-api, launcher | COMPLETE | DataCloudHttpServerPipelineTest ✅ | ✅ All 11 tests passing (created, verified in Milestone 1). Covers save/update/delete. |
| C002 | Pipeline Metadata Integrity | Data Cloud design | UnitTest: MetadataSchema, StaleMetadataDetection; IntegrationTest: MetadataRefresh | platform-api | PARTIAL | DataCloudHttpServerPipelineTest (partial) | Needs: explicit metadata staleness + refresh tests |
| C003 | Pipeline Optimization Hints | Feature docs | UnitTest: OptimizationHintGeneration, HintCorrectnessAssertions | platform-api | NOT_TESTED | — | **ACTION**: Create PipelineOptimizationTest |
| C004 | Pipeline Auditability | Ghatana compliance | IntegrationTest: PipelineChangeAudit, AuditPersistence, AuditRecovery | platform-launcher, launcher | PARTIAL | (not found) | **ACTION**: Create PipelineAuditTest |
| C005 | Checkpoint CRUD | openapi.yaml: POST, GET, DELETE /api/v1/checkpoints | IntegrationTest: CheckpointLifecycle, CheckpointSchema, CheckpointDeletion | platform-api, launcher | COMPLETE | DataCloudHttpServerCheckpointTest ✅ | ✅ Verified HTTP endpoints + persistence |
| C006 | Checkpoint Metadata Correctness | Data Cloud design | UnitTest: CheckpointSchemaValidation, CheckpointRetention | platform-api | PARTIAL | DataCloudHttpServerCheckpointTest (partial) | Needs: retention policy + purge semantics tests |

---

## Section D: Analytics & Reports

| Req ID | Feature | Source | Required Tests | Using Modules | Current Status | Test File | Notes |
|--------|---------|--------|-----------------|----------------|----------------|-----------|-------|
| D001 | Report Generation | openapi.yaml: POST /api/v1/reports/generate | IntegrationTest: ReportGeneration, ReportSchema, ReportQueryCorrectness | platform-analytics, launcher | PARTIAL | ReportServiceTest | **ACTION NEEDED (Milestone 1)**: Create DataCloudHttpServerReportsTest + update fixtures |
| D002 | Query Correctness (filters, aggregations) | Feature docs, openapi.yaml | UnitTest: AggregationFormulas, FilterParsing; IntegrationTest: QueryRegressionFixtures | platform-analytics | PARTIAL | AnalyticsQueryEngineTest | Needs: deterministic fixtures with known results |
| D003 | Analytics Cache Consistency | Data Cloud architecture | IntegrationTest: CacheHitRate, CacheEviction, StalenessDetection | data-cloud-cache (implicit) | NOT_TESTED | — | **ACTION**: Create CacheConsistencyIntegrationTest |
| D004 | Report List/Retrieval by ID | openapi.yaml: GET /api/v1/reports | IntegrationTest: ReportRetrieval, ReportListPagination | platform-analytics, launcher | NOT_TESTED | — | **ACTION**: Add to DataCloudHttpServerReportsTest |
| D005 | Cost Reporting | Feature docs | UnitTest: CostCalculation, CostAggregation; IntegrationTest: CostAccuracy | platform-analytics | NOT_TESTED | — | **ACTION**: Create CostReportingTest |

---

## Section E: Memory Plane & Brain

| Req ID | Feature | Source | Required Tests | Using Modules | Current Status | Test File | Notes |
|--------|---------|--------|-----------------|----------------|----------------|-----------|-------|
| E001 | Memory Get All / By Tier | openapi.yaml: GET /api/v1/memory | IntegrationTest: MemoryRetrieval, TierInvariantCorrectness | platform-launcher, launcher | COMPLETE | DataCloudHttpServerMemoryTest ✅ | ✅ Verified HTTP endpoints |
| E002 | Memory Semantic Search | openapi.yaml: POST /api/v1/memory/search | IntegrationTest: SemanticSearchAccuracy, RankingCorrectness, SearchLimit | platform-launcher, launcher | PARTIAL | DataCloudHttpServerMemoryTest | **ACTION NEEDED (Milestone 1)**: Add semantic search fixture validation |
| E003 | Brain Workspace Read/Stream | openapi.yaml: GET /api/v1/brain/workspace | IntegrationTest: WorkspaceStreamCorrectness, WorkspaceLiveUpdates | platform-launcher, launcher | PARTIAL | DataCloudHttpServerBrainTest | **ACTION NEEDED (Milestone 1)**: Upgrade to full streaming test |
| E004 | Brain Threshold Management | openapi.yaml: GET/PUT /api/v1/brain/thresholds | IntegrationTest: ThresholdPersistence, ThresholdMutation | platform-launcher, launcher | PARTIAL | DataCloudHttpServerBrainTest | Needs: explicit put + persistence tests |
| E005 | Memory Tenant Isolation | Ghatana Rule 3 | IntegrationTest: MemoryTenantLeakageNegative, AgentMemorySeparation | platform-launcher, launcher | PARTIAL | DataCloudHttpServerMemoryTest | Needs: cross-tenant negative test |
| E006 | Brain Salience & Pattern Matching | Feature docs | UnitTest: SalienceScoring, PatternMatchingLogic; IntegrationTest: SalienceInvariants, PatternRecall | platform-launcher | NOT_TESTED | — | **ACTION**: Create BrainSalienceTest |

---

## Section F: Governance & Security

| Req ID | Feature | Source | Required Tests | Using Modules | Current Status | Test File | Notes |
|--------|---------|--------|-----------------|----------------|----------------|-----------|-------|
| F001 | Retention Classification | openapi.yaml: GET /api/v1/governance/retention | IntegrationTest: RetentionClassification, RetentionPolicyApplication | platform-launcher, launcher | NOT_TESTED | — | **ACTION (P2)**: Create DataCloudHttpServerGovernanceTest (extant but incomplete) |
| F002 | Data Purge (Destructive) | openapi.yaml: POST /api/v1/governance/purge | IntegrationTest: PurgeExecution, PurgePersistence, PurgeRecovery, DryRunVsLive | platform-launcher, launcher | PARTIAL | DataCloudHttpServerGovernanceTest | Needs: durability + rollback tests |
| F003 | Data Redaction | openapi.yaml: POST /api/v1/governance/redact | IntegrationTest: RedactionExecution, RedactionFieldMasking, RedactionRecovery | platform-launcher, launcher | PARTIAL | DataCloudHttpServerGovernanceTest | Needs: explicit masking + persistence tests |
| F004 | PII Fields List | openapi.yaml: GET /api/v1/governance/pii-fields | UnitTest: PiiClassification, PiiFieldLookup | platform-launcher, launcher | PARTIAL | DataCloudHttpServerGovernanceTest | Needs: completeness test against real schema |
| F005 | Compliance Summary | Feature docs | IntegrationTest: ComplianceSummaryAccuracy, ComplianceSummaryRefresh | platform-launcher | NOT_TESTED | — | **ACTION (P2)**: Create ComplianceSummaryTest |
| F006 | Audit & Access Logging | Ghatana compliance | IntegrationTest: AuditTrailPersistence, AccessLogFormat, AuditQueryCorrectness | platform-launcher, launcher | PARTIAL | (not found) | **ACTION (P2)**: Create AuditLoggingIntegrationTest |
| F007 | Authentication & Authorization | Ghatana Rule 3 | IntegrationTest: JwtValidation, RoleBasedAccess, TenantContextPropagation | launcher, platform-launcher | PARTIAL | DataCloudSecurityFilterTest | Needs: AuthZ matrix tests (who can run what?) |

---

## Section G: Learning & Model Registry

| Req ID | Feature | Source | Required Tests | Using Modules | Current Status | Test File | Notes |
|--------|---------|--------|-----------------|----------------|----------------|-----------|-------|
| G001 | Learning Trigger / Status | openapi.yaml: POST /api/v1/learning/trigger | IntegrationTest: LearningTrigger, LearningStatusPolling | platform-launcher, launcher | PARTIAL | DataCloudHttpServerLearningTest | Needs: state transition tests |
| G002 | Model Registry List/Register | openapi.yaml: GET, POST /api/v1/models | IntegrationTest: ModelLifecycle, ModelVersionManagement | platform-launcher, launcher | NOT_TESTED | — | **ACTION (P2)**: Create DataCloudHttpServerModelsTest |
| G003 | Model Promotion with Approval | Feature docs | IntegrationTest: PromotionStateMachine, PromotionApprovalFlow, InvalidPromotionRejection | platform-launcher, launcher | NOT_TESTED | — | **ACTION (P2)**: Add to DataCloudHttpServerModelsTest |
| G004 | Review Queue & Learning Stream | openapi.yaml: GET /api/v1/learning/review-queue | IntegrationTest: ReviewQueueOrdering, ReviewStateTransitions | launcher | PARTIAL | DataCloudHttpServerLearningTest | Needs: streaming + state tests |
| G005 | Approve/Reject Learning | openapi.yaml: POST /api/v1/learning/review | IntegrationTest: ApprovalPersistence, ApprovalAudit, RejectionReasoning | launcher | PARTIAL | DataCloudHttpServerLearningTest | Needs: audit + side-effect verification |

---

## Section H: Features & Ingest

| Req ID | Feature | Source | Required Tests | Using Modules | Current Status | Test File | Notes |
|--------|---------|--------|-----------------|----------------|----------------|-----------|-------|
| H001 | Feature Vector Ingest | openapi.yaml: POST /api/v1/features/ingest | IntegrationTest: FeatureVectorValidation, FeatureStoreWrite, Overwrite vs Append | feature-store-ingest, launcher | PARTIAL | FeatureStoreIngestLauncherTest | Needs: schema mismatch + latency tests |
| H002 | Feature Retrieve | openapi.yaml: GET /api/v1/features | IntegrationTest: FeatureRetrievalCorrectness, FeatureVersioning | platform-launcher, launcher | NOT_TESTED | — | **ACTION (P2)**: Create DataCloudHttpServerFeaturesTest |
| H003 | Feature Freshness & Overwrite Policy | Feature docs | IntegrationTest: FeatureStalenessPolicyEnforcement, OverwriteVsAppendSemantics | feature-store-ingest | PARTIAL | FeatureStoreIngestLauncherTest | Needs: deterministic timestamp + policy assertion tests |
| H004 | Feature Tenant Isolation | Ghatana Rule 3 | IntegrationTest: FeatureTenantLeakageNegative | feature-store-ingest, platform-launcher | PARTIAL | FeatureStoreIngestLauncherTest | Needs: explicit cross-tenant negative |

---

## Section I: Voice & Realtime

| Req ID | Feature | Source | Required Tests | Using Modules | Current Status | Test File | Notes |
|--------|---------|--------|-----------------|----------------|----------------|-----------|-------|
| I001 | Voice Intent Execute | openapi.yaml: POST /api/v1/voice/intent/execute | IntegrationTest: IntentExecutionCorrectness, IntentConfidenceScoring, FallbackBehavior | launcher | PARTIAL | DataCloudHttpServerVoiceTest | Needs: strict confidence assertions, error paths |
| I002 | Voice Intent Classify | openapi.yaml: POST /api/v1/voice/intent/classify | IntegrationTest: ClassificationAccuracy, ConfusionMatrixGeneration | launcher | PARTIAL | DataCloudHttpServerVoiceTest | Needs: classification-only contract alignment |
| I003 | Voice Intent List | openapi.yaml: GET /api/v1/voice/intents | IntegrationTest: IntentCatalogCompleteness, IntentMetadataCorrectness | launcher | NOT_TESTED | — | **ACTION (P3)**: Create voice intent list endpoint test |
| I004 | Transcript Retention & Audit | Feature docs | IntegrationTest: TranscriptPersistence, TranscriptRetentionPolicy, AuditTrail | launcher | NOT_TESTED | — | **ACTION (P3)**: Create VoiceTranscriptAuditTest |
| I005 | WebSocket Connection Lifecycle | openapi.yaml: WebSocket /api/v1/voice/stream | IntegrationTest: WsConnectionHandshake, WsMessageFlow, WsReconnect | launcher | PARTIAL | WebSocketResilienceTest | Needs: explicit handshake + message ordering tests |

---

## Section J: Plugins & Data Fabric

| Req ID | Feature | Source | Required Tests | Using Modules | Current Status | Test File | Notes |
|--------|---------|--------|-----------------|----------------|----------------|-----------|-------|
| J001 | Plugin Discovery/Install | spi, platform-plugins | IntegrationTest: PluginDiscovery, PluginInstall, PluginActivation | spi, platform-plugins | PARTIAL | (not found) | **ACTION (P2)**: Create PluginLifecycleIntegrationTest |
| J002 | Plugin Capability Declaration | spi docs | UnitTest: CapabilitySchema, CapabilityValidation | spi, platform-plugins | PARTIAL | (not found) | Needs: capability contract tests |
| J003 | Plugin Isolation & Error Containment | Data Cloud design | IntegrationTest: PluginCrashContainment, PluginResourceExhaustion, PluginTimeoutHandling | platform-plugins | NOT_TESTED | — | **ACTION (P2)**: Create PluginIsolationTest |
| J004 | Storage Profile CRUD | openapi.yaml: POST, GET, PUT, DELETE /api/v1/storage-profiles | IntegrationTest: StorageProfileLifecycle, StorageProfileValidation | platform-plugins, launcher | NOT_TESTED | — | **ACTION (P2)**: Create DataFabricStorageProfileTest |
| J005 | Data Connector CRUD & Sync | openapi.yaml: POST, GET, PUT, DELETE /api/v1/connectors | IntegrationTest: ConnectorLifecycle, SyncJobFlow, SyncRetryAndRecovery | platform-plugins, launcher | PARTIAL | (not found) | **ACTION (P2)**: Create DataFabricConnectorTest |
| J006 | Agent Registry Lookup | openapi.yaml: GET /api/v1/agents | IntegrationTest: RegistryLookup, CapabilityMatching, FailurePathLookup | agent-registry, launcher | PARTIAL | (not found) | **ACTION (P2)**: Create AgentRegistryContractIntegrationTest |
| J007 | Plugin & Connector Tenant Visibility | Ghatana Rule 3 | IntegrationTest: PluginTenantIsolation, ConnectorTenantLeakageNegative | platform-plugins, launcher | NOT_TESTED | — | **ACTION (P2)**: Add tenant tests to J004, J005 |

---

## Section K: AI Assistance & Suggestions

| Req ID | Feature | Source | Required Tests | Using Modules | Current Status | Test File | Notes |
|--------|---------|--------|-----------------|----------------|----------------|-----------|-------|
| K001 | AI Suggest for Entities | openapi.yaml: POST /api/v1/entities/suggest | IntegrationTest: SuggestionCorrectness, SuggestionConfidence, FallbackOnLowConfidence | platform-launcher, launcher | PARTIAL | DataCloudHttpServerAiAssistTest | Needs: confidence thresholds + deterministic fixtures |
| K002 | AI Suggest for Pipelines | Feature docs | IntegrationTest: PipelineSuggestion, PipelineSuggestionAccuracy | platform-launcher, launcher | PARTIAL | DataCloudHttpServerAiAssistTest | Needs: suggestion schema + suggestion scoring tests |
| K003 | Semantic Search | openapi.yaml: POST /api/v1/search/semantic | IntegrationTest: SemanticSearchAccuracy, RankingCorrectness, SearchLimit | platform-launcher, launcher | PARTIAL | DataCloudHttpServerAiAssistTest | Needs: deterministic embeddings + ranking assertions |
| K004 | AI Fallback Behavior | Data Cloud design | IntegrationTest: AiFallbackDeterminism, FallbackReasonability | platform-launcher | PARTIAL | AiFallbackDeterminismTest | Needs: stricter assertions on fallback results |
| K005 | AI Explain (for decisions) | Feature docs | IntegrationTest: ExplainResponseSchema, ExplainConfidence, ExplainAuditability | platform-launcher, launcher | NOT_TESTED | — | **ACTION (P2)**: Create AiExplainTest |

---

## Section L: Infrastructure & Observability

| Req ID | Feature | Source | Required Tests | Using Modules | Current Status | Test File | Notes |
|--------|---------|--------|-----------------|----------------|----------------|-----------|-------|
| L001 | Health Probe / Readiness | openapi.yaml: GET /healthz | IntegrationTest: HealthProbeSchema, HealthProbeLatency | launcher | PARTIAL | DataCloudHttpServerHealthTest | Needs: component-level health breakdown |
| L002 | Metrics Exposure (Prometheus) | Ghatana Rule 11 | IntegrationTest: MetricsSchema, MetricsCorrectness | launcher | PARTIAL | DataCloudHttpMetricsTest | Needs: cardinality + accuracy tests |
| L003 | Request Correlation IDs | Ghatana Rule 11 | IntegrationTest: CorrelationIdPropagation, CorrelationIdLogging | launcher | PARTIAL | (not found) | **ACTION (P2)**: Create CorrelationIdTest |
| L004 | Graceful Degradation | Data Cloud design | IntegrationTest: DegradedModeOperations, PartialFailures | launcher | NOT_TESTED | — | **ACTION (P2)**: Create ResilientOperationTest |

---

## Section M: UI/Frontend Requirements

| Req ID | Feature | Source | Required Tests | Using Modules | Current Status | Test File | Notes |
|--------|---------|--------|-----------------|----------------|----------------|-----------|-------|
| M001 | Shell & Routing | web-page-specs: 00_shell_and_routing.md | ContractTest: RouteResolution, AuthRedirect; E2ETest: NavigationFlow, NotFoundState | UI: routing, auth | NOT_TESTED | — | **ACTION (Milestone 2)**: Create UiShellRoutingContractTest |
| M002 | Dashboard Page | web-page-specs: 01_dashboard_page.md | LogicTest: WidgetRefresh, QuickActionClick; E2ETest: DashboardLoad, PermissionAwareRendering | UI: dashboard | NOT_TESTED | — | **ACTION (Milestone 2)**: Create DashboardPageE2ETest |
| M003 | Collections CRUD (UI) | web-page-specs: 02-04_collections_*.md | E2ETest: CollectionCreate, CollectionEdit, ValidationErrorHandling, SaveCancelFlow | UI: collections | NOT_TESTED | — | **ACTION (Milestone 2)**: Create CollectionsUIIntegrationTest |
| M004 | Workflow Designer | web-page-specs: 06_workflow_designer_canvas.md | LogicTest: CanvasInteraction, StateManagement; E2ETest: WorkflowSave, UndoRedo, AiAssist | UI: workflow-designer | NOT_TESTED | — | **ACTION (Milestone 2)**: Extend from existing canvas tests |
| M005 | Dataset Explorer | web-page-specs: 11_dataset_explorer_list_page.md | LogicTest: SearchFilter, Sorting, Pagination; E2ETest: DatasetDetailDrillDown, InsightsLoad | UI: dataset-explorer | NOT_TESTED | — | **ACTION (Milestone 2)**: Create DatasetExplorerE2ETest |
| M006 | SQL Workspace | web-page-specs: 14_sql_workspace_page.md | LogicTest: QueryEditor, ResultsDisplay, HistoryManagement; E2ETest: QueryExecution, ErrorHandling, AiSuggest | UI: sql-workspace | NOT_TESTED | — | **ACTION (Milestone 2)**: Create SqlWorkspaceE2ETest |
| M007 | Trust Center & Settings | web-page-specs: TrustCenter, SettingsPage | AccessibilityTest: KeyboardNav, ScreenReaderLabels; E2ETest: SettingsChange, PermissionDisplay | UI: trust-center, settings | NOT_TESTED | — | **ACTION (Milestone 2)**: Create TrustCenterAccessibilityTest |
| M008 | Shared UI Components | design-system | LogicTest: ButtonStates, FormInputValidation, ModalInteraction; E2ETest: ComponentInteractionAcrossPages | @ghatana/design-system | PARTIAL | (in design-system repo) | Verify data-cloud UI tests use canonical components |

---

## Coverage Summary (As of April 4, 2026)

| Category | Total | Complete | Partial | Not Tested | % Covered |
|----------|-------|----------|---------|------------|-----------|
| **Core (A-D)** | 18 | 6 | 5 | 7 | 61% |
| **Memory/Brain (E)** | 6 | 1 | 4 | 1 | 83% |
| **Governance (F)** | 7 | 0 | 4 | 3 | 57% |
| **Learning/Models (G)** | 5 | 0 | 3 | 2 | 60% |
| **Features (H)** | 4 | 0 | 3 | 1 | 75% |
| **Voice (I)** | 5 | 0 | 3 | 2 | 60% |
| **Plugins (J)** | 7 | 0 | 2 | 5 | 29% |
| **AI (K)** | 5 | 0 | 3 | 2 | 60% |
| **Infrastructure (L)** | 4 | 0 | 2 | 2 | 50% |
| **UI/Frontend (M)** | 8 | 0 | 0 | 8 | 0% |
| **TOTAL** | 69 | 7 | 29 | 33 | **52%** |

---

## Milestone 1 Execution Lane (Weeks 1-4)

### Week 1-2: Matrices & P1 Test Suite Creation
- [x] Create this requirement matrix (IN PROGRESS)
- [ ] Create DATA_CLOUD_ROUTE_COVERAGE_MATRIX.yaml
- [ ] Create DATA_CLOUD_MODULE_COVERAGE_MATRIX.md
- [ ] Create DATA_CLOUD_UI_COVERAGE_MATRIX.md
- [ ] Begin: **DataCloudHttpServerReportsTest** (Reports, D001-D005)
- [ ] Begin: **Extend DataCloudHttpServerBrainTest** (Workspace Stream, E003+)

### Week 3-4: Core P1 Completion
- [ ] **DataCloudHttpServerReportsTest** (complete, 80%+ coverage on analytics module)
- [ ] **DataCloudHttpServerMemoryTest** (extended with semantic search, E002)
- [ ] **DataCloudHttpServerBrainTest** (streaming + threshold management, E003-E004)
- [ ] All P1 tests passing in CI, >70% coverage on platform-api

---

## Code Review Checklist (All Test Additions)

Before approving any test addition, verify:

- [ ] Test extends correct TestBase (EventloopTestBase or DataCloudHttpServerTestBase)
- [ ] @DisplayName present on every @Test method
- [ ] Javadoc + @doc.* tags on test class
- [ ] No code duplication with existing tests (check templates)
- [ ] Tenant isolation tested if applicable
- [ ] Failure paths tested (schema validation, missing resources, auth failures)
- [ ] No Mockito mocks except where integration not feasible (note in comment)
- [ ] Response validated with Zod schema (TypeScript) or parsed JSON assertions (Java)
- [ ] No `any` types or `suppressWarnings`
- [ ] Requirement ID referenced in test name or Javadoc (e.g., `// C001: Pipeline CRUD`)

---

## References

- **Product Vision**: [products/data-cloud/README.md](../README.md)
- **Execution Plan**: [products/data-cloud/docs/DATA_CLOUD_E2E_VISION_EXECUTION_PLAN.md](./DATA_CLOUD_E2E_VISION_EXECUTION_PLAN.md)
- **OpenAPI Spec**: [products/data-cloud/docs/openapi.yaml](./openapi.yaml)
- **UI Specs**: [products/data-cloud/ui/docs/web-page-specs/INDEX.md](../ui/docs/web-page-specs/INDEX.md)
- **Ghatana Standards**: [.github/copilot-instructions.md](../../../../.github/copilot-instructions.md)
- **Critical Analysis**: [products/data-cloud/docs/testing/CRITICAL_ANALYSIS_AND_RISK_MITIGATION.md](./CRITICAL_ANALYSIS_AND_RISK_MITIGATION.md)

---

**Next Action**: Create DATA_CLOUD_ROUTE_COVERAGE_MATRIX.yaml (maps OpenAPI routes to test files)

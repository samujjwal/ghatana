# Data Cloud 100% Test Coverage Implementation Plan

**Document ID:** DC-COVERAGE-001  
**Version:** 1.0  
**Date:** 2026-04-04  
**Scope:** Complete test coverage for all libraries and modules in `products/data-cloud`

---

## Executive Summary

This document provides a comprehensive implementation plan to achieve 100% test coverage for Data Cloud, ensuring tests validate vision → requirements → use cases → flows → logic → computation → queries → interactions → outcomes, NOT implementation.

**Current State:**
- Overall requirement coverage: 52% (69 requirements: 7 complete, 29 partial, 33 not tested)
- Launcher: 71% line / 68% branch (812 tests passing)
- Platform-api: 62% line / 55% branch
- Other modules: 0-58% line, 0-55% branch
- UI/Frontend: 0% requirement coverage

**Target:**
- 100% structural coverage (line, branch, method/function)
- 100% behavioral coverage (requirements, use cases, flows, logic, queries, interactions, failure modes)

---

## 1. Source of Truth

### Vision Statement
Data-Cloud is the intelligent data foundation that makes AI/ML-native data management effortless, secure, and extensible for modern organizations.

### Strategic Goals
1. Unified Data Management (entity storage, event streaming, analytics, governance)
2. AI/ML-Native Experience (intelligence embedded in workflows)
3. Production-Ready Reliability (enterprise-grade security, observability)
4. Developer Experience (intuitive APIs, comprehensive SDKs)
5. Ecosystem Integration (foundational platform for Ghatana)

---

## 2. Complete Module Inventory

### 2.1 Java Gradle Modules

| Module | Purpose | Current Coverage | Target | Priority |
|--------|---------|------------------|--------|----------|
| spi | Plugin/storage contracts | 45% / 40% | 100% / 100% | P1 |
| platform-config | Config models, validation | 38% / 30% | 100% / 100% | P1 |
| platform-entity | Entity contracts, queries | 52% / 48% | 100% / 100% | P1 |
| platform-event | Event contracts, ordering | 44% / 35% | 100% / 100% | P1 |
| platform-analytics | Query, aggregation, reporting | 38% / 32% | 100% / 100% | P1 |
| platform-api | Controllers, DTOs, handlers | 62% / 55% | 100% / 100% | P1 |
| platform-client | Client contracts, adapters | 41% / 38% | 100% / 100% | P2 |
| platform-launcher | Service wiring, workflows | 58% / 50% | 100% / 100% | P1 |
| platform-plugins | Plugin implementations | 0% / 0% | 100% / 100% | P2 |
| feature-store-ingest | Feature ingest, writes | 44% / 40% | 100% / 100% | P2 |
| agent-registry | Agent catalog, lookup | 0% / 0% | 100% / 100% | P2 |
| launcher | HTTP transport, handlers | 71% / 68% | 100% / 100% | P1 |
| api | API contracts, OpenAPI | 28% / 20% | 100% / 100% | P2 |
| sdk | SDK generation | 0% / 0% | 100% / 100% | P3 |
| platform | Shared abstractions | 0% / 0% | 100% / 100% | P3 |
| data-cloud-cache | Query cache service | TBD | 100% / 100% | P2 |

### 2.2 Frontend Areas

| Area | Current Coverage | Target | Priority |
|------|------------------|--------|----------|
| ui/src/api | Service wrappers | Partial | 100% | P1 |
| ui/src/lib/api | Shared HTTP clients | Partial | 100% | P1 |
| ui/src/contracts | Schema contracts | Partial | 100% | P1 |
| ui/src/services | AI services | Partial | 100% | P1 |
| ui/src/stores | State management | Partial | 100% | P1 |
| ui/src/lib | Utilities (auth, a11y, etc.) | Partial | 100% | P1 |
| ui/src/components | Reusable components | Partial | 100% | P1 |
| ui/src/pages | Route-level pages | 0% | 100% | P1 |

---

## 3. Core Requirements (69 Total)

### A. Core Entities & Collections (A001-A006)
- A001: Entity CRUD Operations ✅ COMPLETE
- A002: Entity Search/Filter/Sort ✅ COMPLETE
- A003: Entity Anomaly Detection ⚠️ PARTIAL (needs deterministic fixtures)
- A004: Entity Export ⚠️ PARTIAL (needs streaming memory test)
- A005: Collection Tenancy Isolation ✅ COMPLETE
- A006: Entity Versioning & Concurrency ⚠️ PARTIAL (needs conflict resolution tests)

### B. Events & Event Streams (B001-B006)
- B001: Event Append ✅ COMPLETE
- B002: Event Query ✅ COMPLETE
- B003: Event Streaming ⚠️ PARTIAL (needs dedicated SSE test)
- B004: Event Tenant Isolation ⚠️ PARTIAL (needs explicit negative test)
- B005: Event Durability & Replayability ❌ NOT_TESTED
- B006: Event CDC ❌ NOT_TESTED

### C. Pipelines & Workflows (C001-C006)
- C001: Pipeline CRUD ✅ COMPLETE
- C002: Pipeline Metadata ⚠️ PARTIAL (needs staleness detection)
- C003: Pipeline Optimization ❌ NOT_TESTED
- C004: Pipeline Auditability ❌ NOT_TESTED
- C005: Checkpoint CRUD ✅ COMPLETE
- C006: Checkpoint Metadata ⚠️ PARTIAL (needs retention tests)

### D. Analytics & Reports (D001-D005)
- D001: Report Generation ⚠️ PARTIAL (needs HTTP endpoint test)
- D002: Query Correctness ⚠️ PARTIAL (needs deterministic fixtures)
- D003: Cache Consistency ❌ NOT_TESTED
- D004: Report Retrieval ❌ NOT_TESTED
- D005: Cost Reporting ❌ NOT_TESTED

### E. Memory Plane & Brain (E001-E006)
- E001: Memory Get ✅ COMPLETE
- E002: Semantic Search ⚠️ PARTIAL (needs ranking fixtures)
- E003: Brain Stream ⚠️ PARTIAL (needs streaming test)
- E004: Brain Thresholds ⚠️ PARTIAL (needs PUT tests)
- E005: Memory Isolation ⚠️ PARTIAL (needs negative test)
- E006: Brain Salience ❌ NOT_TESTED

### F. Governance & Security (F001-F007)
- F001: Retention ❌ NOT_TESTED
- F002: Data Purge ⚠️ PARTIAL (needs rollback tests)
- F003: Data Redaction ⚠️ PARTIAL (needs masking tests)
- F004: PII Fields ⚠️ PARTIAL (needs completeness test)
- F005: Compliance ❌ NOT_TESTED
- F006: Audit ❌ NOT_TESTED
- F007: AuthZ ⚠️ PARTIAL (needs matrix tests)

### G. Learning & Model Registry (G001-G005)
- G001: Learning Trigger ⚠️ PARTIAL (needs state transitions)
- G002: Model Registry ❌ NOT_TESTED
- G003: Model Promotion ❌ NOT_TESTED
- G004: Review Queue ⚠️ PARTIAL (needs streaming test)
- G005: Approve/Reject ⚠️ PARTIAL (needs side-effect tests)

### H. Features & Ingest (H001-H004)
- H001: Feature Ingest ⚠️ PARTIAL (needs schema mismatch tests)
- H002: Feature Retrieve ❌ NOT_TESTED
- H003: Feature Freshness ⚠️ PARTIAL (needs deterministic tests)
- H004: Feature Isolation ⚠️ PARTIAL (needs negative test)

### I. Voice & Realtime (I001-I005)
- I001: Voice Execute ⚠️ PARTIAL (needs strict assertions)
- I002: Voice Classify ⚠️ PARTIAL (needs accuracy tests)
- I003: Voice List ❌ NOT_TESTED
- I004: Transcript Audit ❌ NOT_TESTED
- I005: WebSocket ⚠️ PARTIAL (needs explicit tests)

### J. Plugins & Data Fabric (J001-J007)
- J001: Plugin Lifecycle ❌ NOT_TESTED
- J002: Plugin Capability ❌ NOT_TESTED
- J003: Plugin Isolation ❌ NOT_TESTED
- J004: Storage Profile ❌ NOT_TESTED
- J005: Data Connector ❌ NOT_TESTED
- J006: Agent Registry ❌ NOT_TESTED
- J007: Plugin/Connector Isolation ❌ NOT_TESTED

### K. AI Assistance (K001-K005)
- K001: AI Suggest Entity ⚠️ PARTIAL (needs threshold tests)
- K002: AI Suggest Pipeline ⚠️ PARTIAL (needs schema tests)
- K003: Semantic Search ⚠️ PARTIAL (needs deterministic embeddings)
- K004: AI Fallback ⚠️ PARTIAL (needs stricter assertions)
- K005: AI Explain ❌ NOT_TESTED

### L. Infrastructure (L001-L004)
- L001: Health Probe ⚠️ PARTIAL (needs component breakdown)
- L002: Metrics ⚠️ PARTIAL (needs cardinality tests)
- L003: Correlation ID ❌ NOT_TESTED
- L004: Graceful Degradation ❌ NOT_TESTED

### M. UI/Frontend (M001-M008)
- M001: Shell Routing ❌ NOT_TESTED
- M002: Dashboard ❌ NOT_TESTED
- M003: Collections UI ❌ NOT_TESTED
- M004: Workflow Designer ❌ NOT_TESTED
- M005: Dataset Explorer ❌ NOT_TESTED
- M006: SQL Workspace ❌ NOT_TESTED
- M007: Trust Center ❌ NOT_TESTED
- M008: Settings ❌ NOT_TESTED

---

## 4. Test Implementation Plan

### Phase 1: P1 Critical Path (Weeks 1-4)

#### Week 1: Foundation & Reports
- [ ] Create DataCloudHttpServerReportsTest (D001, D004)
  - POST /api/v1/reports/generate
  - GET /api/v1/reports
  - GET /api/v1/reports/{id}
  - Validate query execution, formatting, caching
- [ ] Extend ReportServiceTest with deterministic fixtures (D002)
  - SUM, AVG, COUNT, MIN, MAX aggregates
  - Filter correctness (equals, range, regex, set membership)
  - Sort correctness (ascending, descending, multi-key)
- [ ] Create EventDurabilityAndReplayTest (B005)
  - Replay from offset
  - Duplicate handling
  - Offset progression validation
- [ ] Create CdcStreamTest (B006)
  - CDC stream accuracy
  - Change detection
  - Latency assertions

#### Week 2: Memory, Brain, Learning
- [ ] Extend DataCloudHttpServerMemoryTest (E002, E005)
  - Semantic search ranking with fixtures
  - Cross-tenant negative test
- [ ] Extend DataCloudHttpServerBrainTest (E003, E004)
  - Workspace streaming test
  - Threshold PUT + persistence test
- [ ] Create BrainSalienceTest (E006)
  - Salience scoring invariants
  - Pattern matching with fixtures
  - Threshold classification
- [ ] Extend DataCloudHttpServerLearningTest (G001, G004)
  - State transition tests (pending→approved, pending→rejected)
  - Review queue streaming test
- [ ] Extend DataCloudHttpServerLearningTest (G005)
  - Audit side-effect verification
  - Rejection reason validation

#### Week 3: Models, Features, Voice
- [ ] Create DataCloudHttpServerModelsTest (G002, G003)
  - Model lifecycle (list, register, get)
  - Model promotion state machine
  - Invalid transition rejection
- [ ] Create DataCloudHttpServerFeaturesTest (H002)
  - Feature retrieval correctness
  - Feature versioning
- [ ] Extend FeatureStoreIngestLauncherTest (H001, H003)
  - Schema mismatch handling
  - Deterministic timestamp + overwrite semantics
  - Cross-tenant negative test
- [ ] Extend DataCloudHttpServerVoiceTest (I001, I002)
  - Strict confidence threshold assertions
  - Classification accuracy tests
- [ ] Create VoiceIntentListTest (I003)
  - Intent catalog completeness
- [ ] Create VoiceTranscriptAuditTest (I004)
  - Transcript retention policy
  - Audit trail persistence

#### Week 4: Streaming & WebSocket
- [ ] Create DataCloudHttpServerSseTest (B003)
  - SSE event stream
  - Connection lifecycle
  - Backpressure handling
- [ ] Extend WebSocketResilienceTest (I005)
  - Explicit handshake test
  - Message ordering test
  - Reconnect test
- [ ] Create CacheConsistencyIntegrationTest (D003)
  - Cache hit rate validation
  - Cache eviction on schema change
  - Staleness detection

### Phase 2: P2 Real Interactions (Weeks 5-8)

#### Week 5: Governance & Security
- [ ] Extend DataCloudHttpServerGovernanceTest (F001, F002, F003)
  - Retention classification + policy application
  - Purge execution with rollback tests
  - Redaction execution with masking tests
- [ ] Extend DataCloudHttpServerGovernanceTest (F004)
  - PII field completeness test
- [ ] Create ComplianceSummaryTest (F005)
  - Compliance summary accuracy
  - Refresh logic
- [ ] Create AuditLoggingIntegrationTest (F006)
  - Audit persistence
  - Access log format
  - Audit query correctness
- [ ] Extend DataCloudSecurityFilterTest (F007)
  - RBAC matrix tests
  - Permission checks per role

#### Week 6: Plugins & Data Fabric
- [ ] Create PluginLifecycleIntegrationTest (J001)
  - Plugin discovery, install, activate, deactivate, remove
- [ ] Create CapabilityContractTest (J002)
  - Plugin capability declaration validation
- [ ] Create PluginIsolationTest (J003)
  - Plugin crash containment
  - Resource exhaustion
  - Timeout handling
- [ ] Create DataFabricStorageProfileTest (J004)
  - Storage profile CRUD
  - Validation
  - Tenant isolation
- [ ] Create DataFabricConnectorTest (J005)
  - Connector CRUD
  - Sync job flow
  - Retry and recovery
- [ ] Create AgentRegistryContractIntegrationTest (J006)
  - Registry lookup
  - Capability matching
  - Failure path lookup
- [ ] Add tenant isolation tests to J004, J005 (J007)

#### Week 7: AI Assistance
- [ ] Extend DataCloudHttpServerAiAssistTest (K001, K002)
  - Confidence threshold tests
  - Suggestion schema validation
  - Suggestion scoring tests
- [ ] Extend DataCloudHttpServerAiAssistTest (K003)
  - Deterministic embedding fixtures
  - Ranking correctness assertions
- [ ] Extend AiFallbackDeterminismTest (K004)
  - Stricter fallback result assertions
- [ ] Create AiExplainTest (K005)
  - Explain response schema
  - Explain confidence
  - Explain auditability

#### Week 8: Infrastructure
- [ ] Extend DataCloudHttpServerHealthTest (L001)
  - Component-level health breakdown
- [ ] Extend DataCloudHttpMetricsTest (L002)
  - Metrics schema validation
  - Cardinality tests
  - Accuracy tests
- [ ] Create CorrelationIdTest (L003)
  - Correlation ID propagation
  - Correlation ID logging
- [ ] Create ResilientOperationTest (L004)
  - Degraded mode operations
  - Partial failure handling

### Phase 3: P3 Structural Closure (Weeks 9-12)

#### Week 9: Module Structural Coverage
- [ ] spi: Extend CapabilityContractTest (QuerySpec, BatchResult)
- [ ] platform-config: Add ConfigValidationTest (properties, defaults, validators)
- [ ] platform-entity: Extend EntityQueryBoundaryTest (schema, versioning, CDC)
- [ ] platform-event: Extend EventOrderingInvariantTest (append, query, dedup, replay)

#### Week 10: Module Structural Coverage Continued
- [ ] platform-analytics: Add QueryCorrectnessFixtureTest (formulas, aggregation, reporting)
- [ ] platform-api: Extend existing tests (Reports, Memory, Brain handlers)
- [ ] platform-client: Extend ClientSerializationTest (serialization, error mapping)
- [ ] platform-launcher: Add PipelinePersistenceIntegrationTest (real DB, testcontainers)

#### Week 11: Plugin & Feature Modules
- [ ] platform-plugins: Add PluginLifecycleTest (vector, Kafka, Redis, S3, Iceberg)
- [ ] feature-store-ingest: Extend FeatureIngestErrorHandlingTest (validation, schema, overwrite)
- [ ] agent-registry: Add AgentRegistryContractTest (registration, lookup, policy)

#### Week 12: Remaining Modules
- [ ] api: Extend OpenApiDriftDetectionTest (route/schema/doc parity)
- [ ] sdk: Add SdkSmokeTest (generated code, schema parity)
- [ ] platform: Audit for dead code, remove or test
- [ ] data-cloud-cache: Extend cache tests (correctness, staleness, eviction)

### Phase 4: UI/Frontend Coverage (Weeks 13-16)

#### Week 13: UI Foundation
- [ ] Create UiShellRoutingContractTest (M001)
  - Route resolution
  - Auth redirects
  - Suspense
  - 404 handling
- [ ] Create DashboardPageE2ETest (M002)
  - Widget refresh
  - Quick actions
  - Permission-aware rendering

#### Week 14: Collections & Workflows
- [ ] Create CollectionsUIIntegrationTest (M003)
  - Collection create
  - Collection edit
  - Validation error handling
  - Save/cancel flow
- [ ] Extend WorkflowDesigner tests (M004)
  - Canvas interaction
  - State management
  - Workflow save
  - Undo/redo
  - AI assist

#### Week 15: Data Exploration
- [ ] Create DatasetExplorerE2ETest (M005)
  - Search, filter, sorting
  - Pagination
  - Dataset detail drill-down
  - Insights load
- [ ] Create SqlWorkspaceE2ETest (M006)
  - Query editor
  - Results display
  - History management
  - Error handling
  - AI suggest

#### Week 16: Settings & Trust
- [ ] Create TrustCenterAccessibilityTest (M007)
  - Keyboard navigation
  - Screen reader labels
  - Permission display
- [ ] Create SettingsPageAccessControlTest (M008)
  - Settings change
  - Permission validation
  - Access control

---

## 5. Integration Test Requirements

### 5.1 Real Database Integration
- **PipelinePersistenceIntegrationTest**: Real PostgreSQL, testcontainers
  - Pipeline CRUD with real persistence
  - Version conflict handling
  - Metadata staleness detection
- **CheckpointPersistenceIntegrationTest**: Real PostgreSQL, testcontainers
  - Checkpoint CRUD with real persistence
  - Large object storage
  - Retention policy enforcement
- **ReportsPersistenceIntegrationTest**: Real PostgreSQL, testcontainers
  - Report cache lifecycle
  - Cache invalidation on schema change
  - Report retrieval by ID

### 5.2 Real Event Store Integration
- **EventReplayIntegrationTest**: Real Kafka, testcontainers
  - Offset progression validation
  - Event replay from offset
  - Duplicate handling
- **SseStreamingIntegrationTest**: Real Kafka + SSE
  - SSE connection lifecycle
  - Event streaming correctness
  - Backpressure handling

### 5.3 Real Storage Backend Integration
- **MemorySearchIntegrationTest**: Real PostgreSQL + vector extension
  - Semantic search correctness
  - Ranking validation
  - Tier transition logic
- **ModelRegistryIntegrationTest**: Real PostgreSQL + S3
  - Model file storage
  - Version management
  - Promotion logic
- **FeatureStoreIntegrationTest**: Real PostgreSQL + Redis
  - Feature vector storage
  - Cache consistency
  - Overwrite vs append semantics

### 5.4 Real Analytics Integration
- **GovernanceRedactionIntegrationTest**: Real PostgreSQL
  - Redaction execution
  - Field masking correctness
  - Recovery capability
- **GovernancePurgeIntegrationTest**: Real PostgreSQL
  - Purge execution
  - Rollback capability
  - Dry-run vs live-run divergence

### 5.5 Cross-Module Integration
- **AccessControlIntegrationTest**: Real auth + RBAC
  - Access control matrix
  - Permission checks
  - Cross-tenant isolation
- **AuditTrailIntegrationTest**: Real audit logging
  - Audit persistence
  - Audit format validation
  - Audit query correctness

---

## 6. Edge Cases & Failure Modes Test Matrix

### 6.1 Invalid Input Tests
- [ ] Null/missing required fields (all CRUD endpoints)
- [ ] Empty string required fields (all CRUD endpoints)
- [ ] Invalid data types (all CRUD endpoints)
- [ ] Invalid formats (JSON, timestamp, UUID)
- [ ] Malformed JSON (all POST/PUT endpoints)

### 6.2 Boundary Value Tests
- [ ] Numeric boundaries (min, max, overflow, underflow)
- [ ] String boundaries (min, max, exceeded)
- [ ] Array boundaries (min, max, exceeded)
- [ ] Pagination boundaries (page=0, page=max, size=1, size=max)
- [ ] Timestamp boundaries (past, future, min, max, invalid)

### 6.3 Concurrency Tests
- [ ] Concurrent entity create (same ID)
- [ ] Concurrent entity update (same version)
- [ ] Concurrent event append (sequential offsets)
- [ ] Concurrent pipeline update (conflict handling)
- [ ] Concurrent checkpoint save (isolation)
- [ ] Concurrent memory update (conflict handling)
- [ ] Concurrent brain threshold update (last-write-wins)
- [ ] Concurrent feature ingest (last-write-wins)

### 6.4 Timeout & Retry Tests
- [ ] Database timeout (504 response)
- [ ] Kafka timeout (504 response)
- [ ] ClickHouse timeout (504 response)
- [ ] Redis timeout (504 response)
- [ ] S3 timeout (504 response)
- [ ] Transient failure retry (success after retry)
- [ ] Permanent failure retry (failure after limit)

### 6.5 Partial Failure Tests
- [ ] Batch operation partial failure (207 Multi-Status)
- [ ] Multi-step operation partial failure (atomic failure)
- [ ] Distributed transaction partial failure (rollback)
- [ ] Cache partial failure (fallback to direct query)

### 6.6 Idempotency Tests
- [ ] Entity create idempotency (200 OK on duplicate)
- [ ] Event append idempotency (409 on duplicate)
- [ ] Pipeline save idempotency (200 OK on duplicate)
- [ ] Checkpoint save idempotency (200 OK on duplicate)
- [ ] Feature ingest idempotency (overwrite on duplicate)

### 6.7 Large Data Tests
- [ ] Large entity (10MB success, 11MB failure)
- [ ] Large batch (1000 success, 1001 failure)
- [ ] Large query result (pagination validation)
- [ ] Large export (streaming validation)
- [ ] Large feature vector (1000 dims success, 100k failure)

### 6.8 Performance Tests
- [ ] Entity create < 100ms p99 (JMH benchmark)
- [ ] Event append < 50ms p99 (JMH benchmark)
- [ ] Entity query < 200ms p99 (JMH benchmark)
- [ ] Report generation < 10s p99 for 1M rows (JMH benchmark)
- [ ] Feature ingest < 1s p99 for 1000 features (JMH benchmark)

---

## 7. Test Quality Standards

### 7.1 Reject Tests That
- Mirror implementation (check internal method calls instead of behavior)
- Only check status/calls (no response body or side effect validation)
- Use shallow assertions (null/type checks only, no specific values)
- Hide logic via mocks (mock business logic instead of testing it)
- Pass with wrong outcomes (missing critical assertions)

### 7.2 Tests MUST Assert
- **Outputs**: Response body structure and values, return values, error messages
- **State Changes**: Database state, cache state, in-memory state
- **Persistence Correctness**: Data correctly persisted/retrieved/updated/deleted
- **Side Effects**: Events emitted, metrics updated, cache invalidated, logs written
- **Invariants**: Business rules, data integrity, consistency preserved

### 7.3 Test Naming Convention
```java
@Test
@DisplayName("[Feature]: [action]_[scenario]_[assertion]")
void entityCreate_validInput_returns201() { }

@Test
@DisplayName("[Boundary]: missing_required_field_returns400")
void entityCreate_missingName_returns400() { }

@Test
@DisplayName("[Tenant Isolation]: different_tenant_sees_no_data")
void entityQuery_tenantAlpha_cantSeeTenantBeta() { }
```

### 7.4 Test Structure Template
```java
/**
 * @doc.type class
 * @doc.purpose Behavioral tests for [FEATURE]
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("[Feature] HTTP Handler Integration Tests")
@ExtendWith(MockitoExtension.class)
class DataCloudHttpServer[Feature]Test extends DataCloudHttpServerTestBase {
    
    @Mock DataCloudClient mockClient;
    private [Feature]Service service;
    
    @BeforeEach
    void setUp() {
        service = new [Feature]Service(mockClient);
        startServer(new [Feature]Handler(service));
    }
    
    @Test
    @DisplayName("[Feature]: create_validInput_returns201")
    void create_validInput_returns201() {
        // Arrange
        String tenantId = TestConstants.TENANT_ALPHA;
        var input = [Feature]Request.builder().field("value").build();
        
        lenient().when(mockClient.create(eq(tenantId), anyString(), any()))
            .thenReturn(Promise.of(result));
        
        // Act
        var request = POST("/api/v1/[route]")
            .withHeader("X-Tenant-ID", tenantId)
            .withBody(jsonEncode(input));
        var response = runPromise(() -> client.send(request));
        
        // Assert
        assertStatusCode(response, TestConstants.HTTP_CREATED);
        Map<String, Object> body = parseJsonResponse(response);
        assertThat(body).containsKeys("id", "name");
        
        // Verify
        verify(mockClient).create(eq(tenantId), anyString(), argThat(matches));
    }
    
    @Test
    @DisplayName("[Boundary]: missing_required_field_returns400")
    void create_missingField_returns400() {
        var request = POST("/api/v1/[route]").withBody("{ invalid }");
        var response = runPromise(() -> client.send(request));
        
        assertStatusCode(response, TestConstants.HTTP_BAD_REQUEST);
        verify(mockClient, never()).create(any(), any(), any());
    }
}
```

---

## 8. Coverage Gates

### 8.1 Structural Coverage Gate
```bash
# Java coverage
./gradlew products:data-cloud:jacocoTestReport
./gradlew products:data-cloud:jacocoTestCoverageVerification

# TypeScript coverage
cd products/data-cloud/ui
npm run test:coverage
```
- Target: 100% line, 100% branch, 100% method/function
- CI fails if coverage drops below 100%
- Exclusions: Generated code, dead code (to be removed)

### 8.2 Behavioral Coverage Gate
```bash
# Parse requirement matrix
./scripts/verify-requirement-coverage.sh
```
- Every requirement must have test mapping
- Every use case must have success + failure coverage
- CI fails if requirement not mapped or not tested

### 8.3 Route Coverage Gate
```bash
# Parse OpenAPI spec
./scripts/verify-route-coverage.sh
```
- Every OpenAPI route must have contract test
- Every OpenAPI route must have behavior test
- CI fails if route not covered

### 8.4 UI Contract Drift Gate
```bash
# Validate UI contracts
./scripts/verify-ui-contracts.sh
```
- UI contracts must match OpenAPI spec
- UI E2E mocks must use canonical routes
- CI fails if contract drift detected

### 8.5 Streaming Coverage Gate
```bash
# Validate streaming routes
./scripts/verify-streaming-coverage.sh
```
- Every SSE/WebSocket route must have dedicated test
- CI fails if streaming route not covered

### 8.6 Product Surface Completeness Gate
```bash
# Validate all surfaces covered
./scripts/verify-surface-completeness.sh
```
- Every module in Section 2.1 must be in matrix
- Every frontend area in Section 2.2 must be in matrix
- Every requirement in Section 3 must be tested
- CI fails if surface not covered

---

## 9. Execution Order

1. **Week 1-4**: Phase 1 - P1 Critical Path (Reports, Memory, Brain, Learning, Models, Features, Voice, Streaming)
2. **Week 5-8**: Phase 2 - P2 Real Interactions (Governance, Plugins, AI, Infrastructure)
3. **Week 9-12**: Phase 3 - P3 Structural Closure (Module-by-module coverage)
4. **Week 13-16**: Phase 4 - UI/Frontend Coverage (All UI pages and workflows)
5. **Week 17-18**: CI Gate Implementation (All coverage gates)
6. **Week 19-20**: Final Verification (Full suite run, 100% coverage validation)

---

## 10. Acceptance Checklist

- [ ] Every requirement in matrix has tests (69/69)
- [ ] Every use case has success + failure coverage
- [ ] Every route in OpenAPI has contract + behavior tests
- [ ] Every destructive flow has audit + rollback/failure tests
- [ ] Every streaming route has dedicated tests
- [ ] UI tests use only canonical routes
- [ ] No fake integration tests for critical paths
- [ ] JaCoCo shows 100% line/branch/method for Java
- [ ] Vitest shows 100% line/branch/function for TypeScript
- [ ] Every Java module represented in coverage matrix
- [ ] Every frontend area represented in test matrix
- [ ] CI fails on any regression

---

## 11. Missing Coverage Summary

### High Priority Gaps (P1)
- B005: Event Durability & Replayability
- B006: Event CDC
- D001: Report Generation HTTP endpoint
- D003: Cache Consistency
- D004: Report Retrieval
- E006: Brain Salience
- G002: Model Registry
- G003: Model Promotion
- H002: Feature Retrieve
- I003: Voice Intent List
- I004: Transcript Audit

### Medium Priority Gaps (P2)
- C003: Pipeline Optimization
- C004: Pipeline Auditability
- D005: Cost Reporting
- F001-F007: Governance & Security (most not tested)
- J001-J007: Plugins & Data Fabric (all not tested)
- K005: AI Explain
- L003-L004: Correlation ID, Graceful Degradation

### Low Priority Gaps (P3)
- M001-M008: UI/Frontend (all not tested)
- sdk: SDK generation
- platform: Shared abstractions (audit needed)

---

## 12. Risk Mitigation

### Risk 1: Test Flakiness
- **Mitigation**: Use deterministic test data, avoid time-based assertions, use testcontainers for consistency
- **Action**: All tests must be deterministic, no retries or sleep loops

### Risk 2: Slow Test Suite
- **Mitigation**: Parallelize tests, use in-memory DB where possible, cache testcontainers
- **Action**: Target < 10 minutes for full suite

### Risk 3: Mock Overuse
- **Mitigation**: Prefer real integration tests, use mocks only for external dependencies
- **Action**: Replace fake integration tests with real module wiring

### Risk 4: Coverage Gaps Missed
- **Mitigation**: Automated coverage gates, requirement matrix validation
- **Action**: CI fails if any requirement not mapped or not tested

### Risk 5: UI Contract Drift
- **Mitigation**: Automated contract validation, canonical route enforcement
- **Action**: CI fails if UI uses non-canonical routes

---

**Next Action**: Begin Phase 1, Week 1 - Create DataCloudHttpServerReportsTest and extend ReportServiceTest

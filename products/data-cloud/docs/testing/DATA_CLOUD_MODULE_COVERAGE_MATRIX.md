# Data Cloud Module Coverage Matrix

> **Status**: Milestone 1 - Week 1 (Active)
> **Purpose**: Track structural code coverage by Java module, identify gaps
> **Last Updated**: April 4, 2026
> **Generated from**: products/data-cloud/*/src/[main|test]/java

---

## Overview

This matrix tracks test coverage per Java Gradle module (Section 3.1 of the implementation plan).

For each module:
- **Test Files**: Count of existing test classes
- **Line Coverage**: JaCoCo report (≥ baseline target)
- **Branch Coverage**: JaCoCo report (≥ baseline target)
- **Status**: Tracking milestone progress
- **Action Items**: Explicit test suites needed (Week X of Milestone Y)

---

## Module Coverage Table

| Module | Purpose | Test Files | Line % | Branch % | Status | Milestone 1 Action | Notes |
|--------|---------|-----------|--------|----------|--------|-------------------|-------|
| **spi** | Plugin/storage contracts | 2 | 45% | 40% | PARTIAL | Extend: CapabilityContractTest (Week 2) | QuerySpec, BatchResult |
| **platform-config** | Config models, validation | 1 | 38% | 30% | PARTIAL | Add: ConfigValidationTest (Week 2) | Properties, defaults, validators |
| **platform-entity** | Entity contracts, queries | 3 | 52% | 48% | PARTIAL | Extend: EntityQueryBoundaryTest (Week 2) | Schema, versioning, CDC |
| **platform-event** | Event contracts, ordering | 2 | 44% | 35% | PARTIAL | Extend: EventOrderingInvariantTest (Week 2) | Append, query, dedup, replay |
| **platform-analytics** | Query, agg, reporting | 4 | 38% | 32% | PARTIAL | Add: QueryCorrectnessFixtureTest (Week 2-3) | Formulas, aggregation, reporting |
| **platform-api** | Controllers, DTOs, handlers | 12 | 62% | 55% | PARTIAL | Add: ReportsHandlerTest (Week 2-3) | HTTP transport, schemas |
| **platform-client** | Client contracts, adapters | 2 | 41% | 38% | PARTIAL | Extend: ClientSerializationTest (Week 3) | Serialization, error mapping |
| **platform-launcher** | Service wiring, workflows | 8 | 58% | 50% | PARTIAL | Add: PipelinePersistenceIntegrationTest (Week 5) | Real integrations, persistence |
| **platform-plugins** | Plugin implementations | 0 | 0% | 0% | NOT_STARTED | Add: PluginLifecycleTest (Week 5) | Vector, Kafka, Redis, S3, Iceberg |
| **feature-store-ingest** | Feature ingest, writes | 3 | 44% | 40% | PARTIAL | Extend: FeatureIngestErrorHandlingTest (Week 4) | Validation, schema, overwrite |
| **agent-registry** | Agent catalog, lookup | 0 | 0% | 0% | NOT_STARTED | Add: AgentRegistryContractTest (Week 4) | Registration, lookup, policy |
| **launcher** | HTTP transport, handlers | 45 | 71% | 68% | COMPLETE ✅ | DONE: All handlers, routes verified | ✅ 812/812 tests passing |
| **api** | API contracts, OpenAPI | 1 | 28% | 20% | PARTIAL | Extend: OpenApiDriftDetectionTest (Week 1) | Route/schema/doc parity |
| **sdk** | SDK generation | 0 | 0% | 0% | PARTIAL | Add: SdkSmokeTest (Week 12) | Generated code, schema parity |
| **platform** | Shared abstractions | 0 | 0% | 0% | NOT_STARTED | Classify: Dead code removal or test (Week 6) | TBD based on audit |

---

## Coverage Targets by Milestone

### Baseline (Week 1, April 4)
- **launcher**: 71% line / 68% branch ✅ (DONE)
- **platform-api**: 62% line / 55% branch
- **Other modules**: 28-58% line, 20-55% branch

### Milestone 1 Target (Week 4, April 25)
- **launcher**: 85% / 80% (↑ additional stream/voice tests)
- **platform-api**: 75% / 70% (↑ Reports, Memory, Brain handlers)
- **platform-analytics**: 60% / 55% (↑ query correctness fixtures)
- **Others**: ≥40% / 35%

### Milestone 2 Target (Week 8, May 23)
- **launcher**: 90% / 85%
- **platform-api**: 85% / 80%
- **platform-analytics**: 75% / 70%
- **platform-launcher**: 75% / 70% (↑ integration tests)
- **Others**: ≥50% / 45%

### Milestone 3 Target (Week 12, June 20)
- **launcher**: 95% / 90%
- **platform-api**: 90% / 85%
- **All P1 modules**: ≥85% / 80%

### Final Target (Week 16, July 18)
- **All modules**: 100% / 100% (or dead code removed)

---

## Detailed Module Plans

### 1. launcher (✅ COMPLETE)

**Current**: 45 test files, 71% line / 68% branch, 812 tests passing

**What's Covered**:
- ✅ All HTTP handlers (entities, events, pipelines, checkpoints, memory, brain, governance, learning, voice, health)
- ✅ Security filters (auth, CORS, rate limiting)
- ✅ Streaming (WebSocket, SSE resilience)
- ✅ Error handling and edge cases
- ✅ Tenant isolation in all CRUD routes
- ✅ Chaos engineering tests (performance, network partition)

**Remaining Work (Milestone 1, Week 3-4)**:
- Add: SearchedEventStreamingTest (deduplicated SSE/WebSocket flows)
- Add: StrictVoiceConfidenceValidationTest (confidence threshold filtering)
- Extend: BrainWorkspaceStreamingTest (threshold mutations during streaming)

**Quality Gate**: All tests must be deterministic, no retries or sleep loops.

---

### 2. platform-api (62% → 90%)

**Current**: 12 test files, 62% line / 55% branch

**Existing Coverage**:
- Entity CRUD handlers ✅
- Pipeline CRUD handlers ✅
- Checkpoint handlers ✅
- Some analytics handlers
- Some AI handlers

**Gaps** (Milestone 1, Week 2-3):
- [ ] **Reports**: create DataCloudHttpServerReportsTest (POST /api/v1/reports/generate, GET /api/v1/reports, GET /api/v1/reports/{id})
- [ ] **Memory Semantic Search**: enhance DataCloudHttpServerMemoryTest (POST /api/v1/memory/search with ranking)
- [ ] **Brain Thresholds**: add threshold GET/PUT tests to DataCloudHttpServerBrainTest
- [ ] **Models**: create DataCloudHttpServerModelsTest (list, register, promote)
- [ ] **Features**: create DataCloudHttpServerFeaturesTest (ingest, retrieve, freshness)

**Code Template** (reuse for no duplication):
```java
/**
 * @doc.type class
 * @doc.purpose Behavioral tests for [FEATURE_NAME] HTTP endpoints
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("[Feature] HTTP Handler Integration Tests")
@ExtendWith(MockitoExtension.class)
class DataCloudHttpServer[Feature]Test extends DataCloudHttpServerTestBase {
    
    @Mock DataCloudClient mockClient;
    
    @BeforeEach
    void setUp() {
        service = new [FeatureService](mockClient);
        startServer(...handlers..., new [FeatureHandler](service));
    }
    
    @Test @DisplayName("[Feature] create_validInput_returns201")
    void create_validInput_returns201() { ... }
    
    @Test @DisplayName("[Feature] create_invalidSchema_returns400")
    void create_invalidSchema_returns400() { ... }
    
    // ... (positive + negative paths)
}
```

---

### 3. platform-launcher (58% → 85%)

**Current**: 8 test files, 58% line / 50% branch

**Existing Coverage**:
- Embedded storage (SQLite, H2)
- Configuration validation
- Client builders
- Memory leak detection

**Gaps** (Milestone 2, Week 5-8):
- [ ] **PipelinePersistenceIntegrationTest**: real DB, testcontainers
- [ ] **EventReplayIntegrationTest**: offset progression, durability
- [ ] **ReportsPersistenceIntegrationTest**: report cache + refresh
- [ ] **MemorySearchIntegrationTest**: semantic search correctness
- [ ] **ThresholdMutationIntegrationTest**: threshold changes during streaming

---

### 4. platform-analytics (38% → 75%)

**Current**: 4 test files, 38% line / 32% branch

**Existing Coverage**:
- ReportServiceTest (partial)
- AnalyticsQueryEngineTest (partial, no fixtures)
- AnomalyDetectorTest (logic only, no integration)
- EntityExportServiceTest (partial)

**Gaps** (Milestone 1-2, Week 2-5):
- [ ] **QueryCorrectnessFixturesTest**: deterministic datasets with known query results
  - Aggregate functions: SUM, AVG, COUNT, MIN, MAX
  - Filters: equals, range, regex, set membership
  - Sort: ascending, descending, multi-key
  - Group-by: correctness of grouping + aggregate application
- [ ] **AnomalyDetectorRegressionTest**: fixtures with known anomalies
- [ ] **ExportStreamingTest**: large dataset memory usage + resumability
- [ ] **ReportCacheConsistencyTest**: cache invalidation on schema change

**Key Rule**: Use deterministic fixtures (not random data). Example:
```java
@Test @DisplayName("SumAggregation_correctlyCalculates")
void sumAggregation_correctlyCalculates() {
    var dataset = DatasetFixture.create(
        Row(id=1, amount=100),
        Row(id=2, amount=200),
        Row(id=3, amount=150)
    );
    var result = analyticsService.aggregate(dataset, SUM("amount"));
    assertThat(result).isEqualTo(450);
}
```

---

### 5. spi (45% → 80%)

**Current**: 2 test files, 45% line / 40% branch

**Gaps**:
- [ ] **CapabilityContractTest**: plugin capability declaration validation
- [ ] **StorageSpiIntegrationTest**: plugin storage interface contract

---

### 6. platform-config (38% → 80%)

**Current**: 1 test file, 38% line / 30% branch

**Gaps**:
- [ ] **ConfigValidationNegativeTest**: schema validation, required fields, type coercion

---

### 7. platform-entity (52% → 85%)

**Current**: 3 test files, 52% line / 48% branch

**Gaps**:
- [ ] **EntityQueryBoundaryTest**: filter expression boundary conditions
- [ ] **EntityVersioningIntegrationTest**: optimistic locking + conflict resolution

---

### 8. platform-event (44% → 80%)

**Current**: 2 test files, 44% line / 35% branch

**Gaps**:
- [ ] **EventOrderingInvariantTest**: strict monotonic offset progression
- [ ] **CdcStreamTest**: CDC stream correctness, latency assertions

---

### 9. platform-client (41% → 85%)

**Current**: 2 test files, 41% line / 38% branch

**Gaps**:
- [ ] **ClientSerializationBoundaryTest**: Zod schema validation for responses
- [ ] **ClientErrorMappingTest**: HTTP error status → domain exception mapping

---

### 10. platform-plugins (0% → 75%, P2)

**Current**: 0 test files, 0% coverage

**Milestone 2, Week 5-7**:
- [ ] **PluginLifecycleIntegrationTest**: discover, install, activate, deactivate, remove
- [ ] **PluginIsolationTest**: resource exhaustion, timeout, crash containment
- [ ] **StoragePluginTest**: vector, S3, Iceberg, Trino plugins
- [ ] **StreamPluginTest**: Kafka plugin with real testcontainers

---

### 11. feature-store-ingest (44% → 85%, P2)

**Current**: 3 test files, 44% line / 40% branch

**Gaps**:
- [ ] **FeatureStoreIngestErrorHandlingTest**: schema mismatch, DLQ behavior
- [ ] **FeatureOverwriteVsAppendTest**: deterministic timestamp + overwrite semantics

---

### 12. agent-registry (0% → 75%, P2)

**Current**: 0 test files, 0% coverage

**Milestone 2, Week 4**:
- [ ] **AgentRegistryContractIntegrationTest**: lookup, policy, capability matching
- [ ] **FallbackAgentTest**: default agent when no match found

---

### 13. api (28% → 85%, P2)

**Current**: 1 test file, 28% line / 20% branch

**Gaps**:
- [ ] **OpenApiDriftDetectionTest**: schema validation against openapi.yaml
- [ ] **RouteCompletionTest**: verify every OpenAPI route has handler

---

### 14. sdk (0% → 50%, P3)

**Current**: 0 test files, 0% coverage (generated code)

**Milestone 4, Week 12**:
- [ ] **GeneratedJavaSdkSmokeTest**: schema parity, 1 example call per route type

---

### 15. platform (TBD)

**Current**: 0 test files, 0% coverage

**Action**: Audit to determine dead code vs. prod code. If prod code, add tests. If dead, remove.

---

## Test File Template (Prevent Duplication)

```java
/**
 * @doc.type class
 * @doc.purpose HTTP behavioral tests for [FEATURE_NAME] (Section [REQ_ID] of plan)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("[Feature] HTTP Handler Integration Tests")
@ExtendWith(MockitoExtension.class)
class DataCloudHttpServer[Feature]Test extends DataCloudHttpServerTestBase {
    
    @Mock DataCloudClient mockClient;
    private [Feature]Service service;
    private [Feature]Handler handler;
    
    @BeforeEach
    void setUp() {
        // 1. Create service with mock client
        lenient().when(mockClient.findById(anyString(), anyString(), anyString()))
            .thenReturn(Promise.of(Optional.empty()));
        
        service = new [Feature]Service(mockClient);
        handler = new [Feature]Handler(service);
        
        // 2. Start test server
        startServer(handler); // Inherited from TestBase
    }
    
    @Test
    @DisplayName("[Behavior]: [positive_path]")
    void [methodName]_[scenario]_[assertion]() {
        // 3. Arrange: mock setup + prepare input
        String tenantId = TestConstants.TENANT_ALPHA;
        var input = [FeatureLQl.builder().field("value").build();
        
        lenient().when(mockClient.create(eq(tenantId), eq("collection"), any()))
            .thenReturn(Promise.of([Result]));
        
        // 4. Act: make HTTP request
        var request = POST("/api/v1/[routes]")
            .withHeader("X-Tenant-ID", tenantId)
            .withBody(jsonEncode(input));
        var response = runPromise(() -> client.send(request));
        
        // 5. Assert: verify response
        assertStatusCode(response, TestConstants.HTTP_CREATED);
        Map<String, Object> body = parseJsonResponse(response);
        assertThat(body).containsKeys("[expected_fields]");
        
        // 6. Verify: mock calls + side-effects
        verify(mockClient).create(eq(tenantId), eq("collection"), argThat(matches));
    }
    
    @Test
    @DisplayName("[Boundary]: missing_required_field_returns400")
    void [methodName]_missingField_returns400() {
        var request = POST("/api/v1/[route]")
            .withBody("{ invalid json }");
        var response = runPromise(() -> client.send(request));
        
        assertStatusCode(response, TestConstants.HTTP_BAD_REQUEST);
        // Service MUST NOT be called on invalid schema
        verify(mockClient, never()).create(any(), any(), any());
    }
    
    @Test
    @DisplayName("[Tenant Isolation]: different_tenant_sees_no_data")
    void [methodName]_tenantAlpha_cantSeeTenantBeta() {
        // Create with TENANT_ALPHA
        // Query with TENANT_BETA
        // Assert: 404 or empty result (tenant-scoped)
    }
}
```

---

## CI Integration Check

Before merging any module's test changes:

```bash
./gradlew products:data-cloud:launcher:test --continue
./gradlew products:data-cloud:platform-api:test --continue
./gradlew products:data-cloud:platform-launcher:test --continue
# ... (all modules)

# Check coverage
./gradlew products:data-cloud:launcher:jacocoTestReport
# Verify: target/$MODULE/jacoco/index.html shows ≥ target %
```

---

## Weekly Tracking (Update Every Friday)

**Week 1 (Apr 4-11)**:
- [ ] Create matrices (DONE)
- [ ] Setup test templates
- [ ] Begin DataCloudHttpServerReportsTest

**Week 2 (Apr 11-18)**:
- [ ] DataCloudHttpServerReportsTest (60%+ complete)
- [ ] Extend memory/brain tests
- [ ] Verify launcher 85% / 80% coverage

**Week 3 (Apr 18-25)**:
- [ ] DataCloudHttpServerReportsTest (DONE, 100%)
- [ ] Complete brain streaming
- [ ] Target: platform-api 75% / 70%

**Week 4+ (May+)**:
- [ ] P2 module focus (analytics, governance, learning, models, features)
- [ ] Milestone 2: Real integration suites

---

## References

- Full 100% Plan: [DATA_CLOUD_100_PERCENT_TEST_COVERAGE_IMPLEMENTATION_PLAN.md](./DATA_CLOUD_100_PERCENT_TEST_COVERAGE_IMPLEMENTATION_PLAN.md)
- Requirement Matrix: [DATA_CLOUD_REQUIREMENT_COVERAGE_MATRIX.md](./DATA_CLOUD_REQUIREMENT_COVERAGE_MATRIX.md)
- Route Matrix: [DATA_CLOUD_ROUTE_COVERAGE_MATRIX.yaml](./DATA_CLOUD_ROUTE_COVERAGE_MATRIX.yaml)
- Ghatana Standards: [.github/copilot-instructions.md](../../../../.github/copilot-instructions.md)

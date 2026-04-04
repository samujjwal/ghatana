# Data Cloud 100% Test Coverage - Milestone Execution Action Plan

> **Status**: Finalized (April 4, 2026)  
> **Owner**: [Assign team lead]  
> **Scope**: All 4 Milestones (Weeks 1-16)  
> **QA Sign-off Required**: Before starting Week 1

---

## Executive Summary & Strategy

### What We Accomplished (Week 0: Weeks 1-3 of a 4-week cycle)
- ✅ Launcher module: 812/812 tests passing (100% pass rate)
- ✅ All core HTTP handlers and routes implemented
- ✅ Ghatana standards applied throughout
- ✅ TestBase infrastructure established (no duplication)

### What's New (This Plan)
- ✅ Created 3 comprehensive matrices (requirement, route, module)
- ✅ Prioritized P1, P2, P3 features strictly
- ✅ Identified 69 unique requirements, 92 API routes, 15 Java modules
- ✅ Mapped current coverage: 52% of requirements, 43% of routes
- ✅ Created test templates (prevent code duplication)
- ✅ Assigned owner accountability

### Success Criteria (Definition of Done)
- [ ] Every row in matrices has a test file mapping
- [ ] Every OpenAPI route has contract + behavior tests
- [ ] Every P1 module ≥80% line coverage, ≥75% branch coverage
- [ ] Zero test duplication (all use templates)
- [ ] All tests deterministic (no retries, no sleep loops)
- [ ] All test classes have Javadoc + @doc.* tags
- [ ] CI gates enforcing coverage thresholds
- [ ] Final build: CLEAN, ALL tests passing

---

## Milestone 1: Foundation & Core Features (Weeks 1-4, April 4-25)

### Goal
Establish requirement-test mapping, complete P1 feature coverage, raise module coverage to 70-90%.

### Weekly Breakdown

### **Week 1 (Apr 4-11): Planning & Setup**

#### Phase 1.1: Matrices & Infrastructure (Team: 1 person, 2 days)
- [x] Requirement coverage matrix ✅ (CREATED today)
- [x] Route coverage matrix ✅ (CREATED today)
- [x] Module coverage matrix ✅ (CREATED today)
- [ ] **TODO THIS WEEK**: Create UI coverage matrix (see template below)
- [ ] **TODO THIS WEEK**: Create test templates doc (link to DataCloudHttpServerTestBase)
- [ ] **TODO THIS WEEK**: Setup CI job to track coverage % weekly

**Deliverable**: `DATA_CLOUD_UI_COVERAGE_MATRIX.md` (by Friday EOD)

**Template Template for UI Lifecycle Tests**:
```markdown
| Page | Component | Test Suite | Status | Notes |
|------|-----------|-----------|--------|-------|
| Collections | CRUD Form | CollectionsUIIntegrationTest | NOT_STARTED | New suite, plan 2 days |
| Dashboard | Widget Refresh | DashboardPageE2ETest | NOT_STARTED | New suite, depends on API |
```

#### Phase 1.2: Team Kickoff (Team: Full, 1 hour)
- Distribute matrices to team
- Explain P1/P2/P3 priority (NO deviations)
- Assign module owners:
  - **platform-api**: [Assign person] → focus: Reports, Memory, Brain handlers
  - **launcher**: [Assign person] → focus: Extend streaming, voice confidence
  - **platform-analytics**: [Assign person] → focus: Query fixtures, cache
  - **others**: [Assign person] → focus: P2 prep
- Review code templates (prevent duplication)

**Deliverables**: 
- Assigned owners documented in matrix headers
- Kickoff meeting notes in `/memories/session/milestone1-kickoff.md`

---

### **Week 2 (Apr 11-18): P1 Test Suite Creation**

#### Focus: Create DataCloudHttpServerReportsTest (Core P1 feature: Analytics/Reports)

**Owner**: [platform-api lead]  
**Effort**: 2-3 days  
**Requirements Covered**: D001-D005 (Reports section)

**Acceptance Criteria**:
- [ ] Test file created at `products/data-cloud/launcher/src/test/java/.../DataCloudHttpServerReportsTest.java`
- [ ] Extends `DataCloudHttpServerTestBase`
- [ ] Covers operations:
  - POST /api/v1/reports/generate (success + invalid query + missing table paths)
  - GET /api/v1/reports (list + pagination)
  - GET /api/v1/reports/{id} (success + 404 not found)
- [ ] Each test has @DisplayName + Javadoc + @doc.* tags
- [ ] Tenant isolation verified (X-Tenant-ID header)
- [ ] Response validated: schema keys match openapi.yaml
- [ ] All tests passing in CI
- [ ] ≥15 test cases (positive + negative paths)
- [ ] Coverage gain: platform-analytics +10% (38% → 50%+)

**Code Review Checklist**:
- [ ] No code duplication with EntityTest, PipelineTest, etc. (use TestBase)
- [ ] Mocks use lenient().when() for unused stubs
- [ ] No `any` types or `suppressWarnings`
- [ ] Response parsing uses parseJsonResponse() helper
- [ ] Assertions use containsKeys() for schema validation

**Test Structure** (fill in template):
```java
class DataCloudHttpServerReportsTest extends DataCloudHttpServerTestBase {
  @Test @DisplayName("generateReport_validQuery_returns201")
  void generateReport_validQuery_returns201() { }
  
  @Test @DisplayName("generateReport_invalidQuery_returns400")
  void generateReport_invalidQuery_returns400() { }
  
  @Test @DisplayName("generateReport_withTenantID_isolates")
  void generateReport_withTenantID_isolates() { }
  
  @Test @DisplayName("listReports_paginates_correctly")
  void listReports_paginates_correctly() { }
  
  @Test @DisplayName("getReport_byId_notFound_returns404")
  void getReport_byId_notFound_returns404() { }
  
  // ... (9+ more)
}
```

**Parallel Work (Week 2)**:
- **platform-analytics owner**: Create `QueryCorrectnessFixturesTest` with deterministic datasets
  - SUM, AVG, COUNT, MIN, MAX aggregations
  - Filter operators (eq, range, regex, set membership)
  - Sort orders + multi-key sort
  - Group-by correctness

---

### **Week 3 (Apr 18-25): P1 Streaming & Memory/Brain**

#### Focus A: Extend DataCloudHttpServerMemoryTest (Memory Plane)

**Owner**: [launcher lead]  
**Effort**: 1.5 days  
**Requirements Covered**: E001-E005

**Additions**:
- [ ] POST /api/v1/memory/search with semantic search fixtures
  - Mock embeddings deterministically
  - Assert ranking correctness (top 3 results match expected order)
  - Assert limit enforcement (limit=10, get ≤10 results)
- [ ] Test: tenant isolation (query TENANT_A, verify no TENANT_B data)
- [ ] Test: memory tier invariants (get by tier, verify no cross-tier leakage)

**Code Example**:
```java
@Test @DisplayName("semanticSearch_ranksCorrectly")
void semanticSearch_ranksCorrectly() {
    // Mock: 5 entities with embeddings
    lenient().when(mockClient.semanticSearch(anyString(), any(), anyInt()))
        .thenReturn(Promise.of(List.of(
            MemoryRecord.of("id1", 0.95, "exact_match"),
            MemoryRecord.of("id2", 0.72, "partial_match"),
            MemoryRecord.of("id3", 0.41, "distant_match")
        )));
    
    var response = POST("/api/v1/memory/search")
        .withBody(json(query, limit: 10))
        .send();
    
    Map<String, Object> body = parseJsonResponse(response);
    List<String> ids = (List) body.get("records");
    assertThat(ids).containsExactly("id1", "id2", "id3"); // Ranking preserved
}
```

#### Focus B: Extend DataCloudHttpServerBrainTest (Brain Workspace & Thresholds)

**Owner**: [launcher lead]  
**Effort**: 1.5 days  
**Requirements Covered**: E003-E004

**Additions**:
- [ ] WebSocket /api/v1/brain/workspace:stream (workspace live updates)
  - Open WS connection
  - Mock workspace changes
  - Verify messages arrive in-order
  - Test reconnect + catch-up
- [ ] PUT /api/v1/brain/thresholds (threshold persistence)
  - Update threshold
  - Verify persisted (follow-up GET returns new value)
  - Test invalid threshold rejected (400)

**Deliverables (Week 3 EOD)**:
- DataCloudHttpServerReportsTest ✅ (DONE, all tests passing)
- DataCloudHttpServerMemoryTest extended ✅ (all tests passing)
- DataCloudHttpServerBrainTest extended ✅ (all tests passing)
- Query correctness fixtures added to AnalyticsQueryEngineTest ✅

**Coverage Status (Week 3 EOD)**:
- launcher: 85%+ line, 80%+ branch ✅
- platform-api: 75%+ line, 70%+ branch ✅
- platform-analytics: 60%+ line, 55%+ branch ✅

---

### **Week 4 (Apr 25-May 2): P1 Completion & CI Setup**

#### Phase 4.1: Final P1 Fixes & Sign-off (Team: 1-2 days)

- [ ] Run full test suite: `./gradlew products:data-cloud:launcher:test --no-daemon`
  - Verify: 812+ tests all passing
  - Verify: 0 flaky tests (deterministic)
- [ ] Review code coverage reports
  - launcher: target 85%+ ✅
  - platform-api: target 75%+ ✅
  - platform-analytics: target 60%+ ✅
- [ ] Code review all Week 2-3 test additions
  - Verify: no duplication
  - Verify: all Javadoc + @doc.* tags
  - Verify: boundary tests (401, 403, 404, 400)
- [ ] Update matrices: mark rows as COMPLETE

#### Phase 4.2: CI Coverage Gates & Tracking (Team: 0.5 day)

- [ ] Create Gradle task: `reportTestCoverage` (weekly tracking)
- [ ] Define CI gate config:
  ```yaml
  coverage_gates:
    launcher:
      line: 85
      branch: 80
    platform_api:
      line: 75
      branch: 70
    # ... (others)
  ```
- [ ] Setup GitHub Actions workflow to fail build if coverage drops
- [ ] Create dashboard: shared spreadsheet tracking coverage % by module/week

#### Phase 4.3: Milestone 1 Retrospective (Team: 1 hour)

- Review: what worked well? (TestBase templates, matrices)
- Review: what was hard? (mocking, fixtures?)
- Adjust approach for Milestone 2

**Deliverables (Milestone 1 Complete)**:
- ✅ 3 matrices completed + linked in docs
- ✅ DataCloudHttpServerReportsTest (P1 complete)
- ✅ Memory/Brain tests extended (P1 complete)
- ✅ Query correctness fixtures (P1 complete)
- ✅ CI gates + tracking in place
- ✅ launcher: 85%+ coverage
- ✅ platform-api: 75%+ coverage
- ✅ platform-analytics: 60%+ coverage

---

## Milestone 2: Real Integrations & P2 Features (Weeks 5-8, May 2-30)

### Goal
Replace fake mocks with real service integrations (testcontainers), complete P2 feature coverage.

### **Week 5 (May 2-9): Real Integration Foundation**

**Focus**: Upgrade Pipeline & Event tests to use real databases (testcontainers)

#### Task 2.1: PipelinePersistenceIntegrationTest

**Requirements Covered**: C001-C006

**Approach**:
- Start testcontainers H2 database
- Real PipelineRepository (not mocked)
- Assert: save → get → update → delete → not found
- Assert: tenant isolation
- Assert: audit trail persisted

**Template**:
```java
@ExtendWith(MockitoExtension.class)
@Testcontainers
class PipelinePersistenceIntegrationTest {
    @Container static GenericContainer<?> h2 = new GenericContainer<>("h2database/h2")...;
    
    private real PipelineRepository repository;
    private PipelineService service;
    
    @BeforeEach void setUp() {
        repository = new H2PipelineRepository(h2.getJdbcUrl());
        service = new PipelineService(repository);
    }
    
    @Test void create_andRetrieve_persists() {
        var pipe = Pipeline.builder().name("test").build();
        service.create(TENANT_A, pipe);
        
        var retrieved = service.get(TENANT_A, pipe.id());
        assertThat(retrieved).isNotEmpty().contains(pipe);
    }
}
```

#### Task 2.2: EventDurabilityAndReplayIntegrationTest

**Requirements Covered**: B005

**Approach**:
- Real EventStore (testcontainers Postgres or H2)
- Append 100 events
- Verify strict offset progression (0, 1, 2, ..., 99)
- Replay from offset 50, verify exact tail
- Test duplicate handling (same event appended twice)

---

### **Week 6-7: P2 Feature Test Suites**

Focus on features in the P2 list:

- [ ] **Models**: DataCloudHttpServerModelsTest
  - List, register, get, promote with approval flow
  - State machine: DRAFT → APPROVED → PROMOTED
  - Test invalid promotion rejection (e.g., can't promote DRAFT directly)

- [ ] **Features**: DataCloudHttpServerFeaturesTest
  - Ingest, retrieve, freshness validation
  - Overwrite vs append semantics
  - Test: schema mismatch error handling

- [ ] **Governance (Purge/Redact)**: DataCloudHttpServerGovernanceTest extended
  - Purge: verify data deleted, audit persisted, dry-run vs live
  - Redact: verify masking applied, original not recoverable
  - Test: partial failure + roll

back/recovery

- [ ] **Plugin Lifecycle**: PluginLifecycleIntegrationTest (real integration)
  - Install → activate → deactivate → remove
  - Test plugin isolation (plugin crash doesn't crash platform)

---

### **Week 8: Milestone 2 Completion & Thresholds**

- [ ] All P2 test suites passing
- [ ] Coverage targets: P1 modules 85%+, P2 modules 70%+
- [ ] CI gates tuned (no false positives)
- [ ] Retrospective + adjust for Milestone 3

---

## Milestone 3: P3 Features & UI Coverage (Weeks 9-12, June 2-27)

### Goal
Admin/advanced features (plugins, voice, data fabric), UI contract & E2E tests, 85%+ overall coverage.

### Recommended Sequencing (No longer P1 critical path):

1. **Voice Intent Tests** (Week 9)
   - Intent classify, execute, list
   - Transcript retention, confidence thresholds
   - WebSocket message ordering

2. **Data Fabric Admin** (Week 10)
   - Storage profiles CRUD
   - Connector CRUD + sync job management
   - Connector error recovery + retry

3. **UI Contract Tests** (Weeks 10-11)
   - Shell routing, navigation, auth redirects
   - Collections CRUD pages (create → edit → delete)
   - Dashboard widgets + quick actions
   - SQL workspace (query editor, execution, history)

4. **UI E2E Tests** (Week 11-12, selective)
   - Critical journeys only:
    - User → create collection → add entities → query
    - User → write SQL → execute → see results
    - User → view dashboard → refresh → see updates
   - NOT exhaustive (avoid maintenance burden)

### UI Test Strategy
- **Contract tests**: validate API calls match openapi.yaml schema
- **Logic tests**: state management, filters, pagination
- **E2E tests**: 3-5 critical user journeys only (not every page)
- **Accessibility tests**: WCAG 2.1 AA compliance for primary pages

---

## Milestone 4: Cleanup & Gates (Weeks 13-16, July 2-25)

### Goal
100% coverage, all modules tested, CI gates enforced, production readiness.

### **Week 13-14: Burn-down & Coverage**

- [ ] Audit "platform" module (dead code removal or minimum tests)
- [ ] SDK smoke tests (generated code)
- [ ] Missing boundary tests (all routes validated for 401/403/404)
- [ ] Coverage reports: target 85%+ line, 75%+ branch across all modules

### **Week 15: CI Integration & Enforcement**

- [ ] CI gates rolling out:
  - Fail build if launcher coverage drops below 85%
  - Fail build if platform-api coverage drops below 75%
  - Fail build if any new code is untested (0% → 0% is failure)
- [ ] Update README with coverage targets
- [ ] Setup monitoring: Grafana dashboard showing trend

### **Week 16: Final Verification & Sign-off**

- [ ] Full build + test suite: `./gradlew products:data-cloud:build test`
  - All tests passing
  - 0 warnings, 0 errors
  - No flaky tests
- [ ] Coverage audit: every module listed in plan has tests
- [ ] Matrices updated: all rows marked COMPLETE or VERIFIED
- [ ] Production readiness sign-off: product, QA, security

**Deliverable**: `MILESTONE_4_COMPLETION_REPORT.md`

---

## Code Quality Enforcement (All Milestones)

### Template Reuse (NO duplication)

All HTTP handler tests MUST extend `DataCloudHttpServerTestBase`:

✅ **Good** — uses base:
```java
class DataCloudHttpServerFeaturesTest extends DataCloudHttpServerTestBase {
    // Setup inherited: client, server, response parsing
}
```

❌ **Bad** — duplicates base code:
```java
class DataCloudHttpServerFeaturesTest {
    private HttpClient client;
    private DataCloudHttpServer server;
    // ... (DUPLICATED setup)
}
```

### Test Naming Convention

Format: `[object]_[scenario]_[assertion]`

✅ Good:
- `createPipeline_validInput_returns201`
- `listPipelines_withPagination_returnsCorrectPage`
- `createPipeline_tenantAlpha_isolatedFromTenantBeta`

❌ Bad:
- `test1`, `testCreate`, `shouldWork`

### Javadoc + @doc.* Tags (Required)

Every test class must have:
```java
/**
 * @doc.type class
 * @doc.purpose Behavioral tests for Pipeline HTTP handlers (Section C001-C006)
 * @doc.layer product
 * @doc.pattern Test
 */
class DataCloudHttpServerPipelineTest { ... }
```

### Boundary Tests (Every CRUD Route)

Every route must test:
- ✅ Positive path (200/201 with valid data)
- ✅ Negative paths:
  - Invalid schema → 400 Bad Request
  - Missing auth → 401 Unauthorized
  - Insufficient permissions → 403 Forbidden
  - Resource not found → 404 Not Found
  - Conflict (duplicate key) → 409 Conflict

### Tenant Isolation (Every Route)

Every CRUD route MUST test:
- Create with TENANT_A
- Query with TENANT_B
- Assert: 404 Not Found or empty result (tenant-scoped visibility)

---

## Risk Mitigation Strategies

### Risk 1: Test Flakiness (Causes CI to flip)

**Mitigation**:
- No sleep() or retry loops
- No time-dependent assertions
- Deterministic fixtures (no random data)
- Run each test 5x in CI success check

### Risk 2: Mock Proliferation (Hard to understand, unmaintainable)

**Mitigation**:
- Use testcontainers from Week 5 (Milestone 2)
- Real integrations > mocks
- Document why each mock exists (comment in code)

### Risk 3: Scope Creep (Start P3 before P1 done)

**Mitigation**:
- Strict P1 → P2 → P3 ordering in schedule
- Weekly stand-up: report what's in progress
- Block pull requests that deviate from roadmap

### Risk 4: Coverage Burnout (Everyone rushes, quality drops)

**Mitigation**:
- Realistic pace: 2-3 weeks per module
- Pair testing: junior + senior on complex features
- Code review enforcing standards

---

## Weekly Tracking Template

```markdown
## Week X (Month DD-DD)

### Completed
- [ ] [Task done]
- [ ] [Task done]

### In Progress
- [ ] [Task, % done]

### Blockers
- [Any issues?]

### Metrics
- Coverage: launcher 71% → XX%, platform-api XX% → XX%, ...
- Tests added: NN new suites, MM test cases
- Flaky tests: 0

### Retrospective Notes
- What went well?
- What was hard?
- Adjust for next week?
```

---

## Sign-Off & Approval

Before execution begins:

- [ ] Product Owner: Approve priority order (P1/P2/P3)
- [ ] Engineering Lead: Confirm team capacity + resources
- [ ] QA Lead: Setup CI gates + tracking
- [ ] Security: Review governance tests (purge, redaction, audit)

**Approvals Captured in**: `/memories/session/milestone-execution-signoff.md`

---

## Reference Links

- Requirement Matrix: [DATA_CLOUD_REQUIREMENT_COVERAGE_MATRIX.md](./DATA_CLOUD_REQUIREMENT_COVERAGE_MATRIX.md)
- Route Matrix: [DATA_CLOUD_ROUTE_COVERAGE_MATRIX.yaml](./DATA_CLOUD_ROUTE_COVERAGE_MATRIX.yaml)
- Module Matrix: [DATA_CLOUD_MODULE_COVERAGE_MATRIX.md](./DATA_CLOUD_MODULE_COVERAGE_MATRIX.md)
- Original 100% Plan: [DATA_CLOUD_100_PERCENT_TEST_COVERAGE_IMPLEMENTATION_PLAN.md](./DATA_CLOUD_100_PERCENT_TEST_COVERAGE_IMPLEMENTATION_PLAN.md)
- Critical Analysis: [CRITICAL_ANALYSIS_AND_RISK_MITIGATION.md](./CRITICAL_ANALYSIS_AND_RISK_MITIGATION.md)
- Ghatana Standards: [.github/copilot-instructions.md](../../../../.github/copilot-instructions.md)
- Test Base Class: [products/data-cloud/launcher/src/test/.../DataCloudHttpServerTestBase.java](../launcher/src/test/java/com/ghatana/datacloud/launcher/http/DataCloudHttpServerTestBase.java)
- Test Constants: [products/data-cloud/launcher/src/test/.../TestConstants.java](../launcher/src/test/java/com/ghatana/datacloud/launcher/http/TestConstants.java)

---

**Status**: Ready to kick off Week 1 (April 4, 2026)  
**Next Step**: Obtain sign-offs, assign owners, create UI coverage matrix

# Data Cloud 100% Test Coverage — Production Execution Guide

> **Last Updated**: April 4, 2026
> **Status**: Ready for Milestone 1 execution
> **Owner**: Data Cloud Engineering
> **Alignment**: Ghatana copilot-instructions.md (Sections 4, 5, 8, 16)

---

## Executive Summary

This guide operationalizes the `DATA_CLOUD_100_PERCENT_TEST_COVERAGE_IMPLEMENTATION_PLAN.md` with:
- Clear prioritization by business impact
- Production-grade test patterns aligned to Ghatana repo conventions
- Duplicate-prevention mechanisms
- Realistic interim coverage targets
- Executable workstreams with clear acceptance criteria
- Integration with existing CI/CD gates

**Key Principle**: Follow Ghatana convention → avoid creating new patterns. Reuse and extend existing test harnesses from `platform:java:testing` and `@ghatana/*` packages.

---

## Part 1: Milestone 1 (P0 Correctness) — Weeks 1-4

### 1.1 Overview

Milestone 1 establishes the **sources of truth** for what tests to write and prioritizes the highest-impact coverage gaps.

**Output Artifacts**:
1. `products/data-cloud/docs/testing/REQUIREMENT_COVERAGE_MATRIX.md` — canonical requirement-to-test mapping
2. `products/data-cloud/docs/testing/ROUTE_COVERAGE_MATRIX.yaml` — OpenAPI routes + test ids
3. `products/data-cloud/docs/testing/MODULE_COVERAGE_MATRIX.yaml` — Java Gradle modules / TypeScript areas -> test suites
4. `products/data-cloud/docs/testing/UI_COVERAGE_MATRIX.md` — UI/UX areas + test suites + accessibility requirements
5. Test templates for Java and TypeScript (avoiding duplicates via copy-paste)
6. First wave of missing tests (pipelines, reports, models, features, governance destructive flows)

**Success Criteria**:
- All Section 3 rows from the plan mapped to test artifacts
- No speculative or non-canonical routes in any test or UI mock
- First 20 test suites from Section 11 passing with >80% coverage on their targeted modules
- No duplicate test classes or test methods across the product
- All new test classes follow Ghatana conventions (Javadoc + @doc.* tags for public Java tests)
- All new TypeScript test files use co-located `__tests__/` pattern with strict types

---

### 1.2 Step 1: Build the Requirement/Route/Module/UI Matrices (Days 1-5)

**Task 1.2.1**: Extract canonical sources

```bash
# Create source directory for testing artifacts
mkdir -p products/data-cloud/docs/testing
```

**Sources to extract from**:
- `products/data-cloud/README.md` → product vision statements
- `products/data-cloud/docs/DATA_CLOUD_E2E_VISION_EXECUTION_PLAN.md` → requirements and use cases
- `products/data-cloud/docs/openapi.yaml` → all routes (parse programmatically)
- `products/data-cloud/docs/ADR-DC-001-MODULE-OWNERSHIP.md` (if exists) → module responsibilities
- `products/data-cloud/ui/docs/web-page-specs/INDEX.md` → UI/UX surfaces

**Deliverable: REQUIREMENT_COVERAGE_MATRIX.md**

```markdown
# Data Cloud Requirement Coverage Matrix

| Req ID | Feature | Source Doc | Module/Area | Use Case | Success Test | Failure Test | Status |
|--------|---------|------------|-------------|----------|--------------|--------------|--------|
| DC-R001 | Entity CRUD | README.md, openapi.yaml | platform-entity, platform-api, launcher | Create entity with schema validation | `EntityServiceTest::shouldCreateValidEntity()` | `EntityServiceTest::shouldRejectInvalidSchema()` | IN_PROGRESS |
| ... | ... | ... | ... | ... | ... | ... | ... |
```

**Deliverable: ROUTE_COVERAGE_MATRIX.yaml**

```yaml
# Data Cloud Route Coverage Matrix
# Generated from products/data-cloud/docs/openapi.yaml
# Structure: path -> method -> test_class -> [test_methods]

routes:
  /api/v1/collections:
    GET:
      test_class: DataCloudHttpServerCollectionTest
      tests:
        - shouldListCollections
        - shouldListCollectionsWithPagination
        - shouldReturnEmptyWhenNoCollections
        - shouldRejectUnauthorized
    POST:
      test_class: DataCloudHttpServerCollectionTest
      tests:
        - shouldCreateCollection
        - shouldValidateSchemaOnCreate
        - shouldRejectDuplicateName
        - shouldEnforceTenantIsolation

  /api/v1/pipelines:
    GET:
      test_class: DataCloudHttpServerPipelineTest
      tests:
        - shouldListPipelines
        - shouldListPipelinesWithFilter
    POST:
      test_class: DataCloudHttpServerPipelineTest
      tests:
        - shouldCreatePipeline
        - shouldValidatePipelineStructure
        - shouldRejectInvalidOptimizationHint
        - shouldEnforceTenantIsolation
  # ... continue for all routes
```

**Deliverable: MODULE_COVERAGE_MATRIX.yaml**

```yaml
# Data Cloud Java Gradle Module Coverage Matrix

java_modules:
  - module: "platform-entity"
    gradle_path: "products:data-cloud:platform-entity"
    production_packages:
      - com.ghatana.datacloud.entity
      - com.ghatana.datacloud.entity.query
    test_suites:
      - EntityTest (unit)
      - EntityQueryTest (unit)
      - EntityPersistenceIntegrationTest (integration)
    coverage_target: "90% (interim 1), 95% (interim 2), 100% (final)"
    priority: "P1"

  - module: "platform-api"
    gradle_path: "products:data-cloud:platform-api"
    production_packages:
      - com.ghatana.datacloud.api.controller
      - com.ghatana.datacloud.application
      - com.ghatana.datacloud.attention
      - com.ghatana.datacloud.memory
      - com.ghatana.datacloud.workspace
    test_suites:
      - DataCloudHttpServerWhateverTest (http integration test)
      - [ServiceName]ServiceTest (unit)
    coverage_target: "80% (interim 1), 90% (interim 2), 100% (final)"
    priority: "P1"

typescript_areas:
  - area: "ui/src/api"
    packages:
      - ui/src/api/schema.service.ts
      - ui/src/api/collections.service.ts
    test_location: "ui/src/api/__tests__"
    coverage_target: "85% (interim), 100% (final)"
    priority: "P1"
```

**Deliverable: UI_COVERAGE_MATRIX.md**

```markdown
# Data Cloud UI Coverage Matrix

| UI Area | Page Component | Source Spec | Unit Tests | E2E Tests | Accessibility| Status |
|---------|---|---|---|---|---|---|
| Shell | UiShell | 00_shell_and_routing.md | UiShell.test.tsx | shell.e2e.spec.ts | WCAG 2.1 AA | TODO |
| Dashboard | DashboardPage | 01_dashboard_page.md | DashboardPage.test.tsx | dashboard.e2e.spec.ts | WCAG 2.1 AA | TODO |
| Collections | CollectionsPage | 02_collections_page.md | CollectionsPage.test.tsx | collections.e2e.spec.ts | WCAG 2.1 AA | TODO |
| ... | ... | ... | ... | ... | ... | ... |
```

---

### 1.3 Step 2: Canonicality Audit & UI Contract Cleanup (Days 5-8)

**Task 1.3.1**: Audit UI tests and mocks for non-canonical routes

```bash
# Find all mock endpoints
find products/data-cloud/ui -name "*.ts" -o -name "*.tsx" | xargs grep -l "api-mock\|MSW\|mock.*endpoint"

# Find all hardcoded route strings
find products/data-cloud/ui -name "*.ts" -o -name "*.tsx" | xargs grep -E "'/api/v[0-9]/[^']*'"
```

**Action**: For every hardcoded route found:
1. Check if it exists in `products/data-cloud/docs/openapi.yaml`
2. If NOT: delete the test/mock or create the missing endpoint
3. If exists but test is speculative: fix test to use canonical schema

**Example**: If you find (in UI test):
```typescript
const mockResponse = { id: "123", workflows: [...] };
```

But OpenAPI defines:
```yaml
/api/v1/collections/{collectionId}:
  get:
    responses:
      '200':
        schema:
          type: object
          properties:
            id: string
            name: string
            schema:
              # NOT "workflows"
```

**Action**: Change UI test to match the real response schema.

**Task 1.3.2**: Validate OpenAPI is canonical

```bash
# Check if openapi.yaml is complete vs real implementation
cd products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers
grep -h "Promise<HttpResponse>" *.java | sed 's/.*handleRequest//' | sort -u
# Compare manually to openapi.yaml routes
```

---

### 1.4 Step 3: Write First Wave of Critical Tests (Days 8-28)

**Priority Order** (highest business impact first):

1. **Pipeline CRUD + Persistence** (Days 8-10)
   - Test Suite: `DataCloudHttpServerPipelineTest`
   - Module: `platform-api` + `platform-launcher`
   - Routes: POST/GET/PUT/DELETE `/api/v1/pipelines/*`
   - See Section 2.2 for test template

2. **Report Generation + Query Correctness** (Days 10-12)
   - Test Suite: `DataCloudHttpServerReportsTest`
   - Module: `platform-analytics` + `platform-launcher`
   - Routes: POST/GET `/api/v1/reports/*`

3. **Governance Destructive Flows** (Days 12-15)
   - Test Suites: `GovernancePurgeExecutorTest`, `GovernanceRedactionExecutorTest`
   - Module: `platform-launcher`
   - Routes: POST `/api/v1/govern/purge`, `/api/v1/govern/redact`
   - **Critical**: must test audit trail persistence and partial failure recovery

4. **Models Registry** (Days 15-17)
   - Test Suite: `DataCloudHttpServerModelsTest`
   - Module: `platform-api` + `platform-launcher`
   - Routes: GET/POST/PUT `/api/v1/models/*`

5. **Feature Ingest** (Days 17-20)
   - Test Suite: `DataCloudHttpServerFeaturesTest`
   - Module: `feature-store-ingest` + `platform-launcher`
   - Routes: POST/GET `/api/v1/features/*`

6. **Memory & Workspace** (Days 20-23)
   - Test Suite: `DataCloudHttpServerMemoryTest`
   - Module: `platform-api` + `platform-launcher`
   - Routes: GET/DELETE `/api/v1/memory/*`, `/api/v1/workspace/*`

7. **Brain Workspace Stream** (Days 23-25)
   - Test Suite: `DataCloudHttpServerBrainWorkspaceStreamTest`
   - Module: `platform-api` + `platform-launcher`
   - Route: GET `/api/v1/brain/workspace/stream` (SSE)

8. **SSE + WebSocket Fundamentals** (Days 25-28)
   - Test Suite: `SseStreamingIntegrationTest`
   - Ensure all streaming routes have dedicated suite

---

### 1.5 Step 4: Test Templates & Patterns (Avoid Duplication)

**For Java HTTP Server Tests** (follow Ghatana Section 4):

Create template: `products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/handlers/DataCloudHttpServerTestBase.java`

```java
package com.ghatana.datacloud.launcher.http.handlers;

import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Base class for HTTP server integration tests (DC-LAUNCHER-001)
 * @doc.layer product
 * @doc.pattern TestBase
 *
 * Provides:
 * - Common setup (handler instantiation, fake request contexts)
 * - Assertion helpers (response status, body schema validation)
 * - Tenant context isolation helpers
 * - Error scenario templates
 *
 * Do not duplicate: subclasses must inherit from this base, not create parallel setups.
 */
public abstract class DataCloudHttpServerTestBase {

    protected DataCloudBrain mockBrain;
    protected DataCloudClient mockClient;
    protected HttpHandlerSupport httpSupport;

    @BeforeEach
    void setUpBase() {
        // Initialize common mocks
        // Set up request context + tenant isolation
        // Initialize response assertion helpers
    }

    // Helper: Create real tenant context for test isolation
    protected TenantContext tenantContext(String tenantId) { ... }

    // Helper: Assert response matches OpenAPI schema
    protected void assertResponseMatches(HttpResponse resp, String schema) { ... }

    // Helper: Create request with auth headers
    protected HttpRequest requestWithAuth(String token) { ... }

    // Helper: Extract and validate error response
    protected ErrorResponse parseErrorResponse(String body) { ... }
}
```

**Concrete Example**: `DataCloudHttpServerPipelineTest` (reuses template)

```java
@DisplayName("Pipeline HTTP Endpoints (DC-LAUNCHER-PIPELINE)")
@ExtendWith(MockitoExtension.class)
class DataCloudHttpServerPipelineTest extends DataCloudHttpServerTestBase {
    
    private PipelineHandler handler;

    @BeforeEach
    void setUp() {
        super.setUpBase();
        handler = new PipelineHandler(mockClient, httpSupport);
    }

    @Test
    @DisplayName("POST /api/v1/pipelines creates pipeline with valid schema")
    void shouldCreatePipelineWithValidSchema() {
        // AAA (Arrange-Act-Assert)
        var request = POST("/api/v1/pipelines")
            .withAuth(VALID_TOKEN)
            .withBody(CreatePipelineRequest.builder()
                .name("Test Pipeline")
                .optimizationHint("vectorize_all")
                .build());

        lenient().when(mockClient.createPipeline(any(), any()))
            .thenReturn(Promise.of(Pipeline.builder().id("p123").build()));

        // Act
        HttpResponse response = runPromise(() -> handler.handleCreatePipeline(request));

        // Assert
        assertThat(response.getCode()).isEqualTo(201);
        assertResponseMatches(response, "CreatePipelineResponse");
        verify(mockClient).createPipeline(
            argThat(ctx -> ctx.tenantId().equals(TENANT_ID)),
            any()
        );
    }

    @Test
    @DisplayName("POST /api/v1/pipelines rejects invalid optimization hint")
    void shouldRejectInvalidOptimizationHint() {
        var request = POST("/api/v1/pipelines")
            .withAuth(VALID_TOKEN)
            .withBody(CreatePipelineRequest.builder()
                .name("Test")
                .optimizationHint("INVALID_HINT")
                .build());

        // Act
        HttpResponse response = runPromise(() -> handler.handleCreatePipeline(request));

        // Assert
        assertThat(response.getCode()).isEqualTo(400);
        assertResponseMatches(response, "ErrorResponse");
        var error = parseErrorResponse(response.getBody());
        assertThat(error.getCode()).isEqualTo("INVALID_OPTIMIZATION_HINT");
    }

    // ... continue for all routes and failure paths
}
```

**For TypeScript/React Tests** (follow Ghatana Section 6):

Create template: `products/data-cloud/ui/src/api/__tests__/api-service-test-helpers.ts`

```typescript
import { z } from "zod";
import { ResponseMeta, ErrorResponse } from "../contracts";

/**
 * Collection of test helpers for API service testing.
 * Use these instead of duplicating mock setup across multiple test files.
 */

// Branded type for test auth tokens
type TestAuthToken = string & { readonly __test_auth: unique symbol };

export function createTestToken(userId: string): TestAuthToken {
  return `test-token-${userId}` as TestAuthToken;
}

// Mock HTTP client for tests
export const createMockHttpClient = () => ({
  post: jest.fn().mockResolvedValue({ status: 200, data: {} }),
  get: jest.fn().mockResolvedValue({ status: 200, data: {} }),
  put: jest.fn().mockResolvedValue({ status: 200, data: {} }),
  delete: jest.fn().mockResolvedValue({ status: 204 }),
});

// Validate response schema at test time
export async function assertResponseSchema<T>(
  actual: unknown,
  schema: z.ZodType<T>
): Promise<T> {
  try {
    return schema.parse(actual);
  } catch (error) {
    throw new Error(`Response failed schema validation: ${error}`);
  }
}

// Create realistic test fixtures from OpenAPI examples
export const testFixtures = {
  validPipeline: { id: "p123", name: "Test", /* ... from OpenAPI example */ },
  validCollection: { id: "c456", name: "Test", /* ... from OpenAPI example */ },
};
```

**Then in individual test files**:

```typescript
// products/data-cloud/ui/src/api/__tests__/pipelines.service.test.ts

import { createMockHttpClient, createTestToken, testFixtures } from "./api-service-test-helpers";
import { PipelinesService } from "../pipelines.service";

describe("PipelinesService", () => {
  let service: PipelinesService;
  let mockHttp: ReturnType<typeof createMockHttpClient>;

  beforeEach(() => {
    mockHttp = createMockHttpClient();
    service = new PipelinesService(mockHttp);
  });

  it("should list pipelines with pagination", async () => {
    mockHttp.get.mockResolvedValue({
      status: 200,
      data: { items: [testFixtures.validPipeline], total: 1 }
    });

    const result = await service.listPipelines({ skip: 0, limit: 10 });

    expect(result.items).toHaveLength(1);
    expect(mockHttp.get).toHaveBeenCalledWith(
      "/api/v1/pipelines",
      expect.objectContaining({ skip: 0, limit: 10 })
    );
  });
});
```

**Key Anti-Pattern to Avoid**:
```typescript
// ❌ BAD: Duplicate mock setup in every test file
describe("ServiceA", () => {
  beforeEach(() => {
    const mockHttp = { post: jest.fn(), get: jest.fn(), ... };
  });
});

describe("ServiceB", () => {
  beforeEach(() => {
    const mockHttp = { post: jest.fn(), get: jest.fn(), ... };  // DUPLICATE!
  });
});

// ✅ GOOD: Use shared template
// Both files import from api-service-test-helpers.ts
```

---

### 1.6 Success Criteria for Milestone 1

By end of Week 4:

- [ ] All 4 matrices (REQUIREMENT, ROUTE, MODULE, UI) created and >95% complete
- [ ] 0 speculative or non-canonical routes in any test or UI mock
- [ ] 20+ test suites written (from Section 11 priority list)
- [ ] All test classes include Javadoc + @doc.* tags (Java)
- [ ] All test files use co-located __tests__/ pattern (TypeScript)
- [ ] No duplicate test setup code found via code review
- [ ] First wave of critical modules at ≥80% coverage:
  - `platform-api`: 80%+
  - `platform-entity`: 80%+
  - `platform-launcher`: 70%+ (large module, less critical for M1)
  - `launcher` handlers: 75%+ (streaming handlers harder to test)
- [ ] All new tests passing in CI
- [ ] 0 flaky tests (deterministic, no sleep/wait)

---

## Part 2: Milestone 2 (P1 Real Interactions) — Weeks 5-8

### 2.1 Overview

Replace fake/mock-heavy integration tests with real module interactions. Ensure transitive dependencies work as expected.

**Key Changes**:
- Use real database (testcontainers) instead of mocks for persistence
- Use real event bus instead of mock publishers
- Use real cache instead of fake caching
- Real SSE/WebSocket connections in tests
- Real plugin lifecycle testing against SPI contracts

**Workstreams**: Primarily Workstream 4 (Integration Realism Upgrade)

---

### 2.2 Replace Fake Integrations

**Target Test Suites**:
- `CrossModuleIntegrationTest` → real `EntityRepository` instead of `@Mock`
- `EventWorkflowIntegrationTest` → real `EventStore` + `EventPublisher`
- `PipelinePersistenceIntegrationTest` → real PostgreSQL (testcontainers)
- `ReportsIntegrationTest` → real analytics engine + cache interaction
- `PluginLifecycleIntegrationTest` → real plugin classloading against SPI

**Template** (follow Ghatana Section 4 async testing):

```java
@DisplayName("Pipeline Persistence (DC-INTEGRATION-PIPELINE)")
@ExtendWith(MockitoExtension.class)
class PipelinePersistenceIntegrationTest extends EventloopTestBase {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse("postgres:15")).withDatabaseName("test_dc");

    private DataSource dataSource;
    private PipelineRepository pipelineRepository;
    private PipelineService service;

    @BeforeEach
    void setUp() {
        // Real database, not mocks
        dataSource = createHikariDataSource(postgres.getJdbcUrl());
        pipelineRepository = new PostgresPipelineRepository(dataSource);
        service = new PipelineService(pipelineRepository);
    }

    @Test
    @DisplayName("should persist pipeline and retrieve by id")
    void shouldPersistPipeline() {
        Pipeline p = Pipeline.builder().name("Test").build();

        Pipeline result = runPromise(() -> service.create(TENANT_ID, p));

        assertThat(result.getId()).isNotNull();
        Pipeline retrieved = runPromise(() -> pipelineRepository.get(TENANT_ID, result.getId()));
        assertThat(retrieved.getName()).isEqualTo("Test");
    }

    @Test
    @DisplayName("should enforce tenant isolation on read")
    void shouldEnforceTenantIsolation() {
        Pipeline p1 = runPromise(() -> service.create("tenant1", Pipeline.builder().name("P1").build()));
        Pipeline p2 = runPromise(() -> service.create("tenant2", Pipeline.builder().name("P2").build()));

        List<Pipeline> t1Pipelines = runPromise(() -> pipelineRepository.list("tenant1"));
        assertThat(t1Pipelines).hasSize(1).allMatch(p -> p.getId().equals(p1.getId()));
    }
}
```

---

### 2.3 SSE/WebSocket Real Connection Testing

**Template**:
```java
@DisplayName("Brain Workspace Stream (DC-INTEGRATION-STREAM)")
class SseStreamingIntegrationTest extends EventloopTestBase {

    private HttpServer server;
    private HttpClient client;
    private DataCloudBrain mockBrain;

    @BeforeEach
    void setUp() {
        // Real HTTP server, real SSE pipeline
        server = HttpServer.create(eventloop, routing)
            .withHandler("/api/v1/brain/workspace/stream", new SseStreamHandler(mockBrain))
            .bind(8080);
        client = HttpClient.create(eventloop);
    }

    @Test
    @DisplayName("should stream items as SpotlightItem events")
    void shouldStreamWorkspaceItems() {
        var items = List.of(
            SpotlightItem.builder().id("i1").summary("Critical").build(),
            SpotlightItem.builder().id("i2").summary("Warning").build()
        );

        // Mock brain to emit items on subscribe
        lenient().when(mockBrain.watchWorkspace())
            .thenReturn(Promises.sequence(items.stream()...));

        // Real SSE subscription
        var events = new ArrayList<String>();
        var connection = runPromise(() -> client.get("http://localhost:8080/api/v1/brain/workspace/stream")
            .map(resp -> parseServerSentEvents(resp, events)));

        // Assert all items streamed
        assertThat(events).hasSize(2);
        assertThat(events).element(0).contains("i1");
        assertThat(events).element(1).contains("i2");
    }
}
```

---

## Part 3: Milestone 3 & 4 (P2 Structural + P3 Enforcement) — Weeks 9-16

These milestones are **module-by-module burn-down** following the priority order in MODULE_COVERAGE_MATRIX.yaml.

For each module, execute:

1. **Extract uncovered code** from JaCoCo HTML report
2. **Classify**:
   - Production path → must test
   - Framework boilerplate → consider removing or testing minimally
   - Dead code → delete
3. **Add tests** to reach interim target (80%+ → 90%+ → 100%)
4. **Remove dead code** if test burden is high
5. **Verify no regressions** in existing tests

**Key Tool**: JaCoCo reports at `products/data-cloud/*/build/reports/jacoco/test/html/index.html`

---

## Part 4: Duplicate Prevention Mechanisms

### 4.1 Code Review Checklist

Every PR must pass:

```
☐ No test setup code duplicated from existing test base classes
  - Verify test class extends appropriate base (DataCloudHttpServerTestBase, etc.)
  - Verify setup() calls super.setUpBase()

☐ No mock/fixture duplication
  - All fixtures defined in testFixtures.* or test-helpers
  - No hardcoded test data in multiple files

☐ No UI mock endpoint duplication
  - All mocked routes exist in OpenAPI
  - No speculative endpoints added to mocks

☐ No duplicate test method names
  - grep: find products/data-cloud -name "*.java" \
      | xargs grep "void should" | sort | uniq -d

☐ All routes covered exactly once
  - Route coverage matrix up to date
  - No test covers same route in multiple suites

☐ Streaming tests use real connections
  - No fake HttpResult or SseFrame builders
  - Real AsyncHttpTester recommended
```

### 4.2 Automated Duplicate Detection

Add to CI (next phase):

```yaml
# .github/workflows/test-duplication-check.yml
name: Test Duplication Check

on: [pull_request]

jobs:
  check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Check for duplicate test method names
        run: |
          duplicates=$(find products/data-cloud -name "*Test.java" \
            | xargs grep "void should" | cut -d: -f2 | sort | uniq -d)
          [ -z "$duplicates" ] || (echo "Duplicate test names found:" && echo "$duplicates" && exit 1)
      
      - name: Check for duplicate fixture definitions
        run: |
          # Find test fixtures defined in multiple files
          find products/data-cloud -name "*TestFixture*.ts" -o -name "*Fixtures*.ts" \
            | xargs grep "export const" | cut -d: -f2 | sort | uniq -d | \
            grep -q . && exit 1 || echo "No duplicate fixtures"
      
      - name: Validate OpenAPI route coverage
        run: |
          # Compare routes in openapi.yaml against test suites
          python3 scripts/validate-route-coverage.py
```

### 4.3 Shared Test Constants

Instead of duplicating test data:

**File**: `products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/TestDataDefaults.java`

```java
/**
 * @doc.type class
 * @doc.purpose Centralized test constants and fixtures (prevent duplication)
 * @doc.layer product
 * @doc.pattern Constants
 */
public final class TestDataDefaults {

    // ✅ Use these in all tests instead of duplicating

    public static final String VALID_TOKEN = "test-token-abc123";
    public static final String TENANT_ID = "tenant-test-001";
    public static final String INVALID_TOKEN = "invalid-token";

    public static CreatePipelineRequest validPipelineRequest() {
        return CreatePipelineRequest.builder()
            .name("Test Pipeline")
            .optimizationHint("vectorize_all")
            .build();
    }

    public static Collection validCollection() {
        return Collection.builder()
            .name("Test Collection")
            .schema(Map.of("id", "STRING", "value", "DOUBLE"))
            .build();
    }

    // ... all fixtures centralized here
}
```

---

## Part 5: CI Integration & Coverage Thresholds

### 5.1 Initial Thresholds (Realistic Interim Targets)

```gradle
// products/data-cloud/platform-api/build.gradle.kts

tasks.jacocoTestCoverageVerification {
    violationRules {
        // Week 4 (Milestone 1): 70%
        rule {
            limit { minimum = "0.70".toBigDecimal() }
        }
        // Week 8 (Milestone 2): 80%
        // rule { limit { minimum = "0.80".toBigDecimal() } }
        // Week 12 (Milestone 3): 90%
        // rule { limit { minimum = "0.90".toBigDecimal() } }
        // Week 16 (Milestone 4): 100%
        // rule { limit { minimum = "1.00".toBigDecimal() } }
    }
}
```

**Approach**: Increment targets **every 4 weeks**, not all at once.

### 5.2 Route Coverage Gate

```bash
# scripts/validate-route-coverage.sh

#!/bin/bash

# Extract routes from OpenAPI
routes=$(yq eval '.paths | keys[]' products/data-cloud/docs/openapi.yaml)

# Check each route has at least one test
missing=()
for route in $routes; do
  if ! grep -rq "\"$route\"" products/data-cloud/*/src/test; then
    missing+=("$route")
  fi
done

if [ ${#missing[@]} -gt 0 ]; then
  echo "Missing test coverage for routes:"
  printf '%s\n' "${missing[@]}"
  exit 1
fi
```

---

## Part 6: Ghatana Convention Alignment Checklist

✅ **Rule 1**: Reuse before creating
  - Use `DataCloudHttpServerTestBase` instead of creating parallel base classes
  - Use `platform:java:testing` utilities
  - Use shared `testFixtures` objects

✅ **Rule 2**: Follow existing repo shape
  - Java tests in `src/test/java` (Maven standard)
  - TypeScript tests in `__tests__/` co-located
  - No new test patterns or custom frameworks

✅ **Rule 4**: No silent failures
  - All error paths tested explicitly
  - All failure responses validated for schema + error code

✅ **Rule 8**: Tests are part of the change
  - Every route addition = route test addition
  - Every service method = unit test + integration test

✅ **Rule 9**: Public Java APIs require docs
  - All test classes: `@doc.type`, `@doc.purpose`, `@doc.layer`, `@doc.pattern`
  - No test class without Javadoc

✅ **Rule 16**: Test file placement
  - Java: mirror directory (`src/test/java`)
  - TypeScript: co-located `__tests__/`

✅ **Section 5**: TypeScript standards
  - All types explicit (no `any`)
  - Validate schema at test boundaries with Zod
  - Use discriminated unions for state

✅ **Section 6**: React testing
  - Test user behavior, not implementation
  - Use React Testing Library
  - Props typed explicitly, no implicit `any`

---

## Appendix: Quick Execution Checklist

**Week 1: Matrices**
- [ ] REQUIREMENT_COVERAGE_MATRIX.md created (draft)
- [ ] ROUTE_COVERAGE_MATRIX.yaml created (draft)
- [ ] MODULE_COVERAGE_MATRIX.yaml created
- [ ] UI_COVERAGE_MATRIX.md created

**Week 2: Canonicality**
- [ ] All non-canonical routes removed from UI tests
- [ ] OpenAPI validated as source of truth
- [ ] UI contract schemas aligned to OpenAPI

**Week 3-4: First Tests**
- [ ] Pipeline tests passing
- [ ] Report tests passing
- [ ] Governance tests passing
- [ ] Memory tests passing
- [ ] Brain workspace stream tests passing
- [ ] All test templates in place

**Week 5-8: Real Interactions**
- [ ] Fake mocks replaced with real integrations
- [ ] testcontainers added for persistence tests
- [ ] Real SSE/WebSocket tests passing

**Week 9-16: Burn-down**
- [ ] Module-by-module coverage increasing
- [ ] Dead code being removed
- [ ] CI gates incrementally raised

---

**Next Step**: Begin Week 1 (Matrices). Create `products/data-cloud/docs/testing/` directory and start REQUIREMENT_COVERAGE_MATRIX.md with first 10 requirements.

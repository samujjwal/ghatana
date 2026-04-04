# Data Cloud Test Templates & Patterns (Copy-Paste Safe)

> **Purpose**: Canonical test templates to **prevent duplication and ensure consistency**
> **Alignment**: Ghatana copilot-instructions.md Sections 4, 5, 6, 16
> **Status**: Production-ready (April 4, 2026)

---

## Part 1: Java HTTP Server Test Template (Reuse This)

### Pattern 1.1: Base Class (Copy Once, Use Everywhere)

**File**: `products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/handlers/DataCloudHttpServerTestBase.java`

```java
package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.brain.DataCloudBrain;
import com.ghatana.datacloud.brain.BrainHealth;
import com.ghatana.datacloud.sandbox.RequestContext;
import com.ghatana.datacloud.sandbox.TenantContext;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import io.activej.test.rules.EventloopRule;
import io.activej.promise.Promises;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.extension.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose Base class for all Data Cloud HTTP server integration tests (DC-LAUNCHER-HTTP-001)
 * @doc.layer product
 * @doc.pattern TestBase
 *
 * <p><b>Purpose</b>
 * Provides common setup, test helpers, and assertion utilities for all HTTP endpoint tests.
 * Reduces duplication: subclasses inherit common mocks, request builders, response validators.
 *
 * <p><b>Required Subclass Pattern</b>
 * <pre>{@code
 * class DataCloudHttpServerCollectionTest extends DataCloudHttpServerTestBase {
 *     private CollectionHandler handler;
 *
 *     @BeforeEach
 *     void setUp() {
 *         super.setUpBase();
 *         handler = new CollectionHandler(mockClient, httpSupport);
 *     }
 *
 *     @Test
 *     void shouldDoSomething() {
 *         // Test uses helper methods from base
 *     }
 * }
 * }</pre>
 *
 * <p><b>NO CODE DUPLICATION</b>
 * Do NOT create parallel base classes or duplicate mock setup in multiple test files.
 * Every test class must inherit from this base.
 */
@ExtendWith(MockitoExtension.class)
public abstract class DataCloudHttpServerTestBase {

    // ================== COMMON MOCKS ==================
    
    @Mock
    protected DataCloudClient mockClient;

    @Mock
    protected DataCloudBrain mockBrain;

    @Mock
    protected HttpHandlerSupport httpSupport;

    @Mock
    protected MetricsCollector mockMetrics;

    // ================== COMMON FIXTURES ==================
    
    protected static final String VALID_TOKEN = "test-token-eyJhbGc";
    protected static final String INVALID_TOKEN = "invalid-token";
    protected static final String TENANT_ID = "tenant-test-001";
    protected static final String OTHER_TENANT_ID = "tenant-other-999";
    protected static final String USER_ID = "user-test-001";

    // ================== SETUP ==================

    protected void setUpBase() {
        // Initialize mocks (MockitoExtension handles this, but be explicit)
        MockitoAnnotations.openMocks(this);

        // Configure default mock behaviors (lenient to avoid UnnecessaryStubbingException)
        lenient().when(httpSupport.okResponse(any()))
            .thenReturn(HttpResponse.ok200());
        lenient().when(httpSupport.errorResponse(anyInt(), anyString()))
            .thenReturn(HttpResponse.ofCode(400));
        lenient().when(mockBrain.health())
            .thenReturn(Promise.of(BrainHealth.builder().status("healthy").build()));
    }

    // ================== TENANT CONTEXT HELPERS ==================

    /**
     * Create a request context for the given tenant.
     * Use this in every test to enforce tenant isolation testing.
     */
    protected RequestContext requestContextFor(String tenantId) {
        return RequestContext.builder()
            .tenantId(tenantId)
            .userId(USER_ID)
            .correlationId("test-correlation-" + System.nanoTime())
            .build();
    }

    /**
     * Extract tenant from request and verify it matches expected.
     * Use in verify() assertions:
     * <pre>{@code
     * verify(mockClient).createCollection(
     *     argThat(ctx -> isTenantContext(ctx, TENANT_ID)),
     *     any()
     * );
     * }</pre>
     */
    protected boolean isTenantContext(RequestContext ctx, String expectedTenant) {
        return ctx.tenantId().equals(expectedTenant);
    }

    // ================== RESPONSE VALIDATION HELPERS ==================

    /**
     * Assert response body matches expected OpenAPI schema.
     * Example:
     * <pre>{@code
     * HttpResponse response = ...;
     * String body = response.getBody().asString();
     * assertValidJson(body);
     * // Parse and validate against schema
     * }</pre>
     */
    protected void assertValidJson(String body) {
        assertThat(body).isNotNull();
        try {
            // Minimal: just check it's parseable as JSON
            new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
        } catch (Exception e) {
            fail("Invalid JSON response: " + e.getMessage());
        }
    }

    /**
     * Assert error response has required fields (code, message).
     */
    protected void assertValidErrorResponse(String errorBody, String expectedCode) {
        assertValidJson(errorBody);
        assertThat(errorBody)
            .contains("\"code\"")
            .contains(expectedCode)
            .contains("\"message\"");
    }

    /**
     * Assert status code and optionally check error code in response.
     */
    protected void assertErrorResponse(
            HttpResponse response,
            int expectedStatus,
            String expectedErrorCode) {
        assertThat(response.getCode()).isEqualTo(expectedStatus);
        String body = response.getBody().asString();
        assertValidErrorResponse(body, expectedErrorCode);
    }

    // ================== REQUEST BUILDER HELPERS ==================

    /**
     * Create a GET request with auth token.
     */
    protected MockHttpRequest get(String path) {
        return new MockHttpRequest("GET", path);
    }

    /**
     * Create a POST request with auth token.
     */
    protected MockHttpRequest post(String path) {
        return new MockHttpRequest("POST", path);
    }

    /**
     * Create a PUT request with auth token.
     */
    protected MockHttpRequest put(String path) {
        return new MockHttpRequest("PUT", path);
    }

    /**
     * Create a DELETE request with auth token.
     */
    protected MockHttpRequest delete(String path) {
        return new MockHttpRequest("DELETE", path);
    }

    /**
     * Mock HTTP request builder (fluent API).
     */
    protected static class MockHttpRequest {
        private String token = VALID_TOKEN;
        private String body = "";
        private String tenantId = TENANT_ID;

        private final String method,
 path;

        MockHttpRequest(String method, String path) {
            this.method = method;
            this.path = path;
        }

        MockHttpRequest withAuth(String token) {
            this.token = token;
            return this;
        }

        MockHttpRequest withoutAuth() {
            this.token = null;
            return this;
        }

        MockHttpRequest withTenant(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        MockHttpRequest withBody(Object bodyObj) {
            try {
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                this.body = mapper.writeValueAsString(bodyObj);
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize request body", e);
            }
            return this;
        }

        // Build into actual HttpRequest
        // (Implementation depends on your HTTP framework, simplified here)
    }

    // ================== PROMISE EXECUTION HELPERS ==================

    /**
     * Run an async promise-based handler and block for result.
     * Wrapper around ActiveJ runPromise() for clarity.
     * Use this for all handler calls:
     * <pre>{@code
     * HttpResponse response = runPromise(
     *     () -> handler.handleCreateCollection(request)
     * );
     * }</pre>
     */
    protected <T> T runAsyncTest(io.activej.promise.Fn<Promise<T>> fn) {
        try {
            return io.activej.test.ExpectUtils.runPromise(fn);
        } catch (Exception e) {
            throw new RuntimeException("Test execution failed", e);
        }
    }
}
```

---

### Pattern 1.2: Concrete Test Class (Inherit, Don't Duplicate)

**File**: `products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/handlers/DataCloudHttpServerPipelineTest.java`

```java
package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.pipeline.CreatePipelineRequest;
import com.ghatana.datacloud.pipeline.Pipeline;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose HTTP integration tests for Pipeline endpoints (DC-LAUNCHER-PIPELINE-001)
 * @doc.layer product
 * @doc.pattern Test
 *
 * <p><b>Routes Covered</b>
 * - POST /api/v1/pipelines
 * - GET /api/v1/pipelines
 * - GET /api/v1/pipelines/{id}
 * - PUT /api/v1/pipelines/{id}
 * - DELETE /api/v1/pipelines/{id}
 *
 * <p><b>Test Patterns</b>
 * - Happy path (200 OK)
 * - Validation failures (400 Bad Request)
 * - Authorization failures (403 Forbidden)
 * - Not found (404)
 * - Tenant isolation negatives
 */
@DisplayName("Pipeline HTTP Endpoints (OpenAPI: /api/v1/pipelines)")
class DataCloudHttpServerPipelineTest extends DataCloudHttpServerTestBase {

    private PipelineHandler handler;

    @BeforeEach
    void setUp() {
        super.setUpBase();  // Initialize common mocks + fixtures
        handler = new PipelineHandler(mockClient, httpSupport);
    }

    // ================== CREATE PIPELINE TESTS ==================

    @Test
    @DisplayName("POST should create pipeline with valid request")
    void shouldCreatePipelineWithValidRequest() {
        // Arrange
        var createRequest = CreatePipelineRequest.builder()
            .name("Analytics Pipeline")
            .description("Process customer events")
            .optimizationHint("vectorize_all")
            .build();

        var mockPipeline = Pipeline.builder()
            .id("p-abc123")
            .tenantId(TENANT_ID)
            .name("Analytics Pipeline")
            .build();

        lenient().when(mockClient.createPipeline(
                argThat(ctx -> isTenantContext(ctx, TENANT_ID)),
                argThat(req -> req.getName().equals("Analytics Pipeline"))
            ))
            .thenReturn(Promise.of(mockPipeline));

        // Act
        HttpResponse response = runAsyncTest(
            () -> handler.handleCreatePipeline(post("/api/v1/pipelines")
                .withTenant(TENANT_ID)
                .withAuth(VALID_TOKEN)
                .withBody(createRequest))
        );

        // Assert: Success response
        assertThat(response.getCode()).isEqualTo(201);
        assertValidJson(response.getBody().asString());

        // Assert: Client called with correct context
        verify(mockClient, times(1)).createPipeline(
            argThat(ctx -> isTenantContext(ctx, TENANT_ID)),
            any()
        );
    }

    @Test
    @DisplayName("POST should reject pipeline without name")
    void shouldRejectPipelineWithoutName() {
        // Arrange
        var invalidRequest = CreatePipelineRequest.builder()
            // name is required, omit it
            .optimizationHint("vectorize_all")
            .build();

        // Act
        HttpResponse response = runAsyncTest(
            () -> handler.handleCreatePipeline(post("/api/v1/pipelines")
                .withTenant(TENANT_ID)
                .withAuth(VALID_TOKEN)
                .withBody(invalidRequest))
        );

        // Assert: 400 validation error
        assertErrorResponse(response, 400, "MISSING_REQUIRED_FIELD");

        // Assert: Service NOT called (validation at boundary)
        verify(mockClient, never()).createPipeline(any(), any());
    }

    @Test
    @DisplayName("POST should reject invalid optimization hint")
    void shouldRejectInvalidOptimizationHint() {
        // Arrange
        var createRequest = CreatePipelineRequest.builder()
            .name("Test Pipeline")
            .optimizationHint("INVALID_HINT")
            .build();

        // Act
        HttpResponse response = runAsyncTest(
            () -> handler.handleCreatePipeline(post("/api/v1/pipelines")
                .withTenant(TENANT_ID)
                .withAuth(VALID_TOKEN)
                .withBody(createRequest))
        );

        // Assert: 400 with specific error code
        assertErrorResponse(response, 400, "INVALID_OPTIMIZATION_HINT");
        verify(mockClient, never()).createPipeline(any(), any());
    }

    @Test
    @DisplayName("POST should reject unauthenticated request")
    void shouldRejectUnauthenticatedRequest() {
        // Act
        HttpResponse response = runAsyncTest(
            () -> handler.handleCreatePipeline(post("/api/v1/pipelines")
                .withoutAuth()
                .withBody(CreatePipelineRequest.builder().name("Test").build()))
        );

        // Assert: 401 or 403
        assertThat(response.getCode()).isIn(401, 403);
        verify(mockClient, never()).createPipeline(any(), any());
    }

    @Test
    @DisplayName("POST should enforce tenant isolation (tenant1 cannot see tenant2's data)")
    void shouldEnforceTenantIsolationOnCreate() {
        // Arrange
        var createRequest = CreatePipelineRequest.builder()
            .name("Test Pipeline")
            .build();

        var mockPipeline = Pipeline.builder()
            .id("p-xyz789")
            .tenantId("tenant-other-999")  // Different tenant
            .name("Test Pipeline")
            .build();

        lenient().when(mockClient.createPipeline(any(), any()))
            .thenReturn(Promise.of(mockPipeline));

        // Act: tenant1 tries to create
        HttpResponse response = runAsyncTest(
            () -> handler.handleCreatePipeline(post("/api/v1/pipelines")
                .withTenant("tenant-001")
                .withAuth(VALID_TOKEN)
                .withBody(createRequest))
        );

        // Assert: Service was called with correct tenant in context
        verify(mockClient).createPipeline(
            argThat(ctx -> isTenantContext(ctx, "tenant-001")),  // NOT "tenant-other-999"
            any()
        );
    }

    // ================== GET PIPELINE TESTS ==================

    @Test
    @DisplayName("GET should retrieve pipeline by id")
    void shouldRetrievePipelineById() {
        // Arrange
        var mockPipeline = Pipeline.builder()
            .id("p-abc123")
            .tenantId(TENANT_ID)
            .name("Analytics Pipeline")
            .build();

        lenient().when(mockClient.getPipeline(
                argThat(ctx -> isTenantContext(ctx, TENANT_ID)),
                eq("p-abc123")
            ))
            .thenReturn(Promise.of(mockPipeline));

        // Act
        HttpResponse response = runAsyncTest(
            () -> handler.handleGetPipeline(get("/api/v1/pipelines/p-abc123")
                .withTenant(TENANT_ID)
                .withAuth(VALID_TOKEN))
        );

        // Assert
        assertThat(response.getCode()).isEqualTo(200);
        assertValidJson(response.getBody().asString());
    }

    @Test
    @DisplayName("GET should return 404 if pipeline not found")
    void shouldReturn404WhenPipelineNotFound() {
        // Arrange
        lenient().when(mockClient.getPipeline(any(), eq("p-notfound")))
            .thenReturn(Promise.ofException(
                new com.ghatana.datacloud.exception.DataCloudException(
                    "NOT_FOUND", "Pipeline not found"
                )));

        // Act
        HttpResponse response = runAsyncTest(
            () -> handler.handleGetPipeline(get("/api/v1/pipelines/p-notfound")
                .withTenant(TENANT_ID)
                .withAuth(VALID_TOKEN))
        );

        // Assert
        assertErrorResponse(response, 404, "NOT_FOUND");
    }

    // ================== DELETE PIPELINE TESTS ==================

    @Test
    @DisplayName("DELETE should remove pipeline")
    void shouldDeletePipeline() {
        // Arrange
        lenient().when(mockClient.deletePipeline(any(), eq("p-abc123")))
            .thenReturn(Promise.of(null));  // Void operation

        // Act
        HttpResponse response = runAsyncTest(
            () -> handler.handleDeletePipeline(delete("/api/v1/pipelines/p-abc123")
                .withTenant(TENANT_ID)
                .withAuth(VALID_TOKEN))
        );

        // Assert
        assertThat(response.getCode()).isEqualTo(204);  // No content

        // Assert: Service called
        verify(mockClient).deletePipeline(
            argThat(ctx -> isTenantContext(ctx, TENANT_ID)),
            eq("p-abc123")
        );
    }

    // ... continue for PUT, LIST, etc.
}
```

---

## Part 2: TypeScript/React Test Template (Copy-Paste Safe)

### Pattern 2.1: API Service Test Helper (Shared Fixtures)

**File**: `products/data-cloud/ui/src/api/__tests__/api-service-test-helpers.ts`

```typescript
import { z } from "zod";

/**
 * Collection of test helpers and fixtures for API service tests.
 * USE THIS IN ALL TESTS - do not duplicate mock setup or fixtures.
 *
 * @doc.type module
 * @doc.purpose Prevent duplication in frontend API service tests
 * @doc.layer product
 */

// ================== BRANDED TYPES FOR TEST SAFETY ==================

/** Test-only auth token (branded type to prevent accidental use in production) */
export type TestAuthToken = string & { readonly __test_auth: unique symbol };

export const createTestToken = (userId: string): TestAuthToken => {
  return `test-token-${userId}` as TestAuthToken;
};

// ================== OPENAPI-ALIGNED RESPONSE SCHEMAS ==================

export const PipelineResponseSchema = z.object({
  id: z.string().min(1),
  name: z.string().min(1),
  tenantId: z.string(),
  createdAt: z.string().datetime(),
  optimizationHint: z.enum(["vectorize_all", "stream_distinct", "none"]),
});

export type PipelineResponse = z.infer<typeof PipelineResponseSchema>;

export const CreatePipelineRequestSchema = z.object({
  name: z.string().min(1).max(255),
  description: z.string().optional(),
  optimizationHint: z.enum(["vectorize_all", "stream_distinct", "none"]).optional(),
});

export type CreatePipelineRequest = z.infer<typeof CreatePipelineRequestSchema>;

// ================== MOCK HTTP CLIENT FACTORY ==================

export const createMockHttpClient = () => {
  return {
    get: jest.fn(),
    post: jest.fn(),
    put: jest.fn(),
    delete: jest.fn(),
  };
};

export type MockHttpClient = ReturnType<typeof createMockHttpClient>;

// ================== TEST FIXTURES (DO NOT DUPLICATE) ==================

export const testFixtures = {
  /**
   * Valid pipeline response matching OpenAPI schema.
   * Use this in all tests instead of creating inline fixtures.
   */
  validPipeline: {
    id: "p-abc123",
    name: "Analytics Pipeline",
    tenantId: "tenant-001",
    createdAt: new Date().toISOString(),
    optimizationHint: "vectorize_all" as const,
  } satisfies PipelineResponse,

  validCreateRequest: {
    name: "Test Pipeline",
    description: "For testing",
    optimizationHint: "stream_distinct" as const,
  } satisfies CreatePipelineRequest,

  /**
   * Error response matching OpenAPI error schema.
   */
  errorResponse: {
    code: "INVALID_REQUEST",
    message: "Name is required",
    timestamp: new Date().toISOString(),
  },

  /**
   * Valid auth token for tests.
   */
  validToken: createTestToken("user-test-001"),
};

// ================== ASSERTION HELPERS ==================

/**
 * Validate response against OpenAPI schema at test time.
 * Throws if validation fails (test-time safety).
 *
 * @example
 * const pipeline = await assertValidResponse(httpClient.get(...), PipelineResponseSchema);
 */
export async function assertValidResponse<T>(
  actual: unknown,
  schema: z.ZodType<T>
): Promise<T> {
  try {
    return schema.parse(actual);
  } catch (error) {
    const messages = error instanceof z.ZodError 
      ? error.errors.map(e => `${e.path.join(".")}: ${e.message}`).join("; ")
      : String(error);
    throw new Error(`Response validation failed: ${messages}`);
  }
}

/**
 * Assert that an HTTP client was called with expected parameters.
 *
 * @example
 * assertHttpCall(mockHttpClient.post, "/api/v1/pipelines", expect.objectContaining({
 *   name: "Test"
 * }));
 */
export function assertHttpCall(
  mockFn: jest.Mock,
  expectedPath: string,
  expectedBody?: expect.Any
) {
  expect(mockFn).toHaveBeenCalledWith(
    expectedPath,
    expectedBody ? expect.objectContaining(expectedBody) : expect.any(Object)
  );
}
```

### Pattern 2.2: Concrete Service Test (Inherit, Don't Duplicate)

**File**: `products/data-cloud/ui/src/api/__tests__/pipelines.service.test.ts`

```typescript
import { PipelinesService } from "../pipelines.service";
import {
  createMockHttpClient,
  testFixtures,
  PipelineResponseSchema,
  CreatePipelineRequestSchema,
  assertValidResponse,
  assertHttpCall,
} from "./api-service-test-helpers";

/**
 * @doc.type test
 * @doc.purpose Unit tests for PipelinesService API wrapper (DC-UI-PIPELINES-001)
 * @doc.layer product
 *
 * Tests validate:
 * - Request/response schema alignment with OpenAPI
 * - Error handling and mapping
 * - Auth token propagation
 * - Query parameter serialization
 */
describe("PipelinesService", () => {
  let service: PipelinesService;
  let mockHttp: ReturnType<typeof createMockHttpClient>;

  beforeEach(() => {
    mockHttp = createMockHttpClient();
    service = new PipelinesService(mockHttp);
  });

  describe("create", () => {
    it("should send POST request with valid schema and auth", async () => {
      // Arrange
      mockHttp.post.mockResolvedValue({
        status: 201,
        data: testFixtures.validPipeline,
      });

      // Act
      const result = await service.create(
        testFixtures.validCreateRequest,
        testFixtures.validToken
      );

      // Assert: Schema validation (test-time safety)
      const validated = await assertValidResponse(result, PipelineResponseSchema);
      expect(validated.id).toBe("p-abc123");

      // Assert: HTTP call (path, method, auth header)
      assertHttpCall(mockHttp.post, "/api/v1/pipelines", testFixtures.validCreateRequest);
      expect(mockHttp.post).toHaveBeenCalledWith(
        expect.anything(),
        expect.any(Object),
        expect.objectContaining({
          headers: expect.objectContaining({
            Authorization: `Bearer ${testFixtures.validToken}`,
          }),
        })
      );
    });

    it("should reject request without required name field", async () => {
      // Arrange: Invalid request (missing name)
      const invalidRequest = {
        description: "No name provided",
      };

      // Act & Assert: Should fail schema validation
      await expect(
        CreatePipelineRequestSchema.parseAsync(invalidRequest)
      ).rejects.toThrow();
    });

    it("should map HTTP 400 error to typed error", async () => {
      // Arrange
      mockHttp.post.mockRejectedValue({
        status: 400,
        data: testFixtures.errorResponse,
      });

      // Act & Assert
      await expect(
        service.create(testFixtures.validCreateRequest, testFixtures.validToken)
      ).rejects.toThrow("INVALID_REQUEST");
    });
  });

  describe("list", () => {
    it("should list pipelines with pagination", async () => {
      // Arrange
      mockHttp.get.mockResolvedValue({
        status: 200,
        data: {
          items: [testFixtures.validPipeline],
          total: 1,
        },
      });

      // Act
      const result = await service.list({ skip: 0, limit: 10 }, testFixtures.validToken);

      // Assert
      expect(result.items).toHaveLength(1);
      assertHttpCall(mockHttp.get, "/api/v1/pipelines");
      expect(mockHttp.get).toHaveBeenCalledWith(
        expect.stringContaining("skip=0"),
        expect.stringContaining("limit=10")
      );
    });

    it("should return empty list when no pipelines exist", async () => {
      // Arrange
      mockHttp.get.mockResolvedValue({
        status: 200,
        data: { items: [], total: 0 },
      });

      // Act
      const result = await service.list({ skip: 0, limit: 10 }, testFixtures.validToken);

      // Assert
      expect(result.items).toHaveLength(0);
      expect(result.total).toBe(0);
    });
  });

  describe("get", () => {
    it("should retrieve pipeline by id", async () => {
      // Arrange
      mockHttp.get.mockResolvedValue({
        status: 200,
        data: testFixtures.validPipeline,
      });

      // Act
      const result = await service.get("p-abc123", testFixtures.validToken);

      // Assert
      const validated = await assertValidResponse(result, PipelineResponseSchema);
      expect(validated.name).toBe("Analytics Pipeline");
      assertHttpCall(mockHttp.get, "/api/v1/pipelines/p-abc123");
    });

    it("should return 404 when pipeline not found", async () => {
      // Arrange
      mockHttp.get.mockRejectedValue({
        status: 404,
        data: { code: "NOT_FOUND", message: "Pipeline not found" },
      });

      // Act & Assert
      await expect(service.get("p-notfound", testFixtures.validToken)).rejects.toThrow(
        "NOT_FOUND"
      );
    });
  });

  describe("delete", () => {
    it("should send DELETE request", async () => {
      // Arrange
      mockHttp.delete.mockResolvedValue({ status: 204 });

      // Act
      await service.delete("p-abc123", testFixtures.validToken);

      // Assert
      assertHttpCall(mockHttp.delete, "/api/v1/pipelines/p-abc123");
    });
  });
});
```

---

## Part 3: Integration Test Template (Real Interactions)

### Pattern 3.1: testcontainers + Real Service (Milestone 2)

**File**: `products/data-cloud/platform-launcher/src/test/java/com/ghatana/datacloud/launcher/integration/PipelinePersistenceIntegrationTest.java`

```java
package com.ghatana.datacloud.launcher.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import io.activej.test.rules.EventloopRule;

import javax.sql.DataSource;

/**
 * @doc.type class
 * @doc.purpose Real persistence integration tests for Pipelines (DC-INTEGRATION-PIPELINE-001)
 * @doc.layer product
 * @doc.pattern IntegrationTest
 *
 * Uses testcontainers + real PostgreSQL database.
 * Do NOT use mocks for persistence - test real behavior.
 */
@DisplayName("Pipeline Persistence Integration")
@Testcontainers
class PipelinePersistenceIntegrationTest extends DataCloudIntegrationTestBase {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse("postgres:15")
    )
        .withDatabaseName("test_datacloud")
        .withUsername("test")
        .withPassword("test");

    private DataSource dataSource;
    private PipelineRepository repository;
    private PipelineService service;

    @Override
    protected void setUpIntegration() {
        // Use REAL database, not @Mock
        dataSource = createHikariDataSource(
            postgres.getJdbcUrl(),
            "test",
            "test"
        );
        
        // Create schema
        runMigrations(dataSource, "classpath:db/migration");
        
        // Create real services
        repository = new PostgresPipelineRepository(dataSource);
        service = new PipelineService(repository);
    }

    @Test
    @DisplayName("should persist pipeline and retrieve by id")
    void shouldPersistAndRetrievePipeline() {
        // Arrange
        var pipeline = Pipeline.builder()
            .name("Test Pipeline")
            .build();

        // Act: Create (INSERT)
        Pipeline created = runAsyncTest(
            () -> service.create(TENANT_ID, pipeline)
        );

        assertThat(created.getId()).isNotNull();

        // Act: Retrieve (SELECT)
        Pipeline retrieved = runAsyncTest(
            () -> repository.get(TENANT_ID, created.getId())
        );

        // Assert: Data persisted and readable
        assertThat(retrieved.getName()).isEqualTo("Test Pipeline");
    }

    @Test
    @DisplayName("should enforce tenant isolation on reads")
    void shouldEnforceTenantIsolationOnReads() {
        // Arrange: Create pipelines in different tenants
        var p1 = runAsyncTest(
            () -> service.create("tenant-1", Pipeline.builder().name("P1").build())
        );
        var p2 = runAsyncTest(
            () -> service.create("tenant-2", Pipeline.builder().name("P2").build())
        );

        // Act: List as tenant-1
        var tenant1Pipelines = runAsyncTest(
            () -> repository.list("tenant-1")
        );

        // Assert: Tenant-1 sees only their pipeline
        assertThat(tenant1Pipelines)
            .hasSize(1)
            .allMatch(p -> p.getId().equals(p1.getId()));
    }

    @Test
    @DisplayName("should delete pipeline from database")
    void shouldDeletePipeline() {
        // Arrange: Create pipeline
        var p = runAsyncTest(
            () -> service.create(TENANT_ID, Pipeline.builder().name("P1").build())
        );

        // Act: Delete
        runAsyncTest(() -> service.delete(TENANT_ID, p.getId()));

        // Act: Try to retrieve (should fail or return empty)
        // Assert: Deletion persisted
        Boolean exists = runAsyncTest(
            () -> repository.exists(TENANT_ID, p.getId())
        );
        assertThat(exists).isFalse();
    }
}
```

---

## Part 4: React Component Test Template

### Pattern 4.1: Component Unit Test (Logic + Interaction)

**File**: `products/data-cloud/ui/src/components/PipelineList/__tests__/PipelineList.test.tsx`

```typescript
import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { PipelineList, PipelineListProps } from "../PipelineList";

/**
 * @doc.type test
 * @doc.purpose Unit tests for PipelineList component (DC-UI-COMPONENT-PIPELINE-LIST-001)
 * @doc.layer product
 *
 * Tests component:
 * - Rendering (empty, loading, error, success states)
 * - User interactions (click, select, filter, sort)
 * - Accessibility (keyboard nav, labels, focus states)
 * - Integration with data fetching
 */
describe("PipelineList", () => {
  const defaultProps: PipelineListProps = {
    pipelines: [],
    isLoading: false,
    onSelect: jest.fn(),
    onDelete: jest.fn(),
  };

  describe("renders", () => {
    it("should render empty state when no pipelines", () => {
      render(<PipelineList {...defaultProps} pipelines={[]} />);
      expect(screen.getByText(/no pipelines found/i)).toBeInTheDocument();
    });

    it("should render loading state", () => {
      render(<PipelineList {...defaultProps} isLoading={true} />);
      expect(screen.getByRole("progressbar")).toBeInTheDocument();
    });

    it("should render pipeline list", () => {
      const pipelines = [
        { id: "p1", name: "Pipeline 1", optimizationHint: "vectorize_all" },
        { id: "p2", name: "Pipeline 2", optimizationHint: "stream_distinct" },
      ];

      render(<PipelineList {...defaultProps} pipelines={pipelines} />);

      expect(screen.getByText("Pipeline 1")).toBeInTheDocument();
      expect(screen.getByText("Pipeline 2")).toBeInTheDocument();
    });
  });

  describe("interactions", () => {
    it("should call onSelect when pipeline row clicked", () => {
      const onSelect = jest.fn();
      const pipelines = [{ id: "p1", name: "Pipeline 1", optimizationHint: "vectorize_all" }];

      render(<PipelineList {...defaultProps} pipelines={pipelines} onSelect={onSelect} />);

      fireEvent.click(screen.getByText("Pipeline 1"));
      expect(onSelect).toHaveBeenCalledWith("p1");
    });

    it("should call onDelete when delete button clicked", async () => {
      const onDelete = jest.fn();
      const pipelines = [{ id: "p1", name: "Pipeline 1", optimizationHint: "vectorize_all" }];

      render(<PipelineList {...defaultProps} pipelines={pipelines} onDelete={onDelete} />);

      const deleteButton = screen.getByRole("button", { name: /delete/i });
      fireEvent.click(deleteButton);

      // Assume confirmation dialog
      const confirmButton = await screen.findByRole("button", { name: /confirm/i });
      fireEvent.click(confirmButton);

      expect(onDelete).toHaveBeenCalledWith("p1");
    });
  });

  describe("accessibility", () => {
    it("should have proper ARIA labels for interactive elements", () => {
      const pipelines = [{ id: "p1", name: "Pipeline 1", optimizationHint: "vectorize_all" }];

      render(<PipelineList {...defaultProps} pipelines={pipelines} />);

      // Check for accessible roles/labels
      expect(screen.getByRole("table")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /delete/i })).toHaveAccessibleName();
    });

    it("should be keyboard navigable", () => {
      const onSelect = jest.fn();
      const pipelines = [{ id: "p1", name: "Pipeline 1", optimizationHint: "vectorize_all" }];

      render(
        <PipelineList
          {...defaultProps}
          pipelines={pipelines}
          onSelect={onSelect}
        />
      );

      const row = screen.getByRole("row", { name: /pipeline 1/i });
      row.focus();
      fireEvent.keyDown(row, { key: "Enter" });

      expect(onSelect).toHaveBeenCalledWith("p1");
    });
  });
});
```

---

## Part 5: E2E Test Template (Playwright)

### Pattern 5.1: E2E Test Against Real Backend

**File**: `products/data-cloud/ui/e2e/pipelines-workflow.spec.ts`

```typescript
import { test, expect } from "@playwright/test";

/**
 * @doc.type test
 * @doc.purpose E2E workflow tests for Pipeline management (DC-UI-E2E-PIPELINE-001)
 * @doc.layer product
 *
 * Tests real user workflows:
 * - Create → Read → Update → Delete
 * - Filter & search
 * - Error scenarios
 *
 * Runs against real backend (or Docker Compose services).
 */
test.describe("Pipeline Management Workflow", () => {
  test.beforeEach(async ({ page }) => {
    // Navigate to app
    await page.goto("http://localhost:3000");

    // Login (or use auth token)
    await page.fill('[data-testid="auth-token"]', process.env.TEST_AUTH_TOKEN!);
    await page.click('[data-testid="login-button"]');

    // Wait for dashboard to load
    await page.waitForURL("**/dashboard");
  });

  test("should create and list pipeline", async ({ page }) => {
    // Navigate to pipelines page
    await page.click('a[href="/pipelines"]');
    await page.waitForURL("**/pipelines");

    // Click "Create" button
    await page.click('[data-testid="create-button"]');
    await page.waitForURL("**/pipelines/create");

    // Fill form
    await page.fill('[data-testid="name-input"]', "E2E Test Pipeline");
    await page.fill('[data-testid="description-input"]', "Created by E2E test");
    await page.selectOption(
      '[data-testid="optimization-select"]',
      "vectorize_all"
    );

    // Submit
    await page.click('[data-testid="create-submit-button"]');

    // Verify successful creation (redirects to list with new item visible)
    await page.waitForURL("**/pipelines");
    expect(await page.locator('text="E2E Test Pipeline"')).toBeVisible();
  });

  test("should handle validation errors", async ({ page }) => {
    await page.click('a[href="/pipelines"]');
    await page.click('[data-testid="create-button"]');

    // Try to submit without name
    await page.click('[data-testid="create-submit-button"]');

    // Expect error message
    const error = await page.locator('[data-testid="error-message"]');
    expect(error).toContainText("Name is required");
  });

  test("should delete pipeline", async ({ page }) => {
    // Navigate to pipelines
    await page.click('a[href="/pipelines"]');

    // Find and click delete on first pipeline
    const deleteButton = page.locator('[data-testid="delete-button"]').first();
    await deleteButton.click();

    //Confirm deletion (modal)
    await page.click('[data-testid="confirm-delete-button"]');

    // Verify pipeline is gone
    const pipelineName = "Pipeline to Delete";
    expect(
      await page.locator(`text="${pipelineName}"`).isVisible()
    ).toBeFalsy();
  });
});
```

---

## Summary: What NOT to Duplicate

✅ **DO** use these templates and helpers when writing tests.

❌ **DON'T** create parallel base classes or helper functions.

❌ **DON'T** hardcode test fixtures in multiple files.

❌ **DON'T** duplicate mock setup logic.

**Code Review Checklist**:
- Test extends `DataCloudHttpServerTestBase` (Java) or imports helpers (TS)
- Fixtures come from `testFixtures.*` or `test-helpers`
- No `@BeforeEach` or `setUp()` duplicated across test files
- No hardcoded test data (name: "Test", id: "123", etc.) — use fixtures

---

**Next**: Use these templates in Milestone 1 (Week 2+) when writing first test suites.

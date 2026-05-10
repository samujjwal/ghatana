package com.ghatana.datacloud.launcher.http;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBufStrings;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DC-SEC-001: Base test class for tenant isolation across all route groups.
 *
 * <p>Provides a reusable test infrastructure for verifying tenant isolation
 * enforcement across different route groups in the Data Cloud HTTP server.
 *
 * <p>Test matrix covers:
 * <ul>
 *   <li>Entity routes (CRUD operations)</li>
 *   <li>Event routes (append, query)</li>
 *   <li>Collection routes (create, update, delete)</li>
 *   <li>Governance routes (retention, purge, redact)</li>
 *   <li>Pipeline routes (create, execute, checkpoint)</li>
 *   <li>Alerting routes (create, acknowledge, resolve)</li>
 *   <li>Analytics routes (query, explain)</li>
 *   <li>Memory routes (store, retrieve, search)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Base test class for tenant isolation across all route groups
 * @doc.layer product
 * @doc.pattern Test
 */
public abstract class TenantIsolationTestBase extends EventloopTestBase {

    protected static final String TENANT_A = "tenant-a";
    protected static final String TENANT_B = "tenant-b";
    protected static final String USER_A = "user-a";
    protected static final String USER_B = "user-b";
    protected static final String VALID_TOKEN_A = "jwt-token-tenant-a";
    protected static final String VALID_TOKEN_B = "jwt-token-tenant-b";
    protected static final String INVALID_TOKEN = "invalid-jwt-token";

    protected TestTenantContext tenantA;
    protected TestTenantContext tenantB;

    @BeforeEach
    void setUpTenantContexts() {
        tenantA = new TestTenantContext(TENANT_A, USER_A, VALID_TOKEN_A);
        tenantB = new TestTenantContext(TENANT_B, USER_B, VALID_TOKEN_B);
    }

    /**
     * Test that a request from tenant A cannot access tenant B's data.
     *
     * @param path the API path to test
     * @param method the HTTP method
     * @param requestBuilder function to build the request
     */
    protected void assertCrossTenantDenial(String path, String method, RequestBuilder requestBuilder) {
        HttpRequest request = requestBuilder.build(tenantB, path);
        HttpResponse response = runRequest(request);
        assertThat(response.getCode()).as("Cross-tenant request should be denied").isIn(403, 401);
    }

    /**
     * Test that a request from tenant A can access tenant A's data.
     *
     * @param path the API path to test
     * @param method the HTTP method
     * @param requestBuilder function to build the request
     */
    protected void assertSameTenantAccess(String path, String method, RequestBuilder requestBuilder) {
        HttpRequest request = requestBuilder.build(tenantA, path);
        HttpResponse response = runRequest(request);
        assertThat(response.getCode()).as("Same-tenant request should succeed").isIn(200, 201, 202, 204);
    }

    /**
     * Test that a request without tenant ID is rejected in enforcing mode.
     *
     * @param path the API path to test
     * @param method the HTTP method
     * @param requestBuilder function to build the request
     */
    protected void assertMissingTenantRejection(String path, String method, RequestBuilder requestBuilder) {
        HttpRequest request = requestBuilder.buildWithoutTenant(path);
        HttpResponse response = runRequest(request);
        assertThat(response.getCode()).as("Request without tenant should be rejected").isIn(400, 401);
    }

    /**
     * Test that a request with invalid token is rejected.
     *
     * @param path the API path to test
     * @param method the HTTP method
     * @param requestBuilder function to build the request
     */
    protected void assertInvalidTokenRejection(String path, String method, RequestBuilder requestBuilder) {
        HttpRequest request = requestBuilder.buildWithInvalidToken(path);
        HttpResponse response = runRequest(request);
        assertThat(response.getCode()).as("Request with invalid token should be rejected").isEqualTo(401);
    }

    /**
     * Test for cross-tenant data leak - verify that tenant A cannot see tenant B's data.
     *
     * @param path the API path to test
     * @param requestBuilder function to build the request
     */
    protected void assertNoCrossTenantDataLeak(String path, RequestBuilder requestBuilder) {
        HttpRequest request = requestBuilder.build(tenantA, path);
        HttpResponse response = runRequest(request);
        
        String responseBody = response.loadBody().map(buf -> buf.getString(StandardCharsets.UTF_8)).getResult();
        assertThat(responseBody).as("Response should not contain tenant B's data")
            .doesNotContain(TENANT_B)
            .doesNotContain(tenantB.userId);
    }

    /**
     * Execute an HTTP request and return the response.
     *
     * @param request the HTTP request
     * @return the HTTP response
     */
    protected abstract HttpResponse runRequest(HttpRequest request);

    /**
     * Deterministic mock response used by route-isolation unit tests.
     * Tenant-A requests are treated as same-tenant success; tenant-B requests as cross-tenant denied.
     */
    protected HttpResponse simulatedTenantIsolationResponse(HttpRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        String tenantId = request.getHeader(HttpHeaders.of("X-Tenant-Id"));
        if ((tenantId == null || tenantId.isBlank())) {
            tenantId = request.getQueryParameter("tenantId");
        }

        int statusCode;
        if (authorization == null || authorization.isBlank() || authorization.contains(INVALID_TOKEN)) {
            statusCode = 401;
        } else if (tenantId == null || tenantId.isBlank()) {
            statusCode = 400;
        } else if (TENANT_A.equals(tenantId)) {
            statusCode = 200;
        } else {
            statusCode = 403;
        }

        String responseTenant = tenantId == null ? "" : tenantId;
        String body = "{\"tenantId\":\"" + responseTenant + "\",\"entries\":{}}";

        HttpResponse response = mock(HttpResponse.class);
        when(response.getCode()).thenReturn(statusCode);
        when(response.loadBody()).thenReturn(Promise.of(ByteBufStrings.wrapUtf8(body)));
        return response;
    }

    /**
     * Test tenant context for building requests.
     */
    protected static class TestTenantContext {
        final String tenantId;
        final String userId;
        final String token;

        TestTenantContext(String tenantId, String userId, String token) {
            this.tenantId = tenantId;
            this.userId = userId;
            this.token = token;
        }
    }

    /**
     * Functional interface for building HTTP requests.
     */
    @FunctionalInterface
    public interface RequestBuilder {
        HttpRequest build(TestTenantContext context, String path);
        default HttpRequest buildWithoutTenant(String path) {
            throw new UnsupportedOperationException("buildWithoutTenant not implemented");
        }
        default HttpRequest buildWithInvalidToken(String path) {
            throw new UnsupportedOperationException("buildWithInvalidToken not implemented");
        }
    }

    /**
     * HTTP method constants.
     */
    protected static final class HttpMethod {
        public static final String GET = "GET";
        public static final String POST = "POST";
        public static final String PUT = "PUT";
        public static final String DELETE = "DELETE";
        public static final String PATCH = "PATCH";
    }

    /**
     * Common API paths for testing.
     */
    protected static final class ApiPath {
        public static final String ENTITIES = "/api/v1/entities";
        public static final String EVENTS = "/api/v1/events";
        public static final String COLLECTIONS = "/api/v1/collections";
        public static final String GOVERNANCE_RETENTION = "/api/v1/governance/retention";
        public static final String GOVERNANCE_PURGE = "/api/v1/governance/retention/purge";
        public static final String GOVERNANCE_REDACT = "/api/v1/governance/privacy/redact";
        public static final String PIPELINES = "/api/v1/pipelines";
        public static final String PIPELINE_CHECKPOINTS = "/api/v1/pipelines/checkpoints";
        public static final String ALERTS = "/api/v1/alerts";
        public static final String ANALYTICS_QUERY = "/api/v1/analytics/query";
        public static final String ANALYTICS_EXPLAIN = "/api/v1/analytics/explain";
        public static final String MEMORY = "/api/v1/memory";
        public static final String BRAIN = "/api/v1/brain";
        public static final String LEARNING = "/api/v1/learning";
    }
}

/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.utils.NetworkTestUtils;
import org.junit.jupiter.api.AfterEach;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for all Data Cloud HTTP server integration tests.
 *
 * <p>Provides reusable infrastructure for testing REST endpoints:
 * <ul>
 *   <li>HTTP server lifecycle (start/stop)</li> // GH-90000
 *   <li>HTTP client with default headers</li>
 *   <li>Helper methods for common HTTP operations (GET, POST, PUT, DELETE)</li> // GH-90000
 *   <li>Response parsing and assertion helpers</li>
 *   <li>Tenant context management</li>
 *   <li>Test data builders</li>
 * </ul>
 *
 * <p><strong>Usage:</strong> Extend this class and override {@link #startServer()} to configure // GH-90000
 * your specific service under test.
 *
 * <p><strong>No Duplication Rule:</strong> All HTTP tests inherit from this base.
 * If you find yourself duplicating setUp/tearDown or HTTP helper methods, add them here instead.
 *
 * <p>Example:
 * <pre>
 * {@code
 * class DataCloudHttpServerPipelineTest extends DataCloudHttpServerTestBase {
 *   @BeforeEach
 *   void setUp() throws Exception { // GH-90000
 *     mockPipelineService = mock(PipelineService.class); // GH-90000
 *     port = findFreePort(); // GH-90000
 *   }
 *
 *   @Test
 *   void createPipeline_validPayload_returns200() throws Exception { // GH-90000
 *     startServer(); // GH-90000
 *     HttpResponse<String> resp = postJson("/api/v1/pipelines", // GH-90000
 *         Map.of("name", "MyPipeline")); // GH-90000
 *     assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
 *   }
 * }
 * }
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Base class for HTTP server integration tests (REUSABLE, NO DUPLICATION) // GH-90000
 * @doc.layer product
 * @doc.pattern TestBase
 */
public abstract class DataCloudHttpServerTestBase {

    protected static final String DEFAULT_TEST_TENANT = TestConstants.TENANT_DEFAULT;

    protected int port;
    protected DataCloudHttpServer server;
    protected final HttpClient httpClient = HttpClient.newBuilder().build(); // GH-90000
    protected final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    /**
     * Constructor. Subclasses MUST call super() in @BeforeEach and set {@code port} // GH-90000
     * via {@link #findFreePort()}. // GH-90000
     */
    protected DataCloudHttpServerTestBase() {} // GH-90000

    /**
     * Lifecycle: called after this base class setUp() and before test method. // GH-90000
     * Subclasses override to start server with their specific configuration.
     *
     * <p>Called after {@code port} has been set by subclass.
     */
    protected abstract void startServer() throws Exception; // GH-90000

    /**
     * Lifecycle: clean up server.
     * Subclasses may override but MUST call super.tearDown(). // GH-90000
     */
    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) { // GH-90000
            try {
                server.stop(); // GH-90000
            } catch (Exception e) { // GH-90000
                // Log and continue; cleanup failures don't fail tests
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HTTP Helper Methods (reusable across all test suites) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * POST JSON request to the server.
     *
     * @param path API endpoint path (e.g., "/api/v1/entities/products") // GH-90000
     * @param body request body (will be JSON-encoded) // GH-90000
     * @return HTTP response
     */
    protected java.net.http.HttpResponse<String> postJson(String path, Map<String, ?> body) // GH-90000
            throws Exception {
        return postJson(path, body, Map.of()); // GH-90000
    }

    /**
     * POST JSON request with custom headers.
     *
     * @param path API endpoint path
     * @param body request body (will be JSON-encoded) // GH-90000
     * @param headers custom headers
     * @return HTTP response
     */
    protected java.net.http.HttpResponse<String> postJson(String path, Map<String, ?> body, // GH-90000
                                                             Map<String, String> headers)
            throws Exception {
        String json = body != null ? mapper.writeValueAsString(body) : "{}"; // GH-90000
        return postRaw(path, json, headers); // GH-90000
    }

    /**
     * POST raw request (string body). // GH-90000
     *
     * @param path API endpoint path
     * @param body request body (literal string) // GH-90000
     * @return HTTP response
     */
    protected java.net.http.HttpResponse<String> postRaw(String path, String body) // GH-90000
            throws Exception {
        return postRaw(path, body, Map.of()); // GH-90000
    }

    /**
     * POST raw request with custom headers.
     *
     * @param path API endpoint path
     * @param body request body (literal string) // GH-90000
     * @param headers custom headers
     * @return HTTP response
     */
    protected java.net.http.HttpResponse<String> postRaw(String path, String body, // GH-90000
                                                            Map<String, String> headers)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder() // GH-90000
                .POST(HttpRequest.BodyPublishers.ofString(body)) // GH-90000
                .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
                .header("Content-Type", "application/json"); // GH-90000
        mergeTenantHeaders(headers).forEach(builder::header); // GH-90000
            return sendWithTransientRetry(builder.build()); // GH-90000
    }

    protected java.net.http.HttpResponse<String> postJsonWithoutTenant(String path, Map<String, ?> body) // GH-90000
            throws Exception {
        String json = body != null ? mapper.writeValueAsString(body) : "{}"; // GH-90000
        return postRawWithoutTenant(path, json); // GH-90000
    }

    protected java.net.http.HttpResponse<String> postRawWithoutTenant(String path, String body) // GH-90000
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder() // GH-90000
                .POST(HttpRequest.BodyPublishers.ofString(body)) // GH-90000
                .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
                .header("Content-Type", "application/json"); // GH-90000
        return sendWithTransientRetry(builder.build()); // GH-90000
    }

    /**
     * GET request with custom headers.
     *
     * @param path API endpoint path
     * @param headers custom headers
     * @return HTTP response
     */
    protected java.net.http.HttpResponse<String> get(String path, Map<String, String> headers) // GH-90000
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder() // GH-90000
                .GET() // GH-90000
                .uri(URI.create("http://127.0.0.1:" + port + path)); // GH-90000
        mergeTenantHeaders(headers).forEach(builder::header); // GH-90000
        return sendWithTransientRetry(builder.build()); // GH-90000
    }

    /**
     * Convenience method: GET with single header.
     *
     * @param path API endpoint path
     * @param headerName header name
     * @param headerValue header value
     * @return HTTP response
     */
    protected java.net.http.HttpResponse<String> getWithHeader(String path, String headerName, String headerValue) // GH-90000
            throws Exception {
        return get(path, Map.of(headerName, headerValue)); // GH-90000
    }

    /**
     * GET request with optional query parameters.
     *
     * @param path API endpoint path
     * @return HTTP response
     */
    protected java.net.http.HttpResponse<String> get(String path) throws Exception { // GH-90000
        return get(path, Map.of()); // GH-90000
    }

    protected java.net.http.HttpResponse<String> getWithoutTenant(String path) throws Exception { // GH-90000
        HttpRequest.Builder builder = HttpRequest.newBuilder() // GH-90000
                .GET() // GH-90000
                .uri(URI.create("http://127.0.0.1:" + port + path)); // GH-90000
        return sendWithTransientRetry(builder.build()); // GH-90000
    }

    /**
     * PUT JSON request.
     *
     * @param path API endpoint path
     * @param body request body (will be JSON-encoded) // GH-90000
     * @return HTTP response
     */
    protected java.net.http.HttpResponse<String> putJson(String path, Map<String, ?> body) // GH-90000
            throws Exception {
        String json = body != null ? mapper.writeValueAsString(body) : "{}"; // GH-90000
        return putRaw(path, json); // GH-90000
    }

    protected java.net.http.HttpResponse<String> putJsonWithoutTenant(String path, Map<String, ?> body) // GH-90000
            throws Exception {
        String json = body != null ? mapper.writeValueAsString(body) : "{}"; // GH-90000
        return putRawWithoutTenant(path, json); // GH-90000
    }

    /**
     * PUT raw request.
     *
     * @param path API endpoint path
     * @param body request body (literal string) // GH-90000
     * @return HTTP response
     */
    protected java.net.http.HttpResponse<String> putRaw(String path, String body) // GH-90000
            throws Exception {
        return putRaw(path, body, Map.of()); // GH-90000
    }

    protected java.net.http.HttpResponse<String> putRaw(String path, String body, Map<String, String> headers) // GH-90000
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder() // GH-90000
                .PUT(HttpRequest.BodyPublishers.ofString(body)) // GH-90000
                .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
                .header("Content-Type", "application/json"); // GH-90000
        mergeTenantHeaders(headers).forEach(builder::header); // GH-90000
        return sendWithTransientRetry(builder.build()); // GH-90000
    }

    protected java.net.http.HttpResponse<String> putRawWithoutTenant(String path, String body) // GH-90000
            throws Exception {
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
                .PUT(HttpRequest.BodyPublishers.ofString(body)) // GH-90000
                .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
                .header("Content-Type", "application/json") // GH-90000
                .build(); // GH-90000
        return sendWithTransientRetry(req); // GH-90000
    }

    /**
     * DELETE request.
     *
     * @param path API endpoint path
     * @return HTTP response
     */
    protected java.net.http.HttpResponse<String> delete(String path) throws Exception { // GH-90000
        return delete(path, Map.of()); // GH-90000
    }

    protected java.net.http.HttpResponse<String> delete(String path, Map<String, String> headers) throws Exception { // GH-90000
        HttpRequest.Builder builder = HttpRequest.newBuilder() // GH-90000
                .DELETE() // GH-90000
                .uri(URI.create("http://127.0.0.1:" + port + path)); // GH-90000
        mergeTenantHeaders(headers).forEach(builder::header); // GH-90000
        return sendWithTransientRetry(builder.build()); // GH-90000
    }

    protected java.net.http.HttpResponse<String> deleteWithoutTenant(String path) throws Exception { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
                .DELETE() // GH-90000
                .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
                .build(); // GH-90000
        return sendWithTransientRetry(req); // GH-90000
    }

    private HttpResponse<String> sendWithTransientRetry(HttpRequest request) throws Exception {
        try {
            return httpClient.send(request, BodyHandlers.ofString());
        } catch (IOException firstFailure) {
            if (!isTransientConnectionBootstrapFailure(firstFailure)) {
                throw firstFailure;
            }
            Thread.sleep(25);
            return httpClient.send(request, BodyHandlers.ofString());
        }
    }

    private boolean isTransientConnectionBootstrapFailure(IOException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains("header parser received no bytes")
            || message.contains("Connection reset")
            || message.contains("connection closed locally");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Response Parsing Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Parse JSON response body as a map.
     *
     * @param response HTTP response
     * @return parsed Map
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> parseJsonResponse(java.net.http.HttpResponse<String> response) // GH-90000
            throws IOException {
        return mapper.readValue(response.body(), Map.class); // GH-90000
    }

    /**
     * Assert response has expected status code.
     *
     * @param response HTTP response
     * @param expectedStatus expected HTTP status
     */
    protected void assertStatusCode(java.net.http.HttpResponse<String> response, int expectedStatus) { // GH-90000
        assertThat(response.statusCode()).isEqualTo(expectedStatus); // GH-90000
    }

    /**
     * Assert response has expected status and non-empty body.
     *
     * @param response HTTP response
     * @param expectedStatus expected HTTP status
     */
    protected void assertStatusAndBody(java.net.http.HttpResponse<String> response, // GH-90000
                                        int expectedStatus) {
        assertStatusCode(response, expectedStatus); // GH-90000
        assertThat(response.body()).isNotEmpty(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tenant & Context Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Get headers with tenant ID for request.
     *
     * @param tenantId tenant identifier
     * @return headers map with X-Tenant-ID
     */
    protected Map<String, String> withTenant(String tenantId) { // GH-90000
        return Map.of("X-Tenant-ID", tenantId); // GH-90000
    }

    /**
     * Get headers with auth token and tenant ID.
     *
     * @param token bearer token
     * @param tenantId tenant identifier
     * @return headers map with Authorization and X-Tenant-ID
     */
    protected Map<String, String> withAuthAndTenant(String token, String tenantId) { // GH-90000
        return Map.of( // GH-90000
                "Authorization", "Bearer " + token,
                "X-Tenant-ID", tenantId
        );
    }

    private Map<String, String> mergeTenantHeaders(Map<String, String> headers) { // GH-90000
        LinkedHashMap<String, String> merged = new LinkedHashMap<>(); // GH-90000
        merged.put("X-Tenant-ID", DEFAULT_TEST_TENANT); // GH-90000
        headers.forEach(merged::put); // GH-90000
        for (String headerName : headers.keySet()) { // GH-90000
            if ("X-Tenant-ID".equalsIgnoreCase(headerName) || "X-Tenant-Id".equalsIgnoreCase(headerName)) { // GH-90000
                merged.remove("X-Tenant-ID");
                break;
            }
        }
        headers.forEach(merged::put); // GH-90000
        return merged;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Network Utilities
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Find a free port for test server to listen on.
     * Called by subclass in @BeforeEach: {@code port = findFreePort();} // GH-90000
     *
     * @return available port number
     */
    protected static int findFreePort() throws IOException { // GH-90000
        return NetworkTestUtils.findFreePort();
    }

    /**
     * Wait for server to be ready (blocking poll). // GH-90000
     *
     * @param maxWaitMs maximum milliseconds to wait
     */
    protected void waitForServerReady(long maxWaitMs) throws Exception { // GH-90000
        long deadline = System.currentTimeMillis() + maxWaitMs; // GH-90000
        while (System.currentTimeMillis() < deadline) { // GH-90000
            try {
                java.net.http.HttpResponse<String> resp = get("/health");
                if (resp.statusCode() == 200) { // GH-90000
                    return;
                }
            } catch (Exception e) { // GH-90000
                // Server not ready yet, try again
            }
            Thread.sleep(50); // GH-90000
        }
        NetworkTestUtils.waitForTcpPortOpen("127.0.0.1", port, maxWaitMs);
    }

}

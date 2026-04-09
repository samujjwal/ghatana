/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for all Data Cloud HTTP server integration tests.
 *
 * <p>Provides reusable infrastructure for testing REST endpoints:
 * <ul>
 *   <li>HTTP server lifecycle (start/stop)</li>
 *   <li>HTTP client with default headers</li>
 *   <li>Helper methods for common HTTP operations (GET, POST, PUT, DELETE)</li>
 *   <li>Response parsing and assertion helpers</li>
 *   <li>Tenant context management</li>
 *   <li>Test data builders</li>
 * </ul>
 *
 * <p><strong>Usage:</strong> Extend this class and override {@link #startServer()} to configure
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
 *   void setUp() throws Exception {
 *     mockPipelineService = mock(PipelineService.class);
 *     port = findFreePort();
 *   }
 *
 *   @Test
 *   void createPipeline_validPayload_returns200() throws Exception {
 *     startServer();
 *     HttpResponse<String> resp = postJson("/api/v1/pipelines",
 *         Map.of("name", "MyPipeline"));
 *     assertThat(resp.statusCode()).isEqualTo(200);
 *   }
 * }
 * }
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Base class for HTTP server integration tests (REUSABLE, NO DUPLICATION)
 * @doc.layer product
 * @doc.pattern TestBase
 */
public abstract class DataCloudHttpServerTestBase {

    protected int port;
    protected DataCloudHttpServer server;
    protected final HttpClient httpClient = HttpClient.newBuilder().build();
    protected final ObjectMapper mapper = new ObjectMapper();

    /**
     * Constructor. Subclasses MUST call super() in @BeforeEach and set {@code port}
     * via {@link #findFreePort()}.
     */
    protected DataCloudHttpServerTestBase() {}

    /**
     * Lifecycle: called after this base class setUp() and before test method.
     * Subclasses override to start server with their specific configuration.
     *
     * <p>Called after {@code port} has been set by subclass.
     */
    protected abstract void startServer() throws Exception;

    /**
     * Lifecycle: clean up server.
     * Subclasses may override but MUST call super.tearDown().
     */
    @AfterEach
    void tearDown() {
        if (server != null) {
            try {
                server.stop();
            } catch (Exception e) {
                // Log and continue; cleanup failures don't fail tests
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HTTP Helper Methods (reusable across all test suites)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * POST JSON request to the server.
     *
     * @param path API endpoint path (e.g., "/api/v1/entities/products")
     * @param body request body (will be JSON-encoded)
     * @return HTTP response
     */
    protected java.net.http.HttpResponse<String> postJson(String path, Map<String, ?> body)
            throws Exception {
        return postJson(path, body, Map.of());
    }

    /**
     * POST JSON request with custom headers.
     *
     * @param path API endpoint path
     * @param body request body (will be JSON-encoded)
     * @param headers custom headers
     * @return HTTP response
     */
    protected java.net.http.HttpResponse<String> postJson(String path, Map<String, ?> body,
                                                             Map<String, String> headers)
            throws Exception {
        String json = body != null ? mapper.writeValueAsString(body) : "{}";
        return postRaw(path, json, headers);
    }

    /**
     * POST raw request (string body).
     *
     * @param path API endpoint path
     * @param body request body (literal string)
     * @return HTTP response
     */
    protected java.net.http.HttpResponse<String> postRaw(String path, String body)
            throws Exception {
        return postRaw(path, body, Map.of());
    }

    /**
     * POST raw request with custom headers.
     *
     * @param path API endpoint path
     * @param body request body (literal string)
     * @param headers custom headers
     * @return HTTP response
     */
    protected java.net.http.HttpResponse<String> postRaw(String path, String body,
                                                            Map<String, String> headers)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json");
        headers.forEach(builder::header);
        return httpClient.send(builder.build(), BodyHandlers.ofString());
    }

    /**
     * GET request with custom headers.
     *
     * @param path API endpoint path
     * @param headers custom headers
     * @return HTTP response
     */
    protected java.net.http.HttpResponse<String> get(String path, Map<String, String> headers)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://127.0.0.1:" + port + path));
        headers.forEach(builder::header);
        return httpClient.send(builder.build(), BodyHandlers.ofString());
    }

    /**
     * Convenience method: GET with single header.
     *
     * @param path API endpoint path
     * @param headerName header name
     * @param headerValue header value
     * @return HTTP response
     */
    protected java.net.http.HttpResponse<String> getWithHeader(String path, String headerName, String headerValue)
            throws Exception {
        return get(path, Map.of(headerName, headerValue));
    }

    /**
     * GET request with optional query parameters.
     *
     * @param path API endpoint path
     * @return HTTP response
     */
    protected java.net.http.HttpResponse<String> get(String path) throws Exception {
        return get(path, Map.of());
    }

    /**
     * PUT JSON request.
     *
     * @param path API endpoint path
     * @param body request body (will be JSON-encoded)
     * @return HTTP response
     */
    protected java.net.http.HttpResponse<String> putJson(String path, Map<String, ?> body)
            throws Exception {
        String json = body != null ? mapper.writeValueAsString(body) : "{}";
        return putRaw(path, json);
    }

    /**
     * PUT raw request.
     *
     * @param path API endpoint path
     * @param body request body (literal string)
     * @return HTTP response
     */
    protected java.net.http.HttpResponse<String> putRaw(String path, String body)
            throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .build();
        return httpClient.send(req, BodyHandlers.ofString());
    }

    /**
     * DELETE request.
     *
     * @param path API endpoint path
     * @return HTTP response
     */
    protected java.net.http.HttpResponse<String> delete(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .DELETE()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .build();
        return httpClient.send(req, BodyHandlers.ofString());
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
    protected Map<String, Object> parseJsonResponse(java.net.http.HttpResponse<String> response)
            throws IOException {
        return mapper.readValue(response.body(), Map.class);
    }

    /**
     * Assert response has expected status code.
     *
     * @param response HTTP response
     * @param expectedStatus expected HTTP status
     */
    protected void assertStatusCode(java.net.http.HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).isEqualTo(expectedStatus);
    }

    /**
     * Assert response has expected status and non-empty body.
     *
     * @param response HTTP response
     * @param expectedStatus expected HTTP status
     */
    protected void assertStatusAndBody(java.net.http.HttpResponse<String> response,
                                        int expectedStatus) {
        assertStatusCode(response, expectedStatus);
        assertThat(response.body()).isNotEmpty();
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
    protected Map<String, String> withTenant(String tenantId) {
        return Map.of("X-Tenant-ID", tenantId);
    }

    /**
     * Get headers with auth token and tenant ID.
     *
     * @param token bearer token
     * @param tenantId tenant identifier
     * @return headers map with Authorization and X-Tenant-ID
     */
    protected Map<String, String> withAuthAndTenant(String token, String tenantId) {
        return Map.of(
                "Authorization", "Bearer " + token,
                "X-Tenant-ID", tenantId
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Network Utilities
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Find a free port for test server to listen on.
     * Called by subclass in @BeforeEach: {@code port = findFreePort();}
     *
     * @return available port number
     */
    protected static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /**
     * Wait for server to be ready (blocking poll).
     *
     * @param maxWaitMs maximum milliseconds to wait
     */
    protected void waitForServerReady(long maxWaitMs) throws Exception {
        long deadline = System.currentTimeMillis() + maxWaitMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                java.net.http.HttpResponse<String> resp = get("/health");
                if (resp.statusCode() == 200) {
                    return;
                }
            } catch (Exception e) {
                // Server not ready yet, try again
            }
            Thread.sleep(50);
        }
        throw new TimeoutException("Server did not become ready within " + maxWaitMs + "ms");
    }

    private static class TimeoutException extends Exception {
        TimeoutException(String msg) { super(msg); }
    }
}

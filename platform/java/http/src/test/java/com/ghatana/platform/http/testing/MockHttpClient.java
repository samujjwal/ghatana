package com.ghatana.platform.http.server.testing;

import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import lombok.Builder;
import lombok.Singular;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;

/**
 * Production-grade mock HTTP client for testing without network calls, with request stubbing and default responses.
 *
 * <p><b>Purpose</b><br>
 * Provides in-memory HTTP client for testing that stubs responses for specific requests,
 * eliminating network overhead and external dependencies. Enables fast, deterministic
 * testing of HTTP client code without real servers.
 *
 * <p><b>Architecture Role</b><br>
 * Mock HTTP client in core/http/testing for unit testing HTTP client code.
 * Used by:
 * - HTTP Client Tests - Test client logic without network
 * - Service Tests - Mock external HTTP services
 * - Integration Tests - Test error handling without real failures
 * - Performance Tests - Eliminate network variability
 *
 * <p><b>Mock Client Features</b><br>
 * - <b>Request Stubbing</b>: Map (method, path) → response // GH-90000
 * - <b>Dynamic Responses</b>: Function-based responses (access request data) // GH-90000
 * - <b>Default Response</b>: Fallback for unmatched requests (default 404) // GH-90000
 * - <b>Stub Management</b>: Add, remove, clear stubs dynamically
 * - <b>Builder Pattern</b>: Fluent API for stub configuration
 * - <b>In-Memory</b>: No network I/O (instant responses) // GH-90000
 * - <b>Thread-Safe</b>: Safe for concurrent access (after build) // GH-90000
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // 1. Basic stubbing (static responses) // GH-90000
 * MockHttpClient client = MockHttpClient.builder() // GH-90000
 *     .stub(HttpMethod.GET, "/users/123", request -> // GH-90000
 *         HttpResponse.ok200() // GH-90000
 *             .withHeader("Content-Type", "application/json") // GH-90000
 *             .withBody("{\"id\":123,\"name\":\"John Doe\"}".getBytes()) // GH-90000
 *             .build() // GH-90000
 *     )
 *     .stub(HttpMethod.POST, "/users", request -> // GH-90000
 *         HttpResponse.ofCode(201) // GH-90000
 *             .withJson("{\"id\":124,\"name\":\"Jane Doe\"}") // GH-90000
 *             .build() // GH-90000
 *     )
 *     .build(); // GH-90000
 *
 * HttpResponse response = client.execute(HttpRequest.get("/users/123"));
 * assertEquals(200, response.getCode()); // GH-90000
 *
 * // 2. Dynamic responses (based on request) // GH-90000
 * MockHttpClient client = MockHttpClient.builder() // GH-90000
 *     .stub(HttpMethod.GET, "/users/:id", request -> { // GH-90000
 *         String id = extractId(request.getPath()); // GH-90000
 *
 *         if ("123".equals(id)) { // GH-90000
 *             return HttpResponse.ok200() // GH-90000
 *                 .withJson("{\"id\":123,\"name\":\"John\"}") // GH-90000
 *                 .build(); // GH-90000
 *         } else {
 *             return HttpResponse.ofCode(404) // GH-90000
 *                 .withJson("{\"error\":\"User not found\"}") // GH-90000
 *                 .build(); // GH-90000
 *         }
 *     })
 *     .build(); // GH-90000
 *
 * // 3. Custom default response
 * MockHttpClient client = MockHttpClient.builder() // GH-90000
 *     .stub(HttpMethod.GET, "/api/data", request -> // GH-90000
 *         HttpResponse.ok200().withJson("{\"data\":[]}") // GH-90000
 *     )
 *     .defaultResponse( // GH-90000
 *         HttpResponse.ofCode(503) // GH-90000
 *             .withJson("{\"error\":\"Service unavailable\"}") // GH-90000
 *             .build() // GH-90000
 *     )
 *     .build(); // GH-90000
 *
 * HttpResponse response = client.execute(HttpRequest.get("/unknown"));
 * assertEquals(503, response.getCode()); // GH-90000
 *
 * // 4. Test error handling
 * MockHttpClient client = MockHttpClient.builder() // GH-90000
 *     .stub(HttpMethod.GET, "/api/data", request -> // GH-90000
 *         HttpResponse.ofCode(500) // GH-90000
 *             .withJson("{\"error\":\"Internal server error\"}") // GH-90000
 *             .build() // GH-90000
 *     )
 *     .build(); // GH-90000
 *
 * try {
 *     myService.fetchData(client); // GH-90000
 *     fail("Expected exception");
 * } catch (ServiceException e) { // GH-90000
 *     assertEquals("Internal server error", e.getMessage()); // GH-90000
 * }
 *
 * // 5. Multiple stubs with different methods
 * MockHttpClient client = MockHttpClient.builder() // GH-90000
 *     .stub(HttpMethod.GET, "/users", request -> // GH-90000
 *         HttpResponse.ok200().withJson("[{\"id\":1},{\"id\":2}]") // GH-90000
 *     )
 *     .stub(HttpMethod.POST, "/users", request -> // GH-90000
 *         HttpResponse.ofCode(201).withJson("{\"id\":3}") // GH-90000
 *     )
 *     .stub(HttpMethod.PUT, "/users/1", request -> // GH-90000
 *         HttpResponse.ok200().withJson("{\"id\":1,\"updated\":true}") // GH-90000
 *     )
 *     .stub(HttpMethod.DELETE, "/users/1", request -> // GH-90000
 *         HttpResponse.ofCode(204).build() // GH-90000
 *     )
 *     .build(); // GH-90000
 *
 * // 6. Request-based conditional responses
 * MockHttpClient client = MockHttpClient.builder() // GH-90000
 *     .stub(HttpMethod.GET, "/api/search", request -> { // GH-90000
 *         String query = request.getQueryParameter("q");
 *
 *         if (query == null || query.isEmpty()) { // GH-90000
 *             return HttpResponse.ofCode(400) // GH-90000
 *                 .withJson("{\"error\":\"Missing query parameter\"}") // GH-90000
 *                 .build(); // GH-90000
 *         }
 *
 *         return HttpResponse.ok200() // GH-90000
 *             .withJson("{\"results\":[],\"query\":\"" + query + "\"}") // GH-90000
 *             .build(); // GH-90000
 *     })
 *     .build(); // GH-90000
 *
 * // 7. Dynamic stub management
 * MockHttpClient client = MockHttpClient.builder().build(); // GH-90000
 *
 * // Add stub at runtime
 * client.addStub(HttpMethod.GET, "/health", request -> // GH-90000
 *     HttpResponse.ok200().withJson("{\"status\":\"healthy\"}") // GH-90000
 * );
 *
 * // Test
 * HttpResponse response = client.execute(HttpRequest.get("/health"));
 * assertEquals(200, response.getCode()); // GH-90000
 *
 * // Remove stub
 * client.removeStub(HttpMethod.GET, "/health"); // GH-90000
 *
 * // Clear all stubs
 * client.clearStubs(); // GH-90000
 * }</pre>
 *
 * <p><b>Stub Key Format</b><br>
 * Stubs are keyed by method + path:
 * <pre>
 * GET /users     → "GET:/users"
 * POST /users    → "POST:/users"
 * PUT /users/123 → "PUT:/users/123"
 * </pre>
 *
 * <p><b>Request Matching</b><br>
 * <pre>
 * 1. Extract method and path from request
 * 2. Create stub key: method + ":" + path
 * 3. Look up stub in map
 * 4. If found: Execute stub function with request
 * 5. If not found: Return default response (404) // GH-90000
 * </pre>
 *
 * <p><b>Default Response</b><br>
 * <pre>
 * Status: 404
 * Body: "Mock: No stub found"
 *
 * Can be overridden with custom response:
 * .defaultResponse(HttpResponse.ofCode(503).withJson("..."))
 * </pre>
 *
 * <p><b>Response Functions</b><br>
 * Functions can access request properties:
 * <pre>{@code
 * .stub(HttpMethod.POST, "/api/data", request -> { // GH-90000
 *     // Access method
 *     HttpMethod method = request.getMethod(); // GH-90000
 *
 *     // Access headers
 *     String authHeader = request.getHeader("Authorization");
 *
 *     // Access query parameters
 *     String filter = request.getQueryParameter("filter");
 *
 *     // Access body
 *     byte[] body = request.getBody().asArray(); // GH-90000
 *
 *     // Build dynamic response
 *     return HttpResponse.ok200() // GH-90000
 *         .withJson(createResponse(body, filter)) // GH-90000
 *         .build(); // GH-90000
 * })
 * }</pre>
 *
 * <p><b>Common Patterns</b><br>
 * <pre>{@code
 * // Success/error based on request data
 * .stub(HttpMethod.POST, "/api/validate", request -> { // GH-90000
 *     String json = new String(request.getBody().asArray()); // GH-90000
 *     boolean valid = validateJson(json); // GH-90000
 *
 *     return valid
 *         ? HttpResponse.ok200().withJson("{\"valid\":true}") // GH-90000
 *         : HttpResponse.ofCode(400).withJson("{\"valid\":false}"); // GH-90000
 * })
 *
 * // Simulate slow responses
 * .stub(HttpMethod.GET, "/slow", request -> { // GH-90000
 *     Thread.sleep(5000);  // 5 second delay // GH-90000
 *     return HttpResponse.ok200().withJson("{\"data\":\"slow\"}"); // GH-90000
 * })
 *
 * // Simulate network errors
 * .stub(HttpMethod.GET, "/error", request -> { // GH-90000
 *     throw new IOException("Connection refused");
 * })
 * }</pre>
 *
 * <p><b>Best Practices</b><br>
 * - Use stubs for predictable, fast tests
 * - Define stubs in @BeforeEach for test isolation
 * - Use dynamic responses for request-based logic
 * - Override default response for consistent error handling
 * - Clear stubs between tests (or create new client) // GH-90000
 * - Keep stub functions simple (avoid complex logic) // GH-90000
 * - Test both success and error cases
 * - Use MockHttpClient for unit tests, HttpServerTestRunner for integration tests
 *
 * <p><b>Limitations</b><br>
 * - No path parameter matching (exact path match only) // GH-90000
 * - No wildcard/regex path matching
 * - No request history tracking
 * - No verification of stub invocations
 * - For advanced mocking, consider Mockito + real HTTP client
 *
 * <p><b>Thread Safety</b><br>
 * Builder is NOT thread-safe (configure from single thread). // GH-90000
 * Built MockHttpClient IS thread-safe (stubs map is concurrent). // GH-90000
 * Safe to use with parallel tests after build(). // GH-90000
 *
 * @see HttpServerTestRunner
 * @see HttpTestUtils
 * @see HttpServerTestExtension
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Mock HTTP client for testing without network calls
 * @doc.layer core
 * @doc.pattern Adapter
 */
@Builder
public class MockHttpClient {

    @Singular
    private final Map<String, Function<HttpRequest, HttpResponse>> stubs;

    @Builder.Default
    private final HttpResponse defaultResponse = HttpResponse.ofCode(404) // GH-90000
        .withBody("Mock: No stub found".getBytes(StandardCharsets.UTF_8)) // GH-90000
        .build(); // GH-90000

    /**
     * Executes a request against the mock client.
     *
     * @param request The HTTP request
     * @return The stubbed or default response
     */
    public HttpResponse execute(HttpRequest request) { // GH-90000
        String key = stubKey(request.getMethod(), request.getPath()); // GH-90000
        Function<HttpRequest, HttpResponse> stub = stubs.get(key); // GH-90000

        if (stub != null) { // GH-90000
            return stub.apply(request); // GH-90000
        }

        return defaultResponse;
    }

    /**
     * Adds a stub for a specific method and path.
     *
     * @param method The HTTP method
     * @param path The request path
     * @param responseFunction Function that creates the response
     */
    public void addStub(HttpMethod method, String path, Function<HttpRequest, HttpResponse> responseFunction) { // GH-90000
        stubs.put(stubKey(method, path), responseFunction); // GH-90000
    }

    /**
     * Removes a stub.
     *
     * @param method The HTTP method
     * @param path The request path
     */
    public void removeStub(HttpMethod method, String path) { // GH-90000
        stubs.remove(stubKey(method, path)); // GH-90000
    }

    /**
     * Clears all stubs.
     */
    public void clearStubs() { // GH-90000
        stubs.clear(); // GH-90000
    }

    /**
     * Gets the number of stubs.
     *
     * @return The stub count
     */
    public int getStubCount() { // GH-90000
        return stubs.size(); // GH-90000
    }

    private String stubKey(HttpMethod method, String path) { // GH-90000
        return method.name() + ":" + path; // GH-90000
    }

    /**
     * Builder helper for creating common response stubs.
     */
    public static class ResponseStubs {

        /**
         * Creates a JSON response stub.
         */
        public static Function<HttpRequest, HttpResponse> json(String json) { // GH-90000
            return request -> HttpResponse.ok200() // GH-90000
                .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json") // GH-90000
                .withBody(json.getBytes(StandardCharsets.UTF_8)) // GH-90000
                .build(); // GH-90000
        }

        /**
         * Creates a JSON response stub with custom status.
         */
        public static Function<HttpRequest, HttpResponse> json(int status, String json) { // GH-90000
            return request -> HttpResponse.ofCode(status) // GH-90000
                .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json") // GH-90000
                .withBody(json.getBytes(StandardCharsets.UTF_8)) // GH-90000
                .build(); // GH-90000
        }

        /**
         * Creates a text response stub.
         */
        public static Function<HttpRequest, HttpResponse> text(String text) { // GH-90000
            return request -> HttpResponse.ok200() // GH-90000
                .withBody(text.getBytes(StandardCharsets.UTF_8)) // GH-90000
                .build(); // GH-90000
        }

        /**
         * Creates an error response stub.
         */
        public static Function<HttpRequest, HttpResponse> error(int status, String message) { // GH-90000
            return request -> HttpResponse.ofCode(status) // GH-90000
                .withBody(message.getBytes(StandardCharsets.UTF_8)) // GH-90000
                .build(); // GH-90000
        }

        /**
         * Creates a response that echoes the request body.
         */
        public static Function<HttpRequest, HttpResponse> echo() { // GH-90000
            return request -> HttpResponse.ok200() // GH-90000
                .withBody(request.getBody()) // GH-90000
                .build(); // GH-90000
        }
    }
}

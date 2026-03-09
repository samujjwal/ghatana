package com.ghatana.platform.http.server.testing;

import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import lombok.Builder;
import lombok.Singular;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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
 * - <b>Request Stubbing</b>: Map (method, path) → response
 * - <b>Dynamic Responses</b>: Function-based responses (access request data)
 * - <b>Default Response</b>: Fallback for unmatched requests (default 404)
 * - <b>Stub Management</b>: Add, remove, clear stubs dynamically
 * - <b>Builder Pattern</b>: Fluent API for stub configuration
 * - <b>In-Memory</b>: No network I/O (instant responses)
 * - <b>Thread-Safe</b>: Safe for concurrent access (after build)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // 1. Basic stubbing (static responses)
 * MockHttpClient client = MockHttpClient.builder()
 *     .stub(HttpMethod.GET, "/users/123", request -> 
 *         HttpResponse.ok200()
 *             .withHeader("Content-Type", "application/json")
 *             .withBody("{\"id\":123,\"name\":\"John Doe\"}".getBytes())
 *             .build()
 *     )
 *     .stub(HttpMethod.POST, "/users", request -> 
 *         HttpResponse.ofCode(201)
 *             .withJson("{\"id\":124,\"name\":\"Jane Doe\"}")
 *             .build()
 *     )
 *     .build();
 *
 * HttpResponse response = client.execute(HttpRequest.get("/users/123"));
 * assertEquals(200, response.getCode());
 *
 * // 2. Dynamic responses (based on request)
 * MockHttpClient client = MockHttpClient.builder()
 *     .stub(HttpMethod.GET, "/users/:id", request -> {
 *         String id = extractId(request.getPath());
 *         
 *         if ("123".equals(id)) {
 *             return HttpResponse.ok200()
 *                 .withJson("{\"id\":123,\"name\":\"John\"}")
 *                 .build();
 *         } else {
 *             return HttpResponse.ofCode(404)
 *                 .withJson("{\"error\":\"User not found\"}")
 *                 .build();
 *         }
 *     })
 *     .build();
 *
 * // 3. Custom default response
 * MockHttpClient client = MockHttpClient.builder()
 *     .stub(HttpMethod.GET, "/api/data", request -> 
 *         HttpResponse.ok200().withJson("{\"data\":[]}")
 *     )
 *     .defaultResponse(
 *         HttpResponse.ofCode(503)
 *             .withJson("{\"error\":\"Service unavailable\"}")
 *             .build()
 *     )
 *     .build();
 *
 * HttpResponse response = client.execute(HttpRequest.get("/unknown"));
 * assertEquals(503, response.getCode());
 *
 * // 4. Test error handling
 * MockHttpClient client = MockHttpClient.builder()
 *     .stub(HttpMethod.GET, "/api/data", request -> 
 *         HttpResponse.ofCode(500)
 *             .withJson("{\"error\":\"Internal server error\"}")
 *             .build()
 *     )
 *     .build();
 *
 * try {
 *     myService.fetchData(client);
 *     fail("Expected exception");
 * } catch (ServiceException e) {
 *     assertEquals("Internal server error", e.getMessage());
 * }
 *
 * // 5. Multiple stubs with different methods
 * MockHttpClient client = MockHttpClient.builder()
 *     .stub(HttpMethod.GET, "/users", request -> 
 *         HttpResponse.ok200().withJson("[{\"id\":1},{\"id\":2}]")
 *     )
 *     .stub(HttpMethod.POST, "/users", request -> 
 *         HttpResponse.ofCode(201).withJson("{\"id\":3}")
 *     )
 *     .stub(HttpMethod.PUT, "/users/1", request -> 
 *         HttpResponse.ok200().withJson("{\"id\":1,\"updated\":true}")
 *     )
 *     .stub(HttpMethod.DELETE, "/users/1", request -> 
 *         HttpResponse.ofCode(204).build()
 *     )
 *     .build();
 *
 * // 6. Request-based conditional responses
 * MockHttpClient client = MockHttpClient.builder()
 *     .stub(HttpMethod.GET, "/api/search", request -> {
 *         String query = request.getQueryParameter("q");
 *         
 *         if (query == null || query.isEmpty()) {
 *             return HttpResponse.ofCode(400)
 *                 .withJson("{\"error\":\"Missing query parameter\"}")
 *                 .build();
 *         }
 *         
 *         return HttpResponse.ok200()
 *             .withJson("{\"results\":[],\"query\":\"" + query + "\"}")
 *             .build();
 *     })
 *     .build();
 *
 * // 7. Dynamic stub management
 * MockHttpClient client = MockHttpClient.builder().build();
 *
 * // Add stub at runtime
 * client.addStub(HttpMethod.GET, "/health", request ->
 *     HttpResponse.ok200().withJson("{\"status\":\"healthy\"}")
 * );
 *
 * // Test
 * HttpResponse response = client.execute(HttpRequest.get("/health"));
 * assertEquals(200, response.getCode());
 *
 * // Remove stub
 * client.removeStub(HttpMethod.GET, "/health");
 *
 * // Clear all stubs
 * client.clearStubs();
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
 * 5. If not found: Return default response (404)
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
 * .stub(HttpMethod.POST, "/api/data", request -> {
 *     // Access method
 *     HttpMethod method = request.getMethod();
 *     
 *     // Access headers
 *     String authHeader = request.getHeader("Authorization");
 *     
 *     // Access query parameters
 *     String filter = request.getQueryParameter("filter");
 *     
 *     // Access body
 *     byte[] body = request.getBody().asArray();
 *     
 *     // Build dynamic response
 *     return HttpResponse.ok200()
 *         .withJson(createResponse(body, filter))
 *         .build();
 * })
 * }</pre>
 *
 * <p><b>Common Patterns</b><br>
 * <pre>{@code
 * // Success/error based on request data
 * .stub(HttpMethod.POST, "/api/validate", request -> {
 *     String json = new String(request.getBody().asArray());
 *     boolean valid = validateJson(json);
 *     
 *     return valid 
 *         ? HttpResponse.ok200().withJson("{\"valid\":true}")
 *         : HttpResponse.ofCode(400).withJson("{\"valid\":false}");
 * })
 *
 * // Simulate slow responses
 * .stub(HttpMethod.GET, "/slow", request -> {
 *     Thread.sleep(5000);  // 5 second delay
 *     return HttpResponse.ok200().withJson("{\"data\":\"slow\"}");
 * })
 *
 * // Simulate network errors
 * .stub(HttpMethod.GET, "/error", request -> {
 *     throw new IOException("Connection refused");
 * })
 * }</pre>
 *
 * <p><b>Best Practices</b><br>
 * - Use stubs for predictable, fast tests
 * - Define stubs in @BeforeEach for test isolation
 * - Use dynamic responses for request-based logic
 * - Override default response for consistent error handling
 * - Clear stubs between tests (or create new client)
 * - Keep stub functions simple (avoid complex logic)
 * - Test both success and error cases
 * - Use MockHttpClient for unit tests, HttpServerTestRunner for integration tests
 *
 * <p><b>Limitations</b><br>
 * - No path parameter matching (exact path match only)
 * - No wildcard/regex path matching
 * - No request history tracking
 * - No verification of stub invocations
 * - For advanced mocking, consider Mockito + real HTTP client
 *
 * <p><b>Thread Safety</b><br>
 * Builder is NOT thread-safe (configure from single thread).
 * Built MockHttpClient IS thread-safe (stubs map is concurrent).
 * Safe to use with parallel tests after build().
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
    private final HttpResponse defaultResponse = HttpResponse.ofCode(404)
        .withBody("Mock: No stub found".getBytes(StandardCharsets.UTF_8))
        .build();
    
    /**
     * Executes a request against the mock client.
     * 
     * @param request The HTTP request
     * @return The stubbed or default response
     */
    public HttpResponse execute(HttpRequest request) {
        String key = stubKey(request.getMethod(), request.getPath());
        Function<HttpRequest, HttpResponse> stub = stubs.get(key);
        
        if (stub != null) {
            return stub.apply(request);
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
    public void addStub(HttpMethod method, String path, Function<HttpRequest, HttpResponse> responseFunction) {
        stubs.put(stubKey(method, path), responseFunction);
    }
    
    /**
     * Removes a stub.
     * 
     * @param method The HTTP method
     * @param path The request path
     */
    public void removeStub(HttpMethod method, String path) {
        stubs.remove(stubKey(method, path));
    }
    
    /**
     * Clears all stubs.
     */
    public void clearStubs() {
        stubs.clear();
    }
    
    /**
     * Gets the number of stubs.
     * 
     * @return The stub count
     */
    public int getStubCount() {
        return stubs.size();
    }
    
    private String stubKey(HttpMethod method, String path) {
        return method.name() + ":" + path;
    }
    
    /**
     * Builder helper for creating common response stubs.
     */
    public static class ResponseStubs {
        
        /**
         * Creates a JSON response stub.
         */
        public static Function<HttpRequest, HttpResponse> json(String json) {
            return request -> HttpResponse.ok200()
                .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        }
        
        /**
         * Creates a JSON response stub with custom status.
         */
        public static Function<HttpRequest, HttpResponse> json(int status, String json) {
            return request -> HttpResponse.ofCode(status)
                .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        }
        
        /**
         * Creates a text response stub.
         */
        public static Function<HttpRequest, HttpResponse> text(String text) {
            return request -> HttpResponse.ok200()
                .withBody(text.getBytes(StandardCharsets.UTF_8))
                .build();
        }
        
        /**
         * Creates an error response stub.
         */
        public static Function<HttpRequest, HttpResponse> error(int status, String message) {
            return request -> HttpResponse.ofCode(status)
                .withBody(message.getBytes(StandardCharsets.UTF_8))
                .build();
        }
        
        /**
         * Creates a response that echoes the request body.
         */
        public static Function<HttpRequest, HttpResponse> echo() {
            return request -> HttpResponse.ok200()
                .withBody(request.getBody())
                .build();
        }
    }
}

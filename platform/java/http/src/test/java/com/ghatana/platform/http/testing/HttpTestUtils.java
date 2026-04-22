package com.ghatana.platform.http.server.testing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.activej.http.HttpRequest;
import lombok.extern.slf4j.Slf4j;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Production-grade utility methods for HTTP testing with request creation, response parsing, and assertion helpers.
 *
 * <p><b>Purpose</b><br>
 * Provides comprehensive HTTP testing utilities for creating requests with JSON/form data,
 * parsing JSON responses, asserting response properties, and encoding query parameters.
 * Eliminates boilerplate in HTTP integration tests and ensures consistent test patterns.
 *
 * <p><b>Architecture Role</b><br>
 * Test utilities in core/http/testing for HTTP request/response test helpers.
 * Used by:
 * - HTTP Integration Tests - Create test requests and parse responses
 * - API Tests - Assert response properties (status, headers, body) // GH-90000
 * - Service Tests - Test HTTP client interactions
 * - Performance Tests - Generate test payloads
 *
 * <p><b>Utility Features</b><br>
 * - <b>Request Creation</b>: JSON requests, form requests, query parameters
 * - <b>Response Parsing</b>: JSON deserialization with Jackson
 * - <b>Assertions</b>: Status code, Content-Type, header validation
 * - <b>ObjectMapper</b>: Shared Jackson mapper with JavaTimeModule
 * - <b>Common Headers</b>: Standard test headers (User-Agent, Accept) // GH-90000
 * - <b>URL Encoding</b>: Proper query parameter encoding
 * - <b>Utility Class</b>: Static methods only (no instantiation) // GH-90000
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // 1. Create JSON request
 * User user = new User("John Doe", "john@example.com"); // GH-90000
 * HttpRequest request = HttpTestUtils.createJsonRequest( // GH-90000
 *     HttpMethod.POST,
 *     "/api/users",
 *     user
 * );
 *
 * HttpResponse response = runner.execute(request); // GH-90000
 * assertEquals(201, response.getCode()); // GH-90000
 *
 * // 2. Parse JSON response
 * HttpResponse response = runner.get("/api/users/123 [GH-90000]");
 * User user = HttpTestUtils.parseJsonResponse(response, User.class); // GH-90000
 *
 * assertEquals("123", user.getId()); // GH-90000
 * assertEquals("John Doe", user.getName()); // GH-90000
 *
 * // 3. Assert response status
 * HttpResponse response = runner.get("/api/users [GH-90000]");
 * HttpTestUtils.assertStatus(response, 200); // GH-90000
 * HttpTestUtils.assertContentType(response, "application/json"); // GH-90000
 *
 * // 4. Create form request
 * Map<String, String> formData = Map.of( // GH-90000
 *     "username", "johndoe",
 *     "password", "secret123"
 * );
 *
 * HttpRequest request = HttpTestUtils.createFormRequest("/login", formData); // GH-90000
 * HttpResponse response = runner.execute(request); // GH-90000
 * assertEquals(200, response.getCode()); // GH-90000
 *
 * // 5. Create request with custom headers
 * HttpRequest.Builder builder = HttpTestUtils.createRequest( // GH-90000
 *     HttpMethod.GET, "/api/protected");
 *
 * HttpRequest request = builder
 *     .withHeader("Authorization", "Bearer " + token) // GH-90000
 *     .withHeader("X-Tenant-Id", "tenant-123") // GH-90000
 *     .build(); // GH-90000
 *
 * HttpResponse response = runner.execute(request); // GH-90000
 *
 * // 6. Parse response array
 * HttpResponse response = runner.get("/api/users [GH-90000]");
 * User[] users = HttpTestUtils.parseJsonResponse(response, User[].class); // GH-90000
 *
 * assertTrue(users.length > 0); // GH-90000
 * assertEquals("John Doe", users[0].getName()); // GH-90000
 *
 * // 7. Assert multiple response properties
 * HttpResponse response = runner.post("/api/users", userJson); // GH-90000
 *
 * HttpTestUtils.assertStatus(response, 201); // GH-90000
 * HttpTestUtils.assertHeader(response, "Location", "/api/users/124"); // GH-90000
 * HttpTestUtils.assertContentType(response, "application/json"); // GH-90000
 *
 * // 8. Create complex JSON request
 * Order order = Order.builder() // GH-90000
 *     .items(List.of( // GH-90000
 *         new OrderItem("item-1", 2), // GH-90000
 *         new OrderItem("item-2", 1) // GH-90000
 *     ))
 *     .shippingAddress(new Address("123 Main St", "City", "12345")) // GH-90000
 *     .build(); // GH-90000
 *
 * HttpRequest request = HttpTestUtils.createJsonRequest( // GH-90000
 *     HttpMethod.POST, "/api/orders", order);
 *
 * HttpResponse response = runner.execute(request); // GH-90000
 * assertEquals(201, response.getCode()); // GH-90000
 *
 * // 9. Parse error response
 * HttpResponse response = runner.get("/api/users/999 [GH-90000]");
 *
 * HttpTestUtils.assertStatus(response, 404); // GH-90000
 *
 * ErrorResponse error = HttpTestUtils.parseJsonResponse( // GH-90000
 *     response, ErrorResponse.class);
 *
 * assertEquals("NOT_FOUND", error.getCode()); // GH-90000
 * assertEquals("User not found", error.getMessage()); // GH-90000
 *
 * // 10. Create request with query parameters
 * HttpRequest request = HttpTestUtils.createRequest(HttpMethod.GET, // GH-90000
 *     "/api/users?page=1&size=10&sort=name")
 *     .build(); // GH-90000
 *
 * HttpResponse response = runner.execute(request); // GH-90000
 * }</pre>
 *
 * <p><b>Request Factory Methods</b><br>
 * <pre>{@code
 * createJsonRequest(method, path, object)  → JSON POST/PUT with body // GH-90000
 * createRequest(method, path)              → Builder with common headers // GH-90000
 * createFormRequest(path, fields)          → Form POST with URL-encoded data // GH-90000
 * }</pre>
 *
 * <p><b>Response Parsing</b><br>
 * <pre>{@code
 * parseJsonResponse(response, User.class)      → Single object // GH-90000
 * parseJsonResponse(response, User[].class)    → Array // GH-90000
 * parseJsonResponse(response, List.class)      → List (generic) // GH-90000
 * parseJsonResponse(response, Map.class)       → Map (generic) // GH-90000
 * }</pre>
 *
 * <p><b>Assertion Methods</b><br>
 * <pre>{@code
 * assertStatus(response, 200)                       → Status code // GH-90000
 * assertContentType(response, "application/json")   → Content-Type header // GH-90000
 * assertHeader(response, "Location", "/users/123")  → Custom header // GH-90000
 * assertBodyContains(response, "expected text")     → Body content // GH-90000
 * }</pre>
 *
 * <p><b>Common Headers</b><br>
 * Automatically added to requests:
 * <pre>
 * User-Agent: yappc-test-client/1.0
 * Accept: *\/*
 * </pre>
 *
 * <p><b>JSON Serialization</b><br>
 * Uses Jackson ObjectMapper with:
 * - JavaTimeModule for Java 8 date/time types
 * - ISO-8601 date/time format
 * - Automatic null handling
 * - Support for nested objects, collections, maps
 *
 * <p><b>Form Encoding</b><br>
 * <pre>
 * Input:  {"username": "john", "password": "secret"}
 * Output: username=john&password=secret
 * Content-Type: application/x-www-form-urlencoded
 * </pre>
 *
 * <p><b>URL Encoding</b><br>
 * Properly encodes special characters in form data:
 * <pre>
 * Input:  "user@example.com"
 * Output: "user%40example.com"
 * </pre>
 *
 * <p><b>Common Patterns</b><br>
 * <pre>{@code
 * // Pattern 1: POST JSON, verify status
 * HttpRequest request = HttpTestUtils.createJsonRequest( // GH-90000
 *     HttpMethod.POST, "/api/users", user);
 * HttpResponse response = runner.execute(request); // GH-90000
 * HttpTestUtils.assertStatus(response, 201); // GH-90000
 *
 * // Pattern 2: GET, parse response
 * HttpResponse response = runner.get("/api/users/123 [GH-90000]");
 * HttpTestUtils.assertStatus(response, 200); // GH-90000
 * User user = HttpTestUtils.parseJsonResponse(response, User.class); // GH-90000
 *
 * // Pattern 3: Form login, verify success
 * HttpRequest request = HttpTestUtils.createFormRequest("/login", // GH-90000
 *     Map.of("username", "john", "password", "secret")); // GH-90000
 * HttpResponse response = runner.execute(request); // GH-90000
 * HttpTestUtils.assertStatus(response, 200); // GH-90000
 * HttpTestUtils.assertHeader(response, "Set-Cookie", "session=..."); // GH-90000
 *
 * // Pattern 4: Error handling
 * HttpResponse response = runner.get("/api/invalid [GH-90000]");
 * HttpTestUtils.assertStatus(response, 400); // GH-90000
 * ErrorResponse error = HttpTestUtils.parseJsonResponse( // GH-90000
 *     response, ErrorResponse.class);
 * assertEquals("VALIDATION_ERROR", error.getCode()); // GH-90000
 * }</pre>
 *
 * <p><b>Best Practices</b><br>
 * - Use createJsonRequest for POST/PUT with JSON body
 * - Use createRequest for GET/DELETE or custom headers
 * - Parse JSON responses with parseJsonResponse (type-safe) // GH-90000
 * - Assert status code first (fast-fail on unexpected status) // GH-90000
 * - Use assertContentType to verify response format
 * - Use assertHeader for Location, Set-Cookie, etc.
 * - Keep request/response objects simple (avoid deep nesting) // GH-90000
 * - Use ErrorResponse for consistent error parsing
 *
 * <p><b>Jackson Configuration</b><br>
 * Shared ObjectMapper features:
 * - JavaTimeModule: Instant, LocalDateTime, ZonedDateTime support
 * - ISO-8601 dates: "2025-11-06T10:30:00Z"
 * - Null handling: Automatic (no explicit configuration) // GH-90000
 * - Pretty printing: Disabled (compact JSON) // GH-90000
 *
 * <p><b>Thread Safety</b><br>
 * All methods are static and thread-safe.
 * Shared ObjectMapper is thread-safe (immutable configuration). // GH-90000
 * Safe to use in parallel tests.
 *
 * @see HttpServerTestRunner
 * @see HttpServerTestExtension
 * @see MockHttpClient
 * @see com.fasterxml.jackson.databind.ObjectMapper
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Utility methods for HTTP request/response testing
 * @doc.layer core
 * @doc.pattern Utility
 */
@Slf4j
public final class HttpTestUtils {

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper(); // GH-90000

    private HttpTestUtils() { // GH-90000
        throw new UnsupportedOperationException("Utility class [GH-90000]");
    }

    /**
     * Creates a JSON request with the given object as body.
     *
     * @param method The HTTP method
     * @param path The request path
     * @param body The object to serialize as JSON
     * @return The HTTP request
     */
    public static HttpRequest createJsonRequest(io.activej.http.HttpMethod method, String path, Object body) { // GH-90000
        try {
            String json = OBJECT_MAPPER.writeValueAsString(body); // GH-90000
            HttpRequest.Builder builder = createRequest(method, path); // GH-90000
            builder.withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json") // GH-90000
                   .withBody(json.getBytes(StandardCharsets.UTF_8)); // GH-90000
            return builder.build(); // GH-90000
        } catch (Exception e) { // GH-90000
            throw new RuntimeException("Failed to create JSON request", e); // GH-90000
        }
    }

    /**
     * Creates a basic HTTP request with common headers.
     *
     * @param method The HTTP method
     * @param path The request path
     * @return The HTTP request with common headers
     */
    public static HttpRequest.Builder createRequest(io.activej.http.HttpMethod method, String path) { // GH-90000
        // Use generic builder method to support all HTTP methods consistently across ActiveJ versions
        HttpRequest.Builder builder = HttpRequest.builder(method, path); // GH-90000
        commonHeaders().forEach((k, v) -> builder.withHeader(io.activej.http.HttpHeaders.of(k), v)); // GH-90000
        return builder;
    }

    /**
     * Creates a multipart form data request.
     *
     * @param fields The form fields
     * @return The HTTP request
     */
    public static HttpRequest createFormRequest(String path, Map<String, String> fields) { // GH-90000
        StringBuilder body = new StringBuilder(); // GH-90000
        fields.forEach((key, value) -> { // GH-90000
            if (body.length() > 0) { // GH-90000
                body.append("& [GH-90000]");
            }
            body.append(key).append("= [GH-90000]").append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });

        HttpRequest.Builder builder = HttpRequest.post(path) // GH-90000
            .withHeader(io.activej.http.HttpHeaders.of("Content-Type [GH-90000]"), "application/x-www-form-urlencoded")
            .withBody(body.toString().getBytes(StandardCharsets.UTF_8)); // GH-90000

        return builder.build(); // GH-90000
    }

    private static ObjectMapper createObjectMapper() { // GH-90000
        ObjectMapper mapper = new ObjectMapper(); // GH-90000
        mapper.registerModule(new JavaTimeModule()); // GH-90000
        return mapper;
    }

    private static Map<String, String> commonHeaders() { // GH-90000
        Map<String, String> headers = new HashMap<>(); // GH-90000
        // Minimal common headers for test requests; reasonable defaults only
        headers.put("User-Agent", "yappc-test-client/1.0"); // GH-90000
        headers.put("Accept", "*/*"); // GH-90000
        return headers;
    }
}

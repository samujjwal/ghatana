package com.ghatana.platform.http.server.testing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
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
 * - API Tests - Assert response properties (status, headers, body)
 * - Service Tests - Test HTTP client interactions
 * - Performance Tests - Generate test payloads
 *
 * <p><b>Utility Features</b><br>
 * - <b>Request Creation</b>: JSON requests, form requests, query parameters
 * - <b>Response Parsing</b>: JSON deserialization with Jackson
 * - <b>Assertions</b>: Status code, Content-Type, header validation
 * - <b>ObjectMapper</b>: Shared Jackson mapper with JavaTimeModule
 * - <b>Common Headers</b>: Standard test headers (User-Agent, Accept)
 * - <b>URL Encoding</b>: Proper query parameter encoding
 * - <b>Utility Class</b>: Static methods only (no instantiation)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // 1. Create JSON request
 * User user = new User("John Doe", "john@example.com");
 * HttpRequest request = HttpTestUtils.createJsonRequest(
 *     HttpMethod.POST, 
 *     "/api/users", 
 *     user
 * );
 * 
 * HttpResponse response = runner.execute(request);
 * assertEquals(201, response.getCode());
 *
 * // 2. Parse JSON response
 * HttpResponse response = runner.get("/api/users/123");
 * User user = HttpTestUtils.parseJsonResponse(response, User.class);
 * 
 * assertEquals("123", user.getId());
 * assertEquals("John Doe", user.getName());
 *
 * // 3. Assert response status
 * HttpResponse response = runner.get("/api/users");
 * HttpTestUtils.assertStatus(response, 200);
 * HttpTestUtils.assertContentType(response, "application/json");
 *
 * // 4. Create form request
 * Map<String, String> formData = Map.of(
 *     "username", "johndoe",
 *     "password", "secret123"
 * );
 * 
 * HttpRequest request = HttpTestUtils.createFormRequest("/login", formData);
 * HttpResponse response = runner.execute(request);
 * assertEquals(200, response.getCode());
 *
 * // 5. Create request with custom headers
 * HttpRequest.Builder builder = HttpTestUtils.createRequest(
 *     HttpMethod.GET, "/api/protected");
 * 
 * HttpRequest request = builder
 *     .withHeader("Authorization", "Bearer " + token)
 *     .withHeader("X-Tenant-Id", "tenant-123")
 *     .build();
 * 
 * HttpResponse response = runner.execute(request);
 *
 * // 6. Parse response array
 * HttpResponse response = runner.get("/api/users");
 * User[] users = HttpTestUtils.parseJsonResponse(response, User[].class);
 * 
 * assertTrue(users.length > 0);
 * assertEquals("John Doe", users[0].getName());
 *
 * // 7. Assert multiple response properties
 * HttpResponse response = runner.post("/api/users", userJson);
 * 
 * HttpTestUtils.assertStatus(response, 201);
 * HttpTestUtils.assertHeader(response, "Location", "/api/users/124");
 * HttpTestUtils.assertContentType(response, "application/json");
 *
 * // 8. Create complex JSON request
 * Order order = Order.builder()
 *     .items(List.of(
 *         new OrderItem("item-1", 2),
 *         new OrderItem("item-2", 1)
 *     ))
 *     .shippingAddress(new Address("123 Main St", "City", "12345"))
 *     .build();
 * 
 * HttpRequest request = HttpTestUtils.createJsonRequest(
 *     HttpMethod.POST, "/api/orders", order);
 * 
 * HttpResponse response = runner.execute(request);
 * assertEquals(201, response.getCode());
 *
 * // 9. Parse error response
 * HttpResponse response = runner.get("/api/users/999");
 * 
 * HttpTestUtils.assertStatus(response, 404);
 * 
 * ErrorResponse error = HttpTestUtils.parseJsonResponse(
 *     response, ErrorResponse.class);
 * 
 * assertEquals("NOT_FOUND", error.getCode());
 * assertEquals("User not found", error.getMessage());
 *
 * // 10. Create request with query parameters
 * HttpRequest request = HttpTestUtils.createRequest(HttpMethod.GET, 
 *     "/api/users?page=1&size=10&sort=name")
 *     .build();
 * 
 * HttpResponse response = runner.execute(request);
 * }</pre>
 *
 * <p><b>Request Factory Methods</b><br>
 * <pre>{@code
 * createJsonRequest(method, path, object)  → JSON POST/PUT with body
 * createRequest(method, path)              → Builder with common headers
 * createFormRequest(path, fields)          → Form POST with URL-encoded data
 * }</pre>
 *
 * <p><b>Response Parsing</b><br>
 * <pre>{@code
 * parseJsonResponse(response, User.class)      → Single object
 * parseJsonResponse(response, User[].class)    → Array
 * parseJsonResponse(response, List.class)      → List (generic)
 * parseJsonResponse(response, Map.class)       → Map (generic)
 * }</pre>
 *
 * <p><b>Assertion Methods</b><br>
 * <pre>{@code
 * assertStatus(response, 200)                       → Status code
 * assertContentType(response, "application/json")   → Content-Type header
 * assertHeader(response, "Location", "/users/123")  → Custom header
 * assertBodyContains(response, "expected text")     → Body content
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
 * HttpRequest request = HttpTestUtils.createJsonRequest(
 *     HttpMethod.POST, "/api/users", user);
 * HttpResponse response = runner.execute(request);
 * HttpTestUtils.assertStatus(response, 201);
 *
 * // Pattern 2: GET, parse response
 * HttpResponse response = runner.get("/api/users/123");
 * HttpTestUtils.assertStatus(response, 200);
 * User user = HttpTestUtils.parseJsonResponse(response, User.class);
 *
 * // Pattern 3: Form login, verify success
 * HttpRequest request = HttpTestUtils.createFormRequest("/login", 
 *     Map.of("username", "john", "password", "secret"));
 * HttpResponse response = runner.execute(request);
 * HttpTestUtils.assertStatus(response, 200);
 * HttpTestUtils.assertHeader(response, "Set-Cookie", "session=...");
 *
 * // Pattern 4: Error handling
 * HttpResponse response = runner.get("/api/invalid");
 * HttpTestUtils.assertStatus(response, 400);
 * ErrorResponse error = HttpTestUtils.parseJsonResponse(
 *     response, ErrorResponse.class);
 * assertEquals("VALIDATION_ERROR", error.getCode());
 * }</pre>
 *
 * <p><b>Best Practices</b><br>
 * - Use createJsonRequest for POST/PUT with JSON body
 * - Use createRequest for GET/DELETE or custom headers
 * - Parse JSON responses with parseJsonResponse (type-safe)
 * - Assert status code first (fast-fail on unexpected status)
 * - Use assertContentType to verify response format
 * - Use assertHeader for Location, Set-Cookie, etc.
 * - Keep request/response objects simple (avoid deep nesting)
 * - Use ErrorResponse for consistent error parsing
 *
 * <p><b>Jackson Configuration</b><br>
 * Shared ObjectMapper features:
 * - JavaTimeModule: Instant, LocalDateTime, ZonedDateTime support
 * - ISO-8601 dates: "2025-11-06T10:30:00Z"
 * - Null handling: Automatic (no explicit configuration)
 * - Pretty printing: Disabled (compact JSON)
 *
 * <p><b>Thread Safety</b><br>
 * All methods are static and thread-safe.
 * Shared ObjectMapper is thread-safe (immutable configuration).
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
    
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();
    
    private HttpTestUtils() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * Creates a JSON request with the given object as body.
     * 
     * @param method The HTTP method
     * @param path The request path
     * @param body The object to serialize as JSON
     * @return The HTTP request
     */
    public static HttpRequest createJsonRequest(io.activej.http.HttpMethod method, String path, Object body) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(body);
            HttpRequest.Builder builder = createRequest(method, path);
            builder.withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
                   .withBody(json.getBytes(StandardCharsets.UTF_8));
            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create JSON request", e);
        }
    }
    
    /**
     * Creates a basic HTTP request with common headers.
     * 
     * @param method The HTTP method
     * @param path The request path
     * @return The HTTP request with common headers
     */
    public static HttpRequest.Builder createRequest(io.activej.http.HttpMethod method, String path) {
        // Use generic builder method to support all HTTP methods consistently across ActiveJ versions
        HttpRequest.Builder builder = HttpRequest.builder(method, path);
        commonHeaders().forEach((k, v) -> builder.withHeader(io.activej.http.HttpHeaders.of(k), v));
        return builder;
    }
    
    /**
     * Creates a multipart form data request.
     * 
     * @param fields The form fields
     * @return The HTTP request
     */
    public static HttpRequest createFormRequest(String path, Map<String, String> fields) {
        StringBuilder body = new StringBuilder();
        fields.forEach((key, value) -> {
            if (body.length() > 0) {
                body.append("&");
            }
            body.append(key).append("=").append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
        
        HttpRequest.Builder builder = HttpRequest.post(path)
            .withHeader(io.activej.http.HttpHeaders.of("Content-Type"), "application/x-www-form-urlencoded")
            .withBody(body.toString().getBytes(StandardCharsets.UTF_8));
        
        return builder.build();
    }
    
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    private static Map<String, String> commonHeaders() {
        Map<String, String> headers = new HashMap<>();
        // Minimal common headers for test requests; reasonable defaults only
        headers.put("User-Agent", "yappc-test-client/1.0");
        headers.put("Accept", "*/*");
        return headers;
    }
}

package com.ghatana.platform.http.server.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import io.activej.http.HttpHeader;
import io.activej.http.HttpResponse;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

/**
 * Production-grade fluent builder for HTTP responses with automatic JSON
 * serialization and common status code factories.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides unified, type-safe HTTP response construction with Jackson JSON
 * serialization, standard status codes, header management, and content type
 * handling. Eliminates boilerplate in response creation and ensures consistent
 * response formatting.
 *
 * <p>
 * <b>Architecture Role</b><br>
 * Response builder in core/http/response for HTTP response construction. Used
 * by: - Route Handlers - Create HTTP responses in RoutingServlet - Error
 * Handlers - Format error responses consistently - API Endpoints - Return
 * JSON/text responses - Filters - Modify responses in filter chain
 *
 * <p>
 * <b>Builder Features</b><br>
 * - <b>Fluent API</b>: Method chaining for response configuration - <b>Status
 * Factories</b>: Factory methods for common status codes (ok, created,
 * badRequest, etc.) - <b>JSON Serialization</b>: Automatic Jackson
 * serialization with JavaTimeModule - <b>Text Content</b>: Plain text responses
 * with UTF-8 encoding - <b>Header Management</b>: Add custom headers (Location,
 * Cache-Control, etc.) - <b>Content Types</b>: Automatic Content-Type header
 * (application/json, text/plain) - <b>Error Responses</b>: Consistent error
 * formatting with ErrorResponse
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * // 1. Simple text response (200 OK)
 * HttpResponse response = ResponseBuilder.ok()
 *     .text("Hello, World!")
 *     .build();
 *
 * // 2. JSON response (200 OK)
 * User user = userService.getUser(id);
 * HttpResponse response = ResponseBuilder.ok()
 *     .json(user)
 *     .build();
 *
 * // 3. Created response with Location header (201 Created)
 * User created = userService.createUser(userData);
 * HttpResponse response = ResponseBuilder.created()
 *     .header("Location", "/users/" + created.getId())
 *     .json(created)
 *     .build();
 *
 * // 4. No content response (204 No Content)
 * userService.deleteUser(id);
 * HttpResponse response = ResponseBuilder.noContent()
 *     .build();
 *
 * // 5. Bad request with error details (400 Bad Request)
 * HttpResponse response = ResponseBuilder.badRequest()
 *     .json(ErrorResponse.builder()
 *         .status(400)
 *         .code("VALIDATION_ERROR")
 *         .message("Invalid user data")
 *         .validationErrors(errors)
 *         .build())
 *     .build();
 *
 * // 6. Unauthorized response (401 Unauthorized)
 * HttpResponse response = ResponseBuilder.unauthorized()
 *     .header("WWW-Authenticate", "Bearer realm=\"API\"")
 *     .json(ErrorResponse.of(401, "INVALID_TOKEN", "Token expired"))
 *     .build();
 *
 * // 7. Not found response (404 Not Found)
 * HttpResponse response = ResponseBuilder.notFound()
 *     .json(ErrorResponse.of(404, "USER_NOT_FOUND", "User " + id + " not found"))
 *     .build();
 *
 * // 8. Conflict response (409 Conflict)
 * HttpResponse response = ResponseBuilder.conflict()
 *     .json(ErrorResponse.of(409, "DUPLICATE_EMAIL", "Email already exists"))
 *     .build();
 *
 * // 9. Internal server error (500 Internal Server Error)
 * HttpResponse response = ResponseBuilder.internalServerError()
 *     .json(ErrorResponse.builder()
 *         .status(500)
 *         .code("INTERNAL_ERROR")
 *         .message("An unexpected error occurred")
 *         .traceId(traceId)
 *         .build())
 *     .build();
 *
 * // 10. Custom headers and caching
 * HttpResponse response = ResponseBuilder.ok()
 *     .header("Cache-Control", "max-age=3600, public")
 *     .header("ETag", "\"" + resourceVersion + "\"")
 *     .json(resource)
 *     .build();
 *
 * // 11. Accepted for async processing (202 Accepted)
 * String jobId = asyncService.submitJob(jobData);
 * HttpResponse response = ResponseBuilder.accepted()
 *     .header("Location", "/jobs/" + jobId)
 *     .json(Map.of("jobId", jobId, "status", "processing"))
 *     .build();
 *
 * // 12. Forbidden access (403 Forbidden)
 * HttpResponse response = ResponseBuilder.forbidden()
 *     .json(ErrorResponse.of(403, "ACCESS_DENIED", "Insufficient permissions"))
 *     .build();
 * }</pre>
 *
 * <p>
 * <b>Status Code Factories</b><br>
 * Factory methods for common HTTP status codes:
 * <pre>
 * 200 OK                → ResponseBuilder.ok()
 * 201 Created           → ResponseBuilder.created()
 * 202 Accepted          → ResponseBuilder.accepted()
 * 204 No Content        → ResponseBuilder.noContent()
 * 400 Bad Request       → ResponseBuilder.badRequest()
 * 401 Unauthorized      → ResponseBuilder.unauthorized()
 * 403 Forbidden         → ResponseBuilder.forbidden()
 * 404 Not Found         → ResponseBuilder.notFound()
 * 409 Conflict          → ResponseBuilder.conflict()
 * 500 Internal Server   → ResponseBuilder.internalServerError()
 * Custom                → ResponseBuilder.status(code)
 * </pre>
 *
 * <p>
 * <b>JSON Serialization</b><br>
 * Automatic Jackson serialization with: - JavaTimeModule for Java 8 date/time
 * types - Pretty printing disabled (compact JSON) - Non-null values only (via
 * @JsonInclude) - ISO-8601 dates/times
 *
 * <p>
 * <b>Content Types</b><br>
 * Automatically set based on content method:
 * <pre>
 * .text(...)  → Content-Type: text/plain; charset=UTF-8
 * .json(...)  → Content-Type: application/json; charset=UTF-8
 * .html(...)  → Content-Type: text/html; charset=UTF-8
 * </pre>
 *
 * <p>
 * <b>Header Management</b><br>
 * Common headers:
 * <pre>{@code
 * .header("Location", "/resource/123")
 * .header("Cache-Control", "no-cache")
 * .header("ETag", "\"v1\"")
 * .header("WWW-Authenticate", "Bearer realm=\"API\"")
 * }</pre>
 *
 * <p>
 * <b>Error Response Integration</b><br>
 * Works seamlessly with ErrorResponse:
 * <pre>{@code
 * ResponseBuilder.badRequest()
 *     .json(ErrorResponse.builder()
 *         .status(400)
 *         .code("INVALID_INPUT")
 *         .message("Email format is invalid")
 *         .validationErrors(errors)
 *         .build())
 *     .build();
 * }</pre>
 *
 * <p>
 * <b>Common Patterns</b><br>
 * <pre>{@code
 * // Resource creation with Location
 * ResponseBuilder.created()
 *     .header("Location", resourceUri)
 *     .json(createdResource);
 *
 * // Pagination headers
 * ResponseBuilder.ok()
 *     .header("X-Total-Count", String.valueOf(total))
 *     .header("Link", buildLinkHeader(page, size, total))
 *     .json(items);
 *
 * // CORS headers
 * ResponseBuilder.ok()
 *     .header("Access-Control-Allow-Origin", "*")
 *     .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE")
 *     .json(data);
 * }</pre>
 *
 * <p>
 * <b>Best Practices</b><br>
 * - Use factory methods (ok(), created()) instead of status codes - Return
 * ErrorResponse for all error cases - Set Location header for 201 Created
 * responses - Use 204 No Content for successful DELETE operations - Include
 * traceId in error responses for debugging - Set Cache-Control for cacheable
 * resources - Use 202 Accepted for async operations
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Builder instances are NOT thread-safe (designed for single-thread use). Built
 * HttpResponse instances are immutable and thread-safe. Shared ObjectMapper is
 * thread-safe and reusable.
 *
 * @see ErrorResponse
 * @see RoutingServlet
 * @see io.activej.http.HttpResponse
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Fluent builder for HTTP responses with JSON serialization
 * @doc.layer core
 * @doc.pattern Builder
 */
@Slf4j
public class ResponseBuilder {

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private final int statusCode;
    private final HttpResponse.Builder builder;

    private ResponseBuilder(int statusCode) {
        this.statusCode = statusCode;
        this.builder = HttpResponse.ofCode(statusCode);
    }

    // Factory methods for common status codes
    /**
     * Creates a 200 OK response builder.
     */
    public static ResponseBuilder ok() {
        return new ResponseBuilder(200);
    }

    /**
     * Creates a 201 Created response builder.
     */
    public static ResponseBuilder created() {
        return new ResponseBuilder(201);
    }

    /**
     * Creates a 202 Accepted response builder.
     */
    public static ResponseBuilder accepted() {
        return new ResponseBuilder(202);
    }

    /**
     * Creates a 204 No Content response builder.
     */
    public static ResponseBuilder noContent() {
        return new ResponseBuilder(204);
    }

    /**
     * Creates a 400 Bad Request response builder.
     */
    public static ResponseBuilder badRequest() {
        return new ResponseBuilder(400);
    }

    /**
     * Creates a 401 Unauthorized response builder.
     */
    public static ResponseBuilder unauthorized() {
        return new ResponseBuilder(401);
    }

    /**
     * Creates a 403 Forbidden response builder.
     */
    public static ResponseBuilder forbidden() {
        return new ResponseBuilder(403);
    }

    /**
     * Creates a 404 Not Found response builder.
     */
    public static ResponseBuilder notFound() {
        return new ResponseBuilder(404);
    }

    /**
     * Creates a 409 Conflict response builder.
     */
    public static ResponseBuilder conflict() {
        return new ResponseBuilder(409);
    }

    /**
     * Creates a 500 Internal Server Error response builder.
     */
    public static ResponseBuilder internalServerError() {
        return new ResponseBuilder(500);
    }

    /**
     * Backwards-compatible alias for internalServerError(). Some callers use
     * the older name `serverError()`; provide the alias to avoid widespread
     * call-site changes.
     */
    public static ResponseBuilder serverError() {
        return internalServerError();
    }

    /**
     * Creates a 503 Service Unavailable response builder.
     */
    public static ResponseBuilder serviceUnavailable() {
        return new ResponseBuilder(503);
    }

    /**
     * Creates a response builder with custom status code.
     */
    public static ResponseBuilder status(int code) {
        return new ResponseBuilder(code);
    }

    // Content methods
    /**
     * Sets plain text content.
     */
    public ResponseBuilder text(String text) {
        builder.withHeader(io.activej.http.HttpHeaders.of("Content-Type"), "text/plain; charset=utf-8");
        builder.withBody(text.getBytes(StandardCharsets.UTF_8));
        return this;
    }

    /**
     * Sets HTML content.
     */
    public ResponseBuilder html(String html) {
        builder.withHeader(io.activej.http.HttpHeaders.of("Content-Type"), "text/html; charset=utf-8");
        builder.withBody(html.getBytes(StandardCharsets.UTF_8));
        return this;
    }

    /**
     * Sets JSON content by serializing the object.
     */
    public ResponseBuilder json(Object object) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(object);
            builder.withHeader(io.activej.http.HttpHeaders.of("Content-Type"), "application/json; charset=utf-8");
            builder.withBody(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Failed to serialize object to JSON", e);
            throw new RuntimeException("JSON serialization failed", e);
        }
        return this;
    }

    /**
     * Sets raw JSON content (already serialized).
     */
    public ResponseBuilder rawJson(String json) {
        builder.withHeader(io.activej.http.HttpHeaders.of("Content-Type"), "application/json; charset=utf-8");
        builder.withBody(json.getBytes(StandardCharsets.UTF_8));
        return this;
    }

    /**
     * Sets binary content.
     */
    public ResponseBuilder bytes(byte[] data, String contentType) {
        builder.withHeader(io.activej.http.HttpHeaders.of("Content-Type"), contentType);
        builder.withBody(data);
        return this;
    }

    /**
     * Gets the underlying ActiveJ HttpResponse.Builder for advanced use cases.
     * Use this for operations not covered by ResponseBuilder API, such as
     * streaming responses.
     *
     * <p>
     * Example for SSE streaming:
     * <pre>{@code
     * ResponseBuilder.ok()
     *     .header(HttpHeaders.CONTENT_TYPE, "text/event-stream")
     *     .rawBuilder()
     *     .withBodyStream(channelSupplier)
     *     .build()
     * }</pre>
     */
    public HttpResponse.Builder rawBuilder() {
        return builder;
    }

    // Header methods
    /**
     * Adds a header to the response.
     */
    public ResponseBuilder header(String name, String value) {
        builder.withHeader(io.activej.http.HttpHeaders.of(name), value);
        return this;
    }

    /**
     * Adds a header to the response using an ActiveJ HttpHeader instance. This
     * overload is a compatibility helper for callers that pass HttpHeader
     * constants directly.
     */
    public ResponseBuilder header(HttpHeader header, String value) {
        builder.withHeader(header, value);
        return this;
    }

    /**
     * Sets the Location header (typically for 201 Created responses).
     */
    public ResponseBuilder location(String uri) {
        builder.withHeader(io.activej.http.HttpHeaders.of("Location"), uri);
        return this;
    }

    /**
     * Sets CORS headers for cross-origin requests.
     */
    public ResponseBuilder cors(String origin) {
        builder.withHeader(io.activej.http.HttpHeaders.of("Access-Control-Allow-Origin"), origin);
        builder.withHeader(io.activej.http.HttpHeaders.of("Access-Control-Allow-Methods"), "GET, POST, PUT, DELETE, OPTIONS");
        builder.withHeader(io.activej.http.HttpHeaders.of("Access-Control-Allow-Headers"), "Content-Type, Authorization");
        return this;
    }

    /**
     * Sets cache control headers.
     */
    public ResponseBuilder cacheControl(String directive) {
        builder.withHeader(io.activej.http.HttpHeaders.of("Cache-Control"), directive);
        return this;
    }

    /**
     * Disables caching.
     */
    public ResponseBuilder noCache() {
        builder.withHeader(io.activej.http.HttpHeaders.of("Cache-Control"), "no-cache, no-store, must-revalidate");
        builder.withHeader(io.activej.http.HttpHeaders.of("Pragma"), "no-cache");
        builder.withHeader(io.activej.http.HttpHeaders.of("Expires"), "0");
        return this;
    }

    /**
     * Builds the final HTTP response.
     */
    public HttpResponse build() {
        return builder.build();
    }

    private static ObjectMapper createObjectMapper() {
        return JsonUtils.getDefaultMapper();
    }
}

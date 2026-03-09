package com.ghatana.platform.http.server.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Production-grade standard error response structure for HTTP APIs with validation errors, tracing, and consistent formatting.
 *
 * <p><b>Purpose</b><br>
 * Provides unified error response format across all HTTP endpoints with structured error details,
 * validation errors, distributed tracing integration, and machine-readable error codes.
 * Ensures consistent error handling and debugging across the platform.
 *
 * <p><b>Architecture Role</b><br>
 * Error response value object in core/http/response for HTTP error formatting.
 * Used by:
 * - ResponseBuilder - Format error responses consistently
 * - Route Handlers - Return structured errors
 * - Error Filters - Transform exceptions to HTTP errors
 * - API Documentation - Document error response schemas
 *
 * <p><b>Error Response Features</b><br>
 * - <b>Status Code</b>: HTTP status code (400, 404, 500, etc.)
 * - <b>Error Code</b>: Machine-readable error code (VALIDATION_ERROR, NOT_FOUND, etc.)
 * - <b>Message</b>: Human-readable error message
 * - <b>Details</b>: Optional detailed error information (string or structured)
 * - <b>Validation Errors</b>: List of field-specific validation failures
 * - <b>Timestamp</b>: When the error occurred (ISO-8601)
 * - <b>Path</b>: Request path that caused the error
 * - <b>Trace ID</b>: Distributed tracing correlation ID
 * - <b>Immutable</b>: Value object pattern with Lombok @Value
 * - <b>Non-null Serialization</b>: Only populated fields in JSON (@JsonInclude)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // 1. Simple error response
 * ErrorResponse error = ErrorResponse.of(400, "Invalid request");
 * // → {"status": 400, "message": "Invalid request", "timestamp": "2025-11-06T10:30:00Z"}
 *
 * // 2. Error with code and message
 * ErrorResponse error = ErrorResponse.of(404, "USER_NOT_FOUND", "User 123 not found");
 * // → {"status": 404, "code": "USER_NOT_FOUND", "message": "User 123 not found", "timestamp": ...}
 *
 * // 3. Validation error with field details
 * ErrorResponse error = ErrorResponse.builder()
 *     .status(400)
 *     .code("VALIDATION_ERROR")
 *     .message("Invalid user data")
 *     .path("/api/users")
 *     .validationErrors(List.of(
 *         ValidationError.builder()
 *             .field("email")
 *             .message("Email format is invalid")
 *             .rejectedValue("not-an-email")
 *             .build(),
 *         ValidationError.builder()
 *             .field("age")
 *             .message("Age must be at least 18")
 *             .rejectedValue(15)
 *             .build()
 *     ))
 *     .build();
 *
 * // 4. Error with distributed tracing
 * ErrorResponse error = ErrorResponse.builder()
 *     .status(500)
 *     .code("DATABASE_ERROR")
 *     .message("Failed to save user")
 *     .path("/api/users")
 *     .traceId("550e8400-e29b-41d4-a716-446655440000")
 *     .details("Connection timeout after 30 seconds")
 *     .build();
 *
 * // 5. Bad request factory method
 * ErrorResponse error = ErrorResponse.badRequest("Missing required field: email", "/api/users");
 *
 * // 6. Not found factory method
 * ErrorResponse error = ErrorResponse.notFound("User 123 not found", "/api/users/123");
 *
 * // 7. Internal server error factory method
 * ErrorResponse error = ErrorResponse.internalServerError(
 *     "Unexpected error occurred", "/api/data");
 *
 * // 8. Error with structured details map
 * ErrorResponse error = ErrorResponse.builder()
 *     .status(409)
 *     .code("CONFLICT")
 *     .message("Email already exists")
 *     .path("/api/users")
 *     .detailsMap(Map.of(
 *         "conflictingField", "email",
 *         "existingUserId", "user-456",
 *         "suggestion", "Use a different email or recover existing account"
 *     ))
 *     .build();
 *
 * // 9. Multiple validation errors
 * List<ValidationError> errors = new ArrayList<>();
 * errors.add(ValidationError.builder()
 *     .field("username")
 *     .message("Username must be at least 3 characters")
 *     .rejectedValue("ab")
 *     .build());
 * errors.add(ValidationError.builder()
 *     .field("password")
 *     .message("Password must contain at least one digit")
 *     .rejectedValue("weakpass")
 *     .build());
 * 
 * ErrorResponse error = ErrorResponse.builder()
 *     .status(400)
 *     .code("VALIDATION_ERROR")
 *     .message("User validation failed")
 *     .path("/api/users")
 *     .validationErrors(errors)
 *     .build();
 *
 * // 10. Integration with ResponseBuilder
 * HttpResponse response = ResponseBuilder.badRequest()
 *     .json(ErrorResponse.of(400, "INVALID_INPUT", "Email format is invalid"))
 *     .build();
 * }</pre>
 *
 * <p><b>JSON Serialization Examples</b><br>
 * <pre>
 * Simple error:
 * {
 *   "status": 404,
 *   "message": "User not found",
 *   "timestamp": "2025-11-06T10:30:00Z"
 * }
 *
 * Error with code:
 * {
 *   "status": 400,
 *   "code": "VALIDATION_ERROR",
 *   "message": "Invalid user data",
 *   "timestamp": "2025-11-06T10:30:00Z"
 * }
 *
 * Validation error with field details:
 * {
 *   "status": 400,
 *   "code": "VALIDATION_ERROR",
 *   "message": "User validation failed",
 *   "path": "/api/users",
 *   "timestamp": "2025-11-06T10:30:00Z",
 *   "validationErrors": [
 *     {
 *       "field": "email",
 *       "message": "Email format is invalid",
 *       "rejectedValue": "not-an-email"
 *     },
 *     {
 *       "field": "age",
 *       "message": "Age must be at least 18",
 *       "rejectedValue": 15
 *     }
 *   ]
 * }
 *
 * Error with tracing:
 * {
 *   "status": 500,
 *   "code": "DATABASE_ERROR",
 *   "message": "Failed to save user",
 *   "path": "/api/users",
 *   "traceId": "550e8400-e29b-41d4-a716-446655440000",
 *   "details": "Connection timeout after 30 seconds",
 *   "timestamp": "2025-11-06T10:30:00Z"
 * }
 * </pre>
 *
 * <p><b>Standard Error Codes</b><br>
 * <pre>
 * BAD_REQUEST          → 400 (malformed request, missing fields)
 * VALIDATION_ERROR     → 400 (field validation failed)
 * UNAUTHORIZED         → 401 (authentication required)
 * FORBIDDEN            → 403 (insufficient permissions)
 * NOT_FOUND            → 404 (resource not found)
 * CONFLICT             → 409 (duplicate email, version conflict)
 * INTERNAL_SERVER_ERROR → 500 (unexpected server error)
 * DATABASE_ERROR       → 500 (database operation failed)
 * SERVICE_UNAVAILABLE  → 503 (downstream service unavailable)
 * </pre>
 *
 * <p><b>Factory Methods</b><br>
 * Convenience factories for common errors:
 * <pre>{@code
 * ErrorResponse.of(status, message)
 * ErrorResponse.of(status, code, message)
 * ErrorResponse.badRequest(message, path)
 * ErrorResponse.notFound(message, path)
 * ErrorResponse.internalServerError(message, path)
 * ErrorResponse.internalError(message, path)  // Alias
 * }</pre>
 *
 * <p><b>Validation Error Details</b><br>
 * Field-specific validation errors:
 * <pre>{@code
 * ValidationError.builder()
 *     .field("email")                        // Which field failed
 *     .message("Email format is invalid")    // Why it failed
 *     .rejectedValue("not-an-email")         // What was rejected
 *     .build();
 * }</pre>
 *
 * <p><b>Distributed Tracing Integration</b><br>
 * Include traceId for correlation across services:
 * <pre>{@code
 * String traceId = TracingContext.getCurrentTraceId();
 * ErrorResponse.builder()
 *     .status(500)
 *     .code("DOWNSTREAM_ERROR")
 *     .message("Payment service unavailable")
 *     .traceId(traceId)
 *     .build();
 * }</pre>
 *
 * <p><b>Structured Details</b><br>
 * Use detailsMap for additional structured information:
 * <pre>{@code
 * .detailsMap(Map.of(
 *     "retryAfter", "60",
 *     "endpoint", "https://api.example.com/payment",
 *     "errorCode", "GATEWAY_TIMEOUT"
 * ))
 * }</pre>
 *
 * <p><b>Best Practices</b><br>
 * - Use factory methods (badRequest, notFound) for common errors
 * - Include error codes for programmatic handling
 * - Add traceId for all 500-level errors
 * - Use validationErrors for field-specific failures
 * - Set path to help users identify failing endpoint
 * - Keep message user-friendly (not stack traces)
 * - Use detailsMap for structured additional context
 * - Log full exception server-side, return sanitized ErrorResponse to client
 *
 * <p><b>Security Considerations</b><br>
 * - Never include sensitive data in error messages
 * - Don't expose internal paths, database details, or stack traces
 * - Use generic messages for authentication/authorization failures
 * - Sanitize rejectedValue if it contains sensitive data
 *
 * <p><b>Thread Safety</b><br>
 * Immutable value object (Lombok @Value) - thread-safe.
 * Builder is NOT thread-safe (designed for single-thread use).
 *
 * @see ResponseBuilder
 * @see RoutingServlet
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Standard error response structure for HTTP APIs
 * @doc.layer core
 * @doc.pattern Value Object
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    
    /**
     * HTTP status code
     */
    int status;
    
    /**
     * Error code for programmatic handling
     */
    String code;
    
    /**
     * Human-readable error message
     */
    String message;
    
    /**
     * Optional detailed error information (string or structured)
     */
    String details;
    
    /**
     * Optional structured details map
     */
    Map<String, Object> detailsMap;
    
    /**
     * Timestamp when the error occurred
     */
    @Builder.Default
    Instant timestamp = Instant.now();
    
    /**
     * Request path that caused the error
     */
    String path;
    
    /**
     * Trace ID for distributed tracing
     */
    String traceId;
    
    /**
     * List of validation errors (for validation failures)
     */
    List<ValidationError> validationErrors;
    
    /**
     * Creates a simple error response with status and message.
     */
    public static ErrorResponse of(int status, String message) {
        return ErrorResponse.builder()
            .status(status)
            .message(message)
            .build();
    }
    
    /**
     * Creates an error response with status, code, and message.
     */
    public static ErrorResponse of(int status, String code, String message) {
        return ErrorResponse.builder()
            .status(status)
            .code(code)
            .message(message)
            .build();
    }
    
    /**
     * Creates a 400 Bad Request error.
     */
    public static ErrorResponse badRequest(String message, String path) {
        return ErrorResponse.builder()
            .status(400)
            .code("BAD_REQUEST")
            .message(message)
            .path(path)
            .build();
    }
    
    /**
     * Creates a 404 Not Found error.
     */
    public static ErrorResponse notFound(String message, String path) {
        return ErrorResponse.builder()
            .status(404)
            .code("NOT_FOUND")
            .message(message)
            .path(path)
            .build();
    }
    
    /**
     * Creates a 500 Internal Server Error.
     */
    public static ErrorResponse internalServerError(String message, String path) {
        return ErrorResponse.builder()
            .status(500)
            .code("INTERNAL_SERVER_ERROR")
            .message(message)
            .path(path)
            .build();
    }
    
    /**
     * Creates a 500 Internal Server Error.
     * Alias for internalServerError for backward compatibility.
     */
    public static ErrorResponse internalError(String message, String path) {
        return internalServerError(message, path);
    }
    
    /**
     * Validation error detail.
     */
    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ValidationError {
        /**
         * Field that failed validation
         */
        String field;
        
        /**
         * Validation error message
         */
        String message;
        
        /**
         * The rejected value
         */
        Object rejectedValue;
    }
}

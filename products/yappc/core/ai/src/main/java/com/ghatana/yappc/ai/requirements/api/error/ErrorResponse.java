package com.ghatana.yappc.ai.requirements.api.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ghatana.platform.http.server.response.ResponseBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Centralized error handling utility for consistent API error responses.
 *
 * <p><b>Purpose</b><br>
 * Provides standardized error response creation with proper HTTP status codes,
 * error categorization, and detailed error information for API consumers.
 *
 * <p><b>Error Categories</b><br>
 * - **Validation Errors:** 400 - Invalid request data or parameters
 * - **Authentication Errors:** 401 - Missing or invalid authentication
 * - **Authorization Errors:** 403 - Insufficient permissions
 * - **Not Found Errors:** 404 - Resource not found
 * - **Conflict Errors:** 409 - Resource conflicts or duplicates
 * - **Rate Limit Errors:** 429 - Too many requests
 * - **Server Errors:** 500 - Internal server errors
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Validation error
 * return ErrorResponse.badRequest("Invalid input", Map.of("field", "name"));
 * 
 * // Authentication error
 * return ErrorResponse.unauthorized("Invalid JWT token");
 * 
 * // Not found error
 * return ErrorResponse.notFound("Workspace not found", "workspace-123");
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Centralized error response utility
 * @doc.layer product
 * @doc.pattern Utility
 * @since 1.0.0
 */
public final class ErrorResponse {
    
    private ErrorResponse() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Create a bad request error response (400).
     *
     * @param message Human-readable error message
     * @return HTTP response with 400 status
     */
    public static io.activej.http.HttpResponse badRequest(String message) {
        return badRequest(message, null, null);
    }
    
    /**
     * Create a bad request error response (400) with details.
     *
     * @param message Human-readable error message
     * @param details Additional error details
     * @return HTTP response with 400 status
     */
    public static io.activej.http.HttpResponse badRequest(String message, Map<String, Object> details) {
        return badRequest(message, "VALIDATION_ERROR", details);
    }
    
    /**
     * Create a bad request error response (400) with code and details.
     *
     * @param message Human-readable error message
     * @param code Machine-readable error code
     * @param details Additional error details
     * @return HTTP response with 400 status
     */
    public static io.activej.http.HttpResponse badRequest(String message, String code, Map<String, Object> details) {
        ErrorBody body = new ErrorBody(400, message, code, details);
        return ResponseBuilder.badRequest()
            .header("Content-Type", "application/json")
            .text(body.toJson())
            .build();
    }
    
    /**
     * Create an unauthorized error response (401).
     *
     * @param message Human-readable error message
     * @return HTTP response with 401 status
     */
    public static io.activej.http.HttpResponse unauthorized(String message) {
        return unauthorized(message, null, null);
    }
    
    /**
     * Create an unauthorized error response (401) with code and details.
     *
     * @param message Human-readable error message
     * @param code Machine-readable error code
     * @param details Additional error details
     * @return HTTP response with 401 status
     */
    public static io.activej.http.HttpResponse unauthorized(String message, String code, Map<String, Object> details) {
        ErrorBody body = new ErrorBody(401, message, code != null ? code : "UNAUTHORIZED", details);
        return ResponseBuilder.unauthorized()
            .header("Content-Type", "application/json")
            .text(body.toJson())
            .build();
    }
    
    /**
     * Create a forbidden error response (403).
     *
     * @param message Human-readable error message
     * @return HTTP response with 403 status
     */
    public static io.activej.http.HttpResponse forbidden(String message) {
        return forbidden(message, null, null);
    }
    
    /**
     * Create a forbidden error response (403) with code and details.
     *
     * @param message Human-readable error message
     * @param code Machine-readable error code
     * @param details Additional error details
     * @return HTTP response with 403 status
     */
    public static io.activej.http.HttpResponse forbidden(String message, String code, Map<String, Object> details) {
        ErrorBody body = new ErrorBody(403, message, code != null ? code : "FORBIDDEN", details);
        return ResponseBuilder.forbidden()
            .header("Content-Type", "application/json")
            .text(body.toJson())
            .build();
    }
    
    /**
     * Create a not found error response (404).
     *
     * @param message Human-readable error message
     * @return HTTP response with 404 status
     */
    public static io.activej.http.HttpResponse notFound(String message) {
        return notFound(message, null, null);
    }
    
    /**
     * Create a not found error response (404) with code and details.
     *
     * @param message Human-readable error message
     * @param code Machine-readable error code
     * @param details Additional error details
     * @return HTTP response with 404 status
     */
    public static io.activej.http.HttpResponse notFound(String message, String code, Map<String, Object> details) {
        ErrorBody body = new ErrorBody(404, message, code != null ? code : "NOT_FOUND", details);
        return ResponseBuilder.notFound()
            .header("Content-Type", "application/json")
            .text(body.toJson())
            .build();
    }
    
    /**
     * Create a conflict error response (409).
     *
     * @param message Human-readable error message
     * @return HTTP response with 409 status
     */
    public static io.activej.http.HttpResponse conflict(String message) {
        return conflict(message, null, null);
    }
    
    /**
     * Create a conflict error response (409) with code and details.
     *
     * @param message Human-readable error message
     * @param code Machine-readable error code
     * @param details Additional error details
     * @return HTTP response with 409 status
     */
    public static io.activej.http.HttpResponse conflict(String message, String code, Map<String, Object> details) {
        ErrorBody body = new ErrorBody(409, message, code != null ? code : "CONFLICT", details);
        return ResponseBuilder.status(409)
            .header("Content-Type", "application/json")
            .text(body.toJson())
            .build();
    }
    
    /**
     * Create a rate limit error response (429).
     *
     * @param message Human-readable error message
     * @return HTTP response with 429 status
     */
    public static io.activej.http.HttpResponse rateLimited(String message) {
        return rateLimited(message, null, null);
    }
    
    /**
     * Create a rate limit error response (429) with code and details.
     *
     * @param message Human-readable error message
     * @param code Machine-readable error code
     * @param details Additional error details
     * @return HTTP response with 429 status
     */
    public static io.activej.http.HttpResponse rateLimited(String message, String code, Map<String, Object> details) {
        ErrorBody body = new ErrorBody(429, message, code != null ? code : "RATE_LIMITED", details);
        return ResponseBuilder.status(429)
            .header("Content-Type", "application/json")
            .header("Retry-After", "60") // Suggest retry after 60 seconds
            .text(body.toJson())
            .build();
    }
    
    /**
     * Create an internal server error response (500).
     *
     * @param message Human-readable error message
     * @return HTTP response with 500 status
     */
    public static io.activej.http.HttpResponse internalServerError(String message) {
        return internalServerError(message, null, null);
    }
    
    /**
     * Create an internal server error response (500) with code and details.
     *
     * @param message Human-readable error message
     * @param code Machine-readable error code
     * @param details Additional error details
     * @return HTTP response with 500 status
     */
    public static io.activej.http.HttpResponse internalServerError(String message, String code, Map<String, Object> details) {
        ErrorBody body = new ErrorBody(500, message, code != null ? code : "INTERNAL_ERROR", details);
        return ResponseBuilder.internalServerError()
            .header("Content-Type", "application/json")
            .text(body.toJson())
            .build();
    }
    
    /**
     * Create an error response from an exception.
     *
     * @param exception The exception to convert
     * @return HTTP response with appropriate status
     */
    public static io.activej.http.HttpResponse fromException(Exception exception) {
        Objects.requireNonNull(exception, "Exception cannot be null");
        
        // Handle specific exception types
        if (exception instanceof ValidationException) {
            ValidationException ve = (ValidationException) exception;
            return badRequest(ve.getMessage(), "VALIDATION_ERROR", ve.getDetails());
        }
        
        if (exception instanceof AuthenticationException) {
            AuthenticationException ae = (AuthenticationException) exception;
            return unauthorized(ae.getMessage(), ae.getCode(), ae.getDetails());
        }
        
        if (exception instanceof AuthorizationException) {
            AuthorizationException ae = (AuthorizationException) exception;
            return forbidden(ae.getMessage(), ae.getCode(), ae.getDetails());
        }
        
        if (exception instanceof NotFoundException) {
            NotFoundException nfe = (NotFoundException) exception;
            return notFound(nfe.getMessage(), "NOT_FOUND", nfe.getDetails());
        }
        
        if (exception instanceof ConflictException) {
            ConflictException ce = (ConflictException) exception;
            return conflict(ce.getMessage(), "CONFLICT", ce.getDetails());
        }
        
        if (exception instanceof RateLimitException) {
            RateLimitException rle = (RateLimitException) exception;
            return rateLimited(rle.getMessage(), "RATE_LIMITED", rle.getDetails());
        }
        
        // Default to internal server error for unknown exceptions
        return internalServerError("An unexpected error occurred", "INTERNAL_ERROR", 
            Map.of("type", exception.getClass().getSimpleName()));
    }
    
    /**
     * Error response body with standardized format.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class ErrorBody {
        private final int status;
        private final String error;
        private final String code;
        private final Map<String, Object> details;
        private final String timestamp;
        
        public ErrorBody(int status, String error, String code, Map<String, Object> details) {
            this.status = status;
            this.error = error;
            this.code = code;
            this.details = details;
            this.timestamp = Instant.now().toString();
        }
        
        public int getStatus() { return status; }
        public String getError() { return error; }
        public String getCode() { return code; }
        public Map<String, Object> getDetails() { return details; }
        public String getTimestamp() { return timestamp; }
        
        public String toJson() {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                return mapper.writeValueAsString(this);
            } catch (Exception e) {
                // Fallback to simple JSON format
                return String.format(
                    "{\"status\":%d,\"error\":\"%s\",\"code\":\"%s\",\"timestamp\":\"%s\"}",
                    status, error.replace("\"", "\\\""), code, timestamp
                );
            }
        }
    }
    
    // Custom exception types for better error handling
    
    public static class ValidationException extends Exception {
        private final Map<String, Object> details;
        
        public ValidationException(String message) {
            this(message, null);
        }
        
        public ValidationException(String message, Map<String, Object> details) {
            super(message);
            this.details = details;
        }
        
        public Map<String, Object> getDetails() { return details; }
    }
    
    public static class AuthenticationException extends Exception {
        private final String code;
        private final Map<String, Object> details;
        
        public AuthenticationException(String message) {
            this(message, null, null);
        }
        
        public AuthenticationException(String message, String code, Map<String, Object> details) {
            super(message);
            this.code = code;
            this.details = details;
        }
        
        public String getCode() { return code; }
        public Map<String, Object> getDetails() { return details; }
    }
    
    public static class AuthorizationException extends Exception {
        private final String code;
        private final Map<String, Object> details;
        
        public AuthorizationException(String message) {
            this(message, null, null);
        }
        
        public AuthorizationException(String message, String code, Map<String, Object> details) {
            super(message);
            this.code = code;
            this.details = details;
        }
        
        public String getCode() { return code; }
        public Map<String, Object> getDetails() { return details; }
    }
    
    public static class NotFoundException extends Exception {
        private final Map<String, Object> details;
        
        public NotFoundException(String message) {
            this(message, null);
        }
        
        public NotFoundException(String message, Map<String, Object> details) {
            super(message);
            this.details = details;
        }
        
        public Map<String, Object> getDetails() { return details; }
    }
    
    public static class ConflictException extends Exception {
        private final Map<String, Object> details;
        
        public ConflictException(String message) {
            this(message, null);
        }
        
        public ConflictException(String message, Map<String, Object> details) {
            super(message);
            this.details = details;
        }
        
        public Map<String, Object> getDetails() { return details; }
    }
    
    public static class RateLimitException extends Exception {
        private final Map<String, Object> details;
        
        public RateLimitException(String message) {
            this(message, null);
        }
        
        public RateLimitException(String message, Map<String, Object> details) {
            super(message);
            this.details = details;
        }
        
        public Map<String, Object> getDetails() { return details; }
    }
}

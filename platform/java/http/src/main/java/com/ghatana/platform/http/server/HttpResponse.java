package com.ghatana.platform.http.server;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;

/**
 * Standard HTTP response wrapper for consistent API responses.
 * 
 * Provides a unified response format across all endpoints.
 *
 * @param <T> the type of the response data
 *
 * @doc.type record
 * @doc.purpose Typed HTTP response wrapper with status and body
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HttpResponse<T>(
        boolean success,
        @Nullable T data,
        @Nullable ErrorInfo error,
        @NotNull Instant timestamp,
        @Nullable Map<String, Object> meta
) {
    
    private static final ObjectMapper MAPPER = JsonUtils.getDefaultMapper();
    
    /**
     * Create a successful response with data.
     */
    public static <T> HttpResponse<T> ok(@NotNull T data) {
        return new HttpResponse<>(true, data, null, Instant.now(), null);
    }
    
    /**
     * Create a successful response with data and metadata.
     */
    public static <T> HttpResponse<T> ok(@NotNull T data, @NotNull Map<String, Object> meta) {
        return new HttpResponse<>(true, data, null, Instant.now(), meta);
    }
    
    /**
     * Create a successful response with no data.
     */
    public static <Void> HttpResponse<Void> ok() {
        return new HttpResponse<>(true, null, null, Instant.now(), null);
    }
    
    /**
     * Create an error response.
     */
    public static <T> HttpResponse<T> error(@NotNull String code, @NotNull String message) {
        return new HttpResponse<>(false, null, new ErrorInfo(code, message, null), Instant.now(), null);
    }
    
    /**
     * Create an error response with details.
     */
    public static <T> HttpResponse<T> error(
            @NotNull String code, 
            @NotNull String message, 
            @Nullable Map<String, Object> details) {
        return new HttpResponse<>(false, null, new ErrorInfo(code, message, details), Instant.now(), null);
    }
    
    /**
     * Create an error response from an exception.
     */
    public static <T> HttpResponse<T> error(@NotNull Throwable throwable) {
        String code = throwable.getClass().getSimpleName();
        String message = throwable.getMessage() != null ? throwable.getMessage() : "An error occurred";
        return error(code, message);
    }
    
    /**
     * Create a not found error response.
     */
    public static <T> HttpResponse<T> notFound(@NotNull String message) {
        return error("NOT_FOUND", message);
    }
    
    /**
     * Create a bad request error response.
     */
    public static <T> HttpResponse<T> badRequest(@NotNull String message) {
        return error("BAD_REQUEST", message);
    }
    
    /**
     * Create an unauthorized error response.
     */
    public static <T> HttpResponse<T> unauthorized(@NotNull String message) {
        return error("UNAUTHORIZED", message);
    }
    
    /**
     * Create a forbidden error response.
     */
    public static <T> HttpResponse<T> forbidden(@NotNull String message) {
        return error("FORBIDDEN", message);
    }
    
    /**
     * Create an internal server error response.
     */
    public static <T> HttpResponse<T> internalError(@NotNull String message) {
        return error("INTERNAL_ERROR", message);
    }
    
    /**
     * Serialize this response to JSON.
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            return "{\"success\":false,\"error\":{\"code\":\"SERIALIZATION_ERROR\",\"message\":\"Failed to serialize response\"}}";
        }
    }
    
    /**
     * Error information for error responses.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorInfo(
            @NotNull String code,
            @NotNull String message,
            @Nullable Map<String, Object> details
    ) {}
}

package com.ghatana.platform.core.exception;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility for building structured HTTP error response bodies from {@link PlatformException}.
 *
 * <p>Produces a consistent error payload that all HTTP-facing services should use when
 * returning error responses. The shape is:
 * <pre>
 * {
 *   "code":      "AUTH-003",
 *   "message":   "Invalid credentials",
 *   "status":    401,
 *   "timestamp": "2026-03-25T10:00:00Z",
 *   "metadata":  { ... }   // optional context from the exception
 * }
 * </pre>
 *
 * <p>Usage example in an ActiveJ HTTP handler:
 * <pre>{@code
 * } catch (PlatformException ex) {
 *     Map<String, Object> body = ErrorResponseBuilder.from(ex).build();
 *     return HttpResponse.ofCode(ex.getHttpStatus())
 *         .withJson(JsonUtils.toJson(body));
 * }
 * </pre>
 *
 * <p>This class is a pure value-builder — it is stateless and thread-safe.
 *
 * @doc.type class
 * @doc.purpose Builds structured HTTP error response payloads from PlatformException (CONS-006)
 * @doc.layer platform
 * @doc.pattern Builder
 * @see PlatformException
 * @see ErrorCode
 */
public final class ErrorResponseBuilder {

    private final String code;
    private final String message;
    private final int status;
    private final Instant timestamp;
    private final Map<String, Object> metadata;

    private ErrorResponseBuilder(
            @NotNull String code,
            @NotNull String message,
            int status,
            @NotNull Instant timestamp,
            @NotNull Map<String, Object> metadata) {
        this.code = code;
        this.message = message;
        this.status = status;
        this.timestamp = timestamp;
        this.metadata = Map.copyOf(metadata);
    }

    /**
     * Creates a builder pre-populated from the given {@link PlatformException}.
     *
     * @param ex the platform exception to convert
     * @return an {@code ErrorResponseBuilder} ready to {@link #build()}
     */
    @NotNull
    public static ErrorResponseBuilder from(@NotNull PlatformException ex) {
        return new ErrorResponseBuilder(
            ex.getErrorCodeString(),
            ex.getMessage() != null ? ex.getMessage() : ex.getErrorCode().getDefaultMessage(),
            ex.getHttpStatus(),
            Instant.now(),
            ex.getMetadata()
        );
    }

    /**
     * Creates a builder from a raw {@link ErrorCode} with the default message.
     *
     * @param errorCode the error code
     * @return an {@code ErrorResponseBuilder}
     */
    @NotNull
    public static ErrorResponseBuilder from(@NotNull ErrorCode errorCode) {
        return new ErrorResponseBuilder(
            errorCode.getCode(),
            errorCode.getDefaultMessage(),
            errorCode.getHttpStatus(),
            Instant.now(),
            Map.of()
        );
    }

    /**
     * Creates a builder from a raw {@link ErrorCode} with a custom message.
     *
     * @param errorCode the error code
     * @param message   a human-readable override message
     * @return an {@code ErrorResponseBuilder}
     */
    @NotNull
    public static ErrorResponseBuilder from(@NotNull ErrorCode errorCode, @NotNull String message) {
        return new ErrorResponseBuilder(
            errorCode.getCode(),
            message,
            errorCode.getHttpStatus(),
            Instant.now(),
            Map.of()
        );
    }

    /**
     * Returns a new builder with an extra metadata entry added.
     *
     * @param key   metadata key
     * @param value metadata value (must be JSON-serializable)
     * @return new builder with the key/value included
     */
    @NotNull
    public ErrorResponseBuilder withMeta(@NotNull String key, @Nullable Object value) {
        Map<String, Object> updated = new HashMap<>(this.metadata);
        updated.put(key, value);
        return new ErrorResponseBuilder(code, message, status, timestamp, updated);
    }

    /**
     * Builds the response body as an immutable {@code Map<String, Object>},
     * suitable for JSON serialisation.
     *
     * <p>Keys present:
     * <ul>
     *   <li>{@code code} — error code string, e.g. {@code "AUTH-003"}</li>
     *   <li>{@code message} — human-readable description</li>
     *   <li>{@code status} — HTTP status integer</li>
     *   <li>{@code timestamp} — ISO-8601 UTC timestamp</li>
     *   <li>{@code metadata} — optional context map (omitted if empty)</li>
     * </ul>
     *
     * @return immutable map ready for JSON serialisation
     */
    @NotNull
    public Map<String, Object> build() {
        Map<String, Object> response = new HashMap<>();
        response.put("code", code);
        response.put("message", message);
        response.put("status", status);
        response.put("timestamp", timestamp.toString());
        if (!metadata.isEmpty()) {
            response.put("metadata", metadata);
        }
        return Map.copyOf(response);
    }

    /**
     * Convenience: builds and serialises to JSON using {@link com.ghatana.platform.core.util.JsonUtils}.
     *
     * @return JSON string of the error response
     * @throws RuntimeException if JSON serialisation fails
     */
    @NotNull
    public String toJson() {
        try {
            return com.ghatana.platform.core.util.JsonUtils.toJson(build());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to serialise error response to JSON", e);
        }
    }
}

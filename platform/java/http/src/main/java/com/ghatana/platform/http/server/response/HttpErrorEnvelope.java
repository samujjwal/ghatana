package com.ghatana.platform.http.server.response;

import io.activej.http.HttpHeaders;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kernel HTTP Error Envelope
 *
 * Provides standardized error response formatting for all HTTP endpoints.
 * Ensures consistent error structure across web, mobile, and backend APIs.
 *
 * <p>Error envelope structure:</p>
 * <pre>
 * {
 *   "error": {
 *     "code": "ERROR_CODE",
 *     "message": "Human-readable error message",
 *     "correlationId": "request-correlation-id",
 *     "timestamp": "ISO-8601 timestamp"
 *   }
 * }
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Standardized HTTP error response envelope
 * @doc.layer platform
 * @doc.pattern Response Builder
 */
public final class HttpErrorEnvelope {

    private HttpErrorEnvelope() {
        // Utility class - prevent instantiation
    }

    /**
     * Create an error response with the given code, message, and correlation ID.
     *
     * @param statusCode HTTP status code
     * @param errorCode application-specific error code
     * @param message human-readable error message
     * @param correlationId request correlation ID
     * @return HTTP response with error envelope
     */
    public static HttpResponse create(
            int statusCode,
            String errorCode,
            String message,
            String correlationId) {
        Map<String, Object> errorBody = new LinkedHashMap<>();
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", errorCode);
        error.put("message", message);
        error.put("correlationId", correlationId);
        error.put("timestamp", java.time.Instant.now().toString());
        errorBody.put("error", error);

        String jsonBody = toJsonString(errorBody);
        return HttpResponse.ofCode(statusCode)
            .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), correlationId)
            .withBody(jsonBody.getBytes(StandardCharsets.UTF_8))
            .build();
    }

    /**
     * Create a 400 Bad Request error response.
     *
     * @param errorCode application-specific error code
     * @param message human-readable error message
     * @param correlationId request correlation ID
     * @return HTTP response with error envelope
     */
    public static HttpResponse badRequest(
            String errorCode,
            String message,
            String correlationId) {
        return create(400, errorCode, message, correlationId);
    }

    /**
     * Create a 401 Unauthorized error response.
     *
     * @param errorCode application-specific error code
     * @param message human-readable error message
     * @param correlationId request correlation ID
     * @return HTTP response with error envelope
     */
    public static HttpResponse unauthorized(
            String errorCode,
            String message,
            String correlationId) {
        return create(401, errorCode, message, correlationId);
    }

    /**
     * Create a 403 Forbidden error response.
     *
     * @param errorCode application-specific error code
     * @param message human-readable error message
     * @param correlationId request correlation ID
     * @return HTTP response with error envelope
     */
    public static HttpResponse forbidden(
            String errorCode,
            String message,
            String correlationId) {
        return create(403, errorCode, message, correlationId);
    }

    /**
     * Create a 404 Not Found error response.
     *
     * @param errorCode application-specific error code
     * @param message human-readable error message
     * @param correlationId request correlation ID
     * @return HTTP response with error envelope
     */
    public static HttpResponse notFound(
            String errorCode,
            String message,
            String correlationId) {
        return create(404, errorCode, message, correlationId);
    }

    /**
     * Create a 409 Conflict error response.
     *
     * @param errorCode application-specific error code
     * @param message human-readable error message
     * @param correlationId request correlation ID
     * @return HTTP response with error envelope
     */
    public static HttpResponse conflict(
            String errorCode,
            String message,
            String correlationId) {
        return create(409, errorCode, message, correlationId);
    }

    /**
     * Create a 500 Internal Server Error response.
     *
     * @param errorCode application-specific error code
     * @param message human-readable error message
     * @param correlationId request correlation ID
     * @return HTTP response with error envelope
     */
    public static HttpResponse internalError(
            String errorCode,
            String message,
            String correlationId) {
        return create(500, errorCode, message, correlationId);
    }

    /**
     * Create a 502 Bad Gateway error response.
     *
     * @param errorCode application-specific error code
     * @param message human-readable error message
     * @param correlationId request correlation ID
     * @return HTTP response with error envelope
     */
    public static HttpResponse badGateway(
            String errorCode,
            String message,
            String correlationId) {
        return create(502, errorCode, message, correlationId);
    }

    /**
     * Create a 503 Service Unavailable error response.
     *
     * @param errorCode application-specific error code
     * @param message human-readable error message
     * @param correlationId request correlation ID
     * @return HTTP response with error envelope
     */
    public static HttpResponse serviceUnavailable(
            String errorCode,
            String message,
            String correlationId) {
        return create(503, errorCode, message, correlationId);
    }

    /**
     * Convert a map to JSON string.
     *
     * @param data the data to convert
     * @return JSON string
     */
    private static String toJsonString(Map<String, Object> data) {
        // Simple JSON serialization - in production use Jackson or similar
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(escapeJson((String) value)).append("\"");
            } else if (value instanceof Map) {
                sb.append(toJsonString((Map<String, Object>) value));
            } else {
                sb.append(value);
            }
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Escape special characters in JSON string.
     *
     * @param value the value to escape
     * @return escaped value
     */
    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}

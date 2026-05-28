package com.ghatana.platform.http.server.activej;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared ActiveJ HTTP boundary helpers for correlation IDs, idempotency keys,
 * JSON responses, and structured error envelopes.
 *
 * @doc.type class
 * @doc.purpose ActiveJ HTTP request/response helpers for product route adapters
 * @doc.layer platform
 * @doc.pattern Adapter Helper
 */
public final class ActiveJHttpExchangeSupport {

    public static final String CORRELATION_HEADER = "X-Correlation-ID";
    public static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";
    public static final String CONTENT_JSON = "application/json";

    private static final String IDEMPOTENCY_KEY_PATTERN = "^[a-zA-Z0-9_-]{8,255}$";

    private ActiveJHttpExchangeSupport() {}

    /**
     * Extracts a caller-provided correlation ID or generates a new UUID.
     *
     * @param request the inbound ActiveJ request
     * @return a non-blank correlation ID
     */
    public static String correlationId(HttpRequest request) {
        String value = firstHeader(request, CORRELATION_HEADER, "X-Correlation-Id", "X-Request-ID");
        if (value != null && !value.isBlank()) {
            return value.strip();
        }
        return UUID.randomUUID().toString();
    }

    /**
     * Extracts and validates an optional idempotency key.
     *
     * @param request the inbound ActiveJ request
     * @return the idempotency key when present
     * @throws IllegalArgumentException when the key is present but malformed
     */
    public static Optional<String> idempotencyKey(HttpRequest request) {
        String key = request.getHeader(HttpHeaders.of(IDEMPOTENCY_HEADER));
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }

        String trimmed = key.strip();
        if (!isValidIdempotencyKey(trimmed)) {
            throw new IllegalArgumentException(
                "Idempotency key must be 8-255 URL-safe alphanumeric characters, hyphens, or underscores"
            );
        }
        return Optional.of(trimmed);
    }

    /**
     * Validates an idempotency key without reading request state.
     *
     * @param key the candidate idempotency key
     * @return {@code true} when the key is syntactically valid
     */
    public static boolean isValidIdempotencyKey(String key) {
        return key != null && !key.isBlank() && key.matches(IDEMPOTENCY_KEY_PATTERN);
    }

    /**
     * Builds a JSON response and echoes the correlation ID when supplied.
     *
     * @param mapper the object mapper used for serialization
     * @param statusCode HTTP status code
     * @param body response body or pre-serialized JSON string
     * @param correlationId correlation ID to echo
     * @return ActiveJ HTTP response
     */
    public static HttpResponse jsonResponse(
        ObjectMapper mapper,
        int statusCode,
        Object body,
        String correlationId
    ) {
        try {
            String json = body instanceof String stringBody ? stringBody : mapper.writeValueAsString(body);
            return responseBuilder(statusCode, correlationId)
                .withJson(json)
                .build();
        } catch (JsonProcessingException ex) {
            return errorResponse(mapper, 500, "SERIALIZATION_ERROR", "Failed to serialize response", correlationId);
        }
    }

    /**
     * Builds a structured JSON error envelope.
     *
     * @param mapper the object mapper used for serialization
     * @param statusCode HTTP status code
     * @param code machine-readable error code
     * @param message domain-safe error message
     * @param correlationId correlation ID to include and echo
     * @return ActiveJ HTTP response
     */
    public static HttpResponse errorResponse(
        ObjectMapper mapper,
        int statusCode,
        String code,
        String message,
        String correlationId
    ) {
        return errorResponse(mapper, statusCode, code, message, correlationId, Map.of());
    }

    /**
     * Builds a structured JSON error envelope with safe details.
     *
     * @param mapper the object mapper used for serialization
     * @param statusCode HTTP status code
     * @param code machine-readable error code
     * @param message domain-safe error message
     * @param correlationId correlation ID to include and echo
     * @param details optional safe detail map
     * @return ActiveJ HTTP response
     */
    public static HttpResponse errorResponse(
        ObjectMapper mapper,
        int statusCode,
        String code,
        String message,
        String correlationId,
        Map<String, Object> details
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("message", message);
        body.put("correlationId", correlationId);
        if (details != null && !details.isEmpty()) {
            body.put("details", details);
        }

        try {
            return responseBuilder(statusCode, correlationId)
                .withJson(mapper.writeValueAsString(body))
                .build();
        } catch (JsonProcessingException ex) {
            return responseBuilder(500, correlationId)
                .withJson("{\"error\":\"SERIALIZATION_ERROR\",\"message\":\"Failed to serialize error response\"}")
                .build();
        }
    }

    private static HttpResponse.Builder responseBuilder(int statusCode, String correlationId) {
        HttpResponse.Builder builder = HttpResponse.ofCode(statusCode)
            .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_JSON);
        if (correlationId != null && !correlationId.isBlank()) {
            builder = builder.withHeader(HttpHeaders.of(CORRELATION_HEADER), correlationId);
        }
        return builder;
    }

    private static String firstHeader(HttpRequest request, String... names) {
        for (String name : names) {
            String value = request.getHeader(HttpHeaders.of(name));
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}

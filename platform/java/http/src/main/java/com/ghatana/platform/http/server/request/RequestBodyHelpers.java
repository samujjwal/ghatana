package com.ghatana.platform.http.server.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Kernel Request Body Helpers
 *
 * Provides reusable request body parsing, validation, idempotency, and error helpers
 * for all HTTP endpoints. Ensures consistent request handling across web, mobile, and backend APIs.
 *
 * @doc.type class
 * @doc.purpose Reusable request body helpers for HTTP endpoints
 * @doc.layer platform
 * @doc.pattern Helper, Utility
 */
public final class RequestBodyHelpers {

    private static final ObjectMapper JSON = new ObjectMapper();

    private RequestBodyHelpers() {
        // Utility class - prevent instantiation
    }

    /**
     * Parse request body as JSON.
     *
     * @param request the HTTP request
     * @return Promise containing parsed JSON node
     */
    public static Promise<JsonNode> parseJsonBody(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                try {
                    String bodyString = body.getString(StandardCharsets.UTF_8);
                    JsonNode node = JSON.readTree(bodyString);
                    return Promise.of(node);
                } catch (Exception e) {
                    return Promise.ofException(new RequestParseException("Invalid JSON body", e));
                }
            });
    }

    /**
     * Parse request body as JSON synchronously.
     *
     * @param request the HTTP request
     * @return parsed JSON node
     * @throws RequestParseException if parsing fails
     */
    public static JsonNode parseJsonBodySync(HttpRequest request) throws RequestParseException {
        try {
            io.activej.bytebuf.ByteBuf bodyBuf = request.getBody();
            if (bodyBuf == null || bodyBuf.readRemaining() == 0) {
                throw new RequestParseException("Request body is empty");
            }
            byte[] body = bodyBuf.asArray();
            String bodyString = new String(body, StandardCharsets.UTF_8);
            return JSON.readTree(bodyString);
        } catch (RequestParseException e) {
            throw e;
        } catch (Exception e) {
            throw new RequestParseException("Invalid JSON body", e);
        }
    }

    /**
     * Validate that a required field exists and is not blank.
     *
     * @param node the JSON node
     * @param fieldName the field name
     * @return field value
     * @throws ValidationException if field is missing or blank
     */
    public static String requireTextField(JsonNode node, String fieldName) throws ValidationException {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            throw new ValidationException(fieldName + " is required");
        }
        String value = field.asText();
        if (value == null || value.isBlank()) {
            throw new ValidationException(fieldName + " is required");
        }
        return value.strip();
    }

    /**
     * Validate that a required field exists.
     *
     * @param node the JSON node
     * @param fieldName the field name
     * @return field value
     * @throws ValidationException if field is missing
     */
    public static JsonNode requireField(JsonNode node, String fieldName) throws ValidationException {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            throw new ValidationException(fieldName + " is required");
        }
        return field;
    }

    /**
     * Get optional field value.
     *
     * @param node the JSON node
     * @param fieldName the field name
     * @return field value, or null if missing
     */
    public static String getOptionalTextField(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        String value = field.asText();
        return value == null || value.isBlank() ? null : value.strip();
    }

    /**
     * Extract or generate idempotency key from request.
     *
     * @param request the HTTP request
     * @return idempotency key
     */
    public static String getIdempotencyKey(HttpRequest request) {
        String key = request.getHeader(HttpHeaders.of("X-Idempotency-Key"));
        if (key == null || key.isBlank()) {
            key = UUID.randomUUID().toString();
        }
        return key;
    }

    /**
     * Check if request has idempotency key.
     *
     * @param request the HTTP request
     * @return true if idempotency key is present
     */
    public static boolean hasIdempotencyKey(HttpRequest request) {
        String key = request.getHeader(HttpHeaders.of("X-Idempotency-Key"));
        return key != null && !key.isBlank();
    }

    /**
     * Extract correlation ID from request.
     *
     * @param request the HTTP request
     * @return correlation ID
     */
    public static String getCorrelationId(HttpRequest request) {
        String correlationId = request.getHeader(HttpHeaders.of("X-Correlation-ID"));
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        return correlationId;
    }

    /**
     * Exception thrown when request parsing fails.
     */
    public static class RequestParseException extends Exception {
        public RequestParseException(String message) {
            super(message);
        }

        public RequestParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when request validation fails.
     */
    public static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }

        public ValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

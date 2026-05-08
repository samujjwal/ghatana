package com.ghatana.digitalmarketing.api;

import java.util.Map;
import java.util.Objects;

/**
 * P1-012: Standard error envelope for DMOS API responses.
 *
 * <p>Provides a consistent error response format across all servlets with:
 * - HTTP status code
 * - User-safe message (never exposes internal details)
 * - Correlation ID for diagnostics
 * - Optional field-level errors for validation failures</p>
 *
 * <p>The UI's ApiError class parses this envelope to display safe user messages
 * while preserving correlation IDs for support diagnostics.</p>
 *
 * @doc.type record
 * @doc.purpose Standard error envelope for API responses (P1-012)
 * @doc.layer product
 * @doc.pattern ErrorEnvelope
 */
public record StandardErrorEnvelope(
    String error,
    String message,
    int status,
    String correlationId,
    Map<String, String> details
) {
    public StandardErrorEnvelope {
        Objects.requireNonNull(error, "error must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        details = details != null ? Map.copyOf(details) : Map.of();
    }

    /**
     * Creates a standard error envelope with minimal fields.
     */
    public StandardErrorEnvelope(String error, String message, int status, String correlationId) {
        this(error, message, status, correlationId, Map.of());
    }

    /**
     * Creates a standard error envelope from a status code and message.
     * Generates a correlation ID if not provided.
     */
    public static StandardErrorEnvelope of(int status, String message, String correlationId) {
        return new StandardErrorEnvelope(errorCodeForStatus(status), message, status, correlationId);
    }

    /**
     * Creates a standard error envelope with field-level errors.
     */
    public static StandardErrorEnvelope withDetails(
        int status,
        String message,
        String correlationId,
        Map<String, String> details
    ) {
        return new StandardErrorEnvelope(errorCodeForStatus(status), message, status, correlationId, details);
    }

    private static String errorCodeForStatus(int status) {
        return switch (status) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 409 -> "CONFLICT";
            case 423 -> "LOCKED";
            case 429 -> "TOO_MANY_REQUESTS";
            default -> status >= 500 ? "INTERNAL_SERVER_ERROR" : "UNKNOWN_ERROR";
        };
    }
}

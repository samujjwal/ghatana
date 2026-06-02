package com.ghatana.platform.http.server.filter;

import io.activej.http.HttpHeaders;
import io.activej.http.HttpHeaderValue;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.util.UUID;

/**
 * Kernel Correlation Middleware
 *
 * Provides unified correlation ID handling for web, mobile, and backend HTTP requests.
 * Ensures every request has a correlation ID for tracing and observability.
 *
 * <p>Correlation ID flow:</p>
 * <ul>
 *   <li>Extract from X-Correlation-ID header if present</li>
 *   <li>Generate new UUID if missing</li>
 *   <li>Set in request context for downstream use</li>
 *   <li>Propagate to response headers</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Unified correlation ID middleware for HTTP requests
 * @doc.layer platform
 * @doc.pattern Middleware, Filter
 */
public final class CorrelationMiddleware {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String CORRELATION_ID_CONTEXT_KEY = "correlationId";

    private CorrelationMiddleware() {
        // Utility class - prevent instantiation
    }

    /**
     * Apply correlation middleware to an HTTP request.
     *
     * @param request the HTTP request
     * @param handler the request handler
     * @return Promise containing the HTTP response
     */
    public static Promise<HttpResponse> apply(
            HttpRequest request,
            java.util.function.Function<HttpRequest, Promise<HttpResponse>> handler) {
        String correlationId = extractOrCreateCorrelationId(request);

        // Store correlation ID in request attachment for downstream use
        request.attach(CORRELATION_ID_CONTEXT_KEY, correlationId);

        return handler.apply(request)
            .then(response -> {
                // Ensure correlation ID is in response headers
                if (response.getHeader(HttpHeaders.of(CORRELATION_ID_HEADER)) == null) {
                    HttpResponse.Builder builder = HttpResponse.ofCode(response.getCode());
                    for (var entry : response.getHeaders()) {
                        builder.withHeader(entry.getKey(), entry.getValue());
                    }
                    builder.withHeader(HttpHeaders.of(CORRELATION_ID_HEADER), correlationId);
                    return Promise.of(builder.build());
                }
                return Promise.of(response);
            });
    }

    /**
     * Extract correlation ID from request or generate new one.
     *
     * @param request the HTTP request
     * @return correlation ID
     */
    public static String extractOrCreateCorrelationId(HttpRequest request) {
        String correlationId = request.getHeader(HttpHeaders.of(CORRELATION_ID_HEADER));
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = generateCorrelationId();
        }
        return correlationId;
    }

    /**
     * Generate a new correlation ID.
     *
     * @return new correlation ID
     */
    public static String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Get correlation ID from request context.
     *
     * @param request the HTTP request
     * @return correlation ID, or null if not set
     */
    public static String getCorrelationId(HttpRequest request) {
        return request.getHeader(HttpHeaders.of(CORRELATION_ID_HEADER));
    }

    /**
     * Validate correlation ID format.
     *
     * @param correlationId the correlation ID to validate
     * @return true if valid UUID format
     */
    public static boolean isValidCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(correlationId);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

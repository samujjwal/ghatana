/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.platform.core.exception.BaseException;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standardised async error propagation helpers for ActiveJ Promise chains in
 * Data-Cloud HTTP handlers.
 *
 * <p>Eliminates the repeated {@code .mapException()} / {@code .then()} boilerplate
 * that handlers applied inconsistently.  A typical handler looks like:
 *
 * <pre>{@code
 * @Override
 * public Promise<HttpResponse> handleCreate(HttpRequest request) {
 *     String correlationId = http.resolveCorrelationId(request);
 *     String tenantId      = http.resolveTenantId(request);
 *
 *     try (RequestContext ctx = RequestContext.bind(correlationId, tenantId)) {
 *         return entityService.create(tenantId, payload)
 *             .map(entity -> http.jsonResponse(Map.of("entity", entity), correlationId))
 *             .mapException(e -> PromiseSupport.toHttpResponse(e, http, correlationId));
 *     }
 * }
 * }</pre>
 *
 * <h2>Error Mapping</h2>
 * <ul>
 *   <li>{@link BaseException} with 4xx semantics → forwarded status code</li>
 *   <li>{@link IllegalArgumentException} / {@link NullPointerException} → 400</li>
 *   <li>{@link SecurityException} → 403</li>
 *   <li>All other {@link Exception} → 500 (detail logged, safe message returned)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Standardised async error propagation for ActiveJ handler chains (DC-017)
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class PromiseSupport {

    private static final Logger log = LoggerFactory.getLogger(PromiseSupport.class);

    private PromiseSupport() {}

    /**
     * Maps an exception from a Promise chain to a deterministic {@link HttpResponse}.
     *
     * <p>Use as the argument to {@code Promise.mapException()}.
     *
     * @param e             exception from the promise chain
     * @param http          handler support for building responses
     * @param correlationId request correlation ID to propagate in the response
     * @return HTTP response with an appropriate status code and JSON error body
     */
    public static HttpResponse toHttpResponse(Exception e, HttpHandlerSupport http,
                                               String correlationId) {
        if (e instanceof BaseException base) {
            int statusCode = base.getErrorCode().getHttpStatus();
            // Treat all client errors as-is; log server errors at WARN
            if (statusCode >= 500) {
                log.warn("[{}] Internal server error: {}", correlationId, base.getMessage(), base);
            } else {
                log.debug("[{}] Client error {}: {}", correlationId, statusCode, base.getMessage());
            }
            return http.errorResponse(statusCode, base.getMessage(), correlationId);
        }

        if (e instanceof IllegalArgumentException || e instanceof NullPointerException) {
            log.debug("[{}] Bad request: {}", correlationId, e.getMessage());
            return http.errorResponse(400, e.getMessage() != null ? e.getMessage() : "Bad request",
                correlationId);
        }

        if (e instanceof SecurityException) {
            log.warn("[{}] Forbidden: {}", correlationId, e.getMessage());
            return http.errorResponse(403, "Forbidden", correlationId);
        }

        // Unexpected server-side error — log with full stack trace, return safe message
        log.error("[{}] Unhandled exception: {}", correlationId, e.getMessage(), e);
        return http.errorResponse(500, "An internal error occurred", correlationId);
    }

    /**
     * Wraps an exception-throwing supplier into a {@link Promise}.
     *
     * <p>Converts any checked exception thrown by {@code supplier} into a
     * {@link Promise#ofException(Throwable)} so it propagates through the
     * Promise chain rather than escaping to the eventloop as an unchecked
     * exception.
     *
     * @param <T>      result type
     * @param supplier the supplying function (may throw {@link Exception})
     * @return promise that resolves to the supplier's result or rejects with
     *         its exception
     */
    public static <T> Promise<T> safeSupply(ThrowingSupplier<T> supplier) {
        try {
            return Promise.of(supplier.get());
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    /**
     * Functional interface for suppliers that may throw a checked exception.
     *
     * @param <T> result type
     */
    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}

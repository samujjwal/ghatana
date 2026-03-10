/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.middleware;

import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeader;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * HTTP middleware that enforces a correlation ID on every request.
 *
 * <h2>Behaviour</h2>
 * <ol>
 *   <li>Reads the {@code X-Correlation-ID} request header.</li>
 *   <li>If absent, generates a new random UUID.</li>
 *   <li>Stores the correlation ID in the SLF4J {@link MDC} under key
 *       {@code correlationId} for the lifetime of the request handler.</li>
 *   <li>Appends {@code X-Correlation-ID} to the response so callers can
 *       trace the request end-to-end.</li>
 * </ol>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AsyncServlet servlet = RoutingServlet.create()...;
 * AsyncServlet withCorrelation = new CorrelationIdFilter(servlet);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Propagates correlation IDs from/to requests for end-to-end tracing
 * @doc.layer api
 * @doc.pattern Middleware, Decorator
 */
public class CorrelationIdFilter implements AsyncServlet {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    /** Header name for correlation ID propagation. */
    public static final HttpHeader X_CORRELATION_ID = HttpHeaders.register("X-Correlation-ID");
    /** MDC key used for structured logging. */
    public static final String MDC_KEY = "correlationId";

    private final AsyncServlet delegate;

    /**
     * @param delegate the underlying servlet to wrap
     */
    public CorrelationIdFilter(AsyncServlet delegate) {
        this.delegate = delegate;
    }

    @Override
    public Promise<HttpResponse> serve(HttpRequest request) throws Exception {
        String correlationId = request.getHeader(X_CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        // Store in MDC so all log statements within this request carry the ID
        final String finalId = correlationId;
        MDC.put(MDC_KEY, finalId);

        log.debug("Correlation ID: {}", finalId);

        return delegate.serve(request)
                .then(
                    response -> {
                        MDC.remove(MDC_KEY);
                        return Promise.of(addCorrelationHeader(response, finalId));
                    },
                    e -> {
                        MDC.remove(MDC_KEY);
                        log.warn("[correlationId={}] Request failed: {}", finalId, e.getMessage());
                        return Promise.ofException(e);
                    });
    }

    private HttpResponse addCorrelationHeader(HttpResponse response, String correlationId) {
        HttpResponse.Builder builder = HttpResponse.ofCode(response.getCode());
        for (var entry : response.getHeaders()) {
            builder.withHeader(entry.getKey(), entry.getValue());
        }
        builder.withHeader(X_CORRELATION_ID, correlationId);
        builder.withBody(response.getBody());
        return builder.build();
    }
}

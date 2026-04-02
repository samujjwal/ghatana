package com.ghatana.platform.observability;

import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.util.UUID;

/**
 * HTTP filter that initializes correlation context for structured logging.
 *
 * <p>TraceIdMdcFilter extracts or generates request IDs from HTTP headers and initializes
 * {@link CorrelationContext} so SLF4J MDC stays consistent across request handling. Ensures
 * context cleanup or restoration after request completion.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Extract request ID from X-Request-Id header</li>
 *   <li>Generate UUID request ID if header missing</li>
 *   <li>Populate CorrelationContext and MDC from a single source of truth</li>
 *   <li>Automatic context cleanup after async request completion</li>
 *   <li>ActiveJ Promise integration (non-blocking)</li>
 * </ul>
 *
 * <p><b>MDC Keys:</b></p>
 * <ul>
 *   <li><b>requestId:</b> X-Request-Id header or generated UUID</li>
 *   <li><b>traceId:</b> Placeholder (set to requestId for consistency)</li>
 *   <li><b>spanId:</b> Placeholder (set to "root" for top-level requests)</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * TraceIdMdcFilter mdcFilter = new TraceIdMdcFilter();
 * AsyncServlet servlet = mdcFilter.wrap(request -> {
 *     logger.info("Handling request");  // MDC populated with requestId
 *     return handleRequest(request);
 * });
 * }</pre>
 *
 * <p><b>HTTP Headers:</b></p>
 * <ul>
 *   <li><b>X-Request-Id:</b> Request identifier from client (optional)</li>
 *   <li>If missing or blank, generates new UUID</li>
 * </ul>
 *
 * <p><b>Integration with CorrelationContext:</b></p>
 * <p>This filter uses {@link CorrelationContext} directly so requestId and correlationId remain
 * available to downstream async handlers and logging.</p>
 *
 * <p><b>Cleanup Guarantee:</b></p>
 * <ul>
 *   <li>Context restored after promise completion (guaranteed cleanup)</li>
 *   <li>Prevents MDC leakage in thread pool environments</li>
 *   <li>Safe for reused threads (ActiveJ eventloop threads)</li>
 * </ul>
 *
 * <p><b>Performance:</b></p>
 * <ul>
 *   <li>Header extraction: O(1) overhead (< 100ns)</li>
 *   <li>UUID generation: ~1µs overhead (only if header missing)</li>
 *   <li>MDC put/remove: < 500ns overhead (3 operations)</li>
 * </ul>
 *
 * <p><b>Thread-Safety:</b> Thread-safe via MDC's ThreadLocal storage.</p>
 *
 * @see CorrelationContext for full correlation context management
 * @see HttpMetricsFilter for HTTP metrics collection
 *
 * @author Platform Team
 * @created 2024-10-01
 * @updated 2025-10-29
 * @version 1.0.0
 * @type HTTP Filter (MDC Interceptor)
 * @purpose Correlation context initialization for structured logging
 * @pattern Filter pattern, Decorator pattern, Interceptor pattern
 * @responsibility Request ID extraction/generation, correlation context population, cleanup
 * @usage Wrap AsyncServlet with wrap(): `filter.wrap(delegate)`
 * @examples See class-level JavaDoc for AsyncServlet wrapping example
 * @testing Test header extraction, UUID generation, MDC population, MDC cleanup, exception handling
 * @notes Cleanup occurs after async completion; generates UUID if X-Request-Id missing
 *
 * @doc.type class
 * @doc.purpose HTTP filter initializing correlation context for structured logging
 * @doc.layer platform
 * @doc.pattern Filter
 */
public final class TraceIdMdcFilter {
    private static final String HDR_REQUEST_ID = "X-Request-Id";

    public AsyncServlet wrap(AsyncServlet delegate) {
        return request -> serveWithCorrelationContext(delegate, request);
    }

    private Promise<HttpResponse> serveWithCorrelationContext(AsyncServlet delegate, HttpRequest request) throws Exception {
        CorrelationContext.CorrelationData previousContext = CorrelationContext.getCurrentData();
        String requestId = resolveRequestId(request);

        CorrelationContext.initialize(requestId, null, null, requestId);
        try {
            Promise<HttpResponse> responsePromise = delegate.serve(request);
            return responsePromise.whenComplete((response, exception) -> restoreContext(previousContext));
        } catch (Exception exception) {
            restoreContext(previousContext);
            throw exception;
        }
    }

    private String resolveRequestId(HttpRequest request) {
        String requestId = request.getHeader(HttpHeaders.of(HDR_REQUEST_ID));
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return requestId;
    }

    private void restoreContext(CorrelationContext.CorrelationData previousContext) {
        if (hasContext(previousContext)) {
            CorrelationContext.initializeFrom(previousContext);
            return;
        }
        CorrelationContext.clear();
    }

    private boolean hasContext(CorrelationContext.CorrelationData previousContext) {
        return previousContext.getCorrelationId() != null
            || previousContext.getUserId() != null
            || previousContext.getTenantId() != null
            || previousContext.getRequestId() != null
            || previousContext.getTraceId() != null
            || previousContext.getSpanId() != null;
    }
}

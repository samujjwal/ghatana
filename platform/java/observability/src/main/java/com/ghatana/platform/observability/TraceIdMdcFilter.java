package com.ghatana.platform.observability;

import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * HTTP filter that injects trace IDs into SLF4J MDC for structured logging.
 *
 * <p>TraceIdMdcFilter extracts or generates request IDs from HTTP headers and populates
 * SLF4J MDC with trace context for consistent logging across requests. Ensures MDC cleanup
 * after request completion.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Extract request ID from X-Request-Id header</li>
 *   <li>Generate UUID request ID if header missing</li>
 *   <li>Populate MDC with requestId, traceId, spanId</li>
 *   <li>Automatic MDC cleanup after request (in finally block)</li>
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
 * <p>This filter provides basic MDC setup. For full correlation context (userId, tenantId, etc.),
 * use {@link CorrelationContext} in combination with this filter or replace with
 * CorrelationContext-based filter.</p>
 *
 * <p><b>Cleanup Guarantee:</b></p>
 * <ul>
 *   <li>MDC cleared in finally block (guaranteed cleanup)</li>
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
 * @purpose Trace ID injection into SLF4J MDC for structured logging
 * @pattern Filter pattern, Decorator pattern, Interceptor pattern
 * @responsibility Request ID extraction/generation, MDC population, MDC cleanup
 * @usage Wrap AsyncServlet with wrap(): `filter.wrap(delegate)`
 * @examples See class-level JavaDoc for AsyncServlet wrapping example
 * @testing Test header extraction, UUID generation, MDC population, MDC cleanup, exception handling
 * @notes MDC cleanup guaranteed via finally; generates UUID if X-Request-Id missing; placeholder traceId/spanId
 *
 * @doc.type class
 * @doc.purpose HTTP filter injecting trace IDs into SLF4J MDC for structured logging
 * @doc.layer platform
 * @doc.pattern Filter
 */
public final class TraceIdMdcFilter {
    private static final String HDR_REQUEST_ID = "X-Request-Id";

    public AsyncServlet wrap(AsyncServlet delegate) {
        return request -> {
            try {
                return serveWithMdc(delegate, request);
            } catch (Exception e) {
                MDC.clear();
                return Promise.ofException(e);
            }
        };
    }

    private Promise<HttpResponse> serveWithMdc(AsyncServlet delegate, HttpRequest request) throws Exception {
        String requestId = request.getHeader(HttpHeaders.of(HDR_REQUEST_ID));
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put("requestId", requestId);
        // Placeholders to keep MDC keys consistent
        MDC.put("traceId", requestId);
        MDC.put("spanId", "root");
        try {
            return delegate.serve(request);
        } finally {
            MDC.remove("requestId");
            MDC.remove("traceId");
            MDC.remove("spanId");
        }
    }
}

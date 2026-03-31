package com.ghatana.yappc.infrastructure.observability.tracing;

import io.activej.http.HttpHeaders;
import io.activej.http.HttpHeader;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import io.activej.http.AsyncServlet;
import org.slf4j.MDC;

/**
 * HTTP filter that extracts/injects correlation IDs for distributed tracing.
 *
 * <p><b>Purpose</b><br>
 * Intercepts HTTP requests to extract correlation IDs from headers (or generate
 * new ones), and adds correlation IDs to response headers. Integrates with
 * SLF4J MDC for logging correlation.
 *
 * <p><b>Headers</b><br>
 * - X-Correlation-ID: Primary correlation identifier<br>
 * - X-Trace-ID: Trace identifier (falls back to correlation ID)<br>
 * - X-Span-ID: Current span identifier<br>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RoutingServlet router = RoutingServlet.create()
 *     .with("/*", new TracingFilter(delegateServlet))
 *     .with("/api/*", apiServlet);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose HTTP correlation ID filter for distributed tracing
 * @doc.layer infrastructure
 * @doc.pattern Filter, Decorator
 */
public class TracingFilter implements AsyncServlet {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String TRACE_ID_HEADER = "X-Trace-ID";
    public static final String SPAN_ID_HEADER = "X-Span-ID";

    private static final HttpHeader CORRELATION_ID_HTTP_HEADER = HttpHeaders.of(CORRELATION_ID_HEADER);
    private static final HttpHeader TRACE_ID_HTTP_HEADER = HttpHeaders.of(TRACE_ID_HEADER);
    private static final HttpHeader SPAN_ID_HTTP_HEADER = HttpHeaders.of(SPAN_ID_HEADER);

    private final AsyncServlet delegate;

    public TracingFilter(AsyncServlet delegate) {
        this.delegate = delegate;
    }

    @Override
    public Promise<HttpResponse> serve(HttpRequest request) throws Exception {
        // Extract or generate correlation ID
        String correlationId = extractOrCreateCorrelationId(request);
        String traceId = request.getHeader(TRACE_ID_HTTP_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = correlationId;
        }

        // Initialize tracing context
        TracingContext.initialize(correlationId);
        TracingContext.set(TracingContext.TRACE_ID_KEY, traceId);
        TracingContext.startSpan();

        // Set MDC for logging
        MDC.put("correlationId", correlationId);
        MDC.put("traceId", traceId);
        MDC.put("spanId", TracingContext.getSpanId());
        MDC.put("method", request.getMethod().toString());
        MDC.put("path", request.getRelativePath());

        return delegate.serve(request)
            .map(response -> addTracingHeaders(response, correlationId))
            .whenComplete((response, exception) -> {
                // Always cleanup
                MDC.clear();
                TracingContext.clear();
            });
    }

    private String extractOrCreateCorrelationId(HttpRequest request) {
        String correlationId = request.getHeader(CORRELATION_ID_HTTP_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = TracingContext.initialize();
        }
        return correlationId;
    }

    private HttpResponse addTracingHeaders(HttpResponse response, String correlationId) {
        return HttpResponse.builder()
            .withCode(response.getCode())
            .withHeader(HttpHeaders.of(CORRELATION_ID_HEADER), correlationId)
            .withHeader(HttpHeaders.of(TRACE_ID_HEADER), TracingContext.getTraceId())
            .withHeader(HttpHeaders.of(SPAN_ID_HEADER), TracingContext.getSpanId())
            .withBody(response.getBody())
            .build();
    }
}
